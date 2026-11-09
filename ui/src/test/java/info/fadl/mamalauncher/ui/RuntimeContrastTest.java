package info.fadl.mamalauncher.ui;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import app.cash.paparazzi.Paparazzi;
public class RuntimeContrastTest {
 @Rule public final Paparazzi paparazzi=new Paparazzi(
  app.cash.paparazzi.EnvironmentKt.detectEnvironment(),
  app.cash.paparazzi.DeviceConfig.NEXUS_5,"MamaTheme.Contrast");
    private android.graphics.drawable.Drawable freshIcon(View v,int id) {
        try (android.content.res.XmlResourceParser parser=v.getResources().getXml(id)) {
            return android.graphics.drawable.Drawable.createFromXml(v.getResources(),parser,
                    v.getContext().getTheme());
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private void addHomeRows(View root, LinearLayout container, boolean contrast) {
        List<View> tiles = new ArrayList<>();
        tiles.add(UiBinder.buildHomeTile(root.getContext(), "الهاتف",
                freshIcon(root,R.drawable.ic_call),
                R.drawable.chip_green, contrast, null));
        tiles.add(UiBinder.buildHomeTile(root.getContext(), "يوتيوب",
                freshIcon(root,R.drawable.ic_play),
                R.drawable.chip_blue, contrast, null));
        tiles.add(UiBinder.buildHomeTile(root.getContext(), "واتساب",
                freshIcon(root,R.drawable.ic_chat),
                R.drawable.chip_wa, contrast, null));
        float d = root.getResources().getDisplayMetrics().density;
        int m = (int) (6 * d);
        int h = (int) (170 * d);
        for (int i = 0; i < tiles.size(); i += 2) {
            LinearLayout row = new LinearLayout(root.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(m, m, m, m);
            row.addView(tiles.get(i), lp);
            if (i + 1 < tiles.size()) {
                row.addView(tiles.get(i + 1), lp);
            } else {
                row.addView(new View(root.getContext()),
                        new LinearLayout.LayoutParams(0, 1, 1f));
            }
            container.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    @Test
    public void everydayHomeHighContrast() {
        android.content.Context ctx=paparazzi.getContext();
        View v=paparazzi.inflate(R.layout.activity_main);
        v.setBackgroundColor(ctx.getResources().getColor(R.color.contrast_bg));
        ((TextView) v.findViewById(R.id.home_clock)).setText("13:00");
        ((TextView) v.findViewById(R.id.home_date)).setText("الاثنين ٩ نوفمبر");
        addHomeRows(v, v.findViewById(R.id.tiles_container), true);
        paparazzi.snapshot(v, "everyday-home-contrast");
    }
    @Test public void dialpadRuntimeContrastSurface() {
        android.content.Context ctx=paparazzi.getContext();
        View v=paparazzi.inflate(R.layout.activity_dialpad);
        v.setBackgroundColor(ctx.getResources().getColor(R.color.contrast_bg));
        ((TextView)v.findViewById(R.id.dial_display)).setText("0100 000 0000");
        v.findViewById(R.id.dial_display).setVisibility(View.VISIBLE);v.findViewById(R.id.dial_placeholder).setVisibility(View.GONE);
        paparazzi.snapshot(v,"dialpad-runtime-contrast");
    }
}