package com.stradaos.launcher;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Descargas HTTPS que también funcionan en Android 4.x:
 * - activa TLS 1.2 (viene apagado en Android 4.1–4.4)
 * - confía en certificados raíz actuales incluidos en la app (además de los del sistema)
 */
public class Net {
    private static SSLSocketFactory factory;
    static String initInfo = "sin iniciar";

    public static synchronized void init(Context c) {
        if (factory != null) return;
        try {
            final X509TrustManager system = defaultTm(null);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            InputStream in = c.getAssets().open("roots.pem");
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte[] rb = new byte[16384];
            int rn;
            while ((rn = in.read(rb)) > 0) raw.write(rb, 0, rn);
            in.close();
            String pem = new String(raw.toByteArray(), "US-ASCII");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            int i = 0, bad = 0, at = 0;
            while (true) {   // uno por uno: Android 4.x no entiende algunas raíces nuevas y fallaría todo el archivo
                int s0 = pem.indexOf("-----BEGIN CERTIFICATE-----", at);
                if (s0 < 0) break;
                int e0 = pem.indexOf("-----END CERTIFICATE-----", s0);
                if (e0 < 0) break;
                at = e0 + 25;
                try {
                    Certificate cert = cf.generateCertificate(new java.io.ByteArrayInputStream(pem.substring(s0, at).getBytes("US-ASCII")));
                    ks.setCertificateEntry("root" + (i++), cert);
                } catch (Throwable t) { bad++; }
            }
            final X509TrustManager bundled = defaultTm(ks);
            X509TrustManager both = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
                    system.checkClientTrusted(chain, auth);
                }
                public void checkServerTrusted(X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
                    try { system.checkServerTrusted(chain, auth); }
                    catch (java.security.cert.CertificateException e) { bundled.checkServerTrusted(chain, auth); }
                }
                public X509Certificate[] getAcceptedIssuers() { return system.getAcceptedIssuers(); }
            };
            SSLContext ctx;
            try { ctx = SSLContext.getInstance("TLSv1.2"); } catch (Throwable e) { ctx = SSLContext.getInstance("TLS"); }
            ctx.init(null, new TrustManager[]{both}, null);
            // Android 5+ ya trae TLS 1.2 y buenos cifrados: solo se agregan las raíces. Tocar los cifrados ahí rompía
            // el mapa en Android 9 (se colaba TLS_FALLBACK_SCSV y los servidores con TLS 1.3 cortaban el saludo).
            factory = android.os.Build.VERSION.SDK_INT >= 21 ? ctx.getSocketFactory() : new Tls12Factory(ctx.getSocketFactory());
            initInfo = "TLS listo (" + ctx.getProtocol() + "), " + i + " raíces" + (bad > 0 ? ", " + bad + " omitidas" : "");
        } catch (Throwable e) {
            factory = null;
            initInfo = "TLS no disponible: " + e;
        }
    }

    private static X509TrustManager defaultTm(KeyStore ks) throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(ks);
        for (TrustManager t : f.getTrustManagers()) if (t instanceof X509TrustManager) return (X509TrustManager) t;
        throw new IllegalStateException("sin TrustManager");
    }

    public static byte[] get(String url, int timeoutMs) throws Exception {
        try {
            return get(url, timeoutMs, factory);
        } catch (javax.net.ssl.SSLException e) {
            // si el saludo seguro falla con nuestra configuración, se reintenta con la del sistema
            if (factory == null || !url.startsWith("https")) throw e;
            return get(url, timeoutMs, null);
        }
    }

    /** Conexión configurada (TLS 1.2 y raíces actuales en Android 4.x) para descargas grandes por partes. */
    public static HttpURLConnection open(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (c instanceof HttpsURLConnection && factory != null) ((HttpsURLConnection) c).setSSLSocketFactory(factory);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", "StradaOS (Android head unit launcher; offline maps)");
        c.setRequestProperty("Accept-Encoding", "identity");
        return c;
    }

    private static byte[] get(String url, int timeoutMs, SSLSocketFactory f) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (c instanceof HttpsURLConnection && f != null) ((HttpsURLConnection) c).setSSLSocketFactory(f);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", "StradaOS/2.1 (Android head unit launcher)");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    /** Envuelve la fábrica para encender TLS 1.1/1.2 en cada socket. */
    private static class Tls12Factory extends SSLSocketFactory {
        private final SSLSocketFactory d;
        Tls12Factory(SSLSocketFactory d) { this.d = d; }
        private Socket on(Socket s) { return on(s, null); }
        private Socket on(Socket s, String host) {
            if (s instanceof SSLSocket) {
                try { // Android 4.x trae pocos cifrados activados: se encienden los demás, menos los inseguros y las señales
                    java.util.ArrayList<String> ok = new java.util.ArrayList<String>();
                    for (String c : ((SSLSocket) s).getSupportedCipherSuites()) {
                        if (c.contains("SCSV") || c.contains("_anon_") || c.contains("NULL") || c.contains("EXPORT")
                                || c.contains("_RC4_") || c.contains("_DES_") || c.contains("_MD5")) continue;
                        ok.add(c);
                    }
                    if (!ok.isEmpty()) ((SSLSocket) s).setEnabledCipherSuites(ok.toArray(new String[0]));
                } catch (Throwable ignored) { }
                if (host != null) {
                    try { s.getClass().getMethod("setHostname", String.class).invoke(s, host); } catch (Throwable ignored) { }   // SNI
                    try { s.getClass().getMethod("setUseSessionTickets", boolean.class).invoke(s, true); } catch (Throwable ignored) { }
                }
                try {
                    String[] sup = ((SSLSocket) s).getSupportedProtocols();
                    java.util.ArrayList<String> want = new java.util.ArrayList<String>();
                    for (String p : sup) if (p.equals("TLSv1.2") || p.equals("TLSv1.3") || p.equals("TLSv1.1")) want.add(p);
                    if (!want.isEmpty()) ((SSLSocket) s).setEnabledProtocols(want.toArray(new String[0]));
                } catch (Throwable ignored) { }
            }
            return s;
        }
        public String[] getDefaultCipherSuites() { return d.getDefaultCipherSuites(); }
        public String[] getSupportedCipherSuites() { return d.getSupportedCipherSuites(); }
        public Socket createSocket(Socket s, String h, int p, boolean a) throws java.io.IOException { return on(d.createSocket(s, h, p, a), h); }
        public Socket createSocket(String h, int p) throws java.io.IOException { return on(d.createSocket(h, p), h); }
        public Socket createSocket(String h, int p, InetAddress l, int lp) throws java.io.IOException { return on(d.createSocket(h, p, l, lp), h); }
        public Socket createSocket(InetAddress h, int p) throws java.io.IOException { return on(d.createSocket(h, p)); }
        public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws java.io.IOException { return on(d.createSocket(h, p, l, lp)); }
    }
}
