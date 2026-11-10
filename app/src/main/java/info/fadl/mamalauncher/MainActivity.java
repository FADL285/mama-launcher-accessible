package info.fadl.mamalauncher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.fadl.mamalauncher.ui.UiBinder;

/**
 * Everyday mode: big clock/date and ONLY the buttons the family setter chose.
 * No settings affordance, no "not installed" placeholders: an app that got
 * uninstalled simply disappears. Settings mode opens by long-pressing the
 * always-present clock header, then the PIN / device-lock gate.
 */
public class MainActivity extends BaseActivity {

    static final String GUIDE_SEEN="first_use_guide_v1";
    private LinearLayout tiles;
    private BadgeCoordinator badges;
    private boolean returningFromApps;
    protected boolean isSecondary(){return false;}
    private boolean appliedContrast;
    private int appliedFont;
    private final android.content.BroadcastReceiver packageChanges = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) { buildTiles(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tiles = findViewById(R.id.tiles_container);
        badges = new BadgeCoordinator(this);
        returningFromApps=savedInstanceState!=null && savedInstanceState.getBoolean("navigation.returning_from_apps",false);
        if(isSecondary()) {
            View header=info.fadl.mamalauncher.ui.PageHeader.apps((android.widget.ScrollView)findViewById(R.id.home_header).getParent().getParent());
            guard(header.findViewById(R.id.btn_back),this::finish);
        }
        View guide=findViewById(R.id.first_use_guide);
        guide.setVisibility(!isSecondary() && !Prefs.get(this).getBoolean(GUIDE_SEEN,false)?View.VISIBLE:View.GONE);
        guard(findViewById(R.id.btn_guide_done),()->{Prefs.get(this).edit().putBoolean(GUIDE_SEEN,true).apply();guide.setVisibility(View.GONE);});
        guard(findViewById(R.id.btn_guide_sound),()->startActivity(PinGateActivity.intentForEntry(this).putExtra(SettingsActivity.EXTRA_SOUND,true)));
        appliedContrast = Prefs.highContrast(this);
        appliedFont = Prefs.fontScale(this);
        android.content.IntentFilter changes = new android.content.IntentFilter();
        changes.addAction(Intent.ACTION_PACKAGE_ADDED);
        changes.addAction(Intent.ACTION_PACKAGE_REMOVED);
        changes.addAction(Intent.ACTION_PACKAGE_CHANGED);
        changes.addDataScheme("package");
        registerReceiver(packageChanges, changes);
        findViewById(R.id.home_header).setOnLongClickListener(v -> {
            haptic(v);
            startActivity(PinGateActivity.intentForEntry(this));
            return true;
        });
    }

    private boolean clockReceiverRegistered;
    private final android.content.BroadcastReceiver timeChanges=new android.content.BroadcastReceiver(){@Override public void onReceive(android.content.Context c,Intent i){updateClock();}};
    protected Date clockNow(){return new Date();}
    private final Handler clock = new Handler();
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            clock.postDelayed(this, 30_000);
        }
    };

    private void updateClock() {
        if(isSecondary())return;
        Date now=clockNow();java.util.TimeZone zone=java.util.TimeZone.getDefault();
        info.fadl.mamalauncher.ui.HomeClock.bind(findViewById(R.id.home_header),now,zone);
        ((TextView)findViewById(R.id.home_date)).setText(info.fadl.mamalauncher.ui.HomeClock.date(now,zone));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(isFinishing())return;
        if (appliedContrast != Prefs.highContrast(this) || appliedFont != Prefs.fontScale(this)) {
            recreate();
            return;
        }
        if(!clockReceiverRegistered && !isSecondary()){android.content.IntentFilter filter=new android.content.IntentFilter();filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);filter.addAction(Intent.ACTION_DATE_CHANGED);registerReceiver(timeChanges,filter);clockReceiverRegistered=true;}
        updateClock();
        clock.removeCallbacks(clockTick);
        clock.postDelayed(clockTick, 30_000);
        buildTiles();
        badges.start();
        if(!isSecondary() && returningFromApps){returningFromApps=false;resetHomeScroll();}
    }

    @Override
    protected void onPause() {
        if (badges != null) badges.stop();
        if(clockReceiverRegistered){unregisterReceiver(timeChanges);clockReceiverRegistered=false;}
        clock.removeCallbacks(clockTick);
        super.onPause();
    }

    /** Persist selections but hide an empty folder when every app is unavailable. */
    boolean hasLaunchableSecondary() {
        for(String pkg:AppSelection.parse(Prefs.get(this).getString(AppSelection.SECONDARY_KEY,null)))
            if(getPackageManager().getLaunchIntentForPackage(pkg)!=null)return true;
        return false;
    }
    private void buildTiles() {
        tiles.removeAllViews();
        if (badges != null) badges.clearTiles();
        List<View> built = new ArrayList<>();

        PackageManager pm = getPackageManager();
        List<String> chosen=AppSelection.parse(Prefs.get(this).getString(isSecondary()?AppSelection.SECONDARY_KEY:AppSelection.PREF_KEY,null));
        List<String> order=isSecondary()?chosen:AppSelection.homeOrder(Prefs.get(this).getString(AppSelection.HOME_ORDER_KEY,null),chosen);
        for (String pkg : order) {
            if(AppSelection.PHONE.equals(pkg)){View phone=UiBinder.buildHomeTile(this,getString(R.string.tile_phone),getResources().getDrawable(R.drawable.ic_call),R.drawable.chip_green,contrast(),null);phone.setTag(AppSelection.PHONE);badges.tile(AppSelection.PHONE, phone);guardTile(phone,()->startActivity(new Intent(this,PhoneActivity.class)));built.add(phone);continue;}
            if(AppSelection.FOLDER.equals(pkg)){if(hasLaunchableSecondary()){View folder=UiBinder.buildHomeTile(this,getString(R.string.apps_page),getResources().getDrawable(R.drawable.ic_apps),R.drawable.chip_green,contrast(),null);folder.setTag(AppSelection.FOLDER);guardTile(folder,()->{returningFromApps=true;startActivity(new Intent(this,AppsPageActivity.class));});built.add(folder);}continue;}

            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue; // uninstalled: silently gone, no placeholder
            String name = pkg;
            Drawable icon = null;
            try {
                name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                icon = pm.getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) { }
            View tile = UiBinder.buildHomeTile(this, name, icon, 0, contrast(), null);
            badges.tile(pkg, tile);
            guardTile(tile, () -> {
                try { startActivity(launch); }
                catch (android.content.ActivityNotFoundException e) { buildTiles(); }
            });
            built.add(tile);
        }

        if(isSecondary() && built.isEmpty()) {
            TextView empty=new TextView(this);empty.setText(R.string.apps_empty);empty.setTextSize(24);empty.setTextColor(info.fadl.mamalauncher.ui.SurfaceText.color(this));tiles.addView(empty);
        }
        float d = getResources().getDisplayMetrics().density;
        int m = (int) (6 * d);
        int h = (int) (170 * d);
        for (int i = 0; i < built.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.TOP);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(m, m, m, m);
            built.get(i).setMinimumHeight(h);
            row.addView(built.get(i), lp);
            if (i + 1 < built.size()) {
                built.get(i + 1).setMinimumHeight(h);
                row.addView(built.get(i + 1), lp);
            } else {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
            tiles.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void guardTile(View tile,Runnable action){
        guard(tile,action);
        if(!Prefs.touchGuard(this))return;
        final long[] down={-1};
        tile.setOnTouchListener((v,e)->{
            boolean inside=e.getX()>=0 && e.getX()<v.getWidth() && e.getY()>=0 && e.getY()<v.getHeight();
            switch(e.getActionMasked()){
                case android.view.MotionEvent.ACTION_DOWN:down[0]=e.getEventTime();return true;
                case android.view.MotionEvent.ACTION_MOVE:if(!inside)down[0]=-1;return true;
                case android.view.MotionEvent.ACTION_UP:
                    long held=down[0]<0?-1:e.getEventTime()-down[0];down[0]=-1;
                    if(inside && held>=1000)v.performLongClick();else if(inside && held>=GUARD_PRESS_MS)v.performClick();return true;
                case android.view.MotionEvent.ACTION_CANCEL:down[0]=-1;return true;
                default:return true;
            }
        });
    }

    @Override protected void onDestroy() {
        if (badges != null) badges.close();
        if(clockReceiverRegistered){unregisterReceiver(timeChanges);clockReceiverRegistered=false;}
        unregisterReceiver(packageChanges);
        clock.removeCallbacks(clockTick);
        super.onDestroy();
    }

    /**
     * System Home (or the launcher icon) re-delivers MAIN to this singleTask
     * instance without a fresh onCreate. Coming back must always land at the
     * TOP of the everyday page: the family lost their place when the old
     * scroll position survived a Home press or the return from the Apps page.
     * Ordinary resumes (Back from an app, settings, rotation) are untouched.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(isSecondary() || intent==null || !(Intent.ACTION_MAIN.equals(intent.getAction()) ||
                (intent.getComponent()!=null && MainActivity.class.getName().equals(intent.getComponent().getClassName()))))return;
        resetHomeScroll();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putBoolean("navigation.returning_from_apps",returningFromApps);
        super.onSaveInstanceState(out);
    }
    private void resetHomeScroll(){
        View scroll=findViewById(R.id.home_scroll);
        if(scroll!=null)scroll.post(()->scroll.scrollTo(0,0));
    }

    @Override
    public void onBackPressed() {
        if(isSecondary()){finish();return;}
        // Home launcher: BACK on the everyday screen must not exit to a blank state.
    }
}
