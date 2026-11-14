package info.fadl.mamalauncher;

import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** One worker and one cancellation/generation boundary per foreground owner. */
final class CallLogWorker implements AutoCloseable {
  interface Read<T> {
    T run(CancellationSignal signal);
  }

  /** Managed resource limit: a provider that ignores interrupts leaves its
   *  thread (and maybe an open cursor) stuck after shutdownNow. Abandoning is
   *  capped; past the cap, later reads fail through their watchdog instead of
   *  leaking another thread. A stuck thread that finally returns frees a slot. */
  private static final int MAX_ABANDONED = 3;

  private ExecutorService executor = Executors.newSingleThreadExecutor();
  private int abandoned;
  private final Handler main = new Handler(Looper.getMainLooper());
  private Future<?> task;
  private ExecutorService taskExecutor;
  /** The CURRENT task's running state: set while its callable executes, even
   *  if it is stuck. Per task, so an abandoned task finishing late cannot
   *  clear the state of the read that replaced it. */
  private volatile java.util.concurrent.atomic.AtomicBoolean taskRunning;
  /** Marks the current task as abandoned so its late return frees a slot. */
  private volatile java.util.concurrent.atomic.AtomicBoolean taskAbandoned;
  /** Per-task one-shot guard: an abandoned slot is freed exactly once, no
   *  matter whether the task's own finally or cancel() notices the exit
   *  first (the finish-and-mark window cannot lose or double-free a slot). */
  private volatile java.util.concurrent.atomic.AtomicBoolean taskSlotFreed;
  private CancellationSignal signal;
  private int generation;
  private boolean closed;

  <T> void read(Read<T> read, Consumer<T> success, Runnable failure) {
    cancel();
    if (closed) return;
    int current = generation;
    signal = new CancellationSignal();
    CancellationSignal token = signal;
    Runnable timeout =
        () -> {
          if (!closed && generation == current) {
            cancel();
            failure.run();
          }
        };
    main.postDelayed(timeout, 6000);
    taskExecutor = executor;
    final java.util.concurrent.atomic.AtomicBoolean running =
        new java.util.concurrent.atomic.AtomicBoolean();
    final java.util.concurrent.atomic.AtomicBoolean wasAbandoned =
        new java.util.concurrent.atomic.AtomicBoolean();
    final java.util.concurrent.atomic.AtomicBoolean slotFreed =
        new java.util.concurrent.atomic.AtomicBoolean();
    taskRunning = running;
    taskAbandoned = wasAbandoned;
    taskSlotFreed = slotFreed;
    task =
        executor.submit(
            () -> {
              T result = null;
              boolean ok = false;
              running.set(true);
              try {
                result = read.run(token);
                ok = !token.isCanceled();
              } catch (Throwable ignored) {
                // Errors (OOM, linkage) must also surface as failure - an
                // uncaught one would remove the watchdog and hang the view.
              } finally {
                running.set(false);
                if (wasAbandoned.get() && slotFreed.compareAndSet(false, true))
                  main.post(
                      () -> {
                        // A stuck thread that returned frees its slot.
                        if (abandoned > 0) abandoned--;
                      });
                main.removeCallbacks(timeout);
              }
              final T value = result;
              final boolean valid = ok;
              main.post(
                  () -> {
                    if (!closed && generation == current) {
                      if (valid) success.accept(value);
                      else failure.run();
                    }
                  });
            });
  }

  void cancel() {
    generation++;
    if (signal != null) signal.cancel();
    Future<?> current = task;
    if (current != null) {
      current.cancel(true);
      // Future.cancel() reports done immediately even when the callable keeps
      // running, so a provider blocked inside a binder call (which ignores
      // interruption) would keep the executor's only thread forever and every
      // later read would queue behind it. The current task's own running flag
      // still says its callable is executing: abandon that executor and give
      // later reads a fresh one. The stuck thread leaks, bounded by the number
      // of abandoned reads; a queued-but-unstarted task is simply cancelled,
      // no swap.
      java.util.concurrent.atomic.AtomicBoolean running = taskRunning;
      if (running != null && running.get() && taskExecutor != null) {
        // Abandon only while swapping is still allowed, and only then shut the
        // stuck executor down. Past the cap the executor is left running: the
        // next read queues behind it and fails through its own watchdog - a
        // clear retryable failure, never a RejectedExecutionException thrown
        // at the caller from a terminated executor.
        if (executor == taskExecutor && abandoned < MAX_ABANDONED) {
          taskExecutor.shutdownNow();
          abandoned++;
          java.util.concurrent.atomic.AtomicBoolean mark = taskAbandoned;
          if (mark != null) mark.set(true);
          executor = Executors.newSingleThreadExecutor();
          // The task may have exited between the running check and the mark:
          // its finally then saw no mark and freed nothing. Free the slot
          // here instead - the CAS keeps it exactly-once either way.
          java.util.concurrent.atomic.AtomicBoolean freed = taskSlotFreed;
          if (freed != null && !running.get() && freed.compareAndSet(false, true)
              && abandoned > 0) {
            abandoned--;
          }
        }
      }
    }
  }

  /** Test seam: how many stuck executors are currently abandoned. */
  int abandonedForTest() {
    return abandoned;
  }

  /** Test seam: wait until the current executor has drained. */
  void awaitIdleForTest() throws Exception {
    executor.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
  }

  @Override
  public void close() {
    closed = true;
    cancel();
    executor.shutdownNow();
  }
}
