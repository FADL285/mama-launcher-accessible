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
}
