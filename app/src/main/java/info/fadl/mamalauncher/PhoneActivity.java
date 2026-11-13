package info.fadl.mamalauncher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;

import java.util.List;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.ContactGridAdapter;
import info.fadl.mamalauncher.ui.ContactPhotoLoader;

/** The Phone button: big photo contacts, plus the dial-pad entry for a new
 *  number. Contacts permission is requested here, not on the home screen. */
public class PhoneActivity extends BaseActivity {
 @Override protected boolean homeAfterLockApplies(){return true;}

    private static final int REQ_CONTACTS = 41;
    private static final String PREF_CONTACTS_DENIED = "contacts_denial_completed";

    private CallHistoryController history;
    private ContactPhotoLoader photos;
    private GridView grid;
    private View setupView;
    private View emptyView;
    private int refreshGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone);
        info.fadl.mamalauncher.ui.PageHeader.title(findViewById(android.R.id.content),R.string.tile_phone);
        guard(findViewById(R.id.btn_retry_contacts),this::refresh);
        photos = new ContactPhotoLoader(getContentResolver());
        grid = findViewById(R.id.contacts_grid);
        setupView = findViewById(R.id.setup_view);
        emptyView = findViewById(R.id.empty_contacts);

        history = new CallHistoryController(this, this::guard);
        guard(findViewById(R.id.btn_tab_contacts), () -> selectHistory(false));
        guard(findViewById(R.id.btn_tab_history), () -> selectHistory(true));
        selectHistory(false);
        guard(findViewById(R.id.btn_back), this::finish);
        guard(findViewById(R.id.btn_dialpad),
                () -> startActivity(new Intent(this, DialpadActivity.class)));
        guard(findViewById(R.id.btn_allow_contacts), () -> {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED) { refresh(); return; }
            // A previous launch or a false rationale is NOT proof of permanent
            // denial. Always let an explicit tap ask Android again. USER_FIXED
            // may suppress the dialog; never replace this tap with Settings.
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        });
        guard(findViewById(R.id.btn_contacts_settings), () -> startActivity(new Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + getPackageName()))));


    }

    @Override
    protected void onResume() {
        super.onResume();
        if(isFinishing())return;
        refresh();
        history.resume();
    }

    private void refresh() {
        final int generation = ++refreshGeneration;
        findViewById(R.id.contacts_read_error).setVisibility(View.GONE);
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            boolean denied = Prefs.get(this).getBoolean(PREF_CONTACTS_DENIED, false);
            boolean recovery = denied && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS);
            ((android.widget.TextView)findViewById(R.id.btn_allow_contacts)).setText(
                    denied ? R.string.contacts_permission_retry : R.string.allow_contacts);
            findViewById(R.id.contacts_permission_help).setVisibility(recovery ? View.VISIBLE : View.GONE);
            findViewById(R.id.btn_contacts_settings).setVisibility(recovery ? View.VISIBLE : View.GONE);
            setupView.setVisibility(View.VISIBLE);
            grid.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            return;
        }
        Prefs.get(this).edit().remove(PREF_CONTACTS_DENIED).apply();
        setupView.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<ContactEntry> original;
            try { original=ContactsLoader.loadVerified(getContentResolver(),getResources()); }
            catch(RuntimeException readFailure) {
                runOnUiThread(() -> {
                    if(generation!=refreshGeneration || isDestroyed() || isFinishing())return;
                    if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){refresh();return;}
                    grid.setVisibility(View.GONE);emptyView.setVisibility(View.GONE);
                    findViewById(R.id.contacts_read_error).setVisibility(View.VISIBLE);
                });return;
            }
            final java.util.Set<Long> systemStars=new java.util.HashSet<>();
            for(ContactEntry c:original)if(c.starred)systemStars.add(c.id);
            List<ContactEntry> sorted=ContactSorter.sort(LocalFavorites.apply(this,original));
            runOnUiThread(() -> {
                if (generation != refreshGeneration) return; // stale results
                if (isDestroyed() || isFinishing()) return;
                if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){refresh();return;}
                grid.setAdapter(new ContactGridAdapter(this, sorted, photos) {
                    @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                        View tile = super.getView(position, recycled, parent);
                        ContactEntry contact = sorted.get(position);
                        guard(tile, () -> { if (beginNavigation()) startActivity(ConfirmCallActivity.intentFor(
                                PhoneActivity.this, contact).putExtra("system_favorite",systemStars.contains(contact.id))); });
                        return tile;
                    }
                });
                emptyView.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    void selectHistory(boolean selected) {
        findViewById(R.id.phone_contacts_panel).setVisibility(selected ? View.GONE : View.VISIBLE);
        findViewById(R.id.phone_history_panel).setVisibility(selected ? View.VISIBLE : View.GONE);
        for (int id : new int[]{R.id.btn_tab_contacts, R.id.btn_tab_history}) {
            boolean chosen = id == (selected ? R.id.btn_tab_history : R.id.btn_tab_contacts);
            info.fadl.mamalauncher.ui.PhoneUi.select(findViewById(android.R.id.content), id, chosen);
        }
        history.select(selected);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && history != null) history.focused();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        selectHistory(false);
    }
    @Override protected void onPause() {
        if (history != null) history.pause();
        refreshGeneration++;
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (history != null) history.close();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CallLogAccess.REQUEST) {
            CallLogAccess.recordResult(this, permissions, grantResults);
            history.refresh();
            return;
        }
        if (requestCode != REQ_CONTACTS) return;
        // Empty results mean interruption/cancellation, not a completed denial.
        if (permissions.length > 0 && grantResults.length > 0
                && Manifest.permission.READ_CONTACTS.equals(permissions[0])) {
            Prefs.get(this).edit().putBoolean(PREF_CONTACTS_DENIED,
                    grantResults[0] != PackageManager.PERMISSION_GRANTED).apply();
        }
        refresh();
    }
}
