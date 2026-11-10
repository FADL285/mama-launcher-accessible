package info.fadl.mamalauncher.ui;
import android.view.*;import android.widget.*;import app.cash.paparazzi.Paparazzi;import org.junit.*;import static org.junit.Assert.*;
public class ExpandedRenderTest {
 private static app.cash.paparazzi.DeviceConfig device(){app.cash.paparazzi.DeviceConfig d=app.cash.paparazzi.DeviceConfig.NEXUS_5;return new app.cash.paparazzi.DeviceConfig(1520,720,271,271,d.getOrientation(),com.android.resources.UiMode.NORMAL,d.getNightMode(),com.android.resources.Density.DPI_280,1.5f,com.android.resources.LayoutDirection.RTL,"ar",d.getRatio(),d.getSize(),d.getKeyboard(),d.getTouchScreen(),d.getKeyboardState(),d.getSoftButtons(),d.getNavigation(),d.getScreenRound(),d.getReleased());}
 @Rule public final Paparazzi paparazzi=new Paparazzi(app.cash.paparazzi.EnvironmentKt.detectEnvironment(),device(),"MamaTheme");
 void measure(View v){v.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1416,View.MeasureSpec.EXACTLY));v.layout(0,0,720,1416);}
 void snap(View v,String name){paparazzi.snapshot(v,name);}
 View confirm(String state,boolean contrast){android.content.Context c=new ContextThemeWrapper(paparazzi.getContext(),contrast?R.style.MamaTheme_Contrast:R.style.MamaTheme);View v=LayoutInflater.from(c).inflate(R.layout.activity_confirm,null);v.setBackgroundColor(c.getResources().getColor(contrast?R.color.contrast_bg:R.color.bg,c.getTheme()));((TextView)v.findViewById(R.id.big_name)).setText("اسم تجريبي");TextView fallback=v.findViewById(R.id.big_photo_fallback);fallback.setText("اسم تجريبي");fallback.setVisibility(View.VISIBLE);fallback.setBackgroundColor(0xFF317778);UiBinder.bindNumbers(v,java.util.Collections.singletonList(new ContactEntry.PhoneNumber("01000000001","موبايل")),n->{});SpeechStatusUi.show(v,state);return v;}
 @Test public void speechReadyKeepsLeftListenAndRightFavorite(){snap(confirm("READY",false),"speech-ready");}
 @Test public void speechRetryBottomKeepsCallAndBack(){View v=confirm("RETRYABLE",false);measure(v);((ScrollView)v.findViewById(R.id.confirm_scroll)).fullScroll(View.FOCUS_DOWN);snap(v,"speech-retry-bottom");}
 @Test public void speechInitializing(){snap(confirm("INITIALIZING",false),"speech-initializing");}
 @Test public void speechSetupContrast(){View v=confirm("NEEDS_SETUP",true);measure(v);((ScrollView)v.findViewById(R.id.confirm_scroll)).fullScroll(View.FOCUS_DOWN);snap(v,"speech-setup-contrast");}
 @Test public void firstUseGuide(){View v=paparazzi.inflate(R.layout.activity_main);HomeClock.bind(v,new java.util.Date(0),java.util.TimeZone.getTimeZone("UTC"));((TextView)v.findViewById(R.id.home_date)).setText("الاثنين ٩ نوفمبر");v.findViewById(R.id.first_use_guide).setVisibility(View.VISIBLE);snap(v,"first-use-guide");}
 @Test public void firstUseGuideBottom(){View v=paparazzi.inflate(R.layout.activity_main);HomeClock.bind(v,new java.util.Date(0),java.util.TimeZone.getTimeZone("UTC"));((TextView)v.findViewById(R.id.home_date)).setText("الاثنين ٩ نوفمبر");v.findViewById(R.id.first_use_guide).setVisibility(View.VISIBLE);measure(v);((ScrollView)v).fullScroll(View.FOCUS_DOWN);snap(v,"first-use-guide-bottom");}
 @Test public void soundHumanCheck(){View v=paparazzi.inflate(R.layout.activity_settings);SettingsSections.show(v,1);v.findViewById(R.id.tts_human_check).setVisibility(View.VISIBLE);((TextView)v.findViewById(R.id.tts_status)).setText(R.string.tts_test_ok);measure(v);((ScrollView)v.findViewById(R.id.settings_scroll)).fullScroll(View.FOCUS_DOWN);snap(v,"sound-human-check");}
 @Test public void soundGuideExpanded(){View v=paparazzi.inflate(R.layout.activity_settings);SettingsSections.show(v,1);v.findViewById(R.id.sound_guide_details).setVisibility(View.VISIBLE);snap(v,"sound-guide-expanded");}
 @Test public void equalLongAndShortCardsHaveEllipsisAndFullName(){
  LinearLayout row=new LinearLayout(paparazzi.getContext());row.setOrientation(LinearLayout.HORIZONTAL);String longName="تطبيق الإنترنت من سامسونج باسم طويل جدًا جدًا للاختبار";
  View shortTile=UiBinder.buildHomeTile(row.getContext(),"الهاتف",row.getResources().getDrawable(R.drawable.ic_call),R.drawable.chip_green,false,null);View longTile=UiBinder.buildHomeTile(row.getContext(),longName,new android.graphics.drawable.ColorDrawable(0xFF317778),0,false,null);row.addView(shortTile,new LinearLayout.LayoutParams(0,-2,1));row.addView(longTile,new LinearLayout.LayoutParams(0,-2,1));measure(row);
  TextView text=(TextView)((LinearLayout)longTile).getChildAt(1);assertEquals(shortTile.getHeight(),longTile.getHeight());assertEquals(longName,longTile.getContentDescription());assertEquals(2,text.getLayout().getLineCount());assertTrue(text.getLayout().getEllipsisCount(1)>0);snap(row,"equal-truncated-cards");
 }
 @Test public void speechOptionsInitialContrastIsVisibleAndOpensRecovery(){View v=confirm("NEEDS_SETUP",true);measure(v);android.widget.ScrollView scroll=v.findViewById(R.id.confirm_scroll);scroll.setSmoothScrollingEnabled(false);View options=v.findViewById(R.id.btn_speech_options);assertEquals(View.VISIBLE,options.getVisibility());assertEquals(0,scroll.getScrollY());android.graphics.Rect visible=new android.graphics.Rect();assertTrue(options.getGlobalVisibleRect(visible));assertEquals(options.getHeight(),visible.height());snap(v,"speech-options-initial-contrast");int callTop=v.findViewById(R.id.btn_call).getTop();options.performClick();assertTrue(scroll.getScrollY()>0);View setup=v.findViewById(R.id.btn_speech_setup);assertTrue(setup.getGlobalVisibleRect(visible));assertEquals(setup.getHeight(),visible.height());assertEquals(callTop,v.findViewById(R.id.btn_call).getTop());snap(v,"speech-options-revealed-contrast");}
 @Test public void speechOptionsHiddenForReadyAndLoading(){for(String state:new String[]{"READY","INITIALIZING"})assertEquals(View.GONE,confirm(state,false).findViewById(R.id.btn_speech_options).getVisibility());}
 @Test public void contactsFailureContrastKeepsRetryAndDialpad(){android.content.Context c=new ContextThemeWrapper(paparazzi.getContext(),R.style.MamaTheme_Contrast);View v=LayoutInflater.from(c).inflate(R.layout.activity_phone,null);v.setBackgroundColor(0xFF000000);PageHeader.title(v,R.string.tile_phone);v.findViewById(R.id.contacts_grid).setVisibility(View.GONE);v.findViewById(R.id.contacts_read_error).setVisibility(View.VISIBLE);measure(v);android.graphics.Rect r=new android.graphics.Rect();assertTrue(v.findViewById(R.id.btn_retry_contacts).getGlobalVisibleRect(r));assertEquals(v.findViewById(R.id.btn_retry_contacts).getHeight(),r.height());snap(v,"contacts-failure-contrast");}
 @Test public void everySettingsHeaderTitleRemainsComplete(){for(int section=-1;section<5;section++){View v=paparazzi.inflate(R.layout.activity_settings);SettingsSections.show(v,section);measure(v);TextView title=v.findViewById(R.id.page_title);android.text.Layout l=title.getLayout();assertEquals(title.getText().length(),l.getLineEnd(l.getLineCount()-1));assertTrue(title.getHeight()>=l.getLineBottom(l.getLineCount()-1));}}

 @Test public void shortHomeLabelGroupIsVerticallyCentered(){
  LinearLayout tile=(LinearLayout)UiBinder.buildHomeTile(paparazzi.getContext(),"الهاتف",paparazzi.getContext().getDrawable(R.drawable.ic_call),R.drawable.chip_green,false,null);
  tile.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST));tile.layout(0,0,320,tile.getMeasuredHeight());
  TextView label=(TextView)tile.getChildAt(1);assertEquals(1,label.getLayout().getLineCount());
  float top=tile.getChildAt(0).getTop(),bottom=label.getBottom();assertEquals(tile.getHeight()/2f,(top+bottom)/2f,2f);
 }
 @Test public void contactsAllowLabelFitsLargestText(){
  View v=paparazzi.inflate(R.layout.activity_phone);v.findViewById(R.id.setup_view).setVisibility(View.VISIBLE);v.findViewById(R.id.contacts_grid).setVisibility(View.GONE);v.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(650,View.MeasureSpec.EXACTLY));v.layout(0,0,400,650);
  TextView allow=v.findViewById(R.id.btn_allow_contacts);android.text.Layout l=allow.getLayout();
  assertTrue("Full wrapped label needs vertical space",allow.getHeight()-allow.getCompoundPaddingTop()-allow.getCompoundPaddingBottom()>=l.getHeight());
  assertEquals(allow.getText().length(),l.getLineEnd(l.getLineCount()-1));snap(v,"contacts-permission-largest");
 }

 @Test public void contactsRecoveryCanScrollAndDialpadStaysReachable(){
  View v=paparazzi.inflate(R.layout.activity_phone);v.findViewById(R.id.setup_view).setVisibility(View.VISIBLE);v.findViewById(R.id.contacts_grid).setVisibility(View.GONE);
  ((TextView)v.findViewById(R.id.btn_allow_contacts)).setText(R.string.contacts_permission_retry);
  v.findViewById(R.id.contacts_permission_help).setVisibility(View.VISIBLE);v.findViewById(R.id.btn_contacts_settings).setVisibility(View.VISIBLE);
  v.measure(View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,560,800);
  ScrollView scroll=v.findViewById(R.id.setup_view);scroll.setSmoothScrollingEnabled(false);scroll.fullScroll(View.FOCUS_DOWN);
  android.graphics.Rect visible=new android.graphics.Rect();View settings=v.findViewById(R.id.btn_contacts_settings);assertTrue(settings.getGlobalVisibleRect(visible));assertEquals(settings.getHeight(),visible.height());
  View dial=v.findViewById(R.id.btn_dialpad);assertTrue(dial.getGlobalVisibleRect(visible));assertEquals(dial.getHeight(),visible.height());assertTrue(scroll.getScrollY()>0);snap(v,"contacts-recovery-scroll");
 }

 @Test public void studioNeverSplitsAnArabicWordAtLargestText(){
  LinearLayout tile=(LinearLayout)UiBinder.buildHomeTile(paparazzi.getContext(),"الاستوديو",paparazzi.getContext().getDrawable(R.drawable.ic_call),R.drawable.chip_green,false,null);
  tile.measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST));tile.layout(0,0,300,tile.getMeasuredHeight());TextView text=(TextView)tile.getChildAt(1);
  assertEquals(1,text.getLayout().getLineCount());assertEquals(0,text.getLayout().getEllipsisCount(0));assertTrue(text.getTextSize()>=18f*text.getResources().getDisplayMetrics().scaledDensity);
  assertEquals(tile.getHeight()/2f,(tile.getChildAt(0).getTop()+text.getBottom())/2f,2f);snap(tile,"studio-one-word");
 }
}
