package com.tvlite.home;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;

/** Dark screensaver: big clock, dates and the next prayer. Drifts every minute to avoid burn-in. */
public class AmbientView extends FrameLayout {

    private final LinearLayout box;
    private final TextView time, ampm, date, hijri, next;
    private final Random random = new Random();
    private int lastMinute = -1;

    public AmbientView(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box = Ui.column(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout tr = Ui.row(c);
        time = Ui.text(c, 96, 0xFFE8E8E8, true);
        ampm = Ui.text(c, 30, 0xFF9AA4B2, false);
        ampm.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
        tr.addView(time);
        tr.addView(ampm);
        date = Ui.text(c, 24, 0xFFB0B8C4, false);
        hijri = Ui.text(c, 22, 0xFFC9A64B, false);
        next = Ui.text(c, 22, 0xFF7FC8A9, false);
        next.setPadding(0, Ui.dp(c, 14), 0, 0);
        box.addView(tr);
        box.addView(date);
        box.addView(hijri);
        box.addView(next);
        addView(box, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    public void update() {
        Calendar now = Calendar.getInstance();
        int mod = Fmt.minutesOfDay(now);
        time.setText(Fmt.hm12(mod));
        ampm.setText(Fmt.ampm(mod));
        if (mod == lastMinute) return;
        lastMinute = mod;
        int[] h = Hijri.of(now, 0);
        date.setText(Fmt.gregorian(now));
        hijri.setText(Hijri.format(h));
        int[] pt = PrayerTimes.compute(now, Config.LAT, Config.LNG, Config.TZ, h[1] == 9);
        next.setText(nextPrayerText(pt, mod));
        drift();
    }

    private void drift() {
        int w = getWidth(), hgt = getHeight();
        if (w == 0) return;
        int rx = Math.max(0, (w - box.getWidth()) / 2 - Ui.dp(getContext(), 20));
        int ry = Math.max(0, (hgt - box.getHeight()) / 2 - Ui.dp(getContext(), 20));
        box.setTranslationX(rx == 0 ? 0 : random.nextInt(rx * 2 + 1) - rx);
        box.setTranslationY(ry == 0 ? 0 : random.nextInt(ry * 2 + 1) - ry);
    }

    /** "العصر 3:12 م · بعد 1:05" */
    public static String nextPrayerText(int[] pt, int nowMinutes) {
        for (int i = 0; i < 6; i++) {
            if (i == 1) continue;
            if (pt[i] > nowMinutes) {
                return PrayerTimes.NAMES[i] + " " + Fmt.time(pt[i]) + "  ·  بعد " + Fmt.duration(pt[i] - nowMinutes);
            }
        }
        return PrayerTimes.NAMES[0] + " " + Fmt.time(pt[0]) + "  ·  بعد " + Fmt.duration(pt[0] + 1440 - nowMinutes);
    }
}
