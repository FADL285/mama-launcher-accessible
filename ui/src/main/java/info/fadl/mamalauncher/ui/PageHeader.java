package info.fadl.mamalauncher.ui;
import android.view.View;
import android.widget.TextView;
/** Shared compact inline back/title bar. Navigation stays outside page scrolling. */
public final class PageHeader {
 private PageHeader(){}
 public static View apps(android.widget.ScrollView scroll){
  scroll.findViewById(R.id.home_header).setVisibility(View.GONE);
  android.content.Context c=scroll.getContext();View header=android.view.LayoutInflater.from(c).inflate(R.layout.page_header,null);title(header,R.string.apps_page);
  android.view.ViewGroup host=(android.view.ViewGroup)scroll.getParent();if(host!=null)host.removeView(scroll);
  android.widget.LinearLayout fixed=new android.widget.LinearLayout(c);fixed.setOrientation(android.widget.LinearLayout.VERTICAL);int edge=(int)(12*c.getResources().getDisplayMetrics().density);fixed.setPadding(edge,edge,edge,0);
  fixed.addView(header,new android.widget.LinearLayout.LayoutParams(-1,-2));fixed.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));
  if(host!=null)host.addView(fixed,new android.view.ViewGroup.LayoutParams(-1,-1));return header;
 }
 public static void title(View root,int text){((TextView)root.findViewById(R.id.page_title)).setText(text);}
}
