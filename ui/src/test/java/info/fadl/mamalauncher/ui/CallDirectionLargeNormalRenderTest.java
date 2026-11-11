package info.fadl.mamalauncher.ui;

import app.cash.paparazzi.*;
import org.junit.*;

public class CallDirectionLargeNormalRenderTest {
  @Rule public final Paparazzi paparazzi = new Paparazzi(
      EnvironmentKt.detectEnvironment(), CallDirectionLargeRenderTest.device(), "MamaTheme");

  @Test public void historyScreenLargestFontNormal() {
    paparazzi.snapshot(CallDirectionRenderTest.screen(paparazzi), "history-arrows-spacing-largest-normal");
  }
}
