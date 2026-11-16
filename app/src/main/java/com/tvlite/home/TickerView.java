package com.tvlite.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * News ticker. Headlines enter on the left and move to the right, so each Arabic headline is read
 * from its start (its right end comes in first). Each one carries a coloured tag with its section.
 */
public class TickerView extends View {

    private static final String RLE = "‫", PDF = "‬"; // keep Arabic lines right-to-left

    private static final class Seg {
        News.Item item;
        String title, source;
        float right, width, pillW, titleW, srcW;
    }

    private final List<Seg> segs = new ArrayList<Seg>();
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float gap, pad, barH, radius;
    private float speed; // px per second
    private boolean running;
    private long last;

    public TickerView(Context c) {
        super(c);
        title.setColor(Ui.TEXT);
        title.setTextSize(Ui.dp(c, 21));
        small.setTextSize(Ui.dp(c, 15));
        small.setFakeBoldText(true);
        bg.setColor(0x8C000000);
        gap = Ui.dp(c, 70);
        pad = Ui.dp(c, 10);
        barH = Ui.dp(c, 44);
        radius = Ui.dp(c, 8);
        setSpeed(1);
        setFocusable(false);
    }

    /** 0 slow, 1 normal, 2 fast */
    public void setSpeed(int s) {
        float dps = s <= 0 ? 45 : s == 1 ? 75 : 115;
        speed = dps * getResources().getDisplayMetrics().density;
    }

    /** Moves only while it is actually on screen, so no headline is used up unseen. */
    public void setRunning(boolean on) {
        if (on == running) return;
        running = on;
        last = SystemClock.uptimeMillis();
        if (on) postInvalidateOnAnimation();
    }

    public void clear() { segs.clear(); invalidate(); }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) barH);
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        last = SystemClock.uptimeMillis();
        if (v == VISIBLE && running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        long now = SystemClock.uptimeMillis();
        if (running) {
            float dx = speed * Math.min(now - last, 100) / 1000f;
            for (Seg s : segs) s.right += dx;
            while (!segs.isEmpty() && segs.get(0).right - segs.get(0).width > w) segs.remove(0);
            Seg lastSeg = segs.isEmpty() ? null : segs.get(segs.size() - 1);
            if (lastSeg == null || lastSeg.right - lastSeg.width >= gap) {
                News.Item it = News.next(getContext());
                if (it != null) {
                    Seg s = make(it);
                    s.right = lastSeg == null ? 0 : Math.min(lastSeg.right - lastSeg.width - gap, 0);
                    segs.add(s);
                }
            }
        }
        last = now;
        if (!segs.isEmpty()) {
            r.set(0, 0, w, h);
            c.drawRoundRect(r, radius, radius, bg);
            float base = h / 2f - (title.descent() + title.ascent()) / 2f;
            float sbase = h / 2f - (small.descent() + small.ascent()) / 2f;
            float ph = small.getTextSize() * 1.6f;
            for (Seg s : segs) {
                if (s.right < 0 || s.right - s.width > w) continue;
                float x = s.right;
                // section tag
                pill.setColor(s.item.color);
                r.set(x - s.pillW, h / 2f - ph / 2f, x, h / 2f + ph / 2f);
                c.drawRoundRect(r, ph / 2f, ph / 2f, pill);
                small.setColor(Ui.TEXT);
                c.drawText(s.item.label, x - s.pillW + pad, sbase, small);
                x -= s.pillW + pad * 1.2f;
                // headline
                c.drawText(s.title, x - s.titleW, base, title);
                x -= s.titleW + pad;
                // source
                small.setColor(Ui.MUTED);
                c.drawText(s.source, x - s.srcW, sbase, small);
                // separator
                small.setColor(Ui.ACCENT);
                c.drawText("◆", s.right - s.width - gap / 2f - small.measureText("◆") / 2f, sbase, small);
            }
        }
        if (running && getWindowVisibility() == VISIBLE) postInvalidateOnAnimation();
    }

    private Seg make(News.Item it) {
        Seg s = new Seg();
        s.item = it;
        s.title = RLE + it.title + PDF;
        s.source = RLE + "— " + it.source + PDF;
        s.pillW = small.measureText(it.label) + pad * 2;
        s.titleW = title.measureText(s.title);
        s.srcW = small.measureText(s.source);
        s.width = s.pillW + pad * 1.2f + s.titleW + pad + s.srcW;
        return s;
    }
}
