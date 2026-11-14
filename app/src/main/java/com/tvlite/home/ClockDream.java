package com.tvlite.home;

import android.os.Handler;
import android.service.dreams.DreamService;

/** System screensaver (Daydream) for devices that support it. */
public class ClockDream extends DreamService {

    private final Handler handler = new Handler();
    private AmbientView view;

    private final Runnable tick = new Runnable() {
        public void run() {
            view.update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        view = new AmbientView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(tick);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }
}
