package com.tvlite.home;

import java.util.Calendar;

/**
 * Offline prayer-time calculation, Umm al-Qura method.
 * Fajr at 18.5 degrees, Isha 90 minutes after Maghrib (120 in Ramadan),
 * Asr with the standard shadow factor of 1.
 * Astronomy follows the well-known public-domain PrayTimes.org formulas.
 */
public final class PrayerTimes {

    public static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    private PrayerTimes() {}

    /** Returns six times as minutes since local midnight: fajr, sunrise, dhuhr, asr, maghrib, isha. */
    public static int[] compute(Calendar day, double lat, double lng, double tz, boolean ramadan) {
        int y = day.get(Calendar.YEAR);
        int m = day.get(Calendar.MONTH) + 1;
        int d = day.get(Calendar.DAY_OF_MONTH);
        double jDate = julian(y, m, d) - lng / (15.0 * 24.0);

        double fajr = sunAngleTime(jDate, 18.5, 5 / 24.0, true, lat);
        double sunrise = sunAngleTime(jDate, 0.833, 6 / 24.0, true, lat);
        double dhuhr = midDay(jDate, 12 / 24.0);
        double asr = asrTime(jDate, 1, 13 / 24.0, lat);
        double maghrib = sunAngleTime(jDate, 0.833, 18 / 24.0, false, lat);

        double adj = tz - lng / 15.0;
        double[] t = {fajr + adj, sunrise + adj, dhuhr + adj, asr + adj, maghrib + adj};
        int[] out = new int[6];
        for (int i = 0; i < 5; i++) out[i] = (int) Math.round(fixHour(t[i]) * 60.0);
        out[5] = out[4] + (ramadan ? 120 : 90);
        return out;
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** returns {declination, equation of time} */
    private static double[] sun(double jd) {
        double D = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * D);
        double q = fixAngle(280.459 + 0.98564736 * D);
        double L = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * D;
        double ra = darctan2(dcos(e) * dsin(L), dcos(L)) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        double decl = darcsin(dsin(e) * dsin(L));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jDate, double t) {
        return fixHour(12 - sun(jDate + t)[1]);
    }

    private static double sunAngleTime(double jDate, double angle, double t, boolean ccw, double lat) {
        double decl = sun(jDate + t)[0];
        double noon = midDay(jDate, t);
        double x = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        x = Math.max(-1, Math.min(1, x));
        double T = darccos(x) / 15.0;
        return noon + (ccw ? -T : T);
    }

    private static double asrTime(double jDate, double factor, double t, double lat) {
        double decl = sun(jDate + t)[0];
        double angle = -Math.toDegrees(Math.atan(1.0 / (factor + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        return sunAngleTime(jDate, angle, t, false, lat);
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double fixAngle(double a) { a = a - 360.0 * Math.floor(a / 360.0); return a < 0 ? a + 360 : a; }
    private static double fixHour(double a) { a = a - 24.0 * Math.floor(a / 24.0); return a < 0 ? a + 24 : a; }
}
