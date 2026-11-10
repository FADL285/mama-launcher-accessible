package info.fadl.mamalauncher;

import static org.junit.Assert.*;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.CallLog;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CallLogRepositoryTest {
  Context app;
  Fixture provider;

  static class Fixture extends ContentProvider {
    boolean fail, empty, malformed;
    int count = 201, queries, writes;
    String lastSort, lastSelection;

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(Uri uri, String[] columns, String selection, String[] args, String order) {
      queries++;
      lastSort = order;
      lastSelection = selection;
      if (fail) return null;
      MatrixCursor c = new MatrixCursor(columns);
      if (empty) return c;
      for (int i = 0; i < count; i++) {
        Object[] row = new Object[columns.length];
        for (int j = 0; j < columns.length; j++)
          switch (columns[j]) {
            case "_id":
              row[j] = i + 1;
              break;
            case "date":
              row[j] = 1000L;
              break;
            case "type":
              row[j] = 3;
              break;
            case "new":
              row[j] = malformed ? 2 : 1;
              break;
            case "is_read":
              row[j] = null;
              break;
            case "number":
              row[j] = "01000000000";
              break;
            case "presentation":
              row[j] = 1;
              break;
            default:
              row[j] = 0;
          }
        c.addRow(row);
      }
      return c;
    }

    @Override
    public String getType(Uri u) {
      return null;
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
      writes++;
      throw new AssertionError("write");
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
      writes++;
      throw new AssertionError("write");
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
      writes++;
      throw new AssertionError("write");
    }
  }

  @Before
  public void setup() {
    app = RuntimeEnvironment.getApplication();
    provider = new Fixture();
    org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
        CallLog.AUTHORITY, provider);
  }

  @Test
  public void completePendingIsIndependentFrom200DisplayBatch() {
    assertEquals(
        201, CallLogRepository.pending(app.getContentResolver(), new CancellationSignal()).size());
    assertTrue(provider.lastSelection.contains("is_read IS NOT 1"));
    CallLogRepository.Result r =
        CallLogRepository.load(app.getContentResolver(), false, 200, new CancellationSignal());
    assertEquals(200, r.entries.size());
    assertTrue(r.more);
    assertFalse(provider.lastSort.contains("LIMIT"));
    assertEquals(0, provider.writes);
  }

  @Test
  public void explicitNextPageShowsAllAndEqualTimestampsStayDistinct() {
    CallLogRepository.Result r =
        CallLogRepository.load(app.getContentResolver(), false, 400, new CancellationSignal());
    assertEquals(201, r.entries.size());
    assertFalse(r.more);
    assertNotEquals(r.entries.get(0).identity(), r.entries.get(1).identity());
  }

  @Test
  public void successfulEmptyIsKnownEmpty() {
    provider.empty = true;
    assertTrue(
        CallLogRepository.pending(app.getContentResolver(), new CancellationSignal()).isEmpty());
  }

  @Test(expected = IllegalStateException.class)
  public void nullCursorIsNotEmpty() {
    provider.fail = true;
    CallLogRepository.pending(app.getContentResolver(), new CancellationSignal());
  }

  @Test(expected = IllegalStateException.class)
  public void malformedFlagsAreUnknown() {
    provider.malformed = true;
    CallLogRepository.pending(app.getContentResolver(), new CancellationSignal());
  }

  @Test(expected = android.os.OperationCanceledException.class)
  public void canceledReadNeverReturnsPartial() {
    CancellationSignal c = new CancellationSignal();
    c.cancel();
    CallLogRepository.pending(app.getContentResolver(), c);
  }

  @Test
  public void missedFilterHasBoundArguments() {
    CallLogRepository.load(app.getContentResolver(), true, 200, new CancellationSignal());
    assertEquals("type=?", provider.lastSelection);
  }
}
