package info.fadl.mamalauncher;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Process-local metadata only. Unknown is distinct from an empty ready snapshot. */
final class NotificationBadgeStore {
  enum Status {
    DISABLED,
    CONNECTING,
    READY,
    DISCONNECTED,
    UNAVAILABLE
  }

  static final class Item {
    final String key, pkg, group;
    final boolean summary;

    Item(String key, String pkg, String group, boolean summary) {
      this.key = key;
      this.pkg = pkg;
      this.group = group;
      this.summary = summary;
    }
  }

  private static final Map<String, Item> items = new HashMap<>();
  private static Status status = Status.DISABLED;
  private static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();

  static synchronized Status status() {
    return status;
  }

  static void replace(Status next, Iterable<Item> snapshot) {
    synchronized (NotificationBadgeStore.class) {
      items.clear();
      for (Item i : snapshot) items.put(i.key, i);
      status = next;
    }
    for (Runnable listener : listeners) listener.run();
  }

  static void invalidate(Status next) {
    replace(next, java.util.Collections.emptyList());
  }

  static synchronized boolean has(String pkg) {
    if (status != Status.READY) return false;
    // A summary alone is enough for existence, children don't add a second mark.
    for (Item item : items.values()) if (item.pkg.equals(pkg)) return true;
    return false;
  }

  static void listen(Runnable listener) {
    listeners.add(listener);
  }

  static void unlisten(Runnable listener) {
    listeners.remove(listener);
  }
}
