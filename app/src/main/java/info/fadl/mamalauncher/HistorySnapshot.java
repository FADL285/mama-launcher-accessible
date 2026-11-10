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
  final Map<String, ContactEntry> contacts;

  HistorySnapshot(
      CallLogRepository.Result page, Set<String> pending, Map<String, ContactEntry> contacts) {
    this.page = page;
    this.pending = pending;
    this.contacts = contacts;
  }

  static Map<String, ContactEntry> contacts(Context c, CancellationSignal signal) {
    Map<String, ContactEntry> result = new HashMap<>();
    Set<String> ambiguous = new HashSet<>();
    if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) return result;
    try {
      for (ContactEntry entry :
          ContactsLoader.loadVerified(c.getContentResolver(), c.getResources())) {
        signal.throwIfCanceled();
        for (ContactEntry.PhoneNumber pn : entry.numbers) {
          String key = DialContactMatcher.digits(pn.number);
          if (key.isEmpty()) continue;
          ContactEntry previous = result.get(key);
          if (previous != null && previous.id != entry.id) ambiguous.add(key);
          result.put(key, entry);
        }
      }
    } catch (RuntimeException unavailable) {
      return Collections.emptyMap();
    }
    for (String key : ambiguous) result.remove(key);
    if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) return Collections.emptyMap();
    return result;
  }

  ContactEntry contact(CallLogEntry row) {
    return row.callable() ? contacts.get(DialContactMatcher.digits(row.number)) : null;
  }
}
