package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.*;
import app.cash.paparazzi.*;
import org.junit.*;

public class CallDirectionLargeRenderTest {
  static DeviceConfig device() {
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

  @Test public void historyScreenLargestFontContrast() {
    paparazzi.snapshot(CallDirectionRenderTest.screen(paparazzi), "history-arrows-spacing-largest");
  }

  @Test public void largeContrastWordsWrapWithoutCoveringArrows() {
    ScrollView scroll = new ScrollView(paparazzi.getContext());
    LinearLayout rows = CallDirectionRenderTest.rows(paparazzi.getContext());
    scroll.addView(rows);
    paparazzi.snapshot(scroll, "call-directions-largest-contrast");
  }

  @Test public void internationalNumberAndRejectedRemainReadableInLtrWindow() {
    LinearLayout column = new LinearLayout(paparazzi.getContext());
    column.setOrientation(1); column.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    CallLogRow numeric = new CallLogRow(column.getContext());
    numeric.bind("+201012345678", "", "صادرة · ١١/١١/٢٠٢٦، ١٠:٣٠ ص", true,
        CallDirectionView.Direction.OUTGOING);
    column.addView(numeric);
    CallLogRow rejected = new CallLogRow(column.getContext());
    rejected.bind("اسم تجريبي طويل للاختبار", "+201012345678", "مرفوضة · ١١/١١/٢٠٢٦، ١٠:٣٠ ص", false,
        CallDirectionView.Direction.REJECTED);
    column.addView(rejected);
    paparazzi.snapshot(column, "call-directions-ltr-number-largest");
  }
}
