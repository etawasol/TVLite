package com.tvlite.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final long WEATHER_EVERY = 30 * 60 * 1000L;
    private static final long MATCHES_EVERY = 15 * 60 * 1000L;
    private static final long MATCHES_LIVE_EVERY = 2 * 60 * 1000L;

    private final Handler handler = new Handler();
    private SharedPreferences cache;

    private TextView clock, ampm, date, hijri, nextPrayer;
    private TextView weatherTemp, weatherDesc, weatherRange;
    private final TextView[] prayerName = new TextView[6];
    private final TextView[] prayerTime = new TextView[6];
    private final LinearLayout[] prayerCard = new LinearLayout[6];
    private LinearLayout matchesList, appsRow;
    private TextView matchesStatus;
    private AmbientView ambient;

    private int lastMinute = -1;
    private long lastInput = System.currentTimeMillis();
    private long nextWeather, nextMatches;
    private boolean resumed;
    private volatile String matchesError;

    private final Runnable tick = new Runnable() {
        public void run() {
            onTick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        cache = getSharedPreferences("cache", MODE_PRIVATE);
        setContentView(build());
        showWeather(cache.getString("weather", null));
        showMatches(null, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        lastInput = System.currentTimeMillis();
        loadApps();
        lastMinute = -1;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(tick);
        super.onPause();
    }

    // ---------------------------------------------------------------- layout

    private View build() {
        Context c = this;
        FrameLayout root = new FrameLayout(c);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0E2A47, 0xFF0A1A30, 0xFF050C18});
        root.setBackgroundDrawable(bg);

        LinearLayout main = Ui.column(c);
        int pad = Ui.dp(c, 22);
        main.setPadding(pad, Ui.dp(c, 16), pad, Ui.dp(c, 10));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // top: clock and dates | weather
        LinearLayout top = Ui.row(c);
        LinearLayout clockCol = Ui.column(c);
        LinearLayout timeRow = Ui.row(c);
        clock = Ui.text(c, 56, Ui.TEXT, true);
        ampm = Ui.text(c, 22, Ui.MUTED, false);
        ampm.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        timeRow.addView(clock);
        timeRow.addView(ampm);
        date = Ui.text(c, 18, Ui.TEXT, false);
        hijri = Ui.text(c, 17, Ui.ACCENT, false);
        hijri.setPadding(0, Ui.dp(c, 4), 0, 0);
        clockCol.addView(timeRow);
        clockCol.addView(date);
        clockCol.addView(hijri);
        top.addView(clockCol, Ui.lp(0, -2, 1));

        LinearLayout weather = Ui.card(c);
        weather.setGravity(Gravity.CENTER);
        weather.setMinimumWidth(Ui.dp(c, 190));
        TextView city = Ui.text(c, 14, Ui.MUTED, false);
        city.setText(Config.CITY);
        weatherTemp = Ui.text(c, 34, Ui.TEXT, true);
        weatherDesc = Ui.text(c, 15, Ui.TEXT, false);
        weatherRange = Ui.text(c, 13, Ui.MUTED, false);
        weather.addView(city);
        weather.addView(weatherTemp);
        weather.addView(weatherDesc);
        weather.addView(weatherRange);
        top.addView(weather, Ui.lp(-2, -2, 0));
        main.addView(top);

        // prayers
        LinearLayout prayers = Ui.row(c);
        prayers.setPadding(0, Ui.dp(c, 12), 0, 0);
        for (int i = 0; i < 6; i++) {
            LinearLayout card = Ui.card(c);
            card.setGravity(Gravity.CENTER);
            card.setPadding(Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
            prayerName[i] = Ui.text(c, 15, Ui.MUTED, false);
            prayerName[i].setText(PrayerTimes.NAMES[i]);
            prayerTime[i] = Ui.text(c, 21, Ui.TEXT, true);
            prayerTime[i].setPadding(0, Ui.dp(c, 4), 0, 0);
            card.addView(prayerName[i]);
            card.addView(prayerTime[i]);
            LinearLayout.LayoutParams lp = Ui.lp(0, -2, 1);
            lp.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            prayerCard[i] = card;
            prayers.addView(card, lp);
        }
        main.addView(prayers);
        nextPrayer = Ui.text(c, 16, Ui.ACCENT, false);
        nextPrayer.setGravity(Gravity.CENTER);
        nextPrayer.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 8));
        main.addView(nextPrayer, Ui.lp(-1, -2, 0));

        // matches
        LinearLayout matches = Ui.card(c);
        LinearLayout head = Ui.row(c);
        TextView title = Ui.text(c, 17, Ui.TEXT, true);
        title.setText("⚽  مباريات فرقك");
        matchesStatus = Ui.text(c, 13, Ui.MUTED, false);
        matchesStatus.setGravity(Gravity.END);
        head.addView(title, Ui.lp(0, -2, 1));
        head.addView(matchesStatus);
        matches.addView(head);
        matchesList = Ui.column(c);
        matchesList.setPadding(0, Ui.dp(c, 4), 0, 0);
        matches.addView(matchesList, Ui.lp(-1, -2, 0));
        main.addView(matches, Ui.lp(-1, 0, 1));

        // apps
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFillViewport(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 4));
        appsRow = Ui.row(c);
        hs.addView(appsRow);
        main.addView(hs, Ui.lp(-1, -2, 0));

        ambient = new AmbientView(c);
        ambient.setVisibility(View.GONE);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    // ---------------------------------------------------------------- clock and prayers

    private void onTick() {
        Calendar now = Calendar.getInstance();
        int mod = Fmt.minutesOfDay(now);
        clock.setText(Fmt.hm12(mod));
        ampm.setText(Fmt.ampm(mod));
        if (ambient.getVisibility() == View.VISIBLE) ambient.update();

        if (mod != lastMinute) {
            lastMinute = mod;
            int[] h = Hijri.of(now, 0);
            date.setText(Fmt.gregorian(now));
            hijri.setText(Hijri.format(h));
            int[] pt = PrayerTimes.compute(now, Config.LAT, Config.LNG, Config.TZ, h[1] == 9);
            int next = -1;
            for (int i = 0; i < 6; i++) {
                prayerTime[i].setText(Fmt.time(pt[i]));
                if (next < 0 && i != 1 && pt[i] > mod) next = i;
            }
            if (next < 0) next = 0;
            for (int i = 0; i < 6; i++) {
                prayerCard[i].setBackgroundDrawable(Ui.round(this, i == next ? Ui.CARD_HI : Ui.CARD, 14));
                prayerName[i].setTextColor(i == next ? Ui.TEXT : Ui.MUTED);
            }
            nextPrayer.setText("الصلاة القادمة: " + AmbientView.nextPrayerText(pt, mod));
        }

        long t = System.currentTimeMillis();
        if (t >= nextWeather) { nextWeather = t + WEATHER_EVERY; fetchWeather(); }
        if (t >= nextMatches) { nextMatches = t + MATCHES_EVERY; fetchMatches(); }

        if (resumed && ambient.getVisibility() != View.VISIBLE
                && t - lastInput > Config.IDLE_MINUTES * 60 * 1000L) {
            showAmbient(true);
        }
    }

    private void showAmbient(boolean on) {
        ambient.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) ambient.update();
    }

    // ---------------------------------------------------------------- weather

    private void fetchWeather() {
        new Thread(new Runnable() {
            public void run() {
                Net.init(MainActivity.this);
                try {
                    final String json = Net.get(Weather.url(Config.LAT, Config.LNG));
                    Weather.parse(json);
                    cache.edit().putString("weather", json).apply();
                    handler.post(new Runnable() { public void run() { showWeather(json); } });
                } catch (Exception e) {
                    android.util.Log.w("TVLite", "weather failed", e);
                    nextWeather = System.currentTimeMillis() + 5 * 60 * 1000L;
                }
            }
        }).start();
    }

    private void showWeather(String json) {
        if (json == null) {
            weatherTemp.setText("--°");
            weatherDesc.setText("جاري التحميل");
            weatherRange.setText("");
            return;
        }
        try {
            Weather w = Weather.parse(json);
            weatherTemp.setText(w.symbol() + " " + w.temp + "°");
            weatherDesc.setText(w.description());
            weatherRange.setText("العظمى " + w.max + "°  الصغرى " + w.min + "°");
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- matches

    private void fetchMatches() {
        final Calendar now = Calendar.getInstance();
        new Thread(new Runnable() {
            public void run() {
                Net.init(MainActivity.this);
                boolean any = false;
                String err = null;
                SharedPreferences.Editor ed = cache.edit();
                for (String[] l : Matches.LEAGUES) {
                    List<String> urls = Matches.urls(l[0], now);
                    for (int k = 0; k < 3; k++) {
                        String key = "m_" + l[0] + "_" + k;
                        if (k >= urls.size()) { ed.remove(key); continue; }
                        try {
                            ed.putString(key, Net.get(urls.get(k)));
                            any = true;
                        } catch (Exception e) {
                            android.util.Log.w("TVLite", "matches " + urls.get(k) + " failed", e);
                            if (err == null) err = Net.reason(e);
                        }
                    }
                }
                ed.apply();
                matchesError = any ? null : err;
                final boolean ok = any;
                if (!ok) nextMatches = System.currentTimeMillis() + 5 * 60 * 1000L;
                handler.post(new Runnable() { public void run() { showMatches(null, !ok); } });
            }
        }).start();
    }

    private void showMatches(String unused, boolean failed) {
        List<Matches.Match> all = new ArrayList<Matches.Match>();
        for (String[] l : Matches.LEAGUES) {
            for (int k = 0; k < 3; k++) {
                String json = cache.getString("m_" + l[0] + "_" + k, null);
                if (json == null) continue;
                try { Matches.parse(json, l[1], all); } catch (Exception ignored) { }
            }
        }
        List<Matches.Match> list = Matches.arrange(all, 5);
        matchesList.removeAllViews();
        boolean live = false;
        for (Matches.Match m : list) {
            matchesList.addView(matchRow(m));
            if ("in".equals(m.state)) live = true;
        }
        if (list.isEmpty()) {
            TextView t = Ui.text(this, 15, Ui.MUTED, false);
            t.setText(failed ? "ما قدرت أجيب المباريات، بحاول مرة ثانية بعد شوي" : "جاري تحميل المباريات…");
            t.setPadding(0, Ui.dp(this, 8), 0, 0);
            matchesList.addView(t);
            if (failed && matchesError != null) {
                TextView why = Ui.text(this, 12, Ui.MUTED, false);
                why.setText(matchesError);
                why.setTextDirection(View.TEXT_DIRECTION_LTR);
                why.setPadding(0, Ui.dp(this, 4), 0, 0);
                matchesList.addView(why);
            }
        }
        if (live) nextMatches = Math.min(nextMatches, System.currentTimeMillis() + MATCHES_LIVE_EVERY);
        matchesStatus.setText(live ? "مباشر الحين" : (failed ? "بدون اتصال" : ""));
        matchesStatus.setTextColor(live ? Ui.LIVE : Ui.MUTED);
    }

    private View matchRow(Matches.Match m) {
        Context c = this;
        LinearLayout r = Ui.row(c);
        r.setPadding(0, Ui.dp(c, 5), 0, Ui.dp(c, 5));

        TextView home = Ui.text(c, 17, Ui.TEXT, true);
        home.setText(m.home);
        home.setGravity(Gravity.CENTER);
        TextView mid = Ui.text(c, 17, Ui.ACCENT, true);
        mid.setGravity(Gravity.CENTER);
        TextView away = Ui.text(c, 17, Ui.TEXT, true);
        away.setText(m.away);
        away.setGravity(Gravity.CENTER);
        TextView when = Ui.text(c, 14, Ui.MUTED, false);
        TextView league = Ui.text(c, 13, Ui.MUTED, false);
        league.setText(m.league);
        league.setGravity(Gravity.END);

        if ("pre".equals(m.state)) {
            mid.setText("×");
            when.setText(Matches.when(m.time));
        } else {
            // scores are written home-away; in RTL the home side sits on the right
            mid.setText(m.awayScore + " - " + m.homeScore);
            if ("in".equals(m.state)) {
                when.setText("● مباشر " + (TextUtils.isEmpty(m.detail) ? "" : m.detail));
                when.setTextColor(Ui.LIVE);
            } else {
                when.setText("انتهت · " + Matches.when(m.time));
            }
        }
        mid.setTextDirection(View.TEXT_DIRECTION_LTR);
        r.addView(home, Ui.lp(0, -2, 2));
        r.addView(mid, Ui.lp(0, -2, 1.2f));
        r.addView(away, Ui.lp(0, -2, 2));
        r.addView(when, Ui.lp(0, -2, 2.6f));
        r.addView(league, Ui.lp(0, -2, 1.8f));
        return r;
    }

    // ---------------------------------------------------------------- apps

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = new ArrayList<ResolveInfo>();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        found.addAll(pm.queryIntentActivities(i, 0));
        if (Build.VERSION.SDK_INT >= 21) {
            Intent tv = new Intent(Intent.ACTION_MAIN);
            tv.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            found.addAll(pm.queryIntentActivities(tv, 0));
        }
        final List<Object[]> apps = new ArrayList<Object[]>(); // {label, pkg, icon}
        Set<String> seen = new HashSet<String>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            CharSequence label = ri.loadLabel(pm);
            apps.add(new Object[]{label == null ? pkg : label.toString(), pkg, ri.loadIcon(pm)});
        }
        final Collator col = Collator.getInstance(new Locale("ar"));
        Collections.sort(apps, new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) { return col.compare((String) a[0], (String) b[0]); }
        });

        View focused = getCurrentFocus();
        String focusedTag = focused != null && focused.getTag() != null ? focused.getTag().toString() : null;
        appsRow.removeAllViews();

        Drawable settingsIcon = null;
        try { settingsIcon = pm.getApplicationIcon("com.android.settings"); } catch (Exception ignored) { }
        appsRow.addView(tile("⚙", "الإعدادات", settingsIcon, "#settings", new Runnable() {
            public void run() { launch(new Intent(Settings.ACTION_SETTINGS)); }
        }));
        appsRow.addView(tile("☾", "شاشة التوقف", null, "#ambient", new Runnable() {
            public void run() { showAmbient(true); }
        }));
        for (final Object[] a : apps) {
            appsRow.addView(tile(null, (String) a[0], (Drawable) a[2], (String) a[1], new Runnable() {
                public void run() { launchPackage((String) a[1]); }
            }));
        }
        View restore = focusedTag == null ? null : appsRow.findViewWithTag(focusedTag);
        if (restore == null && appsRow.getChildCount() > 2) restore = appsRow.getChildAt(2);
        if (restore != null) restore.requestFocus();
    }

    private View tile(String glyph, String label, Drawable icon, String tag, final Runnable action) {
        Context c = this;
        final LinearLayout t = Ui.column(c);
        t.setGravity(Gravity.CENTER);
        t.setTag(tag);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
        t.setBackgroundDrawable(Ui.round(c, Ui.CARD, 12));
        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            t.addView(iv, new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46)));
        } else {
            TextView g = Ui.text(c, 32, Ui.TEXT, false);
            g.setText(glyph);
            g.setGravity(Gravity.CENTER);
            t.addView(g, new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46)));
        }
        TextView l = Ui.text(c, 13, Ui.TEXT, false);
        l.setText(label);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Ui.dp(c, 6), 0, 0);
        t.addView(l, new LinearLayout.LayoutParams(-1, -2));
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackgroundDrawable(Ui.round(v.getContext(), has ? Ui.CARD_HI : Ui.CARD, 12));
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(120).start();
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 112), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4));
        t.setLayoutParams(lp);
        return t;
    }

    private void launchPackage(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = null;
        if (Build.VERSION.SDK_INT >= 21) i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) launch(i);
    }

    private void launch(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        lastInput = System.currentTimeMillis();
        if (ambient.getVisibility() == View.VISIBLE) {
            if (e.getAction() == KeyEvent.ACTION_UP) showAmbient(false);
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            launch(new Intent(Settings.ACTION_SETTINGS));
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        lastInput = System.currentTimeMillis();
        if (ambient.getVisibility() == View.VISIBLE) {
            if (e.getAction() == MotionEvent.ACTION_UP) showAmbient(false);
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    public void onBackPressed() {
        Intent i = getIntent();
        if (i != null && i.hasCategory(Intent.CATEGORY_HOME)) return; // we are the home screen
        super.onBackPressed();
    }
}
