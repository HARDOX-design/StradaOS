package com.stradaos.launcher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Necesario para leer las sesiones multimedia en Android 5+ (el usuario lo activa en "Acceso a notificaciones"). */
public class MediaListener extends NotificationListenerService {
    static boolean connected;
    /**
     * 2.30 (#50): apps de Android Auto / CarPlay con una notificación activa (ZLink, AutoKit, Open Headunit…).
     * Va al diagnóstico del reproductor; ▶ usa solo las que tienen notificación multimedia (projectionMedia).
     */
    static final Set<String> projection = Collections.synchronizedSet(new HashSet<String>());
    /** Las mismas, pero solo con notificación multimedia (están reproduciendo algo del celular). Es la que usa ▶. */
    static final Set<String> projectionMedia = Collections.synchronizedSet(new HashSet<String>());

    static boolean isMedia(StatusBarNotification n) {
        try { return n.getNotification().extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION); }
        catch (Throwable e) { return false; }
    }

    private static void note(StatusBarNotification n) {
        String p = n.getPackageName();
        if (!isProjection(p)) return;
        projection.add(p);
        if (isMedia(n)) projectionMedia.add(p);
    }

    static boolean isProjection(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(java.util.Locale.US);
        if (p.equals(Main.PKG_HUR) || p.equals("gb.xxy.hr")) return true;
        for (String k : new String[]{"zlink", "carlink", "autokit", "carplay", "phonemirror", "speedplay", "tlink.", "androidauto", "android.projection"})
            if (p.contains(k)) return true;
        return false;
    }

    @Override public void onListenerConnected() {
        connected = true;
        MediaWatcher.kick(this);
        try {
            projection.clear();
            projectionMedia.clear();
            StatusBarNotification[] a = getActiveNotifications();
            if (a != null) for (StatusBarNotification n : a) note(n);
        } catch (Throwable ignored) { }
    }
    @Override public void onListenerDisconnected() { connected = false; projection.clear(); projectionMedia.clear(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try { note(sbn); } catch (Throwable ignored) { }
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        try {
            String p = sbn.getPackageName();
            if (!isProjection(p)) return;
            StatusBarNotification[] a = getActiveNotifications();
            boolean still = false, media = false;
            if (a != null) for (StatusBarNotification n : a) if (p.equals(n.getPackageName())) { still = true; if (isMedia(n)) media = true; }
            if (!still) projection.remove(p);
            if (!media) projectionMedia.remove(p);
        } catch (Throwable e) {
            projection.remove(sbn.getPackageName());
            projectionMedia.remove(sbn.getPackageName());
        }
    }
}
