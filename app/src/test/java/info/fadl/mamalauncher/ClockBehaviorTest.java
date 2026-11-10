package info.fadl.mamalauncher;
import info.fadl.mamalauncher.ui.HomeClock;import android.content.Intent;import android.widget.TextView;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;import java.util.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class ClockBehaviorTest {
 TimeZone oldZone;Locale oldLocale;
 @Before public void before(){oldZone=TimeZone.getDefault();oldLocale=Locale.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Africa/Cairo"));Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().apply();ClockFixture.instant=new Date(0);}
 @After public void after(){TimeZone.setDefault(oldZone);Locale.setDefault(oldLocale);}
 static Date at(int hour,int minute){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"),Locale.US);c.clear();c.set(2026,Calendar.NOVEMBER,9,hour,minute);return c.getTime();}
 public static class ClockFixture extends MainActivity {static Date instant;@Override protected Date clockNow(){return instant;}}
 String text(MainActivity a){return a.findViewById(R.id.clock_row).getContentDescription().toString();}
 @Test public void noonAndMidnightBoundariesUseArabicMarkers(){for(int[] t:new int[][]{{0,0},{0,1},{11,59},{12,0},{12,1},{23,59}}){String expected=t[0]==0?"12":t[0]>12?""+(t[0]-12):""+t[0];expected+=String.format(Locale.US,":%02d %s",t[1],t[0]<12?"ص":"م");assertEquals(expected,HomeClock.time(at(t[0],t[1]),TimeZone.getDefault()));}}
 @Test public void languageCannotChangeMarkersOrTwelveHourPolicy(){for(Locale locale:new Locale[]{Locale.US,Locale.FRANCE,new Locale("ar","EG")}){Locale.setDefault(locale);assertEquals("1:00 م",HomeClock.time(at(13,0),TimeZone.getDefault()));}}
 @Test public void sameInstantUsesSpecifiedTimezone(){Date now=at(13,0);assertEquals("1:00 م",HomeClock.time(now,TimeZone.getTimeZone("Africa/Cairo")));assertEquals("11:00 ص",HomeClock.time(now,TimeZone.getTimeZone("UTC")));}
 @Test public void timeChangedBroadcastImmediatelyRefreshes(){ClockFixture.instant=at(11,59);ClockFixture a=Robolectric.buildActivity(ClockFixture.class).setup().get();assertEquals("11:59 ص",text(a));ClockFixture.instant=at(12,0);a.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("12:00 م",text(a));}
 @Test public void timezoneChangedBroadcastUsesNewZone(){ClockFixture.instant=at(13,0);ClockFixture a=Robolectric.buildActivity(ClockFixture.class).setup().get();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));a.sendBroadcast(new Intent(Intent.ACTION_TIMEZONE_CHANGED));Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("11:00 ص",text(a));}
 @Test public void resumeRefreshesDateAndTimeAfterMidnight(){ClockFixture.instant=at(23,59);org.robolectric.android.controller.ActivityController<ClockFixture> c=Robolectric.buildActivity(ClockFixture.class).setup();String yesterday=((TextView)c.get().findViewById(R.id.home_date)).getText().toString();c.pause();ClockFixture.instant=new Date(at(23,59).getTime()+60000);c.resume();assertEquals("12:00 ص",text(c.get()));assertNotEquals(yesterday,((TextView)c.get().findViewById(R.id.home_date)).getText().toString());}
}
