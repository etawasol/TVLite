package com.tvlite.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Mosque-style screen around each prayer:
 *   adhan time            -> "حان الآن وقت صلاة الظهر" + iqama countdown
 *   until the iqama       -> big countdown
 *   10 minutes after it   -> dim "أقيمت الصلاة"
 * BACK hides it for that prayer.
 */
public class PrayerOverlay extends FrameLayout {

    private static final long ADHAN_PHASE = 3 * 60 * 1000L;
    private static final long PRAYING = 10 * 60 * 1000L;

    private final TextView title, big, sub;
    private String dismissedKey;
    private String currentKey;

    public PrayerOverlay(Context c) {
        super(c);
        setBackgroundColor(0xE6000000);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout box = Ui.column(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        title = Ui.text(c, 34, Ui.ACCENT, true);
        big = Ui.text(c, 96, Ui.TEXT, true);
        sub = Ui.text(c, 22, Ui.MUTED, false);
        title.setGravity(Gravity.CENTER);
        big.setGravity(Gravity.CENTER);
        sub.setGravity(Gravity.CENTER);
        big.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.addView(title);
        box.addView(big);
        box.addView(sub);
        addView(box, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setVisibility(GONE);
    }

    /** Called every second. */
    public void update() {
        Context c = getContext();
        if (!Prefs.prayerScreen(c)) { setVisibility(GONE); return; }
        Calendar now = Calendar.getInstance();
        long t = now.getTimeInMillis();
        int[] pt = Prayers.times(c, now);
        for (int i : Prefs.PRAYERS) {
            long adhan = Prayers.at(now, pt[i]);
            long iqama = adhan + Prefs.iqama(c, i) * 60000L;
            if (t < adhan || t >= iqama + PRAYING) continue;
            currentKey = now.get(Calendar.DAY_OF_YEAR) + "_" + i;
            if (currentKey.equals(dismissedKey)) { setVisibility(GONE); return; }
            String name = Prayers.name(now, i);
            if (t < iqama) {
                long left = (iqama - t + 999) / 1000;
                title.setText(t < adhan + ADHAN_PHASE ? "حان الآن وقت صلاة " + name : "صلاة " + name);
                big.setText(Fmt.two((int) (left / 60)) + ":" + Fmt.two((int) (left % 60)));
                sub.setText("باقي على الإقامة");
                setBackgroundColor(0xE6000000);
            } else {
                title.setText("أقيمت صلاة " + name);
                big.setText("");
                sub.setText("الشاشة ترجع بعد الصلاة");
                setBackgroundColor(0xF2000000);
            }
            setVisibility(VISIBLE);
            return;
        }
        setVisibility(GONE);
    }

    public void dismiss() {
        dismissedKey = currentKey;
        setVisibility(GONE);
    }
}
