package info.fadl.mamalauncher;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Settings-mode PIN: salted SHA-256 storage (never plaintext) and pure
 *  attempt-limiting logic, pinned by PinLockTest. The PIN protects the
 *  configuration mode from accidental changes; it is not account security. */
public final class PinLock {

    public static final int MAX_FAILURES_BEFORE_LOCK = 5;
    public static final long BASE_LOCK_MS = 30_000;
    public static final long MAX_LOCK_MS = 600_000;

    private PinLock() {}

    public static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + ":" + pin).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verify(String salt, String pin, String expectedHash) {
        return expectedHash != null && hash(salt, pin).equals(expectedHash);
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
}
