package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copia de seguridad de la configuración (2.21, idea de ashoksoft en Reddit): todo lo que el usuario configuró
 * (ajustes, mantenimientos, kilometraje, aceite y la foto del vehículo) en UN archivo que se guarda en la memoria de
 * la pantalla o en una USB. Nada sale a internet: es un archivo de texto (JSON) que el usuario puede copiar.
 *
 * Sirve para las pantallas que hay que restablecer de fábrica cada tanto (se pierde todo) y para pasar la
 * configuración a otra pantalla.
 */
public class Backup {
    static final String PREFIX = "StradaOS-backup", EXT = ".json", AUTO = "StradaOS-backup-auto.json";
    static final int FORMAT = 1;

    /** Datos que no se copian ni se restauran: son de este equipo o de este momento (descargas, cachés, pruebas). */
    static final String[] SKIP = {"map_dl_", "map_file", "map_provider", "upd_last", "sim_screen", "city_key", "city_name",
        "opens", "donate_url", "backup_", "first_use", "enjoy_", "theme_last_dark"};

    static boolean skip(String k) {
        for (String p : SKIP) if (k.startsWith(p)) return true;
        return false;
    }

    /** Lo que se sabe de una copia sin restaurarla. */
    static class Info {
        File file;
        long created;
        String version, device;
        int settings;
        boolean photo;
    }

    // ───────────────────────── crear ─────────────────────────

    private static String photoCache;
    private static long photoStamp = -1;

    /** El texto de la copia (JSON). */
    static String build(Context c) throws Exception {
        SharedPreferences p = c.getSharedPreferences("launcher", 0);
        JSONObject root = new JSONObject();
        root.put("app", "StradaOS");
        root.put("format", FORMAT);
        root.put("version", Updates.currentVersion(c));
        root.put("created", System.currentTimeMillis());
        root.put("device", (Build.MANUFACTURER + " " + Build.MODEL).trim());
        root.put("api", Build.VERSION.SDK_INT);
        JSONObject pr = new JSONObject();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k == null || v == null || skip(k)) continue;
            JSONObject o = new JSONObject();
            if (v instanceof String) o.put("t", "s").put("v", v);
            else if (v instanceof Boolean) o.put("t", "b").put("v", ((Boolean) v).booleanValue());
            else if (v instanceof Integer) o.put("t", "i").put("v", ((Integer) v).intValue());
            else if (v instanceof Long) o.put("t", "l").put("v", ((Long) v).longValue());
            else if (v instanceof Float) {
                float f = (Float) v;
                if (Float.isNaN(f) || Float.isInfinite(f)) continue;
                o.put("t", "f").put("v", String.valueOf(f));   // como texto: vuelve exactamente el mismo número
            } else if (v instanceof Set) {
                JSONArray a = new JSONArray();
                for (Object x : (Set<?>) v) a.put(String.valueOf(x));
                o.put("t", "ss").put("v", a);
            } else continue;
            pr.put(k, o);
        }
        root.put("prefs", pr);
        // la foto del vehículo (un PNG pequeño) va dentro del mismo archivo
        File photo = new File(c.getFilesDir(), CarPhoto.FILE);
        if (photo.isFile() && photo.length() > 0 && photo.length() < 6L * 1024 * 1024) {
            synchronized (Backup.class) {
                if (photoCache == null || photoStamp != photo.lastModified()) {
                    photoCache = Base64.encodeToString(readAll(new FileInputStream(photo)), Base64.NO_WRAP);
                    photoStamp = photo.lastModified();
                }
                root.put("photo", photoCache);
            }
        }
        return root.toString();
    }

    static byte[] readAll(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 24 * 1024 * 1024) throw new Exception("archivo demasiado grande");
            }
            return bo.toByteArray();
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
        }
    }

    static String name() {
        return PREFIX + "-" + new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(new java.util.Date()) + EXT;
    }

    // ───────────────────────── dónde guardar ─────────────────────────

    /** Memoria de la pantalla (la «tarjeta SD» interna). */
    static File primary() { return Environment.getExternalStorageDirectory(); }

    /** Carpetas propias de StradaOS en cada memoria (Android 4.4+): se puede escribir en ellas sin permisos. */
    static File[] appDirs(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 19) {
                File[] d = c.getExternalFilesDirs(null);
                if (d != null) return d;
            }
            File one = c.getExternalFilesDir(null);
            return one != null ? new File[]{one} : new File[0];
        } catch (Throwable e) { return new File[0]; }
    }

    private static String canon(File f) {
        try { return f.getCanonicalPath(); } catch (Throwable e) { return f.getAbsolutePath(); }
    }

    /** Memorias USB y tarjetas SD conectadas (la raíz de cada una). */
    static List<File> removable(Context c) {
        LinkedHashMap<String, File> out = new LinkedHashMap<String, File>();
        String prim = canon(primary());
        // 1) Lo que Android reconoce como memoria (4.4+): …/Android/data/<paquete>/files → la raíz está 4 carpetas arriba
        for (File d : appDirs(c)) {
            if (d == null) continue;
            File r = d;
            for (int i = 0; i < 4 && r != null; i++) r = r.getParentFile();
            if (r != null && r.isDirectory() && !canon(r).equals(prim)) out.put(canon(r), r);
        }
        // 2) Las carpetas donde las pantallas de carro montan las USB (muchas no avisan a Android)
        ArrayList<File> cand = new ArrayList<File>();
        String sec = System.getenv("SECONDARY_STORAGE");
        if (sec != null) for (String r : sec.split(":")) if (r.length() > 0) cand.add(new File(r));
        for (String parent : new String[]{"/storage", "/mnt", "/mnt/media_rw", "/mnt/usb_storage"}) {
            File[] fs = null;
            try { fs = new File(parent).listFiles(); } catch (Throwable ignored) { }
            if (fs == null) continue;
            for (File f : fs) {
                String n = f.getName().toLowerCase(java.util.Locale.US);
                boolean vol = n.matches("[0-9a-f]{4}-[0-9a-f]{4}") || n.startsWith("usb") || n.startsWith("udisk") || n.startsWith("uhost")
                    || n.startsWith("extsd") || n.startsWith("external_sd") || n.startsWith("sdcard1") || n.startsWith("ext_sd")
                    || n.equals("card") || n.startsWith("tf") || n.startsWith("usbdisk") || n.startsWith("usb_storage");
                if (vol || parent.equals("/mnt/usb_storage")) cand.add(f);
            }
        }
        cand.add(new File("/udisk"));
        for (int i = 0; i < cand.size() && i < 60; i++) {
            File f = cand.get(i);
            try {
                if (!f.isDirectory() || !f.canRead()) continue;
                String cp = canon(f);
                if (cp.equals(prim) || out.containsKey(cp)) continue;
                File[] ls = f.listFiles();
                if (ls == null) continue;
                if (!f.canWrite()) {
                    // punto de montaje vacío (sin memoria puesta) o carpeta que solo agrupa memorias
                    // (p. ej. /mnt/usb_storage/USB_DISK0/udisk0): se miran las de adentro
                    boolean onlyDirs = ls.length > 0 && ls.length <= 4;
                    for (File ch : ls) if (!ch.isDirectory()) onlyDirs = false;
                    if (ls.length == 0) continue;
                    if (onlyDirs) { for (File ch : ls) cand.add(ch); continue; }
                }
                out.put(cp, f);
            } catch (Throwable ignored) { }
        }
        return new ArrayList<File>(out.values());
    }

    /**
     * Guarda la copia en una memoria. Primero en la carpeta «StradaOS» a la vista del usuario; si Android no deja
     * escribir ahí (memorias extraíbles en Android 4.4 a 10), en la carpeta propia de StradaOS dentro de esa memoria.
     */
    static File save(Context c, File root, String name, String json) throws Exception {
        Exception first = null;
        try {
            return writeFile(new File(root, "StradaOS"), name, json);
        } catch (Exception e) { first = e; }
        String rp = canon(root);
        for (File d : appDirs(c)) {
            if (d == null || !canon(d).startsWith(rp)) continue;
            try { return writeFile(d, name, json); } catch (Exception ignored) { }
        }
        throw first;
    }

    private static File writeFile(File dir, String name, String json) throws Exception {
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) throw new java.io.IOException("no se pudo crear " + dir);
        File tmp = new File(dir, name + ".tmp"), f = new File(dir, name);
        byte[] data = json.getBytes("UTF-8");
        FileOutputStream os = new FileOutputStream(tmp);
        try {
            os.write(data);
            os.flush();
            try { os.getFD().sync(); } catch (Throwable ignored) { }   // que quede en la memoria aunque se apague el carro
        } finally {
            os.close();
        }
        if (tmp.length() != data.length) { tmp.delete(); throw new java.io.IOException("la memoria no guardó el archivo completo"); }
        if (f.exists()) f.delete();
        if (!tmp.renameTo(f)) { tmp.delete(); throw new java.io.IOException("no se pudo guardar " + f); }
        return f;
    }

    static void write(OutputStream os, String json) throws Exception {
        try { os.write(json.getBytes("UTF-8")); os.flush(); } finally { os.close(); }
    }

    /**
     * Copia automática: un solo archivo (siempre el mismo nombre) en la memoria de la pantalla y en cada USB conectada.
     * Devuelve cuántas se guardaron.
     */
    static int auto(Context c) {
        int ok = 0;
        try {
            String json = build(c);
            ArrayList<File> roots = new ArrayList<File>();
            roots.add(primary());
            roots.addAll(removable(c));
            for (File r : roots) {
                try { save(c, r, AUTO, json); ok++; } catch (Throwable ignored) { }
            }
        } catch (Throwable e) {
            Guard.log("Copia automática: " + e);
        }
        return ok;
    }

    // ───────────────────────── buscar y leer ─────────────────────────

    /** Copias que hay en la memoria de la pantalla y en las USB, de la más nueva a la más vieja. */
    static List<Info> find(Context c) {
        ArrayList<File> dirs = new ArrayList<File>();
        ArrayList<File> roots = new ArrayList<File>();
        roots.add(primary());
        roots.addAll(removable(c));
        for (File r : roots) {
            dirs.add(r);
            dirs.add(new File(r, "StradaOS"));
            dirs.add(new File(r, "Download"));
            dirs.add(new File(r, "Download/StradaOS"));
        }
        for (File d : appDirs(c)) if (d != null) dirs.add(d);
        // y una búsqueda amplia (3 carpetas de profundidad) por si la pantalla monta las USB en otro lado
        HashSet<String> walked = new HashSet<String>();
        int[] budget = {2500};
        for (String r : new String[]{canon(primary()), "/storage", "/mnt"}) walk(new File(r), 0, dirs, walked, budget);
        ArrayList<Info> out = new ArrayList<Info>();
        HashSet<String> seen = new HashSet<String>();
        for (File d : dirs) {
            File[] fs = null;
            try { fs = d.listFiles(); } catch (Throwable ignored) { }
            if (fs == null) continue;
            for (File f : fs) {
                String n = f.getName();
                if (!f.isFile() || !n.startsWith(PREFIX) || !n.endsWith(EXT) || !seen.add(canon(f))) continue;
                try {
                    Info i = peek(new String(readAll(new FileInputStream(f)), "UTF-8"));
                    if (i == null) continue;
                    i.file = f;
                    if (i.created <= 0) i.created = f.lastModified();
                    out.add(i);
                } catch (Throwable ignored) { }
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<Info>() {
            public int compare(Info a, Info b) { return a.created < b.created ? 1 : a.created > b.created ? -1 : 0; }
        });
        return out;
    }

    private static void walk(File d, int depth, List<File> dirs, Set<String> seen, int[] budget) {
        if (depth > 3 || budget[0]-- <= 0) return;
        if (!seen.add(canon(d))) return;
        File[] fs = null;
        try { fs = d.listFiles(); } catch (Throwable ignored) { }
        if (fs == null) return;
        boolean has = false;
        for (File f : fs) {
            String n = f.getName();
            if (n.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (n.equals("Android") || n.equals("obb") || n.equals("asec") || n.equals("secure") || n.equals("runtime") || n.equals("DCIM")) continue;
                walk(f, depth + 1, dirs, seen, budget);
            } else if (n.startsWith(PREFIX) && n.endsWith(EXT)) has = true;
        }
        if (has) dirs.add(d);
    }

    /** Datos de una copia, o null si el texto no es una copia de StradaOS. */
    static Info peek(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject pr = root.optJSONObject("prefs");
            if (!"StradaOS".equals(root.optString("app")) || pr == null) return null;
            Info i = new Info();
            i.created = root.optLong("created", 0);
            i.version = root.optString("version", "");
            i.device = root.optString("device", "");
            i.settings = pr.length();
            i.photo = root.optString("photo", "").length() > 0;
            return i;
        } catch (Throwable e) { return null; }
    }

    // ───────────────────────── restaurar ─────────────────────────

    /**
     * Reemplaza la configuración por la de la copia. Devuelve null si salió bien, o el motivo (en español, se
     * traduce al mostrarlo). No toca nada si el archivo no sirve.
     */
    static String apply(Context c, String json) {
        JSONObject root, pr;
        try {
            root = new JSONObject(json);
            pr = root.optJSONObject("prefs");
        } catch (Throwable e) { return "Este archivo no es una copia de seguridad de StradaOS"; }
        if (!"StradaOS".equals(root.optString("app")) || pr == null) return "Este archivo no es una copia de seguridad de StradaOS";
        if (root.optInt("format", 1) > FORMAT) return "Esta copia se hizo con una versión más nueva de StradaOS. Actualiza StradaOS y vuelve a intentarlo";
        try {
            SharedPreferences p = c.getSharedPreferences("launcher", 0);
            SharedPreferences.Editor e = p.edit();
            // se quita lo actual (menos lo propio de este equipo) y se pone lo de la copia
            for (String k : p.getAll().keySet()) if (!skip(k)) e.remove(k);
            Iterator<String> it = pr.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (skip(k)) continue;
                JSONObject o = pr.optJSONObject(k);
                if (o == null) continue;
                String t = o.optString("t");
                if ("s".equals(t)) e.putString(k, o.getString("v"));
                else if ("b".equals(t)) e.putBoolean(k, o.getBoolean("v"));
                else if ("i".equals(t)) e.putInt(k, o.getInt("v"));
                else if ("l".equals(t)) e.putLong(k, o.getLong("v"));
                else if ("f".equals(t)) e.putFloat(k, Float.parseFloat(o.getString("v")));
                else if ("ss".equals(t)) {
                    JSONArray a = o.getJSONArray("v");
                    HashSet<String> set = new HashSet<String>();
                    for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                    e.putStringSet(k, set);
                }
            }
            e.putBoolean("setup_done", true);   // quien restaura ya configuró: no se repite el asistente
            String photo = root.optString("photo", "");
            byte[] data = photo.length() > 0 ? Base64.decode(photo, Base64.DEFAULT) : null;   // antes de guardar: si está dañada, no se cambia nada
            if (!e.commit()) return "No se pudo guardar la configuración";
            if (data != null && data.length > 0) {
                FileOutputStream os = new FileOutputStream(new File(c.getFilesDir(), CarPhoto.FILE));
                try { os.write(data); } finally { os.close(); }
            }
            return null;
        } catch (Throwable ex) {
            Guard.log("Restaurar copia: " + ex);
            return "La copia está dañada o incompleta";
        }
    }
}
