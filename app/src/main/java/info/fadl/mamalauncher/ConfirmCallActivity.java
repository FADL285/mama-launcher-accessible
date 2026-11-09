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


    private static final String EXTRA_ID = "id";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_NUMBERS = "numbers";
    private static final String EXTRA_LABELS = "labels";
    private static final String EXTRA_PHOTO = "photo";
    private static final String STATE_NUMBER = "state_number";

    private String selectedNumber; // null until visibly chosen (multi-number)
    private NameSpeaker speaker;
    private Button callButton;
    private DirectCallAction callAction;
    private long contactId;
    private boolean systemFavorite;

    public static Intent intentFor(Context ctx, ContactEntry c) {
        Intent i = new Intent(ctx, ConfirmCallActivity.class);
        i.putExtra(EXTRA_ID, c.id);
        i.putExtra("system_favorite",c.starred);
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
        contactId=id;systemFavorite=getIntent().getBooleanExtra("system_favorite",false);
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
            refreshFavorite();
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
        guard(findViewById(R.id.btn_favorite),()-> {
            if(selectedNumber==null)return;
            LocalFavorites.set(this,contactId,selectedNumber,!LocalFavorites.contains(this,contactId,selectedNumber,systemFavorite));
            refreshFavorite();
        });
        refreshFavorite();
        callAction=new DirectCallAction(this,(blocked,granted) -> {
            findViewById(R.id.call_blocked).setVisibility(blocked?View.VISIBLE:View.GONE);
            if(granted)android.widget.Toast.makeText(this,R.string.call_permission_ready,android.widget.Toast.LENGTH_LONG).show();
        });
        guard(callButton, () -> callAction.tap(selectedNumber));
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
            refreshFavorite();
                });
            }
        }

        speaker = new NameSpeaker();
        speaker.setAvailabilityListener(new NameSpeaker.AvailabilityListener() {
            @Override public void onUnavailable(){findViewById(R.id.speak_unavailable).setVisibility(View.VISIBLE);}
            @Override public void onAvailable(){findViewById(R.id.speak_unavailable).setVisibility(View.GONE);}
        });
        speaker.init(this);
        final String spoken = NameSpeaker.speakText(name, getString(R.string.no_name));
        if (savedInstanceState == null && Prefs.autoSpeak(this)) speaker.speak(spoken);

        android.widget.ImageButton repeat = findViewById(R.id.btn_repeat_name);
        repeat.setOnClickListener(v -> speaker.speak(spoken)); // hearing the name NEVER calls

        Button back = findViewById(R.id.btn_back);
        back.setOnClickListener(v -> finish());
    }

    private void refreshFavorite() {
        android.widget.TextView star=findViewById(R.id.btn_favorite);
        boolean chosen=selectedNumber!=null;
        boolean favorite=chosen && LocalFavorites.contains(this,contactId,selectedNumber,systemFavorite);
        findViewById(R.id.favorite_hint).setVisibility(chosen?View.GONE:View.VISIBLE);
        star.setText(favorite?"★":"☆");star.setEnabled(chosen);star.setAlpha(chosen?1f:0.5f);
        star.setContentDescription(getString(!chosen?R.string.favorite_choose_number:favorite?R.string.favorite_remove:R.string.favorite_add));
    }

    @Override protected void onResume() {
        super.onResume();if(callAction!=null)callAction.onResume();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        callAction.permissionResult(code,results);
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
