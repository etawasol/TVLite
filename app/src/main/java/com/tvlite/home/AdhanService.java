package com.tvlite.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

/** Plays the adhan. Works while another app is on screen. */
public class AdhanService extends Service {

    public static final String EXTRA_PATH = "path";
    private static final String ACTION_STOP = "stop";
    private static final int SHORT_SECONDS = 45;

    private static volatile boolean playing;
    private MediaPlayer player;
    private final Handler handler = new Handler();

    public static boolean isPlaying() { return playing; }

    public static void start(Context c, int prayer) {
        String path = Audio.forPrayer(c, prayer);
        if (path == null) return;
        play(c, path, PrayerTimes.NAMES[prayer]);
    }

    public static void play(Context c, String path, String title) {
        Intent i = new Intent(c, AdhanService.class).putExtra(EXTRA_PATH, path).putExtra("title", title);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) { }
    }

    public static void stop(Context c) {
        try {
            c.startService(new Intent(c, AdhanService.class).setAction(ACTION_STOP));
        } catch (Exception ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) foreground(intent == null ? "" : intent.getStringExtra("title"));
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            finish();
            return START_NOT_STICKY;
        }
        release();
        String path = intent.getStringExtra(EXTRA_PATH);
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(path);
            final float vol = Prefs.volume(this) / 10f;
            player.setVolume(vol, vol);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) { finish(); }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int what, int extra) { finish(); return true; }
            });
            player.prepare();
            player.start();
            playing = true;
            if (Prefs.shortAdhan(this) && !"الإقامة".equals(intent.getStringExtra("title"))) fadeOutAfter(SHORT_SECONDS * 1000L, vol);
        } catch (Exception e) {
            android.util.Log.w("TVLite", "adhan failed: " + path, e);
            finish();
        }
        return START_NOT_STICKY;
    }

    /** Short adhan: play the opening, then fade out over three seconds. */
    private void fadeOutAfter(long ms, final float vol) {
        for (int s = 0; s <= 10; s++) {
            final float v = vol * (1f - s / 10f);
            final boolean last = s == 10;
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (player == null) return;
                    if (last) { finish(); return; }
                    try { player.setVolume(v, v); } catch (Exception ignored) { }
                }
            }, ms + s * 300L);
        }
    }

    private void foreground(String title) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(
                new NotificationChannel("adhan", "الأذان", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "adhan")
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("الأذان " + (title == null ? "" : title))
                .build();
        startForeground(7, n);
    }

    private void release() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
        playing = false;
    }

    private void finish() {
        release();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
