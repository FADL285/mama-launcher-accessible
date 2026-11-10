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
        TextView target=root.findViewById(R.id.selected_call_number);
        target.setVisibility(View.VISIBLE);target.setText(R.string.call_choose_hint);
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
            b.setTextSize(17f);
            b.setPadding(dp(root, 6),dp(root, 6),dp(root, 6),dp(root, 6));
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
                target.setText((String)v.getTag());target.setTextDirection(View.TEXT_DIRECTION_LTR);
                listener.onNumberSelected((String) v.getTag());
            });
            row.addView(b);
        }
    }

    /** Re-applies the visible selection highlight (e.g. after activity recreation). */
    public static void selectNumber(View root, String number) {
        LinearLayout box = root.findViewById(R.id.numbers_container);
        TextView target=root.findViewById(R.id.selected_call_number);
        if(number!=null && box.getVisibility()==View.VISIBLE){target.setVisibility(View.VISIBLE);target.setText(number);target.setTextDirection(View.TEXT_DIRECTION_LTR);}
        for (int r = 0; r < box.getChildCount(); r++) {
            LinearLayout rr = (LinearLayout) box.getChildAt(r);
            for (int k = 0; k < rr.getChildCount(); k++) {
                View b = rr.getChildAt(k);
                b.setBackgroundResource(number != null && number.equals(b.getTag())
                        ? R.drawable.btn_number_selected : R.drawable.btn_number);
            }
        }
    }

    private static int dp(View root, int v) {
        return (int) (v * root.getResources().getDisplayMetrics().density);
    }

    /**
     * One big everyday-mode home tile: 68dp icon area on top, bold label below,
     * tall enough for shaky hands. Built in code so the same path serves the
     * device and the snapshot tests. chipBgRes != 0 draws a colored circle
     * behind the icon (used for the special "Phone" tile); chipBgRes == 0
     * shows the app's real icon directly.
     */
    public static View buildHomeTile(android.content.Context ctx, String label,
                                     android.graphics.drawable.Drawable icon,
                                     int chipBgRes, boolean contrast,
                                     View.OnClickListener onTap) {
        // Never attach a resource-cached drawable instance to multiple views.
        // Give each tile its own bounds/tint state (also stable in layoutlib).
        if (icon != null && icon.getConstantState() != null) {
            icon = icon.getConstantState().newDrawable(ctx.getResources()).mutate();
        }
        LinearLayout tile = new HomeTile(ctx);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(android.view.Gravity.CENTER);
        tile.setBackgroundResource(contrast ? R.drawable.tile_bg_contrast : R.drawable.tile_bg);
        tile.setTag("card");
        tile.setClipChildren(false);
        tile.setClipToPadding(false);
        tile.setMinimumHeight(dp(tile, 170));
        tile.setPadding(dp(tile, 8), dp(tile, 10), dp(tile, 8), dp(tile, 10));

        int chip = dp(tile, 68);
        android.widget.FrameLayout iconBox = new android.widget.FrameLayout(ctx);
        iconBox.setClipChildren(false);
        iconBox.setClipToPadding(false);
        android.widget.FrameLayout imageBox = new android.widget.FrameLayout(ctx);
        if (chipBgRes != 0) imageBox.setBackgroundResource(chipBgRes);
        android.widget.ImageView image = new android.widget.ImageView(ctx);
        int size = chipBgRes != 0 ? dp(tile, 34) : chip;
        android.widget.FrameLayout.LayoutParams imageParams =
                new android.widget.FrameLayout.LayoutParams(size, size, android.view.Gravity.CENTER);
        if (icon != null) image.setImageDrawable(icon);
        imageBox.addView(image, imageParams);
        iconBox.addView(imageBox, new android.widget.FrameLayout.LayoutParams(chip, chip));
        BadgeView badge = new BadgeView(ctx);
        android.widget.FrameLayout.LayoutParams badgeParams = new android.widget.FrameLayout.LayoutParams(
                dp(tile, 28), dp(tile, 28), android.view.Gravity.TOP | android.view.Gravity.RIGHT);
        badgeParams.topMargin = -dp(tile, 8);
        badgeParams.rightMargin = -dp(tile, 8);
        iconBox.addView(badge, badgeParams);
        tile.addView(iconBox, new LinearLayout.LayoutParams(chip, chip));

        TextView tv = new HomeLabel(ctx);
        tv.setText(label);
        // Measure only the visible lines. Keep full names for accessibility and long press.
        tv.setTextSize(24f);
        tv.setMaxLines(2);tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.setContentDescription(label);if(android.os.Build.VERSION.SDK_INT>=26)tile.setTooltipText(label);
        tile.setOnLongClickListener(v->{new android.app.AlertDialog.Builder(ctx).setMessage(label).setPositiveButton(android.R.string.ok,null).show();return true;});
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setTextColor(ctx.getResources().getColor(
                contrast ? R.color.contrast_text : R.color.text_main));
        tv.setGravity(android.view.Gravity.CENTER);
        // Reserve equal card height, not an empty line INSIDE a one-line label.
        // Then the tile gravity centers the real icon+label group with a tight gap.
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setPadding(dp(tile,4),0,dp(tile,4),dp(tile,2));
        tp.setMargins(0, dp(tile, 6), 0, 0);
        tile.addView(tv, tp);

        if (onTap != null) tile.setOnClickListener(onTap);
        return tile;
    }
    /** -2 is an app dot; positive values are missed calls; zero/unknown is hidden. */
    public static void bindHomeBadge(View tile, int count, String description) {
        BadgeView badge = tile.findViewWithTag(BadgeView.TAG);
        if (badge == null) return;
        badge.bind(count);
        CharSequence label = ((TextView)((LinearLayout)tile).getChildAt(1)).getText();
        tile.setContentDescription(count > 0 || count == -2 ? label + "، " + description : label);
    }

    private static final class HomeLabel extends TextView {
        boolean reserveHeight;
        // API 33 stubs name LineBreaker constants, but that class starts at API 29.
        // Layout supplies the same inlined value on our API 24+ baseline.
        @android.annotation.SuppressLint("WrongConstant")
        HomeLabel(android.content.Context context) {
            super(context);
            setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
            setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = View.MeasureSpec.getSize(widthSpec) - getCompoundPaddingLeft() - getCompoundPaddingRight();
            if (reserveHeight) {
                setTextSize(24f);setMaxLines(2);
            } else if (width > 0 && View.MeasureSpec.getMode(widthSpec) != View.MeasureSpec.UNSPECIFIED) {
                float scale = getResources().getDisplayMetrics().scaledDensity;
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24f*scale);
                float longest = 0;
                for (String word : getText().toString().split("\\s+"))
                    longest = Math.max(longest,getPaint().measureText(word));
                float size = longest > width ? Math.max(18f*scale,24f*scale*width/longest*0.98f) : 24f*scale;
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);
                // If the word cannot fit at the readable floor, ellipsize a
                // single line instead of Android's emergency mid-word wrap.
                setMaxLines(longest*(size/(24f*scale)) > width ? 1 : 2);
            }
            super.onMeasure(widthSpec,heightSpec);
        }
    }
    private static final class HomeTile extends LinearLayout {
        HomeTile(android.content.Context context) { super(context); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            // Two-pass measurement retains equal two-line card heights, including
            // Arabic fallback-font metrics. The final layout has only visible lines.
            HomeLabel label = (HomeLabel)getChildAt(1);
            label.reserveHeight=true;label.setMinLines(2);
            super.onMeasure(widthSpec, heightSpec);
            int reservedHeight = getMeasuredHeight();
            label.reserveHeight=false;label.setMinLines(1);
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(
                    reservedHeight, View.MeasureSpec.EXACTLY));
        }
    }
}