package info.fadl.mamalauncher;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

final class NotificationAccess {
  static boolean allowed(Context context) {
    if (!BuildConfig.NOTIFICATION_ACCESS) return false;
    String enabled =
        Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
    ComponentName ours = new ComponentName(context, LauncherNotificationListener.class);
    if (enabled != null)
      for (String name : enabled.split(":"))
        if (ours.equals(ComponentName.unflattenFromString(name))) return true;
    return false;
  }

  static boolean unsupported(Context c) {
    ActivityManager manager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
    return Build.VERSION.SDK_INT <= 29 && manager != null && manager.isLowRamDevice();
  }

  static Intent settings(Context c) {
    if (!BuildConfig.NOTIFICATION_ACCESS)
      throw new IllegalStateException("Notification access is not included in this build");
    Intent detail =
        new Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS")
            .putExtra(
                "android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME",
                new ComponentName(c, LauncherNotificationListener.class).flattenToString());
    return detail.resolveActivity(c.getPackageManager()) != null
        ? detail
        : new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
  }

  static void refresh(Context c) {
    try {
      if (!allowed(c)) NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.DISABLED);
      else if (unsupported(c))
        NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.UNAVAILABLE);
      else LauncherNotificationListener.refreshConnected(c);
    } catch (RuntimeException unavailable) {
      NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.UNAVAILABLE);
    }
  }
}
