package info.fadl.mamalauncher;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33,qualifiers="ar-rEG-w411dp-h868dp")
public class HomeLabelFitTest {
 @Test public void longLabelsKeepEqualTwoLineCardsAndExposeFullName(){
  for(float scale:new float[]{1f,1.25f,1.5f}) {
   android.content.res.Configuration config=new android.content.res.Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());config.fontScale=scale;
   android.content.Context c=RuntimeEnvironment.getApplication().createConfigurationContext(config);
   for(String label:new String[]{"تطبيق الإنترنت من","تطبيق الإنترنت من سامسونج","Galaxy Store"}) {
    LinearLayout tile=(LinearLayout)info.fadl.mamalauncher.ui.UiBinder.buildHomeTile(c,label,null,0,false,null);
    tile.measure(View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));tile.layout(0,0,tile.getMeasuredWidth(),tile.getMeasuredHeight());
    TextView text=(TextView)tile.getChildAt(tile.getChildCount()-1);
    assertEquals(label,text.getText().toString());assertEquals(android.text.TextUtils.TruncateAt.END,text.getEllipsize());assertEquals(2,text.getMaxLines());assertEquals(label,tile.getContentDescription());assertEquals(label,tile.getTooltipText());assertTrue(tile.performLongClick());assertEquals(label,((TextView)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().findViewById(android.R.id.message)).getText().toString());assertTrue(text.getLineCount()>0);
    assertTrue(text.getBottom()+tile.getPaddingBottom()<=tile.getHeight());
    assertTrue(text.getHeight()>=text.getLayout().getLineBottom(text.getLineCount()-1));
    for(int line=0;line<text.getLineCount();line++) {
     assertTrue(text.getLayout().getLineLeft(line)>=-1);
     assertTrue(text.getLayout().getLineRight(line)<=text.getWidth()-text.getPaddingLeft()-text.getPaddingRight()+1);
    }
   }
  }
 }
}
