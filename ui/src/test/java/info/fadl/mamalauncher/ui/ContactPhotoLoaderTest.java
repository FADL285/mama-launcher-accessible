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

/** T-07: async photo loading identity (contact id + URI + photo version +
 *  size), placeholder, refresh retire/retain semantics, the bounded/expiring
 *  negative cache, and the managed queue/abandon limits. The direct-executor
 *  seam makes every decode synchronous; delivery still goes through the main
 *  looper. */
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

    private static byte[] pngOf(int color) {
        Bitmap src = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        src.eraseColor(color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        src.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
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
        loader.loadAsync(view, 1, u.toString(), null, 360, bmp -> delivered[0] = true);
        assertFalse("placeholder phase: decode finished but nothing applied before the frame",
                delivered[0]);
        idle();
        assertTrue(delivered[0]);

        ImageView second = new ImageView(app);
        boolean[] sync = {false};
        loader.loadAsync(second, 1, u.toString(), null, 360, bmp -> sync[0] = true);
        assertTrue("cache hit applies synchronously", sync[0]);
    }

    @Test
    public void recycledViewNeverGetsTheStalePhoto() {
        Uri u = uri("b");
        registerPhoto(u);
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, u.toString(), null, 360, bmp -> delivered[0] = true);
        // Row recycled to another contact before the decode landed.
        view.setTag(R.id.photo_request, "2|content://fixture.photos/other|null|360");
        idle();
        assertFalse("stale completion must not display", delivered[0]);
    }

    @Test
    public void photoLessRebindRetiresThePreviousRequest() {
        Uri u = uri("retire");
        registerPhoto(u);
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, u.toString(), null, 360, bmp -> delivered[0] = true);
        // Row rebound to a contact with no photo before the decode landed.
        loader.loadAsync(view, 2, null, null, 360, bmp -> {});
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
        loader.loadAsync(view, 1, bad.toString(), null, 360, bmp -> delivered[0] = true);
        idle();
        assertFalse("unreadable photo falls back to the name tile", delivered[0]);

        // The URI becomes readable inside the miss TTL: still skipped.
        registerPhoto(bad);
        loader.loadAsync(view, 2, bad.toString(), null, 360, bmp -> delivered[0] = true);
        idle();
        assertFalse("fresh negative cache still defers", delivered[0]);

        // The real refresh sequence clears the miss: the recovered URI loads.
        loader.retireRequests();
        loader.retainUris(new java.util.HashSet<>(java.util.Collections.singletonList(
                ContactPhotoLoader.photoKey(bad.toString(), null))));
        loader.loadAsync(view, 3, bad.toString(), null, 360, bmp -> delivered[0] = true);
        idle();
        assertTrue("failed-then-readable URI recovers after refresh", delivered[0]);
    }

    @Test
    public void refreshDropsACompletedButUndeliveredPhoto() {
        Uri u = uri("c");
        registerPhoto(u);
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, u.toString(), null, 360, bmp -> delivered[0] = true);
        // The real refresh sequence lands before the delivery frame.
        loader.retireRequests();
        loader.retainUris(new java.util.HashSet<>(java.util.Collections.singletonList(
                ContactPhotoLoader.photoKey(u.toString(), null))));
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
        loader.loadAsync(a, 1, good.toString(), null, 360, bmp -> {});
        loader.loadAsync(b, 2, bad.toString(), null, 360, bmp -> {});
        idle();

        // Refresh start: in-flight retired, misses cleared, decoded kept.
        loader.retireRequests();
        registerPhoto(bad); // the URI recovered
        boolean[] goodSync = {false}, badLoaded = {false};
        loader.loadAsync(a, 1, good.toString(), null, 360, bmp -> goodSync[0] = true);
        assertTrue("decoded photo survives an unchanged refresh (no re-decode, no flash)", goodSync[0]);
        loader.loadAsync(b, 2, bad.toString(), null, 360, bmp -> badLoaded[0] = true);
        idle();
        assertTrue("cleared miss is retried on refresh", badLoaded[0]);
    }

    @Test
    public void retainUrisDropsOnlyDepartedPhotosAndNullDropsAll() {
        Uri stays = uri("stays"), left = uri("left");
        registerPhoto(stays);
        registerPhoto(left);
        ImageView a = new ImageView(app), b = new ImageView(app), c = new ImageView(app), d = new ImageView(app);
        loader.loadAsync(a, 1, stays.toString(), null, 360, bmp -> {});
        loader.loadAsync(b, 2, left.toString(), null, 360, bmp -> {});
        idle();

        loader.retainUris(new java.util.HashSet<>(java.util.Collections.singletonList(
                ContactPhotoLoader.photoKey(stays.toString(), null))));
        boolean[] staysSync = {false}, leftSync = {false};
        loader.loadAsync(c, 1, stays.toString(), null, 360, bmp -> staysSync[0] = true);
        assertTrue("unchanged photo key is still a cache hit", staysSync[0]);
        loader.loadAsync(d, 2, left.toString(), null, 360, bmp -> leftSync[0] = true);
        assertFalse("departed photo was evicted and must decode again", leftSync[0]);
        idle();
        assertTrue(leftSync[0]); // the re-decode completes normally

        loader.retainUris(null); // permission lost
        boolean[] afterRevoke = {false};
        loader.loadAsync(c, 1, stays.toString(), null, 360, bmp -> afterRevoke[0] = true);
        assertFalse("permission loss evicts everything", afterRevoke[0]);
    }

    @Test
    public void sameUriNewPhotoVersionDecodesAndServesFreshPixels() {
        Uri u = uri("edited");
        AtomicInteger opens = new AtomicInteger();
        byte[] before = pngOf(0xFF2F5D8A), after = pngOf(0xFF8A2F4E);
        byte[][] current = {before};
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(u, () -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(current[0]);
        });
        ImageView view = new ImageView(app);
        Bitmap[] first = {null};
        loader.loadAsync(view, 1, u.toString(), "1", 360, bmp -> first[0] = bmp);
        idle();
        int afterFirstDecode = opens.get();
        assertTrue("the first version decoded", afterFirstDecode > 0);

        // Same URI + same version: cache hit, no re-decode.
        ImageView again = new ImageView(app);
        boolean[] sync = {false};
        loader.loadAsync(again, 1, u.toString(), "1", 360, bmp -> sync[0] = true);
        assertTrue("unchanged version is a cache hit", sync[0]);
        assertEquals(afterFirstDecode, opens.get());

        // The contact's photo changed: the URI is identical (thumbnail-only
        // contacts) but the provider bumped PHOTO_ID.
        current[0] = after;
        loader.retireRequests();
        loader.retainUris(new java.util.HashSet<>(java.util.Collections.singletonList(
                ContactPhotoLoader.photoKey(u.toString(), "2"))));
        boolean[] staleSync = {false};
        loader.loadAsync(again, 1, u.toString(), "1", 360, bmp -> staleSync[0] = true);
        assertFalse("the old version was evicted with the refresh", staleSync[0]);
        Bitmap[] second = {null};
        loader.loadAsync(view, 1, u.toString(), "2", 360, bmp -> second[0] = bmp);
        assertNull("new version is a cache miss: decodes again", second[0]);
        assertTrue("same URI with a new version re-decodes", opens.get() > afterFirstDecode);
        idle();
        assertNotNull(second[0]);
        assertNotEquals("fresh pixels, not the stale photo",
                first[0].getPixel(0, 0), second[0].getPixel(0, 0));
    }

    @Test
    public void oldAdapterCompletionCannotRepopulateADepartedPhoto() throws Exception {
        java.util.concurrent.ExecutorService real = java.util.concurrent.Executors.newSingleThreadExecutor();
        ContactPhotoLoader live = new ContactPhotoLoader(app.getContentResolver(), real, main);
        Uri departed = uri("departed"), kept = uri("kept");
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger decodes = new AtomicInteger();
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(departed, () -> {
            decodes.incrementAndGet();
            try { gate.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            return new ByteArrayInputStream(png);
        });
        registerPhoto(kept);
        try {
            // Establish the old list's allowed set, then a refresh replaces it:
            // the departed photo is evicted and only the kept one may be cached.
            live.retainUris(new java.util.HashSet<>(java.util.Arrays.asList(
                    ContactPhotoLoader.photoKey(departed.toString(), null),
                    ContactPhotoLoader.photoKey(kept.toString(), null))));
            live.retireRequests();
            live.retainUris(new java.util.HashSet<>(java.util.Collections.singletonList(
                    ContactPhotoLoader.photoKey(kept.toString(), null))));

            // An old-adapter request for the departed photo, queued from before
            // the new list, is still decoding when it finishes.
            ImageView view = new ImageView(app);
            live.loadAsync(view, 1, departed.toString(), null, 360, bmp -> {});
            gate.countDown();
            live.awaitIdleForTest();
            idle();
            assertEquals(2, decodes.get()); // bounds pass + decode pass

            // The completion must NOT have repopulated the cache: the next
            // request decodes again instead of hitting.
            ImageView second = new ImageView(app);
            boolean[] sync = {false};
            live.loadAsync(second, 1, departed.toString(), null, 360, bmp -> sync[0] = true);
            assertFalse("a departed photo's late completion never re-enters the cache", sync[0]);
            live.awaitIdleForTest();
            assertEquals(4, decodes.get());
        } finally {
            gate.countDown();
            live.close();
            real.shutdownNow();
        }
    }

    @Test
    public void pendingQueueIsBoundedUnderRepeatedRefreshes() throws Exception {
        java.util.concurrent.ExecutorService real = java.util.concurrent.Executors.newSingleThreadExecutor();
        ContactPhotoLoader live = new ContactPhotoLoader(app.getContentResolver(), real, main);
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        java.util.Set<String> decodedUris = java.util.concurrent.ConcurrentHashMap.newKeySet();
        try {
            real.execute(() -> { // occupy the single worker so requests pile up
                try { gate.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            });
            int requested = 100;
            for (int i = 0; i < requested; i++) {
                Uri u = uri("q" + i);
                Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(u, () -> {
                    decodedUris.add(u.toString());
                    return new ByteArrayInputStream(png);
                });
                live.loadAsync(new ImageView(app), 1000 + i, u.toString(), null, 360, bmp -> {});
            }
            gate.countDown();
            live.awaitIdleForTest();
            idle();
            assertTrue("queued + running work is bounded: overflow requests are dropped",
                    decodedUris.size() > 0 && decodedUris.size() <= 64);
        } finally {
            gate.countDown();
            live.close();
            real.shutdownNow();
        }
    }

    @Test
    public void blockedDecodeIsAbandonedAndRecoversWhenTheStuckThreadReturns() throws Exception {
        // Owned worker: the loader may replace it when a decode gets stuck.
        ContactPhotoLoader live = new ContactPhotoLoader(app.getContentResolver());
        Uri stuck = uri("stuck"), fresh = uri("fresh");
        java.util.concurrent.CountDownLatch opened = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch freshDecoded = new java.util.concurrent.CountDownLatch(1);
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(stuck, () -> {
            opened.countDown();
            try { release.await(30, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            return new ByteArrayInputStream(png);
        });
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(fresh, () -> {
            freshDecoded.countDown();
            return new ByteArrayInputStream(png);
        });
        try {
            ImageView stuckView = new ImageView(app);
            live.loadAsync(stuckView, 1, stuck.toString(), null, 360, bmp -> {});
            assertTrue("decode started", opened.await(5, java.util.concurrent.TimeUnit.SECONDS));

            // The provider call never returns: after the decode timeout the
            // loader abandons the stuck worker and keeps serving requests.
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(11, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("stuck decode abandoned its worker", 1, live.abandonedForTest());

            ImageView freshView = new ImageView(app);
            boolean[] freshDelivered = {false};
            live.loadAsync(freshView, 2, fresh.toString(), null, 360, bmp -> freshDelivered[0] = true);
            assertTrue("fresh request decodes on the replacement worker",
                    freshDecoded.await(5, java.util.concurrent.TimeUnit.SECONDS));
            live.awaitIdleForTest(); // the delivery is posted before the task ends
            idle();
            assertTrue(freshDelivered[0]);

            // When the stuck thread finally returns, its slot is freed.
            release.countDown();
            for (int i = 0; i < 50 && live.abandonedForTest() != 0; i++) {
                Thread.sleep(50);
                idle();
            }
            assertEquals("returned stuck thread frees the abandoned slot", 0, live.abandonedForTest());
        } finally {
            release.countDown();
            live.close();
        }
    }

    @Test
    public void refreshDuringAnInFlightDecodeDoesNotLoseTheReboundPhoto() throws Exception {
        // A request is decoding when a refresh retires it. The new adapter
        // rebinds the same contact with an identical key: without a
        // generation-aware in-flight key the rebind would dedupe against the
        // retired request, whose completion then fails the generation check -
        // the tile would stay on its placeholder until another bind.
        java.util.concurrent.ExecutorService real = java.util.concurrent.Executors.newSingleThreadExecutor();
        ContactPhotoLoader live = new ContactPhotoLoader(app.getContentResolver(), real, main);
        Uri u = uri("refreshrace");
        java.util.concurrent.CountDownLatch decodeOpened = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch decodeRelease = new java.util.concurrent.CountDownLatch(1);
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(u, () -> {
            decodeOpened.countDown();
            try { decodeRelease.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            return new ByteArrayInputStream(png);
        });
        try {
            String key = ContactPhotoLoader.photoKey(u.toString(), null);
            live.retainUris(new java.util.HashSet<>(java.util.Collections.singletonList(key)));
            ImageView oldRow = new ImageView(app);
            live.loadAsync(oldRow, 7, u.toString(), null, 360, bmp -> {});
            assertTrue("first decode started", decodeOpened.await(5, java.util.concurrent.TimeUnit.SECONDS));

            // Refresh retires the in-flight request; the new adapter binds
            // the same contact again while the old decode is still blocked.
            live.retireRequests();
            live.retainUris(new java.util.HashSet<>(java.util.Collections.singletonList(key)));
            ImageView newRow = new ImageView(app);
            boolean[] delivered = {false};
            live.loadAsync(newRow, 7, u.toString(), null, 360, bmp -> delivered[0] = true);

            decodeRelease.countDown();
            live.awaitIdleForTest();
            idle();
            assertTrue("the rebound tile receives its photo after the refresh", delivered[0]);
        } finally {
            decodeRelease.countDown();
            live.close();
            real.shutdownNow();
        }
    }

    @Test
    public void abandonedWorkerHandsItsQueuedRequestsToTheReplacement() throws Exception {
        // Owned worker: a stuck decode replaces it. A still-current queued
        // request must move to the replacement worker and deliver to its
        // visible row, not be dropped with the old worker.
        ContactPhotoLoader live = new ContactPhotoLoader(app.getContentResolver());
        Uri stuck = uri("stuck-resubmit"), queued = uri("queued-resubmit");
        java.util.concurrent.CountDownLatch opened = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch queuedDecoded = new java.util.concurrent.CountDownLatch(1);
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(stuck, () -> {
            opened.countDown();
            try { release.await(30, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            return new ByteArrayInputStream(png);
        });
        Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(queued, () -> {
            queuedDecoded.countDown();
            return new ByteArrayInputStream(png);
        });
        try {
            ImageView stuckView = new ImageView(app);
            live.loadAsync(stuckView, 1, stuck.toString(), null, 360, bmp -> {});
            assertTrue("decode started", opened.await(5, java.util.concurrent.TimeUnit.SECONDS));
            ImageView queuedView = new ImageView(app);
            boolean[] queuedDelivered = {false};
            live.loadAsync(queuedView, 2, queued.toString(), null, 360, bmp -> queuedDelivered[0] = true);

            Shadows.shadowOf(Looper.getMainLooper()).idleFor(11, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("stuck decode abandoned its worker", 1, live.abandonedForTest());
            assertTrue("the queued request decodes on the replacement worker",
                    queuedDecoded.await(5, java.util.concurrent.TimeUnit.SECONDS));
            live.awaitIdleForTest();
            idle();
            assertTrue("the queued request still delivers to its visible row", queuedDelivered[0]);
        } finally {
            release.countDown();
            live.close();
        }
    }

    @Test
    public void closedLoaderIgnoresNewRequestsQuietly() {
        Uri u = uri("closed");
        registerPhoto(u);
        loader.close();
        ImageView view = new ImageView(app);
        boolean[] delivered = {false};
        loader.loadAsync(view, 1, u.toString(), null, 360, bmp -> delivered[0] = true); // must not throw
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
            live.loadAsync(view, 1, u.toString(), null, 360, bmp -> delivered[0] = true);
            // The row is recycled to another contact while the request is queued.
            view.setTag(R.id.photo_request, "2|content://fixture.photos/other|null|360");
            gate.countDown();
            real.submit(() -> {}).get(2, java.util.concurrent.TimeUnit.SECONDS);
            idle();
            assertEquals("a recycled target must be skipped before decoding", 0, decodes.get());
            assertFalse(delivered[0]);
        } finally {
            gate.countDown();
            live.close();
            real.shutdownNow();
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
        loader.loadAsync(a, 1, u.toString(), null, 360, bmp -> {});
        idle();
        int afterFirst = opens.get();
        loader.loadAsync(b, 1, u.toString(), null, 360, bmp -> {});
        assertEquals("same size is a cache hit", afterFirst, opens.get());
        loader.loadAsync(c, 1, u.toString(), null, 900, bmp -> {});
        assertTrue("different size decodes again", opens.get() > afterFirst);
    }
}
