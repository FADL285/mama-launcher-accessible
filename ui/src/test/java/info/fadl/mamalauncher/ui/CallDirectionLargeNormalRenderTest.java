package info.fadl.mamalauncher.ui;

import app.cash.paparazzi.*;
import org.junit.*;

public class CallDirectionLargeNormalRenderTest {
  @Rule public final Paparazzi paparazzi = new Paparazzi(
      EnvironmentKt.detectEnvironment(), CallDirectionLargeRenderTest.device(), "MamaTheme");

  @Test public void historyScreenLargestFontNormal() {
    paparazzi.snapshot(CallDirectionRenderTest.screen(paparazzi), "history-arrows-spacing-largest-normal");
  }
  @Test public void contactsTabLargestFontBottomEdge() {
    android.view.View root = CallDirectionRenderTest.screen(paparazzi);
    PhoneUi.select(root, R.id.btn_tab_contacts, true);
    PhoneUi.select(root, R.id.btn_tab_history, false);
    paparazzi.snapshot(root, "phone-tabs-contacts-selected-largest");
  }
}
