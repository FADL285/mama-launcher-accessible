package info.fadl.mamalauncher.ui;

import android.view.*;
import android.widget.*;
import app.cash.paparazzi.Paparazzi;
import org.junit.*;

/** T-11: PIN gate and Settings menu top under MamaTheme.Contrast (layout renders, not handset captures). */
public class PinGateSettingsContrastRenderTest {
 private static app.cash.paparazzi.DeviceConfig device() {
  app.cash.paparazzi.DeviceConfig d=app.cash.paparazzi.DeviceConfig.NEXUS_5;
  return new app.cash.paparazzi.DeviceConfig(1520,720,271,271,d.getOrientation(),com.android.resources.UiMode.NORMAL,d.getNightMode(),com.android.resources.Density.DPI_280,1.5f,com.android.resources.LayoutDirection.RTL,"ar",d.getRatio(),d.getSize(),d.getKeyboard(),d.getTouchScreen(),d.getKeyboardState(),d.getSoftButtons(),d.getNavigation(),d.getScreenRound(),d.getReleased());
 }
 @Rule public final Paparazzi paparazzi=new Paparazzi(app.cash.paparazzi.EnvironmentKt.detectEnvironment(),device(),"MamaTheme.Contrast");
 @Test public void pinGateContrast() {
  View v=paparazzi.inflate(R.layout.activity_pingate);v.setBackgroundColor(android.graphics.Color.BLACK);
  ((TextView)v.findViewById(R.id.pin_error)).setText(R.string.pingate_wrong);
  paparazzi.snapshot(v,"contrast-pingate");
 }
 @Test public void settingsMenuTopContrast() {
  View v=paparazzi.inflate(R.layout.activity_settings);v.setBackgroundColor(android.graphics.Color.BLACK);
  paparazzi.snapshot(v,"contrast-settings-menu-top");
 }
}
