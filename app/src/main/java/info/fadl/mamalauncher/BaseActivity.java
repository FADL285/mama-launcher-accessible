package info.fadl.mamalauncher;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Shared senior-mode behavior: theme (normal/high-contrast), font scale,
 *  optional haptic confirmation, and the optional accidental-touch guard
 *  (button reacts only after a slightly longer press). */
public abstract class BaseActivity extends Activity {

    private static final long GUARD_PRESS_MS = 400;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        Configuration c = new Configuration(newBase.getResources().getConfiguration());
        c.fontScale = Prefs.fontScaleValue(Prefs.fontScale(newBase));
        applyOverrideConfiguration(c);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Prefs.highContrast(this)) setTheme(R.style.MamaTheme_Contrast);
        super.onCreate(savedInstanceState);
    }

    protected boolean contrast() { return Prefs.highContrast(this); }

    protected void haptic(View v) {
        if (Prefs.vibrate(this)) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    /** Wires the action with haptic feedback; when the touch guard is on the
     *  press must be held a little longer before it counts. */
    protected void guard(View v, Runnable action) {
        if (Prefs.touchGuard(this)) {
            v.setOnTouchListener((view, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setTag(ev.getEventTime());
                        return true;
                    case MotionEvent.ACTION_UP:
                        Long t0 = (Long) view.getTag();
                        if (t0 != null && ev.getEventTime() - t0 >= GUARD_PRESS_MS) {
                            haptic(view);
                            action.run();
                        }
                        view.setTag(null);
                        return true;
                    default:
                        return true;
                }
            });
        } else {
            v.setOnClickListener(view -> {
                haptic(view);
                action.run();
            });
        }
    }
}
