package info.fadl.mamalauncher.ui;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** Photo decode + small byte-bounded cache. Photos never leave the device.
 *  Cache is capped (low-RAM pilot devices) and large images are subsampled
 *  before scaling so decoding cannot blow up memory. */
public class ContactPhotoLoader {
    private static final int CACHE_BYTES = 6 * 1024 * 1024;

    private final ContentResolver resolver;
    private final android.util.LruCache<String, Bitmap> cache =
            new android.util.LruCache<String, Bitmap>(CACHE_BYTES) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private final Set<String> misses = new HashSet<>();

    public ContactPhotoLoader(ContentResolver resolver) { this.resolver = resolver; }

    public Bitmap load(String photoUri, int maxDim) {
        if (photoUri == null) return null;
        Bitmap cached = cache.get(photoUri);
        if (cached != null) return cached;
        if (misses.contains(photoUri)) return null;

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
        if (bmp != null) cache.put(photoUri, bmp); else misses.add(photoUri);
        return bmp;
    }
}
