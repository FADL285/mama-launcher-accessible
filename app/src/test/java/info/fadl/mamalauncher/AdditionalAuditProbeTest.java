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
 @Test public void pickerMustBeAfterProtectionControls() {
  android.content.Context app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();SessionGate.unlock();
  SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
  android.view.View picker=a.findViewById(R.id.settings_apps_list),pin=a.findViewById(R.id.btn_change_pin);
  android.view.ViewGroup content=(android.view.ViewGroup)picker.getParent();while(pin.getParent()!=content)pin=(android.view.View)pin.getParent();
  assertTrue(content.indexOfChild(picker)>content.indexOfChild(pin));
 }
}
