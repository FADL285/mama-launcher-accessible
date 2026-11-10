package info.fadl.mamalauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Pending-minus-acknowledged. Store only identities, never numbers or names. */
final class MissedCallState {
  private static final String FILE = "missed_review_v1";
  private static final String INITIALIZED = "initialized", ACK = "acknowledged";
  private static long revision;

  private MissedCallState() {}

  static final class Snapshot {
    final Set<String> pending;
    final int count;

    Snapshot(Set<String> pending, int count) {
      this.pending = pending;
      this.count = count;
    }
  }

  static Snapshot read(Context c, android.os.CancellationSignal signal) {
    final long before;
    synchronized (MissedCallState.class) {
      before = revision;
    }
    Set<String> pending = CallLogRepository.pending(c.getContentResolver(), signal);
    synchronized (MissedCallState.class) {
      signal.throwIfCanceled();
      if (before != revision) throw new android.os.OperationCanceledException();
      return new Snapshot(pending, reconcile(c, pending));
    }
  }

  static synchronized int reconcile(Context c, Set<String> pending) {
    if (!CallLogAccess.granted(c)) throw new SecurityException("Call log permission lost");
    SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    Set<String> ack = new HashSet<>(p.getStringSet(ACK, Collections.emptySet()));
    if (!p.getBoolean(INITIALIZED, false)) ack.addAll(pending);
    ack.retainAll(pending);
    if ((!p.getBoolean(INITIALIZED, false)
            || !ack.equals(p.getStringSet(ACK, Collections.emptySet())))
        && !p.edit().putBoolean(INITIALIZED, true).putStringSet(ACK, ack).commit())
      throw new IllegalStateException("Cannot store missed-call baseline");
    revision++;
    Set<String> unseen = new HashSet<>(pending);
    unseen.removeAll(ack);
    return unseen.size();
  }

  static synchronized void acknowledge(Context c, Set<String> displayedSnapshot) {
    if (!CallLogAccess.granted(c)) throw new SecurityException("Call log permission lost");
    SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    Set<String> ack = new HashSet<>(p.getStringSet(ACK, Collections.emptySet()));
    ack.addAll(displayedSnapshot);
    p.edit().putBoolean(INITIALIZED, true).putStringSet(ACK, ack).apply();
    revision++;
  }

  static synchronized void permissionLost(Context c) {
    SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    if (p.getBoolean(INITIALIZED, false)) {
      p.edit().clear().apply();
      revision++;
    }
  }
}
