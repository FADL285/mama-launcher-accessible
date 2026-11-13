package info.fadl.mamalauncher;
import android.content.Context;
import android.content.SharedPreferences;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;
/** App-local contact pins (D-02: the star belongs to the contact, not a number).
 *  Never writes to the system contacts provider. */
final class LocalFavorites {
 private static final String SCHEMA="contact_star_schema";
 private static final int CURRENT_SCHEMA=2;
 private static String key(long id){return "contact_star."+id;}
 /** One-time key-only collapse of legacy per-number marks (favorite.<id>.<number>).
  *  For each contact id: any true -> pinned; else any false -> unpinned; orphan
  *  marks count as-is. Existing contact-level marks are never overwritten, legacy
  *  keys stay read-only, malformed keys are skipped, and re-runs are no-ops.
 *  The batch write's commit() result is deliberately not thrown on: a storage
 *  failure must never close Contacts or the confirmation screen. */
 static synchronized void migrateIfNeeded(Context c){
  migrateIfNeeded(Prefs.get(c));
 }
 /** The migration proper, on an explicit prefs handle so the failed-commit
  *  branch is testable. Runs on the class lock: the grid reads from a worker
  *  thread while confirmation toggles on the main thread. */
 private static boolean retryPending; // a commit() that reported disk failure
 private static Map<Long,Boolean> pendingMarks=new HashMap<>(); // our undurable writes
 static synchronized void migrateIfNeeded(SharedPreferences p){
  if(!retryPending && p.getInt(SCHEMA,1)>=CURRENT_SCHEMA)return;
  Map<Long,Boolean> decided=new HashMap<>();
  for(Map.Entry<String,?> e:p.getAll().entrySet()){
   String k=e.getKey();
   if(!k.startsWith("favorite.") || !(e.getValue() instanceof Boolean))continue;
   String rest=k.substring("favorite.".length());
   int dot=rest.indexOf('.');
   if(dot<=0)continue;
   long id;
   try{id=Long.parseLong(rest.substring(0,dot));}catch(NumberFormatException nfe){continue;}
   boolean mark=(Boolean)e.getValue();
   Boolean cur=decided.get(id);
   if(cur==null)decided.put(id,mark);
   else if(mark)decided.put(id,Boolean.TRUE);
  }
  SharedPreferences.Editor ed=p.edit();
  for(Map.Entry<Long,Boolean> e:decided.entrySet()){
   String k=key(e.getKey());
   if(!p.contains(k))ed.putBoolean(k,e.getValue());
   else if(retryPending && pendingMarks.containsKey(e.getKey())
     && p.getBoolean(k,false)==e.getValue())ed.putBoolean(k,e.getValue());
   // else: a pre-existing or user-written mark - migration never overwrites it.
   // The retry re-put only fires when the current value still equals what the
   // migration itself wrote, i.e. our own write that never reached disk.
  }
  // A failed commit must never crash the launcher. Android applies commit()
  // to memory before returning the disk result, so the migrated state stays
  // visible for this process; but the in-memory flag alone must not mark the
  // migration done - retryPending forces the next call to persist again.
  // The re-run is idempotent and never overwrites marks a user toggle wrote
  // meanwhile (the contains check above), so retrying is always safe.
  retryPending=!ed.putInt(SCHEMA,CURRENT_SCHEMA).commit();
  pendingMarks=decided;
 }
 static boolean contains(Context c,long id,boolean systemDefault){
  migrateIfNeeded(c);
  return Prefs.get(c).getBoolean(key(id),systemDefault);
 }
 static void set(Context c,long id,boolean value){
  migrateIfNeeded(c);
  Prefs.get(c).edit().putBoolean(key(id),value).apply();
 }
 static List<ContactEntry> apply(Context c,List<ContactEntry> contacts){
  migrateIfNeeded(c);
  List<ContactEntry> result=new ArrayList<>();
  for(ContactEntry e:contacts)result.add(new ContactEntry(e.id,e.name,e.numbers,e.photoUri,contains(c,e.id,e.starred)));
  return result;
 }
}
