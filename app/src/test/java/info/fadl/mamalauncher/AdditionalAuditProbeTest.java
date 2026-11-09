package info.fadl.mamalauncher;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class AdditionalAuditProbeTest {
 @Test public void legacySha1HashMustVerifyWithModernProvider() throws Exception {
  byte[] d=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(
   new javax.crypto.spec.PBEKeySpec("2468".toCharArray(),"salt".getBytes("UTF-8"),12000,256)).getEncoded();
  StringBuilder h=new StringBuilder();for(byte b:d)h.append(String.format("%02x",b));
  assertTrue(PinLock.verify("salt","2468",h.toString()));
 }
 @Test public void contrastClockMustUseVisibleSurfaceForeground() {
  android.content.Context app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().putBoolean(Prefs.HIGH_CONTRAST,true).apply();
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();
  assertEquals(a.getResources().getColor(R.color.contrast_text),((android.widget.TextView)a.findViewById(R.id.home_clock)).getCurrentTextColor());
 }
 @Test public void pickerCannotDisplaceProtectionBecauseSectionsAreSeparate() {
  android.content.Context app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();SessionGate.unlock();
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
  a.findViewById(R.id.open_section_protection).performClick();
  assertTrue(a.findViewById(R.id.btn_change_pin).isShown());
  assertFalse(a.findViewById(R.id.settings_apps_list).isShown());
 }
}
