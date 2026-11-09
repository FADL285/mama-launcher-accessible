package info.fadl.mamalauncher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Big dial pad for a new number. No new permissions by design: the green
 * button hands the composed number to the phone's own dialer (ACTION_DIAL),
 * and "save number" opens the phone's normal add-contact screen with the
 * number prefilled (Contacts insert UI does the writing, not us).
 */
public class DialpadActivity extends BaseActivity {

    private final StringBuilder number = new StringBuilder();
    private TextView display;
    private View callBtn;
    private View saveBtn;

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

        guard(callBtn, () -> {
            if (number.length() == 0) return;
            try { startActivity(DialIntents.dial(number.toString())); }
            catch (android.content.ActivityNotFoundException e) { handoffUnavailable(); }
        });
        guard(saveBtn, () -> {
            if (number.length() == 0) return;
            try {
                startActivity(DialIntents.saveAsContact(number.toString()));
            } catch (android.content.ActivityNotFoundException e) { handoffUnavailable(); }
        });
        sync();
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
