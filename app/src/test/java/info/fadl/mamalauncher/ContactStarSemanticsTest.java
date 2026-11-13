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
 private void settle(){for(int i=0;i<60;i++){idle();try{Thread.sleep(10);}catch(InterruptedException e){}}}
 private List<Long> gridOrder(android.widget.GridView grid){
  List<Long> order=new ArrayList<>();
  for(int i=0;i<grid.getAdapter().getCount();i++)order.add(((ContactEntry)grid.getAdapter().getItem(i)).id);
  return order;
 }
 private org.robolectric.android.controller.ActivityController<PhoneActivity> gridLoaded() throws Exception {
  org.robolectric.android.controller.ActivityController<PhoneActivity> pc=Robolectric.buildActivity(PhoneActivity.class).setup();
  android.widget.GridView grid=pc.get().findViewById(R.id.contacts_grid);
  for(int i=0;i<100 && (grid.getAdapter()==null||grid.getAdapter().getCount()==0);i++){idle();Thread.sleep(5);}
  return pc;
 }
 private ConfirmCallActivity openTile(PhoneActivity phone,long id){
  android.widget.GridView grid=phone.findViewById(R.id.contacts_grid);
  int pos=-1;for(int i=0;i<grid.getAdapter().getCount();i++)if(((ContactEntry)grid.getAdapter().getItem(i)).id==id)pos=i;
  assertTrue("contact "+id+" on the grid",pos>=0);
  grid.getAdapter().getView(pos,null,grid).performClick();idle();
  Intent open=Shadows.shadowOf(phone).getNextStartedActivity();
  assertEquals(ConfirmCallActivity.class.getName(),open.getComponent().getClassName());
  return Robolectric.buildActivity(ConfirmCallActivity.class,open).setup().get();
 }
 /** T-16 required combined row, 2-number: grid -> confirmation -> toggle ->
  *  switch number -> Back -> provable reorder, peers untouched, zero writes. */
 @Test public void twoNumberCombinedFlowSwitchBackReordersGrid() throws Exception {
  org.robolectric.android.controller.ActivityController<PhoneActivity> pc=gridLoaded();
  PhoneActivity phone=pc.get();
  android.widget.GridView grid=phone.findViewById(R.id.contacts_grid);
  assertEquals(Arrays.asList(9L,7L,8L),gridOrder(grid));
  ConfirmCallActivity confirm=openTile(phone,7L); // ماما: system-starred, two numbers
  select(confirm,"01000000001");
  confirm.findViewById(R.id.btn_favorite).performClick(); // un-star number 1
  assertEquals("☆",starText(confirm));
  select(confirm,"01000000002"); // switch: same contact-level state on number 2
  assertEquals("☆",starText(confirm));
  assertNull(Shadows.shadowOf(confirm).getNextStartedActivity());
  pc.pause();pc.resume(); // Back: the grid refreshes on resume
  for(int i=0;i<100 && !Arrays.asList(9L,8L,7L).equals(gridOrder(grid));i++){idle();Thread.sleep(5);}
  assertEquals(Arrays.asList(9L,8L,7L),gridOrder(grid)); // 7 left the pinned group, dropped below 8; 9 untouched
  assertEquals(0,providerWrites);
 }
 /** T-16 required combined row, 3-number, with a third-choice switch. */
 @Test public void threeNumberCombinedFlowSwitchBackReordersGrid() throws Exception {
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){
   @Override public Cursor query(Uri uri,String[] projection,String s,String[] args,String sort) {
    MatrixCursor c=new MatrixCursor(projection);
    c.addRow(new Object[]{7L,"ماما","01000000001",2,null,null,1});
    c.addRow(new Object[]{7L,"ماما","01000000002",2,null,null,1});
    c.addRow(new Object[]{8L,"خالتي","02000000001",2,null,null,0});
    c.addRow(new Object[]{9L,"عمتي","03000000001",2,null,null,1});
    c.addRow(new Object[]{10L,"بابا","04000000001",2,null,null,1});
    c.addRow(new Object[]{10L,"بابا","04000000002",2,null,null,1});
    c.addRow(new Object[]{10L,"بابا","04000000003",2,null,null,1});
    return c;
   }
   @Override public Uri insert(Uri u,ContentValues v){providerWrites++;return null;}
   @Override public int update(Uri u,ContentValues v,String s,String[] a){providerWrites++;return 0;}
   @Override public int delete(Uri u,String s,String[] a){providerWrites++;return 0;}
  });
  org.robolectric.android.controller.ActivityController<PhoneActivity> pc=gridLoaded();
  PhoneActivity phone=pc.get();
  android.widget.GridView grid=phone.findViewById(R.id.contacts_grid);
  assertEquals(Arrays.asList(10L,9L,7L,8L),gridOrder(grid));
  ConfirmCallActivity confirm=openTile(phone,10L);
  select(confirm,"04000000001");
  confirm.findViewById(R.id.btn_favorite).performClick(); // un-star via number 1
  select(confirm,"04000000003"); // third choice shows the same contact-level state
  assertEquals("☆",starText(confirm));
  assertNull(Shadows.shadowOf(confirm).getNextStartedActivity());
  pc.pause();pc.resume();
  for(int i=0;i<100 && !Arrays.asList(9L,7L,10L,8L).equals(gridOrder(grid));i++){idle();Thread.sleep(5);}
  assertEquals(Arrays.asList(9L,7L,10L,8L),gridOrder(grid)); // 10 moved out of the pinned group, 8 still last
  assertEquals(0,providerWrites);
 }
 /** T-16 recreation row: old activity destroyed, exact target + highlight
  *  restored, and no call until a fresh deliberate press. */
 @Test public void recreationAfterDestroyKeepsSecondChoiceTargetHighlightAndNeedsFreshPress(){
  recreationAfterDestroyRow("01000000002");
 }
 @Test public void recreationAfterDestroyKeepsThirdChoiceTargetHighlightAndNeedsFreshPress(){
  recreationAfterDestroyRow("01000000003");
 }
 private void recreationAfterDestroyRow(String target){
  ContactEntry c=new ContactEntry(7,"ماما",Arrays.asList(new ContactEntry.PhoneNumber("01000000001","A"),new ContactEntry.PhoneNumber("01000000002","B"),new ContactEntry.PhoneNumber("01000000003","C")),null,false);
  org.robolectric.android.controller.ActivityController<ConfirmCallActivity> ctl=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,c)).setup();
  ConfirmCallActivity a=ctl.get();
  select(a,target);a.findViewById(R.id.btn_favorite).performClick();
  android.os.Bundle state=new android.os.Bundle();ctl.saveInstanceState(state);ctl.pause().stop().destroy();
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CALL_PHONE);
  ConfirmCallActivity b=Robolectric.buildActivity(ConfirmCallActivity.class,ConfirmCallActivity.intentFor(app,c)).create(state).start().resume().visible().get();
  assertEquals(target,((TextView)b.findViewById(R.id.selected_call_number)).getText().toString());
  assertEquals("★",starText(b));
  View row=key(b.findViewById(R.id.numbers_container),target);
  assertEquals(R.drawable.btn_number_selected,Shadows.shadowOf(row.getBackground()).getCreatedFromResId());
  assertNull(Shadows.shadowOf(b).getNextStartedActivity()); // no call on restore
  b.findViewById(R.id.btn_call).performClick();
  Intent call=Shadows.shadowOf(b).getNextStartedActivity();
  assertEquals(Intent.ACTION_CALL,call.getAction());
  assertEquals("tel:"+target,call.getDataString()); // the exact restored target
 }
 /** T-16 entry-path row: a real dialpad match row carries one number; the star
  *  still pins the whole two-number contact on the grid. */
 @Test public void dialpadMatchRowStarPinsWholeTwoNumberContact(){
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){
   @Override public Cursor query(Uri uri,String[] projection,String s,String[] args,String sort) {
    MatrixCursor c=new MatrixCursor(projection);
    c.addRow(new Object[]{11L,"جدتي","05000000001",2,null,null,0});
    c.addRow(new Object[]{11L,"جدتي","05000000002",2,null,null,0});
    return c;
   }
   @Override public Uri insert(Uri u,ContentValues v){providerWrites++;return null;}
   @Override public int update(Uri u,ContentValues v,String s,String[] a){providerWrites++;return 0;}
   @Override public int delete(Uri u,String s,String[] a){providerWrites++;return 0;}
  });
  DialpadActivity d=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  settle();
  for(char ch:"05000000001".toCharArray())key(d.findViewById(R.id.dial_grid),String.valueOf(ch)).performClick();
  // Deterministic wait: flush the lookup worker, then run the posted UI steps.
  for(int i=0;i<20;i++){try{d.awaitLookupsForTest();}catch(Exception e){throw new AssertionError(e);}idle();}
  android.widget.LinearLayout box=d.findViewById(R.id.dial_matches);
  assertTrue("a clickable match row is shown (not the loading hint)",
   box.getChildCount()>0 && box.getChildAt(0) instanceof android.widget.Button);
  box.getChildAt(0).performClick();idle();
  Intent open=Shadows.shadowOf(d).getNextStartedActivity();
  assertEquals(ConfirmCallActivity.class.getName(),open.getComponent().getClassName());
  ConfirmCallActivity confirm=Robolectric.buildActivity(ConfirmCallActivity.class,open).setup().get();
  assertEquals("☆",starText(confirm)); // جدتي is not system-starred
  confirm.findViewById(R.id.btn_favorite).performClick();
  ContactEntry whole=new ContactEntry(11,"جدتي",Arrays.asList(new ContactEntry.PhoneNumber("05000000001","موبايل"),new ContactEntry.PhoneNumber("05000000002","بيت")),null,false);
  assertTrue(LocalFavorites.apply(app,Arrays.asList(whole)).get(0).starred);
  assertEquals(0,providerWrites);
 }
 /** T-16 entry-path row: a real history row carries one number; the star pins
  *  the whole two-number contact on the grid. */
 @Test public void historyRowStarPinsWholeTwoNumberContact() throws Exception {
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){
   @Override public Cursor query(Uri uri,String[] projection,String s,String[] args,String sort) {
    MatrixCursor c=new MatrixCursor(projection);
    c.addRow(new Object[]{11L,"جدتي","05000000001",2,null,null,0});
    c.addRow(new Object[]{11L,"جدتي","05000000002",2,null,null,0});
    return c;
   }
   @Override public Uri insert(Uri u,ContentValues v){providerWrites++;return null;}
   @Override public int update(Uri u,ContentValues v,String s,String[] a){providerWrites++;return 0;}
   @Override public int delete(Uri u,String s,String[] a){providerWrites++;return 0;}
  });
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.CallLog.AUTHORITY,new FixtureContactsProvider(){
   @Override public Cursor query(Uri uri,String[] projection,String s,String[] args,String sort) {
    MatrixCursor c=new MatrixCursor(projection);
    if(s!=null)return c; // the pending-missed snapshot has no rows here
    Object[] row=new Object[projection.length];
    for(int j=0;j<projection.length;j++)switch(projection[j]){
     case "_id":row[j]=1;break;
     case "number":row[j]="05000000001";break; // جدتي's first number
     case "date":row[j]=1000L;break;
     case "type":row[j]=1;break;
     case "presentation":row[j]=1;break;
     case "is_read":row[j]=1;break;
     default:row[j]=0;
    }
    c.addRow(row);
    return c;
   }
   @Override public Uri insert(Uri u,ContentValues v){providerWrites++;return null;}
   @Override public int update(Uri u,ContentValues v,String s,String[] a){providerWrites++;return 0;}
   @Override public int delete(Uri u,String s,String[] a){providerWrites++;return 0;}
  });
  Shadows.shadowOf((android.app.Application)app).grantPermissions(android.Manifest.permission.READ_CALL_LOG);
  PhoneActivity phone=Robolectric.buildActivity(PhoneActivity.class).setup().get();
  settle();
  phone.findViewById(R.id.btn_tab_history).performClick();
  android.widget.ListView list=phone.findViewById(R.id.history_list);
  // Condition-based wait: the history read runs on a worker thread.
  for(int i=0;i<200 && list.getAdapter()==null;i++){idle();try{Thread.sleep(10);}catch(InterruptedException e){}}
  assertNotNull("the history page loaded",list.getAdapter());
  assertEquals(1,list.getAdapter().getCount());
  list.getAdapter().getView(0,null,list).performClick(); // rows carry their own guarded click
  Intent open=null;
  for(int i=0;i<200 && open==null;i++){idle();try{Thread.sleep(10);}catch(InterruptedException e){}open=Shadows.shadowOf(phone).getNextStartedActivity();}
  assertNotNull("history row opens the confirmation screen",open);
  assertEquals(ConfirmCallActivity.class.getName(),open.getComponent().getClassName());
  ConfirmCallActivity confirm=Robolectric.buildActivity(ConfirmCallActivity.class,open).setup().get();
  assertEquals("☆",starText(confirm));
  confirm.findViewById(R.id.btn_favorite).performClick();
  ContactEntry whole=new ContactEntry(11,"جدتي",Arrays.asList(new ContactEntry.PhoneNumber("05000000001","موبايل"),new ContactEntry.PhoneNumber("05000000002","بيت")),null,false);
  assertTrue(LocalFavorites.apply(app,Arrays.asList(whole)).get(0).starred);
  assertEquals(0,providerWrites);
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
