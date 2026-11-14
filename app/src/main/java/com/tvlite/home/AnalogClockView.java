package com.tvlite.home;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.Calendar;

/** Analog clock drawn on a canvas, in several styles. Smooth second hand. */
public class AnalogClockView extends View {

    public static final String[] STYLES = {
            "كلاسيكي", "أرقام عربية", "بسيط", "روماني ذهبي", "نيون", "دافئ", "ليلي بدون أرقام"};

    private static final String[] ARABIC = {"١٢", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "١٠", "١١"};
    private static final String[] WESTERN = {"12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"};
    private static final String[] ROMAN = {"XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int style;
    private boolean seconds = true;

    public AnalogClockView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // blur glow needs software rendering on old devices
    }

    public void setStyle(int s) { style = Math.max(0, Math.min(STYLES.length - 1, s)); invalidate(); }
    public void setSeconds(boolean on) { seconds = on; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f * 0.94f;
        Calendar now = Clock.cal();
        float sec = now.get(Calendar.SECOND) + now.get(Calendar.MILLISECOND) / 1000f;
        float min = now.get(Calendar.MINUTE) + sec / 60f;
        float hr = now.get(Calendar.HOUR) + min / 60f;

        int face, ring, ticks, text, hand, accent;
        switch (style) {
            case 1:  face = 0x33000000; ring = 0xFFE8E8E8; ticks = 0xCCFFFFFF; text = 0xFFFFFFFF; hand = 0xFFFFFFFF; accent = 0xFF7FC8A9; break;
            case 2:  face = 0x00000000; ring = 0x00000000; ticks = 0x99FFFFFF; text = 0;          hand = 0xFFF2F2F2; accent = 0xFFE5484D; break;
            case 3:  face = 0xFF14100A; ring = 0xFFC9A64B; ticks = 0xFFC9A64B; text = 0xFFE7CF8A; hand = 0xFFE7CF8A; accent = 0xFFC9A64B; break;
            case 4:  face = 0xFF02060C; ring = 0xFF39D5FF; ticks = 0xFF39D5FF; text = 0;          hand = 0xFFBDF3FF; accent = 0xFFFF4FD8; break;
            case 5:  face = 0xFF2A1408; ring = 0xFFFF9A3C; ticks = 0xFFFFB46B; text = 0xFFFFD2A1; hand = 0xFFFFE2C2; accent = 0xFFFF6A2B; break;
            case 6:  face = 0xFF0A0F1C; ring = 0xFF2E3A55; ticks = 0xFF5A6B8F; text = 0;          hand = 0xFFB0BCD6; accent = 0xFFC9A64B; break;
            default: face = 0xFFF4F1EA; ring = 0xFF2B2B2B; ticks = 0xFF2B2B2B; text = 0xFF1E1E1E; hand = 0xFF1E1E1E; accent = 0xFFC0392B; break;
        }

        // face
        p.setMaskFilter(null);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        if (style == 5) {
            p.setShader(new RadialGradient(cx, cy, r, 0xFF4A2410, 0xFF1A0A04, Shader.TileMode.CLAMP));
        }
        p.setColor(face);
        if (Color.alpha(face) > 0 || style == 5) c.drawCircle(cx, cy, r, p);
        p.setShader(null);

        // ring (with glow for neon)
        if (Color.alpha(ring) > 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * (style == 3 ? 0.05f : 0.03f));
            p.setColor(ring);
            if (style == 4) p.setMaskFilter(new BlurMaskFilter(r * 0.04f, BlurMaskFilter.Blur.SOLID));
            c.drawCircle(cx, cy, r * 0.97f, p);
            p.setMaskFilter(null);
        }

        // ticks
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 60; i++) {
            boolean hour = i % 5 == 0;
            if (!hour && (style == 2 || style == 6)) continue;
            float a = (float) Math.toRadians(i * 6 - 90);
            float outer = r * 0.9f, inner = r * (hour ? 0.78f : 0.85f);
            if (style == 2 && i % 15 != 0) inner = r * 0.84f;
            p.setStrokeWidth(r * (hour ? 0.025f : 0.008f));
            p.setColor(ticks);
            if (style == 4) p.setMaskFilter(new BlurMaskFilter(r * 0.02f, BlurMaskFilter.Blur.SOLID));
            c.drawLine(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * outer, cy + (float) Math.sin(a) * outer, p);
            p.setMaskFilter(null);
        }

        // numbers
        if (text != 0) {
            String[] nums = style == 1 ? ARABIC : style == 3 ? ROMAN : WESTERN;
            p.setStyle(Paint.Style.FILL);
            p.setColor(text);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(style == 3 ? Typeface.SERIF : Typeface.DEFAULT_BOLD);
            p.setTextSize(r * (style == 3 ? 0.13f : 0.16f));
            for (int i = 0; i < 12; i++) {
                float a = (float) Math.toRadians(i * 30 - 90);
                float tr = r * 0.64f;
                float x = cx + (float) Math.cos(a) * tr;
                float y = cy + (float) Math.sin(a) * tr - (p.descent() + p.ascent()) / 2f;
                c.drawText(nums[i], x, y, p);
            }
        }

        // hands
        if (style == 4) p.setMaskFilter(new BlurMaskFilter(r * 0.03f, BlurMaskFilter.Blur.SOLID));
        hand(c, cx, cy, hr * 30, r * 0.48f, r * 0.055f, hand);
        hand(c, cx, cy, min * 6, r * 0.72f, r * 0.035f, hand);
        if (seconds) hand(c, cx, cy, sec * 6, r * 0.8f, r * 0.012f, accent);
        p.setMaskFilter(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(seconds ? accent : hand);
        c.drawCircle(cx, cy, r * 0.04f, p);
    }

    private void hand(Canvas c, float cx, float cy, float deg, float len, float width, int color) {
        float a = (float) Math.toRadians(deg - 90);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(width);
        p.setColor(color);
        float back = len * 0.15f;
        c.drawLine(cx - (float) Math.cos(a) * back, cy - (float) Math.sin(a) * back,
                cx + (float) Math.cos(a) * len, cy + (float) Math.sin(a) * len, p);
    }
}
