package info.fadl.mamalauncher;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
final class ArabicSpeechState {
 enum State {READY,PREPARING,MISSING,UNSUPPORTED}
 static State from(int languageResult,Voice voice) {
  if(voice!=null && voice.getFeatures()!=null && voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))return State.PREPARING;
  if(languageResult==TextToSpeech.LANG_NOT_SUPPORTED)return State.UNSUPPORTED;
  if(languageResult<0)return State.MISSING;
  if(voice!=null && !"ar".equals(voice.getLocale().getLanguage()))return State.MISSING;
  return State.READY;
 }
}
