package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.cash.paparazzi.Paparazzi;

import org.junit.Rule;
import org.junit.Test;

/** Separate settings sections at normal device text scale. */
public class SettingsFullTest {

    private static app.cash.paparazzi.DeviceConfig arabicDevice() {
        app.cash.paparazzi.DeviceConfig d = app.cash.paparazzi.DeviceConfig.NEXUS_5;
        return new app.cash.paparazzi.DeviceConfig(
                d.getScreenHeight(), d.getScreenWidth(), d.getXdpi(), d.getYdpi(),
                d.getOrientation(), com.android.resources.UiMode.NORMAL, d.getNightMode(),
                d.getDensity(), d.getFontScale(), com.android.resources.LayoutDirection.RTL,
                "ar", d.getRatio(), d.getSize(), d.getKeyboard(), d.getTouchScreen(),
                d.getKeyboardState(), d.getSoftButtons(), d.getNavigation(),
                d.getScreenRound(), d.getReleased());
    }

    @Rule
    public final Paparazzi paparazzi = new Paparazzi(
            app.cash.paparazzi.EnvironmentKt.detectEnvironment(), arabicDevice(), "MamaTheme");

    @Test
    public void settingsPickerPopulated() {
        View v = paparazzi.inflate(R.layout.activity_settings);
        SettingsSections.show(v,0);
        v.findViewById(R.id.apps_loading).setVisibility(View.GONE);
        LinearLayout box = v.findViewById(R.id.settings_apps_list);
        addPickerRow(v, box, "يوتيوب", true, false, true);
        addPickerRow(v, box, "واتساب", true, true, true);
        addPickerRow(v, box, "جوجل مابس", true, true, false);
        addPickerRow(v, box, "فيسبوك", false, false, false);
        ((TextView) v.findViewById(R.id.perm_contacts_status)).setText("مسموح");
        ((TextView) v.findViewById(R.id.perm_call_status)).setText("مش مسموح");
        ((TextView) v.findViewById(R.id.tts_status)).setText("محرك الصوت جاهز");
        ((android.widget.Switch) v.findViewById(R.id.switch_auto_speak)).setChecked(true);
        ((android.widget.Switch) v.findViewById(R.id.switch_vibrate)).setChecked(true);
        paparazzi.snapshot(v, "settings-picker");
    }

    @Test
    public void settingsAccessibility() {
        View v = paparazzi.inflate(R.layout.activity_settings);
        v.findViewById(R.id.apps_loading).setVisibility(View.GONE);
        LinearLayout box = v.findViewById(R.id.settings_apps_list);
        addPickerRow(v, box, "يوتيوب", true, false, true);
        addPickerRow(v, box, "واتساب", true, true, true);
        addPickerRow(v, box, "جوجل مابس", true, true, false);
        addPickerRow(v, box, "فيسبوك", false, false, false);
        ((TextView) v.findViewById(R.id.perm_contacts_status)).setText("مسموح");
        ((TextView) v.findViewById(R.id.perm_call_status)).setText("مش مسموح");
        ((TextView) v.findViewById(R.id.tts_status)).setText("محرك الصوت جاهز");
        ((android.widget.Switch) v.findViewById(R.id.switch_auto_speak)).setChecked(true);
        ((android.widget.Switch) v.findViewById(R.id.switch_vibrate)).setChecked(true);
        ((TextView) v.findViewById(R.id.perm_contacts_status)).setText("مسموح");
        ((TextView) v.findViewById(R.id.perm_call_status)).setText("مسموح");
        ((TextView) v.findViewById(R.id.tts_status)).setText("محرك الصوت جاهز");
        ((android.widget.Switch) v.findViewById(R.id.switch_auto_speak)).setChecked(true);
        ((android.widget.Switch) v.findViewById(R.id.switch_vibrate)).setChecked(true);
        ((android.widget.Switch) v.findViewById(R.id.switch_touch_guard)).setChecked(true);
        SettingsSections.show(v,2);
        paparazzi.snapshot(v,"settings-accessibility");

    }

    private void addPickerRow(View root, LinearLayout box, String label,
                              boolean selected, boolean canUp, boolean canDown) {
        android.content.Context ctx = root.getContext();
        float d = root.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int) (56 * d));
        android.widget.CheckBox cb = new android.widget.CheckBox(ctx);
        cb.setText(label);
        cb.setTextSize(20f);
        cb.setTextColor(root.getResources().getColor(R.color.text_main));
        cb.setChecked(selected);
        row.addView(cb, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (selected) {
            row.addView(arrow(ctx, root, "↑", canUp));
            row.addView(arrow(ctx, root, "↓", canDown));
        }
        box.addView(row);
    }

    private View arrow(android.content.Context ctx, View root, String glyph, boolean enabled) {
        TextView b = new TextView(ctx);
        float d = root.getResources().getDisplayMetrics().density;
        b.setText(glyph);
        b.setTextSize(24f);
        b.setGravity(android.view.Gravity.CENTER);
        b.setTextColor(root.getResources().getColor(R.color.back_blue));
        b.setAlpha(enabled ? 1f : 0.25f);
        int pad = (int) (10 * d);
        b.setPadding(pad, pad, pad, pad);
        return b;
    }
}
