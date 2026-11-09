package info.fadl.mamalauncher;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.*;

/** Contact names stay local: never select a network-required voice. ar-EG is
 * preferred only if this engine actually offers it, not claimed as installed. */
final class ArabicVoicePolicy {
 static Voice best(Set<Voice> voices) {
  if(voices==null)return null;
  List<Voice> choices=new ArrayList<>();
  for(Voice v:voices)if("ar".equals(v.getLocale().getLanguage()) && !v.isNetworkConnectionRequired()
    && (v.getFeatures()==null || !v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))choices.add(v);
  choices.sort(Comparator.comparingInt((Voice v)->"EG".equals(v.getLocale().getCountry())?0:1)
      .thenComparing(Comparator.comparingInt(Voice::getQuality).reversed()).thenComparing(Voice::getName));
  return choices.isEmpty()?null:choices.get(0);
 }
 static ArabicSpeechState.State prepare(TextToSpeech tts) {
  int result=tts.setLanguage(new Locale("ar","EG"));
  if(result==TextToSpeech.LANG_NOT_SUPPORTED)result=tts.setLanguage(new Locale("ar"));
  Voice voice=best(tts.getVoices());
  if(voice!=null) {
   int selected=tts.setVoice(voice);
   return selected==TextToSpeech.SUCCESS?ArabicSpeechState.from(TextToSpeech.LANG_AVAILABLE,voice):ArabicSpeechState.State.MISSING;
  }
  Voice current=tts.getVoice();
  if(current!=null && current.isNetworkConnectionRequired())return ArabicSpeechState.State.MISSING;
  // Some engines expose only the active voice while data is being prepared.
  return current==null?(result==TextToSpeech.LANG_NOT_SUPPORTED?ArabicSpeechState.State.UNSUPPORTED:ArabicSpeechState.State.MISSING):ArabicSpeechState.from(result,current);
 }
}
