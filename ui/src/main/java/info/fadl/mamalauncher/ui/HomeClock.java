package info.fadl.mamalauncher.ui;


import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** One instant/timezone for the 12-hour time and date, independent of device language. */
public final class HomeClock {
    private HomeClock() {}

    /** Digits only, 12-hour clock, Latin digits on every device language. */
    public static String digits(Date instant, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("h:mm", Locale.US);
        format.setTimeZone(zone);
        return format.format(instant);
    }

    /** ص before noon, م from noon on. */
    public static String period(Date instant, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone, Locale.US);
        calendar.setTime(instant);
        return calendar.get(Calendar.HOUR_OF_DAY) < 12 ? "ص" : "م";
    }

    public static String time(Date instant, TimeZone zone) {
        return digits(instant,zone)+" "+period(instant,zone);
    }
    /** Separate views impose physical placement rather than relying on bidi. */
    public static void bind(android.view.View root, Date instant, TimeZone zone){
        android.widget.TextView digits=root.findViewById(R.id.home_clock);
        android.widget.TextView marker=root.findViewById(R.id.home_clock_period);
        digits.setText(digits(instant,zone));
        marker.setText(period(instant,zone));
        root.findViewById(R.id.clock_row).setContentDescription(time(instant,zone));
    }

    public static String date(Date instant, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("EEEE d MMMM", new Locale("ar"));
        format.setTimeZone(zone);
        return format.format(instant);
    }
}
