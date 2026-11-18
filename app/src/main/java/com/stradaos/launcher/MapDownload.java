package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descarga de mapas sin conexión (archivos .map de mapsforge, gratis, de download.mapsforge.org).
 *
 * - Lista continentes y países leyendo el índice del servidor (siempre al día, con tamaño y fecha).
 * - Descarga en segundo plano a Download/StradaOS/, en un archivo .part que se reanuda si se corta la señal
 *   (el servidor acepta «Range»), incluso después de reiniciar la pantalla.
 * - Al terminar comprueba el archivo y lo deja como mapa sin conexión.
 * Usa la conexión segura de Net (TLS 1.2 y raíces actuales en Android 4.x); si el cifrado falla, usa http.
 */
public class MapDownload {
    static final String BASE = "download.mapsforge.org/maps/v5/";

    /** Elemento del índice: carpeta (continente, país con regiones) o archivo .map. */
    static final class Entry {
        String path, name;   // path relativo a BASE («south-america/colombia.map»), name a mostrar
        boolean dir;
        long size;           // aproximado (del índice), en bytes
        String date;         // «2026-10-25»
    }

    // estado de la descarga en curso (una a la vez)
    static volatile boolean running;
    static volatile long done, total, speed;     // bytes, bytes, bytes/s
    static volatile String name, error;          // archivo que se descarga; último error
    private static volatile boolean stop, discard;
    private static boolean useHttp;              // el cifrado no funcionó en esta pantalla
    private static final Handler ui = new Handler(Looper.getMainLooper());
    static Runnable onFinish;                    // lo pone Main para avisar y refrescar
    static volatile boolean finishedOk;          // la última descarga terminó bien

    // ── índice del servidor ──

    private static byte[] fetch(String rel) throws Exception {
        if (!useHttp) {
            try { return Net.get("https://" + BASE + rel, 15000); }
            catch (javax.net.ssl.SSLException e) { useHttp = true; }
        }
        return Net.get("http://" + BASE + rel, 15000);
    }

    private static final Pattern ROW = Pattern.compile(
            "<a href=\"([^\"?/][^\"]*)\">[^<]*</a>\\s*</td>\\s*<td[^>]*>\\s*(\\d{4}-\\d\\d-\\d\\d)[^<]*</td>\\s*<td[^>]*>\\s*([0-9.]+[KMG]?|-)\\s*</td>");

    /** Lee una carpeta del servidor («» = continentes, «south-america/» = países…). */
    static ArrayList<Entry> list(String folder) throws Exception {
        String html = new String(fetch(folder), "UTF-8");
        ArrayList<Entry> out = new ArrayList<Entry>();
        Matcher m = ROW.matcher(html);
        while (m.find()) {
            String href = m.group(1);
            Entry e = new Entry();
            e.dir = href.endsWith("/");
            if (!e.dir && !href.endsWith(".map")) continue;
            String slug = e.dir ? href.substring(0, href.length() - 1) : href.substring(0, href.length() - 4);
            if (folder.length() == 0 && "world".equals(slug)) continue;   // mapa mundial de bajo detalle: no sirve en el carro
            e.path = folder + href;
            e.name = folder.length() == 0 ? continent(slug) : place(slug);
            e.date = m.group(2);
            e.size = parseSize(m.group(3));
            out.add(e);
        }
        if (out.isEmpty()) throw new Exception("índice vacío");
        java.util.Collections.sort(out, new java.util.Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                int c = java.text.Collator.getInstance(I18n.loc).compare(a.name, b.name);
                return c != 0 ? c : (a.dir ? 1 : 0) - (b.dir ? 1 : 0);   // el país completo antes que sus regiones
            }
        });
        return out;
    }

    private static long parseSize(String s) {
        if (s.equals("-")) return 0;
        double mul = 1;
        char u = s.charAt(s.length() - 1);
        if (u == 'K') mul = 1024; else if (u == 'M') mul = 1024 * 1024; else if (u == 'G') mul = 1024.0 * 1024 * 1024;
        try { return (long) (Double.parseDouble(Character.isDigit(u) ? s : s.substring(0, s.length() - 1)) * mul); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String continent(String slug) {
        if ("africa".equals(slug)) return I18n.t("África");
        if ("asia".equals(slug)) return "Asia";
        if ("australia-oceania".equals(slug)) return I18n.t("Australia y Oceanía");
        if ("central-america".equals(slug)) return I18n.t("Centroamérica y el Caribe");
        if ("europe".equals(slug)) return I18n.t("Europa");
        if ("north-america".equals(slug)) return I18n.t("Norteamérica");
        if ("russia".equals(slug)) return I18n.t("Rusia");
        if ("south-america".equals(slug)) return I18n.t("Sudamérica");
        return pretty(slug);
    }

    private static java.util.HashMap<String, String> countries;
    private static Locale countriesLoc;   // idioma con que se armó la tabla (al cambiar el idioma se rehace)

    /** Nombre del país en el idioma de la app (germany → Alemania), o el del archivo bien escrito. */
    private static String place(String slug) {
        if (countries == null || !I18n.loc.equals(countriesLoc)) {
            countriesLoc = I18n.loc;
            java.util.HashMap<String, String> m = new java.util.HashMap<String, String>();
            for (String cc : Locale.getISOCountries()) {
                Locale l = new Locale("", cc);
                String en = l.getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.US).replace("&", "and").replace(' ', '-').replace(".", "").replace(",", "");
                String name = l.getDisplayCountry(I18n.loc);
                m.put(en, name);
                // «Myanmar (Burma)» → myanmar
                int par = en.indexOf("-(");
                if (par > 0 && !m.containsKey(en.substring(0, par))) m.put(en.substring(0, par), name);
            }
            m.put("great-britain", cn("GB"));
            m.put("czech-republic", cn("CZ"));
            m.put("us", cn("US"));
            // nombres del servidor de mapas que no coinciden con el nombre en inglés de Android
            String[][] alias = {{"ivory-coast", "CI"}, {"east-timor", "TL"}, {"congo-brazzaville", "CG"},
                {"congo-democratic-republic", "CD"}, {"macedonia", "MK"}, {"cape-verde", "CV"}, {"swaziland", "SZ"},
                {"myanmar", "MM"}, {"burma", "MM"}, {"south-korea", "KR"}, {"north-korea", "KP"}, {"laos", "LA"},
                {"syria", "SY"}, {"iran", "IR"}, {"russia", "RU"}, {"vietnam", "VN"}, {"palestine", "PS"},
                {"bosnia-herzegovina", "BA"}, {"taiwan", "TW"}, {"brunei", "BN"}, {"micronesia", "FM"},
                {"polynesie-francaise", "PF"}, {"wallis-et-futuna", "WF"}, {"pitcairn-islands", "PN"},
                {"saint-helena-ascension-and-tristan-da-cunha", "SH"}, {"moldova", "MD"}, {"bolivia", "BO"},
                {"venezuela", "VE"}, {"tanzania", "TZ"}, {"turkey", "TR"}, {"netherlands", "NL"},
                {"sao-tome-and-principe", "ST"}, {"comores", "KM"}};
            for (String[] a : alias) m.put(a[0], cn(a[1]));
            countries = m;
        }
        // #34: regiones que el servidor trae juntas
        if ("gcc-states".equals(slug)) return I18n.t("Países del Golfo") + " (" + cns("SA", "AE", "KW", "QA", "BH", "OM") + ")";
        if ("malaysia-singapore-brunei".equals(slug)) return cns("MY", "SG", "BN");
        if ("haiti-and-domrep".equals(slug)) return cns("HT", "DO");
        if ("guernsey-jersey".equals(slug)) return cns("GG", "JE");
        if ("ireland-and-northern-ireland".equals(slug)) return cn("IE") + sep() + I18n.t("Irlanda del Norte");
        if ("canary-islands".equals(slug)) return I18n.t("Islas Canarias");
        String n = countries.get(slug);
        if (n != null && n.length() > 0) return n;
        // países partidos en varios archivos: «new-zealand-1» → Nueva Zelanda 1
        java.util.regex.Matcher part = java.util.regex.Pattern.compile("(.+)-(\\d+)").matcher(slug);
        if (part.matches()) return place(part.group(1)) + " " + part.group(2);
        // «us-midwest» → Estados Unidos (Midwest)
        if (slug.startsWith("us-")) return cn("US") + " (" + pretty(slug.substring(3)) + ")";
        // «israel-and-palestine», «senegal-and-gambia»…
        int and = slug.indexOf("-and-");
        if (and > 0) {
            String a = countries.get(slug.substring(0, and)), b = countries.get(slug.substring(and + 5));
            if (a != null && b != null && a.length() > 0 && b.length() > 0) return a + sep() + b;
        }
        return pretty(slug);
    }

    /** Nombre de un país por su código ISO en el idioma de la app. */
    private static String cn(String cc) {
        String n = new Locale("", cc).getDisplayCountry(I18n.loc);
        return n != null && n.length() > 0 ? n : cc;
    }

    private static String cns(String... ccs) {
        StringBuilder b = new StringBuilder();
        for (String cc : ccs) { if (b.length() > 0) b.append(sep()); b.append(cn(cc)); }
        return b.toString();
    }

    /** Separador de listas: «، » en árabe, «、» en chino, «, » en los demás. */
    private static String sep() {
        String l = I18n.loc.getLanguage();
        return "ar".equals(l) ? "، " : "zh".equals(l) ? "、" : ", ";
    }

    private static String pretty(String slug) {
        StringBuilder b = new StringBuilder();
        for (String w : slug.split("[-_]")) {
            if (w.length() == 0) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    static String mb(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        return Math.max(1, Math.round(bytes / (1024.0 * 1024))) + " MB";
    }

    // ── archivos ──

    /** Carpeta de los mapas: Download/StradaOS (se conserva al reinstalar y sirve para la versión de prueba). */
    static File folder(Context c) {
        File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "StradaOS");
        if ((d.isDirectory() || d.mkdirs()) && d.canWrite()) return d;
        File app = c.getExternalFilesDir(null);
        return app != null ? new File(app, "mapas") : new File(c.getFilesDir(), "mapas");
    }

    static long free(File dir) {
        try {
            android.os.StatFs st = new android.os.StatFs(dir.getAbsolutePath());
            if (android.os.Build.VERSION.SDK_INT >= 18) return st.getAvailableBytes();
            return (long) st.getAvailableBlocks() * st.getBlockSize();
        } catch (Throwable e) { return -1; }
    }

    /** ¿Hay una descarga a medias guardada? */
    static boolean paused(Context c) {
        return !running && c.getSharedPreferences("launcher", 0).getString("map_dl_path", "").length() > 0;
    }

    static int percent() { return total > 0 ? (int) (done * 100 / total) : 0; }

    /** Texto del estado para los ajustes. */
    static String status(Context c) {
        SharedPreferences p = c.getSharedPreferences("launcher", 0);
        if (running) return I18n.t("Descargando") + " " + name + " · " + percent() + " % (" + mb(done) + " / " + mb(total) + ")"
                + (speed > 0 ? " · " + Math.round(speed / 1024.0) + " KB/s" : "");
        String path = p.getString("map_dl_path", "");
        if (path.length() == 0) return null;
        long t = p.getLong("map_dl_total", 0);
        File part = new File(p.getString("map_dl_dest", "") + ".part");
        long d = part.length();
        return I18n.t("Descarga en pausa") + ": " + p.getString("map_dl_name", "") + (t > 0 ? " · " + (d * 100 / t) + " %" : "")
                + (error != null ? " · " + error : "");
    }

    // ── descarga ──

    /** Empieza (o continúa) la descarga de path («south-america/colombia.map»). */
    static void start(final Context ctx, String path, String display) {
        if (running) return;
        final Context c = ctx.getApplicationContext();
        final SharedPreferences p = c.getSharedPreferences("launcher", 0);
        String file = path.substring(path.lastIndexOf('/') + 1);
        File dest = new File(folder(c), file);
        if (!path.equals(p.getString("map_dl_path", ""))) {
            // otro mapa: lo que hubiera a medias se descarta
            String old = p.getString("map_dl_dest", "");
            if (old.length() > 0) new File(old + ".part").delete();
            p.edit().putString("map_dl_path", path).putString("map_dl_name", display).putString("map_dl_dest", dest.getAbsolutePath())
                    .remove("map_dl_etag").remove("map_dl_total").apply();
        }
        resume(c);
    }

    /** Continúa la descarga guardada. */
    static void resume(final Context ctx) {
        if (running) return;
        final Context c = ctx.getApplicationContext();
        final SharedPreferences p = c.getSharedPreferences("launcher", 0);
        final String path = p.getString("map_dl_path", "");
        if (path.length() == 0) return;
        running = true; stop = false; discard = false; error = null; speed = 0;
        name = p.getString("map_dl_name", path);
        total = p.getLong("map_dl_total", 0);
        final File dest = new File(p.getString("map_dl_dest", ""));
        final File part = new File(dest.getAbsolutePath() + ".part");
        done = part.length();
        new Thread(new Runnable() { public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            Net.init(c);
            String err = null;
            boolean ok = false;
            int fails = 0;
            try {
                dest.getParentFile().mkdirs();
                while (!stop) {
                    try {
                        if (download(p, path, part)) { ok = true; break; }
                        fails = 0;
                    } catch (java.io.IOException e) {
                        // se cortó la señal o el servidor: se espera y se continúa donde iba (hasta 8 intentos seguidos)
                        if (++fails > 8 || stop) throw e;
                        error = I18n.t("sin conexión, reintentando…");
                        for (int i = 0; i < fails * 5 && !stop; i++) Thread.sleep(1000);
                        error = null;
                    }
                }
                if (ok) {
                    if (total > 0 && part.length() != total) throw new java.io.IOException(I18n.t("el archivo quedó incompleto"));
                    File tmp = new File(dest.getAbsolutePath() + ".old");
                    if (dest.exists() && !dest.renameTo(tmp)) dest.delete();
                    if (!part.renameTo(dest)) throw new java.io.IOException(I18n.t("no se pudo guardar el archivo"));
                    try {
                        OfflineMap.open(dest).close();   // que sea un mapa válido
                    } catch (java.io.IOException e) {
                        dest.delete();
                        if (tmp.exists()) tmp.renameTo(dest);
                        throw e;
                    }
                    tmp.delete();
                    p.edit().remove("map_dl_path").remove("map_dl_name").remove("map_dl_dest").remove("map_dl_etag").remove("map_dl_total")
                            .putString("map_file", dest.getAbsolutePath()).apply();
                }
            } catch (Throwable e) {
                err = e instanceof java.io.IOException && e.getMessage() != null ? e.getMessage() : e.toString();
                if (e instanceof java.net.UnknownHostException) err = I18n.t("sin internet");
            }
            if (discard) {
                part.delete();
                p.edit().remove("map_dl_path").remove("map_dl_name").remove("map_dl_dest").remove("map_dl_etag").remove("map_dl_total").apply();
                err = null;
            }
            final boolean fok = ok && err == null;
            final String ferr = stop ? null : err;
            ui.post(new Runnable() { public void run() {
                running = false;
                error = ferr;
                finishedOk = fok;
                if (fok) {
                    // si el mismo archivo ya estaba abierto, cerrarlo para leer el nuevo
                    if (LiveMapView.fileMap != null && LiveMapView.fileMap.file.equals(dest)) { LiveMapView.fileMap.close(); LiveMapView.fileMap = null; }
                    LiveMapView.loadFile(c);
                }
                if (onFinish != null) onFinish.run();
            }});
        }}).start();
    }

    /** Pausa (conserva lo descargado) o cancela (lo borra). */
    static void stop(boolean cancel) {
        discard = cancel;
        stop = true;
    }

    /** Descarga desde donde iba. true = terminado; false = el servidor cambió el archivo y hay que volver a empezar. */
    private static boolean download(SharedPreferences p, String path, File part) throws Exception {
        long from = part.length();
        String etag = p.getString("map_dl_etag", null);
        HttpURLConnection c = null;
        try {
            c = open(path, from, etag);
            int code = c.getResponseCode();
            if (code == 416 && total > 0 && from >= total) return true;   // ya estaba completo
            if (code != 200 && code != 206) throw new java.io.IOException("HTTP " + code);
            String newTag = c.getHeaderField("ETag");
            if (code == 200 || (etag != null && newTag != null && !etag.equals(newTag))) {
                // sin «Range» o el mapa cambió en el servidor: se empieza de cero
                if (code == 206) { c.disconnect(); new RandomAccessFile(part, "rw").setLength(0); p.edit().remove("map_dl_etag").apply(); return false; }
                from = 0;
            }
            long len = parseLong(c.getHeaderField("Content-Length"));
            total = len >= 0 ? from + len : total;
            p.edit().putString("map_dl_etag", newTag).putLong("map_dl_total", total).apply();
            if (free(part.getParentFile()) >= 0 && free(part.getParentFile()) < total - from + 20L * 1024 * 1024)
                throw new java.io.IOException(I18n.t("no hay espacio suficiente") + " (" + mb(total - from) + ")");
            RandomAccessFile out = new RandomAccessFile(part, "rw");
            try {
                out.setLength(from);
                out.seek(from);
                InputStream in = c.getInputStream();
                byte[] buf = new byte[64 * 1024];
                long t0 = System.currentTimeMillis(), b0 = from;
                done = from;
                int n;
                while (!stop && (n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (now - t0 >= 2000) { speed = (done - b0) * 1000 / (now - t0); t0 = now; b0 = done; }
                }
                in.close();
            } finally {
                out.close();
            }
            if (stop) return false;
            if (total > 0 && done < total) throw new java.io.IOException(I18n.t("se cortó la descarga"));
            return true;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static long parseLong(String s) {
        try { return s == null ? -1 : Long.parseLong(s.trim()); } catch (NumberFormatException e) { return -1; }
    }

    private static HttpURLConnection open(String path, long from, String etag) throws Exception {
        if (!useHttp) {
            try {
                HttpURLConnection c = Net.open("https://" + BASE + path, 20000);
                if (from > 0) { c.setRequestProperty("Range", "bytes=" + from + "-"); if (etag != null) c.setRequestProperty("If-Range", etag); }
                c.getResponseCode();   // aquí se hace el saludo seguro
                return c;
            } catch (javax.net.ssl.SSLException e) { useHttp = true; }
        }
        HttpURLConnection c = Net.open("http://" + BASE + path, 20000);
        if (from > 0) { c.setRequestProperty("Range", "bytes=" + from + "-"); if (etag != null) c.setRequestProperty("If-Range", etag); }
        return c;
    }
}
