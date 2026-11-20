package com.stradaos.launcher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Guía del escáner OBD-II (2.26): textos cortos dentro del launcher, sin internet, en el idioma elegido.
 *
 * Cada idioma tiene su archivo en assets/guide/obd-LANG.txt (si falta, se usa el inglés y después el español).
 * Formato, una línea por elemento:
 *   # comentario
 *   == id | Título | Resumen corto (se ve en el índice)
 *   ### Subtítulo
 *   - Viñeta
 *   >> acción | Texto del botón      (acciones: obd_conn, obd_alerts, obd_codes, obd_live, obd_ai, demo, panel)
 *   Cualquier otra línea es un párrafo.
 * Los textos ya vienen traducidos: no pasan por I18n.t.
 */
class ObdGuide {
    static final int PARA = 0, BULLET = 1, SUBHEAD = 2, ACTION = 3;

    static class Item {
        int type;
        String text, action;
    }

    static class Section {
        String id, title, sub;
        final List<Item> items = new ArrayList<Item>();
    }

    private static String cachedLang;
    private static List<Section> cached;

    static synchronized List<Section> load(Context c) {
        String lang = I18n.lang;
        if (cached != null && lang.equals(cachedLang)) return cached;
        List<Section> out = read(c, lang);
        if (out.isEmpty() && !"en".equals(lang)) out = read(c, "en");
        if (out.isEmpty()) out = read(c, "es");
        cached = out;
        cachedLang = lang;
        return out;
    }

    static Section get(Context c, String id) {
        for (Section s : load(c)) if (s.id.equals(id)) return s;
        return null;
    }

    /** La sección que sigue (null si es la última). */
    static Section next(Context c, String id) {
        List<Section> all = load(c);
        for (int i = 0; i < all.size() - 1; i++) if (all.get(i).id.equals(id)) return all.get(i + 1);
        return null;
    }

    private static List<Section> read(Context c, String lang) {
        List<Section> out = new ArrayList<Section>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("guide/obd-" + lang + ".txt"), "UTF-8"));
            Section cur = null;
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#") && !line.startsWith("###")) continue;
                if (line.startsWith("==")) {
                    String[] p = line.substring(2).split("\\|", 3);
                    cur = new Section();
                    cur.id = p[0].trim();
                    cur.title = p.length > 1 ? p[1].trim() : cur.id;
                    cur.sub = p.length > 2 ? p[2].trim() : "";
                    out.add(cur);
                    continue;
                }
                if (cur == null) continue;
                Item it = new Item();
                if (line.startsWith("###")) { it.type = SUBHEAD; it.text = line.substring(3).trim(); }
                else if (line.startsWith(">>")) {
                    String[] p = line.substring(2).split("\\|", 2);
                    it.type = ACTION;
                    it.action = p[0].trim();
                    it.text = p.length > 1 ? p[1].trim() : it.action;
                } else if (line.startsWith("- ")) { it.type = BULLET; it.text = line.substring(2).trim(); }
                else { it.type = PARA; it.text = line; }
                it.text = it.text.replace("\\n", "\n");
                cur.items.add(it);
            }
            r.close();
        } catch (Throwable e) {
            if (!"en".equals(lang)) Guard.log("Guía OBD sin el idioma " + lang + ": " + e);
        }
        return out;
    }
}
