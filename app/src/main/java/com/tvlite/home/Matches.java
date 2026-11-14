package com.tvlite.home;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Fixtures and results for the favourite clubs, from ESPN's public scoreboard feed. */
public final class Matches {

    /** ESPN league slug -> Arabic name */
    public static final String[][] LEAGUES = {
            {"ksa.1", "دوري روشن"},
            {"ksa.kings.cup", "كأس الملك"},
            {"afc.champions", "دوري أبطال آسيا"},
            {"afc.cup", "دوري أبطال آسيا 2"},
    };

    /** keyword in ESPN English name -> Arabic name. First six are the favourites. */
    private static final String[][] TEAMS = {
            {"hilal", "الهلال"}, {"nassr", "النصر"}, {"ittihad", "الاتحاد"}, {"ahli", "الأهلي"},
            {"fateh", "الفتح"}, {"qadsi", "القادسية"},
            {"shabab", "الشباب"}, {"ettifaq", "الاتفاق"}, {"taawoun", "التعاون"}, {"taawon", "التعاون"},
            {"raed", "الرائد"}, {"khaleej", "الخليج"}, {"fayha", "الفيحاء"}, {"damac", "ضمك"},
            {"riyadh", "الرياض"}, {"kholood", "الخلود"}, {"okhdood", "الأخدود"}, {"akhdoud", "الأخدود"},
            {"wehda", "الوحدة"}, {"orobah", "العروبة"}, {"najma", "النجمة"}, {"hazem", "الحزم"},
            {"neom", "نيوم"}, {"feiha", "الفيحاء"},
    };
    private static final int FAVOURITES = 6;

    public static final class Match {
        public String id;
        public String league;
        public String home;
        public String away;
        public String homeScore;
        public String awayScore;
        public String state;   // pre, in, post
        public String detail;  // e.g. 63'
        public long time;
    }

    public static String url(String slug, Calendar now) {
        Calendar from = (Calendar) now.clone();
        from.add(Calendar.DAY_OF_MONTH, -3);
        Calendar to = (Calendar) now.clone();
        to.add(Calendar.DAY_OF_MONTH, 30);
        return "https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug
                + "/scoreboard?limit=300&dates=" + ymd(from) + "-" + ymd(to);
    }

    private static String ymd(Calendar c) {
        return c.get(Calendar.YEAR) + Fmt.two(c.get(Calendar.MONTH) + 1) + Fmt.two(c.get(Calendar.DAY_OF_MONTH));
    }

    public static void parse(String json, String leagueName, List<Match> out) throws Exception {
        JSONArray events = new JSONObject(json).optJSONArray("events");
        if (events == null) return;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
            JSONArray cs = comp.getJSONArray("competitors");
            if (cs.length() < 2) continue;
            JSONObject home = cs.getJSONObject(0), away = cs.getJSONObject(1);
            if ("away".equals(home.optString("homeAway"))) { JSONObject t = home; home = away; away = t; }
            String hn = home.getJSONObject("team").optString("displayName");
            String an = away.getJSONObject("team").optString("displayName");
            if (!isFavourite(hn) && !isFavourite(an)) continue;
            Match m = new Match();
            m.id = e.optString("id");
            m.league = leagueName;
            m.home = arabic(hn);
            m.away = arabic(an);
            m.homeScore = home.optString("score", "");
            m.awayScore = away.optString("score", "");
            JSONObject st = e.optJSONObject("status");
            JSONObject type = st == null ? null : st.optJSONObject("type");
            m.state = type == null ? "pre" : type.optString("state", "pre");
            m.detail = st == null ? "" : st.optString("displayClock", "");
            if (type != null && "post".equals(m.state) && !type.optBoolean("completed", true)) m.state = "off";
            m.time = parseDate(e.optString("date"));
            out.add(m);
        }
    }

    /** Live first, then upcoming (soonest first), then the latest results. */
    public static List<Match> arrange(List<Match> all, int max) {
        Set<String> seen = new HashSet<String>();
        List<Match> live = new ArrayList<Match>(), next = new ArrayList<Match>(), done = new ArrayList<Match>();
        for (Match m : all) {
            if (!seen.add(m.id)) continue;
            if ("in".equals(m.state)) live.add(m);
            else if ("pre".equals(m.state)) next.add(m);
            else if ("post".equals(m.state)) done.add(m);
        }
        Comparator<Match> asc = new Comparator<Match>() {
            public int compare(Match a, Match b) { return a.time < b.time ? -1 : (a.time == b.time ? 0 : 1); }
        };
        Collections.sort(next, asc);
        Collections.sort(done, Collections.reverseOrder(asc));
        List<Match> r = new ArrayList<Match>(live);
        int results = Math.min(done.size(), 2);
        for (Match m : next) { if (r.size() >= max - results) break; r.add(m); }
        for (Match m : done) { if (r.size() >= max) break; r.add(m); }
        return r;
    }

    private static boolean isFavourite(String english) {
        String s = english.toLowerCase(Locale.US);
        if (s.contains("doha") || s.contains("kalba") || s.contains("cairo") || s.contains("tripoli")) return false;
        for (int i = 0; i < FAVOURITES; i++) if (s.contains(TEAMS[i][0])) return true;
        return false;
    }

    private static String arabic(String english) {
        String s = english.toLowerCase(Locale.US);
        for (String[] t : TEAMS) if (s.contains(t[0])) return t[1];
        return english;
    }

    private static long parseDate(String s) {
        String[] fmts = {"yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String f : fmts) {
            try {
                SimpleDateFormat p = new SimpleDateFormat(f, Locale.US);
                p.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = p.parse(s);
                if (d != null) return d.getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }

    /** "اليوم 8:00 م", "بكرة 9:30 م", "السبت 21/11 8:00 م" */
    public static String when(long t) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        String time = Fmt.time(Fmt.minutesOfDay(c));
        int dayDiff = dayIndex(c) - dayIndex(now);
        if (dayDiff == 0) return "اليوم " + time;
        if (dayDiff == 1) return "بكرة " + time;
        if (dayDiff == -1) return "أمس";
        String date = c.get(Calendar.DAY_OF_MONTH) + "/" + (c.get(Calendar.MONTH) + 1);
        if (dayDiff < 0) return date;
        return Fmt.DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + date + " " + time;
    }

    private static int dayIndex(Calendar c) {
        return c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR);
    }
}
