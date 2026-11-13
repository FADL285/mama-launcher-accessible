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
 private static SharedPreferences.Editor failOnceEditor(final SharedPreferences real,final boolean[] fail){
  final SharedPreferences.Editor e=real.edit();
  return (SharedPreferences.Editor)java.lang.reflect.Proxy.newProxyInstance(
   ContactStarMigrationTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},
   (proxy,m,args)->{
    if(m.getName().equals("commit")&&fail[0]){fail[0]=false;return false;}
    Object r=m.invoke(e,args);
    return r==e?proxy:r; // keep the fluent chain on the proxy
   });
 }
 @Test public void failedCommitLeavesMigrationIncompleteAndRetries(){
  legacy(1,"010",true);
  final SharedPreferences real=Prefs.get(app);
  final boolean[] fail={true};
  SharedPreferences fake=(SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(
   ContactStarMigrationTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},
   (proxy,m,args)->{
    if(m.getName().equals("edit"))return failOnceEditor(real,fail);
    return m.invoke(real,args);
   });
  try{LocalFavorites.migrateIfNeeded(fake);fail("must throw on a failed commit");}
  catch(IllegalStateException expected){}
  assertEquals("schema flag must not persist",0,real.getInt("contact_star_schema",0));
  assertFalse("no contact key persists",real.contains("contact_star.1"));
  LocalFavorites.migrateIfNeeded(app); // retry on real storage succeeds
  assertTrue(LocalFavorites.contains(app,1,false));
 }
}
