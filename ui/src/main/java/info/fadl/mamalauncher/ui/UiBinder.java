package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Shared binding for the confirmation screen so the exact same code path is
 *  what gets pixel-rendered in the snapshot tests and what runs on device. */
public final class UiBinder {

    public interface NumberSelectionListener {
        void onNumberSelected(String number);
    }

    private UiBinder() {}

    /**
     * Multi-number contacts: every number appears as a big labeled button and
     * the call button stays disabled until one is visibly chosen. Single-number
     * contacts: the number is shown and preselected. Nothing ever dials an
     * arbitrary "first" number silently.
     */
    public static void bindNumbers(View root, List<ContactEntry.PhoneNumber> numbers,
                                   NumberSelectionListener listener) {
        LinearLayout box = root.findViewById(R.id.numbers_container);
        TextView single = root.findViewById(R.id.single_number);
        if (numbers.size() <= 1) {
            box.setVisibility(View.GONE);
            single.setVisibility(numbers.isEmpty() ? View.GONE : View.VISIBLE);
            if (!numbers.isEmpty()) {
                single.setText(numbers.get(0).number);
                listener.onNumberSelected(numbers.get(0).number);
            }
            return;
        }
        single.setVisibility(View.GONE);
        box.setVisibility(View.VISIBLE);
        box.removeAllViews();

        LinearLayout row = null;
        for (int i = 0; i < numbers.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(root.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            ContactEntry.PhoneNumber pn = numbers.get(i);
            Button b = new Button(root.getContext());
            b.setMinHeight(dp(root, 72));
            b.setTextSize(19f);
            b.setTextColor(root.getResources().getColor(R.color.text_main));
            b.setTextDirection(View.TEXT_DIRECTION_LTR);
            String label = pn.label.isEmpty() ? "" : pn.label + "\n";
            b.setText(label + pn.number);
            b.setBackgroundResource(R.drawable.btn_number);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(root, 4), 0, dp(root, 4), dp(root, 10));
            b.setLayoutParams(lp);
            b.setTag(pn.number);
            b.setOnClickListener(v -> {
                for (int r = 0; r < box.getChildCount(); r++) {
                    LinearLayout rr = (LinearLayout) box.getChildAt(r);
                    for (int k = 0; k < rr.getChildCount(); k++) {
                        rr.getChildAt(k).setBackgroundResource(R.drawable.btn_number);
                    }
                }
                v.setBackgroundResource(R.drawable.btn_number_selected);
                listener.onNumberSelected((String) v.getTag());
            });
            row.addView(b);
        }
    }

    private static int dp(View root, int v) {
        return (int) (v * root.getResources().getDisplayMetrics().density);
    }
}
