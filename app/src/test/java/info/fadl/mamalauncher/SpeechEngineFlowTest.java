package info.fadl.mamalauncher;
import android.content.*;
import android.content.pm.*;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class SpeechEngineFlowTest {
 private Context app;
 @Before public void init(){app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();SessionGate.unlock();}
 private void engine(String pkg,String label){
  ResolveInfo r=new ResolveInfo();r.serviceInfo=new ServiceInfo();r.serviceInfo.packageName=pkg;r.serviceInfo.name=pkg+".Tts";r.serviceInfo.enabled=true;
  r.serviceInfo.applicationInfo=new ApplicationInfo();r.serviceInfo.applicationInfo.enabled=true;r.nonLocalizedLabel=label;
  Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),r);
 }
 private TextToSpeech speech(SettingsActivity a)throws Exception {java.lang.reflect.Field f=SettingsActivity.class.getDeclaredField("tts");f.setAccessible(true);return (TextToSpeech)f.get(a);}
 @Test public void chosenEnginePersistsForSettingsInstallerAndNameSpeech()throws Exception {
  engine("example.arabic.engine","Arabic fixture engine");
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
  a.findViewById(R.id.btn_tts_engine).performClick();
  android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(null,0,0);
  assertEquals("example.arabic.engine",SpeechEngineChoice.requested(a));
  assertTrue(((TextView)a.findViewById(R.id.tts_engine_label)).getText().toString().contains("Arabic fixture engine"));
  a.findViewById(R.id.btn_tts_data).performClick();Intent install=Shadows.shadowOf(a).getNextStartedActivity();
  assertEquals(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA,install.getAction());assertEquals("example.arabic.engine",install.getPackage());
  NameSpeaker names=new NameSpeaker();names.init(a);
  // Android constructor's requested engine, not an assertion about device audio.
  assertEquals("example.arabic.engine",org.robolectric.util.ReflectionHelpers.getField(org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance(),"mRequestedEngine"));
  names.shutdown();
 }
 @Test public void returningFromBoundInstallerRebuildsAndRejectsOldCallbacks()throws Exception {
  engine("example.arabic.engine","Arabic fixture engine");Prefs.get(app).edit().putString(SpeechEngineChoice.KEY,"example.arabic.engine").apply();
  org.robolectric.android.controller.ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup();
  TextToSpeech old=speech(c.get());c.get().findViewById(R.id.btn_tts_data).performClick();Shadows.shadowOf(c.get()).getNextStartedActivity();
  c.pause().stop();org.robolectric.shadows.ShadowTextToSpeech.addLanguageAvailability(new java.util.Locale("ar"));c.restart().resume();
  TextToSpeech now=speech(c.get());assertNotSame(old,now);assertTrue(Shadows.shadowOf(old).isShutdown());
  org.robolectric.shadows.ShadowTextToSpeech.addVoice(new android.speech.tts.Voice("offline-fixture",new java.util.Locale("ar"),300,300,false,java.util.Collections.emptySet()));
  Shadows.shadowOf(now).getOnInitListener().onInit(TextToSpeech.SUCCESS);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  assertEquals(c.get().getString(R.string.tts_ready),((TextView)c.get().findViewById(R.id.tts_status)).getText().toString());
  Shadows.shadowOf(old).getOnInitListener().onInit(TextToSpeech.ERROR);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  assertEquals(c.get().getString(R.string.tts_ready),((TextView)c.get().findViewById(R.id.tts_status)).getText().toString());
 }
 @Test public void removedExplicitEngineDoesNotSilentlyBecomeDefault(){
  Prefs.get(app).edit().putString(SpeechEngineChoice.KEY,"example.missing").apply();
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
  assertEquals(a.getString(R.string.tts_no_engines),((TextView)a.findViewById(R.id.tts_status)).getText().toString());
  assertFalse(a.findViewById(R.id.btn_tts_test).isEnabled());
 }
}
