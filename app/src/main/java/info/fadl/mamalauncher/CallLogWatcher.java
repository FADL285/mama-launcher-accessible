package info.fadl.mamalauncher;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;

/** Foreground invalidation only, never an always-on polling service. */
final class CallLogWatcher implements AutoCloseable {
  private final Context context;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh;
  private boolean registered;
  private final ContentObserver observer =
      new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
          handler.removeCallbacks(refresh);
          handler.postDelayed(refresh, 200);
        }
      };

  CallLogWatcher(Context c, Runnable refresh) {
    context = c;
    this.refresh = refresh;
  }

  void start() {
    if (!registered && CallLogAccess.granted(context)) {
      try {
        context
            .getContentResolver()
            .registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer);
        registered = true;
      } catch (RuntimeException ignored) {
      }
    }
  }

  @Override
  public void close() {
    handler.removeCallbacks(refresh);
    if (registered) {
      context.getContentResolver().unregisterContentObserver(observer);
      registered = false;
    }
  }
}
