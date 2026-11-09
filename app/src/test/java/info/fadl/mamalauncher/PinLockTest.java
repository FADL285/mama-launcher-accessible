package info.fadl.mamalauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinLockTest {

    @Test
    public void verifyAcceptsOnlyTheRightPinForTheSameSalt() {
        String hash = PinLock.hash("salt-1", "1234");
        assertTrue(PinLock.verify("salt-1", "1234", hash));
        assertFalse(PinLock.verify("salt-1", "1235", hash));
        assertFalse(PinLock.verify("salt-2", "1234", hash)); // salt is part of the secret
        assertFalse(PinLock.verify("salt-1", "1234", null));
    }

    @Test
    public void storedValueIsNeverThePlainPin() {
        String hash = PinLock.hash("salt", "1234");
        assertNotEquals("1234", hash);
        assertEquals(64, hash.length()); // SHA-256 hex
    }

    @Test
    public void noLockoutBelowTheFailureThreshold() {
        assertEquals(0, PinLock.lockoutMillis(0));
        assertEquals(0, PinLock.lockoutMillis(4));
    }

    @Test
    public void lockoutDoublesPerFailureBlockAndCaps() {
        assertEquals(30_000, PinLock.lockoutMillis(5));
        assertEquals(30_000, PinLock.lockoutMillis(9));
        assertEquals(60_000, PinLock.lockoutMillis(10));
        assertEquals(120_000, PinLock.lockoutMillis(15));
        assertEquals(PinLock.MAX_LOCK_MS, PinLock.lockoutMillis(100));
    }

    @Test
    public void isLockedOnlyWhileTheDeadlineIsInTheFuture() {
        assertTrue(PinLock.isLocked(10_000, 9_999));
        assertFalse(PinLock.isLocked(10_000, 10_000));
        assertFalse(PinLock.isLocked(0, 5_000));
    }
}
