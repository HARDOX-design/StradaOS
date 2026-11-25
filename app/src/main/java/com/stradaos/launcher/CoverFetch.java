package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Carátula desde internet (opcional, Ajustes → Música) para las canciones que llegan sin ella, sobre todo por
 * Bluetooth. Busca la canción en MusicBrainz y baja la portada del álbum de Cover Art Archive (bases abiertas, sin
 * clave); si ahí no está, o no responden, la busca en iTunes (también sin clave). Solo se envían el título y el artista. Lo encontrado se guarda en el equipo: la misma canción no se vuelve
 * a pedir y se ve aunque no haya señal.
 */
public class CoverFetch {

    public interface Listener { void onCover(String key, Bitmap art); }

    private static final String UA = "StradaOS ( https://github.com/lordnordic-debug/StradaOS )";
    private static final int MAX_FILES = 400;
    /** MusicBrainz pide como mucho una consulta por segundo. */
    private static long lastQueryAt;
    static String lastInfo = "—";

    /** Clave de la canción (también nombre del archivo guardado). */
    public static String key(String artist, String title) {
        return md5((clean(artist) + "|" + clean(title)).toLowerCase(Locale.US));
    }

    /** En otro hilo; el listener recibe null si no hay carátula (en ese mismo hilo). */
    public static void fetch(final Context c, final String artist, final String title, final Listener l) {
        final String key = key(artist, title);
        new Thread(new Runnable() {
            public void run() {
                Bitmap b = null;
                try { b = load(c, artist, title, key); }
                catch (Throwable e) { lastInfo = title + ": " + brief(e); }
                l.onCover(key, b);
            }
        }).start();
    }

    private static Bitmap load(Context c, String artist, String title, String key) throws Exception {
        File dir = new File(c.getCacheDir(), "covers");
        File f = new File(dir, key + ".jpg");
        File none = new File(dir, key + ".none");
        if (f.exists()) { lastInfo = title + ": guardada en el equipo"; return decode(f); }
        // «no hay» se recuerda una semana (MusicBrainz crece; después se vuelve a probar)
        if (none.exists() && System.currentTimeMillis() - none.lastModified() < 7L * 24 * 3600 * 1000) {
            lastInfo = title + ": sin carátula (ya se buscó)";
            return null;
        }
        String a = clean(artist), t = clean(title);
        if (a.length() == 0 || t.length() == 0) return null;
        Net.init(c);   // TLS 1.2 y certificados actuales en Android 4.x
        Bitmap b = null;
        Throwable err = null;
        boolean mbAnswered = false, itAnswered = false;
        // 2.36: el diagnóstico dice qué pasó en cada paso (el 11 nov. un «no encontrada» escondía el error real)
        String mb, it = "no se probó", dzEarlyErr = null;
        boolean dzEarly = false;
        if (android.os.Build.VERSION.SDK_INT < 21) {
            // 2.36 r2: en Android 4.x (Picanto y emuladores 4.1/4.2) MusicBrainz, Cover Art Archive (archive.org) e iTunes
            // rechazan su cifrado y exigen https; Deezer sí lo acepta. Se prueba primero para no esperar tres fallos.
            plainUsed = false;
            try {
                b = deezer(t, a, dir, f);
                dzEarly = true;
                if (b != null) { lastInfo = title + ": descargada de Deezer" + (plainUsed ? " (sin cifrar)" : ""); return b; }
            } catch (Throwable e) { dzEarlyErr = brief(e); }
        }
        plainUsed = false;
        try { b = musicBrainz(t, a, dir, f); mbAnswered = true; mb = b != null ? "sí" : "sin portada"; }
        catch (Throwable e) { err = e; mb = brief(e); }
        if (plainUsed) mb += " (sin cifrar)";
        if (b != null) { lastInfo = title + ": descargada de Cover Art Archive" + (plainUsed ? " (sin cifrar)" : ""); return b; }
        plainUsed = false;
        try { b = itunes(t, a, dir, f); itAnswered = true; it = b != null ? "sí" : "sin portada"; }
        catch (Throwable e) { if (err == null) err = e; it = brief(e); }
        if (plainUsed) it += " (sin cifrar)";
        if (b != null) { lastInfo = title + ": descargada de iTunes" + (plainUsed ? " (sin cifrar)" : ""); return b; }
        // 2.36 r2: Deezer, tercera fuente (su API y sus imágenes responden también sin cifrar)
        boolean dzAnswered = false;
        String dz;
        plainUsed = false;
        if (dzEarly) { dzAnswered = true; dz = "sin portada"; }          // ya se probó primero (Android 4.x)
        else if (dzEarlyErr != null) dz = dzEarlyErr;
        else try { b = deezer(t, a, dir, f); dzAnswered = true; dz = b != null ? "sí" : "sin portada"; }
        catch (Throwable e) { if (err == null) err = e; dz = brief(e); }
        if (plainUsed) dz += " (sin cifrar)";
        if (b != null) { lastInfo = title + ": descargada de Deezer" + (plainUsed ? " (sin cifrar)" : ""); return b; }
        if (mbAnswered && itAnswered && dzAnswered) {
            // las tres respondieron y no hay portada: se anota (sin internet no se llega aquí)
            save(dir, none, new byte[0]);
            lastInfo = title + ": no está en MusicBrainz, iTunes ni Deezer";
            return null;
        }
        lastInfo = title + ": falló · MusicBrainz: " + mb + " · iTunes: " + it + " · Deezer: " + dz;
        return null;
    }

    /** MusicBrainz + Cover Art Archive. null si respondieron y no hay portada. */
    private static Bitmap musicBrainz(String t, String a, File dir, File f) throws Exception {
        String base = "recording:\"" + lucene(t) + "\" AND artist:\"" + lucene(a) + "\"";
        // Primero solo álbumes, sencillos y EP oficiales: las canciones famosas tienen cientos de grabaciones y las
        // primeras suelen ser directos o recopilatorios (Bohemian Rhapsody daba un disco en vivo). Si no, sin filtro.
        String[] queries = {
            base + " AND status:official AND (primarytype:album OR primarytype:single OR primarytype:ep)"
                + " AND NOT secondarytype:live AND NOT secondarytype:compilation",
            base
        };
        List<String> tried = new ArrayList<String>();
        for (String q : queries) {
            for (String id : groups(q)) {
                if (tried.contains(id)) continue;
                if (tried.size() >= 6) break;   // como mucho 6 portadas probadas por canción
                tried.add(id);
                byte[] img;
                try { img = get("https://coverartarchive.org/release-group/" + id + "/front-500", 12000); }
                catch (NotFound e) { continue; }
                Bitmap b = BitmapFactory.decodeByteArray(img, 0, img.length);
                if (b == null) continue;
                save(dir, f, img);
                return b;
            }
        }
        return null;
    }

    /** Discos que no son el original: si hay otro, mejor ese (Bohemian Rhapsody daba la banda sonora de la película). */
    private static final String[] NOT_ORIGINAL = {"live", "directo", "vivo", "concierto", "unplugged", "acoustic", "acustico",
        "soundtrack", "banda sonora", "greatest", "hits", "best of", "essential", "esencial", "lo mejor", "grandes exitos",
        "collection", "coleccion", "remix", "karaoke", "tribute", "tributo"};

    /**
     * iTunes Search (sin clave). Solo vale un resultado del mismo artista con el mismo título: la búsqueda es por
     * palabras y también devuelve otras canciones. Entre los que valen, primero los discos originales.
     */
    private static Bitmap itunes(String t, String a, File dir, File f) throws Exception {
        String country = Locale.getDefault().getCountry();
        if (country == null || country.length() != 2) country = "US";
        JSONObject root = new JSONObject(new String(get("https://itunes.apple.com/search?media=music&entity=song&limit=25&country="
                + country + "&term=" + URLEncoder.encode(a + " " + t, "UTF-8"), 10000), "UTF-8"));
        JSONArray res = root.optJSONArray("results");
        String nt = norm(t), na = artistKey(a);
        String best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int i = 0; res != null && i < res.length(); i++) {
            JSONObject r = res.getJSONObject(i);
            String art = r.optString("artworkUrl100", "");
            if (art.length() == 0 || !nt.equals(norm(clean(r.optString("trackName"))))) continue;
            String ra = artistKey(r.optString("artistName"));
            if (ra.length() == 0 || !(ra.contains(na) || na.contains(ra))) continue;
            String va = norm(r.optString("collectionArtistName"));
            if (va.startsWith("various") || va.startsWith("varios")) continue;   // recopilatorio de varios artistas
            String col = " " + norm(r.optString("collectionName")) + " ";
            int penalty = 0;
            for (String w : NOT_ORIGINAL) if (col.contains(" " + w + " ")) penalty++;
            if (penalty < bestPenalty) { bestPenalty = penalty; best = art; }
        }
        if (best == null) return null;
        byte[] img = get(best.replace("100x100bb", "600x600bb"), 12000);
        Bitmap b = BitmapFactory.decodeByteArray(img, 0, img.length);
        if (b != null) save(dir, f, img);
        return b;
    }

    /** 2.36 r2: Deezer (api.deezer.com, sin clave). Mismo criterio que iTunes: título igual y artista que coincide. */
    private static Bitmap deezer(String t, String a, File dir, File f) throws Exception {
        String best = deezerFind(t, a, 10000, null);
        if (best == null) return null;
        byte[] img = get(best, 12000);
        Bitmap b = BitmapFactory.decodeByteArray(img, 0, img.length);
        if (b != null) save(dir, f, img);
        return b;
    }

    private static String deezerFind(String t, String a, int timeoutMs, StringBuilder trace) throws Exception {
        JSONObject root = new JSONObject(new String(get("https://api.deezer.com/search?limit=25&q="
                + URLEncoder.encode("artist:\"" + a + "\" track:\"" + t + "\"", "UTF-8"), timeoutMs, trace), "UTF-8"));
        JSONArray res = root.optJSONArray("data");
        String nt = norm(t), na = artistKey(a);
        String best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int i = 0; res != null && i < res.length(); i++) {
            JSONObject r = res.getJSONObject(i);
            JSONObject al = r.optJSONObject("album"), ar = r.optJSONObject("artist");
            String cover = al != null ? al.optString("cover_big", al.optString("cover_medium", "")) : "";
            if (cover.length() == 0 || !nt.equals(norm(clean(r.optString("title"))))) continue;
            String ra = artistKey(ar != null ? ar.optString("name") : "");
            if (ra.length() == 0 || !(ra.contains(na) || na.contains(ra))) continue;
            String col = " " + norm(al.optString("title")) + " ";
            int penalty = 0;
            for (String w : NOT_ORIGINAL) if (col.contains(" " + w + " ")) penalty++;
            if (penalty < bestPenalty) { bestPenalty = penalty; best = cover; }
        }
        return best;
    }

    /** Artista para comparar: «Fito & Fitipaldis» = «Fito y Fitipaldis». */
    private static String artistKey(String s) {
        return (" " + norm(s) + " ").replaceAll(" (y|and|e|et|und|the) ", " ").replaceAll("\\s+", " ").trim();
    }

    /** Para comparar nombres: minúsculas, sin tildes ni signos («ROSALÍA» = «Rosalía»). */
    private static String norm(String s) {
        if (s == null) return "";
        s = java.text.Normalizer.normalize(s.toLowerCase(Locale.US), java.text.Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}+", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /** «Various Artists» en MusicBrainz: sus recopilatorios traen otra portada, mejor ninguna. */
    private static final String VARIOUS = "89ad4ac3-39f7-470e-963a-56509c546377";

    /**
     * Grupos de álbum (release groups) de las grabaciones que coinciden bien, en orden: primero álbumes, sencillos y
     * EP oficiales del más antiguo al más nuevo (el original antes que reediciones y remixes), después bandas
     * sonoras, directos y el resto. Los recopilatorios de varios artistas no entran.
     */
    private static List<String> groups(String q) throws Exception {
        synchronized (CoverFetch.class) {
            long wait = lastQueryAt + 1100 - SystemClock.elapsedRealtime();
            if (wait > 0) Thread.sleep(wait);
            lastQueryAt = SystemClock.elapsedRealtime();
        }
        JSONObject root = new JSONObject(new String(get("https://musicbrainz.org/ws/2/recording/?fmt=json&limit=10&query="
                + URLEncoder.encode(q, "UTF-8"), 10000), "UTF-8"));
        JSONArray recs = root.optJSONArray("recordings");
        final HashMap<String, String> date = new HashMap<String, String>();   // grupo bueno → fecha más antigua
        List<String> good = new ArrayList<String>(), other = new ArrayList<String>();
        for (int i = 0; recs != null && i < recs.length(); i++) {
            JSONObject r = recs.getJSONObject(i);
            if (r.optInt("score", 0) < 90) continue;
            JSONArray rels = r.optJSONArray("releases");
            for (int j = 0; rels != null && j < rels.length(); j++) {
                JSONObject rel = rels.getJSONObject(j);
                JSONObject rg = rel.optJSONObject("release-group");
                if (rg == null || rg.optString("id", "").length() == 0) continue;
                JSONArray ac = rel.optJSONArray("artist-credit");
                JSONObject first = ac != null && ac.length() > 0 ? ac.optJSONObject(0) : null;
                if (first != null && first.optJSONObject("artist") != null
                        && VARIOUS.equals(first.optJSONObject("artist").optString("id"))) continue;
                String id = rg.getString("id");
                String type = rg.optString("primary-type", "");
                JSONArray sec = rg.optJSONArray("secondary-types");
                boolean official = "Official".equals(rel.optString("status", "Official"));
                if (official && (sec == null || sec.length() == 0)
                        && ("Album".equals(type) || "Single".equals(type) || "EP".equals(type))) {
                    String d = rel.optString("date", "");
                    if (d.length() == 0) d = "9999";
                    if (!good.contains(id)) { good.add(id); date.put(id, d); }
                    else if (d.compareTo(date.get(id)) < 0) date.put(id, d);
                    other.remove(id);
                } else if (!good.contains(id) && !other.contains(id)) {
                    other.add(id);
                }
            }
        }
        Collections.sort(good, new Comparator<String>() {
            public int compare(String x, String y) { return date.get(x).compareTo(date.get(y)); }
        });
        good.addAll(other);
        return good;
    }

    private static class NotFound extends Exception { }

    /** Se usó el respaldo sin cifrar en la última consulta (Android 4.x). */
    private static volatile boolean plainUsed;

    private static byte[] get(String url, int timeoutMs) throws Exception { return get(url, timeoutMs, null); }

    /**
     * GET siguiendo redirecciones a mano (Cover Art Archive manda a archive.org, en otro servidor). 2.36: en Android 4.x
     * (API < 21), si el saludo seguro falla (cifrados o certificados que la pantalla no maneja), se reintenta esa misma
     * dirección por http: solo viajan el título y el artista, o la imagen. {@code trace} anota cada salto (diagnóstico).
     */
    private static byte[] get(String url, int timeoutMs, StringBuilder trace) throws Exception {
        int busy = 0;
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (int hop = 0; hop < 8; hop++) {
            // 2.36 r2: en el Picanto, sin cifrar, el servidor mandaba otra vez a https y se daba vueltas hasta «demasiadas
            // redirecciones». Si una dirección se repite, ese servidor exige https y la pantalla no puede con su cifrado.
            if (!seen.add(url)) throw new Exception("el servidor exige https y esta pantalla no puede con su cifrado (" + host(url) + ")");
            HttpURLConnection h = Net.open(url, timeoutMs);
            h.setInstanceFollowRedirects(false);
            h.setRequestProperty("User-Agent", UA);
            long t0 = SystemClock.elapsedRealtime();
            try {
                int code;
                try { code = h.getResponseCode(); }
                catch (javax.net.ssl.SSLException e) {
                    if (trace != null) trace.append("  ").append(host(url)).append(": ").append(brief(e)).append('\n');
                    if (android.os.Build.VERSION.SDK_INT >= 21 || !url.startsWith("https://")) throw e;
                    url = "http://" + url.substring(8);
                    plainUsed = true;
                    if (trace != null) trace.append("  → sin cifrar\n");
                    continue;
                }
                if (trace != null) trace.append("  ").append(url.startsWith("https") ? "https " : "http ").append(host(url))
                        .append(": HTTP ").append(code).append(" (").append(SystemClock.elapsedRealtime() - t0).append(" ms)\n");
                if (code >= 300 && code < 400) {
                    String loc = h.getHeaderField("Location");
                    if (loc == null) throw new Exception("HTTP " + code);
                    String next = new URL(new URL(url), loc).toString();
                    // ya sin cifrar: si el servidor manda de vuelta a https, se sigue sin cifrar (http → https → http…)
                    if (plainUsed && next.startsWith("https://") && android.os.Build.VERSION.SDK_INT < 21 && url.startsWith("http://"))
                        next = "http://" + next.substring(8);
                    url = next;
                    continue;
                }
                if (code == 404) throw new NotFound();
                // MusicBrainz responde 503 cuando está saturado (o si se pregunta muy seguido): se reintenta
                if (code == 503 && busy++ < 2) { Thread.sleep(2000); hop--; seen.remove(url); continue; }   // ocupado: reintento, no bucle
                if (code != 200) throw new Exception("HTTP " + code);
                InputStream in = h.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                return out.toByteArray();
            } finally {
                h.disconnect();
            }
        }
        throw new Exception("demasiadas redirecciones");
    }

    private static String host(String url) {
        try { return new URL(url).getHost(); } catch (Throwable e) { return url; }
    }

    /** Error en una línea: tipo y mensaje (SSLHandshakeException: …, UnknownHostException: …, HTTP 503…). */
    static String brief(Throwable e) {
        if (e instanceof NotFound) return "no está (404)";
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c && (c.getMessage() == null || c.getMessage().length() < 4)) c = c.getCause();
        String m = c.getMessage();
        String r = c.getClass().getSimpleName() + (m != null ? ": " + m : "");
        return r.length() > 160 ? r.substring(0, 160) + "…" : r;
    }

    /**
     * 2.36: «Probar la búsqueda de carátulas» (Ajustes → Música): recorre los cuatro pasos con la canción que suena (o
     * una de ejemplo) y anota, servidor por servidor, el código o el error exacto y cuánto tardó. No guarda nada.
     */
    static String test(Context c, String artist, String title) {
        Net.init(c);
        StringBuilder b = new StringBuilder();
        b.append("Android ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        b.append("Conexión segura: ").append(Net.initInfo).append('\n');
        b.append("Fecha de la pantalla: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new java.util.Date()))
         .append(" (si está mal, los certificados fallan)\n");
        String a = clean(artist), t = clean(title);
        if (a.length() == 0 || t.length() == 0) { a = "Juanes"; t = "La Camisa Negra"; b.append("Sin canción con artista: se prueba con una de ejemplo\n"); }
        b.append("Canción: ").append(t).append(" — ").append(a).append("\n\n");
        String group = null, art = null;
        b.append("1. MusicBrainz (buscar la canción)\n");
        StringBuilder tr = new StringBuilder();
        try {
            JSONObject root = new JSONObject(new String(get("https://musicbrainz.org/ws/2/recording/?fmt=json&limit=5&query="
                    + URLEncoder.encode("recording:\"" + lucene(t) + "\" AND artist:\"" + lucene(a) + "\"", "UTF-8"), 10000, tr), "UTF-8"));
            b.append(tr);
            JSONArray recs = root.optJSONArray("recordings");
            for (int i = 0; recs != null && i < recs.length() && group == null; i++) {
                JSONArray rels = recs.getJSONObject(i).optJSONArray("releases");
                for (int j = 0; rels != null && j < rels.length() && group == null; j++) {
                    JSONObject rg = rels.getJSONObject(j).optJSONObject("release-group");
                    if (rg != null && rg.optString("id", "").length() > 0) group = rg.getString("id");
                }
            }
            b.append("  ").append(recs == null ? 0 : recs.length()).append(" resultados").append(group != null ? "" : ", sin álbum").append('\n');
        } catch (Throwable e) { b.append(tr).append("  ERROR: ").append(brief(e)).append('\n'); }
        b.append("2. Cover Art Archive (bajar la portada)\n");
        if (group == null) b.append("  no se probó (sin álbum de MusicBrainz)\n");
        else try {
            tr.setLength(0);
            byte[] img = get("https://coverartarchive.org/release-group/" + group + "/front-250", 12000, tr);
            b.append(tr).append("  imagen: ").append(img.length / 1024).append(" KB, ")
             .append(BitmapFactory.decodeByteArray(img, 0, img.length) != null ? "se ve bien" : "NO se pudo leer").append('\n');
        } catch (Throwable e) { b.append(tr).append("  ERROR: ").append(brief(e)).append('\n'); }
        b.append("3. iTunes (buscar la canción)\n");
        try {
            tr.setLength(0);
            JSONObject root = new JSONObject(new String(get("https://itunes.apple.com/search?media=music&entity=song&limit=5&term="
                    + URLEncoder.encode(a + " " + t, "UTF-8"), 10000, tr), "UTF-8"));
            b.append(tr);
            JSONArray res = root.optJSONArray("results");
            for (int i = 0; res != null && i < res.length() && art == null; i++) {
                String u = res.getJSONObject(i).optString("artworkUrl100", "");
                if (u.length() > 0) art = u;
            }
            b.append("  ").append(res == null ? 0 : res.length()).append(" resultados\n");
        } catch (Throwable e) { b.append(tr).append("  ERROR: ").append(brief(e)).append('\n'); }
        b.append("4. iTunes (bajar la portada)\n");
        if (art == null) b.append("  no se probó (sin resultado de iTunes)\n");
        else try {
            tr.setLength(0);
            byte[] img = get(art, 12000, tr);
            b.append(tr).append("  imagen: ").append(img.length / 1024).append(" KB, ")
             .append(BitmapFactory.decodeByteArray(img, 0, img.length) != null ? "se ve bien" : "NO se pudo leer").append('\n');
        } catch (Throwable e) { b.append(tr).append("  ERROR: ").append(brief(e)).append('\n'); }
        String dz = null;
        b.append("5. Deezer (buscar la canción)\n");
        try {
            tr.setLength(0);
            dz = deezerFind(t, a, 10000, tr);
            b.append(tr).append("  ").append(dz != null ? "encontrada" : "sin resultado").append('\n');
        } catch (Throwable e) { b.append(tr).append("  ERROR: ").append(brief(e)).append('\n'); }
        b.append("6. Deezer (bajar la portada)\n");
        if (dz == null) b.append("  no se probó (sin resultado de Deezer)\n");
        else try {
            tr.setLength(0);
            byte[] img = get(dz, 12000, tr);
            b.append(tr).append("  imagen: ").append(img.length / 1024).append(" KB, ")
             .append(BitmapFactory.decodeByteArray(img, 0, img.length) != null ? "se ve bien" : "NO se pudo leer").append('\n');
        } catch (Throwable e) { b.append(tr).append("  ERROR: ").append(brief(e)).append('\n'); }
        return b.toString();
    }

    /** Quita lo que no forma parte del nombre: «(Remastered 2011)», «[Live]», «- Radio Edit», «feat. …». */
    static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("[\\(\\[][^\\)\\]]*[\\)\\]]", " ");
        s = s.replaceAll("(?i)\\s+(feat\\.?|ft\\.?|featuring)\\s.*$", "");
        s = s.replaceAll("(?i)\\s+-\\s+.*(remaster|version|versión|edit|live|mix|mono|stereo).*$", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String lucene(String s) {
        return s.replace("\\", " ").replace("\"", " ");
    }

    private static Bitmap decode(File f) {
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b == null) f.delete();
        else f.setLastModified(System.currentTimeMillis());   // las más usadas no se borran al limpiar
        return b;
    }

    private static void save(File dir, File f, byte[] data) {
        try {
            dir.mkdirs();
            FileOutputStream o = new FileOutputStream(f);
            o.write(data);
            o.close();
            File[] all = dir.listFiles();
            if (all != null && all.length > MAX_FILES) {
                Arrays.sort(all, new Comparator<File>() {
                    public int compare(File x, File y) { long a = x.lastModified(), b = y.lastModified();   // Long.compare es de Android 4.4
                        return a < b ? -1 : a > b ? 1 : 0; }
                });
                for (int i = 0; i < all.length - MAX_FILES; i++) all[i].delete();
            }
        } catch (Throwable ignored) { }
    }

    /** Borra todo lo guardado. Devuelve cuántas carátulas había. */
    static int clear(Context c) {
        File[] all = new File(c.getCacheDir(), "covers").listFiles();
        int n = 0;
        if (all != null) for (File f : all) { if (f.getName().endsWith(".jpg")) n++; f.delete(); }
        return n;
    }

    private static String md5(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format(Locale.US, "%02x", x & 0xff));
            return b.toString();
        } catch (Throwable e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
