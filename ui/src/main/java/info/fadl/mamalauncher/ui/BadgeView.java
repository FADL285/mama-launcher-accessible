package info.fadl.mamalauncher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.text.NumberFormat;

/** Decorative icon-corner mark. Parent tile owns focus, label and click. */
public final class BadgeView extends View {
  public static final String TAG = "home.badge";
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int count;

  public BadgeView(Context c) {
    super(c);
    setTag(TAG);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setVisibility(GONE);
  }

  public void bind(int value) {
    count = value;
    setVisibility(value > 0 || value == -2 ? VISIBLE : GONE);
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float x = getWidth() / 2f, y = getHeight() / 2f;
    float radius = (count == -2 ? 9 : 13) * getResources().getDisplayMetrics().density;
    paint.setColor(Color.WHITE);
    canvas.drawCircle(x, y, radius, paint);
    paint.setColor(Color.rgb(173, 21, 48));
    canvas.drawCircle(x, y, radius - 2 * getResources().getDisplayMetrics().density, paint);
    if (count > 0) {
      paint.setColor(Color.WHITE);
      paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
      paint.setTextSize(13 * getResources().getDisplayMetrics().density);
      paint.setTextAlign(Paint.Align.CENTER);
      String text =
          NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0))
                  .format(Math.min(count, 99))
              + (count > 99 ? "+" : "");
      if (paint.measureText(text) > radius * 1.5f)
        paint.setTextSize(paint.getTextSize() * radius * 1.5f / paint.measureText(text));
      canvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2, paint);
    }
  }
}
