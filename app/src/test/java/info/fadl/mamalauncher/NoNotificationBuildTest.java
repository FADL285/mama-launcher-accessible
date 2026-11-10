package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 25, 33})
public class NoNotificationBuildTest {
  private Context context;

  @Before public void setup() {
    Assume.assumeFalse(BuildConfig.NOTIFICATION_ACCESS);
    context = RuntimeEnvironment.getApplication();
    context.setTheme(R.style.MamaTheme);
  }

  @Test public void listenerReallyAbsentAndCallLogPermissionPreserved() throws Exception {
    try {
      context.getPackageManager().getServiceInfo(
          new ComponentName(context, LauncherNotificationListener.class), 0);
      fail("Listener must not be registered in this variant");
    } catch (PackageManager.NameNotFoundException expected) { }
    String[] permissions = context.getPackageManager().getPackageInfo(
        context.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
    assertTrue(java.util.Arrays.asList(permissions).contains(android.Manifest.permission.READ_CALL_LOG));
  }

  @Test public void staleSystemGrantCannotEnableNotificationAccess() {
    Settings.Secure.putString(context.getContentResolver(), "enabled_notification_listeners",
        new ComponentName(context, LauncherNotificationListener.class).flattenToString());
    assertFalse(NotificationAccess.allowed(context));
    NotificationBadgeStore.replace(NotificationBadgeStore.Status.READY,
        java.util.Collections.singletonList(new NotificationBadgeStore.Item("k", "one", "g", false)));
    NotificationAccess.refresh(context);
    assertEquals(NotificationBadgeStore.Status.DISABLED, NotificationBadgeStore.status());
    assertFalse(NotificationBadgeStore.has("one"));
    try {
      NotificationAccess.settings(context);
      fail("Unavailable capability must not open notification settings");
    } catch (IllegalStateException expected) { }
  }

  @Test public void unavailableUiHidesGrantButtonAndExplainsCallLogIndependence() {
    View view = android.view.LayoutInflater.from(context).inflate(R.layout.activity_settings, null);
    info.fadl.mamalauncher.ui.NotificationAccessUi.unavailable(view);
    assertEquals(View.GONE, view.findViewById(R.id.btn_notification_access).getVisibility());
    assertEquals(View.GONE, view.findViewById(R.id.notification_restricted_help).getVisibility());
    assertEquals(context.getString(R.string.notification_access_not_in_build),
        ((TextView) view.findViewById(R.id.perm_notification_status)).getText().toString());
  }
}
