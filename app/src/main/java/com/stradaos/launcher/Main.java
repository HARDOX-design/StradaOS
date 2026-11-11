package com.stradaos.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class Main extends Activity {

    static int RED = 0xFFFE3211;   // color de acento (configurable)
    static int BG = 0xFF15171C;
    static int CARD = 0xFF1E2128;
    static int INNER = 0xFF262A33;
    static int BTN = 0xFF2A2E37;
    static int DOCK = 0xFF0D0E11;
    static int TEXT = 0xFFF2F3F5;
    static int MUTED = 0xFF8B9099;
    static int SOFT = 0xFFC5C8CE;
    static int CIRCLE = 0xFF23262E;
    static int ICON = 0xFFFFFFFF;
    static boolean LIGHT;
    static int RED_DARK = 0xFF2A1416;

    static final String PKG_HUR = "com.andrerinas.headunitrevived";
    static final String PKG_WEB = "org.bromite.bromite";
    static final String ASSET_HUR = "apps/headunit-revived.apk";

    static final Locale ES = new Locale("es", "CO");

    // ranuras configurables: id, nombre, palabras clave en el nombre de la app, palabras clave en el paquete
    static final String[][] SLOTS = {
        {"radio", "Radio", "radio|fm|收音", "radio|fm"},
        {"bt", "Bluetooth", "bluetooth|bt|teléfono|telefono|phone", "bluetooth|.bt|btphone|btmusic"},
        {"cam", "Cámara", "cámara|camara|camera|avin|reversa|dvr", "avin|camera|dvr|backcar|reverse"},
        {"files", "Archivos", "archivo|explorador|gestor|file|files", "filemanager|fileexplorer|documentsui|file"},
        {"settings", "Ajustes", "", ""},
    };
    static final String[] SLOT_ICONS = {"radio", "bluetooth", "photo_camera", "folder", "settings"};

    private SharedPreferences prefs;
    private final Handler h = new Handler();
    private final List<ResolveInfo> apps = new ArrayList<ResolveInfo>();
    private final HashMap<String, Bitmap> iconCache = new HashMap<String, Bitmap>();

    private FrameLayout root;
    private FrameLayout board;
    private FrameLayout drawer;
    private float s = 1f;
    private int builtW, builtH;
    /** Ancho extra (en unidades de 1024x600) en pantallas más anchas. */
    private float ex;
    // ── diseño del inicio (Bloque 2) ──
    /** Ancho de la barra lateral de botones (modo «Lateral»). */
    static final float RAIL = 96;
    private boolean sideDock, compactDock, playerLeft, showCar;
    /** x donde empieza el área de tarjetas (con la barra lateral a la izquierda), alto de las tarjetas y arriba del dock. */
    private float ox, cardH = 380, dockTop = 456, boardH = 600;
    /** Columnas: x de la tarjeta del vehículo, del mapa y del reproductor, y anchos del mapa y del reproductor. */
    private float carX = 16, mapX = 328, mapW = 420, audX = 760, audW = 248;
    private TextView topSpeedTv, topOilTv;
    /** Reproductor vertical (tarjeta alta, p. ej. con la barra lateral): carátula y botones más grandes. */
    private boolean tallPlayer;
    /** Escala de 1024x600 para Ajustes, el cajón y sus ventanas: con la barra lateral el inicio se agranda, pero
     *  esas pantallas deben conservar su tamaño (en un S23 con barra lateral Ajustes quedaba cortado arriba). */
    private float uiS = 1f;
    static final int[] SIM_W = {0, 1024, 1280, 1920, 2000, 1280};
    static final int[] SIM_H = {0, 600, 720, 720, 1200, 480};

    private TextView weatherTv, weatherCityTv;
    private ImageView weatherIc;
    private LinearLayout weatherBox;
    private double wLat = Double.NaN, wLon = Double.NaN;
    private long weatherAt;
    private Weather.Now weatherNow;
    private FrameLayout settingsView;
    private int settingsCat = -1;
    private TextView timeTv, dateTv, greetTv, speedTv, gpsTv, aaBtnTv, aaSubTv, trackTv, artistTv, carTv, chipTv;
    private NowPlaying nowPlaying;
    private Boolean playingState;
    private TextView sourceTv, timePosTv, mapTagTv, cpSubTv;
    private LiveMapView liveMap;
    private MediaWatcher mediaWatcher;
    private boolean askedPerms;
    static final int SDK = android.os.Build.VERSION.SDK_INT;
    private PlayerArt playerArt;
    // estado de la pista
    private String curTitle, curArtist, curSource;
    private Bitmap curArt;
    private long durMs = -1, posBaseMs = 0, posBaseAt = 0;
    private boolean posKnown;
    private String curOrigin;
    private BtBridge bt;
    private View gpsDot;
    private ImageView playIc;

    private LocationManager lm;
    private long lastFix, lastFixAny, gpsStartAt;
    private boolean installing;

    // ───────────────────────── ciclo de vida ─────────────────────────

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Guard.install(this);
        Guard.startWatchdog();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        I18n.init(this, prefs);
        LiveMapView.headingUp = prefs.getBoolean("map_heading", false);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (getPackageName().endsWith(".debug") && getIntent() != null && "saltar".equals(getIntent().getStringExtra("prueba")))
            prefs.edit().putBoolean("setup_done", true).apply();
        migrateOrSetup();
        testHook(getIntent());
        applyTheme();
        applyAccent();

        root = new FrameLayout(this) {
            @Override
            protected void onSizeChanged(int w, int hh, int ow, int oh) {
                super.onSizeChanged(w, hh, ow, oh);
                if (w > 0 && hh > 0 && (w != builtW || hh != builtH)) {
                    builtW = w;
                    builtH = hh;
                    sizeStorm(w, hh);
                    final int fw = w, fh = hh;
                    post(new Runnable() { public void run() { build(fw, fh); } });
                }
            }
        };
        root.setBackgroundColor(BG);
        setContentView(root);
        try {
            Location ll = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (ll == null) ll = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (ll != null) { wLat = ll.getLatitude(); wLon = ll.getLongitude(); }
        } catch (Throwable ignored) { }
        loadApps();
        startOdo();
        // mapa sin conexión: aviso al terminar la descarga y, si quedó a medias (se apagó el carro), continuarla
        MapDownload.onFinish = new Runnable() { public void run() { onMapDownloadFinished(); } };
        if (MapDownload.paused(this) && !prefs.getBoolean("map_dl_user_paused", false)) {
            h.postDelayed(new Runnable() { public void run() { if (!isFinishing()) MapDownload.resume(Main.this); } }, 20000);
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
        IntentFilter t = new IntentFilter(Intent.ACTION_TIME_TICK);
        t.addAction(Intent.ACTION_TIME_CHANGED);
        t.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, t);
        try {
            registerReceiver(usbReceiver, new IntentFilter("android.hardware.usb.action.USB_DEVICE_ATTACHED"));
        } catch (Throwable ignored) { }
        try {
            registerReceiver(usbOffReceiver, new IntentFilter("android.hardware.usb.action.USB_DEVICE_DETACHED"));
        } catch (Throwable ignored) { }
        nowPlaying = new NowPlaying(this, new NowPlaying.Listener() {
            public void onTrack(NowPlaying.Track t) { showTrack(t); }
        });
        try { nowPlaying.start(); } catch (Throwable ignored) { }
        NotifArt.listener = new NotifArt.Listener() {
            public void onAaNotification(final Bitmap art, final java.util.List<String> texts) {
                h.post(new Runnable() { public void run() { applyAaNotif(art, texts); } });
            }
        };
        if (SDK >= 21) {
            try {
                mediaWatcher = new MediaWatcher(this, new NowPlaying.Listener() {
                    public void onTrack(final NowPlaying.Track t) {
                        h.post(new Runnable() { public void run() { showTrack(t); } });
                    }
                });
                mediaWatcher.start();
            } catch (Throwable e) { mediaWatcher = null; }
        }
        bt = new BtBridge(this, new BtBridge.Listener() {
            public void onBtState(NowPlaying.Track t) { showTrack(t); }
        });
        clwBt = BtBridge.available(this);
        bt.onPhoneStarted = new Runnable() { public void run() { onPhoneStartedBt(); } };
        bt.onLost = new Runnable() { public void run() { btRouted = false; } };
        h.postDelayed(btTicker, 3000);
        try {
            IntentFilter bf = new IntentFilter("android.bluetooth.device.action.ACL_CONNECTED");
            bf.addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");
            registerReceiver(btConnReceiver, bf);
        } catch (Throwable ignored) { }
    }

    // ───────────────────────── audio Bluetooth ─────────────────────────

    private boolean clwBt;
    /** true cuando «Audio Bluetooth» fue la última app de fuente al frente (el sistema le dio el sonido). */
    private boolean btRouted;
    private long lastBtConnect, lastBtOpen;
    /** Apps de fuente de fábrica: si pasan al frente, el sonido deja de ser Bluetooth. */
    static final String[] OTHER_SOURCES = {"com.clw.fmam", "com.clw.aux", "com.clw.dvd", "com.clw.video",
        "com.autochips.dvdplayer", "com.autochips.ipodplayer", "com.android.music", "com.clw.music", "com.clw.media"};

    /** Pantallas Clinkway: StradaOS pasa el sonido a Bluetooth cuando el celular empieza a sonar. */
    private boolean btAutoOn() {
        return clwBt && prefs.getBoolean("bt_auto3", false);
    }

    private final long startedAt = SystemClock.elapsedRealtime();

    /** Paquete de la app que está al frente (Android 4.x; en versiones nuevas puede venir vacío). */
    private String topPackage() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<android.app.ActivityManager.RunningTaskInfo> t = am.getRunningTasks(1);
            if (t != null && !t.isEmpty() && t.get(0).topActivity != null) return t.get(0).topActivity.getPackageName();
        } catch (Throwable ignored) { }
        return null;
    }

    /** Sigue qué app de fuente estuvo al frente por última vez. */
    private void trackSource() {
        String top = topPackage();
        if (top == null) return;
        if (top.equals(BtBridge.PKG)) btRouted = true;
        else for (String p : OTHER_SOURCES) if (p.equals(top)) { if (btRouted) BtBridge.log("otra fuente al frente: " + top); btRouted = false; }
    }

    /** El celular empezó a sonar por Bluetooth. */
    private void onPhoneStartedBt() {
        if (!btAutoOn()) return;
        if (SystemClock.elapsedRealtime() - startedAt < 60000) { BtBridge.log("no se cambió: StradaOS acaba de arrancar"); return; }
        String top = topPackage();
        if (top != null && (top.contains("btphone") || top.contains("dialer") || top.contains("incallui"))) { BtBridge.log("no se cambió: llamada en curso"); return; }
        if ("Android Auto".equals(curSource) && aaRunning()) { BtBridge.log("no se cambió: Android Auto tiene el sonido"); return; }
        trackSource();
        if (btRouted) { BtBridge.log("el sonido ya es Bluetooth"); return; }
        openBtAudioApp(false);
    }

    /** Otras pantallas: al conectar el celular, abre un momento la app de música Bluetooth y vuelve. */
    private final BroadcastReceiver btConnReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a != null && a.contains("CONNECTION_STATE_CHANGED")) {
                int st = -1;
                try { st = i.getIntExtra("android.bluetooth.profile.extra.STATE", -1); } catch (Throwable ignored) { }
                if (st != 2) return; // 2 = conectado
            }
            BtBridge.log("celular conectado por Bluetooth");
            if (clwBt) { if (bt != null && btAutoOn()) bt.connect(); return; }
            if (!prefs.getBoolean("bt_open_connect", false)) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastBtConnect < 20000) return;
            lastBtConnect = now;
            h.postDelayed(new Runnable() { public void run() { openBtAudioApp(false); } }, 4000);
        }
    };

    /** App de música Bluetooth de esta pantalla (la elegida o la que se detecta por el nombre). */
    private ComponentName btMusicApp() {
        String c = prefs.getString("btaudio", null);
        if (c != null) return ComponentName.unflattenFromString(c);
        ResolveInfo r = find("audio bluetooth|música bluetooth|musica bluetooth|bt music|bluetooth music|música bt|musica bt|a2dp",
                "btmusic|bt.music|bt_music|btaudio|a2dp|bluetoothmusic|music.bt");
        return r == null ? null : new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
    }

    /** Abre la app de música Bluetooth un instante (para que tome el sonido) y regresa a donde estaba el usuario. */
    private void openBtAudioApp(boolean manual) {
        final String top = topPackage();
        if (!manual) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastBtOpen < 15000) return;
            if (top != null && (top.contains("backcar") || top.contains("avin") || top.contains("camera") || top.contains("dvr"))) {
                BtBridge.log("no se abrió la app BT: cámara de reversa al frente"); return;
            }
            if (top == null && !hasWindowFocus()) { BtBridge.log("no se abrió la app BT: no sé qué app está al frente"); return; }
            if (settingsView != null && hasWindowFocus()) { BtBridge.log("no se abrió la app BT: ajustes abiertos"); return; }
            lastBtOpen = now;
        }
        // tarea a la que se vuelve después (otra app, por ejemplo Waze), o StradaOS
        int backTask = -1;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<android.app.ActivityManager.RunningTaskInfo> t = am.getRunningTasks(1);
            if (top != null && !top.equals(getPackageName()) && !top.equals(BtBridge.PKG) && t != null && !t.isEmpty()) backTask = t.get(0).id;
        } catch (Throwable ignored) { }
        final int back = backTask;
        ComponentName cn = btMusicApp();
        if (cn == null) {
            if (manual) { toast("Elige primero la app de música Bluetooth"); pickFor("btaudio", "Música Bluetooth"); }
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
            btRouted = true;
            BtBridge.activations++;
            BtBridge.log("abierta " + cn.getPackageName() + " para tomar el sonido" + (back >= 0 ? " (luego vuelve a " + top + ")" : ""));
        } catch (Throwable e) {
            if (manual) toast("No se pudo abrir la app");
            return;
        }
        h.postDelayed(new Runnable() { public void run() {
            // si el usuario ya cambió de app, no lo movemos
            String now = topPackage();
            if (now != null && !now.equals(BtBridge.PKG) && !now.equals(btMusicApp() != null ? btMusicApp().getPackageName() : "")) {
                BtBridge.log("no se volvió: el usuario abrió " + now); return;
            }
            try {
                if (back >= 0) {
                    ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).moveTaskToFront(back, 0);
                    return;
                }
            } catch (Throwable ignored) { }
            try {
                startActivity(new Intent(Main.this, Main.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } catch (Throwable ignored) { }
        }}, prefs.getInt("bt_return_ms", clwBt ? 2000 : 2500));
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(timeReceiver); } catch (Exception ignored) { }
        try { nowPlaying.stop(); } catch (Throwable ignored) { }
        try { unregisterReceiver(usbReceiver); } catch (Throwable ignored) { }
        try { unregisterReceiver(usbOffReceiver); } catch (Throwable ignored) { }
        try { bt.disconnect(); } catch (Throwable ignored) { }
        try { unregisterReceiver(btConnReceiver); } catch (Throwable ignored) { }
        h.removeCallbacks(btTicker);
        h.removeCallbacks(aaWait);
        try { if (mediaWatcher != null) mediaWatcher.stop(); } catch (Throwable ignored) { }
        try { lm.removeUpdates(odoListener); } catch (Throwable ignored) { }
        odoRunning = false;
        if (odoPending > 0) prefs.edit().putFloat("odo_gps_m", prefs.getFloat("odo_gps_m", 0) + odoPending).apply();
        Guard.stopWatchdog();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (homeFixing && SDK < 29) h.postDelayed(new Runnable() { public void run() { homeResult(); } }, 700);
        immersive();
        if (SDK >= 23 && !askedPerms) {
            askedPerms = true;
            try { Modern.ensurePermissions(this, 77); } catch (Throwable ignored) { }
        }
        if (mediaWatcher != null) mediaWatcher.start();
        guardUsb();
        maybeWeather();
        h.postDelayed(new Runnable() { public void run() { autoChecks(); } }, 4000);
        updateClock();
        refreshStates();
        startGps();
        h.postDelayed(ticker, 1000);
        h.removeCallbacks(progressTick);
        h.post(progressTick);
        applyAaNotif(NotifArt.lastArt, NotifArt.lastTexts);
    }

    /** Android 6+: respuesta a la solicitud de permisos. */
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_STORAGE_W) {   // con o sin permiso se sigue (sin él, el mapa se guarda en la carpeta de la app)
            h.post(new Runnable() { public void run() { chooseMapToDownload(""); } });
            return;
        }
        if (req == REQ_STORAGE) {
            if (res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) pickMapFile();
            else toast("Sin permiso de almacenamiento no se pueden buscar mapas");
            return;
        }
        if (req != 77 && req != 78) return;
        try { lm.removeUpdates(odoListener); } catch (Throwable ignored) { }
        odoRunning = false;
        startOdo();
        stopGps();
        startGps();
        seedLastLocation();
        updateMapTag();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopGps();
        h.removeCallbacks(ticker);
        h.removeCallbacks(progressTick);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        closeDrawer();
        testHook(i);
    }

    /**
     * Solo en la versión de prueba (paquete .debug): permite que las pruebas automáticas abran cada pantalla
     * sin tocar coordenadas. adb shell am start -n com.stradaos.launcher.debug/com.stradaos.launcher.Main --es prueba VALOR
     * VALOR: saltar (omite el asistente), inicio, ajustes:N, boton:N (menú del botón N), elegir:N (lista de apps del botón N),
     * cajon (todas las apps), musica (lista de música preferida), asistente, demo-barra (cambia los botones 3 y 4), idioma:es|en|pt|auto,
     * ultimo-aceite / ultimo-alto / unidad-aceite (recordatorio de aceite), ocultas / ocultar-demo / ocultar-limpiar (apps ocultas),
     * salir-fabrica (abre el launcher de fábrica), desactivar (apaga StradaOS; solo lo usa salida.yml),
     * diseno:izq|der|sinauto|conauto|normal|compacta|lateral|plegar|base (diseño del inicio), sim:5 (1280x480).
     */
    private void testHook(Intent i) {
        if (i == null || !getPackageName().endsWith(".debug")) return;
        final String v = i.getStringExtra("prueba");
        if (v == null) return;
        if ("saltar".equals(v)) {
            prefs.edit().putBoolean("setup_done", true).putBoolean("home_never", true).apply();
            try { if (wizardDlg != null) wizardDlg.dismiss(); } catch (Throwable ignored) { }
        }
        h.postDelayed(new Runnable() { public void run() {
            try {
                if (settingsView != null) closeSettings();
                closeDrawer();
                if (v.startsWith("ajustes:")) showSettings(Integer.parseInt(v.substring(8)));
                else if (v.startsWith("boton:")) dockMenu(Integer.parseInt(v.substring(6)), null);
                else if (v.startsWith("elegir:")) pickDock(Integer.parseInt(v.substring(7)), null);
                else if ("cajon".equals(v)) openDrawer();
                else if ("ocultas".equals(v)) manageHidden(null);
                else if ("salir-fabrica".equals(v)) openFactoryLauncher();
                else if ("pista-demo".equals(v)) {
                    // canción de ejemplo con duración y posición, para ver la línea de tiempo en las capturas
                    curTitle = "Canción de prueba"; curArtist = "StradaOS"; curSource = "Bluetooth";
                    durMs = 215000; posBaseMs = 68000; posBaseAt = SystemClock.elapsedRealtime(); posKnown = true; playingState = false;
                    // carátula de ejemplo (degradado con una nota), sin portadas reales
                    int az = 400;
                    Bitmap art = Bitmap.createBitmap(az, az, Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas cv = new android.graphics.Canvas(art);
                    android.graphics.Paint gp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    gp.setShader(new android.graphics.LinearGradient(0, 0, az, az, 0xFFFF5A36, 0xFFB0124A, android.graphics.Shader.TileMode.CLAMP));
                    cv.drawRect(0, 0, az, az, gp);
                    Bitmap note = asset("ic/music_note.png");
                    if (note != null) cv.drawBitmap(note, null, new android.graphics.RectF(az * 0.3f, az * 0.3f, az * 0.7f, az * 0.7f), null);
                    curArt = art;
                    renderTrack();
                }
                else if ("tema:claro".equals(v) || "tema:oscuro".equals(v)) {
                    prefs.edit().putString("theme", "tema:claro".equals(v) ? "light" : "dark").apply();
                    applyTheme(); applyAccent(); rebuild();
                }
                else if (v.startsWith("diseno:")) {
                    // diseno:izq|der|sinauto|conauto|normal|compacta|lateral|base  (base = como viene)
                    String d = v.substring(7);
                    SharedPreferences.Editor e = prefs.edit();
                    if ("izq".equals(d)) e.putString("player_side", "left");
                    else if ("der".equals(d)) e.putString("player_side", "right");
                    else if ("sinauto".equals(d)) e.putBoolean("show_car", false);
                    else if ("conauto".equals(d)) e.putBoolean("show_car", true);
                    else if ("normal".equals(d) || "compacta".equals(d) || "lateral".equals(d))
                        e.putString("dock_mode", "lateral".equals(d) ? "side" : "compacta".equals(d) ? "compact" : "normal");
                    else if ("plegar".equals(d)) e.putBoolean("aa_panel_open", false);
                    else if ("base".equals(d)) e.remove("player_side").remove("show_car").remove("dock_mode").remove("aa_panel_open");
                    e.apply();
                    rebuild();
                }
                else if ("desactivar".equals(v) && !otherHomes().isEmpty()) disableSelf(otherHomes().get(0));   // solo instalador.yml
                else if ("musica".equals(v)) pickFor("music_app", "Música", null);
                else if ("asistente".equals(v)) wizard();
                else if (v.startsWith("idioma:")) setLanguage(v.substring(7));
                else if ("fijar-inicio".equals(v)) makeDefaultHome();
                else if (v.startsWith("sim:")) { prefs.edit().putInt("sim_screen", Integer.parseInt(v.substring(4))).apply(); rebuild(); }
                else if ("diag-mapa".equals(v)) showMapDiag();
                else if ("demo-aceite".equals(v)) {
                    prefs.edit().putBoolean("oil_on", true).putFloat("odo_base_km", 44200).putFloat("odo_gps_m", 0)
                        .putFloat("oil_last_km", 40000).putFloat("oil_interval_km", 5000).putFloat("oil_snooze_km", 0).apply();
                    rebuild();
                }
                else if ("aviso-aceite".equals(v)) {
                    prefs.edit().putFloat("odo_base_km", 45100).putFloat("odo_gps_m", 0).putFloat("oil_snooze_km", 0).apply();
                    oilLater = false; rebuild(); oilAlert();
                }
                else if ("menu-aceite".equals(v)) oilMenu();
                else if ("ultimo-aceite".equals(v)) askLast(null);
                else if ("ultimo-alto".equals(v)) saveLast(odoKm() / 0.621371f, null);   // el caso del issue #7: el número en millas
                else if ("unidad-aceite".equals(v)) { prefs.edit().putString("speed_unit", "auto").apply(); askOilUnit(new Runnable() { public void run() { } }); }
                else if ("ocultar-demo".equals(v) && !apps.isEmpty()) {
                    java.util.HashSet<String> hs = new java.util.HashSet<String>();
                    hs.add(compKey(apps.get(0)));
                    if (apps.size() > 1) hs.add(compKey(apps.get(1)));
                    saveHidden(hs); closeDrawer(); openDrawer();
                }
                else if ("ocultar-limpiar".equals(v)) { prefs.edit().remove("hidden_apps").apply(); closeDrawer(); }
                else if (v.startsWith("rumbo:")) {
                    prefs.edit().putBoolean("map_heading", "1".equals(v.substring(6))).apply(); LiveMapView.headingUp = "1".equals(v.substring(6)); rebuild();
                    h.postDelayed(new Runnable() { public void run() {
                        if (liveMap == null) return;
                        Location l = new Location(LocationManager.GPS_PROVIDER);
                        l.setLatitude(4.6533); l.setLongitude(-74.0836); l.setBearing(45); l.setSpeed(12); l.setTime(System.currentTimeMillis());
                        feedMap(l);
                    }}, 1500);
                }
                else if (v.startsWith("mapa-archivo:")) {
                    prefs.edit().putString("map_file", v.substring(13)).apply();
                    LiveMapView.loadFile(Main.this); rebuild();
                }
                else if (v.startsWith("mapa-zoom:")) { if (liveMap != null) liveMap.setZoom(Integer.parseInt(v.substring(10))); }
                else if ("mapa-buscar".equals(v)) pickMapFile();
                else if ("mapa-diag".equals(v)) showMapDiag();
                else if (v.startsWith("mapa-lista")) chooseMapToDownload(v.length() > 11 ? v.substring(11) : "");
                else if (v.startsWith("mapa-descargar:")) { String pth = v.substring(15); MapDownload.start(Main.this, pth, pth.substring(pth.lastIndexOf('/') + 1)); }
                else if ("mapa-pausar".equals(v)) MapDownload.stop(false);
                else if ("mapa-continuar".equals(v)) MapDownload.resume(Main.this);
                else if ("mapa-mover".equals(v)) {
                    // 20 posiciones, una por segundo, a ~43 km/h hacia el noreste (para ver el movimiento suave)
                    final double la0 = !Double.isNaN(wLat) ? wLat : 6.2442, lo0 = !Double.isNaN(wLon) ? wLon : -75.5680;
                    for (int i = 0; i < 20; i++) {
                        final int n = i;
                        h.postDelayed(new Runnable() { public void run() {
                            Location l = new Location(LocationManager.GPS_PROVIDER);
                            l.setLatitude(la0 + n * 0.000085); l.setLongitude(lo0 + n * 0.000085);
                            l.setBearing(45); l.setSpeed(12); l.setTime(System.currentTimeMillis());
                            feedMap(l);
                        }}, i * 1000L);
                    }
                }
                else if (v.startsWith("unidad:")) { prefs.edit().putString("speed_unit", v.substring(7)).apply(); rebuild(); }
                else if ("odo-sim".equals(v)) {
                    // 300 posiciones a 25 m una de otra, una por segundo (90 km/h): deben sumar 7,5 km
                    float before = odoKm();
                    odoLast = null;
                    long t0 = System.currentTimeMillis();
                    float expect = 0;
                    Location prev = null;
                    for (int i = 0; i <= 300; i++) {
                        Location l = new Location(LocationManager.GPS_PROVIDER);
                        l.setLatitude(4.6 + i * 25 / 111320.0); l.setLongitude(-74.08); l.setTime(t0 + i * 1000L);
                        odoStep(l);
                        if (prev != null) expect += prev.distanceTo(l) / 1000f;
                        prev = l;
                    }
                    // un salto imposible (50 km en 1 s) no debe sumarse
                    Location j = new Location(LocationManager.GPS_PROVIDER);
                    j.setLatitude(5.05); j.setLongitude(-74.08); j.setTime(t0 + 301000L);
                    odoStep(j);
                    prefs.edit().putFloat("odo_gps_m", prefs.getFloat("odo_gps_m", 0) + odoPending).apply();
                    odoPending = 0;
                    updateOilBadge();
                    float got = odoKm() - before;
                    Guard.log("PRUEBA odo-sim: ok=" + (Math.abs(got - expect) < 0.05f && expect > 7) + " sumo=" + got + " esperado=" + expect);
                }
                else if ("verificar-apk".equals(v)) {
                    boolean own = verifyApk(new File(getPackageCodePath()));
                    boolean other = false;
                    try { other = verifyApk(new File(getPackageManager().getApplicationInfo("com.android.settings", 0).sourceDir)); } catch (Throwable ignored) { }
                    Guard.log("PRUEBA verificar-apk: propio=" + own + " otro=" + other);
                }
                else if ("aviso-inicio".equals(v)) askDefaultHome(null);
                else if ("demo-barra".equals(v) && !apps.isEmpty()) {
                    ResolveInfo r = apps.get(0);
                    prefs.edit().putString("dock_2", "fn:music")
                        .putString("dock_3", "app:" + new ComponentName(r.activityInfo.packageName, r.activityInfo.name).flattenToString())
                        .putString("dock_3_name", "Mi app").apply();
                    rebuild();
                }
            } catch (Throwable e) {
                Guard.log("Prueba «" + v + "» falló: " + e);
            }
        }}, 1500);
    }

    @Override
    public void onBackPressed() {
        if (settingsView != null) { closeSettings(); return; }
        closeDrawer(); // es la pantalla de inicio: atrás no la cierra
    }

    /**
     * Protección: si el tamaño de la ventana cambia una y otra vez (barras del sistema que aparecen y desaparecen
     * al pedir pantalla completa), StradaOS se reconstruiría sin parar. Tras 5 cambios en 6 s deja de pedir
     * pantalla completa y lo anota en el registro de fallos.
     */
    private void sizeStorm(int w, int hh) {
        long now = SystemClock.uptimeMillis();
        if (now - stormStart > 6000) { stormStart = now; stormCount = 0; }
        if (++stormCount >= 5 && !noImmersive) {
            noImmersive = true;
            Guard.log("La ventana cambió de tamaño " + stormCount + " veces en 6 s (último " + w + "x" + hh + "): se desactiva la pantalla completa.");
            try { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE); } catch (Throwable ignored) { }
            try { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); } catch (Throwable ignored) { }
        }
    }
    private long stormStart;
    private int stormCount;
    private boolean noImmersive;

    private void immersive() {
        if (noImmersive) return;
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN | 0x00001000 /* IMMERSIVE_STICKY, API 19 */);
        } catch (Exception ignored) { }
    }

    /** Al conectar algo por USB: si aún no se eligió cómo conectar el celular y hay varias opciones, se pregunta. */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            if (prefs.getString("conn_app", null) == null && prefs.getString("aa", null) == null && connCandidates().size() > 1 && hasWindowFocus())
                chooseConnection(null);
            else guardUsb();
        }
    };

    private int btPoll, usbTick, themeTick;
    private boolean usbWarned;

    /** Apps de espejo de fábrica que se adelantan a Open Headunit al conectar el celular por USB. */
    static final String[] USB_HOGS = {"net.easyconn", "com.carbit", "net.easyconn.carman"};
    static final java.util.HashSet<String> USB_HOG_SET = new java.util.HashSet<String>(java.util.Arrays.asList(USB_HOGS));

    private boolean processRunning(String pkg) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<android.app.ActivityManager.RunningAppProcessInfo> ps = am.getRunningAppProcesses();
            if (ps == null) return false;
            for (android.app.ActivityManager.RunningAppProcessInfo p : ps)
                if (p.processName != null && (p.processName.equals(pkg) || p.processName.startsWith(pkg + ":"))) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    /** Si EasyConnection quedó corriendo, la cierra; si vuelve, pide al usuario «Forzar detención». */
    private void guardUsb() {
        // Solo se vigila cuando el usuario eligió OTRA app para conectar el celular.
        String chosen = prefs.getString("conn_app", null);
        if (chosen == null && prefs.getString("aa", null) != null) {
            try { chosen = ComponentName.unflattenFromString(prefs.getString("aa", "")).getPackageName(); } catch (Throwable ignored) { }
        }
        if (chosen == null || USB_HOG_SET.contains(chosen) || prefs.getBoolean("allow_easyconn", false)) return;
        final android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (final String pkg : USB_HOGS) {
            if (!installed(pkg) || !processRunning(pkg)) continue;
            // Silencioso: con los datos de EasyConnection borrados, Android Auto funciona aunque siga corriendo.
            try { am.killBackgroundProcesses(pkg); } catch (Throwable ignored) { }
        }
    }

    /** Bluetooth: sigue revisando aunque StradaOS esté detrás de otra app (Waze, Maps…). */
    private final Runnable btTicker = new Runnable() {
        public void run() {
            if (bt != null) {
                bt.setAutoCreate(btAutoOn());
                bt.connect();
                if (btAutoOn()) trackSource();
                if (bt.isConnected()) bt.poll(btAutoOn());
            }
            checkAaGone(false);
            h.postDelayed(this, 2000);
        }
    };

    private final Runnable ticker = new Runnable() {
        public void run() {
            if (lastFix > 0 && SystemClock.elapsedRealtime() - lastFix > 6000) setGps(false, 0);
            else if (lastFix == 0) setGps(false, 0);
            if (++usbTick % 5 == 0) guardUsb();
            if (bt != null && bt.isConnected() && (++btPoll % 3 == 0 || curTitle == null)) bt.refresh();
            updatePlayIcon();
            if (++themeTick % 30 == 0) {
                boolean was = LIGHT;
                applyTheme();
                if (was != LIGHT) { applyAccent(); rebuild(); }
                maybeWeather();
            }
            h.postDelayed(this, 2000);
        }
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { updateClock(); }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            loadApps();
            refreshStates();
        }
    };

    // ───────────────────────── construcción de la UI ─────────────────────────

    private void build(int W, int H) {
        root.removeAllViews();
        // versión de prueba: simular otra pantalla (p. ej. una ancha de 1920x720 en una tablet)
        int sim = getPackageName().endsWith(".debug") ? prefs.getInt("sim_screen", 0) : 0;
        if (sim > 0) {
            float r = SIM_W[sim] / (float) SIM_H[sim];
            int pw = 0, ph = 0;
            if (W / (float) H > r) { pw = (int) ((W - H * r) / 2); W = (int) (H * r); }
            else { ph = (int) ((H - W / r) / 2); H = (int) (W / r); }
            root.setPadding(pw, ph, pw, ph);
            root.setBackgroundColor(Color.BLACK);
            View bg = new View(this);
            bg.setBackgroundColor(BG);
            root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        } else {
            root.setPadding(0, 0, 0, 0);
            root.setBackgroundColor(BG);
        }
        // Diseño del inicio (Ajustes → Apariencia → Diseño del inicio)
        String dm = prefs.getString("dock_mode", "normal");
        sideDock = "side".equals(dm);
        compactDock = "compact".equals(dm);
        playerLeft = "left".equals(prefs.getString("player_side", "right"));
        showCar = prefs.getBoolean("show_car", true);
        // Lienzo virtual: 1024x600. Con la barra lateral los botones salen de abajo y el lienzo queda más bajo
        // (1128x460): en pantallas bajas y anchas como 1280x480 todo se ve ~30 % más grande.
        float dw = 1024 + (sideDock ? RAIL + 8 : 0), dh = sideDock ? 460 : 600;
        s = Math.min(W / dw, H / dh);
        // pantallas más anchas que el lienzo (p. ej. 12,3" 1920x720): el mapa del centro se queda con el espacio extra
        ex = Math.max(0f, W / s - dw);
        if (ex < 24) ex = 0;
        // con la barra lateral en pantallas que no son tan bajas (p. ej. 1024x600) las tarjetas crecen hasta llenar
        // el alto, en vez de dejar franjas vacías arriba y abajo
        if (sideDock && H / s > dh + 1) dh = H / s;
        uiS = Math.min(W / 1024f, H / 600f);
        boardH = dh;
        ox = sideDock && playerLeft ? RAIL + 8 : 0;   // la barra lateral va del lado del reproductor (el del conductor)
        dockTop = sideDock ? dh : compactDock ? 500 : 456;
        cardH = sideDock ? dh - 80 : dockTop - 76;
        layoutColumns();
        getWindow().setBackgroundDrawable(new ColorDrawable(sim > 0 ? Color.BLACK : BG));
        board = new FrameLayout(this);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams((int) ((dw + ex) * s), (int) (dh * s));
        blp.gravity = Gravity.CENTER;
        root.addView(board, blp);

        if (!sideDock) {
            // franja del dock a todo el ancho (aunque la pantalla no sea 1024x600)
            View dockBg = new View(this);
            dockBg.setBackgroundColor(DOCK);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (H - ((H - dh * s) / 2 + dockTop * s)) + 1);
            dlp.gravity = Gravity.BOTTOM;
            root.addView(dockBg, 0, dlp);
        }

        buildTop();
        buildCarCard();
        buildAaCard();
        buildAudioCard();
        buildDock();

        drawer = null;
        settingsView = null;
        updateClock();
        refreshStates();
        showWeather();
        if (settingsCat >= 0) showSettings(settingsCat);
    }

    /**
     * Columnas del inicio: tarjeta del vehículo (300), mapa (lo que sobra) y reproductor (248; 300 si no hay tarjeta
     * del vehículo). «Reproductor a la izquierda» las intercambia para dejarlo del lado del conductor.
     */
    private void layoutColumns() {
        float content = 1024 + ex;
        audW = showCar ? 248 : 300;
        float carW = showCar ? 300 : 0;
        float leftW = playerLeft ? audW : carW, rightW = playerLeft ? carW : audW;
        float x0 = ox + 16;
        mapX = x0 + (leftW > 0 ? leftW + 12 : 0);
        mapW = content - 32 - leftW - rightW - (leftW > 0 ? 12 : 0) - (rightW > 0 ? 12 : 0);
        float rightX = mapX + mapW + 12;
        carX = playerLeft ? rightX : x0;
        audX = playerLeft ? x0 : rightX;
    }

    private void buildTop() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(true);
        timeTv = tv("", 30, TEXT, true);
        dateTv = tv("", 14, MUTED, false);
        dateTv.setPadding((int) (12 * s), 0, 0, 0);
        row.addView(timeTv);
        row.addView(dateTv);
        topSpeedTv = null;
        if (!showCar) {
            // sin tarjeta del vehículo, la velocidad se muestra aquí
            topSpeedTv = tv("", 15, TEXT, true);
            topSpeedTv.setPadding((int) (16 * s), 0, 0, 0);
            row.addView(topSpeedTv);
        }
        topOilTv = null;
        if (!showCar) {
            // …y también el aceite (toca para abrir su menú), para que no se pierda al ocultar la tarjeta
            topOilTv = tv("", 13, TEXT, true);
            topOilTv.setBackground(round(CIRCLE, 12));
            topOilTv.setPadding((int) (10 * s), (int) (4 * s), (int) (12 * s), (int) (4 * s));
            android.graphics.drawable.BitmapDrawable di = new android.graphics.drawable.BitmapDrawable(getResources(), asset("ic/water_drop.png"));
            di.setBounds(0, 0, (int) (14 * s), (int) (14 * s));
            if (ICON != 0xFFFFFFFF) di.setColorFilter(ICON, android.graphics.PorterDuff.Mode.SRC_IN);
            topOilTv.setCompoundDrawables(di, null, null, null);
            topOilTv.setCompoundDrawablePadding((int) (6 * s));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
            olp.leftMargin = (int) (14 * s);
            row.addView(topOilTv, olp);
            topOilTv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { oilMenu(); } });
        }
        placeWrap(board, row, ox + 24, 10);

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        weatherBox = new LinearLayout(this);
        weatherBox.setGravity(Gravity.CENTER_VERTICAL);
        weatherIc = icon("cloud", 20);
        weatherBox.addView(weatherIc, new LinearLayout.LayoutParams((int) (20 * s), (int) (20 * s)));
        weatherTv = tv("", 15, TEXT, true);
        weatherTv.setPadding((int) (6 * s), 0, 0, 0);
        weatherBox.addView(weatherTv);
        weatherCityTv = tv("", 13, MUTED, false);
        weatherCityTv.setPadding((int) (6 * s), 0, (int) (14 * s), 0);
        weatherBox.addView(weatherCityTv);
        View sep = new View(this);
        sep.setBackgroundColor(BTN);
        weatherBox.addView(sep, new LinearLayout.LayoutParams(Math.max(1, (int) s), (int) (18 * s)));
        if (!showCar) sep.setVisibility(View.GONE);   // sin saludo, sin separador
        weatherBox.setVisibility(View.GONE);
        right.addView(weatherBox);
        greetTv = tv("", 15, SOFT, false);
        greetTv.setPadding((int) (14 * s), 0, 0, 0);
        right.addView(greetTv);
        // sin la tarjeta del vehículo, la velocidad y el aceite ocupan la barra de arriba: el saludo se oculta
        if (!showCar) greetTv.setVisibility(View.GONE);
        place(board, right, ox + 400 + ex, 14, 552, 32);
        View gear = circleIcon("settings", 40, 22, CIRCLE);
        place(board, gear, ox + 964 + ex, 12, 40, 40);
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
        greetTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
    }

    private void buildCarCard() {
        final FrameLayout card = card(carX, 64, 300, cardH, CARD);
        final float dy = cardH - 380;   // barra de abajo compacta: la foto crece y la velocidad baja
        carTv = tv(prefs.getString("car_name", "Mi vehículo"), 20, TEXT, true);
        place(card, carTv, 20, 16, 260, 26);
        TextView chip = tv(prefs.getString("car_tag", ""), 11, Color.WHITE, true);
        chipTv = chip;
        chip.setPadding((int) (8 * s), (int) (3 * s), (int) (8 * s), (int) (3 * s));
        chip.setBackground(round(RED, 8));
        chip.setVisibility(prefs.getString("car_tag", "").trim().length() == 0 ? View.GONE : View.VISIBLE);
        placeWrap(card, chip, 20, 46);

        Bitmap photo = CarPhoto.load(this);
        if (photo != null) {
            ImageView car = new ImageView(this);
            car.setImageBitmap(photo);
            car.setScaleType(ImageView.ScaleType.FIT_CENTER);
            place(card, car, 18, 78, 264, 160 + dy);
            car.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { photoMenu(); return true; }
            });
        } else {
            LinearLayout ph = new LinearLayout(this);
            ph.setOrientation(LinearLayout.VERTICAL);
            ph.setGravity(Gravity.CENTER);
            ph.setBackground(round(INNER, 16));
            ImageView ic = icon("directions_car", 72);
            ic.setAlpha(0.35f);
            ph.addView(ic, new LinearLayout.LayoutParams((int) (72 * s), (int) (72 * s)));
            TextView t = tv("Toca para poner la foto de tu vehículo", 12, MUTED, false);
            t.setPadding(0, (int) (6 * s), 0, 0);
            ph.addView(t);
            place(card, ph, 18, 82, 264, 152 + dy);
            ph.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { pickPhoto(); }
            });
        }

        FrameLayout box = new FrameLayout(this);
        box.setBackground(round(INNER, 16));
        place(card, box, 12, 250 + dy, 276, 118);

        LinearLayout lbl = new LinearLayout(this);
        lbl.setGravity(Gravity.CENTER_VERTICAL);
        ImageView sp = icon("speed", 16);
        sp.setAlpha(0.6f);
        lbl.addView(sp, new LinearLayout.LayoutParams((int) (16 * s), (int) (16 * s)));
        TextView vl = tv("Velocidad", 13, MUTED, false);
        vl.setPadding((int) (6 * s), 0, 0, 0);
        lbl.addView(vl);
        placeWrap(box, lbl, 16, 12);

        LinearLayout g = new LinearLayout(this);
        g.setGravity(Gravity.CENTER_VERTICAL);
        gpsDot = new View(this);
        gpsDot.setBackground(round(MUTED, 4));
        g.addView(gpsDot, new LinearLayout.LayoutParams((int) (8 * s), (int) (8 * s)));
        gpsTv = tv("GPS", 11, MUTED, false);
        gpsTv.setPadding((int) (6 * s), 0, 0, 0);
        g.addView(gpsTv);
        g.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        place(box, g, 120, 12, 140, 18);

        LinearLayout sv = new LinearLayout(this);
        sv.setBaselineAligned(true);
        speedTv = tv("0", 56, TEXT, true);
        TextView kmh = tv(useMph() ? "mph" : "km/h", 15, MUTED, false);
        kmh.setPadding((int) (8 * s), 0, 0, 0);
        sv.addView(speedTv);
        sv.addView(kmh);
        placeWrap(box, sv, 16, 34);
        buildOilBadge(box);
        // Oculta: se arma igual (la velocidad y el aceite siguen actualizándose sin fallar) pero no se muestra.
        if (!showCar) board.removeView(card);
        setGps(false, 0);
    }

    // ───────────────────────── recordatorio de cambio de aceite ─────────────────────────
    // StradaOS no puede leer el odómetro del tablero, así que lo estima: el usuario escribe el kilometraje una vez
    // y StradaOS le suma la distancia recorrida que mide el GPS. Todo se guarda en km; se muestra en km o millas.

    private TextView oilValTv;
    private View oilBar, oilBarBg;
    private Location odoLast;
    private float odoPending;
    private long odoSavedAt;

    private float odoKm() { return prefs.getFloat("odo_base_km", 0) + prefs.getFloat("odo_gps_m", 0) / 1000f; }
    private boolean oilOn() { return prefs.getBoolean("oil_on", false); }
    private float oilNextKm() { return prefs.getFloat("oil_last_km", 0) + prefs.getFloat("oil_interval_km", 5000); }
    private float toUnit(float km) { return useMph() ? km * 0.621371f : km; }
    private float fromUnit(float v) { return useMph() ? v / 0.621371f : v; }
    private String unit() { return useMph() ? "mi" : "km"; }
    private String fmtDist(float km) {
        return java.text.NumberFormat.getIntegerInstance(I18n.loc).format(Math.round(toUnit(km))) + " " + unit();
    }

    /** Distancia medida por el GPS (también con StradaOS detrás de otras apps). */
    private final LocationListener odoListener = new LocationListener() {
        public void onLocationChanged(Location l) {
            try { odoStep(l); } catch (Throwable ignored) { }
        }
        public void onStatusChanged(String p, int st, Bundle e) { }
        public void onProviderEnabled(String p) { }
        public void onProviderDisabled(String p) { }
    };

    private void odoStep(Location l) {
        if (!oilOn() || l == null) return;
        if (l.hasAccuracy() && l.getAccuracy() > 40) return;
        if (odoLast == null) { odoLast = l; return; }
        float d = odoLast.distanceTo(l);
        float dt = Math.max(1, (l.getTime() - odoLast.getTime()) / 1000f);
        if (d < 20) return;                                 // ruido del GPS con el carro quieto
        if (d / dt > 70) { odoLast = l; return; }           // salto imposible (> 250 km/h): se descarta
        odoLast = l;
        odoPending += d;
        long now = SystemClock.elapsedRealtime();
        if (odoPending >= 200 || now - odoSavedAt > 60000) {
            prefs.edit().putFloat("odo_gps_m", prefs.getFloat("odo_gps_m", 0) + odoPending).apply();
            odoPending = 0;
            odoSavedAt = now;
            updateOilBadge();
        }
    }

    private boolean odoRunning;

    /** Solo escucha el GPS en segundo plano si el recordatorio está activo. */
    private void startOdo() {
        boolean want = oilOn();
        if (want == odoRunning) return;
        try {
            if (want) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 10, odoListener);
            else lm.removeUpdates(odoListener);
            odoRunning = want;
        } catch (Throwable ignored) { }
        odoLast = null;
    }

    private void buildOilBadge(FrameLayout box) {
        View sep = new View(this);
        sep.setBackgroundColor(BTN);
        place(box, sep, 166, 40, 1, 62);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = icon("water_drop", 13);
        ic.setAlpha(0.6f);
        top.addView(ic, new LinearLayout.LayoutParams((int) (13 * s), (int) (13 * s)));
        TextView t = tv("Aceite", 11, MUTED, false);
        t.setPadding((int) (4 * s), 0, 0, 0);
        top.addView(t);
        col.addView(top);
        oilValTv = tv("", 14, TEXT, true);
        oilValTv.setPadding(0, (int) (5 * s), 0, (int) (6 * s));
        col.addView(oilValTv);
        FrameLayout bar = new FrameLayout(this);
        oilBarBg = bar;
        bar.setBackground(round(BTN, 2));
        oilBar = new View(this);
        bar.addView(oilBar, new FrameLayout.LayoutParams(0, -1));
        col.addView(bar, new LinearLayout.LayoutParams(-1, (int) (4 * s)));
        place(box, col, 176, 44, 88, 58);
        col.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { oilMenu(); } });
        updateOilBadge();
    }

    private void updateOilBadge() {
        startOdo();
        if (topOilTv != null) {
            float l = oilNextKm() - odoKm();
            float iv = Math.max(1, prefs.getFloat("oil_interval_km", 5000));
            if (!oilOn()) { topOilTv.setText(I18n.t("Aceite: configurar")); topOilTv.setTextColor(MUTED); }
            else if (Math.round(toUnit(l)) <= 0) { topOilTv.setText(I18n.t("¡Cambiar aceite!")); topOilTv.setTextColor(RED); }
            else { topOilTv.setText(I18n.t("Aceite: " + fmtDist(l))); topOilTv.setTextColor(l < iv * 0.1f ? RED : TEXT); }
        }
        if (oilValTv == null) return;
        if (!oilOn()) {
            oilValTv.setText(I18n.t("Configurar"));
            oilValTv.setTextColor(MUTED);
            oilBarBg.setVisibility(View.INVISIBLE);
            return;
        }
        float left = oilNextKm() - odoKm();
        float interval = Math.max(1, prefs.getFloat("oil_interval_km", 5000));
        oilBarBg.setVisibility(View.VISIBLE);
        if (Math.round(toUnit(left)) <= 0) {          // también si falta menos de medio km (no mostrar «0 km»)
            oilValTv.setText(I18n.t("¡Cambiar!"));
            oilValTv.setTextColor(RED);
        } else {
            oilValTv.setText(fmtDist(left));
            oilValTv.setTextColor(left < interval * 0.1f ? RED : TEXT);
        }
        final float used = Math.max(0, Math.min(1, 1 - left / interval));
        oilBarBg.post(new Runnable() { public void run() {
            ViewGroup.LayoutParams lp = oilBar.getLayoutParams();
            lp.width = Math.max((int) (2 * s), (int) (oilBarBg.getWidth() * used));
            oilBar.setLayoutParams(lp);
            oilBar.setBackground(round(used >= 0.9f ? RED : 0xFF2DBE6C, 2));
        }});
    }

    /** Toca el recuadro de aceite (o Ajustes → Tu vehículo). */
    private void oilMenu() {
        if (!oilOn()) { oilSetup(); return; }
        float left = oilNextKm() - odoKm();
        String head = I18n.t("Kilometraje estimado") + ": ≈ " + fmtDist(odoKm()) + "\n"
            + I18n.t("Último cambio") + ": " + fmtDist(prefs.getFloat("oil_last_km", 0)) + "\n"
            + I18n.t("Próximo cambio") + ": " + fmtDist(oilNextKm())
            + "\n" + (Math.round(toUnit(left)) > 0 ? I18n.t("Faltan " + fmtDist(left)) : I18n.t("¡Ya toca!"));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        int hp = (int) (22 * s);
        ht.setPadding(hp, hp, hp, (int) (6 * s));
        ht.addView(tv("Cambio de aceite", 19, TEXT, true));
        TextView hs = tv(head, 14, MUTED, false);
        hs.setSingleLine(false);
        hs.setMaxLines(5);
        hs.setLineSpacing(3 * s, 1f);
        hs.setPadding(0, (int) (6 * s), 0, 0);
        ht.addView(hs);
        new TB(this, dlg())
            .setCustomTitle(ht)
            .setItems(new String[]{"Ya hice el cambio de aceite", "Corregir el kilometraje actual", "Corregir el último cambio", "Cambiar cada cuánto se hace", "Desactivar el recordatorio"},
                new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    if (w == 0) oilDone();
                    else if (w == 1) askOdo(null);
                    else if (w == 2) askLast(null);
                    else if (w == 3) askInterval(null);
                    else { prefs.edit().putBoolean("oil_on", false).apply(); updateOilBadge(); if (settingsView != null) showSettings(1); }
                }})
            .setNegativeButton("Cerrar", null)
            .show();
    }

    /** Primera vez: kilometraje actual, cada cuánto y cuándo fue el último cambio. */
    private void oilSetup() {
        new TB(this, dlg())
            .setTitle("Recordatorio de cambio de aceite")
            .setMessage("StradaOS no puede leer el kilometraje del tablero del carro. Escríbelo una vez y StradaOS le irá sumando lo que recorras con el GPS, para avisarte cuando toque el cambio.\n\nDe vez en cuando puedes corregirlo con el valor real del tablero.")
            .setPositiveButton("Empezar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                askOilUnit(new Runnable() { public void run() {
                    askOdo(new Runnable() { public void run() {
                        askInterval(new Runnable() { public void run() {
                            askLast(new Runnable() { public void run() {
                                prefs.edit().putBoolean("oil_on", true).apply();
                                updateOilBadge();
                                toast(I18n.t("Próximo cambio") + ": " + fmtDist(oilNextKm()));
                                if (settingsView != null) showSettings(1);
                            }});
                        }});
                    }});
                }});
            }})
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /**
     * Unidad del tablero. En automático se decide por el idioma del sistema y casi todas las pantallas chinas vienen en
     * inglés de EE. UU. (= millas): por eso, antes de pedir números, se pregunta una vez y queda fija (issue #7).
     */
    private void askOilUnit(final Runnable next) {
        if (!"auto".equals(prefs.getString("speed_unit", "auto"))) { next.run(); return; }
        new TB(this, dlg())
            .setTitle("¿Tu tablero marca kilómetros o millas?")
            .setItems(new String[]{"Kilómetros (km)", "Millas (mi)"}, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                prefs.edit().putString("speed_unit", w == 1 ? "mph" : "kmh").apply();
                next.run();
            }})
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Kilometraje del último cambio de aceite, sin tocar el kilometraje actual. Avisa si queda por encima del actual. */
    private void askLast(final Runnable next) {
        float def = prefs.contains("oil_last_km") ? toUnit(prefs.getFloat("oil_last_km", 0)) : toUnit(odoKm());
        askNumber(I18n.t("¿A cuántos " + unit() + " fue el último cambio de aceite?"), def, new NumCb() { public void on(float v) {
            saveLast(fromUnit(v), next);
        }});
    }

    private void saveLast(final float km, final Runnable next) {
        final Runnable save = new Runnable() { public void run() {
            prefs.edit().putFloat("oil_last_km", km).putFloat("oil_snooze_km", 0).apply();
            updateOilBadge();
            if (next != null) next.run();
            else {
                toast(I18n.t("Próximo cambio") + ": " + fmtDist(oilNextKm()));
                if (settingsView != null) showSettings(1);
            }
        }};
        // Más de 100 km por encima del kilometraje actual: casi seguro es un error de unidad o de dígitos.
        if (prefs.contains("odo_base_km") && km > odoKm() + 100) {
            new TB(Main.this, dlg())
                .setTitle("Revisa el último cambio")
                .setMessage(I18n.t("El último cambio (" + fmtDist(km) + ") queda por encima del kilometraje actual (" + fmtDist(odoKm()) + "). ¿Lo escribiste en otra unidad?"))
                .setPositiveButton("Corregir", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { askLast(next); } })
                .setNegativeButton("Guardar igual", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { save.run(); } })
                .show();
        } else save.run();
    }

    private void askOdo(final Runnable next) {
        askNumber(I18n.t("Kilometraje actual del tablero") + " (" + unit() + ")", prefs.contains("odo_base_km") ? toUnit(odoKm()) : -1, new NumCb() { public void on(float v) {
            prefs.edit().putFloat("odo_base_km", fromUnit(v)).putFloat("odo_gps_m", 0).apply();
            odoPending = 0;
            updateOilBadge();
            if (next != null) next.run(); else if (settingsView != null) showSettings(1);
        }});
    }

    private void askInterval(final Runnable next) {
        float def = prefs.contains("oil_interval_km") ? toUnit(prefs.getFloat("oil_interval_km", 5000)) : (useMph() ? 3000 : 5000);
        askNumber(I18n.t("¿Cada cuántos " + unit() + " cambias el aceite?"), def, new NumCb() { public void on(float v) {
            if (v < 100) { toast("Escribe un número válido"); return; }
            prefs.edit().putFloat("oil_interval_km", fromUnit(v)).putFloat("oil_snooze_km", 0).apply();
            updateOilBadge();
            if (next != null) next.run(); else if (settingsView != null) showSettings(1);
        }});
    }

    /** «Ya lo hice»: confirma el kilometraje real y reinicia el conteo. */
    private void oilDone() {
        askNumber(I18n.t("Kilometraje del tablero al hacer el cambio") + " (" + unit() + ")", toUnit(odoKm()), new NumCb() { public void on(float v) {
            float km = fromUnit(v);
            prefs.edit().putFloat("odo_base_km", km).putFloat("odo_gps_m", 0).putFloat("oil_last_km", km).putFloat("oil_snooze_km", 0).apply();
            odoPending = 0;
            updateOilBadge();
            toast(I18n.t("Próximo cambio") + ": " + fmtDist(oilNextKm()));
            if (settingsView != null) showSettings(1);
        }});
    }

    /** Aviso en el inicio cuando toca el cambio. «Aún no» lo vuelve a mostrar tras 100 km (60 mi). */
    private android.app.Dialog oilDlg;
    private static boolean oilLater;   // cerró el aviso sin elegir: se vuelve a mostrar la próxima vez que abra StradaOS

    private boolean oilAlert() {
        if (oilLater || (oilDlg != null && oilDlg.isShowing())) return false;
        if (!oilOn() || !prefs.contains("odo_base_km")) return false;
        float now = odoKm();
        if (now < oilNextKm() || now < prefs.getFloat("oil_snooze_km", 0)) return false;
        oilDlg = new TB(this, dlg())
            .setTitle("Es hora del cambio de aceite")
            .setMessage(I18n.t("El kilometraje estimado es ≈ " + fmtDist(now) + " y ya toca el cambio de aceite (cada " + fmtDist(prefs.getFloat("oil_interval_km", 5000)) + ").")
                + "\n\n" + I18n.t("¿Ya lo hiciste?"))
            .setPositiveButton("Ya lo hice", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { oilDone(); } })
            .setNegativeButton("Aún no", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                prefs.edit().putFloat("oil_snooze_km", odoKm() + (useMph() ? 60 / 0.621371f : 100)).apply();
            }})
            .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface d) { oilLater = true; } })
            .show();
        return true;
    }

    interface NumCb { void on(float v); }

    /**
     * Campo de texto para las ventanas. La actividad usa un tema oscuro y las ventanas siguen el tema de StradaOS:
     * en modo claro el texto y la pista salían blancos sobre fondo blanco (visto en la 2.13 de prueba).
     */
    private android.widget.EditText field() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setTextColor(LIGHT ? 0xFF1A1A1A : Color.WHITE);
        et.setHintTextColor(LIGHT ? 0xFF7A808A : 0xFFA0A6B0);
        return et;
    }

    private void askNumber(String title, float def, final NumCb cb) {
        final android.widget.EditText et = field();
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (def >= 0) { et.setText(String.valueOf(Math.round(def))); et.setSelection(et.getText().length()); }
        FrameLayout wrap = new FrameLayout(this);
        int pad = (int) (20 * s);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(et);
        new TB(this, dlg())
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                try {
                    float v = Float.parseFloat(et.getText().toString().replaceAll("[^0-9]", ""));
                    if (v < 0 || v > 5000000) throw new NumberFormatException();
                    cb.on(v);
                } catch (Throwable e) { toast("Escribe un número válido"); }
            }})
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void buildAaCard() {
        FrameLayout card = new FrameLayout(this);
        place(board, card, mapX, 64, mapW, cardH);
        // fondo decorativo mientras no hay GPS; encima, el mapa en vivo
        card.addView(new MapView(this, s, 20 * s), new FrameLayout.LayoutParams(-1, -1));
        if (!prefs.getBoolean("map_live", true)) { liveMap = null; seedLastLocation(); buildAaCardRest(card); return; }
        liveMap = new LiveMapView(this, s, 20 * s);
        card.addView(liveMap, new FrameLayout.LayoutParams(-1, -1));
        liveMap.setVisibility(View.INVISIBLE);
        liveMap.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { liveMap.cycleZoom(); }
        });
        liveMap.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                showMapDiag();
                return true;
            }
        });
        seedLastLocation();

        buildAaCardRest(card);
    }

    private void buildAaCardRest(FrameLayout card) {
        LinearLayout tag = new LinearLayout(this);
        tag.setGravity(Gravity.CENTER_VERTICAL);
        tag.setBackground(round((CARD & 0x00FFFFFF) | 0xE0000000, 12));
        tag.setPadding((int) (12 * s), (int) (7 * s), (int) (12 * s), (int) (7 * s));
        tag.addView(icon("my_location", 16), new LinearLayout.LayoutParams((int) (16 * s), (int) (16 * s)));
        mapTagTv = tv("Buscando GPS…", 13, TEXT, true);
        mapTagTv.setPadding((int) (8 * s), 0, 0, 0);
        tag.addView(mapTagTv);
        placeWrap(card, tag, 16, 16);
        tag.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { fixGps(); }
        });

        final float panelY = cardH - 96, panelW = mapW - 24;
        final FrameLayout panel = new FrameLayout(this);
        panel.setBackground(round((CARD & 0x00FFFFFF) | 0xEB000000, 16));
        place(card, panel, 12, panelY, panelW, 84);

        final View navBtn = circleIcon("map", 52, 26, (CARD & 0x00FFFFFF) | 0xEB000000);
        place(card, navBtn, mapW - 64, panelY - 64, 52, 52);
        navBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openNavApp(); }
        });
        navBtn.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { pickFor("nav_app", "Navegación"); return true; }
        });

        float btnW = (panelW - 28) / 2;
        View aa = projButton("navigation", "Android Auto", RED, true);
        aaBtnTv = (TextView) aa.getTag();
        place(panel, aa, 10, 10, btnW, 64);
        View cp = projButton("phone_iphone", "CarPlay", BTN, false);
        cpSubTv = (TextView) cp.getTag();
        place(panel, cp, 18 + btnW, 10, btnW, 64);
        aaSubTv = aaBtnTv;

        // Android Auto / CarPlay plegables: una flechita los esconde para ver todo el mapa (Druids07, idea de Andrés).
        // Tocar o deslizar la flechita hacia arriba los muestra; hacia abajo (o deslizar los botones) los esconde.
        final FrameLayout handle = new FrameLayout(this);
        handle.setBackground(round((CARD & 0x00FFFFFF) | 0xEB000000, 11));
        final ImageView chev = icon("chevron_right", 18);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams((int) (18 * s), (int) (18 * s));
        clp.gravity = Gravity.CENTER;
        handle.addView(chev, clp);
        place(card, handle, (mapW - 64) / 2, panelY - 28, 64, 24);
        final float shift = 96 * s;
        final View[] moving = {panel, navBtn, handle};
        final boolean open0 = prefs.getBoolean("aa_panel_open", true);
        for (View v : moving) v.setTranslationY(open0 ? 0 : shift);
        chev.setRotation(open0 ? 90 : -90);
        final Runnable[] setOpen = new Runnable[2];   // [0] mostrar, [1] esconder
        for (int k = 0; k < 2; k++) {
            final boolean show = k == 0;
            setOpen[k] = new Runnable() { public void run() {
                prefs.edit().putBoolean("aa_panel_open", show).apply();
                for (View v : moving) v.animate().translationY(show ? 0 : shift).setDuration(220);
                chev.animate().rotation(show ? 90 : -90).setDuration(220);
            }};
        }
        handle.setOnTouchListener(new View.OnTouchListener() {
            float y0;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { y0 = e.getRawY(); return true; }
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
                float d = e.getRawY() - y0;
                boolean isOpen = prefs.getBoolean("aa_panel_open", true);
                if (d < -12 * s) setOpen[0].run();
                else if (d > 12 * s) setOpen[1].run();
                else setOpen[isOpen ? 1 : 0].run();
                return true;
            }
        });
        View.OnTouchListener swipeDown = new View.OnTouchListener() {
            float x0, y0;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getRawX(); y0 = e.getRawY(); return false; }
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return false;
                float dx = e.getRawX() - x0, d = e.getRawY() - y0;
                if (d < 30 * s || d < 2 * Math.abs(dx)) return false;
                MotionEvent c = MotionEvent.obtain(e);
                c.setAction(MotionEvent.ACTION_CANCEL);
                v.onTouchEvent(c);   // que no abra Android Auto/CarPlay
                c.recycle();
                setOpen[1].run();
                return true;
            }
        };
        aa.setOnTouchListener(swipeDown);
        cp.setOnTouchListener(swipeDown);

        aa.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openAndroidAuto(); }
        });
        aa.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { chooseConnection(null); return true; }
        });
        cp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openCarPlay(); }
        });
        cp.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { pickFor("carplay", "CarPlay"); return true; }
        });
        updateMapTag();
    }

    /** Botón de proyección: ícono + título + subtítulo (el subtítulo se guarda en getTag()). */
    private View projButton(String ic, String title, int color, boolean filled) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(round(color, 14));
        b.setPadding((int) (12 * s), 0, (int) (8 * s), 0);
        ImageView pic = icon(ic, 26);
        if (filled) pic.clearColorFilter();
        b.addView(pic, new LinearLayout.LayoutParams((int) (26 * s), (int) (26 * s)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (10 * s), 0, 0, 0);
        col.addView(tv(title, 15, filled ? Color.WHITE : TEXT, true));
        TextView sub = tv("", 11, filled ? 0xD9FFFFFF : MUTED, false);
        sub.setPadding(0, (int) (3 * s), 0, 0);
        col.addView(sub);
        b.addView(col, new LinearLayout.LayoutParams(-1, -2));
        b.setTag(sub);
        return b;
    }

    // ── mapa en vivo ──

    private void seedLastLocation() {
        Location best = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "passive"}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Throwable ignored) { }
        }
        if (best != null) feedMap(best);
    }

    private void feedMap(Location l) {
        if (l != null) { wLat = l.getLatitude(); wLon = l.getLongitude(); }
        if (liveMap == null || l == null) return;
        float kmh = l.hasSpeed() ? l.getSpeed() * 3.6f : 0;
        liveMap.setPosition(l.getLatitude(), l.getLongitude(), l.hasBearing() && kmh > 4 ? l.getBearing() : Float.NaN, kmh);
        liveMap.setVisibility(View.VISIBLE);
        updateMapTag();
    }

    /** km/h o mph: lo elegido, o mph en EE. UU., Reino Unido, Liberia y Birmania. */
    private boolean useMph() {
        String u = prefs.getString("speed_unit", "auto");
        if ("mph".equals(u)) return true;
        if ("kmh".equals(u)) return false;
        String c = java.util.Locale.getDefault().getCountry();
        return "US".equals(c) || "GB".equals(c) || "LR".equals(c) || "MM".equals(c);
    }

    /** 0 = bien (o buscando señal), 1 = StradaOS sin permiso de ubicación, 2 = ubicación del equipo apagada. */
    private int gpsState() {
        try {
            if (SDK >= 23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return 1;
        } catch (Throwable ignored) { }
        try {
            boolean gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean net = false;
            try { net = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Throwable ignored) { }
            if (!gps && !net) return 2;
        } catch (Throwable ignored) { }
        return 0;
    }

    /** Toca el letrero del mapa: pide el permiso o abre los ajustes de ubicación según la causa. */
    private void fixGps() {
        int st = gpsState();
        if (st == 1) {
            boolean asked = false;
            if (!prefs.getBoolean("loc_asked", false)) {
                try { asked = !Modern.ensurePermissions(this, 78); } catch (Throwable ignored) { }
            }
            if (!asked) {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    toast("Permisos → Ubicación → Permitir");
                } catch (Throwable e) { toast("Activa la ubicación para StradaOS en los ajustes de la pantalla"); }
            }
            prefs.edit().putBoolean("loc_asked", true).apply();
        } else if (st == 2) {
            try {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                toast("Activa la ubicación (GPS)");
            } catch (Throwable e) { toast("Activa la ubicación (GPS) en los ajustes de la pantalla"); }
        } else {
            showMapDiag();
        }
    }

    /** Diagnóstico del mapa con la causa del GPS arriba. */
    private String mapDiag() {
        int st = gpsState();
        boolean gpsOn = false, netOn = false;
        try { gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Throwable ignored) { }
        try { netOn = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Throwable ignored) { }
        boolean hasGps = false;
        try { hasGps = lm.getProvider(LocationManager.GPS_PROVIDER) != null; } catch (Throwable ignored) { }
        long ago = lastFixAny > 0 ? (SystemClock.elapsedRealtime() - lastFixAny) / 1000 : -1;
        String head = "Permiso de ubicación: " + (st == 1 ? "NO (toca el letrero del mapa)" : "sí")
            + "\nGPS del equipo: " + (!hasGps ? "no encontrado" : gpsOn ? "encendido" : "apagado")
            + " · ubicación por red: " + (netOn ? "encendida" : "apagada")
            + "\nÚltima señal: " + (ago < 0 ? "ninguna desde que abrió StradaOS" : ago < 120 ? "hace " + ago + " s" : "hace " + (ago / 60) + " min")
            + (gpsStartAt > 0 ? "\nBuscando desde hace " + ((SystemClock.elapsedRealtime() - gpsStartAt) / 60000) + " min" : "");
        return head + "\n\n" + (liveMap != null ? liveMap.diag() : "Mapa en vivo apagado");
    }

    private void showMapDiag() {
        new TB(this, dlg()).setTitle("Diagnóstico del mapa").setMessage(mapDiag()).setPositiveButton("OK", null).show();
    }

    private void updateMapTag() {
        if (mapTagTv == null) return;
        boolean live = lastFix > 0;
        int st = gpsState();
        if (!prefs.getBoolean("map_live", true)) mapTagTv.setText(I18n.t("Mapa en vivo apagado"));
        else if (!live && st == 1) mapTagTv.setText(I18n.t("Sin permiso de ubicación · toca aquí"));
        else if (!live && st == 2) mapTagTv.setText(I18n.t("Ubicación apagada · toca aquí"));
        else if (liveMap == null || !liveMap.hasFix())
            mapTagTv.setText(I18n.t(gpsStartAt > 0 && SystemClock.elapsedRealtime() - gpsStartAt > 180000 ? "Sin señal GPS · toca aquí" : "Buscando GPS…"));
        else if (liveMap.usingFile()) mapTagTv.setText(I18n.t(live ? "Mapa sin conexión" : "Sin señal GPS · mapa sin conexión"));
        else if (liveMap.offline()) mapTagTv.setText(I18n.t(live ? "En vivo · sin internet" : "Sin señal GPS ni internet"));
        else mapTagTv.setText(I18n.t(live ? "Mapa en vivo" : "Sin señal GPS · última posición"));
    }

    private void buildAudioCard() {
        final FrameLayout card = card(audX, 64, audW, cardH, CARD);
        // contenido centrado: la tarjeta es más ancha sin la del vehículo y más alta con la barra compacta
        final float dx = (audW - 248) / 2, dy = (cardH - 380) / 2;
        placeWrap(card, tv("AUDIO", 11, MUTED, true), 18, 14);
        sourceTv = tv("", 11, RED, true);
        sourceTv.setGravity(Gravity.RIGHT);
        sourceTv.setSingleLine(true);
        sourceTv.setPadding(0, 0, (int) (4 * s), 0);   // la última letra en negrita se recortaba en Android 11
        place(card, sourceTv, 70, 14, audW - 88, 18);   // ancho de sobra: en Android 11 «BLUETOOTH» salía cortado
        playerArt = new PlayerArt(this, s, asset("ic/music_note.png"));
        // Tarjeta alta (barra lateral): diseño vertical que llena el alto en vez de dejar espacio vacío (Andrés, Picanto)
        tallPlayer = cardH - 380 >= 60;
        final View prev, play, next;
        if (tallPlayer) {
            boolean big = audW >= 290;
            float bs = big ? 66 : 60, ps = big ? 86 : 76, gap = big ? 12 : 10;
            float yb = cardH - 22 - ps;                 // botones abajo
            float yt = yb - 18 - 18;                    // tiempo (el avance lo muestra el anillo de la carátula)
            float ya = yt - 6 - 20;                     // artista
            float yti = ya - 28;                        // canción
            float area = yti - 10 - 38;
            float A = Math.min(audW - 56, area);
            place(card, playerArt, (audW - A) / 2, 38 + (area - A) / 2, A, A);
            trackTv = tv("Nada sonando", 19, TEXT, true);
            trackTv.setGravity(Gravity.CENTER_HORIZONTAL);
            trackTv.setSingleLine(true);
            trackTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            place(card, trackTv, 18, yti, audW - 36, 26);
            artistTv = tv("Radio, Bluetooth o USB", 14, MUTED, false);
            artistTv.setGravity(Gravity.CENTER_HORIZONTAL);
            artistTv.setSingleLine(true);
            artistTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            place(card, artistTv, 18, ya, audW - 36, 20);
            timePosTv = tv("", 13, MUTED, false);
            timePosTv.setGravity(Gravity.CENTER_HORIZONTAL);
            place(card, timePosTv, 18, yt, audW - 36, 18);
            prev = circleIcon("skip_previous", bs, bs / 2, BTN);
            play = circleIcon("play_arrow", ps, ps * 0.47f, RED);
            next = circleIcon("skip_next", bs, bs / 2, BTN);
            float x0 = (audW - (bs * 2 + ps + gap * 2)) / 2;
            place(card, prev, x0, yb + (ps - bs) / 2, bs, bs);
            place(card, play, x0 + bs + gap, yb, ps, ps);
            place(card, next, x0 + bs + gap + ps + gap, yb + (ps - bs) / 2, bs, bs);
        } else {
        place(card, playerArt, 34 + dx, 34 + dy, 180, 180);
        trackTv = tv("Nada sonando", 17, TEXT, true);
        trackTv.setGravity(Gravity.CENTER_HORIZONTAL);
        place(card, trackTv, 14, 222 + dy, audW - 28, 24);
        artistTv = tv("Radio, Bluetooth o USB", 13, MUTED, false);
        artistTv.setGravity(Gravity.CENTER_HORIZONTAL);
        place(card, artistTv, 14, 250 + dy, audW - 28, 20);
        timePosTv = tv("", 12, MUTED, false);
        timePosTv.setGravity(Gravity.CENTER_HORIZONTAL);
        place(card, timePosTv, 14, 274 + dy, audW - 28, 18);
        // botones más grandes (antes 52/64): se tocan mejor con el carro en marcha
        prev = circleIcon("skip_previous", 60, 30, BTN);
        play = circleIcon("play_arrow", 74, 36, RED);
        next = circleIcon("skip_next", 60, 30, BTN);
        place(card, prev, 20 + dx, 300 + dy, 60, 60);
        place(card, play, 87 + dx, 293 + dy, 74, 74);
        place(card, next, 168 + dx, 300 + dy, 60, 60);
        }
        playIc = (ImageView) ((FrameLayout) play).getChildAt(0);
        final View.OnClickListener goPrev = mediaClick(KeyEvent.KEYCODE_MEDIA_PREVIOUS), goNext = mediaClick(KeyEvent.KEYCODE_MEDIA_NEXT);
        prev.setOnClickListener(goPrev);
        play.setOnClickListener(mediaClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        next.setOnClickListener(goNext);
        // deslizar sobre la tarjeta: ← siguiente canción, → anterior (como pasar páginas)
        card.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getX(); y0 = e.getY(); return false; }
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return false;
                float dx = e.getX() - x0, dy = e.getY() - y0;
                if (Math.abs(dx) < 60 * s || Math.abs(dx) < 2 * Math.abs(dy)) return false;
                MotionEvent c = MotionEvent.obtain(e);
                c.setAction(MotionEvent.ACTION_CANCEL);
                v.onTouchEvent(c);   // que no cuente como toque (abrir la app) ni como toque largo
                c.recycle();
                final boolean nextSong = dx < 0;
                (nextSong ? goNext : goPrev).onClick(v);
                playerArt.animate().translationX((nextSong ? -24 : 24) * s).setDuration(120).withEndAction(new Runnable() {
                    public void run() { playerArt.animate().translationX(0).setDuration(160); }
                });
                return true;
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { showPlayerDiag(); return true; }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openMusicApp(); }
        });
        renderTrack();
    }

    private void buildWebCard() {
        FrameLayout card = card(760 + ex, 260, 248, 184, CARD);
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(round(RED_DARK, 16));
        ImageView g = icon("public", 30);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (30 * s), (int) (30 * s));
        lp.gravity = Gravity.CENTER;
        ic.addView(g, lp);
        place(card, ic, 18, 20, 56, 56);
        placeWrap(card, tv("Navegador", 18, TEXT, true), 18, 94);
        placeWrap(card, tv("Internet desde la pantalla", 12, MUTED, false), 18, 122);
        TextView arrow = tv("›", 26, RED, false);
        placeWrap(card, arrow, 214, 130);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openBrowser(); }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { pickFor("web", "Navegador"); return true; }
        });
    }

    // ───────────────────────── barra inferior personalizable ─────────────────────────
    // Cada botón guarda en "dock_N" una app ("app:paquete/Actividad") o una función ("fn:id"), y en "dock_N_name"
    // un nombre propio opcional. Sin nada guardado, cada botón hace lo de siempre (DOCK_DEFAULT).

    static final String[] DOCK_DEFAULT = {"radio", "bt", "cam", "web", "files", "settings", "apps"};
    /** Funciones que puede tener un botón: id, nombre, ícono. */
    static final String[][] DOCK_FNS = {
        {"radio", "Radio", "radio"}, {"bt", "Bluetooth", "bluetooth"}, {"cam", "Cámara", "photo_camera"},
        {"music", "Música", "music_note"}, {"nav", "Navegación", "map"}, {"aa", "Android Auto", "navigation"},
        {"carplay", "CarPlay", "phone_iphone"}, {"web", "Navegador web", "public"}, {"files", "Archivos", "folder"},
        {"settings", "Ajustes", "settings"}, {"apps", "Apps", "apps"},
    };

    private String[] dockFn(String id) {
        for (String[] f : DOCK_FNS) if (f[0].equals(id)) return f;
        return DOCK_FNS[DOCK_FNS.length - 1];
    }

    /** Lo que hace el botón N: "app:…" o "fn:…". Si la app elegida ya no está instalada, vuelve a lo de siempre. */
    private String dockTarget(int i) {
        String t = prefs.getString("dock_" + i, null);
        if (t != null && t.startsWith("app:")) {
            ComponentName cn = ComponentName.unflattenFromString(t.substring(4));
            if (cn != null && installed(cn.getPackageName())) return t;
            t = null;
        }
        return t != null ? t : "fn:" + DOCK_DEFAULT[i];
    }

    private String dockName(int i) {
        String custom = prefs.getString("dock_" + i + "_name", "").trim();
        if (custom.length() > 0) return custom;
        String t = dockTarget(i);
        if (t.startsWith("app:")) return labelOf(t.substring(4));
        String id = t.substring(3);
        return "web".equals(id) ? "Navegador" : dockFn(id)[1];
    }

    private Drawable appIcon(String component) {
        try {
            return getPackageManager().getActivityIcon(ComponentName.unflattenFromString(component));
        } catch (Throwable e) {
            try { return getPackageManager().getApplicationIcon(ComponentName.unflattenFromString(component).getPackageName()); }
            catch (Throwable e2) { return null; }
        }
    }

    private void buildDock() {
        // tamaños según la barra: normal (como siempre), compacta (menos alta) o lateral (botones al costado)
        final float cs = sideDock ? 40 : compactDock ? 52 : 64;          // círculo
        final float lblSize = sideDock ? 10 : compactDock ? 12 : 13, lblPad = sideDock ? 2 : compactDock ? 4 : 8;
        final float railX = playerLeft ? 16 : 1016 + ex, step = (boardH - 24) / 7f;
        if (sideDock) {
            View rail = new View(this);
            rail.setBackground(round(DOCK, 20));
            place(board, rail, railX, 12, RAIL, boardH - 24);
        }
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            String t = dockTarget(i);
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            Drawable appIc = t.startsWith("app:") ? appIcon(t.substring(4)) : null;
            if (appIc != null) {
                FrameLayout f = new FrameLayout(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(CIRCLE);
                f.setBackground(g);
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(appIc);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (cs * 0.69f * s), (int) (cs * 0.69f * s));
                lp.gravity = Gravity.CENTER;
                f.addView(iv, lp);
                b.addView(f, new LinearLayout.LayoutParams((int) (cs * s), (int) (cs * s)));
            } else {
                String id = t.startsWith("fn:") ? t.substring(3) : DOCK_DEFAULT[i];
                b.addView(circleIcon(dockFn(id)[2], cs, cs * 0.44f, CIRCLE), new LinearLayout.LayoutParams((int) (cs * s), (int) (cs * s)));
            }
            TextView l = tv(dockName(i), lblSize, SOFT, false);
            l.setGravity(Gravity.CENTER_HORIZONTAL);
            l.setSingleLine(true);
            l.setEllipsize(android.text.TextUtils.TruncateAt.END);
            l.setPadding(0, (int) (lblPad * s), 0, 0);
            b.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (sideDock) { b.setGravity(Gravity.CENTER); place(board, b, railX, 12 + step * i, RAIL, step); }
            else if (compactDock) place(board, b, 38 + (138 + ex / 6) * i, dockTop + 9, 120, 86);
            else place(board, b, 38 + (138 + ex / 6) * i, 474, 120, 110);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { runDock(idx); }
            });
            b.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { dockMenu(idx, null); return true; }
            });
        }
    }

    private void runDock(int i) {
        String t = dockTarget(i);
        if (t.startsWith("app:")) {
            try {
                Intent it = new Intent(Intent.ACTION_MAIN);
                it.addCategory(Intent.CATEGORY_LAUNCHER);
                it.setComponent(ComponentName.unflattenFromString(t.substring(4)));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(it);
            } catch (Throwable e) {
                toast("No se pudo abrir «" + dockName(i) + "». Déjalo presionado para elegir otra app.");
            }
            return;
        }
        runFn(t.substring(3));
    }

    private void runFn(String id) {
        if ("radio".equals(id)) openSlot(0);
        else if ("bt".equals(id)) openSlot(1);
        else if ("cam".equals(id)) openSlot(2);
        else if ("files".equals(id)) openSlot(3);
        else if ("settings".equals(id)) openSlot(4);
        else if ("web".equals(id)) openBrowser();
        else if ("music".equals(id)) openMusicApp();
        else if ("nav".equals(id)) openNavApp();
        else if ("aa".equals(id)) openAndroidAuto();
        else if ("carplay".equals(id)) openCarPlay();
        else openDrawer();
    }

    /** Menú del botón N: cambiar app o función, cambiar nombre, volver a lo de siempre. */
    private void dockMenu(final int i, final Runnable after) {
        final String def = dockFn(DOCK_DEFAULT[i])[1];
        new TB(this, dlg())
            .setTitle("Botón " + (i + 1) + " · " + dockName(i))
            .setItems(new String[]{"Cambiar app o función", "Cambiar el nombre", "Restablecer (" + ("web".equals(DOCK_DEFAULT[i]) ? "Navegador" : def) + ")"},
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) pickDock(i, after);
                        else if (w == 1) renameDock(i, after);
                        else {
                            prefs.edit().remove("dock_" + i).remove("dock_" + i + "_name").apply();
                            toast("Botón restablecido");
                            dockChanged(after);
                        }
                    }
                })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private void dockChanged(Runnable after) {
        rebuild();
        if (after != null) after.run();
    }

    private void pickDock(final int i, final Runnable after) {
        final TargetCb save = new TargetCb() { public void on(String target) {
            prefs.edit().putString("dock_" + i, target).remove("dock_" + i + "_name").apply();
            toast("Guardado: " + dockName(i));
            dockChanged(after);
        }};
        final List<String[]> extra = new ArrayList<String[]>();
        extra.add(new String[]{"fns", "★  Funciones de StradaOS (Música, Navegación, Android Auto…)", "apps"});
        pickApp("¿Qué abre el botón " + (i + 1) + "?", extra, null, true, new TargetCb() { public void on(String target) {
            if (!"fns".equals(target)) { save.on(target); return; }
            List<String[]> fns = new ArrayList<String[]>();
            for (String[] f : DOCK_FNS) fns.add(new String[]{"fn:" + f[0], f[1], f[2]});
            pickApp("Función del botón " + (i + 1), fns, null, false, save);
        }});
    }

    private void renameDock(final int i, final Runnable after) {
        final android.widget.EditText et = field();
        et.setSingleLine(true);
        et.setText(dockName(i));
        et.setSelection(et.getText().length());
        FrameLayout wrap = new FrameLayout(this);
        int pad = (int) (20 * s);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(et);
        new TB(this, dlg())
            .setTitle("Nombre del botón " + (i + 1) + " (vacío = el de la app)")
            .setView(wrap)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                prefs.edit().putString("dock_" + i + "_name", et.getText().toString().trim()).apply();
                dockChanged(after);
            }})
            .setNegativeButton("Cancelar", null)
            .show();
    }

    interface TargetCb { void on(String target); }

    /**
     * Lista para elegir una app, con su ícono. `extra`: opciones fijas arriba ({valor, texto, ícono}).
     * `pin`: paquetes que van primero (p. ej. apps de navegación). Devuelve "app:paquete/Actividad" o el valor extra.
     */
    private void pickApp(String title, final List<String[]> extra, String[] pin, boolean withApps, final TargetCb cb) {
        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> list = new ArrayList<ResolveInfo>();
        if (withApps) {
            if (pin != null) for (String p : pin) for (ResolveInfo r : apps) if (r.activityInfo.packageName.equals(p)) list.add(r);
            for (ResolveInfo r : apps) if (!list.contains(r)) list.add(r);
        }
        final int nx = extra == null ? 0 : extra.size();
        final HashMap<Integer, Drawable> icons = new HashMap<Integer, Drawable>();
        BaseAdapter ad = new BaseAdapter() {
            public int getCount() { return nx + list.size(); }
            public Object getItem(int pos) { return pos; }
            public long getItemId(int pos) { return pos; }
            public View getView(int pos, View cv, ViewGroup parent) {
                LinearLayout row = new LinearLayout(Main.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding((int) (20 * s), (int) (10 * s), (int) (20 * s), (int) (10 * s));
                TextView t;
                if (pos < nx) {
                    String[] e = extra.get(pos);
                    View ic = e[2] != null ? icon(e[2], 28) : new View(Main.this);
                    row.addView(ic, new LinearLayout.LayoutParams((int) (36 * s), (int) (28 * s)));
                    t = tv(e[1], 17, LIGHT ? 0xFF1A1A1A : Color.WHITE, true);
                } else {
                    ResolveInfo r = list.get(pos - nx);
                    Drawable d = icons.get(pos);
                    if (d == null) { try { d = r.loadIcon(pm); } catch (Throwable e) { d = null; } if (d != null) icons.put(pos, d); }
                    ImageView iv = new ImageView(Main.this);
                    iv.setImageDrawable(d);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    row.addView(iv, new LinearLayout.LayoutParams((int) (36 * s), (int) (36 * s)));
                    t = tv(String.valueOf(r.loadLabel(pm)), 17, LIGHT ? 0xFF1A1A1A : Color.WHITE, false);
                }
                t.setPadding((int) (16 * s), 0, 0, 0);
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                return row;
            }
        };
        new TB(this, dlg())
            .setTitle(title)
            .setAdapter(ad, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    if (which < nx) { cb.on(extra.get(which)[0]); return; }
                    ResolveInfo r = list.get(which - nx);
                    cb.on("app:" + new ComponentName(r.activityInfo.packageName, r.activityInfo.name).flattenToString());
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    // ── apps preferidas ──

    static final String[] MUSIC_PKGS = {"com.spotify.music", "in.krosbits.musicolet", "com.google.android.apps.youtube.music",
        "com.maxrave.simpmusic", "com.maxmpz.audioplayer", "org.videolan.vlc", "deezer.android.app", "com.amazon.mp3",
        "com.apple.android.music", "com.soundcloud.android", "com.google.android.youtube", "com.aspiro.tidal", "com.zionhuang.music",
        "it.vfsfitvnm.vimusic", "app.vitune.android", "com.kapp.youtube.final"};
    static final String[] NAV_PKGS = {"com.waze", "com.google.android.apps.maps", "com.google.android.apps.mapslite", "com.sygic.aura",
        "net.osmand", "net.osmand.plus", "app.organicmaps", "com.mapswithme.maps.pro", "com.here.app.maps", "ru.yandex.yandexnavi",
        "com.huawei.maps.app", "cz.seznam.mapy", "com.tomtom.gplay.navapp", "com.navitel", "com.magicearth.app"};

    private String firstInstalled(String[] pkgs) {
        for (String p : pkgs) if (getPackageManager().getLaunchIntentForPackage(p) != null) return p;
        return null;
    }

    /** Toca el reproductor: la app de música elegida; si no hay, la de la fuente que suena. */
    private void openMusicApp() {
        if (launchSaved("music_app")) return;
        if ("Android Auto".equals(curSource) && launchPkg(PKG_HUR)) return;
        if ("Bluetooth".equals(curSource)) { ComponentName cn = btMusicApp(); if (cn != null && launchComponent(cn)) return; }
        String p = firstInstalled(MUSIC_PKGS);
        if (p != null && launchPkg(p)) return;
        toast("Elige tu app de música");
        pickFor("music_app", "Música", null);
    }

    /** App de navegación elegida; si no hay, la primera conocida que esté instalada (Waze, Maps, OsmAnd…). */
    private void openNavApp() {
        if (launchSaved("nav_app")) return;
        String p = firstInstalled(NAV_PKGS);
        if (p != null && launchPkg(p)) return;
        toast("Elige tu app de navegación");
        pickFor("nav_app", "Navegación", null);
    }

    private boolean launchComponent(ComponentName cn) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
            return true;
        } catch (Throwable e) { return false; }
    }

    private String autoLabel(String key, String[] pkgs, String none) {
        String c = prefs.getString(key, null);
        if (c != null) return labelOf(c);
        String p = firstInstalled(pkgs);
        return p != null ? "Automático · " + labelOf(p) : none;
    }

    // ───────────────────────── helpers de UI ─────────────────────────

    private TextView tv(String t, float size, int color, boolean medium) {
        TextView v = new TextView(this);
        v.setText(I18n.t(t));
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return v;
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(r * s);
        return d;
    }

    private void place(ViewGroup parent, View v, float x, float y, float w, float hh) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (w * s), (int) (hh * s));
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = (int) (x * s);
        lp.topMargin = (int) (y * s);
        parent.addView(v, lp);
    }

    private void placeWrap(ViewGroup parent, View v, float x, float y) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = (int) (x * s);
        lp.topMargin = (int) (y * s);
        parent.addView(v, lp);
    }

    private FrameLayout card(float x, float y, float w, float hh, int color) {
        FrameLayout c = new FrameLayout(this);
        c.setBackground(round(color, 20));
        place(board, c, x, y, w, hh);
        return c;
    }

    private ImageView icon(String name, float size) {
        ImageView v = new ImageView(this);
        v.setImageBitmap(asset("ic/" + name + ".png"));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (ICON != 0xFFFFFFFF) v.setColorFilter(ICON);
        return v;
    }

    private FrameLayout circleIcon(String name, float d, float ic, int color) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        f.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (ic * s), (int) (ic * s));
        lp.gravity = Gravity.CENTER;
        ImageView iv = icon(name, ic);
        if (color == RED) iv.clearColorFilter();
        f.addView(iv, lp);
        return f;
    }

    private Bitmap asset(String path) {
        Bitmap b = iconCache.get(path);
        if (b != null) return b;
        try {
            InputStream in = getAssets().open(path);
            b = BitmapFactory.decodeStream(in);
            in.close();
            iconCache.put(path, b);
        } catch (Exception e) {
            b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
        return b;
    }

    private void toast(String m) {
        Toast.makeText(this, I18n.t(m), Toast.LENGTH_LONG).show();
    }

    // ───────────────────────── reloj, GPS, audio ─────────────────────────

    private void updateClock() {
        if (timeTv == null) return;
        Date now = new Date();
        String fmt = prefs.getString("clock", "auto");
        boolean h24 = "24".equals(fmt) || ("auto".equals(fmt) && android.text.format.DateFormat.is24HourFormat(this));
        timeTv.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", I18n.loc).format(now));
        String d = new SimpleDateFormat(I18n.datePattern(), I18n.loc).format(now);
        dateTv.setText(d.substring(0, 1).toUpperCase(I18n.loc) + d.substring(1));
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String saludo = I18n.t(hr >= 5 && hr < 12 ? "Buenos días" : (hr >= 12 && hr < 19 ? "Buenas tardes" : "Buenas noches"));
        String nombre = prefs.getString("user_name", "").trim();
        greetTv.setText(nombre.length() > 0 ? saludo + ", " + nombre : saludo);
    }

    private final LocationListener gpsListener = new LocationListener() {
        public void onLocationChanged(Location l) {
            lastFix = SystemClock.elapsedRealtime();
            lastFixAny = lastFix;
            setGps(true, l.hasSpeed() ? l.getSpeed() * 3.6f : 0);
            feedMap(l);
        }
        public void onStatusChanged(String p, int st, Bundle e) { }
        public void onProviderEnabled(String p) { }
        public void onProviderDisabled(String p) { setGps(false, 0); }
    };

    /** Ubicación por red (WiFi/datos): solo para ubicar el mapa mientras llega el GPS. */
    private final LocationListener netListener = new LocationListener() {
        public void onLocationChanged(Location l) { if (lastFix == 0) feedMap(l); }
        public void onStatusChanged(String p, int st, Bundle e) { }
        public void onProviderEnabled(String p) { }
        public void onProviderDisabled(String p) { }
    };

    private void startGps() {
        if (gpsStartAt == 0) gpsStartAt = SystemClock.elapsedRealtime();
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener);
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 0, netListener); } catch (Throwable ignored) { }
        } catch (Exception e) {
            setGps(false, 0);
        }
    }

    private void stopGps() {
        try { lm.removeUpdates(gpsListener); } catch (Exception ignored) { }
        try { lm.removeUpdates(netListener); } catch (Exception ignored) { }
    }

    private void setGps(boolean ok, float kmh) {
        if (speedTv == null) return;
        if (!ok) lastFix = 0;
        float v = useMph() ? kmh * 0.621371f : kmh;
        speedTv.setText(ok ? String.valueOf(Math.round(kmh < 2 ? 0 : v)) : "--");
        if (topSpeedTv != null) topSpeedTv.setText(ok ? Math.round(kmh < 2 ? 0 : v) + " " + (useMph() ? "mph" : "km/h") : "");
        int gs = ok ? 0 : gpsState();
        gpsTv.setText(ok ? "GPS" : I18n.t(gs == 1 ? "Sin permiso" : gs == 2 ? "GPS apagado" : "Buscando GPS"));
        gpsDot.setBackground(round(ok ? 0xFF3DDC84 : MUTED, 4));
        updateMapTag();
    }

    private View.OnClickListener mediaClick(final int code) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                int which = code == KeyEvent.KEYCODE_MEDIA_NEXT ? 1 : code == KeyEvent.KEYCODE_MEDIA_PREVIOUS ? -1 : 0;
                boolean done = false;

                if (mediaWatcher != null && "ms".equals(curOrigin)) {
                    try { done = mediaWatcher.command(which); } catch (Throwable ignored) { }
                }
                if (!done && which == 0) done = startMusicApp();
                if (!done && !sendBtCommand(code)) sendMediaKey(code);
                h.postDelayed(new Runnable() { public void run() { updatePlayIcon(); } }, 600);
            }
        };
    }

    /**
     * ▶ sin nada sonando ni ninguna app de música activa (Android 5+, issue #2): le pide reproducir a la app de música
     * preferida con su receptor de teclas multimedia y, si en 2,5 s no suena nada, la abre. En Android 4 no se usa:
     * ahí ▶ es lo que pasa el sonido de la radio al Bluetooth.
     */
    private boolean startMusicApp() {
        if (SDK < 21 || "Bluetooth".equals(curSource) || "Android Auto".equals(curSource)) return false;
        if (mediaWatcher != null && mediaWatcher.hasSession()) return false;
        final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am.isMusicActive()) return false;
        String saved = prefs.getString("music_app", null);
        ComponentName cn = saved != null ? ComponentName.unflattenFromString(saved) : null;
        final String pkg = cn != null ? cn.getPackageName() : firstInstalled(MUSIC_PKGS);
        if (pkg == null) return false;
        long t = SystemClock.uptimeMillis();
        for (int a : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
            i.setPackage(pkg);
            i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(t, t, a, KeyEvent.KEYCODE_MEDIA_PLAY, 0));
            try { sendBroadcast(i); } catch (Throwable ignored) { }
        }
        h.postDelayed(new Runnable() { public void run() {
            if (am.isMusicActive()) return;
            if (!launchSaved("music_app")) launchPkg(pkg);
        }}, 2500);
        return true;
    }

    /** Si suena Bluetooth, controla directamente la app Bluetooth de fábrica (com.clw.btmusic). */
    private boolean sendBtCommand(int code) {
        if (!"Bluetooth".equals(curSource)) return false;
        if (bt != null && bt.command(code == KeyEvent.KEYCODE_MEDIA_NEXT ? 1 : code == KeyEvent.KEYCODE_MEDIA_PREVIOUS ? -1 : 0)) {
            h.postDelayed(new Runnable() { public void run() { if (bt != null) bt.refresh(); } }, 700);
            return true;
        }
        String a = code == KeyEvent.KEYCODE_MEDIA_NEXT ? "com.clw.btmusic.next"
                : code == KeyEvent.KEYCODE_MEDIA_PREVIOUS ? "com.clw.btmusic.previous" : "com.clw.btmusic.togglepause";
        try {
            Intent i = new Intent(a);
            i.setClassName("com.clw.btmusic", "com.clw.btmusic.BTPlaybackService");
            return startService(i) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    private void sendMediaKey(int code) {
        long t = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0);
        KeyEvent up = new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0);
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            java.lang.reflect.Method m = AudioManager.class.getMethod("dispatchMediaKeyEvent", KeyEvent.class);
            m.invoke(am, down);
            m.invoke(am, up);
            return;
        } catch (Throwable ignored) { }
        Intent i1 = new Intent(Intent.ACTION_MEDIA_BUTTON);
        i1.putExtra(Intent.EXTRA_KEY_EVENT, down);
        sendOrderedBroadcast(i1, null);
        Intent i2 = new Intent(Intent.ACTION_MEDIA_BUTTON);
        i2.putExtra(Intent.EXTRA_KEY_EVENT, up);
        sendOrderedBroadcast(i2, null);
    }

    private void updatePlayIcon() {
        if (playIc == null) return;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        boolean on = playingState != null ? playingState : am.isMusicActive();
        playIc.setImageBitmap(asset(on ? "ic/pause.png" : "ic/play_arrow.png"));
    }

    // ───────────────────────── pista en reproducción ─────────────────────────

    private void showTrack(NowPlaying.Track t) {
        long now = SystemClock.elapsedRealtime();
        boolean hasContent = t.title != null || t.artist != null;
        // Un aviso sin pista de otra fuente (p. ej. el sistema cuando entra la cámara de reversa)
        // no debe borrar ni pausar lo que muestra la fuente actual.
        if (curOrigin != null && !curOrigin.equals(t.origin) && !hasContent) return;
        // Con Android Auto conectado el celular manda la misma canción también por Bluetooth (AVRCP), a cada segundo
        // y a veces sin título (solo tiempo o estado). Mientras Android Auto sea la fuente, Bluetooth solo aporta
        // tiempo y estado: nunca cambia la etiqueta ni borra la pista. Antes esos avisos sin título ponían
        // «Bluetooth» y la notificación de Open Headunit volvía a poner «Android Auto»: la etiqueta titilaba.
        boolean aaOwns = "rcc".equals(curOrigin) || ("Android Auto".equals(curSource) && aaRunning());
        if (aaOwns && "bt".equals(t.origin)) {
            if (t.clear) return;
            if (t.polled && Boolean.FALSE.equals(t.playing)) t.playing = null; // estado local del servicio, no el real
            if (t.title == null || sameTitle(t.title, curTitle)) {
                t.title = null;
                t.source = null;
                t.artist = null;
                hasContent = false;
            } else if ("Android Auto".equals(curSource)) {
                // Canción nueva: la notificación de Open Headunit suele llegar un instante después.
                // Se mantiene «Android Auto» unos segundos; si no llega, pasa a Bluetooth.
                t.source = null;
                aaWaitTitle = t.title;
                h.removeCallbacks(aaWait);
                h.postDelayed(aaWait, 5000);
            }
        }
        if (hasContent && !t.clear) curOrigin = t.origin;
        if (t.clear) {
            curOrigin = null;
            curTitle = curArtist = curSource = null;
            curArt = null;
            durMs = -1;
            posKnown = false;
            playingState = false;
            renderTrack();
            return;
        }
        boolean newTrack = t.title != null && !t.title.equals(curTitle);
        if (newTrack) {
            posBaseMs = 0;
            posBaseAt = now;
            posKnown = true;
            durMs = -1;
            if (!t.artSet) curArt = null;
            // ¿La notificación de Open Headunit ya trae la carátula de esta canción?
            if (!t.artSet && NotifArt.lastArt != null && textsMatch(NotifArt.lastTexts, t.title)) {
                curArt = NotifArt.lastArt;
                t.source = "Android Auto";
                aaWaitTitle = null;
                h.removeCallbacks(aaWait);
            }
        }
        if (t.title != null) curTitle = t.title;
        if (t.artist != null) curArtist = t.artist;
        if (t.source != null) curSource = t.source;
        if (t.durationMs > 0) durMs = t.durationMs;
        if (t.artSet) curArt = t.art;
        long pos = currentPos();
        if (t.positionMs >= 0) { pos = t.positionMs; posKnown = true; }
        if (t.playing != null) {
            playingState = t.playing;
        }
        posBaseMs = pos;
        posBaseAt = now;
        renderTrack();
    }

    /** Misma canción aunque una fuente la mande recortada o con otras mayúsculas. */
    private static boolean sameTitle(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.trim().toLowerCase(ES), y = b.trim().toLowerCase(ES);
        if (x.equals(y)) return true;
        return Math.min(x.length(), y.length()) > 8 && (x.startsWith(y) || y.startsWith(x));
    }

    /**
     * Android Auto sigue conectado: Open Headunit corriendo y, además, el celular conectado por USB en modo
     * Android Auto (o, si nunca se vio por USB, p. ej. inalámbrico, una notificación de Open Headunit reciente).
     * Open Headunit sigue corriendo después de quitar el cable, así que el proceso solo no basta.
     * Se revisa cada 3 s como máximo.
     */
    private boolean aaRunning() {
        long now = SystemClock.elapsedRealtime();
        if (now - aaCheckedAt > 3000) {
            aaCheckedAt = now;
            boolean alive = processRunning(PKG_HUR);
            if (alive) {
                int usb = aaUsbState();
                if (usb == 1) aaUsbSeen = true;
                else if (usb == 0) alive = !aaUsbSeen && NotifArt.lastAt > 0 && now - NotifArt.lastAt < 15000;
            }
            aaRunningCached = alive;
        }
        return aaRunningCached;
    }

    /** 1 = hay un celular por USB en modo Android Auto (accesorio de Google), 0 = no hay, -1 = no se puede saber. */
    private int aaUsbState() {
        try {
            android.hardware.usb.UsbManager um = (android.hardware.usb.UsbManager) getSystemService(USB_SERVICE);
            if (um == null) return -1;
            java.util.HashMap<String, android.hardware.usb.UsbDevice> list = um.getDeviceList();
            if (list == null) return -1;
            for (android.hardware.usb.UsbDevice d : list.values())
                if (d.getVendorId() == 0x18D1 && d.getProductId() >= 0x2D00 && d.getProductId() <= 0x2D05) return 1;
            return 0;
        } catch (Throwable e) {
            return -1;
        }
    }
    private boolean aaUsbSeen;

    /**
     * Si Android Auto tenía la canción y se desconectó (se quitó el cable), la fuente pasa a Bluetooth y, si está
     * activado, abre un instante la app de música Bluetooth para que el sonido vuelva al Bluetooth.
     */
    private void checkAaGone(boolean now) {
        if (!"Android Auto".equals(curSource) || "rcc".equals(curOrigin)) return;
        if (now) aaCheckedAt = 0;
        if (aaRunning()) return;
        BtBridge.log("Android Auto se desconectó");
        aaWaitTitle = null;
        h.removeCallbacks(aaWait);
        curSource = "Bluetooth";
        curArt = null;
        renderTrack();
        if (bt != null && bt.isConnected()) bt.refresh();
        if (prefs.getBoolean("aa_off_bt", true) && (clwBt || btMusicApp() != null)) {
            btRouted = false;
            openBtAudioApp(false);
        }
    }

    private final BroadcastReceiver usbOffReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            h.postDelayed(new Runnable() { public void run() { checkAaGone(true); } }, 1500);
        }
    };
    private long aaCheckedAt;
    private boolean aaRunningCached;
    private String aaWaitTitle;

    /** Si la canción nueva no llegó por la notificación de Android Auto, la fuente pasa a Bluetooth. */
    private final Runnable aaWait = new Runnable() {
        public void run() {
            String w = aaWaitTitle;
            aaWaitTitle = null;
            if (w == null || !"Android Auto".equals(curSource) || !sameTitle(w, curTitle)) return;
            curSource = "Bluetooth";
            curArt = null;
            renderTrack();
        }
    };

    private static boolean textsMatch(java.util.List<String> texts, String title) {
        if (texts == null || title == null) return false;
        String tt = title.trim().toLowerCase(ES);
        for (String s : texts) {
            if (s == null) continue;
            String x = s.trim().toLowerCase(ES);
            if (x.equals(tt) || (tt.length() > 3 && x.contains(tt))) return true;
        }
        return false;
    }

    /** Carátula de Android Auto leída de la notificación de Open Headunit. */
    private void applyAaNotif(Bitmap art, java.util.List<String> texts) {
        if (art == null || curTitle == null) return;
        if (!textsMatch(texts, curTitle)) return;
        if (aaWaitTitle != null && sameTitle(aaWaitTitle, curTitle)) { aaWaitTitle = null; h.removeCallbacks(aaWait); }
        curArt = art;
        curSource = "Android Auto";
        renderTrack();
    }

    private void showPlayerDiag() {
        String msg = "Fuente: " + curOrigin + " / " + curSource
                + "\nPista: " + curTitle + " — " + curArtist
                + "\nCarátula: " + (curArt != null ? curArt.getWidth() + "x" + curArt.getHeight() : "no")
                + "\n\nLector de notificaciones (accesibilidad): " + (NotifArt.running ? "ACTIVO" : "apagado")
                + "\nÚltima notificación de Open Headunit: " + (NotifArt.lastAt == 0 ? "ninguna"
                    : ((SystemClock.elapsedRealtime() - NotifArt.lastAt) / 1000) + " s, carátula "
                    + (NotifArt.lastArt != null ? "sí" : "no") + ", textos " + NotifArt.lastTexts)
                + "\nOpen Headunit corriendo: " + (processRunning(PKG_HUR) ? "sí" : "no")
                + " · celular por USB en modo Android Auto: " + (aaUsbState() == 1 ? "sí" : aaUsbState() == 0 ? "no" : "no se sabe")
                + "\nAndroid Auto conectado (para StradaOS): " + (aaRunning() ? "sí" : "no")
                + "\nApp Bluetooth conectada: " + (bt != null && bt.isConnected() ? "sí" : "no")
                + "\nAudio Bluetooth automático: " + (clwBt ? (btAutoOn() ? "activado" : "desactivado") : "no aplica (otra marca)")
                + (clwBt ? "" : "\nApp de música Bluetooth: " + (btMusicApp() != null ? btMusicApp().getPackageName() : "no encontrada")
                    + " · abrir al conectar: " + (prefs.getBoolean("bt_open_connect", false) ? "sí" : "no"))
                + "\nSonido en Bluetooth (estimado): " + (btRouted ? "sí" : "no") + " · app al frente: " + topPackage()
                + "\nActivaciones: " + BtBridge.activations
                + "\n\nEventos Bluetooth:\n" + BtBridge.logText();
        new TB(this, dlg())
            .setTitle("Diagnóstico del reproductor")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setNeutralButton("Accesibilidad", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { openAccessibility(); }
            })
            .show();
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            toast("Activa «Carátula de Android Auto» (StradaOS)");
        } catch (Throwable e) {
            toast("No se pudo abrir Accesibilidad");
        }
    }

    private long currentPos() {
        long p = posBaseMs;
        if (playingState != null && playingState) p += SystemClock.elapsedRealtime() - posBaseAt;
        if (durMs > 0 && p > durMs) p = durMs;
        return p;
    }

    private static String fmt(long ms) {
        long sec = Math.max(0, ms / 1000);
        return (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
    }

    private void renderTrack() {
        if (trackTv == null) return;
        if (curTitle == null && curArtist == null) {
            trackTv.setText(I18n.t("Nada sonando"));
            artistTv.setText(I18n.t("Radio, Bluetooth o USB"));
            sourceTv.setText("");
            timePosTv.setText("");
            playerArt.setArt(null);
            playerArt.setProgress(-1);
        } else {
            boolean aaOff = curSource != null && curSource.toLowerCase(ES).contains("headunit")
                    && ("Conectado".equalsIgnoreCase(curArtist) || "Connected".equalsIgnoreCase(curArtist));
            trackTv.setText(curTitle != null ? curTitle : (curSource != null ? I18n.t(curSource) : ""));
            artistTv.setText(aaOff ? I18n.t("Activa «Pista actual de AA» en Open Headunit") : (curArtist != null ? curArtist : ""));
            sourceTv.setText(curSource != null ? I18n.t(curSource).toUpperCase(I18n.loc) : "");
            playerArt.setArt(curArt);
            updateProgress();
        }
        if (playIc != null) {
            boolean on = playingState != null ? playingState
                    : ((AudioManager) getSystemService(AUDIO_SERVICE)).isMusicActive();
            playIc.setImageBitmap(asset(on ? "ic/pause.png" : "ic/play_arrow.png"));
        }
    }

    private void updateProgress() {
        if (playerArt == null || (curTitle == null && curArtist == null)) return;
        if (durMs > 0 && posKnown) {
            long p = currentPos();
            playerArt.setProgress(p / (float) durMs);
            timePosTv.setText(fmt(p) + "  /  " + fmt(durMs));
        } else if (durMs > 0) {
            playerArt.setProgress(-1);
            timePosTv.setText(fmt(durMs));
        } else {
            playerArt.setProgress(-1);
            timePosTv.setText("");
        }
    }

    private final Runnable progressTick = new Runnable() {
        public void run() {
            updateProgress();
            h.postDelayed(this, 1000);
        }
    };

    // ───────────────────────── configuración ─────────────────────────

    private void openSettings() { showSettings(1); }

    private void usbHelp() {
        new TB(this, dlg())
            .setTitle("«Ninguna aplic. funciona con este accesorio USB»")
            .setMessage("Ese aviso lo causa una app de espejo de fábrica (EasyConnection/CarbitLink) que toma el USB antes que la app que elegiste.\n\n1. Desconecta el celular.\n2. Ajustes → Aplicaciones → EasyConnection → «Borrar datos».\n3. Apaga y enciende el carro.\n4. En Open Headunit → USB, si tu celular aparece como «Ignorado», tócalo para permitirlo.")
            .setPositiveButton("Abrir EasyConnection", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    for (String p : USB_HOGS) if (installed(p)) {
                        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignored) { }
                        return;
                    }
                    toast("No hay apps de espejo instaladas");
                }
            })
            .setNegativeButton("Cerrar", null).show();
    }

    private void runDiag() {
        toast("Exportando… puede tardar un minuto");
        new Thread(new Runnable() {
            public void run() {
                final String r = Diag.export(Main.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        new TB(Main.this, dlg())
                            .setTitle("Diagnóstico listo")
                            .setMessage("Se guardó en:\n" + r + "\n\nCopia la carpeta StradaDiag a una memoria USB y adjúntala en un «issue» del repositorio de StradaOS en GitHub.")
                            .setPositiveButton("OK", null).show();
                    }
                });
            }
        }).start();
    }

    private String clockLabel() {
        String c = prefs.getString("clock", "auto");
        return "12".equals(c) ? "12 horas" : "24".equals(c) ? "24 horas" : "Automático";
    }

    private void chooseClock() {
        final String[] opts = {"Automático", "12 horas (a.m./p.m.)", "24 horas"};
        final String[] vals = {"auto", "12", "24"};
        new TB(this, dlg())
            .setTitle("Formato de hora")
            .setItems(opts, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    prefs.edit().putString("clock", vals[w]).apply();
                    updateClock();
                }
            }).show();
    }

    private void editText(final String key, String title, String def) {
        final android.widget.EditText et = field();
        et.setSingleLine(true);
        et.setText(prefs.getString(key, def));
        et.setSelection(et.getText().length());
        FrameLayout wrap = new FrameLayout(this);
        int pad = (int) (20 * s);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(et);
        new TB(this, dlg())
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    prefs.edit().putString(key, et.getText().toString().trim()).apply();
                    if ("city_manual".equals(key)) refreshWeatherNow();
                    if ("map_url".equals(key)) toast("Se aplicará al reiniciar StradaOS");
                    if ("extra_actions".equals(key)) {
                        try { nowPlaying.stop(); nowPlaying.start(); } catch (Throwable ignored) { }
                    }
                    builtW = 0;
                    build(root.getWidth(), root.getHeight());
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    // ───────────────────────── universal: asistente, color, foto, CarPlay ─────────────────────────

    static final String[] ACCENT_NAMES = {"StradaOS", "Rojo", "Naranja", "Amarillo", "Verde", "Turquesa", "Azul", "Morado", "Rosa", "Plata"};
    static final int[] ACCENT_COLORS = {0xFFFE3211, 0xFFE3262F, 0xFFFF7A1A, 0xFFF5B800, 0xFF2DBE6C, 0xFF14B8B0, 0xFF2F7CF6, 0xFF8E5CF7, 0xFFE8457C, 0xFF9AA3AF};
    static final String[] CARPLAY_PKGS = {"com.zjinnova.zlink", "com.zjinnova.zlink5", "com.autokit", "cn.manstep.phonemirrorBox",
            "cn.manstep.phonemirrorBox.nomap", "com.suding.speedplay", "com.tlink.carplay", "com.syu.carlink", "com.carlinkit.autokit"};

    private void rebuild() {
        builtW = 0;
        if (root.getWidth() > 0) build(root.getWidth(), root.getHeight());
    }

    private void applyAccent() {
        RED = prefs.getInt("accent", 0xFFFE3211);
        int r = (RED >> 16) & 255, g = (RED >> 8) & 255, b = RED & 255;
        int br = (BG >> 16) & 255, bgc = (BG >> 8) & 255, bb = BG & 255, pct = LIGHT ? 16 : 18;
        RED_DARK = 0xFF000000 | ((r * pct / 100 + br * (100 - pct) / 100) << 16) | ((g * pct / 100 + bgc * (100 - pct) / 100) << 8) | (b * pct / 100 + bb * (100 - pct) / 100);
    }

    /** Usuario que actualiza desde una versión anterior: conserva su carro. Usuario nuevo: asistente. */
    private void migrateOrSetup() {
        if (prefs.getBoolean("setup_done", false)) return;
        boolean upgrade = false;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            upgrade = pi.lastUpdateTime - pi.firstInstallTime > 60000;
        } catch (Throwable ignored) { }
        if (upgrade) {
            prefs.edit().putBoolean("setup_done", true).apply();
        } else {
            h.postDelayed(new Runnable() { public void run() { wizard(); } }, 600);
        }
    }

    /** Cambia el idioma («auto», «es», «en», «pt», «it», «de», «ms») y reconstruye la pantalla. */
    private void setLanguage(String code) {
        prefs.edit().putString("lang", code).apply();
        I18n.set(this, "auto".equals(code) ? I18n.system() : code);
        rebuild();
    }

    /** Primer paso del asistente: idioma (preseleccionado el del sistema). */
    private void wizard() {
        int sel = Math.max(0, java.util.Arrays.asList(I18n.CODES).indexOf(I18n.lang));
        wizardDlg = new android.app.AlertDialog.Builder(this, dlg())
            .setTitle("Idioma · Language · Lingua · Sprache · Bahasa")
            .setSingleChoiceItems(I18n.NAMES, sel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    d.dismiss();
                    setLanguage(I18n.CODES[w]);
                    wizardWelcome();
                }
            })
            .setCancelable(false)
            .show();
    }
    private android.app.Dialog wizardDlg;

    private void wizardWelcome() {
        new TB(this, dlg())
            .setTitle("Bienvenido a StradaOS")
            .setMessage("Vamos a dejar el launcher a tu gusto: tu nombre, tu vehículo, el color y la foto. Toma menos de un minuto y lo puedes cambiar después en el engranaje ⚙.")
            .setPositiveButton("Empezar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    ask("user_name", "¿Cómo te llamas?", "", new Runnable() { public void run() {
                    ask("car_name", "Nombre del vehículo (ej.: Mazda 2, Spark GT)", "Mi vehículo", new Runnable() { public void run() {
                    ask("car_tag", "Etiqueta corta (versión o placa). Déjalo vacío si no quieres", "", new Runnable() { public void run() {
                    chooseAccent(new Runnable() { public void run() {
                        prefs.edit().putBoolean("setup_done", true).apply();
                        rebuild();
                        reviewShortcuts(new Runnable() { public void run() {
                            if (!askDefaultHome(new Runnable() { public void run() { pickPhoto(); } })) pickPhoto();
                        } });
                    }});
                    }});
                    }});
                    }});
                }
            })
            .setNegativeButton("Después", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { prefs.edit().putBoolean("setup_done", true).apply(); }
            })
            .show();
    }

    // ── asistente: revisar qué app abre cada botón ──

    static final String[][] REVIEW = {{"radio", "Radio"}, {"bt", "Bluetooth"}, {"btaudio", "Música Bluetooth"}, {"cam", "Cámara"}, {"files", "Archivos"}, {"aa", "Android Auto"}};

    /** Qué app abre cada botón en esta pantalla: la elegida o la que StradaOS detectó. */
    private String shortcutLabel(String key) {
        String c = prefs.getString(key, null);
        if (c != null) return labelOf(c);
        if ("btaudio".equals(key)) { ComponentName cn = btMusicApp(); return cn != null ? "Automático · " + labelOf(cn.flattenToString()) : "No encontrada"; }
        if ("aa".equals(key)) {
            String p = prefs.getString("conn_app", null);
            if (p != null) return labelOf(p);
            List<String> cand = connCandidates();
            return cand.isEmpty() ? "No encontrada" : "Automático · " + labelOf(cand.get(0));
        }
        for (String[] sl : SLOTS) if (sl[0].equals(key)) {
            ResolveInfo r = find(sl[2], sl[3]);
            return r != null ? "Automático · " + r.loadLabel(getPackageManager()) : "No encontrada";
        }
        return "Automático";
    }

    /** Lista de botones con su app; tocar uno deja elegir otra. Funciona igual en cualquier versión de Android. */
    private void reviewShortcuts(final Runnable next) {
        final String[] items = new String[REVIEW.length];
        for (int i = 0; i < REVIEW.length; i++) items[i] = REVIEW[i][1] + "\n    → " + shortcutLabel(REVIEW[i][0]);
        new TB(this, dlg())
            .setTitle("¿Qué app abre cada botón?")
            .setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, final int w) {
                    pickFor(REVIEW[w][0], REVIEW[w][1], new Runnable() { public void run() { reviewShortcuts(next); } });
                }
            })
            .setCancelable(false)
            .setPositiveButton("Están bien", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { refreshStates(); if (next != null) next.run(); }
            })
            .show();
    }

    private void ask(final String key, String title, String def, final Runnable next) {
        final android.widget.EditText et = field();
        et.setSingleLine(true);
        et.setText(prefs.getString(key, def));
        et.setSelection(et.getText().length());
        FrameLayout wrap = new FrameLayout(this);
        int pad = (int) (20 * s);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(et);
        new TB(this, dlg())
            .setTitle(title)
            .setView(wrap)
            .setCancelable(false)
            .setPositiveButton("Siguiente", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    prefs.edit().putString(key, et.getText().toString().trim()).apply();
                    if (next != null) next.run();
                }
            })
            .show();
    }

    private void chooseAccent(final Runnable next) {
        new TB(this, dlg())
            .setTitle("Color de acento")
            .setAdapter(new BaseAdapter() {
                public int getCount() { return ACCENT_NAMES.length; }
                public Object getItem(int p) { return ACCENT_NAMES[p]; }
                public long getItemId(int p) { return p; }
                public View getView(int p, View cv, ViewGroup parent) {
                    LinearLayout row = new LinearLayout(Main.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    int pad = (int) (14 * s);
                    row.setPadding(pad * 2, pad, pad, pad);
                    View dot = new View(Main.this);
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor(ACCENT_COLORS[p]);
                    dot.setBackground(g);
                    row.addView(dot, new LinearLayout.LayoutParams((int) (28 * s), (int) (28 * s)));
                    TextView t = tv(ACCENT_NAMES[p], 17, TEXT, false);
                    t.setPadding(pad, 0, 0, 0);
                    row.addView(t);
                    return row;
                }
            }, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    prefs.edit().putInt("accent", ACCENT_COLORS[w]).apply();
                    applyAccent();
                    if (next != null) next.run(); else rebuild();
                }
            })
            .setCancelable(next == null)
            .show();
    }

    // ── foto del vehículo ──

    private static final int REQ_PHOTO = 41;

    private void photoMenu() {
        final boolean has = CarPhoto.load(this) != null;
        String[] opts = has ? new String[]{"Elegir otra foto", "Quitar la foto"} : new String[]{"Elegir foto"};
        new TB(this, dlg())
            .setTitle("Foto del vehículo")
            .setItems(opts, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) pickPhoto();
                    else { CarPhoto.delete(Main.this); rebuild(); }
                }
            }).show();
    }

    private void pickPhoto() {
        new TB(this, dlg())
            .setTitle("¿De dónde sacamos la foto?")
            .setMessage("Consejo: usa una foto del carro de lado o de frente con fondo liso (blanco, gris o cielo). El fondo se quita solo.")
            .setPositiveButton("Memoria / USB", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { browseImages(); }
            })
            .setNeutralButton("Galería", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("image/*");
                        startActivityForResult(Intent.createChooser(i, "Elige la foto"), REQ_PHOTO);
                    } catch (Throwable e) { browseImages(); }
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_HOME) { homeResult(); return; }
        if (req != REQ_PHOTO || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        processPhoto(new CarPhoto.Opener() {
            public InputStream open() throws Exception { return getContentResolver().openInputStream(uri); }
        });
    }

    private void browseImages() {
        final ProgressDialogLite pd = new ProgressDialogLite("Buscando fotos…");
        new Thread(new Runnable() {
            public void run() {
                final List<File> found = new ArrayList<File>();
                File ext = android.os.Environment.getExternalStorageDirectory();
                String[] roots = {ext.getAbsolutePath(), "/mnt", "/storage", "/udisk", "/mnt/usb_storage"};
                for (String r : roots) scanImages(new File(r), 4, found);
                Collections.sort(found, new Comparator<File>() {
                    public int compare(File a, File b) { long x = b.lastModified() - a.lastModified(); return x > 0 ? 1 : x < 0 ? -1 : 0; }
                });
                runOnUiThread(new Runnable() {
                    public void run() {
                        pd.dismiss();
                        if (found.isEmpty()) {
                            toast("No encontré fotos (.jpg/.png). Cópialas a la memoria USB o a Download.");
                            return;
                        }
                        final int n = Math.min(found.size(), 80);
                        String[] names = new String[n];
                        for (int i = 0; i < n; i++) names[i] = found.get(i).getName() + "  ·  " + found.get(i).getParentFile().getName();
                        new TB(Main.this, dlg())
                            .setTitle("Elige la foto")
                            .setItems(names, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) {
                                    final File f = found.get(w);
                                    processPhoto(new CarPhoto.Opener() {
                                        public InputStream open() throws Exception { return new java.io.FileInputStream(f); }
                                    });
                                }
                            }).setNegativeButton("Cancelar", null).show();
                    }
                });
            }
        }).start();
    }

    private final java.util.HashSet<String> scanned = new java.util.HashSet<String>();

    private void scanImages(File dir, int depth, List<File> out) {
        if (depth < 0 || dir == null || out.size() > 400) return;
        try {
            String canon = dir.getCanonicalPath();
            if (!scanned.add(canon) && depth < 4) return;
        } catch (Throwable ignored) { }
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName().toLowerCase(ES);
            if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                    && f.length() > 15000 && !n.startsWith(".")) out.add(f);
        }
        for (File f : fs) {
            String n = f.getName();
            if (f.isDirectory() && !n.startsWith(".") && !"Android".equals(n) && !"LOST.DIR".equals(n)
                    && !n.equalsIgnoreCase("thumbnails") && !"StradaDiag".equals(n)) scanImages(f, depth - 1, out);
        }
    }

    private void processPhoto(final CarPhoto.Opener opener) {
        final ProgressDialogLite pd = new ProgressDialogLite("Quitando el fondo…");
        new Thread(new Runnable() {
            public void run() {
                Bitmap src = null, cut = null;
                String err = null;
                boolean already = false;
                try {
                    src = CarPhoto.decode(opener);
                    already = CarPhoto.hasTransparency(src);
                    cut = already ? src : CarPhoto.removeBackground(src, 1);
                } catch (Throwable e) {
                    err = e.getMessage();
                }
                final Bitmap fsrc = src, fcut = cut;
                final String ferr = err;
                final boolean falready = already;
                runOnUiThread(new Runnable() {
                    public void run() {
                        pd.dismiss();
                        if (fsrc == null) { toast("No se pudo abrir la imagen" + (ferr != null ? ": " + ferr : "")); return; }
                        previewPhoto(fsrc, fcut, 1, falready);
                    }
                });
            }
        }).start();
    }

    private void previewPhoto(final Bitmap src, final Bitmap cut, final int strength, boolean already) {
        final Bitmap shown = CarPhoto.fitToCard(cut != null ? cut : src);
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(CARD);
        int pad = (int) (16 * s);
        box.setPadding(pad, pad, pad, pad);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(shown);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(iv, new FrameLayout.LayoutParams((int) (396 * s), (int) (240 * s)));
        String title = already ? "La foto ya trae fondo transparente"
                : cut == null ? "No se detectó un fondo liso (se usará tal cual)"
                : "Vista previa · nivel " + (strength + 1) + " de 4" + (strength >= 2 ? " (también quita el reflejo del piso)" : "");
        AlertDialog.Builder b = new TB(this, dlg())
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Usar esta", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (CarPhoto.save(Main.this, shown)) { toast("Foto guardada"); rebuild(); }
                    else toast("No se pudo guardar la foto");
                }
            })
            .setNegativeButton("Cancelar", null);
        if (!already && strength < 3) {
            b.setNeutralButton("Quitar más fondo", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    final ProgressDialogLite pd = new ProgressDialogLite("Quitando más fondo…");
                    new Thread(new Runnable() {
                        public void run() {
                            final Bitmap c2 = CarPhoto.removeBackground(src, strength + 1);
                            runOnUiThread(new Runnable() {
                                public void run() { pd.dismiss(); previewPhoto(src, c2 != null ? c2 : cut, strength + 1, false); }
                            });
                        }
                    }).start();
                }
            });
        }
        b.show();
    }

    /** Diálogo de espera simple (compatible con Android 4.1). */
    private class ProgressDialogLite {
        private final AlertDialog d;
        ProgressDialogLite(String msg) {
            LinearLayout l = new LinearLayout(Main.this);
            l.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) (22 * s);
            l.setPadding(pad, pad, pad, pad);
            l.addView(new android.widget.ProgressBar(Main.this));
            TextView t = tv(msg, 16, TEXT, false);
            t.setPadding(pad, 0, 0, 0);
            l.addView(t);
            d = new TB(Main.this, dlg()).setView(l).setCancelable(false).create();
            d.show();
        }
        void dismiss() { try { d.dismiss(); } catch (Throwable ignored) { } }
    }

    // ── CarPlay (requiere adaptador USB con su app) ──

    /** App de proyección integrada en la pantalla (ZLink, CarLink, TLink, AutoKit…): suele manejar Android Auto y CarPlay. */
    private String nativeProjectionApp() {
        for (String p : CARPLAY_PKGS) if (installed(p)) return p;
        ResolveInfo r = find("carplay|zlink|carlink|autokit|tlink|speedplay", "carplay|zlink|carlink|autokit|tlink");
        // EasyConnection/Carbit es solo espejo de pantalla: no es Android Auto ni CarPlay
        if (r != null && r.activityInfo.packageName.startsWith("net.easyconn")) return null;
        return r != null ? r.activityInfo.packageName : null;
    }

    private String carPlayApp() {
        String saved = prefs.getString("carplay", null);
        if (saved != null) return saved;
        return nativeProjectionApp();
    }

    /** Nombre visible de un paquete o de un componente guardado. */
    private String labelOf(String pkgOrComponent) {
        try {
            String pkg = pkgOrComponent.contains("/") ? ComponentName.unflattenFromString(pkgOrComponent).getPackageName() : pkgOrComponent;
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Throwable e) {
            return "App elegida";
        }
    }

    private void openCarPlay() {
        if (launchSaved("carplay")) return;
        String p = nativeProjectionApp();
        if (p != null && launchPkg(p)) return;
        new TB(this, dlg())
            .setTitle("Apple CarPlay")
            .setMessage("CarPlay necesita un chip de autenticación de Apple dentro del equipo, así que no se puede activar solo con una app.\n\n• Si tu pantalla trae CarPlay integrado, deja presionado este botón y elige su app (ZLink, CarLink, TLink…).\n• Si no, puedes usar un adaptador CarPlay USB para pantallas Android (tipo Carlinkit/AutoKit): instala su app y este botón la abrirá.")
            .setPositiveButton("Entendido", null)
            .setNeutralButton("Elegir app", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { pickFor("carplay", "CarPlay"); }
            }).show();
    }

    // ───────────────────────── tema (oscuro / claro / automático) ─────────────────────────

    static int dlg() { return LIGHT ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK; }

    private void applyTheme() {
        String mode = prefs.getString("theme", "dark");
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        LIGHT = "light".equals(mode) || ("auto".equals(mode) && hr >= 6 && hr < 18);
        if (LIGHT) {
            BG = 0xFFE9ECF1; CARD = 0xFFFFFFFF; INNER = 0xFFF1F3F7; BTN = 0xFFE2E6EC; DOCK = 0xFFDDE1E8;
            TEXT = 0xFF14161B; MUTED = 0xFF6B7280; SOFT = 0xFF3A404C; CIRCLE = 0xFFFFFFFF; ICON = 0xFF2B303A;
        } else {
            BG = 0xFF15171C; CARD = 0xFF1E2128; INNER = 0xFF262A33; BTN = 0xFF2A2E37; DOCK = 0xFF0D0E11;
            TEXT = 0xFFF2F3F5; MUTED = 0xFF8B9099; SOFT = 0xFFC5C8CE; CIRCLE = 0xFF23262E; ICON = 0xFFFFFFFF;
        }
    }

    // ───────────────────────── clima ─────────────────────────

    private void maybeWeather() {
        if (!prefs.getBoolean("weather_on", true) || Double.isNaN(wLat)) { showWeather(); return; }
        if (SystemClock.elapsedRealtime() - weatherAt < 30 * 60 * 1000L && weatherNow != null) return;
        weatherAt = SystemClock.elapsedRealtime();
        Weather.fetch(this, wLat, wLon, new Weather.Listener() {
            public void onWeather(final Weather.Now n, String err) {
                h.post(new Runnable() { public void run() {
                    if (n != null) weatherNow = n; else weatherAt = SystemClock.elapsedRealtime() - 25 * 60 * 1000L; // reintenta en 5 min
                    showWeather();
                }});
            }
        });
    }

    private void refreshWeatherNow() { weatherAt = 0; weatherNow = null; maybeWeather(); }

    private void showWeather() {
        if (weatherBox == null) return;
        if (!prefs.getBoolean("weather_on", true) || weatherNow == null) { weatherBox.setVisibility(View.GONE); return; }
        weatherBox.setVisibility(View.VISIBLE);
        weatherIc.setImageBitmap(asset("ic/" + Weather.icon(weatherNow.code, weatherNow.day) + ".png"));
        weatherTv.setText(Math.round(weatherNow.temp) + "°" + weatherNow.unit);
        weatherCityTv.setText(weatherNow.city != null ? weatherNow.city : I18n.t(Weather.describe(weatherNow.code)));
    }

    // ───────────────────────── panel de ajustes ─────────────────────────

    interface IntCb { void on(int i); }

    static final String[] CAT_NAMES = {"Volver al inicio", "Tu vehículo", "Apariencia", "Conexión", "Accesos y apps", "Mapa y clima", "Música", "Sistema", "Acerca de"};
    static final String[] CAT_ICONS = {"arrow_back", "directions_car", "palette", "usb", "apps", "map", "music_note", "tune", "info"};

    private android.widget.ScrollView settingsScroll;
    private int settingsScrollCat = -1;
    private int settingsPendingY = -1;   // posición por restaurar en una página que aún no se ha acomodado

    private void closeSettings() {
        settingsCat = -1;
        settingsScroll = null; settingsScrollCat = -1; settingsPendingY = -1;
        if (settingsView != null) { root.removeView(settingsView); settingsView = null; }
    }

    private void showSettings(int cat) {
        float keep = s;
        s = uiS;
        try { showSettings0(cat); } finally { s = keep; }
    }

    private void showSettings0(int cat) {
        if (cat == 0) { closeSettings(); return; }
        // al cambiar un ajuste se redibuja la página: que se quede donde estaba, no que vuelva arriba
        // (algunos ajustes redibujan dos veces seguidas: la segunda vez la página anterior aún no se acomodó y su
        // posición sigue pendiente; se usa esa y no un 0)
        final int keepY = settingsScroll == null || settingsScrollCat != cat ? 0
                : settingsPendingY >= 0 ? settingsPendingY : settingsScroll.getScrollY();
        settingsPendingY = keepY > 0 ? keepY : -1;
        settingsCat = cat;
        if (settingsView != null) root.removeView(settingsView);
        settingsView = new FrameLayout(this);
        settingsView.setBackgroundColor(BG);
        settingsView.setClickable(true);
        root.addView(settingsView, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout panel = new FrameLayout(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams((int) (1024 * s), (int) (600 * s));
        plp.gravity = Gravity.CENTER;
        settingsView.addView(panel, plp);

        // barra lateral
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackground(round(CARD, 20));
        side.setPadding((int) (10 * s), (int) (14 * s), (int) (10 * s), (int) (14 * s));
        place(panel, side, 16, 16, 240, 568);
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding((int) (8 * s), 0, 0, (int) (12 * s));
        ImageView logo = new ImageView(this);
        logo.setImageBitmap(asset("logo.png"));
        brand.addView(logo, new LinearLayout.LayoutParams((int) (30 * s), (int) (30 * s)));
        TextView bt = tv("StradaOS", 18, TEXT, true);
        bt.setPadding((int) (10 * s), 0, 0, 0);
        brand.addView(bt);
        side.addView(brand);
        for (int i = 0; i < CAT_NAMES.length; i++) {
            final int idx = i;
            boolean sel = i == cat;
            LinearLayout it = new LinearLayout(this);
            it.setGravity(Gravity.CENTER_VERTICAL);
            it.setPadding((int) (12 * s), 0, (int) (8 * s), 0);
            if (sel) it.setBackground(round(RED_DARK, 12));
            ImageView ic = icon(CAT_ICONS[i], 20);
            if (sel) ic.setColorFilter(RED);
            else if (ICON == 0xFFFFFFFF) ic.setAlpha(0.7f);
            it.addView(ic, new LinearLayout.LayoutParams((int) (20 * s), (int) (20 * s)));
            TextView lt = tv(CAT_NAMES[i], 14, sel ? RED : SOFT, sel);
            lt.setPadding((int) (12 * s), 0, 0, 0);
            it.addView(lt);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, (int) (46 * s));
            ilp.bottomMargin = (int) (4 * s);
            side.addView(it, ilp);
            if (i == 0) {
                View d = new View(this);
                d.setBackgroundColor(BTN);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, (int) s));
                dl.topMargin = (int) (2 * s);
                dl.bottomMargin = (int) (8 * s);
                side.addView(d, dl);
            }
            it.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showSettings(idx); }
            });
        }

        // contenido
        // La posición anterior se pone durante el acomodo (onLayout), antes del primer dibujo: si se pusiera después
        // (con post), se vería un cuadro con la página arriba y luego el salto a su sitio (un parpadeo).
        final android.widget.ScrollView sc = new android.widget.ScrollView(this) {
            private boolean restored = keepY <= 0;
            @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                if (!restored && getChildCount() > 0 && getChildAt(0).getHeight() > 0) {
                    restored = true;
                    scrollTo(0, keepY);   // ScrollView lo limita solo si la página quedó más corta
                    if (settingsScroll == this) settingsPendingY = -1;
                }
            }
        };
        sc.setVerticalScrollBarEnabled(false);
        place(panel, sc, 272, 16, 736, 568);
        settingsScroll = sc; settingsScrollCat = cat;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding((int) (8 * s), (int) (6 * s), (int) (8 * s), (int) (24 * s));
        sc.addView(body, new FrameLayout.LayoutParams(-1, -2));
        TextView title = tv(CAT_NAMES[cat], 24, TEXT, true);
        title.setPadding((int) (4 * s), 0, 0, (int) (14 * s));
        body.addView(title);

        switch (cat) {
            case 1: catVehicle(body); break;
            case 2: catLook(body); break;
            case 3: catConnection(body); break;
            case 4: catApps(body); break;
            case 5: catMapWeather(body); break;
            case 6: catMusic(body); break;
            case 7: catSystem(body); break;
            case 8: catAbout(body); break;
        }
    }

    // ── piezas de diseño ──

    private LinearLayout section(LinearLayout body, String name) {
        TextView h2 = tv(I18n.t(name).toUpperCase(I18n.loc), 11, MUTED, true);
        h2.setPadding((int) (6 * s), (int) (10 * s), 0, (int) (8 * s));
        body.addView(h2);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, 16));
        card.setPadding((int) (18 * s), (int) (4 * s), (int) (18 * s), (int) (4 * s));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = (int) (10 * s);
        body.addView(card, lp);
        return card;
    }

    private LinearLayout rowBase(LinearLayout card, String title, String sub) {
        if (card.getChildCount() > 0) {
            View d = new View(this);
            d.setBackgroundColor(BTN);
            card.addView(d, new LinearLayout.LayoutParams(-1, Math.max(1, (int) s)));
        }
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, (int) (12 * s), 0, (int) (12 * s));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = tv(title, 15, TEXT, true);
        t.setSingleLine(false);
        col.addView(t);
        if (sub != null && sub.length() > 0) {
            TextView st = tv(sub, 12, MUTED, false);
            st.setSingleLine(false);
            st.setPadding(0, (int) (3 * s), 0, 0);
            col.addView(st);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    private void row(LinearLayout card, String title, String sub, String value, final Runnable onClick) {
        LinearLayout r = rowBase(card, title, sub);
        if (value != null) {
            TextView v = tv(value, 14, MUTED, false);
            v.setGravity(Gravity.RIGHT);
            v.setPadding((int) (12 * s), 0, (int) (4 * s), 0);
            r.addView(v, new LinearLayout.LayoutParams((int) (220 * s), -2));
        }
        if (onClick != null) {
            ImageView ch = icon("chevron_right", 20);
            ch.setAlpha(0.6f);
            r.addView(ch, new LinearLayout.LayoutParams((int) (20 * s), (int) (20 * s)));
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onClick.run(); } });
        }
    }

    private void segmented(LinearLayout card, String title, String sub, String[] opts, int sel, final IntCb cb) {
        LinearLayout r = rowBase(card, title, sub);
        LinearLayout g = new LinearLayout(this);
        g.setBackground(round(BTN, 18));
        int p = (int) (3 * s);
        g.setPadding(p, p, p, p);
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            TextView o = tv(opts[i], 13, i == sel ? Color.WHITE : SOFT, i == sel);
            o.setGravity(Gravity.CENTER);
            o.setPadding((int) (14 * s), (int) (7 * s), (int) (14 * s), (int) (7 * s));
            if (i == sel) o.setBackground(round(RED, 15));
            o.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { cb.on(idx); } });
            g.addView(o);
        }
        r.addView(g);
    }

    private void toggle(LinearLayout card, String title, String sub, final boolean on, final IntCb cb) {
        LinearLayout r = rowBase(card, title, sub);
        FrameLayout sw = new FrameLayout(this);
        sw.setBackground(round(on ? RED : BTN, 14));
        View knob = new View(this);
        GradientDrawable k = new GradientDrawable();
        k.setShape(GradientDrawable.OVAL);
        k.setColor(Color.WHITE);
        knob.setBackground(k);
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams((int) (22 * s), (int) (22 * s));
        klp.gravity = (on ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL;
        klp.leftMargin = klp.rightMargin = (int) (3 * s);
        sw.addView(knob, klp);
        r.addView(sw, new LinearLayout.LayoutParams((int) (50 * s), (int) (28 * s)));
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { cb.on(on ? 0 : 1); } });
    }

    private String appLabel(String key, String def) {
        String c = prefs.getString(key, null);
        return c == null ? def : labelOf(c);
    }

    private Runnable edit(final String key, final String title, final String def) {
        return new Runnable() { public void run() { editText(key, title, def); } };
    }

    // ── categorías ──

    private void catVehicle(LinearLayout body) {
        LinearLayout c = section(body, "Datos");
        row(c, "Tu nombre", "Aparece en el saludo de la pantalla de inicio", prefs.getString("user_name", ""), edit("user_name", "Tu nombre", ""));
        row(c, "Nombre del vehículo", null, prefs.getString("car_name", "Mi vehículo"), edit("car_name", "Nombre del vehículo", "Mi vehículo"));
        row(c, "Etiqueta", "Versión o placa. Déjala vacía para ocultarla", prefs.getString("car_tag", ""), edit("car_tag", "Etiqueta (vacío para ocultarla)", ""));
        LinearLayout f = section(body, "Foto");
        row(f, "Foto del vehículo", "Se recorta y se le quita el fondo automáticamente", CarPhoto.load(this) != null ? "Configurada" : "Sin foto",
            new Runnable() { public void run() { photoMenu(); } });
        LinearLayout o = section(body, "Cambio de aceite");
        toggle(o, "Recordatorio de cambio de aceite", "Se muestra al lado de la velocidad y avisa cuando toca", oilOn(), new IntCb() { public void on(int i) {
            if (i == 1 && !prefs.contains("odo_base_km")) { oilSetup(); return; }
            prefs.edit().putBoolean("oil_on", i == 1).apply(); updateOilBadge(); showSettings(1);
        }});
        if (oilOn()) {
            row(o, "Kilometraje actual", "Estimado con el GPS. Corrígelo con el del tablero cuando quieras", "≈ " + fmtDist(odoKm()), new Runnable() { public void run() { askOdo(null); } });
            row(o, "Cada cuánto", null, fmtDist(prefs.getFloat("oil_interval_km", 5000)), new Runnable() { public void run() { askInterval(null); } });
            row(o, "Último cambio", "Kilometraje del tablero cuando se hizo", fmtDist(prefs.getFloat("oil_last_km", 0)), new Runnable() { public void run() { askLast(null); } });
            row(o, "Próximo cambio", null, fmtDist(oilNextKm()), new Runnable() { public void run() { oilMenu(); } });
            row(o, "Ya hice el cambio de aceite", null, null, new Runnable() { public void run() { oilDone(); } });
        }
    }

    private void catLook(LinearLayout body) {
        LinearLayout lg = section(body, "Idioma");
        String lpref = I18n.pref(prefs);
        int li = java.util.Arrays.asList(I18n.CODES).indexOf(lpref);
        row(lg, "Idioma", "Automático: el del sistema", li >= 0 ? I18n.NAMES[li] : I18n.t("Automático"), new Runnable() { public void run() {
            final String[] opts = new String[I18n.NAMES.length + 1];
            opts[0] = I18n.t("Automático");
            System.arraycopy(I18n.NAMES, 0, opts, 1, I18n.NAMES.length);
            int sel = java.util.Arrays.asList(I18n.CODES).indexOf(I18n.pref(prefs)) + 1;
            new android.app.AlertDialog.Builder(Main.this, dlg())
                .setTitle(I18n.t("Idioma"))
                .setSingleChoiceItems(opts, sel, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    d.dismiss();
                    setLanguage(w == 0 ? "auto" : I18n.CODES[w - 1]);
                    showSettings(2);
                }})
                .show();
        }});
        LinearLayout c = section(body, "Tema");
        String t = prefs.getString("theme", "dark");
        segmented(c, "Modo", "Automático: claro de 6 a.m. a 6 p.m.", new String[]{"Oscuro", "Claro", "Automático"},
            "light".equals(t) ? 1 : "auto".equals(t) ? 2 : 0, new IntCb() { public void on(int i) {
                prefs.edit().putString("theme", i == 1 ? "light" : i == 2 ? "auto" : "dark").apply();
                applyTheme(); applyAccent(); rebuild();
            }});
        // Diseño del inicio (Bloque 2: comentarios de Reddit — Druids07, chiripiscuis, bouff1053, iitsFxcuu)
        LinearLayout lay = section(body, "Diseño del inicio");
        segmented(lay, "Reproductor de música", "Ponlo del lado del conductor para tenerlo a la mano", new String[]{"Derecha", "Izquierda"},
            playerLeft ? 1 : 0, new IntCb() { public void on(int i) {
                prefs.edit().putString("player_side", i == 1 ? "left" : "right").apply(); rebuild();
            }});
        toggle(lay, "Tarjeta del vehículo", "Si la ocultas, la velocidad pasa arriba y el mapa y el reproductor ganan espacio", showCar, new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("show_car", i == 1).apply(); rebuild();
        }});
        segmented(lay, "Barra de botones", "Lateral: los botones van al costado y todo se ve más grande en pantallas bajas como 1280×480", new String[]{"Normal", "Compacta", "Lateral"},
            sideDock ? 2 : compactDock ? 1 : 0, new IntCb() { public void on(int i) {
                prefs.edit().putString("dock_mode", i == 2 ? "side" : i == 1 ? "compact" : "normal").apply(); rebuild();
            }});
        LinearLayout a = section(body, "Color de acento");
        LinearLayout r = rowBase(a, "Color", "Botones, anillo del reproductor, ruta y etiquetas");
        LinearLayout dots = new LinearLayout(this);
        int cur = prefs.getInt("accent", 0xFFFE3211);
        for (int i = 0; i < ACCENT_COLORS.length; i++) {
            final int col = ACCENT_COLORS[i];
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            if (col == cur) g.setStroke((int) (3 * s), TEXT);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (28 * s), (int) (28 * s));
            lp.leftMargin = (int) (8 * s);
            dots.addView(d, lp);
            d.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                prefs.edit().putInt("accent", col).apply(); applyAccent(); rebuild();
            }});
        }
        r.addView(dots);
        LinearLayout un = section(body, "Unidades");
        String su = prefs.getString("speed_unit", "auto");
        segmented(un, "Velocidad", "Automático: mph en EE. UU. y Reino Unido", new String[]{"Auto", "km/h", "mph"}, "kmh".equals(su) ? 1 : "mph".equals(su) ? 2 : 0,
            new IntCb() { public void on(int i) {
                prefs.edit().putString("speed_unit", i == 1 ? "kmh" : i == 2 ? "mph" : "auto").apply(); rebuild(); showSettings(2);
            }});
        LinearLayout h = section(body, "Reloj");
        String ck = prefs.getString("clock", "auto");
        segmented(h, "Formato de hora", null, new String[]{"Auto", "12 h", "24 h"}, "12".equals(ck) ? 1 : "24".equals(ck) ? 2 : 0,
            new IntCb() { public void on(int i) {
                prefs.edit().putString("clock", i == 1 ? "12" : i == 2 ? "24" : "auto").apply(); updateClock(); showSettings(2);
            }});
    }

    private void catConnection(LinearLayout body) {
        LinearLayout c = section(body, "Celular");
        String chosen = prefs.getString("conn_app", null);
        row(c, "Cómo conectar el celular", "La app que se abre al conectar por USB", chosen != null ? labelOf(chosen) : "Preguntar",
            new Runnable() { public void run() { chooseConnection(null); } });
        row(c, "App de Android Auto", "Botón naranja de la tarjeta central", appLabel("aa", "Automática"),
            new Runnable() { public void run() { pickFor("aa", "Android Auto"); } });
        row(c, "App de CarPlay", "Requiere pantalla con CarPlay o un adaptador", appLabel("carplay", carPlayApp() != null ? labelOf(carPlayApp()) : "Ninguna"),
            new Runnable() { public void run() { pickFor("carplay", "CarPlay"); } });
        LinearLayout hl = section(body, "Ayuda");
        row(hl, "El celular dice «Ninguna aplic. funciona…»", "Cómo evitar que EasyConnection tome el USB", null,
            new Runnable() { public void run() { usbHelp(); } });
    }

    private void catApps(LinearLayout body) {
        LinearLayout c = section(body, "Barra inferior (déjalos presionados en el inicio para cambiarlos)");
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            String t = dockTarget(i);
            String what = t.startsWith("app:") ? "App: " + labelOf(t.substring(4)) : "Función: " + ("web".equals(t.substring(3)) ? "Navegador web" : dockFn(t.substring(3))[1]);
            row(c, "Botón " + (i + 1) + " · " + dockName(i), what, null, new Runnable() { public void run() {
                dockMenu(idx, new Runnable() { public void run() { if (settingsView != null) showSettings(4); } });
            }});
        }
        LinearLayout pf = section(body, "Apps preferidas");
        row(pf, "Música", "Se abre al tocar el reproductor", autoLabel("music_app", MUSIC_PKGS, "Toca para elegir"), new Runnable() { public void run() { pickFor("music_app", "Música"); } });
        row(pf, "Navegación", "Se abre con el botón del mapa", autoLabel("nav_app", NAV_PKGS, "Toca para elegir"), new Runnable() { public void run() { pickFor("nav_app", "Navegación"); } });
        LinearLayout dw = section(body, "Cajón de apps");
        int nh = hiddenApps().size();
        row(dw, "Apps ocultas", "No se ven en el cajón, pero siguen instaladas y se pueden poner en los botones", nh == 0 ? I18n.t("Ninguna") : String.valueOf(nh),
            new Runnable() { public void run() { manageHidden(new Runnable() { public void run() { if (settingsView != null) showSettings(4); } }); } });
        LinearLayout fa = section(body, "Apps de cada función");
        String[][] items = {{"radio", "Radio"}, {"bt", "Bluetooth"}, {"cam", "Cámara"}, {"web", "Navegador web"}, {"files", "Archivos"}};
        for (final String[] it : items) {
            row(fa, it[1], null, "web".equals(it[0]) ? appLabel(it[0], "Automático") : shortcutLabel(it[0]), new Runnable() { public void run() { pickFor(it[0], it[1]); } });
        }
        row(fa, "Música Bluetooth", "La app que hace sonar el Bluetooth (se usa para pasar el sonido)", shortcutLabel("btaudio"), new Runnable() { public void run() { pickFor("btaudio", "Música Bluetooth"); } });
        row(fa, "Revisar todos los botones", "Lista rápida de qué abre cada uno, como en el asistente", null, new Runnable() { public void run() {
            reviewShortcuts(new Runnable() { public void run() { if (settingsView != null) showSettings(4); } });
        }});
        LinearLayout r = section(body, "Restablecer");
        row(r, "Restablecer accesos", "La barra vuelve a lo de siempre y se buscan las apps de fábrica", null, new Runnable() { public void run() {
            SharedPreferences.Editor e = prefs.edit();
            for (String k : new String[]{"radio", "bt", "cam", "files", "settings", "aa", "web", "carplay", "music_app", "nav_app"}) e.remove(k);
            for (int i = 0; i < 7; i++) e.remove("dock_" + i).remove("dock_" + i + "_name");
            e.apply();
            toast("Accesos restablecidos");
            refreshStates();
            rebuild();
            showSettings(4);
        }});
    }

    private void catMapWeather(LinearLayout body) {
        LinearLayout w = section(body, "Clima");
        toggle(w, "Mostrar el clima", "Arriba, junto al saludo (necesita internet)", prefs.getBoolean("weather_on", true), new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("weather_on", i == 1).apply(); refreshWeatherNow(); showSettings(5);
        }});
        segmented(w, "Unidad", null, new String[]{"°C", "°F"}, "F".equals(prefs.getString("temp_unit", "C")) ? 1 : 0, new IntCb() { public void on(int i) {
            prefs.edit().putString("temp_unit", i == 1 ? "F" : "C").apply(); refreshWeatherNow(); showSettings(5);
        }});
        String manual = prefs.getString("city_manual", "");
        row(w, "Ciudad", "Vacío = automática por GPS", manual.length() > 0 ? manual : (weatherNow != null && weatherNow.city != null ? weatherNow.city + " (GPS)" : "Automática"),
            edit("city_manual", "Ciudad (vacío = automática por GPS)", ""));
        LinearLayout mo = section(body, "Orientación del mapa");
        segmented(mo, "El mapa apunta", "Según tu dirección: gira con el carro mientras te mueves", new String[]{"Norte arriba", "Según tu dirección"},
            prefs.getBoolean("map_heading", false) ? 1 : 0, new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("map_heading", i == 1).apply(); LiveMapView.headingUp = i == 1;
                if (liveMap != null) liveMap.invalidate(); showSettings(5);
            }});
        LinearLayout ml = section(body, "Rendimiento");
        toggle(ml, "Mapa en vivo", "Apágalo si la pantalla va lenta: queda un mapa de adorno y el velocímetro sigue funcionando", prefs.getBoolean("map_live", true), new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("map_live", i == 1).apply(); rebuild(); showSettings(5);
        }});
        LinearLayout nv = section(body, "Navegación");
        row(nv, "App de navegación preferida", "Se abre con el botón del mapa (Waze, Maps, OsmAnd…)", autoLabel("nav_app", NAV_PKGS, "Toca para elegir"),
            new Runnable() { public void run() { pickFor("nav_app", "Navegación"); } });
        LinearLayout m = section(body, "Mapa");
        segmented(m, "Brillo del mapa", "Solo en modo oscuro", new String[]{"Bajo", "Medio", "Alto"}, prefs.getInt("map_bright", 1), new IntCb() { public void on(int i) {
            prefs.edit().putInt("map_bright", i).apply(); rebuild();
        }});
        row(m, "Diagnóstico del mapa", "Fuentes probadas y errores", null, new Runnable() { public void run() {
            new TB(Main.this, dlg()).setTitle("Diagnóstico del mapa")
                .setMessage(mapDiag()).setPositiveButton("OK", null).show();
        }});
        row(m, "Fuente del mapa (avanzado)", "URL propia con {z}/{x}/{y}", prefs.getString("map_url", "").length() > 0 ? "Personalizada" : "Automática",
            edit("map_url", "URL de teselas {z}/{x}/{y} (vacío = automático)", ""));
        LinearLayout off = section(body, "Mapa sin conexión");
        LiveMapView.loadFile(this);
        String mf = prefs.getString("map_file", "");
        row(off, "Archivo del mapa", "Un archivo .map (p. ej. colombia.map) en la memoria de la pantalla o en una USB: el mapa se ve sin internet",
            mf.length() == 0 ? "Ninguno" : LiveMapView.fileMap != null ? new File(mf).getName() : "Error",
            new Runnable() { public void run() { pickMapFile(); } });
        if (mf.length() > 0) {
            row(off, LiveMapView.fileMap != null ? LiveMapView.fileMap.describe() : I18n.t("No se pudo abrir: " + LiveMapView.fileErr),
                "Toca para dejar de usarlo o borrarlo", null, new Runnable() { public void run() { mapFileOptions(); } });
        }
        row(off, "Descargar un mapa", "Elige tu país: se descarga gratis de OpenStreetMap (mapsforge) y se usa sin internet", null,
            new Runnable() { public void run() {
                if (MapDownload.running || MapDownload.paused(Main.this)) downloadOptions(); else chooseMapToDownload("");
            }});
        dlStatusTv = tv("", 13, RED, true);
        dlStatusTv.setPadding((int) (18 * s), (int) (4 * s), (int) (18 * s), (int) (12 * s));
        off.addView(dlStatusTv);
        refreshDownloadStatus();
        row(off, "¿Dónde consigo el mapa?", "Mapas gratis de OpenStreetMap por país", null, new Runnable() { public void run() { mapHowTo(); } });
    }

    private TextView dlStatusTv;
    private final Runnable dlTicker = new Runnable() { public void run() { refreshDownloadStatus(); } };

    /** Muestra el avance de la descarga debajo de «Descargar un mapa» (se actualiza cada segundo). */
    private void refreshDownloadStatus() {
        h.removeCallbacks(dlTicker);
        if (dlStatusTv == null || settingsView == null || dlStatusTv.getParent() == null) return;
        String st = MapDownload.status(this);
        dlStatusTv.setText(st == null ? "" : st);
        dlStatusTv.setVisibility(st == null ? View.GONE : View.VISIBLE);
        if (MapDownload.running) h.postDelayed(dlTicker, 1000);
    }

    /** Lista de continentes / países del servidor de mapas. */
    private boolean askedWrite;

    private void chooseMapToDownload(final String folder) {
        // Android 6–9: permiso para guardar en Download/StradaOS (se pide una sola vez). Android 10+ no lo tiene para
        // StradaOS: el mapa va a la carpeta propia de la app, sin pedir nada.
        if (folder.length() == 0 && SDK >= 23 && SDK <= 28 && !askedWrite
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askedWrite = true;
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE_W);
            return;
        }
        toast("Cargando la lista de mapas…");
        new Thread(new Runnable() { public void run() {
            Net.init(Main.this);
            java.util.List<MapDownload.Entry> list = null;
            String err = null;
            try { list = MapDownload.list(folder); } catch (Throwable e) { err = e instanceof java.net.UnknownHostException ? I18n.t("sin internet") : String.valueOf(e.getMessage()); }
            final java.util.List<MapDownload.Entry> fl = list;
            final String fe = err;
            h.post(new Runnable() { public void run() {
                if (isFinishing()) return;
                if (fl == null) {
                    new TB(Main.this, dlg()).setTitle("No se pudo cargar la lista de mapas")
                        .setMessage(fe + "\n\n" + I18n.t("Revisa la conexión a internet e inténtalo de nuevo."))
                        .setPositiveButton("OK", null).show();
                    return;
                }
                showMapList(folder, fl);
            }});
        }}).start();
    }

    private void showMapList(final String folder, final java.util.List<MapDownload.Entry> list) {
        String[] items = new String[list.size()];
        java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, I18n.loc);
        for (int i = 0; i < items.length; i++) {
            MapDownload.Entry e = list.get(i);
            if (e.dir) items[i] = e.name + (folder.length() > 0 ? "  ·  " + I18n.t("por regiones") : "") + "  ›";
            else {
                String date = e.date;
                try { date = df.format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(e.date)); } catch (Throwable ignored) { }
                items[i] = e.name + "  ·  " + MapDownload.mb(e.size) + "  ·  " + date;
            }
        }
        android.app.AlertDialog.Builder b = new TB(this, dlg()).setTitle(folder.length() == 0 ? "Elige el continente" : "Elige el mapa")
            .setItems(items, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                MapDownload.Entry e = list.get(w);
                if (e.dir) chooseMapToDownload(e.path); else confirmDownload(e);
            }});
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void confirmDownload(final MapDownload.Entry e) {
        File dir = MapDownload.folder(this);
        long free = MapDownload.free(dir.getParentFile() != null && !dir.exists() ? dir.getParentFile() : dir);
        String where = dir.getAbsolutePath().replace(android.os.Environment.getExternalStorageDirectory().getAbsolutePath(), I18n.t("memoria"));
        String msg = I18n.t("Tamaño") + ": " + MapDownload.mb(e.size) + "\n" + I18n.t("Se guarda en") + ": " + where
            + (free >= 0 ? "\n" + I18n.t("Espacio libre") + ": " + MapDownload.mb(free) : "")
            + "\n\n" + I18n.t("Usa datos: mejor con WiFi o un plan amplio. Puedes seguir usando la pantalla; si se corta la señal o la apagas, la descarga continúa después.");
        boolean noRoom = free >= 0 && free < e.size + 20L * 1024 * 1024;
        android.app.AlertDialog.Builder b = new TB(this, dlg()).setTitle(I18n.t("Descargar") + " " + e.name).setMessage(noRoom ? msg + "\n\n⚠ " + I18n.t("No hay espacio suficiente en la memoria.") : msg)
            .setNegativeButton("Cancelar", null);
        if (!noRoom) b.setPositiveButton("Descargar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            prefs.edit().putBoolean("map_dl_user_paused", false).apply();
            MapDownload.start(Main.this, e.path, e.name);
            toast("Descargando el mapa… puedes seguir usando la pantalla");
            refreshDownloadStatus();
        }});
        b.show();
    }

    /** Descarga en curso o en pausa: pausar, continuar o cancelar. */
    private void downloadOptions() {
        final boolean run = MapDownload.running;
        android.app.AlertDialog.Builder b = new TB(this, dlg()).setTitle(run ? "Descarga en curso" : "Descarga en pausa").setMessage(MapDownload.status(this));
        if (run) b.setPositiveButton("Pausar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            prefs.edit().putBoolean("map_dl_user_paused", true).apply();
            MapDownload.stop(false);
        }});
        else b.setPositiveButton("Continuar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            prefs.edit().putBoolean("map_dl_user_paused", false).apply();
            MapDownload.resume(Main.this);
            refreshDownloadStatus();
        }});
        b.setNeutralButton("Cancelar descarga", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            if (MapDownload.running) MapDownload.stop(true);
            else {
                String dest = prefs.getString("map_dl_dest", "");
                if (dest.length() > 0) new File(dest + ".part").delete();
                prefs.edit().remove("map_dl_path").remove("map_dl_name").remove("map_dl_dest").remove("map_dl_etag").remove("map_dl_total").apply();
                MapDownload.error = null;
            }
            toast("Descarga cancelada");
            if (settingsView != null) { rebuild(); showSettings(5); }
        }});
        b.setNegativeButton("Cerrar", null);
        b.show();
    }

    /** Mapa en uso: dejar de usarlo o, si lo descargó StradaOS, borrarlo. */
    private void mapFileOptions() {
        final File f = new File(prefs.getString("map_file", ""));
        File dir = MapDownload.folder(this);
        boolean ours = f.getParentFile() != null && f.getParentFile().equals(dir);
        android.app.AlertDialog.Builder b = new TB(this, dlg()).setTitle(f.getName())
            .setMessage(ours ? "Este mapa lo descargó StradaOS. Puedes dejar de usarlo o borrarlo para liberar espacio." : "Puedes dejar de usar este mapa (el archivo no se borra).")
            .setPositiveButton("Dejar de usarlo", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                prefs.edit().remove("map_file").apply();
                LiveMapView.loadFile(Main.this);
                toast("Mapa sin conexión quitado");
                rebuild(); showSettings(5);
            }})
            .setNegativeButton("Cancelar", null);
        if (ours) b.setNeutralButton("Borrar el archivo", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            prefs.edit().remove("map_file").apply();
            LiveMapView.loadFile(Main.this);
            f.delete();
            toast("Mapa borrado");
            rebuild(); showSettings(5);
        }});
        b.show();
    }

    /** Fin de una descarga (correcta o con error). */
    private void onMapDownloadFinished() {
        if (MapDownload.error != null) {
            toast(I18n.t("Descarga en pausa") + ": " + MapDownload.error);
        } else if (MapDownload.finishedOk && LiveMapView.fileMap != null) {
            MapDownload.finishedOk = false;
            toast("Mapa descargado y listo: " + LiveMapView.fileMap.file.getName());
            rebuild();
            if (settingsView != null) showSettings(5);
            return;
        }
        refreshDownloadStatus();
    }

    /** Ayuda: de dónde bajar un mapa .map y dónde ponerlo. */
    private void mapHowTo() {
        new TB(this, dlg()).setTitle("Mapa sin conexión")
            .setMessage("1. En un computador o celular, descarga el mapa de tu país (formato mapsforge, gratis):\ndownload.mapsforge.org/maps/v5\n\nColombia:\ndownload.mapsforge.org/maps/v5/south-america/colombia.map\n\n2. Cópialo a una memoria USB o a la carpeta Download de la pantalla.\n\n3. En Ajustes → Mapa y clima → Mapa sin conexión, toca «Archivo del mapa» y elígelo.\n\nEl mapa se usa en el inicio incluso con internet. Para actualizarlo, descarga uno nuevo y reemplaza el archivo.")
            .setPositiveButton("OK", null).show();
    }

    private static final int REQ_STORAGE = 79, REQ_STORAGE_W = 80;

    /** Busca archivos .map en la memoria y en las USB, y deja elegir uno. */
    private void pickMapFile() {
        if (SDK >= 23 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        toast("Buscando mapas…");
        new Thread(new Runnable() { public void run() {
            final java.util.List<File> found = findMapFiles();
            h.post(new Runnable() { public void run() { showMapFiles(found); } });
        }}).start();
    }

    private void showMapFiles(final java.util.List<File> found) {
        if (isFinishing()) return;
        if (found.isEmpty()) {
            new TB(this, dlg()).setTitle("No se encontró ningún mapa")
                .setMessage("No hay archivos .map en la memoria de la pantalla ni en las USB conectadas. Toca «Cómo conseguirlo» para ver de dónde bajarlo.")
                .setPositiveButton("Cómo conseguirlo", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { mapHowTo(); } })
                .setNegativeButton("Cerrar", null).show();
            return;
        }
        String[] items = new String[found.size()];
        for (int i = 0; i < items.length; i++) {
            File f = found.get(i);
            items[i] = f.getName() + "  ·  " + (f.length() / (1024 * 1024)) + " MB\n" + f.getParent();
        }
        new TB(this, dlg()).setTitle("Elige el mapa")
            .setItems(items, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                File f = found.get(w);
                prefs.edit().putString("map_file", f.getAbsolutePath()).apply();
                LiveMapView.loadFile(Main.this);
                if (LiveMapView.fileMap == null) {
                    prefs.edit().remove("map_file").apply();
                    new TB(Main.this, dlg()).setTitle("No se pudo usar este archivo")
                        .setMessage(LiveMapView.fileErr + "\n\n" + I18n.t("Debe ser un mapa de mapsforge (.map) copiado completo."))
                        .setPositiveButton("OK", null).show();
                    return;
                }
                toast("Mapa sin conexión listo: " + f.getName());
                rebuild();
                if (settingsView != null) showSettings(5);
            }})
            .setNegativeButton("Cancelar", null).show();
    }

    /** Archivos .map de más de 1 MB en la memoria interna, tarjetas y USB (hasta 4 carpetas de profundidad). */
    private java.util.List<File> findMapFiles() {
        java.util.LinkedHashSet<String> roots = new java.util.LinkedHashSet<String>();
        roots.add(android.os.Environment.getExternalStorageDirectory().getAbsolutePath());
        String sec = System.getenv("SECONDARY_STORAGE");
        if (sec != null) for (String r : sec.split(":")) if (r.length() > 0) roots.add(r);
        roots.add("/storage");
        roots.add("/mnt");
        java.util.ArrayList<File> out = new java.util.ArrayList<File>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        int[] budget = {4000};
        for (String r : roots) scanMaps(new File(r), 0, out, seen, budget);
        java.util.Collections.sort(out, new java.util.Comparator<File>() {
            public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });
        return out;
    }

    private void scanMaps(File d, int depth, java.util.List<File> out, java.util.Set<String> seen, int[] budget) {
        if (depth > 4 || budget[0]-- <= 0) return;
        String canon;
        try { canon = d.getCanonicalPath(); } catch (Throwable e) { return; }
        if (!seen.add(canon)) return;
        File[] fs;
        try { fs = d.listFiles(); } catch (Throwable e) { return; }
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName();
            if (n.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (n.equals("Android") || n.equals("obb") || n.equals("asec") || n.equals("secure") || n.equals("runtime")) continue;
                scanMaps(f, depth + 1, out, seen, budget);
            } else if (n.toLowerCase(java.util.Locale.US).endsWith(".map") && f.length() > 1024 * 1024) {
                String c;
                try { c = f.getCanonicalPath(); } catch (Throwable e) { c = f.getAbsolutePath(); }
                if (seen.add(c)) out.add(f);
            }
        }
    }

    private void catMusic(LinearLayout body) {
        LinearLayout ma = section(body, "App de música");
        row(ma, "App de música preferida", "Se abre al tocar el reproductor del inicio", autoLabel("music_app", MUSIC_PKGS, "Toca para elegir"),
            new Runnable() { public void run() { pickFor("music_app", "Música"); } });
        LinearLayout c = section(body, "Pista en reproducción");
        row(c, "Carátula de Android Auto", "Lee la notificación de Open Headunit (accesibilidad)", NotifArt.running ? "Activada" : "Desactivada",
            new Runnable() { public void run() { openAccessibility(); } });
        if (SDK >= 18) row(c, "Música de cualquier app", "Android 5+: acceso a notificaciones", MediaListener.connected ? "Activado" : "Desactivado",
            new Runnable() { public void run() {
                try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Throwable e) { toast("No se pudo abrir el ajuste"); }
            }});
        row(c, "Diagnóstico del reproductor", "Fuente activa, lector de notificaciones y Bluetooth", null, new Runnable() { public void run() { showPlayerDiag(); } });
        LinearLayout b = section(body, "Audio Bluetooth");
        row(b, "¿La música Bluetooth se corta o no aparece?", "Qué hacer en cada caso", null, new Runnable() { public void run() { btHelp(); } });
        toggle(b, "Volver al Bluetooth al desconectar Android Auto", "Al quitar el cable, abre un instante la app de música Bluetooth para que el sonido siga por Bluetooth",
            prefs.getBoolean("aa_off_bt", true), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("aa_off_bt", i == 1).apply(); showSettings(6);
            }});
        if (clwBt) {
            row(b, "Escuchar el Bluetooth", "En esta pantalla, toca ▶ en el inicio: StradaOS pasa el sonido a Bluetooth (por ejemplo, si venías de la radio)", null, null);
            toggle(b, "Pasar a Bluetooth al dar play en el celular (en prueba)", "Abre un instante «Audio Bluetooth» y vuelve. En algunas pantallas no detecta el play del celular",
                prefs.getBoolean("bt_auto3", false), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("bt_auto3", i == 1).apply(); showSettings(6);
                }});
            row(b, "Probar ahora", "Abre «Audio Bluetooth» un instante y vuelve al inicio", null,
                new Runnable() { public void run() { closeSettings(); openBtAudioApp(true); } });
        } else {
            toggle(b, "Abrir la app de música Bluetooth al conectar el celular", "Para pantallas que solo dejan sonar el Bluetooth con su app abierta: la abre un momento y vuelve al inicio",
                prefs.getBoolean("bt_open_connect", false), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("bt_open_connect", i == 1).apply(); showSettings(6);
                }});
            ComponentName cn = btMusicApp();
            row(b, "App de música Bluetooth", "La que hace sonar el Bluetooth en esta pantalla",
                cn != null ? labelOf(cn.flattenToString()) : "No encontrada",
                new Runnable() { public void run() { pickFor("btaudio", "Música Bluetooth"); } });
            segmented(b, "Tiempo antes de volver al inicio", "Súbelo si el audio se corta al volver", new String[]{"1,5 s", "2,5 s", "4 s"},
                prefs.getInt("bt_return_ms", 2500) <= 1500 ? 0 : prefs.getInt("bt_return_ms", 2500) >= 4000 ? 2 : 1, new IntCb() { public void on(int i) {
                    prefs.edit().putInt("bt_return_ms", i == 0 ? 1500 : i == 2 ? 4000 : 2500).apply(); showSettings(6);
                }});
            row(b, "Probar ahora", "Abre la app de música Bluetooth y vuelve al inicio", null,
                new Runnable() { public void run() { closeSettings(); openBtAudioApp(true); } });
        }
        LinearLayout a = section(body, "Avanzado");
        row(a, "Avisos extra de música", "Nombres de avisos (broadcast) de otros equipos", prefs.getString("extra_actions", "").length() > 0 ? "Configurados" : "Ninguno",
            edit("extra_actions", "Acciones de aviso extra (separadas por coma)", ""));
    }

    private void catSystem(LinearLayout body) {
        LinearLayout hm = section(body, "Pantalla de inicio");
        final boolean def = isDefaultHome();
        row(hm, "Pantalla de inicio", "La que aparece al encender y al tocar Inicio", def ? "StradaOS ✓" : "Otra · toca para cambiar", new Runnable() { public void run() {
            if (isDefaultHome()) homeHelp(); else makeDefaultHome();
        }});
        toggle(hm, "Abrir StradaOS al encender", "Para pantallas que siempre arrancan en su menú de fábrica (Android 4 a 9)", prefs.getBoolean("boot_open", false), new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("boot_open", i == 1).apply(); showSettings(7);
        }});
        // Salidas (issue #9): una pantalla Android 11 quedó atrapada en StradaOS tras elegirlo como launcher desde su
        // modo fábrica (toda app, incluso la ventana de desinstalar, se abría y volvía a StradaOS).
        LinearLayout out = section(body, "Salir de StradaOS");
        row(out, "Abrir el launcher de fábrica", "Sale de StradaOS sin desinstalarlo", null, new Runnable() { public void run() { openFactoryLauncher(); } });
        row(out, "Desactivar StradaOS", "Si la pantalla quedó atrapada en StradaOS: se apaga solo, sin ventanas de Android", null, new Runnable() { public void run() { confirmDisableSelf(); } });
        row(out, "Desinstalar StradaOS", "Android pide confirmar", null, new Runnable() { public void run() { uninstallSelf(); } });
        if (getPackageName().endsWith(".debug")) {
            LinearLayout sm = section(body, "Versión de prueba");
            segmented(sm, "Simular pantalla", "Para probar otros tamaños en una tablet o celular", new String[]{"Real", "1024×600", "1280×720", "1920×720", "2000×1200", "1280×480"},
                prefs.getInt("sim_screen", 0), new IntCb() { public void on(int i) {
                    prefs.edit().putInt("sim_screen", i).apply(); rebuild(); showSettings(7);
                }});
        }
        LinearLayout c = section(body, "Equipo");
        row(c, "Ajustes del equipo", "Los ajustes de la pantalla (los de la marca si los tiene)", null, new Runnable() { public void run() {
            openSystemSettings();
        }});
        row(c, "Asistente de configuración inicial", "Nombre, vehículo, color y foto", null, new Runnable() { public void run() { closeSettings(); wizard(); } });
        LinearLayout k = section(body, "Compatibilidad");
        row(k, "Qué funciona en esta pantalla", "Cada función, desde qué Android sirve y su estado aquí", null, new Runnable() { public void run() { showCompat(); } });
        LinearLayout d = section(body, "Soporte");
        row(d, "Exportar diagnóstico", "Guarda información de las apps de audio en la memoria interna", null, new Runnable() { public void run() { runDiag(); } });
    }

    /** Tabla de compatibilidad: qué función sirve desde qué Android o marca de pantalla, y cómo está aquí. */
    private void showCompat() {
        StringBuilder b = new StringBuilder();
        b.append(I18n.t("Esta pantalla:")).append(" Android ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(SDK).append(")")
         .append(" · ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
        if (!android.os.Build.VERSION.RELEASE.startsWith(androidName(SDK).split(" ")[0]))
            b.append(I18n.t("Ojo: la pantalla dice Android " + android.os.Build.VERSION.RELEASE + ", pero por dentro es " + androidName(SDK) + ".")).append("\n");
        b.append("\n");
        compatLine(b, "Inicio, mapa, clima, velocímetro", "Android 4.1+", true, null);
        compatLine(b, "Pista y carátula por Bluetooth", "Android 4.1+", true, clwBt ? "app Bluetooth de fábrica detectada" : "depende de la marca");
        compatLine(b, "Botón ▶ del inicio pasa el sonido a Bluetooth", "Pantallas Clinkway / Autochips", clwBt, clwBt ? "funciona aunque vengas de la radio" : "en otras marcas: «Abrir la app de música Bluetooth al conectar»");
        compatLine(b, "Pasar a Bluetooth al dar play en el celular (en prueba)", "Pantallas Clinkway / Autochips", clwBt, clwBt ? (btAutoOn() ? "activado" : "desactivado; en algunas pantallas no detecta el play del celular") : null);
        compatLine(b, "Carátula de Android Auto (Open Headunit)", "Android 4.1+ · accesibilidad", true, NotifArt.running ? "activada" : "falta activar la accesibilidad");
        compatLine(b, "Música de cualquier app (Spotify, YouTube…)", "Android 5+", SDK >= 21, SDK >= 21 ? (MediaListener.connected ? "activada" : "falta dar acceso a notificaciones") : "no disponible en " + androidName(SDK));
        compatLine(b, "Android Auto con Open Headunit", "Android 4.1+ · cable USB", true, installed(PKG_HUR) ? "Open Headunit instalado" : "falta instalar Open Headunit");
        compatLine(b, "Android Auto / CarPlay de la pantalla", "Si la pantalla lo trae", nativeProjectionApp() != null, nativeProjectionApp() != null ? labelOf(nativeProjectionApp()) : "no detectado");
        compatLine(b, "CarPlay", "Pantalla con CarPlay o adaptador USB", carPlayApp() != null, carPlayApp() != null ? labelOf(carPlayApp()) : "no detectado");
        compatLine(b, "Mapa con internet seguro (TLS 1.2)", "Android 4.1+ (StradaOS lo activa)", true, SDK < 20 ? "en Android 4.x solo algunos servidores de mapas responden" : null);
        compatLine(b, "Instalar actualizaciones desde la app", "Android 4.1+", true, SDK >= 26 ? "pide permiso «Instalar apps desconocidas»" : null);
        new TB(this, dlg()).setTitle("Compatibilidad").setMessage(b.toString().trim()).setPositiveButton("OK", null).show();
    }

    private static void compatLine(StringBuilder b, String name, String needs, boolean ok, String note) {
        b.append(ok ? "✓ " : "✗ ").append(I18n.t(name)).append("\n     ").append(I18n.t("Requiere:")).append(" ").append(I18n.t(needs));
        if (note != null) b.append("\n     ").append(I18n.t("Aquí:")).append(" ").append(I18n.t(note));
        b.append("\n\n");
    }

    static String androidName(int api) {
        String[] n = {"4.1", "4.2", "4.3", "4.4", "4.4W", "5.0", "5.1", "6.0", "7.0", "7.1", "8.0", "8.1", "9", "10", "11", "12", "12L", "13", "14", "15", "16", "17"};
        int i = api - 16;
        return i >= 0 && i < n.length ? "Android " + n[i] : "API " + api;
    }

    private void btHelp() {
        new TB(this, dlg())
            .setTitle("Música Bluetooth")
            .setMessage("• Se corta al salir de la app Bluetooth: sal con el botón Inicio (la casita), no con Atrás. Atrás cierra la app de fábrica y con ella la música.\n\n• No suena hasta abrir la app Bluetooth: activa «Abrir la app de música Bluetooth al conectar el celular» (aquí mismo). Abre la app un instante y vuelve al inicio.\n\n• La canción no aparece en el inicio (Android 5 o más nuevo): activa «Música de cualquier app» arriba y dale acceso a notificaciones a StradaOS.\n\n• Revisa en Ajustes → Accesos y apps que «Música Bluetooth» sea la app Bluetooth de tu pantalla.")
            .setPositiveButton("Entendido", null)
            .show();
    }

    private void showGuardLog() {
        String t = Guard.read(this);
        if (t == null) { toast("No hay fallos registrados"); return; }
        if (t.length() > 6000) t = "…" + t.substring(t.length() - 6000);
        TextView v = tv(t, 12, TEXT, false);
        v.setTextIsSelectable(true);
        v.setPadding((int) (16 * s), (int) (8 * s), (int) (16 * s), (int) (8 * s));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(v);
        new TB(this, dlg())
            .setTitle("Registro de fallos")
            .setView(sv)
            .setPositiveButton("OK", null)
            .setNeutralButton("Borrar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                Guard.clear(Main.this); toast("Registro borrado"); if (settingsView != null) showSettings(8); } })
            .show();
    }

    private void catAbout(LinearLayout body) {
        LinearLayout c = section(body, "StradaOS");
        LinearLayout r = rowBase(c, "StradaOS · Auto Head Unit UI", "Gratis y de código abierto. Una nueva vida para las pantallas Android del carro.");
        ImageView logo = new ImageView(this);
        logo.setImageBitmap(asset("logo.png"));
        r.addView(logo, 0, new LinearLayout.LayoutParams((int) (56 * s), (int) (56 * s)));
        ((LinearLayout.LayoutParams) r.getChildAt(1).getLayoutParams()).leftMargin = (int) (14 * s);
        row(c, "Versión", null, Updates.currentVersion(this), null);
        row(c, "Android de la pantalla", "Real: " + androidName(SDK) + " · ver qué funciona aquí", android.os.Build.VERSION.RELEASE + " (API " + SDK + ")", new Runnable() { public void run() { showCompat(); } });
        row(c, "Registro de fallos", "Cierres inesperados o bloqueos de StradaOS en esta pantalla", Guard.read(this) == null ? "Sin fallos" : "Ver",
            new Runnable() { public void run() { showGuardLog(); } });

        LinearLayout u = section(body, "Actualizaciones");
        final boolean fd = Updates.fromFdroid(this);
        if (fd) row(u, "Instalado desde " + Updates.storeName(this), "Las actualizaciones llegan por esa app", null, null);
        toggle(u, fd ? "Avisar también de versiones en GitHub" : "Comprobar actualizaciones automáticamente",
            fd ? "Opcional: las descargas de GitHub no pasan por la revisión de la tienda" : "Al abrir StradaOS con internet, una vez al día",
            prefs.getBoolean("auto_update", !fd), new IntCb() { public void on(final int i) {
                if (i == 1 && fd) {
                    new TB(Main.this, dlg()).setTitle("Actualizaciones desde GitHub")
                        .setMessage("Instalaste StradaOS desde " + Updates.storeName(Main.this) + ". Si activas esto, StradaOS revisará una vez al día las versiones publicadas en github.com/" + Updates.OWNER + "/" + Updates.REPO + " y te ofrecerá descargarlas desde ahí.\n\nEsas descargas no pasan por la revisión de la tienda. Puede que GitHub tenga una versión un poco antes que la tienda.")
                        .setPositiveButton("Activar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                            prefs.edit().putBoolean("auto_update", true).apply(); showSettings(8); } })
                        .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { showSettings(8); } })
                        .show();
                    return;
                }
                prefs.edit().putBoolean("auto_update", i == 1).apply(); showSettings(8);
            }});
        row(u, "Canal de actualizaciones", null, fd ? Updates.storeName(this) : "GitHub Releases", null);
        long last = prefs.getLong("upd_last", 0);
        row(u, "Buscar actualizaciones", last == 0 ? "Aún no se ha comprobado"
                : "Última comprobación: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", ES).format(new Date(last)), null,
            new Runnable() { public void run() { checkUpdates(true); } });

        LinearLayout k = section(body, "Comunidad");
        row(k, "Dar una estrella en GitHub", "Ayuda a que más personas encuentren StradaOS", "★",
            new Runnable() { public void run() { linkDialog("Dar una estrella ⭐", "Abre el repositorio y toca «Star». ¡Gracias!", Updates.REPO_URL); } });
        row(k, "Donar", "Si StradaOS revivió tu pantalla, invítame un café", "☕",
            new Runnable() { public void run() { linkDialog("Apoya el proyecto ☕", "StradaOS es gratis. Las donaciones ayudan a mantenerlo y a probarlo en más pantallas.", Updates.donateUrl(Main.this)); } });
        row(k, "Reportar un problema o sugerir algo", "Crea un «issue» en GitHub (adjunta el diagnóstico)", null,
            new Runnable() { public void run() { linkDialog("Reportar un problema", "Cuenta tu modelo de pantalla y su versión de Android. Puedes adjuntar la carpeta StradaDiag (Sistema → Exportar diagnóstico).", Updates.ISSUES_URL); } });
        row(k, "Código fuente", Updates.REPO_URL.replace("https://", ""), null,
            new Runnable() { public void run() { linkDialog("Código fuente", "Licencia GPL-3.0", Updates.REPO_URL); } });

        LinearLayout l = section(body, "Créditos y licencias");
        row(l, "Licencia", "GNU General Public License v3.0", null, null);
        row(l, "Mapas", "© colaboradores de OpenStreetMap, OpenTopoMap, Esri", null, null);
        row(l, "Clima", "Open-Meteo.com (CC BY 4.0)", null, null);
        row(l, "Android Auto", "Open Headunit (GPL-3.0) · Android Auto es marca de Google", null, null);
        row(l, "Códigos QR", "QR Code generator library · Project Nayuki (MIT)", null, null);
        row(l, "Íconos", "Material Symbols (Apache 2.0)", null, null);
        row(l, "Certificados raíz", "Mozilla CA (certifi, MPL-2.0)", null, null);
    }

    // ───────────────────────── actualizaciones, estrella y donaciones ─────────────────────────

    private boolean updBusy, promptShown;

    /** Al volver al inicio: busca versión nueva (1 vez al día) o, si toca, el recuadro «¿Te gusta StradaOS?». */
    private void autoChecks() {
        if (!hasWindowFocus() || settingsView != null || drawer != null) return;
        if (!homeAsked && prefs.getBoolean("setup_done", false) && !prefs.getBoolean("home_never", false) && askDefaultHome(null)) return;
        if (oilAlert()) return;
        long now = System.currentTimeMillis();
        boolean auto = prefs.getBoolean("auto_update", !Updates.fromFdroid(this));
        if (getPackageName().endsWith(".debug")) auto = false;   // la versión de prueba no ofrece actualizar a la normal
        if (auto && now - prefs.getLong("upd_last", 0) > 20L * 3600 * 1000) { checkUpdates(false); return; }
        // recuadro tipo SimpMusic: tras 5 días de uso y 15 aperturas, y luego cada 45 días si eligió «Más tarde»
        int opens = prefs.getInt("opens", 0) + 1;
        long first = prefs.getLong("first_use", 0);
        SharedPreferences.Editor e = prefs.edit().putInt("opens", opens);
        if (first == 0) e.putLong("first_use", first = now);
        e.apply();
        long next = prefs.getLong("enjoy_next", first + 5L * 24 * 3600 * 1000);
        if (!promptShown && opens >= 15 && now >= next && !prefs.getBoolean("enjoy_done", false)) {
            promptShown = true;
            enjoyDialog();
        }
    }

    private void checkUpdates(final boolean manual) {
        if (updBusy) return;
        updBusy = true;
        if (manual) toast("Buscando actualizaciones…");
        Updates.check(this, new Updates.Listener() {
            public void onResult(final Updates.Release r, final String err) {
                h.post(new Runnable() { public void run() {
                    updBusy = false;
                    if (settingsView != null && settingsCat == 8) showSettings(8);
                    if (r != null) {
                        if (manual || !r.version.equals(prefs.getString("upd_skip", ""))) updateDialog(r);
                    } else if (manual) {
                        if (err != null) linkDialog("No se pudo comprobar", "Esta pantalla no logró conectarse con GitHub (" + shortNetErr(err) + "). Puedes revisar las versiones desde tu celular:", Updates.RELEASES_URL);
                        else new TB(Main.this, dlg()).setTitle("Tienes la última versión (" + Updates.currentVersion(Main.this) + ")")
                            .setMessage("Fuentes consultadas:\n" + Updates.lastReport).setPositiveButton("OK", null).show();
                    }
                }});
            }
        });
    }

    private static String shortNetErr(String e) {
        if (e.contains("SSL") || e.contains("handshake")) return "cifrado no compatible";
        if (e.contains("UnknownHost")) return "sin internet";
        if (e.contains("Timeout")) return "tiempo agotado";
        return "error de red";
    }

    private void updateDialog(final Updates.Release r) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * s);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView v = tv("Tienes la " + Updates.currentVersion(this) + " · nueva: " + r.version, 13, MUTED, false);
        box.addView(v);
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        TextView notes = tv(Updates.plain(r.notes).length() > 0 ? Updates.plain(r.notes) : "Mejoras y correcciones.", 14, TEXT, false);
        notes.setSingleLine(false);
        notes.setPadding(0, (int) (10 * s), 0, (int) (10 * s));
        sc.addView(notes);
        box.addView(sc, new LinearLayout.LayoutParams(-1, (int) (200 * s)));
        AlertDialog.Builder b = new TB(this, dlg())
            .setTitle("Nueva versión de StradaOS")
            .setView(box);
        if (Updates.fromFdroid(this) && !prefs.getBoolean("auto_update", false)) {
            // instalada desde una tienda: se actualiza por ahí; GitHub queda como opción explícita
            v.setText(I18n.t("Tienes la " + Updates.currentVersion(this) + " · en GitHub: " + r.version
                + "\nActualiza desde " + Updates.storeName(this) + " cuando llegue. Descargar de GitHub omite la revisión de la tienda."));
            b.setPositiveButton("Entendido", null)
             .setNeutralButton("Ver en GitHub", new DialogInterface.OnClickListener() {
                 public void onClick(DialogInterface d, int w) { linkDialog("StradaOS " + r.version, "Descarga desde GitHub:", r.pageUrl); } })
             .show();
            return;
        }
        b
            .setPositiveButton(r.apkUrl != null ? "Descargar e instalar" : "Ver en GitHub", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (r.apkUrl != null) downloadUpdate(r); else linkDialog("StradaOS " + r.version, "Descarga la nueva versión desde:", r.pageUrl);
                }
            })
            .setNeutralButton("Omitir esta versión", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { prefs.edit().putString("upd_skip", r.version).apply(); }
            })
            .setNegativeButton("Más tarde", null);
        b.show();
    }

    /** Descarga el APK y abre el instalador; si esta pantalla no puede conectarse, muestra el código QR. */
    private void downloadUpdate(final Updates.Release r) {
        if (!unknownSourcesOk()) return;
        final ProgressDialogLite pd = new ProgressDialogLite("Descargando StradaOS " + r.version + "…");
        new Thread(new Runnable() { public void run() {
            File out = null;
            try {
                if (r.apkUrl == null || !r.apkUrl.startsWith(Updates.APK_PREFIX)) throw new Exception("origen no permitido");
                byte[] data = Net.get(r.apkUrl, 60000);
                File dir = getExternalFilesDir(null);
                if (dir == null) dir = getFilesDir();
                dir.mkdirs();
                out = new File(dir, "StradaOS-" + r.version + ".apk");
                java.io.FileOutputStream os = new java.io.FileOutputStream(out);
                os.write(data);
                os.close();
                out.setReadable(true, false);
                if (!verifyApk(out)) {
                    Guard.log("Actualización rechazada: el APK descargado no es StradaOS firmado con la misma llave.");
                    out.delete();
                    out = null;
                }
            } catch (Throwable e) { out = null; }
            final File f = out;
            runOnUiThread(new Runnable() { public void run() {
                pd.dismiss();
                if (f != null) installFile(f, "StradaOS " + r.version);
                else linkDialog("Descárgala desde tu celular", "Esta pantalla no pudo descargar el archivo. Escanea el código, descarga el APK y pásalo por USB.", r.pageUrl);
            }});
        }}).start();
    }

    private void enjoyDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (22 * s);
        box.setPadding(pad, pad, pad, (int) (6 * s));
        ImageView logo = new ImageView(this);
        logo.setImageBitmap(asset("logo.png"));
        box.addView(logo, new LinearLayout.LayoutParams((int) (64 * s), (int) (64 * s)));
        TextView t = tv("¿Disfrutando StradaOS?", 20, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, (int) (14 * s), 0, (int) (8 * s));
        box.addView(t);
        TextView m = tv("Si te gusta usar StradaOS, dale una estrella en GitHub. Y si revivió tu pantalla, considera invitarme un café ☕", 14, SOFT, false);
        m.setSingleLine(false);
        m.setGravity(Gravity.CENTER);
        box.addView(m);
        new TB(this, dlg())
            .setView(box)
            .setPositiveButton("Dar una estrella", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    prefs.edit().putBoolean("enjoy_done", true).apply();
                    linkDialog("Dar una estrella ⭐", "Abre el repositorio y toca «Star». ¡Gracias!", Updates.REPO_URL);
                }
            })
            .setNeutralButton("Donar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    prefs.edit().putBoolean("enjoy_done", true).apply();
                    linkDialog("Apoya el proyecto ☕", "StradaOS es gratis. Las donaciones ayudan a mantenerlo.", Updates.donateUrl(Main.this));
                }
            })
            .setNegativeButton("Más tarde", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    prefs.edit().putLong("enjoy_next", System.currentTimeMillis() + 45L * 24 * 3600 * 1000).apply();
                }
            })
            .show();
    }

    /** Enlace con código QR: en pantallas viejas el navegador no abre GitHub, pero el celular sí. */
    private void linkDialog(String title, String msg, final String url) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (20 * s);
        box.setPadding(pad, pad, pad, (int) (6 * s));
        ImageView qr = new ImageView(this);
        qr.setImageBitmap(qrBitmap(url, (int) (200 * s)));
        box.addView(qr, new LinearLayout.LayoutParams((int) (200 * s), (int) (200 * s)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (20 * s), 0, 0, 0);
        TextView m = tv(msg, 14, SOFT, false);
        m.setSingleLine(false);
        col.addView(m);
        TextView hint = tv("Escanéalo con la cámara de tu celular o entra a:", 12, MUTED, false);
        hint.setSingleLine(false);
        hint.setPadding(0, (int) (12 * s), 0, (int) (4 * s));
        col.addView(hint);
        TextView link = tv(url.replace("https://", ""), 13, RED, true);
        link.setSingleLine(false);
        col.addView(link);
        box.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        new TB(this, dlg())
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Abrir aquí", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                    catch (Throwable e) { toast("No hay navegador disponible"); }
                }
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private static Bitmap qrBitmap(String text, int size) {
        io.nayuki.qrcodegen.QrCode q = io.nayuki.qrcodegen.QrCode.encodeText(text, io.nayuki.qrcodegen.QrCode.Ecc.MEDIUM);
        int border = 2, n = q.size + border * 2;
        int scale = Math.max(1, size / n);
        Bitmap b = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.RGB_565);
        b.eraseColor(Color.WHITE);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(Color.BLACK);
        for (int y = 0; y < q.size; y++)
            for (int x = 0; x < q.size; x++)
                if (q.getModule(x, y)) c.drawRect((x + border) * scale, (y + border) * scale, (x + border + 1) * scale, (y + border + 1) * scale, p);
        return b;
    }

    // ───────────────────────── apps ─────────────────────────

    private void loadApps() {
        apps.clear();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        final PackageManager pm = getPackageManager();
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            if (!getPackageName().equals(r.activityInfo.packageName)) apps.add(r);
        }
        final HashMap<ResolveInfo, String> labels = new HashMap<ResolveInfo, String>();
        for (ResolveInfo r : apps) labels.put(r, String.valueOf(r.loadLabel(pm)));
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            public int compare(ResolveInfo a, ResolveInfo b) {
                return labels.get(a).compareToIgnoreCase(labels.get(b));
            }
        });
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void refreshStates() {
        if (aaBtnTv == null) return;
        String nat = nativeProjectionApp();
        String aaSaved = prefs.getString("aa", null);
        String chosen = prefs.getString("conn_app", null);
        if (aaSaved != null) aaBtnTv.setText(labelOf(aaSaved));
        else if (chosen != null && installed(chosen)) aaBtnTv.setText(labelOf(chosen));
        else if (connCandidates().size() > 1) aaBtnTv.setText(I18n.t("Toca para elegir"));
        else if (nat != null) aaBtnTv.setText(labelOf(nat));
        else if (installed(PKG_HUR)) aaBtnTv.setText("Open Headunit · USB");
        else aaBtnTv.setText(I18n.t("Toca para instalar"));
        String cpApp = carPlayApp();
        cpSubTv.setText(cpApp != null ? labelOf(cpApp) : I18n.t("Pantalla o adaptador"));
        updatePlayIcon();
    }

    private ResolveInfo find(String labelKeys, String pkgKeys) {
        PackageManager pm = getPackageManager();
        if (labelKeys.length() > 0) {
            for (String k : labelKeys.split("\\|")) {
                for (ResolveInfo r : apps) {
                    String l = String.valueOf(r.loadLabel(pm)).toLowerCase(ES);
                    if (k.length() <= 2 ? l.equals(k) || l.startsWith(k + " ") : l.contains(k)) return r;
                }
            }
        }
        if (pkgKeys.length() > 0) {
            for (String k : pkgKeys.split("\\|")) {
                for (ResolveInfo r : apps) {
                    if (r.activityInfo.packageName.toLowerCase(ES).contains(k)) return r;
                }
            }
        }
        return null;
    }

    private boolean launchSaved(String key) {
        String c = prefs.getString(key, null);
        if (c == null) return false;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(ComponentName.unflattenFromString(c));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
            return true;
        } catch (Exception e) {
            prefs.edit().remove(key).apply();
            return false;
        }
    }

    private void launch(ResolveInfo r) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("No se pudo abrir la app");
        }
    }

    private boolean launchPkg(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ajustes del equipo. En pantallas con Android 10 o más nuevo, la marca suele traer su propia app de ajustes
     * (la que muestra su launcher) y deja los Ajustes de Android ocultos y sin adaptar. Abrir esos Ajustes ocultos
     * dejó en negro una TS10 con Android 14 (todas las apps y la cámara en negro hasta reiniciar), así que en esas
     * pantallas se abre la app de ajustes de la marca; los Ajustes de Android solo si el launcher de fábrica los
     * muestra. En Android 4–9 se mantiene como siempre.
     */
    private void openSystemSettings() {
        PackageManager pm = getPackageManager();
        ResolveInfo vendor = null;
        int best = 0;
        boolean aospVisible = false;
        for (ResolveInfo r : apps) {
            String pkg = r.activityInfo.packageName;
            if ("com.android.settings".equals(pkg)) { aospVisible = true; continue; }
            String l = String.valueOf(r.loadLabel(pm)).toLowerCase(ES).trim();
            String pl = pkg.toLowerCase(ES);
            if (pl.contains("bluetooth") || l.contains("bluetooth") || l.contains("wifi") || l.contains("wi-fi")) continue;
            int score = 0;
            if (l.equals("ajustes") || l.equals("configuración") || l.equals("configuracion") || l.equals("settings")
                    || l.equals("setting") || l.equals("设置") || l.equals("car settings") || l.equals("carsetting")
                    || l.equals("ajustes del coche") || l.equals("ajustes del vehículo") || l.equals("ajustes del carro")) score = 3;
            else if (pl.contains("carsetting") || pl.contains("car.setting") || pl.endsWith(".settings") || pl.endsWith(".setting")) score = 2;
            else if (l.startsWith("ajustes") || l.startsWith("configuraci") || l.contains("settings") || pl.contains("setting")) score = 1;
            if (score > best) { best = score; vendor = r; }
        }
        if (SDK >= 29) {
            if (vendor != null) { launch(vendor); return; }
            if (!aospVisible) {
                toast("Elige la app de ajustes de tu pantalla");
                pickFor("settings", "Ajustes");
                return;
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable e) {
            toast("No se pudieron abrir los ajustes");
        }
    }

    // ───────────────────────── pantalla de inicio predeterminada ─────────────────────────

    static final int REQ_HOME = 77;
    private boolean homeAsked, homeFixing;

    /** Otras pantallas de inicio instaladas (el launcher de fábrica), sin StradaOS ni la de emergencia de Ajustes. */
    private List<ResolveInfo> otherHomes() {
        List<ResolveInfo> out = new ArrayList<ResolveInfo>();
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : getPackageManager().queryIntentActivities(i, 0)) {
                String p = r.activityInfo.packageName, n = r.activityInfo.name;
                if (getPackageName().equals(p) || p.startsWith("com.stradaos.") || n.endsWith("FallbackHome")
                        || "com.android.provision".equals(p) || r.priority < 0) continue;
                out.add(r);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** Abre el launcher de fábrica y deja de ser la elegida «Siempre» (si hay varias, pregunta cuál). */
    private void openFactoryLauncher() {
        final List<ResolveInfo> homes = otherHomes();
        if (homes.isEmpty()) { toast("No encontré otra pantalla de inicio en este equipo"); return; }
        if (homes.size() == 1) { goHome(homes.get(0)); return; }
        PackageManager pm = getPackageManager();
        String[] names = new String[homes.size()];
        for (int k = 0; k < names.length; k++) names[k] = String.valueOf(homes.get(k).loadLabel(pm));
        new TB(this, dlg()).setTitle("¿Cuál es el launcher de fábrica?")
            .setItems(names, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { goHome(homes.get(w)); } })
            .setNegativeButton("Cancelar", null).show();
    }

    private void goHome(ResolveInfo r) {
        try { getPackageManager().clearPackagePreferredActivities(getPackageName()); } catch (Throwable ignored) { }
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(r.activityInfo.packageName, r.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Throwable e) { toast("No se pudo abrir"); }
    }

    private void confirmDisableSelf() {
        final List<ResolveInfo> homes = otherHomes();
        if (homes.isEmpty()) {
            // sin otra pantalla de inicio, desactivar dejaría el equipo sin inicio
            toast("No encontré otra pantalla de inicio: StradaOS no se puede desactivar en este equipo");
            return;
        }
        new TB(this, dlg())
            .setTitle("¿Desactivar StradaOS?")
            .setMessage("StradaOS deja de ser la pantalla de inicio, ya no se abre al encender y vuelve el launcher de fábrica. No pide confirmaciones de Android, así que sirve aunque la pantalla haya quedado atrapada.\n\nPara volver a usarlo después: desinstálalo desde los ajustes de la pantalla e instálalo de nuevo.")
            .setPositiveButton("Desactivar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { disableSelf(homes.get(0)); } })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /**
     * Apaga StradaOS sin ventanas del sistema: desactiva sus componentes (una app puede hacerlo con los suyos sin
     * permisos) para que Android y el firmware ya no puedan abrirlo, borra la elección «Siempre» y abre el de fábrica.
     */
    private void disableSelf(ResolveInfo home) {
        PackageManager pm = getPackageManager();
        try { pm.clearPackagePreferredActivities(getPackageName()); } catch (Throwable ignored) { }
        goHome(home);
        for (Class<?> c : new Class<?>[]{BootReceiver.class, FakeHome.class, Main.class}) {
            try {
                pm.setComponentEnabledSetting(new ComponentName(this, c), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            } catch (Throwable ignored) { }
        }
        h.postDelayed(new Runnable() { public void run() { finish(); } }, 800);
    }

    private void uninstallSelf() {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable e) { toast("No se pudo abrir el ajuste"); }
    }

    /** true si StradaOS es la app que abre Android al encender y al tocar Inicio. */
    private boolean isDefaultHome() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
        } catch (Throwable e) {
            return true;
        }
    }

    /** Aviso: StradaOS no es la pantalla de inicio (por eso al encender aparece el menú de fábrica). */
    private boolean askDefaultHome(final Runnable after) {
        if (isDefaultHome()) return false;
        homeAsked = true;
        new TB(this, dlg())
            .setTitle("StradaOS aún no es tu pantalla de inicio")
            .setMessage("Por eso al encender el carro o al tocar Inicio aparece el menú de fábrica.\n\nToca «Fijar ahora», elige StradaOS y luego «Siempre».")
            .setPositiveButton("Fijar ahora", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { makeDefaultHome(); if (after != null) after.run(); } })
            .setNegativeButton("Ahora no", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { if (after != null) after.run(); } })
            .setNeutralButton("No volver a preguntar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                prefs.edit().putBoolean("home_never", true).apply(); if (after != null) after.run(); } })
            .show();
        return true;
    }

    /** Android 10+: ventana del sistema para elegir la app de inicio. Android 4–9: lista «¿Qué app usar?». */
    private void makeDefaultHome() {
        homeFixing = true;
        if (SDK >= 29) {
            try { if (Modern.requestHomeRole(this, REQ_HOME)) return; } catch (Throwable ignored) { }
        }
        toast("Elige StradaOS y toca «Siempre»");
        PackageManager pm = getPackageManager();
        ComponentName fake = new ComponentName(this, FakeHome.class);
        try {
            pm.setComponentEnabledSetting(fake, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable e) {
            homeFixing = false;
            homeHelp();
        } finally {
            try { pm.setComponentEnabledSetting(fake, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); } catch (Throwable ignored) { }
        }
    }

    /** Al volver de elegir: confirma o explica cómo hacerlo a mano. */
    private void homeResult() {
        if (!homeFixing) return;
        homeFixing = false;
        if (isDefaultHome()) toast("Listo: StradaOS es tu pantalla de inicio");
        else homeHelp();
    }

    private void homeHelp() {
        new TB(this, dlg())
            .setTitle("Fijar StradaOS a mano")
            .setMessage("Algunas pantallas no muestran la lista para elegir. Prueba así:\n\n1. Toca el botón Inicio (la casita) de la pantalla. Si pregunta qué app usar, elige StradaOS → Siempre.\n2. Si no pregunta: en los ajustes de la pantalla busca Aplicaciones → Aplicaciones predeterminadas → Pantalla de inicio (o «App de inicio») y elige StradaOS.\n3. Con Android 10 o más nuevo, no lo elijas desde el modo fábrica de la pantalla («Launcher», «Interfaz» o «UI»): en algunas marcas eso deja las demás apps sin poder abrirse. En Android 4 a 9 sí puedes usar esa opción.\n\nSi tu pantalla siempre vuelve a su menú de fábrica al encender, activa «Abrir StradaOS al encender» en Ajustes → Sistema.")
            .setPositiveButton("Entendido", null)
            .show();
    }

    private void openSlot(int idx) {
        String key = SLOTS[idx][0];
        if (launchSaved(key)) return;
        if ("settings".equals(key)) {
            openSystemSettings();
            return;
        }
        ResolveInfo r = find(SLOTS[idx][2], SLOTS[idx][3]);
        if (r != null) {
            launch(r);
        } else {
            toast("No encontré la app de " + SLOTS[idx][1] + ". Elígela de la lista.");
            pickFor(key, SLOTS[idx][1]);
        }
    }

    private void pickFor(final String key, String name) { pickFor(key, name, null); }

    private void pickFor(final String key, String name, final Runnable after) {
        List<String[]> extra = new ArrayList<String[]>();
        extra.add(new String[]{"auto", "↺  Automático (por defecto)", "restart_alt"});
        String[] pin = "nav_app".equals(key) ? NAV_PKGS : "music_app".equals(key) ? MUSIC_PKGS : null;
        pickApp("¿Qué app abre «" + name + "»?", extra, pin, true, new TargetCb() { public void on(String target) {
            if ("auto".equals(target)) {
                prefs.edit().remove(key).apply();
                toast("Restablecido");
            } else {
                String c = target.substring(4);
                prefs.edit().putString(key, c).apply();
                toast("Guardado: " + labelOf(c));
            }
            refreshStates();
            if (after != null) after.run();
            else if (settingsView != null) showSettings(settingsCat);
        }});
    }

    private void openAndroidAuto() {
        if (launchSaved("aa")) return;
        String chosen = prefs.getString("conn_app", null);
        if (chosen != null && launchPkg(chosen)) return;
        List<String> c = connCandidates();
        if (chosen == null && c.size() > 1) {
            chooseConnection(new Runnable() { public void run() { openAndroidAuto(); } });
            return;
        }
        if (c.size() == 1 && launchPkg(c.get(0))) return;
        File apk = findApkOnUsb("revived");
        if (apk != null && compatible(apk)) {
            installFile(apk, "Open Headunit");
            return;
        }
        new TB(this, dlg())
            .setTitle("Android Auto")
            .setMessage("Esta pantalla no trae Android Auto integrado.\n\nInstala la app gratuita «Open Headunit» (github.com/andreknieriem/headunit-revived): copia su APK en la memoria USB o en Download y vuelve a tocar el botón. Luego conecta el celular por cable USB.")
            .setPositiveButton("Entendido", null)
            .setNeutralButton("Elegir app", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { pickFor("aa", "Android Auto"); }
            }).show();
    }

    // ── método de conexión del celular (lo elige el usuario) ──

    /** Apps de esta pantalla que pueden conectar el celular, en orden sugerido. */
    private List<String> connCandidates() {
        List<String> out = new ArrayList<String>();
        if (installed(PKG_HUR)) out.add(PKG_HUR);
        String nat = nativeProjectionApp();
        if (nat != null && !out.contains(nat)) out.add(nat);
        for (String p : CARPLAY_PKGS) if (installed(p) && !out.contains(p)) out.add(p);
        for (String p : USB_HOGS) if (installed(p) && !out.contains(p)) out.add(p);
        return out;
    }

    private String connDescription(String pkg) {
        if (PKG_HUR.equals(pkg)) return "Android Auto por cable USB (necesita la app Android Auto en el celular)";
        if (pkg.startsWith("net.easyconn") || pkg.startsWith("com.carbit")) return "Espejo de pantalla (necesita la app CarbitLink en el celular)";
        return "Android Auto / CarPlay integrados en la pantalla";
    }

    private void chooseConnection(final Runnable after) {
        final List<String> c = connCandidates();
        final List<String> items = new ArrayList<String>();
        for (String p : c) items.add(labelOf(p) + "\n" + connDescription(p));
        items.add("Otra app…");
        new TB(this, dlg())
            .setTitle("¿Cómo quieres conectar tu celular?")
            .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == c.size()) { pickFor("aa", "Android Auto"); return; }
                    prefs.edit().putString("conn_app", c.get(w)).remove("aa").apply();
                    usbWarned = false;
                    toast("Conexión: " + labelOf(c.get(w)) + ". Cámbiala dejando presionado el botón.");
                    refreshStates();
                    if (settingsView != null) showSettings(settingsCat);
                    if (!USB_HOG_SET.contains(c.get(w))) guardUsb();
                    if (after != null) after.run();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }


    private void openBrowser() {
        if (launchSaved("web")) return;
        for (String p : new String[]{"org.mozilla.firefox", "org.mozilla.fennec_fdroid", "com.opera.mini.native",
                "com.opera.browser", PKG_WEB}) {
            if (launchPkg(p)) return;
        }
        File apk = findApkOnUsb("firefox", "navegador", "browser", "opera");
        if (apk != null && compatible(apk)) {
            installFile(apk, "el navegador");
            return;
        }
        if (apk != null) toast("«" + apk.getName() + "» necesita un Android más nuevo que el de esta pantalla. Abro el navegador de fábrica.");
        if (launchPkg("com.android.browser")) return;
        ResolveInfo r = find("navegador|browser|internet", "browser");
        if (r != null) { launch(r); return; }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("No encontré un navegador. Déjalo presionado para elegir uno.");
        }
    }

    /** true si el APK se puede instalar en esta versión de Android. */
    private boolean compatible(File apk) {
        try {
            return getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0) != null;
        } catch (Throwable e) {
            return true;
        }
    }

    private boolean unknownSourcesOk() {
        if (SDK >= 26) {
            try {
                if (Modern.canInstall(this)) return true;
                toast("Permite «Instalar apps desconocidas» para StradaOS y vuelve a tocar.");
                return false;
            } catch (Throwable e) { return true; }
        }
        try {
            if (Settings.Secure.getInt(getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS) == 1) return true;
        } catch (Exception e) {
            return true; // si no se puede leer, deja que el instalador decida
        }
        toast("Activa «Orígenes desconocidos» y vuelve a tocar.");
        try {
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
        return false;
    }

    private void installFromAsset(final String asset, final String name) {
        if (installing || !unknownSourcesOk()) return;
        installing = true;
        toast("Preparando " + name + "…");
        new Thread(new Runnable() {
            public void run() {
                File out = null;
                try {
                    File dir = getExternalFilesDir(null);
                    if (dir == null) dir = getFilesDir();
                    dir.mkdirs();
                    out = new File(dir, new File(asset).getName());
                    InputStream in = getAssets().open(asset);
                    OutputStream os = new FileOutputStream(out);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    os.close();
                    in.close();
                    out.setReadable(true, false);
                    dir.setExecutable(true, false);
                } catch (Exception e) {
                    out = null;
                }
                final File f = out;
                runOnUiThread(new Runnable() {
                    public void run() {
                        installing = false;
                        if (f == null) toast("No se pudo preparar " + name);
                        else installFile(f, name);
                    }
                });
            }
        }).start();
    }

    /**
     * true si el APK es StradaOS (mismo paquete) y, cuando el equipo deja leer la firma, está firmado con la misma
     * llave que la app instalada. Si la firma no se puede leer, Android igual exige la misma llave para actualizar
     * un paquete ya instalado, así que basta con que el paquete coincida.
     */
    boolean verifyApk(File f) {
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo a = pm.getPackageArchiveInfo(f.getAbsolutePath(), PackageManager.GET_SIGNATURES);
            if (a == null || !getPackageName().equals(a.packageName)) return false;
            if (a.signatures == null || a.signatures.length == 0) return true;
            android.content.pm.PackageInfo me = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            if (me.signatures == null || me.signatures.length == 0) return true;
            java.util.HashSet<String> mine = new java.util.HashSet<String>();
            for (android.content.pm.Signature sg : me.signatures) mine.add(sg.toCharsString());
            java.util.HashSet<String> theirs = new java.util.HashSet<String>();
            for (android.content.pm.Signature sg : a.signatures) theirs.add(sg.toCharsString());
            return mine.equals(theirs);
        } catch (Throwable e) {
            return false;
        }
    }

    private void installFile(File f, String name) {
        if (!unknownSourcesOk()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            if (SDK >= 24) {
                i.setDataAndType(ApkProvider.share(f), "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                i.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("No se pudo abrir el instalador para " + name);
        }
    }

    private File findApkOnUsb(String... keys) {
        String[] roots = {"/storage", "/mnt", "/mnt/media_rw", "/mnt/usb_storage", "/udisk", "/sdcard"};
        File dl = new File(android.os.Environment.getExternalStorageDirectory(), "Download");
        File hit0 = scanDir(dl, keys, 0);
        if (hit0 != null) return hit0;
        for (String r : roots) {
            File[] vols = new File(r).listFiles();
            if (vols == null) continue;
            for (File v : vols) {
                File hit = scanDir(v, keys, 2);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private File scanDir(File dir, String[] keys, int depth) {
        if (depth < 0 || dir == null || !dir.isDirectory()) return null;
        File[] fs = dir.listFiles();
        if (fs == null) return null;
        for (File f : fs) {
            String n = f.getName().toLowerCase(ES);
            if (f.isFile() && n.endsWith(".apk")) {
                for (String k : keys) if (n.contains(k)) return f;
            }
        }
        for (File f : fs) {
            if (f.isDirectory() && !f.getName().startsWith(".") && !"Android".equals(f.getName())) {
                File hit = scanDir(f, keys, depth - 1);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    // ───────────────────────── cajón de apps ─────────────────────────

    private void openDrawer() {
        float keep = s;
        s = uiS;
        try { openDrawer0(); } finally { s = keep; }
    }

    private void openDrawer0() {
        if (drawer != null) return;
        loadApps();
        drawer = new FrameLayout(this);
        drawer.setBackgroundColor((BG & 0x00FFFFFF) | 0xF5000000);
        drawer.setClickable(true);

        TextView title = tv("Aplicaciones  ·  Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")", 18, TEXT, true);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2);
        tlp.leftMargin = (int) (28 * s);
        tlp.topMargin = (int) (20 * s);
        drawer.addView(title, tlp);

        // Apps ocultas (issue #6): no se muestran aquí, pero siguen instaladas y se pueden elegir para los botones.
        final java.util.HashSet<String> hidden = hiddenApps();
        final List<ResolveInfo> shown = new ArrayList<ResolveInfo>();
        for (ResolveInfo r : apps) if (!hidden.contains(compKey(r))) shown.add(r);
        int nHidden = apps.size() - shown.size();

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        if (nHidden > 0) {
            TextView hid = tv(I18n.t("Ocultas: " + nHidden), 15, SOFT, false);
            hid.setGravity(Gravity.CENTER);
            hid.setBackground(round(BTN, 18));
            hid.setPadding((int) (16 * s), (int) (8 * s), (int) (16 * s), (int) (8 * s));
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
            hlp.rightMargin = (int) (10 * s);
            bar.addView(hid, hlp);
            hid.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { manageHidden(new Runnable() { public void run() { closeDrawer(); openDrawer(); } }); }
            });
        }
        TextView close = tv("Cerrar  ✕", 15, SOFT, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(BTN, 18));
        close.setPadding((int) (16 * s), (int) (8 * s), (int) (16 * s), (int) (8 * s));
        bar.addView(close, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2);
        clp.gravity = Gravity.TOP | Gravity.RIGHT;
        clp.rightMargin = (int) (24 * s);
        clp.topMargin = (int) (16 * s);
        drawer.addView(bar, clp);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeDrawer(); }
        });

        final PackageManager pm = getPackageManager();
        GridView grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setVerticalSpacing((int) (18 * s));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setAdapter(new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int p) { return shown.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View cv, ViewGroup parent) {
                LinearLayout cell;
                ImageView im;
                TextView lb;
                if (cv == null) {
                    float keep = s;
                    s = uiS;   // las celdas se crean después de abrir el cajón: mismo tamaño que el resto del cajón
                    cell = new LinearLayout(Main.this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    cell.setPadding(0, (int) (6 * s), 0, (int) (6 * s));
                    im = new ImageView(Main.this);
                    cell.addView(im, new LinearLayout.LayoutParams((int) (56 * s), (int) (56 * s)));
                    lb = tv("", 13, SOFT, false);
                    lb.setGravity(Gravity.CENTER);
                    lb.setPadding((int) (4 * s), (int) (8 * s), (int) (4 * s), 0);
                    cell.addView(lb, new LinearLayout.LayoutParams(-1, -2));
                    s = keep;
                } else {
                    cell = (LinearLayout) cv;
                    im = (ImageView) cell.getChildAt(0);
                    lb = (TextView) cell.getChildAt(1);
                }
                ResolveInfo r = shown.get(p);
                Drawable d = r.loadIcon(pm);
                im.setImageDrawable(d);
                lb.setText(r.loadLabel(pm));
                return cell;
            }
        });
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int p, long id) {
                launch(shown.get(p));
            }
        });
        // Dejar presionada una app: ocultarla del cajón o abrir su información en Android.
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> a, View v, int p, long id) {
                final ResolveInfo r = shown.get(p);
                new TB(Main.this, dlg())
                    .setTitle(String.valueOf(r.loadLabel(pm)))
                    .setItems(new String[]{"Ocultar del cajón", "Desinstalar", "Información de la app"}, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            if (w == 0) {
                                java.util.HashSet<String> h = hiddenApps();
                                h.add(compKey(r));
                                saveHidden(h);
                                toast("Oculta. Para volver a verla: Ajustes → Accesos y apps → Apps ocultas");
                                closeDrawer();
                                openDrawer();
                            } else if (w == 1) {
                                // Android pide confirmar; las apps del sistema no se pueden quitar (Android lo avisa)
                                try {
                                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + r.activityInfo.packageName))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                    closeDrawer();
                                } catch (Throwable e) { toast("No se pudo abrir el ajuste"); }
                            } else {
                                try {
                                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + r.activityInfo.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                } catch (Throwable e) { toast("No se pudo abrir el ajuste"); }
                            }
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
                return true;
            }
        });
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-1, -1);
        glp.topMargin = (int) (76 * s);
        glp.leftMargin = (int) (16 * s);
        glp.rightMargin = (int) (16 * s);
        drawer.addView(grid, glp);

        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
    }

    // ── apps ocultas del cajón ──

    /** Clave de una app del cajón: paquete/actividad (una app puede tener varios íconos). */
    private static String compKey(ResolveInfo r) { return r.activityInfo.packageName + "/" + r.activityInfo.name; }

    private java.util.HashSet<String> hiddenApps() {
        java.util.HashSet<String> out = new java.util.HashSet<String>();
        for (String k : prefs.getString("hidden_apps", "").split("\n")) if (k.length() > 0) out.add(k);
        return out;
    }

    private void saveHidden(java.util.Set<String> set) {
        StringBuilder b = new StringBuilder();
        for (String k : set) { if (b.length() > 0) b.append('\n'); b.append(k); }
        prefs.edit().putString("hidden_apps", b.toString()).apply();
    }

    /** Lista de todas las apps con casillas y buscador para elegir cuáles se ocultan del cajón. */
    private void manageHidden(final Runnable after) {
        float keep = s;
        s = uiS;
        try { manageHidden0(after); } finally { s = keep; }
    }

    private void manageHidden0(final Runnable after) {
        loadApps();
        final PackageManager pm = getPackageManager();
        final java.util.HashSet<String> sel = hiddenApps();
        final HashMap<ResolveInfo, String> labels = new HashMap<ResolveInfo, String>();
        for (ResolveInfo r : apps) labels.put(r, String.valueOf(r.loadLabel(pm)));
        // primero las que ya están ocultas, luego el resto (ya vienen en orden alfabético)
        final List<ResolveInfo> all = new ArrayList<ResolveInfo>();
        for (ResolveInfo r : apps) if (sel.contains(compKey(r))) all.add(r);
        for (ResolveInfo r : apps) if (!sel.contains(compKey(r))) all.add(r);
        final List<ResolveInfo> view = new ArrayList<ResolveInfo>(all);
        final HashMap<String, Drawable> icons = new HashMap<String, Drawable>();
        final int fg = LIGHT ? 0xFF1A1A1A : Color.WHITE;

        final BaseAdapter ad = new BaseAdapter() {
            public int getCount() { return view.size(); }
            public Object getItem(int p) { return view.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View cv, ViewGroup parent) {
                float keep = s;
                s = uiS;
                try { return row(p); } finally { s = keep; }
            }
            private View row(int p) {
                ResolveInfo r = view.get(p);
                String k = compKey(r);
                LinearLayout row = new LinearLayout(Main.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding((int) (8 * s), (int) (8 * s), (int) (8 * s), (int) (8 * s));
                Drawable d = icons.get(k);
                if (d == null) { try { d = r.loadIcon(pm); } catch (Throwable e) { d = null; } if (d != null) icons.put(k, d); }
                ImageView iv = new ImageView(Main.this);
                iv.setImageDrawable(d);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                row.addView(iv, new LinearLayout.LayoutParams((int) (34 * s), (int) (34 * s)));
                TextView t = new TextView(Main.this);
                t.setText(labels.get(r));
                t.setTextColor(fg);
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17 * s);
                t.setSingleLine(true);
                t.setPadding((int) (14 * s), 0, (int) (8 * s), 0);
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                android.widget.CheckBox cb = new android.widget.CheckBox(Main.this);
                cb.setChecked(sel.contains(k));
                cb.setClickable(false);
                cb.setFocusable(false);
                row.addView(cb, new LinearLayout.LayoutParams(-2, -2));
                return row;
            }
        };

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * s);
        box.setPadding(pad, pad / 2, pad, 0);
        final android.widget.EditText q = field();
        q.setSingleLine(true);
        q.setHint(I18n.t("Buscar app"));
        box.addView(q, new LinearLayout.LayoutParams(-1, -2));
        android.widget.ListView lv = new android.widget.ListView(this);
        lv.setAdapter(ad);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int p, long id) {
                String k = compKey(view.get(p));
                if (!sel.remove(k)) sel.add(k);
                ad.notifyDataSetChanged();
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(-1, (int) (250 * s)));
        q.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            public void onTextChanged(CharSequence c, int a, int b, int d) { }
            public void afterTextChanged(android.text.Editable e) {
                String f = e.toString().trim().toLowerCase(I18n.loc);
                view.clear();
                for (ResolveInfo r : all) if (f.length() == 0 || labels.get(r).toLowerCase(I18n.loc).contains(f)) view.add(r);
                ad.notifyDataSetChanged();
            }
        });

        new TB(this, dlg())
            .setTitle("Apps ocultas en el cajón")
            .setView(box)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                // conserva las ocultas que ya no están instaladas (si se reinstalan, siguen ocultas)
                saveHidden(sel);
                toast(sel.isEmpty() ? I18n.t("Se muestran todas las apps") : I18n.t("Apps ocultas: " + sel.size()));
                if (after != null) after.run();
            }})
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void closeDrawer() {
        if (drawer != null) {
            root.removeView(drawer);
            drawer = null;
        }
    }
}
