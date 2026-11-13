package info.fadl.mamalauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.CancellationSignal;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;

/** Optional contact resolution is separate from required call-log success. */
final class HistorySnapshot {
  final CallLogRepository.Result page;
  final Set<String> pending;
  final ContactIndex contacts;

  HistorySnapshot(CallLogRepository.Result page, Set<String> pending, ContactIndex contacts) {
    this.page = page;
    this.pending = pending;
    this.contacts = contacts;
  }

  static final class ContactIndex {
    private final String country;
    private final Map<String, ContactEntry> entries;

    ContactIndex(List<ContactEntry> contacts, String country) {
      this.country = country;
      Map<String, ContactEntry> result = new HashMap<>();
      Set<String> ambiguous = new HashSet<>();
      for (ContactEntry contact : contacts)
        for (ContactEntry.PhoneNumber pn : contact.numbers) {
          String key = key(pn.number);
          if (key.isEmpty()) continue;
          ContactEntry previous = result.get(key);
          if (previous != null && previous.id != contact.id) ambiguous.add(key);
          result.put(key, contact);
        }
      for (String key : ambiguous) result.remove(key);
      entries = Collections.unmodifiableMap(result);
    }

    private String key(String number) {
      String raw = DialContactMatcher.digits(number);
      if (raw.replace("+", "").length() < 7) return raw;
      String e164 = android.telephony.PhoneNumberUtils.formatNumberToE164(raw, country);
      return e164 == null ? raw : e164;
    }

    ContactEntry find(String number) {
      return entries.get(key(number));
    }
  }

  static ContactIndex contacts(Context c, CancellationSignal signal) {
    String region = NumberRegion.current(c);
    ContactIndex empty = new ContactIndex(Collections.emptyList(), region);
    if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) return empty;
    List<ContactEntry> current;
    try {
      current = ContactsLoader.loadVerified(c.getContentResolver(), c.getResources(), NumberRegion.current(c));
      signal.throwIfCanceled();
    } catch (RuntimeException unavailable) {
      return empty;
    }
    if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) return empty;
    return new ContactIndex(current, region);
  }

  ContactEntry contact(CallLogEntry row) {
    return row.callable() ? contacts.find(row.number) : null;
  }
}
