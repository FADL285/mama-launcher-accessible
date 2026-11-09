package info.fadl.mamalauncher.ui;

import android.content.Context;
import android.util.TypedValue;

/** Resolve readable surface text from the active normal or high-contrast theme. */
public final class SurfaceText {
 private SurfaceText() {}
 public static int color(Context context) {return resolve(context,R.attr.surfaceText);}
 public static int action(Context context) {return resolve(context,R.attr.surfaceAction);}
 private static int resolve(Context context,int attribute) {
  TypedValue value=new TypedValue();
  if(!context.getTheme().resolveAttribute(attribute,value,true))
   throw new IllegalStateException("Theme must define surfaceText");
  return value.resourceId==0?value.data:context.getResources().getColor(value.resourceId);
 }
}
