package info.fadl.mamalauncher.ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** One instant/timezone for the 12-hour time and date, independent of device language. */
public final class HomeClock {
    private HomeClock() {}

    public static String time(Date instant, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone, Locale.US);
        calendar.setTime(instant);
        SimpleDateFormat format = new SimpleDateFormat("h:mm", Locale.US);
        format.setTimeZone(zone);
        String period = calendar.get(Calendar.HOUR_OF_DAY) < 12 ? "ص" : "م";
        return format.format(instant) + " " + period;
    }

    public static String date(Date instant, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("EEEE d MMMM", new Locale("ar"));
        format.setTimeZone(zone);
        return format.format(instant);
    }
}
