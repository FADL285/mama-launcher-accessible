package info.fadl.mamalauncher.ui;
import android.view.*;
import android.widget.*;
import app.cash.paparazzi.Paparazzi;
import org.junit.*;
public class DeveloperFooterRenderTest {
 private static app.cash.paparazzi.DeviceConfig device(){app.cash.paparazzi.DeviceConfig d=app.cash.paparazzi.DeviceConfig.NEXUS_5;return new app.cash.paparazzi.DeviceConfig(1520,720,271,271,d.getOrientation(),com.android.resources.UiMode.NORMAL,d.getNightMode(),com.android.resources.Density.DPI_280,1.5f,com.android.resources.LayoutDirection.RTL,"ar",d.getRatio(),d.getSize(),d.getKeyboard(),d.getTouchScreen(),d.getKeyboardState(),d.getSoftButtons(),d.getNavigation(),d.getScreenRound(),d.getReleased());}
 @Rule public final Paparazzi paparazzi=new Paparazzi(app.cash.paparazzi.EnvironmentKt.detectEnvironment(),device(),"MamaTheme");
 @Test public void settingsFooterLargestText(){render(false);}
 @Test public void settingsFooterContrast(){render(true);}
 void render(boolean contrast){android.content.Context context=new android.view.ContextThemeWrapper(paparazzi.getContext(),contrast?R.style.MamaTheme_Contrast:R.style.MamaTheme);View v=android.view.LayoutInflater.from(context).inflate(R.layout.activity_settings,null);SettingsSections.show(v,-1);v.setBackgroundColor(context.getResources().getColor(contrast?R.color.contrast_bg:R.color.bg,context.getTheme()));v.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1416,View.MeasureSpec.EXACTLY));v.layout(0,0,720,1416);ScrollView scroll=v.findViewById(R.id.settings_scroll);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());paparazzi.snapshot(v,contrast?"footer-contrast":"footer-largest");}
}
