package info.fadl.mamalauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import org.robolectric.RuntimeEnvironment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PinLockTest {

    private SharedPreferences prefs;

    @Before
    public void freshPrefs() {
        Context app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("pinlock_test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    private void arm(long until, long armedWall, long armedElapsed) {
        prefs.edit().putLong(Prefs.PIN_LOCKOUT_UNTIL, until)
                .putLong(Prefs.PIN_LOCKOUT_ARMED_WALL, armedWall)
                .putLong(Prefs.PIN_LOCKOUT_ARMED_ELAPSED, armedElapsed).commit();
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
        arm(armedWall + lock, armedWall, armedElapsed);
        assertEquals(lock, PinLock.remainingLockoutMs(prefs, armedWall, armedElapsed));
        assertEquals(lock - 60_000, PinLock.remainingLockoutMs(prefs, armedWall + 60_000, armedElapsed + 60_000));
        assertEquals(0, PinLock.remainingLockoutMs(prefs, armedWall + lock, armedElapsed + lock));
    }

    @Test
    public void backwardClockJumpCannotStretchTheLockout() {
        // 9 minutes elapsed of a 10-minute lockout, then the clock jumps back 8.
        long armedWall = 1_000_000, armedElapsed = 5_000, lock = 600_000;
        arm(armedWall + lock, armedWall, armedElapsed);
        long nowWall = armedWall + 9 * 60_000 - 8 * 60_000;
        long nowElapsed = armedElapsed + 9 * 60_000;
        assertEquals(60_000, PinLock.remainingLockoutMs(prefs, nowWall, nowElapsed));
        assertEquals(0, PinLock.remainingLockoutMs(prefs, nowWall, nowElapsed + 60_000));
    }

    @Test
    public void unanchoredFarFutureDeadlineReArmsOnceAndCountsDown() {
        // Legacy/corrupt state: a deadline a day out with no arming record.
        long now = 1_000_000;
        arm(now + 86_400_000L, 0, 0);
        // First read re-arms a bounded countdown and persists it...
        assertEquals(PinLock.MAX_LOCK_MS, PinLock.remainingLockoutMs(prefs, now, 5_000));
        // ...so repeated reads count down instead of clamping to the cap forever.
        assertEquals(PinLock.MAX_LOCK_MS - 60_000, PinLock.remainingLockoutMs(prefs, now + 60_000, 65_000));
        assertEquals(60_000, PinLock.remainingLockoutMs(prefs, now + 9 * 60_000, 5_000 + 9 * 60_000));
        assertEquals(0, PinLock.remainingLockoutMs(prefs, now + PinLock.MAX_LOCK_MS, 5_000 + PinLock.MAX_LOCK_MS));
    }

    @Test
    public void rebootPlusDayLongClockRollbackStillUnlocksOnSchedule() {
        // Armed before a reboot; elapsedRealtime resets and the wall clock was
        // set back a day, so the stored deadline looks a day away.
        long armedWall = 1_700_000_000_000L, armedElapsed = 3_600_000, lock = 600_000;
        long until = armedWall + lock;
        arm(until, armedWall, armedElapsed);
        long nowWall = until - 86_400_000L; // clock rolled back a day
        assertEquals(PinLock.MAX_LOCK_MS, PinLock.remainingLockoutMs(prefs, nowWall, 5_000));
        assertEquals(PinLock.MAX_LOCK_MS - 60_000, PinLock.remainingLockoutMs(prefs, nowWall + 60_000, 65_000));
        assertEquals(0, PinLock.remainingLockoutMs(prefs, nowWall + PinLock.MAX_LOCK_MS, 5_000 + PinLock.MAX_LOCK_MS));
    }

    @Test
    public void rebootWithShortRemainingKeepsTheWallDeadline() {
        // Within the cap there is nothing to repair; the wall deadline applies.
        long armedWall = 1_700_000_000_000L, armedElapsed = 3_600_000, lock = 600_000;
        long until = armedWall + lock;
        arm(until, armedWall, armedElapsed);
        assertEquals(lock - 120_000, PinLock.remainingLockoutMs(prefs, armedWall + 120_000, 5_000));
        assertEquals(0, PinLock.remainingLockoutMs(prefs, until, 5_000));
    }

    @Test
    public void monotonicExpiryClearsTheRecordSoARebootCannotReArmIt() {
        // Backward clock change: the wall deadline is still ahead when the
        // monotonic schedule runs out.
        long armedWall = 1_700_000_000_000L, armedElapsed = 3_600_000, lock = 600_000;
        arm(armedWall + lock, armedWall, armedElapsed);
        long nowWall = armedWall - 3_600_000L; // wall rolled back an hour
        assertEquals(0, PinLock.remainingLockoutMs(prefs, nowWall, armedElapsed + lock));
        // The record is cleared...
        assertFalse(prefs.contains(Prefs.PIN_LOCKOUT_UNTIL));
        assertFalse(prefs.contains(Prefs.PIN_LOCKOUT_ARMED_WALL));
        assertFalse(prefs.contains(Prefs.PIN_LOCKOUT_ARMED_ELAPSED));
        // ...so after a reboot (elapsedRealtime below the anchors) the spent
        // lockout is not re-armed by the unanchored fallback.
        assertEquals(0, PinLock.remainingLockoutMs(prefs, nowWall + 3_600_000L, 1_000));
    }

    @Test
    public void noDeadlineOrExpiredDeadlineIsUnlocked() {
        arm(0, 0, 0);
        assertEquals(0, PinLock.remainingLockoutMs(prefs, 1_000_000, 5_000));
        arm(999_999, 0, 0);
        assertEquals(0, PinLock.remainingLockoutMs(prefs, 1_000_000, 5_000));
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
