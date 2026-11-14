package com.tvlite.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import java.util.Calendar;

/**
 * Wall-clock chimes, synthesised in the app (no sound files):
 * the traditional Westminster quarter melody every 15 minutes, and on the hour
 * one bell strike per hour (2 o'clock = 2 strikes, 12-hour count).
 */
public final class Chimes {

    public static final String ACTION = "com.tvlite.home.CHIME";
    public static final int OFF = 0, HOURS = 1, QUARTERS = 2;
    private static final int RATE = 22050;

    // E major: G#4 F#4 E4 B3
    private static final double GS = 415.30, FS = 369.99, E = 329.63, B = 246.94, STRIKE = 164.81;
    private static final double[][] CHANGES = {
            {GS, FS, E, B}, {E, GS, FS, B}, {E, FS, GS, E}, {GS, E, FS, B}, {B, FS, GS, E}};
    private static final int[][] QUARTER_CHANGES = {{}, {0}, {1, 2}, {3, 4, 0}, {1, 2, 3, 4}};

    private Chimes() {}

    public static int mode(Context c) { return Prefs.get(c).getInt("chimes", OFF); }
    public static boolean nightQuiet(Context c) { return Prefs.get(c).getBoolean("chimes_night_quiet", true); }

    /** True between 11 pm and 6 am when night quiet is on. */
    public static boolean quietNow(Context c, Calendar t) {
        int h = t.get(Calendar.HOUR_OF_DAY);
        return nightQuiet(c) && (h >= 23 || h < 6);
    }

    // ------------------------------------------------------------- scheduling

    public static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c);
        int mode = mode(c);
        if (mode == OFF) { am.cancel(pi); return; }
        Calendar t = Calendar.getInstance();
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        int step = mode == QUARTERS ? 15 : 60;
        int m = t.get(Calendar.MINUTE);
        t.add(Calendar.MINUTE, step - (m % step));
        long at = t.getTimeInMillis();
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, 2, i, flags);
    }

    /** Called by the alarm: decide what to ring now. */
    public static void onAlarm(Context c) {
        // Round to the nearest quarter hour (alarms can fire a little early or late).
        long q = 15 * 60 * 1000L;
        Calendar t = Calendar.getInstance();
        long local = t.getTimeInMillis() + t.get(Calendar.ZONE_OFFSET) + t.get(Calendar.DST_OFFSET);
        t.setTimeInMillis(t.getTimeInMillis() + (Math.round((double) local / q) * q - local));
        int quarter = t.get(Calendar.MINUTE) / 15;
        int mode = mode(c);
        if (mode == OFF || quietNow(c, t) || AdhanService.isPlaying()) return;
        if (mode == HOURS && quarter != 0) return;
        int hour12 = t.get(Calendar.HOUR) == 0 ? 12 : t.get(Calendar.HOUR);
        AdhanService.chime(c, quarter == 0 ? 4 : quarter, quarter == 0 ? hour12 : 0);
    }

    // ------------------------------------------------------------- sound

    /** Renders and plays the melody for a quarter (1-4) followed by `strikes` hour strikes. Blocks until done. */
    public static void play(int quarter, int strikes, float volume) {
        int[] changes = QUARTER_CHANGES[Math.max(0, Math.min(4, quarter))];
        double noteGap = 0.85, changeGap = 0.6, strikeGap = 2.0;
        double melody = changes.length * (4 * noteGap + changeGap);
        double total = melody + (strikes > 0 ? 0.8 + strikes * strikeGap : 0) + 3.5;
        short[] pcm = new short[(int) (total * RATE)];
        float[] mix = new float[pcm.length];
        double at = 0.1;
        for (int ch : changes) {
            for (double f : CHANGES[ch]) { bell(mix, at, f, 0.55, 2.6); at += noteGap; }
            at += changeGap;
        }
        if (strikes > 0) {
            at += 0.8;
            for (int s = 0; s < strikes; s++) { bell(mix, at, STRIKE, 0.9, 3.5); at += strikeGap; }
        }
        float peak = 0.0001f;
        for (float v : mix) peak = Math.max(peak, Math.abs(v));
        float gain = 0.9f / peak;
        for (int i = 0; i < mix.length; i++) pcm[i] = (short) (mix[i] * gain * 32767);

        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, pcm.length * 2, AudioTrack.MODE_STATIC);
        try {
            track.write(pcm, 0, pcm.length);
            track.setStereoVolume(volume, volume);
            track.play();
            Thread.sleep((long) (total * 1000));
        } catch (Exception ignored) {
        } finally {
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
        }
    }

    /** A church-bell-like tone: a few inharmonic partials with exponential decay. */
    private static void bell(float[] out, double start, double f, double amp, double decay) {
        double[] ratio = {0.5, 1.0, 1.19, 1.5, 2.0, 2.52, 3.0};
        double[] level = {0.35, 1.0, 0.45, 0.3, 0.25, 0.12, 0.08};
        int s0 = (int) (start * RATE);
        int len = (int) (decay * 2.2 * RATE);
        for (int i = 0; i < len && s0 + i < out.length; i++) {
            double t = (double) i / RATE;
            double attack = Math.min(1.0, t / 0.004);
            double v = 0;
            for (int k = 0; k < ratio.length; k++) {
                v += level[k] * Math.exp(-t * (1.0 + k * 0.6) / decay) * Math.sin(2 * Math.PI * f * ratio[k] * t);
            }
            out[s0 + i] += (float) (amp * attack * v);
        }
    }
}
