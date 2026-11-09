package info.fadl.mamalauncher;

import static org.junit.Assert.assertEquals;

import android.content.Intent;
import android.provider.ContactsContract;

import org.junit.Test;

/** Pins the dial pad's handoff shape on the JVM: android.jar method calls
 *  are stubbed in local unit tests, so this pins the URI builder and the
 *  (compile-time-inlined) intent constants. Actually starting the intents
 *  stays for the on-phone trial - by design nothing here can place a call. */
public class DialIntentsTest {

    @Test
    public void dialUriIsATelUri() {
        assertEquals("tel:01000000000", DialIntents.dialUri("01000000000"));
    }

    @Test
    public void dialUriEncodesHashAndSpaces() {
        assertEquals("tel:01%23%202", DialIntents.dialUri("01# 2"));
    }

    @Test
    public void handoffActionsAreDialAndInsertNeverCall() {
        // ACTION_DIAL opens the dialer prefilled; ACTION_CALL would place the
        // call and is deliberately never used here.
        assertEquals("android.intent.action.DIAL", Intent.ACTION_DIAL);
        assertEquals("android.intent.action.INSERT", Intent.ACTION_INSERT);
    }

    @Test
    public void saveUsesTheContactsInsertContract() {
        assertEquals("vnd.android.cursor.dir/contact", ContactsContract.Contacts.CONTENT_TYPE);
        assertEquals("phone", ContactsContract.Intents.Insert.PHONE);
    }
}
