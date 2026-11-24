package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Busca versiones nuevas en GitHub Releases.
 * Orden: API de GitHub → update.json del repositorio (raw) → update.json por jsDelivr (también http,
 * para pantallas con Android 4.x que no logran el cifrado de GitHub; solo si ninguna fuente cifrada respondió).
 * Seguridad: de las fuentes solo se toma el número de versión. El APK se descarga siempre de los Releases del
 * repositorio y, antes de instalarlo, se comprueba que sea StradaOS firmado con la misma llave (Main.verifyApk).
 */
public class Updates {

    public static final String OWNER = "lordnordic-debug";
    public static final String REPO = "StradaOS";
    public static final String REPO_URL = "https://github.com/" + OWNER + "/" + REPO;
    public static final String RELEASES_URL = REPO_URL + "/releases/latest";
    public static final String ISSUES_URL = REPO_URL + "/issues";
    public static final String DONATE_DEFAULT = REPO_URL + "#-apoya-el-proyecto";
    /** Único lugar del que se descarga el APK (los Releases del repositorio). */
    static final String APK_PREFIX = REPO_URL + "/releases/download/v";

    static final String[] JSON_URLS = {
        "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/main/update.json",
        "https://cdn.jsdelivr.net/gh/" + OWNER + "/" + REPO + "@main/update.json",
        "http://cdn.jsdelivr.net/gh/" + OWNER + "/" + REPO + "@main/update.json",
    };

    public static class Release {
        public String source;
        public String version;   // "2.8.0"
        public String notes = "";
        public String pageUrl = RELEASES_URL;
        public String apkUrl;
    }

    public interface Listener { void onResult(Release newer, String error); }

    static String lastError = "";
    /** Resultado de cada fuente en la última búsqueda (para mostrarlo en Acerca de). */
    static String lastReport = "";

    static String shortErr(Throwable e) {
        String m = String.valueOf(e);
        if (m.contains("SSL") || m.contains("andshake")) return "cifrado no compatible";
        if (m.contains("UnknownHost")) return "sin internet";
        if (m.contains("imeout")) return "tiempo agotado";
        if (m.contains("HTTP ")) return m.substring(m.indexOf("HTTP "));
        return "error de red";
    }

    public static String donateUrl(Context c) {
        String u = c.getSharedPreferences("launcher", 0).getString("donate_url", "");
        return trustedDonate(u) ? u : DONATE_DEFAULT;
    }

    public static String currentVersion(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Throwable e) { return "0"; }
    }

    /**
     * true si la app se instaló desde una tienda tipo F-Droid (F-Droid, IzzyOnDroid con Droid-ify, Neo Store…).
     * En ese caso las actualizaciones las entrega esa tienda, y bajarlas de GitHub es opcional (opt-in),
     * como piden las reglas de IzzyOnDroid y F-Droid.
     */
    public static boolean fromFdroid(Context c) {
        String i = installer(c);
        if (i == null) return false;
        i = i.toLowerCase(java.util.Locale.US);
        return i.contains("fdroid") || i.contains("droidify") || i.contains("neostore") || i.contains("foxydroid") || i.contains("izzy");
    }

    static String installer(Context c) {
        try { return c.getPackageManager().getInstallerPackageName(c.getPackageName()); } catch (Throwable e) { return null; }
    }

    /** Nombre de la tienda para mostrarlo (por ejemplo «F-Droid / IzzyOnDroid»). */
    public static String storeName(Context c) {
        String i = installer(c);
        if (i == null) return "tu tienda de apps";
        if (i.contains("droidify")) return "Droid-ify";
        if (i.contains("neostore") || i.contains("machiav3lli")) return "Neo Store";
        return "F-Droid / IzzyOnDroid";
    }

    /** Busca en segundo plano. newer = null si ya está al día. */
    public static void check(final Context ctx, final Listener l) {
        new Thread(new Runnable() {
            public void run() {
                // Se consultan todas las fuentes y gana la versión más alta: una copia en caché (jsDelivr)
                // puede estar atrasada horas, y en Android 4.x algunas fuentes fallan por el cifrado.
                Release r = null;
                StringBuilder rep = new StringBuilder();
                String[] names = {"GitHub", "GitHub (raw)", "jsDelivr", "jsDelivr (http)"};
                boolean secureOk = false;
                for (int i = 0; i <= JSON_URLS.length; i++) {
                    boolean plain = i > 0 && JSON_URLS[i - 1].startsWith("http:");
                    // la fuente sin cifrar (para Android 4.x) solo se consulta si ninguna fuente segura respondió
                    if (plain && secureOk) continue;
                    Release c = null;
                    try { c = i == 0 ? fromApi() : fromJson(ctx, JSON_URLS[i - 1]); }
                    catch (Throwable e) { lastError = names[i] + ": " + e; rep.append(names[i]).append(": ").append(shortErr(e)).append('\n'); }
                    if (c == null) continue;
                    if (!plain) secureOk = true;
                    rep.append(names[i]).append(": ").append(c.version).append('\n');
                    if (r == null || compare(c.version, r.version) > 0) { r = c; r.source = names[i]; }
                    if (i == 0) break; // la API de GitHub siempre está al día
                }
                lastReport = rep.toString().trim();
                SharedPreferences p = ctx.getSharedPreferences("launcher", 0);
                if (r == null) { l.onResult(null, lastError); return; }
                p.edit().putLong("upd_last", System.currentTimeMillis()).putString("upd_latest", r.version).apply();
                l.onResult(compare(r.version, currentVersion(ctx)) > 0 ? r : null, null);
            }
        }).start();
    }

    private static Release fromApi() throws Exception {
        JSONObject o = new JSONObject(new String(Net.get("https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest", 10000), "UTF-8"));
        Release r = new Release();
        r.version = safeVersion(o.getString("tag_name"));
        r.notes = o.optString("body", "");
        links(r);
        return r;
    }

    private static Release fromJson(Context c, String url) throws Exception {
        JSONObject o = new JSONObject(new String(Net.get(url, 10000), "UTF-8"));
        Release r = new Release();
        r.version = safeVersion(o.getString("version"));
        r.notes = o.optString("notes", "");
        // los enlaces no se toman del archivo: se arman siempre hacia los Releases del repositorio
        links(r);
        // el enlace de donación solo se acepta por una fuente cifrada y hacia sitios conocidos
        String d = o.optString("donate_url", "");
        if (url.startsWith("https://") && trustedDonate(d)) c.getSharedPreferences("launcher", 0).edit().putString("donate_url", d).apply();
        return r;
    }

    /** Solo números y puntos (p. ej. «2.11.1»); cualquier otra cosa se rechaza. */
    static String safeVersion(String tag) throws Exception {
        String v = clean(tag);
        if (!v.matches("\\d{1,4}(\\.\\d{1,4}){0,3}")) throw new Exception("versión no válida");
        return v;
    }

    /** Página y APK de la versión, siempre en github.com/lordnordic-debug/StradaOS. */
    static void links(Release r) {
        r.pageUrl = REPO_URL + "/releases/tag/v" + r.version;
        r.apkUrl = APK_PREFIX + r.version + "/StradaOS-" + r.version + ".apk";
    }

    static boolean trustedDonate(String u) {
        return u != null && (u.startsWith("https://ko-fi.com/") || u.startsWith("https://github.com/sponsors/")
                || u.startsWith("https://www.paypal.com/") || u.startsWith(REPO_URL));
    }

    static String clean(String tag) {
        String t = tag.trim();
        while (t.length() > 0 && !Character.isDigit(t.charAt(0))) t = t.substring(1);
        return t;
    }

    /** Compara "2.10.1" con "2.9": >0 si a es más nueva. */
    public static int compare(String a, String b) {
        String[] x = clean(a).split("[^0-9]+"), y = clean(b).split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && x[i].length() > 0 ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length && y[i].length() > 0 ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p > q ? 1 : -1;
        }
        return 0;
    }

    /** Notas de la versión sin marcas de Markdown, para mostrarlas en pantalla. */
    /**
     * Notas en el idioma de la pantalla. changelogs/X.md trae primero el español y después secciones que empiezan con
     * una línea «[](#lang-en)», «[](#lang-pt)»…: un enlace vacío que GitHub no muestra y que {@link #plain} de las
     * versiones anteriores convierte en una línea en blanco (así a ellas les salen las dos, sin marcas raras). Sin
     * sección para el idioma elegido, en inglés; en español, la primera. Se quita lo que va después de una línea «---»
     * (las instrucciones de instalación del Release: el aviso ya descarga e instala).
     */
    public static String localNotes(String md) {
        if (md == null) return "";
        java.util.Map<String, StringBuilder> secs = new java.util.LinkedHashMap<String, StringBuilder>();
        String cur = "es";
        secs.put(cur, new StringBuilder());
        boolean cut = false;
        for (String line : md.replace("\r", "").split("\n")) {
            java.util.regex.Matcher m = LANG_MARK.matcher(line);
            if (m.matches()) {
                cur = m.group(1).toLowerCase(java.util.Locale.US);
                if (!secs.containsKey(cur)) secs.put(cur, new StringBuilder());
                cut = false;
                continue;
            }
            if (line.trim().equals("---")) cut = true;
            if (!cut) secs.get(cur).append(line).append('\n');
        }
        String lang = I18n.lang != null ? I18n.lang : "es";
        StringBuilder pick = secs.get(lang);
        if ((pick == null || pick.toString().trim().length() == 0) && !"es".equals(lang)) pick = secs.get("en");
        if (pick == null || pick.toString().trim().length() == 0) pick = secs.get("es");
        return pick.toString().trim();
    }

    private static final java.util.regex.Pattern LANG_MARK =
        java.util.regex.Pattern.compile("^\\s*\\[\\]\\(#lang-([a-zA-Z]{2})\\)\\s*$");

    public static String plain(String md) {
        if (md == null) return "";
        StringBuilder b = new StringBuilder();
        for (String line : md.replace("\r", "").split("\n")) {
            String t = line.replaceAll("^#+\\s*", "").replaceAll("\\*\\*|__|`", "")
                    .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "").replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
            if (t.trim().startsWith("- ") || t.trim().startsWith("* ")) t = "• " + t.trim().substring(2);
            b.append(t).append('\n');
        }
        return b.toString().trim();
    }
}
