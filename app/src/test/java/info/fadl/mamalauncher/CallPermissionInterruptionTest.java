package info.fadl.mamalauncher;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={24,30,33})
public class CallPermissionInterruptionTest {
 @Before public void reset(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().apply();SessionGate.unlock();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.CALL_PHONE);
 }
 private View key(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=key(((ViewGroup)v).getChildAt(i),tag);if(r!=null)return r;}return null;}
 private void deliver(android.app.Activity a,int code,String[] perms,int[] results){
  org.robolectric.util.ReflectionHelpers.setField(a,"mHasCurrentPermissionsRequest",false);
  a.onRequestPermissionsResult(code,perms,results);
 }
 private android.content.Intent next(android.app.Activity a){
  android.content.Intent i=Shadows.shadowOf(a).getNextStartedActivity();
  org.robolectric.shadows.ShadowActivity.IntentForResult r=Shadows.shadowOf(a).getNextStartedActivityForResult();
  return i!=null?i:(r!=null?r.intent:null);
 }
 private DialpadActivity dialpadAsking(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  key(a.findViewById(R.id.dial_grid),"4").performClick();key(a.findViewById(R.id.dial_grid),"5").performClick();key(a.findViewById(R.id.dial_grid),"6").performClick();
  a.findViewById(R.id.btn_call).performClick();
  Intent asked=next(a);
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",asked.getAction());
  return a;
 }
 private ConfirmCallActivity confirmAsking(){
  info.fadl.mamalauncher.ui.ContactEntry c=new info.fadl.mamalauncher.ui.ContactEntry(7,"اختبار","01000000000",null,false);
  ConfirmCallActivity a=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(RuntimeEnvironment.getApplication(),c)).setup().get();
  // A single-number contact is auto-selected; the green button is ready.
  a.findViewById(R.id.btn_call).performClick();
  Intent asked=next(a);
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",asked.getAction());
  return a;
 }
 @Test public void interruptedSheetRecordsNoDenialAndAsksAgainOnFreshPress(){
  DialpadActivity a=dialpadAsking();
  deliver(a,42,new String[0],new int[0]);
  assertFalse(Prefs.get(a).contains("call_denied_once"));
  assertNull(next(a));
  a.findViewById(R.id.btn_call).performClick();
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",next(a).getAction());
 }
 @Test public void mismatchedPermissionNameRecordsNoDenial(){
  DialpadActivity a=dialpadAsking();
  deliver(a,42,new String[]{android.Manifest.permission.READ_CONTACTS},new int[]{PackageManager.PERMISSION_DENIED});
  assertFalse(Prefs.get(a).contains("call_denied_once"));
  a.findViewById(R.id.btn_call).performClick();
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",next(a).getAction());
 }
 @Test public void mismatchedArrayLengthsRecordNoDenial(){
  DialpadActivity a=dialpadAsking();
  deliver(a,42,new String[]{android.Manifest.permission.CALL_PHONE},new int[0]);
  assertFalse(Prefs.get(a).contains("call_denied_once"));
  a.findViewById(R.id.btn_call).performClick();
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",next(a).getAction());
 }
 @Test public void foreignRequestCodeIsIgnored(){
  DialpadActivity a=dialpadAsking();
  deliver(a,99,new String[]{android.Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_DENIED});
  assertFalse(Prefs.get(a).contains("call_denied_once"));
  deliver(a,42,new String[]{android.Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_DENIED});
  assertTrue(Prefs.get(a).getBoolean("call_denied_once",false));
  assertNull(next(a));
 }
 @Test public void realDenialIsRecordedAndRepeatedDenialStopsAsking(){
  DialpadActivity a=dialpadAsking();
  deliver(a,42,new String[]{android.Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_DENIED});
  assertTrue(Prefs.get(a).getBoolean("call_denied_once",false));
  assertNull(next(a));
  a.findViewById(R.id.btn_call).performClick();
  assertNull(next(a));
 }
 @Test public void denialWithRationaleAvailableAsksAgain(){
  // Plan row: denied once but the system still offers an explanation -> ask again.
  DialpadActivity a=dialpadAsking();
  deliver(a,42,new String[]{android.Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_DENIED});
  assertTrue(Prefs.get(a).getBoolean("call_denied_once",false));
  ((org.robolectric.shadows.ShadowPackageManager)Shadows.shadowOf(a.getPackageManager()))
   .setShouldShowRequestPermissionRationale(android.Manifest.permission.CALL_PHONE,true);
  a.findViewById(R.id.btn_call).performClick();
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",next(a).getAction());
  ((org.robolectric.shadows.ShadowPackageManager)Shadows.shadowOf(a.getPackageManager()))
   .setShouldShowRequestPermissionRationale(android.Manifest.permission.CALL_PHONE,false);
 }
 @Test public void revokeAfterRegrantAsksAgainInsteadOfBlockedHint(){
  // Plan row: granted, then revoked in Settings -> a fresh press asks, never calls.
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CALL_PHONE);
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  key(a.findViewById(R.id.dial_grid),"4").performClick();key(a.findViewById(R.id.dial_grid),"5").performClick();key(a.findViewById(R.id.dial_grid),"6").performClick();
  a.findViewById(R.id.btn_call).performClick();
  assertEquals(Intent.ACTION_CALL,next(a).getAction());
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.CALL_PHONE);
  // Back from the system dialer with the grant now revoked: a fresh press asks.
  DialpadActivity b=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  key(b.findViewById(R.id.dial_grid),"4").performClick();key(b.findViewById(R.id.dial_grid),"5").performClick();key(b.findViewById(R.id.dial_grid),"6").performClick();
  b.findViewById(R.id.btn_call).performClick();
  Intent asked=next(b);
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",asked.getAction());
 }
 @Test public void grantCallbackClearsEarlierDenialButNeverCalls(){
  DialpadActivity a=dialpadAsking();
  Prefs.get(a).edit().putBoolean("call_denied_once",true).apply();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CALL_PHONE);
  deliver(a,42,new String[]{android.Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_GRANTED});
  assertFalse(Prefs.get(a).getBoolean("call_denied_once",false));
  assertNull(next(a));
  a.findViewById(R.id.btn_call).performClick();
  assertEquals(Intent.ACTION_CALL,next(a).getAction());
 }
 @Test public void tapObservingLiveGrantClearsStaleDenialFlag(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().putBoolean("call_denied_once",true).apply();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CALL_PHONE);
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  key(a.findViewById(R.id.dial_grid),"4").performClick();key(a.findViewById(R.id.dial_grid),"5").performClick();key(a.findViewById(R.id.dial_grid),"6").performClick();
  a.findViewById(R.id.btn_call).performClick();
  assertFalse(Prefs.get(a).getBoolean("call_denied_once",false));
  assertEquals(Intent.ACTION_CALL,next(a).getAction());
 }
 @Test public void confirmInterruptedSheetRecordsNoDenialAndShowsNoBlockedHint(){
  ConfirmCallActivity a=confirmAsking();
  deliver(a,42,new String[0],new int[0]);
  assertFalse(Prefs.get(a).contains("call_denied_once"));
  assertEquals(View.GONE,a.findViewById(R.id.call_blocked).getVisibility());
  a.findViewById(R.id.btn_call).performClick();
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",next(a).getAction());
 }
 @Test public void confirmRealDenialShowsBlockedHintAndNeverCalls(){
  ConfirmCallActivity a=confirmAsking();
  deliver(a,42,new String[]{android.Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_DENIED});
  assertTrue(Prefs.get(a).getBoolean("call_denied_once",false));
  assertEquals(View.VISIBLE,a.findViewById(R.id.call_blocked).getVisibility());
  assertNull(next(a));
 }
 @Test public void confirmGrantClearsDenialButStillNeedsFreshPress(){
  ConfirmCallActivity a=confirmAsking();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CALL_PHONE);
  deliver(a,42,new String[]{android.Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_GRANTED});
  assertFalse(Prefs.get(a).getBoolean("call_denied_once",false));
  assertNull(next(a));
  a.findViewById(R.id.btn_call).performClick();
  assertEquals(Intent.ACTION_CALL,next(a).getAction());
 }
}
