package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Idiomas de StradaOS (español, inglés, portugués, italiano, alemán, malayo, árabe, turco, francés, chino, húngaro, polaco). El código se escribe en español y cada texto se traduce al
 * mostrarse (tv(), toast(), ventanas TB, etc.) buscándolo en assets/i18n/LANG.txt.
 *
 * Formato del archivo: una línea por texto, «español<TAB>traducción». «\n» representa un salto de línea.
 * Un texto con {} es un patrón: «Guardado: {}<TAB>Saved: {}». Lo que va en {} también se traduce si está en la
 * tabla. En la traducción, {1}, {2}… permiten cambiar el orden. Si un texto no está en la tabla queda en español.
 */
public class I18n {
    static final String[] CODES = {"es", "en", "pt", "it", "de", "ms", "ar", "tr", "fr", "zh", "hu", "pl"};
    static final String[] NAMES = {"Español", "English", "Português", "Italiano", "Deutsch", "Melayu", "العربية", "Türkçe", "Français", "中文", "Magyar", "Polski"};

    static String lang = "es";
    static Locale loc = new Locale("es", "CO");
    /** Locale de los números: en árabe se usan dígitos latinos (0-9), no ٠-٩. */
    static Locale num = loc;
    private static final HashMap<String, String> map = new HashMap<String, String>();
    private static final List<Pat> pats = new ArrayList<Pat>();
    private static final HashMap<String, String> cache = new HashMap<String, String>();
    /** Solo en StradaOS Prueba: anota en el logcat (StradaI18n) cada texto que quedó sin traducir. */
    private static boolean audit;
    private static final java.util.HashSet<String> missed = new java.util.HashSet<String>();
    private static final java.util.regex.Pattern WORD = java.util.regex.Pattern.compile("[a-záéíóúñ]{3,}");

    private static class Pat {
        Pattern re;
        String out;
        int lit;   // letras fijas: se prueban primero los patrones más específicos
    }

    /** Idioma guardado («auto», «es», «en», «pt», «it», «de», «ms», «ar», «tr», «fr», «zh», «hu»). Instalaciones anteriores a los idiomas siguen en español. */
    static String pref(SharedPreferences p) {
        String v = p.getString("lang", null);
        if (v == null) {
            v = p.getBoolean("setup_done", false) ? "es" : "auto";
            p.edit().putString("lang", v).apply();
        }
        return v;
    }

    /** Idioma del sistema si es uno de los disponibles; si no, inglés. */
    static String system() {
        String l = Locale.getDefault().getLanguage();
        for (String c : CODES) if (c.equals(l)) return l;
        return "en";
    }

    static void init(Context c, SharedPreferences p) {
        String v = pref(p);
        set(c, "auto".equals(v) ? system() : v);
    }

    static synchronized void set(Context c, String code) {
        if (code == null) code = "es";
        lang = code;
        audit = c != null && c.getPackageName().endsWith(".debug");
        missed.clear();
        loc = "en".equals(code) ? Locale.US : "pt".equals(code) ? new Locale("pt", "BR")
            : "it".equals(code) ? Locale.ITALY : "de".equals(code) ? Locale.GERMANY
            : "ms".equals(code) ? new Locale("ms", "MY") : "ar".equals(code) ? new Locale("ar")
            : "tr".equals(code) ? new Locale("tr", "TR") : "fr".equals(code) ? Locale.FRANCE
            : "zh".equals(code) ? Locale.SIMPLIFIED_CHINESE : "hu".equals(code) ? new Locale("hu", "HU")
            : "pl".equals(code) ? new Locale("pl", "PL")
            : new Locale("es", "CO");
        num = "ar".equals(code) ? Locale.US : loc;
        map.clear();
        pats.clear();
        cache.clear();
        if ("es".equals(code)) return;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("i18n/" + code + ".txt"), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.length() == 0 || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String k = line.substring(0, tab).replace("\\n", "\n");
                String t = line.substring(tab + 1).replace("\\n", "\n");
                if (k.contains("{}")) addPattern(k, t);
                else map.put(k, t);
            }
            r.close();
        } catch (Throwable e) {
            Guard.log("No se pudo cargar el idioma " + code + ": " + e);
        }
        java.util.Collections.sort(pats, new java.util.Comparator<Pat>() {
            public int compare(Pat a, Pat b) { return b.lit - a.lit; }
        });
    }

    private static void addPattern(String k, String t) {
        StringBuilder re = new StringBuilder("^");
        String[] parts = k.split("\\{\\}", -1);
        int lit = 0;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) re.append("(.+?)");
            re.append(Pattern.quote(parts[i]));
            lit += parts[i].length();
        }
        re.append("$");
        Pat p = new Pat();
        p.re = Pattern.compile(re.toString(), Pattern.DOTALL);
        p.out = t;
        p.lit = lit;
        pats.add(p);
    }

    static String t(CharSequence cs) {
        if (cs == null) return null;
        String s = cs.toString();
        if ("es".equals(lang) || s.length() == 0) return s;
        return tr(s, 0);
    }

    /** ¿Hay traducción exacta de este texto en el idioma de ahora? (en español, siempre) */
    static synchronized boolean has(String s) {
        if ("es".equals(lang) || s != null && map.containsKey(s)) return true;
        return s != null && !tr(s, 0).equals(s);              // con patrón («… del cilindro {}.»)
    }

    /** Texto de varias líneas (diagnósticos): se traduce cada línea por separado. */
    static String lines(String s) {
        if (s == null || "es".equals(lang)) return s;
        String[] ls = s.split("\n", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ls.length; i++) {
            if (i > 0) b.append('\n');
            String l = ls[i];
            // eventos con la hora adelante («12:03:11 el celular empezó a sonar»): la hora queda y se traduce el resto
            boolean timed = l.length() > 9 && l.charAt(2) == ':' && l.charAt(5) == ':' && l.charAt(8) == ' ' && Character.isDigit(l.charAt(0));
            b.append(l.length() == 0 ? "" : timed ? l.substring(0, 9) + t(l.substring(9)) : t(l));
        }
        return b.toString();
    }

    /** Texto con %s, %d… (formato de Java) traducido y luego completado. */
    static String f(String es, Object... args) {
        return String.format(num, t(es), args);
    }

    private static synchronized String tr(String s, int depth) {
        String hit = map.get(s);
        if (hit != null) return hit;
        if (depth > 3 || s.length() > 600) return s;
        String c = cache.get(s);
        if (c != null) return c;
        String out = s;
        // espacios alrededor (p. ej. «  Cerrar ✕»): se traducen sin ellos
        String trim = s.trim();
        if (trim.length() != s.length() && trim.length() > 0) {
            String in = tr(trim, depth + 1);
            if (!in.equals(trim)) out = s.replace(trim, in);
        }
        if (out == s) {
            for (Pat p : pats) {
                Matcher m = p.re.matcher(s);
                if (!m.matches()) continue;
                String r = p.out;
                int seq = 0;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < r.length(); i++) {
                    char ch = r.charAt(i);
                    if (ch == '{' && i + 1 < r.length()) {
                        int close = r.indexOf('}', i);
                        if (close > i) {
                            String inner = r.substring(i + 1, close);
                            int g = -1;
                            if (inner.length() == 0) g = ++seq;
                            else try { g = Integer.parseInt(inner); } catch (NumberFormatException ignored) { }
                            if (g >= 1 && g <= m.groupCount()) {
                                sb.append(tr(m.group(g), depth + 1));
                                i = close;
                                continue;
                            }
                        }
                    }
                    sb.append(ch);
                }
                out = sb.toString();
                break;
            }
        }
        if (audit && out.equals(s) && missed.size() < 3000 && WORD.matcher(s).find() && missed.add(s))
            android.util.Log.w("StradaI18n", lang + "\t" + s.replace("\n", "\\n"));
        if (cache.size() > 3000) cache.clear();
        cache.put(s, out);
        return out;
    }

    /** Números enteros con el locale de los números (dígitos latinos en árabe). */
    static String integer(long v) {
        return java.text.NumberFormat.getIntegerInstance(num).format(v);
    }

    /** Fecha/hora con nombres del idioma pero dígitos latinos (en árabe, Android usaría ٠-٩). */
    static java.text.DateFormat latin(java.text.DateFormat df) {
        if (num != loc) {
            java.text.NumberFormat nf = java.text.NumberFormat.getIntegerInstance(num);
            nf.setGroupingUsed(false);
            df.setNumberFormat(nf);
        }
        return df;
    }

    static java.text.SimpleDateFormat sdf(String pattern) {
        return (java.text.SimpleDateFormat) latin(new java.text.SimpleDateFormat(pattern, loc));
    }

    /** Patrón de la fecha de la pantalla de inicio. */
    static String datePattern() {
        if ("en".equals(lang)) return "EEEE, MMMM d";
        if ("it".equals(lang) || "fr".equals(lang)) return "EEEE d MMMM";
        if ("de".equals(lang)) return "EEEE, d. MMMM";
        if ("ms".equals(lang) || "tr".equals(lang)) return "EEEE, d MMMM";
        if ("ar".equals(lang)) return "EEEE، d MMMM";
        if ("zh".equals(lang)) return "EEEE, M月d日";
        if ("hu".equals(lang)) return "MMMM d., EEEE";
        if ("pl".equals(lang)) return "EEEE, d MMMM";
        return "EEEE, d 'de' MMMM";
    }
}
