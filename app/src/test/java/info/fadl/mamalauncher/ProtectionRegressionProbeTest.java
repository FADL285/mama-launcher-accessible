package info.fadl.mamalauncher;

import android.app.Activity;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

/** Regression coverage for replacement cancellation and background authentication. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class ProtectionRegressionProbeTest {
    private Context app;
    @Before public void reset() {
        app=RuntimeEnvironment.getApplication();
        Prefs.get(app).edit().clear()
            .putString(Prefs.PIN_SALT,"audit-synthetic-salt")
            .putString(Prefs.PIN_HASH,PinLock.hash("audit-synthetic-salt","9999"))
            .putInt(Prefs.PIN_FAILURES,1).apply();
        SessionGate.lock();
    }
    @Test public void recoveryMustKeepExistingPinUntilReplacementConfirmed() {
        PinGateActivity a=Robolectric.buildActivity(PinGateActivity.class,
            PinGateActivity.intentForEntry(app)).setup().get();
        a.onActivityResult(51,Activity.RESULT_OK,null);
        a.finish(); // no replacement chosen
        assertTrue("abandoned recovery must not erase the existing PIN",Prefs.hasPin(app));
    }
    @Test public void backgroundedChangePinMustNotResumeInSetupMode() {
        SessionGate.unlock();
        ActivityController<PinGateActivity> c=Robolectric.buildActivity(PinGateActivity.class,
            PinGateActivity.intentForSetup(app)).setup();
        c.pause().stop();
        SessionGate.lock(); // Settings leaving has locked the session
        c.restart().resume();
        android.widget.TextView subtitle=c.get().findViewById(R.id.pin_subtitle);
        assertNotEquals("return from background must require authentication, not a new PIN",
            c.get().getString(R.string.pingate_set_new),subtitle.getText().toString());
    }

    @Test public void staleSetupIntentAfterProcessDeathRequiresExistingPin() {
        SessionGate.lock();
        PinGateActivity a=Robolectric.buildActivity(PinGateActivity.class,
            PinGateActivity.intentForSetup(app)).setup().get();
        assertEquals(a.getString(R.string.pingate_enter),
            ((android.widget.TextView)a.findViewById(R.id.pin_subtitle)).getText().toString());
        assertTrue(Prefs.hasPin(app));
    }
    @Test public void firstSetupStillWorksAfterBackgrounding() {
        Prefs.get(app).edit().clear().apply();
        ActivityController<PinGateActivity> c=Robolectric.buildActivity(PinGateActivity.class,
            PinGateActivity.intentForEntry(app)).setup();
        c.pause().stop().restart().resume();
        assertEquals(c.get().getString(R.string.pingate_set_new),
            ((android.widget.TextView)c.get().findViewById(R.id.pin_subtitle)).getText().toString());
    }

    @Test public void discoveryUsesTheNewestSavedSelectionNotAnOldCapture() {
        SessionGate.unlock();
        ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup();
        Prefs.get(app).edit().putString(AppSelection.PREF_KEY,"new.app,old.app").apply();
        java.util.List<String[]> apps=new java.util.ArrayList<>();
        apps.add(new String[]{"old.app","Old"});
        apps.add(new String[]{"new.app","New"});
        c.get().applyDiscoveredApps(apps);
        assertEquals("new.app,old.app",Prefs.get(app).getString(AppSelection.PREF_KEY,null));
        c.pause().stop().destroy();
        Prefs.get(app).edit().putString(AppSelection.PREF_KEY,"new.app").apply();
        c.get().applyDiscoveredApps(apps);
        assertEquals("new.app",Prefs.get(app).getString(AppSelection.PREF_KEY,null));
    }
    @Test public void delayedAppListIsBelowStaticControlsAndCannotPushThemAway() {
        SessionGate.unlock();
        SettingsActivity a=Robolectric.buildActivity(SettingsActivity.class).setup().get();
        android.view.View picker=a.findViewById(R.id.settings_apps_list);
        android.view.View sound=a.findViewById(R.id.switch_auto_speak);
        android.view.View pin=a.findViewById(R.id.btn_change_pin);
        int[] before=new int[2]; sound.getLocationOnScreen(before);
        java.util.List<String[]> apps=new java.util.ArrayList<>();
        for(int n=0;n<30;n++) apps.add(new String[]{"fake.app"+n,"App "+n});
        a.applyDiscoveredApps(apps);
        android.view.ViewGroup content=(android.view.ViewGroup)picker.getParent();
        android.view.View pinSection=pin;
        while(pinSection.getParent()!=content) pinSection=(android.view.View)pinSection.getParent();
        assertTrue(content.indexOfChild(picker)>content.indexOfChild(pinSection));
        int[] after=new int[2];sound.getLocationOnScreen(after);
        assertArrayEquals(before,after);
    }

    private android.view.View key(android.view.View view,String digit) {
        if(digit.equals(view.getTag())) return view;
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int n=0;n<group.getChildCount();n++) {
                android.view.View found=key(group.getChildAt(n),digit);
                if(found!=null)return found;
            }
        }
        return null;
    }
    @Test public void recoveryReplacesPinOnlyAfterMatchingDoubleEntry() {
        PinGateActivity a=Robolectric.buildActivity(PinGateActivity.class,
            PinGateActivity.intentForEntry(app)).setup().get();
        String old=Prefs.get(app).getString(Prefs.PIN_HASH,null);
        a.onActivityResult(51,Activity.RESULT_OK,null);
        for(char digit:"2468".toCharArray())key(a.findViewById(R.id.pin_pad),""+digit).performClick();
        assertEquals(old,Prefs.get(app).getString(Prefs.PIN_HASH,null));
        for(char digit:"2468".toCharArray())key(a.findViewById(R.id.pin_pad),""+digit).performClick();
        String salt=Prefs.get(app).getString(Prefs.PIN_SALT,"");
        String hash=Prefs.get(app).getString(Prefs.PIN_HASH,null);
        assertTrue(PinLock.verify(salt,"2468",hash));
        assertFalse(PinLock.verify(salt,"9999",hash));
        assertTrue(SessionGate.isValid());
    }
}
