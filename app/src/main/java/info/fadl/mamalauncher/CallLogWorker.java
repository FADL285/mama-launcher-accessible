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
  /** Identity of the task currently EXECUTING on the current executor: set
   *  while its callable runs, even if it is stuck. Tracked separately from
   *  the latest submitted read - a read queued at the abandonment cap must
   *  not overwrite the stuck runner's identity, or a slot freed later could
   *  never swap the wedged executor out (a retry's cancel() would only see
   *  the queued read's never-started flag and leave the executor in place). */
  private volatile java.util.concurrent.atomic.AtomicBoolean runningOnExecutor;
  /** Marks the running task as abandoned so its late return frees a slot. */
  private volatile java.util.concurrent.atomic.AtomicBoolean abandonedOnExecutor;
  /** Per-task one-shot guard: an abandoned slot is freed exactly once, no
   *  matter whether the task's own finally or cancel() notices the exit
   *  first (the finish-and-mark window cannot lose or double-free a slot). */
  private volatile java.util.concurrent.atomic.AtomicBoolean slotFreedOnExecutor;
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
    final java.util.concurrent.atomic.AtomicBoolean running =
        new java.util.concurrent.atomic.AtomicBoolean();
    final java.util.concurrent.atomic.AtomicBoolean wasAbandoned =
        new java.util.concurrent.atomic.AtomicBoolean();
    final java.util.concurrent.atomic.AtomicBoolean slotFreed =
        new java.util.concurrent.atomic.AtomicBoolean();
    task =
        executor.submit(
            () -> {
              T result = null;
              boolean ok = false;
              // A task only ever starts on the executor that was current at
              // its submit (a swapped-out executor's queued tasks are
              // discarded by shutdownNow), so claiming the runner identity
              // here always describes the current executor.
              runningOnExecutor = running;
              abandonedOnExecutor = wasAbandoned;
              slotFreedOnExecutor = slotFreed;
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
    if (current != null) current.cancel(true);
    // Future.cancel() reports done immediately even when the callable keeps
    // running, so a provider blocked inside a binder call (which ignores
    // interruption) would keep the executor's only thread forever and every
    // later read would queue behind it. The running task's own flag still
    // says its callable is executing: abandon that executor and give later
    // reads a fresh one. The stuck thread leaks, bounded by the number of
    // abandoned reads; a queued-but-unstarted task is simply cancelled,
    // no swap.
    java.util.concurrent.atomic.AtomicBoolean running = runningOnExecutor;
    if (running != null && running.get()) {
      // Abandon only while swapping is still allowed, and only then shut the
      // stuck executor down. Past the cap the executor is left running: the
      // next read queues behind it and fails through its own watchdog - a
      // clear retryable failure, never a RejectedExecutionException thrown
      // at the caller from a terminated executor.
      if (abandoned < MAX_ABANDONED) {
        ExecutorService stuck = executor;
        stuck.shutdownNow();
        abandoned++;
        java.util.concurrent.atomic.AtomicBoolean mark = abandonedOnExecutor;
        if (mark != null) mark.set(true);
        executor = Executors.newSingleThreadExecutor();
        runningOnExecutor = null;
        abandonedOnExecutor = null;
        java.util.concurrent.atomic.AtomicBoolean freed = slotFreedOnExecutor;
        slotFreedOnExecutor = null;
        // The task may have exited between the running check and the mark:
        // its finally then saw no mark and freed nothing. Free the slot
        // here instead - the CAS keeps it exactly-once either way.
        if (freed != null && !running.get() && freed.compareAndSet(false, true)
            && abandoned > 0) {
          abandoned--;
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
