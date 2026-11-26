package com.stradaos.launcher;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 2.38 (#79, LexicoON, pantallas K2501/NWD sin ADB): guarda en un .zip la app de música Bluetooth de fábrica (y las
 * apps de fábrica parecidas) con un informe de sus servicios, para decompilarla y enseñarle a BtBridge a leerla.
 * Solo copia los archivos .apk que Android ya deja leer a cualquier app; no toca nada del sistema.
 */
final class BtExport {

    static final class App {
        String pkg, label, src;
        long size;
        boolean guessed;
    }

    private static final String[] KEYS = {"bt", "blue", "a2dp", "avrcp", "music", "musica", "audio", "media", "nwd", "nowada",
            "phone", "dialer", "goc", "mcu", "canbus", "carservice", "sound"};
    private static final long MAX_APP = 80L * 1024 * 1024;

    /** Apps de fábrica que pueden tener el Bluetooth; la que StradaOS cree que es la de música Bluetooth va primero. */
    static List<App> candidates(Context c, String guessedPkg) {
        PackageManager pm = c.getPackageManager();
        List<App> out = new ArrayList<App>();
        List<ApplicationInfo> all;
        try { all = pm.getInstalledApplications(0); } catch (Throwable e) { return out; }
        for (ApplicationInfo ai : all) {
            try {
                if (ai.packageName.equals(c.getPackageName()) || ai.packageName.startsWith("com.google.")) continue;
                boolean sys = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                String label = String.valueOf(pm.getApplicationLabel(ai));
                String key = (ai.packageName + " " + label).toLowerCase(Locale.US);
                boolean guessed = ai.packageName.equals(guessedPkg);
                boolean want = guessed || "com.android.bluetooth".equals(ai.packageName);
                if (!want && sys) for (String k : KEYS) if (key.contains(k)) { want = true; break; }
                if (!want || ai.sourceDir == null) continue;
                App a = new App();
                a.pkg = ai.packageName;
                a.label = label;
                a.src = ai.sourceDir;
                a.size = new File(ai.sourceDir).length();
                a.guessed = guessed;
                out.add(a);
            } catch (Throwable ignored) { }
        }
        Collections.sort(out, new Comparator<App>() {
            public int compare(App x, App y) {
                int r = rank(x) - rank(y);
                return r != 0 ? r : x.label.compareToIgnoreCase(y.label);
            }
        });
        return out;
    }

    private static int rank(App a) {
        if (a.guessed) return 0;
        String k = (a.pkg + " " + a.label).toLowerCase(Locale.US);
        if (k.contains("bt") || k.contains("blue") || k.contains("a2dp") || k.contains("avrcp")) return 1;
        if (k.contains("music") || k.contains("musica") || k.contains("audio")) return 2;
        return 3;
    }

    /** Archivos de una app: el .apk, sus partes (Android 5+) y el código ya optimizado si está a la vista (.odex). */
    private static List<File> files(Context c, String pkg) {
        List<File> out = new ArrayList<File>();
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            File apk = new File(ai.sourceDir);
            out.add(apk);
            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    Object sp = ApplicationInfo.class.getField("splitSourceDirs").get(ai);
                    if (sp instanceof String[]) for (String s : (String[]) sp) if (s != null) out.add(new File(s));
                } catch (Throwable ignored) { }
            }
            String base = ai.sourceDir.endsWith(".apk") ? ai.sourceDir.substring(0, ai.sourceDir.length() - 4) : ai.sourceDir;
            File odex = new File(base + ".odex");
            if (odex.isFile()) out.add(odex);
            File oat = new File(apk.getParentFile(), "oat");
            if (oat.isDirectory()) addAll(oat, out, 3);
        } catch (Throwable ignored) { }
        return out;
    }

    private static void addAll(File d, List<File> out, int depth) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) { if (depth > 0) addAll(f, out, depth - 1); }
            else if (f.canRead()) out.add(f);
        }
    }

    /** Informe: equipo, cada app con sus servicios/receptores/proveedores y los servicios que están corriendo. */
    static String report(Context c, List<App> apps) {
        StringBuilder b = new StringBuilder();
        b.append("StradaOS · exportación de apps de Bluetooth (#79)\n");
        b.append("Equipo: ").append(Build.MANUFACTURER).append(" / ").append(Build.MODEL).append(" / ").append(Build.DEVICE)
         .append(" / ").append(Build.PRODUCT).append(" / placa ").append(Build.BOARD).append(" / hw ").append(Build.HARDWARE).append('\n');
        b.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") ").append(Build.DISPLAY).append('\n');
        b.append("Fingerprint: ").append(Build.FINGERPRINT).append("\n\n");
        PackageManager pm = c.getPackageManager();
        for (App a : apps) {
            b.append("■ ").append(a.label).append(" · ").append(a.pkg).append('\n');
            try {
                PackageInfo pi = pm.getPackageInfo(a.pkg, PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                        | PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS);
                b.append("  versión ").append(pi.versionName).append(" (").append(pi.versionCode).append(") · ").append(a.src).append('\n');
                if (pi.activities != null) for (ActivityInfo x : pi.activities) b.append("  activity ").append(x.name).append(x.exported ? " (exported)" : "").append('\n');
                if (pi.services != null) for (ServiceInfo x : pi.services)
                    b.append("  service  ").append(x.name).append(x.exported ? " (exported)" : "").append(x.permission != null ? " perm=" + x.permission : "").append('\n');
                if (pi.receivers != null) for (ActivityInfo x : pi.receivers) b.append("  receiver ").append(x.name).append(x.exported ? " (exported)" : "").append('\n');
                if (pi.providers != null) for (android.content.pm.ProviderInfo x : pi.providers) b.append("  provider ").append(x.name).append(" auth=").append(x.authority).append('\n');
            } catch (Throwable e) { b.append("  ").append(e).append('\n'); }
            b.append('\n');
        }
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            List<android.app.ActivityManager.RunningServiceInfo> rs = am.getRunningServices(300);
            b.append("Servicios corriendo ahora (mejor exportar con el celular sonando por Bluetooth):\n");
            if (rs != null) for (android.app.ActivityManager.RunningServiceInfo r : rs)
                b.append("  ").append(r.service.flattenToShortString()).append(r.foreground ? " [primer plano]" : "").append('\n');
        } catch (Throwable ignored) { }
        b.append("\n/system/framework:\n");
        try {
            String[] fw = new File("/system/framework").list();
            if (fw != null) { java.util.Arrays.sort(fw); for (String f : fw) b.append("  ").append(f).append('\n'); }
        } catch (Throwable ignored) { }
        b.append("\nEventos Bluetooth de StradaOS:\n").append(BtBridge.logText()).append('\n');
        return b.toString();
    }

    /** Resultado de la exportación. */
    static final class Result {
        File zip;
        int apps;
        long bytes;
        String error;
    }

    /**
     * Escribe «StradaDiag/apps-bluetooth-FECHA.zip» en la memoria elegida. Si Android no deja escribir en su raíz
     * (memorias USB en Android 4.4 a 10), en la carpeta propia de StradaOS dentro de esa memoria.
     */
    static Result export(Context c, List<App> apps, File root) {
        Result r = new Result();
        String name = zipName();
        List<File> dirs = new ArrayList<File>();
        dirs.add(new File(root, "StradaDiag"));
        String rp = canon(root);
        for (File d : Backup.appDirs(c)) if (d != null && canon(d).startsWith(rp)) dirs.add(new File(d, "StradaDiag"));
        for (File dir : dirs) {
            Result t = write(c, apps, dir, name);
            if (t.error == null) return t;
            if (r.error == null) r.error = t.error;
        }
        return r;
    }

    private static String canon(File f) {
        try { return f.getCanonicalPath(); } catch (Throwable e) { return f.getAbsolutePath(); }
    }

    static String zipName() {
        return "apps-bluetooth-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new java.util.Date()) + ".zip";
    }

    private static Result write(Context c, List<App> apps, File dir, String name) {
        Result r = new Result();
        File zip = new File(dir, name);
        try {
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) throw new java.io.IOException("no se pudo crear " + dir);
            r = writeZip(c, apps, new FileOutputStream(zip));
            if (r.error != null) throw new java.io.IOException(r.error);
            r.zip = zip;
        } catch (Throwable e) {
            r.error = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
            zip.delete();
        }
        return r;
    }

    /** Escribe el .zip (informe + archivos de cada app) en el destino y lo cierra. */
    static Result writeZip(Context c, List<App> apps, java.io.OutputStream os) {
        Result r = new Result();
        ZipOutputStream z = null;
        try {
            z = new ZipOutputStream(new java.io.BufferedOutputStream(os, 65536));
            z.setLevel(Deflater.BEST_SPEED);   // los .apk ya vienen comprimidos: no vale la pena gastar la radio en eso
            z.putNextEntry(new ZipEntry("info.txt"));
            z.write(report(c, apps).getBytes("UTF-8"));
            z.closeEntry();
            byte[] buf = new byte[65536];
            for (App a : apps) {
                long appBytes = 0;
                for (File f : files(c, a.pkg)) {
                    if (appBytes + f.length() > MAX_APP) continue;
                    InputStream in = null;
                    try {
                        in = new FileInputStream(f);
                        String rel = f.getName();
                        if (f.getParentFile() != null && !rel.endsWith(".apk")) rel = f.getParentFile().getName() + "_" + rel;
                        z.putNextEntry(new ZipEntry(a.pkg + "/" + rel));
                        int n;
                        while ((n = in.read(buf)) > 0) { z.write(buf, 0, n); appBytes += n; }
                        z.closeEntry();
                    } catch (java.io.FileNotFoundException ignored) {   // sin permiso para leer ese archivo: se sigue con el resto
                    } finally {
                        try { if (in != null) in.close(); } catch (Throwable ignored) { }
                    }
                }
                if (appBytes > 0) r.apps++;
                r.bytes += appBytes;
            }
            z.finish();
            z.close();
            z = null;
            if (r.apps == 0 && !apps.isEmpty()) r.error = "no se pudo leer ninguna app";
        } catch (Throwable e) {
            r.error = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
            try { if (z != null) z.close(); } catch (Throwable ignored) { }
        }
        return r;
    }
}
