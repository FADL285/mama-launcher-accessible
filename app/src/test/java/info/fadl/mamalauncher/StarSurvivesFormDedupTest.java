package info.fadl.mamalauncher;
import android.content.Context;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import static org.junit.Assert.*;
/** T-17 regression (Greptile, verified): a star made on the number form that
 *  dedup removes still pins the contact - structural with T-16's contact-level
 *  key, kept explicit. */
@RunWith(RobolectricTestRunner.class)
public class StarSurvivesFormDedupTest {
 private Context app;
 @Before public void reset(){app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();}
 @Test public void legacyStarOnTheRemovedInternationalFormStillPins(){
  Prefs.get(app).edit().putBoolean("favorite.1.+201001234567",true).apply(); // starred the +20 form
  List<ContactEntry> grouped=ContactsLoader.group(Arrays.asList(
   new ContactsLoader.RawRow(1,"ماما","01001234567","موبايل",null,false),
   new ContactsLoader.RawRow(1,"ماما","+201001234567","موبايل",null,false)),"EG");
  assertEquals(1,grouped.get(0).numbers.size()); // national form survived
  List<ContactEntry> shown=LocalFavorites.apply(app,grouped);
  assertTrue("the pin survives the dedup",shown.get(0).starred);
 }
}
