package info.fadl.mamalauncher;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Family-setter screen (the person preparing the phone, not mama), reached
 * only through the PIN / device-lock gate. Sections: home-button picker
 * (chosen apps, in the setter's order), sound (auto-speak + TTS health),
 * permissions (status + system deep link), accessibility (font, contrast,
 * touch guard, vibration), protection (change PIN, device-lock toggle).
 */
public class SettingsActivity extends BaseActivity {

    private SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean ttsReady;
    private TextView ttsStatus;
    private Button ttsTest;
    private Button ttsData;

    private final List<String> selected = new ArrayList<>();
    private List<String[]> installed = new ArrayList<>(); // [pkg, label], alphabetical

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = Prefs.get(this);
        selected.addAll(AppSelection.parse(prefs.getString(AppSelection.PREF_KEY, null)));

        guard(findViewById(R.id.btn_settings_back), this::finish);
        guard(findViewById(R.id.btn_open_app_settings), () -> {
            SessionGate.expectExternal(); // sanctioned system screen: survives this one leave
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        bindSoundSection();
        bindAccessibilitySection();
        bindProtectionSection();

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            findViewById(R.id.volume_hint).setVisibility(View.VISIBLE);
        }

        loadApps();
        initTts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!SessionGate.isValid()) {
            // Any return to a locked settings screen goes back through the gate.
            startActivity(PinGateActivity.intentForEntry(this));
            finish();
            return;
        }
        SessionGate.clearExternal(); // the sanctioned leave was consumed; next leave locks
        updatePermissionRows();
    }

    @Override
    protected void onStop() {
        // Back to the everyday screen, home, recents, screen off: the session
        // ends NOW, not after a timeout. Only a sanctioned system deep link
        // (app info, TTS installer) keeps it alive, exactly once.
        if (!SessionGate.externalInFlight()) {
            SessionGate.lock();
        }
        super.onStop();
    }

    // ---- sound ----

    private void bindSoundSection() {
        Switch autoSpeak = findViewById(R.id.switch_auto_speak);
        autoSpeak.setChecked(Prefs.autoSpeak(this));
        autoSpeak.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean(Prefs.AUTO_SPEAK, on).apply());

        ttsStatus = findViewById(R.id.tts_status);
        ttsTest = findViewById(R.id.btn_tts_test);
        ttsData = findViewById(R.id.btn_tts_data);
        ttsTest.setOnClickListener(v -> testSpeak());
        ttsData.setOnClickListener(v -> {
            try {
                SessionGate.expectExternal(); // TTS installer is a sanctioned system screen
                startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
            } catch (android.content.ActivityNotFoundException ignored) {
                // Nothing handled the installer: the sanctioned leave never
                // happened, so the exemption must not survive either.
                SessionGate.clearExternal();
                ttsStatus.setText(R.string.tts_missing);
            }
        });
    }

    // ---- accessibility ----

    private void bindAccessibilitySection() {
        int scale = Prefs.fontScale(this);
        guard(findViewById(R.id.btn_font_normal), () -> setFontScale(0));
        guard(findViewById(R.id.btn_font_large), () -> setFontScale(1));
        guard(findViewById(R.id.btn_font_huge), () -> setFontScale(2));
        markFont(scale);

        bindSwitch(R.id.switch_contrast, Prefs.HIGH_CONTRAST, Prefs.highContrast(this), true);
        bindSwitch(R.id.switch_touch_guard, Prefs.TOUCH_GUARD, Prefs.touchGuard(this), false);
        bindSwitch(R.id.switch_vibrate, Prefs.VIBRATE, Prefs.vibrate(this), false);
    }

    private void markFont(int scale) {
        int[] ids = {R.id.btn_font_normal, R.id.btn_font_large, R.id.btn_font_huge};
        for (int i = 0; i < ids.length; i++) {
            findViewById(ids[i]).setAlpha(i == scale ? 1f : 0.55f);
        }
    }

    private void setFontScale(int scale) {
        prefs.edit().putInt(Prefs.FONT_SCALE, scale).apply();
        markFont(scale);
        // recreate() passes through onStop; without the exemption the setter
        // would be bounced to the PIN gate for changing a font size. The
        // exemption is consumed on resume, so the next genuine leave locks.
        SessionGate.expectExternal();
        recreate(); // font scale applies through attachBaseContext
    }

    private void bindSwitch(int id, String key, boolean current, boolean recreateOnChange) {
        Switch s = findViewById(id);
        s.setChecked(current);
        s.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(key, on).apply();
            if (recreateOnChange) {
                SessionGate.expectExternal(); // internal recreation, not a departure
                recreate(); // theme applies before content view
            }
        });
    }

    // ---- protection ----

    private void bindProtectionSection() {
        guard(findViewById(R.id.btn_change_pin),
                () -> startActivity(PinGateActivity.intentForSetup(this)));

        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean deviceSecure = km != null && km.isDeviceSecure();
        Switch deviceLock = findViewById(R.id.switch_device_lock);
        deviceLock.setChecked(deviceSecure && Prefs.useDeviceLock(this));
        deviceLock.setEnabled(deviceSecure);
        if (!deviceSecure) {
            findViewById(R.id.device_lock_hint).setVisibility(View.VISIBLE);
        }
        deviceLock.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean(Prefs.USE_DEVICE_LOCK, on).apply());
    }

    // ---- permissions ----

    private void updatePermissionRows() {
        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        boolean call = checkSelfPermission(Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
        TextView contactsStatus = findViewById(R.id.perm_contacts_status);
        contactsStatus.setText(contacts ? R.string.perm_allowed : R.string.perm_denied);
        contactsStatus.setTextColor(getResources().getColor(
                contacts ? R.color.call_green : R.color.danger_text));
        TextView callStatus = findViewById(R.id.perm_call_status);
        callStatus.setText(call ? R.string.perm_allowed : R.string.perm_denied);
        callStatus.setTextColor(getResources().getColor(
                call ? R.color.call_green : R.color.danger_text));
    }

    // ---- home-button picker (installed apps only, setter-ordered) ----

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
            List<String[]> apps = new ArrayList<>();
            for (ResolveInfo ri : found) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                apps.add(new String[]{pkg, ri.loadLabel(pm).toString()});
            }
            Collections.sort(apps, Comparator.comparing(a -> a[1]));
            runOnUiThread(() -> applyDiscoveredApps(apps));
        }).start();
    }

    void applyDiscoveredApps(List<String[]> apps) {
        if (isDestroyed() || isFinishing()) return;
        installed = apps;
        List<String> installedPkgs = new ArrayList<>();
        for (String[] a : apps) installedPkgs.add(a[0]);
        selected.clear();
        selected.addAll(AppSelection.parse(prefs.getString(AppSelection.PREF_KEY, null)));
        selected.retainAll(installedPkgs);
        saveSelection();
        rebuildPicker();
    }

    private void rebuildPicker() {
        findViewById(R.id.apps_loading).setVisibility(View.GONE);
        LinearLayout box = findViewById(R.id.settings_apps_list);
        box.removeAllViews();
        // Chosen apps first, in the setter's order, with up/down controls.
        for (int i = 0; i < selected.size(); i++) {
            String[] app = findApp(selected.get(i));
            if (app != null) box.addView(pickerRow(app, true, i));
        }
        // The rest alphabetically below.
        for (String[] app : installed) {
            if (!selected.contains(app[0])) box.addView(pickerRow(app, false, -1));
        }
    }

    private View pickerRow(String[] app, boolean isSelected, int position) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int) (56 * d));

        CheckBox cb = new CheckBox(this);
        cb.setText(app[1]);
        cb.setTextSize(20f);
        cb.setTextColor(getResources().getColor(R.color.text_main));
        cb.setChecked(isSelected);
        cb.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                if (!selected.contains(app[0])) selected.add(app[0]);
            } else {
                selected.remove(app[0]);
            }
            saveSelection();
            rebuildPicker();
        });
        row.addView(cb, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (isSelected) {
            row.addView(arrowButton("↑", position > 0,
                    () -> moveSelected(position, -1)));
            row.addView(arrowButton("↓", position < selected.size() - 1,
                    () -> moveSelected(position, +1)));
        }
        return row;
    }

    private View arrowButton(String glyph, boolean enabled, Runnable action) {
        TextView b = new TextView(this);
        float d = getResources().getDisplayMetrics().density;
        b.setText(glyph);
        b.setTextSize(24f);
        b.setGravity(android.view.Gravity.CENTER);
        b.setTextColor(getResources().getColor(enabled ? R.color.back_blue : R.color.text_main));
        b.setAlpha(enabled ? 1f : 0.25f);
        int pad = (int) (10 * d);
        b.setPadding(pad, pad, pad, pad);
        if (enabled) b.setOnClickListener(v -> action.run());
        return b;
    }

    private void moveSelected(int position, int delta) {
        List<String> moved = AppSelection.move(selected, position, delta);
        selected.clear();
        selected.addAll(moved);
        saveSelection();
        rebuildPicker();
    }

    private String[] findApp(String pkg) {
        for (String[] a : installed) if (a[0].equals(pkg)) return a;
        return null;
    }

    private void saveSelection() {
        prefs.edit().putString(AppSelection.PREF_KEY, AppSelection.join(selected)).apply();
    }

    // ---- TTS health ----

    private void initTts() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) {
                ttsStatus.setText(R.string.tts_missing);
                ttsData.setVisibility(View.VISIBLE);
                return;
            }
            int ar = tts.isLanguageAvailable(new Locale("ar"));
            if (ar == TextToSpeech.LANG_MISSING_DATA
                    || ar == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsStatus.setText(R.string.tts_arabic_missing);
                ttsData.setVisibility(View.VISIBLE);
            } else {
                ttsStatus.setText(R.string.tts_ready);
            }
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) {
                    runOnUiThread(() -> ttsStatus.setText(R.string.tts_test_ok));
                }
                @Override public void onError(String id) {
                    runOnUiThread(() -> ttsStatus.setText(R.string.tts_test_failed));
                }
            });
            ttsTest.setEnabled(true);
        });
    }

    private void testSpeak() {
        if (!ttsReady) return;
        Locale ar = new Locale("ar");
        if (tts.isLanguageAvailable(ar) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(ar);
            tts.speak(getString(R.string.app_name), TextToSpeech.QUEUE_FLUSH, null, "settings-test");
        }
        // Arabic missing: do NOT test in the default voice. A "success" status
        // would be misleading - automatic name speech requires Arabic - so the
        // Arabic-missing warning stays on screen instead.
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
