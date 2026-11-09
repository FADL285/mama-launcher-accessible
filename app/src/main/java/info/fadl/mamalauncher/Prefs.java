package info.fadl.mamalauncher;

import android.content.Context;
import android.content.SharedPreferences;

/** All family-setter configuration in one place. */
public final class Prefs {

    public static final String AUTO_SPEAK = "auto_speak";
    public static final String FONT_SCALE = "font_scale";       // 0 normal, 1 large, 2 huge
    public static final String HIGH_CONTRAST = "high_contrast";
    public static final String TOUCH_GUARD = "touch_guard";
    public static final String VIBRATE = "vibrate";
    public static final String PIN_SALT = "pin_salt";
    public static final String PIN_HASH = "pin_hash";
    public static final String PIN_FAILURES = "pin_failures";
    public static final String PIN_LOCKOUT_UNTIL = "pin_lockout_until";
    public static final String USE_DEVICE_LOCK = "use_device_lock";
    public static final String HOME_AFTER_LOCK = "home_after_lock";

    private Prefs() {}

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences("pilot", Context.MODE_PRIVATE);
    }

    public static boolean autoSpeak(Context c) { return get(c).getBoolean(AUTO_SPEAK, true); }
    public static int fontScale(Context c) { return get(c).getInt(FONT_SCALE, 1); } // default large for seniors
    public static boolean highContrast(Context c) { return get(c).getBoolean(HIGH_CONTRAST, false); }
    public static boolean touchGuard(Context c) { return get(c).getBoolean(TOUCH_GUARD, false); }
    public static boolean vibrate(Context c) { return get(c).getBoolean(VIBRATE, true); }
    public static boolean hasPin(Context c) { return get(c).getString(PIN_HASH, null) != null; }
    public static boolean useDeviceLock(Context c) { return get(c).getBoolean(USE_DEVICE_LOCK, false); }
    // Default OFF until the family confirms the default; the setting is visible either way.
    public static boolean homeAfterLock(Context c) { return get(c).getBoolean(HOME_AFTER_LOCK, false); }

    /** Maps the stored scale choice to a Configuration fontScale multiplier. */
    public static float fontScaleValue(int scale) {
        switch (scale) {
            case 0: return 1.0f;
            case 2: return 1.5f;
            case 1:
            default: return 1.25f;
        }
    }
}
