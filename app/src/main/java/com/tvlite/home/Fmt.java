package com.tvlite.home;

import java.util.Calendar;

/** Arabic date and time text, with Western digits. */
public final class Fmt {

    public static final String[] DAYS = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
    public static final String[] MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};

    private Fmt() {}

    public static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    /** "8:05" (12h) */
    public static String hm12(int minutesOfDay) {
        int h = (minutesOfDay / 60) % 24;
        int m = minutesOfDay % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + two(m);
    }

    public static String ampm(int minutesOfDay) {
        return ((minutesOfDay / 60) % 24) < 12 ? "ص" : "م";
    }

    public static String time(int minutesOfDay) { return hm12(minutesOfDay) + " " + ampm(minutesOfDay); }

    public static String gregorian(Calendar c) {
        return DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH) + " "
                + MONTHS[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    public static String duration(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return h + ":" + two(m);
    }

    public static int minutesOfDay(Calendar c) {
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }
}
