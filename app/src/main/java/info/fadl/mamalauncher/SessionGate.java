package info.fadl.mamalauncher;

/**
 * The unlocked settings session. The PIN gate stamps it on success; settings
 * re-validates on every resume, so leaving the app (home, recents, screen
 * off) for longer than the timeout silently relocks - a passerby cannot
 * return to an open settings screen. Memory-only on purpose: a process
 * restart is always locked.
 */
public final class SessionGate {

    private static final long TIMEOUT_MS = 90_000;
    private static long unlockedUntilMs;

    private SessionGate() {}

    public static void unlock() {
        unlockedUntilMs = System.currentTimeMillis() + TIMEOUT_MS;
    }

    public static boolean isValid() {
        return System.currentTimeMillis() < unlockedUntilMs;
    }

    public static void lock() {
        unlockedUntilMs = 0;
    }
}
