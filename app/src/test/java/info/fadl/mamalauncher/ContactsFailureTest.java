package info.fadl.mamalauncher;
import android.database.*;import android.net.Uri;import android.view.View;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;import java.util.concurrent.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class ContactsFailureTest {
 android.app.Application app;
 @Before public void before(){app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);}
 void provider(boolean fail,boolean nullCursor){org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){@Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){if(fail)throw new SecurityException("Synthetic revoked read");return nullCursor?null:new MatrixCursor(p);}});}
 void settle()throws Exception{for(int i=0;i<30;i++){Thread.sleep(10);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}}
 @Test public void failedReadNeverEscapesWorkerOrClaimsEmpty()throws Exception{
  java.util.List<Throwable> uncaught=new java.util.concurrent.CopyOnWriteArrayList<>();Thread.UncaughtExceptionHandler old=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler((t,e)->uncaught.add(e));
  try{provider(true,false);PhoneActivity a=Robolectric.buildActivity(PhoneActivity.class).setup().get();settle();assertTrue("Provider failure escaped worker: "+uncaught,uncaught.isEmpty());assertEquals(View.VISIBLE,a.findViewById(R.id.contacts_read_error).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.empty_contacts).getVisibility());provider(false,false);a.findViewById(R.id.btn_retry_contacts).performClick();settle();assertEquals(View.GONE,a.findViewById(R.id.contacts_read_error).getVisibility());assertEquals(View.VISIBLE,a.findViewById(R.id.empty_contacts).getVisibility());}finally{Thread.setDefaultUncaughtExceptionHandler(old);}
 }
 @Test public void nullCursorShowsRetryNotFalseEmpty()throws Exception{provider(false,true);PhoneActivity a=Robolectric.buildActivity(PhoneActivity.class).setup().get();settle();assertEquals(View.VISIBLE,a.findViewById(R.id.contacts_read_error).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.empty_contacts).getVisibility());}
 @Test public void permissionRevokedWhileReadIsPendingDiscardsLateResult()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){@Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}return new MatrixCursor(p);}});
  org.robolectric.android.controller.ActivityController<PhoneActivity> c=Robolectric.buildActivity(PhoneActivity.class).setup();PhoneActivity a=c.get();assertTrue(entered.await(2,TimeUnit.SECONDS));Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.READ_CONTACTS);c.pause().resume();release.countDown();settle();assertEquals(View.VISIBLE,a.findViewById(R.id.setup_view).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.contacts_grid).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.empty_contacts).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.contacts_read_error).getVisibility());
 }
}
