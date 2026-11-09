package info.fadl.mamalauncher;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.security.SecureRandom;

/**
 * The settings-mode gate. Two modes:
 *  - SETUP (no PIN yet, or "change PIN"): the family setter enters a new
 *    4-digit PIN twice; it is stored salted+hashed, never in plaintext.
 *  - UNLOCK: wrong attempts are counted and progressively lock the gate
 *    (30s after 5 failures, doubling, 10min cap) - enough to stop curious
 *    tapping without punishing the family setter. If the phone itself has a
 *    screen lock and the family enabled it, the device lock is accepted too.
 */
public class PinGateActivity extends BaseActivity {

    private static final String EXTRA_MODE = "mode";
    private static final int MODE_SETUP = 1;
    private static final int MODE_UNLOCK = 2;
    private static final int REQ_DEVICE_CRED = 51;
    private static final int PIN_LEN = 4;

    private int mode;
    private boolean recovery;
    private StringBuilder entered = new StringBuilder();
    private String firstEntry; // setup only
    private TextView subtitle;
    private TextView error;
    private LinearLayout dots;

    /** Entry from the everyday screen: SETUP when no PIN exists yet. */
    public static Intent intentForEntry(Context ctx) {
        Intent i = new Intent(ctx, PinGateActivity.class);
        i.putExtra(EXTRA_MODE, Prefs.hasPin(ctx) ? MODE_UNLOCK : MODE_SETUP);
        return i;
    }

    /** "Change PIN" from inside settings. */
    public static Intent intentForSetup(Context ctx) {
        Intent i = new Intent(ctx, PinGateActivity.class);
        i.putExtra(EXTRA_MODE, MODE_SETUP);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pingate);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_UNLOCK);

        subtitle = findViewById(R.id.pin_subtitle);
        error = findViewById(R.id.pin_error);
        dots = findViewById(R.id.pin_dots);

        guard(findViewById(R.id.btn_pin_back), this::finish);
        guard(findViewById(R.id.btn_pin_backspace), () -> {
            if (entered.length() > 0) entered.deleteCharAt(entered.length() - 1);
            syncDots();
        });
        wirePad(findViewById(R.id.pin_pad));

        // Device credential serves two deliberate roles: the family's chosen
        // unlock alternative, AND the recovery path when the PIN is forgotten
        // (offered once a wrong attempt proves the setter does not have it).
        // With no device lock at all there is intentionally no recovery -
        // the settings stay locked rather than opening to anyone holding the
        // phone; the last resort is clearing app data (loses configuration
        // only, never contacts), decided by the family, never advised lightly.
        refreshDeviceButton();

        subtitle.setText(mode == MODE_SETUP ? R.string.pingate_set_new : R.string.pingate_enter);
    }

    /** Recovery appears the moment the FIRST wrong attempt lands too: the
     *  setter who just mistyped must not have to leave and reopen the gate
     *  (mid-lockout included) to find the way back in. */
    private void refreshDeviceButton() {
        View deviceBtn = findViewById(R.id.btn_device_lock);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean deviceSecure = km != null && km.isDeviceSecure();
        recovery = mode == MODE_UNLOCK && !Prefs.useDeviceLock(this)
                && Prefs.get(this).getInt(Prefs.PIN_FAILURES, 0) > 0;
        if (mode == MODE_UNLOCK && deviceSecure
                && (Prefs.useDeviceLock(this) || recovery)) {
            deviceBtn.setVisibility(View.VISIBLE);
            guard(deviceBtn, () -> {
                Intent confirm = km.createConfirmDeviceCredentialIntent(
                        getString(R.string.pingate_title),
                        getString(R.string.device_lock_prompt));
                if (confirm != null) startActivityForResult(confirm, REQ_DEVICE_CRED);
            });
        } else {
            deviceBtn.setVisibility(View.GONE);
        }
    }

    private void wirePad(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) {
                wirePad((ViewGroup) child);
            } else if (child.getTag() != null) {
                final String digit = child.getTag().toString();
                guard(child, () -> onDigit(digit));
            }
        }
    }

    private void onDigit(String digit) {
        if (mode == MODE_UNLOCK && isLockedOut()) return;
        if (entered.length() >= PIN_LEN) return;
        entered.append(digit);
        syncDots();
        if (entered.length() == PIN_LEN) {
            if (mode == MODE_SETUP) onSetupComplete();
            else onUnlockAttempt();
        }
    }

    private void onSetupComplete() {
        if (firstEntry == null) {
            firstEntry = entered.toString();
            entered.setLength(0);
            subtitle.setText(R.string.pingate_confirm);
            error.setText("");
            syncDots();
            return;
        }
        if (!firstEntry.equals(entered.toString())) {
            firstEntry = null;
            entered.setLength(0);
            subtitle.setText(R.string.pingate_set_new);
            error.setText(R.string.pingate_mismatch);
            syncDots();
            return;
        }
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        StringBuilder salt = new StringBuilder();
        for (byte b : saltBytes) salt.append(String.format("%02x", b));
        Prefs.get(this).edit()
                .putString(Prefs.PIN_SALT, salt.toString())
                .putString(Prefs.PIN_HASH, PinLock.hash(salt.toString(), firstEntry))
                .putInt(Prefs.PIN_FAILURES, 0)
                .putLong(Prefs.PIN_LOCKOUT_UNTIL, 0)
                .apply();
        error.setText(R.string.pingate_saved);
        openSettings();
    }

    private void onUnlockAttempt() {
        String salt = Prefs.get(this).getString(Prefs.PIN_SALT, "");
        String hash = Prefs.get(this).getString(Prefs.PIN_HASH, null);
        if (PinLock.verify(salt, entered.toString(), hash)) {
            Prefs.get(this).edit()
                    .putInt(Prefs.PIN_FAILURES, 0)
                    .putLong(Prefs.PIN_LOCKOUT_UNTIL, 0)
                    .apply();
            openSettings();
            return;
        }
        int failures = Prefs.get(this).getInt(Prefs.PIN_FAILURES, 0) + 1;
        long lock = PinLock.lockoutMillis(failures);
        Prefs.get(this).edit()
                .putInt(Prefs.PIN_FAILURES, failures)
                .putLong(Prefs.PIN_LOCKOUT_UNTIL, lock > 0 ? System.currentTimeMillis() + lock : 0)
                .apply();
        entered.setLength(0);
        syncDots();
        error.setText(lock > 0 ? R.string.pingate_locked : R.string.pingate_wrong);
        refreshDeviceButton(); // a wrong attempt is what unlocks the recovery path
    }

    private boolean isLockedOut() {
        long until = Prefs.get(this).getLong(Prefs.PIN_LOCKOUT_UNTIL, 0);
        boolean locked = PinLock.isLocked(until, System.currentTimeMillis());
        if (locked) error.setText(R.string.pingate_locked);
        return locked;
    }

    private void syncDots() {
        Drawable filled = getResources().getDrawable(R.drawable.chip_green);
        Drawable empty = getResources().getDrawable(R.drawable.chip_grey);
        for (int i = 0; i < dots.getChildCount(); i++) {
            dots.getChildAt(i).setBackground(i < entered.length() ? filled : empty);
        }
    }

    private void openSettings() {
        SessionGate.unlock();
        // CLEAR_TOP: after a PIN CHANGE launched from Settings, return to the
        // Settings screen already underneath instead of stacking a second one
        // (Back would otherwise land on the stale copy). On normal entry from
        // the everyday screen no Settings is on the stack, so this just opens
        // it fresh; its onResume re-checks the session either way.
        startActivity(new Intent(this, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEVICE_CRED && resultCode == RESULT_OK) {
            Prefs.get(this).edit()
                    .putInt(Prefs.PIN_FAILURES, 0)
                    .putLong(Prefs.PIN_LOCKOUT_UNTIL, 0)
                    .apply();
            if (recovery) {
                // Forgotten PIN: the device credential proves the setter;
                // clear the old PIN and let them choose a new one now.
                Prefs.get(this).edit().remove(Prefs.PIN_SALT).remove(Prefs.PIN_HASH).apply();
                recovery = false;
                mode = MODE_SETUP;
                firstEntry = null;
                entered.setLength(0);
                subtitle.setText(R.string.pingate_set_new);
                error.setText("");
                findViewById(R.id.btn_device_lock).setVisibility(View.GONE);
                syncDots();
            } else {
                openSettings();
            }
        }
    }
}
