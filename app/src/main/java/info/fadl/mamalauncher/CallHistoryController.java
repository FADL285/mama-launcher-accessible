package info.fadl.mamalauncher;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.CallLog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import info.fadl.mamalauncher.ui.CallLogRow;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Owns only the history tab; Contacts and call execution remain separate. */
final class CallHistoryController implements AutoCloseable {
  private final PhoneActivity activity;
  private final BiConsumer<View, Runnable> guard;
  private final CallLogWorker worker = new CallLogWorker();
  private final CallLogWatcher watcher;
  private boolean active, selected, missedOnly;
  private int limit = CallLogRepository.PAGE_SIZE;
  private int displayGeneration;
  private HistorySnapshot displayed;
  private int displayedGeneration;
  private boolean rendered;
  private android.view.ViewTreeObserver drawObserver;
  private final Runnable afterDraw =
      () -> {
        clearDrawWatch();
        focused();
      };
  private final android.view.ViewTreeObserver.OnDrawListener drawn =
      () -> {
        if (!rendered) {
          rendered = true;
          postAfterDraw();
        }
      };

  private void postAfterDraw() {
    activity.findViewById(android.R.id.content).post(afterDraw);
  }

  private void clearDrawWatch() {
    activity.findViewById(android.R.id.content).removeCallbacks(afterDraw);
    if (drawObserver != null && drawObserver.isAlive()) drawObserver.removeOnDrawListener(drawn);
    drawObserver = null;
  }

  CallHistoryController(PhoneActivity a, BiConsumer<View, Runnable> guard) {
    activity = a;
    this.guard = guard;
    watcher = new CallLogWatcher(a, this::refresh);
    guard.accept(
        a.findViewById(R.id.btn_allow_history),
        () -> {
          if (CallLogAccess.granted(a)) {
            refresh();
            return;
          }
          a.requestPermissions(
              new String[] {Manifest.permission.READ_CALL_LOG}, CallLogAccess.REQUEST);
        });
    guard.accept(a.findViewById(R.id.btn_history_not_now), () -> a.selectHistory(false));
    guard.accept(a.findViewById(R.id.btn_history_retry), this::refresh);
    guard.accept(
        a.findViewById(R.id.btn_history_settings),
        () -> {
          try {
            a.startActivity(
                new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + a.getPackageName())));
          } catch (android.content.ActivityNotFoundException | SecurityException missing) {
            android.widget.Toast.makeText(
                    a, R.string.settings_unavailable, android.widget.Toast.LENGTH_LONG)
                .show();
          }
        });
    guard.accept(a.findViewById(R.id.btn_history_all), () -> filter(false));
    guard.accept(a.findViewById(R.id.btn_history_missed), () -> filter(true));
    guard.accept(
        a.findViewById(R.id.btn_history_more),
        () -> {
          if (limit < Integer.MAX_VALUE - CallLogRepository.PAGE_SIZE) {
            limit += CallLogRepository.PAGE_SIZE;
            refresh();
          }
        });
  }

  private void filter(boolean missed) {
    missedOnly = missed;
    limit = CallLogRepository.PAGE_SIZE;
    refresh();
  }

  void select(boolean value) {
    selected = value;
    displayGeneration++;
    worker.cancel();
    watcher.close();
    if (active && selected) {
      watcher.start();
      refresh();
    }
  }

  void resume() {
    active = true;
    if (selected) {
      watcher.start();
      refresh();
    }
  }

  void pause() {
    active = false;
    clearDrawWatch();
    displayGeneration++;
    worker.cancel();
    watcher.close();
    displayed = null;
    ((ListView) activity.findViewById(R.id.history_list)).setAdapter(null);
  }

  void focused() {
    if (rendered && displayed != null && valid(displayedGeneration) && activity.hasWindowFocus()) {
      try {
        MissedCallState.acknowledge(activity, displayed.pending);
      } catch (SecurityException revoked) {
        refresh();
      }
      displayed = null;
    }
  }

  void refresh() {
    if (!active || !selected) return;
    clearDrawWatch();
    displayed = null;
    final int current = ++displayGeneration;
    boolean permission = CallLogAccess.granted(activity);
    show(permission ? R.string.history_loading : R.string.history_permission, !permission, false);
    if (!permission) {
      MissedCallState.permissionLost(activity);
      return;
    }
    watcher.start();
    final boolean filter = missedOnly;
    final int pageLimit = limit;
    worker.read(
        signal -> {
          // Pending snapshot BEFORE history read. A newly arriving call cannot be
          // acknowledged merely because it joined a later pending query.
          Set<String> pending = MissedCallState.read(activity, signal).pending;
          CallLogRepository.Result page =
              CallLogRepository.load(activity.getContentResolver(), filter, pageLimit, signal);
          Map<String, ContactEntry> contacts = HistorySnapshot.contacts(activity, signal);
          signal.throwIfCanceled();
          return new HistorySnapshot(page, pending, contacts);
        },
        snapshot -> {
          if (!valid(current)) {
            if (active
                && selected
                && current == displayGeneration
                && !CallLogAccess.granted(activity)) refresh();
            return;
          }
          bind(snapshot, current);
        },
        () -> {
          if (active && selected) {
            if (!CallLogAccess.granted(activity)) refresh();
            else show(R.string.history_failed, false, true);
          }
        });
    selectFilter();
  }

  private boolean valid(int generation) {
    return active
        && selected
        && generation == displayGeneration
        && !activity.isFinishing()
        && !activity.isDestroyed()
        && CallLogAccess.granted(activity);
  }

  private void selectFilter() {
    for (int id : new int[] {R.id.btn_history_all, R.id.btn_history_missed}) {
      boolean yes = id == (missedOnly ? R.id.btn_history_missed : R.id.btn_history_all);
      info.fadl.mamalauncher.ui.PhoneUi.select(
          activity.findViewById(android.R.id.content), id, yes);
    }
  }

  private void bind(HistorySnapshot snapshot, int generation) {
    ListView list = activity.findViewById(R.id.history_list);
    activity.findViewById(R.id.history_filters).setVisibility(View.VISIBLE);
    activity
        .findViewById(R.id.history_state)
        .setVisibility(snapshot.page.entries.isEmpty() ? View.VISIBLE : View.GONE);
    ((TextView) activity.findViewById(R.id.history_message)).setText(R.string.history_empty);
    list.setVisibility(snapshot.page.entries.isEmpty() ? View.GONE : View.VISIBLE);
    activity.findViewById(R.id.btn_allow_history).setVisibility(View.GONE);
    activity.findViewById(R.id.btn_history_not_now).setVisibility(View.GONE);
    activity.findViewById(R.id.btn_history_settings).setVisibility(View.GONE);
    activity.findViewById(R.id.history_permission_help).setVisibility(View.GONE);
    activity.findViewById(R.id.btn_history_retry).setVisibility(View.GONE);
    activity
        .findViewById(R.id.btn_history_more)
        .setVisibility(snapshot.page.more ? View.VISIBLE : View.GONE);
    list.setAdapter(
        new BaseAdapter() {
          public int getCount() {
            return snapshot.page.entries.size();
          }

          public Object getItem(int p) {
            return snapshot.page.entries.get(p);
          }

          public long getItemId(int p) {
            return snapshot.page.entries.get(p).id;
          }

          public View getView(int p, View old, ViewGroup parent) {
            CallLogEntry row = snapshot.page.entries.get(p);
            ContactEntry contact =
                activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                        == PackageManager.PERMISSION_GRANTED
                    ? snapshot.contact(row)
                    : null;
            CallLogRow view =
                old instanceof CallLogRow ? (CallLogRow) old : new CallLogRow(activity);
            view.bind(
                title(row, contact),
                row.callable() && contact != null ? row.number : "",
                detail(row));
            guard.accept(view, () -> open(row));
            return view;
          }
        });
    // Wait for a real window draw, then validate again after the frame.
    displayed = snapshot;
    displayedGeneration = generation;
    rendered = false;
    drawObserver = activity.findViewById(android.R.id.content).getViewTreeObserver();
    drawObserver.addOnDrawListener(drawn);
  }

  private void show(int message, boolean permission, boolean retry) {
    ((ListView) activity.findViewById(R.id.history_list)).setAdapter(null);
    activity.findViewById(R.id.history_filters).setVisibility(View.GONE);
    activity.findViewById(R.id.history_list).setVisibility(View.GONE);
    activity.findViewById(R.id.history_state).setVisibility(View.VISIBLE);
    ((TextView) activity.findViewById(R.id.history_message)).setText(message);
    activity
        .findViewById(R.id.btn_allow_history)
        .setVisibility(permission ? View.VISIBLE : View.GONE);
    activity
        .findViewById(R.id.btn_history_not_now)
        .setVisibility(permission ? View.VISIBLE : View.GONE);
    boolean recovery = permission && Prefs.get(activity).getBoolean(CallLogAccess.DENIED, false);
    activity
        .findViewById(R.id.btn_history_settings)
        .setVisibility(recovery ? View.VISIBLE : View.GONE);
    activity
        .findViewById(R.id.history_permission_help)
        .setVisibility(recovery ? View.VISIBLE : View.GONE);
    activity.findViewById(R.id.btn_history_retry).setVisibility(retry ? View.VISIBLE : View.GONE);
    activity.findViewById(R.id.btn_history_more).setVisibility(View.GONE);
  }

  private String title(CallLogEntry row, ContactEntry contact) {
    if (row.presentation != CallLog.Calls.PRESENTATION_ALLOWED)
      return activity.getString(R.string.history_private);
    if (!row.callable()) return activity.getString(R.string.history_unknown_number);
    return contact != null && !contact.name.isEmpty() ? contact.name : row.number;
  }

  private String detail(CallLogEntry row) {
    return activity.getString(typeLabel(row.type))
        + " · "
        + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(new Date(row.date));
  }

  static int typeLabel(int type) {
    switch (type) {
      case CallLog.Calls.INCOMING_TYPE:
        return R.string.history_incoming;
      case CallLog.Calls.OUTGOING_TYPE:
        return R.string.history_outgoing;
      case CallLog.Calls.MISSED_TYPE:
        return R.string.history_missed_type;
      case CallLog.Calls.REJECTED_TYPE:
        return R.string.history_rejected;
      case CallLog.Calls.BLOCKED_TYPE:
        return R.string.history_blocked;
      default:
        return R.string.history_other;
    }
  }

  private void open(CallLogEntry row) {
    if (!row.callable()) {
      new AlertDialog.Builder(activity)
          .setMessage(title(row, null) + "\n" + detail(row))
          .setPositiveButton(android.R.string.ok, null)
          .show();
      return;
    }
    final int current = ++displayGeneration;
    worker.read(
        signal -> HistorySnapshot.contacts(activity, signal),
        contacts -> {
          if (!valid(current)) return;
          ContactEntry contact = contacts.get(DialContactMatcher.digits(row.number));
          String label = title(row, contact);
          ContactEntry exact =
              new ContactEntry(
                  contact == null ? -1 : contact.id,
                  label,
                  row.number,
                  contact == null ? null : contact.photoUri,
                  contact != null && contact.starred);
          activity.startActivity(ConfirmCallActivity.intentFor(activity, exact));
        },
        () -> {
          if (active && selected) show(R.string.history_failed, false, true);
        });
  }

  @Override
  public void close() {
    pause();
    worker.close();
  }
}
