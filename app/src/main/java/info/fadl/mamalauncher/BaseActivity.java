package info.fadl.mamalauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
        returnHomePending=savedInstanceState!=null && savedInstanceState.getBoolean("navigation.home_after_lock_pending",false);
    }

    protected boolean contrast() { return Prefs.highContrast(this); }

    // ---- optional "back to Home after a real screen lock" (family setting) ----
    // Applies ONLY to internal content pages that opt in (Apps, Phone,
    // Dialpad). Never to the call confirmation, the PIN gate or settings: a
    // pending call keeps its existing behavior. Protected settings still use
    // their separate, existing screen-off lock; this option does not change it.
    protected boolean homeAfterLockApplies() { return false; }

    private boolean foreground;
    private boolean returnHomePending;
    private android.content.BroadcastReceiver screenOffReceiver;

    @Override protected void onStart() {
        super.onStart();
        if (homeAfterLockApplies() && screenOffReceiver == null) {
            screenOffReceiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (Intent.ACTION_SCREEN_OFF.equals(i.getAction()) && foreground) markScreenOff();
                }
            };
            registerReceiver(screenOffReceiver, new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF));
        }
    }
    private boolean navPending;
    /** True for the first navigation tap; repeats are ignored until onResume clears the latch. */
    protected boolean beginNavigation() {
        if (navPending) return false;
        navPending = true;
        return true;
    }
    @Override protected void onResume() {
        super.onResume();
        navPending = false;
        foreground = true;
        completeHomeReturnIfUnlocked();
    }
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused && foreground)completeHomeReturnIfUnlocked();
    }
    private void completeHomeReturnIfUnlocked(){
        android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        android.app.KeyguardManager keyguard=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(returnHomePending && (power==null || power.isInteractive())
                && (keyguard==null || !keyguard.isKeyguardLocked())) {
            returnHomePending=false;
            if(Prefs.homeAfterLock(this) && homeAfterLockApplies()) {
                startActivity(new Intent(this,MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }
        }
    }
    @Override protected void onPause() {
        // Screen-off and onPause order varies by Android/OEM. Capture both
        // orders. Leaving for another app while the display is on is NOT a lock.
        android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        if(foreground && power!=null && !power.isInteractive())markScreenOff();
        foreground=false;
        super.onPause();
    }
    private void markScreenOff(){
        if(homeAfterLockApplies() && Prefs.homeAfterLock(this))returnHomePending=true;
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putBoolean("navigation.home_after_lock_pending",returnHomePending);
        super.onSaveInstanceState(out);
    }
    @Override protected void onStop() {
        if(screenOffReceiver!=null){unregisterReceiver(screenOffReceiver);screenOffReceiver=null;}
        super.onStop();
    }

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
