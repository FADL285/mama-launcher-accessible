package info.fadl.mamalauncher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.fadl.mamalauncher.ui.UiBinder;

/**
 * Everyday mode: big clock/date and ONLY the buttons the family setter chose.
 * No settings affordance, no "not installed" placeholders: an app that got
 * uninstalled simply disappears. Settings mode opens by long-pressing the
 * always-present clock header, then the PIN / device-lock gate.
 */
public class MainActivity extends BaseActivity {

    private LinearLayout tiles;
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

    private final Handler clock = new Handler();
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            clock.postDelayed(this, 30_000);
        }
    };

    private void updateClock() {
        ((TextView) findViewById(R.id.home_clock)).setText(
                new SimpleDateFormat("h:mm", Locale.US).format(new Date()));
        ((TextView) findViewById(R.id.home_date)).setText(
                new SimpleDateFormat("EEEE d MMMM", new Locale("ar")).format(new Date()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedContrast != Prefs.highContrast(this) || appliedFont != Prefs.fontScale(this)) {
            recreate();
            return;
        }
        updateClock();
        clock.removeCallbacks(clockTick);
        clock.postDelayed(clockTick, 30_000);
        buildTiles();
    }

    @Override
    protected void onPause() {
        clock.removeCallbacks(clockTick);
        super.onPause();
    }

    private void buildTiles() {
        tiles.removeAllViews();
        List<View> built = new ArrayList<>();

        View phone = UiBinder.buildHomeTile(this, getString(R.string.tile_phone),
                getResources().getDrawable(R.drawable.ic_call), R.drawable.chip_green,
                contrast(), null);
        guard(phone, () -> startActivity(new Intent(this, PhoneActivity.class)));
        built.add(phone);

        PackageManager pm = getPackageManager();
        for (String pkg : AppSelection.parse(Prefs.get(this).getString(AppSelection.PREF_KEY, null))) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue; // uninstalled: silently gone, no placeholder
            String name = pkg;
            Drawable icon = null;
            try {
                name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                icon = pm.getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) { }
            View tile = UiBinder.buildHomeTile(this, name, icon, 0, contrast(), null);
            guard(tile, () -> {
                try { startActivity(launch); }
                catch (android.content.ActivityNotFoundException e) { buildTiles(); }
            });
            built.add(tile);
        }

        float d = getResources().getDisplayMetrics().density;
        int m = (int) (6 * d);
        int h = (int) (170 * d);
        for (int i = 0; i < built.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
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

    @Override protected void onDestroy() {
        unregisterReceiver(packageChanges);
        clock.removeCallbacks(clockTick);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Home launcher: BACK on the everyday screen must not exit to a blank state.
    }
}
