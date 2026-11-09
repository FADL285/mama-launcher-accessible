package info.fadl.mamalauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;

/**
 * Robolectric flow tests for the everyday screen, phone screen, dial pad and
 * call confirmation: real Activities and clicks on the JVM. Nothing here can
 * place a real call - intents are only inspected, never delivered.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LauncherFlowTest {

    private android.content.Context app;
    private SharedPreferences prefs;

    @Before
    public void reset() {
        app = RuntimeEnvironment.getApplication();
        prefs = Prefs.get(app);
        prefs.edit().clear().apply();
        SessionGate.lock();
    }

    private static ShadowActivity shadow(Activity a) {
        return org.robolectric.shadow.api.Shadow.extract(a);
    }

    // ---- everyday home ----

    private static LinearLayout tilesContainer(Activity a) {
        return a.findViewById(R.id.tiles_container);
    }

    @Test
    public void homeWithNoSelectionShowsOnlyThePhoneTile() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        LinearLayout box = tilesContainer(a);
        assertEquals(1, box.getChildCount()); // one row
        ViewGroup row = (ViewGroup) box.getChildAt(0);
        assertEquals(2, row.getChildCount()); // phone tile + balancing spacer
    }

    @Test
    public void homeShowsOnlyChosenInstalledApps() {
        // Our own package really has a launch intent in this test app, so it
        // stands in for an installed chosen app; a bogus package stands in
        // for an uninstalled one and must NOT render.
        prefs.edit().putString(AppSelection.PREF_KEY,
                app.getPackageName() + ",com.example.notinstalled").apply();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        LinearLayout box = tilesContainer(a);
        assertEquals(1, box.getChildCount());
        ViewGroup row = (ViewGroup) box.getChildAt(0);
        assertEquals(2, row.getChildCount()); // phone + one app, no bogus tile
    }

    @Test
    public void longPressingTheHeaderOpensTheGate() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue(a.findViewById(R.id.home_header).performLongClick());
        Intent next = shadow(a).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(PinGateActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void homeBackDoesNotExit() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.onBackPressed();
        assertFalse(a.isFinishing());
    }

    // ---- phone screen ----

    @Test
    public void phoneWithoutContactsPermissionShowsTheSetupCard() {
        PhoneActivity a = Robolectric.buildActivity(PhoneActivity.class).setup().get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.setup_view).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.contacts_grid).getVisibility());
    }

    @Test
    public void phoneWithPermissionShowsGridAndEmptyHint() {
        ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.READ_CONTACTS);
        PhoneActivity a = Robolectric.buildActivity(PhoneActivity.class).setup().get();
        // contacts loader runs on a background thread; let it finish
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        try { Thread.sleep(300); } catch (InterruptedException ignored) { }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(View.GONE, a.findViewById(R.id.setup_view).getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.contacts_grid).getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.empty_contacts).getVisibility());
    }

    @Test
    public void permanentlyDeniedContactsPermissionOpensAppSettings() {
        PhoneActivity a = Robolectric.buildActivity(PhoneActivity.class).setup().get();
        ShadowActivity shadow = shadow(a);
        // Robolectric's default: no rationale shown, matching a permanent denial.
        prefs.edit().putBoolean("contacts_asked", true).apply();
        a.findViewById(R.id.btn_allow_contacts).performClick();
        Intent recovery = shadow.getNextStartedActivity();
        assertNotNull(recovery);
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                recovery.getAction());
    }

    @Test
    public void firstContactsAskStillRequestsThePermission() {
        PhoneActivity a = Robolectric.buildActivity(PhoneActivity.class).setup().get();
        a.findViewById(R.id.btn_allow_contacts).performClick();
        Intent req = shadow(a).getNextStartedActivity();
        assertNotNull(req);
        assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", req.getAction());
    }

    // ---- dial pad ----

    private static void tapDial(Activity a, String digits) {
        for (int i = 0; i < digits.length(); i++) {
            String d = String.valueOf(digits.charAt(i));
            View key = findTagged(a.findViewById(R.id.dial_grid), d);
            assertNotNull("dial key " + d, key);
            key.performClick();
        }
    }

    private static View findTagged(ViewGroup g, String tag) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (tag.equals(c.getTag())) return c;
            if (c instanceof ViewGroup) {
                View f = findTagged((ViewGroup) c, tag);
                if (f != null) return f;
            }
        }
        return null;
    }

    @Test
    public void dialpadComposesThenCallsDirectlyOnDeliberatePress() {
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CALL_PHONE);
        DialpadActivity a = Robolectric.buildActivity(DialpadActivity.class).setup().get();
        tapDial(a, "01000000000");
        assertEquals("01000000000",
                ((TextView) a.findViewById(R.id.dial_display)).getText().toString());
        a.findViewById(R.id.btn_backspace).performClick();
        assertEquals("0100000000",
                ((TextView) a.findViewById(R.id.dial_display)).getText().toString());
        a.findViewById(R.id.btn_call).performClick();
        Intent dial = shadow(a).getNextStartedActivity();
        assertNotNull(dial);
        assertEquals(Intent.ACTION_CALL, dial.getAction()); // direct call only after this deliberate press
        assertEquals("tel:0100000000", dial.getDataString());
    }

    @Test
    public void dialpadSaveOpensContactsInsertPrefilled() throws Exception {
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.READ_CONTACTS);
        FixtureContactsProvider provider=new FixtureContactsProvider(){
            @Override public android.database.Cursor query(android.net.Uri uri,String[] projection,String selection,String[] args,String sort){return new android.database.MatrixCursor(projection);}
        };
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,provider);
        DialpadActivity a = Robolectric.buildActivity(DialpadActivity.class).setup().get();
        tapDial(a, "0100");
        a.findViewById(R.id.btn_save_number).performClick();
        Thread.sleep(150);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Intent insert = shadow(a).getNextStartedActivity();
        assertNotNull(insert);
        assertEquals(Intent.ACTION_INSERT, insert.getAction());
        assertEquals("0100",
                insert.getStringExtra(android.provider.ContactsContract.Intents.Insert.PHONE));
    }

    @Test
    public void dialpadDoesNothingWithNoNumber() {
        DialpadActivity a = Robolectric.buildActivity(DialpadActivity.class).setup().get();
        a.findViewById(R.id.btn_call).performClick();
        a.findViewById(R.id.btn_save_number).performClick();
        assertNull(shadow(a).getNextStartedActivity());
    }

    // ---- call confirmation: permission discipline ----

    private static Intent confirmIntent(android.content.Context ctx) {
        info.fadl.mamalauncher.ui.ContactEntry c = new info.fadl.mamalauncher.ui.ContactEntry(
                7, "فاطمة", "01000000000", null, false);
        return ConfirmCallActivity.intentFor(ctx, c);
    }

    @Test
    public void confirmWithoutCallPermissionRequestsItAndCallsNothing() {
        ConfirmCallActivity a = Robolectric.buildActivity(ConfirmCallActivity.class,
                confirmIntent(app)).setup().get();
        org.robolectric.shadows.ShadowSystemClock.advanceBy(
                2, java.util.concurrent.TimeUnit.SECONDS); // Robolectric clock starts at 0
        a.findViewById(R.id.btn_call).performClick();
        ShadowActivity shadow = shadow(a);
        // Robolectric surfaces requestPermissions() as a started activity with
        // the platform's REQUEST_PERMISSIONS action: assert it is exactly that
        // (asking for CALL_PHONE) and never a call intent.
        android.content.Intent started = shadow.getNextStartedActivity();
        assertNotNull(started);
        assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", started.getAction());
        String[] wanted = started.getStringArrayExtra(
                "android.content.pm.extra.REQUEST_PERMISSIONS_NAMES");
        assertNotNull(wanted);
        assertEquals(android.Manifest.permission.CALL_PHONE, wanted[0]);
        assertNull("no second intent (and no ACTION_CALL)", shadow.getNextStartedActivity());
    }

    @Test
    public void confirmWithPermissionStartsExactlyOneCallIntent() {
        ShadowApplication.getInstance().grantPermissions(android.Manifest.permission.CALL_PHONE);
        ConfirmCallActivity a = Robolectric.buildActivity(ConfirmCallActivity.class,
                confirmIntent(app)).setup().get();
        org.robolectric.shadows.ShadowSystemClock.advanceBy(
                2, java.util.concurrent.TimeUnit.SECONDS); // first tap must pass the debounce
        a.findViewById(R.id.btn_call).performClick();
        a.findViewById(R.id.btn_call).performClick(); // shaky double tap: debounced
        ShadowActivity shadow = shadow(a);
        Intent call = shadow.getNextStartedActivity();
        assertNotNull(call);
        assertEquals(Intent.ACTION_CALL, call.getAction());
        assertEquals("tel:01000000000", call.getDataString());
        assertNull(shadow.getNextStartedActivity()); // no second call intent
    }

    @Test
    public void permissionGrantNeverCallsByItself() {
        ConfirmCallActivity a = Robolectric.buildActivity(ConfirmCallActivity.class,
                confirmIntent(app)).setup().get();
        a.onRequestPermissionsResult(42,
                new String[]{android.Manifest.permission.CALL_PHONE},
                new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});
        assertNull(shadow(a).getNextStartedActivity());
    }
}
