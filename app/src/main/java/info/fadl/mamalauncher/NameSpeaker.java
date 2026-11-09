package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import java.util.Locale;

/**
 * TTS wrapper for D1's "name spoken when the confirmation screen opens".
 * Pilot default: device TTS (D2 still open: TTS vs family recordings is
 * confirmed in test T6 with mama). If Arabic is not available on the device
 * engine we still speak in the default locale and T6 will judge it.
 */
public class NameSpeaker implements android.speech.tts.TextToSpeech.OnInitListener {

    private android.speech.tts.TextToSpeech tts;
    private boolean ready;
    private String pending;

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
        if (!ready) return;
        Locale ar = new Locale("ar");
        int avail = tts.isLanguageAvailable(ar);
        if (avail >= android.speech.tts.TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(ar);
        }
        if (pending != null) {
            speak(pending);
            pending = null;
        }
    }

    public void speak(String name) {
        if (!ready) { pending = name; return; }
        tts.speak(name, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "mama-name");
    }

    public void shutdown() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
