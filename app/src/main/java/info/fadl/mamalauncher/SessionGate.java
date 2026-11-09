package info.fadl.mamalauncher;

/**
 * The unlocked settings session. There is NO time window: the session is
 * valid only while the settings screen is in the foreground. Going back to
 * the everyday screen, pressing home, opening recents, or turning the
 * screen off locks it IMMEDIATELY (SettingsActivity.onStop), and a process
 * restart is always locked (memory-only on purpose). The single exception:
 * a sanctioned jump to a system screen the setter asked for (the app-info
 * deep link, the TTS data installer) survives exactly that leave-and-return,
 * once.
 */
public final class SessionGate {

    private static boolean unlocked;
    private static boolean externalInFlight;

    private SessionGate() {}

    public static void unlock() {
        unlocked = true;
        externalInFlight = false;
    }

    public static void lock() {
        unlocked = false;
        externalInFlight = false;
    }

    public static boolean isValid() {
        return unlocked;
    }

    /** Called just before launching a sanctioned system screen. */
    public static void expectExternal() {
        externalInFlight = true;
    }

    /** True while a sanctioned external screen covers settings. */
    public static boolean externalInFlight() {
        return externalInFlight;
    }

    /** Cleared on settings resume so the NEXT leave locks again. */
    public static void clearExternal() {
        externalInFlight = false;
    }
}
