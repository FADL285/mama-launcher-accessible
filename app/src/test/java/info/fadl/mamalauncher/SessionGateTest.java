package info.fadl.mamalauncher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class SessionGateTest {

    @After
    public void reset() {
        SessionGate.lock();
    }

    @Test
    public void lockedUntilTheGateStampsIt() {
        assertFalse(SessionGate.isValid()); // process start is always locked
        SessionGate.unlock();
        assertTrue(SessionGate.isValid());
    }

    @Test
    public void leavingSettingsLocksImmediatelyUnlessExternalWasExpected() {
        SessionGate.unlock();
        // ordinary leave (back, home, recents, screen off): no external expected
        assertFalse(SessionGate.externalInFlight());
        SessionGate.lock(); // what SettingsActivity.onStop does
        assertFalse(SessionGate.isValid());
    }

    @Test
    public void sanctionedDeepLinkSurvivesExactlyOneLeaveAndReturn() {
        SessionGate.unlock();
        SessionGate.expectExternal(); // launching system app-info / TTS installer
        assertTrue(SessionGate.externalInFlight());
        // onStop with external in flight: no lock
        assertTrue(SessionGate.isValid());
        // return: resume consumes the exception
        SessionGate.clearExternal();
        assertFalse(SessionGate.externalInFlight());
        // the NEXT ordinary leave locks again
        SessionGate.lock();
        assertFalse(SessionGate.isValid());
    }

    @Test
    public void clearExternalCancelsTheExemptionWithoutLocking() {
        SessionGate.unlock();
        SessionGate.expectExternal();
        SessionGate.clearExternal(); // e.g. the sanctioned screen failed to open
        assertTrue(SessionGate.isValid());
        assertFalse(SessionGate.externalInFlight());
    }

    @Test
    public void lockAlsoClearsPendingExternalExpectation() {
        SessionGate.unlock();
        SessionGate.expectExternal();
        SessionGate.lock();
        assertFalse(SessionGate.isValid());
        assertFalse(SessionGate.externalInFlight());
    }
}
