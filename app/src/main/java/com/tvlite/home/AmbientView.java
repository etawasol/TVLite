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
    private final LinearLayout digital, info;
    private final AnalogClockView analog;
    private final TickerView ticker;
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
        digital = tr;
        analog = new AnalogClockView(c);
        info = Ui.column(c);
        info.setGravity(Gravity.CENTER_HORIZONTAL);
        info.addView(tr);
        info.addView(date);
        info.addView(hijri);
        info.addView(next);
        box.addView(analog, new LinearLayout.LayoutParams(1, 1));
        box.addView(info);
        addView(box, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ticker = new TickerView(c);
        ticker.setVisibility(GONE);
        LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int m = Ui.dp(c, 16);
        tl.setMargins(m, 0, m, m);
        addView(ticker, tl);
        applyPrefs();
    }

    /**
     * Digital (-1) or one of the analog styles, from the settings.
     * The analog clock fills 80% of the screen height, with the dates and next prayer beside it.
     */
    public void applyPrefs() {
        int style = Prefs.clockStyle(getContext());
        boolean isAnalog = style >= 0;
        analog.setVisibility(isAnalog ? VISIBLE : GONE);
        digital.setVisibility(isAnalog ? GONE : VISIBLE);
        box.setOrientation(isAnalog ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        box.setGravity(isAnalog ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
        if (isAnalog) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int size = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.8f);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) analog.getLayoutParams();
            if (lp.width != size) {
                lp.width = size;
                lp.height = size;
                lp.setMargins(0, 0, 0, 0);
                lp.setMarginEnd(Ui.dp(getContext(), 36));
                analog.setLayoutParams(lp);
            }
            analog.setStyle(style);
            analog.setSeconds(Prefs.clockSeconds(getContext()));
        }
    }

    /** News ticker along the bottom of the screensaver (when chosen in the settings). */
    public void setTickerRunning(boolean on) {
        ticker.setVisibility(on ? VISIBLE : GONE);
        if (on) ticker.setSpeed(Prefs.newsSpeed(getContext()));
        ticker.setRunning(on);
    }

    public void update() {
        Calendar now = Clock.cal();
        int mod = Fmt.minutesOfDay(now);
        time.setText(Fmt.hm12(mod));
        ampm.setText(Fmt.ampm(mod));
        if (analog.getVisibility() == VISIBLE) analog.invalidate();
        if (mod == lastMinute) return;
        applyPrefs();
        lastMinute = mod;
        int[] h = Prayers.hijri(getContext(), now);
        date.setText(Fmt.gregorian(now));
        hijri.setText(Hijri.format(h));
        int[] pt = Prayers.times(getContext(), now);
        next.setText(nextPrayerText(pt, mod));
        drift();
    }

    /** Moves the clock to a random spot, after layout so sizes are current, keeping it fully on screen. */
    private void drift() {
        post(new Runnable() {
            public void run() {
                int w = getWidth(), hgt = getHeight();
                if (w == 0 || box.getWidth() == 0) return;
                int m = Ui.dp(getContext(), 20);
                int rx = Math.max(0, (w - box.getWidth()) / 2 - m);
                int ry = Math.max(0, (hgt - box.getHeight()) / 2 - m);
                box.setTranslationX(rx == 0 ? 0 : random.nextInt(rx * 2 + 1) - rx);
                box.setTranslationY(ry == 0 ? 0 : random.nextInt(ry * 2 + 1) - ry);
            }
        });
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
