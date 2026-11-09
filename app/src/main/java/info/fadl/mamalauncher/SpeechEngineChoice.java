package info.fadl.mamalauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import java.util.LinkedHashMap;
import java.util.Map;

/** One engine preference for settings tests, the installer and everyday speech.
 * Uses installed service metadata, not hard-coded vendor package names. */
final class SpeechEngineChoice {
    static final String KEY="speech_engine";
    static final String AUTO_KEY="speech_engine_auto";
    static Map<String,String> installed(Context context) {
        Map<String,String> result=new LinkedHashMap<>();
        for(ResolveInfo r:context.getPackageManager().queryIntentServices(
                new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),0)) {
            if(r.serviceInfo!=null && r.serviceInfo.enabled && r.serviceInfo.applicationInfo.enabled)
                result.put(r.serviceInfo.packageName,r.loadLabel(context.getPackageManager()).toString());
        }
        return result;
    }
    static String requested(Context context) {
        String saved=Prefs.get(context).getString(KEY,null);
        if(saved!=null)return saved;
        String automatic=Prefs.get(context).getString(AUTO_KEY,null);
        if(automatic!=null && installed(context).containsKey(automatic))return automatic;
        String system=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.TTS_DEFAULT_SYNTH);
        Map<String,String> engines=installed(context);
        if(system!=null && engines.containsKey(system))return system;
        // First-use Android setups can have an installed engine but no default.
        // A single installed engine is unambiguous; several still use Android's default.
        return engines.size()==1?engines.keySet().iterator().next():null;
    }
    static Intent installIntent(String engine) {
        return new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(engine);
    }
}
