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
        return controller(7, "Mama", photo.toString());
    }

    private org.robolectric.android.controller.ActivityController<ConfirmCallActivity> controller(
            long id, String name, String uri) {
        ContactEntry entry = new ContactEntry(id, name,
                Collections.singletonList(new ContactEntry.PhoneNumber("01000000000", "")),
                uri, false);
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
        // First screen is really destroyed before the next one opens, and the
        // two screens show DIFFERENT contacts with different photos - so a
        // leaked decode from the dead screen would land on the wrong tile.
        // The wrong-contact/recycled-view identity cases are covered in
        // ContactPhotoLoaderTest.
        org.robolectric.android.controller.ActivityController<ConfirmCallActivity> first =
                controller();
        first.setup().get();
        settle();
        assertEquals(View.VISIBLE,
                first.get().findViewById(R.id.big_photo).getVisibility());
        first.destroy();

        Uri other = Uri.parse("content://fixture.photos/confirm2");
        Bitmap src = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        src.eraseColor(0xFF8A2F4E);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        src.compress(Bitmap.CompressFormat.PNG, 100, out);
        byte[] png2 = out.toByteArray();
        Shadows.shadowOf(app.getContentResolver())
                .registerInputStreamSupplier(other, () -> new ByteArrayInputStream(png2));
        ConfirmCallActivity second = controller(9, "Tant", other.toString()).setup().get();
        settle();
        ImageView bigPhoto = second.findViewById(R.id.big_photo);
        assertEquals(View.VISIBLE, bigPhoto.getVisibility());
        assertNotNull(bigPhoto.getDrawable());
        assertEquals(View.GONE, second.findViewById(R.id.big_photo_fallback).getVisibility());
    }
}
