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

    public static String hash(String salt, String pin) {
        SecretKeyFactory skf;
        try {
            skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256"); // API 26+
        } catch (NoSuchAlgorithmException e) {
            try {
                skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1"); // API 10+
            } catch (NoSuchAlgorithmException e2) {
                throw new IllegalStateException(e2);
            }
        }
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(),
                salt.getBytes(java.nio.charset.StandardCharsets.UTF_8), ITERATIONS, 256);
        try {
            byte[] d = skf.generateSecret(spec).getEncoded();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (InvalidKeySpecException e) {
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
