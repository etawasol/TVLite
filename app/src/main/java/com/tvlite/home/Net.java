package com.tvlite.home;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Tiny HTTP GET.
 * Old Android versions lack newer root certificates (Let's Encrypt, DigiCert G2...), so on top of the
 * system store we also trust a few current public roots shipped in assets/roots.pem.
 * Certificates are still fully verified; nothing is trusted blindly.
 */
public final class Net {

    private static SSLSocketFactory factory;

    private Net() {}

    public static synchronized void init(Context c) {
        if (factory != null) return;
        try {
            final X509TrustManager system = defaultTm(null);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            String pem = readAll(c.getAssets().open("roots.pem"));
            int i = 0, n = 0;
            while ((i = pem.indexOf("-----BEGIN CERTIFICATE-----", i)) >= 0) {
                int end = pem.indexOf("-----END CERTIFICATE-----", i);
                if (end < 0) break;
                end += "-----END CERTIFICATE-----".length();
                Certificate cert = cf.generateCertificate(
                        new ByteArrayInputStream(pem.substring(i, end).getBytes("US-ASCII")));
                ks.setCertificateEntry("extra" + (n++), cert);
                i = end;
            }
            final X509TrustManager extra = defaultTm(ks);
            X509TrustManager both = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                    system.checkClientTrusted(chain, auth);
                }
                public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                    try {
                        system.checkServerTrusted(chain, auth);
                    } catch (CertificateException e) {
                        extra.checkServerTrusted(chain, auth);
                    }
                }
                public X509Certificate[] getAcceptedIssuers() { return system.getAcceptedIssuers(); }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{both}, null);
            factory = ctx.getSocketFactory();
        } catch (Exception ignored) {
            // fall back to the platform defaults
        }
    }

    private static X509TrustManager defaultTm(KeyStore ks) throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(ks);
        for (TrustManager t : f.getTrustManagers()) if (t instanceof X509TrustManager) return (X509TrustManager) t;
        throw new IllegalStateException("no X509TrustManager");
    }

    public static String get(String url) throws Exception {
        try {
            return fetch(url);
        } catch (Exception e) {
            if (url.startsWith("https://")) {
                try {
                    return fetch("http://" + url.substring(8));
                } catch (Exception ignored) { }
            }
            throw e;
        }
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (factory != null && c instanceof HttpsURLConnection) ((HttpsURLConnection) c).setSSLSocketFactory(factory);
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) TVLite/1.0");
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            Clock.observe(c.getDate());
            if (code != 200) throw new Exception("HTTP " + code);
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    /** Downloads url into file (via a temp file, so a cut connection never leaves half a picture). */
    public static void download(String url, java.io.File file) throws Exception {
        java.io.File tmp = new java.io.File(file.getPath() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (factory != null && c instanceof HttpsURLConnection) ((HttpsURLConnection) c).setSSLSocketFactory(factory);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) TVLite/1.0 (personal TV app)");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = c.getInputStream();
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            if (!tmp.renameTo(file)) throw new Exception("rename failed");
        } finally {
            c.disconnect();
            tmp.delete();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    /** Short, readable reason for an error, for the on-screen status. */
    public static String reason(Exception e) {
        String s = e.getClass().getSimpleName();
        if (e.getMessage() != null) s += ": " + e.getMessage();
        return s.length() > 90 ? s.substring(0, 90) : s;
    }
}
