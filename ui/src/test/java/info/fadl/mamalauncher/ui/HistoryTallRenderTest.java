package info.fadl.mamalauncher.ui;

import android.view.*;
import android.widget.*;
import app.cash.paparazzi.*;
import org.junit.*;

public class HistoryTallRenderTest {
  private static DeviceConfig device() {
    DeviceConfig d = DeviceConfig.NEXUS_5;
    return new DeviceConfig(
        d.getScreenHeight(),
        d.getScreenWidth(),
        d.getXdpi(),
        d.getYdpi(),
        d.getOrientation(),
        com.android.resources.UiMode.NORMAL,
        d.getNightMode(),
        d.getDensity(),
        d.getFontScale(),
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

  @Test
  public void completeRecordLabelsInOrdinaryWindow() {
    View v = paparazzi.inflate(R.layout.activity_phone);
    v.findViewById(R.id.phone_contacts_panel).setVisibility(View.GONE);
    v.findViewById(R.id.phone_history_panel).setVisibility(View.VISIBLE);
    v.findViewById(R.id.history_state).setVisibility(View.GONE);
    PhoneUi.select(v, R.id.btn_history_all, true);
    PhoneUi.select(v, R.id.btn_history_missed, false);
    PhoneUi.select(v, R.id.btn_tab_contacts, false);
    PhoneUi.select(v, R.id.btn_tab_history, true);
    ListView list = v.findViewById(R.id.history_list);
    list.setVisibility(View.VISIBLE);
    list.setAdapter(
        new BaseAdapter() {
          public int getCount() {
            return 3;
          }

          public Object getItem(int p) {
            return p;
          }

          public long getItemId(int p) {
            return p;
          }

          public View getView(int p, View old, ViewGroup parent) {
            CallLogRow row = new CallLogRow(parent.getContext());
            row.bind(
                new String[] {"اسم تجريبي", "01000000000", "رقم مخفي"}[p],
                p == 0 ? "01000000001" : "",
                new String[] {"مكالمة فائتة", "مكالمة واردة", "مكالمة صادرة"}[p]
                    + " · ١٠/١١/٢٠٢٦، ١٠:٣٠ ص");
            return row;
          }
        });
    paparazzi.snapshot(v, "history-normal-records");
  }

  @Test
  public void internationalNumberThenRecycledArabicName() {
    android.widget.LinearLayout column = new android.widget.LinearLayout(paparazzi.getContext());
    column.setOrientation(1);
    column.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    column.setBackgroundColor(0xFFF9F8F5);
    CallLogRow numeric = new CallLogRow(column.getContext());
    numeric.bind("+201012345678", "", "مكالمة فائتة · ١٠/١١/٢٠٢٦", true);
    column.addView(numeric);
    CallLogRow recycled = new CallLogRow(column.getContext());
    recycled.bind("+201012345678", "", "", true);
    recycled.bind("اسم تجريبي", "+201012345678", "مكالمة واردة · ١٠/١١/٢٠٢٦", false);
    column.addView(recycled);
    paparazzi.snapshot(column, "history-international-and-recycled-name");
  }
}
