package info.fadl.mamalauncher;
import info.fadl.mamalauncher.ui.ContactEntry;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import static org.junit.Assert.*;
/** T-17: national/international forms of one number collapse to one button;
 *  the survivor follows a fixed, provider-order-independent rule. */
@RunWith(RobolectricTestRunner.class)
public class ContactsDedupFormsTest {
 private static final String EG="EG";
 private ContactsLoader.RawRow row(long id,String name,String number,String label){
  return new ContactsLoader.RawRow(id,name,number,label,null,false);
 }
 private ContactEntry only(List<ContactEntry> out){assertEquals(1,out.size());return out.get(0);}
 @Test public void threeFormsCollapseToOneButton(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","01001234567","موبايل"),
   row(1,"ماما","+201001234567","موبايل"),
   row(1,"ماما","0100 123 4567","موبايل")),EG);
  assertEquals(1,only(out).numbers.size());
 }
 @Test public void survivorIsTheNationalFormInEitherProviderOrder(){
  List<ContactEntry> a=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","01001234567","موبايل"),row(1,"ماما","+201001234567","بيت")),EG);
  List<ContactEntry> b=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","+201001234567","بيت"),row(1,"ماما","01001234567","موبايل")),EG);
  assertEquals("01001234567",only(a).numbers.get(0).number);
  assertEquals("01001234567",only(b).numbers.get(0).number);
  assertEquals("survivor keeps its own label","موبايل",only(a).numbers.get(0).label);
  assertEquals("موبايل",only(b).numbers.get(0).label);
 }
 @Test public void genuinelyDifferentNumbersAreUntouched(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","01001234567","موبايل"),row(1,"ماما","01234567890","بيت")),EG);
  assertEquals(2,only(out).numbers.size());
 }
 @Test public void shortNumbersKeepDigitsOnlyBehaviour(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","12345","خدمة"),row(1,"ماما","+2012345","خدمة")),EG);
  assertEquals(2,only(out).numbers.size());
 }
 @Test public void unresolvableRegionNeverMerges(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","01001234567","موبايل"),row(1,"ماما","+201001234567","موبايل")),"");
  assertEquals(2,only(out).numbers.size());
 }
 @Test public void serviceStringsAreNeverEquated(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","*#06#","خدمة"),row(1,"ماما","01001234567","موبايل")),EG);
  assertEquals(2,only(out).numbers.size());
 }
 @Test public void unicodeDigitFormsCollapse(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","٠١٠٠١٢٣٤٥٦٧","موبايل"),row(1,"ماما","01001234567","موبايل")),EG);
  assertEquals(1,only(out).numbers.size());
 }
 @Test public void twoEquivalentPairsOnOneContactCollapseIndependently(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","01001234567","موبايل"),row(1,"ماما","+201001234567","موبايل"),
   row(1,"ماما","01234567890","بيت"),row(1,"ماما","+201234567890","بيت")),EG);
  assertEquals(2,only(out).numbers.size());
 }
 @Test public void crossContactNumbersStaySeparate(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","01001234567","موبايل"),row(2,"خالة","+201001234567","موبايل")),EG);
  assertEquals(2,out.size());
  assertEquals(1,out.get(0).numbers.size());
  assertEquals(1,out.get(1).numbers.size());
 }
 @Test public void allSixPermutationsPickTheSameNationalSurvivor(){
  String[][] perms={
   {"01001234567","+201001234567","0100 123 4567"},
   {"01001234567","0100 123 4567","+201001234567"},
   {"+201001234567","01001234567","0100 123 4567"},
   {"+201001234567","0100 123 4567","01001234567"},
   {"0100 123 4567","01001234567","+201001234567"},
   {"0100 123 4567","+201001234567","01001234567"}};
  String first=null;
  for(String[] p:perms){
   List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
    row(1,"ماما",p[0],"موبايل"),row(1,"ماما",p[1],"موبايل"),row(1,"ماما",p[2],"موبايل")),EG);
   assertEquals(1,only(out).numbers.size());
   String shown=only(out).numbers.get(0).number;
   if(first==null)first=shown;
   assertEquals("provider order must not change the survivor",first,shown);
  }
 }
 @Test public void iddFormLosesToTheNationalForm(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","00201001234567","موبايل"),row(1,"ماما","01001234567","موبايل")),EG);
  assertEquals(1,only(out).numbers.size());
  assertEquals("01001234567",only(out).numbers.get(0).number);
 }
 @Test public void identicalNumberDifferentLabelsPickDeterministically(){
  List<ContactEntry> a=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","01001234567","موبايل"),row(1,"ماما","01001234567","بيت")),EG);
  List<ContactEntry> b=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","01001234567","بيت"),row(1,"ماما","01001234567","موبايل")),EG);
  assertEquals(1,only(a).numbers.size());
  assertEquals(only(a).numbers.get(0).label,only(b).numbers.get(0).label);
  assertEquals("بيت",only(a).numbers.get(0).label); // lexicographic tie-break
 }
 @Test public void knownNonEgRegionDoesNotMergeEgyptianForms(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","01001234567","موبايل"),row(1,"ماما","+201001234567","موبايل")),"US");
  assertEquals(2,only(out).numbers.size());
 }
 @Test public void unicodeDigitsMergeEvenWithUnknownRegion(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","٠١٠٠١٢٣٤٥٦٧","موبايل"),row(1,"ماما","01001234567","موبايل")),"");
  assertEquals(1,only(out).numbers.size());
  assertEquals("01001234567",only(out).numbers.get(0).number); // ASCII survives
 }
 @Test public void plainNumberAndSameDigitServiceStringStaySeparate(){
  // v12 row: 123 (ambulance) and *123# are different dial targets.
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","123","خدمة"),row(1,"ماما","*123#","خدمة")),EG);
  assertEquals(2,only(out).numbers.size());
  assertEquals("123",only(out).numbers.get(0).number);
  assertEquals("*123#",only(out).numbers.get(1).number);
 }
 @Test public void plainNumberAndSameDigitServiceStringStaySeparateReversed(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","*123#","خدمة"),row(1,"ماما","123","خدمة")),EG);
  assertEquals(2,only(out).numbers.size());
  assertEquals("*123#",only(out).numbers.get(0).number);
  assertEquals("123",only(out).numbers.get(1).number);
 }
 @Test public void sameDigitServiceStringsWithDifferentMarksStaySeparate(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","*123#","خدمة"),row(1,"ماما","#123#","خدمة")),EG);
  assertEquals(2,only(out).numbers.size());
 }
 @Test public void identicalServiceStringsStillCollapse(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","*123#","خدمة"),row(1,"ماما","*123#","خدمة")),EG);
  assertEquals(1,only(out).numbers.size());
 }
 @Test public void distinctServiceStringsStaySeparate(){
  List<ContactEntry> out=ContactsLoader.group(Arrays.asList(
   row(1,"ماما","*#06#","خدمة"),row(1,"ماما","*#07#","خدمة")),EG);
  assertEquals(2,only(out).numbers.size());
 }
}
