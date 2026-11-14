package com.tvlite.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires at prayer time; also re-arms the alarm after a reboot, time change or app update. */
public class AdhanReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        if (Chimes.ACTION.equals(intent.getAction())) {
            Chimes.onAlarm(c);
            Chimes.scheduleNext(c);
            return;
        }
        Chimes.scheduleNext(c);
        if (AdhanScheduler.ACTION_ADHAN.equals(intent.getAction())) {
            int prayer = intent.getIntExtra(AdhanScheduler.EXTRA_PRAYER, -1);
            long at = intent.getLongExtra(AdhanScheduler.EXTRA_TIME, 0);
            // Skip an adhan that is badly late (device was off), but allow a few minutes of drift.
            if (prayer >= 0 && Math.abs(System.currentTimeMillis() - at) < 5 * 60 * 1000L) {
                if (intent.getBooleanExtra(AdhanScheduler.EXTRA_IQAMA, false)) {
                    String iq = Audio.iqama(c);
                    if (iq != null) AdhanService.play(c, iq, "الإقامة", false);
                } else {
                    AdhanService.start(c, prayer);
                }
                if (Prefs.bringToFront(c) && !intent.getBooleanExtra(AdhanScheduler.EXTRA_IQAMA, false)) {
                    try {
                        c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) { }
                }
            }
        }
        AdhanScheduler.scheduleNext(c);
    }
}
