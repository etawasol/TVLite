package com.tvlite.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * The app's idea of "now". Cheap TV boxes often boot with a wrong clock (e.g. May 2025) and never fix it,
 * so whenever a web server answers we compare its Date header with the system clock and, if they differ by
 * more than a couple of minutes, show the server's time instead. Alarms are converted back to system time.
 */
public final class Clock {

    private static final long TOLERANCE = 2 * 60 * 1000L;
    private static volatile long offset;
    private static SharedPreferences prefs;

    private Clock() {}

    public static void init(Context c) {
        prefs = c.getSharedPreferences("clock", Context.MODE_PRIVATE);
        offset = prefs.getLong("offset", 0);
    }

    public static long now() { return System.currentTimeMillis() + offset; }

    public static Calendar cal() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now());
        return c;
    }

    /** Converts a real time into the device clock, for AlarmManager. */
    public static long toSystem(long real) { return real - offset; }

    public static long offset() { return offset; }

    /** Called with a web server's Date header (millis, 0 if missing). */
    public static void observe(long serverTime) {
        if (serverTime <= 0) return;
        long diff = serverTime - System.currentTimeMillis();
        long next = Math.abs(diff) > TOLERANCE ? diff : 0;
        if (Math.abs(next - offset) < TOLERANCE && !(next == 0 && offset != 0)) return;
        offset = next;
        if (prefs != null) prefs.edit().putLong("offset", next).apply();
    }
}
