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

    private static final int REQ_CONTACTS = 41;

    private ContactPhotoLoader photos;
    private GridView grid;
    private View setupView;
    private View emptyView;
    private int refreshGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone);
        photos = new ContactPhotoLoader(getContentResolver());
        grid = findViewById(R.id.contacts_grid);
        setupView = findViewById(R.id.setup_view);
        emptyView = findViewById(R.id.empty_contacts);

        guard(findViewById(R.id.btn_back), this::finish);
        guard(findViewById(R.id.btn_dialpad),
                () -> startActivity(new Intent(this, DialpadActivity.class)));
        guard(findViewById(R.id.btn_allow_contacts), () ->
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS));

        grid.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            haptic(view);
            ContactEntry c = (ContactEntry) parent.getItemAtPosition(position);
            startActivity(ConfirmCallActivity.intentFor(this, c));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            setupView.setVisibility(View.VISIBLE);
            grid.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            return;
        }
        setupView.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        final int generation = ++refreshGeneration;
        new Thread(() -> {
            List<ContactEntry> sorted = ContactSorter.sort(
                    ContactsLoader.load(getContentResolver(), getResources()));
            runOnUiThread(() -> {
                if (generation != refreshGeneration) return; // stale results
                grid.setAdapter(new ContactGridAdapter(this, sorted, photos));
                emptyView.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }
}
