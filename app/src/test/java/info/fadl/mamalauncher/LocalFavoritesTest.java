package info.fadl.mamalauncher;
import android.content.*;
import android.view.*;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class LocalFavoritesTest {
 private Context app;
 @Before public void reset(){app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();}
 private ContactEntry one(long id,String name,boolean star){return new ContactEntry(id,name,"0100000000"+id,null,star);}
 @Test public void localOverridePinsButKeepsAllAndCanUnstarSystemDefault(){
  List<ContactEntry> all=Arrays.asList(one(1,"أحمد",true),one(2,"سارة",false),one(3,"خالد",false));
  LocalFavorites.set(app,1,false);LocalFavorites.set(app,2,true);
  List<ContactEntry> sorted=ContactSorter.sort(LocalFavorites.apply(app,all));assertEquals(3,sorted.size());assertEquals(2,sorted.get(0).id);
  assertFalse(LocalFavorites.contains(app,1,true));assertTrue(all.get(0).starred);
 }
 @Test public void multiNumberFavoriteNeverSelectsCallTarget(){
  ContactEntry c=new ContactEntry(9,"اختبار",Arrays.asList(new ContactEntry.PhoneNumber("01000000001","A"),new ContactEntry.PhoneNumber("01000000002","B")),null,false);
  LocalFavorites.set(app,9,true);
  ConfirmCallActivity a=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,c)).setup().get();
  assertFalse(a.findViewById(R.id.btn_favorite).isEnabled());assertFalse(a.findViewById(R.id.btn_call).isEnabled());assertEquals(View.VISIBLE,a.findViewById(R.id.favorite_hint).getVisibility());
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void singleNumberStarTogglesWithoutAnyExternalIntent(){
  ConfirmCallActivity a=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,one(4,"اختبار",false))).setup().get();
  a.findViewById(R.id.btn_favorite).performClick();assertTrue(LocalFavorites.contains(app,4,false));
  a.findViewById(R.id.btn_favorite).performClick();assertFalse(LocalFavorites.contains(app,4,false));assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
}
