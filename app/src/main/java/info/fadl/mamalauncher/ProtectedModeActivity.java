package info.fadl.mamalauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;

/** Screen off ends protected mode, including a gate or credential recovery.
 * Navigation between settings sections does not leave this Activity. */
abstract class ProtectedModeActivity extends BaseActivity {
    private boolean closedForScreenOff;
    private final BroadcastReceiver screenOff=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) endForScreenOff();
        }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
    protected final boolean protectedModeEnded() { return closedForScreenOff; }
    protected void clearProtectedInput() { }
    private void endForScreenOff() {
        if(closedForScreenOff)return;
        closedForScreenOff=true;
        SessionGate.lock(); // screen off overrides even a sanctioned external leave
        clearProtectedInput();
        startActivity(new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    @Override protected void onStop() {
        super.onStop();
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        if(!isFinishing() && power!=null && !power.isInteractive())endForScreenOff();
    }
    @Override protected void onDestroy() {
        unregisterReceiver(screenOff);
        super.onDestroy();
    }
}
