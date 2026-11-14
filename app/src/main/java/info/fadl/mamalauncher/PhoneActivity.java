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
    // T-13 (owner decision 2026-11-13, option 1): the user's scroll anchor is the
    // first-visible contact, never the opened one. Back from confirmation keeps the
    // same first-visible contact by id, even when a pin/un-pin re-sort moved it.
    private static final String STATE_FIRST_VISIBLE = "phone.first_visible_contact";
    private long savedFirstVisible = -1;
    private View setupView;
    private View emptyView;
    private int refreshGeneration;
    /** T-06: the grid's contacts read owns one CallLogWorker: a new refresh
     *  cancels the in-flight read, a provider blocked inside query() or
     *  moveToNext() is cut off by the worker's watchdog and fails loudly,
     *  and only this screen's read is touched - the dialpad, Save and
     *  history consumers keep their own workers/signals. */
    private final CallLogWorker contactsWorker = new CallLogWorker();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            long top = savedInstanceState.getLong(STATE_FIRST_VISIBLE, -1);
            if (top > 0) savedFirstVisible = top;
        }
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
        contactsWorker.cancel();
        findViewById(R.id.contacts_read_error).setVisibility(View.GONE);
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission lost: nothing photo-related may stay in memory.
            photos.retireRequests();
            photos.retainUris(null);
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
        // Refresh STARTS here: in-flight decodes belong to the old list and
        // must be orphaned now (not after the read), and recorded misses get
        // a fresh chance. Decoded photos stay - see the success callback.
        photos.retireRequests();
        contactsWorker.read(
            signal -> {
                List<ContactEntry> original = ContactsLoader.loadVerified(
                        getContentResolver(), getResources(), NumberRegion.current(this), signal);
                signal.throwIfCanceled();
                final java.util.Set<Long> systemStars = new java.util.HashSet<>();
                for (ContactEntry c : original) if (c.starred) systemStars.add(c.id);
                List<ContactEntry> sorted = ContactSorter.sort(LocalFavorites.apply(this, original));
                return new android.util.Pair<>(systemStars, sorted);
            },
            result -> {
                final java.util.Set<Long> systemStars = result.first;
                final List<ContactEntry> sorted = result.second;
                if (generation != refreshGeneration) return; // stale results
                if (isDestroyed() || isFinishing()) return;
                if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){refresh();return;}
                final Long firstVisible;
                {
                    android.widget.ListAdapter old = grid.getAdapter();
                    int pos = grid.getFirstVisiblePosition();
                    ContactEntry top = (old != null && pos >= 0 && pos < old.getCount())
                            ? (ContactEntry) old.getItem(pos) : null;
                    firstVisible = top != null ? top.id : null;
                }
                // T-07: keep decoded photos whose identity the new list still
                // carries (no placeholder flash on an unchanged refresh);
                // drop the ones that left. Photo identity is URI + provider
                // photo version (PHOTO_ID): a photo edit keeps the URI but
                // bumps the version, so its stale pixels are evicted here.
                final java.util.Set<String> photoKeys = new java.util.HashSet<>();
                for (ContactEntry c : sorted) if (c.photoUri != null)
                    photoKeys.add(ContactPhotoLoader.photoKey(c.photoUri, c.photoVersion));
                photos.retainUris(photoKeys);
                grid.setAdapter(new ContactGridAdapter(this, sorted, photos) {
                    @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                        View tile = super.getView(position, recycled, parent);
                        ContactEntry contact = sorted.get(position);
                        guard(tile, () -> { if (beginNavigation()) {
                                startActivity(ConfirmCallActivity.intentFor(
                                PhoneActivity.this, contact).putExtra("system_favorite",systemStars.contains(contact.id)));
                        } });
                        return tile;
                    }
                });
                emptyView.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
                // T-13: keep the user's place. Back from confirmation restores the
                // same first-visible contact at its (possibly re-sorted) position;
                // otherwise the top. The opened contact is never scrolled to.
                final Long fallbackVisible = firstVisible != null ? firstVisible
                        : (savedFirstVisible > 0 ? savedFirstVisible : null);
                // The saved anchor is consumed only when the restore is applied,
                // so a second refresh racing this one cannot drop the place.
                grid.post(() -> { if (generation != refreshGeneration) return; // a newer refresh owns the restore
                    if (grid.getAdapter() != null && !sorted.isEmpty()) {
                    int place = restorePosition(sorted, fallbackVisible);
                    if (place > 0) grid.setSelection(place);
                    savedFirstVisible = -1;
                } });
            },
            () -> { // read failed or the watchdog cut a blocked provider off
                if(generation!=refreshGeneration || isDestroyed() || isFinishing())return;
                if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){refresh();return;}
                grid.setVisibility(View.GONE);emptyView.setVisibility(View.GONE);
                findViewById(R.id.contacts_read_error).setVisibility(View.VISIBLE);
            });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        // T-13: the first-visible contact survives recreation while confirmation
        // is open, so Back still lands on the user's place.
        android.widget.ListAdapter ad = grid.getAdapter();
        int pos = grid.getFirstVisiblePosition();
        if (ad != null && pos >= 0 && pos < ad.getCount())
            out.putLong(STATE_FIRST_VISIBLE, ((ContactEntry) ad.getItem(pos)).id);
        else if (savedFirstVisible > 0)
            // A second recreation before the async load installed an adapter
            // must not drop the restored anchor.
            out.putLong(STATE_FIRST_VISIBLE, savedFirstVisible);
        super.onSaveInstanceState(out);
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
        contactsWorker.cancel();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (history != null) history.close();
        contactsWorker.close();
        if (photos != null) photos.close();
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

    static int restorePosition(java.util.List<ContactEntry> sorted, Long firstVisible) {
        if (firstVisible != null) { int i = indexOfContact(sorted, firstVisible); if (i >= 0) return i; }
        return 0;
    }
    private static int indexOfContact(java.util.List<ContactEntry> sorted, long id) {
        for (int i = 0; i < sorted.size(); i++) if (sorted.get(i).id == id) return i;
        return -1;
    }
}
