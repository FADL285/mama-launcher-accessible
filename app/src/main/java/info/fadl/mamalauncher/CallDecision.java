package info.fadl.mamalauncher;

/**
 * What the green button is allowed to do (D1: the call starts ONLY from this
 * button, and never without the CALL_PHONE permission). Pure logic so it is
 * unit-testable off-device.
 */
public final class CallDecision {
    public enum PermissionState { GRANTED, CAN_REQUEST, BLOCKED }
    public enum Action { PLACE_CALL, REQUEST_PERMISSION, SHOW_SETUP_HELP }

    /** After a permission dialog closes: a GRANT must never place a call on its
     *  own. The call still requires an explicit next tap on the green button. */
    public enum PostResult { WAIT_FOR_EXPLICIT_TAP, SHOW_SETUP_HELP }

    private CallDecision() {}

    public static Action decide(PermissionState state) {
        switch (state) {
            case GRANTED: return Action.PLACE_CALL;
            case CAN_REQUEST: return Action.REQUEST_PERMISSION;
            default: return Action.SHOW_SETUP_HELP;
        }
    }

    public static PostResult afterPermissionResult(boolean granted) {
        return granted ? PostResult.WAIT_FOR_EXPLICIT_TAP : PostResult.SHOW_SETUP_HELP;
    }
}
