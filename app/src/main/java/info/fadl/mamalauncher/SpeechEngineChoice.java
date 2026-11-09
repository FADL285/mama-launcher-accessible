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
        return Settings.Secure.getString(context.getContentResolver(),Settings.Secure.TTS_DEFAULT_SYNTH);
    }
    static Intent installIntent(String engine) {
        return new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(engine);
    }
}
