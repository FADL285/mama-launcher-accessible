package info.fadl.mamalauncher.ui;

import java.util.List;

/** One person on the home grid. Keeps ALL their numbers: the confirmation
 *  screen shows them and nothing dials until a number is visibly chosen. */
public class ContactEntry {

    public static class PhoneNumber {
        public final String number;
        public final String label; // localized type label, e.g. موبايل / البيت

        public PhoneNumber(String number, String label) {
            this.number = number == null ? "" : number;
            this.label = label == null ? "" : label;
        }
    }

    public final long id;
    public final String name;
    public final List<PhoneNumber> numbers;
    public final String photoUri; // may be null
    public final boolean starred;
    /** Provider photo version (PHOTO_ID); null when the provider has none.
     *  The photo URI alone cannot detect a new photo (thumbnail-only contacts
     *  keep the same URI across edits), so photo identity is URI + version. */
    public final String photoVersion;

    public ContactEntry(long id, String name, List<PhoneNumber> numbers, String photoUri, boolean starred) {
        this(id, name, numbers, photoUri, starred, null);
    }

    public ContactEntry(long id, String name, List<PhoneNumber> numbers, String photoUri, boolean starred, String photoVersion) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.numbers = numbers;
        this.photoUri = photoUri;
        this.starred = starred;
        this.photoVersion = photoVersion;
    }

    /** Convenience for tests: single number, no label. */
    public ContactEntry(long id, String name, String number, String photoUri, boolean starred) {
        this(id, name, java.util.Collections.singletonList(new PhoneNumber(number, "")), photoUri, starred);
    }

    public String firstNumber() {
        return numbers.isEmpty() ? "" : numbers.get(0).number;
    }
}
