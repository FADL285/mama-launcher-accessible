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
  public void readsAfterAnAbandonedOneStillRun() throws Exception {
    // A blocked read ignores interruption and keeps its thread; cancel() must
    // abandon that executor so the next read is not queued behind it. The
    // block here swallows interrupts the way a binder call would.
    CallLogWorker w = new CallLogWorker();
    CountDownLatch entered = new CountDownLatch(1), never = new CountDownLatch(1);
    w.read(
        signal -> {
          entered.countDown();
          boolean released = false;
          while (!released) {
            try {
              never.await(30, TimeUnit.SECONDS);
              released = true;
            } catch (InterruptedException ignored) {
              // Like a blocked binder call: interruption does not free us.
            }
          }
          return 1;
        },
        value -> {},
        () -> {});
    assertTrue(entered.await(1, TimeUnit.SECONDS));
    w.cancel();
    AtomicInteger successes = new AtomicInteger();
    try {
      w.read(signal -> 2, value -> successes.incrementAndGet(), () -> {});
      for (int i = 0; i < 50 && successes.get() == 0; i++) {
        Thread.sleep(20);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
      }
      assertEquals("the next read must not queue behind the abandoned one", 1, successes.get());
    } finally {
      never.countDown();
      w.close();
    }
  }

  @Test
  public void errorSurfacesAsFailureAndWorkerRecovers() throws Exception {
    // An Error (not just RuntimeException) must reach the failure callback -
    // an uncaught one would remove the watchdog and hang the view forever.
    CallLogWorker w = new CallLogWorker();
    AtomicInteger successes = new AtomicInteger(), failures = new AtomicInteger();
    w.read(
        signal -> {
          throw new OutOfMemoryError("synthetic");
        },
        value -> successes.incrementAndGet(),
        failures::incrementAndGet);
    for (int i = 0; i < 100 && failures.get() == 0; i++) {
      Thread.sleep(20);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    assertEquals("an Error must surface as failure, not a silent hang", 1, failures.get());
    w.read(signal -> 7, value -> successes.incrementAndGet(), failures::incrementAndGet);
    for (int i = 0; i < 100 && successes.get() == 0; i++) {
      Thread.sleep(20);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    assertEquals(1, successes.get());
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
