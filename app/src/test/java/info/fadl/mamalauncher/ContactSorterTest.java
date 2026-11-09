package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

public class ContactSorterTest {

    private static ContactEntry c(long id, String name, boolean starred) {
        return new ContactEntry(id, name, "010000000" + id, null, starred);
    }

    @Test
    public void starredComeFirst() {
        List<ContactEntry> out = ContactSorter.sort(Arrays.asList(
                c(1, "أحمد", false), c(2, "خالد", true), c(3, "بنت", false)));
        assertEquals(2, out.get(0).id);
        assertFalse(out.get(1).starred);
        assertFalse(out.get(2).starred);
    }

    @Test
    public void alphabeticalWithinGroupsArabicAware() {
        List<ContactEntry> out = ContactSorter.sort(Arrays.asList(
                c(1, "سمير", false), c(2, "أحمد", false), c(3, "بنت", false)));
        assertEquals("أحمد", out.get(0).name);
        assertEquals("بنت", out.get(1).name);
        assertEquals("سمير", out.get(2).name);
    }

    @Test
    public void orderIsStableAcrossRuns() {
        List<ContactEntry> in = Arrays.asList(c(5, "منى", true), c(6, "منى", false), c(7, "منى", false));
        List<ContactEntry> first = ContactSorter.sort(in);
        List<ContactEntry> second = ContactSorter.sort(in);
        for (int i = 0; i < first.size(); i++) assertEquals(first.get(i).id, second.get(i).id);
    }

    @Test
    public void emptyAndNullNamesDoNotCrash() {
        List<ContactEntry> out = ContactSorter.sort(Arrays.asList(
                new ContactEntry(9, null, "010", null, false), c(10, "آية", false)));
        assertEquals(2, out.size());
    }
}
