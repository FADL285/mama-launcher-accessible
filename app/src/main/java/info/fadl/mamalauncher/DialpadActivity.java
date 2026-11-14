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
 @Override protected boolean homeAfterLockApplies(){return true;}

    private final StringBuilder number = new StringBuilder();
    private TextView display;
    private View callBtn;
    private View saveBtn;
    private DirectCallAction callAction;
    private java.util.List<info.fadl.mamalauncher.ui.ContactEntry> contacts = new java.util.ArrayList<>();
    /** In-memory match rendering only - provider reads never run on this worker. */
    private final java.util.concurrent.ExecutorService lookupWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    /** T-06: refresh and Save are separate consumers on separate workers, each
     *  with cancel + watchdog + abandoned-thread recovery, so a new refresh
     *  cancels only the previous refresh, a stuck refresh never delays Save -
     *  and neither touches the grid or history readers. */
    private final CallLogWorker lookupReader=new CallLogWorker();
    private final CallLogWorker saveReader=new CallLogWorker();
    private volatile int matchGeneration;
    private boolean lookupPending;
    private boolean checkingSave;
    private int saveRequest;
    private boolean resumed;
    private android.app.AlertDialog saveChooser;
    private boolean contactsAvailable;
    private String country() { return NumberRegion.current(this); }
    private boolean canReadContacts() {return checkSelfPermission(android.Manifest.permission.READ_CONTACTS)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    private void refreshContacts() {
        if(!canReadContacts()){lookupReader.cancel();lookupPending=false;contactsAvailable=false;contacts.clear();renderMatches();return;}
        lookupPending=true;renderMatches();
        lookupReader.read(
            signal -> ContactsLoader.loadVerified(getContentResolver(),getResources(),NumberRegion.current(this),signal),
            loaded -> {contacts=loaded;lookupPending=false;contactsAvailable=true;renderMatches();},
            () -> {
                // A failed refresh only ends the pending state: it must never
                // wipe verified data a Save (or an earlier refresh) published -
                // e.g. while the multi-match chooser is open. Real revocation
                // is handled by the permission path above.
                lookupPending=false;renderMatches();
            });
    }
    /** D-01 (option C): callable when 3+ digits, or the input contains * or #. */
    static boolean callable(String s) {
        if(s==null || s.isEmpty()) return false;
        if(s.indexOf('*')>=0 || s.indexOf('#')>=0) return true;
        int digits=0;
        for(int i=0;i<s.length();i++) {char c=s.charAt(i);if(c>='0'&&c<='9')digits++;}
        return digits>=3;
    }
    /** A Save lookup: the verified contacts plus the exact matches for the typed number. */
    private static final class SaveLookup {
        final java.util.List<info.fadl.mamalauncher.ui.ContactEntry> all;
        final java.util.List<info.fadl.mamalauncher.ui.ContactEntry> exact;
        SaveLookup(java.util.List<info.fadl.mamalauncher.ui.ContactEntry> all,java.util.List<info.fadl.mamalauncher.ui.ContactEntry> exact){this.all=all;this.exact=exact;}
    }
    private void saveOrShowExisting() {
        if(!callable(number.toString()) || checkingSave || !resumed)return;
        final String typed=number.toString();final int request=++saveRequest;final String region=country();checkingSave=true;
        saveReader.read(
            signal -> {
                if(!canReadContacts()) throw new SecurityException("READ_CONTACTS not granted");
                java.util.List<info.fadl.mamalauncher.ui.ContactEntry> loaded=ContactsLoader.loadVerified(getContentResolver(),getResources(),NumberRegion.current(this),signal);
                return new SaveLookup(loaded,DialContactMatcher.matches(loaded,typed,region,true));
            },
            result -> {
                checkingSave=false;
                if(!resumed || isDestroyed() || isFinishing())return;
                if(!typed.equals(number.toString())){android.widget.Toast.makeText(this,R.string.dial_save_changed,android.widget.Toast.LENGTH_LONG).show();return;}
                contacts=result.all;contactsAvailable=true;renderMatches();
                final java.util.List<info.fadl.mamalauncher.ui.ContactEntry> exact=result.exact;
                if(exact.size()==1)startActivity(ConfirmCallActivity.intentFor(this,exact.get(0)));
                else if(exact.size()>1)saveChooser=new android.app.AlertDialog.Builder(this).setTitle(R.string.dial_saved_choose)
                    .setItems(exact.stream().map(c->c.name).toArray(String[]::new),(d,i)->{if(resumed && request==saveRequest)startActivity(ConfirmCallActivity.intentFor(this,exact.get(i)));})
                    .setNegativeButton(android.R.string.cancel,null).show();
                else try {startActivity(DialIntents.saveAsContact(typed));}catch(android.content.ActivityNotFoundException e){handoffUnavailable();}
            },
            () -> {
                // A blocked or failed provider is a failed lookup, not a hang.
                checkingSave=false;
                if(resumed && !isDestroyed()) android.widget.Toast.makeText(this,R.string.dial_lookup_needed,android.widget.Toast.LENGTH_LONG).show();
            });
    }
    private void renderMatches() {
        if(display==null)return;
        android.widget.LinearLayout box=findViewById(R.id.dial_matches);box.removeAllViews();
        final int generation=++matchGeneration;
        final String typed=number.toString();
        ((TextView)findViewById(R.id.save_number_label)).setText(R.string.save_number);
        if(number.length()<3)return;
        if(lookupPending || !contactsAvailable) {
            TextView hint=new TextView(this);hint.setText(lookupPending?R.string.dial_lookup_loading:R.string.dial_lookup_needed);hint.setTextSize(16);int inset=(int)(8*getResources().getDisplayMetrics().density);hint.setPadding(inset,0,inset,0);hint.setTextColor(info.fadl.mamalauncher.ui.SurfaceText.color(this));box.addView(hint);return;
        }
        final java.util.List<info.fadl.mamalauncher.ui.ContactEntry> snapshot=new java.util.ArrayList<>(contacts);
        final String region=country();
        lookupWorker.execute(()-> {
            if(generation!=matchGeneration)return;
            java.util.List<info.fadl.mamalauncher.ui.ContactEntry> exact=DialContactMatcher.matches(snapshot,typed,region,true);
            java.util.List<info.fadl.mamalauncher.ui.ContactEntry> found=DialContactMatcher.matches(snapshot,typed,region,false);
            runOnUiThread(()-> {
                if(isDestroyed() || generation!=matchGeneration || !typed.equals(number.toString()))return;
                ((TextView)findViewById(R.id.save_number_label)).setText(exact.isEmpty()?R.string.save_number:R.string.dial_view_saved);
                showMatches(found,exact,typed);
            });
        });
    }
    private void showMatches(java.util.List<info.fadl.mamalauncher.ui.ContactEntry> found,java.util.List<info.fadl.mamalauncher.ui.ContactEntry> exact,String typed) {
        android.widget.LinearLayout box=findViewById(R.id.dial_matches);box.removeAllViews();
        int shown=0;
        for(info.fadl.mamalauncher.ui.ContactEntry c:found) {
            if(shown++==1)break;
            boolean saved=exact.stream().anyMatch(e->e.id==c.id);
            android.widget.Button row=info.fadl.mamalauncher.ui.DialMatchUi.preview(this,c,saved);
            guard(row,()->{if(beginNavigation())startActivity(ConfirmCallActivity.intentFor(this,c));});box.addView(row);
        }
        if(found.size()>1) {
            android.widget.Button more=new android.widget.Button(this);more.setText(getString(R.string.dial_more,found.size()));more.setAllCaps(false);more.setTextSize(18);
            guard(more,()->new android.app.AlertDialog.Builder(this).setTitle(R.string.dial_results)
                .setAdapter(new android.widget.BaseAdapter(){
                    public int getCount(){return found.size();}
                    public Object getItem(int i){return found.get(i);}
                    public long getItemId(int i){return found.get(i).id;}
                    public android.view.View getView(int i,android.view.View recycled,android.view.ViewGroup parent){return info.fadl.mamalauncher.ui.DialMatchUi.chooserItem(parent.getContext(),found.get(i));}
                },(d,i)->{if(beginNavigation())startActivity(ConfirmCallActivity.intentFor(this,found.get(i)));})
                .setNegativeButton(android.R.string.cancel,null).show());box.addView(more);
        }
    }
    /** Deterministic synchronization point for isolated JVM flow tests. */
    void awaitLookupsForTest() throws Exception {lookupReader.awaitIdleForTest();saveReader.awaitIdleForTest();lookupWorker.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);}
    @Override protected void onDestroy(){matchGeneration++;lookupReader.close();saveReader.close();lookupWorker.shutdownNow();super.onDestroy();}


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialpad);
        info.fadl.mamalauncher.ui.PageHeader.title(findViewById(android.R.id.content),R.string.dialpad_title);
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
        guard(callBtn, () -> { if(callable(number.toString())) callAction.tap(number.toString()); });
        guard(saveBtn, this::saveOrShowExisting);
        sync();
    }

    @Override protected void onResume() {
        super.onResume(); if(isFinishing())return;resumed=true;if(callAction!=null)callAction.onResume();refreshContacts();
    }
    /** Leaving the page invalidates reads and chooser actions, even if it is not destroyed. */
    @Override protected void onPause() {
        resumed=false;checkingSave=false;lookupPending=false;
        saveReader.cancel();
        lookupReader.cancel();
        if(saveChooser!=null){saveChooser.dismiss();saveChooser=null;}
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        callAction.permissionResult(code,permissions,results);
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
        boolean can = callable(number.toString());
        // Truly disabled, not just dimmed: TalkBack announces the real state.
        callBtn.setEnabled(can);
        saveBtn.setEnabled(can);
        callBtn.setAlpha(can ? 1f : 0.45f);
        saveBtn.setAlpha(can ? 1f : 0.45f);
        renderMatches();
    }
}
