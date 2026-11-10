package info.fadl.mamalauncher;

import android.content.Context;
import android.telephony.TelephonyManager;
import java.util.Locale;

/** Shared device-region hint. Never assumes Egypt from the app language. */
final class NumberRegion {
  private NumberRegion() {}

  static String current(Context context) {
    TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    String iso = "";
    try {
      iso = tm == null ? "" : tm.getSimCountryIso();
      if (iso == null || iso.isEmpty()) iso = tm == null ? "" : tm.getNetworkCountryIso();
    } catch (RuntimeException unavailable) {
      iso = "";
    }
    if (iso == null || iso.isEmpty()) iso = Locale.getDefault().getCountry();
    return iso == null ? "" : iso.toUpperCase(Locale.ROOT);
  }
}
