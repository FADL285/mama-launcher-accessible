package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ListView;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

/** The call history never depends on optional contact labels: rows render with
 *  numbers whatever the labels read does, a failed labels read offers a retry,
 *  and a callable row always opens confirmation - with the raw number when
 *  labels are unavailable. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HistoryLabelsTest {
  Application app;
  CallLogRepositoryTest.Fixture calls;

  @Before
  public void setup() {
    app = RuntimeEnvironment.getApplication();
    Prefs.get(app).edit().clear().apply();
    app.getSharedPreferences("missed_review_v1", 0).edit().clear().apply();
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
    calls = new CallLogRepositoryTest.Fixture();
    calls.count = 2;
    ShadowContentResolver.registerProviderInternal(android.provider.CallLog.AUTHORITY, calls);
  }

  private static final String[] COLUMNS = {
    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
    ContactsContract.CommonDataKinds.Phone.NUMBER,
    ContactsContract.CommonDataKinds.Phone.TYPE,
    ContactsContract.CommonDataKinds.Phone.LABEL,
    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
    ContactsContract.CommonDataKinds.Phone.STARRED
  };

  private void contactsProvider(FixtureContactsProvider p) {
    ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, p);
  }

  private void settle() throws Exception {
    for (int i = 0; i < 30; i++) {
      Thread.sleep(10);
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
  }

  @Test
  public void failedLabelsKeepRowsAndOfferAWorkingRetry() throws Exception {
    contactsProvider(
        new FixtureContactsProvider() {
          @Override
          public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
            return null; // a null cursor fails the labels read loudly
          }
        });
    ActivityController<PhoneActivity> c = Robolectric.buildActivity(PhoneActivity.class).setup();
    PhoneActivity a = c.get();
    a.findViewById(R.id.btn_tab_history).performClick();
    settle();

    ListView list = a.findViewById(R.id.history_list);
    assertEquals(View.VISIBLE, list.getVisibility());
    assertEquals("rows render with numbers even while labels failed", 2, list.getAdapter().getCount());
    assertEquals(View.VISIBLE, a.findViewById(R.id.history_labels_state).getVisibility());

    // Retry with a healthy provider: the banner goes and the name resolves.
    contactsProvider(
        new FixtureContactsProvider() {
          @Override
          public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
            MatrixCursor mc = new MatrixCursor(COLUMNS);
            mc.addRow(new Object[] {1L, "Mama", "01000000000", 1, null, null, 0});
            return mc;
          }
        });
    a.findViewById(R.id.btn_history_labels_retry).performClick();
    settle();
    assertEquals(View.GONE, a.findViewById(R.id.history_labels_state).getVisibility());
    assertEquals(2, list.getAdapter().getCount());
    c.pause().stop().destroy();
  }

  @Test
  public void blockedLabelsKeepRowsAndRowOpensWithRawNumber() throws Exception {
    CountDownLatch never = new CountDownLatch(1);
    contactsProvider(
        new FixtureContactsProvider() {
          @Override
          public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
            boolean released = false;
            while (!released) {
              try {
                never.await(30, TimeUnit.SECONDS);
                released = true;
              } catch (InterruptedException ignored) {
                // Like a blocked binder call: interruption does not free us.
              }
            }
            return new MatrixCursor(p);
          }
        });
    ActivityController<PhoneActivity> c = Robolectric.buildActivity(PhoneActivity.class).setup();
    PhoneActivity a = c.get();
    a.findViewById(R.id.btn_tab_history).performClick();
    try {
      settle(); // the page read completes; the labels read stays blocked
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(7));
      settle();

      ListView list = a.findViewById(R.id.history_list);
      assertEquals("blocked labels must never hide the call list", View.VISIBLE, list.getVisibility());
      assertEquals(2, list.getAdapter().getCount());
      assertEquals(View.VISIBLE, a.findViewById(R.id.history_labels_state).getVisibility());

      // Tapping a callable row still opens confirmation, with the raw number.
      list.getAdapter().getView(0, null, list).performClick();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(7));
      settle();
      Intent intent = Shadows.shadowOf(a).getNextStartedActivity();
      assertNotNull("a callable row opens confirmation even when labels are blocked", intent);
      assertEquals(ConfirmCallActivity.class.getName(), intent.getComponent().getClassName());
      assertEquals(-1, intent.getLongExtra("id", 0));
      assertEquals(
          java.util.Collections.singletonList("01000000000"),
          intent.getStringArrayListExtra("numbers"));
    } finally {
      never.countDown(); // free the stuck read threads
      c.pause().stop().destroy();
    }
  }
}
