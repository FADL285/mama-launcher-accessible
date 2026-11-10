package info.fadl.mamalauncher.ui;

import android.view.View;
import app.cash.paparazzi.Paparazzi;
import org.junit.Rule;
import org.junit.Test;

public class NoNotificationRenderTest {
  @Rule public final Paparazzi paparazzi = new Paparazzi(
      app.cash.paparazzi.EnvironmentKt.detectEnvironment(),
      app.cash.paparazzi.DeviceConfig.NEXUS_5, "MamaTheme");

  @Test public void notificationFreePermissionDisclosure() {
    View view = paparazzi.inflate(R.layout.activity_settings);
    SettingsSections.show(view, 3);
    NotificationAccessUi.unavailable(view);
    paparazzi.snapshot(view, "notification-free-permissions");
  }
}
