package info.fadl.mamalauncher;

import static org.junit.Assert.*;
import android.view.View;
import android.content.Context;
import info.fadl.mamalauncher.ui.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 25, 33})
public class CallDirectionRowTest {
  @Test public void recycleClearsStaleIconWithoutChangingSpokenRow() {
    Context c = RuntimeEnvironment.getApplication(); c.setTheme(R.style.MamaTheme);
    CallLogRow row = new CallLogRow(c);
    row.bind("اسم تجريبي", "01000000000", "فائتة · تاريخ", false,
        CallDirectionView.Direction.MISSED);
    CallDirectionView icon = (CallDirectionView)((android.view.ViewGroup)row.getChildAt(2)).getChildAt(0);
    assertEquals(View.VISIBLE, icon.getVisibility());
    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, icon.getImportantForAccessibility());
    assertFalse(icon.isFocusable()); assertFalse(icon.isClickable());
    assertEquals("اسم تجريبي، 01000000000، فائتة · تاريخ", row.getContentDescription());
    row.bind("رقم مخفي", "", "أخرى · تاريخ", false, CallDirectionView.Direction.UNKNOWN);
    assertEquals(View.GONE, icon.getVisibility());
    assertEquals(CallDirectionView.Direction.UNKNOWN, icon.direction());
    assertEquals("رقم مخفي، ، أخرى · تاريخ", row.getContentDescription());
    row.bind("اسم آخر", "", "صادرة · تاريخ", false, CallDirectionView.Direction.OUTGOING);
    assertEquals(CallDirectionView.Direction.OUTGOING, icon.direction());
    assertEquals(View.VISIBLE, icon.getVisibility());
  }
}
