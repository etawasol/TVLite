package com.tvlite.home;

import org.json.JSONObject;

/** Current weather from Open-Meteo (free, no key). */
public final class Weather {

    public int temp;
    public int max;
    public int min;
    public int wind;
    public int code;

    public static String url(double lat, double lng) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lng
                + "&current=temperature_2m,weather_code,wind_speed_10m"
                + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=Asia%2FRiyadh";
    }

    public static Weather parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONObject cur = o.getJSONObject("current");
        JSONObject daily = o.getJSONObject("daily");
        Weather w = new Weather();
        w.temp = (int) Math.round(cur.getDouble("temperature_2m"));
        w.code = cur.getInt("weather_code");
        w.wind = (int) Math.round(cur.optDouble("wind_speed_10m", 0));
        w.max = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        w.min = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        return w;
    }

    public String description() {
        int c = code;
        if (c == 0) return "صحو";
        if (c == 1) return "صحو غالبًا";
        if (c == 2) return "غائم جزئيًا";
        if (c == 3) return "غائم";
        if (c == 45 || c == 48) return "ضباب";
        if (c >= 51 && c <= 57) return "رذاذ";
        if (c >= 61 && c <= 67) return "مطر";
        if (c >= 71 && c <= 77) return "ثلج";
        if (c >= 80 && c <= 82) return "زخات مطر";
        if (c >= 95) return "عواصف رعدية";
        return "";
    }

    public String symbol() {
        int c = code;
        if (c <= 1) return "☀";
        if (c == 2) return "⛅";
        if (c == 3 || c == 45 || c == 48) return "☁";
        if (c >= 95) return "⚡";
        if (c >= 71 && c <= 77) return "❄";
        return "☂";
    }
}
