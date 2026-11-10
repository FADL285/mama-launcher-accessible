package info.fadl.mamalauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

/** Actual grant is the authority; denial history is only a UI hint. */
final class CallLogAccess {
  static final int REQUEST = 52;
  static final String DENIED = "call_log_denial_completed";

  static boolean granted(Context context) {
    return context.checkSelfPermission(Manifest.permission.READ_CALL_LOG)
        == PackageManager.PERMISSION_GRANTED;
  }

  static void recordResult(Context c, String[] permissions, int[] results) {
    for (int i = 0; i < permissions.length && i < results.length; i++) {
      if (Manifest.permission.READ_CALL_LOG.equals(permissions[i])) {
        Prefs.get(c)
            .edit()
            .putBoolean(DENIED, results[i] != PackageManager.PERMISSION_GRANTED)
            .apply();
      }
    }
  }
}
