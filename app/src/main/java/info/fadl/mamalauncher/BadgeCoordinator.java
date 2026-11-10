package info.fadl.mamalauncher;

import android.content.Context;
import android.view.View;
import info.fadl.mamalauncher.ui.UiBinder;
import java.util.HashMap;
import java.util.Map;

/** Foreground badge owner. Source state never changes the underlying notification/log. */
final class BadgeCoordinator implements AutoCloseable {
  private final Context context;
  private final CallLogWorker worker = new CallLogWorker();
  private final CallLogWatcher watcher;
  private final Map<String, View> tiles = new HashMap<>();
  private final Runnable notifications = this::bind;
  private int missed = -1;
  private boolean active;

  BadgeCoordinator(Context context) {
    this.context = context;
    watcher = new CallLogWatcher(context, this::refreshMissed);
  }

  void clearTiles() {
    tiles.clear();
  }

  void tile(String pkg, View tile) {
    tiles.put(pkg, tile);
    bind();
  }

  void start() {
    active = true;
    NotificationBadgeStore.listen(notifications);
    NotificationAccess.refresh(context);
    watcher.start();
    refreshMissed();
  }

  void stop() {
    active = false;
    NotificationBadgeStore.unlisten(notifications);
    watcher.close();
    worker.cancel();
    missed = -1;
  }

  private void refreshMissed() {
    if (!active) return;
    if (!CallLogAccess.granted(context)) {
      MissedCallState.permissionLost(context);
      worker.cancel();
      missed = -1;
      bind();
      return;
    }
    missed = -1;
    bind();
    worker.read(
        signal -> MissedCallState.read(context, signal).count,
        count -> {
          missed = CallLogAccess.granted(context) ? count : -1;
          bind();
        },
        () -> {
          missed = -1;
          bind();
        });
  }

  private void bind() {
    for (Map.Entry<String, View> tile : tiles.entrySet()) {
      boolean phone = AppSelection.PHONE.equals(tile.getKey());
      UiBinder.bindHomeBadge(
          tile.getValue(),
          phone ? missed : NotificationBadgeStore.has(tile.getKey()) ? -2 : 0,
          phone
              ? context.getString(R.string.missed_badge_description, missed)
              : context.getString(R.string.has_notifications));
    }
  }

  @Override
  public void close() {
    stop();
    worker.close();
    tiles.clear();
  }
}
