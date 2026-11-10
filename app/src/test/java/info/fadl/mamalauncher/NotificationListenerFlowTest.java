package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.app.Notification;
import android.content.ComponentName;
import android.os.*;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 25, 33})
public class NotificationListenerFlowTest {
  LauncherNotificationListener service;

  @Before
  public void setup() {
    service = Robolectric.buildService(LauncherNotificationListener.class).create().get();
    Settings.Secure.putString(
        service.getContentResolver(),
        "enabled_notification_listeners",
        new ComponentName(service, LauncherNotificationListener.class).flattenToString());
    Prefs.get(service)
        .edit()
        .putString(AppSelection.PREF_KEY, "one")
        .putString(AppSelection.SECONDARY_KEY, "")
        .apply();
    NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.DISABLED);
  }

  @After
  public void cleanup() {
    service.onDestroy();
  }

  void add(String pkg, Notification n) {
    Shadows.shadowOf(service)
        .addActiveNotification(
            new StatusBarNotification(
                pkg, pkg, 1, null, 1000, 0, 0, n, android.os.Process.myUserHandle(), 0));
  }

  @Test
  public void emptyConnectedSnapshotIsKnownReadyNotDisconnected() {
    service.onListenerConnected();
    assertEquals(NotificationBadgeStore.Status.READY, NotificationBadgeStore.status());
    service.onListenerDisconnected();
    assertEquals(NotificationBadgeStore.Status.DISCONNECTED, NotificationBadgeStore.status());
    assertFalse(NotificationBadgeStore.has("one"));
  }

  @Test
  public void rankingMissingIsUnknownOn26PlusButGuardedFallbackOn24And25() {
    add("one", new Notification());
    service.onListenerConnected();
    if (Build.VERSION.SDK_INT >= 26) {
      assertEquals(NotificationBadgeStore.Status.UNAVAILABLE, NotificationBadgeStore.status());
      assertFalse(NotificationBadgeStore.has("one"));
    } else {
      assertEquals(NotificationBadgeStore.Status.READY, NotificationBadgeStore.status());
      assertTrue(NotificationBadgeStore.has("one"));
    }
  }

  @Test
  public void unconfiguredPackagesAndOngoingNotificationsDoNotProduceDots() {
    add("other", new Notification());
    Notification n = new Notification();
    n.flags = Notification.FLAG_ONGOING_EVENT;
    add("one", n);
    service.onListenerConnected();
    assertEquals(NotificationBadgeStore.Status.READY, NotificationBadgeStore.status());
    assertFalse(NotificationBadgeStore.has("one"));
    assertFalse(NotificationBadgeStore.has("other"));
  }

  @Test
  public void accessRevocationInvalidatesEvenExistingConnection() {
    service.onListenerConnected();
    Settings.Secure.putString(service.getContentResolver(), "enabled_notification_listeners", "");
    service.onNotificationRankingUpdate(null);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(150));
    assertEquals(NotificationBadgeStore.Status.DISABLED, NotificationBadgeStore.status());
  }
}
