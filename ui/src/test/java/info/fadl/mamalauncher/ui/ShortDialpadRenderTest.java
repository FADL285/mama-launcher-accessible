package info.fadl.mamalauncher.ui;
import android.view.*;
import android.widget.*;
import app.cash.paparazzi.Paparazzi;
import org.junit.*;
import static org.junit.Assert.*;
public class ShortDialpadRenderTest {
 private static app.cash.paparazzi.DeviceConfig device(){app.cash.paparazzi.DeviceConfig d=app.cash.paparazzi.DeviceConfig.NEXUS_5;return new app.cash.paparazzi.DeviceConfig(840,720,271,271,d.getOrientation(),com.android.resources.UiMode.NORMAL,d.getNightMode(),com.android.resources.Density.DPI_280,1.5f,com.android.resources.LayoutDirection.RTL,"ar",d.getRatio(),d.getSize(),d.getKeyboard(),d.getTouchScreen(),d.getKeyboardState(),d.getSoftButtons(),d.getNavigation(),d.getScreenRound(),d.getReleased());}
 @Rule public final Paparazzi paparazzi=new Paparazzi(app.cash.paparazzi.EnvironmentKt.detectEnvironment(),device(),"MamaTheme");
 @Test public void shortWindowBeforeResults(){render(false,false);}
 @Test public void shortWindowWithResults(){render(true,false);}
 @Test public void shortWindowBottomKeysReachable(){render(true,true);}
 void render(boolean results,boolean bottom){View v=paparazzi.inflate(R.layout.activity_dialpad);((TextView)v.findViewById(R.id.dial_display)).setText("010");v.findViewById(R.id.dial_display).setVisibility(View.VISIBLE);v.findViewById(R.id.dial_placeholder).setVisibility(View.GONE);if(results)((LinearLayout)v.findViewById(R.id.dial_matches)).addView(DialMatchUi.preview(v.getContext(),new ContactEntry(1,"اسم تجريبي","0100 000 0001",null,false),false));ScrollView keys=v.findViewById(R.id.dial_keys_scroll);assertNotNull(keys);if(bottom){int width=720,height=720;v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,width,height);keys.scrollTo(0,keys.getChildAt(0).getHeight());}paparazzi.snapshot(v,bottom?"short-bottom-keys":results?"short-results":"short-before");}
}
