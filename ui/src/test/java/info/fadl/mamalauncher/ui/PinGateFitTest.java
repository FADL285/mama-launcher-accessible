package info.fadl.mamalauncher.ui;

import android.view.View;

import app.cash.paparazzi.Paparazzi;

import org.junit.Rule;
import org.junit.Test;

/** The trial phone is a Samsung A10s (720x1520, ~271dpi). The PIN pad must
 *  fit fully - every key visible without scrolling - even at the largest
 *  font scale (1.5). */
public class PinGateFitTest {

    private static app.cash.paparazzi.DeviceConfig a10sLargestText() {
        app.cash.paparazzi.DeviceConfig d = app.cash.paparazzi.DeviceConfig.NEXUS_5;
        return new app.cash.paparazzi.DeviceConfig(
                1520, 720, 271, 271,
                d.getOrientation(), com.android.resources.UiMode.NORMAL, d.getNightMode(),
                com.android.resources.Density.DPI_280, 1.5f,
                com.android.resources.LayoutDirection.RTL,
                "ar", d.getRatio(), d.getSize(), d.getKeyboard(), d.getTouchScreen(),
                d.getKeyboardState(), d.getSoftButtons(), d.getNavigation(),
                d.getScreenRound(), d.getReleased());
    }

    @Rule
    public final Paparazzi paparazzi = new Paparazzi(
            app.cash.paparazzi.EnvironmentKt.detectEnvironment(), a10sLargestText(), "MamaTheme");

    @Test public void dialpadEmptyHintLargestText(){
        snapshot(paparazzi.inflate(R.layout.activity_dialpad), "dialpad-empty-hint");
    }
    @Test public void confirmLargestTextBottomActions(){
        View v=paparazzi.inflate(R.layout.activity_confirm);
        ((android.widget.TextView)v.findViewById(R.id.big_name)).setText("سوسن محمود");
        v.findViewById(R.id.favorite_hint).setVisibility(View.VISIBLE);
        UiBinder.bindNumbers(v, java.util.Arrays.asList(new ContactEntry.PhoneNumber("01000000001","موبايل"),new ContactEntry.PhoneNumber("0200000002","البيت")), n -> {});
        android.widget.ScrollView scroll=v.findViewById(R.id.confirm_scroll);
        v.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(650,View.MeasureSpec.EXACTLY));v.layout(0,0,400,650);scroll.fullScroll(View.FOCUS_DOWN);
        snapshot(v, "confirm-largest-bottom");
    }
    @Test
    public void pinGateFitsA10sLargestText() {
        View v = paparazzi.inflate(R.layout.activity_pingate);
        snapshot(v, "pin-gate-a10s-largest");
    }

    @Test
    public void dialpadA10sLargestText() {
        View v = paparazzi.inflate(R.layout.activity_dialpad);
        // Full 11-digit Egyptian mobile number: every digit must stay visible.
        ((android.widget.TextView) v.findViewById(R.id.dial_display)).setText("0100 000 0000");
        v.findViewById(R.id.dial_display).setVisibility(View.VISIBLE);v.findViewById(R.id.dial_placeholder).setVisibility(View.GONE);
        snapshot(v, "dialpad-a10s-largest");
    }

    @Test
    public void dialpadA10sInternationalLength() {
        View v = paparazzi.inflate(R.layout.activity_dialpad);
        // International-length input must not truncate or lose digits.
        ((android.widget.TextView) v.findViewById(R.id.dial_display)).setText("+2010 0000 0000");
        v.findViewById(R.id.dial_display).setVisibility(View.VISIBLE);v.findViewById(R.id.dial_placeholder).setVisibility(View.GONE);
        snapshot(v, "dialpad-a10s-international");
    }

    @Test
    public void pinGateLockedOutShowsReachableRecovery() {
        View v = paparazzi.inflate(R.layout.activity_pingate);
        ((android.widget.TextView) v.findViewById(R.id.pin_error))
                .setText("محاولات كتير غلط - استنى شوية وجرب تاني");
        v.findViewById(R.id.btn_device_lock).setVisibility(View.VISIBLE);
        snapshot(v, "pin-gate-locked-recovery");
    }

    @Test
    public void everydayHomeA10sLargestText() {
        View v = paparazzi.inflate(R.layout.activity_main);
        ((android.widget.TextView) v.findViewById(R.id.home_clock)).setText("1:00");
        ((android.widget.TextView) v.findViewById(R.id.home_date)).setText("الاثنين ٩ نوفمبر");
        android.widget.LinearLayout box=v.findViewById(R.id.tiles_container);
        android.widget.LinearLayout row=new android.widget.LinearLayout(v.getContext());
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        for(String label:new String[]{"الهاتف","تطبيق العائلة للرسائل"}) {
            View tile=UiBinder.buildHomeTile(v.getContext(),label,
                v.getResources().getDrawable(R.drawable.ic_call),R.drawable.chip_green,false,null);
            android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(
                0,android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,1f);
            int m=(int)(6*v.getResources().getDisplayMetrics().density);
            lp.setMargins(m,m,m,m);row.addView(tile,lp);
        }
        box.addView(row);
        snapshot(v, "home-a10s-largest");
    }

    private View internetHome(boolean bottom) {
        View v=paparazzi.inflate(R.layout.activity_main);
        ((android.widget.TextView)v.findViewById(R.id.home_clock)).setText("1:00");
        ((android.widget.TextView)v.findViewById(R.id.home_date)).setText("الاثنين ٩ نوفمبر");
        android.widget.LinearLayout box=v.findViewById(R.id.tiles_container);
        String[] labels={"الهاتف","الاستوديو","YouTube","Chrome","واتساب","Galaxy Store","Gmail","Meet","تطبيق الإنترنت من سامسونج","فجر اب"};
        for(int i=0;i<labels.length;i+=2) {
            android.widget.LinearLayout row=new android.widget.LinearLayout(v.getContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            for(int j=i;j<i+2;j++) {
                View tile=UiBinder.buildHomeTile(v.getContext(),labels[j],v.getResources().getDrawable(R.drawable.ic_play),R.drawable.chip_blue,false,null);
                android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(0,android.widget.LinearLayout.LayoutParams.MATCH_PARENT,1f);
                int m=(int)(6*v.getResources().getDisplayMetrics().density);lp.setMargins(m,m,m,m);row.addView(tile,lp);
            }
            box.addView(row);
        }
        if(bottom) {
            int w=(int)(720f/280*160),h=(int)(1520f/280*160);
            v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);
            ((android.widget.ScrollView)v).fullScroll(View.FOCUS_DOWN);
        }
        return v;
    }
    @Test public void internetLabelMultirowTopLargestText() {snapshot(internetHome(false),"internet-home-top");}
    @Test public void internetLabelLastRowLargestText() {snapshot(internetHome(true),"internet-home-bottom");}

    @Test public void internetVisiblePrefixAtLastRowLargestText() {
        View v=internetHome(true);
        android.widget.LinearLayout box=v.findViewById(R.id.tiles_container);
        android.widget.LinearLayout row=(android.widget.LinearLayout)box.getChildAt(box.getChildCount()-1);
        android.widget.LinearLayout tile=(android.widget.LinearLayout)row.getChildAt(0);
        ((android.widget.TextView)tile.getChildAt(tile.getChildCount()-1)).setText("تطبيق الإنترنت من");
        v.requestLayout();
        int w=(int)(720f/280*160),h=(int)(1520f/280*160);
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);
        ((android.widget.ScrollView)v).fullScroll(View.FOCUS_DOWN);
        snapshot(v,"internet-visible-prefix-bottom");
    }
    @Test public void dialpadSavedMatchLargestText() {
        View v=paparazzi.inflate(R.layout.activity_dialpad);
        ((android.widget.TextView)v.findViewById(R.id.dial_display)).setText("01000000001");v.findViewById(R.id.dial_display).setVisibility(View.VISIBLE);v.findViewById(R.id.dial_placeholder).setVisibility(View.GONE);
        ((android.widget.TextView)v.findViewById(R.id.save_number_label)).setText(R.string.dial_view_saved);
        android.widget.Button match=DialMatchUi.preview(v.getContext(),new ContactEntry(1,"اسم تجريبي","0100 000 0001",null,false),true);
        ((android.widget.LinearLayout)v.findViewById(R.id.dial_matches)).addView(match);
        snapshot(v,"dialpad-saved-match-largest");
    }
    @Test public void dialpadSuggestionsLargestText() {
        View v=paparazzi.inflate(R.layout.activity_dialpad);
        ((android.widget.TextView)v.findViewById(R.id.dial_display)).setText("010");v.findViewById(R.id.dial_display).setVisibility(View.VISIBLE);v.findViewById(R.id.dial_placeholder).setVisibility(View.GONE);
        android.widget.LinearLayout box=v.findViewById(R.id.dial_matches);
        android.widget.Button match=DialMatchUi.preview(v.getContext(),new ContactEntry(1,"اسم تجريبي","0100 000 0001",null,false),false);box.addView(match);
        android.widget.Button more=new android.widget.Button(v.getContext());more.setText("كل النتائج (6)");more.setTextSize(18);box.addView(more);
        snapshot(v,"dialpad-suggestions-largest");
    }
    @Test public void homeWithAppsPageLargestText() {
        View v=paparazzi.inflate(R.layout.activity_main);
        ((android.widget.TextView)v.findViewById(R.id.home_clock)).setText("1:00");((android.widget.TextView)v.findViewById(R.id.home_date)).setText("الاثنين ٩ نوفمبر");
        android.widget.LinearLayout box=v.findViewById(R.id.tiles_container);
        addAppRow(v,box,new String[]{"الهاتف","تطبيقات"},new int[]{R.drawable.ic_call,R.drawable.ic_apps});
        addAppRow(v,box,new String[]{"واتساب","الاستوديو"},new int[]{R.drawable.ic_chat,R.drawable.ic_play});
        snapshot(v,"home-with-apps-page-largest");
    }
    @Test public void secondaryAppsPageLargestText() {
        View v=paparazzi.inflate(R.layout.activity_main);v.findViewById(R.id.home_header).setVisibility(View.GONE);
        android.widget.LinearLayout box=v.findViewById(R.id.tiles_container);
        android.widget.LinearLayout fixed=new android.widget.LinearLayout(v.getContext());fixed.setOrientation(android.widget.LinearLayout.VERTICAL);int edge=(int)(12*v.getResources().getDisplayMetrics().density);fixed.setPadding(edge,edge,edge,0);
        android.widget.Button back=new android.widget.Button(v.getContext());back.setText(R.string.back);back.setTextSize(26);back.setBackgroundResource(R.drawable.btn_back);back.setTextColor(android.graphics.Color.WHITE);back.setMinHeight((int)(72*v.getResources().getDisplayMetrics().density));fixed.addView(back,new android.widget.LinearLayout.LayoutParams(-1,-2));
        android.widget.TextView title=AppPickerUi.heading(v.getContext(),"تطبيقات",30);title.setGravity(android.view.Gravity.CENTER);fixed.addView(title,new android.widget.LinearLayout.LayoutParams(-1,-2));
        fixed.addView(v,new android.widget.LinearLayout.LayoutParams(-1,0,1));
        addAppRow(v,box,new String[]{"الحاسبة","تطبيق الإنترنت من سامسونج"},new int[]{R.drawable.ic_apps,R.drawable.ic_play});
        addAppRow(v,box,new String[]{"يوتيوب","الخرائط"},new int[]{R.drawable.ic_play,R.drawable.ic_apps});
        snapshot(fixed,"secondary-apps-page-largest");
    }
    @Test public void secondaryAppsScrolledBackRemainsVisible() {
        View v=paparazzi.inflate(R.layout.activity_main);v.findViewById(R.id.home_header).setVisibility(View.GONE);
        android.widget.LinearLayout box=v.findViewById(R.id.tiles_container);android.widget.LinearLayout fixed=new android.widget.LinearLayout(v.getContext());fixed.setOrientation(android.widget.LinearLayout.VERTICAL);int edge=(int)(12*v.getResources().getDisplayMetrics().density);fixed.setPadding(edge,edge,edge,0);
        android.widget.Button back=new android.widget.Button(v.getContext());back.setText(R.string.back);back.setTextSize(26);back.setBackgroundResource(R.drawable.btn_back);back.setTextColor(android.graphics.Color.WHITE);back.setMinHeight((int)(72*v.getResources().getDisplayMetrics().density));fixed.addView(back,new android.widget.LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<7;i++)addAppRow(v,box,new String[]{"تطبيق تجريبي "+i,"تطبيق آخر "+i},new int[]{R.drawable.ic_apps,R.drawable.ic_play});
        fixed.addView(v,new android.widget.LinearLayout.LayoutParams(-1,0,1));
        int w=(int)(720f/280*160),h=(int)(1520f/280*160);fixed.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));fixed.layout(0,0,w,h);((android.widget.ScrollView)v).fullScroll(View.FOCUS_DOWN);
        snapshot(fixed,"secondary-scrolled-fixed-back");
    }
    private void addAppRow(View v,android.widget.LinearLayout box,String[] labels,int[] icons) {
        android.widget.LinearLayout row=new android.widget.LinearLayout(v.getContext());row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        for(int i=0;i<labels.length;i++) {
            View tile=UiBinder.buildHomeTile(v.getContext(),labels[i],v.getResources().getDrawable(icons[i]),R.drawable.chip_green,false,null);
            android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(0,-2,1);int m=(int)(6*v.getResources().getDisplayMetrics().density);lp.setMargins(m,m,m,m);row.addView(tile,lp);
        }box.addView(row,new android.widget.LinearLayout.LayoutParams(-1,-2));
    }
    private void snapshot(View v,String name) {
        paparazzi.snapshot(v,name);

    }
    private View populatedSettings(int section) {
        View v=paparazzi.inflate(R.layout.activity_settings);
        SettingsSections.show(v,section);
        android.widget.LinearLayout box=v.findViewById(R.id.settings_apps_list);
        v.findViewById(R.id.apps_loading).setVisibility(View.GONE);
        box.addView(AppPickerUi.heading(v.getContext(),v.getContext().getString(R.string.apps_drag_hint),18));
        box.addView(AppPickerUi.heading(v.getContext(),v.getContext().getString(R.string.apps_home),24));
        for(int i=0;i<30;i++)box.addView(AppPickerUi.row(v.getContext(),"تطبيق تجريبي "+i,i<4?0:i<8?1:2));
        return v;
    }
    @Test public void settingsLandingBeforeDiscovery() {
        View v=paparazzi.inflate(R.layout.activity_settings);
        snapshot(v,"settings-before-discovery");
    }
    @Test public void settingsLandingAfterDiscovery() {
        snapshot(populatedSettings(-1),"settings-after-discovery");
    }
    @Test public void settingsAppsTop() {snapshot(populatedSettings(0),"settings-apps-top");}
    @Test public void settingsAppsScrolledBottom() {
        View v=populatedSettings(0);
        // Measure/layout before scrolling; fixed header must remain visible.
        int width=(int)(720f/280*160),height=(int)(1520f/280*160);
        v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        v.layout(0,0,width,height);
        android.widget.ScrollView scroll=v.findViewById(R.id.settings_scroll);
        scroll.fullScroll(View.FOCUS_DOWN);
        snapshot(v,"settings-picker-scrolled-bottom");
    }
    @Test public void settingsLandingScrolledBottom() {
        View v=populatedSettings(-1);
        int width=(int)(720f/280*160),height=(int)(1520f/280*160);
        v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,width,height);
        ((android.widget.ScrollView)v.findViewById(R.id.settings_scroll)).fullScroll(View.FOCUS_DOWN);
        snapshot(v,"settings-menu-bottom");
    }
    @Test public void settingsSoundLargestText() {
        View v=populatedSettings(1);
        ((android.widget.TextView)v.findViewById(R.id.tts_status)).setText("بيانات صوت العربي ناقصة");
        v.findViewById(R.id.btn_tts_data).setVisibility(View.VISIBLE);
        ((android.widget.TextView)v.findViewById(R.id.tts_engine_label)).setText("المحرك المختار: محرك تجريبي للعربي");
        snapshot(v,"settings-sound");
    }
    @Test public void settingsSoundPendingLargestText() {
        View v=populatedSettings(1);
        ((android.widget.TextView)v.findViewById(R.id.tts_status)).setText(R.string.tts_preparing);
        ((android.widget.TextView)v.findViewById(R.id.tts_engine_label)).setText("المحرك المختار: محرك تجريبي للعربي");
        v.findViewById(R.id.btn_tts_data).setVisibility(View.VISIBLE);
        snapshot(v,"settings-sound-pending");
    }
    @Test public void settingsAccessibilityLargestText() {snapshot(populatedSettings(2),"settings-accessibility");}
    @Test public void settingsPermissionsLargestText() {
        View v=populatedSettings(3);
        ((android.widget.TextView)v.findViewById(R.id.perm_contacts_status)).setText("مسموح");
        ((android.widget.TextView)v.findViewById(R.id.perm_call_status)).setText("مش مسموح");
        snapshot(v,"settings-permissions");
    }
    @Test public void settingsProtectionLargestText() {snapshot(populatedSettings(4),"settings-protection");}
}
