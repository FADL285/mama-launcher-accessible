package info.fadl.mamalauncher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Big dial pad. The green action calls directly after an explicit press and
 * CALL_PHONE permission. Permission grant alone never calls. Save opens the
 * system contact-insert form and never writes contacts silently. */
public class DialpadActivity extends BaseActivity {

    private final StringBuilder number = new StringBuilder();
    private TextView display;
    private View callBtn;
    private View saveBtn;
    private DirectCallAction callAction;
    private java.util.List<info.fadl.mamalauncher.ui.ContactEntry> contacts = new java.util.ArrayList<>();
    private final java.util.concurrent.ExecutorService lookupWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private int lookupGeneration;
    private boolean checkingSave;
    private boolean contactsAvailable;
    private String country() {
        android.telephony.TelephonyManager tm=(android.telephony.TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        String iso=tm==null?"":tm.getNetworkCountryIso();
        if(iso==null || iso.isEmpty())iso=java.util.Locale.getDefault().getCountry();
        return iso.toUpperCase(java.util.Locale.US);
    }
    private boolean canReadContacts() {return checkSelfPermission(android.Manifest.permission.READ_CONTACTS)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    private void refreshContacts() {
        final int generation=++lookupGeneration;
        if(!canReadContacts()){contactsAvailable=false;contacts.clear();renderMatches();return;}
        lookupWorker.execute(()-> {
            java.util.List<info.fadl.mamalauncher.ui.ContactEntry> loaded;
            boolean ok=true;
            try {loaded=ContactsLoader.loadVerified(getContentResolver(),getResources());}catch(RuntimeException e){loaded=new java.util.ArrayList<>();ok=false;}
            final java.util.List<info.fadl.mamalauncher.ui.ContactEntry> result=loaded;final boolean success=ok;
            runOnUiThread(()-> {if(isDestroyed() || generation!=lookupGeneration)return;contacts=result;contactsAvailable=success;renderMatches();});
        });
    }
    private void saveOrShowExisting() {
        if(number.length()<3 || checkingSave)return;
        final String typed=number.toString();checkingSave=true;
        lookupWorker.execute(()-> {
            java.util.List<info.fadl.mamalauncher.ui.ContactEntry> loaded=new java.util.ArrayList<>();boolean ok=canReadContacts();
            try {if(ok)loaded=ContactsLoader.loadVerified(getContentResolver(),getResources());}catch(RuntimeException e){ok=false;}
            final boolean verified=ok;final java.util.List<info.fadl.mamalauncher.ui.ContactEntry> current=loaded;
            runOnUiThread(()-> {
                checkingSave=false;if(isDestroyed() || isFinishing() || !typed.equals(number.toString()))return;
                contacts=current;contactsAvailable=verified;renderMatches();
                if(!verified) {android.widget.Toast.makeText(this,R.string.dial_lookup_needed,android.widget.Toast.LENGTH_LONG).show();return;}
                java.util.List<info.fadl.mamalauncher.ui.ContactEntry> exact=DialContactMatcher.matches(current,typed,country(),true);
                if(exact.size()==1)startActivity(ConfirmCallActivity.intentFor(this,exact.get(0)));
                else if(exact.size()>1)new android.app.AlertDialog.Builder(this).setTitle(R.string.dial_saved_choose)
                    .setItems(exact.stream().map(c->c.name).toArray(String[]::new),(d,i)->startActivity(ConfirmCallActivity.intentFor(this,exact.get(i))))
                    .setNegativeButton(android.R.string.cancel,null).show();
                else try {startActivity(DialIntents.saveAsContact(typed));}catch(android.content.ActivityNotFoundException e){handoffUnavailable();}
            });
        });
    }
    private void renderMatches() {
        if(display==null)return;
        android.widget.LinearLayout box=findViewById(R.id.dial_matches);box.removeAllViews();
        java.util.List<info.fadl.mamalauncher.ui.ContactEntry> exact=DialContactMatcher.matches(contacts,number.toString(),country(),true);
        ((TextView)findViewById(R.id.save_number_label)).setText(exact.isEmpty()?R.string.save_number:R.string.dial_view_saved);
        if(number.length()<3)return;
        if(!contactsAvailable) {TextView hint=new TextView(this);hint.setText(R.string.dial_lookup_needed);hint.setTextSize(16);hint.setTextColor(getResources().getColor(R.color.text_main));box.addView(hint);return;}
        java.util.List<info.fadl.mamalauncher.ui.ContactEntry> found=DialContactMatcher.matches(contacts,number.toString(),country(),false);
        int shown=0;
        for(info.fadl.mamalauncher.ui.ContactEntry c:found) {
            if(shown++==1)break;
            android.widget.Button row=new android.widget.Button(this);
            boolean saved=DialContactMatcher.exact(number.toString(),c.firstNumber(),country());
            row.setText((saved?getString(R.string.dial_saved):getString(R.string.dial_suggestion))+" · "+c.name+"\n"+c.firstNumber());
            row.setTextSize(18);row.setAllCaps(false);row.setMinHeight((int)(64*getResources().getDisplayMetrics().density));
            guard(row,()->startActivity(ConfirmCallActivity.intentFor(this,c)));box.addView(row);
        }
        if(found.size()>1) {
            android.widget.Button more=new android.widget.Button(this);more.setText(getString(R.string.dial_more,found.size()));more.setAllCaps(false);more.setTextSize(18);
            guard(more,()->new android.app.AlertDialog.Builder(this).setTitle(R.string.dial_results)
                .setItems(found.stream().map(c->c.name+" · "+c.firstNumber()).toArray(String[]::new),(d,i)->startActivity(ConfirmCallActivity.intentFor(this,found.get(i))))
                .setNegativeButton(android.R.string.cancel,null).show());box.addView(more);
        }
    }
    @Override protected void onDestroy(){lookupGeneration++;lookupWorker.shutdownNow();super.onDestroy();}


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialpad);
        display = findViewById(R.id.dial_display);
        if (savedInstanceState != null) number.append(savedInstanceState.getString("number", ""));
        callBtn = findViewById(R.id.btn_call);
        saveBtn = findViewById(R.id.btn_save_number);

        guard(findViewById(R.id.btn_back), this::finish);
        guard(findViewById(R.id.btn_backspace), () -> {
            if (number.length() > 0) number.deleteCharAt(number.length() - 1);
            sync();
        });
        wireDigits(findViewById(R.id.dial_grid));

        callAction=new DirectCallAction(this,(blocked,granted) ->
                android.widget.Toast.makeText(this,blocked?R.string.call_blocked_hint:R.string.call_permission_ready,
                        android.widget.Toast.LENGTH_LONG).show());
        guard(callBtn, () -> callAction.tap(number.toString()));
        guard(saveBtn, this::saveOrShowExisting);
        sync();
    }

    @Override protected void onResume() {
        super.onResume(); if(callAction!=null)callAction.onResume();refreshContacts();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        callAction.permissionResult(code,results);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("number", number.toString());
    }

    private void handoffUnavailable() {
        android.widget.Toast.makeText(this, R.string.handoff_unavailable,
                android.widget.Toast.LENGTH_LONG).show();
    }

    private void wireDigits(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getTag() == null && child instanceof ViewGroup) {
                wireDigits((ViewGroup) child);
            } else if (child.getTag() != null) {
                final String digit = child.getTag().toString();
                guard(child, () -> {
                    number.append(digit);
                    sync();
                });
                if ("0".equals(digit)) {
                    child.setOnLongClickListener(v -> {
                        if (number.length() == 0) { number.append('+'); sync(); }
                        return true; // consume: never append a trailing 0 after a hold
                    });
                    if (Prefs.touchGuard(this)) {
                        final long[] down={-1};
                        child.setOnTouchListener((v,event)->{
                            boolean inside=event.getX()>=0 && event.getX()<v.getWidth() && event.getY()>=0 && event.getY()<v.getHeight();
                            switch(event.getActionMasked()) {
                                case android.view.MotionEvent.ACTION_DOWN: down[0]=event.getEventTime(); return true;
                                case android.view.MotionEvent.ACTION_MOVE: if(!inside)down[0]=-1; return true;
                                case android.view.MotionEvent.ACTION_UP:
                                    long duration=down[0]<0?-1:event.getEventTime()-down[0]; down[0]=-1;
                                    if(inside && duration>=android.view.ViewConfiguration.getLongPressTimeout())v.performLongClick();
                                    else if(inside && duration>=GUARD_PRESS_MS)v.performClick();
                                    return true;
                                case android.view.MotionEvent.ACTION_CANCEL: down[0]=-1; return true;
                                default:return true;
                            }
                        });
                    }
                }
            }
        }
    }

    private void sync() {
        display.setText(number.toString());
        display.setVisibility(number.length()==0?View.INVISIBLE:View.VISIBLE);
        findViewById(R.id.dial_placeholder).setVisibility(number.length()==0?View.VISIBLE:View.GONE);
        // Long numbers shrink via autosize (API 26+); on older devices the
        // field scrolls horizontally and we keep the last digit in view.
        // Digits are NEVER dropped or ellipsized.
        display.post(() -> display.bringPointIntoView(number.length()));
        boolean has = number.length() >= 3;
        callBtn.setAlpha(has ? 1f : 0.45f);
        saveBtn.setAlpha(has ? 1f : 0.45f);
        renderMatches();
    }
}
