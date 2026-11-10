package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.TextView;

/** Shared tab/control styling and scrollable history filters for short windows. */
public final class PhoneUi {
  private PhoneUi() {}

  public static void select(View root, int id, boolean selected) {
    TextView button = root.findViewById(id);
    button.setSelected(selected);
    button.setTypeface(
        null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    boolean contrast =
        SurfaceText.color(root.getContext()) == root.getResources().getColor(R.color.contrast_text);
    button.setBackgroundResource(
        selected
            ? R.drawable.btn_back
            : contrast ? R.drawable.tile_bg_contrast : R.drawable.btn_number);
    button.setTextColor(
        selected ? android.graphics.Color.WHITE : SurfaceText.color(root.getContext()));
  }
}
