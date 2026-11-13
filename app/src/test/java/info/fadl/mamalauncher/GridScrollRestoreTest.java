package info.fadl.mamalauncher;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
/** T-13: scroll restore is by contact, not index. */
public class GridScrollRestoreTest {
 private List<ContactEntry> contacts(long... ids){
  List<ContactEntry> out=new ArrayList<>();
  for(long id:ids)out.add(new ContactEntry(id,"اسم"+id,"01000000"+String.format("%03d",id),null,false));
  return out;
 }
 @Test public void openedContactWinsAtItsNewPosition(){
  // pinned re-sort moved id 5 to the front; Back still shows id 5
  assertEquals(0,PhoneActivity.restorePosition(contacts(5,1,2,3,4),5L,3L));
 }
 @Test public void firstVisibleContactIsKeptWhenNothingWasOpened(){
  assertEquals(2,PhoneActivity.restorePosition(contacts(1,2,3,4),null,3L));
 }
 @Test public void missingContactsFallBackToTop(){
  assertEquals(0,PhoneActivity.restorePosition(contacts(1,2,3),9L,8L));
 }
 @Test public void openedContactMissingFallsBackToFirstVisible(){
  assertEquals(1,PhoneActivity.restorePosition(contacts(1,2,3),9L,2L));
 }
 @Test public void emptyListStaysAtTop(){
  assertEquals(0,PhoneActivity.restorePosition(new ArrayList<>(),1L,1L));
 }
}
