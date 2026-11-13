package info.fadl.mamalauncher;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.view.View;
import android.widget.GridView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
/** T-12: rapid navigation taps open exactly one confirmation screen per visit. */
@RunWith(RobolectricTestRunner.class) @Config(sdk={24,30,33})
public class NavigationDebounceTest {
 @Before public void reset(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().apply();SessionGate.unlock();
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){
   @Override public Cursor query(Uri uri,String[] projection,String s,String[] args,String sort) {
    MatrixCursor c=new MatrixCursor(projection);c.addRow(new Object[]{7,"اختبار","01000000001",2,null,null,0});return c;
   }
  });
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.READ_CONTACTS);
 }
 private View key(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){View r=key(((android.view.ViewGroup)v).getChildAt(i),tag);if(r!=null)return r;}return null;}
 private void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
 private View gridTile(PhoneActivity a) throws Exception {
  GridView grid=a.findViewById(R.id.contacts_grid);
  for(int i=0;i<100 && (grid.getAdapter()==null||grid.getAdapter().getCount()==0);i++){idle();Thread.sleep(5);}
  assertNotNull(grid.getAdapter());assertTrue(grid.getAdapter().getCount()>0);
  return grid.getAdapter().getView(0,null,grid);
 }
 @Test public void gridDoubleTapOpensOneConfirmation() throws Exception {
  org.robolectric.android.controller.ActivityController<PhoneActivity> c=Robolectric.buildActivity(PhoneActivity.class).setup();
  PhoneActivity a=c.get();View tile=gridTile(a);
  tile.performClick();tile.performClick();idle();
  Intent first=Shadows.shadowOf(a).getNextStartedActivity();
  assertNotNull(first);assertEquals(ConfirmCallActivity.class.getName(),first.getComponent().getClassName());
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void gridReentryAfterReturnStillNavigates() throws Exception {
  org.robolectric.android.controller.ActivityController<PhoneActivity> c=Robolectric.buildActivity(PhoneActivity.class).setup();
  PhoneActivity a=c.get();View tile=gridTile(a);
  tile.performClick();idle();
  assertEquals(ConfirmCallActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
  c.pause();c.resume();
  View again=gridTile(a);again.performClick();idle();
  assertEquals(ConfirmCallActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
 }
 @Test public void dialpadMatchRowDoubleTapOpensOneConfirmation() throws Exception {
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();
  for(char ch:"01000000001".toCharArray())key(a.findViewById(R.id.dial_grid),""+ch).performClick();
  for(int i=0;i<3;i++){a.awaitLookupsForTest();idle();}
  android.widget.LinearLayout box=a.findViewById(R.id.dial_matches);assertTrue(box.getChildCount()>0);
  View row=box.getChildAt(0);row.performClick();row.performClick();idle();
  Intent first=Shadows.shadowOf(a).getNextStartedActivity();
  assertNotNull(first);assertEquals(ConfirmCallActivity.class.getName(),first.getComponent().getClassName());
  assertNull(Shadows.shadowOf(a).getNextStartedActivity());
 }
}
