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

    void loadApps() {
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
        secondary.clear();secondary.addAll(AppSelection.parse(prefs.getString(AppSelection.SECONDARY_KEY,null)));
        secondary.retainAll(installedPkgs);secondary.removeAll(selected);
        saveSelection();
        rebuildPicker();
    }

    private final List<String> secondary=new ArrayList<>();
    private void rebuildPicker() {
        findViewById(R.id.apps_loading).setVisibility(View.GONE);
        LinearLayout box = findViewById(R.id.settings_apps_list);
        box.removeAllViews();
        TextView hint=new TextView(this);hint.setText(R.string.apps_drag_hint);hint.setTextSize(18);hint.setTextColor(getResources().getColor(R.color.text_main));hint.setPadding(8,8,8,16);box.addView(hint);
        addGroup(box,selected,getString(R.string.apps_home));
        addGroup(box,secondary,getString(R.string.apps_folder));
        for(String[] app:installed)if(!selected.contains(app[0]) && !secondary.contains(app[0]))box.addView(pickerRow(app,null,-1));
    }
    private void addGroup(LinearLayout box,List<String> group,String label) {
        TextView heading=new TextView(this);heading.setText(label);heading.setTextSize(24);heading.setTextColor(getResources().getColor(R.color.text_main));box.addView(heading);
        for(int i=0;i<group.size();i++){String[] app=findApp(group.get(i));if(app!=null)box.addView(pickerRow(app,group,i));}
    }
    private View pickerRow(String[] app,List<String> group,int position) {
        float d=getResources().getDisplayMetrics().density;
        LinearLayout outer=info.fadl.mamalauncher.ui.AppPickerUi.row(this,app[1],group==selected?0:group==secondary?1:2);
        android.widget.Button location=outer.findViewWithTag("app_place");
        location.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle(app[1])
            .setSingleChoiceItems(new String[]{getString(R.string.apps_home),getString(R.string.apps_folder),getString(R.string.apps_hidden)},group==selected?0:group==secondary?1:2,(dialog,which)-> {
                selected.remove(app[0]);secondary.remove(app[0]);if(which==0)selected.add(app[0]);else if(which==1)secondary.add(app[0]);saveSelection();rebuildPicker();dialog.dismiss();
            }).setNegativeButton(android.R.string.cancel,null).show());
        if(group!=null) {
            TextView handle=outer.findViewWithTag("app_handle");
            handle.setOnLongClickListener(v->v.startDragAndDrop(android.content.ClipData.newPlainText("app",app[0]),new View.DragShadowBuilder(outer),app[0],0));
            final float[] start={0,0};final boolean[] dragging={false};
            handle.setOnTouchListener((v,event)->{
                switch(event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:start[0]=event.getX();start[1]=event.getY();dragging[0]=false;v.getParent().requestDisallowInterceptTouchEvent(true);return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if(!dragging[0] && Math.hypot(event.getX()-start[0],event.getY()-start[1])>android.view.ViewConfiguration.get(this).getScaledTouchSlop()) {
                            dragging[0]=v.startDragAndDrop(android.content.ClipData.newPlainText("app",app[0]),new View.DragShadowBuilder(outer),app[0],0);
                        }return true;
                    case android.view.MotionEvent.ACTION_UP:if(!dragging[0])v.performClick();v.getParent().requestDisallowInterceptTouchEvent(false);return true;
                    case android.view.MotionEvent.ACTION_CANCEL:v.getParent().requestDisallowInterceptTouchEvent(false);return true;
                    default:return true;
                }
            });
            handle.setOnClickListener(v->{String[] positions=new String[group.size()];for(int i=0;i<positions.length;i++)positions[i]=(i+1)+" · "+findApp(group.get(i))[1];
                new android.app.AlertDialog.Builder(this).setTitle(R.string.app_move_title).setSingleChoiceItems(positions,position,(dialog,target)->{reorder(group,app[0],target);dialog.dismiss();}).setNegativeButton(android.R.string.cancel,null).show();});
            outer.setOnDragListener((v,event)->{
                Object dragged=event.getLocalState();if(!(dragged instanceof String) || !group.contains(dragged))return false;
                switch(event.getAction()) {
                    case android.view.DragEvent.ACTION_DRAG_STARTED:return true;
                    case android.view.DragEvent.ACTION_DRAG_ENTERED:v.setAlpha(0.55f);return true;
                    case android.view.DragEvent.ACTION_DRAG_EXITED:v.setAlpha(1f);return true;
                    case android.view.DragEvent.ACTION_DRAG_LOCATION:
                        android.widget.ScrollView scroll=findViewById(R.id.settings_scroll);
                        if(scroll!=null){int[] xy=new int[2];v.getLocationOnScreen(xy);int[] sy=new int[2];scroll.getLocationOnScreen(sy);float y=xy[1]+event.getY()-sy[1];if(y<80*d)scroll.smoothScrollBy(0,-(int)(28*d));else if(y>scroll.getHeight()-80*d)scroll.smoothScrollBy(0,(int)(28*d));}return true;
                    case android.view.DragEvent.ACTION_DROP:reorder(group,(String)dragged,position);return true;
                    case android.view.DragEvent.ACTION_DRAG_ENDED:v.setAlpha(1f);return true;
                    default:return true;
                }
            });
        }
        return outer;
    }
    void reorder(List<String> group,String pkg,int target) {
        List<String> moved=AppSelection.moveTo(group,group.indexOf(pkg),target);group.clear();group.addAll(moved);saveSelection();rebuildPicker();
    }

    private String[] findApp(String pkg) {
        for (String[] a : installed) if (a[0].equals(pkg)) return a;
        return null;
    }

    private void saveSelection() {
        prefs.edit().putString(AppSelection.PREF_KEY, AppSelection.join(selected)).putString(AppSelection.SECONDARY_KEY,AppSelection.join(secondary)).apply();
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
        tts=new TextToSpeech(getApplicationContext(),status -> new android.os.Handler(android.os.Looper.getMainLooper()).post(()-> {
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
        ArabicSpeechState.State state=ArabicVoicePolicy.prepare(tts);
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
        if(ArabicVoicePolicy.prepare(tts)!=ArabicSpeechState.State.READY)return;
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
