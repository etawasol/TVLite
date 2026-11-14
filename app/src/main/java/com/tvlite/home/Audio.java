package com.tvlite.home;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Adhan recordings: the ones the app downloaded plus any the user put in /sdcard/TVLite/adhan. */
public final class Audio {

    private Audio() {}

    public static File privateFolder(Context c) {
        File f = c.getExternalFilesDir("adhan");
        return f != null ? f : new File(c.getFilesDir(), "adhan");
    }

    public static File publicFolder() {
        return new File(Environment.getExternalStorageDirectory(), "TVLite/adhan");
    }

    public static List<File> all(Context c) {
        List<File> r = new ArrayList<File>();
        for (File dir : new File[]{privateFolder(c), publicFolder()}) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.US);
                if (n.startsWith("iqama")) continue;
                if (f.isFile() && f.length() > 0 && (n.endsWith(".mp3") || n.endsWith(".ogg")
                        || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".wav") || n.endsWith(".opus"))) {
                    r.add(f);
                }
            }
        }
        return r;
    }

    /** The iqama recording, if downloaded. */
    public static String iqama(Context c) {
        File f = new File(privateFolder(c), "iqamah.mp3");
        return f.exists() && f.length() > 0 ? f.getAbsolutePath() : null;
    }

    /** Arabic label saved when the pack was downloaded, else the file name. */
    public static String label(Context c, String path) {
        if (path == null || path.length() == 0) return "بدون";
        String name = new File(path).getName();
        String l = c.getSharedPreferences("labels", Context.MODE_PRIVATE).getString(name, null);
        if (l != null) return l;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** The recording to play for prayer i, or null if there is none. */
    public static String forPrayer(Context c, int i) {
        String p = i == 0 ? Prefs.muezzinFajr(c) : "";
        if (p.length() == 0 || !new File(p).exists()) p = Prefs.muezzin(c);
        if (p.length() > 0 && new File(p).exists()) return p;
        List<File> all = all(c);
        return all.isEmpty() ? null : all.get(0).getAbsolutePath();
    }
}
