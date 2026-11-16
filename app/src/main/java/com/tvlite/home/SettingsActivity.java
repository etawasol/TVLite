package com.tvlite.home;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Prayer settings, driven by the remote: up/down to move, left/right to change, OK to toggle or play. */
public class SettingsActivity extends Activity {

    private SharedPreferences prefs;
    private LinearLayout list;
    private final List<Row> rows = new ArrayList<Row>();
    private boolean previewed;
    private AmbientView preview;
    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable previewTick = new Runnable() {
        public void run() {
            if (preview.getVisibility() != View.VISIBLE) return;
            preview.update();
            handler.postDelayed(this, 1000);
        }
    };

    private void showPreview() {
        preview.applyPrefs();
        preview.setVisibility(View.VISIBLE);
        handler.post(previewTick);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (preview != null && preview.getVisibility() == View.VISIBLE) {
            if (e.getAction() == KeyEvent.ACTION_UP) preview.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private abstract class Row {
        final String title;
        TextView value;
        Row(String title) { this.title = title; }
        abstract String value();
        void change(int dir) { }
        void press() { change(1); }
    }

    private class Toggle extends Row {
        final String key; final boolean def;
        Toggle(String title, String key, boolean def) { super(title); this.key = key; this.def = def; }
        String value() { return prefs.getBoolean(key, def) ? "شغّال" : "موقّف"; }
        void change(int dir) { prefs.edit().putBoolean(key, !prefs.getBoolean(key, def)).apply(); }
    }

    private class Number extends Row {
        final String key; final int def, min, max; final String unit; final boolean signed;
        Number(String title, String key, int def, int min, int max, String unit, boolean signed) {
            super(title); this.key = key; this.def = def; this.min = min; this.max = max; this.unit = unit; this.signed = signed;
        }
        String value() {
            int v = prefs.getInt(key, def);
            return (signed && v > 0 ? "+" : "") + v + (unit.length() > 0 ? " " + unit : "");
        }
        void change(int dir) {
            int v = Math.max(min, Math.min(max, prefs.getInt(key, def) + dir));
            prefs.edit().putInt(key, v).apply();
        }
        void press() { }
    }

    /** Pick one recording. "" means none / same as the main muezzin. */
    private class Choice extends Row {
        final String key; final String emptyLabel;
        Choice(String title, String key, String emptyLabel) { super(title); this.key = key; this.emptyLabel = emptyLabel; }
        List<String> options() {
            List<String> o = new ArrayList<String>();
            o.add("");
            for (File f : Audio.all(SettingsActivity.this)) o.add(f.getAbsolutePath());
            return o;
        }
        String value() {
            String v = prefs.getString(key, "");
            return v.length() == 0 ? emptyLabel : Audio.label(SettingsActivity.this, v);
        }
        void change(int dir) {
            List<String> o = options();
            int i = o.indexOf(prefs.getString(key, ""));
            i = ((i < 0 ? 0 : i) + dir + o.size()) % o.size();
            prefs.edit().putString(key, o.get(i)).apply();
        }
    }

    private class Action extends Row {
        final Runnable run; final String label;
        Action(String title, String label, Runnable run) { super(title); this.label = label; this.run = run; }
        String value() { return label; }
        void press() { run.run(); }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = Prefs.get(this);
        try { Audio.publicFolder().mkdirs(); } catch (Exception ignored) { }

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0E2A47, 0xFF050C18}));
        list = Ui.column(this);
        int p = Ui.dp(this, 28);
        list.setPadding(p, Ui.dp(this, 20), p, Ui.dp(this, 40));
        scroll.addView(list);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(scroll);
        preview = new AmbientView(this);
        preview.setVisibility(View.GONE);
        root.addView(preview);
        setContentView(root);

        TextView title = Ui.text(this, 26, Ui.TEXT, true);
        title.setText("إعدادات الصلاة");
        list.addView(title);
        TextView hint = Ui.text(this, 14, Ui.MUTED, false);
        hint.setText("فوق وتحت للتنقل · يمين ويسار للتغيير · OK للتشغيل والإيقاف · رجوع للحفظ والخروج");
        hint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        list.addView(hint);

        header("الأذان");
        add(new Choice("المؤذن", "muezzin", "تلقائي (أول تسجيل)"));
        add(new Choice("أذان الفجر", "muezzin_fajr", "نفس المؤذن"));
        add(new Number("مستوى الصوت", "volume", 8, 1, 10, "من 10", false));
        add(new Action("تجربة الأذان", "▶ تشغيل / إيقاف", new Runnable() {
            public void run() { preview(2); }
        }));
        add(new Action("تجربة أذان الفجر", "▶ تشغيل / إيقاف", new Runnable() {
            public void run() { preview(0); }
        }));
        for (int i : Prefs.PRAYERS) add(new Toggle("أذان " + PrayerTimes.NAMES[i], "adhan_on_" + i, true));
        header("طول الأذان: كامل أو مختصر (أول 45 ثانية ثم يخفت)");
        for (int i : Prefs.PRAYERS) {
            add(new Toggle("أذان " + PrayerTimes.NAMES[i], "short_" + i, false) {
                String value() { return prefs.getBoolean(key, def) ? "مختصر" : "كامل"; }
            });
        }

        header("الإقامة: كم دقيقة بعد الأذان");
        add(new Toggle("صوت الإقامة", "iqama_sound", true));
        int[] iq = {25, 0, 20, 20, 10, 20};
        for (int i : Prefs.PRAYERS) add(new Number("إقامة " + PrayerTimes.NAMES[i], "iqama_" + i, iq[i], 0, 60, "دقيقة", false));

        header("تعديل الأوقات بالدقائق");
        for (int i = 0; i < 6; i++) add(new Number(PrayerTimes.NAMES[i], "adj_" + i, 0, -30, 30, "دقيقة", true));

        header("دقات الساعة");
        add(new Number("الدقات", "chimes", Chimes.OFF, 0, 2, "", false) {
            String value() {
                int v = prefs.getInt(key, def);
                return v == Chimes.QUARTERS ? "كل ربع ساعة + عدد الساعة" : v == Chimes.HOURS ? "عدد الساعة كل ساعة" : "موقّفة";
            }
            void change(int dir) { prefs.edit().putInt(key, ((prefs.getInt(key, def) + dir) % 3 + 3) % 3).apply(); }
            void press() { change(1); }
        });
        add(new Number("صوت الدقات", "chimes_volume", 6, 1, 10, "من 10", false));
        add(new Toggle("إيقافها بالليل (11 م - 6 ص)", "chimes_night_quiet", true));
        add(new Action("تجربة الدقات", "▶ ربع ساعة / الساعة 3", new Runnable() {
            int n;
            public void run() {
                if (n++ % 2 == 0) AdhanService.chime(SettingsActivity.this, 1, 0);
                else AdhanService.chime(SettingsActivity.this, 4, 3);
            }
        }));

        header("شاشة التوقف");
        add(new Number("شكل الساعة", "clock_style", -1, -1, AnalogClockView.STYLES.length - 1, "", false) {
            String value() {
                int v = prefs.getInt(key, def);
                return v < 0 ? "رقمية" : "عقارب: " + AnalogClockView.STYLES[v];
            }
            void change(int dir) {
                int n = AnalogClockView.STYLES.length + 1;
                int v = ((prefs.getInt(key, def) + 1 + dir) % n + n) % n - 1;
                prefs.edit().putInt(key, v).apply();
            }
            void press() { change(1); }
        });
        add(new Toggle("عقرب الثواني", "clock_seconds", true));
        add(new Action("معاينة شاشة التوقف", "▶ عرض", new Runnable() {
            public void run() { showPreview(); }
        }));

        header("شريط الأخبار");
        add(new Toggle("إظهار شريط الأخبار", "news_on", true));
        for (Object[] cat : News.CATS) add(new Toggle("أخبار " + cat[1], "news_cat_" + cat[0], true));
        add(new Number("سرعة الشريط", "news_speed", 1, 0, 2, "", false) {
            String value() {
                int v = prefs.getInt(key, def);
                return v == 0 ? "بطيء" : v == 2 ? "سريع" : "عادي";
            }
        });
        add(new Toggle("وسم «عاجل» للأخبار الجديدة (آخر نصف ساعة)", "news_breaking", true));
        add(new Toggle("نتائج مباريات فرقك المباشرة والأهداف", "news_live", true));
        add(new Toggle("تنبيه قبل الأذان بـ 10 دقائق", "news_prayer", true));
        add(new Toggle("الشريط في شاشة التوقف", "news_ambient", false));

        header("الشاشة");
        add(new Toggle("شاشة الأذان وعدّاد الإقامة", "prayer_screen", true));
        add(new Toggle("افتح شاشتي وقت الأذان فوق أي تطبيق", "bring_front", false));
        add(new Number("تعديل التاريخ الهجري", "hijri_offset", 0, -2, 2, "يوم", true));

        TextView note = Ui.text(this, 13, Ui.MUTED, false);
        note.setSingleLine(false);
        note.setText("تقدر تضيف أصوات أذان بنفسك: حط ملفات mp3 في المجلد " + Audio.publicFolder().getAbsolutePath());
        note.setPadding(0, Ui.dp(this, 16), 0, 0);
        list.addView(note);

        if (list.getChildCount() > 0) {
            for (int i = 0; i < list.getChildCount(); i++) {
                View v = list.getChildAt(i);
                if (v.isFocusable()) { v.requestFocus(); break; }
            }
        }
    }

    private void header(String text) {
        TextView h = Ui.text(this, 17, Ui.ACCENT, true);
        h.setText(text);
        h.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        list.addView(h);
    }

    private void add(final Row row) {
        rows.add(row);
        final LinearLayout r = Ui.row(this);
        r.setFocusable(true);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        r.setBackgroundDrawable(Ui.round(this, 0x22FFFFFF, 10));
        TextView t = Ui.text(this, 18, Ui.TEXT, false);
        t.setText(row.title);
        row.value = Ui.text(this, 18, Ui.ACCENT, true);
        row.value.setText(row.value());
        r.addView(t, Ui.lp(0, -2, 1));
        r.addView(row.value);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2, 0);
        lp.setMargins(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackgroundDrawable(Ui.round(v.getContext(), has ? Ui.CARD_HI : 0x22FFFFFF, 10));
            }
        });
        r.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                // The screen is right-to-left, so "right" means less and "left" means more.
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) { row.change(1); } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { row.change(-1); } else if (code == KeyEvent.KEYCODE_DPAD_CENTER
                        || code == KeyEvent.KEYCODE_ENTER) { row.press(); } else return false;
                row.value.setText(row.value());
                return true;
            }
        });
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { row.press(); row.value.setText(row.value()); }
        });
        list.addView(r, lp);
    }

    private void preview(int prayer) {
        if (AdhanService.isPlaying()) { AdhanService.stop(this); return; }
        String path = Audio.forPrayer(this, prayer);
        if (path == null) {
            android.widget.Toast.makeText(this, "ما فيه أصوات أذان للحين، تنزل تلقائيًا أول ما يتصل الجهاز بالإنترنت",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        previewed = true;
        AdhanService.play(this, path, PrayerTimes.NAMES[prayer], Prefs.shortAdhan(this, prayer));
    }

    @Override
    protected void onPause() {
        super.onPause();
        AdhanScheduler.scheduleNext(this);
        Chimes.scheduleNext(this);
    }

    @Override
    protected void onDestroy() {
        if (previewed) AdhanService.stop(this);
        super.onDestroy();
    }
}
