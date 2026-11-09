package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import info.fadl.mamalauncher.ui.ContactGridAdapter;
import info.fadl.mamalauncher.ui.ContactPhotoLoader;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_CONTACTS = 41;

    private ContactPhotoLoader photos;
    private View setupView;
    private View homeView;
    private GridView grid;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        photos = new ContactPhotoLoader(getContentResolver());
        setupView = findViewById(R.id.setup_view);
        homeView = findViewById(R.id.home_view);
        grid = findViewById(R.id.contacts_grid);
        emptyView = findViewById(R.id.empty_contacts);

        Button allow = findViewById(R.id.btn_allow_contacts);
        allow.setOnClickListener(v ->
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS));

        grid.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            ContactEntry c = (ContactEntry) parent.getItemAtPosition(position);
            startActivity(ConfirmCallActivity.intentFor(this, c));
        });

        wireAppButton(R.id.app_whatsapp, R.id.icon_whatsapp, R.id.label_whatsapp,
                "com.whatsapp", R.string.whatsapp);
        wireAppButton(R.id.app_messenger, R.id.icon_messenger, R.id.label_messenger,
                "com.facebook.orca", R.string.messenger);
        wireAppButton(R.id.app_youtube, R.id.icon_youtube, R.id.label_youtube,
                "com.google.android.youtube", R.string.youtube);
    }

    /** Shows the app's REAL installed icon (the familiar one mama already knows),
     *  loaded at runtime from the device - nothing trademarked is bundled. */
    private void wireAppButton(int containerId, int iconId, int labelId, String pkg, int nameRes) {
        View container = findViewById(containerId);
        ImageView icon = findViewById(iconId);
        TextView label = findViewById(labelId);
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            container.setBackgroundResource(R.drawable.tile_app_disabled);
            icon.setVisibility(View.GONE);
            label.setText(getString(nameRes) + "\n" + getString(R.string.app_not_installed));
            label.setTextSize(18f);
            container.setAlpha(0.5f);
            container.setOnClickListener(v -> { /* not installed: deliberate no-op */ });
        } else {
            Drawable real = null;
            try {
                real = getPackageManager().getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) { }
            if (real != null) { icon.setImageDrawable(real); icon.setVisibility(View.VISIBLE); }
            label.setText(nameRes);
            container.setOnClickListener(v -> startActivity(launch));
        }
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
            homeView.setVisibility(View.GONE);
            return;
        }
        setupView.setVisibility(View.GONE);
        homeView.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<ContactEntry> sorted = ContactSorter.sort(
                    ContactsLoader.load(getContentResolver(), getResources()));
            runOnUiThread(() -> {
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

    @Override
    public void onBackPressed() {
        // As a home launcher, BACK on the main screen must not exit to a blank state.
        // Deliberate no-op: the grid IS the home screen.
    }
}
