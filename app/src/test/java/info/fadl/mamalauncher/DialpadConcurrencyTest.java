package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.View;
import android.view.ViewGroup;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

/** Refresh and Save are separate consumers on separate workers: a refresh
 *  stuck in a blocked provider can never delay or swallow a Save lookup. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DialpadConcurrencyTest {
  android.app.Application app;

  @Before
  public void before() {
    app = RuntimeEnvironment.getApplication();
    Prefs.get(app).edit().clear().apply();
    Prefs.get(app).edit().putBoolean(Prefs.TOUCH_GUARD, false).apply();
    SessionGate.unlock();
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS);
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

  private View key(View v, String tag) {
    if (tag.equals(v.getTag())) return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View r = key(((ViewGroup) v).getChildAt(i), tag);
        if (r != null) return r;
      }
    return null;
  }

  @Test
  public void saveNeverQueuesBehindAStuckRefresh() throws Exception {
    CountDownLatch refreshStarted = new CountDownLatch(1), release = new CountDownLatch(1);
    AtomicInteger queries = new AtomicInteger();
    ShadowContentResolver.registerProviderInternal(
        ContactsContract.AUTHORITY,
        new FixtureContactsProvider() {
          @Override
          public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
            if (queries.incrementAndGet() == 1) {
              // The first read (the refresh) blocks like a stuck binder call.
              refreshStarted.countDown();
              boolean done = false;
              while (!done) {
                try {
                  release.await(30, TimeUnit.SECONDS);
                  done = true;
                } catch (InterruptedException ignored) {
                }
              }
            }
            MatrixCursor c = new MatrixCursor(COLUMNS);
            c.addRow(new Object[] {5L, "Mama", "01000000000", 1, null, null, 0});
            return c;
          }
        });
    ActivityController<DialpadActivity> c =
        Robolectric.buildActivity(DialpadActivity.class).setup().visible();
    DialpadActivity a = c.get();
    assertTrue(refreshStarted.await(2, TimeUnit.SECONDS));
    try {
      View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
      for (char d : "01000000000".toCharArray()) {
        View k = key(root, String.valueOf(d));
        assertNotNull("digit " + d, k);
        k.performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      }
      a.findViewById(R.id.btn_save_number).performClick();

      // Save must complete on its own worker even though the refresh is stuck.
      Intent started = null;
      for (int i = 0; i < 150 && started == null; i++) {
        Thread.sleep(20);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        started = Shadows.shadowOf(a).getNextStartedActivity();
      }
      assertNotNull("Save must not queue behind the stuck refresh", started);
      assertEquals(ConfirmCallActivity.class.getName(), started.getComponent().getClassName());
      assertEquals(5, started.getLongExtra("id", 0));
    } finally {
      release.countDown(); // free the stuck refresh thread
      c.pause().stop().destroy();
    }
  }

  @Test
  public void failingRefreshNeverWipesVerifiedMatches() throws Exception {
    java.util.concurrent.atomic.AtomicBoolean failing = new java.util.concurrent.atomic.AtomicBoolean();
    ShadowContentResolver.registerProviderInternal(
        ContactsContract.AUTHORITY,
        new FixtureContactsProvider() {
          @Override
          public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
            if (failing.get()) return null; // provider now unreadable
            MatrixCursor c = new MatrixCursor(COLUMNS);
            c.addRow(new Object[] {5L, "Mama", "01000000000", 1, null, null, 0});
            c.addRow(new Object[] {6L, "Mama Home", "01000000001", 1, null, null, 0});
            return c;
          }
        });
    ActivityController<DialpadActivity> c =
        Robolectric.buildActivity(DialpadActivity.class).setup().visible();
    DialpadActivity a = c.get();
    View root = ((android.view.ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
    for (char d : "0100".toCharArray()) {
      key(root, String.valueOf(d)).performClick();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    for (int i = 0; i < 150; i++) {
      Thread.sleep(20);
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    android.widget.LinearLayout matches = a.findViewById(R.id.dial_matches);
    assertTrue("verified matches render", matches.getChildAt(0) instanceof android.widget.Button);

    // The provider turns unreadable and a new refresh runs (leave + return).
    failing.set(true);
    c.pause().resume();
    for (int i = 0; i < 150; i++) {
      Thread.sleep(20);
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    assertTrue(
        "a failed refresh must not wipe verified matches",
        matches.getChildCount() > 0 && matches.getChildAt(0) instanceof android.widget.Button);
    c.pause().stop().destroy();
  }
}
