package info.fadl.mamalauncher;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Settings-mode PIN: PBKDF2 (slow KDF, 12000 iterations, 256-bit) over a
 *  random 128-bit salt - never plaintext, and offline brute-force of the
 *  4-digit space is deliberately expensive. Online guessing is limited by the
 *  persisted lockout schedule below. The PIN protects the configuration mode
 *  from accidental or curious changes; it is not account security. */
public final class PinLock {

    public static final int MAX_FAILURES_BEFORE_LOCK = 5;
    public static final long BASE_LOCK_MS = 30_000;
    public static final long MAX_LOCK_MS = 600_000;
    static final int ITERATIONS = 12_000;

    private PinLock() {}

    private static final String SHA256 = "PBKDF2WithHmacSHA256";
    private static final String SHA1 = "PBKDF2WithHmacSHA1";

    public static String hash(String salt, String pin) {
        String algorithm;
        try {
            SecretKeyFactory.getInstance(SHA256);
            algorithm = SHA256;
        } catch (NoSuchAlgorithmException e) {
            algorithm = SHA1;
        }
        return algorithm + "$" + derive(salt, pin, algorithm);
    }

    static String derive(String salt, String pin, String algorithm) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(),
                salt.getBytes(java.nio.charset.StandardCharsets.UTF_8), ITERATIONS, 256);
        try {
            byte[] d = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean matches(String salt, String pin, String hash, String algorithm) {
        try {
            return java.security.MessageDigest.isEqual(
                    derive(salt, pin, algorithm).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    hash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IllegalStateException e) {
            return false; // unavailable provider: never unlock or crash
        }
    }

    public static boolean verify(String salt, String pin, String expectedHash) {
        if (expectedHash == null) return false;
        int separator = expectedHash.indexOf('$');
        if (separator >= 0) {
            String algorithm = expectedHash.substring(0, separator);
            if (!SHA256.equals(algorithm) && !SHA1.equals(algorithm)) return false;
            return matches(salt, pin, expectedHash.substring(separator + 1), algorithm);
        }
        // Existing pilot hashes did not store an algorithm. Try both supported
        // legacy algorithms so an OS/provider upgrade does not lock out a PIN.
        return matches(salt, pin, expectedHash, SHA256)
                || matches(salt, pin, expectedHash, SHA1);
    }

    /** Lockout after a failure: 0ms while under the threshold, then 30s
     *  doubling per further threshold block, capped at 10 minutes. */
    public static long lockoutMillis(int totalFailures) {
        if (totalFailures < MAX_FAILURES_BEFORE_LOCK) return 0;
        int blocks = totalFailures / MAX_FAILURES_BEFORE_LOCK; // 5..9 -> 1, 10..14 -> 2, ...
        long ms = BASE_LOCK_MS;
        for (int i = 1; i < blocks && ms < MAX_LOCK_MS; i++) ms *= 2;
        return Math.min(ms, MAX_LOCK_MS);
    }

    /** True when a stored lockout deadline is still in the future. */
    public static boolean isLocked(long lockoutUntilMs, long nowMs) {
        return lockoutUntilMs > nowMs;
    }

    private static void clearRecord(android.content.SharedPreferences p) {
        p.edit().remove(Prefs.PIN_LOCKOUT_UNTIL)
                .remove(Prefs.PIN_LOCKOUT_ARMED_WALL)
                .remove(Prefs.PIN_LOCKOUT_ARMED_ELAPSED).apply();
    }

    /** Milliseconds left on the stored lockout, repairing untrustworthy state.
     *  The wall-clock deadline is bounded by the monotonic arming record, so a
     *  backward clock change cannot stretch a lockout past its schedule.
     *  Without a usable arming record (a legacy install upgraded mid-lockout,
     *  or a reboot that reset elapsedRealtime) a deadline beyond the cap is
     *  corrupt: it is re-armed ONCE to now+MAX_LOCK_MS and persisted with fresh
     *  anchors, so the lockout counts down instead of clamping every read to
     *  the cap and waiting out the far-future wall time. A stored deadline
     *  within the cap needs no repair and simply counts down on the wall
     *  clock. Persistence is best-effort: a failed write still returns the
     *  bounded value, and the next read re-arms. */
    public static long remainingLockoutMs(android.content.SharedPreferences p,
                                          long nowWall, long nowElapsed) {
        long until = p.getLong(Prefs.PIN_LOCKOUT_UNTIL, 0);
        if (until <= 0) return 0;
        long remWall = until - nowWall;
        if (remWall <= 0) {
            // Ordinary wall expiry: the record is spent, so retire it here too.
            // A reboot plus a wall-clock reset into the past (a drained battery
            // resetting the RTC) would otherwise make the stale deadline future
            // again and let the unanchored fallback re-arm a fresh lockout for
            // someone who has not mistyped.
            clearRecord(p);
            return 0;
        }
        long armedWall = p.getLong(Prefs.PIN_LOCKOUT_ARMED_WALL, 0);
        long armedElapsed = p.getLong(Prefs.PIN_LOCKOUT_ARMED_ELAPSED, 0);
        if (armedWall > 0 && armedElapsed > 0 && nowElapsed >= armedElapsed) {
            long remMono = (until - armedWall) - (nowElapsed - armedElapsed);
            if (remMono <= 0) {
                // The monotonic schedule is spent even though the wall deadline
                // is still ahead (a backward clock change). Clear the record:
                // after a reboot, elapsedRealtime drops below the anchors and
                // the unanchored fallback would otherwise re-arm a spent lockout.
                clearRecord(p);
                return 0;
            }
            return Math.min(Math.min(remWall, MAX_LOCK_MS), remMono);
        }
        if (remWall > MAX_LOCK_MS) {
            p.edit().putLong(Prefs.PIN_LOCKOUT_UNTIL, nowWall + MAX_LOCK_MS)
                    .putLong(Prefs.PIN_LOCKOUT_ARMED_WALL, nowWall)
                    .putLong(Prefs.PIN_LOCKOUT_ARMED_ELAPSED, nowElapsed).apply();
            return MAX_LOCK_MS;
        }
        return remWall;
    }
}
