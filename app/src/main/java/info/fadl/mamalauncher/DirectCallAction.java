package info.fadl.mamalauncher;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;

/** Deliberate green-button action. Never invoked from a permission callback. */
final class DirectCallAction {
    static final int REQUEST = 42;
    interface Feedback { void show(boolean blocked, boolean granted); }
    private final Activity activity;
    private final Feedback feedback;
    private boolean pending;
    DirectCallAction(Activity activity, Feedback feedback) {this.activity=activity;this.feedback=feedback;}
    void onResume() { pending=false; }
    void tap(String number) {
        if(pending || number==null || number.trim().isEmpty()) return;
        pending=true; // one attempt until return or permission result, including time zero
        if(activity.checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED) {
            try { activity.startActivity(DialIntents.call(number)); }
            catch(ActivityNotFoundException e) {
                pending=false;
                android.widget.Toast.makeText(activity,R.string.handoff_unavailable,android.widget.Toast.LENGTH_LONG).show();
            }
            catch(SecurityException e) {
                pending=false;feedback.show(true,false);
            }
        } else {
            boolean denied=activity.getSharedPreferences("pilot",Activity.MODE_PRIVATE).getBoolean("call_denied_once",false);
            if(denied && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
                pending=false;feedback.show(true,false);
            } else activity.requestPermissions(new String[]{Manifest.permission.CALL_PHONE},REQUEST);
        }
    }
    void permissionResult(int code,int[] results) {
        if(code!=REQUEST)return;
        pending=false;
        boolean granted=results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED;
        if(!granted) activity.getSharedPreferences("pilot",Activity.MODE_PRIVATE).edit().putBoolean("call_denied_once",true).apply();
        feedback.show(!granted,granted);
        // Deliberately no call here. A fresh green-button press is mandatory.
    }
}
