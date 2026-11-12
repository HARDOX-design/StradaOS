package com.stradaos.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;

/**
 * Consulta directa a la app Bluetooth de fábrica (com.clw.btmusic / IBTPlaybackService).
 *
 * En estas pantallas el audio A2DP solo sale por los parlantes cuando la pantalla de «Audio Bluetooth»
 * pasa al frente: el sistema cambia la fuente de audio según la app de fuente que se abrió por última vez
 * (probado en 2.9.0: llamar solo a setBTMusicMode() desde el servicio no basta). StradaOS mantiene vivo
 * el servicio para saber cuándo el celular empieza a sonar y avisa a Main, que abre esa pantalla un
 * instante y vuelve a donde estaba el usuario.
 */
public class BtBridge {
    private static final String DESCRIPTOR = "com.clw.btmusic.IBTPlaybackService";
    private static final int T_TOGGLE = 1, T_PREV = 2, T_NEXT = 3, T_IS_PLAYING = 4, T_A2DP = 5,
            T_TMP_LOSE = 6,
            T_TITLE = 10, T_ARTIST = 11, T_ALBUM = 12, T_TOTAL = 13, T_CURRENT = 14, T_REFRESH = 15, T_BT_MODE = 16;
    static final String PKG = "com.clw.btmusic";

    public interface Listener { void onBtState(NowPlaying.Track t); }

    /** Main: el celular empezó a sonar (flanco pausa → sonando). */
    public Runnable onPhoneStarted;
    /** Main: el servicio de fábrica se cerró (suele pasar cuando otra fuente toma el audio). */
    public Runnable onLost;

    private final Context ctx;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private IBinder binder;
    private boolean bound, boundAuto, autoCreate, pendingActivate;
    /** null = aún no se ha visto el estado: el primer dato no cuenta como «empezó a sonar» (abría la app al arrancar). */
    private volatile Boolean wasPlaying;
    private volatile boolean polling;
    private long lastActivate, pauseAutoUntil;
    private final long[] drops = new long[3];
    private int dropIdx;
    /** Últimos eventos, para el diagnóstico. */
    static final java.util.LinkedList<String> LOG = new java.util.LinkedList<String>();
    static volatile long activations;

    static void log(String m) {
        synchronized (LOG) {
            LOG.addLast(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + m);
            while (LOG.size() > 30) LOG.removeFirst();
        }
    }

    static String logText() {
        StringBuilder b = new StringBuilder();
        synchronized (LOG) { for (String l : LOG) b.append(l).append('\n'); }
        return b.length() == 0 ? "(sin eventos)" : b.toString();
    }

    public BtBridge(Context c, Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
    }

    private final ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
            binder = b;
            log("conectado a la app Bluetooth");
            if (pendingActivate) { pendingActivate = false; activateAudio(); }
            refresh();
        }
        public void onServiceDisconnected(ComponentName n) {
            binder = null;
            wasPlaying = null;
            log("la app Bluetooth se cerró");
            if (onLost != null) ui.post(onLost);
            if (!boundAuto) return;
            // si el servicio se cierra una y otra vez, deja de mantenerlo vivo un rato
            long now = SystemClock.elapsedRealtime();
            drops[dropIdx++ % drops.length] = now;
            boolean loop = true;
            for (long d : drops) if (d == 0 || now - d > 90000) loop = false;
            if (loop) {
                pauseAutoUntil = now + 15 * 60000;
                log("se reinicia muy seguido: pausa de 15 min");
                ui.post(new Runnable() { public void run() { disconnect(); } });
            }
        }
    };

    public static boolean available(Context c) {
        try { c.getPackageManager().getPackageInfo(PKG, 0); return true; } catch (Throwable e) { return false; }
    }

    /**
     * true: mantiene vivo el servicio Bluetooth de fábrica (lo arranca si hace falta).
     * false: solo se conecta si ya está corriendo, sin cambiar nada.
     */
    public void setAutoCreate(boolean on) {
        autoCreate = on;
    }

    private boolean wantAuto() {
        return autoCreate && SystemClock.elapsedRealtime() > pauseAutoUntil;
    }

    /** Intenta conectarse. */
    public void connect() {
        boolean auto = wantAuto();
        if (bound && auto == boundAuto) return;
        if (bound) disconnect();
        try {
            Intent i = new Intent();
            i.setClassName(PKG, "com.clw.btmusic.BTPlaybackService");
            bound = ctx.bindService(i, conn, auto ? Context.BIND_AUTO_CREATE : 0);
            boundAuto = bound && auto;
        } catch (Throwable e) {
            bound = false;
        }
    }

    public void disconnect() {
        if (!bound) return;
        try { ctx.unbindService(conn); } catch (Throwable ignored) { }
        bound = false;
        boundAuto = false;
        binder = null;
        wasPlaying = null;
    }

    /**
     * Pide a la app de fábrica el modo de audio Bluetooth (lo mismo que hace su pantalla al abrirse).
     * Si el celular no está sonando, la app además le envía «play».
     */
    public void activateAudio() {
        final IBinder b = binder;
        if (b == null) {
            if (available(ctx)) {
                pendingActivate = true;
                try {
                    Intent i = new Intent();
                    i.setClassName(PKG, "com.clw.btmusic.BTPlaybackService");
                    if (!bound || !boundAuto) {
                        if (bound) disconnect();
                        bound = ctx.bindService(i, conn, Context.BIND_AUTO_CREATE);
                        boundAuto = bound;
                    }
                } catch (Throwable ignored) { }
            }
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!bool(b, T_A2DP)) { log("activar audio: el celular no está conectado por Bluetooth"); return; }
                    // con el celular en pausa, setBTMusicMode() no envía play (rompía el botón play en 2.9.0)
                    if (!bool(b, T_IS_PLAYING)) { log("activar audio: el celular está en pausa"); return; }
                    call(b, T_BT_MODE);
                    lastActivate = SystemClock.elapsedRealtime();
                    activations++;
                    log("modo de audio Bluetooth solicitado");
                } catch (Throwable e) { log("activar audio falló: " + e); }
            }
        }).start();
    }

    /**
     * Revisión rápida (cada 2 s): si el celular acaba de empezar a sonar y la app de fábrica no tiene
     * el audio, se lo pide. Solo actúa en el cambio de «pausado» a «sonando», para no pelear con la
     * radio u otra fuente que el usuario elija después.
     */
    public void poll(final boolean auto) {
        final IBinder b = binder;
        if (b == null || polling) return;
        polling = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    boolean on = bool(b, T_A2DP);
                    a2dp = on;
                    if (!on) { wasPlaying = null; return; }
                    boolean playing = bool(b, T_IS_PLAYING);
                    boolean rising = playing && Boolean.FALSE.equals(wasPlaying);
                    wasPlaying = playing;
                    if (!auto || !rising) return;
                    // isAudioTmpLose() es true justo cuando la radio tiene el sonido: no se usa para descartar (bug de 2.9.0)
                    if (SystemClock.elapsedRealtime() - lastActivate < 8000) return;
                    lastActivate = SystemClock.elapsedRealtime();
                    log("el celular empezó a sonar");
                    // no se llama a setBTMusicMode() aquí: en 2.9.0 no pasaba el sonido y dañaba el play/pausa
                    if (onPhoneStarted != null) ui.post(onPhoneStarted);
                } catch (Throwable e) {
                    log("revisión falló: " + e);
                } finally {
                    polling = false;
                }
            }
        }).start();
    }

    public boolean isConnected() { return binder != null && binder.isBinderAlive(); }

    /** Último estado leído del celular conectado por Bluetooth (null = todavía no se sabe). Para el ícono de arriba. */
    private volatile Boolean a2dp;
    public Boolean a2dp() { return isConnected() ? a2dp : null; }

    /** Lee el estado actual en un hilo aparte y lo entrega en la UI. */
    public void refresh() {
        final IBinder b = binder;
        if (b == null) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    final NowPlaying.Track t = new NowPlaying.Track();
                    t.origin = "bt";
                    t.source = "Bluetooth";
                    t.polled = true;
                    boolean on = bool(b, T_A2DP);
                    a2dp = on;
                    if (!on) {
                        t.clear = true;
                    } else {
                        call(b, T_REFRESH);                 // pide a la app que reenvíe la info
                        t.title = str(b, T_TITLE);
                        t.artist = str(b, T_ARTIST);
                        t.album = str(b, T_ALBUM);
                        long tot = lng(b, T_TOTAL), cur = lng(b, T_CURRENT);
                        if (tot > 0 && tot < 36000) { tot *= 1000; cur *= 1000; } // venía en segundos
                        if (tot > 0) t.durationMs = tot;
                        if (cur >= 0 && tot > 0) t.positionMs = cur;
                        t.playing = bool(b, T_IS_PLAYING);
                        if (t.title == null && t.artist == null && !t.playing) return;
                    }
                    ui.post(new Runnable() { public void run() { listener.onBtState(t); } });
                } catch (Throwable ignored) { }
            }
        }).start();
    }

    /** Controles directos. Devuelve false si no hay conexión. */
    public boolean command(int which) {
        final IBinder b = binder;
        if (b == null) return false;
        final int code = which == 1 ? T_NEXT : which == -1 ? T_PREV : T_TOGGLE;
        new Thread(new Runnable() { public void run() { try { call(b, code); } catch (Throwable ignored) { } } }).start();
        return true;
    }

    private static Parcel tx(IBinder b, int code) throws Exception {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(DESCRIPTOR);
            b.transact(code, d, r, 0);
            r.readException();
            return r;
        } finally {
            d.recycle();
        }
    }

    private static void call(IBinder b, int code) throws Exception { tx(b, code).recycle(); }

    private static String str(IBinder b, int code) throws Exception {
        Parcel r = tx(b, code);
        try {
            String s = r.readString();
            return s == null || s.trim().length() == 0 || "Not Provided".equalsIgnoreCase(s.trim()) ? null : s.trim();
        } finally { r.recycle(); }
    }

    private static long lng(IBinder b, int code) throws Exception {
        Parcel r = tx(b, code);
        try { return r.readLong(); } finally { r.recycle(); }
    }

    private static boolean bool(IBinder b, int code) throws Exception {
        Parcel r = tx(b, code);
        try { return r.readInt() != 0; } finally { r.recycle(); }
    }
}
