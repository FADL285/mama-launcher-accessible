package info.fadl.mamalauncher;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.provider.CallLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared provider boundary for history and the complete CURRENT pending set. */
final class CallLogRepository {
  static final int PAGE_SIZE = 200;
  private static final long MAX_READ_MS = 5000;
  private static final int MAX_PENDING = 10000;
  static final String PENDING =
      CallLog.Calls.TYPE
          + "=? AND "
          + CallLog.Calls.NEW
          + "=1 AND "
          + CallLog.Calls.IS_READ
          + " IS NOT 1";

  static final class Result {
    final List<CallLogEntry> entries;
    final boolean more;

    Result(List<CallLogEntry> entries, boolean more) {
      this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
      this.more = more;
    }
  }

  static Set<String> pending(ContentResolver resolver, CancellationSignal cancel) {
    long started = SystemClock.elapsedRealtime();
    Set<String> keys = new HashSet<>();
    try (Cursor cursor =
        resolver.query(
            CallLog.Calls.CONTENT_URI,
            new String[] {
              CallLog.Calls._ID,
              CallLog.Calls.DATE,
              CallLog.Calls.TYPE,
              CallLog.Calls.NEW,
              CallLog.Calls.IS_READ
            },
            PENDING,
            new String[] {String.valueOf(CallLog.Calls.MISSED_TYPE)},
            null,
            cancel)) {
      if (cursor == null) throw new IllegalStateException("Call log unavailable");
      while (cursor.moveToNext()) {
        checkBudget(started, cancel);
        if (keys.size() >= MAX_PENDING)
          throw new IllegalStateException("Pending snapshot too large");
        if (cursor.isNull(0) || cursor.isNull(1) || cursor.isNull(2))
          throw new IllegalStateException("Incomplete pending identity");
        if (cursor.getInt(2) != CallLog.Calls.MISSED_TYPE
            || cursor.isNull(3)
            || cursor.getInt(3) != 1
            || (!cursor.isNull(4) && cursor.getInt(4) != 0))
          throw new IllegalStateException("Malformed pending flags");
        keys.add(cursor.getLong(0) + ":" + cursor.getLong(1) + ":" + cursor.getInt(2));
      }
    }
    checkBudget(started, cancel);
    return keys;
  }

  static Result load(
      ContentResolver resolver, boolean missedOnly, int pageSize, CancellationSignal cancel) {
    long started = SystemClock.elapsedRealtime();
    List<CallLogEntry> entries = new ArrayList<>();
    boolean more = false;
    String[] columns = {
      CallLog.Calls._ID,
      CallLog.Calls.DATE,
      CallLog.Calls.DURATION,
      CallLog.Calls.TYPE,
      CallLog.Calls.NUMBER_PRESENTATION,
      CallLog.Calls.NUMBER
    };
    try (Cursor cursor =
        resolver.query(
            CallLog.Calls.CONTENT_URI,
            columns,
            missedOnly ? CallLog.Calls.TYPE + "=?" : null,
            missedOnly ? new String[] {String.valueOf(CallLog.Calls.MISSED_TYPE)} : null,
            CallLog.Calls.DATE + " DESC, " + CallLog.Calls._ID + " DESC",
            cancel)) {
      if (cursor == null) throw new IllegalStateException("Call log unavailable");
      while (cursor.moveToNext()) {
        checkBudget(started, cancel);
        if (entries.size() >= pageSize) {
          more = true;
          break;
        }
        if (cursor.isNull(0) || cursor.isNull(1) || cursor.isNull(3) || cursor.isNull(4))
          throw new IllegalStateException("Incomplete history record");
        entries.add(
            new CallLogEntry(
                cursor.getLong(0),
                cursor.getLong(1),
                cursor.getLong(2),
                cursor.getInt(3),
                cursor.getInt(4),
                cursor.getString(5),
                ""));
      }
    }
    checkBudget(started, cancel);
    // Query pending first at the controller level when an acknowledgement is intended.
    return new Result(entries, more);
  }

  private static void checkBudget(long started, CancellationSignal cancel) {
    cancel.throwIfCanceled();
    if (SystemClock.elapsedRealtime() - started > MAX_READ_MS)
      throw new IllegalStateException("Call log timed out");
  }
}
