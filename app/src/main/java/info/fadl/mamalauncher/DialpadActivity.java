package info.fadl.mamalauncher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Big dial pad. The green action calls directly after an explicit press and
 * CALL_PHONE permission. Permission grant alone never calls. Save opens the
 * system contact-insert form and never writes contacts silently. */
public class DialpadActivity extends BaseActivity {

    private final StringBuilder number = new StringBuilder();
    private TextView display;
    private View callBtn;
    private View saveBtn;
    private DirectCallAction callAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialpad);
        display = findViewById(R.id.dial_display);
        if (savedInstanceState != null) number.append(savedInstanceState.getString("number", ""));
        callBtn = findViewById(R.id.btn_call);
        saveBtn = findViewById(R.id.btn_save_number);

        guard(findViewById(R.id.btn_back), this::finish);
        guard(findViewById(R.id.btn_backspace), () -> {
            if (number.length() > 0) number.deleteCharAt(number.length() - 1);
            sync();
        });
        wireDigits(findViewById(R.id.dial_grid));

        callAction=new DirectCallAction(this,(blocked,granted) ->
                android.widget.Toast.makeText(this,blocked?R.string.call_blocked_hint:R.string.call_permission_ready,
                        android.widget.Toast.LENGTH_LONG).show());
        guard(callBtn, () -> callAction.tap(number.toString()));
        guard(saveBtn, () -> {
            if (number.length() == 0) return;
            try {
                startActivity(DialIntents.saveAsContact(number.toString()));
            } catch (android.content.ActivityNotFoundException e) { handoffUnavailable(); }
        });
        sync();
    }

    @Override protected void onResume() {
        super.onResume(); if(callAction!=null)callAction.onResume();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        callAction.permissionResult(code,results);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("number", number.toString());
    }

    private void handoffUnavailable() {
        android.widget.Toast.makeText(this, R.string.handoff_unavailable,
                android.widget.Toast.LENGTH_LONG).show();
    }

    private void wireDigits(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getTag() == null && child instanceof ViewGroup) {
                wireDigits((ViewGroup) child);
            } else if (child.getTag() != null) {
                final String digit = child.getTag().toString();
                guard(child, () -> {
                    number.append(digit);
                    sync();
                });
                if ("0".equals(digit)) {
                    child.setOnLongClickListener(v -> {
                        if (number.length() == 0) { number.append('+'); sync(); }
                        return true; // consume: never append a trailing 0 after a hold
                    });
                    if (Prefs.touchGuard(this)) {
                        final long[] down={-1};
                        child.setOnTouchListener((v,event)->{
                            boolean inside=event.getX()>=0 && event.getX()<v.getWidth() && event.getY()>=0 && event.getY()<v.getHeight();
                            switch(event.getActionMasked()) {
                                case android.view.MotionEvent.ACTION_DOWN: down[0]=event.getEventTime(); return true;
                                case android.view.MotionEvent.ACTION_MOVE: if(!inside)down[0]=-1; return true;
                                case android.view.MotionEvent.ACTION_UP:
                                    long duration=down[0]<0?-1:event.getEventTime()-down[0]; down[0]=-1;
                                    if(inside && duration>=android.view.ViewConfiguration.getLongPressTimeout())v.performLongClick();
                                    else if(inside && duration>=GUARD_PRESS_MS)v.performClick();
                                    return true;
                                case android.view.MotionEvent.ACTION_CANCEL: down[0]=-1; return true;
                                default:return true;
                            }
                        });
                    }
                }
            }
        }
    }

    private void sync() {
        display.setText(number.toString());
        display.setVisibility(number.length()==0?View.INVISIBLE:View.VISIBLE);
        findViewById(R.id.dial_placeholder).setVisibility(number.length()==0?View.VISIBLE:View.GONE);
        // Long numbers shrink via autosize (API 26+); on older devices the
        // field scrolls horizontally and we keep the last digit in view.
        // Digits are NEVER dropped or ellipsized.
        display.post(() -> display.bringPointIntoView(number.length()));
        boolean has = number.length() >= 3;
        callBtn.setAlpha(has ? 1f : 0.45f);
        saveBtn.setAlpha(has ? 1f : 0.45f);
    }
}
