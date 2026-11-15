package info.fadl.mamalauncher.ui;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Photo decode + small byte-bounded cache. Photos never leave the device.
 *  Cache is capped (low-RAM pilot devices) and large images are subsampled
 *  before scaling so decoding cannot blow up memory.
 *
 *  T-07: decoding happens on a background thread. A request binds a
 *  placeholder immediately and delivers on the main thread only when the
 *  target view is still bound to the same contact id + URI + photo version +
 *  size (grid rows recycle, so adapter position is never an identity). Each
 *  owner (grid screen, confirmation screen) closes its loader on destroy.
 *
 *  Photo identity is URI + provider photo version (PHOTO_ID): for
 *  thumbnail-only contacts the photo URI stays identical across photo
 *  changes, so the URI alone cannot detect a new photo. The version comes
 *  from the contacts query, costs nothing, and changes whenever the photo
 *  does - a refresh after a photo edit evicts the old pixels instead of
 *  serving them, while a refresh that changed nothing keeps every decoded
 *  entry (no placeholder flash, no re-decode).
 *
 *  A refresh retires in-flight work (generation bump + negative-cache clear)
 *  but KEEPS decoded photos. Queued work whose target was recycled is skipped
 *  before it decodes. Failures land in a bounded, expiring negative cache so
 *  a failed-then-readable URI recovers.
 *
 *  Managed resource limits (same policy as the call-log worker): queued +
 *  running requests are bounded (overflow is dropped; the row keeps its
 *  placeholder and re-requests on the next bind), one request per target is
 *  in flight at a time (rebinds dedupe), and a decode stuck past
 *  DECODE_TIMEOUT_MS - a provider binder call that ignores interrupts - is
 *  abandoned: its worker is replaced and its queued requests move to the
 *  replacement, so fresh requests keep flowing.
 *  Abandonment is capped; a stuck thread that finally returns frees a slot. */
public class ContactPhotoLoader implements AutoCloseable {
    private static final int CACHE_BYTES = 6 * 1024 * 1024;
    /** Negative cache: at most 200 URIs, each remembered for one minute. */
    private static final int MAX_MISSES = 200;
    private static final long MISS_TTL_MS = 60_000;
    /** Queued + running decode requests; beyond it new requests are dropped. */
    private static final int MAX_PENDING = 64;
    /** A decode still running after this is abandoned and its worker replaced. */
    private static final long DECODE_TIMEOUT_MS = 10_000;
    /** Managed resource limit for abandoned stuck decodes (see class doc). */
    private static final int MAX_ABANDONED = 3;

    public interface Callback {
        /** Main thread. Only fires while the target still owns this request. */
        void onPhoto(Bitmap bitmap);
    }

    private final ContentResolver resolver;
    /** Owned worker, replaced when a stuck decode is abandoned; null when the
     *  executor is injected (tests) - an injected executor is never shut down
     *  or replaced by the loader. */
    private volatile ExecutorService ownedWorker;
    /** Where decodes run: the owned worker, or the injected test executor. */
    private volatile Executor worker;
    private final Handler main;
    private final AtomicInteger generation = new AtomicInteger();
    /** Serializes generation bumps with cache writes (see loadAsync). */
    private final Object lock = new Object();
    private volatile boolean closed;
    /** Request keys currently queued or decoding (bounded by MAX_PENDING).
     *  Keys are generation-prefixed: a refresh must not let a retired request
     *  keep deduping the replacement bind - the old task would fail its
     *  generation check and the new tile would never load. */
    private final Set<String> inFlight = new HashSet<>();
    private int abandoned;
    /** Photo keys the latest contact list still carries; a decode for anything
     *  else must never reach the cache. Null until the first retainUris call
     *  (no list seen yet: everything is allowed), empty after permission loss. */
    private volatile Set<String> allowed;

    private final android.util.LruCache<String, Bitmap> cache =
            new android.util.LruCache<String, Bitmap>(CACHE_BYTES) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private final Map<String, Long> misses = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(MAX_MISSES + 1, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_MISSES;
                }
            });

    public ContactPhotoLoader(ContentResolver resolver) {
        this(resolver, null, new Handler(Looper.getMainLooper()));
    }

    /** Test seam: a direct background executor makes requests synchronous, so
     *  golden screenshots capture exactly what a cache hit would show. An
     *  injected executor is never shut down or replaced by the loader. */
    public ContactPhotoLoader(ContentResolver resolver, Executor background, Handler main) {
        this.resolver = resolver;
        if (background == null) {
            this.ownedWorker = Executors.newSingleThreadExecutor();
            this.worker = this.ownedWorker;
        } else {
            this.ownedWorker = null;
            this.worker = background;
        }
        this.main = main;
    }

    /** Photo identity: the URI plus the provider's photo version. Two photos
     *  for the same contact can share a URI, so the version is what separates
     *  "same photo as before" from "edited since". */
    public static String photoKey(String photoUri, String photoVersion) {
        return photoUri + "#" + (photoVersion == null ? "" : photoVersion);
    }

    /** Cache identity includes the decode size: the grid's 360px tile and the
     *  confirmation screen's 900px photo are different entries. */
    static String cacheKey(String photoUri, String photoVersion, int maxDim) {
        return photoKey(photoUri, photoVersion) + "@" + maxDim;
    }

    /** Requests a photo for a target view. The caller has already bound the
     *  placeholder. A cache hit applies synchronously; a decode applies on
     *  the main thread after it finishes. Either way the bitmap is applied
     *  only if the target is still bound to this exact request. */
    public void loadAsync(View target, long contactId, String photoUri, String photoVersion,
                          int maxDim, Callback callback) {
        final String requestKey = contactId + "|" + photoUri + "|" + photoVersion + "|" + maxDim;
        // Retag even when there is no photo: a recycled row must never keep
        // the previous contact's request key.
        target.setTag(R.id.photo_request, requestKey);
        if (photoUri == null) return;
        if (closed) return;
        final int gen = generation.get();
        final String flightKey = gen + "|" + requestKey;
        final String photoKey = photoKey(photoUri, photoVersion);
        final String key = photoKey + "@" + maxDim;
        Bitmap cached = cache.get(key);
        if (cached != null) {
            callback.onPhoto(cached);
            return;
        }
        Long missedAt = misses.get(key);
        if (missedAt != null && System.currentTimeMillis() - missedAt < MISS_TTL_MS) return;
        final AtomicBoolean running = new AtomicBoolean();
        final AtomicBoolean wasAbandoned = new AtomicBoolean();
        final Runnable[] watchdog = new Runnable[1];
        final Runnable[] self = new Runnable[1];
        synchronized (lock) {
            // Bound pending work: past the limit the request is dropped - the
            // row keeps its placeholder and re-requests on its next bind.
            if (inFlight.size() >= MAX_PENDING) return;
            // One request per target: a rebind of the same row dedupes here.
            if (!inFlight.add(flightKey)) return;
        }
        try {
            self[0] = () -> {
            running.set(true);
            if (ownedWorker != null) {
                watchdog[0] = () -> {
                    // The decode is still inside the provider after the
                    // timeout: the thread may never return (binder calls
                    // ignore interrupts), so abandon the worker and give
                    // later requests a fresh one - capped, like CallLogWorker.
                    if (!running.get()) return;
                    synchronized (lock) {
                        if (abandoned >= MAX_ABANDONED) return;
                        abandoned++;
                        wasAbandoned.set(true);
                        ExecutorService stuck = ownedWorker;
                        ExecutorService fresh = Executors.newSingleThreadExecutor();
                        ownedWorker = fresh;
                        worker = fresh;
                        // Queued-but-never-started tasks move to the fresh
                        // worker instead of being dropped with the stuck one:
                        // rows that stayed visible keep loading rather than
                        // holding their placeholders until a rebind. Each
                        // task re-checks the generation and its target tag
                        // before decoding and arms its own watchdog when it
                        // starts.
                        for (Runnable queued : stuck.shutdownNow()) {
                            fresh.execute(queued);
                        }
                    }
                };
                main.postDelayed(watchdog[0], DECODE_TIMEOUT_MS);
            }
            try {
                if (generation.get() != gen) return; // refresh made this queued work obsolete
                // A fling recycles rows faster than one worker decodes: never spend
                // the decode on a target that has since been rebound.
                if (!requestKey.equals(target.getTag(R.id.photo_request))) return;
                Bitmap bmp = load(photoUri, maxDim);
                // Generation check and cache write are one atomic step: a refresh
                // between them must not let old pixels or a stale miss into the
                // freshly cleared caches. The allowed photo-key set is applied
                // at write time too: a request queued from the previous contact
                // list can finish after the new list arrived, and a departed
                // photo must never repopulate the cache (it still delivers to a
                // view that is genuinely still bound to it).
                synchronized (lock) {
                    if (generation.get() != gen) return;
                    Set<String> allowedNow = allowed;
                    if (allowedNow == null || allowedNow.contains(photoKey)) {
                        if (bmp != null) cache.put(key, bmp);
                        else { misses.remove(key); misses.put(key, System.currentTimeMillis()); }
                    }
                }
                if (bmp == null) return;
                main.post(() -> {
                    if (generation.get() != gen) return;
                    if (!requestKey.equals(target.getTag(R.id.photo_request))) return;
                    callback.onPhoto(bmp);
                });
            } finally {
                running.set(false);
                if (watchdog[0] != null) main.removeCallbacks(watchdog[0]);
                synchronized (lock) { inFlight.remove(flightKey); }
                if (wasAbandoned.get()) {
                    // A stuck thread that returned frees its slot.
                    main.post(() -> {
                        synchronized (lock) { if (abandoned > 0) abandoned--; }
                    });
                }
            }
        };
            worker.execute(self[0]);
        } catch (java.util.concurrent.RejectedExecutionException shutDown) {
            // close() or abandonment raced the enqueue: nothing to deliver.
            synchronized (lock) { inFlight.remove(flightKey); }
        }
    }

    /**
     * Refresh start: orphan in-flight decodes and forget recorded misses so a
     * recovered URI is retried - while keeping every decoded photo, so a
     * refresh that changes nothing causes no placeholder flash or re-decode.
     */
    public void retireRequests() {
        synchronized (lock) {
            generation.incrementAndGet();
            misses.clear();
        }
    }

    /**
     * After a refresh: drop decoded entries whose photo key (URI + provider
     *  photo version) is no longer in the contact list - the photo changed
     *  (same URI, new version), or the contact left. Also constrains every
     *  later cache write to this set, so a request queued from the old list
     *  cannot repopulate a departed photo. A null set means permission was
     *  lost - nothing may stay or be written.
     */
    public void retainUris(Set<String> photoKeys) {
        synchronized (lock) {
            if (photoKeys == null) {
                cache.evictAll();
                allowed = Collections.emptySet();
                return;
            }
            allowed = new HashSet<>(photoKeys);
            java.util.List<String> stale = new java.util.ArrayList<>();
            for (String key : cache.snapshot().keySet()) {
                String photoKey = key.substring(0, key.lastIndexOf('@'));
                if (!photoKeys.contains(photoKey)) stale.add(key);
            }
            for (String key : stale) cache.remove(key);
        }
    }

    /** Stops deliveries, rejects new requests, and shuts the owned background
     *  thread down. The flag and the generation bump are one atomic step with
     *  cache writes, so a completion racing close() cannot repopulate. */
    @Override public void close() {
        synchronized (lock) {
            closed = true;
            generation.incrementAndGet();
            inFlight.clear();
        }
        ExecutorService owned = ownedWorker;
        if (owned != null) owned.shutdownNow();
    }

    /** Test seam: how many stuck decode workers are currently abandoned. */
    int abandonedForTest() {
        synchronized (lock) { return abandoned; }
    }

    /** Test seam: wait until the current worker has drained. */
    void awaitIdleForTest() throws Exception {
        ExecutorService owned = ownedWorker;
        Executor w = worker;
        ExecutorService drain = owned != null ? owned
                : w instanceof ExecutorService ? (ExecutorService) w : null;
        if (drain != null) drain.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Synchronous decode with subsampling; null on any unreadable stream. */
    public Bitmap load(String photoUri, int maxDim) {
        Bitmap bmp = null;
        Uri uri = Uri.parse(photoUri);
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in != null) BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                int sample = 1;
                int max = Math.max(bounds.outWidth, bounds.outHeight);
                while (max / (sample * 2) >= maxDim) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                Bitmap raw;
                try (InputStream in2 = resolver.openInputStream(uri)) {
                    raw = in2 == null ? null : BitmapFactory.decodeStream(in2, null, opts);
                }
                if (raw != null) {
                    int w = raw.getWidth(), h = raw.getHeight();
                    int m = Math.max(w, h);
                    if (m > maxDim) {
                        float s = maxDim / (float) m;
                        bmp = Bitmap.createScaledBitmap(raw,
                                Math.max(1, Math.round(w * s)),
                                Math.max(1, Math.round(h * s)), true);
                        if (bmp != raw) raw.recycle();
                    } else {
                        bmp = raw;
                    }
                }
            }
        } catch (Exception ignored) {
            bmp = null; // unreadable photo: fall back to the name tile
        }
        return bmp;
    }
}
