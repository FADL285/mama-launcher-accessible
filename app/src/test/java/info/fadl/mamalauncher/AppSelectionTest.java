package info.fadl.mamalauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AppSelectionTest {

    @Test
    public void noAppsAreImposedByDefault() {
        assertTrue(AppSelection.parse(null).isEmpty());
        assertTrue(AppSelection.parse("").isEmpty());
        assertTrue(AppSelection.parse("   ").isEmpty());
    }

    @Test
    public void parseKeepsOrderAndDropsDuplicatesAndBlanks() {
        List<String> parsed = AppSelection.parse(
                " com.google.android.youtube,,com.whatsapp ,com.google.android.youtube ");
        assertEquals(Arrays.asList("com.google.android.youtube", "com.whatsapp"), parsed);
    }

    @Test
    public void joinRoundTripsThroughParse() {
        List<String> sel = Arrays.asList("a.b", "c.d", "a.b", "e.f");
        assertEquals(Arrays.asList("a.b", "c.d", "e.f"),
                AppSelection.parse(AppSelection.join(sel)));
    }

    @Test
    public void moveSwapsNeighboursAndKeepsOrder() {
        List<String> sel = Arrays.asList("a.b", "c.d", "e.f");
        assertEquals(Arrays.asList("c.d", "a.b", "e.f"), AppSelection.move(sel, 1, -1));
        assertEquals(Arrays.asList("a.b", "e.f", "c.d"), AppSelection.move(sel, 1, +1));
    }

    @Test
    public void moveAtEdgesLeavesListUntouched() {
        List<String> sel = Arrays.asList("a.b", "c.d");
        assertEquals(sel, AppSelection.move(sel, 0, -1));
        assertEquals(sel, AppSelection.move(sel, 1, +1));
        assertEquals(sel, AppSelection.move(sel, 5, +1));
        assertEquals(sel, AppSelection.move(sel, -1, +1));
    }
}
