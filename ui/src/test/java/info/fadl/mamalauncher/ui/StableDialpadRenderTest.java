package info.fadl.mamalauncher.ui;

import android.view.*;
import android.widget.*;
import app.cash.paparazzi.Paparazzi;
import org.junit.*;
import static org.junit.Assert.*;

/** Largest-text RTL pixel fixtures use the same preview formatter as production. */
public class StableDialpadRenderTest {
 private static app.cash.paparazzi.DeviceConfig device() {
  app.cash.paparazzi.DeviceConfig d=app.cash.paparazzi.DeviceConfig.NEXUS_5;
  return new app.cash.paparazzi.DeviceConfig(1520,720,271,271,d.getOrientation(),com.android.resources.UiMode.NORMAL,d.getNightMode(),com.android.resources.Density.DPI_280,1.5f,com.android.resources.LayoutDirection.RTL,"ar",d.getRatio(),d.getSize(),d.getKeyboard(),d.getTouchScreen(),d.getKeyboardState(),d.getSoftButtons(),d.getNavigation(),d.getScreenRound(),d.getReleased());
 }
 @Rule public final Paparazzi paparazzi=new Paparazzi(app.cash.paparazzi.EnvironmentKt.detectEnvironment(),device(),"MamaTheme");
 View state(String typed,String raw,boolean more,boolean saved) {
  View v=paparazzi.inflate(R.layout.activity_dialpad);((TextView)v.findViewById(R.id.dial_display)).setText(typed);v.findViewById(R.id.dial_display).setVisibility(View.VISIBLE);v.findViewById(R.id.dial_placeholder).setVisibility(View.GONE);
  if(saved)((TextView)v.findViewById(R.id.save_number_label)).setText(R.string.dial_view_saved);
  if(raw!=null)((LinearLayout)v.findViewById(R.id.dial_matches)).addView(DialMatchUi.preview(v.getContext(),new ContactEntry(1,"اسم تجريبي طويل",raw,null,false),saved));
  if(more){Button b=new Button(v.getContext());b.setText(v.getContext().getString(R.string.dial_more,3));b.setTextSize(18);b.setAllCaps(false);((LinearLayout)v.findViewById(R.id.dial_matches)).addView(b);}
  return v;
 }
 @Test public void beforeSuggestion(){paparazzi.snapshot(state("01",null,false,false),"keys-before-suggestion");}
 @Test public void groupedLocalSuggestion(){paparazzi.snapshot(state("010","0100 000 0001",true,false),"keys-grouped-local-suggestion");}
 @Test public void internationalExact(){paparazzi.snapshot(state("+201000000001","+20 100-000-0001",false,true),"keys-international-exact");}
 @Test public void noMatches(){paparazzi.snapshot(state("09999",null,false,false),"keys-after-no-matches");}
 @Test public void numberRunsAreVisuallyLeftToRightInPreviewAndChooser(){numberRuns(new String[]{"0100 000 0001","+20 100-000-0001","0020 100 000 0001"},"isolated-phone-runs");}
 @Test public void arabicDigitGroupsAlsoRemainLeftToRight(){numberRuns(new String[]{"٠١٠٠ ٠٠٠ ٠٠٠١"},"isolated-arabic-digit-runs");}
 private void numberRuns(String[] values,String snapshot){
  LinearLayout box=new LinearLayout(paparazzi.getContext());box.setOrientation(LinearLayout.VERTICAL);box.setPadding(12,12,12,12);
  for(String raw:values) {
   ContactEntry c=new ContactEntry(1,"اسم تجريبي",raw,null,false);Button preview=DialMatchUi.preview(paparazzi.getContext(),c,true);box.addView(preview,new LinearLayout.LayoutParams(-1,-2));Button chooser=new Button(paparazzi.getContext());chooser.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);chooser.setAllCaps(false);chooser.setTextSize(18);chooser.setText(DialMatchUi.chooserLabel(c));box.addView(chooser,new LinearLayout.LayoutParams(-1,-2));
  }
  int width=(int)(411*box.getResources().getDisplayMetrics().density);box.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1800,View.MeasureSpec.AT_MOST));box.layout(0,0,width,box.getMeasuredHeight());
  for(int i=0;i<box.getChildCount();i++) {
   TextView text=(TextView)box.getChildAt(i);String value=text.getText().toString();int start=value.indexOf('\u2066')+1,end=value.indexOf('\u2069');assertTrue(start>0);android.text.Layout layout=text.getLayout();assertNotNull(layout);float previous=-Float.MAX_VALUE;
   for(int j=start;j<end;j++)if(Character.isDigit(value.charAt(j)) || value.charAt(j)=='+'){float x=layout.getPrimaryHorizontal(j);assertTrue("LTR digit order at index "+j+" x="+x+" previous="+previous+" for "+value,x>previous);previous=x;}
  }
  paparazzi.snapshot(box,snapshot);
 }
}
