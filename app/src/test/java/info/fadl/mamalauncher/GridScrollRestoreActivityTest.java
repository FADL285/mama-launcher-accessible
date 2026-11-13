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
/** T-13 activity level (CL-06; owner decision 2026-11-13, option 1): a
 *  120-contact grid keeps the user's scroll anchor - Back from confirmation
 *  shows the SAME first-visible contact, even after a pin-driven re-sort, and
 *  never scrolls to the opened contact. Fictional fixture contacts only. */
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
    for(long id=1;id<=COUNT;id++){
     c.addRow(new Object[]{id,"جهة "+String.format("%03d",id),"0100000"+String.format("%04d",id),Phone.TYPE_MOBILE,null,null,0});
     if(id>=80&&id<=95) // the scrolled-into band carries two numbers (multi-number target)
      c.addRow(new Object[]{id,"جهة "+String.format("%03d",id),"0100001"+String.format("%04d",id),Phone.TYPE_HOME,null,null,0});
    }
    return c;
   }});
 }
 private void settle()throws Exception{for(int i=0;i<60;i++){Thread.sleep(10);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}}
 private void layOut(GridView grid)throws Exception{
  // Robolectric window sizing is flaky; measure the grid directly.
  int w=View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY);
  int h=View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY);
  grid.measure(w,h);grid.layout(0,0,1080,1920);
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
 }
 private int indexOf(GridView grid,long id){
  for(int i=0;i<grid.getAdapter().getCount();i++)
   if(((ContactEntry)grid.getAdapter().getItem(i)).id==id)return i;
  return -1;
 }
 /** Scrolls a laid-out grid deep into the list and returns the contact actually
  *  shown first (the scroll anchor). */
 private ContactEntry scrollDeep(GridView grid)throws Exception{
  grid.setSelection(80);
  settle();layOut(grid);
  int pos=grid.getFirstVisiblePosition();
  assertTrue("the grid scrolled deep, got "+pos,pos>=60);
  assertTrue("rows are laid out below the anchor",grid.getChildCount()>21);
  return (ContactEntry)grid.getAdapter().getItem(pos);
 }
 /** Clicks a laid-out visible tile twenty rows below the anchor. */
 private ContactEntry openVisibleBelowAnchor(GridView grid){
  int pos=grid.getFirstVisiblePosition();
  ContactEntry opened=(ContactEntry)grid.getAdapter().getItem(pos+20);
  assertTrue(grid.getChildAt(20).performClick()); // a real laid-out tile opens confirmation
  return opened;
 }
 @Test public void backKeepsTheFirstVisibleContactWhenNothingChanged()throws Exception{
  ActivityController<PhoneActivity> ctl=Robolectric.buildActivity(PhoneActivity.class).setup().visible();
  PhoneActivity a=ctl.get();
  GridView grid=a.findViewById(R.id.contacts_grid);
  settle();layOut(grid);
  assertEquals(COUNT,grid.getAdapter().getCount());
  ContactEntry anchor=scrollDeep(grid);
  int anchorPos=grid.getFirstVisiblePosition();
  ContactEntry opened=openVisibleBelowAnchor(grid);
  assertTrue(anchor.id!=opened.id);
  ctl.pause().resume(); // Back: onResume -> refresh
  settle();layOut(grid);
  assertEquals(COUNT,grid.getAdapter().getCount());
  assertEquals("data unchanged, order unchanged",anchorPos,indexOf(grid,anchor.id));
  assertEquals("Back keeps the first-visible position, not the opened contact's row",
   anchorPos,grid.getFirstVisiblePosition());
  assertEquals("the same first-visible contact is shown",
   anchor.id,((ContactEntry)grid.getAdapter().getItem(grid.getFirstVisiblePosition())).id);
  assertTrue("the opened contact is below the viewport top",
   indexOf(grid,opened.id)>grid.getFirstVisiblePosition());
 }
 @Test public void backKeepsTheFirstVisibleContactAcrossAPinResort()throws Exception{
  ActivityController<PhoneActivity> ctl=Robolectric.buildActivity(PhoneActivity.class).setup().visible();
  PhoneActivity a=ctl.get();
  GridView grid=a.findViewById(R.id.contacts_grid);
  settle();layOut(grid);
  ContactEntry anchor=scrollDeep(grid);
  ContactEntry opened=openVisibleBelowAnchor(grid); // a two-number contact in the 80..95 band
  assertEquals("the opened contact has two numbers",2,opened.numbers.size());
  LocalFavorites.set(a,opened.id,true); // the pin happens on the confirmation screen
  ctl.pause().resume(); // Back: one return, one refresh
  settle();layOut(grid);
  assertEquals("the newly pinned contact leads the grid",0,indexOf(grid,opened.id));
  int first=grid.getFirstVisiblePosition();
  int anchorNew=indexOf(grid,anchor.id);
  assertTrue("the re-sort shifted the anchor down, it is not at the top",anchorNew>0);
  assertTrue("Back did not return to the top / the opened contact",first>0);
  assertTrue("the same first-visible contact stays on screen",
   first<=anchorNew && anchorNew<first+grid.getChildCount());
  assertTrue("the viewport is still deep in the list, got "+first,first>=60);
 }
 @Test public void recreationKeepsTheFirstVisibleContact()throws Exception{
  ActivityController<PhoneActivity> ctl=Robolectric.buildActivity(PhoneActivity.class).setup().visible();
  PhoneActivity a=ctl.get();
  GridView grid=a.findViewById(R.id.contacts_grid);
  settle();layOut(grid);
  ContactEntry anchor=scrollDeep(grid);
  openVisibleBelowAnchor(grid); // confirmation opened; Android kills PhoneActivity behind it
  android.os.Bundle state=new android.os.Bundle();
  ctl.saveInstanceState(state).pause().stop().destroy();
  PhoneActivity b=Robolectric.buildActivity(PhoneActivity.class)
   .create(state).start().postCreate(state).restoreInstanceState(state).resume().visible().get();
  settle();
  GridView grid2=b.findViewById(R.id.contacts_grid);
  layOut(grid2);
  assertEquals(COUNT,grid2.getAdapter().getCount());
  assertEquals("recreation keeps the same first-visible contact",
   anchor.id,((ContactEntry)grid2.getAdapter().getItem(grid2.getFirstVisiblePosition())).id);
 }
}
