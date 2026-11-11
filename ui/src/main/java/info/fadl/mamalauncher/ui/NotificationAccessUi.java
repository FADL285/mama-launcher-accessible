package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.TextView;

/** Honest capability disclosure for a build that has no notification listener. */
public final class NotificationAccessUi {
  private NotificationAccessUi() {}

  public static void unavailable(View root) {
    root.findViewById(R.id.btn_notification_access).setVisibility(View.GONE);
    root.findViewById(R.id.notification_restricted_help).setVisibility(View.GONE);
    ((TextView) root.findViewById(R.id.perm_notification_status))
        .setText(R.string.notification_access_not_in_build);
  }
}
