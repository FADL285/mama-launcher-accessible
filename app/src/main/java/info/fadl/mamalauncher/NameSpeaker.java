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
    public enum State { INITIALIZING, READY, RETRYABLE, PREPARING, NEEDS_SETUP }
    public interface AvailabilityListener {
        void onUnavailable(); default void onAvailable(){}
        default void onStateChanged(State state){}
    }
    private State state=State.INITIALIZING;
    public State state(){return state;}
    private void state(State next){
        state=next;
        if(availabilityListener==null)return;
        availabilityListener.onStateChanged(next);
        if(next==State.READY)availabilityListener.onAvailable();
        else if(next!=State.INITIALIZING)availabilityListener.onUnavailable();
    }

    private android.speech.tts.TextToSpeech tts;
    private boolean ready;
    private boolean closed;
    private boolean initializationFinished;
    private String pending;
    private android.content.Context context;
    private int generation;
    private java.util.List<String> engines;
    private int engineIndex;
    private String currentEngine;
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
        this.context=context.getApplicationContext();
        closed=false;ready=false;initializationFinished=false;generation++;state(State.INITIALIZING);
        engines=new java.util.ArrayList<>();engineIndex=0;
        String requested=SpeechEngineChoice.requested(context);
        java.util.Map<String,String> installed=SpeechEngineChoice.installed(context);
        boolean explicit=Prefs.get(context).contains(SpeechEngineChoice.KEY);
        if(explicit && !installed.containsKey(requested)) {
            initializationFinished=true;
            state(State.NEEDS_SETUP);return;
        }
        if(requested!=null || installed.isEmpty())engines.add(requested);
        // A manual selection is binding. Only an unset choice may try another
        // installed engine, and no contact text is passed until offline Arabic exists.
        if(!explicit)for(String pkg:installed.keySet())if(!engines.contains(pkg))engines.add(pkg);
        startEngine();
    }
    private void startEngine() {
        if(tts!=null){tts.stop();tts.shutdown();}
        final int current=++generation;
        currentEngine=engines.get(engineIndex);
        tts=new android.speech.tts.TextToSpeech(context,status->main.post(()-> {
            if(!closed && current==generation)onInit(status);
        }),currentEngine);
        main.postDelayed(()-> {if(!closed && current==generation && !initializationFinished)onInit(android.speech.tts.TextToSpeech.ERROR);},6000);
    }
    private boolean nextEngine() {
        if(engineIndex+1>=engines.size())return false;
        engineIndex++;initializationFinished=false;startEngine();return true;
    }

    @Override
    public void onInit(int status) {
        if(closed)return;
        initializationFinished=true;
        ready = status == android.speech.tts.TextToSpeech.SUCCESS;
        if (!ready) {
            if(nextEngine())return;
            state(State.RETRYABLE);
            return;
        }
        ArabicSpeechState.State arabic=ArabicVoicePolicy.prepare(tts);
        if (arabic!=ArabicSpeechState.State.READY) {
            // Arabic missing on this engine: say so instead of pronouncing
            // the name wrongly in a default locale. The settings screen
            // already offers the official Arabic data installer. NOT ready:
            // a later repeat-name tap must not reach the default voice either.
            ready = false;
            if(nextEngine())return;
            state(arabic==ArabicSpeechState.State.PREPARING?State.PREPARING:State.NEEDS_SETUP);
            pending = null;
            return;
        }
        if(currentEngine!=null && !Prefs.get(context).contains(SpeechEngineChoice.KEY))
            Prefs.get(context).edit().putString(SpeechEngineChoice.AUTO_KEY,currentEngine).apply();
        state(State.READY);
        // Queued success is not proof of sound: synthesis can still fail
        // after speak() returns. Surface that so the screen can say so.
        final int speechGeneration=generation;
        tts.setOnUtteranceProgressListener(
                new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { }
                    @Override public void onDone(String utteranceId) { }
                    @Override public void onError(String utteranceId) { failed(State.RETRYABLE); }
                    @Override public void onError(String utteranceId,int code){failed(code==android.speech.tts.TextToSpeech.ERROR_NOT_INSTALLED_YET?State.PREPARING:State.RETRYABLE);}
                    private void failed(State failure) {
                        main.post(() -> {
                            if (!closed && generation==speechGeneration) {
                                ready=false;
                                state(failure);
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
        if(closed)return;
        if (!ready) {
            pending=name;
            // Retry on a deliberate Listen tap after failed initialization/data
            // install. Queue during first init, without creating parallel engines.
            if(context!=null && initializationFinished) {
                if(tts!=null)tts.shutdown();initializationFinished=false;init(context);
            }
            return;
        }
        int r = tts.speak(name, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "mama-name");
        if (r == android.speech.tts.TextToSpeech.ERROR) {
            ready=false;
            state(State.RETRYABLE);
        }
    }

    public void shutdown() {
        closed=true;generation++;ready=false;pending=null;
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
