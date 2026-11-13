package info.fadl.mamalauncher;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class RobustnessFlowTest {
 @Before public void reset(){Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().apply();SessionGate.lock();}
 private View key(View v,String tag){
  if(tag.equals(v.getTag()))return v;
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View k=key(g.getChildAt(i),tag);if(k!=null)return k;}}
  return null;
 }
 private void touch(View v,long start,long duration,int lastAction){
  v.layout(0,0,180,180);
  v.dispatchTouchEvent(MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,40,40,0));
  v.dispatchTouchEvent(MotionEvent.obtain(start,start+duration,lastAction,40,40,0));
 }
 private ConfirmCallActivity confirm(){
  info.fadl.mamalauncher.ui.ContactEntry c=new info.fadl.mamalauncher.ui.ContactEntry(7,"اختبار","01000000000",null,false);
  return Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(RuntimeEnvironment.getApplication(),c)).setup().get();
 }
 @Test public void guardedDialKeySupportsAccessibilityClickWithoutLosingItsTag(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().putBoolean(Prefs.TOUCH_GUARD,true).apply();
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  View one=key(a.findViewById(R.id.dial_grid),"1");
  one.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);
  assertEquals("1",((TextView)a.findViewById(R.id.dial_display)).getText().toString());
  touch(one,1000,600,MotionEvent.ACTION_UP);
  assertEquals("1",one.getTag());
  assertEquals("11",((TextView)a.findViewById(R.id.dial_display)).getText().toString());
 }
 @Test public void shortTouchOnCallCannotStartCallOrRequestPermission(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().putBoolean(Prefs.TOUCH_GUARD,true).apply();
  ConfirmCallActivity a=confirm();
  touch(a.findViewById(R.id.btn_call),1000,100,MotionEvent.ACTION_UP);
  assertNull(org.robolectric.Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void heldCallRequestsPermissionButNeverDeliversRealCall(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().putBoolean(Prefs.TOUCH_GUARD,true).apply();
  ConfirmCallActivity a=confirm();
  org.robolectric.shadows.ShadowSystemClock.advanceBy(2,java.util.concurrent.TimeUnit.SECONDS);
  touch(a.findViewById(R.id.btn_call),1000,600,MotionEvent.ACTION_UP);
  Intent i=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
  assertNotNull(i);assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",i.getAction());
 }
 @Test public void dialNumberSurvivesActivityRecreation(){
  org.robolectric.android.controller.ActivityController<DialpadActivity> c=Robolectric.buildActivity(DialpadActivity.class).setup();
  for(char ch:"0100123".toCharArray())key(c.get().findViewById(R.id.dial_grid),""+ch).performClick();
  c.recreate();
  assertEquals("0100123",((TextView)c.get().findViewById(R.id.dial_display)).getText().toString());
 }
 @Test public void slideOffThenBackInStillCancelsGuardedAction(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().putBoolean(Prefs.TOUCH_GUARD,true).apply();
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  View one=key(a.findViewById(R.id.dial_grid),"1");one.layout(0,0,180,180);
  one.dispatchTouchEvent(MotionEvent.obtain(1000,1000,0,40,40,0));
  one.dispatchTouchEvent(MotionEvent.obtain(1000,1200,2,-20,40,0));
  one.dispatchTouchEvent(MotionEvent.obtain(1000,1700,1,40,40,0));
  assertEquals("",((TextView)a.findViewById(R.id.dial_display)).getText().toString());
 }
 @Test public void handoffContractsUseTheReturnedIntents(){
  Intent dial=DialIntents.dial("01000000000");
  assertEquals(Intent.ACTION_DIAL,dial.getAction());assertEquals("tel:01000000000",dial.getDataString());
  Intent save=DialIntents.saveAsContact("01000000000");
  assertEquals(Intent.ACTION_INSERT,save.getAction());
  assertEquals(android.provider.ContactsContract.Contacts.CONTENT_TYPE,save.getType());
  assertEquals("01000000000",save.getStringExtra(android.provider.ContactsContract.Intents.Insert.PHONE));
 }
 public static class NoDialerActivity extends DialpadActivity {
  @Override public void startActivity(Intent i) {
   if(Intent.ACTION_CALL.equals(i.getAction()))throw new ActivityNotFoundException();
   super.startActivity(i);
  }
 }
 @Test public void noDialerProducesFeedbackInsteadOfCrash(){
  DialpadActivity a=Robolectric.buildActivity(NoDialerActivity.class).setup().get();
  org.robolectric.Shadows.shadowOf(a.getApplication()).grantPermissions(android.Manifest.permission.CALL_PHONE);
  key(a.findViewById(R.id.dial_grid),"4").performClick();key(a.findViewById(R.id.dial_grid),"5").performClick();key(a.findViewById(R.id.dial_grid),"6").performClick();
  a.findViewById(R.id.btn_call).performClick();
  assertEquals(a.getString(R.string.handoff_unavailable),org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
 }

 @Test public void shortNumberChoiceDoesNotSelectButAccessibilityChoiceDoes(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().putBoolean(Prefs.TOUCH_GUARD,true).apply();
  java.util.List<info.fadl.mamalauncher.ui.ContactEntry.PhoneNumber> nums=java.util.Arrays.asList(
   new info.fadl.mamalauncher.ui.ContactEntry.PhoneNumber("01000000001","A"),
   new info.fadl.mamalauncher.ui.ContactEntry.PhoneNumber("01000000002","B"));
  info.fadl.mamalauncher.ui.ContactEntry contact=new info.fadl.mamalauncher.ui.ContactEntry(1,"اختبار",nums,null,false);
  ConfirmCallActivity a=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(RuntimeEnvironment.getApplication(),contact)).setup().get();
  View choice=key(a.findViewById(R.id.numbers_container),"01000000002");
  touch(choice,1000,100,MotionEvent.ACTION_UP);
  assertFalse(a.findViewById(R.id.btn_call).isEnabled());
  choice.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);
  assertTrue(a.findViewById(R.id.btn_call).isEnabled());
 }
 public static class TrackingHome extends MainActivity {
  boolean requested;
  @Override public void recreate(){requested=true;}
 }
 @Test public void returningHomeAfterContrastChangeRequestsRecreation(){
  org.robolectric.android.controller.ActivityController<TrackingHome> c=Robolectric.buildActivity(TrackingHome.class).setup();
  c.pause().stop();Prefs.get(c.get()).edit().putBoolean(Prefs.HIGH_CONTRAST,true).apply();
  c.restart().resume();
  assertTrue(c.get().requested);
 }

 @Test public void returningFromInstallerRefreshesArabicHealth() throws Exception {
  SessionGate.unlock();
  org.robolectric.android.controller.ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup();
  java.lang.reflect.Field field=SettingsActivity.class.getDeclaredField("tts");field.setAccessible(true);
  android.speech.tts.TextToSpeech tts=(android.speech.tts.TextToSpeech)field.get(c.get());
  org.robolectric.shadows.ShadowTextToSpeech st=org.robolectric.Shadows.shadowOf(tts);
  st.getOnInitListener().onInit(android.speech.tts.TextToSpeech.SUCCESS);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  assertEquals(c.get().getString(R.string.tts_unsupported),((TextView)c.get().findViewById(R.id.tts_status)).getText().toString());
  assertTrue(c.get().findViewById(R.id.btn_tts_test).isEnabled());
  SessionGate.expectExternal();c.pause().stop();
  org.robolectric.shadows.ShadowTextToSpeech.addLanguageAvailability(new java.util.Locale("ar"));
  org.robolectric.shadows.ShadowTextToSpeech.addVoice(new android.speech.tts.Voice("offline-fixture",new java.util.Locale("ar"),300,300,false,java.util.Collections.emptySet()));
  c.restart().resume();
  assertEquals(c.get().getString(R.string.tts_ready),((TextView)c.get().findViewById(R.id.tts_status)).getText().toString());
  assertTrue(c.get().findViewById(R.id.btn_tts_test).isEnabled());
  assertEquals(View.GONE,c.get().findViewById(R.id.btn_tts_data).getVisibility());
 }
}
