package info.fadl.mamalauncher;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.*;
import android.widget.TextView;
import info.fadl.mamalauncher.ui.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 25, 33})
public class PhoneTabSurfaceTest {
  @Test public void bothTabsKeepFlatSurfacesWhenSelectionAndPressChange() {
    Context c = RuntimeEnvironment.getApplication(); c.setTheme(R.style.MamaTheme);
    View root = LayoutInflater.from(c).inflate(R.layout.activity_phone, null);
    for (boolean contacts : new boolean[] {true, false}) {
      PhoneUi.select(root, R.id.btn_tab_contacts, contacts);
      PhoneUi.select(root, R.id.btn_tab_history, !contacts);
      for (int id : new int[] {R.id.btn_tab_contacts, R.id.btn_tab_history}) {
        TextView tab = root.findViewById(id);
        assertNull(tab.getStateListAnimator());
        for (boolean pressed : new boolean[] {false, true, false}) {
          tab.setPressed(pressed); tab.jumpDrawablesToCurrentState();
          assertEquals(0f, tab.getElevation(), 0f);
          assertEquals(0f, tab.getTranslationZ(), 0f);
        }
        assertTrue(tab.getMinimumHeight() >= 64 * c.getResources().getDisplayMetrics().density);
      }
    }
  }
}
