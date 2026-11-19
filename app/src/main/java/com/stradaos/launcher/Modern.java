package com.stradaos.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

/** Funciones de Android 6+ (solo se llaman cuando el equipo las tiene). */
public class Modern {

    /** Pide los permisos que el launcher necesita (Android 6+). Devuelve true si ya estaban todos. */
    public static boolean ensurePermissions(Activity a, int req) {
        java.util.ArrayList<String> need = new java.util.ArrayList<String>();
        String[] all = android.os.Build.VERSION.SDK_INT >= 33
                ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION, "android.permission.READ_MEDIA_IMAGES"}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE};
        for (String p : all) if (a.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (need.isEmpty()) return true;
        a.requestPermissions(need.toArray(new String[0]), req);
        return false;
    }

    /** ¿Están dados estos permisos? Antes de Android 6 se dan al instalar: siempre true. */
    public static boolean hasPermissions(Activity a, String... perms) {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        for (String p : perms) if (a.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    public static void requestPermissions(Activity a, int req, String... perms) {
        if (android.os.Build.VERSION.SDK_INT < 23) return;
        a.requestPermissions(perms, req);
    }

    /** Android 8+: permiso por app para instalar APKs. */
    public static boolean canInstall(Activity a) {
        if (android.os.Build.VERSION.SDK_INT < 26) return true;
        if (a.getPackageManager().canRequestPackageInstalls()) return true;
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) { }
        return false;
    }

    /** Android 7+: el permiso está dado pero el sistema no conectó el lector (pasa en algunas pantallas): pedirlo otra vez. */
    public static void rebindListener(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 24) return;
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                new android.content.ComponentName(c, MediaListener.class));
        } catch (Throwable ignored) { }
    }

    public static boolean notificationAccess(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    /** Android 10+: ventana del sistema «¿Usar StradaOS como app de inicio?». Devuelve false si no se pudo pedir. */
    public static boolean requestHomeRole(Activity a, int req) {
        if (android.os.Build.VERSION.SDK_INT < 29) return false;
        android.app.role.RoleManager rm = a.getSystemService(android.app.role.RoleManager.class);
        if (rm == null || !rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return false;
        a.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), req);
        return true;
    }
}
