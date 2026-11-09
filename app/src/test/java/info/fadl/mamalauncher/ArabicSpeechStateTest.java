package info.fadl.mamalauncher;
import android.speech.tts.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class ArabicSpeechStateTest {
 @Test public void supportedEngineWithPendingVoiceIsNotReady(){
  Voice voice=new Voice("fixture",new Locale("ar"),300,300,false,Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
  assertEquals(ArabicSpeechState.State.PREPARING,ArabicSpeechState.from(TextToSpeech.LANG_AVAILABLE,voice));
 }
 @Test public void unsupportedDiffersFromMissingData(){
  assertEquals(ArabicSpeechState.State.UNSUPPORTED,ArabicSpeechState.from(TextToSpeech.LANG_NOT_SUPPORTED,null));
  assertEquals(ArabicSpeechState.State.MISSING,ArabicSpeechState.from(TextToSpeech.LANG_MISSING_DATA,null));
 }
 @Test public void wrongLanguageFallbackCannotBecomeArabicSuccess(){
  Voice voice=new Voice("fixture",Locale.ENGLISH,300,300,false,Collections.emptySet());
  assertEquals(ArabicSpeechState.State.MISSING,ArabicSpeechState.from(TextToSpeech.LANG_AVAILABLE,voice));
 }
}
