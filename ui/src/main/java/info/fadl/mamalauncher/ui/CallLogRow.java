package info.fadl.mamalauncher.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared runtime/snapshot row, plain large text with independent LTR number. */
public final class CallLogRow extends LinearLayout {
  private final TextView name, number, detail;

  public CallLogRow(Context c) {
    super(c);
    setOrientation(VERTICAL);
    setPadding(dp(14), dp(12), dp(14), dp(12));
    setBackgroundResource(
        SurfaceText.color(c) == c.getResources().getColor(R.color.contrast_text)
            ? R.drawable.tile_bg_contrast
            : R.drawable.tile_bg);
    setMinimumHeight(dp(104));
    name = text(24);
    name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
    number = text(20);
    number.setTextDirection(View.TEXT_DIRECTION_LTR);
    detail = text(19);
    setFocusable(true);
  }

  private TextView text(int size) {
    TextView t = new TextView(getContext());
    t.setTextColor(SurfaceText.color(getContext()));
    t.setTextSize(size);
    t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(t, new LayoutParams(-1, -2));
    return t;
  }

  private int dp(int size) {
    return Math.round(size * getResources().getDisplayMetrics().density);
  }

  public void bind(String title, String phone, String description) {
    bind(title, phone, description, false);
  }

  public void bind(String title, String phone, String description, boolean numberTitle) {
    name.setTextDirection(numberTitle ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_FIRST_STRONG);
    name.setText(title);
    number.setText(phone);
    number.setVisibility(phone.isEmpty() ? GONE : VISIBLE);
    detail.setText(description);
    setContentDescription(title + "، " + phone + "، " + description);
  }
}
