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
 * only through the PIN / device-lock gate. Landing menu opens one section
 * within this Activity, preserving the authorized session. Sections: home-button picker
 * (chosen apps, in the setter's order), sound (auto-speak + TTS health),
 * permissions (status + system deep link), accessibility (font, contrast,
 * touch guard, vibration), protection (change PIN, device-lock toggle).
 */
public class SettingsActivity extends ProtectedModeActivity {

    private int currentSection = -1;
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean ttsReady;
    private TextView ttsStatus;
    private Button ttsTest;
    private Button ttsData;
    private String requestedEngine;
    private boolean refreshSpeechOnReturn;
    private int speechGeneration;

    private final List<String> selected = new ArrayList<>();
    private List<String[]> installed = new ArrayList<>(); // [pkg, label], alphabetical

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = Prefs.get(this);
        selected.addAll(AppSelection.parse(prefs.getString(AppSelection.PREF_KEY, null)));

        guard(findViewById(R.id.btn_settings_back), this::onBackPressed);
        for(int i=0;i<info.fadl.mamalauncher.ui.SettingsSections.BUTTONS.length;i++) {
            final int section=i;
            guard(findViewById(info.fadl.mamalauncher.ui.SettingsSections.BUTTONS[i]), () -> showSection(section));
        }
        showSection(savedInstanceState == null ? -1 : savedInstanceState.getInt("settings.section", -1));
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

    private void showSection(int section) {
        currentSection = section >= 0 && section < info.fadl.mamalauncher.ui.SettingsSections.PANELS.length ? section : -1;
        info.fadl.mamalauncher.ui.SettingsSections.show(findViewById(android.R.id.content), currentSection);
    }

    @Override public void onBackPressed() {
        if (currentSection != -1) showSection(-1);
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("settings.section", currentSection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        if (!SessionGate.isValid()) {
            // Any return to a locked settings screen goes back through the gate.
            startActivity(PinGateActivity.intentForEntry(this));
            finish();
            return;
        }
        SessionGate.clearExternal(); // the sanctioned leave was consumed; next leave locks
        updatePermissionRows();
        if (refreshSpeechOnReturn) { refreshSpeechOnReturn=false; initTts(); }
        else if (tts != null) refreshTtsHealth();
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
        findViewById(R.id.btn_tts_engine).setOnClickListener(v -> chooseEngine());
        ttsData.setOnClickListener(v -> {
            try {
                SessionGate.expectExternal(); // TTS installer is a sanctioned system screen
                String engine=requestedEngine;
                if(engine==null || !SpeechEngineChoice.installed(this).containsKey(engine)) {
                    SessionGate.clearExternal();chooseEngine();return;
                }
                refreshSpeechOnReturn=true;
                startActivity(SpeechEngineChoice.installIntent(engine));
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
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (ResolveInfo ri : found) {
                String pkg = ri.activityInfo.packageName;
                // Picker chooses apps, not arbitrary entry activities. Show
                // one package-labelled default shortcut per app, so there is
                // no misleading choice of an activity we cannot persist.
                if (pkg.equals(getPackageName()) || !seen.add(pkg)
                        || pm.getLaunchIntentForPackage(pkg) == null) continue;
                apps.add(new String[]{pkg, ri.activityInfo.applicationInfo.loadLabel(pm).toString()});
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

    private void chooseEngine() {
        java.util.Map<String,String> engines=SpeechEngineChoice.installed(this);
        if(engines.isEmpty()) { ttsStatus.setText(R.string.tts_no_engines);return; }
        String[] packages=engines.keySet().toArray(new String[0]);
        String[] labels=engines.values().toArray(new String[0]);
        new android.app.AlertDialog.Builder(this).setTitle(R.string.tts_choose_engine)
            .setItems(labels,(dialog,index)-> {
                if(protectedModeEnded() || isFinishing())return;
                prefs.edit().putString(SpeechEngineChoice.KEY,packages[index]).apply();
                initTts();
            }).setNegativeButton(android.R.string.cancel,null).show();
    }

    private void initTts() {
        final int generation=++speechGeneration;
        if(tts!=null) {tts.stop();tts.shutdown();}
        ttsReady=false;ttsTest.setEnabled(false);ttsStatus.setText(R.string.tts_checking);
        requestedEngine=SpeechEngineChoice.requested(this);
        java.util.Map<String,String> engines=SpeechEngineChoice.installed(this);
        String label=requestedEngine==null?getString(R.string.tts_engine_default):engines.get(requestedEngine);
        ((TextView)findViewById(R.id.tts_engine_label)).setText(getString(R.string.tts_engine_selected,label==null?requestedEngine:label));
        // A removed explicit choice must not silently switch to a different vendor.
        if(Prefs.get(this).contains(SpeechEngineChoice.KEY) && !engines.containsKey(requestedEngine)) {
            ttsStatus.setText(R.string.tts_no_engines);ttsData.setVisibility(View.GONE);return;
        }
        tts=new TextToSpeech(getApplicationContext(),status -> runOnUiThread(()-> {
            if(generation!=speechGeneration || isDestroyed() || isFinishing())return;
            ttsReady=status==TextToSpeech.SUCCESS;
            if(!ttsReady) {ttsStatus.setText(R.string.tts_missing);ttsData.setVisibility(View.VISIBLE);return;}
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { update(R.string.tts_test_ok); }
                @Override public void onError(String id) { update(R.string.tts_test_failed); }
                @Override public void onError(String id,int error) {
                    update(error==TextToSpeech.ERROR_NOT_INSTALLED_YET?R.string.tts_preparing:R.string.tts_test_failed);
                }
                private void update(int message) {runOnUiThread(()-> {
                    if(generation==speechGeneration && !isFinishing() && !isDestroyed())ttsStatus.setText(message);
                });}
            });
            refreshTtsHealth();
        }),requestedEngine);
    }

    private void refreshTtsHealth() {
        if(!ttsReady || tts==null)return;
        // setLanguage is the official engine-supported automatic voice download
        // trigger. Never use a different-language fallback as a successful test.
        int result=tts.setLanguage(new Locale("ar"));
        ArabicSpeechState.State state=ArabicSpeechState.from(result,tts.getVoice());
        ttsStatus.setText(state==ArabicSpeechState.State.PREPARING?R.string.tts_preparing:
            state==ArabicSpeechState.State.READY?R.string.tts_ready:
            state==ArabicSpeechState.State.UNSUPPORTED?R.string.tts_unsupported:R.string.tts_arabic_missing);
        ttsData.setVisibility(state==ArabicSpeechState.State.READY?View.GONE:View.VISIBLE);
        // Allow another explicit try to refresh after an engine download finishes.
        ttsTest.setEnabled(true);
    }

    private void testSpeak() {
        if(!ttsReady)return;
        refreshTtsHealth();
        if(ArabicSpeechState.from(tts.setLanguage(new Locale("ar")),tts.getVoice())!=ArabicSpeechState.State.READY)return;
        ttsStatus.setText(R.string.tts_test_started);
        if(tts.speak(getString(R.string.app_name),TextToSpeech.QUEUE_FLUSH,null,"settings-test")==TextToSpeech.ERROR)
            ttsStatus.setText(R.string.tts_test_failed);
    }

    @Override
    protected void onDestroy() {
        speechGeneration++;
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
