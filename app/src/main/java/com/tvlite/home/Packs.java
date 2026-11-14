package com.tvlite.home;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Wallpaper packs downloaded by the app itself, so no cable or adb is needed.
 * The list lives in the repo (wallpapers/packs.json) so new pictures can be added without an app update;
 * a built-in copy is used if GitHub can't be reached.
 */
public final class Packs {

    public static final String LIST_URL =
            "https://raw.githubusercontent.com/etawasol/TVLite/main/wallpapers/packs.json";

    private Packs() {}

    /** The first downloaded recordings become the defaults until the user picks others. */
    private static void defaultMuezzin(Context c, File f, boolean fajr) {
        android.content.SharedPreferences p = Prefs.get(c);
        String key = fajr ? "muezzin_fajr" : "muezzin";
        if (p.getString(key, "").length() == 0) p.edit().putString(key, f.getAbsolutePath()).apply();
    }

    /** Downloads any missing pictures and adhan recordings. Returns the files that were newly added. */
    public static List<File> sync(Context c) {
        List<File> added = new ArrayList<File>();
        String json;
        try {
            json = Net.get(LIST_URL);
        } catch (Exception e) {
            json = BuiltIn.PACKS;
        }
        File dir = Backgrounds.privateFolder(c);
        dir.mkdirs();
        android.content.SharedPreferences labels = c.getSharedPreferences("labels", Context.MODE_PRIVATE);
        File audioDir = Audio.privateFolder(c);
        audioDir.mkdirs();
        try {
            JSONArray packs = new JSONObject(json).getJSONArray("packs");
            for (int p = 0; p < packs.length(); p++) {
                JSONObject pack = packs.getJSONObject(p);
                boolean audio = "adhan".equals(pack.optString("type"));
                JSONArray items = pack.optJSONArray(audio ? "audio" : "images");
                if (items == null) continue;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    String name = it.getString("file");
                    if (name.contains("/") || name.contains("..")) continue;
                    if (it.has("label")) labels.edit().putString(name, it.getString("label")).apply();
                    File f = new File(audio ? audioDir : dir, name);
                    if (f.exists() && f.length() > 0) continue;
                    try {
                        if (!added.isEmpty()) Thread.sleep(4000); // be gentle with Wikimedia (429 otherwise)
                        try {
                            Net.download(it.getString("url"), f);
                        } catch (Exception busy) {
                            if (!String.valueOf(busy.getMessage()).contains("429")) throw busy;
                            Thread.sleep(30000); // rate limited: wait and try once more
                            Net.download(it.getString("url"), f);
                        }
                        added.add(f);
                        if (audio) defaultMuezzin(c, f, it.optBoolean("fajr", false));
                    } catch (Exception e) {
                        android.util.Log.w("TVLite", "download " + name + " failed", e);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("TVLite", "packs list failed", e);
        }
        return added;
    }
}
