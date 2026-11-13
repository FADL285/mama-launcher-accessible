package info.fadl.mamalauncher;
import android.content.Intent;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;
/** D-01 (option C): Call/Save truly disabled below 3 digits unless * or # appears. */
@RunWith(RobolectricTestRunner.class) @Config(sdk={24,30,33})
public class DialpadShortInputGateTest {
 @Before public void reset(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().apply();SessionGate.unlock();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.CALL_PHONE);
 }
 private View key(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){View r=key(((android.view.ViewGroup)v).getChildAt(i),tag);if(r!=null)return r;}return null;}
 private void type(DialpadActivity a,String s){for(char c:s.toCharArray())key(a.findViewById(R.id.dial_grid),""+c).performClick();}
 private void assertGate(DialpadActivity a,boolean expected){
  assertEquals("call enabled",expected,a.findViewById(R.id.btn_call).isEnabled());
  assertEquals("save enabled",expected,a.findViewById(R.id.btn_save_number).isEnabled());
 }
 @Test public void emptyAndOneAndTwoDigitsAreTrulyDisabled(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  assertGate(a,false);
  type(a,"1");assertGate(a,false);
  type(a,"2");assertGate(a,false);
 }
 @Test public void threeDigitsEnableBothButtons(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  type(a,"122");assertGate(a,true); // Egyptian emergency length stays callable
  type(a,"9");assertGate(a,true);
 }
 @Test public void starAndHashInputsStayCallableAtAnyLength(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  type(a,"*");assertGate(a,true);
  type(a,"#");assertGate(a,true);
  type(a,"0");type(a,"6");type(a,"#");assertGate(a,true); // *#06# MMI
 }
 @Test public void lonePlusAndShortPlusFormsAreDisabled(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  View zero=key(a.findViewById(R.id.dial_grid),"0");zero.performLongClick(); // "+"
  assertGate(a,false);
  type(a,"1");assertGate(a,false);
  type(a,"2");assertGate(a,false);
  type(a,"3");assertGate(a,true); // "+123" reaches 3 digits
 }
 @Test public void backspaceAcrossTheBoundaryReDisables(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  type(a,"112");assertGate(a,true);
  a.findViewById(R.id.btn_backspace).performClick();assertGate(a,false);
  a.findViewById(R.id.btn_backspace).performClick();assertGate(a,false);
  type(a,"#");assertGate(a,true);
 }
 @Test public void disabledCallNeverAsksPermissionOrCalls(){
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  type(a,"12");
  a.findViewById(R.id.btn_call).performClick();
  a.findViewById(R.id.btn_save_number).performClick();
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void restoredShortInputKeepsTheDisabledState(){
  ActivityController<DialpadActivity> c=Robolectric.buildActivity(DialpadActivity.class).setup();
  type(c.get(),"12");
  android.os.Bundle state=new android.os.Bundle();c.saveInstanceState(state);
  DialpadActivity restored=Robolectric.buildActivity(DialpadActivity.class).create(state).start().resume().visible().get();
  assertGate(restored,false);
 }
 @Test public void restoredCallableInputKeepsTheEnabledState(){
  ActivityController<DialpadActivity> c=Robolectric.buildActivity(DialpadActivity.class).setup();
  type(c.get(),"112");
  android.os.Bundle state=new android.os.Bundle();c.saveInstanceState(state);
  DialpadActivity restored=Robolectric.buildActivity(DialpadActivity.class).create(state).start().resume().visible().get();
  assertGate(restored,true);
 }
 @Test public void accessibilityActivationOfDisabledCallDoesNothing(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().putBoolean(Prefs.TOUCH_GUARD,true).apply();
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  type(a,"12");
  a.findViewById(R.id.btn_call).performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
}
