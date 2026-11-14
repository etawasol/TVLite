package com.tvlite.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.util.DisplayMetrics;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Wallpapers: a few built-in colour themes plus any pictures the user drops in
 *   /sdcard/TVLite/backgrounds   (or the app's own folder, which needs no permission)
 * Entries are "g:<n>" for built-ins or an absolute file path.
 */
public final class Backgrounds {

    public static final String[] GRADIENT_NAMES = {"أزرق ليلي", "أخضر زمردي", "بنفسجي", "غروب", "رمادي داكن"};
    private static final int[][] GRADIENTS = {
            {0xFF0E2A47, 0xFF0A1A30, 0xFF050C18},
            {0xFF0F3B33, 0xFF0A2A24, 0xFF041512},
            {0xFF2B1B4A, 0xFF1C1236, 0xFF0C0818},
            {0xFF5A2A1A, 0xFF3A1A2E, 0xFF140A18},
            {0xFF2A2F36, 0xFF1A1D22, 0xFF0B0C0F},
    };

    private Backgrounds() {}

    public static File publicFolder() {
        return new File(Environment.getExternalStorageDirectory(), "TVLite/backgrounds");
    }

    public static File privateFolder(Context c) {
        File f = c.getExternalFilesDir("backgrounds");
        return f != null ? f : new File(c.getFilesDir(), "backgrounds");
    }

    /** Every available background: built-ins first, then pictures sorted by name. */
    public static List<String> all(Context c) {
        List<String> r = new ArrayList<String>();
        for (int i = 0; i < GRADIENTS.length; i++) r.add("g:" + i);
        for (File dir : new File[]{publicFolder(), privateFolder(c)}) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.US);
                if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) {
                    r.add(f.getAbsolutePath());
                }
            }
        }
        return r;
    }

    public static boolean isGradient(String id) { return id == null || id.startsWith("g:"); }

    public static GradientDrawable gradient(String id) {
        int i = 0;
        try { i = Integer.parseInt(id.substring(2)); } catch (Exception ignored) { }
        if (i < 0 || i >= GRADIENTS.length) i = 0;
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, GRADIENTS[i]);
    }

    public static String name(String id) {
        if (isGradient(id)) {
            int i = 0;
            try { i = Integer.parseInt(id.substring(2)); } catch (Exception ignored) { }
            return GRADIENT_NAMES[Math.max(0, Math.min(i, GRADIENT_NAMES.length - 1))];
        }
        return new File(id).getName();
    }

    /** Decodes a picture scaled close to the screen size, so big photos don't run old boxes out of memory. */
    public static Bitmap load(Context c, String path) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels), h = Math.min(dm.widthPixels, dm.heightPixels);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(path, d);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
