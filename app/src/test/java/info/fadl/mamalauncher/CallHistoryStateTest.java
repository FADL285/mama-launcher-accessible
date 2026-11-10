package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.CallLog;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CallHistoryStateTest {
  Context app;

  @Before
  public void prepare() {
    app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("missed_review_v1", 0).edit().clear().commit();
    Prefs.get(app).edit().clear().commit();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .grantPermissions(Manifest.permission.READ_CALL_LOG);
  }

  Set<String> set(String... values) {
    return new HashSet<>(Arrays.asList(values));
  }

  @Test
  public void firstCompleteSnapshotBaselinesOldButNotLaterCalls() {
    assertEquals(0, MissedCallState.reconcile(app, set("1:100:3")));
    assertEquals(1, MissedCallState.reconcile(app, set("1:100:3", "2:100:3")));
  }

  @Test
  public void acknowledgmentIsExactSnapshotNotClockWatermark() {
    MissedCallState.reconcile(app, set());
    MissedCallState.acknowledge(app, set("1:100:3"));
    assertEquals(2, MissedCallState.reconcile(app, set("1:100:3", "2:100:3", "1:200:3")));
  }

  @Test
  public void systemClearingAndDeletionPruneSuppression() {
    MissedCallState.reconcile(app, set("1:100:3"));
    assertEquals(0, MissedCallState.reconcile(app, set()));
    assertEquals(1, MissedCallState.reconcile(app, set("1:100:3")));
  }

  @Test
  public void permissionLossRegrantMakesNewBaseline() {
    MissedCallState.reconcile(app, set("1:100:3"));
    MissedCallState.permissionLost(app);
    assertEquals(0, MissedCallState.reconcile(app, set("1:100:3", "2:100:3")));
  }

  @Test
  public void stateSurvivesDifferentContextAndStoresNoNumber() {
    MissedCallState.reconcile(app, set());
    MissedCallState.acknowledge(app, set("1:100:3"));
    assertEquals(
        1, MissedCallState.reconcile(app.getApplicationContext(), set("1:100:3", "2:100:3")));
    assertEquals(
        set("initialized", "acknowledged"),
        app.getSharedPreferences("missed_review_v1", 0).getAll().keySet());
  }

  @Test
  public void emptyPermissionCallbackDoesNotInventDenial() {
    CallLogAccess.recordResult(app, new String[] {Manifest.permission.READ_CALL_LOG}, new int[] {});
    assertFalse(Prefs.get(app).contains(CallLogAccess.DENIED));
  }

  @Test
  public void completedDenialAndGrantAreHintsOnly() {
    CallLogAccess.recordResult(
        app,
        new String[] {Manifest.permission.READ_CALL_LOG},
        new int[] {PackageManager.PERMISSION_DENIED});
    assertTrue(Prefs.get(app).getBoolean(CallLogAccess.DENIED, false));
    assertTrue(CallLogAccess.granted(app));
    CallLogAccess.recordResult(
        app,
        new String[] {Manifest.permission.READ_CALL_LOG},
        new int[] {PackageManager.PERMISSION_GRANTED});
    assertFalse(Prefs.get(app).getBoolean(CallLogAccess.DENIED, true));
  }

  @Test(expected = SecurityException.class)
  public void actualRevocationRejectsLateStateWrite() {
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .denyPermissions(Manifest.permission.READ_CALL_LOG);
    MissedCallState.reconcile(app, set("1:100:3"));
  }

  @Test
  public void noncallableNumbersNeverReachCallAction() {
    for (String value : new String[] {"", "-1", "unknown", "*123#", "123;456", "tel:123"})
      assertFalse(value, new CallLogEntry(1, 2, 0, 3, 1, value, "").callable());
    assertFalse(
        new CallLogEntry(1, 2, 0, 3, CallLog.Calls.PRESENTATION_RESTRICTED, "01000000000", "")
            .callable());
    assertTrue(new CallLogEntry(1, 2, 0, 3, 1, "+20 100 000 0000", "").callable());
  }

  @Test
  public void allCallTypesHaveSeparateLabels() {
    Set<Integer> labels = new HashSet<>();
    for (int type : new int[] {1, 2, 3, 5, 6, 7}) labels.add(CallHistoryController.typeLabel(type));
    assertEquals(6, labels.size());
  }
}
