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

    /** Downloads any missing pictures. Returns the files that were newly added. */
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
        try {
            JSONArray packs = new JSONObject(json).getJSONArray("packs");
            for (int p = 0; p < packs.length(); p++) {
                JSONArray images = packs.getJSONObject(p).getJSONArray("images");
                for (int i = 0; i < images.length(); i++) {
                    JSONObject img = images.getJSONObject(i);
                    String name = img.getString("file");
                    if (name.contains("/") || name.contains("..")) continue;
                    File f = new File(dir, name);
                    if (f.exists() && f.length() > 0) continue;
                    try {
                        if (!added.isEmpty()) Thread.sleep(4000); // be gentle with Wikimedia (429 otherwise)
                        Net.download(img.getString("url"), f);
                        added.add(f);
                    } catch (Exception e) {
                        android.util.Log.w("TVLite", "wallpaper " + name + " failed", e);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("TVLite", "packs list failed", e);
        }
        return added;
    }
}
