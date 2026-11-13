package info.fadl.mamalauncher;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
/** T-13 (owner decision 2026-11-13, option 1): scroll restore anchors on the
 *  first-visible contact by id, never the opened contact, never a raw index. */
public class GridScrollRestoreTest {
 private List<ContactEntry> contacts(long... ids){
  List<ContactEntry> out=new ArrayList<>();
  for(long id:ids)out.add(new ContactEntry(id,"اسم"+id,"01000000"+String.format("%03d",id),null,false));
  return out;
 }
 @Test public void firstVisibleContactIsKept(){
  assertEquals(2,PhoneActivity.restorePosition(contacts(1,2,3,4),3L));
 }
 @Test public void firstVisibleContactIsFoundAtItsResortedPosition(){
  // a pin re-sort moved id 5 to the front; the anchor still lands on it
  assertEquals(0,PhoneActivity.restorePosition(contacts(5,1,2,3,4),5L));
  // ... and an anchor pushed down by a re-sort is followed to its new row
  assertEquals(3,PhoneActivity.restorePosition(contacts(5,1,2,3,4),3L));
 }
 @Test public void missingContactFallsBackToTop(){
  assertEquals(0,PhoneActivity.restorePosition(contacts(1,2,3),9L));
 }
 @Test public void noAnchorStaysAtTop(){
  assertEquals(0,PhoneActivity.restorePosition(contacts(1,2,3),null));
 }
 @Test public void emptyListStaysAtTop(){
  assertEquals(0,PhoneActivity.restorePosition(new ArrayList<>(),1L));
 }
}
