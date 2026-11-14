package com.tvlite.home;

import android.os.Build;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Date;

/**
 * Hijri date. Uses the system Umm al-Qura calendar on Android 7+,
 * and a tabular (arithmetic) calendar on older devices.
 */
public final class Hijri {

    public static final String[] MONTHS = {
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};

    private Hijri() {}

    /** returns {year, month(1-12), day} */
    public static int[] of(Calendar c, int offsetDays) {
        Calendar x = (Calendar) c.clone();
        x.add(Calendar.DAY_OF_MONTH, offsetDays);
        if (Build.VERSION.SDK_INT >= 24) {
            int[] r = icu(x.getTime());
            if (r != null) return r;
        }
        return tabular(x.get(Calendar.YEAR), x.get(Calendar.MONTH) + 1, x.get(Calendar.DAY_OF_MONTH));
    }

    public static String format(int[] h) {
        return h[2] + " " + MONTHS[h[1] - 1] + " " + h[0] + "هـ";
    }

    private static int[] icu(Date date) {
        try {
            Class<?> cls = Class.forName("android.icu.util.IslamicCalendar");
            Object cal = cls.newInstance();
            Class<?> typeCls = Class.forName("android.icu.util.IslamicCalendar$CalculationType");
            Object umalqura = null;
            for (Object o : typeCls.getEnumConstants()) {
                if ("ISLAMIC_UMALQURA".equals(o.toString())) umalqura = o;
            }
            if (umalqura == null) return null;
            cls.getMethod("setCalculationType", typeCls).invoke(cal, umalqura);
            Class<?> base = Class.forName("android.icu.util.Calendar");
            base.getMethod("setTime", Date.class).invoke(cal, date);
            Method get = base.getMethod("get", int.class);
            int y = (Integer) get.invoke(cal, 1);   // EXTENDED field ids: YEAR=1
            int m = (Integer) get.invoke(cal, 2);   // MONTH=2 (0-based)
            int d = (Integer) get.invoke(cal, 5);   // DAY_OF_MONTH=5
            return new int[]{y, m + 1, d};
        } catch (Throwable t) {
            return null;
        }
    }

    private static int[] tabular(int y, int m, int d) {
        int a = (14 - m) / 12;
        int yy = y + 4800 - a;
        int mm = m + 12 * a - 3;
        int jd = d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
        int l = jd - 1948440 + 10632;
        int n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        int j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int hm = (24 * l) / 709;
        int hd = l - (709 * hm) / 24;
        int hy = 30 * n + j - 30;
        return new int[]{hy, hm, hd};
    }
}
