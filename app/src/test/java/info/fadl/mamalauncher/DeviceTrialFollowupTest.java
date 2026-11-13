package info.fadl.mamalauncher;
import android.content.*;
import android.content.pm.PackageManager;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class DeviceTrialFollowupTest {
 @Before public void reset(){Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().apply();SessionGate.unlock();Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.CALL_PHONE);}
 private View key(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=key(((ViewGroup)v).getChildAt(i),tag);if(r!=null)return r;}return null;}
 @Test public void guardedPhysicalZeroHoldAddsPlusWithoutZero(){
  android.content.Context app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().putBoolean(Prefs.TOUCH_GUARD,true).apply();
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();View zero=key(a.findViewById(R.id.dial_grid),"0");zero.layout(0,0,100,100);
  zero.dispatchTouchEvent(android.view.MotionEvent.obtain(1000,1000,android.view.MotionEvent.ACTION_DOWN,20,20,0));
  zero.dispatchTouchEvent(android.view.MotionEvent.obtain(1000,2000,android.view.MotionEvent.ACTION_UP,20,20,0));
  assertEquals("+",((android.widget.TextView)a.findViewById(R.id.dial_display)).getText().toString());
 }
 @Test public void zeroTapAndLongHoldAreDistinctAndPreservePrefix(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  View zero=key(a.findViewById(R.id.dial_grid),"0");assertTrue(zero.performLongClick());
  assertEquals("+",((android.widget.TextView)a.findViewById(R.id.dial_display)).getText().toString());
  zero.performClick();assertEquals("+0",((android.widget.TextView)a.findViewById(R.id.dial_display)).getText().toString());
  zero.performLongClick();assertEquals("+0",((android.widget.TextView)a.findViewById(R.id.dial_display)).getText().toString());
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());
  assertEquals("اكتب الرقم",((android.widget.TextView)a.findViewById(R.id.dial_placeholder)).getText().toString());
 }
 @Test public void dialPressCallsDirectlyOnlyOnceWhenGranted(){
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CALL_PHONE);
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  key(a.findViewById(R.id.dial_grid),"1").performClick();key(a.findViewById(R.id.dial_grid),"1").performClick();key(a.findViewById(R.id.dial_grid),"2").performClick();
  a.findViewById(R.id.btn_call).performClick();a.findViewById(R.id.btn_call).performClick();
  Intent call=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(Intent.ACTION_CALL,call.getAction());assertEquals("tel:112",call.getDataString());
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void permissionGrantNeverAutoCallsAndNewPressIsRequired(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();key(a.findViewById(R.id.dial_grid),"1").performClick();key(a.findViewById(R.id.dial_grid),"1").performClick();key(a.findViewById(R.id.dial_grid),"2").performClick();
  a.findViewById(R.id.btn_call).performClick();
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",Shadows.shadowOf(a).getNextStartedActivity().getAction());
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CALL_PHONE);
  a.onRequestPermissionsResult(42,new String[]{android.Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_GRANTED});
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());
  a.findViewById(R.id.btn_call).performClick();assertEquals(Intent.ACTION_CALL,Shadows.shadowOf(a).getNextStartedActivity().getAction());
 }
 @Test public void deniedPermissionNeverCalls(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();key(a.findViewById(R.id.dial_grid),"1").performClick();key(a.findViewById(R.id.dial_grid),"1").performClick();key(a.findViewById(R.id.dial_grid),"2").performClick();a.findViewById(R.id.btn_call).performClick();Shadows.shadowOf(a).getNextStartedActivity();
  a.onRequestPermissionsResult(42,new String[]{android.Manifest.permission.CALL_PHONE},new int[]{PackageManager.PERMISSION_DENIED});assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 private void screenOff(android.app.Activity a){
  a.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  assertFalse(SessionGate.isValid());assertTrue(a.isFinishing());
  Intent next=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(MainActivity.class.getName(),next.getComponent().getClassName());
  assertTrue((next.getFlags()&Intent.FLAG_ACTIVITY_CLEAR_TOP)!=0);
 }
 @Test public void screenOffSettingsReturnsHomeEvenDuringExternalExemption(){
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();SessionGate.expectExternal();screenOff(a);assertFalse(SessionGate.externalInFlight());
 }
 @Test public void screenOffFirstSetupDiscardsPartialAndReturnsHome(){
  PinGateActivity a=Robolectric.buildActivity(PinGateActivity.class,PinGateActivity.intentForEntry(RuntimeEnvironment.getApplication())).setup().get();
  for(char c:"2468".toCharArray())key(a.findViewById(R.id.pin_pad),""+c).performClick();
  screenOff(a);assertFalse(Prefs.hasPin(a));
 }
 @Test public void screenOffChangePinLeavesExistingCredentialUntouched(){
  android.content.Context app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().putString(Prefs.PIN_SALT,"salt").putString(Prefs.PIN_HASH,PinLock.hash("salt","2468")).apply();
  PinGateActivity a=Robolectric.buildActivity(PinGateActivity.class,PinGateActivity.intentForSetup(app)).setup().get();
  for(char c:"1357".toCharArray())key(a.findViewById(R.id.pin_pad),""+c).performClick();screenOff(a);
  assertTrue(PinLock.verify("salt","2468",Prefs.get(a).getString(Prefs.PIN_HASH,null)));
 }
 @Test public void lateRecoveryCredentialAfterScreenOffCannotUnlockOrReplace(){
  android.content.Context app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().putString(Prefs.PIN_SALT,"salt").putString(Prefs.PIN_HASH,PinLock.hash("salt","2468")).putInt(Prefs.PIN_FAILURES,1).apply();
  android.app.KeyguardManager km=(android.app.KeyguardManager)app.getSystemService(android.content.Context.KEYGUARD_SERVICE);
  Shadows.shadowOf(km).setIsDeviceSecure(true);
  PinGateActivity a=Robolectric.buildActivity(PinGateActivity.class,PinGateActivity.intentForEntry(app)).setup().get();
  assertEquals(View.VISIBLE,a.findViewById(R.id.btn_device_lock).getVisibility());
  screenOff(a);a.onActivityResult(51,android.app.Activity.RESULT_OK,null);
  assertFalse(SessionGate.isValid());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
  assertTrue(PinLock.verify("salt","2468",Prefs.get(a).getString(Prefs.PIN_HASH,null)));assertEquals(1,Prefs.get(a).getInt(Prefs.PIN_FAILURES,0));
 }
 @Test public void staleKeyTapAfterScreenOffCannotFinishFirstSetup(){
  PinGateActivity a=Robolectric.buildActivity(PinGateActivity.class,PinGateActivity.intentForEntry(RuntimeEnvironment.getApplication())).setup().get();
  for(char c:"2468".toCharArray())key(a.findViewById(R.id.pin_pad),""+c).performClick();screenOff(a);
  for(char c:"24682468".toCharArray())key(a.findViewById(R.id.pin_pad),""+c).performClick();
  assertFalse(Prefs.hasPin(a));assertFalse(SessionGate.isValid());
 }
 @Test public void numericRowsAreSpatiallyLtrInArabic(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  View root=a.findViewById(android.R.id.content);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1400,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1400);
  assertTrue(key(a.findViewById(R.id.dial_grid),"1").getLeft()<key(a.findViewById(R.id.dial_grid),"3").getLeft());
  PinGateActivity p=Robolectric.buildActivity(PinGateActivity.class,PinGateActivity.intentForEntry(a)).setup().get();root=p.findViewById(android.R.id.content);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1400,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1400);
  assertTrue(key(p.findViewById(R.id.pin_pad),"1").getLeft()<key(p.findViewById(R.id.pin_pad),"3").getLeft());
 }
}
