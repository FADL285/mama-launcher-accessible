package info.fadl.mamalauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.CancellationSignal;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;

/** Optional contact resolution is separate from required call-log success. */
final class HistorySnapshot {
  /** Contact labels are still being read. */
  static final int LABELS_LOADING = 0;
  /** Contact labels read successfully (possibly legitimately empty). */
  static final int LABELS_LOADED = 1;
  /** The label read failed or was abandoned; numbers still render, retry offered. */
  static final int LABELS_FAILED = 2;

  final CallLogRepository.Result page;
  final Set<String> pending;
  final ContactIndex contacts;
  final int labelsState;

  HistorySnapshot(
      CallLogRepository.Result page, Set<String> pending, ContactIndex contacts, int labelsState) {
    this.page = page;
    this.pending = pending;
    this.contacts = contacts;
    this.labelsState = labelsState;
  }

  HistorySnapshot withLabels(ContactIndex labels, int state) {
    return new HistorySnapshot(page, pending, labels, state);
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

  /**
   * Optional labels are either resolved or loudly unavailable - never silently
   * empty. Missing permission is the one quiet empty: the user declined labels.
   * Every read failure (deadline, blocked provider, cancellation) propagates so
   * the caller can surface it and offer a retry.
   */
  static ContactIndex contacts(Context c, CancellationSignal signal) {
    String region = NumberRegion.current(c);
    ContactIndex empty = new ContactIndex(Collections.emptyList(), region);
    if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) return empty;
    // T-06: the signal aborts the read mid-loop, not only after it. A failure
    // here is thrown, not folded into an empty index.
    List<ContactEntry> current =
        ContactsLoader.loadVerified(
            c.getContentResolver(), c.getResources(), NumberRegion.current(c), signal);
    signal.throwIfCanceled();
    if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) return empty;
    return new ContactIndex(current, region);
  }

  ContactEntry contact(CallLogEntry row) {
    return row.callable() ? contacts.find(row.number) : null;
  }
}
