package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.cash.paparazzi.Paparazzi;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Renders the REAL layouts to PNGs via layoutlib (no emulator) as visual
 *  evidence for the pilot review, on an Arabic RTL device configuration.
 *  All fixtures are fictional people and numbers. */
public class UiSnapshotTest {

    private static app.cash.paparazzi.DeviceConfig arabicDevice() {
        app.cash.paparazzi.DeviceConfig d = app.cash.paparazzi.DeviceConfig.NEXUS_5;
        return new app.cash.paparazzi.DeviceConfig(
                d.getScreenHeight(), d.getScreenWidth(), d.getXdpi(), d.getYdpi(),
                d.getOrientation(), com.android.resources.UiMode.NORMAL, d.getNightMode(),
                d.getDensity(), d.getFontScale(), com.android.resources.LayoutDirection.RTL,
                "ar", d.getRatio(), d.getSize(), d.getKeyboard(), d.getTouchScreen(),
                d.getKeyboardState(), d.getSoftButtons(), d.getNavigation(),
                d.getScreenRound(), d.getReleased());
    }

    @Rule
    public final Paparazzi paparazzi = new Paparazzi(
            app.cash.paparazzi.EnvironmentKt.detectEnvironment(), arabicDevice(), "MamaTheme");

    // ---- everyday home ----

    private void addHomeRows(View root, LinearLayout container, boolean contrast) {
        List<View> tiles = new ArrayList<>();
        tiles.add(UiBinder.buildHomeTile(root.getContext(), "الهاتف",
                root.getResources().getDrawable(R.drawable.ic_call),
                R.drawable.chip_green, contrast, null));
        tiles.add(UiBinder.buildHomeTile(root.getContext(), "يوتيوب",
                root.getResources().getDrawable(R.drawable.ic_play),
                R.drawable.chip_blue, contrast, null));
        tiles.add(UiBinder.buildHomeTile(root.getContext(), "واتساب",
                root.getResources().getDrawable(R.drawable.ic_chat),
                R.drawable.chip_wa, contrast, null));
        float d = root.getResources().getDisplayMetrics().density;
        int m = (int) (6 * d);
        int h = (int) (170 * d);
        for (int i = 0; i < tiles.size(); i += 2) {
            LinearLayout row = new LinearLayout(root.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(m, m, m, m);
            row.addView(tiles.get(i), lp);
            if (i + 1 < tiles.size()) {
                row.addView(tiles.get(i + 1), lp);
            } else {
                row.addView(new View(root.getContext()),
                        new LinearLayout.LayoutParams(0, 1, 1f));
            }
            container.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    @Test
    public void everydayHome() {
        View v = paparazzi.inflate(R.layout.activity_main);
        ((TextView) v.findViewById(R.id.home_clock)).setText("1:00");
        ((TextView) v.findViewById(R.id.home_date)).setText("الاثنين ٩ نوفمبر");
        addHomeRows(v, v.findViewById(R.id.tiles_container), false);
        paparazzi.snapshot(v, "everyday-home");
    }



    // ---- phone (photo contacts) ----

    private List<ContactEntry> fictionalContacts() {
        List<ContactEntry> contacts = new ArrayList<>();
        contacts.add(new ContactEntry(1, "فاطمة", "01000000001", null, true));
        contacts.add(new ContactEntry(2, "سوسن", "01000000002", null, true));
        contacts.add(new ContactEntry(3, "محمود", "01000000003", null, false));
        contacts.add(new ContactEntry(4, "هالة", "01000000004", null, false));
        contacts.add(new ContactEntry(5, "خالد", "01000000005", null, false));
        contacts.add(new ContactEntry(6, "سارة", "01000000006", null, false));
        return contacts;
    }

    @Test
    public void phoneContacts() {
        View v = paparazzi.inflate(R.layout.activity_phone);
        GridView grid = v.findViewById(R.id.contacts_grid);
        grid.setAdapter(new ContactGridAdapter(v.getContext(), fictionalContacts(),
                new ContactPhotoLoader(null)));
        paparazzi.snapshot(v, "phone-contacts");
    }

    @Test
    public void phoneContactsPermissionSetup() {
        View v = paparazzi.inflate(R.layout.activity_phone);
        v.findViewById(R.id.contacts_grid).setVisibility(View.GONE);
        v.findViewById(R.id.setup_view).setVisibility(View.VISIBLE);
        paparazzi.snapshot(v, "phone-setup");
    }

    // ---- dial pad ----

    @Test
    public void dialpadComposing() {
        View v = paparazzi.inflate(R.layout.activity_dialpad);
        ((TextView) v.findViewById(R.id.dial_display)).setText("0100 000 0");
        paparazzi.snapshot(v, "dialpad-composing");
    }

    // ---- settings gate ----

    @Test
    public void pinGateEntry() {
        View v = paparazzi.inflate(R.layout.activity_pingate);
        android.graphics.drawable.Drawable filled =
                v.getResources().getDrawable(R.drawable.chip_green);
        LinearLayout dots = v.findViewById(R.id.pin_dots);
        dots.getChildAt(0).setBackground(filled);
        dots.getChildAt(1).setBackground(filled);
        paparazzi.snapshot(v, "pin-gate");
    }

    // ---- settings ----

    @Test
    public void settingsScreen() {
        View v = paparazzi.inflate(R.layout.activity_settings);
        v.findViewById(R.id.apps_loading).setVisibility(View.GONE);
        ((TextView) v.findViewById(R.id.perm_contacts_status)).setText("مسموح");
        ((TextView) v.findViewById(R.id.perm_call_status)).setText("مش مسموح");
        ((TextView) v.findViewById(R.id.tts_status)).setText("محرك الصوت جاهز");
        ((android.widget.Switch) v.findViewById(R.id.switch_auto_speak)).setChecked(true);
        ((android.widget.Switch) v.findViewById(R.id.switch_vibrate)).setChecked(true);
        paparazzi.snapshot(v, "settings-screen");
    }

    // ---- call confirmation (unchanged behavior, preserved from pilot) ----

    private void bindConfirmCommon(View v, String name, int tileColor) {
        ((TextView) v.findViewById(R.id.big_name)).setText(name);
        TextView fallback = v.findViewById(R.id.big_photo_fallback);
        v.findViewById(R.id.big_photo).setVisibility(View.GONE);
        fallback.setBackgroundColor(tileColor);
        fallback.setText(name);
        fallback.setVisibility(View.VISIBLE);
    }

    private static List<ContactEntry.PhoneNumber> nums(String... pairs) {
        List<ContactEntry.PhoneNumber> out = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.add(new ContactEntry.PhoneNumber(pairs[i], pairs[i + 1]));
        }
        return out;
    }

    @Test
    public void confirmSingleNumber() {
        View v = paparazzi.inflate(R.layout.activity_confirm);
        bindConfirmCommon(v, "فاطمة", 0xFF2F5D8A);
        UiBinder.bindNumbers(v, nums("01000000001", "موبايل"), n -> { });
        v.findViewById(R.id.btn_call).setEnabled(true);
        paparazzi.snapshot(v, "confirm-single");
    }

    @Test
    public void confirmMultiNumberNothingCallableUntilChosen() {
        View v = paparazzi.inflate(R.layout.activity_confirm);
        bindConfirmCommon(v, "سوسن محمود", 0xFF8A2F4E);
        UiBinder.bindNumbers(v, nums("01000000001", "موبايل", "0200000002", "البيت"), n -> { });
        v.findViewById(R.id.btn_call).setEnabled(false); // until a number is tapped
        paparazzi.snapshot(v, "confirm-multi-unselected");
    }

    @Test
    public void confirmMultiNumberSelected() {
        View v = paparazzi.inflate(R.layout.activity_confirm);
        bindConfirmCommon(v, "سوسن محمود", 0xFF8A2F4E);
        UiBinder.bindNumbers(v, nums("01000000001", "موبايل", "0200000002", "البيت"), n -> { });
        android.view.ViewGroup box = v.findViewById(R.id.numbers_container);
        ((android.view.ViewGroup) box.getChildAt(0)).getChildAt(1).performClick();
        v.findViewById(R.id.btn_call).setEnabled(true);
        paparazzi.snapshot(v, "confirm-multi-selected");
    }

    @Test
    public void contactTileFallbackWithStar() {
        View v = paparazzi.inflate(R.layout.item_contact);
        ((TextView) v.findViewById(R.id.name)).setText("ماما");
        TextView fb = v.findViewById(R.id.photo_fallback_name);
        v.findViewById(R.id.photo).setVisibility(View.GONE);
        fb.setBackgroundColor(0xFF6B4E8E);
        fb.setText("ماما");
        fb.setVisibility(View.VISIBLE);
        v.findViewById(R.id.star).setVisibility(View.VISIBLE);
        paparazzi.snapshot(v, "tile-fallback-star");
    }

    // ---- settings gate states ----

    @Test
    public void pinGateSetupFirstEntry() {
        View v = paparazzi.inflate(R.layout.activity_pingate);
        ((TextView) v.findViewById(R.id.pin_subtitle)).setText("اختار رمز جديد من ٤ أرقام");
        paparazzi.snapshot(v, "pin-gate-setup");
    }

    @Test
    public void pinGateWrongPin() {
        View v = paparazzi.inflate(R.layout.activity_pingate);
        ((TextView) v.findViewById(R.id.pin_error)).setText("الرمز غلط");
        paparazzi.snapshot(v, "pin-gate-wrong");
    }

    @Test
    public void pinGateLockedOut() {
        View v = paparazzi.inflate(R.layout.activity_pingate);
        ((TextView) v.findViewById(R.id.pin_error))
                .setText("محاولات كتير غلط - استنى شوية وجرب تاني");
        paparazzi.snapshot(v, "pin-gate-locked");
    }

    @Test
    public void pinGateDeviceUnlockOffered() {
        View v = paparazzi.inflate(R.layout.activity_pingate);
        v.findViewById(R.id.btn_device_lock).setVisibility(View.VISIBLE);
        paparazzi.snapshot(v, "pin-gate-device-unlock");
    }

    // ---- settings: populated picker + every section ----

    @Test
    public void launcherIcon() {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(paparazzi.getContext());
        frame.setBackgroundColor(0xFF1E7B34); // @color/call_green, the adaptive icon background
        android.widget.ImageView iv = new android.widget.ImageView(paparazzi.getContext());
        iv.setImageDrawable(paparazzi.getContext().getResources()
                .getDrawable(R.drawable.ic_mama_mark));
        int s = (int) (192 * paparazzi.getContext().getResources().getDisplayMetrics().density);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(s, s);
        lp.gravity = android.view.Gravity.CENTER;
        frame.addView(iv, lp);
        paparazzi.snapshot(frame, "launcher-icon");
    }


}