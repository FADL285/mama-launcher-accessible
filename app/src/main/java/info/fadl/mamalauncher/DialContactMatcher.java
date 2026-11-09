package info.fadl.mamalauncher;

import android.telephony.PhoneNumberUtils;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;

/** Local-only number search. Never treats a suffix/short code as an exact match. */
final class DialContactMatcher {
 static String digits(String value) {
  if(value==null)return "";
  StringBuilder s=new StringBuilder();
  for(char c:value.toCharArray()) {
   int d=Character.digit(c,10);
   if(d>=0)s.append((char)('0'+d));
   else if(c=='+' && s.length()==0)s.append(c);
   else if(!Character.isWhitespace(c) && c!='-' && c!='(' && c!=')' && c!='.')return "";
  }
  return s.toString();
 }
 static boolean exact(String a,String b,String country) {
  String x=digits(a), y=digits(b);
  if(x.isEmpty() || y.isEmpty())return false;
  if(x.equals(y))return true;
  String ex=PhoneNumberUtils.formatNumberToE164(x,country),ey=PhoneNumberUtils.formatNumberToE164(y,country);
  return ex!=null && ex.equals(ey);
 }
 static List<ContactEntry> matches(List<ContactEntry> contacts,String typed,String country,boolean exactOnly) {
  List<ContactEntry> result=new ArrayList<>();String query=digits(typed);
  if(query.length()<3)return result;
  for(ContactEntry c:contacts) {
   List<ContactEntry.PhoneNumber> nums=new ArrayList<>();
   for(ContactEntry.PhoneNumber n:c.numbers)if(exact(typed,n.number,country) || (!exactOnly && digits(n.number).startsWith(query)))nums.add(n);
   if(!nums.isEmpty())result.add(new ContactEntry(c.id,c.name,nums,c.photoUri,c.starred));
  }
  result.sort((a,b)->Boolean.compare(!exact(typed,a.firstNumber(),country),!exact(typed,b.firstNumber(),country)));
  return result;
 }
}
