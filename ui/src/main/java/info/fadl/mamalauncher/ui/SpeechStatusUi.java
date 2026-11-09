package info.fadl.mamalauncher.ui;
import android.view.View;
import android.widget.TextView;
/** These states describe engine requests, never a claim about what the listener heard. */
public final class SpeechStatusUi {
 private SpeechStatusUi(){}
 public static void show(View root,String state){
  boolean ready="READY".equals(state), loading="INITIALIZING".equals(state);
  root.findViewById(R.id.btn_repeat_name).setVisibility(ready?View.VISIBLE:View.INVISIBLE);
  root.findViewById(R.id.speech_support).setVisibility(ready?View.GONE:View.VISIBLE);
  ((TextView)root.findViewById(R.id.speak_unavailable)).setText(loading?R.string.speech_loading:
   "PREPARING".equals(state)?R.string.speech_preparing:
   "NEEDS_SETUP".equals(state)?R.string.speech_setup_needed:R.string.speech_retryable);
  root.findViewById(R.id.btn_speech_retry).setVisibility(loading?View.GONE:View.VISIBLE);
  root.findViewById(R.id.btn_speech_setup).setVisibility(loading?View.GONE:View.VISIBLE);
  View options=root.findViewById(R.id.btn_speech_options);
  options.setVisibility(ready||loading?View.GONE:View.VISIBLE);
  options.setOnClickListener(v -> {android.widget.ScrollView scroll=root.findViewById(R.id.confirm_scroll);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());});
 }
}
