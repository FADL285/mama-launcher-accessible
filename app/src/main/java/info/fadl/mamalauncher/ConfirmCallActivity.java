package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import info.fadl.mamalauncher.ui.ContactGridAdapter;
import info.fadl.mamalauncher.ui.ContactPhotoLoader;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * D1 confirmation screen: enlarged photo + spoken name (pilot default: TTS,
 * method still open in D2 / test T6) + big name + visible number choice when
 * the person has more than one number + ONE big green call button (the only
 * thing that starts a call) + a big clear back button.
 */
public class ConfirmCallActivity extends BaseActivity {

    private static final int REQ_CALL = 42;
    private static final String EXTRA_ID = "id";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_NUMBERS = "numbers";
    private static final String EXTRA_LABELS = "labels";
    private static final String EXTRA_PHOTO = "photo";
    private static final String STATE_NUMBER = "state_number";

    private String selectedNumber; // null until visibly chosen (multi-number)
    private NameSpeaker speaker;
    private Button callButton;
    private long lastCallTapMs;

    public static Intent intentFor(Context ctx, ContactEntry c) {
        Intent i = new Intent(ctx, ConfirmCallActivity.class);
        i.putExtra(EXTRA_ID, c.id);
        i.putExtra(EXTRA_NAME, c.name);
        ArrayList<String> numbers = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (ContactEntry.PhoneNumber pn : c.numbers) {
            numbers.add(pn.number);
            labels.add(pn.label);
        }
        i.putStringArrayListExtra(EXTRA_NUMBERS, numbers);
        i.putStringArrayListExtra(EXTRA_LABELS, labels);
        i.putExtra(EXTRA_PHOTO, c.photoUri);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm);

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        String photoUri = getIntent().getStringExtra(EXTRA_PHOTO);
        if (name == null || name.trim().isEmpty()) name = getString(R.string.no_name);

        List<ContactEntry.PhoneNumber> numbers = new ArrayList<>();
        ArrayList<String> nums = getIntent().getStringArrayListExtra(EXTRA_NUMBERS);
        ArrayList<String> labs = getIntent().getStringArrayListExtra(EXTRA_LABELS);
        if (nums != null) {
            for (int i = 0; i < nums.size(); i++) {
                numbers.add(new ContactEntry.PhoneNumber(nums.get(i),
                        labs != null && i < labs.size() ? labs.get(i) : ""));
            }
        }

        TextView bigName = findViewById(R.id.big_name);
        bigName.setText(name);

        ImageView bigPhoto = findViewById(R.id.big_photo);
        TextView fallback = findViewById(R.id.big_photo_fallback);
        Bitmap bmp = new ContactPhotoLoader(getContentResolver()).load(photoUri, 900);
        if (bmp != null) {
            bigPhoto.setImageBitmap(bmp);
        } else {
            bigPhoto.setVisibility(View.GONE);
            fallback.setBackgroundColor(ContactGridAdapter.tileColorFor(id));
            fallback.setText(name);
            fallback.setVisibility(View.VISIBLE);
        }

        callButton = findViewById(R.id.btn_call);
        UiBinder.bindNumbers(findViewById(android.R.id.content), numbers, number -> {
            selectedNumber = number;
            callButton.setEnabled(true);
        });
        // Restore a previously chosen number (rotation/font change recreation):
        // selection and its visible highlight survive, and the name is NOT
        // spoken again just because the activity was recreated.
        String restored = savedInstanceState == null
                ? null : savedInstanceState.getString(STATE_NUMBER);
        if (restored != null) {
            selectedNumber = restored;
            UiBinder.selectNumber(findViewById(android.R.id.content), restored);
        }
        // Multi-number: nothing is callable until a number row is tapped.
        callButton.setEnabled(selectedNumber != null);
        guard(callButton, this::onCallTapped);
        // bindNumbers keeps a click action for visible selection. Add the
        // same physical-touch guard without changing that accessibility path.
        android.widget.LinearLayout numberRows = findViewById(R.id.numbers_container);
        for (int r = 0; r < numberRows.getChildCount(); r++) {
            android.view.ViewGroup row = (android.view.ViewGroup) numberRows.getChildAt(r);
            for (int k = 0; k < row.getChildCount(); k++) {
                View button = row.getChildAt(k);
                final String value = (String) button.getTag();
                guard(button, () -> {
                    UiBinder.selectNumber(findViewById(android.R.id.content), value);
                    selectedNumber = value;
                    callButton.setEnabled(true);
                });
            }
        }

        speaker = new NameSpeaker();
        speaker.setAvailabilityListener(() ->
                findViewById(R.id.speak_unavailable).setVisibility(View.VISIBLE));
        speaker.init(this);
        final String spoken = NameSpeaker.speakText(name, getString(R.string.no_name));
        if (savedInstanceState == null && Prefs.autoSpeak(this)) speaker.speak(spoken);

        android.widget.ImageButton repeat = findViewById(R.id.btn_repeat_name);
        repeat.setOnClickListener(v -> speaker.speak(spoken)); // hearing the name NEVER calls

        Button back = findViewById(R.id.btn_back);
        back.setOnClickListener(v -> finish());
    }

    private void onCallTapped() {
        // Debounce: a shaky double tap must not start two call attempts.
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastCallTapMs > 0 && now - lastCallTapMs < 1500) return;
        lastCallTapMs = now;
        if (selectedNumber == null || selectedNumber.isEmpty()) return;

        CallDecision.PermissionState state;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
            state = CallDecision.PermissionState.GRANTED;
        } else if (permWasPermanentlyDenied()) {
            state = CallDecision.PermissionState.BLOCKED;
        } else {
            state = CallDecision.PermissionState.CAN_REQUEST;
        }

        switch (CallDecision.decide(state)) {
            case PLACE_CALL:
                Intent call = new Intent(Intent.ACTION_CALL,
                        Uri.parse("tel:" + Uri.encode(selectedNumber)));
                try { startActivity(call); }
                catch (android.content.ActivityNotFoundException e) {
                    findViewById(R.id.call_blocked).setVisibility(View.VISIBLE);
                }
                break;
            case REQUEST_PERMISSION:
                requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
                break;
            default:
                findViewById(R.id.call_blocked).setVisibility(View.VISIBLE);
        }
    }

    private boolean permWasPermanentlyDenied() {
        return getSharedPreferences("pilot", MODE_PRIVATE)
                .getBoolean("call_denied_once", false)
                && !shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CALL) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        // A grant NEVER starts a call by itself (pinned by CallDecisionTest):
        // mama still has to tap the green button deliberately.
        if (CallDecision.afterPermissionResult(granted)
                == CallDecision.PostResult.WAIT_FOR_EXPLICIT_TAP) {
            findViewById(R.id.call_blocked).setVisibility(View.GONE);
        } else {
            getSharedPreferences("pilot", MODE_PRIVATE)
                    .edit().putBoolean("call_denied_once", true).apply();
            findViewById(R.id.call_blocked).setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedNumber != null) outState.putString(STATE_NUMBER, selectedNumber);
    }

    @Override
    protected void onDestroy() {
        if (speaker != null) speaker.shutdown();
        super.onDestroy();
    }
}
