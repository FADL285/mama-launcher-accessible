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

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private Future<?> task;
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
    task =
        executor.submit(
            () -> {
              T result = null;
              boolean ok = false;
              try {
                result = read.run(token);
                ok = !token.isCanceled();
              } catch (RuntimeException ignored) {
              } finally {
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
    if (task != null) task.cancel(true);
  }

  @Override
  public void close() {
    closed = true;
    cancel();
    executor.shutdownNow();
  }
}
