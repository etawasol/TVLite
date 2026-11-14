package com.tvlite.home;

import android.content.Context;

import java.util.Calendar;

/** Prayer times for a day with the user's minute adjustments applied. */
public final class Prayers {

    private Prayers() {}

    public static int[] hijri(Context c, Calendar day) {
        return Hijri.of(day, Prefs.hijriOffset(c));
    }

    /** Minutes since midnight for the six times of that day. */
    public static int[] times(Context c, Calendar day) {
        boolean ramadan = hijri(c, day)[1] == 9;
        int[] t = PrayerTimes.compute(day, Config.LAT, Config.LNG, Config.TZ, ramadan);
        for (int i = 0; i < 6; i++) t[i] += Prefs.adjust(c, i);
        return t;
    }

    /** Friday noon prayer is called Jumu'ah. */
    public static String name(Calendar day, int i) {
        if (i == 2 && day.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) return "الجمعة";
        return PrayerTimes.NAMES[i];
    }

    /** Epoch millis of prayer i on the given day. */
    public static long at(Calendar day, int minutes) {
        Calendar x = (Calendar) day.clone();
        x.set(Calendar.HOUR_OF_DAY, 0);
        x.set(Calendar.MINUTE, 0);
        x.set(Calendar.SECOND, 0);
        x.set(Calendar.MILLISECOND, 0);
        return x.getTimeInMillis() + minutes * 60000L;
    }
}
