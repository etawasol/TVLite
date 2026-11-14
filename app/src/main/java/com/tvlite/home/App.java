package com.tvlite.home;

import android.app.Application;

import java.util.TimeZone;

/** Shows Saudi time even on boxes whose system time zone is set wrong (e.g. America/New_York). */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        TimeZone.setDefault(TimeZone.getTimeZone(Config.TIME_ZONE));
    }
}
