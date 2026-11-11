package info.fadl.mamalauncher;

import static org.junit.Assert.*;
import info.fadl.mamalauncher.ui.CallDirectionView.Direction;
import org.junit.Test;

public class CallDirectionMappingTest {
  @Test public void eachSystemTypeKeepsItsMeaningAndUnknownIsNotIncoming() {
    assertEquals(Direction.INCOMING, CallHistoryController.typeDirection(1));
    assertEquals(Direction.OUTGOING, CallHistoryController.typeDirection(2));
    assertEquals(Direction.MISSED, CallHistoryController.typeDirection(3));
    assertEquals(Direction.REJECTED, CallHistoryController.typeDirection(5));
    assertEquals(Direction.BLOCKED, CallHistoryController.typeDirection(6));
    assertEquals(Direction.UNKNOWN, CallHistoryController.typeDirection(4));
    assertEquals(Direction.UNKNOWN, CallHistoryController.typeDirection(7));
    assertEquals(Direction.UNKNOWN, CallHistoryController.typeDirection(999));
  }
}
