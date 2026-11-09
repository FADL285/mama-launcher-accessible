package info.fadl.mamalauncher;

import android.content.*;
import android.database.*;
import android.graphics.Rect;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import info.fadl.mamalauncher.ui.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Coordinate dispatch across delayed lookup transitions, not performClick-only tests. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=33,qualifiers="ar-rEG-w411dp-h820dp")
public class StableDialpadTest {
 int availableHeight=780;
 android.app.Application app;
 @Before public void reset(){app=RuntimeEnvironment.getApplication();Prefs.get(app).edit().clear().apply();SessionGate.unlock();Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS);}
 View key(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=key(((ViewGroup)v).getChildAt(i),tag);if(r!=null)return r;}return null;}
 void layout(View root){float d=root.getResources().getDisplayMetrics().density;root.measure(View.MeasureSpec.makeMeasureSpec((int)(411*d),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((int)(availableHeight*d),View.MeasureSpec.EXACTLY));root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());}
 Rect bounds(ViewGroup root,View child){Rect r=new Rect(child.getScrollX(),child.getScrollY(),child.getScrollX()+child.getWidth(),child.getScrollY()+child.getHeight());root.offsetDescendantRectToMyCoords(child,r);return r;}
 void event(View root,Rect r,int action,long down,long time){MotionEvent e=MotionEvent.obtain(down,time,action,r.centerX(),r.centerY(),0);assertTrue("touch dispatched at "+r,root.dispatchTouchEvent(e));e.recycle();if(action==MotionEvent.ACTION_UP)Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void tap(View root,Rect r){layout(root);long t=SystemClock.uptimeMillis();event(root,r,MotionEvent.ACTION_DOWN,t,t);event(root,r,MotionEvent.ACTION_UP,t,t+20);}
 void settle(DialpadActivity a)throws Exception {for(int i=0;i<3;i++){a.awaitLookupsForTest();Shadows.shadowOf(Looper.getMainLooper()).idle();}}
 void stable(int font,boolean contrast)throws Exception {
  Prefs.get(app).edit().putInt(Prefs.FONT_SCALE,font).putBoolean(Prefs.HIGH_CONTRAST,contrast).putBoolean(Prefs.TOUCH_GUARD,false).apply();
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.ContactsContract.AUTHORITY,new FixtureContactsProvider(){@Override public Cursor query(Uri uri,String[] p,String selection,String[] args,String sort){entered.countDown();try{if(!release.await(4,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){throw new RuntimeException(e);}MatrixCursor c=new MatrixCursor(p);for(int i=0;i<3;i++)c.addRow(new Object[]{i+1,"اسم تجريبي طويل لنتيجة البحث رقم "+i,"011 111 1111",2,null,null,0});return c;}});
  org.robolectric.android.controller.ActivityController<DialpadActivity> controller=Robolectric.buildActivity(DialpadActivity.class).setup().visible();DialpadActivity a=controller.get();assertTrue(entered.await(2,TimeUnit.SECONDS));ViewGroup root=(ViewGroup)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);layout(root);
  Map<String,Rect> initial=new LinkedHashMap<>();for(String digit:new String[]{"0","1","2","3","4","5","6","7","8","9","*","#"})initial.put(digit,bounds(root,key(root,digit)));
  Rect one=initial.get("1");if(a.findViewById(R.id.dial_keys_scroll)==null)tap(root,initial.get("0"));else key(root,"0").performClick();tap(root,one);layout(root);assertEquals("01",((TextView)a.findViewById(R.id.dial_display)).getText().toString());
  tap(root,one);layout(root);assertEquals(a.getString(R.string.dial_lookup_loading),((TextView)((LinearLayout)a.findViewById(R.id.dial_matches)).getChildAt(0)).getText().toString());assertEquals(one,bounds(root,key(root,"1")));
  long t=SystemClock.uptimeMillis();event(root,one,MotionEvent.ACTION_DOWN,t,t);release.countDown();settle(a);layout(root);
  LinearLayout matches=a.findViewById(R.id.dial_matches);assertEquals(2,matches.getChildCount());assertTrue(matches.getChildAt(0) instanceof Button);
  for(Map.Entry<String,Rect> entry:initial.entrySet())assertEquals(entry.getValue(),bounds(root,key(root,entry.getKey())));
  event(root,one,MotionEvent.ACTION_UP,t,t+40);settle(a);layout(root);assertEquals("0111",((TextView)a.findViewById(R.id.dial_display)).getText().toString());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
  for(int i=0;i<6;i++){tap(root,one);settle(a);layout(root);assertEquals(one,bounds(root,key(root,"1")));assertNull(Shadows.shadowOf(a).getNextStartedActivity());}
  assertEquals("0111111111",((TextView)a.findViewById(R.id.dial_display)).getText().toString());
  Rect grid=bounds(root,a.findViewById(R.id.dial_grid)),call=bounds(root,a.findViewById(R.id.btn_call)),scroll=bounds(root,a.findViewById(R.id.dial_results_scroll));View viewport=a.findViewById(R.id.dial_keys_scroll);assertTrue((viewport==null?grid:bounds(root,viewport)).bottom<=call.top);assertTrue(scroll.bottom<=grid.top);assertTrue(scroll.height()>0);
  matches.getChildAt(1).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();android.app.AlertDialog chooser=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(chooser);assertEquals(3,chooser.getListView().getAdapter().getCount());View item=chooser.getListView().getAdapter().getView(0,null,chooser.getListView());assertEquals(TextView.class,item.getClass());assertTrue(((TextView)item).getText().toString().contains("\u2066\u202D011 111 1111\u202C\u2069"));chooser.dismiss();
  for(int i=0;i<8;i++)a.findViewById(R.id.btn_backspace).performClick();settle(a);layout(root);assertEquals(0,matches.getChildCount());assertEquals(one,bounds(root,key(root,"1")));controller.pause().stop().destroy();
 }
 @Test public void asyncResultsCannotMoveAnyKeyOrStealAnInFlightTap()throws Exception{stable(0,false);}
 @Test public void asyncResultsCannotMoveAnyKeyAtLargestFontAndContrast()throws Exception{stable(2,true);}
 @Test @Config(qualifiers="ar-rEG-w411dp-h480dp") public void shortWindowKeepsActionsAndKeyViewportReachable()throws Exception {
  availableHeight=440;stable(2,true);
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();ViewGroup root=(ViewGroup)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);layout(root);
  ScrollView keys=a.findViewById(R.id.dial_keys_scroll);assertNotNull(keys);Rect actions=bounds(root,a.findViewById(R.id.btn_call));assertTrue("actions "+actions+" root "+root.getHeight(),actions.bottom<=root.getHeight());assertTrue("keys height "+keys.getHeight(),keys.getHeight()>48*root.getResources().getDisplayMetrics().density);keys.scrollTo(0,keys.getChildAt(0).getHeight());layout(root);assertTrue("scroll "+keys.getScrollY(),keys.getScrollY()>0);assertTrue("zero "+bounds(root,key(root,"0"))+" keys "+bounds(root,keys),bounds(root,key(root,"0")).bottom<=bounds(root,keys).bottom);tap(root,bounds(root,key(root,"0")));assertEquals("0",((TextView)a.findViewById(R.id.dial_display)).getText().toString());
 }
 @Test public void previewAndChooserIsolateOnlyTheNumberAndKeepRawData(){
  String raw="+20 100 000 0001";ContactEntry c=new ContactEntry(1,"اسم تجريبي",raw,null,false);Button row=DialMatchUi.preview(app,c,true);
  assertTrue(row.getText().toString().endsWith("\u2066\u202D"+raw+"\u202C\u2069"));assertTrue(DialMatchUi.chooserLabel(c).endsWith("\u2066\u202D"+raw+"\u202C\u2069"));assertEquals(raw,c.firstNumber());
  DialpadActivity a=Robolectric.buildActivity(DialpadActivity.class).setup().get();assertEquals(View.TEXT_DIRECTION_LTR,a.findViewById(R.id.dial_display).getTextDirection());
 }
}
