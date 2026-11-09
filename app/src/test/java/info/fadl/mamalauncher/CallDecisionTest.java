package info.fadl.mamalauncher;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import org.junit.Test;
import static org.junit.Assert.*;

public class CallDecisionTest {

    @Test
    public void grantedPlacesCall() {
        assertEquals(CallDecision.Action.PLACE_CALL,
                CallDecision.decide(CallDecision.PermissionState.GRANTED));
    }

    @Test
    public void askableRequestsPermission() {
        assertEquals(CallDecision.Action.REQUEST_PERMISSION,
                CallDecision.decide(CallDecision.PermissionState.CAN_REQUEST));
    }

    @Test
    public void blockedShowsSetupHelpAndNeverCalls() {
        assertEquals(CallDecision.Action.SHOW_SETUP_HELP,
                CallDecision.decide(CallDecision.PermissionState.BLOCKED));
    }

    @Test
    public void permissionGrantNeverAutoCalls() {
        // Regression guard: after the system dialog is granted, the only legal
        // next step is waiting for an explicit tap on the green button.
        assertEquals(CallDecision.PostResult.WAIT_FOR_EXPLICIT_TAP,
                CallDecision.afterPermissionResult(true));
        assertEquals(CallDecision.PostResult.SHOW_SETUP_HELP,
                CallDecision.afterPermissionResult(false));
    }
}
