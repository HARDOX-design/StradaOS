package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Desbloqueo de los temas de pago (Fase 3, 2.27).
 *
 * Cada pantalla tiene un ID corto (XXXX-XXXX) que la persona escribe en el mensaje de su donación en Ko-fi.
 * Andrés corre la Action «Licencia» con ese ID: crea un código STRD-XXXX-XXXX y una licencia firmada con la
 * llave de publicación de StradaOS (la misma del APK, guardada en los secretos de GitHub) en la rama «licencias».
 * La app solo trae la llave pública: con el código baja su licencia una vez (o la lee de una USB) y la guarda.
 *
 * Licencia:   STRADAOS-LIC-1 / id=XXXXXXXX / temas=faro / fecha=AAAA-MM-DD / firma=base64
 * Se firma:   "STRADAOS-LIC-1|" + id + "|" + temas + "|" + fecha  (SHA256withRSA)
 * Archivo:    l/<los 32 primeros hex de sha256("strd-lic|" + id + "|" + código)>.txt
 */
final class Lic {
    private Lic() { }

    static final String HEAD = "STRADAOS-LIC-1";
    private static final String PUB = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqwazG5DcLSkf7nnyL6Jst4NvL5SBN8DWKCmvmX46NOO8Tmge50pUrbxqjxfdTPhMmIemZBCXCBGGmYSekGFYYQwifAYT6mXbbrD2yO99JzqkAK/T7KlIbx0w6Hg/7QOlA8QNGOs3EkSPkmGBNumr+m6V1Ya+OezwyX2RJDGIaj0LNTCKFYkFuC1iFXBa2AfNVCjoKRm0WAZ5nrizZi48MDNzw50lghh35b4vhfuo3VmkOeOLTgBrMm6BwQLAWoS3KyuDFnsgEGPOwyqkYzRZ4R2bzZP+Vdtze1AmhegI6x1mfehFgCQedBdC4ztS7yKWq2zmdJa5J9Ol/GwXT2AShwIDAQAB";
    private static final String ALPHA = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";   // base32 de Crockford (sin I, L, O, U)
    private static final String[] URLS = {
        "https://raw.githubusercontent.com/lordnordic-debug/StradaOS/licencias/l/",
        "https://cdn.jsdelivr.net/gh/lordnordic-debug/StradaOS@licencias/l/"};

    private static String cachedId;
    private static String cachedRecords;
    private static HashSet<String> cachedThemes;

    // ───────────────────────── ID de la pantalla ─────────────────────────

    /** ID de 8 letras de esta pantalla, sin guion. Sale del ANDROID_ID; si la pantalla no tiene uno propio, de uno al azar guardado. */
    static synchronized String id(Context c) {
        if (cachedId != null) return cachedId;
        String seed = null;
        try { seed = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID); } catch (Throwable ignored) { }
        if (seed == null || seed.length() < 8 || "9774d56d682e549c".equalsIgnoreCase(seed) || seed.matches("0+")) {
            SharedPreferences p = c.getSharedPreferences("launcher", 0);
            seed = p.getString("lic_seed", null);
            if (seed == null) {
                byte[] r = new byte[16];
                new SecureRandom().nextBytes(r);
                seed = "r" + hex(r);
                p.edit().putString("lic_seed", seed).commit();
            }
        }
        cachedId = b32(sha("stradaos-id|" + seed), 8);
        return cachedId;
    }

    /** El ID como se muestra: XXXX-XXXX. */
    static String shown(Context c) { String i = id(c); return i.substring(0, 4) + "-" + i.substring(4); }

    // ───────────────────────── temas desbloqueados ─────────────────────────

    static synchronized boolean has(Context c, String theme) {
        String recs = c.getSharedPreferences("launcher", 0).getString("lic_records", "");
        if (cachedThemes == null || !recs.equals(cachedRecords)) {
            HashSet<String> t = new HashSet<String>();
            for (String r : recs.split("\n\n")) {
                List<String> th = valid(c, r);
                if (th != null) t.addAll(th);
            }
            cachedThemes = t;
            cachedRecords = recs;
        }
        return cachedThemes.contains(theme);
    }

    /** Temas de la licencia si la firma es buena y es de esta pantalla; si no, null. */
    static List<String> valid(Context c, String rec) {
        try {
            String id = null, temas = null, fecha = null, firma = null;
            boolean head = false;
            for (String line : rec.replace("\r", "").split("\n")) {
                line = line.trim();
                if (line.equals(HEAD)) head = true;
                else if (line.startsWith("id=")) id = line.substring(3).trim();
                else if (line.startsWith("temas=")) temas = line.substring(6).trim();
                else if (line.startsWith("fecha=")) fecha = line.substring(6).trim();
                else if (line.startsWith("firma=")) firma = line.substring(6).trim();
            }
            if (!head || id == null || temas == null || fecha == null || firma == null) return null;
            if (!id.equals(id(c))) return null;
            PublicKey k = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(PUB, Base64.DEFAULT)));
            Signature sg = Signature.getInstance("SHA256withRSA");
            sg.initVerify(k);
            sg.update((HEAD + "|" + id + "|" + temas + "|" + fecha).getBytes("UTF-8"));
            if (!sg.verify(Base64.decode(firma, Base64.DEFAULT))) return null;
            ArrayList<String> out = new ArrayList<String>();
            for (String t : temas.split(",")) if (t.trim().length() > 0) out.add(t.trim().toLowerCase(Locale.US));
            return out;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Guarda una licencia buena (sin repetirla). Devuelve sus temas o null si no sirve en esta pantalla. */
    static synchronized List<String> add(Context c, String rec) {
        rec = rec.replace("\r", "").trim();
        List<String> th = valid(c, rec);
        if (th == null) return null;
        SharedPreferences p = c.getSharedPreferences("launcher", 0);
        String recs = p.getString("lic_records", "");
        if (!recs.contains(rec)) p.edit().putString("lic_records", recs.length() == 0 ? rec : recs + "\n\n" + rec).commit();
        cachedThemes = null;
        return th;
    }

    // ───────────────────────── código y archivo ─────────────────────────

    /** El código tal como lo escribió la persona → 8 letras (sin STRD ni guiones; O→0, I/L→1), o null si no sirve. */
    static String normCode(String in) {
        if (in == null) return null;
        String s = in.toUpperCase(Locale.US).replaceAll("[^0-9A-Z]", "");
        if (s.startsWith("STRD")) s = s.substring(4);
        s = s.replace('O', '0').replace('I', '1').replace('L', '1');
        if (s.length() != 8) return null;
        for (int i = 0; i < 8; i++) if (ALPHA.indexOf(s.charAt(i)) < 0) return null;
        return s;
    }

    static String fileName(String id, String code) { return hex(sha("strd-lic|" + id + "|" + code)).substring(0, 32) + ".txt"; }

    static final int OK = 0, NOT_FOUND = 1, NO_NET = 2, BAD = 3;
    static List<String> lastThemes;

    /** Baja la licencia del código (en un hilo aparte). */
    static int fetch(Context c, String code) {
        String name = fileName(id(c), code);
        boolean reached = false;
        for (String base : URLS) {
            try {
                HttpURLConnection h = Net.open(base + name + "?t=" + (System.currentTimeMillis() / 60000), 12000);
                int rc = h.getResponseCode();
                reached = true;
                if (rc != 200) { h.disconnect(); continue; }
                String body = new String(Backup.readAll(h.getInputStream()), "UTF-8");
                h.disconnect();
                List<String> th = add(c, body);
                if (th == null) return BAD;
                lastThemes = th;
                return OK;
            } catch (Throwable e) {
                Guard.log("Licencia: " + e);
            }
        }
        return reached ? NOT_FOUND : NO_NET;
    }

    /** Busca licencias en la memoria de la pantalla y en las USB. Devuelve los temas que quedaron desbloqueados. */
    static List<String> importUsb(Context c) {
        ArrayList<File> roots = new ArrayList<File>();
        roots.add(Backup.primary());
        roots.addAll(Backup.removable(c));
        ArrayList<File> dirs = new ArrayList<File>();
        for (File r : roots) {
            dirs.add(r);
            dirs.add(new File(r, "StradaOS"));
            dirs.add(new File(r, "Download"));
        }
        ArrayList<String> got = new ArrayList<String>();
        for (File d : dirs) {
            File[] fs = null;
            try { fs = d.listFiles(); } catch (Throwable ignored) { }
            if (fs == null) continue;
            for (File f : fs) {
                String n = f.getName().toLowerCase(Locale.US);
                boolean lic = n.startsWith("stradaos-licencia") || n.startsWith("stradaos-license") || n.matches("[0-9a-f]{32}\\.txt");
                if (!f.isFile() || !lic || f.length() > 8192) continue;
                try {
                    List<String> th = add(c, new String(Backup.readAll(new FileInputStream(f)), "UTF-8"));
                    if (th != null) for (String t : th) if (!got.contains(t)) got.add(t);
                } catch (Throwable ignored) { }
            }
        }
        return got;
    }

    // ───────────────────────── utilidades ─────────────────────────

    private static byte[] sha(String s) {
        try { return MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")); }
        catch (Throwable e) { return new byte[32]; }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.US, "%02x", x & 0xff));
        return sb.toString();
    }

    /** Los primeros n caracteres en base32 de Crockford. */
    static String b32(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        int buf = 0, bits = 0, i = 0;
        while (sb.length() < n) {
            if (bits < 5) { buf = (buf << 8) | (b[i++] & 0xff); bits += 8; }
            sb.append(ALPHA.charAt((buf >> (bits - 5)) & 31));
            bits -= 5;
        }
        return sb.toString();
    }
}
