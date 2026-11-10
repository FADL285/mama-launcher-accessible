package info.fadl.mamalauncher.ui;

import android.view.*;
import android.widget.*;
import app.cash.paparazzi.*;
import org.junit.*;

public class BadgeRenderTest {
  private static DeviceConfig device() {
    DeviceConfig d = DeviceConfig.NEXUS_5;
    return new DeviceConfig(
        1960,
        720,
        271,
        271,
        d.getOrientation(),
        com.android.resources.UiMode.NORMAL,
        d.getNightMode(),
        com.android.resources.Density.DPI_280,
        1.5f,
        com.android.resources.LayoutDirection.RTL,
        "ar",
        d.getRatio(),
        d.getSize(),
        d.getKeyboard(),
        d.getTouchScreen(),
        d.getKeyboardState(),
        d.getSoftButtons(),
        d.getNavigation(),
        d.getScreenRound(),
        d.getReleased());
  }

  @Rule
  public final Paparazzi paparazzi =
      new Paparazzi(EnvironmentKt.detectEnvironment(), device(), "MamaTheme");

  android.content.Context context(boolean contrast) {
    return new ContextThemeWrapper(
        paparazzi.getContext(), contrast ? R.style.MamaTheme_Contrast : R.style.MamaTheme);
  }

  void marks(boolean contrast) {
    LinearLayout column = new LinearLayout(context(contrast));
    column.setOrientation(1);
    column.setPadding(8, 8, 8, 8);
    column.setBackgroundColor(contrast ? 0xFF000000 : 0xFFF9F8F5);
    int[] values = {-2, 0, 1, 9, 10, 100};
    for (int i = 0; i < values.length; i += 2) {
      LinearLayout row = new LinearLayout(column.getContext());
      for (int j = i; j < i + 2; j++) {
        boolean phone = j >= 2;
        View tile =
            UiBinder.buildHomeTile(
                column.getContext(),
                phone ? "الهاتف" : "تطبيق الإنترنت من سامسونج",
                phone
                    ? column.getContext().getDrawable(R.drawable.ic_call)
                    : new android.graphics.drawable.ColorDrawable(0xFF3075B5),
                j == 0 ? 0 : R.drawable.chip_green,
                contrast,
                null);
        UiBinder.bindHomeBadge(tile, values[j], phone ? "مكالمات فائتة" : "فيه إشعارات");
        row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
      }
      column.addView(row);
    }
    paparazzi.snapshot(column, contrast ? "marks-contrast-largest" : "marks-normal-largest");
  }

  @Test
  public void badgesNormal() {
    marks(false);
  }

  @Test
  public void badgesContrast() {
    marks(true);
  }
}
