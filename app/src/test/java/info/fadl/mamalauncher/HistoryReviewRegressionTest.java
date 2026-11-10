package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Application;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import info.fadl.mamalauncher.ui.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "ar-rEG-w411dp-h820dp")
public class HistoryReviewRegressionTest {
  Application app;
  CallLogRepositoryTest.Fixture provider;

  @Before
  public void setup() {
    app = RuntimeEnvironment.getApplication();
    Prefs.get(app).edit().clear().apply();
    app.getSharedPreferences("missed_review_v1", 0).edit().clear().apply();
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
    Shadows.shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS);
    provider = new CallLogRepositoryTest.Fixture();
    provider.count = 240;
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
        android.provider.CallLog.AUTHORITY, provider);
    MissedCallState.reconcile(app, Collections.emptySet());
  }

  void settle() throws Exception {
    for (int i = 0; i < 30; i++) {
      Thread.sleep(10);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
  }

  ActivityController<PhoneActivity> history() throws Exception {
    ActivityController<PhoneActivity> c =
        Robolectric.buildActivity(PhoneActivity.class).setup().visible();
    c.get().findViewById(R.id.btn_tab_history).performClick();
    settle();
    layout(c.get());
    return c;
  }

  void layout(PhoneActivity a) {
    View root = a.findViewById(android.R.id.content);
    root.measure(
        View.MeasureSpec.makeMeasureSpec(822, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 822, 1500);
  }

  Set<String> acknowledged() {
    return app.getSharedPreferences("missed_review_v1", 0)
        .getStringSet("acknowledged", Collections.emptySet());
  }

  void draw(ActivityController<PhoneActivity> c, boolean focus) {
    c.windowFocusChanged(focus);
    c.get().findViewById(android.R.id.content).getViewTreeObserver().dispatchOnDraw();
  }

  @Test
  public void drawnFocusedHistoryAcknowledgesCompleteSnapshotNotOnly200Rows() throws Exception {
    ActivityController<PhoneActivity> c = history();
    assertEquals(0, acknowledged().size());
    draw(c, true);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue(c.get().hasWindowFocus());
    assertEquals(240, acknowledged().size());
    assertEquals(200, ((ListView) c.get().findViewById(R.id.history_list)).getAdapter().getCount());
    c.pause().stop().destroy();
  }

  @Test
  public void absentWindowFocusDoesNotAcknowledgeUntilFocusReturns() throws Exception {
    ActivityController<PhoneActivity> c = history();
    draw(c, false);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, acknowledged().size());
    c.windowFocusChanged(true);
    assertEquals(240, acknowledged().size());
    c.pause().stop().destroy();
  }

  @Test
  public void leaveTabBeforePostedDrawCallbackNeverAcknowledges() throws Exception {
    ActivityController<PhoneActivity> c = history();
    draw(c, true);
    c.get().findViewById(R.id.btn_tab_contacts).performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, acknowledged().size());
    c.pause().stop().destroy();
  }

  @Test
  public void pauseBeforePostedDrawCallbackNeverAcknowledges() throws Exception {
    ActivityController<PhoneActivity> c = history();
    draw(c, true);
    c.pause();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, acknowledged().size());
    c.stop().destroy();
  }

  @Test
  public void newCallAfterPendingSnapshotStaysUnacknowledged() throws Exception {
    ActivityController<PhoneActivity> c = history();
    provider.count = 241;
    draw(c, true);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(240, acknowledged().size());
    assertEquals(
        1,
        MissedCallState.reconcile(
            app, CallLogRepository.pending(app.getContentResolver(), new CancellationSignal())));
    c.pause().stop().destroy();
  }

  @Test
  public void loadMoreAndOrdinaryReturnKeepFirstVisibleIdentityAndOffset() throws Exception {
    ActivityController<PhoneActivity> c = history();
    PhoneActivity a = c.get();
    ListView list = a.findViewById(R.id.history_list);
    org.robolectric.util.ReflectionHelpers.callStaticMethod(
        org.robolectric.shadows.ShadowWindowManagerGlobal.class,
        "setInTouchMode",
        org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, true));
    list.setSelectionFromTop(40, -25);
    settle();
    int position = list.getFirstVisiblePosition(), top = list.getChildAt(0).getTop();
    assertTrue(position > 0);
    a.findViewById(R.id.btn_history_more).performClick();
    settle();
    assertEquals(240, list.getAdapter().getCount());
    assertEquals(position, list.getFirstVisiblePosition());
    assertEquals(top, list.getChildAt(0).getTop());
    c.pause().resume();
    settle();
    assertEquals(position, list.getFirstVisiblePosition());
    assertEquals(top, list.getChildAt(0).getTop());
    c.pause().stop().destroy();
  }

  @Test
  public void regionAwareIndexMatchesFullNumberAndRejectsAmbiguity() {
    ContactEntry saved = new ContactEntry(1, "اسم تجريبي", "01012345678", null, false);
    HistorySnapshot.ContactIndex index =
        new HistorySnapshot.ContactIndex(Arrays.asList(saved), "EG");
    assertSame(saved, index.find("+201012345678"));
    assertNull(index.find("12345678"));
    ContactEntry duplicate = new ContactEntry(2, "اسم آخر", "+201012345678", null, false);
    index = new HistorySnapshot.ContactIndex(Arrays.asList(saved, duplicate), "EG");
    assertNull(index.find("01012345678"));
    assertNull(index.find("+201012345678"));
  }

  @Test
  public void numberOnlyRowDirectionResetsOnRecycling() {
    CallLogRow row = new CallLogRow(new android.view.ContextThemeWrapper(app, R.style.MamaTheme));
    row.bind("+201012345678", "", "مكالمة", true);
    assertEquals(View.TEXT_DIRECTION_LTR, row.getChildAt(0).getTextDirection());
    row.bind("اسم تجريبي", "01012345678", "مكالمة", false);
    assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, row.getChildAt(0).getTextDirection());
  }

  @Test
  public void arabicBadgeQuantityUsesOneTwoFewAndMany() {
    android.content.res.Resources r = app.getResources();
    assertEquals(
        "مكالمة فائتة واحدة لم تُراجع",
        r.getQuantityString(R.plurals.missed_badge_description, 1, 1));
    assertEquals(
        "مكالمتان فائتتان لم تُراجعا",
        r.getQuantityString(R.plurals.missed_badge_description, 2, 2));
    assertTrue(r.getQuantityString(R.plurals.missed_badge_description, 3, 3).contains("مكالمات"));
    assertTrue(
        r.getQuantityString(R.plurals.missed_badge_description, 11, 11).contains("مكالمة فائتة"));
  }

  @Test
  public void revokeAndRegrantWhileUnobservedCannotBeInferredFromCurrentGrant() {
    Set<String> first = new HashSet<>(Arrays.asList("1:1:3"));
    MissedCallState.reconcile(app, first);
    Shadows.shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG);
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG);
    first.add("2:2:3");
    assertEquals(2, MissedCallState.reconcile(app, first));
  }

  @Test
  public void providerInsertionRetainsIdentityInsteadOfOldPosition() throws Exception {
    final boolean[] prepend = {false};
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
        android.provider.CallLog.AUTHORITY,
        new CallLogRepositoryTest.Fixture() {
          @Override
          public android.database.Cursor query(
              android.net.Uri uri,
              String[] columns,
              String selection,
              String[] args,
              String order) {
            android.database.Cursor old = super.query(uri, columns, selection, args, order);
            if (!prepend[0]) return old;
            android.database.MatrixCursor rows = new android.database.MatrixCursor(columns);
            Object[] first = new Object[columns.length];
            for (int j = 0; j < columns.length; j++)
              switch (columns[j]) {
                case "_id":
                  first[j] = 999;
                  break;
                case "date":
                  first[j] = 2000L;
                  break;
                case "type":
                  first[j] = 3;
                  break;
                case "new":
                  first[j] = 1;
                  break;
                case "is_read":
                  first[j] = null;
                  break;
                case "presentation":
                  first[j] = 1;
                  break;
                case "number":
                  first[j] = "01000000000";
                  break;
                default:
                  first[j] = 0;
              }
            rows.addRow(first);
            while (old.moveToNext()) {
              Object[] values = new Object[columns.length];
              for (int j = 0; j < columns.length; j++)
                values[j] =
                    old.isNull(j)
                        ? null
                        : old.getType(j) == android.database.Cursor.FIELD_TYPE_STRING
                            ? old.getString(j)
                            : old.getLong(j);
              rows.addRow(values);
            }
            old.close();
            return rows;
          }
        });
    ActivityController<PhoneActivity> c = history();
    PhoneActivity a = c.get();
    ListView list = a.findViewById(R.id.history_list);
    list.setSelectionFromTop(40, 0);
    settle();
    String identity =
        ((CallLogEntry) list.getAdapter().getItem(list.getFirstVisiblePosition())).identity();
    int top = list.getChildAt(0).getTop();
    prepend[0] = true;
    c.pause().resume();
    settle();
    assertEquals(
        identity,
        ((CallLogEntry) list.getAdapter().getItem(list.getFirstVisiblePosition())).identity());
    assertEquals(top, list.getChildAt(0).getTop());
    c.pause().stop().destroy();
  }

  @Test
  public void changingFilterResetsScrollToItsNewestRecord() throws Exception {
    ActivityController<PhoneActivity> c = history();
    PhoneActivity a = c.get();
    ListView list = a.findViewById(R.id.history_list);
    list.setSelectionFromTop(40, 0);
    settle();
    assertTrue(list.getFirstVisiblePosition() > 0);
    a.findViewById(R.id.btn_history_missed).performClick();
    settle();
    assertEquals(0, list.getFirstVisiblePosition());
    c.pause().stop().destroy();
  }
}
