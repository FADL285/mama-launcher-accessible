package info.fadl.mamalauncher;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.ContactsContract;
import android.view.View;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowSystemClock;

/** T-06: contacts reads are cancellable and deadline-bounded; a slow or
 *  cancelled read fails loudly (or drops silently when superseded) and can
 *  never surface as "no contacts". */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ContactsPipelineTest {
    android.app.Application app;

    @Before
    public void before() {
        app = RuntimeEnvironment.getApplication();
        Prefs.get(app).edit().clear().apply();
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);
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

    private void provider(FixtureContactsProvider p) {
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, p);
    }

    private static FixtureContactsProvider cursorProvider(Cursor cursor) {
        return new FixtureContactsProvider() {
            @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
                return cursor;
            }
        };
    }

    private static MatrixCursor oneRow() {
        MatrixCursor c = new MatrixCursor(COLUMNS);
        c.addRow(new Object[] {1L, "Mama", "01000000000", 1, null, null, 0});
        return c;
    }

    private void settle() throws Exception {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(10);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
    }

    @Test
    public void deadlineExpiryFailsTheReadNeverClaimsEmpty() {
        // First row takes "6s" (clock advanced deterministically): past the
        // configured ContactsLoader.MAX_READ_MS deadline.
        MatrixCursor slow = new MatrixCursor(COLUMNS) {
            @Override public boolean onMove(int oldPosition, int newPosition) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(ContactsLoader.MAX_READ_MS + 1000));
                return true;
            }
        };
        slow.addRow(new Object[] {1L, "Mama", "01000000000", 1, null, null, 0});
        provider(cursorProvider(slow));
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            ContactsLoader.loadVerified(app.getContentResolver(), app.getResources(), "EG",
                    new CancellationSignal()));
        assertEquals("Contacts read timed out", e.getMessage());
    }

    @Test
    public void cancelledSignalAbortsMidLoopAndClosesCursor() {
        CancellationSignal signal = new CancellationSignal();
        MatrixCursor cancelling = new MatrixCursor(COLUMNS) {
            @Override public boolean onMove(int oldPosition, int newPosition) {
                signal.cancel(); // superseded while the first row is read
                return true;
            }
        };
        cancelling.addRow(new Object[] {1L, "Mama", "01000000000", 1, null, null, 0});
        provider(cursorProvider(cancelling));
        assertThrows(OperationCanceledException.class, () ->
            ContactsLoader.loadVerified(app.getContentResolver(), app.getResources(), "EG", signal));
        assertTrue("cursor must be closed on cancellation", cancelling.isClosed());
    }

    @Test
    public void supersededRefreshDropsTheStaleReadSilently() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger queries = new AtomicInteger();
        provider(new FixtureContactsProvider() {
            @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
                queries.incrementAndGet();
                firstEntered.countDown();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return oneRow();
            }
        });
        org.robolectric.android.controller.ActivityController<PhoneActivity> c =
                Robolectric.buildActivity(PhoneActivity.class).setup();
        PhoneActivity a = c.get();
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
        // Pause cancels the in-flight read; resume starts a fresh one. The
        // stale read must drop silently - never the error view, never a
        // clobbered grid.
        c.pause().resume();
        release.countDown();
        settle();
        assertTrue("resume must issue a fresh read", queries.get() >= 2);
        assertEquals(View.GONE, a.findViewById(R.id.contacts_read_error).getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.contacts_grid).getVisibility());
    }

    @Test
    public void historyLabelsUnavailableYieldNumberOnlyIndex() {
        provider(cursorProvider(oneRow()));
        CancellationSignal signal = new CancellationSignal();
        signal.cancel(); // caller was cancelled before/during the label read
        HistorySnapshot.ContactIndex index = HistorySnapshot.contacts(app, signal);
        assertNull("late labels must not invent a contact", index.find("01000000000"));
    }
}
