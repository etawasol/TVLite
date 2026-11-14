package com.tvlite.home;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks GitHub for a newer build and installs it from the TV itself, so updates don't need adb. */
public final class Updater {

    private static final String RELEASE_API = "https://api.github.com/repos/etawasol/TVLite/releases/tags/latest";
    public static final String APK_URL = "https://github.com/etawasol/TVLite/releases/download/latest/TVLite.apk";

    private Updater() {}

    /** Returns the newest build number on GitHub, or -1. */
    public static int latestBuild() {
        try {
            String name = new JSONObject(Net.get(RELEASE_API)).optString("name");
            Matcher m = Pattern.compile("build (\\d+)").matcher(name);
            return m.find() ? Integer.parseInt(m.group(1)) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public static int installedBuild(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Downloads the APK. Runs on a background thread. */
    public static File download(Context c) throws Exception {
        File dir = c.getExternalCacheDir();
        if (dir == null) dir = c.getCacheDir();
        File apk = new File(dir, "TVLite-update.apk");
        apk.delete();
        Net.download(APK_URL, apk);
        apk.setReadable(true, false);
        return apk;
    }

    /** Opens the system installer, or the download page where file links aren't allowed (Android 7+). */
    public static void install(Context c, File apk) {
        Intent i;
        if (Build.VERSION.SDK_INT < 24 && apk != null) {
            i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
        } else {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL));
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception ignored) { }
    }
}
