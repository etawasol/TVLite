package com.tvlite.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Keeps exactly one alarm set: the next prayer whose adhan is switched on. */
public final class AdhanScheduler {

    public static final String ACTION_ADHAN = "com.tvlite.home.ADHAN";
    public static final String EXTRA_PRAYER = "prayer";
    public static final String EXTRA_TIME = "time";
    public static final String EXTRA_IQAMA = "iqama";

    private AdhanScheduler() {}

    public static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        for (int d = 0; d < 3; d++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_MONTH, d);
            int[] t = Prayers.times(c, day);
            for (int i : Prefs.PRAYERS) {
                long at = Prayers.at(day, t[i]);
                if (at > now + 1000 && Prefs.adhanOn(c, i)) { set(c, am, at, i, false); return; }
                long iq = at + Prefs.iqama(c, i) * 60000L;
                if (iq > now + 1000 && iq > at && Prefs.iqamaSound(c) && Audio.iqama(c) != null) {
                    set(c, am, iq, i, true);
                    return;
                }
            }
        }
        am.cancel(pending(c, 0, 0, false));
    }

    private static PendingIntent pending(Context c, int prayer, long at, boolean iqama) {
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION_ADHAN)
                .putExtra(EXTRA_PRAYER, prayer).putExtra(EXTRA_TIME, at).putExtra(EXTRA_IQAMA, iqama);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, 1, i, flags);
    }

    private static void set(Context c, AlarmManager am, long at, int prayer, boolean iqama) {
        PendingIntent pi = pending(c, prayer, at, iqama);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
