package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.LinearLayout;
import app.cash.paparazzi.*;
import org.junit.*;

public class CallDirectionRenderTest {
  private static DeviceConfig device() {
    DeviceConfig d = DeviceConfig.NEXUS_5;
    return new DeviceConfig(d.getScreenHeight(), d.getScreenWidth(), d.getXdpi(), d.getYdpi(),
        d.getOrientation(), com.android.resources.UiMode.NORMAL, d.getNightMode(), d.getDensity(),
        d.getFontScale(), com.android.resources.LayoutDirection.RTL, "ar", d.getRatio(), d.getSize(),
        d.getKeyboard(), d.getTouchScreen(), d.getKeyboardState(), d.getSoftButtons(),
        d.getNavigation(), d.getScreenRound(), d.getReleased());
  }
  @Rule public final Paparazzi paparazzi = new Paparazzi(
      EnvironmentKt.detectEnvironment(), device(), "MamaTheme");

  @Test public void allCallDirectionsWithWords() {
    android.widget.ScrollView scroll = new android.widget.ScrollView(paparazzi.getContext());
    scroll.addView(rows(paparazzi.getContext()));
    paparazzi.snapshot(scroll, "call-directions-rtl");
  }

  @Test public void unknownTypeClearsRecycledArrow() {
    CallLogRow row = new CallLogRow(paparazzi.getContext());
    row.bind("اسم تجريبي", "", "فائتة", false, CallDirectionView.Direction.MISSED);
    row.bind("رقم مخفي", "", "نوع آخر · ١١/١١/٢٠٢٦", false, CallDirectionView.Direction.UNKNOWN);
    paparazzi.snapshot(row, "unknown-no-guessed-arrow");
  }

  @Test public void historyScreenHasBreathingRoom() {
    paparazzi.snapshot(screen(paparazzi), "history-arrows-spacing-normal");
  }

  static View screen(Paparazzi renderer) {
    View root = renderer.inflate(R.layout.activity_phone);
    root.findViewById(R.id.phone_contacts_panel).setVisibility(View.GONE);
    root.findViewById(R.id.phone_history_panel).setVisibility(View.VISIBLE);
    root.findViewById(R.id.history_state).setVisibility(View.GONE);
    PhoneUi.select(root, R.id.btn_history_all, true);
    PhoneUi.select(root, R.id.btn_history_missed, false);
    PhoneUi.select(root, R.id.btn_tab_history, true);
    PhoneUi.select(root, R.id.btn_tab_contacts, false);
    android.widget.ListView list = root.findViewById(R.id.history_list);
    list.setVisibility(View.VISIBLE);
    list.setAdapter(new android.widget.BaseAdapter() {
      final CallDirectionView.Direction[] types = {CallDirectionView.Direction.OUTGOING,
          CallDirectionView.Direction.INCOMING, CallDirectionView.Direction.MISSED,
          CallDirectionView.Direction.REJECTED};
      final String[] labels = {"صادرة", "تم الرد عليها", "فائتة", "مرفوضة"};
      public int getCount() { return 8; }
      public Object getItem(int p) { return p; }
      public long getItemId(int p) { return p; }
      public View getView(int p, View old, android.view.ViewGroup parent) {
        CallLogRow row = old instanceof CallLogRow ? (CallLogRow) old : new CallLogRow(parent.getContext());
        row.bind("اسم تجريبي", "01000000000", labels[p % 4] + " · ١١/١١/٢٠٢٦، ١٠:٣٠ ص", false, types[p % 4]);
        return row;
      }
    });
    return root;
  }

  static LinearLayout rows(android.content.Context c) {
    LinearLayout column = new LinearLayout(c); column.setOrientation(1);
    column.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    CallDirectionView.Direction[] types = {CallDirectionView.Direction.OUTGOING,
        CallDirectionView.Direction.INCOMING, CallDirectionView.Direction.MISSED,
        CallDirectionView.Direction.REJECTED, CallDirectionView.Direction.BLOCKED,
        CallDirectionView.Direction.UNKNOWN};
    String[] labels = {"صادرة", "تم الرد عليها", "فائتة", "مرفوضة", "محظورة", "نوع آخر"};
    for (int i = 0; i < types.length; i++) {
      CallLogRow row = new CallLogRow(c);
      row.bind("اسم تجريبي", "", labels[i] + " · ١١/١١/٢٠٢٦، ١٠:٣٠ ص", false, types[i]);
      column.addView(row);
    }
    return column;
  }
}
