package com.stradaos.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Pantallas FYT (UIS7862, UIS8581, SC9863…; app de sistema {@code com.syu.ms}).
 *
 * <ul>
 * <li><b>Luces del carro:</b> al encender las luces (cable ILL) la FYT pone {@code Settings.System car_mode = 1}
 *     (y baja el brillo); al apagarlas vuelve a 0. Se lee sin permisos.</li>
 * <li><b>Datos del carro:</b> {@code com.syu.ms} tiene un servicio Binder sin permisos (acción {@code com.syu.ms.toolkit},
 *     AIDL {@code com.syu.ipc.IRemoteToolkit / IRemoteModule / IModuleCallback}). Módulo 0 (MAIN):
 *     código 114 = tensión de la batería medida por el MCU de la pantalla, en 0,1 V (no necesita caja CAN);
 *     código 40 = temperatura exterior {@code 1000 + 10 × °C} (solo si la caja CAN la envía).
 *     Módulo 7 (CANBUS), código 1000 = tipo de caja CAN (16 bits bajos; 0 = sin caja).</li>
 * </ul>
 *
 * Probado en una EKIY T7 (UIS7862S, Android 10, ro.fyt.platform=6315) con caja XP Peugeot/Citroën (tipo 281):
 * 13,6–14,1 V con el motor encendido y 17,0 °C, iguales al tablero y a DuDu.
 * Sin lambdas ni APIs nuevas: compila y no hace nada en Android 4.1 ni en pantallas que no son FYT.
 */
final class Fyt implements ServiceConnection {

    static final String PKG = "com.syu.ms";
    static final String CAR_MODE = "car_mode";

    private static final String D_TOOLKIT = "com.syu.ipc.IRemoteToolkit";
    private static final String D_MODULE = "com.syu.ipc.IRemoteModule";
    private static final String D_CALLBACK = "com.syu.ipc.IModuleCallback";
    private static final int M_MAIN = 0, M_CANBUS = 7;
    private static final int C_TEMP = 40, C_VOLT = 114, C_CANTYPE = 1000;

    // ───────────────────────── detección ─────────────────────────

    private static Boolean detected;
    private static String platform = "";

    /** ¿Es una pantalla FYT? (app de sistema com.syu.ms o la propiedad ro.fyt.platform). Se calcula una vez. */
    static boolean detect(Context c) {
        if (detected != null) return detected;
        boolean d = false;
        try { c.getPackageManager().getPackageInfo(PKG, 0); d = true; } catch (Throwable ignored) { }
        platform = prop("ro.fyt.platform");
        if (platform.length() == 0) platform = prop("sys.fyt.platform");
        if (platform.length() > 0) d = true;
        detected = d;
        return d;
    }

    /** Plataforma FYT (p. ej. «6315» = UIS7862) o "" si no se sabe. */
    static String platform() { return platform; }

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v == null ? "" : v.toString().trim();
        } catch (Throwable e) { return ""; }
    }

    /** Luces del carro: 1 encendidas, 0 apagadas, -1 si la pantalla no lo informa. */
    static int lights(Context c) {
        try {
            return android.provider.Settings.System.getInt(c.getContentResolver(), CAR_MODE, -1);
        } catch (Throwable e) { return -1; }
    }

    // ───────────────────────── datos del carro (com.syu.ms) ─────────────────────────

    interface Listener { void onCarData(); }   // siempre en el hilo principal

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Callback cbMain = new Callback(M_MAIN), cbCan = new Callback(M_CANBUS);
    private IBinder toolkit, modMain, modCan;
    private boolean bound, closed;

    /** Tensión de la batería en décimas de voltio, o -1 si no llegó. */
    volatile int voltDeci = -1;
    /** Temperatura exterior en décimas de °C, o Integer.MIN_VALUE si no llegó (sin caja CAN no llega). */
    volatile int tempDeci = Integer.MIN_VALUE;
    /** Tipo de caja CAN (0 = sin caja, -1 = no se sabe). */
    volatile int canType = -1;
    volatile String state = "sin conectar";

    Fyt(Context c, Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
    }

    boolean hasVolt() { return voltDeci > 0; }
    boolean hasTemp() { return tempDeci != Integer.MIN_VALUE; }

    private final IBinder.DeathRecipient death = new IBinder.DeathRecipient() {
        public void binderDied() {
            state = "com.syu.ms se reinició";
            main.postDelayed(new Runnable() { public void run() { if (!closed) { stop(); start(); } } }, 3000);
        }
    };

    void start() {
        closed = false;
        boolean ok = false;
        try {
            Intent i = new Intent("com.syu.ms.toolkit");
            i.setPackage(PKG);
            ok = ctx.bindService(i, this, Context.BIND_AUTO_CREATE);
        } catch (Throwable ignored) { }
        if (!ok) {
            try { ctx.unbindService(this); } catch (Throwable ignored) { }
            try {
                Intent e = new Intent();
                e.setComponent(new ComponentName(PKG, "app.ToolkitService"));
                ok = ctx.bindService(e, this, Context.BIND_AUTO_CREATE);
            } catch (Throwable ignored) { }
        }
        bound = ok;
        state = ok ? "conectando" : "no se encontró com.syu.ms";
    }

    void stop() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        IBinder mm, mc, tk;
        synchronized (this) { mm = modMain; mc = modCan; tk = toolkit; modMain = modCan = toolkit = null; }
        if (mm != null) { unregister(mm, cbMain, C_TEMP); unregister(mm, cbMain, C_VOLT); }
        if (mc != null) unregister(mc, cbCan, C_CANTYPE);
        if (tk != null) try { tk.unlinkToDeath(death, 0); } catch (Throwable ignored) { }
        if (bound) {
            try { ctx.unbindService(this); } catch (Throwable ignored) { }
            bound = false;
        }
        state = "sin conectar";
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        try { service.linkToDeath(death, 0); } catch (Throwable ignored) { }
        synchronized (this) { toolkit = service; }
        IBinder mm = module(service, M_MAIN), mc = module(service, M_CANBUS);
        synchronized (this) { modMain = mm; modCan = mc; }
        if (mm != null) { register(mm, cbMain, C_VOLT); register(mm, cbMain, C_TEMP); }
        if (mc != null) register(mc, cbCan, C_CANTYPE);
        state = mm != null ? "conectado" : "com.syu.ms no respondió";
        post();
    }

    public void onServiceDisconnected(ComponentName name) {
        synchronized (this) { toolkit = modMain = modCan = null; }
        state = "desconectado";
        post();
    }

    private void post() {
        main.post(new Runnable() { public void run() {
            try { if (!closed) listener.onCarData(); } catch (Throwable e) { Guard.log("FYT: " + e); }
        }});
    }

    private void onValue(int module, int code, int[] v) {
        if (v == null || v.length == 0) return;
        int x = v[0];
        if (module == M_MAIN && code == C_VOLT) {
            voltDeci = x >= 60 && x <= 200 ? x : -1;          // 6–20 V; fuera de eso, dato inválido
        } else if (module == M_MAIN && code == C_TEMP) {
            tempDeci = x >= 500 && x <= 1700 ? x - 1000 : Integer.MIN_VALUE;   // −50…+70 °C
        } else if (module == M_CANBUS && code == C_CANTYPE) {
            canType = x & 0xFFFF;
        } else return;
        post();
    }

    // ───────────────────────── transacciones AIDL (a mano, sin stubs) ─────────────────────────

    private static IBinder module(IBinder tk, int id) {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(D_TOOLKIT);
            d.writeInt(id);
            tk.transact(1, d, r, 0);
            r.readException();
            return r.readStrongBinder();
        } catch (Throwable e) {
            return null;
        } finally { d.recycle(); r.recycle(); }
    }

    private static void register(IBinder m, IBinder cb, int code) {
        Parcel d = Parcel.obtain();
        try {
            d.writeInterfaceToken(D_MODULE);
            d.writeStrongBinder(cb);
            d.writeInt(code);
            d.writeInt(1);   // 1 = mandar también el valor actual
            m.transact(3, d, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable ignored) {
        } finally { d.recycle(); }
    }

    private static void unregister(IBinder m, IBinder cb, int code) {
        Parcel d = Parcel.obtain();
        try {
            d.writeInterfaceToken(D_MODULE);
            d.writeStrongBinder(cb);
            d.writeInt(code);
            m.transact(4, d, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable ignored) {
        } finally { d.recycle(); }
    }

    private final class Callback extends Binder {
        final int module;
        Callback(int m) { module = m; }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(D_CALLBACK);
                return true;
            }
            if (code == 1) {
                try {
                    data.enforceInterface(D_CALLBACK);
                    int c = data.readInt();
                    int[] ints = data.createIntArray();
                    onValue(module, c, ints);
                } catch (Throwable ignored) { }
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }
}
