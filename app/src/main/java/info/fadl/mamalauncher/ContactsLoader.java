package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads contacts WITH phone numbers from the device. Local only, no network. */
public final class ContactsLoader {
    private ContactsLoader() {}

    /** Raw row, separated from Android types so grouping is unit-testable. */
    public static class RawRow {
        public final long id;
        public final String name;
        public final String number;
        public final String typeLabel;
        public final String photoUri;
        public final boolean starred;

        public RawRow(long id, String name, String number, String typeLabel, String photoUri, boolean starred) {
            this.id = id; this.name = name; this.number = number;
            this.typeLabel = typeLabel; this.photoUri = photoUri; this.starred = starred;
        }
    }

    /** Comparison key: digits and one leading '+', so "0100 000-0001" and
     *  "01000000001" count as the same number. Display keeps the original. */
    static String normalizeNumber(String n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (Character.isDigit(ch) || (ch == '+' && sb.length() == 0)) sb.append(ch);
        }
        return sb.toString();
    }

    /** Groups rows into one entry per contact, keeping every distinct number in
     *  order. Linked raw contacts often repeat the same number; identical
     *  duplicates collapse so mama never has to choose between twins. */
    public static List<ContactEntry> group(List<RawRow> rows) {
        Map<Long, ContactEntry> byId = new LinkedHashMap<>();
        Map<Long, List<ContactEntry.PhoneNumber>> numbers = new LinkedHashMap<>();
        Map<Long, java.util.Set<String>> seen = new LinkedHashMap<>();
        for (RawRow r : rows) {
            if (r.number == null || r.number.trim().isEmpty()) continue;
            if (!byId.containsKey(r.id)) {
                byId.put(r.id, new ContactEntry(r.id, r.name,
                        new ArrayList<>(), r.photoUri, r.starred));
                numbers.put(r.id, new ArrayList<>());
                seen.put(r.id, new java.util.LinkedHashSet<>());
            }
            if (!seen.get(r.id).add(normalizeNumber(r.number))) continue;
            numbers.get(r.id).add(new ContactEntry.PhoneNumber(r.number, r.typeLabel));
        }
        List<ContactEntry> out = new ArrayList<>();
        for (Map.Entry<Long, ContactEntry> e : byId.entrySet()) {
            ContactEntry base = e.getValue();
            out.add(new ContactEntry(base.id, base.name, numbers.get(e.getKey()),
                    base.photoUri, base.starred));
        }
        return out;
    }

    public static List<ContactEntry> load(ContentResolver resolver, Resources res) {
        String[] projection = new String[]{
                Phone.CONTACT_ID,
                Phone.DISPLAY_NAME,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
                Phone.PHOTO_URI,
                Phone.STARRED
        };
        List<RawRow> rows = new ArrayList<>();
        try (Cursor c = resolver.query(Phone.CONTENT_URI, projection, null, null, null)) {
            if (c == null) return new ArrayList<>();
            while (c.moveToNext()) {
                CharSequence label = Phone.getTypeLabel(res, c.getInt(3), c.getString(4));
                rows.add(new RawRow(
                        c.getLong(0), c.getString(1), c.getString(2),
                        label == null ? "" : label.toString(),
                        c.getString(5), c.getInt(6) == 1));
            }
        }
        return group(rows);
    }
}
