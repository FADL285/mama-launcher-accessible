package info.fadl.mamalauncher.ui;

import android.view.*;
import android.widget.*;
import app.cash.paparazzi.Paparazzi;
import org.junit.*;

/** Synthetic largest-text contrast fixtures; these are layout renders, not handset captures. */
public class ContrastRegressionTest {
 private static app.cash.paparazzi.DeviceConfig device() {
  app.cash.paparazzi.DeviceConfig d=app.cash.paparazzi.DeviceConfig.NEXUS_5;
  return new app.cash.paparazzi.DeviceConfig(1520,720,271,271,d.getOrientation(),com.android.resources.UiMode.NORMAL,d.getNightMode(),com.android.resources.Density.DPI_280,1.5f,com.android.resources.LayoutDirection.RTL,"ar",d.getRatio(),d.getSize(),d.getKeyboard(),d.getTouchScreen(),d.getKeyboardState(),d.getSoftButtons(),d.getNavigation(),d.getScreenRound(),d.getReleased());
 }
 @Rule public final Paparazzi paparazzi=new Paparazzi(app.cash.paparazzi.EnvironmentKt.detectEnvironment(),device(),"MamaTheme.Contrast");
 private View dialHint(int text) {
  View v=paparazzi.inflate(R.layout.activity_dialpad);v.setBackgroundColor(android.graphics.Color.BLACK);
  ((TextView)v.findViewById(R.id.dial_display)).setText("010");v.findViewById(R.id.dial_display).setVisibility(View.VISIBLE);v.findViewById(R.id.dial_placeholder).setVisibility(View.GONE);
  TextView hint=new TextView(v.getContext());hint.setText(text);hint.setTextSize(16);hint.setTextColor(SurfaceText.color(v.getContext()));((LinearLayout)v.findViewById(R.id.dial_matches)).addView(hint);return v;
 }
 @Test public void lookupUnavailable(){paparazzi.snapshot(dialHint(R.string.dial_lookup_needed),"contrast-lookup-unavailable");}
 @Test public void lookupLoading(){paparazzi.snapshot(dialHint(R.string.dial_lookup_loading),"contrast-lookup-loading");}
 @Test public void appsEmpty() {
  View v=paparazzi.inflate(R.layout.activity_main);v.setBackgroundColor(android.graphics.Color.BLACK);v.findViewById(R.id.home_header).setVisibility(View.GONE);
  LinearLayout fixed=new LinearLayout(v.getContext());fixed.setBackgroundColor(android.graphics.Color.BLACK);fixed.setOrientation(LinearLayout.VERTICAL);int edge=(int)(12*v.getResources().getDisplayMetrics().density);fixed.setPadding(edge,edge,edge,0);
  Button back=new Button(v.getContext());back.setText(R.string.back);back.setTextSize(26);back.setBackgroundResource(R.drawable.btn_back);back.setTextColor(android.graphics.Color.WHITE);back.setMinHeight((int)(72*v.getResources().getDisplayMetrics().density));fixed.addView(back,new LinearLayout.LayoutParams(-1,-2));
  TextView title=new TextView(v.getContext());title.setText(R.string.apps_page);title.setTextSize(30);title.setGravity(Gravity.CENTER);title.setTextColor(SurfaceText.color(v.getContext()));fixed.addView(title,new LinearLayout.LayoutParams(-1,-2));fixed.addView(v,new LinearLayout.LayoutParams(-1,0,1));
  TextView empty=new TextView(v.getContext());empty.setText(R.string.apps_empty);empty.setTextSize(24);empty.setTextColor(SurfaceText.color(v.getContext()));((LinearLayout)v.findViewById(R.id.tiles_container)).addView(empty);paparazzi.snapshot(fixed,"contrast-apps-empty");
 }
 @Test public void pickerHeadingsAndLabels() {
  View v=paparazzi.inflate(R.layout.activity_settings);v.setBackgroundColor(android.graphics.Color.BLACK);SettingsSections.show(v,0);v.findViewById(R.id.apps_loading).setVisibility(View.GONE);
  LinearLayout box=v.findViewById(R.id.settings_apps_list);box.addView(AppPickerUi.heading(v.getContext(),v.getContext().getString(R.string.apps_drag_hint),18));box.addView(AppPickerUi.heading(v.getContext(),v.getContext().getString(R.string.apps_home),24));box.addView(AppPickerUi.row(v.getContext(),"تطبيق الإنترنت من سامسونج",0));paparazzi.snapshot(v,"contrast-picker-labels");
 }
}
