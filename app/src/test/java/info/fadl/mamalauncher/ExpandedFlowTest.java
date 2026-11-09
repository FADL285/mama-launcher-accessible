package info.fadl.mamalauncher;
import android.content.*;import android.view.*;import android.widget.*;import android.speech.tts.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;import java.util.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class ExpandedFlowTest {
 android.app.Application app;
 @Before public void reset(){app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();SessionGate.unlock();Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.READ_CONTACTS);}
 View key(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=key(((ViewGroup)v).getChildAt(i),tag);if(r!=null)return r;}return null;}
 info.fadl.mamalauncher.ui.ContactEntry contact(long id,String name,String number){return new info.fadl.mamalauncher.ui.ContactEntry(id,name,number,null,false);}
 Voice voice(String name,String locale,boolean network,int quality,boolean installing){return new Voice(name,Locale.forLanguageTag(locale),quality,300,network,installing?Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED):Collections.emptySet());}
 public static class SettingsFixture extends SettingsActivity {@Override void loadApps(){}}

 @Test public void homeSpecialItemsMigrateAndKeepUnavailableSlot(){
  assertEquals(Arrays.asList("@phone","@apps","a","b"),AppSelection.homeOrder(null,Arrays.asList("a","b")));
  assertEquals(Arrays.asList("b","@apps","a","@phone"),AppSelection.homeOrder("b,@apps,a,@phone,b,stale",Arrays.asList("a","b")));
 }
 @Test public void phoneAndFolderReorderPersistAndCannotMoveOutOfHome(){
  Prefs.get(app).edit().putString(AppSelection.PREF_KEY,"example.a").apply();SettingsFixture a=Robolectric.buildActivity(SettingsFixture.class).setup().get();a.applyDiscoveredApps(Collections.singletonList(new String[]{"example.a","اختبار"}));
  View box=a.findViewById(R.id.settings_apps_list);View phone=key(box,"app_row:@phone");assertEquals(View.GONE,key(phone,"app_place").getVisibility());key(phone,"app_handle").performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(null,2,2);
  assertEquals("@apps,example.a,@phone",Prefs.get(a).getString(AppSelection.HOME_ORDER_KEY,null));assertEquals("example.a",Prefs.get(a).getString(AppSelection.PREF_KEY,null));
  a.recreate();assertEquals(Arrays.asList("@apps","example.a","@phone"),AppSelection.homeOrder(Prefs.get(a).getString(AppSelection.HOME_ORDER_KEY,null),Arrays.asList("example.a")));
 }
 @Test public void firstGuideDismissalPersistsAndSoundShortcutStillUsesGate(){
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();assertEquals(View.VISIBLE,a.findViewById(R.id.first_use_guide).getVisibility());a.findViewById(R.id.btn_guide_sound).performClick();Intent gate=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(PinGateActivity.class.getName(),gate.getComponent().getClassName());assertTrue(gate.getBooleanExtra(SettingsActivity.EXTRA_SOUND,false));
  a.findViewById(R.id.btn_guide_done).performClick();assertEquals(View.GONE,a.findViewById(R.id.first_use_guide).getVisibility());MainActivity again=Robolectric.buildActivity(MainActivity.class).setup().get();assertEquals(View.GONE,again.findViewById(R.id.first_use_guide).getVisibility());
 }
 @Test public void soundTargetPassesThroughNewPinTwiceBeforeOpeningSettings(){
  SessionGate.lock();PinGateActivity p=Robolectric.buildActivity(PinGateActivity.class,PinGateActivity.intentForEntry(app).putExtra(SettingsActivity.EXTRA_SOUND,true)).setup().get();assertNull(Shadows.shadowOf(p).getNextStartedActivity());for(char c:"24682468".toCharArray())key(p.findViewById(R.id.pin_pad),""+c).performClick();Intent settings=Shadows.shadowOf(p).getNextStartedActivity();assertEquals(SettingsActivity.class.getName(),settings.getComponent().getClassName());assertTrue(settings.getBooleanExtra(SettingsActivity.EXTRA_SOUND,false));assertTrue(SessionGate.isValid());
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class,settings).setup().get();assertEquals(View.VISIBLE,a.findViewById(R.id.settings_section_sound).getVisibility());
 }
 @Test public void transientInitializationOffersRetryAndNeverClaimsPermanentUnsupported(){
  NameSpeaker n=new NameSpeaker();List<NameSpeaker.State> states=new ArrayList<>();n.setAvailabilityListener(new NameSpeaker.AvailabilityListener(){public void onUnavailable(){}public void onStateChanged(NameSpeaker.State s){states.add(s);}});n.init(app);n.speak("اختبار");TextToSpeech first=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();Shadows.shadowOf(first).getOnInitListener().onInit(TextToSpeech.ERROR);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(NameSpeaker.State.RETRYABLE,n.state());
  n.speak("اختبار");assertEquals(NameSpeaker.State.INITIALIZING,n.state());TextToSpeech second=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();org.robolectric.shadows.ShadowTextToSpeech.addVoice(voice("offline","ar-EG",false,300,false));Shadows.shadowOf(second).getOnInitListener().onInit(TextToSpeech.SUCCESS);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(NameSpeaker.State.READY,n.state());assertEquals("اختبار",Shadows.shadowOf(second).getLastSpokenText());n.shutdown();
 }
 @Test public void failedConfirmationKeepsRetryAndSetupWithoutCalling(){
  ConfirmCallActivity a=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,contact(1,"اختبار","01000000001"))).setup().get();assertEquals(View.INVISIBLE,a.findViewById(R.id.btn_repeat_name).getVisibility());TextToSpeech t=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();Shadows.shadowOf(t).getOnInitListener().onInit(TextToSpeech.ERROR);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(View.VISIBLE,a.findViewById(R.id.btn_speech_retry).getVisibility());assertEquals(View.VISIBLE,a.findViewById(R.id.btn_speech_setup).getVisibility());a.findViewById(R.id.btn_speech_retry).performClick();assertNull(Shadows.shadowOf(a).getNextStartedActivity());a.findViewById(R.id.btn_speech_setup).performClick();assertEquals(PinGateActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
 }

 @Test public void soundRequestCompletionIsNotHumanConfirmation()throws Exception {
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();java.lang.reflect.Field f=SettingsActivity.class.getDeclaredField("tts");f.setAccessible(true);TextToSpeech t=(TextToSpeech)f.get(a);org.robolectric.shadows.ShadowTextToSpeech.addVoice(voice("offline","ar-EG",false,300,false));Shadows.shadowOf(t).getOnInitListener().onInit(TextToSpeech.SUCCESS);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();a.findViewById(R.id.btn_tts_test).performClick();UtteranceProgressListener l=org.robolectric.util.ReflectionHelpers.getField(t,"mUtteranceProgressListener");l.onDone("settings-test");Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(a.getString(R.string.tts_test_ok),((TextView)a.findViewById(R.id.tts_status)).getText().toString());assertEquals(View.VISIBLE,a.findViewById(R.id.tts_human_check).getVisibility());a.findViewById(R.id.btn_tts_heard).performClick();assertEquals(a.getString(R.string.tts_human_ok),((TextView)a.findViewById(R.id.tts_status)).getText().toString());a.findViewById(R.id.btn_tts_retry).performClick();l.onDone("settings-test");Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(View.GONE,a.findViewById(R.id.tts_human_check).getVisibility());
 }
 @Test public void confirmStopsInBackgroundAndRechecksAfterSettingsReturn(){
  org.robolectric.android.controller.ActivityController<ConfirmCallActivity> c=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,contact(1,"اختبار","01000000001"))).setup();TextToSpeech old=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();c.pause().stop();assertTrue(Shadows.shadowOf(old).isShutdown());c.restart().resume();assertNotSame(old,org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance());assertEquals(View.INVISIBLE,c.get().findViewById(R.id.btn_repeat_name).getVisibility());
 }
 @Test public void guardedHomeLongHoldShowsFullNameInsteadOfLaunching(){
  Prefs.get(app).edit().putBoolean(Prefs.TOUCH_GUARD,true).putBoolean(MainActivity.GUIDE_SEEN,true).apply();MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();View tile=key(a.findViewById(R.id.tiles_container),AppSelection.PHONE);tile.layout(0,0,200,200);tile.dispatchTouchEvent(MotionEvent.obtain(1000,1000,MotionEvent.ACTION_DOWN,20,20,0));tile.dispatchTouchEvent(MotionEvent.obtain(1000,2100,MotionEvent.ACTION_UP,20,20,0));assertNotNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test @Config(sdk=24) public void fullNameWorksOnMinimumAndroidVersion(){View tile=info.fadl.mamalauncher.ui.UiBinder.buildHomeTile(app,"اسم تطبيق طويل",null,0,false,null);assertTrue(tile.performLongClick());}

 @Test public void themeContrastKeepsConfirmationTextReadable(){Prefs.get(app).edit().putBoolean(Prefs.HIGH_CONTRAST,true).apply();ConfirmCallActivity a=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,contact(1,"اختبار","01000000001"))).setup().get();assertEquals(0xFFFFD600,((TextView)a.findViewById(R.id.big_name)).getCurrentTextColor());assertEquals(0xFFFFD600,((TextView)a.findViewById(R.id.single_number)).getCurrentTextColor());}
}
