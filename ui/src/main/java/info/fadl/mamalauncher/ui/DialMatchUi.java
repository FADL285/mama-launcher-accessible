package info.fadl.mamalauncher.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;

/** A phone number is a separate LTR run inside an otherwise RTL contact label.
 * An isolated LTR override keeps grouped Arabic and Latin digits, spaces, plus
 * and punctuation in their original order.
 * Only display text is wrapped: stored numbers and call/insert intents stay raw.
 */
public final class DialMatchUi {
 private DialMatchUi() {}
 public static String isolatedNumber(String number) {return "\u2066\u202D"+number+"\u202C\u2069";}
 public static String chooserLabel(ContactEntry contact) {return contact.name+"\n"+isolatedNumber(contact.firstNumber());}
 public static Button preview(Context context,ContactEntry contact,boolean saved) {
  Button row=new Button(context);
  row.setText((saved?context.getString(R.string.dial_saved):context.getString(R.string.dial_suggestion))+" · "+contact.name+"\n"+isolatedNumber(contact.firstNumber()));
  row.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
  row.setTextSize(18);row.setAllCaps(false);
  row.setMinHeight((int)(64*context.getResources().getDisplayMetrics().density));
  return row;
 }
}
