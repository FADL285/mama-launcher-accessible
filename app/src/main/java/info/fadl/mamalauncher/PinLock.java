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

    /** Milliseconds left on a lockout. The wall-clock deadline is bounded by
     *  the monotonic arming record, so a backward clock change cannot stretch
     *  a lockout past its schedule, and anything beyond MAX_LOCK_MS is treated
     *  as corrupt legacy state. Without arming data (legacy installs, or after
     *  a reboot resets elapsedRealtime) the wall deadline alone applies. */
    public static long remainingLockoutMs(long until, long armedWall, long armedElapsed,
                                          long nowWall, long nowElapsed) {
        if (until <= 0) return 0;
        long remWall = until - nowWall;
        if (remWall <= 0) return 0;
        if (remWall > MAX_LOCK_MS) remWall = MAX_LOCK_MS;
        long remMono = remWall;
        if (armedWall > 0 && armedElapsed > 0 && nowElapsed >= armedElapsed) {
            remMono = (until - armedWall) - (nowElapsed - armedElapsed);
        }
        return Math.max(0, Math.min(remWall, remMono));
    }
}
