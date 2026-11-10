package info.fadl.mamalauncher.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/** Preserve usable scrolling room above the new fixed footer on short, large-text phones. */
public final class PhoneLayout extends LinearLayout {
  public PhoneLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    boolean compact =
        MeasureSpec.getSize(heightSpec) / getResources().getDisplayMetrics().density < 600
            && getResources().getConfiguration().fontScale > 1.2f;
    View back = findViewById(R.id.btn_back), label = findViewById(R.id.page_back_label);
    if (back != null && label != null) {
      label.setVisibility(compact ? GONE : VISIBLE);
      back.setMinimumWidth(
          Math.round((compact ? 48 : 76) * getResources().getDisplayMetrics().density));
      back.setContentDescription(getContext().getString(R.string.back));
    }
    super.onMeasure(widthSpec, heightSpec);
  }
}
