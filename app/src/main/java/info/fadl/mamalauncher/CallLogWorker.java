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

  private ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private Future<?> task;
  private ExecutorService taskExecutor;
  /** Set while the current task's callable is executing, even if it is stuck. */
  private volatile boolean taskRunning;
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
    task =
        executor.submit(
            () -> {
              T result = null;
              boolean ok = false;
              taskRunning = true;
              try {
                result = read.run(token);
                ok = !token.isCanceled();
              } catch (Throwable ignored) {
                // Errors (OOM, linkage) must also surface as failure - an
                // uncaught one would remove the watchdog and hang the view.
              } finally {
                taskRunning = false;
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
      // later read would queue behind it. Our own running flag still says the
      // callable is executing: abandon that executor and give later reads a
      // fresh one. The stuck thread leaks, bounded by the number of abandoned
      // reads; a queued-but-unstarted task is simply cancelled, no swap.
      if (taskRunning && taskExecutor != null) {
        taskExecutor.shutdownNow();
        if (executor == taskExecutor) executor = Executors.newSingleThreadExecutor();
      }
    }
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
