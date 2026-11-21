package com.stradaos.launcher;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 2.30 (#50): apps de Android Auto / CarPlay con una notificación activa (ZLink, AutoKit, Open Headunit…), que llena
 * MediaListener. Va en una clase aparte y simple porque MediaListener extiende NotificationListenerService, que no existe
 * en Android 4.1 y 4.2: tocarla desde ahí cierra la app.
 */
final class Projection {
    private Projection() { }

    /** Todas las apps de proyección con notificación (para el diagnóstico). */
    static final Set<String> any = Collections.synchronizedSet(new HashSet<String>());
    /** Las mismas, solo con notificación multimedia (están reproduciendo algo del celular). Es la que usa ▶. */
    static final Set<String> media = Collections.synchronizedSet(new HashSet<String>());

    static boolean isProjection(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(java.util.Locale.US);
        if (p.equals(Main.PKG_HUR) || p.equals("gb.xxy.hr")) return true;
        for (String k : new String[]{"zlink", "carlink", "autokit", "carplay", "phonemirror", "speedplay", "tlink.", "androidauto", "android.projection"})
            if (p.contains(k)) return true;
        return false;
    }

    static void clear() { any.clear(); media.clear(); }

    static String describe(Set<String> s) {
        synchronized (s) { return s.isEmpty() ? "ninguna" : s.toString(); }
    }
}
