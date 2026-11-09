package info.fadl.mamalauncher.ui;

import android.view.View;

import app.cash.paparazzi.Paparazzi;

import org.junit.Rule;
import org.junit.Test;

/** The trial phone is a Samsung A10s (720x1520, ~271dpi). The PIN pad must
 *  fit fully - every key visible without scrolling - even at the largest
 *  font scale (1.5). */
public class PinGateFitTest {

    private static app.cash.paparazzi.DeviceConfig a10sLargestText() {
        app.cash.paparazzi.DeviceConfig d = app.cash.paparazzi.DeviceConfig.NEXUS_5;
        return new app.cash.paparazzi.DeviceConfig(
                1520, 720, 271, 271,
                d.getOrientation(), com.android.resources.UiMode.NORMAL, d.getNightMode(),
                com.android.resources.Density.DPI_280, 1.5f,
                com.android.resources.LayoutDirection.RTL,
                "ar", d.getRatio(), d.getSize(), d.getKeyboard(), d.getTouchScreen(),
                d.getKeyboardState(), d.getSoftButtons(), d.getNavigation(),
                d.getScreenRound(), d.getReleased());
    }

    @Rule
    public final Paparazzi paparazzi = new Paparazzi(
            app.cash.paparazzi.EnvironmentKt.detectEnvironment(), a10sLargestText(), "MamaTheme");

    @Test
    public void pinGateFitsA10sLargestText() {
        View v = paparazzi.inflate(R.layout.activity_pingate);
        paparazzi.snapshot(v, "pin-gate-a10s-largest");
    }

    @Test
    public void dialpadA10sLargestText() {
        View v = paparazzi.inflate(R.layout.activity_dialpad);
        // Full 11-digit Egyptian mobile number: every digit must stay visible.
        ((android.widget.TextView) v.findViewById(R.id.dial_display)).setText("0100 000 0000");
        paparazzi.snapshot(v, "dialpad-a10s-largest");
    }

    @Test
    public void dialpadA10sInternationalLength() {
        View v = paparazzi.inflate(R.layout.activity_dialpad);
        // International-length input must not truncate or lose digits.
        ((android.widget.TextView) v.findViewById(R.id.dial_display)).setText("+2010 0000 0000");
        paparazzi.snapshot(v, "dialpad-a10s-international");
    }

    @Test
    public void pinGateLockedOutShowsReachableRecovery() {
        View v = paparazzi.inflate(R.layout.activity_pingate);
        ((android.widget.TextView) v.findViewById(R.id.pin_error))
                .setText("محاولات كتير غلط - استنى شوية وجرب تاني");
        v.findViewById(R.id.btn_device_lock).setVisibility(View.VISIBLE);
        paparazzi.snapshot(v, "pin-gate-locked-recovery");
    }

    @Test
    public void everydayHomeA10sLargestText() {
        View v = paparazzi.inflate(R.layout.activity_main);
        ((android.widget.TextView) v.findViewById(R.id.home_clock)).setText("1:00");
        ((android.widget.TextView) v.findViewById(R.id.home_date)).setText("الاثنين ٩ نوفمبر");
        android.widget.LinearLayout box=v.findViewById(R.id.tiles_container);
        android.widget.LinearLayout row=new android.widget.LinearLayout(v.getContext());
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        for(String label:new String[]{"الهاتف","تطبيق العائلة للرسائل"}) {
            View tile=UiBinder.buildHomeTile(v.getContext(),label,
                v.getResources().getDrawable(R.drawable.ic_call),R.drawable.chip_green,false,null);
            android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(
                0,android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,1f);
            int m=(int)(6*v.getResources().getDisplayMetrics().density);
            lp.setMargins(m,m,m,m);row.addView(tile,lp);
        }
        box.addView(row);
        paparazzi.snapshot(v, "home-a10s-largest");
    }

    @Test public void settingsLoadingDoesNotMoveStaticControls() {
        View v=paparazzi.inflate(R.layout.activity_settings);
        paparazzi.snapshot(v,"settings-before-discovery");
        android.widget.LinearLayout box=v.findViewById(R.id.settings_apps_list);
        v.findViewById(R.id.apps_loading).setVisibility(View.GONE);
        for(int i=0;i<30;i++) {
            android.widget.CheckBox cb=new android.widget.CheckBox(v.getContext());
            cb.setText("تطبيق تجريبي "+i); cb.setTextSize(20);
            box.addView(cb);
        }
        paparazzi.snapshot(v,"settings-after-discovery");
        // Actual ScrollView at the bottom: picker remains reachable after all
        // static settings, even with a long installed-app list.
        android.widget.ScrollView scroll=(android.widget.ScrollView)v;
        scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
        paparazzi.snapshot(v,"settings-picker-scrolled-bottom");
    }
}
