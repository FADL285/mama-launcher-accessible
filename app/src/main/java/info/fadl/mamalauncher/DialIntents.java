package info.fadl.mamalauncher;

import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;

/** The dial pad's handoffs. The URI building is pure JVM (pinned by
 *  DialIntentsTest without ever starting an activity); the Intent wrappers
 *  are one-liners the activity calls on the device. Direct calling needs CALL_PHONE and a deliberate press. The
 *  contacts insert UI does the saving itself without a write permission. */
public final class DialIntents {

    private DialIntents() {}

    /** tel: URI for the system dialer; pure so tests pin the encoding. */
    public static String dialUri(String number) {
        try {
            return "tel:" + java.net.URLEncoder.encode(number, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Opens the system dialer with the number composed; never calls by itself. */
    public static Intent dial(String number) {
        return new Intent(Intent.ACTION_DIAL, Uri.parse(dialUri(number)));
    }

    /** Direct call, only behind DirectCallAction's explicit press/permission gate. */
    public static Intent call(String number) {
        return new Intent(Intent.ACTION_CALL, Uri.parse(dialUri(number)));
    }

    /** Opens the system's normal add-contact screen with the number prefilled. */
    public static Intent saveAsContact(String number) {
        Intent insert = new Intent(Intent.ACTION_INSERT);
        insert.setType(ContactsContract.Contacts.CONTENT_TYPE);
        insert.putExtra(ContactsContract.Intents.Insert.PHONE, number);
        return insert;
    }
}
