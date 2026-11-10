package info.fadl.mamalauncher;

import android.app.Notification;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.List;

/** Never reads notification extras, invokes PendingIntents or cancels notifications. */
public final class LauncherNotificationListener extends NotificationListenerService {
  private static LauncherNotificationListener connected;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh = this::snapshot;

  static void refreshConnected(android.content.Context context) {
    if (connected != null) connected.schedule();
    else {
      NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.CONNECTING);
      try {
        requestRebind(
            new android.content.ComponentName(context, LauncherNotificationListener.class));
      } catch (RuntimeException unavailable) {
        NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.UNAVAILABLE);
      }
    }
  }

  @Override
  public void onListenerConnected() {
    super.onListenerConnected();
    connected = this;
    snapshot();
  }

  @Override
  public void onListenerDisconnected() {
    connected = null;
    handler.removeCallbacks(refresh);
    NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.DISCONNECTED);
    super.onListenerDisconnected();
  }

  @Override
  public void onNotificationPosted(StatusBarNotification n) {
    schedule();
  }

  @Override
  public void onNotificationRemoved(StatusBarNotification n) {
    schedule();
  }

  @Override
  public void onNotificationRankingUpdate(RankingMap ranks) {
    schedule();
  }

  private void schedule() {
    handler.removeCallbacks(refresh);
    handler.postDelayed(refresh, 120);
  }

  private void snapshot() {
    if (connected != this) return;
    try {
      if (!NotificationAccess.allowed(this)) {
        NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.DISABLED);
        return;
      }
      StatusBarNotification[] active = getActiveNotifications();
      if (active == null) throw new IllegalStateException("Notification snapshot unavailable");
      RankingMap rankings = getCurrentRanking();
      java.util.Set<String> configured =
          new java.util.HashSet<>(
              AppSelection.parse(Prefs.get(this).getString(AppSelection.PREF_KEY, "")));
      configured.addAll(
          AppSelection.parse(Prefs.get(this).getString(AppSelection.SECONDARY_KEY, "")));
      List<NotificationBadgeStore.Item> result = new ArrayList<>();
      for (StatusBarNotification sbn : active) {
        if (!configured.contains(sbn.getPackageName())) continue;
        if (!android.os.Process.myUserHandle().equals(sbn.getUser())) continue;
        Notification n = sbn.getNotification();
        if (!eligible(n)) continue;
        if (Build.VERSION.SDK_INT >= 26) {
          Ranking ranking = new Ranking();
          if (rankings == null || !rankings.getRanking(sbn.getKey(), ranking))
            throw new IllegalStateException("Notification ranking unavailable");
          if (!ranking.canShowBadge()) continue;
        }
        result.add(
            new NotificationBadgeStore.Item(
                sbn.getKey(),
                sbn.getPackageName(),
                sbn.getGroupKey(),
                (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0));
      }
      NotificationBadgeStore.replace(NotificationBadgeStore.Status.READY, result);
    } catch (RuntimeException ignored) {
      NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.UNAVAILABLE);
    }
  }

  static boolean eligible(Notification n) {
    if ((n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0)
      return false;
    return !Notification.CATEGORY_TRANSPORT.equals(n.category)
        && !Notification.CATEGORY_NAVIGATION.equals(n.category)
        && !Notification.CATEGORY_PROGRESS.equals(n.category)
        && !Notification.CATEGORY_SERVICE.equals(n.category);
  }

  @Override
  public void onDestroy() {
    if (connected == this) {
      connected = null;
      NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.DISCONNECTED);
    }
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
