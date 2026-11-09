package info.fadl.mamalauncher.ui;

import android.content.Context;
import android.view.View;
import android.widget.*;

/** Shared by production settings and pixel tests, no preferences or private app data. */
public final class AppPickerUi {
 private AppPickerUi(){}
 public static LinearLayout row(Context context,String label,int destination) {
  float d=context.getResources().getDisplayMetrics().density;
  LinearLayout outer=new LinearLayout(context);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(0,8,0,8);
  LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);outer.addView(row);
  TextView name=new TextView(context);name.setText(label);name.setTextSize(22);name.setTextColor(context.getResources().getColor(R.color.text_main));row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  Button place=new Button(context);place.setTag("app_place");place.setText(destination==0?R.string.apps_home:destination==1?R.string.apps_folder:R.string.apps_hidden);place.setTextSize(16);place.setContentDescription(label+" - "+context.getString(R.string.app_place));row.addView(place);
  if(destination!=2){TextView handle=new TextView(context);handle.setTag("app_handle");handle.setText("☰");handle.setTextSize(26);handle.setGravity(android.view.Gravity.CENTER);handle.setTextColor(context.getResources().getColor(R.color.back_blue));handle.setContentDescription(context.getString(R.string.app_move,label));handle.setFocusable(true);handle.setPadding(8,8,8,8);row.addView(handle,new LinearLayout.LayoutParams((int)(56*d),(int)(56*d)));}
  return outer;
 }
 public static TextView heading(Context context,String text,int size) {
  TextView view=new TextView(context);view.setText(text);view.setTextSize(size);view.setTextColor(context.getResources().getColor(R.color.text_main));return view;
 }
}
