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
            // A live grant clears any stale earlier denial before the blocked check.
            activity.getSharedPreferences("pilot",Activity.MODE_PRIVATE).edit().putBoolean("call_denied_once",false).apply();
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
    void permissionResult(int code,String[] permissions,int[] results) {
        if(code!=REQUEST)return;
        pending=false;
        boolean expected=permissions!=null;
        if(expected) {
            expected=false;
            for(String p:permissions) if(Manifest.permission.CALL_PHONE.equals(p)) {expected=true;break;}
        }
        // An interrupted sheet delivers empty or mismatched arrays: persist nothing,
        // show nothing, and wait for a fresh green-button press to ask again.
        if(!expected || results.length!=permissions.length) return;
        boolean granted=false;
        for(int i=0;i<permissions.length;i++) if(Manifest.permission.CALL_PHONE.equals(permissions[i]) && results[i]==PackageManager.PERMISSION_GRANTED) granted=true;
        android.content.SharedPreferences.Editor e=activity.getSharedPreferences("pilot",Activity.MODE_PRIVATE).edit();
        e.putBoolean("call_denied_once",!granted).apply();
        feedback.show(!granted,granted);
        // Deliberately no call here. A fresh green-button press is mandatory.
    }
}
