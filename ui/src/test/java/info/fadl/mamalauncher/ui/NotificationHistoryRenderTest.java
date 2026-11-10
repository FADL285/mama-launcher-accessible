package info.fadl.mamalauncher.ui;

import static org.junit.Assert.*;

import android.view.*;
import android.widget.*;
import app.cash.paparazzi.*;
import org.junit.*;

/** Synthetic pixels from the runtime Views, not handset compatibility evidence. */
public class NotificationHistoryRenderTest {
  private static DeviceConfig device() {
    DeviceConfig d = DeviceConfig.NEXUS_5;
    return new DeviceConfig(
        1000,
        640,
        271,
        271,
        d.getOrientation(),
        com.android.resources.UiMode.NORMAL,
        d.getNightMode(),
        com.android.resources.Density.XHIGH,
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

  void measure(View v) {
    v.measure(
        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
    v.layout(0, 0, 640, 1000);
  }

  View phone(boolean contrast) {
    android.content.Context c = context(contrast);
    View v = LayoutInflater.from(c).inflate(R.layout.activity_phone, null);
    PageHeader.title(v, R.string.tile_phone);
    v.setBackgroundColor(contrast ? 0xFF000000 : 0xFFF9F8F5);
    v.findViewById(R.id.phone_contacts_panel).setVisibility(View.GONE);
    v.findViewById(R.id.phone_history_panel).setVisibility(View.VISIBLE);
    v.findViewById(R.id.history_filters).setVisibility(View.GONE);
    PhoneUi.select(v, R.id.btn_tab_history, true);
    PhoneUi.select(v, R.id.btn_tab_contacts, false);
    PhoneUi.select(v, R.id.btn_history_all, true);
    PhoneUi.select(v, R.id.btn_history_missed, false);
    return v;
  }

  void footerVisible(View v) {
    measure(v);
    android.graphics.Rect r = new android.graphics.Rect();
    for (int id : new int[] {R.id.btn_dialpad, R.id.btn_tab_contacts, R.id.btn_tab_history}) {
      View button = v.findViewById(id);
      assertTrue(button.getGlobalVisibleRect(r));
      assertEquals(button.getHeight(), r.height());
    }
  }

  @Test
  public void historyPermissionLargestShort() {
    View v = phone(false);
    footerVisible(v);
    paparazzi.snapshot(v, "history-permission-short");
  }

  @Test
  public void historyErrorContrast() {
    View v = phone(true);
    ((TextView) v.findViewById(R.id.history_message)).setText(R.string.history_failed);
    v.findViewById(R.id.btn_allow_history).setVisibility(View.GONE);
    v.findViewById(R.id.btn_history_not_now).setVisibility(View.GONE);
    v.findViewById(R.id.btn_history_retry).setVisibility(View.VISIBLE);
    footerVisible(v);
    paparazzi.snapshot(v, "history-error-contrast");
  }

  @Test
  public void historyRowsExactNumberAndPrivate() {
    View v = phone(false);
    v.findViewById(R.id.history_state).setVisibility(View.GONE);
    v.findViewById(R.id.history_filters).setVisibility(View.VISIBLE);
    ListView list = v.findViewById(R.id.history_list);
    list.setVisibility(View.VISIBLE);
    list.setAdapter(
        new BaseAdapter() {
          String[] names = {"اسم تجريبي", "01000000000", "رقم مخفي"};

          public int getCount() {
            return 3;
          }

          public Object getItem(int p) {
            return names[p];
          }

          public long getItemId(int p) {
            return p;
          }

          public View getView(int p, View old, ViewGroup parent) {
            CallLogRow row = new CallLogRow(parent.getContext());
            row.bind(
                names[p],
                p == 0 ? "01000000001" : "",
                p == 0
                    ? "مكالمة فائتة · ١٠/١١/٢٠٢٦، ١٠:٣٠ ص"
                    : "مكالمة واردة · ١٠/١١/٢٠٢٦، ٩:١٠ ص");
            return row;
          }
        });
    footerVisible(v);
    paparazzi.snapshot(v, "history-rows-short");
  }
}
