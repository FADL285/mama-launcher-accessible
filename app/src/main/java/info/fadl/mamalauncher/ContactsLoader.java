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
        return group(rows, "");
    }

    /** T-17: one number saved in several forms (national 0100...,
     *  international +20100..., IDD 0020100..., spaced, Unicode digits)
     *  shows as ONE button. Equivalence classes run over the WHOLE set of
     *  raw forms (digits-equal via DialContactMatcher.digits, or E.164-equal
     *  via DialContactMatcher.exact with the device region), so the old
     *  first-seen collapse cannot leak provider order into the result. The
     *  survivor of each class is picked by a fixed rule: national form
     *  first (no '+' and no "00" international prefix - the form mama sees
     *  and dials today), then fewer normalized digits, then lexicographic
     *  on the normalized form, the raw string, and the label. The survivor
     *  keeps its own display form and its own type label. With an
     *  unresolvable region only digit-identical forms merge (safe). */
    public static List<ContactEntry> group(List<RawRow> rows, String country) {
        Map<Long, ContactEntry> byId = new LinkedHashMap<>();
        Map<Long, List<RawRow>> numbers = new LinkedHashMap<>();
        for (RawRow r : rows) {
            if (r.number == null || r.number.trim().isEmpty()) continue;
            if (!byId.containsKey(r.id)) {
                byId.put(r.id, new ContactEntry(r.id, r.name,
                        new ArrayList<>(), r.photoUri, r.starred));
                numbers.put(r.id, new ArrayList<>());
            }
            numbers.get(r.id).add(r);
        }
        List<ContactEntry> out = new ArrayList<>();
        for (Map.Entry<Long, ContactEntry> e : byId.entrySet()) {
            ContactEntry base = e.getValue();
            out.add(new ContactEntry(base.id, base.name,
                    dedupEquivalentForms(numbers.get(e.getKey()), country),
                    base.photoUri, base.starred));
        }
        return out;
    }

    /** Merges equivalent forms class by class; each class keeps its first
     *  member's position, displaying the fixed-rule survivor. */
    private static List<ContactEntry.PhoneNumber> dedupEquivalentForms(
            List<RawRow> forms, String country) {
        boolean[] consumed = new boolean[forms.size()];
        List<ContactEntry.PhoneNumber> out = new ArrayList<>();
        for (int i = 0; i < forms.size(); i++) {
            if (consumed[i]) continue;
            RawRow survivor = forms.get(i);
            for (int j = i + 1; j < forms.size(); j++) {
                if (consumed[j]) continue;
                if (!sameNumber(forms.get(i).number, forms.get(j).number, country)) continue;
                consumed[j] = true;
                survivor = preferredForm(survivor, forms.get(j));
            }
            out.add(new ContactEntry.PhoneNumber(survivor.number, survivor.typeLabel));
        }
        return out;
    }

    /** Same number: identical digit sequence (DialContactMatcher.digits
     *  normalizes Unicode digits and separators), or E.164-equal in the
     *  device region. Region-free equality needs no country. */
    private static boolean sameNumber(String a, String b, String country) {
        if (normalizeNumber(a).equals(normalizeNumber(b))) return true;
        // Region-free: the same digit sequence in any script (ASCII or
        // Arabic-Indic) is one number - DialContactMatcher.digits equates them.
        String da = DialContactMatcher.digits(a), db = DialContactMatcher.digits(b);
        if (!da.isEmpty() && da.equals(db)) return true;
        if (country == null || country.isEmpty()) return false;
        return DialContactMatcher.exact(a, b, country);
    }

    private static int formRank(String normalized) {
        if (normalized.startsWith("+")) return 1;
        if (normalized.startsWith("00")) return 2;
        return 0; // national: no '+', no international prefix
    }

    /** Fixed survivor rule over the whole class: national rank first, then
     *  fewer digits, then lexicographic on normalized form, raw string and
     *  label - fully deterministic, independent of provider order. */
    private static RawRow preferredForm(RawRow a, RawRow b) {
        String na = normalizeNumber(a.number), nb = normalizeNumber(b.number);
        int ra = formRank(na), rb = formRank(nb);
        if (ra != rb) return ra < rb ? a : b;
        if (na.length() != nb.length()) return na.length() < nb.length() ? a : b;
        int c = na.compareTo(nb); if (c != 0) return c < 0 ? a : b;
        c = a.number.compareTo(b.number); if (c != 0) return c < 0 ? a : b;
        String la = a.typeLabel == null ? "" : a.typeLabel;
        String lb = b.typeLabel == null ? "" : b.typeLabel;
        return la.compareTo(lb) <= 0 ? a : b;
    }

    public static List<ContactEntry> load(ContentResolver resolver, Resources res) {
        return load(resolver,res,"",false);
    }
    /** Saving requires a completed read, not a null cursor mistaken for zero matches. */
    static List<ContactEntry> loadVerified(ContentResolver resolver,Resources res) {
        return load(resolver,res,"",true);
    }
    /** Verified load with the device region for national/international dedup (T-17). */
    static List<ContactEntry> loadVerified(ContentResolver resolver,Resources res,String country) {
        return load(resolver,res,country,true);
    }
    private static List<ContactEntry> load(ContentResolver resolver, Resources res, String country, boolean requireResult) {
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
            if(c==null) {if(requireResult)throw new IllegalStateException("Contacts provider unavailable");return new ArrayList<>();}
            while (c.moveToNext()) {
                CharSequence label = Phone.getTypeLabel(res, c.getInt(3), c.getString(4));
                rows.add(new RawRow(
                        c.getLong(0), c.getString(1), c.getString(2),
                        label == null ? "" : label.toString(),
                        c.getString(5), c.getInt(6) == 1));
            }
        }
        return group(rows, country);
    }
}
