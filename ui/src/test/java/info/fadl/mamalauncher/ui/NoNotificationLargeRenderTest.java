package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.ScrollView;
import app.cash.paparazzi.*;
import org.junit.*;

public class NoNotificationLargeRenderTest {
  private static DeviceConfig device() {
    DeviceConfig d = DeviceConfig.NEXUS_5;
    return new DeviceConfig(1520, 720, 271, 271, d.getOrientation(),
        com.android.resources.UiMode.NORMAL, d.getNightMode(),
        com.android.resources.Density.DPI_280, 1.5f,
        com.android.resources.LayoutDirection.RTL, "ar", d.getRatio(), d.getSize(),
        d.getKeyboard(), d.getTouchScreen(), d.getKeyboardState(), d.getSoftButtons(),
        d.getNavigation(), d.getScreenRound(), d.getReleased());
  }
  @Rule public final Paparazzi paparazzi = new Paparazzi(
      EnvironmentKt.detectEnvironment(), device(), "MamaTheme.Contrast");

  @Test public void disclosureAtLargestTextContrastScrollsToBottom() {
    View root = paparazzi.inflate(R.layout.activity_settings);
    SettingsSections.show(root, 3);
    NotificationAccessUi.unavailable(root);
    root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 400, 800);
    ((ScrollView) root.findViewById(R.id.settings_scroll)).fullScroll(View.FOCUS_DOWN);
    paparazzi.snapshot(root, "no-notification-largest-contrast-bottom");
  }
}
