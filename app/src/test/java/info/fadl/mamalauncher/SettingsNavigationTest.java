package info.fadl.mamalauncher;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class SettingsNavigationTest {
 @Before public void reset(){Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().apply();SessionGate.unlock();}
 @Test public void landingDoesNotShowAnySectionControls(){
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
  assertFalse(a.findViewById(R.id.settings_apps_list).isShown());
  assertFalse(a.findViewById(R.id.switch_auto_speak).isShown());
  assertFalse(a.findViewById(R.id.btn_change_pin).isShown());
 }
 @Test public void eachSectionAndBackStayWithinAuthorizedActivity(){
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
  int[] controls={R.id.settings_apps_list,R.id.switch_auto_speak,R.id.btn_font_huge,R.id.perm_call_status,R.id.btn_change_pin};
  for(int i=0;i<controls.length;i++) {
   a.findViewById(info.fadl.mamalauncher.ui.SettingsSections.BUTTONS[i]).performClick();
   for(int j=0;j<controls.length;j++)assertEquals(i==j,a.findViewById(controls[j]).isShown());
   assertTrue(SessionGate.isValid());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
   a.onBackPressed();assertTrue(a.findViewById(R.id.settings_menu).isShown());
   assertFalse(a.isFinishing());assertTrue(SessionGate.isValid());
  }
  a.onBackPressed();assertTrue(a.isFinishing());
 }
 public static class TrackingSettings extends SettingsActivity {
  boolean requested; @Override public void recreate(){requested=true;}
 }
 @Test public void internalFontRecreationPreservesSectionButOrdinaryBackgroundLocks(){
  org.robolectric.android.controller.ActivityController<TrackingSettings> c=Robolectric.buildActivity(TrackingSettings.class).setup();
  c.get().findViewById(R.id.open_section_accessibility).performClick();
  c.get().findViewById(R.id.btn_font_huge).performClick();
  assertTrue(c.get().requested);
  c.recreate();
  assertTrue(c.get().findViewById(R.id.btn_font_huge).isShown());
  assertFalse(c.get().findViewById(R.id.settings_menu).isShown());assertTrue(SessionGate.isValid());
  c.pause().stop();assertFalse(SessionGate.isValid());
  c.restart().resume();
  assertTrue(c.get().isFinishing());
  assertEquals(PinGateActivity.class.getName(),Shadows.shadowOf(c.get()).getNextStartedActivity().getComponent().getClassName());
 }
}
