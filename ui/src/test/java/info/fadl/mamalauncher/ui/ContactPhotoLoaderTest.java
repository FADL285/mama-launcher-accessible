package info.fadl.mamalauncher.ui;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** T-07: async photo loading identity, placeholder, invalidation and the
 *  bounded/expiring negative cache. The direct-executor seam makes every
 *  decode synchronous; delivery still goes through the main looper. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ContactPhotoLoaderTest {
    private android.app.Application app;
    private Handler main;
    private ContactPhotoLoader loader;
    private byte[] png;

    @Before
    public void before() {
        app = RuntimeEnvironment.getApplication();
        main = new Handler(Looper.getMainLooper());
        loader = new ContactPhotoLoader(app.getContentResolver(), Runnable::run, main);
        Bitmap src = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        src.eraseColor(0xFF2F5D8A);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        src.compress(Bitmap.CompressFormat.PNG, 100, out);
        png = out.toByteArray();
    }

    private static Uri uri(String name) {
        return Uri.parse("content://fixture.photos/" + name);
    }

    private void registerPhoto(Uri uri) {
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(uri, () -> new ByteArrayInputStream(png));
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void placeholderFirstThenPhotoAndCacheHitIsSynchronous() {
        Uri u = uri("a");
        registerPhoto(u);
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, u.toString(), 360, bmp -> delivered[0] = true);
        assertFalse("placeholder phase: decode finished but nothing applied before the frame",
                delivered[0]);
        idle();
        assertTrue(delivered[0]);

        ImageView second = new ImageView(app);
        boolean[] sync = {false};
        loader.loadAsync(second, 1, u.toString(), 360, bmp -> sync[0] = true);
        assertTrue("cache hit applies synchronously", sync[0]);
    }

    @Test
    public void recycledViewNeverGetsTheStalePhoto() {
        Uri u = uri("b");
        registerPhoto(u);
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, u.toString(), 360, bmp -> delivered[0] = true);
        // Row recycled to another contact before the decode landed.
        view.setTag(R.id.photo_request, "2|content://fixture.photos/other|360");
        idle();
        assertFalse("stale completion must not display", delivered[0]);
    }

    @Test
    public void photoLessRebindRetiresThePreviousRequest() {
        Uri u = uri("retire");
        registerPhoto(u);
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, u.toString(), 360, bmp -> delivered[0] = true);
        // Row rebound to a contact with no photo before the decode landed.
        loader.loadAsync(view, 2, null, 360, bmp -> {});
        idle();
        assertFalse("photo-less rebind must retire the old request", delivered[0]);
    }

    @Test
    public void refreshInvalidationDropsInFlightAndRecoversFailedUris() {
        Uri bad = uri("bad");
        // Robolectric decodes any bytes, so an unreadable photo is a failing stream.
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(bad,
                () -> { throw new IllegalStateException("photo gone"); });
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, bad.toString(), 360, bmp -> delivered[0] = true);
        idle();
        assertFalse("unreadable photo falls back to the name tile", delivered[0]);

        // The URI becomes readable inside the miss TTL: still skipped.
        registerPhoto(bad);
        loader.loadAsync(view, 2, bad.toString(), 360, bmp -> delivered[0] = true);
        idle();
        assertFalse("fresh negative cache still defers", delivered[0]);

        // Refresh invalidation clears the miss: the recovered URI loads.
        loader.invalidateAll();
        loader.loadAsync(view, 3, bad.toString(), 360, bmp -> delivered[0] = true);
        idle();
        assertTrue("failed-then-readable URI recovers after refresh", delivered[0]);
    }

    @Test
    public void refreshDropsACompletedButUndeliveredPhoto() {
        Uri u = uri("c");
        registerPhoto(u);
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, u.toString(), 360, bmp -> delivered[0] = true);
        loader.invalidateAll(); // refresh before the delivery frame
        idle();
        assertFalse("old-generation completion must not display", delivered[0]);
    }

    @Test
    public void refreshKeepsDecodedPhotosButRetriesMisses() {
        Uri good = uri("keep"), bad = uri("miss");
        registerPhoto(good);
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(bad,
                () -> { throw new IllegalStateException("photo gone"); });
        ImageView a = new ImageView(app), b = new ImageView(app);
        loader.loadAsync(a, 1, good.toString(), 360, bmp -> {});
        loader.loadAsync(b, 2, bad.toString(), 360, bmp -> {});
        idle();

        // Refresh start: in-flight retired, misses cleared, decoded kept.
        loader.retireRequests();
        registerPhoto(bad); // the URI recovered
        boolean[] goodSync = {false}, badLoaded = {false};
        loader.loadAsync(a, 1, good.toString(), 360, bmp -> goodSync[0] = true);
        assertTrue("decoded photo survives an unchanged refresh (no re-decode, no flash)", goodSync[0]);
        loader.loadAsync(b, 2, bad.toString(), 360, bmp -> badLoaded[0] = true);
        idle();
        assertTrue("cleared miss is retried on refresh", badLoaded[0]);
    }

    @Test
    public void retainUrisDropsOnlyDepartedPhotosAndNullDropsAll() {
        Uri stays = uri("stays"), left = uri("left");
        registerPhoto(stays);
        registerPhoto(left);
        ImageView a = new ImageView(app), b = new ImageView(app), c = new ImageView(app), d = new ImageView(app);
        loader.loadAsync(a, 1, stays.toString(), 360, bmp -> {});
        loader.loadAsync(b, 2, left.toString(), 360, bmp -> {});
        idle();

        loader.retainUris(new java.util.HashSet<>(java.util.Collections.singletonList(stays.toString())));
        boolean[] staysSync = {false}, leftSync = {false};
        loader.loadAsync(c, 1, stays.toString(), 360, bmp -> staysSync[0] = true);
        assertTrue("unchanged URI is still a cache hit", staysSync[0]);
        loader.loadAsync(d, 2, left.toString(), 360, bmp -> leftSync[0] = true);
        assertFalse("departed URI was evicted and must decode again", leftSync[0]);
        idle();
        assertTrue(leftSync[0]); // the re-decode completes normally

        loader.retainUris(null); // permission lost
        boolean[] afterRevoke = {false};
        loader.loadAsync(c, 1, stays.toString(), 360, bmp -> afterRevoke[0] = true);
        assertFalse("permission loss evicts everything", afterRevoke[0]);
    }

    @Test
    public void closedLoaderIgnoresNewRequestsQuietly() {
        Uri u = uri("closed");
        registerPhoto(u);
        loader.close();
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, u.toString(), 360, bmp -> delivered[0] = true); // must not throw
        idle();
        assertFalse("a closed loader never delivers", delivered[0]);
    }

    @Test
    public void recycledTargetIsSkippedBeforeItDecodes() throws Exception {
        java.util.concurrent.ExecutorService real = java.util.concurrent.Executors.newSingleThreadExecutor();
        ContactPhotoLoader live = new ContactPhotoLoader(app.getContentResolver(), real, main);
        Uri u = uri("fling");
        java.util.concurrent.atomic.AtomicInteger decodes = new java.util.concurrent.atomic.AtomicInteger();
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(u, () -> {
            decodes.incrementAndGet();
            return new ByteArrayInputStream(png);
        });
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        try {
            real.execute(() -> { // occupy the single worker so the request stays queued
                try { gate.await(2, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            });
            ImageView view = new ImageView(app);
            boolean[] delivered = {false};
            live.loadAsync(view, 1, u.toString(), 360, bmp -> delivered[0] = true);
            // The row is recycled to another contact while the request is queued.
            view.setTag(R.id.photo_request, "2|content://fixture.photos/other|360");
            gate.countDown();
            real.submit(() -> {}).get(2, java.util.concurrent.TimeUnit.SECONDS);
            idle();
            assertEquals("a recycled target must be skipped before decoding", 0, decodes.get());
            assertFalse(delivered[0]);
        } finally {
            gate.countDown();
            live.close();
        }
    }

    @Test
    public void gridAndConfirmationSizesAreSeparateCacheEntries() {
        Uri u = uri("sized");
        AtomicInteger opens = new AtomicInteger();
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(u, () -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(png);
        });
        ImageView a = new ImageView(app), b = new ImageView(app), c = new ImageView(app);
        loader.loadAsync(a, 1, u.toString(), 360, bmp -> {});
        idle();
        int afterFirst = opens.get();
        loader.loadAsync(b, 1, u.toString(), 360, bmp -> {});
        assertEquals("same size is a cache hit", afterFirst, opens.get());
        loader.loadAsync(c, 1, u.toString(), 900, bmp -> {});
        assertTrue("different size decodes again", opens.get() > afterFirst);
    }
}
