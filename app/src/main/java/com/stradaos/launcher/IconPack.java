package com.stradaos.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Packs de íconos instalados (2.19, Reinaldo, MIGSGx #12): el formato estándar de ADW/Nova/Apex/GO, un archivo
 * «appfilter.xml» que dice qué dibujo usar para cada app. Solo se cambian las apps que el pack conoce; las demás
 * quedan con su ícono de siempre. Funciona desde Android 4.1 (sin APIs nuevas).
 */
public class IconPack {

    static final String[] ACTIONS = {
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME",
    };

    private static IconPack cached;

    final String pkg;
    private final Resources res;
    private final Map<String, String> byComponent = new HashMap<String, String>();
    private final Map<String, String> byPackage = new HashMap<String, String>();
    private final Map<String, Drawable> cache = new HashMap<String, Drawable>();

    private IconPack(String pkg, Resources res) { this.pkg = pkg; this.res = res; }

    /** Packs instalados: {paquete, nombre}. */
    public static List<String[]> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        List<String[]> out = new ArrayList<String[]>();
        Set<String> seen = new HashSet<String>();
        for (String a : ACTIONS) {
            List<ResolveInfo> l;
            try { l = pm.queryIntentActivities(new Intent(a), 0); } catch (Throwable e) { continue; }
            if (l == null) continue;
            for (ResolveInfo r : l) {
                String p = r.activityInfo.packageName;
                if (!seen.add(p)) continue;
                String label;
                try { label = String.valueOf(r.activityInfo.applicationInfo.loadLabel(pm)); } catch (Throwable e) { label = p; }
                out.add(new String[]{p, label});
            }
        }
        return out;
    }

    /** El pack elegido (se carga una vez y queda en memoria); null si no hay o no se pudo leer. */
    public static IconPack get(Context c, String pkg) {
        if (pkg == null || pkg.length() == 0) return null;
        if (cached != null && cached.pkg.equals(pkg)) return cached;
        try {
            IconPack p = new IconPack(pkg, c.getPackageManager().getResourcesForApplication(pkg));
            p.parse(c);
            if (p.byComponent.isEmpty()) return null;
            cached = p;
            return p;
        } catch (Throwable e) {
            Guard.log("Pack de íconos " + pkg + ": " + e);
            return null;
        }
    }

    public int size() { return byComponent.size(); }

    private void parse(Context c) throws Exception {
        XmlPullParser x = null;
        InputStream in = null;
        int id = res.getIdentifier("appfilter", "xml", pkg);
        if (id != 0) x = res.getXml(id);
        else {
            try {
                in = res.getAssets().open("appfilter.xml");
                XmlPullParserFactory f = XmlPullParserFactory.newInstance();
                x = f.newPullParser();
                x.setInput(in, "UTF-8");
            } catch (Throwable e) { x = null; }
        }
        if (x == null) return;
        try {
            for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = x.next()) {
                if (ev != XmlPullParser.START_TAG || !"item".equals(x.getName())) continue;
                String comp = x.getAttributeValue(null, "component"), draw = x.getAttributeValue(null, "drawable");
                if (comp == null || draw == null || draw.length() == 0) continue;
                // «ComponentInfo{paquete/Actividad}»
                int a = comp.indexOf('{'), b = comp.lastIndexOf('}');
                if (a < 0 || b <= a) continue;
                String flat = comp.substring(a + 1, b).trim();
                int slash = flat.indexOf('/');
                if (slash <= 0) continue;
                String p = flat.substring(0, slash), cls = flat.substring(slash + 1);
                if (cls.startsWith(".")) cls = p + cls;
                byComponent.put(p + "/" + cls, draw);
                if (!byPackage.containsKey(p)) byPackage.put(p, draw);
            }
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    /** Dibujo del pack para esa actividad (o, si no está exacta, para su app); null si el pack no la tiene. */
    public Drawable icon(ComponentName cn) {
        if (cn == null) return null;
        String cls = cn.getClassName();
        if (cls.startsWith(".")) cls = cn.getPackageName() + cls;
        String name = byComponent.get(cn.getPackageName() + "/" + cls);
        if (name == null) name = byPackage.get(cn.getPackageName());
        if (name == null) return null;
        if (cache.containsKey(name)) return cache.get(name);
        Drawable d = null;
        try {
            int id = res.getIdentifier(name, "drawable", pkg);
            if (id == 0) id = res.getIdentifier(name, "mipmap", pkg);
            if (id != 0) d = res.getDrawable(id);
        } catch (Throwable e) { d = null; }
        cache.put(name, d);
        return d;
    }
}
