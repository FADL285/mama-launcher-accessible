package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

public class ContactsGrouperTest {

    @Test
    public void multipleNumbersStayWithThePerson() {
        List<ContactEntry> out = ContactsLoader.group(Arrays.asList(
                new ContactsLoader.RawRow(1, "منى", "01000000001", "موبايل", null, true),
                new ContactsLoader.RawRow(1, "منى", "0200000002", "البيت", null, true),
                new ContactsLoader.RawRow(2, "خالد", "01500000003", "موبايل", "p", false)));
        assertEquals(2, out.size());
        assertEquals(2, out.get(0).numbers.size());
        assertEquals("البيت", out.get(0).numbers.get(1).label);
    }

    @Test
    public void emptyNumbersAreSkipped() {
        List<ContactEntry> out = ContactsLoader.group(Arrays.asList(
                new ContactsLoader.RawRow(1, "حد", "  ", "", null, false),
                new ContactsLoader.RawRow(2, "خالد", "01500000003", "", null, false)));
        assertEquals(1, out.size());
        assertEquals(2, out.get(0).id);
    }
}
