package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.GridView;
import android.widget.TextView;

import app.cash.paparazzi.Paparazzi;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Renders the REAL layouts to PNGs via layoutlib (no emulator) as visual
 *  evidence for the pilot review, on an Arabic RTL device configuration.
 *  All fixtures are fictional. */
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
    public void homeGrid() {
        View v = paparazzi.inflate(R.layout.activity_main);
        v.findViewById(R.id.setup_view).setVisibility(View.GONE);
        v.findViewById(R.id.home_view).setVisibility(View.VISIBLE);
        GridView grid = v.findViewById(R.id.contacts_grid);
        List<ContactEntry> contacts = new ArrayList<>();
        contacts.add(new ContactEntry(1, "أحمد", "01000000001", null, true));
        contacts.add(new ContactEntry(2, "منى", "01000000002", null, true));
        contacts.add(new ContactEntry(3, "خالد", "01000000003", null, false));
        contacts.add(new ContactEntry(4, "سارة", "01000000004", null, false));
        contacts.add(new ContactEntry(5, "محمود", "01000000005", null, false));
        contacts.add(new ContactEntry(6, "هالة", "01000000006", null, false));
        grid.setAdapter(new ContactGridAdapter(v.getContext(), contacts,
                new ContactPhotoLoader(null)));
        paparazzi.snapshot(v, "home-grid");
    }

    @Test
    public void confirmSingleNumber() {
        View v = paparazzi.inflate(R.layout.activity_confirm);
        bindConfirmCommon(v, "أحمد", 0xFF2F5D8A);
        UiBinder.bindNumbers(v, nums("01000000001", "موبايل"), n -> { });
        v.findViewById(R.id.btn_call).setEnabled(true);
        paparazzi.snapshot(v, "confirm-single");
    }

    @Test
    public void confirmMultiNumberNothingCallableUntilChosen() {
        View v = paparazzi.inflate(R.layout.activity_confirm);
        bindConfirmCommon(v, "منى سامي", 0xFF8A2F4E);
        UiBinder.bindNumbers(v, nums("01000000001", "موبايل", "0200000002", "البيت"), n -> { });
        v.findViewById(R.id.btn_call).setEnabled(false); // until a number is tapped
        paparazzi.snapshot(v, "confirm-multi-unselected");
    }

    @Test
    public void confirmMultiNumberSelected() {
        View v = paparazzi.inflate(R.layout.activity_confirm);
        bindConfirmCommon(v, "منى سامي", 0xFF8A2F4E);
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

    @Test
    public void setupScreen() {
        View v = paparazzi.inflate(R.layout.activity_main);
        v.findViewById(R.id.setup_view).setVisibility(View.VISIBLE);
        v.findViewById(R.id.home_view).setVisibility(View.GONE);
        paparazzi.snapshot(v, "setup-screen");
    }
}
