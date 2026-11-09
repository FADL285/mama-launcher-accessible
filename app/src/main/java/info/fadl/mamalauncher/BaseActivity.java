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

    protected static final long GUARD_PRESS_MS = 400;

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
        // Accessibility/keyboard activation always has a click action. Only
        // physical pointer input is subject to the hold-duration requirement.
        v.setOnClickListener(view -> { haptic(view); action.run(); });
        v.setOnTouchListener(null);
        if (Prefs.touchGuard(this)) {
            final long[] down = {-1};
            v.setOnTouchListener((view, ev) -> {
                boolean inside = ev.getX() >= 0 && ev.getX() < view.getWidth()
                        && ev.getY() >= 0 && ev.getY() < view.getHeight();
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        down[0] = ev.getEventTime();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!inside) down[0] = -1;
                        return true;
                    case MotionEvent.ACTION_UP:
                        boolean accepted = inside && down[0] >= 0
                                && ev.getEventTime() - down[0] >= GUARD_PRESS_MS;
                        down[0] = -1;
                        if (accepted) view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        down[0] = -1;
                        return true;
                    default: return true;
                }
            });
        }
    }
}
