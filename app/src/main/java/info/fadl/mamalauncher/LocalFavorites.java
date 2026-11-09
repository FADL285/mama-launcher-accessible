package info.fadl.mamalauncher;
import android.content.Context;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;
/** App-local number overrides. Never writes to the system contacts provider. */
final class LocalFavorites {
 private static String key(long id,String number){return "favorite."+id+"."+ContactsLoader.normalizeNumber(number);}
 static boolean contains(Context c,long id,String number,boolean systemDefault){return Prefs.get(c).getBoolean(key(id,number),systemDefault);}
 static void set(Context c,long id,String number,boolean value){Prefs.get(c).edit().putBoolean(key(id,number),value).apply();}
 static List<ContactEntry> apply(Context c,List<ContactEntry> contacts){
  List<ContactEntry> result=new ArrayList<>();
  for(ContactEntry e:contacts){boolean favorite=false;for(ContactEntry.PhoneNumber n:e.numbers)favorite|=contains(c,e.id,n.number,e.starred);
   result.add(new ContactEntry(e.id,e.name,e.numbers,e.photoUri,favorite));}
  return result;
 }
}
