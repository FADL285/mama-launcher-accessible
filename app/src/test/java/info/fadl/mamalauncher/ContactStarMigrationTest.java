package info.fadl.mamalauncher;
import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import static org.junit.Assert.*;
/** T-16 migration contract: key-only true-wins collapse of legacy per-number marks. */
@RunWith(RobolectricTestRunner.class)
public class ContactStarMigrationTest {
 private Context app;
 @Before public void reset(){app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();}
 private void legacy(long id,String number,boolean v){Prefs.get(app).edit().putBoolean("favorite."+id+"."+number,v).apply();}
 @Test public void unpinnedContactWithTrueMarkMigratesPinned(){
  legacy(1,"010",true);
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(LocalFavorites.contains(app,1,false));
 }
 @Test public void starredContactWithSingleFalseMarkMigratesUnpinned(){
  legacy(1,"010",false);
  LocalFavorites.migrateIfNeeded(app);
  assertFalse(LocalFavorites.contains(app,1,true));
 }
 @Test public void starredContactWithAllFalseMarksMigratesUnpinned(){
  legacy(1,"010",false);legacy(1,"011",false);
  LocalFavorites.migrateIfNeeded(app);
  assertFalse(LocalFavorites.contains(app,1,true));
 }
 @Test public void conflictingMarksTrueWins(){
  legacy(1,"010",true);legacy(1,"011",false);
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(LocalFavorites.contains(app,1,true));
 }
 @Test public void orphanMarksCountAsIs(){
  legacy(1,"099999",true); // number no longer on the contact: still a true mark
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(LocalFavorites.contains(app,1,false));
 }
 @Test public void mixedIdsMigrateIndependently(){
  legacy(1,"010",false);legacy(2,"010",true);legacy(3,"011",false);
  LocalFavorites.migrateIfNeeded(app);
  assertFalse(LocalFavorites.contains(app,1,true));
  assertTrue(LocalFavorites.contains(app,2,false));
  assertFalse(LocalFavorites.contains(app,3,true));
 }
 @Test public void migrationNeverOverwritesAnExistingContactMark(){
  Prefs.get(app).edit().putBoolean("contact_star.1",false).apply();
  legacy(1,"010",true);
  LocalFavorites.migrateIfNeeded(app);
  assertFalse(LocalFavorites.contains(app,1,true));
 }
 @Test public void malformedKeysAreSkippedNotFatal(){
  SharedPreferences p=Prefs.get(app);
  p.edit().putBoolean("favorite.",true).putBoolean("favorite.x.010",true).putString("favorite.2.010","yes").putBoolean("favorite.3.010",true).apply();
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(LocalFavorites.contains(app,3,false));
  assertFalse(p.contains("contact_star.2"));
  assertFalse(p.contains("contact_star.x"));
 }
 @Test public void migrationIsIdempotentAndKeepsLegacyKeys(){
  legacy(1,"010",true);
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(Prefs.get(app).contains("favorite.1.010")); // legacy kept read-only
  Prefs.get(app).edit().putBoolean("contact_star.1",false).apply(); // user later unpins
  LocalFavorites.migrateIfNeeded(app); // second run must not re-pin
  assertFalse(LocalFavorites.contains(app,1,true));
 }
 @Test public void schemaFlagMarksMigrationDone(){
  LocalFavorites.migrateIfNeeded(app);
  assertEquals(2,Prefs.get(app).getInt("contact_star_schema",0));
 }
 @Test public void emptyAndSymbolSuffixesCount(){
  legacy(7,"",true);
  legacy(8,"+",false);
  SharedPreferences p=Prefs.get(app);
  p.edit().putBoolean("favorite.7.",true).putBoolean("favorite.8.+",false).apply();
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(LocalFavorites.contains(app,7,false));
  assertFalse(LocalFavorites.contains(app,8,true));
 }
 @Test public void unicodeDigitSuffixCounts(){
  SharedPreferences p=Prefs.get(app);
  p.edit().putBoolean("favorite.9.\u0660\u0661\u0660",true).apply(); // Arabic-Indic digits
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(LocalFavorites.contains(app,9,false));
 }
 @Test public void userToggleDuringMigrationWindowWins(){
  // A contact-level mark written before migration completes is never overwritten.
  legacy(5,"010",true);
  Prefs.get(app).edit().putBoolean("contact_star.5",false).apply(); // user unpinned just now
  LocalFavorites.migrateIfNeeded(app);
  assertFalse(LocalFavorites.contains(app,5,true));
 }
 @Test public void concurrentFirstCallersMigrateOnce() throws Exception {
  legacy(6,"010",true);
  final boolean[] seen=new boolean[2];
  Thread a=new Thread(){public void run(){seen[0]=LocalFavorites.contains(app,6,false);}};
  Thread b=new Thread(){public void run(){seen[1]=LocalFavorites.contains(app,6,false);}};
  a.start();b.start();a.join();b.join();
  assertTrue(seen[0]);assertTrue(seen[1]);
  assertEquals(2,Prefs.get(app).getInt("contact_star_schema",0));
 }
 @Test public void threeNumberCl08ShapeUnpins(){
  // group (1b): starred, two numbers un-starred, one unmarked, no orphan
  legacy(1,"010",false);legacy(1,"011",false);
  LocalFavorites.migrateIfNeeded(app);
  assertFalse(LocalFavorites.contains(app,1,true));
 }
 @Test public void orphanFalseWithCurrentFalseUnpins(){
  // group (2b): starred, current false + unmarked, plus an orphan false
  legacy(1,"010",false);legacy(1,"0999",false);
  LocalFavorites.migrateIfNeeded(app);
  assertFalse(LocalFavorites.contains(app,1,true));
 }
 @Test public void starredAllUnstarredPlusOrphanTrueRepins(){
  // group (3b): the most surprising accepted case - every current number
  // un-starred, and an orphan true brings the pin back (true wins).
  legacy(1,"010",false);legacy(1,"011",false);legacy(1,"0999",true);
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(LocalFavorites.contains(app,1,true));
 }
 @Test public void orphanTrueOnAnAlreadyPinnedContactChangesNothing(){
  // control: visibly pinned today (starred, unmarked numbers) + orphan true
  legacy(1,"0999",true);
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(LocalFavorites.contains(app,1,true));
 }
 private static SharedPreferences.Editor memoryAppliedDiskFailedEditor(final SharedPreferences real,final boolean[] fail){
  final SharedPreferences.Editor e=real.edit();
  return (SharedPreferences.Editor)java.lang.reflect.Proxy.newProxyInstance(
   ContactStarMigrationTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},
   (proxy,m,args)->{
    // AOSP semantics: commit() applies to memory first, THEN reports the disk
    // result - a failed disk write does not roll back the in-memory change.
    if(m.getName().equals("commit")&&fail[0]){fail[0]=false;e.commit();return false;}
    Object r=m.invoke(e,args);
    return r==e?proxy:r; // keep the fluent chain on the proxy
   });
 }
 // A prefs fake with separate in-memory and durable maps, so the AOSP
 // memory-first/disk-failed commit shape is directly testable.
 private static SharedPreferences layeredPrefs(java.util.Map<String,Object> mem,java.util.Map<String,Object> disk,boolean[] failNext){
  return (SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(
   ContactStarMigrationTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},
   (proxy,m,args)->{
    switch(m.getName()){
     case "getAll":return new java.util.HashMap<>(mem);
     case "getInt":return mem.containsKey((String)args[0])?(int)(Integer)mem.get((String)args[0]):(int)(Integer)args[1];
     case "getBoolean":return mem.containsKey((String)args[0])?(boolean)(Boolean)mem.get((String)args[0]):(boolean)(Boolean)args[1];
     case "contains":return mem.containsKey((String)args[0]);
     case "edit":return layeredEditor(mem,disk,failNext);
     default:throw new UnsupportedOperationException(m.getName());
    }
   });
 }
 private static SharedPreferences.Editor layeredEditor(java.util.Map<String,Object> mem,java.util.Map<String,Object> disk,boolean[] failNext){
  java.util.Map<String,Object> staged=new java.util.HashMap<>();
  return (SharedPreferences.Editor)java.lang.reflect.Proxy.newProxyInstance(
   ContactStarMigrationTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},
   (proxy,m,args)->{
    switch(m.getName()){
     case "putBoolean":staged.put((String)args[0],args[1]);return proxy;
     case "putInt":staged.put((String)args[0],args[1]);return proxy;
     case "remove":mem.remove((String)args[0]);return proxy;
     case "commit":
      // AOSP order: memory first, then the disk result.
      mem.putAll(staged);
      if(failNext[0]){failNext[0]=false;return false;}
      disk.putAll(staged);return true;
     case "apply":mem.putAll(staged);disk.putAll(staged);return null;
     default:throw new UnsupportedOperationException(m.getName());
    }
   });
 }
 @Test public void memoryUpdatedDiskFailedCommitRetriesInProcessAndKeepsUserMarks(){
  java.util.Map<String,Object> disk=new java.util.HashMap<>();
  disk.put("favorite.1.010",Boolean.TRUE);   // legacy mark for contact 1
  disk.put("favorite.2.010",Boolean.TRUE);   // legacy mark for contact 2
  java.util.Map<String,Object> mem=new java.util.HashMap<>(disk);
  boolean[] fail={true};
  SharedPreferences p=layeredPrefs(mem,disk,fail);
  LocalFavorites.migrateIfNeeded(p); // no throw
  assertTrue("memory-first: migrated view visible",mem.containsKey("contact_star.1"));
  assertEquals(2,mem.get("contact_star_schema"));
  assertFalse("disk lost the write",disk.containsKey("contact_star_schema"));
  // The user toggles contact 2 while the migration's disk write is pending -
  // the retry must never overwrite it with the legacy-derived value.
  p.edit().putBoolean("contact_star.2",Boolean.FALSE).apply();
  LocalFavorites.migrateIfNeeded(p); // retryPending forces the persistence retry
  assertEquals(2,disk.get("contact_star_schema"));
  assertEquals(Boolean.TRUE,disk.get("contact_star.1"));
  assertEquals(Boolean.FALSE,disk.get("contact_star.2")); // the user's mark wins
  // Restart from durable state: schema flag present, nothing re-runs, marks kept.
  java.util.Map<String,Object> mem2=new java.util.HashMap<>(disk);
  LocalFavorites.migrateIfNeeded(layeredPrefs(mem2,disk,new boolean[]{false}));
  assertEquals(Boolean.FALSE,disk.get("contact_star.2"));
 }
 @Test public void failedCommitNeverCrashesAndStaysConsistent(){
  legacy(1,"010",true);
  final SharedPreferences real=Prefs.get(app);
  final boolean[] fail={true};
  SharedPreferences fake=(SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(
   ContactStarMigrationTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},
   (proxy,m,args)->{
    if(m.getName().equals("edit"))return memoryAppliedDiskFailedEditor(real,fail);
    return m.invoke(real,args);
   });
  LocalFavorites.migrateIfNeeded(fake); // must NOT throw: a storage failure never closes the app
  assertTrue("in-memory migration is visible this process",LocalFavorites.contains(app,1,false));
  assertEquals(2,real.getInt("contact_star_schema",0)); // memory applied, disk result false
  LocalFavorites.migrateIfNeeded(app); // second run is a no-op, never a re-write storm
  assertTrue(LocalFavorites.contains(app,1,false));
  // Restart recovery: a start where the flag WAS lost re-runs the migration
  // from the kept legacy keys - safe because it is idempotent and never-overwriting.
  real.edit().remove("contact_star_schema").remove("contact_star.1").apply();
  LocalFavorites.migrateIfNeeded(app);
  assertTrue(LocalFavorites.contains(app,1,false));
 }
}
