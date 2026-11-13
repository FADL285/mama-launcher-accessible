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
        assertTrue(hash.startsWith("PBKDF2WithHmacSHA256$"));
        assertEquals(64, hash.substring(hash.indexOf('$')+1).length());
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

    @Test
    public void ordinaryLockoutRunsItsSchedule() {
        long armedWall = 1_000_000, armedElapsed = 5_000, lock = 600_000;
        long until = armedWall + lock;
        assertEquals(lock, PinLock.remainingLockoutMs(until, armedWall, armedElapsed, armedWall, armedElapsed));
        assertEquals(lock - 60_000, PinLock.remainingLockoutMs(until, armedWall, armedElapsed, armedWall + 60_000, armedElapsed + 60_000));
        assertEquals(0, PinLock.remainingLockoutMs(until, armedWall, armedElapsed, until, armedElapsed + lock));
    }

    @Test
    public void backwardClockJumpCannotStretchTheLockout() {
        // 9 minutes elapsed of a 10-minute lockout, then the clock jumps back 8.
        long armedWall = 1_000_000, armedElapsed = 5_000, lock = 600_000;
        long until = armedWall + lock;
        long nowWall = armedWall + 9 * 60_000 - 8 * 60_000;
        long nowElapsed = armedElapsed + 9 * 60_000;
        assertEquals(60_000, PinLock.remainingLockoutMs(until, armedWall, armedElapsed, nowWall, nowElapsed));
        assertEquals(0, PinLock.remainingLockoutMs(until, armedWall, armedElapsed, nowWall, nowElapsed + 60_000));
    }

    @Test
    public void farFutureDeadlineIsClampedToTheCap() {
        long now = 1_000_000;
        assertEquals(PinLock.MAX_LOCK_MS, PinLock.remainingLockoutMs(now + 86_400_000L, 0, 0, now, 0));
        assertEquals(0, PinLock.remainingLockoutMs(0, 0, 0, now, 0));
        assertEquals(0, PinLock.remainingLockoutMs(now - 1, 0, 0, now, 0));
    }

    @Test
    public void rebootDuringLockoutFallsBackToTheWallDeadline() {
        // elapsedRealtime resets at boot, so the arming record is unusable.
        long armedWall = 1_000_000, armedElapsed = 3_600_000, lock = 600_000;
        long until = armedWall + lock;
        assertEquals(lock - 120_000, PinLock.remainingLockoutMs(until, armedWall, armedElapsed, armedWall + 120_000, 5_000));
        assertEquals(0, PinLock.remainingLockoutMs(until, armedWall, armedElapsed, until, 5_000));
    }

    @Test public void legacySha1PinStillVerifiesWhenSha256IsAvailable() {
        String legacy=PinLock.derive("salt", "2468", "PBKDF2WithHmacSHA1");
        assertTrue(PinLock.verify("salt", "2468", legacy));
        assertFalse(PinLock.verify("salt", "2469", legacy));
    }
    @Test public void legacySha256AndTaggedAlgorithmsRemainCompatible() {
        for (String algorithm : new String[]{"PBKDF2WithHmacSHA1", "PBKDF2WithHmacSHA256"}) {
            String digest=PinLock.derive("salt", "2468", algorithm);
            assertTrue(PinLock.verify("salt", "2468", digest));
            assertTrue(PinLock.verify("salt", "2468", algorithm+"$"+digest));
            assertFalse(PinLock.verify("salt", "2468", "unknown$"+digest));
        }
    }
}
