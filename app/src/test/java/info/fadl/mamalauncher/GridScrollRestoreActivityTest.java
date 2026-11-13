package info.fadl.mamalauncher;
import android.content.Context;
import android.database.MatrixCursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.view.View;
import android.widget.GridView;
import info.fadl.mamalauncher.ui.ContactEntry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.Shadows;
import static org.junit.Assert.*;
/** T-13 activity level (CL-06): a 120-contact grid keeps the user's place -
 *  Back from confirmation shows the opened contact, even after a pin-driven
 *  re-sort moved it; fictional fixture contacts only. */
@RunWith(RobolectricTestRunner.class)
public class GridScrollRestoreActivityTest {
 private static final int COUNT=120;
 @Before public void reset(){
  Context app=RuntimeEnvironment.getApplication();
  Prefs.get(app).edit().clear().apply();
  ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.READ_CONTACTS);
  ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,new FixtureContactsProvider(){
   @Override public android.database.Cursor query(android.net.Uri uri,String[] projection,String selection,String[] args,String order){
    MatrixCursor c=new MatrixCursor(new String[]{Phone.CONTACT_ID,Phone.DISPLAY_NAME,Phone.NUMBER,Phone.TYPE,Phone.LABEL,Phone.PHOTO_URI,Phone.STARRED});
    for(long id=1;id<=COUNT;id++)
     c.addRow(new Object[]{id,"جهة "+String.format("%03d",id),"0100000"+String.format("%04d",id),Phone.TYPE_MOBILE,null,null,0});
    return c;
   }});
 }
 private void settle()throws Exception{for(int i=0;i<60;i++){Thread.sleep(10);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}}
 private int indexOf(GridView grid,long id){
  for(int i=0;i<grid.getAdapter().getCount();i++)
   if(((ContactEntry)grid.getAdapter().getItem(i)).id==id)return i;
  return -1;
 }
 @Test public void backRestoresTheOpenedContactEvenAfterAPinResort()throws Exception{
  ActivityController<PhoneActivity> ctl=Robolectric.buildActivity(PhoneActivity.class).setup();
  PhoneActivity a=ctl.get();
  settle();
  GridView grid=a.findViewById(R.id.contacts_grid);
  assertEquals(COUNT,grid.getAdapter().getCount());
  ContactEntry target=(ContactEntry)grid.getAdapter().getItem(80);
  View tile=grid.getAdapter().getView(80,null,grid);
  assertTrue(tile.performClick()); // opens the confirmation screen for contact #81
  ctl.pause().resume(); // Back: onResume -> refresh
  settle();
  assertEquals(80,indexOf(grid,target.id)); // data unchanged, order unchanged
  assertEquals("Back must restore the opened contact, not the top",80,grid.getSelectedItemPosition());
  // While "in confirmation" (target still the opened contact), pin ANOTHER
  // contact: the re-sort moves that contact up and shifts the target down.
  ContactEntry other=(ContactEntry)grid.getAdapter().getItem(90); // below the target: the re-sort shifts it
  LocalFavorites.set(a,other.id,true);
  ctl.pause().resume();
  settle();
  assertEquals("the newly pinned contact leads the grid",0,indexOf(grid,other.id));
  int shifted=indexOf(grid,target.id);
  assertTrue("the opened contact shifted down, got "+shifted,shifted>80);
  assertEquals("Back must follow the OPENED contact across the re-sort, not the fallback",
   shifted,grid.getSelectedItemPosition());
 }
 @Test public void recreationKeepsTheOpenedContact()throws Exception{
  ActivityController<PhoneActivity> ctl=Robolectric.buildActivity(PhoneActivity.class).setup();
  PhoneActivity a=ctl.get();
  settle();
  GridView grid=a.findViewById(R.id.contacts_grid);
  ContactEntry target=(ContactEntry)grid.getAdapter().getItem(80);
  grid.getAdapter().getView(80,null,grid).performClick(); // opened
  android.os.Bundle state=new android.os.Bundle();
  ctl.saveInstanceState(state).pause().stop().destroy(); // Android kills it
  PhoneActivity b=Robolectric.buildActivity(PhoneActivity.class)
   .create(state).start().postCreate(state).restoreInstanceState(state).resume().visible().get();
  settle();
  GridView grid2=b.findViewById(R.id.contacts_grid);
  assertEquals(COUNT,grid2.getAdapter().getCount());
  assertEquals("recreation must keep the opened contact's place",
   80,grid2.getSelectedItemPosition());
  assertEquals(80,indexOf(grid2,target.id));
 }
}
