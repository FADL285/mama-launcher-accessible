package info.fadl.mamalauncher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Decorative call-status symbol. The parent row keeps the full spoken text. */
public final class CallDirectionView extends View {
  public enum Direction { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, UNKNOWN }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private Direction direction = Direction.UNKNOWN;

  public CallDirectionView(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setFocusable(false);
    setClickable(false);
    bind(Direction.UNKNOWN);
  }

  public void bind(Direction next) {
    direction = next == null ? Direction.UNKNOWN : next;
    setVisibility(direction == Direction.UNKNOWN ? GONE : VISIBLE);
    invalidate();
  }

  public Direction direction() { return direction; }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int save = canvas.save();
    float size = Math.min(getWidth(), getHeight());
    canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
    canvas.scale(size / 32f, size / 32f);
    boolean contrast = SurfaceText.color(getContext())
        == getResources().getColor(R.color.contrast_text);
    int color;
    switch (direction) {
      case OUTGOING: color = getResources().getColor(R.color.call_green); break;
      case MISSED: color = getResources().getColor(R.color.danger_text); break;
      case REJECTED:
      case BLOCKED: color = getResources().getColor(R.color.back_blue); break;
      default: color = SurfaceText.color(getContext());
    }
    paint.setColor(contrast ? SurfaceText.color(getContext()) : color);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(3);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    path.reset();
    switch (direction) {
      case OUTGOING:
        path.moveTo(7, 25); path.lineTo(25, 7);
        path.moveTo(13, 7); path.lineTo(25, 7); path.lineTo(25, 19);
        break;
      case INCOMING:
        path.moveTo(25, 7); path.lineTo(7, 25);
        path.moveTo(7, 13); path.lineTo(7, 25); path.lineTo(19, 25);
        break;
      case MISSED:
        // Bent missed-call arrow, distinct from the straight incoming arrow.
        path.moveTo(26, 10); path.lineTo(17, 20); path.lineTo(7, 10);
        path.moveTo(7, 20); path.lineTo(7, 10); path.lineTo(17, 10);
        break;
      case REJECTED:
      case BLOCKED:
        canvas.drawCircle(16, 16, 11, paint);
        path.moveTo(8, 8); path.lineTo(24, 24);
        break;
      default: break;
    }
    canvas.drawPath(path, paint);
    canvas.restoreToCount(save);
  }
}
