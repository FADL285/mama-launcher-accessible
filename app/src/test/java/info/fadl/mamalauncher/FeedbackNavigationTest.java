package info.fadl.mamalauncher;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class FeedbackNavigationTest {
 @Before public void clean(){Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().putBoolean(MainActivity.GUIDE_SEEN,true).apply();SessionGate.lock();}
 private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 private ScrollView scrolled(MainActivity a){
  ScrollView s=a.findViewById(R.id.home_scroll);
  View child=s.getChildAt(0);child.setMinimumHeight(3000);
  s.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));s.layout(0,0,400,600);
  s.scrollTo(0,130);assertTrue(s.getScrollY()>0);return s;
 }
 @Test public void homeReselectResetsScroll(){
  ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
  ScrollView s=scrolled(c.get());
  c.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));idle();assertEquals(0,s.getScrollY());c.pause().stop().destroy();
 }
 @Test public void ordinaryResumeDoesNotResetScroll(){
  ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
  ScrollView s=scrolled(c.get());int before=s.getScrollY();c.pause().resume();idle();assertEquals(before,s.getScrollY());c.pause().stop().destroy();
 }
 @Test public void unrelatedNewIntentDoesNotResetScroll(){
  ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();ScrollView s=scrolled(c.get());int before=s.getScrollY();c.newIntent(new Intent("unrelated"));idle();assertEquals(before,s.getScrollY());c.pause().stop().destroy();
 }
 private ActivityController<AppsPageActivity> apps(boolean enabled){Prefs.get(RuntimeEnvironment.getApplication()).edit().putBoolean(Prefs.HOME_AFTER_LOCK,enabled).apply();return Robolectric.buildActivity(AppsPageActivity.class).setup();}
 @Test public void enabledScreenOffReturnsHomeOnlyAfterUnlock(){
  ActivityController<AppsPageActivity> c=apps(true);c.get().sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));idle();assertFalse(c.get().isFinishing());c.pause().resume();idle();assertTrue(c.get().isFinishing());Intent next=Shadows.shadowOf(c.get()).getNextStartedActivity();assertEquals(MainActivity.class.getName(),next.getComponent().getClassName());c.pause().stop().destroy();
 }
 @Test public void disabledScreenOffRetainsApps(){ActivityController<AppsPageActivity> c=apps(false);c.get().sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));idle();c.pause().resume();idle();assertFalse(c.get().isFinishing());c.pause().stop().destroy();}
 @Test public void pausedExternalFlowDoesNotArmReturn(){ActivityController<AppsPageActivity> c=apps(true);c.pause();c.get().sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));idle();c.resume();idle();assertFalse(c.get().isFinishing());c.pause().stop().destroy();}
 @Test public void screenOffDeliveredAfterPauseUsesInteractiveFallback(){ActivityController<AppsPageActivity> c=apps(true);android.os.PowerManager pm=(android.os.PowerManager)c.get().getSystemService(android.content.Context.POWER_SERVICE);Shadows.shadowOf(pm).setIsInteractive(false);c.pause();Shadows.shadowOf(pm).setIsInteractive(true);c.resume();idle();assertTrue(c.get().isFinishing());c.pause().stop().destroy();}
 @Test public void protectedAndCallPagesNeverOptIn(){assertFalse(new ConfirmCallActivity().homeAfterLockApplies());assertFalse(new SettingsActivity().homeAfterLockApplies());assertFalse(new PinGateActivity().homeAfterLockApplies());assertFalse(new MainActivity().homeAfterLockApplies());assertTrue(new PhoneActivity().homeAfterLockApplies());assertTrue(new DialpadActivity().homeAfterLockApplies());}
 @Test public void preferencePersistsBothChoices(){android.content.Context c=RuntimeEnvironment.getApplication();assertTrue(Prefs.homeAfterLock(c));Prefs.get(c).edit().putBoolean(Prefs.HOME_AFTER_LOCK,true).apply();assertTrue(Prefs.homeAfterLock(c));Prefs.get(c).edit().putBoolean(Prefs.HOME_AFTER_LOCK,false).apply();assertFalse(Prefs.homeAfterLock(c));}

 @Test public void keyguardDefersUntilFocusAfterUnlock(){
  ActivityController<AppsPageActivity> c=apps(true);
  android.app.KeyguardManager kg=(android.app.KeyguardManager)c.get().getSystemService(android.content.Context.KEYGUARD_SERVICE);
  Shadows.shadowOf(kg).setKeyguardLocked(true);
  c.get().sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));idle();c.pause().resume();idle();assertFalse(c.get().isFinishing());
  Shadows.shadowOf(kg).setKeyguardLocked(false);c.get().onWindowFocusChanged(true);idle();assertTrue(c.get().isFinishing());c.pause().stop().destroy();
 }
 @Test public void stillNonInteractiveDoesNotReturn(){
  ActivityController<AppsPageActivity> c=apps(true);android.os.PowerManager pm=(android.os.PowerManager)c.get().getSystemService(android.content.Context.POWER_SERVICE);
  Shadows.shadowOf(pm).setIsInteractive(false);c.pause().resume();idle();assertFalse(c.get().isFinishing());
  Shadows.shadowOf(pm).setIsInteractive(true);c.get().onWindowFocusChanged(true);idle();assertTrue(c.get().isFinishing());c.pause().stop().destroy();
 }
 @Test public void pendingReturnSurvivesRecreation(){
  ActivityController<AppsPageActivity> c=apps(true);android.os.Bundle state=new android.os.Bundle();c.get().sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));idle();c.pause().saveInstanceState(state).stop().destroy();
  c=Robolectric.buildActivity(AppsPageActivity.class).create(state).start().restoreInstanceState(state).resume();idle();assertTrue(c.get().isFinishing());c.pause().stop().destroy();
 }
 @Test public void disabledAfterArmingKeepsPage(){
  ActivityController<AppsPageActivity> c=apps(true);c.get().sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));idle();Prefs.get(c.get()).edit().putBoolean(Prefs.HOME_AFTER_LOCK,false).apply();c.pause().resume();idle();assertFalse(c.get().isFinishing());c.pause().stop().destroy();
 }
 @Test public void stoppedReceiverDoesNotArmReturn(){
  ActivityController<AppsPageActivity> c=apps(true);c.pause().stop();c.get().sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));idle();c.restart().start().resume();idle();assertFalse(c.get().isFinishing());c.pause().stop().destroy();
 }
 @Test public void appsBackResetsParentHome(){
  android.content.Context ctx=RuntimeEnvironment.getApplication();android.content.pm.ResolveInfo launcher=new android.content.pm.ResolveInfo();launcher.activityInfo=new android.content.pm.ActivityInfo();launcher.activityInfo.packageName="test.example";launcher.activityInfo.name="test.example.Main";
  Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("test.example");Shadows.shadowOf(ctx.getPackageManager()).addResolveInfoForIntent(launch,launcher);
  Prefs.get(ctx).edit().putString(AppSelection.SECONDARY_KEY,"test.example").apply();
  ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();ScrollView scroll=scrolled(c.get());android.view.ViewGroup tiles=c.get().findViewById(R.id.tiles_container);
  View folder=tiles.findViewWithTag(AppSelection.FOLDER);
  assertNotNull(folder);folder.performClick();assertEquals(AppsPageActivity.class.getName(),Shadows.shadowOf(c.get()).getNextStartedActivity().getComponent().getClassName());c.pause().resume();idle();assertEquals(0,scroll.getScrollY());c.pause().stop().destroy();
 }

 @Test public void settingsSwitchWritesBothChoices(){
  SessionGate.unlock();ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup();android.widget.Switch toggle=c.get().findViewById(R.id.switch_home_after_lock);
  toggle.setChecked(true);assertTrue(Prefs.homeAfterLock(c.get()));toggle.setChecked(false);assertFalse(Prefs.homeAfterLock(c.get()));c.pause().stop().destroy();
 }
}
