package com.tvlite.home;

import android.content.Context;
import android.util.Xml;

import org.json.JSONArray;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Headlines for the news ticker, from free RSS feeds (Google News in Arabic, BBC Arabic as a backup).
 * A headline is shown once: the same story (or a near copy of it from another site) does not come back
 * for 24 hours.
 */
public final class News {

    public static final class Item {
        public String cat, label, title, source;
        public int color;
        public long time;
        String norm;
    }

    private static final String GN = "https://news.google.com/rss";
    private static final String GN_LANG = "hl=ar&gl=SA&ceid=SA:ar";

    /** key, Arabic label, colour, feeds */
    public static final Object[][] CATS = {
            {"politics", "سياسة", 0xFFC0392B, new String[]{
                    GN + "/headlines/section/topic/WORLD?" + GN_LANG,
                    GN + "/headlines/section/topic/NATION?" + GN_LANG,
                    "https://feeds.bbci.co.uk/arabic/rss.xml"}},
            {"tech", "تقنية", 0xFF2D7DD2, new String[]{
                    GN + "/headlines/section/topic/TECHNOLOGY?" + GN_LANG}},
            {"ai", "ذكاء اصطناعي", 0xFF8E44AD, new String[]{
                    search("الذكاء الاصطناعي")}},
            {"ksa_sport", "رياضة سعودية", 0xFF1E8449, new String[]{
                    search("دوري روشن OR الهلال OR النصر OR الاتحاد OR الأهلي OR القادسية OR الفتح OR المنتخب السعودي")}},
            {"world_sport", "رياضة عالمية", 0xFFD68910, new String[]{
                    search("الدوري الإنجليزي OR الدوري الإسباني OR دوري أبطال أوروبا OR ريال مدريد OR برشلونة OR ليفربول OR مانشستر")}},
    };

    private static final long DAY = 24 * 60 * 60 * 1000L;
    private static final int PER_CAT = 25;

    private static final List<Item> pending = new ArrayList<Item>();
    private static List<Object[]> seen; // {norm, Set<String> words, Long shownAt}

    private News() {}

    private static String search(String q) {
        try {
            return GN + "/search?q=" + URLEncoder.encode(q + " when:1d", "UTF-8") + "&" + GN_LANG;
        } catch (Exception e) {
            return GN + "/search?q=news&" + GN_LANG;
        }
    }

    public static boolean catOn(Context c, String key) { return Prefs.get(c).getBoolean("news_cat_" + key, true); }

    /** Which categories are on, as one string (to notice a change in the settings). */
    public static String signature(Context c) {
        StringBuilder b = new StringBuilder();
        for (Object[] cat : CATS) b.append(catOn(c, (String) cat[0]) ? '1' : '0');
        return b.toString();
    }

    // ---------------------------------------------------------------- fetching

    /** Fetches every enabled category (network, call off the UI thread). Returns how many feeds answered. */
    public static int refresh(Context c) {
        loadSeen(c);
        long now = Clock.now();
        List<List<Item>> perCat = new ArrayList<List<Item>>();
        int ok = 0;
        for (Object[] cat : CATS) {
            String key = (String) cat[0];
            if (!catOn(c, key)) continue;
            List<Item> items = new ArrayList<Item>();
            for (String url : (String[]) cat[3]) {
                try {
                    parse(Net.get(url, "application/rss+xml, application/xml, text/xml, */*"), cat, items);
                    ok++;
                } catch (Exception e) {
                    android.util.Log.w("TVLite", "news " + key + " failed: " + Net.reason(e));
                }
            }
            // newest first, last 24 hours only
            Collections.sort(items, new Comparator<Item>() {
                public int compare(Item a, Item b) { return a.time > b.time ? -1 : (a.time == b.time ? 0 : 1); }
            });
            List<Item> keep = new ArrayList<Item>();
            for (Item it : items) {
                if (it.time > 0 && now - it.time > DAY) continue;
                if (isSeen(it.norm) || nearAny(it.norm, keep)) continue;
                keep.add(it);
                if (keep.size() >= PER_CAT) break;
            }
            perCat.add(keep);
        }
        if (ok == 0) return 0;
        // one from each category in turn, dropping the same story found in two categories
        List<Item> mixed = new ArrayList<Item>();
        for (int round = 0; round < PER_CAT; round++) {
            for (List<Item> l : perCat) {
                if (round < l.size() && !nearAny(l.get(round).norm, mixed)) mixed.add(l.get(round));
            }
        }
        synchronized (pending) {
            pending.clear();
            pending.addAll(mixed);
        }
        return ok;
    }

    private static void parse(String xml, Object[] cat, List<Item> out) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new StringReader(xml));
        String title = null, source = null, date = null, tag = null;
        boolean inItem = false;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                tag = p.getName();
                if ("item".equals(tag)) { inItem = true; title = source = date = null; }
            } else if (ev == XmlPullParser.TEXT && inItem && tag != null) {
                String t = p.getText();
                if ("title".equals(tag)) title = title == null ? t : title + t;
                else if ("source".equals(tag)) source = source == null ? t : source + t;
                else if ("pubDate".equals(tag)) date = date == null ? t : date + t;
            } else if (ev == XmlPullParser.END_TAG) {
                if ("item".equals(p.getName()) && title != null) {
                    add(cat, title, source, date, out);
                    inItem = false;
                }
                tag = null;
            }
        }
    }

    private static void add(Object[] cat, String title, String source, String date, List<Item> out) {
        title = title.replaceAll("\\s+", " ").trim();
        if (source != null) source = source.trim();
        // Google News titles end with " - Source"
        int dash = title.lastIndexOf(" - ");
        if (dash > 10) {
            String tail = title.substring(dash + 3).trim();
            if (source == null || source.length() == 0 || tail.equals(source)) { source = tail; title = title.substring(0, dash).trim(); }
        }
        if (source == null || source.length() == 0) source = "BBC عربي";
        if (title.length() < 12) return;
        if (title.length() > 150) title = title.substring(0, 147) + "…";
        Item it = new Item();
        it.cat = (String) cat[0];
        it.label = (String) cat[1];
        it.color = (Integer) cat[2];
        it.title = title;
        it.source = source;
        it.time = parseDate(date);
        it.norm = normalize(title);
        if (it.norm.length() > 0) out.add(it);
    }

    private static long parseDate(String s) {
        if (s == null) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
            Date d = f.parse(s.trim().replace("GMT", "+0000"));
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- the 24-hour rule

    /** Plain form of a headline: no diacritics, one form of each letter, no punctuation. */
    static String normalize(String s) {
        s = s.replaceAll("[\\u064B-\\u0652\\u0640]", "")
                .replaceAll("[أإآٱ]", "ا").replace('ة', 'ه').replace('ى', 'ي')
                .toLowerCase(Locale.US)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return s;
    }

    private static Set<String> words(String norm) {
        Set<String> w = new HashSet<String>();
        for (String x : norm.split(" ")) {
            if (x.length() < 2) continue;
            if (x.startsWith("ال") && x.length() > 4) x = x.substring(2);
            w.add(x);
        }
        return w;
    }

    /** Same story: identical, or most words of the shorter headline appear in the other. */
    private static boolean similar(String a, Set<String> wa, String b, Set<String> wb) {
        if (a.equals(b)) return true;
        int min = Math.min(wa.size(), wb.size());
        if (min < 4) return false;
        int common = 0;
        for (String x : wa) if (wb.contains(x)) common++;
        return common >= Math.ceil(min * 0.75);
    }

    private static boolean nearAny(String norm, List<Item> list) {
        Set<String> w = words(norm);
        for (Item it : list) if (similar(norm, w, it.norm, words(it.norm))) return true;
        return false;
    }

    private static synchronized boolean isSeen(String norm) {
        Set<String> w = words(norm);
        long now = Clock.now();
        for (Object[] s : seen) {
            if (now - (Long) s[2] > DAY) continue;
            if (similar(norm, w, (String) s[0], (Set<String>) s[1])) return true;
        }
        return false;
    }

    private static synchronized void loadSeen(Context c) {
        if (seen != null) return;
        seen = new ArrayList<Object[]>();
        try {
            File f = new File(c.getFilesDir(), "news_seen.json");
            if (!f.exists()) return;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            in.close();
            JSONArray a = new JSONArray(new String(b, 0, n, "UTF-8"));
            long now = Clock.now();
            for (int i = 0; i < a.length(); i++) {
                JSONArray e = a.getJSONArray(i);
                long t = e.getLong(1);
                if (now - t > DAY || t > now + DAY) continue;
                seen.add(new Object[]{e.getString(0), words(e.getString(0)), t});
            }
        } catch (Exception ignored) { }
    }

    private static synchronized void markShown(Context c, Item it) {
        long now = Clock.now();
        for (Iterator<Object[]> i = seen.iterator(); i.hasNext(); ) if (now - (Long) i.next()[2] > DAY) i.remove();
        seen.add(new Object[]{it.norm, words(it.norm), now});
        try {
            JSONArray a = new JSONArray();
            for (Object[] s : seen) { JSONArray e = new JSONArray(); e.put(s[0]); e.put(s[2]); a.put(e); }
            File f = new File(c.getFilesDir(), "news_seen.json");
            File tmp = new File(c.getFilesDir(), "news_seen.json.part");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(a.toString().getBytes("UTF-8"));
            out.close();
            tmp.renameTo(f);
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- live matches and reminders

    private static final int GOAL = 0xFFE5484D, LIVE = 0xFF1E8449, ALERT = 0xFF16A085, BREAKING = 0xFFE00000;
    private static final long LIVE_EVERY = 8 * 60 * 1000L, BREAKING_AGE = 30 * 60 * 1000L;
    private static final List<Item> urgent = new ArrayList<Item>();
    private static final java.util.Map<String, String> lastScore = new java.util.HashMap<String, String>();
    private static final java.util.Map<String, Long> liveShown = new java.util.HashMap<String, Long>();
    private static final java.util.Map<String, Matches.Match> live = new java.util.LinkedHashMap<String, Matches.Match>();
    private static String reminded = "";

    private static Item special(String label, int color, String title, String source) {
        Item it = new Item();
        it.cat = "special";
        it.label = label;
        it.color = color;
        it.title = title;
        it.source = source;
        it.norm = "";
        return it;
    }

    private static String score(Matches.Match m) {
        return m.home + " " + m.homeScore + "، " + m.away + " " + m.awayScore;
    }

    /** Called with the favourite clubs' matches each time they are refreshed: goals and final results. */
    public static synchronized void setMatches(List<Matches.Match> all) {
        for (Matches.Match m : all) {
            String sc = m.homeScore + "-" + m.awayScore;
            if ("in".equals(m.state)) {
                String before = lastScore.get(m.id);
                if (before != null && !before.equals(sc)) {
                    urgent.add(special("هدف", GOAL, "⚽ " + score(m) + (m.detail.length() > 0 ? " · الدقيقة " + m.detail : ""), m.league));
                    liveShown.put(m.id, Clock.now());
                }
                lastScore.put(m.id, sc);
                live.put(m.id, m);
            } else if (live.containsKey(m.id)) {
                live.remove(m.id);
                liveShown.remove(m.id);
                lastScore.remove(m.id);
                if ("post".equals(m.state)) urgent.add(special("نهاية المباراة", LIVE, score(m), m.league));
            }
        }
    }

    private static synchronized Item specialNext(Context c) {
        android.content.SharedPreferences p = Prefs.get(c);
        if (p.getBoolean("news_prayer", true)) {
            java.util.Calendar now = Clock.cal();
            int mod = Fmt.minutesOfDay(now);
            int[] pt = Prayers.times(c, now);
            for (int i : Prefs.PRAYERS) {
                int left = pt[i] - mod;
                String key = now.get(java.util.Calendar.DAY_OF_YEAR) + "/" + i;
                if (left > 0 && left <= 10 && !reminded.equals(key)) {
                    reminded = key;
                    return special("تنبيه", ALERT, "باقي " + minutes(left) + " على أذان " + Prayers.name(now, i), "شاشتي");
                }
            }
        }
        if (!p.getBoolean("news_live", true)) { urgent.clear(); return null; }
        if (!urgent.isEmpty()) return urgent.remove(0);
        long t = Clock.now();
        for (Matches.Match m : live.values()) {
            Long last = liveShown.get(m.id);
            if (last == null || t - last > LIVE_EVERY) {
                liveShown.put(m.id, t);
                return special("مباشر", LIVE, score(m) + (m.detail.length() > 0 ? " · الدقيقة " + m.detail : ""), m.league);
            }
        }
        return null;
    }

    private static String minutes(int n) {
        if (n == 1) return "دقيقة";
        if (n == 2) return "دقيقتين";
        if (n <= 10) return n + " دقائق";
        return n + " دقيقة";
    }

    // ---------------------------------------------------------------- for the ticker

    /** The next thing to show (and remember it as shown), or null when there is nothing new. */
    public static Item next(Context c) {
        Item sp = specialNext(c);
        if (sp != null) return sp;
        if (!Prefs.newsOn(c)) return null;
        loadSeen(c);
        boolean breaking = Prefs.get(c).getBoolean("news_breaking", true);
        while (true) {
            Item it;
            synchronized (pending) {
                if (pending.isEmpty()) return null;
                it = pending.remove(0);
            }
            if (!catOn(c, it.cat) || isSeen(it.norm)) continue;
            markShown(c, it);
            if (breaking && it.time > 0 && Clock.now() - it.time < BREAKING_AGE) {
                it.label = "عاجل · " + it.label;
                it.color = BREAKING;
            }
            return it;
        }
    }

    public static void clearPending() {
        synchronized (pending) { pending.clear(); }
    }
}
