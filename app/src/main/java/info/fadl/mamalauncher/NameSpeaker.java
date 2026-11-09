package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import java.util.Locale;

/**
 * TTS wrapper for D1's "name spoken when the confirmation screen opens".
 * Pilot default: device TTS (D2 still open: TTS vs family recordings is
 * confirmed in test T6 with mama). Arabic voice data is REQUIRED: when it is
 * missing we surface that instead of speaking the name in a wrong locale.
 */
public class NameSpeaker implements android.speech.tts.TextToSpeech.OnInitListener {

    /** Called when speech is unavailable (engine init failed or speak failed),
     *  so the screen can say so instead of failing silently. */
    public interface AvailabilityListener { void onUnavailable(); }

    private android.speech.tts.TextToSpeech tts;
    private boolean ready;
    private String pending;
    private AvailabilityListener availabilityListener;
    private final android.os.Handler main =
            new android.os.Handler(android.os.Looper.getMainLooper());

    public void setAvailabilityListener(AvailabilityListener l) {
        this.availabilityListener = l;
    }

    /** Auto-speak only when the engine really has Arabic: a default-locale
     *  fallback would pronounce Arabic names wrongly and call it success.
     *  Pure so tests pin it (values mirror TextToSpeech.LANG_*). */
    public static boolean shouldAutoSpeak(int langAvailability) {
        return langAvailability >= android.speech.tts.TextToSpeech.LANG_AVAILABLE;
    }

    /** What actually gets spoken; pure so tests can pin it. */
    public static String speakText(String name, String fallback) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? fallback : n;
    }

    public void init(android.content.Context context) {
        tts = new android.speech.tts.TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public void onInit(int status) {
        ready = status == android.speech.tts.TextToSpeech.SUCCESS;
        if (!ready) {
            if (availabilityListener != null) availabilityListener.onUnavailable();
            return;
        }
        Locale ar = new Locale("ar");
        int avail = tts.isLanguageAvailable(ar);
        if (!shouldAutoSpeak(avail)) {
            // Arabic missing on this engine: say so instead of pronouncing
            // the name wrongly in a default locale. The settings screen
            // already offers the official Arabic data installer.
            if (availabilityListener != null) availabilityListener.onUnavailable();
            pending = null;
            return;
        }
        tts.setLanguage(ar);
        // Queued success is not proof of sound: synthesis can still fail
        // after speak() returns. Surface that so the screen can say so.
        tts.setOnUtteranceProgressListener(
                new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { }
                    @Override public void onDone(String utteranceId) { }
                    @Override public void onError(String utteranceId) {
                        main.post(() -> {
                            if (availabilityListener != null) {
                                availabilityListener.onUnavailable();
                            }
                        });
                    }
                });
        if (pending != null) {
            speak(pending);
            pending = null;
        }
    }

    public void speak(String name) {
        if (!ready) { pending = name; return; }
        int r = tts.speak(name, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "mama-name");
        if (r == android.speech.tts.TextToSpeech.ERROR && availabilityListener != null) {
            availabilityListener.onUnavailable();
        }
    }

    public void shutdown() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
