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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Photo decode + small byte-bounded cache. Photos never leave the device.
 *  Cache is capped (low-RAM pilot devices) and large images are subsampled
 *  before scaling so decoding cannot blow up memory.
 *
 *  T-07: decoding happens on a background thread. A request binds a
 *  placeholder immediately and delivers on the main thread only when the
 *  target view is still bound to the same contact id + URI + size (grid rows
 *  recycle, so adapter position is never an identity). Each owner
 *  (grid screen, confirmation screen) closes its loader on destroy; a
 *  refresh bumps the generation, and completions from an older generation
 *  neither display nor repopulate the caches. Failures land in a bounded,
 *  expiring negative cache so a failed-then-readable URI recovers. */
public class ContactPhotoLoader implements AutoCloseable {
    private static final int CACHE_BYTES = 6 * 1024 * 1024;
    /** Negative cache: at most 200 URIs, each remembered for one minute. */
    private static final int MAX_MISSES = 200;
    private static final long MISS_TTL_MS = 60_000;

    public interface Callback {
        /** Main thread. Only fires while the target still owns this request. */
        void onPhoto(Bitmap bitmap);
    }

    private final ContentResolver resolver;
    private final ExecutorService ownedExecutor;
    private final Executor background;
    private final Handler main;
    private final AtomicInteger generation = new AtomicInteger();
    /** Serializes generation bumps with cache writes (see loadAsync). */
    private final Object lock = new Object();

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
     *  golden screenshots capture exactly what a cache hit would show. */
    public ContactPhotoLoader(ContentResolver resolver, Executor background, Handler main) {
        this.resolver = resolver;
        if (background == null) {
            this.ownedExecutor = Executors.newSingleThreadExecutor();
            this.background = this.ownedExecutor;
        } else {
            this.ownedExecutor = null;
            this.background = background;
        }
        this.main = main;
    }

    /** Cache identity includes the decode size: the grid's 360px tile and the
     *  confirmation screen's 900px photo are different entries. */
    static String cacheKey(String photoUri, int maxDim) {
        return photoUri + "@" + maxDim;
    }

    /** Requests a photo for a target view. The caller has already bound the
     *  placeholder. A cache hit applies synchronously; a decode applies on
     *  the main thread after it finishes. Either way the bitmap is applied
     *  only if the target is still bound to this exact request. */
    public void loadAsync(View target, long contactId, String photoUri, int maxDim, Callback callback) {
        final String requestKey = contactId + "|" + photoUri + "|" + maxDim;
        // Retag even when there is no photo: a recycled row must never keep
        // the previous contact's request key.
        target.setTag(R.id.photo_request, requestKey);
        if (photoUri == null) return;
        final int gen = generation.get();
        final String key = cacheKey(photoUri, maxDim);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            callback.onPhoto(cached);
            return;
        }
        Long missedAt = misses.get(key);
        if (missedAt != null && System.currentTimeMillis() - missedAt < MISS_TTL_MS) return;
        background.execute(() -> {
            if (generation.get() != gen) return; // refresh made this queued work obsolete
            Bitmap bmp = load(photoUri, maxDim);
            // Generation check and cache write are one atomic step: a refresh
            // between them must not let old pixels or a stale miss into the
            // freshly cleared caches.
            synchronized (lock) {
                if (generation.get() != gen) return;
                if (bmp != null) cache.put(key, bmp);
                else { misses.remove(key); misses.put(key, System.currentTimeMillis()); }
            }
            if (bmp == null) return;
            main.post(() -> {
                if (generation.get() != gen) return;
                if (!requestKey.equals(target.getTag(R.id.photo_request))) return;
                callback.onPhoto(bmp);
            });
        });
    }

    /** Refresh/revoke invalidation: drops every cached and negative-cached
     *  entry and orphans in-flight decodes. Defined semantics: a contacts
     *  refresh re-decodes photos, because the same URI may now serve
     *  different pixels. */
    public void invalidateAll() {
        synchronized (lock) {
            generation.incrementAndGet();
            cache.evictAll();
            misses.clear();
        }
    }

    /** Stops deliveries and shuts the owned background thread down. */
    @Override public void close() {
        generation.incrementAndGet();
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
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
