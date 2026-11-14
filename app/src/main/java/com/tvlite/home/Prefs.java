package com.tvlite.home;

import android.content.Context;
import android.content.SharedPreferences;

/** Prayer settings chosen on the TV. Indexes follow PrayerTimes.NAMES (0 fajr, 1 sunrise, 2 dhuhr...). */
public final class Prefs {

    public static final int[] PRAYERS = {0, 2, 3, 4, 5};          // the five prayers (no sunrise)
    private static final int[] IQAMA_DEFAULT = {25, 0, 20, 20, 10, 20};

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static boolean adhanOn(Context c, int i) { return get(c).getBoolean("adhan_on_" + i, true); }
    public static int adjust(Context c, int i) { return get(c).getInt("adj_" + i, 0); }
    public static int iqama(Context c, int i) { return get(c).getInt("iqama_" + i, IQAMA_DEFAULT[i]); }
    public static String muezzin(Context c) { return get(c).getString("muezzin", ""); }
    public static String muezzinFajr(Context c) { return get(c).getString("muezzin_fajr", ""); }
    public static int volume(Context c) { return get(c).getInt("volume", 8); }
    public static int hijriOffset(Context c) { return get(c).getInt("hijri_offset", 0); }
    public static boolean prayerScreen(Context c) { return get(c).getBoolean("prayer_screen", true); }
    public static boolean bringToFront(Context c) { return get(c).getBoolean("bring_front", false); }
    public static boolean iqamaSound(Context c) { return get(c).getBoolean("iqama_sound", true); }
    public static boolean shortAdhan(Context c) { return get(c).getBoolean("adhan_short", false); }
}
