package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameSpeakerTest {

    @Test
    public void speaksTheNameWhenPresent() {
        assertEquals("ماما", NameSpeaker.speakText(" ماما ", "بدون اسم"));
    }

    @Test
    public void fallsBackWhenNameMissing() {
        assertEquals("بدون اسم", NameSpeaker.speakText(null, "بدون اسم"));
        assertEquals("بدون اسم", NameSpeaker.speakText("   ", "بدون اسم"));
    }

    @Test
    public void autoSpeakRequiresRealArabicSupport() {
        // TextToSpeech constants: LANG_AVAILABLE=0, LANG_COUNTRY_AVAILABLE=1,
        // LANG_COUNTRY_VAR_AVAILABLE=2, LANG_MISSING_DATA=-1, LANG_NOT_SUPPORTED=-2
        assertTrue(NameSpeaker.shouldAutoSpeak(0));
        assertTrue(NameSpeaker.shouldAutoSpeak(1));
        assertTrue(NameSpeaker.shouldAutoSpeak(2));
        assertFalse(NameSpeaker.shouldAutoSpeak(-1)); // missing Arabic data: surface, never fake it
        assertFalse(NameSpeaker.shouldAutoSpeak(-2));
    }
}
