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
            if (child instanceof ViewGroup) {
                wireDigits((ViewGroup) child);
            } else if (child.getTag() != null) {
                final String digit = child.getTag().toString();
                guard(child, () -> {
                    number.append(digit);
                    sync();
                });
            }
        }
    }

    private void sync() {
        display.setText(number.toString());
        // Long numbers shrink via autosize (API 26+); on older devices the
        // field scrolls horizontally and we keep the last digit in view.
        // Digits are NEVER dropped or ellipsized.
        display.post(() -> display.bringPointIntoView(number.length()));
        boolean has = number.length() >= 3;
        callBtn.setAlpha(has ? 1f : 0.45f);
        saveBtn.setAlpha(has ? 1f : 0.45f);
    }
}
