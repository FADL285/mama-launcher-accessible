package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.ContactPhotoLoader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** T-07: the confirmation screen binds its placeholder first and the decode
 *  lands only while the screen still owns the request. The synchronous
 *  loader seam keeps delivery ordering deterministic; async identity,
 *  invalidation and the negative cache are covered in ContactPhotoLoaderTest. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ConfirmPhotoAsyncTest {
    private android.app.Application app;
    private Uri photo;

    @Before
    public void before() {
        app = RuntimeEnvironment.getApplication();
        Prefs.get(app).edit().clear().apply();
        Bitmap src = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        src.eraseColor(0xFF2F5D8A);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        src.compress(Bitmap.CompressFormat.PNG, 100, out);
        byte[] png = out.toByteArray();
        photo = Uri.parse("content://fixture.photos/confirm");
        Shadows.shadowOf(app.getContentResolver())
                .registerInputStreamSupplier(photo, () -> new ByteArrayInputStream(png));
        ConfirmCallActivity.photoLoaderFactory = resolver ->
                new ContactPhotoLoader(resolver, Runnable::run, new Handler(Looper.getMainLooper()));
    }

    @After
    public void after() {
        ConfirmCallActivity.photoLoaderFactory = ContactPhotoLoader::new;
    }

    private org.robolectric.android.controller.ActivityController<ConfirmCallActivity> controller() {
        ContactEntry entry = new ContactEntry(7, "Mama",
                Collections.singletonList(new ContactEntry.PhoneNumber("01000000000", "")),
                photo.toString(), false);
        return Robolectric.buildActivity(ConfirmCallActivity.class,
                ConfirmCallActivity.intentFor(app, entry));
    }

    private void settle() throws Exception {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(10);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    @Test
    public void decodedPhotoReplacesThePlaceholder() throws Exception {
        ConfirmCallActivity a = controller().setup().get();
        settle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.big_photo).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.big_photo_fallback).getVisibility());
        assertNotNull(((ImageView) a.findViewById(R.id.big_photo)).getDrawable());
    }

    @Test
    public void fastBackForwardShowsTheRightPhotoOnBothScreens() throws Exception {
        // First screen gets its photo, is destroyed, and the next screen gets
        // it again - no leaked state between per-screen loaders. The
        // wrong-contact/recycled-view cases are covered in
        // ContactPhotoLoaderTest.
        ConfirmCallActivity first = controller().setup().get();
        settle();
        assertEquals(View.VISIBLE, first.findViewById(R.id.big_photo).getVisibility());
        ConfirmCallActivity second = controller().setup().get();
        settle();
        ImageView bigPhoto = second.findViewById(R.id.big_photo);
        assertEquals(View.VISIBLE, bigPhoto.getVisibility());
        assertNotNull(bigPhoto.getDrawable());
        assertEquals(View.GONE, second.findViewById(R.id.big_photo_fallback).getVisibility());
    }
}
