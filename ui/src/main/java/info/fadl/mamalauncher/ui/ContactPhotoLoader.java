package info.fadl.mamalauncher.ui;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Simple photo decode + tiny in-memory cache. Photos never leave the device. */
public class ContactPhotoLoader {
    private final ContentResolver resolver;
    private final Map<String, Bitmap> cache = new HashMap<>();

    public ContactPhotoLoader(ContentResolver resolver) { this.resolver = resolver; }

    public Bitmap load(String photoUri, int maxDim) {
        if (photoUri == null) return null;
        if (cache.containsKey(photoUri)) return cache.get(photoUri);
        Bitmap bmp = null;
        try (InputStream in = resolver.openInputStream(Uri.parse(photoUri))) {
            if (in != null) {
                Bitmap raw = BitmapFactory.decodeStream(in);
                if (raw != null) {
                    int w = raw.getWidth(), h = raw.getHeight();
                    int max = Math.max(w, h);
                    if (max > maxDim) {
                        float s = maxDim / (float) max;
                        bmp = Bitmap.createScaledBitmap(raw,
                                Math.max(1, Math.round(w * s)),
                                Math.max(1, Math.round(h * s)), true);
                    } else {
                        bmp = raw;
                    }
                }
            }
        } catch (Exception ignored) {
            bmp = null; // unreadable photo: fall back to the name tile
        }
        cache.put(photoUri, bmp);
        return bmp;
    }
}
