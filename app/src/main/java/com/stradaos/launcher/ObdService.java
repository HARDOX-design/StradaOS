package com.stradaos.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Mantiene vivo el monitor OBD ({@link Obd}) mientras se usan otras apps: servicio en primer plano con una
 * notificación que muestra temperatura, voltaje y RPM. Se apaga desde la pantalla del vehículo.
 */
public class ObdService extends Service implements Obd.Listener {
    private static final int ID = 7311;
    private static final String CHANNEL = "obd";
    private long lastNotif;

    static void start(Context c) {
        Intent i = new Intent(c, ObdService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, ObdService.class));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        I18n.init(this, Obd.prefs(this));          // por si Android reinició el servicio sin abrir StradaOS
        startForeground(ID, build());
        Obd.addListener(this);
        Obd.start(this);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        Obd.removeListener(this);
        Obd.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onObd() {
        long now = System.currentTimeMillis();
        if (now - lastNotif < 3000) return;      // la notificación no necesita cada lectura
        lastNotif = now;
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(ID, build()); } catch (Throwable ignored) { }
    }

    @SuppressWarnings("deprecation")
    private Notification build() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, I18n.t("Monitor del vehículo"), NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Intent open = new Intent(this, ObdActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        b.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(I18n.t("Monitor del vehículo"))
            .setContentText(Obd.summary())
            .setContentIntent(pi)
            .setOngoing(true);
        return Build.VERSION.SDK_INT >= 16 ? b.build() : b.getNotification();
    }
}
