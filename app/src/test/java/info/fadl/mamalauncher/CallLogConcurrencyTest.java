package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CallLogConcurrencyTest {
  Context app;

  @Before
  public void setup() {
    app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("missed_review_v1", 0).edit().clear().apply();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .grantPermissions(Manifest.permission.READ_CALL_LOG);
  }

  @Test(expected = OperationCanceledException.class)
  public void olderQueryCannotPruneNewerAcknowledgment() {
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
        android.provider.CallLog.AUTHORITY,
        new CallLogRepositoryTest.Fixture() {
          @Override
          public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
            MissedCallState.acknowledge(app, new HashSet<>(Arrays.asList("900:100:3")));
            return super.query(u, p, s, a, o);
          }
        });
    try {
      MissedCallState.read(app, new CancellationSignal());
    } finally {
      assertTrue(
          app.getSharedPreferences("missed_review_v1", 0)
              .getStringSet("acknowledged", Collections.emptySet())
              .contains("900:100:3"));
    }
  }

  @Test(expected = IllegalStateException.class)
  public void resourceLimitedPendingNeverBaselinesPartial() {
    CallLogRepositoryTest.Fixture p = new CallLogRepositoryTest.Fixture();
    p.count = 10001;
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
        android.provider.CallLog.AUTHORITY, p);
    try {
      MissedCallState.read(app, new CancellationSignal());
    } finally {
      assertFalse(app.getSharedPreferences("missed_review_v1", 0).contains("initialized"));
    }
  }

  @Test
  public void timeoutPublishesFailureEvenWhenProviderDoesNotReturn() throws Exception {
    CallLogWorker w = new CallLogWorker();
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger(), failures = new AtomicInteger();
    w.read(
        signal -> {
          entered.countDown();
          try {
            release.await(2, TimeUnit.SECONDS);
          } catch (InterruptedException ignored) {
          }
          return 1;
        },
        value -> successes.incrementAndGet(),
        failures::incrementAndGet);
    assertTrue(entered.await(1, TimeUnit.SECONDS));
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(7));
    assertEquals(1, failures.get());
    release.countDown();
    Thread.sleep(30);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, successes.get());
    w.close();
  }

  @Test
  public void explicitCancelDropsStaleCallback() throws Exception {
    CallLogWorker w = new CallLogWorker();
    AtomicInteger calls = new AtomicInteger();
    w.read(signal -> 1, value -> calls.incrementAndGet(), calls::incrementAndGet);
    w.cancel();
    Thread.sleep(30);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, calls.get());
    w.close();
  }
}
