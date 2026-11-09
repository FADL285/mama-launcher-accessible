package info.fadl.mamalauncher;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import info.fadl.mamalauncher.ui.ContactEntry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class HandsetUxTest {
 android.app.Application app;
 @Before public void reset(){app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();SessionGate.unlock();Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.READ_CONTACTS);}
 ContactEntry contact(long id,String name,String number){return new ContactEntry(id,name,number,null,false);}
 @Test public void settingsDeveloperLinkOpensOnlyTheVerifiedWebsite(){
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
  assertEquals("Developed by Mohamed Fadl",((TextView)a.findViewById(R.id.developer_credit)).getText().toString());
  a.findViewById(R.id.developer_site).performClick();Intent link=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(link);assertEquals(Intent.ACTION_VIEW,link.getAction());assertEquals("https://fadl.info",link.getDataString());
 }
 public static class NoBrowserSettings extends SettingsActivity {
  @Override public void startActivity(Intent intent){if(Intent.ACTION_VIEW.equals(intent.getAction()))throw new ActivityNotFoundException();super.startActivity(intent);}
 }
 @Test public void missingBrowserCannotLeaveExternalLockExemption(){
  org.robolectric.android.controller.ActivityController<NoBrowserSettings> c=Robolectric.buildActivity(NoBrowserSettings.class).setup();
  c.get().findViewById(R.id.developer_site).performClick();assertFalse(SessionGate.externalInFlight());
  c.pause().stop();assertFalse(SessionGate.isValid());c.restart().resume();assertTrue(c.get().isFinishing());
 }
 @Test public void exactMatchDoesNotConfusePrefixSuffixOrInternationalCountry(){
  assertTrue(DialContactMatcher.exact("٠١٠٠٠٠٠٠٠٠١","0100 000-0001","EG"));
  assertTrue(DialContactMatcher.exact("01000000001","+201000000001","EG"));
  assertTrue(DialContactMatcher.exact("00201000000001","+201000000001","EG"));
  assertFalse(DialContactMatcher.exact("0000001","01000000001","EG"));
  assertFalse(DialContactMatcher.exact("010","01000000001","EG"));
  assertFalse(DialContactMatcher.exact("*01000000001#","01000000001","EG"));
  assertFalse(DialContactMatcher.exact("01000000001;12","0100000000112","EG"));
  assertFalse(DialContactMatcher.exact("+12120000001","+201000000001","EG"));
 }
 @Test public void suggestionsKeepEveryMatchingPersonAndOnlyMatchedNumbers(){
  ContactEntry multi=new ContactEntry(1,"اختبار",Arrays.asList(new ContactEntry.PhoneNumber("01000000001","موبايل"),new ContactEntry.PhoneNumber("0200000002","بيت")),null,false);
  List<ContactEntry> all=Arrays.asList(multi,contact(2,"تجربة","01000000001"));
  assertEquals(2,DialContactMatcher.matches(all,"010", "EG",false).size());
  assertEquals(0,DialContactMatcher.matches(all,"010", "EG",true).size());
  assertEquals(2,DialContactMatcher.matches(all,"01000000001", "EG",true).size());
  assertEquals(1,DialContactMatcher.matches(all,"010", "EG",false).get(0).numbers.size());
 }
 View key(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=key(((ViewGroup)v).getChildAt(i),tag);if(r!=null)return r;}return null;}
 void type(DialpadActivity a,String typed){for(char c:typed.toCharArray())key(a.findViewById(R.id.dial_grid),""+c).performClick();}
 void settle(DialpadActivity a)throws Exception {for(int i=0;i<3;i++){a.awaitLookupsForTest();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}}
 void provider(String name) {
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){
   @Override public Cursor query(Uri uri,String[] projection,String s,String[] args,String sort) {
    MatrixCursor c=new MatrixCursor(projection);if(name!=null)c.addRow(new Object[]{7,name,"01000000001",2,null,null,0});return c;
   }
  });
 }
 @Test public void exactSavedNumberReplacesSaveAndOpensConfirmationNeverInsertOrCall()throws Exception {
  provider("اسم تجريبي");Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();type(a,"01000000001");settle(a);
  assertEquals(a.getString(R.string.dial_view_saved),((TextView)a.findViewById(R.id.save_number_label)).getText().toString());
  a.findViewById(R.id.btn_save_number).performClick();settle(a);Intent next=Shadows.shadowOf(a).getNextStartedActivity();
  assertEquals(ConfirmCallActivity.class.getName(),next.getComponent().getClassName());assertNull(next.getAction());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void saveRechecksWhenContactWasAddedAfterTyping()throws Exception {
  provider(null);Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();type(a,"01000000001");settle(a);provider("اسم جديد");
  a.findViewById(R.id.btn_save_number).performClick();settle(a);assertEquals(ConfirmCallActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
 }
 @Test public void missingContactsPermissionCannotPresentAnUnverifiedNumberAsNew()throws Exception {
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();type(a,"01000000001");a.findViewById(R.id.btn_save_number).performClick();settle(a);assertEquals(a.getString(R.string.dial_lookup_needed),org.robolectric.shadows.ShadowToast.getTextOfLatestToast());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void nullProviderResultIsNotProofOfNewNumber()throws Exception {
  Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){@Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}});
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();type(a,"01000000001");a.findViewById(R.id.btn_save_number).performClick();settle(a);assertEquals(a.getString(R.string.dial_lookup_needed),org.robolectric.shadows.ShadowToast.getTextOfLatestToast());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 public static class SettingsFixture extends SettingsActivity {@Override void loadApps(){}}
 @Test public void secondaryMigrationDoesNotMovePreviouslyChosenApps(){
  Prefs.get(app).edit().putString(AppSelection.PREF_KEY,"com.example.first,com.example.second").apply();
  SettingsActivity a=Robolectric.buildActivity(SettingsFixture.class).setup().get();
  a.applyDiscoveredApps(Arrays.asList(new String[]{"com.example.first","الأول"},new String[]{"com.example.second","الثاني"}));
  assertEquals("com.example.first,com.example.second",Prefs.get(a).getString(AppSelection.PREF_KEY,null));assertEquals("",Prefs.get(a).getString(AppSelection.SECONDARY_KEY,null));
 }
 @Test public void pickerMoveDestinationAndTapReorderPersistWithoutArrows(){
  Prefs.get(app).edit().putString(AppSelection.PREF_KEY,"com.example.first,com.example.second").apply();SettingsActivity a=Robolectric.buildActivity(SettingsFixture.class).setup().get();
  a.applyDiscoveredApps(Arrays.asList(new String[]{"com.example.first","الأول"},new String[]{"com.example.second","الثاني"}));
  ViewGroup box=a.findViewById(R.id.settings_apps_list);key(key(box,"app_row:com.example.first"),"app_handle").performClick();android.app.AlertDialog order=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();order.getListView().performItemClick(null,3,3);
  assertEquals("com.example.second,com.example.first",Prefs.get(a).getString(AppSelection.PREF_KEY,null));
  key(key(box,"app_row:com.example.second"),"app_place").performClick();android.app.AlertDialog place=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();place.getListView().performItemClick(null,1,1);
  assertEquals("com.example.first",Prefs.get(a).getString(AppSelection.PREF_KEY,null));assertEquals("com.example.second",Prefs.get(a).getString(AppSelection.SECONDARY_KEY,null));
 }
 @Test public void arbitraryDragMovePreservesAllApps(){
  List<String> input=Arrays.asList("a","b","c","d");assertEquals(Arrays.asList("b","c","d","a"),AppSelection.moveTo(input,0,3));assertEquals(Arrays.asList("a","b","c","d"),input);assertEquals(input,AppSelection.moveTo(input,-1,1));
 }
 @Test public void secondaryScreenHasBackAndDoesNotShowPhoneTileOrSettingsGate(){
  AppsPageActivity a=Robolectric.buildActivity(AppsPageActivity.class).setup().get();assertEquals(View.GONE,a.findViewById(R.id.home_header).getVisibility());a.onBackPressed();assertTrue(a.isFinishing());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 Voice voice(String name,String locale,boolean network,int quality,boolean installing){return new Voice(name,Locale.forLanguageTag(locale),quality,300,network,installing?Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED):Collections.emptySet());}
 @Test public void voiceChoicePrefersInstalledEgyptianButNeverCloudOrDownloading(){
  Voice egypt=voice("eg-offline","ar-EG",false,300,false),generic=voice("generic","ar",false,500,false);
  assertEquals(egypt,ArabicVoicePolicy.best(new HashSet<>(Arrays.asList(generic,egypt,voice("cloud","ar-EG",true,500,false),voice("download","ar-EG",false,500,true)))));
  assertNull(ArabicVoicePolicy.best(Collections.singleton(voice("cloud","ar-EG",true,500,false))));
  assertEquals(generic,ArabicVoicePolicy.best(Collections.singleton(generic)));
 }
 @Test public void detailControlsHavePhysicalSidesAndLargePhoto(){
  ConfirmCallActivity a=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,contact(1,"اسم تجريبي","01000000001"))).setup().get();
  FrameLayout.LayoutParams listen=(FrameLayout.LayoutParams)a.findViewById(R.id.btn_repeat_name).getLayoutParams(),star=(FrameLayout.LayoutParams)a.findViewById(R.id.btn_favorite).getLayoutParams();
  assertEquals(Gravity.LEFT,listen.gravity&Gravity.HORIZONTAL_GRAVITY_MASK);assertEquals(Gravity.RIGHT,star.gravity&Gravity.HORIZONTAL_GRAVITY_MASK);assertEquals((int)(220*a.getResources().getDisplayMetrics().density),((View)a.findViewById(R.id.big_photo).getParent()).getLayoutParams().height);
 }
 void engine(String pkg) {
  android.content.pm.ResolveInfo r=new android.content.pm.ResolveInfo();r.serviceInfo=new android.content.pm.ServiceInfo();r.serviceInfo.packageName=pkg;r.serviceInfo.name=pkg+".Tts";r.serviceInfo.enabled=true;r.serviceInfo.applicationInfo=new android.content.pm.ApplicationInfo();r.serviceInfo.applicationInfo.enabled=true;
  Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),r);
 }
 @Test public void firstUseFindsSoleEngineWithoutManualChoice() {
  engine("example.only");android.provider.Settings.Secure.putString(app.getContentResolver(),android.provider.Settings.Secure.TTS_DEFAULT_SYNTH,null);
  assertEquals("example.only",SpeechEngineChoice.requested(app));
 }
 @Test public void unsetChoiceTriesNextEngineWithoutLosingQueuedName() {
  engine("example.first");engine("example.second");android.provider.Settings.Secure.putString(app.getContentResolver(),android.provider.Settings.Secure.TTS_DEFAULT_SYNTH,"example.first");
  NameSpeaker speaker=new NameSpeaker();speaker.init(app);speaker.speak("اسم تجريبي");
  TextToSpeech first=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();Shadows.shadowOf(first).getOnInitListener().onInit(TextToSpeech.ERROR);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  TextToSpeech second=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();assertNotSame(first,second);assertTrue(Shadows.shadowOf(first).isShutdown());
  org.robolectric.shadows.ShadowTextToSpeech.addVoice(voice("offline-ar","ar-EG",false,300,false));
  Shadows.shadowOf(second).getOnInitListener().onInit(TextToSpeech.SUCCESS);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  assertEquals("اسم تجريبي",Shadows.shadowOf(second).getLastSpokenText());assertEquals("example.second",Prefs.get(app).getString(SpeechEngineChoice.AUTO_KEY,null));speaker.shutdown();
 }
 @Test public void explicitChoiceDoesNotSilentlyTryAnotherEngine() {
  engine("example.first");engine("example.second");Prefs.get(app).edit().putString(SpeechEngineChoice.KEY,"example.first").apply();
  NameSpeaker speaker=new NameSpeaker();speaker.init(app);TextToSpeech first=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();
  Shadows.shadowOf(first).getOnInitListener().onInit(TextToSpeech.ERROR);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertSame(first,org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance());speaker.shutdown();
 }
 @Test public void lateInitAfterShutdownCannotSpeakOrStartFallback() {
  engine("example.first");engine("example.second");NameSpeaker speaker=new NameSpeaker();speaker.init(app);speaker.speak("اسم تجريبي");TextToSpeech first=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();speaker.shutdown();
  Shadows.shadowOf(first).getOnInitListener().onInit(TextToSpeech.ERROR);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertSame(first,org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance());assertNull(Shadows.shadowOf(first).getLastSpokenText());
 }
 android.view.DragEvent drag(int action,String pkg) {
  android.view.DragEvent event=org.robolectric.util.ReflectionHelpers.callConstructor(android.view.DragEvent.class);
  org.robolectric.util.ReflectionHelpers.setField(event,"mAction",action);org.robolectric.util.ReflectionHelpers.setField(event,"mLocalState",pkg);return event;
 }
 @Test public void actualRowDropReordersWithinGroupAndCancellationDoesNot() {
  Prefs.get(app).edit().putString(AppSelection.PREF_KEY,"example.a,example.b,example.c").apply();SettingsFixture a=Robolectric.buildActivity(SettingsFixture.class).setup().get();
  a.applyDiscoveredApps(Arrays.asList(new String[]{"example.a","الأول"},new String[]{"example.b","الثاني"},new String[]{"example.c","الثالث"}));
  LinearLayout box=a.findViewById(R.id.settings_apps_list);View last=key(box,"app_row:example.c");
  assertTrue(last.dispatchDragEvent(drag(android.view.DragEvent.ACTION_DRAG_STARTED,"example.a")));
  last.dispatchDragEvent(drag(android.view.DragEvent.ACTION_DRAG_ENDED,"example.a"));assertEquals("example.a,example.b,example.c",Prefs.get(a).getString(AppSelection.PREF_KEY,null));
  assertTrue(last.dispatchDragEvent(drag(android.view.DragEvent.ACTION_DRAG_STARTED,"example.a")));
  assertTrue(last.dispatchDragEvent(drag(android.view.DragEvent.ACTION_DROP,"example.a")));assertEquals("example.b,example.c,example.a",Prefs.get(a).getString(AppSelection.PREF_KEY,null));
 }

 @Test public void chosenMultiNumberRemainsVisibleOutsidePhotoScroll() {
  ContactEntry c=new ContactEntry(1,"اسم تجريبي",Arrays.asList(new ContactEntry.PhoneNumber("01000000001","موبايل"),new ContactEntry.PhoneNumber("0200000002","بيت")),null,false);
  ConfirmCallActivity a=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,c)).setup().get();
  ViewGroup box=a.findViewById(R.id.numbers_container);((ViewGroup)box.getChildAt(0)).getChildAt(1).performClick();
  TextView target=a.findViewById(R.id.selected_call_number);assertEquals("0200000002",target.getText().toString());assertEquals(View.VISIBLE,target.getVisibility());assertSame(a.findViewById(R.id.confirm_scroll).getParent(),target.getParent());assertTrue(a.findViewById(R.id.btn_call).isEnabled());
 }

 @Test public void shortCodesNeverGainExactIdentityThroughE164(){assertFalse(DialContactMatcher.exact("19000","+2019000","EG"));assertTrue(DialContactMatcher.exact("19000","19000","EG"));}
 @Test public void prefixesCrossCountryFormats(){assertTrue(DialContactMatcher.prefix("+201000000001","0100","EG"));assertTrue(DialContactMatcher.prefix("01000000001","+20100","EG"));}
 @Test public void sameDestinationAndTemporarilyMissingAppPreserveOrder(){
  Prefs.get(app).edit().putString(AppSelection.PREF_KEY,"example.a,example.b,example.missing").apply();SettingsFixture a=Robolectric.buildActivity(SettingsFixture.class).setup().get();
  a.applyDiscoveredApps(Arrays.asList(new String[]{"example.a","الأول"},new String[]{"example.b","الثاني"}));
  key(key(a.findViewById(R.id.settings_apps_list),"app_row:example.a"),"app_place").performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(null,0,0);
  assertEquals("example.a,example.b,example.missing",Prefs.get(a).getString(AppSelection.PREF_KEY,null));
 }
 @Test public void appsBackRemainsOutsideScrollAndEmptyFolderIsHidden(){
  AppsPageActivity a=Robolectric.buildActivity(AppsPageActivity.class).setup().get();ViewGroup host=a.findViewById(android.R.id.content);ViewGroup fixed=(ViewGroup)host.getChildAt(0);assertNotNull(fixed.getChildAt(0).findViewById(R.id.btn_back));assertTrue(fixed.getChildAt(1) instanceof ScrollView);assertSame(fixed,a.findViewById(R.id.page_title).getParent().getParent());
  Prefs.get(app).edit().putString(AppSelection.SECONDARY_KEY,"example.uninstalled").apply();MainActivity home=Robolectric.buildActivity(MainActivity.class).setup().get();assertFalse(home.hasLaunchableSecondary());assertEquals("example.uninstalled",Prefs.get(app).getString(AppSelection.SECONDARY_KEY,null));
 }
 @Test public void missingEngineCanRecoverFromListenWithoutReopeningScreen(){
  Prefs.get(app).edit().putString(SpeechEngineChoice.KEY,"example.later").apply();NameSpeaker speaker=new NameSpeaker();speaker.init(app);engine("example.later");speaker.speak("اسم تجريبي");TextToSpeech tts=org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance();
  org.robolectric.shadows.ShadowTextToSpeech.addVoice(voice("offline","ar-EG",false,300,false));Shadows.shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("اسم تجريبي",Shadows.shadowOf(tts).getLastSpokenText());speaker.shutdown();
 }

 @Test public void loadingDoesNotFalselyRequestPermissionAndChangedSaveExplainsCancellation()throws Exception {
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){@Override public Cursor query(Uri uri,String[] p,String selection,String[] args,String sort){entered.countDown();try{if(!release.await(4,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){throw new RuntimeException(e);}return new MatrixCursor(p);}});
  Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));type(a,"010");
  assertEquals(a.getString(R.string.dial_lookup_loading),((TextView)((LinearLayout)a.findViewById(R.id.dial_matches)).getChildAt(0)).getText().toString());
  a.findViewById(R.id.btn_save_number).performClick();type(a,"1");release.countDown();settle(a);
  assertEquals(a.getString(R.string.dial_save_changed),org.robolectric.shadows.ShadowToast.getTextOfLatestToast());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void noDefaultUsesExplicitInstalledEnginesWithoutNullDuplicate()throws Exception {
  engine("example.first");engine("example.second");android.provider.Settings.Secure.putString(app.getContentResolver(),android.provider.Settings.Secure.TTS_DEFAULT_SYNTH,null);
  NameSpeaker speaker=new NameSpeaker();speaker.init(app);java.lang.reflect.Field field=NameSpeaker.class.getDeclaredField("engines");field.setAccessible(true);List<?> choices=(List<?>)field.get(speaker);assertEquals(2,choices.size());assertFalse(choices.contains(null));speaker.shutdown();
 }

 @Test public void exactNumberMovesAheadOfLongerPrefixAndExactContactRanksFirst(){
  ContactEntry multi=new ContactEntry(2,"متعدد",Arrays.asList(new ContactEntry.PhoneNumber("030123456","الأول"),new ContactEntry.PhoneNumber("03012345","الثاني")),null,false);
  List<ContactEntry> matches=DialContactMatcher.matches(Arrays.asList(contact(1,"اقتراح","030123459"),multi),"03012345","EG",false);
  assertEquals(2,matches.get(0).id);assertEquals("03012345",matches.get(0).firstNumber());assertEquals("030123456",matches.get(0).numbers.get(1).number);
  assertEquals("030123456",multi.firstNumber());
 }
 @Test public void savedPreviewDisplaysTheExactNumberNotOtherNumber()throws Exception {
  Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){@Override public Cursor query(Uri uri,String[] p,String selection,String[] args,String sort){MatrixCursor c=new MatrixCursor(p);c.addRow(new Object[]{8,"اقتراح","030123459",2,null,null,0});c.addRow(new Object[]{7,"متعدد","030123456",2,null,null,0});c.addRow(new Object[]{7,"متعدد","03012345",2,null,null,0});return c;}});
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();type(a,"03012345");settle(a);
  String preview=((TextView)((LinearLayout)a.findViewById(R.id.dial_matches)).getChildAt(0)).getText().toString();
  assertEquals(a.getString(R.string.dial_saved)+" · متعدد\n"+info.fadl.mamalauncher.ui.DialMatchUi.isolatedNumber("03012345"),preview);
 }
 void saveCannotNavigateAfterPause(String... names)throws Exception {
  Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);provider(null);
  org.robolectric.android.controller.ActivityController<DialpadActivity> controller=Robolectric.buildActivity(DialpadActivity.class).setup();DialpadActivity a=controller.get();type(a,"01000000001");settle(a);
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){@Override public Cursor query(Uri uri,String[] p,String selection,String[] args,String sort){entered.countDown();try{if(!release.await(4,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){throw new RuntimeException(e);}MatrixCursor c=new MatrixCursor(p);for(int i=0;i<names.length;i++)c.addRow(new Object[]{i+1,names[i],"01000000001",2,null,null,0});return c;}});
  android.app.AlertDialog previous=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();a.findViewById(R.id.btn_save_number).performClick();assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));controller.pause();release.countDown();settle(a);
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());assertSame(previous,org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
  controller.resume();settle(a);assertNull(Shadows.shadowOf(a).getNextStartedActivity());
  a.findViewById(R.id.btn_save_number).performClick();settle(a);
  if(names.length>1){android.app.AlertDialog chooser=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotSame(previous,chooser);assertTrue(chooser.isShowing());controller.pause();assertFalse(chooser.isShowing());chooser.getListView().performItemClick(null,0,0);assertNull(Shadows.shadowOf(a).getNextStartedActivity());}
  else assertNotNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void pausedSaveCannotOpenInsertAndFreshRetryWorks()throws Exception {saveCannotNavigateAfterPause();}
 @Test public void pausedSaveCannotOpenConfirmationAndFreshRetryWorks()throws Exception {saveCannotNavigateAfterPause("اختبار");}
 @Test public void pausedSaveCannotOpenChooserAndChooserDismissesOnPause()throws Exception {saveCannotNavigateAfterPause("الأول","الثاني");}
 @Test public void newSurfaceTextUsesYellowOnHighContrastBlack()throws Exception {
  Prefs.get(app).edit().putBoolean(Prefs.HIGH_CONTRAST,true).apply();DialpadActivity dial=Robolectric.buildActivity(DialpadActivity.class).setup().get();type(dial,"010");
  TextView hint=(TextView)((LinearLayout)dial.findViewById(R.id.dial_matches)).getChildAt(0);assertEquals(0xFFFFD600,hint.getCurrentTextColor());assertEquals((int)(8*dial.getResources().getDisplayMetrics().density),hint.getPaddingRight());assertEquals(hint.getPaddingLeft(),hint.getPaddingRight());
  AppsPageActivity apps=Robolectric.buildActivity(AppsPageActivity.class).setup().get();ViewGroup host=apps.findViewById(android.R.id.content);ViewGroup fixed=(ViewGroup)host.getChildAt(0);
  assertEquals(0xFFFFD600,((TextView)apps.findViewById(R.id.page_title)).getCurrentTextColor());assertEquals(0xFFFFD600,((TextView)((LinearLayout)apps.findViewById(R.id.tiles_container)).getChildAt(0)).getCurrentTextColor());
 }
 @Test public void highContrastPickerUsesThemeForActualHintHeadingAndRow(){
  Prefs.get(app).edit().putBoolean(Prefs.HIGH_CONTRAST,true).putString(AppSelection.PREF_KEY,"example.a").apply();
  SettingsFixture a=Robolectric.buildActivity(SettingsFixture.class).setup().get();a.applyDiscoveredApps(Collections.singletonList(new String[]{"example.a","تطبيق تجريبي"}));
  LinearLayout box=a.findViewById(R.id.settings_apps_list);assertEquals(0xFFFFD600,((TextView)box.getChildAt(0)).getCurrentTextColor());assertEquals(0xFFFFD600,((TextView)box.getChildAt(1)).getCurrentTextColor());assertEquals(0xFFFFD600,((TextView)key(box,"app_handle")).getCurrentTextColor());assertEquals(0xFFFFD600,((TextView)a.findViewById(R.id.page_title)).getCurrentTextColor());
 }
 @Test public void roamingUsesSimCountryForExactAndPrefixRecognition()throws Exception {
  provider("اختبار");Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);
  android.telephony.TelephonyManager tm=(android.telephony.TelephonyManager)app.getSystemService(Context.TELEPHONY_SERVICE);Shadows.shadowOf(tm).setSimCountryIso("eg");Shadows.shadowOf(tm).setNetworkCountryIso("us");
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();java.lang.reflect.Method method=DialpadActivity.class.getDeclaredMethod("country");method.setAccessible(true);String region=(String)method.invoke(a);assertEquals("EG",region);assertTrue(DialContactMatcher.exact("01000000001","+201000000001",region));assertTrue(DialContactMatcher.prefix("+201000000001","0100",region));
  Shadows.shadowOf(tm).setSimCountryIso("");assertEquals("US",method.invoke(a));Shadows.shadowOf(tm).setNetworkCountryIso("");assertEquals(Locale.getDefault().getCountry().toUpperCase(Locale.US),method.invoke(a));
 }

}
