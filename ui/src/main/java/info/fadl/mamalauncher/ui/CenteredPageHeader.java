package info.fadl.mamalauncher.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/** Equal measured sides center the title. Large labels use a full-width second row. */
public final class CenteredPageHeader extends LinearLayout {
    public CenteredPageHeader(Context context, AttributeSet attrs){super(context,attrs);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        View back=findViewById(R.id.btn_back), mirror=findViewById(R.id.header_mirror);
        View title=findViewById(R.id.page_title);
        if(back!=null && mirror!=null && title!=null){
            int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
            measureChild(back,widthSpec,heightSpec);
            int side=back.getMeasuredWidth();
            boolean stack=available-2*side < (int)(160*getResources().getDisplayMetrics().density);
            setOrientation(stack?VERTICAL:HORIZONTAL);
            LayoutParams backParams=(LayoutParams)back.getLayoutParams();
            backParams.gravity=Gravity.START;
            LayoutParams titleParams=(LayoutParams)title.getLayoutParams();
            titleParams.width=stack?LayoutParams.MATCH_PARENT:0;
            titleParams.weight=stack?0:1;
            // Update the existing params in place during measurement. Calling
            // setLayoutParams here would schedule an avoidable layout pass.
            mirror.setVisibility(stack?GONE:INVISIBLE);
            mirror.getLayoutParams().width=side;
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
