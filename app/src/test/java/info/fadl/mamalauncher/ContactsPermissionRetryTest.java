package info.fadl.mamalauncher;
import android.Manifest;import android.content.Intent;import android.content.pm.PackageManager;import android.view.View;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={24,30,33})
public class ContactsPermissionRetryTest {
 @Before public void reset(){Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().apply();Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS);}
 private void tap(PhoneActivity a){a.findViewById(R.id.btn_allow_contacts).performClick();}
 private Intent next(PhoneActivity a){return Shadows.shadowOf(a).getNextStartedActivity();}
 // Framework dispatch normally clears this before invoking the callback.
 private void complete(PhoneActivity a,String[] permissions,int[] results){org.robolectric.util.ReflectionHelpers.setField(a,"mHasCurrentPermissionsRequest",false);a.onRequestPermissionsResult(41,permissions,results);}
 @Test public void legacyAskedFlagCannotRedirectRetryToSettings(){
  Prefs.get(RuntimeEnvironment.getApplication()).edit().putBoolean("contacts_asked",true).apply();PhoneActivity a=Robolectric.buildActivity(PhoneActivity.class).setup().get();tap(a);
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",next(a).getAction());
 }
 @Test public void denialKeepsExplicitRetryEvenWhenRationaleIsFalse(){
  PhoneActivity a=Robolectric.buildActivity(PhoneActivity.class).setup().get();tap(a);next(a);
  complete(a,new String[]{Manifest.permission.READ_CONTACTS},new int[]{PackageManager.PERMISSION_DENIED});
  assertNull(next(a));tap(a);assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",next(a).getAction());
 }
 @Test public void cancelledRequestCanBeRequestedAgain(){
  PhoneActivity a=Robolectric.buildActivity(PhoneActivity.class).setup().get();tap(a);next(a);complete(a,new String[0],new int[0]);tap(a);
  assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",next(a).getAction());
 }

 @Test public void settingsIsSeparateExplicitActionAfterCompletedDenial(){
  PhoneActivity a=Robolectric.buildActivity(PhoneActivity.class).setup().get();
  assertEquals(View.GONE,a.findViewById(R.id.btn_contacts_settings).getVisibility());
  complete(a,new String[]{Manifest.permission.READ_CONTACTS},new int[]{PackageManager.PERMISSION_DENIED});
  assertNull(next(a));assertEquals(View.VISIBLE,a.findViewById(R.id.btn_contacts_settings).getVisibility());
  assertEquals("طلب الإذن تاني",((android.widget.TextView)a.findViewById(R.id.btn_allow_contacts)).getText().toString());
  a.findViewById(R.id.btn_contacts_settings).performClick();Intent settings=next(a);
  assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,settings.getAction());assertEquals("package:"+a.getPackageName(),settings.getDataString());
 }
 @Test public void cancellationDoesNotShowSettingsAndDialpadStillWorks(){
  PhoneActivity a=Robolectric.buildActivity(PhoneActivity.class).setup().get();complete(a,new String[0],new int[0]);
  assertEquals(View.GONE,a.findViewById(R.id.btn_contacts_settings).getVisibility());a.findViewById(R.id.btn_dialpad).performClick();assertEquals(DialpadActivity.class.getName(),next(a).getComponent().getClassName());
 }
 @Test public void unrelatedPermissionCallbackDoesNotRecordContactsDenial(){
  PhoneActivity a=Robolectric.buildActivity(PhoneActivity.class).setup().get();a.onRequestPermissionsResult(99,new String[]{Manifest.permission.READ_CONTACTS},new int[]{PackageManager.PERMISSION_DENIED});
  assertFalse(Prefs.get(a).contains("contacts_denial_completed"));
 }

 @Test public void grantClearsRecoveryAndShowsGrid(){
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){@Override public android.database.Cursor query(android.net.Uri uri,String[] projection,String selection,String[] args,String order){return new android.database.MatrixCursor(projection);}});
  PhoneActivity a=Robolectric.buildActivity(PhoneActivity.class).setup().get();complete(a,new String[]{Manifest.permission.READ_CONTACTS},new int[]{PackageManager.PERMISSION_DENIED});
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CONTACTS);
  complete(a,new String[]{Manifest.permission.READ_CONTACTS},new int[]{PackageManager.PERMISSION_GRANTED});
  assertEquals(View.GONE,a.findViewById(R.id.setup_view).getVisibility());assertEquals(View.VISIBLE,a.findViewById(R.id.contacts_grid).getVisibility());assertFalse(Prefs.get(a).contains("contacts_denial_completed"));assertNull(next(a));
 }
}
