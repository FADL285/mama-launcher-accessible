package info.fadl.mamalauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowKeyguardManager;

/**
 * Robolectric flow tests for the protected settings gate: real Activities,
 * real SharedPreferences, real view clicks - no emulator, no device, and no
 * way for any of this to place a call or touch real data. Run with
 * ./gradlew :app:testDebugUnitTest (also in CI).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GateFlowTest {

    private Context app;
    private SharedPreferences prefs;

    @Before
    public void reset() {
        app = RuntimeEnvironment.getApplication();
        prefs = Prefs.get(app);
        prefs.edit().clear().apply();
        SessionGate.lock();
    }

    private static void tapDigits(Activity a, String digits) {
        for (int i = 0; i < digits.length(); i++) {
            String d = String.valueOf(digits.charAt(i));
            View key = findTagged(a.findViewById(R.id.pin_pad), d);
            assertNotNull("pad key " + d, key);
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

    private static void presetPin(Context ctx, String pin) {
        String salt = "testsalt";
        Prefs.get(ctx).edit()
                .putString(Prefs.PIN_SALT, salt)
                .putString(Prefs.PIN_HASH, PinLock.hash(salt, pin))
                .apply();
    }

    // ---- first-run setup ----

    @Test
    public void firstEntryWithNoPinStartsSetup() {
        PinGateActivity a = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();
        assertEquals(a.getString(R.string.pingate_set_new),
                ((TextView) a.findViewById(R.id.pin_subtitle)).getText().toString());
        assertFalse(Prefs.hasPin(a));
    }

    @Test
    public void setupStoresOnlyAHashedPinAndOpensSettings() {
        PinGateActivity a = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();
        tapDigits(a, "1234");
        assertEquals(a.getString(R.string.pingate_confirm),
                ((TextView) a.findViewById(R.id.pin_subtitle)).getText().toString());
        tapDigits(a, "1234");
        assertTrue(Prefs.hasPin(a));
        String hash = prefs.getString(Prefs.PIN_HASH, null);
        assertNotNull(hash);
        assertFalse(hash.contains("1234")); // never plaintext
        assertTrue(PinLock.verify(prefs.getString(Prefs.PIN_SALT, ""), "1234", hash));
        assertTrue(SessionGate.isValid());
        ShadowActivity shadow = org.robolectric.shadow.api.Shadow.extract(a);
        Intent next = shadow.getNextStartedActivity();
        assertNotNull(next);
        assertEquals(SettingsActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void setupMismatchKeepsNoPinAndRestarts() {
        PinGateActivity a = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();
        tapDigits(a, "1234");
        tapDigits(a, "4321");
        assertFalse(Prefs.hasPin(a));
        assertEquals(a.getString(R.string.pingate_mismatch),
                ((TextView) a.findViewById(R.id.pin_error)).getText().toString());
        assertEquals(a.getString(R.string.pingate_set_new),
                ((TextView) a.findViewById(R.id.pin_subtitle)).getText().toString());
        assertFalse(SessionGate.isValid());
    }

    // ---- unlock ----

    @Test
    public void unlockWithCorrectPinResetsFailuresAndOpensSettings() {
        presetPin(app, "9999");
        prefs.edit().putInt(Prefs.PIN_FAILURES, 3).apply();
        PinGateActivity a = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();
        assertEquals(a.getString(R.string.pingate_enter),
                ((TextView) a.findViewById(R.id.pin_subtitle)).getText().toString());
        tapDigits(a, "9999");
        assertTrue(SessionGate.isValid());
        assertEquals(0, prefs.getInt(Prefs.PIN_FAILURES, -1));
        ShadowActivity shadow = org.robolectric.shadow.api.Shadow.extract(a);
        assertEquals(SettingsActivity.class.getName(),
                shadow.getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void wrongPinPersistsFailureAndShowsError() {
        presetPin(app, "9999");
        PinGateActivity a = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();
        tapDigits(a, "0000");
        assertEquals(1, prefs.getInt(Prefs.PIN_FAILURES, 0));
        assertEquals(a.getString(R.string.pingate_wrong),
                ((TextView) a.findViewById(R.id.pin_error)).getText().toString());
        assertFalse(SessionGate.isValid());
        ShadowActivity shadow = org.robolectric.shadow.api.Shadow.extract(a);
        assertNull(shadow.getNextStartedActivity());
    }

    @Test
    public void fifthWrongAttemptLocksTheGatePersistently() {
        presetPin(app, "9999");
        prefs.edit().putInt(Prefs.PIN_FAILURES, 4).apply();
        PinGateActivity a = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();
        tapDigits(a, "0000"); // 5th failure -> locked
        assertEquals(5, prefs.getInt(Prefs.PIN_FAILURES, 0));
        assertTrue(prefs.getLong(Prefs.PIN_LOCKOUT_UNTIL, 0) > System.currentTimeMillis());
        assertEquals(a.getString(R.string.pingate_locked),
                ((TextView) a.findViewById(R.id.pin_error)).getText().toString());
        // While locked, even the CORRECT pin is refused at the pad.
        tapDigits(a, "9999");
        assertFalse(SessionGate.isValid());
    }

    // ---- recovery ----

    @Test
    public void recoveryAppearsOnlyAfterFailureOnASecuredDevice() {
        presetPin(app, "9999");
        KeyguardManager km = (KeyguardManager) app.getSystemService(Context.KEYGUARD_SERVICE);
        ShadowKeyguardManager skm = org.robolectric.shadow.api.Shadow.extract(km);
        skm.setIsDeviceSecure(true);

        // No failure yet, toggle off: no device button.
        PinGateActivity fresh = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();
        assertEquals(View.GONE, fresh.findViewById(R.id.btn_device_lock).getVisibility());
        fresh.finish();

        prefs.edit().putInt(Prefs.PIN_FAILURES, 1).apply();
        PinGateActivity a = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.btn_device_lock).getVisibility());
    }

    @Test
    public void recoveryRequiresRealAuthSuccessCancelChangesNothing() {
        presetPin(app, "9999");
        prefs.edit().putInt(Prefs.PIN_FAILURES, 2).apply();
        PinGateActivity a = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();

        a.onActivityResult(51, Activity.RESULT_CANCELED, null);
        assertTrue(Prefs.hasPin(a)); // untouched
        assertEquals(2, prefs.getInt(Prefs.PIN_FAILURES, 0));

        a.onActivityResult(51, Activity.RESULT_OK, null);
        assertFalse(Prefs.hasPin(a)); // cleared, straight into choosing a new PIN
        assertEquals(0, prefs.getInt(Prefs.PIN_FAILURES, 0));
        assertEquals(a.getString(R.string.pingate_set_new),
                ((TextView) a.findViewById(R.id.pin_subtitle)).getText().toString());
        ShadowActivity shadow = org.robolectric.shadow.api.Shadow.extract(a);
        assertNull("recovery never opens settings directly", shadow.getNextStartedActivity());
    }

    @Test
    public void deviceUnlockTogglePathOpensSettingsOnAuthSuccess() {
        presetPin(app, "9999");
        prefs.edit().putBoolean(Prefs.USE_DEVICE_LOCK, true).apply();
        KeyguardManager km = (KeyguardManager) app.getSystemService(Context.KEYGUARD_SERVICE);
        ((org.robolectric.shadows.ShadowKeyguardManager)
                org.robolectric.shadow.api.Shadow.extract(km)).setIsDeviceSecure(true);
        PinGateActivity a = Robolectric.buildActivity(PinGateActivity.class,
                PinGateActivity.intentForEntry(app)).setup().get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.btn_device_lock).getVisibility());
        a.onActivityResult(51, Activity.RESULT_OK, null);
        assertTrue(Prefs.hasPin(a)); // PIN kept; this was a normal unlock
        ShadowActivity shadow = org.robolectric.shadow.api.Shadow.extract(a);
        Intent next = shadow.getNextStartedActivity();
        assertNotNull(next);
        assertEquals(SettingsActivity.class.getName(), next.getComponent().getClassName());
    }

    // ---- settings relock ----

    @Test
    public void settingsRejectsEntryWithoutAValidSession() {
        SessionGate.lock();
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        ShadowActivity shadow = org.robolectric.shadow.api.Shadow.extract(a);
        Intent next = shadow.getNextStartedActivity();
        assertNotNull(next);
        assertEquals(PinGateActivity.class.getName(), next.getComponent().getClassName());
        assertTrue(a.isFinishing());
    }

    @Test
    public void leavingSettingsLocksImmediately() {
        SessionGate.unlock();
        org.robolectric.android.controller.ActivityController<SettingsActivity> c =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        assertTrue(SessionGate.isValid());
        c.pause().stop(); // home / recents / screen-off / back all pass here
        assertFalse(SessionGate.isValid());
    }

    @Test
    public void sanctionedSystemDeepLinkSurvivesOneLeave() {
        SessionGate.unlock();
        org.robolectric.android.controller.ActivityController<SettingsActivity> c =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        SessionGate.expectExternal();
        c.pause().stop();
        assertTrue(SessionGate.isValid());
        c.restart().resume(); // back from the system screen
        assertFalse(SessionGate.externalInFlight()); // consumed
        c.pause().stop();
        assertFalse(SessionGate.isValid()); // next ordinary leave locks again
    }
}
