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
  if(x.replace("+","").length()<7 || y.replace("+","").length()<7)return false;
  String ex=PhoneNumberUtils.formatNumberToE164(x,country),ey=PhoneNumberUtils.formatNumberToE164(y,country);
  return ex!=null && ex.equals(ey);
 }
 /** Use saved-number national/international forms for suggestions, not exact identity. */
 static boolean prefix(String saved,String typed,String country) {
  String query=digits(typed), raw=digits(saved);
  if(query.isEmpty())return false;
  if(raw.startsWith(query))return true;
  String e164=PhoneNumberUtils.formatNumberToE164(raw,country);
  if(e164==null)return false;
  if(e164.startsWith(query) || ("00"+e164.substring(1)).startsWith(query))return true;
  // Egyptian national trunk prefix is only used for suggestions. This does
  // not assert exact identity, which still requires full E.164 validation.
  if("EG".equals(country) && e164.startsWith("+20") && ("0"+e164.substring(3)).startsWith(query))return true;
  return false;
 }
 static List<ContactEntry> matches(List<ContactEntry> contacts,String typed,String country,boolean exactOnly) {
  List<ContactEntry> result=new ArrayList<>();String query=digits(typed);
  if(query.length()<3)return result;
  for(ContactEntry c:contacts) {
   List<ContactEntry.PhoneNumber> nums=new ArrayList<>();
   for(ContactEntry.PhoneNumber n:c.numbers)if(exact(typed,n.number,country) || (!exactOnly && prefix(n.number,typed,country)))nums.add(n);
   if(!nums.isEmpty())result.add(new ContactEntry(c.id,c.name,nums,c.photoUri,c.starred));
  }
  result.sort((a,b)->Boolean.compare(!exact(typed,a.firstNumber(),country),!exact(typed,b.firstNumber(),country)));
  return result;
 }
}
