package info.fadl.mamalauncher.ui;

import android.view.View;
import android.widget.TextView;
import android.widget.ScrollView;

/** One settings section at a time within the already-authorized Activity.
 * Shared with render tests so previews use the actual navigation binding. */
public final class SettingsSections {
    public static final int[] PANELS={R.id.settings_section_apps,R.id.settings_section_sound,
            R.id.settings_section_accessibility,R.id.settings_section_permissions,R.id.settings_section_protection};
    public static final int[] BUTTONS={R.id.open_section_apps,R.id.open_section_sound,
            R.id.open_section_accessibility,R.id.open_section_permissions,R.id.open_section_protection};
    private static final int[] TITLES={R.string.apps_section_title,R.string.sound_section_title,
            R.string.access_section_title,R.string.perms_section_title,R.string.protect_section_title};
    private SettingsSections() {}
    public static void show(View root,int section) {
        if(section < -1 || section >= PANELS.length) section=-1;
        root.findViewById(R.id.settings_menu).setVisibility(section==-1?View.VISIBLE:View.GONE);
        for(int i=0;i<PANELS.length;i++)root.findViewById(PANELS[i]).setVisibility(i==section?View.VISIBLE:View.GONE);
        ((TextView)root.findViewById(R.id.settings_page_title)).setText(section==-1?R.string.settings_title:TITLES[section]);
        ((TextView)root.findViewById(R.id.btn_settings_back)).setText(section==-1?R.string.settings_back_home:R.string.settings_sections_back);
        ScrollView scroll=root.findViewById(R.id.settings_scroll);
        scroll.scrollTo(0,0);
        root.findViewById(R.id.settings_page_title).sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }
}
