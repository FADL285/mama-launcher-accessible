package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pilot default ordering (D3 still open): FIXED order, no auto-reorder.
 * Starred (favorites, chosen by whoever prepares the device via the system
 * contacts app) pinned first, then everyone else alphabetical Arabic-aware,
 * ties broken by contact id so the order never jumps between launches.
 */
public final class ContactSorter {
    private ContactSorter() {}

    public static List<ContactEntry> sort(List<ContactEntry> input) {
        List<ContactEntry> list = new ArrayList<>(input);
        final Collator ar = Collator.getInstance(new Locale("ar"));
        ar.setStrength(Collator.PRIMARY);
        Collections.sort(list, new Comparator<ContactEntry>() {
            @Override
            public int compare(ContactEntry a, ContactEntry b) {
                if (a.starred != b.starred) return a.starred ? -1 : 1;
                int byName = ar.compare(a.name, b.name);
                if (byName != 0) return byName;
                return Long.compare(a.id, b.id);
            }
        });
        return list;
    }
}
