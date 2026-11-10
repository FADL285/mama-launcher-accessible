package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.widget.ListView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HistoryFlowTest {
  Application app;
  CallLogRepositoryTest.Fixture provider;

  @Before
  public void setup() {
    app = RuntimeEnvironment.getApplication();
    Prefs.get(app).edit().clear().apply();
    app.getSharedPreferences("missed_review_v1", 0).edit().clear().apply();
    Shadows.shadowOf(app)
        .denyPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG);
    provider = new CallLogRepositoryTest.Fixture();
    provider.count = 2;
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
        android.provider.CallLog.AUTHORITY, provider);
  }

  void settle() throws Exception {
    for (int i = 0; i < 30; i++) {
      Thread.sleep(10);
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
  }

  @Test
  public void freshEntryIsContactsAndNoCallLogReadOrPrompt() {
    ActivityController<PhoneActivity> c = Robolectric.buildActivity(PhoneActivity.class).setup();
    PhoneActivity a = c.get();
    assertEquals(View.VISIBLE, a.findViewById(R.id.phone_contacts_panel).getVisibility());
    assertEquals(View.GONE, a.findViewById(R.id.phone_history_panel).getVisibility());
    assertEquals(0, provider.queries);
    assertNull(Shadows.shadowOf(a).getNextStartedActivity());
    c.pause().stop().destroy();
  }

  @Test
  public void notNowAndEmptyCallbackPreserveUsefulPhone() {
    ActivityController<PhoneActivity> c = Robolectric.buildActivity(PhoneActivity.class).setup();
    PhoneActivity a = c.get();
    a.findViewById(R.id.btn_tab_history).performClick();
    assertEquals(View.VISIBLE, a.findViewById(R.id.btn_allow_history).getVisibility());
    a.onRequestPermissionsResult(
        CallLogAccess.REQUEST, new String[] {Manifest.permission.READ_CALL_LOG}, new int[0]);
    assertFalse(Prefs.get(a).contains(CallLogAccess.DENIED));
    a.findViewById(R.id.btn_history_not_now).performClick();
    assertEquals(View.VISIBLE, a.findViewById(R.id.phone_contacts_panel).getVisibility());
    assertEquals(View.VISIBLE, a.findViewById(R.id.btn_dialpad).getVisibility());
    c.pause().stop().destroy();
  }

  @Test
  public void ordinaryResumePreservesHistoryButNewEntryResetsIt() {
    ActivityController<PhoneActivity> c = Robolectric.buildActivity(PhoneActivity.class).setup();
    PhoneActivity a = c.get();
    a.findViewById(R.id.btn_tab_history).performClick();
    c.pause().resume();
    assertEquals(View.VISIBLE, a.findViewById(R.id.phone_history_panel).getVisibility());
    c.newIntent(new Intent(app, PhoneActivity.class));
    assertEquals(View.VISIBLE, a.findViewById(R.id.phone_contacts_panel).getVisibility());
    c.pause().stop().destroy();
  }

  @Test
  public void successfulHistoryDisplaysAndPauseRevocationClearsCache() throws Exception {
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
    ActivityController<PhoneActivity> c = Robolectric.buildActivity(PhoneActivity.class).setup();
    PhoneActivity a = c.get();
    a.findViewById(R.id.btn_tab_history).performClick();
    settle();
    ListView list = a.findViewById(R.id.history_list);
    assertEquals(2, list.getAdapter().getCount());
    assertNull(Shadows.shadowOf(a).getNextStartedActivity());
    c.pause();
    assertNull(list.getAdapter());
    Shadows.shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG);
    c.resume();
    assertEquals(View.GONE, list.getVisibility());
    assertEquals(View.VISIBLE, a.findViewById(R.id.btn_allow_history).getVisibility());
    c.pause().stop().destroy();
  }

  @Test
  public void failedProviderDoesNotClaimEmptyOrAcknowledge() throws Exception {
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
    provider.fail = true;
    ActivityController<PhoneActivity> c = Robolectric.buildActivity(PhoneActivity.class).setup();
    PhoneActivity a = c.get();
    a.findViewById(R.id.btn_tab_history).performClick();
    settle();
    assertEquals(View.VISIBLE, a.findViewById(R.id.btn_history_retry).getVisibility());
    assertFalse(app.getSharedPreferences("missed_review_v1", 0).getBoolean("initialized", false));
    c.pause().stop().destroy();
  }

  @Test
  public void rowOpensOnlyConfirmationWithExactNumberAndNoFakeFavorite() throws Exception {
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
    ActivityController<PhoneActivity> c = Robolectric.buildActivity(PhoneActivity.class).setup();
    PhoneActivity a = c.get();
    a.findViewById(R.id.btn_tab_history).performClick();
    settle();
    ListView list = a.findViewById(R.id.history_list);
    list.getAdapter().getView(0, null, list).performClick();
    settle();
    Intent intent = Shadows.shadowOf(a).getNextStartedActivity();
    assertEquals(ConfirmCallActivity.class.getName(), intent.getComponent().getClassName());
    assertEquals(
        java.util.Collections.singletonList("01000000000"),
        intent.getStringArrayListExtra("numbers"));
    assertEquals(-1, intent.getLongExtra("id", 0));
    ActivityController<ConfirmCallActivity> confirm =
        Robolectric.buildActivity(ConfirmCallActivity.class, intent).setup();
    assertEquals(View.GONE, confirm.get().findViewById(R.id.btn_favorite).getVisibility());
    assertNull(Shadows.shadowOf(confirm.get()).getNextStartedActivity());
    confirm.pause().stop().destroy();
    c.pause().stop().destroy();
  }
}
