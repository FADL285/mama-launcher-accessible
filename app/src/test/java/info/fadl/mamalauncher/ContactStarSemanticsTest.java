package info.fadl.mamalauncher;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import static org.junit.Assert.*;
/** T-16: the star is per contact (D-02). Every toggle must agree with the pinned group. */
@RunWith(RobolectricTestRunner.class) @org.robolectric.annotation.Config(sdk={24,33})
public class ContactStarSemanticsTest {
 private Context app;
 private int providerWrites;
 @Before public void reset(){
  app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();SessionGate.unlock();providerWrites=0;
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){
   @Override public Cursor query(Uri uri,String[] projection,String s,String[] args,String sort) {
    MatrixCursor c=new MatrixCursor(projection);
    c.addRow(new Object[]{7L,"ماما","01000000001",2,null,null,1});
    c.addRow(new Object[]{7L,"ماما","01000000002",2,null,null,1});
    c.addRow(new Object[]{8L,"خالتي","02000000001",2,null,null,0});
    c.addRow(new Object[]{9L,"عمتي","03000000001",2,null,null,1});
    return c;
   }
   @Override public Uri insert(Uri u,ContentValues v){providerWrites++;return null;}
   @Override public int update(Uri u,ContentValues v,String s,String[] a){providerWrites++;return 0;}
   @Override public int delete(Uri u,String s,String[] a){providerWrites++;return 0;}
  });
  Shadows.shadowOf((android.app.Application)app).grantPermissions(android.Manifest.permission.READ_CONTACTS);
 }
 private View key(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){View r=key(((android.view.ViewGroup)v).getChildAt(i),tag);if(r!=null)return r;}return null;}
 private ContactEntry twoNumber(boolean starred){return new ContactEntry(7,"ماما",Arrays.asList(new ContactEntry.PhoneNumber("01000000001","موبايل"),new ContactEntry.PhoneNumber("01000000002","بيت")),null,starred);}
 private ConfirmCallActivity confirm(ContactEntry c){return Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,c)).setup().get();}
 private void select(ConfirmCallActivity a,String number){key(a.findViewById(R.id.numbers_container),number).performClick();}
 private String starText(ConfirmCallActivity a){return ((TextView)a.findViewById(R.id.btn_favorite)).getText().toString();}
 private void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}

 @Test public void systemStarredUnstarOneNumberUnpinsWholeContact(){
  ConfirmCallActivity a=confirm(twoNumber(true));
  select(a,"01000000001");
  assertEquals("★",starText(a)); // system star shows through before any local mark
  a.findViewById(R.id.btn_favorite).performClick();
  assertFalse(LocalFavorites.contains(app,7,true));
  assertFalse(LocalFavorites.apply(app,Arrays.asList(twoNumber(true))).get(0).starred);
  assertEquals("☆",starText(a));
 }
 @Test public void restarAnyNumberPinsTheWholeContactAgain(){
  ConfirmCallActivity a=confirm(twoNumber(true));
  select(a,"01000000001");a.findViewById(R.id.btn_favorite).performClick();
  select(a,"01000000002");assertEquals("☆",starText(a)); // same contact-level state on the other number
  a.findViewById(R.id.btn_favorite).performClick();
  assertTrue(LocalFavorites.contains(app,7,true));
  assertTrue(LocalFavorites.apply(app,Arrays.asList(twoNumber(true))).get(0).starred);
  assertEquals("★",starText(a));
 }
 @Test public void starOnFirstNumberShowsStarredOnSecond(){
  ConfirmCallActivity a=confirm(twoNumber(false));
  select(a,"01000000001");a.findViewById(R.id.btn_favorite).performClick();
  assertEquals("★",starText(a));
  select(a,"01000000002");
  assertEquals("★",starText(a)); // toggled on A, star shows for B
  assertTrue(LocalFavorites.contains(app,7,false));
 }
 @Test public void starTogglesWriteNothingToTheProviderAndStartNoIntents(){
  ConfirmCallActivity a=confirm(twoNumber(true));
  select(a,"01000000001");
  a.findViewById(R.id.btn_favorite).performClick();
  a.findViewById(R.id.btn_favorite).performClick();
  select(a,"01000000002");
  a.findViewById(R.id.btn_favorite).performClick();
  idle();
  assertEquals(0,providerWrites);
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void singleNumberEntryPinsTheWholeContact(){
  // Dialpad match rows and history rows carry only one of the contact's numbers.
  ConfirmCallActivity a=confirm(new ContactEntry(7,"ماما","01000000001",null,false));
  a.findViewById(R.id.btn_favorite).performClick(); // single number is preselected
  assertTrue(LocalFavorites.apply(app,Arrays.asList(twoNumber(false))).get(0).starred);
 }
 @Test public void numberEditInContactsAppKeepsTheContactMark(){
  LocalFavorites.set(app,7,true);
  ContactEntry edited=new ContactEntry(7,"ماما",Arrays.asList(new ContactEntry.PhoneNumber("01099999999","موبايل")),null,false);
  assertTrue(LocalFavorites.apply(app,Arrays.asList(edited)).get(0).starred);
  LocalFavorites.set(app,7,false);
  assertFalse(LocalFavorites.apply(app,Arrays.asList(edited)).get(0).starred);
 }
 @Test public void sameNumberUnderTwoNamesStaysIndependent(){
  ContactEntry first=new ContactEntry(8,"خالتي","02000000001",null,false);
  ContactEntry second=new ContactEntry(9,"عمتي","02000000001",null,false);
  LocalFavorites.set(app,8,true);
  List<ContactEntry> applied=LocalFavorites.apply(app,Arrays.asList(first,second));
  assertTrue(applied.get(0).starred);
  assertFalse(applied.get(1).starred);
 }
 @Test public void nonSystemStarredStarOneNumberPinsContact(){
  ConfirmCallActivity a=confirm(twoNumber(false));
  select(a,"01000000002");a.findViewById(R.id.btn_favorite).performClick();
  assertTrue(LocalFavorites.apply(app,Arrays.asList(twoNumber(false))).get(0).starred);
 }
 @Test public void recreationKeepsChosenNumberStarStateAndNeedsFreshCallPress(){
  ContactEntry c=new ContactEntry(7,"ماما",Arrays.asList(new ContactEntry.PhoneNumber("01000000001","A"),new ContactEntry.PhoneNumber("01000000002","B"),new ContactEntry.PhoneNumber("01000000003","C")),null,false);
  org.robolectric.android.controller.ActivityController<ConfirmCallActivity> ctl=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,c)).setup();
  ConfirmCallActivity a=ctl.get();
  select(a,"01000000002");a.findViewById(R.id.btn_favorite).performClick();
  android.os.Bundle state=new android.os.Bundle();ctl.saveInstanceState(state);
  ConfirmCallActivity b=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,c)).create(state).start().resume().visible().get();
  assertEquals("01000000002",((TextView)b.findViewById(R.id.selected_call_number)).getText().toString());
  assertEquals("★",starText(b));
  assertTrue(b.findViewById(R.id.btn_call).isEnabled());
  assertNull(Shadows.shadowOf(b).getNextStartedActivity()); // no call until a fresh press
 }
 @Test public void gridConfirmationToggleBackRefreshesPinnedOrdering() throws Exception {
  org.robolectric.android.controller.ActivityController<PhoneActivity> pc=Robolectric.buildActivity(PhoneActivity.class).setup();
  PhoneActivity phone=pc.get();
  android.widget.GridView grid=phone.findViewById(R.id.contacts_grid);
  for(int i=0;i<100 && (grid.getAdapter()==null||grid.getAdapter().getCount()==0);i++){idle();Thread.sleep(5);}
  assertEquals(9L,((ContactEntry)grid.getAdapter().getItem(0)).id); // system-starred pinned first
  assertEquals(7L,((ContactEntry)grid.getAdapter().getItem(1)).id); // ماما also system-starred
  // open the unpinned contact's tile and pin it from the confirmation screen
  int pos=-1;for(int i=0;i<grid.getAdapter().getCount();i++)if(((ContactEntry)grid.getAdapter().getItem(i)).id==8L)pos=i;
  assertTrue(pos>=2);
  grid.getAdapter().getView(pos,null,grid).performClick();idle();
  Intent open=Shadows.shadowOf(phone).getNextStartedActivity();
  assertEquals(ConfirmCallActivity.class.getName(),open.getComponent().getClassName());
  ConfirmCallActivity confirm=Robolectric.buildActivity(ConfirmCallActivity.class,open).setup().get();
  // single-number contact: preselected, star ready
  confirm.findViewById(R.id.btn_favorite).performClick();
  assertNull(Shadows.shadowOf(confirm).getNextStartedActivity());
  pc.pause();pc.resume();
  for(int i=0;i<100 && (grid.getAdapter()==null||((ContactEntry)grid.getAdapter().getItem(grid.getAdapter().getCount()-1)).id!=8L);i++){idle();Thread.sleep(5);}
  List<Long> order=new ArrayList<>();
  for(int i=0;i<grid.getAdapter().getCount();i++)order.add(((ContactEntry)grid.getAdapter().getItem(i)).id);
  assertEquals(Arrays.asList(9L,7L,8L),order); // 8 joined the pinned group, peers unaffected
  assertEquals(0,providerWrites);
 }
}
