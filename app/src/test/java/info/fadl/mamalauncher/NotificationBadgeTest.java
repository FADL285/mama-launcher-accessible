package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.app.Notification;
import android.view.View;
import android.widget.LinearLayout;
import info.fadl.mamalauncher.ui.BadgeView;
import info.fadl.mamalauncher.ui.UiBinder;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 25, 33})
public class NotificationBadgeTest {
  @Before
  public void reset() {
    NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.DISABLED);
  }

  NotificationBadgeStore.Item item(String key, String pkg, boolean summary) {
    return new NotificationBadgeStore.Item(key, pkg, "group", summary);
  }

  @Test
  public void duplicateKeysUpdateRatherThanAccumulate() {
    NotificationBadgeStore.replace(
        NotificationBadgeStore.Status.READY,
        Arrays.asList(item("a", "one", false), item("a", "two", false)));
    assertFalse(NotificationBadgeStore.has("one"));
    assertTrue(NotificationBadgeStore.has("two"));
  }

  @Test
  public void summaryAloneAndChildBothMeanOnlyExistence() {
    NotificationBadgeStore.replace(
        NotificationBadgeStore.Status.READY, Arrays.asList(item("a", "one", true)));
    assertTrue(NotificationBadgeStore.has("one"));
    NotificationBadgeStore.replace(
        NotificationBadgeStore.Status.READY,
        Arrays.asList(item("a", "one", true), item("b", "one", false)));
    assertTrue(NotificationBadgeStore.has("one"));
  }

  @Test
  public void removalAndDisconnectInvalidateStaleMarks() {
    NotificationBadgeStore.replace(
        NotificationBadgeStore.Status.READY, Arrays.asList(item("a", "one", false)));
    NotificationBadgeStore.invalidate(NotificationBadgeStore.Status.DISCONNECTED);
    assertFalse(NotificationBadgeStore.has("one"));
    assertEquals(NotificationBadgeStore.Status.DISCONNECTED, NotificationBadgeStore.status());
    NotificationBadgeStore.replace(NotificationBadgeStore.Status.READY, Collections.emptyList());
    assertEquals(NotificationBadgeStore.Status.READY, NotificationBadgeStore.status());
  }

  @Test
  public void standardServiceMediaNavigationProgressAreExcluded() {
    for (String category :
        new String[] {
          Notification.CATEGORY_SERVICE,
          Notification.CATEGORY_TRANSPORT,
          Notification.CATEGORY_NAVIGATION,
          Notification.CATEGORY_PROGRESS
        }) {
      Notification n = new Notification();
      n.category = category;
      assertFalse(LauncherNotificationListener.eligible(n));
    }
    Notification n = new Notification();
    assertTrue(LauncherNotificationListener.eligible(n));
    n.flags = Notification.FLAG_ONGOING_EVENT;
    assertFalse(LauncherNotificationListener.eligible(n));
    n.flags = Notification.FLAG_FOREGROUND_SERVICE;
    assertFalse(LauncherNotificationListener.eligible(n));
  }

  @Test
  public void dotAccessibilityBelongsToTileAndCanBeRemoved() {
    android.content.Context c = RuntimeEnvironment.getApplication();
    View tile =
        UiBinder.buildHomeTile(
            c, "تطبيق", c.getDrawable(R.drawable.ic_chat), R.drawable.chip_blue, false, null);
    assertEquals(2, ((LinearLayout) tile).getChildCount());
    UiBinder.bindHomeBadge(tile, -2, "فيه إشعارات");
    BadgeView mark = tile.findViewWithTag(BadgeView.TAG);
    assertEquals(View.VISIBLE, mark.getVisibility());
    assertTrue(tile.getContentDescription().toString().contains("فيه إشعارات"));
    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, mark.getImportantForAccessibility());
    UiBinder.bindHomeBadge(tile, 0, "");
    assertEquals(View.GONE, mark.getVisibility());
    assertEquals("تطبيق", tile.getContentDescription());
  }
}
