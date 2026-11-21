package com.stradaos.launcher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Necesario para leer las sesiones multimedia en Android 5+ (el usuario lo activa en "Acceso a notificaciones"). */
public class MediaListener extends NotificationListenerService {
    static boolean connected;

    static boolean isMedia(StatusBarNotification n) {
        try { return n.getNotification().extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION); }
        catch (Throwable e) { return false; }
    }

    /** 2.30 (#50): anota las apps de Android Auto / CarPlay con notificación (ver Projection). */
    private static void note(StatusBarNotification n) {
        String p = n.getPackageName();
        if (!Projection.isProjection(p)) return;
        Projection.any.add(p);
        if (isMedia(n)) Projection.media.add(p);
    }

    @Override public void onListenerConnected() {
        connected = true;
        MediaWatcher.kick(this);
        try {
            Projection.clear();
            StatusBarNotification[] a = getActiveNotifications();
            if (a != null) for (StatusBarNotification n : a) note(n);
        } catch (Throwable ignored) { }
    }
    @Override public void onListenerDisconnected() { connected = false; Projection.clear(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try { note(sbn); } catch (Throwable ignored) { }
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        try {
            String p = sbn.getPackageName();
            if (!Projection.isProjection(p)) return;
            StatusBarNotification[] a = getActiveNotifications();
            boolean still = false, media = false;
            if (a != null) for (StatusBarNotification n : a) if (p.equals(n.getPackageName())) { still = true; if (isMedia(n)) media = true; }
            if (!still) Projection.any.remove(p);
            if (!media) Projection.media.remove(p);
        } catch (Throwable e) {
            Projection.any.remove(sbn.getPackageName());
            Projection.media.remove(sbn.getPackageName());
        }
    }
}
