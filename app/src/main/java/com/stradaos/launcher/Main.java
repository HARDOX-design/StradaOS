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
    private boolean sideDock, compactDock, playerLeft, showCar, showMap, railLeft, dockLabels, showPlayer, carSpeed, oilHome, noDock;
    private int dockCount = 7;
    /** Tamaño de la letra (2.19, Tinoyevski #18): multiplica todos los textos. 1 = el de siempre. */
    static float FONT = 1f;
    /** x donde empieza el área de tarjetas (con la barra lateral a la izquierda), alto de las tarjetas y arriba del dock. */
    private float ox, cardH = 380, dockTop = 456, boardH = 600;
    /**
     * Diseño vertical (2.21, issue #17): pantallas en retrato tipo Tesla. El lienzo mide 720 de ancho y el alto que
     * tenga la pantalla: arriba el mapa grande, debajo la tarjeta del vehículo y el reproductor, y abajo la barra.
     * En horizontal mapY = rowY = 64 y mapH = cardH, o sea, todo queda como siempre.
     */
    private boolean vertical;
    private float mapY = 64, mapH = 380, rowY = 64;
    /** Vertical con solo la tarjeta del vehículo: flota sobre una esquina del mapa y los botones del mapa le dejan sitio. */
    private float insL, insR;
    private View carCard;
    /** Columnas: x de la tarjeta del vehículo, del mapa y del reproductor, y anchos del mapa y del reproductor. */
    private float carX = 16, mapX = 328, mapW = 420, audX = 760, audW = 248;
    private TextView topSpeedTv, topOilTv;
    private Gauge carGauge, mapGauge;
    private View mapGaugeBox;   // velocímetro del mapa en una esquina de abajo: baja junto con el panel
    private ImageView wifiIc, btIc;
    /** Reproductor vertical (tarjeta alta, p. ej. con la barra lateral): carátula y botones más grandes. */
    private boolean tallPlayer;
    /** Escala de 1024x600 para Ajustes, el cajón y sus ventanas: con la barra lateral el inicio se agranda, pero
     *  esas pantallas deben conservar su tamaño (en un S23 con barra lateral Ajustes quedaba cortado arriba). */
    private float uiS = 1f;
    static final int[] SIM_W = {0, 1024, 1280, 1920, 2000, 1280, 768, 1080, 768};
    static final int[] SIM_H = {0, 600, 720, 720, 1200, 480, 1024, 1920, 880};   // 8: tipo Tesla con barra de clima (casi cuadrada)

    private TextView weatherTv, weatherCityTv;
    private ImageView weatherIc;
    private LinearLayout weatherBox;
    private double wLat = Double.NaN, wLon = Double.NaN;
    /** Última ubicación conocida para calcular el amanecer y el atardecer del tema automático (#33). */
    private double sunLat = Double.NaN, sunLon = Double.NaN;
    private long weatherAt;
    private Weather.Now weatherNow;
    private LinearLayout panelWx, panelWxHours;
    private ImageView panelWxIc;
    private TextView panelWxTemp, panelWxDesc, panelWxHiLo;
    private FrameLayout settingsView;
    private int settingsCat = -1;
    private TextView timeTv, dateTv, greetTv, speedTv, gpsTv, aaBtnTv, aaSubTv, trackTv, artistTv, carTv, chipTv;
    private NowPlaying nowPlaying;
    private Boolean playingState;
    private TextView sourceTv, timePosTv, mapTagTv, cpSubTv;
    private View mapTagBox, navSearchBtn, navHud, navLaneBox, topNavPill;
    private Nav.ManeuverView navTurnIc, topNavIc;
    private Nav.LaneStripView navLaneView;
    private TextView navDistTv, navStepTv, navEtaTv, speedSignTv, camBadgeTv, topNavDistTv;
    private final List<Nav.Place> pendingStops = new ArrayList<Nav.Place>();
    private LiveMapView liveMap;
    private MediaWatcher mediaWatcher;
    private Hcn hcn;   // pantallas HCN / Hengchen (issue #5)
    private boolean askedPerms;
    static final int SDK = android.os.Build.VERSION.SDK_INT;
    private PlayerArt playerArt;
    // estado de la pista
    private String curTitle, curArtist, curSource;
    private Bitmap curArt;
    private long durMs = -1, posBaseMs = 0, posBaseAt = 0;
    // para el diagnóstico del reproductor: de dónde salió el tiempo y el último que mandó Bluetooth
    private String posSrc = "—";
    private long btPosMs = -1, btPosAt;
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
        // escáner OBD-II: si está encendido y elegido, el monitor arranca con StradaOS (alertas aunque no se mire)
        if (obdOn() && prefs.contains("obd_addr") && prefs.getBoolean("obd_autostart", true) && !Obd.running()) {
            try { ObdService.start(this); } catch (Throwable ignored) { }
        }

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
            if (!validLoc(ll)) ll = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (!validLoc(ll)) ll = savedLoc();
            if (validLoc(ll)) { wLat = ll.getLatitude(); wLon = ll.getLongitude(); }
        } catch (Throwable ignored) { }
        if (prefs.contains("sun_lat")) { sunLat = prefs.getFloat("sun_lat", 0); sunLon = prefs.getFloat("sun_lon", 0); }
        if (!Double.isNaN(wLat)) sunSpot(wLat, wLon);
        loadApps();
        startOdo();
        if ("internal".equals(prefs.getString("nav_app", null)) && Nav.restore(this)) LiveMapView.headingUp = true;
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
        try {
            getContentResolver().registerContentObserver(
                android.provider.Settings.System.getUriFor(android.provider.Settings.System.SCREEN_BRIGHTNESS), false, brightObs);
        } catch (Throwable ignored) { }
        try {
            getContentResolver().registerContentObserver(android.provider.Settings.System.getUriFor(Fyt.CAR_MODE), false, lightsObs);
        } catch (Throwable ignored) { }
        startFyt();
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
        try {
            hcn = new Hcn(this, new NowPlaying.Listener() {
                public void onTrack(NowPlaying.Track t) { showTrack(t); }
            });
            hcn.start();   // en cualquier otra pantalla no hace nada
        } catch (Throwable e) { hcn = null; }
        clwBt = BtBridge.available(this);
        prefs.registerOnSharedPreferenceChangeListener(backupWatch);
        try {
            IntentFilter sf = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            sf.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenReceiver, sf);
        } catch (Throwable ignored) { }
        maybeBootApp();
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
            BtBridge.log("revisando la vuelta: al frente " + now + (hasWindowFocus() ? " (inicio con foco)" : ""));
            // 2.30 (#47): en Android 5+ el sistema solo deja ver las tareas propias y topPackage() responde StradaOS aunque
            // la app de música esté al frente; antes eso se leía como «el usuario abrió otra app» y nunca se volvía.
            boolean self = getPackageName().equals(now);
            if (self && hasWindowFocus()) { BtBridge.log("no se volvió: ya estás en el inicio"); return; }
            if (now != null && !(self && SDK >= 21) && !now.equals(BtBridge.PKG) && !now.equals(btMusicApp() != null ? btMusicApp().getPackageName() : "")) {
                BtBridge.log("no se volvió: el usuario abrió " + now); return;
            }
            try {
                if (back >= 0) {
                    ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).moveTaskToFront(back, 0);
                    BtBridge.log("vuelta a " + top);
                    return;
                }
            } catch (Throwable ignored) { }
            BtBridge.log("vuelta al inicio");
            // 2.30: primero se trae al frente la tarea de StradaOS (permiso REORDER_TASKS); el startActivity queda de respaldo
            try { ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).moveTaskToFront(getTaskId(), 0); }
            catch (Throwable e) { BtBridge.log("moveTaskToFront: " + e); }
            try {
                startActivity(new Intent(Main.this, Main.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } catch (Throwable e) { BtBridge.log("no se pudo volver: " + e); }
        }}, prefs.getInt("bt_return_ms", clwBt ? 2000 : 2500));
    }

    @Override
    protected void onDestroy() {
        try { getContentResolver().unregisterContentObserver(brightObs); } catch (Throwable ignored) { }
        try { getContentResolver().unregisterContentObserver(lightsObs); } catch (Throwable ignored) { }
        h.removeCallbacks(themeRecheck);
        stopFyt();
        try { unregisterReceiver(screenReceiver); } catch (Throwable ignored) { }
        try { prefs.unregisterOnSharedPreferenceChangeListener(backupWatch); } catch (Throwable ignored) { }
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
        try { if (hcn != null) hcn.stop(); } catch (Throwable ignored) { }
        try { lm.removeUpdates(odoListener); } catch (Throwable ignored) { }
        odoRunning = false;
        if (odoPending > 0) prefs.edit().putFloat("odo_gps_m", prefs.getFloat("odo_gps_m", 0) + odoPending).apply();
        Nav.shutdownTts();
        Guard.stopWatchdog();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Obd.addListener(obdWatch);
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
        hideIdle();
        idleReset();
    }

    // ───────────────────────── pantalla en reposo (2.22, #27 / #28) ─────────────────────────
    // Tras X minutos sin tocar StradaOS, la pantalla se pone negra con la hora, la fecha y la velocidad.
    // Viene apagada (idle_on). Un toque la quita (ese toque no llega a lo de abajo).

    static final int[] IDLE_MIN = {1, 2, 5, 10, 15, 30};
    static final int[] IDLE_COLORS = {0xFFFFFFFF, 0, 0xFF9AA0A8, 0xFFFFB13B};   // 0 = color de acento
    private FrameLayout idleView;
    private TextView idleClock, idleDate, idleSpeedTv;
    private Gauge idleGauge;
    private boolean idleSwallow;

    private final Runnable idleRun = new Runnable() { public void run() { showIdle(); } };

    private void idleReset() {
        h.removeCallbacks(idleRun);
        if (prefs != null && prefs.getBoolean("idle_on", false))
            h.postDelayed(idleRun, prefs.getInt("idle_min", 2) * 60000L);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (idleView != null || idleSwallow) {
            // el toque que despierta la pantalla no hace nada más
            if (e.getAction() == MotionEvent.ACTION_DOWN) { hideIdle(); idleSwallow = true; }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { idleSwallow = false; idleReset(); }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_DOWN) idleReset();
        return super.dispatchTouchEvent(e);
    }

    private int idleColor() {
        int c = IDLE_COLORS[Math.max(0, Math.min(IDLE_COLORS.length - 1, prefs.getInt("idle_color", 0)))];
        return c == 0 ? RED : c;
    }

    private void showIdle() {
        if (root == null || idleView != null) return;
        float keep = s;
        s = uiS > 0 ? uiS : s;
        try { showIdle0(); } catch (Throwable ignored) { } finally { s = keep; }
    }

    private void showIdle0() {
        int col = idleColor();
        float f = new float[]{1f, 1.25f, 1.5f}[Math.max(0, Math.min(2, prefs.getInt("idle_size", 0)))];
        int sp = prefs.getInt("idle_speed", prefs.getInt("speedo", 0));   // 0 digital, 1 analógico, 2 mixto, 3 sin velocidad
        boolean speedOn = sp != 3 && prefs.getBoolean("car_speed", true);
        if (!vertical && speedOn) f = Math.min(f, 1.25f);   // en horizontal, hora + velocímetro lado a lado deben caber en 1024
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(Color.BLACK);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setGravity(Gravity.CENTER);
        idleClock = tv("", 150 * f, col, false);
        idleClock.setGravity(Gravity.CENTER);
        idleDate = tv("", 30 * f, col, false);
        idleDate.setGravity(Gravity.CENTER);
        idleDate.setAlpha(0.7f);
        idleDate.setPadding(0, (int) (10 * s), 0, 0);
        tc.addView(idleClock, new LinearLayout.LayoutParams(-2, -2));
        tc.addView(idleDate, new LinearLayout.LayoutParams(-2, -2));
        box.addView(tc, new LinearLayout.LayoutParams(-2, -2));
        if (speedOn) {
            View speed;
            float gap = 56 * s;
            if (sp == 0) {
                LinearLayout sv = new LinearLayout(this);
                sv.setOrientation(LinearLayout.HORIZONTAL);
                sv.setGravity(Gravity.BOTTOM);
                idleSpeedTv = tv("--", 110 * f, col, true);
                TextView u = tv(useMph() ? "mph" : "km/h", 30 * f, col, false);
                u.setAlpha(0.7f);
                u.setPadding((int) (10 * s), 0, 0, (int) (14 * s * f));
                sv.addView(idleSpeedTv);
                sv.addView(u);
                speed = sv;
            } else {
                idleGauge = new Gauge(this, s * f);
                idleGauge.setColors(RED, col, (col & 0x00FFFFFF) | 0xB0000000);
                idleGauge.setUnit(useMph());
                idleGauge.setNumber(sp == 2);
                speed = idleGauge;
            }
            int gs = (int) (260 * s * f);
            LinearLayout.LayoutParams lp = sp == 0 ? new LinearLayout.LayoutParams(-2, -2) : new LinearLayout.LayoutParams(gs, gs);
            if (vertical) lp.topMargin = (int) gap; else lp.leftMargin = (int) (gap * 1.5f);
            box.addView(speed, lp);
        }
        ov.addView(box, new FrameLayout.LayoutParams(-1, -1));
        idleView = ov;
        root.addView(ov, new FrameLayout.LayoutParams(-1, -1));
        updateIdleClock();
        setGps(lastOk, lastKmh);
    }

    private void hideIdle() {
        if (idleView == null) return;
        try { root.removeView(idleView); } catch (Throwable ignored) { }
        idleView = null; idleClock = idleDate = idleSpeedTv = null; idleGauge = null;
    }

    private void updateIdleClock() {
        if (idleClock == null) return;
        Date now = new Date();
        String fmt = prefs.getString("clock", "auto");
        boolean h24 = "24".equals(fmt) || ("auto".equals(fmt) && android.text.format.DateFormat.is24HourFormat(this));
        idleClock.setText(I18n.sdf(h24 ? "HH:mm" : "h:mm").format(now));
        String d = I18n.sdf(I18n.datePattern()).format(now);
        idleDate.setText(d.substring(0, 1).toUpperCase(I18n.loc) + d.substring(1));
    }

    /** Android 6+: respuesta a la solicitud de permisos. */
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == ObdDialogs.REQ_BT_SCAN) {
            obdDialogs().onPermission(res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED);
            return;
        }
        if (req == REQ_STORAGE_W) {   // con o sin permiso se sigue (sin él, el mapa se guarda en la carpeta de la app)
            h.post(new Runnable() { public void run() { chooseMapToDownload(""); } });
            return;
        }
        if (req == REQ_STORAGE) {
            if (res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) pickMapFile();
            else toast("Sin permiso de almacenamiento no se pueden buscar mapas");
            return;
        }
        if (req == REQ_BACKUP_W) { h.post(new Runnable() { public void run() { backupSave0(); } }); return; }   // con o sin permiso se sigue
        if (req == REQ_BACKUP_R) { h.post(new Runnable() { public void run() { backupRestore0(); } }); return; }
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
        Obd.removeListener(obdWatch);
        if (obdDlg != null) obdDlg.stopDiscovery();
        h.removeCallbacks(idleRun);
        stopGps();
        h.removeCallbacks(ticker);
        h.removeCallbacks(progressTick);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    /** Tema «Sistema»: la pantalla pasó a modo oscuro o claro (p. ej. al encender las luces): se cambia al instante. */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        try {
            if (prefs == null || !"system".equals(prefs.getString("theme", "dark")) || root == null) return;
            boolean was = LIGHT;
            applyTheme();
            if (was != LIGHT) { applyAccent(); rebuild(); }
        } catch (Throwable e) { Guard.log("Cambio de tema del sistema: " + e); }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        closeDrawer();
        if (i != null && i.getBooleanExtra("vehiculo", false)) h.post(new Runnable() { public void run() { openVehiclePanel(); } });
        testHook(i);
    }

    /**
     * Solo en la versión de prueba (paquete .debug): permite que las pruebas automáticas abran cada pantalla
     * sin tocar coordenadas. adb shell am start -n com.stradaos.launcher.debug/com.stradaos.launcher.Main --es prueba VALOR
     * VALOR: saltar (omite el asistente), inicio, ajustes:N, fyt-demo:139,1170 (datos del carro de prueba), boton:N (menú del botón N), elegir:N (lista de apps del botón N),
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
                else if ("obd-demo".equals(v)) obdDialogs().choose("demo", I18n.t("Auto simulado"));
                else if ("obd-off".equals(v)) obdDialogs().turnOff();
                else if ("obd-elegir".equals(v)) obdDialogs().pickAdapter();
                else if ("obd-usb".equals(v)) obdDialogs().pickUsb();
                else if (v.startsWith("obd-addr:")) obdDialogs().choose(v.substring(9), "Prueba " + v.substring(9));
                else if (v.startsWith("obd-vel-demo:")) { obdSpeedDemo = v.endsWith("1"); showSpeed(); }
                else if ("obd-panel".equals(v)) openVehiclePanel();
                else if (v.startsWith("obd-ajustes:")) { settingsSub = v.substring(12); showSettings(C_VEHICLE); }
                else if ("ocultas".equals(v)) manageHidden(null);
                else if ("salir-fabrica".equals(v)) openFactoryLauncher();
                else if (v.startsWith("aa-simular:")) {
                    // simula Android Auto conectado (solo StradaOS Prueba, se pierde al cerrar la app)
                    aaSim = v.endsWith("1");
                    aaCheckedAt = 0;
                }
                else if (v.startsWith("aa-rcc:")) {
                    // canción por el control remoto de Open Headunit, sin tiempo: «aa-rcc:Título,duración_s»
                    // (con coma: «|» lo corta la consola del equipo)
                    String[] a = v.substring(7).split(",");
                    NowPlaying.Track t = new NowPlaying.Track();
                    t.origin = "rcc"; t.title = a[0].replace('_', ' '); t.artist = "StradaOS"; t.source = "Android Auto";
                    t.durationMs = a.length > 1 ? Long.parseLong(a[1]) * 1000 : -1; t.playing = true;
                    showTrack(t);
                }
                else if (v.startsWith("hcn-sim:")) {
                    // reproductor de una pantalla HCN: «hcn-sim:ruta,estado,posición_s,duración_s» (ruta vacía = nada)
                    String[] a = v.substring(8).split(",", -1);
                    Hcn.simulate(a[0], a.length > 1 ? a[1] : "start", a.length > 2 ? Integer.parseInt(a[2]) : 0,
                            a.length > 3 ? Integer.parseInt(a[3]) : 0);
                }
                else if (v.startsWith("avrcp-sim:") && SDK >= 21) {
                    // aviso del Bluetooth del sistema: «avrcp-sim:Título,Artista,estado,posición_s,duración_s»
                    // (título «-» = solo estado; estado 3 sonando, 2 en pausa, -1 sin estado)
                    String[] a = v.substring(10).split(",", -1);
                    String ti = "-".equals(a[0]) ? null : a[0].replace('_', ' ');
                    sendBroadcast(AvrcpMeta.testIntent(ti, a.length > 1 ? a[1].replace('_', ' ') : null,
                            a.length > 2 ? Integer.parseInt(a[2]) : 3, a.length > 3 ? Long.parseLong(a[3]) * 1000 : 0,
                            a.length > 4 ? Long.parseLong(a[4]) * 1000 : 0));
                }
                else if ("diag-reproductor".equals(v)) showPlayerDiag();
                else if (v.startsWith("aa-notif:")) {
                    // notificación de Open Headunit con el tiempo: «aa-notif:Título,posición_s,duración_s»
                    String[] a = v.substring(9).split(",");
                    long ps = Long.parseLong(a[1]), ds = Long.parseLong(a[2]);
                    String prog = String.format(Locale.US, "%02d:%02d / %02d:%02d", ps / 60, ps % 60, ds / 60, ds % 60);
                    java.util.List<String> tx = new java.util.ArrayList<String>();
                    tx.add(a[0].replace('_', ' ')); tx.add("StradaOS"); tx.add(prog);
                    NotifArt.lastTitle = tx.get(0); NotifArt.lastArtist = "StradaOS";
                    NotifArt.lastPosMs = ps * 1000; NotifArt.lastDurMs = ds * 1000; NotifArt.lastPlaying = Boolean.TRUE;
                    NotifArt.lastTexts = tx; NotifArt.lastAt = SystemClock.elapsedRealtime();
                    applyAaNotif(curArt, tx);
                }
                else if ("pista-demo".equals(v) || v.startsWith("pista-demo:")) {
                    // canción de ejemplo con duración y posición, para ver la línea de tiempo en las capturas
                    // («pista-demo:Night_Drive» cambia el título; «_» = espacio)
                    curTitle = v.length() > 11 ? v.substring(11).replace('_', ' ') : "Canción de prueba"; curArtist = "StradaOS"; curSource = "Bluetooth";
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
                else if ("tema:claro".equals(v) || "tema:oscuro".equals(v) || "tema:sistema".equals(v)) {
                    prefs.edit().putString("theme", "tema:claro".equals(v) ? "light" : "tema:sistema".equals(v) ? "system" : "dark").apply();
                    applyThemeNow(); applyAccent(); rebuild();
                }
                else if ("nav-buscar".equals(v)) openNavSearch();
                else if ("nav-salir".equals(v)) stopInternalNav();
                else if (v.startsWith("nav-ir:")) {
                    // nav-ir:lat,lon,nombre — solo para capturas de la revisión
                    String[] a = v.substring(7).split(",");
                    Nav.Place pl = new Nav.Place();
                    pl.lat = Double.parseDouble(a[0]); pl.lon = Double.parseDouble(a[1]);
                    pl.name = a.length > 2 ? a[2] : "Destino";
                    android.util.Log.w("StradaNav", "nav-ir desde " + wLat + "," + wLon + " a " + v);
                    startInternalNav(pl);
                }
                else if (v.startsWith("tema-gratis:")) {
                    // tema-gratis:N aplica el tema N (1-4) como al tocar su tarjeta
                    int n = Integer.parseInt(v.substring(12)) - 1;
                    if (n >= 0 && n < FREE_THEMES.length) applyFreeTheme(FREE_THEMES[n]);
                }
                else if (v.startsWith("tema-premium:")) {
                    // tema-premium:aurora lo aplica; «tema-premium:» lo quita
                    applyPremium(v.length() > 13 ? v.substring(13) : null);
                }
                else if (v.startsWith("lic-prueba:")) prefs.edit().putBoolean("lic_test", v.endsWith("1")).apply();
                else if (v.startsWith("desbloquear:")) unlockDialog(v.substring(12));
                else if (v.startsWith("paquete:")) Prem.forceBundle = v.endsWith("1");
                else if (v.startsWith("lic-codigo:")) checkCode(Faro.ID, v.substring(11));
                else if ("lic-usb".equals(v)) importLicense(Faro.ID);
                else if ("solo-mapa-anim".equals(v)) toggleMapOnly();
                else if (v.startsWith("foto-demo:")) {
                    // foto-demo:recorte (fondo transparente), foto-demo:normal (con fondo), «foto-demo:» la quita
                    String k = v.substring(10);
                    if (k.length() == 0) CarPhoto.delete(Main.this);
                    else {
                        Bitmap b = Bitmap.createBitmap(600, 260, Bitmap.Config.ARGB_8888);
                        android.graphics.Canvas cv = new android.graphics.Canvas(b);
                        if ("normal".equals(k)) cv.drawColor(0xFF8899AA);
                        android.graphics.Paint pp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        pp.setColor(0xFFC0392B);
                        cv.drawRoundRect(new android.graphics.RectF(40, 110, 560, 200), 40, 40, pp);
                        cv.drawRoundRect(new android.graphics.RectF(170, 50, 430, 140), 50, 50, pp);
                        pp.setColor(0xFF111111);
                        cv.drawCircle(160, 200, 42, pp);
                        cv.drawCircle(440, 200, 42, pp);
                        CarPhoto.save(Main.this, b);
                    }
                    rebuild();
                }
                else if (v.startsWith("foto-vacia:")) {
                    // imágenes promocionales: en Cobalto, el recuadro de la foto sin el aviso «Toca para poner…»
                    // (se pone la foto al armar la imagen); su posición queda en logcat (StradaPromo)
                    emptyPhotoSlot = v.endsWith("1");
                    rebuild();
                }
                else if (v.startsWith("solo-mapa:")) {
                    prefs.edit().putBoolean("prem_map_only", v.endsWith("1")).apply();
                    rebuild();
                }
                else if (v.startsWith("vista-temas:")) {
                    // vista-temas:h|v elige la vista previa de Personalización
                    stylePreview = "v".equals(v.substring(12)) ? 1 : 0;
                    stylePreviewFor = vertical;
                    showSettings(C_STYLE);
                }
                else if (v.startsWith("fyt-demo:")) {
                    // fyt-demo:139,1170 = 13,9 V y 17 °C, como si llegaran de com.syu.ms (con «Datos del carro» activado)
                    String[] a = v.substring(9).split(",");
                    if (fyt != null) {
                        fyt.voltDeci = Integer.parseInt(a[0].trim());
                        if (a.length > 1) fyt.tempDeci = Integer.parseInt(a[1].trim()) - 1000;
                        updateCarData();
                    }
                }
                else if ("reposo".equals(v)) h.postDelayed(new Runnable() { public void run() { showIdle(); } }, 1500);
                else if (v.startsWith("pref:") && v.indexOf('=') > 5) {
                    // pref:clave=valor (true/false, número o texto); «pref:clave=» la borra
                    String k = v.substring(5, v.indexOf('=')), val = v.substring(v.indexOf('=') + 1);
                    SharedPreferences.Editor e = prefs.edit();
                    if (val.length() == 0) e.remove(k);
                    else if ("true".equals(val) || "false".equals(val)) e.putBoolean(k, Boolean.parseBoolean(val));
                    else if (val.matches("-?\\d{1,9}")) e.putInt(k, Integer.parseInt(val));
                    else e.putString(k, val);
                    e.apply();
                    if (k.startsWith("fyt_data") || "hw".equals(k)) { stopFyt(); startFyt(); }
                    applyThemeNow(); applyAccent(); rebuild();
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
                    else if ("sinnombres".equals(d)) e.putBoolean("dock_labels", false);
                    else if ("railizq".equals(d) || "railder".equals(d)) e.putString("rail_side", "railizq".equals(d) ? "left" : "right");
                    else if ("siniconos".equals(d)) e.putBoolean("status_icons", false);
                    else if ("sinvelocidad".equals(d)) e.putBoolean("car_speed", false);
                    else if ("sinaceite".equals(d)) e.putBoolean("oil_home", false);
                    else if ("sinreproductor".equals(d)) e.putBoolean("show_player", false);
                    else if (d.startsWith("conexion-")) e.putString("conn_buttons", d.substring(9));
                    else if (d.startsWith("nav-")) e.putInt("nav_btn_size", Integer.parseInt(d.substring(4)));
                    else if ("navizq".equals(d)) e.putString("nav_btn_side", "left");
                    else if ("sinbarra".equals(d)) e.putString("dock_mode", "none");
                    else if (d.startsWith("velo-")) e.putInt("speedo", Integer.parseInt(d.substring(5)));
                    else if ("velomapa".equals(d)) e.putBoolean("map_gauge", true);
                    else if (d.startsWith("esquina-")) e.putString("map_gauge_corner", d.substring(8));
                    else if (d.startsWith("velotam-")) e.putInt("map_gauge_size", Integer.parseInt(d.substring(8)));
                    else if (d.startsWith("botones-")) e.putInt("dock_count", Integer.parseInt(d.substring(8)));
                    else if (d.startsWith("iconos-")) e.putInt("dock_icon_size", Integer.parseInt(d.substring(7)));
                    else if ("estiloapps".equals(d)) e.putString("dock_style", "apps");
                    else if (d.startsWith("letra-")) e.putInt("font_size", Integer.parseInt(d.substring(6)));
                    else if ("base".equals(d)) e.remove("player_side").remove("show_car").remove("dock_mode").remove("aa_panel_open")
                            .remove("dock_labels").remove("rail_side").remove("status_icons").remove("car_speed").remove("oil_home")
                            .remove("show_player").remove("conn_buttons").remove("nav_btn_size").remove("nav_btn_side")
                            .remove("dock_count").remove("dock_icon_size").remove("dock_style").remove("font_size")
                            .remove("speedo").remove("map_gauge").remove("map_gauge_corner").remove("map_gauge_size");
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
                else if (v.startsWith("bt-probar:")) { prefs.edit().putString("btaudio", v.substring(10)).apply(); openBtAudioApp(true); }
                else if ("clima".equals(v)) showWeatherDetail();
                else if (v.startsWith("vel:")) { float kv = Float.parseFloat(v.substring(4)); setGps(true, kv); }
                else if (v.startsWith("pack:")) { prefs.edit().putString("icon_pack", v.substring(5)).putString("dock_style", "apps").apply(); rebuild(); }
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
                else if ("demo-mant".equals(v)) {
                    long day = 86400000L, now2 = System.currentTimeMillis();
                    prefs.edit().putString("maint", "[{\"t\":\"air\",\"km\":15000,\"mo\":12,\"lkm\":31000,\"ld\":" + (now2 - 200 * day)
                        + "},{\"t\":\"insurance\",\"km\":0,\"mo\":12,\"ld\":" + (now2 - 350 * day) + "},{\"n\":\"Llantas nuevas\",\"km\":40000,\"mo\":0,\"lkm\":20000},{\"t\":\"wash\",\"km\":0,\"mo\":0,\"dd\":14,\"tu\":\"w\",\"ld\":"
                        + (now2 - 9 * day) + "}]").apply();
                    maintLater = false; rebuild();
                }
                else if ("aviso-mant".equals(v)) { maintLater = false; oilLater = false; maintAlert(); }
                else if ("menu-mant".equals(v)) taskMenu(0);
                else if ("tiempo-mant".equals(v)) askTime(I18n.t("¿Cada cuánto tiempo?"), 2, 'w', new TimeCb() { public void on(long n, char u) { } });
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
                else if (v.startsWith("mapa-nombres:")) {
                    // registro (StradaMapas) con el nombre que la lista de descargas le da a cada archivo de una carpeta
                    final String folder = v.substring(13);
                    new Thread(new Runnable() { public void run() {
                        try {
                            for (MapDownload.Entry e : MapDownload.list(folder))
                                android.util.Log.w("StradaMapas", e.path + " = " + e.name);
                        } catch (Throwable e) { android.util.Log.w("StradaMapas", folder + " error " + e); }
                    }}).start();
                }
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
                // 2.21: ventanas propias, copia de seguridad
                else if ("diag-reproductor".equals(v)) showPlayerDiag();
                else if ("compat".equals(v)) showCompat();
                else if ("ayuda-bt".equals(v)) btHelp();
                else if ("fecha".equals(v)) pickDate("¿Cuándo fue la última vez?", 0, new LongCb() { public void on(long w) { } });
                else if ("hora".equals(v)) askDarkTime("dark_start", 18 * 60, "Oscuro desde");
                else if ("copia-guardar".equals(v)) backupSave0();
                else if ("copia-restaurar".equals(v)) backupRestore0();
                else if ("copia-prueba".equals(v)) {
                    // ida y vuelta: se guarda una copia, se cambian datos, se restaura y deben volver los de antes
                    prefs.edit().putString("car_name", "Carro de prueba").putFloat("odo_base_km", 12345.5f).putInt("accent", 0xFF2D7FF9).apply();
                    String json = Backup.build(Main.this);
                    File f = Backup.save(Main.this, Backup.primary(), "StradaOS-backup-prueba.json", json);
                    prefs.edit().putString("car_name", "Otro").putFloat("odo_base_km", 1f).remove("accent").putString("maint", "[]").apply();
                    String err = Backup.apply(Main.this, new String(Backup.readAll(new java.io.FileInputStream(f)), "UTF-8"));
                    boolean ok = err == null && "Carro de prueba".equals(prefs.getString("car_name", "")) && prefs.getFloat("odo_base_km", 0) == 12345.5f
                        && prefs.getInt("accent", 0) == 0xFF2D7FF9 && Backup.apply(Main.this, "{\"app\":\"otra\"}") != null;
                    int found = Backup.find(Main.this).size();
                    Guard.log("PRUEBA copia: ok=" + ok + " error=" + err + " archivo=" + f + " encontradas=" + found + " usb=" + Backup.removable(Main.this));
                    reloadAll();
                }
                else if ("demo-barra".equals(v) && !apps.isEmpty()) {
                    ResolveInfo r = apps.get(0);
                    prefs.edit().putString("dock_2", "fn:music")
                        .putString("dock_3", "app:" + new ComponentName(r.activityInfo.packageName, r.activityInfo.name).flattenToString())
                        .putString("dock_3_name", "Mi app").apply();
                    rebuild();
                }
                else if ("nav-demo".equals(v)) {
                    Nav.Route nr = new Nav.Route();
                    nr.pts = new double[]{4.6533, -74.0836, 4.6562, -74.0807, 4.6590, -74.0780, 4.6615, -74.0810};
                    nr.cumDistM = new double[]{0, 455, 895, 1330};
                    nr.totalDistM = 1330; nr.totalDurS = 180;
                    nr.destName = "Parque Simón Bolívar"; nr.destLat = 4.6615; nr.destLon = -74.0810;
                    Nav.Step s0 = new Nav.Step(); s0.lat = 4.6533; s0.lon = -74.0836; s0.icon = 0; s0.distM = 455; s0.street = "Av. Carrera 50"; s0.instruction = "Continúa por Av. Carrera 50";
                    Nav.Step s1 = new Nav.Step(); s1.lat = 4.6562; s1.lon = -74.0807; s1.icon = 6; s1.distM = 440; s1.street = "Calle 53"; s1.instruction = "Gira a la izquierda en Calle 53";
                    Nav.Step s2 = new Nav.Step(); s2.lat = 4.6615; s2.lon = -74.0810; s2.icon = 9; s2.distM = 0; s2.street = ""; s2.instruction = "Llegaste a tu destino";
                    nr.steps.add(s0); nr.steps.add(s1); nr.steps.add(s2);
                    nr.stepPtIdx = new int[]{0, 2, 3};
                    Nav.activate(nr);
                    LiveMapView.headingUp = true;
                    Location l = new Location(LocationManager.GPS_PROVIDER);
                    l.setLatitude(4.6540); l.setLongitude(-74.0829); l.setBearing(45); l.setSpeed(11); l.setTime(System.currentTimeMillis());
                    feedMap(l);
                    updateNavHud();
                }
            } catch (Throwable e) {
                Guard.log("Prueba «" + v + "» falló: " + e);
            }
        }}, 1500);
    }

    @Override
    public void onBackPressed() {
        if (vpanel != null && vpanel.isOpen()) { vpanel.close(); return; }
        if (settingsView != null) {
            if (settingsSub != null && settingsCat == C_VEHICLE) { settingsBack(); return; }
            closeSettings(); return;
        }
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
        // 2.21.1 (PR #26 de wsx79): «Ocultar la barra del sistema» esconde también la barra de navegación (abajo o
        // al costado, como la del Ottocast P3), igual que Waze a pantalla completa; deslizando desde el borde vuelve
        // un momento. Solo desde Android 4.4: antes no existe el modo inmersivo y la barra volvería encima al tocar.
        int nav = SDK >= 19 && prefs != null && prefs.getBoolean("hide_navbar", false)
                ? View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION : 0;
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN | nav | 0x00001000 /* IMMERSIVE_STICKY, API 19 */);
        } catch (Exception ignored) { }
        // Android 11+: la barra de navegación y la barra de tareas se esconden (o vuelven) con el método nuevo
        if (SDK >= 30) try { Bars.navigation(getWindow(), nav != 0); } catch (Throwable ignored) { }
    }

    /**
     * Corrección del velocímetro (2.21.1, PR #26 de wsx79): «+3» o «-2» en la unidad que se muestra, «+5%» en
     * porcentaje, para que coincida con el tablero (que casi siempre marca un poco más que el GPS). Solo cambia el
     * número que se ve: el kilometraje y los mantenimientos siguen con la distancia real del GPS.
     */
    private float speedFix(float v) {
        if (v < 1) return v;
        String o = prefs.getString("speed_offset", "0").trim();
        try {
            if (o.endsWith("%")) v *= 1f + Float.parseFloat(o.substring(0, o.length() - 1).replace("+", "")) / 100f;
            else v += Float.parseFloat(o.replace("+", ""));
        } catch (NumberFormatException ignored) { }
        return Math.max(0, v);
    }

    static final String[] SPEED_OFFSETS = {"0", "+1", "+2", "+3", "+4", "+5", "+7", "+10", "-1", "-2", "-3",
        "+3%", "+5%", "+7%", "+10%", "-5%"};

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
            // Android 5+: suena música sin canción y sin permiso → el reproductor lo dice (y se quita solo)
            if (trackTv != null && curTitle == null && curArtist == null && wantsNotifAccess() != notifHint) renderTrack();
            // permiso dado pero el sistema no conectó el lector (algunas pantallas): pedirlo otra vez cada minuto
            if (SDK >= 24 && usbTick % 30 == 0 && !MediaListener.connected && Modern.notificationAccess(Main.this)) {
                Modern.rebindListener(Main.this);
                if (mediaWatcher != null) mediaWatcher.start();
            }
            updatePlayIcon();
            updateStatusIcons();
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
        build0(W, H);
        attachVehicle();
    }

    private void build0(int W, int H) {
        root.removeAllViews();
        idleView = null; idleClock = idleDate = idleSpeedTv = null; idleGauge = null;
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
        noDock = "none".equals(dm);   // 2.19 (MIGSGx #12): sin barra; el botón Apps va arriba junto al engranaje
        dockCount = Math.max(4, Math.min(10, prefs.getInt("dock_count", 7)));
        int fs = prefs.getInt("font_size", 1);
        FONT = fs == 0 ? 0.9f : fs == 2 ? 1.12f : fs == 3 ? 1.25f : 1f;
        playerLeft = "left".equals(prefs.getString("player_side", "right"));
        showCar = prefs.getBoolean("show_car", true);
        showMap = true;   // «ocultar el mapa» del PR #35 queda para más adelante
        if (Nav.active != null && !"internal".equals(prefs.getString("nav_app", null))) {
            Nav.stop(this);
            LiveMapView.headingUp = prefs.getBoolean("map_heading", false);
        }
        // 2.18: ocultar el reproductor (el mapa gana espacio), la velocidad y el servicio (carros sin GPS) y el aceite
        showPlayer = prefs.getBoolean("show_player", true);
        carSpeed = prefs.getBoolean("car_speed", true);
        oilHome = carSpeed && prefs.getBoolean("oil_home", true);
        // barra lateral: del lado del reproductor (como siempre) o fija a la izquierda / derecha (2.17, Druids07)
        String rs = prefs.getString("rail_side", "auto");
        railLeft = "left".equals(rs) || (!"right".equals(rs) && playerLeft);
        dockLabels = prefs.getBoolean("dock_labels", true);
        // 2.24: tema premium «Aurora»: su propia distribución (la barra lateral y la compacta no aplican)
        premium = premiumActive();
        Prem.style = premium ? prefs.getString("premium_theme", Aurora.ID) : Aurora.ID;
        mapOnly = premium && Prem.mapOnlyOk() && prefs.getBoolean("prem_map_only", false);   // Cobalto y Faro son fijos: sin «Solo mapa»
        // Faro (2.25): qué tan visible es el mapa sobre el fondo. Auto (por defecto): suave en pantallas de alta
        // resolución y más fuerte en las pantallas sencillas de los radios (lado corto ≤ 720 px y densidad baja)
        int fml = prefs.getInt("faro_mapa", -1);
        if (fml < 0) fml = simpleScreen() ? 1 : 0;
        Faro.mapLevel = Math.max(0, Math.min(2, fml));
        if (premium) { sideDock = false; compactDock = false; }
        // Lienzo virtual: 1024x600. Con la barra lateral los botones salen de abajo y el lienzo queda más bajo
        // (1128x460): en pantallas bajas y anchas como 1280x480 todo se ve ~30 % más grande.
        // Diseño (2.22, Jorge Morales): Automático = vertical si la pantalla es al menos 20 % más alta que ancha (como
        // siempre). Las pantallas tipo Tesla con barra de clima de fábrica quedan casi cuadradas: ahí se puede forzar.
        boxW = W; boxH = H;
        // 2.22: Automático pasa a vertical desde 1,05·H (antes 1,2): en pantallas un poco más altas que anchas el diseño
        // horizontal queda chico en el medio (Jorge Morales). Quien ya tenía StradaOS con una pantalla así se queda en
        // horizontal (se guarda una sola vez); las instalaciones nuevas arrancan en vertical sin tocar Ajustes.
        if (!prefs.getBoolean("layout_mig_222", false)) {
            SharedPreferences.Editor me = prefs.edit().putBoolean("layout_mig_222", true);
            if (prefs.getBoolean("setup_done", false) && !prefs.contains("layout_mode") && H >= W * 1.05f && H < W * 1.2f)
                me.putString("layout_mode", "horizontal");
            me.apply();
        }
        String lmode = prefs.getString("layout_mode", "auto");
        vertical = "vertical".equals(lmode) ? canVertical(W, H) : !"horizontal".equals(lmode) && H >= W * 1.05f;
        if (vertical) sideDock = false;   // en vertical la barra va abajo (la lateral le quitaría ancho al mapa)
        if (premium) sideDock = false;
        // Vertical bajo (2.22, Jorge Morales: Tesla con barra de clima, ~768×880): menos de 900 de alto en el lienzo de
        // 720 de ancho. La barra de botones normal se vuelve compacta para que el mapa tenga espacio. Las verticales de
        // siempre (768×1024 = 960, 1080×1920 = 1280) no cambian.
        shortVertical = vertical && H / (W / 720f) < 900;
        if (shortVertical && !noDock) compactDock = true;
        float dw = 1024 + (sideDock ? RAIL + 8 : 0), dh = sideDock ? 460 : 600;
        s = Math.min(W / dw, H / dh);
        if (vertical) buildVerticalMetrics(W, H);
        else {
        // pantallas más anchas que el lienzo (p. ej. 12,3" 1920x720): el mapa del centro se queda con el espacio extra
        ex = Math.max(0f, W / s - dw);
        if (ex < 24) ex = 0;
        // con la barra lateral en pantallas que no son tan bajas (p. ej. 1024x600) las tarjetas crecen hasta llenar
        // el alto, en vez de dejar franjas vacías arriba y abajo
        if (sideDock && H / s > dh + 1) dh = H / s;
        uiS = Math.min(W / 1024f, H / 600f);
        boardH = dh;
        ox = sideDock && railLeft ? RAIL + 8 : 0;   // la barra lateral, por defecto del lado del reproductor (el del conductor)
        // compacta (2.17, Druids07): más baja que antes (500), con menos espacio arriba y abajo
        dockTop = sideDock ? dh : noDock ? 600 : compactDock ? 512 : 456;
        cardH = sideDock ? dh - 80 : dockTop - 76;
        mapY = rowY = 64;
        mapH = cardH;
        insL = insR = 0;
        }
        if (vertical) dh = boardH;
        if (premium) premiumMetrics(); else layoutColumns();
        getWindow().setBackgroundDrawable(new ColorDrawable(sim > 0 ? Color.BLACK : premium ? Prem.base(LIGHT) : BG));
        if (premium) {
            // fondo con el arco difuminado y el grano (pintado una vez, a media resolución)
            premBg = Prem.background(Math.max(1, W), Math.max(1, H), LIGHT, RED);
            android.graphics.drawable.BitmapDrawable ab = new android.graphics.drawable.BitmapDrawable(getResources(), premBg);
            premBgHolder = sim > 0 && root.getChildCount() > 0 ? root.getChildAt(0) : root;
            premBgHolder.setBackground(ab);
        } else { premBg = null; premBgHolder = null; }
        board = new FrameLayout(this);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams((int) ((dw + ex) * s), (int) (dh * s));
        blp.gravity = Gravity.CENTER;
        root.addView(board, blp);

        if (!sideDock && !noDock && !premium) {
            // franja del dock a todo el ancho (aunque la pantalla no sea 1024x600)
            View dockBg = new View(this);
            dockBg.setBackgroundColor(DOCK);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (H - ((H - dh * s) / 2 + dockTop * s)) + 1);
            dlp.gravity = Gravity.BOTTOM;
            root.addView(dockBg, 0, dlp);
        }

        carbMaintBox = null;
        if (premium) buildPremium();
        else {
            buildTop();
            buildCarCard();
            buildAaCard();
            buildAudioCard();
            if (!noDock) buildDock();
        }
        if (!showMap) updateNavHud();
        // vertical con solo la tarjeta del vehículo: flota encima del mapa
        if (!premium && vertical && showCar && !showPlayer && carCard != null) carCard.bringToFront();

        drawer = null;
        settingsView = null;
        updateClock();
        refreshStates();
        updateStatusIcons();
        showWeather();
        if (settingsCat >= 0) showSettings(settingsCat);
    }

    /**
     * Columnas del inicio: tarjeta del vehículo (300), mapa (lo que sobra) y reproductor (248; 300 si no hay tarjeta
     * del vehículo). «Reproductor a la izquierda» las intercambia para dejarlo del lado del conductor.
     */
    /**
     * Medidas del diseño vertical. Lienzo de 720 de ancho (los textos quedan del mismo tamaño físico que en una
     * pantalla horizontal de 9") y tan alto como la pantalla. Se reparten así, de arriba abajo: barra de arriba (64),
     * mapa (lo que sobra), fila de tarjetas y barra de botones.
     */
    /** El diseño vertical cabe si la pantalla es casi cuadrada o más alta que ancha. */
    private static boolean canVertical(int W, int H) { return H >= W * 0.95f; }
    private int boxW, boxH;
    private boolean shortVertical;

    private void buildVerticalMetrics(int W, int H) {
        s = W / 720f;
        float dh = H / s;
        ex = 720 - 1024;   // negativo: lo que va pegado a la derecha se corre hacia adentro
        uiS = s;
        boardH = dh;
        ox = 0;
        float dockH = noDock ? 0 : compactDock ? 88 : 144;
        dockTop = dh - dockH;
        float avail = dockTop - 12 - 64;   // del borde de arriba del mapa al borde de abajo de las tarjetas
        mapY = 64;
        insL = insR = 0;
        if (!showMap) {
            mapH = 0;
            if (showCar && !showPlayer) {
                cardH = Math.min(440, avail);
                rowY = mapY + (avail - cardH) * 0.5f;
            } else {
                cardH = avail;
                rowY = mapY;
            }
        } else if (showCar && showPlayer && shortVertical) {
            // vertical bajo: tarjetas más bajas para que el mapa quede de un tamaño parecido al de ellas
            cardH = Math.max(330, Math.min(360, avail * 0.5f));
            mapH = avail - cardH - 12;
            rowY = mapY + mapH + 12;
        } else if (showCar && showPlayer) {
            // mapa arriba, y debajo el vehículo y el reproductor lado a lado
            cardH = Math.max(380, Math.min(470, avail * 0.38f));
            if (avail - cardH - 12 < 170) cardH = Math.max(300, avail - 12 - 170);
            mapH = avail - cardH - 12;
            rowY = mapY + mapH + 12;
        } else if (showPlayer) {
            // solo el reproductor: una franja ancha debajo del mapa
            cardH = 156;
            mapH = avail - cardH - 12;
            rowY = mapY + mapH + 12;
        } else if (showCar) {
            // solo el vehículo: el mapa ocupa todo y la tarjeta flota en una esquina de abajo
            mapH = avail;
            cardH = Math.min(380, avail - 24);
            rowY = mapY + mapH - 12 - cardH;
            if (playerLeft) insR = 312; else insL = 312;
        } else {
            mapH = avail;
            cardH = 380;
            rowY = mapY;
        }
    }

    private void layoutColumns() {
        if (!showMap) {
            mapX = 16; mapW = 0;
            if (vertical) {
                if (showCar && showPlayer) {
                    audW = 688 - 300 - 12;
                    carX = playerLeft ? 16 + audW + 12 : 16;
                    audX = playerLeft ? 16 : 16 + 300 + 12;
                } else {
                    audW = 688;
                    audX = 16;
                    carX = (720 - 300) * 0.5f;
                }
                return;
            }
            float content = 1024 + ex;
            float x0 = ox + 16;
            if (showCar && showPlayer) {
                float carW = 300;
                audW = Math.max(248, content - 32 - 12 - carW);
                carX = playerLeft ? x0 + audW + 12 : x0;
                audX = playerLeft ? x0 : x0 + carW + 12;
            } else if (showPlayer) {
                audW = content - 32;
                audX = x0;
                carX = x0;
            } else {
                audW = 248;
                audX = x0;
                carX = x0 + Math.max(0, (content - 32 - 300) * 0.5f);
            }
            return;
        }
        if (vertical) {
            mapX = 16;
            mapW = 688;
            if (showCar && showPlayer) {
                audW = 688 - 300 - 12;
                carX = playerLeft ? 16 + audW + 12 : 16;
                audX = playerLeft ? 16 : 16 + 300 + 12;
            } else {
                audW = 688;
                audX = 16;
                carX = playerLeft ? 16 + 688 - 12 - 300 : 16 + 12;
            }
            return;
        }
        float content = 1024 + ex;
        audW = showCar ? 248 : 300;
        float carW = showCar ? 300 : 0;
        float audSlot = showPlayer ? audW : 0;
        float leftW = playerLeft ? audSlot : carW, rightW = playerLeft ? carW : audSlot;
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
        if (!showCar && carSpeed && !prefs.getBoolean("map_gauge", false)) {
            // sin tarjeta del vehículo, la velocidad se muestra aquí
            topSpeedTv = tv("", 15, TEXT, true);
            topSpeedTv.setPadding((int) (16 * s), 0, 0, 0);
            row.addView(topSpeedTv);
        }
        topOilTv = null;
        topIconName = null;
        if (!showCar && oilHome) {
            // …y también el aceite (toca para abrir su menú), para que no se pierda al ocultar la tarjeta
            topOilTv = tv("", 13, TEXT, true);
            topOilTv.setBackground(round(CIRCLE, 12));
            topOilTv.setPadding((int) (10 * s), (int) (4 * s), (int) (12 * s), (int) (4 * s));
            setTopIcon("water_drop");
            topOilTv.setCompoundDrawablePadding((int) (6 * s));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
            olp.leftMargin = (int) (14 * s);
            row.addView(topOilTv, olp);
            topOilTv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { badgeMenu(); } });
        }
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(round(CIRCLE, 14));
        pill.setPadding((int) (8 * s), (int) (4 * s), (int) (11 * s), (int) (4 * s));
        FrameLayout pIcBox = new FrameLayout(this);
        pIcBox.setBackground(round(RED, 8));
        topNavIc = new Nav.ManeuverView(this);
        pIcBox.addView(topNavIc, new FrameLayout.LayoutParams(-1, -1));
        pill.addView(pIcBox, new LinearLayout.LayoutParams((int) (22 * s), (int) (22 * s)));
        topNavDistTv = tv("", 13, TEXT, true);
        topNavDistTv.setPadding((int) (7 * s), 0, 0, 0);
        pill.addView(topNavDistTv);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.leftMargin = (int) (12 * s);
        plp.gravity = Gravity.CENTER_VERTICAL;
        pill.setVisibility(View.GONE);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openNavMenu(); } });
        topNavPill = pill;
        row.addView(pill, plp);
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
        if (!showCar || vertical) sep.setVisibility(View.GONE);   // sin saludo, sin separador
        if (vertical) {
            // en vertical no caben la ciudad ni el saludo: quedan la hora, la fecha corta, el clima y los íconos
            weatherCityTv.setVisibility(View.GONE);
            if (!showCar) dateTv.setVisibility(View.GONE);
        }
        weatherBox.setVisibility(View.GONE);
        weatherBox.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showWeatherDetail(); } });
        // Íconos de Wi-Fi y Bluetooth (2.17, Reinaldo y Daniel): ver si están activos sin abrir los ajustes
        wifiIc = null;
        btIc = null;
        addCarData(right);
        if (prefs.getBoolean("status_icons", true)) {
            LinearLayout st = new LinearLayout(this);
            st.setGravity(Gravity.CENTER_VERTICAL);
            st.setPadding(0, 0, (int) (8 * s), 0);
            wifiIc = statusIcon(st, "wifi", new Runnable() { public void run() { toggleWifi(); } },
                new Runnable() { public void run() { openSettingsPage(Settings.ACTION_WIFI_SETTINGS, new Runnable() { public void run() { openSystemSettings(); } }); } });
            btIc = statusIcon(st, "bluetooth", new Runnable() { public void run() { toggleBt(); } },
                new Runnable() { public void run() {
                    if (clwBt) openSlot(1);
                    else openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS, new Runnable() { public void run() { openSlot(1); } });
                } });
            right.addView(st);
        }
        right.addView(weatherBox);
        greetTv = tv("", 15, SOFT, false);
        greetTv.setPadding((int) (14 * s), 0, 0, 0);
        right.addView(greetTv);
        // sin la tarjeta del vehículo, la velocidad y el aceite ocupan la barra de arriba: el saludo se oculta
        if (!showCar || vertical) greetTv.setVisibility(View.GONE);
        place(board, right, ox + 400 + ex - (noDock ? 50 : 0), 14, 552, 32);
        if (noDock) {
            View appsBtn = circleIcon("apps", 40, 22, CIRCLE);
            place(board, appsBtn, ox + 914 + ex, 12, 40, 40);
            appsBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openDrawer(); } });
        }
        View gear = circleIcon("settings", 40, 22, CIRCLE);
        place(board, gear, ox + 964 + ex, 12, 40, 40);
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
        greetTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
    }

    private ImageView statusIcon(LinearLayout parent, String name, final Runnable tap, final Runnable hold) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = icon(name, 20);
        iv.setTag(name);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (20 * s), (int) (20 * s));
        lp.gravity = Gravity.CENTER;
        f.addView(iv, lp);
        parent.addView(f, new LinearLayout.LayoutParams((int) (36 * s), (int) (32 * s)));
        f.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { tap.run(); } });
        f.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { hold.run(); return true; } });
        return iv;
    }

    private void setStatusIcon(ImageView iv, int state, String on, String off, String connected) {
        View box = (View) iv.getParent();
        if (state < 0) { box.setVisibility(View.GONE); return; }
        box.setVisibility(View.VISIBLE);
        String name = state == 0 ? off : state == 2 ? connected : on;
        if (!name.equals(iv.getTag())) {
            iv.setImageBitmap(asset("ic/" + name + ".png"));
            iv.setTag(name);
        }
        iv.setAlpha(state == 2 ? 1f : 0.45f);
    }

    /** Arriba: Wi-Fi y Bluetooth (atenuado = encendido sin conexión; tachado = apagado). */
    private void updateStatusIcons() {
        if (wifiIc != null) setStatusIcon(wifiIc, wifiState(), "wifi", "wifi_off", "wifi");
        if (btIc != null) setStatusIcon(btIc, btState(), "bluetooth", "bluetooth_disabled", "bluetooth_connected");
    }

    private final Runnable statusRefresh = new Runnable() { public void run() { updateStatusIcons(); } };

    private android.net.wifi.WifiManager wifiMgr() {
        try { return (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE); }
        catch (Throwable e) { return null; }
    }

    /** -1 sin Wi-Fi, 0 apagado, 1 encendido sin conexión, 2 conectado. */
    private int wifiState() {
        try {
            android.net.wifi.WifiManager wm = wifiMgr();
            if (wm == null) return -1;
            if (!wm.isWifiEnabled()) return 0;
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm != null ? cm.getNetworkInfo(android.net.ConnectivityManager.TYPE_WIFI) : null;
            return ni != null && ni.isConnected() ? 2 : 1;
        } catch (Throwable e) { return -1; }
    }

    /**
     * -1 sin Bluetooth, 0 apagado, 1 encendido sin celular, 2 celular conectado. En pantallas Clinkway el Bluetooth es
     * un módulo aparte de Android: el estado sale de su servicio (nunca «apagado»).
     */
    private int btState() {
        try {
            if (clwBt) {
                Boolean a = bt != null ? bt.a2dp() : null;
                return a != null && a ? 2 : 1;
            }
            android.bluetooth.BluetoothAdapter ad = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (ad == null) return -1;
            if (!ad.isEnabled()) return 0;
            for (int p : new int[]{2 /* A2DP */, 1 /* HEADSET */, 11 /* A2DP_SINK */, 16 /* HEADSET_CLIENT */}) {
                try { if (ad.getProfileConnectionState(p) == android.bluetooth.BluetoothProfile.STATE_CONNECTED) return 2; }
                catch (Throwable ignored) { }
            }
            return 1;
        } catch (Throwable e) { return -1; }
    }

    private void toggleWifi() {
        final android.net.wifi.WifiManager wm = wifiMgr();
        if (wm == null) return;
        final boolean on = wm.isWifiEnabled();
        if (on && wifiState() == 2) {
            new TB(this, dlg()).setTitle("¿Apagar el Wi-Fi?")
                .setMessage("La pantalla se queda sin internet (mapa en vivo, clima y actualizaciones).")
                .setPositiveButton("Apagar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { setWifi(wm, false); } })
                .setNegativeButton("Cancelar", null).show();
        } else setWifi(wm, !on);
    }

    private void setWifi(android.net.wifi.WifiManager wm, boolean v) {
        boolean ok = false;
        try { ok = wm.setWifiEnabled(v); } catch (Throwable ignored) { }
        if (!ok) {
            toast("Esta pantalla no deja cambiar el Wi-Fi desde otra app: abre sus ajustes");
            openSettingsPage(Settings.ACTION_WIFI_SETTINGS, new Runnable() { public void run() { openSystemSettings(); } });
        } else toast(v ? "Encendiendo el Wi-Fi…" : "Wi-Fi apagado");
        h.postDelayed(statusRefresh, 800);
        h.postDelayed(statusRefresh, 3000);
    }

    private void toggleBt() {
        if (clwBt) { openSlot(1); return; }
        final android.bluetooth.BluetoothAdapter ad;
        try { ad = android.bluetooth.BluetoothAdapter.getDefaultAdapter(); } catch (Throwable e) { return; }
        if (ad == null) return;
        final boolean on = ad.isEnabled();
        if (on && btState() == 2) {
            new TB(this, dlg()).setTitle("¿Apagar el Bluetooth?")
                .setMessage("Se desconecta el celular (música y llamadas).")
                .setPositiveButton("Apagar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { setBt(ad, false); } })
                .setNegativeButton("Cancelar", null).show();
        } else setBt(ad, !on);
    }

    private void setBt(android.bluetooth.BluetoothAdapter ad, boolean v) {
        boolean ok = false;
        try { ok = v ? ad.enable() : ad.disable(); } catch (Throwable ignored) { }
        if (!ok) {
            toast("Esta pantalla no deja cambiar el Bluetooth desde otra app: abre sus ajustes");
            openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS, new Runnable() { public void run() { openSlot(1); } });
        } else toast(v ? "Encendiendo el Bluetooth…" : "Bluetooth apagado");
        h.postDelayed(statusRefresh, 800);
        h.postDelayed(statusRefresh, 3000);
    }

    /**
     * Abre una pantalla de los Ajustes de Android (Wi-Fi, Bluetooth). En Android 10+, si el launcher de fábrica no
     * muestra los Ajustes de Android, no se abren (lección de la TS10: quedaba todo en negro) y se usa la alternativa.
     */
    private void openSettingsPage(String action, Runnable fallback) {
        if (SDK >= 29) {
            boolean aospVisible = false;
            for (ResolveInfo r : apps) if ("com.android.settings".equals(r.activityInfo.packageName)) { aospVisible = true; break; }
            if (!aospVisible) { fallback.run(); return; }
        }
        try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable e) { fallback.run(); }
    }

    private void buildCarCard() {
        final FrameLayout card = card(carX, rowY, 300, cardH, CARD);
        carCard = card;
        // barra de abajo compacta: la foto crece y la velocidad baja; sin velocidad (carros sin GPS) la foto llena el resto
        // 2.20: velocímetro analógico o mixto: el recuadro de la velocidad crece 70 y la foto se achica lo mismo
        final int speedo = carSpeed ? prefs.getInt("speedo", 0) : 0;
        // 2.20.1: el velocímetro analógico ocupa todo el ancho y el mantenimiento va debajo, dentro del arco
        // el recuadro y la foto quedan del tamaño de la 2.20; adentro, velocímetro centrado y barrita de mantenimiento abajo
        final float gx = speedo > 0 ? 70 : 0;
        final float dy = cardH - 380 + (carSpeed ? 0 : 130) - gx;
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
            // 2.18: una foto con fondo (sin recortar) llena el recuadro con esquinas redondas; la recortada, como siempre
            boolean cutout = false;
            try { cutout = CarPhoto.hasTransparency(photo); } catch (Throwable ignored) { }
            if (cutout) car.setImageBitmap(photo);
            else car.setImageBitmap(CarPhoto.cardCrop(photo, (int) (264 * s), (int) ((160 + dy) * s), 16 * s));
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
            t.setSingleLine(false);   // con fuentes anchas (Samsung) se salía por la izquierda
            t.setMaxLines(2);
            t.setGravity(Gravity.CENTER);
            t.setPadding((int) (12 * s), (int) (6 * s), (int) (12 * s), 0);
            ph.addView(t, new LinearLayout.LayoutParams(-1, -2));
            if (152 + dy < 110) t.setVisibility(View.GONE);   // con el velocímetro analógico el recuadro es bajo
            place(card, ph, 18, 82, 264, 152 + dy);
            ph.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { pickPhoto(); }
            });
        }

        FrameLayout box = new FrameLayout(this);
        box.setBackground(round(INNER, 16));
        place(card, box, 12, 250 + dy, 276, 118 + gx);
        if (!carSpeed) box.setVisibility(View.GONE);   // se arma igual: setGps y el kilometraje siguen funcionando

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
        carGauge = null;
        if (speedo > 0) {
            lbl.setVisibility(View.GONE);
            sv.setVisibility(View.GONE);
            carGauge = new Gauge(this, s);
            carGauge.setColors(RED, TEXT, SOFT);
            carGauge.setUnit(useMph());
            carGauge.setNumber(speedo == 2);
            // Velocímetro centrado, lo más grande que quepa; debajo (afuera del velocímetro) la barrita del mantenimiento
            float bh = 118 + gx, barH = 32, gaugeH = oilHome ? bh - barH - 8 : bh - 6;
            carGauge.setCompact(true);
            place(box, carGauge, 4, 3, 268, gaugeH);
            g.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            gpsTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, gpsTv.getTextSize() * 10f / 11f);
            box.removeView(g);   // ya estaba puesto: se vuelve a poner en la esquina de arriba a la derecha, fuera del aro
            place(box, g, 178, 4, 90, 16);
            if (oilHome) buildOilStrip(box, 10, bh - barH - 6, 256, barH);
        }
        if (oilHome && speedo == 0) buildOilBadge(box, 0, 0);
        if (!oilHome) { oilValTv = null; oilBar = null; oilBarBg = null; oilLblTv = null; oilIc = null; }
        // Oculta: se arma igual (la velocidad y el aceite siguen actualizándose sin fallar) pero no se muestra.
        if (!showCar) board.removeView(card);
        setGps(false, 0);
    }

    // ───────────────────────── recordatorio de cambio de aceite ─────────────────────────
    // StradaOS no puede leer el odómetro del tablero, así que lo estima: el usuario escribe el kilometraje una vez
    // y StradaOS le suma la distancia recorrida que mide el GPS. Todo se guarda en km; se muestra en km o millas.

    private TextView oilValTv, oilLblTv;
    private ImageView oilIc;
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
        return I18n.integer(Math.round(toUnit(km))) + " " + unit();
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
        if (!validLoc(l)) return;
        if (!odoNeeded()) return;
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
        boolean want = odoNeeded();
        if (want == odoRunning) return;
        try {
            if (want) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 10, odoListener);
            else lm.removeUpdates(odoListener);
            odoRunning = want;
        } catch (Throwable ignored) { }
        odoLast = null;
    }

    private void buildOilBadge(FrameLayout box, float oy, float ox) {
        View sep = new View(this);
        sep.setBackgroundColor(BTN);
        place(box, sep, 166 + ox, 40 + oy, 1, 62);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = icon("water_drop", 13);
        ic.setAlpha(0.6f);
        top.addView(ic, new LinearLayout.LayoutParams((int) (13 * s), (int) (13 * s)));
        TextView t = tv("Aceite", 11, MUTED, false);
        t.setPadding((int) (4 * s), 0, 0, 0);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        oilLblTv = t;
        oilIc = ic;
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
        place(box, col, 176 + ox, 44 + oy, 88 - ox, 58);
        col.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { badgeMenu(); } });
        updateOilBadge();
    }

    /** Barrita del mantenimiento debajo del velocímetro analógico o mixto (2.20.1): ícono, nombre, lo que falta y la barra. */
    private void buildOilStrip(FrameLayout box, float x, float y, float w, float h) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(round(BTN, 9));
        col.setPadding((int) (10 * s), (int) (4 * s), (int) (10 * s), (int) (5 * s));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = icon("water_drop", 14);
        ic.setAlpha(0.7f);
        row.addView(ic, new LinearLayout.LayoutParams((int) (14 * s), (int) (14 * s)));
        TextView t = tv("Aceite", 12, MUTED, false);
        t.setPadding((int) (6 * s), 0, (int) (6 * s), 0);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        oilLblTv = t;
        oilIc = ic;
        oilValTv = tv("", 13, TEXT, true);
        oilValTv.setSingleLine(true);
        row.addView(oilValTv, new LinearLayout.LayoutParams(-2, -2));
        col.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout bar = new FrameLayout(this);
        oilBarBg = bar;
        bar.setBackground(round(CARD, 2));
        oilBar = new View(this);
        bar.addView(oilBar, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, (int) (3 * s));
        blp.topMargin = (int) (2 * s);
        col.addView(bar, blp);
        place(box, col, x, y, w, h);
        col.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { badgeMenu(); } });
        updateOilBadge();
    }

    private void updateOilBadge() {
        startOdo();
        renderFaroMaint();
        // 2.20: con mantenimientos propios, el recuadro y la barra de arriba muestran el más próximo
        int ni = nearestMaint();
        badgeTask = ni;
        if (ni >= 0) { showTaskBadge(ni); return; }
        if (oilLblTv != null) { oilLblTv.setText(I18n.t("Aceite")); oilIc.setImageBitmap(asset("ic/water_drop.png")); }
        setTopIcon("water_drop");
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
                    else { prefs.edit().putBoolean("oil_on", false).apply(); updateOilBadge(); if (settingsView != null) showSettings(settingsCat); }
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
                                if (settingsView != null) showSettings(settingsCat);
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
                if (settingsView != null) showSettings(settingsCat);
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
            if (next != null) next.run(); else if (settingsView != null) showSettings(settingsCat);
        }});
    }

    private void askInterval(final Runnable next) {
        float def = prefs.contains("oil_interval_km") ? toUnit(prefs.getFloat("oil_interval_km", 5000)) : (useMph() ? 3000 : 5000);
        askNumber(I18n.t("¿Cada cuántos " + unit() + " cambias el aceite?"), def, new NumCb() { public void on(float v) {
            if (v < 100) { toast("Escribe un número válido"); return; }
            prefs.edit().putFloat("oil_interval_km", fromUnit(v)).putFloat("oil_snooze_km", 0).apply();
            updateOilBadge();
            if (next != null) next.run(); else if (settingsView != null) showSettings(settingsCat);
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
            if (settingsView != null) showSettings(settingsCat);
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

    // ───────────────────────── mantenimientos a medida (2.20) ─────────────────────────
    // Tareas propias además del aceite: cada X km, cada N meses o lo que llegue primero. Se guardan como JSON en
    // «maint»: t (plantilla) o n (nombre propio), km (intervalo, 0 = no), mo (meses, 0 = no), lkm (km de la última
    // vez), ld (fecha de la última vez, ms), sz / sd (aviso pospuesto hasta ese km / esa fecha).
    // 2.20.1: también cada N días o semanas: dd (intervalo en días) y tu ("d" o "w"); en ese caso mo = 0.
    // Por meses se sigue guardando en «mo» como antes, así que lo ya configurado no cambia.
    // En las plantillas, la columna de tiempo es meses ("12") o lleva unidad ("2w" = cada 2 semanas, "10d").

    static final String[][] MAINT_TEMPLATES = {
        {"air", "Filtro de aire", "15000", "12", "air"},
        {"fuelfilter", "Filtro de combustible", "40000", "0", "filter_alt"},
        {"plugs", "Bujías", "30000", "0", "electric_bolt"},
        {"brakes", "Pastillas de freno", "25000", "0", "album"},
        {"brakefluid", "Líquido de frenos", "0", "24", "opacity"},
        {"coolant", "Refrigerante", "40000", "24", "severe_cold"},
        {"belt", "Correa de distribución", "60000", "60", "settings"},
        {"rotation", "Rotación de llantas", "10000", "0", "autorenew"},
        {"align", "Alineación y balanceo", "10000", "6", "tire_repair"},
        {"battery", "Revisión de la batería", "0", "12", "battery_charging_full"},
        {"ac", "Aire acondicionado", "0", "12", "ac_unit"},
        {"insurance", "Seguro obligatorio (SOAT)", "0", "12", "verified_user"},
        {"inspection", "Revisión técnico-mecánica", "0", "12", "fact_check"},
        {"wash", "Lavado", "0", "2w", "local_car_wash"},
    };
    /** Íconos para elegir al crear un mantenimiento. */
    static final String[] MAINT_ICONS = {"build", "car_repair", "oil_barrel", "air", "filter_alt", "electric_bolt", "album", "opacity",
        "severe_cold", "thermostat", "settings", "autorenew", "tire_repair", "battery_charging_full", "ac_unit", "lightbulb",
        "local_gas_station", "local_car_wash", "cleaning_services", "handyman", "verified_user", "fact_check", "water_drop", "speed"};
    private static final double MONTH_MS = 30.44 * 86400000d;
    private int badgeTask = -1;
    private static boolean maintLater;

    private List<org.json.JSONObject> maint() {
        List<org.json.JSONObject> out = new ArrayList<org.json.JSONObject>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs.getString("maint", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i));
        } catch (Throwable ignored) { }
        return out;
    }

    private void saveMaint(List<org.json.JSONObject> l) {
        org.json.JSONArray a = new org.json.JSONArray();
        for (org.json.JSONObject o : l) a.put(o);
        prefs.edit().putString("maint", a.toString()).apply();
        updateOilBadge();
    }

    private String taskIcon(org.json.JSONObject o) {
        String ic = o.optString("ic", "");
        if (ic.length() > 0) return ic;
        String t = o.optString("t", "");
        for (String[] tp : MAINT_TEMPLATES) if (tp[0].equals(t)) return tp[4];
        return "build";
    }

    interface StrCb { void on(String v); }

    /** Cuadrícula de íconos de mantenimiento (2.20, Andrés): para reconocer cada uno sin leer. */
    private void pickIcon(String cur, final StrCb cb) {
        final android.app.AlertDialog[] dref = new android.app.AlertDialog[1];
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * s);
        grid.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout rowL = null;
        for (int i = 0; i < MAINT_ICONS.length; i++) {
            if (i % 6 == 0) { rowL = new LinearLayout(this); rowL.setGravity(Gravity.CENTER); grid.addView(rowL); }
            final String ic = MAINT_ICONS[i];
            FrameLayout cell = new FrameLayout(this);
            cell.setBackground(round(ic.equals(cur) ? RED : BTN, 14));
            ImageView iv = icon(ic, 26);
            if (ic.equals(cur)) iv.setColorFilter(Color.WHITE);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams((int) (26 * s), (int) (26 * s));
            ilp.gravity = Gravity.CENTER;
            cell.addView(iv, ilp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (52 * s), (int) (52 * s));
            lp.setMargins((int) (5 * s), (int) (5 * s), (int) (5 * s), (int) (5 * s));
            rowL.addView(cell, lp);
            cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                cb.on(ic);
            }});
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(grid);
        dref[0] = new TB(this, dlg()).setTitle("Elige un ícono").setView(sv).setNegativeButton("Cancelar", null).show();
    }

    private String taskName(org.json.JSONObject o) {
        String n = o.optString("n", "");
        if (n.length() > 0) return n;
        String t = o.optString("t", "");
        for (String[] tp : MAINT_TEMPLATES) if (tp[0].equals(t)) return I18n.t(tp[1]);
        return I18n.t("Mantenimiento");
    }

    /** El odómetro estimado se necesita para el aceite o para alguna tarea por kilómetros. */
    private boolean odoNeeded() {
        if (oilOn()) return true;
        for (org.json.JSONObject o : maint()) if (o.optDouble("km", 0) > 0) return true;
        return false;
    }

    private double taskKmLeft(org.json.JSONObject o) { return o.optDouble("lkm", 0) + o.optDouble("km", 0) - odoKm(); }
    private double taskDaysLeft(org.json.JSONObject o) {
        return (o.optLong("ld", 0) + taskDays(o) * 86400000d - System.currentTimeMillis()) / 86400000d;
    }

    /** Intervalo de tiempo en días (0 = no va por tiempo). */
    private double taskDays(org.json.JSONObject o) {
        double dd = o.optDouble("dd", 0);
        return dd > 0 ? dd : o.optDouble("mo", 0) * MONTH_MS / 86400000d;
    }
    /** Unidad con la que se configuró: 'd' días, 'w' semanas, 'm' meses. */
    private char taskUnit(org.json.JSONObject o) {
        if (o.optDouble("dd", 0) <= 0) return 'm';
        return "w".equals(o.optString("tu", "")) ? 'w' : 'd';
    }
    /** El número que escribió el usuario (en su unidad). */
    private long taskTimeN(org.json.JSONObject o) {
        char u = taskUnit(o);
        return Math.round(u == 'm' ? o.optDouble("mo", 0) : u == 'w' ? o.optDouble("dd", 0) / 7 : o.optDouble("dd", 0));
    }
    private void setTaskTime(org.json.JSONObject o, double n, char u) {
        try {
            o.remove("dd"); o.remove("tu");
            if (n <= 0) { o.put("mo", 0); return; }
            if (u == 'm') { o.put("mo", n); return; }
            o.put("mo", 0).put("dd", u == 'w' ? n * 7 : n).put("tu", String.valueOf(u));
        } catch (Throwable ignored) { }
    }
    /** Tiempo de una plantilla: "12" (meses), "2w" (semanas) o "10d" (días). */
    private void setTaskTime(org.json.JSONObject o, String tp) {
        char u = 'm';
        if (tp.endsWith("w") || tp.endsWith("d")) { u = tp.charAt(tp.length() - 1); tp = tp.substring(0, tp.length() - 1); }
        setTaskTime(o, Double.parseDouble(tp), u);
    }
    private static String timeText(long n, char u) {
        if (u == 'd') return n == 1 ? "cada día" : "cada " + n + " días";
        if (u == 'w') return n == 1 ? "cada semana" : "cada " + n + " semanas";
        return n == 1 ? "cada mes" : "cada " + n + " meses";
    }

    /** Qué tan cerca está (1 = recién hecho, 0 o menos = ya toca), por km o por fecha, lo que llegue primero. */
    private double taskFrac(org.json.JSONObject o) {
        double f = 9;
        double km = o.optDouble("km", 0), days = taskDays(o);
        if (km > 0 && prefs.contains("odo_base_km")) f = Math.min(f, taskKmLeft(o) / km);
        if (days > 0) f = Math.min(f, taskDaysLeft(o) / days);
        return f;
    }

    private String taskLeftText(org.json.JSONObject o) {
        double km = o.optDouble("km", 0), days = taskDays(o);
        boolean byKm = km > 0 && prefs.contains("odo_base_km");
        boolean useKm = byKm && (days <= 0 || taskKmLeft(o) / km <= taskDaysLeft(o) / days);
        if (taskFrac(o) <= 0) return I18n.t("¡Ya toca!");
        if (useKm) return fmtDist((float) taskKmLeft(o));
        long d = Math.round(Math.ceil(taskDaysLeft(o)));
        if (d >= 60) return I18n.t(I18n.integer(Math.round(d / 30.44)) + " meses");
        return I18n.t(I18n.integer(d) + (d == 1 ? " día" : " días"));
    }

    private String taskEvery(org.json.JSONObject o) {
        double km = o.optDouble("km", 0);
        String a = km > 0 ? I18n.t("Cada " + fmtDist((float) km)) : "";
        long tn = taskTimeN(o);
        String b = taskDays(o) > 0 ? I18n.t(timeText(tn, taskUnit(o)).replace(String.valueOf(tn), I18n.integer(tn))) : "";
        if (a.length() > 0 && b.length() > 0) return a + " · " + b + " " + I18n.t("(lo primero)");
        return a.length() > 0 ? a : b.length() > 0 ? Character.toUpperCase(b.charAt(0)) + b.substring(1) : "";
    }

    /**
     * El mantenimiento que más se acerca (índice), o -1 si es el aceite o no hay tareas. El aceite compite con la
     * misma medida (fracción que falta), así que sin tareas propias todo se ve exactamente como antes.
     */
    private int nearestMaint() {
        List<org.json.JSONObject> l = maint();
        if (l.isEmpty()) return -1;
        double best = oilOn() ? (oilNextKm() - odoKm()) / Math.max(1, prefs.getFloat("oil_interval_km", 5000)) : 99;
        int bi = -1;
        for (int i = 0; i < l.size(); i++) {
            double f = taskFrac(l.get(i));
            if (f < best) { best = f; bi = i; }
        }
        return bi;
    }

    /** Ícono de la píldora de arriba (aceite o el mantenimiento que se esté mostrando). */
    private String topIconName;
    private void setTopIcon(String name) {
        if (topOilTv == null || name.equals(topIconName) && topOilTv.getCompoundDrawables()[0] != null) return;
        topIconName = name;
        android.graphics.drawable.BitmapDrawable di = new android.graphics.drawable.BitmapDrawable(getResources(), asset("ic/" + name + ".png"));
        di.setBounds(0, 0, (int) (14 * s), (int) (14 * s));
        if (ICON != 0xFFFFFFFF) di.setColorFilter(ICON, android.graphics.PorterDuff.Mode.SRC_IN);
        topOilTv.setCompoundDrawables(di, null, null, null);
    }

    private void showTaskBadge(int i) {
        org.json.JSONObject o = maint().get(i);
        double f = taskFrac(o);
        String left = taskLeftText(o);
        boolean due = f <= 0, warn = f < 0.1;
        if (topOilTv != null) {
            setTopIcon(taskIcon(o));
            topOilTv.setText(due ? I18n.t("¡Toca: " + taskName(o) + "!") : taskName(o) + ": " + left);
            topOilTv.setTextColor(warn ? RED : TEXT);
        }
        if (oilValTv == null) return;
        oilLblTv.setText(taskName(o));
        oilIc.setImageBitmap(asset("ic/" + taskIcon(o) + ".png"));
        oilValTv.setText(left);
        oilValTv.setTextColor(warn ? RED : TEXT);
        oilBarBg.setVisibility(View.VISIBLE);
        final float used = (float) Math.max(0, Math.min(1, 1 - f));
        oilBarBg.post(new Runnable() { public void run() {
            ViewGroup.LayoutParams lp = oilBar.getLayoutParams();
            lp.width = Math.max((int) (2 * s), (int) (oilBarBg.getWidth() * used));
            oilBar.setLayoutParams(lp);
            oilBar.setBackground(round(used >= 0.9f ? RED : 0xFF2DBE6C, 2));
        }});
    }

    /** Toque en el recuadro del inicio: el aceite o el mantenimiento que se esté mostrando. */
    private void badgeMenu() {
        if (badgeTask >= 0 && badgeTask < maint().size()) taskMenu(badgeTask);
        else oilMenu();
    }

    /** Aviso en el inicio cuando toca una tarea. «Aún no»: otra vez en 100 km o en 7 días. */
    private boolean maintAlert() {
        if (maintLater || oilLater || (oilDlg != null && oilDlg.isShowing())) return false;
        final List<org.json.JSONObject> l = maint();
        long now = System.currentTimeMillis();
        for (int i = 0; i < l.size(); i++) {
            final org.json.JSONObject o = l.get(i);
            if (taskFrac(o) > 0) continue;
            if (o.optDouble("sz", 0) > odoKm() || o.optLong("sd", 0) > now) continue;
            final int idx = i;
            oilDlg = new TB(this, dlg())
                .setTitle(I18n.t("Es hora de: " + taskName(o)))
                .setMessage(I18n.t(taskEvery(o)) + "\n\n" + I18n.t("¿Ya lo hiciste?"))
                .setPositiveButton("Ya lo hice", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { taskDone(idx, null); } })
                .setNegativeButton("Aún no", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    try {
                        List<org.json.JSONObject> l2 = maint();
                        org.json.JSONObject t = l2.get(idx);
                        t.put("sz", odoKm() + (useMph() ? 60 / 0.621371f : 100));
                        t.put("sd", System.currentTimeMillis() + 7 * 86400000L);
                        saveMaint(l2);
                    } catch (Throwable ignored) { }
                }})
                .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface d) { maintLater = true; } })
                .show();
            return true;
        }
        return false;
    }

    /** Ajustes → Tu vehículo → Otros mantenimientos. */
    private void maintSection(LinearLayout body) {
        LinearLayout m = section(body, "Otros mantenimientos");
        List<org.json.JSONObject> l = maint();
        for (int i = 0; i < l.size(); i++) {
            final int idx = i;
            org.json.JSONObject o = l.get(i);
            rowIc(m, taskIcon(o), taskName(o), taskEvery(o), taskLeftText(o), new Runnable() { public void run() { taskMenu(idx); } });
        }
        rowIc(m, "add_circle", "Agregar mantenimiento", "Filtros, bujías, frenos, correa, SOAT… Avisa por kilómetros, por tiempo o lo que llegue primero", null,
            new Runnable() { public void run() { addTask(); } });
    }

    private void addTask() {
        final String[] names = new String[MAINT_TEMPLATES.length + 1];
        for (int i = 0; i < MAINT_TEMPLATES.length; i++) names[i] = I18n.t(MAINT_TEMPLATES[i][1]);
        names[MAINT_TEMPLATES.length] = I18n.t("Otro (escribe el nombre)");
        new TB(this, dlg()).raw()
            .setTitle(I18n.t("Agregar mantenimiento"))
            .setItems(names, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                final org.json.JSONObject o = new org.json.JSONObject();
                try {
                    if (w < MAINT_TEMPLATES.length) {
                        String[] tp = MAINT_TEMPLATES[w];
                        o.put("t", tp[0]).put("km", Double.parseDouble(tp[2]));
                        setTaskTime(o, tp[3]);
                        pickIcon(tp[4], new StrCb() { public void on(String ic) {
                            try { o.put("ic", ic); } catch (Throwable ignored) { }
                            taskSchedule(o, -1);
                        }});
                    } else {
                        askText(I18n.t("Nombre del mantenimiento"), "", new TextCb() { public void on(String v) {
                            if (v.trim().length() == 0) return;
                            try { o.put("n", v.trim()).put("km", 10000).put("mo", 12); } catch (Throwable ignored) { }
                            pickIcon("build", new StrCb() { public void on(String ic) {
                                try { o.put("ic", ic); } catch (Throwable ignored) { }
                                taskSchedule(o, -1);
                            }});
                        }});
                    }
                } catch (Throwable ignored) { }
            }})
            .setNegativeButton(I18n.t("Cancelar"), null)
            .show();
    }

    /** Cada cuánto: por km, por tiempo o lo que llegue primero; después, la última vez. idx = -1 si es nueva. */
    private void taskSchedule(final org.json.JSONObject o, final int idx) {
        final double km0 = o.optDouble("km", 0), mo0 = taskDays(o);
        final long n0 = taskTimeN(o);
        final char u0 = taskUnit(o);
        int sel = km0 > 0 && mo0 > 0 ? 2 : mo0 > 0 ? 1 : 0;
        String[] opts = {I18n.t("Por kilómetros"), I18n.t("Por tiempo"), I18n.t("Lo que llegue primero")};
        new TB(this, dlg()).raw()
            .setTitle(taskName(o))
            .setSingleChoiceItems(opts, sel, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, final int w) {
                d.dismiss();
                final Runnable askMonths = new Runnable() { public void run() {
                    if (w == 0) { setTaskTime(o, 0, 'm'); taskLast(o, idx); return; }
                    askTime(I18n.t("¿Cada cuánto tiempo?"), mo0 > 0 ? n0 : 12, mo0 > 0 ? u0 : 'm', new TimeCb() { public void on(long n, char u) {
                        setTaskTime(o, n, u);
                        taskLast(o, idx);
                    }});
                }};
                if (w == 1) { try { o.put("km", 0); } catch (Throwable ignored) { } askMonths.run(); return; }
                final Runnable askKm = new Runnable() { public void run() {
                    askNumber(I18n.t("¿Cada cuántos " + unit() + "?"), km0 > 0 ? toUnit((float) km0) : (useMph() ? 6000 : 10000), new NumCb() { public void on(float v) {
                        if (v < 100) { toast("Escribe un número válido"); return; }
                        try { o.put("km", fromUnit(v)); } catch (Throwable ignored) { }
                        askMonths.run();
                    }});
                }};
                // las tareas por km necesitan el kilometraje del tablero (el mismo del aceite)
                if (!prefs.contains("odo_base_km")) {
                    new TB(Main.this, dlg())
                        .setTitle("Kilometraje del tablero")
                        .setMessage("StradaOS no puede leer el kilometraje del carro. Escríbelo una vez y StradaOS le suma lo que recorras con el GPS.")
                        .setPositiveButton("Escribirlo", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dd, int ww) {
                            askOilUnit(new Runnable() { public void run() { askOdo(askKm); } });
                        }})
                        .setNegativeButton("Cancelar", null)
                        .show();
                } else askKm.run();
            }})
            .show();
    }

    /** Última vez: kilometraje (si va por km) y fecha (si va por tiempo). */
    private void taskLast(final org.json.JSONObject o, final int idx) {
        // En Android 4.x el selector de fecha avisa dos veces (al tocar Listo y al cerrarse): se guarda una sola vez
        final boolean[] saved = {false};
        final Runnable finish = new Runnable() { public void run() {
            if (saved[0]) return;
            saved[0] = true;
            try { o.remove("sz"); o.remove("sd"); } catch (Throwable ignored) { }
            List<org.json.JSONObject> l = maint();
            if (idx >= 0 && idx < l.size()) l.set(idx, o); else l.add(o);
            saveMaint(l);
            toast(taskName(o) + ": " + taskLeftText(o));
            if (settingsView != null) showSettings(settingsCat);
        }};
        final Runnable askDate = new Runnable() { public void run() {
            if (taskDays(o) <= 0) { finish.run(); return; }
            pickDate("¿Cuándo fue la última vez?", o.optLong("ld", 0), new LongCb() { public void on(long when) {
                try { o.put("ld", Math.min(System.currentTimeMillis(), when)); } catch (Throwable ignored) { }
                finish.run();
            }});
        }};
        if (o.optDouble("km", 0) > 0) {
            float def = o.has("lkm") ? toUnit((float) o.optDouble("lkm", 0)) : toUnit(odoKm());
            askNumber(I18n.t("¿A cuántos " + unit() + " fue la última vez?"), def, new NumCb() { public void on(float v) {
                try { o.put("lkm", fromUnit(v)); } catch (Throwable ignored) { }
                askDate.run();
            }});
        } else askDate.run();
    }

    private void taskMenu(final int idx) {
        final List<org.json.JSONObject> l = maint();
        if (idx < 0 || idx >= l.size()) return;
        final org.json.JSONObject o = l.get(idx);
        StringBuilder head = new StringBuilder(taskEvery(o));
        if (o.optDouble("km", 0) > 0) head.append("\n").append(I18n.t("Última vez")).append(": ").append(fmtDist((float) o.optDouble("lkm", 0)));
        if (taskDays(o) > 0 && o.optLong("ld", 0) > 0)
            head.append(o.optDouble("km", 0) > 0 ? " · " : "\n" + I18n.t("Última vez") + ": ").append(I18n.sdf("d MMM yyyy").format(new Date(o.optLong("ld", 0))));
        head.append("\n").append(taskFrac(o) <= 0 ? I18n.t("¡Ya toca!") : I18n.t("Faltan " + taskLeftText(o)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        int hp = (int) (22 * s);
        ht.setPadding(hp, hp, hp, (int) (6 * s));
        LinearLayout tt = new LinearLayout(this);
        tt.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ti = icon(taskIcon(o), 24);
        ti.setColorFilter(RED);
        tt.addView(ti, new LinearLayout.LayoutParams((int) (24 * s), (int) (24 * s)));
        TextView tn = tv(taskName(o), 19, TEXT, true);
        tn.setPadding((int) (10 * s), 0, 0, 0);
        tt.addView(tn);
        ht.addView(tt);
        TextView hs = tv(head.toString(), 14, MUTED, false);
        hs.setSingleLine(false);
        hs.setMaxLines(5);
        hs.setLineSpacing(3 * s, 1f);
        hs.setPadding(0, (int) (6 * s), 0, 0);
        ht.addView(hs);
        new TB(this, dlg())
            .setCustomTitle(ht)
            .setItems(new String[]{"Ya lo hice", "Cambiar cada cuánto", "Corregir la última vez", "Cambiar el nombre", "Cambiar el ícono", "Borrar"},
                new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    if (w == 0) taskDone(idx, null);
                    else if (w == 1) taskSchedule(o, idx);
                    else if (w == 2) taskLast(o, idx);
                    else if (w == 3) askText(I18n.t("Nombre del mantenimiento"), taskName(o), new TextCb() { public void on(String v) {
                        if (v.trim().length() == 0) return;
                        try { o.put("n", v.trim()); } catch (Throwable ignored) { }
                        l.set(idx, o); saveMaint(l); if (settingsView != null) showSettings(settingsCat);
                    }});
                    else if (w == 4) pickIcon(taskIcon(o), new StrCb() { public void on(String ic) {
                        try { o.put("ic", ic); } catch (Throwable ignored) { }
                        l.set(idx, o); saveMaint(l); if (settingsView != null) showSettings(settingsCat);
                    }});
                    else new TB(Main.this, dlg())
                        .setTitle(I18n.t("¿Borrar «" + taskName(o) + "»?"))
                        .setPositiveButton("Borrar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dd, int ww) {
                            l.remove(idx); saveMaint(l); if (settingsView != null) showSettings(settingsCat);
                        }})
                        .setNegativeButton("Cancelar", null)
                        .show();
                }})
            .setNegativeButton("Cerrar", null)
            .show();
    }

    /** «Ya lo hice»: si va por km confirma el kilometraje; la fecha es hoy. */
    private void taskDone(final int idx, final Runnable next) {
        final List<org.json.JSONObject> l = maint();
        if (idx < 0 || idx >= l.size()) return;
        final org.json.JSONObject o = l.get(idx);
        final Runnable save = new Runnable() { public void run() {
            try { o.put("ld", System.currentTimeMillis()); o.remove("sz"); o.remove("sd"); } catch (Throwable ignored) { }
            l.set(idx, o);
            saveMaint(l);
            toast(taskName(o) + ": " + taskLeftText(o));
            if (settingsView != null) showSettings(settingsCat);
            if (next != null) next.run();
        }};
        if (o.optDouble("km", 0) > 0) {
            askNumber(I18n.t("Kilometraje del tablero al hacerlo") + " (" + unit() + ")", toUnit(odoKm()), new NumCb() { public void on(float v) {
                float km = fromUnit(v);
                try { o.put("lkm", km); } catch (Throwable ignored) { }
                // de paso corrige el kilometraje estimado con el real del tablero
                prefs.edit().putFloat("odo_base_km", km).putFloat("odo_gps_m", 0).apply();
                odoPending = 0;
                save.run();
            }});
        } else save.run();
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
        // 2.21: recuadro redondeado del color del tema (antes, la raya del sistema, distinta en cada Android)
        et.setBackground(round(INNER, 14));
        et.setPadding((int) (16 * s), (int) (12 * s), (int) (16 * s), (int) (12 * s));
        et.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18 * s * FONT);
        return et;
    }

    interface TimeCb { void on(long n, char unit); }

    /** Número + unidad (días, semanas o meses) en un solo diálogo. */
    private void askTime(String title, long def, char unit, final TimeCb cb) {
        final android.widget.EditText et = field();
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(def));
        et.setSelection(et.getText().length());
        final android.widget.RadioGroup rg = new android.widget.RadioGroup(this);
        rg.setOrientation(LinearLayout.HORIZONTAL);
        final char[] units = {'d', 'w', 'm'};
        String[] names = {"Días", "Semanas", "Meses"};
        for (int i = 0; i < 3; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(new android.view.ContextThemeWrapper(this,
                LIGHT ? android.R.style.Theme_DeviceDefault_Light : android.R.style.Theme_DeviceDefault));
            rb.setId(1000 + i);
            rb.setText(I18n.t(names[i]));
            rb.setTextColor(LIGHT ? 0xFF1A1A1A : Color.WHITE);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15 * s * FONT);
            rb.setPadding(rb.getPaddingLeft(), 0, (int) (14 * s), 0);
            rg.addView(rb, new android.widget.RadioGroup.LayoutParams(-2, -2));
            if (units[i] == unit) rb.setChecked(true);
        }
        if (rg.getCheckedRadioButtonId() == -1) rg.check(1002);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * s);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(et, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = (int) (10 * s);
        wrap.addView(rg, rlp);
        new TB(this, dlg())
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                try {
                    long v = Long.parseLong(et.getText().toString().replaceAll("[^0-9]", ""));
                    int i = Math.max(0, Math.min(2, rg.getCheckedRadioButtonId() - 1000));
                    char u = units[i];
                    long maxV = u == 'd' ? 3650 : u == 'w' ? 520 : 240;
                    if (v < 1 || v > maxV) throw new NumberFormatException();
                    cb.on(v, u);
                } catch (Throwable e) { toast("Escribe un número válido"); }
            }})
            .setNegativeButton("Cancelar", null)
            .show();
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
        if (!showMap) {
            liveMap = null; navHud = null; navTurnIc = null; navDistTv = null; navStepTv = null;
            navEtaTv = null; navLaneBox = null; navLaneView = null; speedSignTv = null; camBadgeTv = null;
            mapTagBox = null; mapTagTv = null; navSearchBtn = null; aaBtnTv = null; aaSubTv = null;
            cpSubTv = null; mapGauge = null; mapGaugeBox = null; panelWx = null;
            seedLastLocation();
            return;
        }
        FrameLayout card = new FrameLayout(this);
        mapClipView = null;
        if (premium) {
            // Aurora: el mapa va dentro de un marco con su tamaño final; al entrar a «Solo mapa» la tarjeta sube
            // dentro del marco y el borde de abajo se queda quieto (no se asoma por debajo junto a la barra)
            FrameLayout clip = new FrameLayout(this);
            place(board, clip, mapX, mapY, mapW, mapH);
            place(clip, card, 0, 0, mapW, mapH);
            mapClipView = clip;
        } else place(board, card, mapX, mapY, mapW, mapH);
        mapCardView = card;
        // fondo decorativo mientras no hay GPS; encima, el mapa en vivo (Faro: sin dibujo de relleno, el área queda limpia)
        if (!(premium && Prem.faroFam())) card.addView(new MapView(this, s, 20 * s), new FrameLayout.LayoutParams(-1, -1));
        if (!prefs.getBoolean("map_live", true)) { liveMap = null; seedLastLocation(); buildAaCardRest(card); return; }
        liveMap = new LiveMapView(this, s, 20 * s);
        card.addView(liveMap, new FrameLayout.LayoutParams(-1, -1));
        // Aurora: esquinas redondas de verdad sobre el fondo y el mapa un poco translúcido
        if (premium && premBg != null) liveMap.setBackdrop(premBg, premBgHolder, Prem.mapAlpha(LIGHT));
        if (premium && Prem.faroFam()) { liveMap.setAttribution(14 * s, 70 * s); liveMap.setTone(Faro.mapTone(LIGHT)); }
        // Cobalto: el mapa se difumina hasta donde empieza el reproductor (abajo, hacia las tarjetas, en vertical)
        if (premium && Prem.cob() && !mapOnly && premBg != null) liveMap.setFade(vertical ? 2 : 1, (vertical ? 150 : Math.max(260, mapW * 0.5f)) * s);
        liveMap.setVisibility(premium && Prem.faroFam() ? View.VISIBLE : View.INVISIBLE);
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
        final boolean faroT = premium && Prem.faroFam();
        if (faroT) buildFaroMapTop(card);
        else {
        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tag = new LinearLayout(this);
        tag.setGravity(Gravity.CENTER_VERTICAL);
        tag.setBackground(round((CARD & 0x00FFFFFF) | 0xE0000000, 12));
        tag.setPadding((int) (12 * s), (int) (7 * s), (int) (12 * s), (int) (7 * s));
        tag.addView(icon("my_location", 16), new LinearLayout.LayoutParams((int) (16 * s), (int) (16 * s)));
        mapTagTv = tv("Buscando GPS…", 13, TEXT, true);
        mapTagTv.setPadding((int) (8 * s), 0, 0, 0);
        tag.addView(mapTagTv);
        topRow.addView(tag);
        tag.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { fixGps(); }
        });

        LinearLayout goBtn = new LinearLayout(this);
        goBtn.setGravity(Gravity.CENTER_VERTICAL);
        goBtn.setBackground(round((CARD & 0x00FFFFFF) | 0xE0000000, 12));
        goBtn.setPadding((int) (10 * s), (int) (7 * s), (int) (12 * s), (int) (7 * s));
        ImageView goIc = icon("navigation", 16);
        goIc.setColorFilter(RED);
        goBtn.addView(goIc, new LinearLayout.LayoutParams((int) (16 * s), (int) (16 * s)));
        TextView goTv = tv("Ir a…", 13, TEXT, true);
        goTv.setPadding((int) (6 * s), 0, 0, 0);
        goBtn.addView(goTv);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.leftMargin = (int) (8 * s);
        topRow.addView(goBtn, glp);
        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openNavSearch(); }
        });
        if (!prefs.getBoolean("map_live", true) || !"internal".equals(prefs.getString("nav_app", null))) goBtn.setVisibility(View.GONE);
        navSearchBtn = goBtn;
        mapTagBox = topRow;
        placeWrap(card, topRow, 16, 16);
        }

        LinearLayout hud = new LinearLayout(this);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER_VERTICAL);
        hud.setBackground(round((CARD & 0x00FFFFFF) | 0xEB000000, 16));
        hud.setPadding((int) (10 * s), (int) (8 * s), (int) (10 * s), (int) (8 * s));
        FrameLayout turnBox = new FrameLayout(this);
        turnBox.setBackground(round(RED, 12));
        navTurnIc = new Nav.ManeuverView(this);
        turnBox.addView(navTurnIc, new FrameLayout.LayoutParams(-1, -1));
        hud.addView(turnBox, new LinearLayout.LayoutParams((int) (48 * s), (int) (48 * s)));
        LinearLayout hudMid = new LinearLayout(this);
        hudMid.setOrientation(LinearLayout.VERTICAL);
        hudMid.setPadding((int) (12 * s), 0, (int) (8 * s), 0);
        LinearLayout hudTop = new LinearLayout(this);
        hudTop.setGravity(Gravity.CENTER_VERTICAL);
        navDistTv = tv("", 19, TEXT, true);
        hudTop.addView(navDistTv, new LinearLayout.LayoutParams(0, -2, 1));
        navEtaTv = tv("", 12, MUTED, false);
        navEtaTv.setGravity(Gravity.RIGHT);
        navEtaTv.setPadding((int) (8 * s), (int) (2 * s), (int) (4 * s), (int) (2 * s));
        navEtaTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean clock = !prefs.getBoolean("nav_eta_clock", false);
                prefs.edit().putBoolean("nav_eta_clock", clock).apply();
                updateNavHud();
                toast(clock ? "Mostrando hora de llegada" : "Mostrando tiempo restante");
            }
        });
        hudTop.addView(navEtaTv);
        hudMid.addView(hudTop);
        navStepTv = tv("", 13, SOFT, false);
        navStepTv.setSingleLine(false);
        navStepTv.setMaxLines(2);
        navStepTv.setPadding(0, (int) (2 * s), 0, 0);
        hudMid.addView(navStepTv);
        hud.addView(hudMid, new LinearLayout.LayoutParams(0, -2, 1));
        TextView stopBtn = tv("✕", 14, TEXT, true);
        stopBtn.setGravity(Gravity.CENTER);
        stopBtn.setBackground(round(BTN, 17));
        hud.addView(stopBtn, new LinearLayout.LayoutParams((int) (34 * s), (int) (34 * s)));
        stopBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { stopInternalNav(); }
        });
        hud.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openNavMenu(); }
        });
        navHud = hud;
        place(card, hud, 12, 12, mapW - 24, 72);

        // 2.18: qué botones de conexión se ven (Reinaldo, MIGSGx #12). Con uno o ninguno, el clima usa el espacio libre.
        String cm = prefs.getString("conn_buttons", "both");
        // Carbono: Android Auto y CarPlay van en la barra de arriba (el reproductor y el clima flotan abajo del mapa)
        boolean carbT = premium && Prem.carb();
        boolean showAa = !carbT && ("both".equals(cm) || "aa".equals(cm)), showCp = !carbT && ("both".equals(cm) || "cp".equals(cm));
        int nBtn = (showAa ? 1 : 0) + (showCp ? 1 : 0);
        // Aurora con su cabecera: el clima ya tiene su tarjeta arriba (con «Solo mapa» vuelve al panel)
        boolean wx = nBtn < 2 && prefs.getBoolean("weather_on", true) && !(premium && !mapOnly);
        boolean hasPanel = nBtn > 0 || wx;
        final float panelY = hasPanel ? mapH - 96 : mapH, panelW = mapW - 24 - insL - insR;
        final FrameLayout panel = new FrameLayout(this);
        panel.setBackground(round(faroT ? Faro.glass(LIGHT) : (CARD & 0x00FFFFFF) | 0xEB000000, 16));
        place(card, panel, 12 + insL, panelY, panelW, 84);
        if (!hasPanel) panel.setVisibility(View.GONE);

        // botón redondo del navegador: tamaño, lado u oculto (Tinoyevski #18, Andrés). Normal = el de siempre.
        int nbSize = prefs.getInt("nav_btn_size", 0);   // 0 normal, 1 grande, 2 muy grande, 3 oculto
        float nb = nbSize == 1 ? 68 : nbSize == 2 ? 84 : 52;
        String nbSide = prefs.getString("nav_btn_side", "auto");
        boolean nbLeft = "left".equals(nbSide) || ("auto".equals(nbSide) && playerLeft);
        final View navBtn = circleIcon("map", nb, nb / 2, (CARD & 0x00FFFFFF) | 0xEB000000);
        place(card, navBtn, nbLeft ? 12 + insL : mapW - 12 - nb - insR, panelY - 12 - nb, nb, nb);
        if (nbSize == 3 || faroT) navBtn.setVisibility(View.GONE);   // Faro: el buscador de arriba abre la navegación
        // 2.20 (Daniel): mini velocímetro sobre el mapa cuando la tarjeta del vehículo está oculta
        mapGauge = null;
        boolean gaugeTopLeft = false;
        // Aurora con «Solo mapa»: velocímetro mixto abajo a la izquierda (horizontal y vertical)
        final boolean premGauge = premium && mapOnly && carSpeed;
        if (premGauge || (!premium && !showCar && carSpeed && prefs.getBoolean("map_gauge", false))) {
            FrameLayout gb = new FrameLayout(this);
            gb.setBackground(round((CARD & 0x00FFFFFF) | 0xE0000000, 66));
            mapGauge = new Gauge(this, s);
            mapGauge.setColors(RED, TEXT, SOFT);
            mapGauge.setUnit(useMph());
            mapGauge.setSmall(true);
            if (premGauge) mapGauge.setNumber(true);
            gb.addView(mapGauge, new FrameLayout.LayoutParams(-1, -1));
            // tamaño (pequeño/normal/grande) limitado para que en pantallas bajas no tape el mapa
            int gsz = premGauge ? 1 : prefs.getInt("map_gauge_size", 1);
            float gs = gsz == 0 ? 108 : gsz == 2 ? 160 : 132;
            gs = Math.min(gs, Math.min(mapH * 0.45f, mapW * 0.42f));
            gb.setBackground(round((CARD & 0x00FFFFFF) | 0xE0000000, gs / 2));
            if (premGauge) gb.setBackground(round(LIGHT ? 0xD9FFFFFF : 0xD91B1724, gs / 2));
            // esquina: automático = arriba, en el lado contrario al botón del navegador (como antes)
            String corner = premGauge ? "bl" : prefs.getString("map_gauge_corner", "auto");
            if ("auto".equals(corner)) corner = nbLeft ? "tr" : "tl";
            boolean gLeft = corner.endsWith("l"), gBottom = corner.startsWith("b");
            gaugeTopLeft = gLeft && !gBottom;
            float gx = gLeft ? 12 : mapW - 12 - gs, gy = 52;
            if (gBottom) gx = gLeft ? 12 + insL : mapW - 12 - gs - insR;
            if (gBottom) {
                gy = Math.max(52, panelY - 12 - gs);
                // abajo del mismo lado que el botón del navegador: se corre hacia adentro para no taparlo
                if (nbSize != 3 && gLeft == nbLeft) gx = gLeft ? 12 + insL + nb + 12 : mapW - 12 - gs - insR - nb - 12;
                mapGaugeBox = gb;
            } else mapGaugeBox = null;
            place(card, gb, gx, gy, gs, gs);
        } else mapGaugeBox = null;

        FrameLayout laneBox = new FrameLayout(this);
        laneBox.setBackground(round((CARD & 0x00FFFFFF) | 0xEB000000, 10));
        laneBox.setPadding((int) (6 * s), (int) (4 * s), (int) (6 * s), (int) (4 * s));
        navLaneView = new Nav.LaneStripView(this);
        navLaneView.setColors(TEXT, (MUTED & 0x00FFFFFF) | 0x66000000, BTN);
        laneBox.addView(navLaneView, new FrameLayout.LayoutParams(-1, -1));
        laneBox.setVisibility(View.GONE);
        navLaneBox = laneBox;
        float laneW = Math.min(184, Math.max(110, mapW - 140));
        place(card, laneBox, (mapW - laneW) * 0.5f, 92, laneW, 30);

        speedSignTv = tv("", 15, 0xFF181A1F, true);
        speedSignTv.setGravity(Gravity.CENTER);
        speedSignTv.setVisibility(View.GONE);
        float sdx = gaugeTopLeft ? mapW - 58 : 12;
        place(card, speedSignTv, sdx, 92, 46, 46);
        if (faroT) speedSignTv = null;   // Faro: el límite va en el velocímetro

        camBadgeTv = tv("", 11, Color.WHITE, true);
        camBadgeTv.setBackground(round(0xFFD92B2B, 10));
        camBadgeTv.setPadding((int) (8 * s), (int) (3 * s), (int) (8 * s), (int) (3 * s));
        camBadgeTv.setVisibility(View.GONE);
        placeWrap(card, camBadgeTv, gaugeTopLeft ? Math.max(12, mapW - 108) : 12, 144);

        updateNavHud();
        navBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openNavApp(); }
        });
        navBtn.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { pickFor("nav_app", "Navegación"); return true; }
        });

        float slotW = (panelW - 28) / 2, fullW = panelW - 20;
        View aa = null, cp = null;
        if (!carbT) { aaBtnTv = null; aaSubTv = null; cpSubTv = null; }
        float x = 10;
        if (showAa) {
            aa = projButton("navigation", "Android Auto", faroT ? Faro.tile(LIGHT) : RED, !faroT);
            if (faroT) faroProj(aa, true);
            aaBtnTv = (TextView) aa.getTag();
            float w = nBtn == 2 || wx ? slotW : fullW;
            place(panel, aa, x, 10, w, 64);
            x += w + 8;
        }
        if (showCp) {
            cp = projButton("phone_iphone", "CarPlay", faroT ? Faro.tile(LIGHT) : BTN, false);
            if (faroT) faroProj(cp, false);
            cpSubTv = (TextView) cp.getTag();
            float w = nBtn == 2 || wx ? slotW : fullW;
            place(panel, cp, x, 10, w, 64);
            x += w + 8;
        }
        if (!carbT) aaSubTv = aaBtnTv;
        panelWx = null;
        if (wx) buildPanelWeather(panel, x, nBtn == 0 ? fullW : slotW, nBtn == 0);

        // Android Auto / CarPlay plegables: una flechita los esconde para ver todo el mapa (Druids07, idea de Andrés).
        // Tocar o deslizar la flechita hacia arriba los muestra; hacia abajo (o deslizar los botones) los esconde.
        final FrameLayout handle = new FrameLayout(this);
        handle.setBackground(round(faroT ? Faro.glass(LIGHT) : (CARD & 0x00FFFFFF) | 0xEB000000, 11));
        final ImageView chev = icon("chevron_right", 18);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams((int) (18 * s), (int) (18 * s));
        clp.gravity = Gravity.CENTER;
        handle.addView(chev, clp);
        place(card, handle, 12 + insL + (panelW - 64) / 2, panelY - 28, 64, 24);
        final float shift = 96 * s;
        final View[] moving = mapGaugeBox != null ? new View[]{panel, navBtn, handle, mapGaugeBox} : new View[]{panel, navBtn, handle};
        if (!hasPanel) handle.setVisibility(View.GONE);
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
        if (panelWx != null) panelWx.setOnTouchListener(swipeDown);
        if (aa != null) {
            aa.setOnTouchListener(swipeDown);
            aa.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openAndroidAuto(); }
            });
            aa.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { chooseConnection(null); return true; }
            });
        }
        if (cp != null) {
            cp.setOnTouchListener(swipeDown);
            cp.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openCarPlay(); }
            });
            cp.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { pickFor("carplay", "CarPlay"); return true; }
            });
        }
        updateMapTag();
    }

    /**
     * Clima en el panel del mapa (2.18, Reinaldo): ícono, temperatura, estado y máx/mín. A todo el ancho (sin botones
     * de conexión) también muestra las próximas horas. Tocarlo abre el detalle.
     */
    private void buildPanelWeather(FrameLayout panel, float x, float w, boolean wide) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(round(BTN, 14));
        b.setPadding((int) (12 * s), 0, (int) (10 * s), 0);
        panelWxIc = icon("cloud", 30);
        b.addView(panelWxIc, new LinearLayout.LayoutParams((int) (30 * s), (int) (30 * s)));
        panelWxTemp = tv("--°", 22, TEXT, true);
        panelWxTemp.setPadding((int) (8 * s), 0, (int) (8 * s), 0);
        b.addView(panelWxTemp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        panelWxDesc = tv("Clima", 13, TEXT, true);
        panelWxHiLo = tv("", 11, MUTED, false);
        panelWxHiLo.setPadding(0, (int) (3 * s), 0, 0);
        col.addView(panelWxDesc);
        col.addView(panelWxHiLo);
        b.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        panelWxHours = null;
        if (wide) {
            panelWxHours = new LinearLayout(this);
            panelWxHours.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            b.addView(panelWxHours, new LinearLayout.LayoutParams(-2, -1));
        }
        place(panel, b, x, 10, w, 64);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showWeatherDetail(); } });
        panelWx = b;
        showPanelWeather();
    }

    private String deg(double t) { return I18n.integer(Math.round(t)) + "°"; }

    private String hourLabel(int hh) {
        String fmt = prefs.getString("clock", "auto");
        boolean h24 = "24".equals(fmt) || ("auto".equals(fmt) && android.text.format.DateFormat.is24HourFormat(this));
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hh);
        c.set(Calendar.MINUTE, 0);
        return I18n.sdf(h24 ? "HH:mm" : "h a").format(c.getTime());
    }

    private void showPanelWeather() {
        renderFaroWx();
        if (panelWx == null) return;
        Weather.Now n = weatherNow;
        if (n == null) {
            panelWxIc.setImageBitmap(asset("ic/cloud.png"));
            panelWxTemp.setText("--°");
            panelWxDesc.setText(I18n.t("Clima"));
            panelWxHiLo.setText(I18n.t(Double.isNaN(wLat) ? "Esperando la ubicación…" : "Cargando…"));
            if (panelWxHours != null) panelWxHours.removeAllViews();
            return;
        }
        panelWxIc.setImageBitmap(asset("ic/" + Weather.icon(n.code, n.day) + ".png"));
        panelWxTemp.setText(deg(n.temp));
        panelWxDesc.setText(I18n.t(Weather.describe(n.code)));
        Weather.Day d = n.today();
        String hl = d != null ? I18n.t("Máx " + deg(d.hi) + " · Mín " + deg(d.lo)) : (n.city != null ? n.city : "");
        panelWxHiLo.setText(hl);
        if (panelWxHours != null) {
            panelWxHours.removeAllViews();
            for (int i = 1; i < n.hours.size() && i <= 3; i++) {
                Weather.Hour hr = n.hours.get(i);
                LinearLayout c = new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setGravity(Gravity.CENTER_HORIZONTAL);
                c.addView(tv(hourLabel(hr.hour), 10, MUTED, false));
                ImageView ic = icon(Weather.icon(hr.code, hr.day), 16);
                c.addView(ic, new LinearLayout.LayoutParams((int) (16 * s), (int) (16 * s)));
                c.addView(tv(deg(hr.temp), 12, TEXT, true));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (52 * s), -2);
                panelWxHours.addView(c, lp);
            }
        }
    }

    /** Probabilidad de lluvia con el ícono del paraguas (vacío si no va a llover). */
    private View rainView(int pct, float size, int gravity) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(gravity);
        if (pct > 0) {
            ImageView u = icon("umbrella", size);
            u.setAlpha(0.6f);
            l.addView(u, new LinearLayout.LayoutParams((int) (size * s), (int) (size * s)));
            TextView t = tv(I18n.integer(pct) + "%", size, MUTED, false);
            t.setPadding((int) (3 * s), 0, 0, 0);
            l.addView(t);
        } else l.addView(tv(" ", size, MUTED, false));
        return l;
    }

    /** Detalle del clima: ahora, próximas horas y los próximos días (tocando el clima de arriba o el del panel). */
    private void showWeatherDetail() {
        final Weather.Now n = weatherNow;
        if (!prefs.getBoolean("weather_on", true)) { toast("El clima está apagado en Ajustes → Mapa y clima"); return; }
        if (n == null) { toast(Double.isNaN(wLat) ? "Esperando la ubicación para el clima…" : "Cargando el clima…"); refreshWeatherNow(); return; }
        int pad = (int) (20 * s);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, pad / 2);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(icon(Weather.icon(n.code, n.day), 48), new LinearLayout.LayoutParams((int) (48 * s), (int) (48 * s)));
        TextView big = tv(deg(n.temp) + n.unit, 40, TEXT, true);
        big.setPadding((int) (14 * s), 0, (int) (16 * s), 0);
        head.addView(big);
        LinearLayout hc = new LinearLayout(this);
        hc.setOrientation(LinearLayout.VERTICAL);
        hc.addView(tv(Weather.describe(n.code), 17, TEXT, true));
        StringBuilder extra = new StringBuilder();
        if (!Double.isNaN(n.feels)) extra.append(I18n.t("Sensación " + deg(n.feels)));
        if (n.humidity >= 0) extra.append(extra.length() > 0 ? " · " : "").append(I18n.t("Humedad " + I18n.integer(n.humidity) + "%"));
        if (!Double.isNaN(n.wind)) extra.append(extra.length() > 0 ? " · " : "").append(I18n.t("Viento " + I18n.integer(Math.round(n.wind)) + " " + n.windUnit));
        TextView ex2 = tv(extra.toString(), 13, MUTED, false);
        ex2.setSingleLine(false);
        ex2.setPadding(0, (int) (4 * s), 0, 0);
        hc.addView(ex2);
        head.addView(hc, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(head);

        if (!n.hours.isEmpty()) {
            TextView ht = tv("PRÓXIMAS HORAS", 11, MUTED, true);
            ht.setPadding(0, (int) (16 * s), 0, (int) (6 * s));
            box.addView(ht);
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout hrow = new LinearLayout(this);
            for (int i = 0; i < n.hours.size(); i++) {
                Weather.Hour hr = n.hours.get(i);
                LinearLayout c = new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setGravity(Gravity.CENTER_HORIZONTAL);
                c.addView(tv(i == 0 ? I18n.t("Ahora") : hourLabel(hr.hour), 11, MUTED, false));
                c.addView(icon(Weather.icon(hr.code, hr.day), 22), new LinearLayout.LayoutParams((int) (22 * s), (int) (22 * s)));
                c.addView(tv(deg(hr.temp), 14, TEXT, true));
                c.addView(rainView(hr.rain, 10, Gravity.CENTER_HORIZONTAL));
                hrow.addView(c, new LinearLayout.LayoutParams((int) (64 * s), -2));
            }
            hs.addView(hrow);
            box.addView(hs);
        }
        if (n.days.size() > 1) {
            TextView dt = tv("PRÓXIMOS DÍAS", 11, MUTED, true);
            dt.setPadding(0, (int) (14 * s), 0, (int) (4 * s));
            box.addView(dt);
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            for (int i = 0; i < n.days.size(); i++) {
                Weather.Day d = n.days.get(i);
                String name;
                if (i == 0) name = I18n.t("Hoy");
                else {
                    try { name = I18n.sdf("EEEE").format(in.parse(d.date)); } catch (Throwable e) { name = d.date; }
                    if (name.length() > 0) name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                }
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(0, (int) (4 * s), 0, (int) (4 * s));
                r.addView(tv(name, 14, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
                r.addView(icon(Weather.icon(d.code, true), 20), new LinearLayout.LayoutParams((int) (20 * s), (int) (20 * s)));
                r.addView(rainView(d.rain, 12, Gravity.RIGHT | Gravity.CENTER_VERTICAL), new LinearLayout.LayoutParams((int) (64 * s), -2));
                TextView t = tv(deg(d.hi) + " / " + deg(d.lo), 14, TEXT, true);
                t.setGravity(Gravity.RIGHT);
                r.addView(t, new LinearLayout.LayoutParams((int) (100 * s), -2));
                box.addView(r);
            }
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        new TB(this, dlg())
            .setTitle(n.city != null ? n.city : I18n.t("Clima"))
            .setView(sv)
            .setPositiveButton("Cerrar", null)
            .show();
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
                if (validLoc(l) && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Throwable ignored) { }
        }
        if (best != null) { feedMap(best); return; }
        // #37: la pantalla no recuerda ninguna ubicación buena (o solo el 0,0): se usa la última que guardó StradaOS.
        // Solo ubica el mapa; no pasa por la navegación para no recalcular con una posición vieja.
        Location sv = savedLoc();
        if (sv == null) return;
        if (Double.isNaN(wLat) || Double.isNaN(wLon)) { wLat = sv.getLatitude(); wLon = sv.getLongitude(); }
        if (liveMap != null && !liveMap.hasFix()) {
            liveMap.setPosition(sv.getLatitude(), sv.getLongitude(), Float.NaN, 0);
            liveMap.setVisibility(View.VISIBLE);
            updateMapTag();
        }
    }

    /**
     * #37 (jothaxc): algunas pantallas mandan 0,0 («Null Island», en medio del mar) cuando no hay señal, en túneles
     * o al arrancar. Eso no es una ubicación: se ignora en el mapa, la navegación, el clima y el amanecer.
     */
    static boolean validLoc(Location l) {
        if (l == null) return false;
        double la = l.getLatitude(), lo = l.getLongitude();
        if (Double.isNaN(la) || Double.isNaN(lo) || Math.abs(la) > 90 || Math.abs(lo) > 180) return false;
        return Math.abs(la) > 0.0001 || Math.abs(lo) > 0.0001;
    }

    private long locSavedAt;

    /** Guarda la última ubicación buena del GPS (cada 2 min como mucho) para cuando la pantalla arranque sin señal. */
    private void saveLoc(Location l) {
        long now = SystemClock.elapsedRealtime();
        if (locSavedAt != 0 && now - locSavedAt < 120000) return;
        locSavedAt = now;
        prefs.edit().putFloat("last_lat", (float) l.getLatitude()).putFloat("last_lon", (float) l.getLongitude()).apply();
    }

    private Location savedLoc() {
        if (prefs == null || !prefs.contains("last_lat")) return null;
        Location l = new Location("saved");
        l.setLatitude(prefs.getFloat("last_lat", 0));
        l.setLongitude(prefs.getFloat("last_lon", 0));
        return validLoc(l) ? l : null;
    }

    private void feedMap(Location l) {
        if (l != null && !validLoc(l)) return;
        if (l != null) {
            wLat = l.getLatitude(); wLon = l.getLongitude();
            if (Nav.active != null) {
                Nav.onLocation(this, l, useMph(), new Runnable() {
                    public void run() {
                        if (Nav.active == null) {
                            LiveMapView.headingUp = prefs.getBoolean("map_heading", false);
                            toast("Llegaste a tu destino");
                        }
                        updateNavHud();
                        if (liveMap != null) liveMap.invalidate();
                    }
                });
                updateNavHud();
            }
        }
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
        return I18n.lines(head + "\n\n" + (liveMap != null ? liveMap.diag() : "Mapa en vivo apagado"));
    }

    private void showMapDiag() {
        new TB(this, dlg()).diag().setTitle("Diagnóstico del mapa").setMessage(mapDiag()).setPositiveButton("OK", null).show();
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
        updateFaroPill();
    }

    private void updateNavHud() {
        Nav.Route r = Nav.active;
        boolean mph = useMph();
        Nav.Step st = Nav.currentStep();
        if (topNavPill != null) {
            boolean showPill = r != null && (!showMap || !prefs.getBoolean("map_live", true));
            topNavPill.setVisibility(showPill ? View.VISIBLE : View.GONE);
            if (showPill) {
                if (topNavIc != null) topNavIc.setIcon(st != null ? st.icon : 0);
                if (topNavDistTv != null) {
                    String td = Nav.rerouting ? I18n.t("Recalculando…")
                        : Nav.offlineOffRoute ? Nav.fmtStepDist(Nav.offRouteM, mph)
                        : Nav.fmtStepDist(Nav.distToStepM, mph);
                    topNavDistTv.setText(td);
                }
            }
        }
        if (navHud == null || mapTagBox == null) return;
        if (r == null) {
            navHud.setVisibility(View.GONE);
            if (navLaneBox != null) navLaneBox.setVisibility(View.GONE);
            if (speedSignTv != null) speedSignTv.setVisibility(View.GONE);
            if (camBadgeTv != null) camBadgeTv.setVisibility(View.GONE);
            mapTagBox.setVisibility(View.VISIBLE);
            return;
        }
        mapTagBox.setVisibility(View.GONE);
        navHud.setVisibility(View.VISIBLE);
        if (navTurnIc != null) navTurnIc.setIcon(st != null ? st.icon : 0);
        if (navEtaTv != null) {
            String eta = Nav.fmtEta(this, Nav.remDurS, Nav.remDistM, mph);
            if ("map".equals(r.source) && r.offline) eta = eta + " · " + I18n.t("aproximada");
            int dMin = (int) Math.round(Nav.trafficDelayS / 60.0);
            if (dMin >= 1) {
                eta = eta + " (+" + dMin + " min)";
                navEtaTv.setTextColor(dMin >= 8 ? RED : 0xFFF5A623);
            } else {
                navEtaTv.setTextColor(MUTED);
            }
            navEtaTv.setText(eta);
        }
        if (navLaneBox != null && navLaneView != null) {
            // guía de carriles: para una próxima versión (opcional, sin ajuste todavía)
            if (prefs.getBoolean("nav_lanes", false) && st != null && st.lanes != null && st.lanes.length > 0 && Nav.distToStepM <= 1200) {
                navLaneView.setLanes(st.lanes);
                navLaneBox.setVisibility(View.VISIBLE);
            } else {
                navLaneBox.setVisibility(View.GONE);
            }
        }
        if (speedSignTv != null) {
            if (Nav.curSpeedLimitKmh > 0 && prefs.getBoolean("nav_speed_alert", false)) {
                int val = mph ? Math.max(5, (int) (Math.round((Nav.curSpeedLimitKmh / 1.60934) / 5.0) * 5)) : Nav.curSpeedLimitKmh;
                GradientDrawable sg = new GradientDrawable();
                sg.setShape(GradientDrawable.OVAL);
                if (Nav.speedingAlert) {
                    sg.setColor(0xFFD92B2B);
                    speedSignTv.setTextColor(Color.WHITE);
                } else {
                    sg.setColor(0xFFFDFDFE);
                    sg.setStroke(Math.max(2, (int) (4 * s)), 0xFFD92B2B);
                    speedSignTv.setTextColor(0xFF181A1F);
                }
                speedSignTv.setBackground(sg);
                speedSignTv.setText(String.valueOf(val));
                speedSignTv.setVisibility(View.VISIBLE);
            } else {
                speedSignTv.setVisibility(View.GONE);
            }
        }
        if (camBadgeTv != null) {
            if (Nav.nearbyCamera != null && Nav.nearbyCameraDistM >= 0 && prefs.getBoolean("nav_speed_cams", false)) {
                camBadgeTv.setText(I18n.t("Radar") + " · " + Nav.fmtStepDist(Nav.nearbyCameraDistM, mph));
                camBadgeTv.setVisibility(View.VISIBLE);
            } else {
                camBadgeTv.setVisibility(View.GONE);
            }
        }
        String stepTxt = st != null && st.instruction != null ? I18n.t(st.instruction) : (r.destName != null ? r.destName : "");
        if (Nav.rerouting) {
            if (navDistTv != null) navDistTv.setText(I18n.t("Recalculando…"));
            if (navStepTv != null) navStepTv.setText(stepTxt);
        } else if (Nav.offlineOffRoute) {
            if (navDistTv != null) navDistTv.setText(Nav.fmtStepDist(Nav.offRouteM, mph));
            if (navStepTv != null) navStepTv.setText(I18n.t("Sin conexión · vuelve a la ruta"));
        } else {
            if (navDistTv != null) navDistTv.setText(Nav.fmtStepDist(Nav.distToStepM, mph));
            if (navStepTv != null) navStepTv.setText(stepTxt);
        }
    }

    private void buildAudioCard() {
        final FrameLayout card = card(audX, rowY, audW, cardH, CARD);
        // contenido centrado: la tarjeta es más ancha sin la del vehículo y más alta con la barra compacta
        final float dx = (audW - 248) / 2, dy = (cardH - 380) / 2;
        final TextView audioLbl = tv("AUDIO", 11, MUTED, true);
        placeWrap(card, audioLbl, 18, 14);
        sourceTv = tv("", 11, RED, true);
        sourceTv.setGravity(Gravity.RIGHT);
        sourceTv.setSingleLine(true);
        sourceTv.setPadding(0, 0, (int) (4 * s), 0);   // la última letra en negrita se recortaba en Android 11
        place(card, sourceTv, 70, 14, audW - 88, 18);   // ancho de sobra: en Android 11 «BLUETOOTH» salía cortado
        playerArt = new PlayerArt(this, s, asset("ic/music_note.png"));
        // Tarjeta alta (barra lateral): diseño vertical que llena el alto en vez de dejar espacio vacío (Andrés, Picanto)
        tallPlayer = cardH - 380 >= 60 || shortVertical;   // vertical bajo: el mismo diseño, calculado con el alto real
        final View prev, play, next;
        if (vertical && cardH < 240) {
            // Franja ancha (vertical sin la tarjeta del vehículo): carátula a la izquierda, canción al centro y
            // botones grandes a la derecha
            tallPlayer = false;
            float A = cardH - 28, tx = 14 + A + 18, bs = 60, ps = 78, gap = 10;
            float bx = audW - 18 - (bs * 2 + ps + gap * 2), tw = bx - tx - 12;
            place(card, playerArt, 14, 14, A, A);
            card.removeView(audioLbl);
            card.removeView(sourceTv);
            sourceTv.setGravity(Gravity.LEFT);
            place(card, sourceTv, tx, 22, tw, 18);
            trackTv = tv("Nada sonando", 20, TEXT, true);
            place(card, trackTv, tx, 46, tw, 30);
            artistTv = tv("Radio, Bluetooth o USB", 14, MUTED, false);
            place(card, artistTv, tx, 80, tw, 22);
            timePosTv = tv("", 13, MUTED, false);
            place(card, timePosTv, tx, 108, tw, 20);
            prev = circleIcon("skip_previous", bs, bs / 2, BTN);
            play = circleIcon("play_arrow", ps, ps * 0.47f, RED);
            next = circleIcon("skip_next", bs, bs / 2, BTN);
            place(card, prev, bx, (cardH - bs) / 2, bs, bs);
            place(card, play, bx + bs + gap, (cardH - ps) / 2, ps, ps);
            place(card, next, bx + bs + gap + ps + gap, (cardH - bs) / 2, bs, bs);
        } else if (tallPlayer) {
            boolean big = audW >= 290, low = cardH < 380;
            float bs = low ? 52 : big ? 66 : 60, ps = low ? 66 : big ? 86 : 76, gap = big ? 12 : 10;
            float yb = cardH - (low ? 16 : 22) - ps;    // botones abajo
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
        // carátula un poco más chica y textos más arriba y más altos: con fuentes de sistema altas (Android 13+)
        // la última línea quedaba cortada contra el botón ▶ (issue #18)
        place(card, playerArt, 38 + dx, 32 + dy, 172, 172);
        trackTv = tv("Nada sonando", 17, TEXT, true);
        trackTv.setGravity(Gravity.CENTER);
        place(card, trackTv, 14, 210 + dy, audW - 28, 28);
        artistTv = tv("Radio, Bluetooth o USB", 13, MUTED, false);
        artistTv.setGravity(Gravity.CENTER);
        place(card, artistTv, 14, 240 + dy, audW - 28, 22);
        timePosTv = tv("", 12, MUTED, false);
        timePosTv.setGravity(Gravity.CENTER);
        place(card, timePosTv, 14, 264 + dy, audW - 28, 20);
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
        // Oculta (2.18): se arma igual (la música sigue actualizándose sin fallar) pero no se muestra
        if (!showPlayer) board.removeView(card);
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
        {"nav_home", "Ir a Casa", "location_on"}, {"nav_work", "Ir a Trabajo", "directions_car"},
        {"obd", "Vehículo (OBD-II)", "car_repair"},
        {"settings", "Ajustes", "settings"}, {"apps", "Apps", "apps"},
    };

    /** Función de fábrica del botón i según la cantidad de botones: «Apps» siempre al final (2.19). Con 7, lo de siempre. */
    private String dockDefault(int i) {
        if (i == dockCount - 1) return "apps";
        if (i < 6) return DOCK_DEFAULT[i];
        String[] extra = {"music", "nav", "aa"};
        return extra[Math.min(extra.length - 1, i - 6)];
    }

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
        return t != null ? t : "fn:" + dockDefault(i);
    }

    private String dockName(int i) {
        String custom = prefs.getString("dock_" + i + "_name", "").trim();
        if (custom.length() > 0) return custom;
        String t = dockTarget(i);
        if (t.startsWith("app:")) return labelOf(t.substring(4));
        String id = t.substring(3);
        return "web".equals(id) ? "Navegador" : dockFn(id)[1];
    }

    /** Pack de íconos elegido (2.19); null = íconos de siempre. */
    private IconPack iconPack() {
        String p = prefs.getString("icon_pack", "");
        if (p.length() == 0) return null;
        IconPack ip = IconPack.get(this, p);
        if (ip == null && !installed(p)) prefs.edit().remove("icon_pack").apply();
        return ip;
    }

    /** Ícono de una app de la lista: el del pack si lo trae, si no el de siempre. */
    private Drawable iconOf(ResolveInfo r) {
        try {
            IconPack ip = iconPack();
            if (ip != null) {
                Drawable d = ip.icon(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
                if (d != null) return d;
            }
        } catch (Throwable ignored) { }
        try {
            return r.loadIcon(getPackageManager());
        } catch (OutOfMemoryError e) {
            // 2.31: pantallas con poca memoria y muchas apps (la regresión lo vio en Android 4.1): un ícono genérico
            // en vez de cerrar StradaOS
            System.gc();
            try { return getResources().getDrawable(android.R.drawable.sym_def_app_icon); } catch (Throwable t) { return null; }
        }
    }

    /**
     * Con «Íconos de las apps» (2.19, Reinaldo, Lucas Cacciola): la app que abre cada función (radio, Bluetooth,
     * cámara, archivos, navegación, música) con su propio ícono. null = se queda el ícono redondo de StradaOS.
     */
    private Drawable fnAppIcon(String id) {
        try {
            String saved = null;
            int slot = -1;
            if ("radio".equals(id)) slot = 0;
            else if ("bt".equals(id)) slot = 1;
            else if ("cam".equals(id)) slot = 2;
            else if ("files".equals(id)) slot = 3;
            else if ("nav".equals(id)) { saved = prefs.getString("nav_app", null); if ("internal".equals(saved)) saved = null; }
            else if ("music".equals(id)) saved = prefs.getString("music_app", null);
            else return null;
            if (slot >= 0) {
                saved = prefs.getString(SLOTS[slot][0], null);
                if (saved == null) {
                    ResolveInfo r = find(SLOTS[slot][2], SLOTS[slot][3]);
                    return r != null ? iconOf(r) : null;
                }
            }
            return saved != null ? appIcon(saved) : null;
        } catch (Throwable e) { return null; }
    }

    private Drawable appIcon(String component) {
        try {
            IconPack ip = iconPack();
            if (ip != null) {
                Drawable d = ip.icon(ComponentName.unflattenFromString(component));
                if (d != null) return d;
            }
        } catch (Throwable ignored) { }
        try {
            return getPackageManager().getActivityIcon(ComponentName.unflattenFromString(component));
        } catch (Throwable e) {
            try { return getPackageManager().getApplicationIcon(ComponentName.unflattenFromString(component).getPackageName()); }
            catch (Throwable e2) { return null; }
        }
    }

    private void buildDock() {
        // tamaños según la barra: normal (como siempre), compacta (menos alta) o lateral (botones al costado)
        // 2.19: cantidad de botones (4 a 10) y tamaño de los íconos (Tinoyevski #18, MIGSGx #12). 7 y normal = lo de siempre.
        final int n = dockCount;
        int isz = prefs.getInt("dock_icon_size", 1);
        float ik = isz == 0 ? 0.82f : isz == 2 ? 1.2f : 1f;
        final float railX = railLeft ? 16 : 1016 + ex, step = (boardH - 24) / (float) n;
        float cs0 = (sideDock ? (dockLabels ? 40 : 48) : compactDock ? 50 : 64) * ik;
        if (sideDock) cs0 = Math.min(cs0, step - (dockLabels ? 18 : 6));          // que quepan en la barra lateral
        final float lblSize = sideDock ? 10 : compactDock ? 12 : 13, lblPad = sideDock ? 2 : compactDock ? 2 : 8;
        final boolean appStyle = "apps".equals(prefs.getString("dock_style", "circle"));
        // botones a lo ancho: con 7 se usan las posiciones de siempre; con otra cantidad se reparten parejo
        final float slotW = n == 7 && !vertical ? 138 + ex / 6 : (948 + ex) / (float) n, itemW = Math.min(120, slotW - 4);
        final float x0 = n == 7 && !vertical ? 38 : 38 + (slotW - itemW) / 2;
        if (vertical) cs0 = Math.min(cs0, slotW - 10);                            // con muchos botones, que no se monten
        final float cs = cs0;          // círculo
        if (sideDock) {
            View rail = new View(this);
            rail.setBackground(round(DOCK, 20));
            place(board, rail, railX, 12, RAIL, boardH - 24);
        }
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String t = dockTarget(i);
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            Drawable appIc = t.startsWith("app:") ? appIcon(t.substring(4)) : appStyle ? fnAppIcon(t.substring(3)) : null;
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
                String id = t.startsWith("fn:") ? t.substring(3) : dockDefault(i);
                b.addView(circleIcon(dockFn(id)[2], cs, cs * 0.44f, CIRCLE), new LinearLayout.LayoutParams((int) (cs * s), (int) (cs * s)));
            }
            if (dockLabels) {
                TextView l = tv(dockName(i), lblSize, SOFT, false);
                l.setGravity(Gravity.CENTER_HORIZONTAL);
                l.setSingleLine(true);
                l.setEllipsize(android.text.TextUtils.TruncateAt.END);
                l.setPadding(0, (int) (lblPad * s), 0, 0);
                b.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                b.setGravity(Gravity.CENTER);   // sin nombres: el círculo queda centrado en la franja
            }
            if (sideDock) { b.setGravity(Gravity.CENTER); place(board, b, railX, 12 + step * i, RAIL, step); }
            else if (compactDock) { b.setGravity(Gravity.CENTER); place(board, b, x0 + slotW * i, dockTop + 4, itemW, boardH - dockTop - 8); }
            else place(board, b, x0 + slotW * i, dockTop + (dockLabels ? 18 : 6), itemW, dockLabels ? 110 : 132);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { runDock(idx); }
            });
            b.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { dockMenu(idx, null); return true; }
            });
        }
    }

    // ───────────────────────── tema premium «Aurora» (2.24, Bloque 14, fase 2) ─────────────────────────
    // Otra distribución del inicio: barra de arriba, cabecera con la hora grande y tres tarjetas translúcidas (música,
    // vehículo y clima), el mapa a lo ancho y la barra de apps tipo píldora con íconos sin círculos. «Solo mapa» oculta
    // la cabecera y el mapa crece hasta arriba. Usa los mismos campos que el inicio de siempre (timeTv, trackTv,
    // speedTv, oil…, panelWx…), así que el reloj, la música, el GPS, los mantenimientos y el clima se actualizan con el
    // código de siempre. Sin controles de aire acondicionado: StradaOS no los maneja.

    private boolean premium, mapOnly;
    private float premHeadY = 60, premHeadH, premPillH;
    /** Pantallas muy anchas y bajas (1280×480, 1920×720): hora y tarjetas en una columna a la izquierda y el mapa
     *  a la derecha con todo el alto; con la cabecera arriba el mapa quedaba como una franja. */
    private boolean premWide;
    private float premColW;
    private View mapCardView;
    private FrameLayout mapClipView;
    private View premTopRow;
    private Bitmap premBg;
    private View premBgHolder;
    /** Hora y tarjetas de la cabecera: se animan al entrar y salir de «Solo mapa». */
    private final List<View> premHeadViews = new ArrayList<View>();

    /**
     * «Solo mapa» con transición: se toma una foto del inicio actual, se arma el nuevo debajo y la foto se desvanece
     * subiendo, mientras la cabecera baja y aparece (al volver) o el mapa sube a su sitio (al agrandarlo). Todo con
     * animaciones de vista (API 16) y sin volver a medir el mapa en cada cuadro: fluido también en el Picanto.
     */
    /** Mueve la tarjeta del mapa hasta su sitio redibujando el mapa en cada cuadro (el fondo de las esquinas y la
     *  transparencia se recalculan con la posición: así no se ve corrido mientras se mueve). */
    private void slideMap(final View v, float from, android.animation.TimeInterpolator in, long delay, long dur, final Runnable end) {
        v.setTranslationY(from);
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(from, 0f);
        va.setDuration(dur);
        va.setStartDelay(delay);
        va.setInterpolator(in);
        if (end != null) va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { end.run(); }
        });
        final boolean slow = slowScreen();
        if (slow) {
            // pantallas modestas (Android 4.x, como el Picanto): el mapa se dibuja una vez en una capa y solo se mueve
            // esa capa; al terminar se redibuja en su sitio (esquinas y transparencia alineadas otra vez)
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    v.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (liveMap != null) liveMap.invalidate();
                }
            });
        }
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                v.setTranslationY((Float) a.getAnimatedValue());
                if (!slow && liveMap != null) liveMap.invalidate();
            }
        });
        va.start();
    }

    /** Android 4.x: pantallas de carro de gama baja. Animaciones un poco más largas y con capas. */
    private static boolean slowScreen() { return SDK < 21; }

    /** Solo para las capturas promocionales (gancho «foto-vacia:1»). */
    private static boolean emptyPhotoSlot;

    /** Duración de una animación de Aurora: un 40 % más larga en pantallas lentas para que se aprecie. */
    private static long dur(long ms) { return slowScreen() ? ms * 7 / 5 : ms; }

    /**
     * Arranca las animaciones después del primer cuadro del inicio nuevo (el más pesado: armar las vistas y pedir el
     * mapa). Si arrancan antes, en pantallas lentas ese cuadro se come el comienzo y parecen un salto.
     */
    private void afterFirstFrame(final Runnable r) {
        final View b = board;
        if (b == null) { r.run(); return; }
        b.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                b.getViewTreeObserver().removeOnPreDrawListener(this);
                b.post(r);
                return true;
            }
        });
    }

    /**
     * Entrar a «Solo mapa» (pedido de Andrés: sin desvanecer todo): la hora y las tarjetas se van hacia arriba una tras
     * otra, en el orden contrario al de volver, y el mapa sube hasta arriba. Las tarjetas que se van son fotos de las
     * de antes (el inicio nuevo ya está armado debajo); el mapa sube dentro de su marco con el borde de abajo quieto.
     */
    private void enterMapOnly() {
        final List<View> heads = new ArrayList<View>(premHeadViews);
        final List<Bitmap> shots = new ArrayList<Bitmap>();
        final List<int[]> boxes = new ArrayList<int[]>();
        for (View v : heads) {
            try {
                if (v.getWidth() <= 0 || v.getHeight() <= 0) continue;
                Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
                v.draw(new android.graphics.Canvas(b));
                shots.add(b);
                boxes.add(new int[]{v.getLeft(), v.getTop(), v.getWidth(), v.getHeight()});
            } catch (Throwable ignored) { }
        }
        final boolean cobT = Prem.cob();
        final float rise = cobT ? (cobTempH > 0 ? (cobTempH + 8) * s : 0) : premWide ? 0 : (premHeadH + 12) * s;
        final boolean vert = vertical;
        mapOnly = true;
        prefs.edit().putBoolean("prem_map_only", true).apply();
        rebuild();
        final android.view.animation.DecelerateInterpolator dec = new android.view.animation.DecelerateInterpolator(1.6f);
        final android.view.animation.AccelerateInterpolator acc = new android.view.animation.AccelerateInterpolator(1.2f);
        final int n = shots.size();
        final List<ImageView> leaving = new ArrayList<ImageView>();
        final List<Integer> dirs = new ArrayList<Integer>();
        for (int i = n - 1; i >= 0; i--) {
            final Bitmap b = shots.get(i);
            int[] bx = boxes.get(i);
            final ImageView iv = new ImageView(this);
            iv.setImageBitmap(b);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bx[2], bx[3]);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.leftMargin = bx[0];
            lp.topMargin = bx[1];
            // Aurora: debajo de todo (el mapa que sube las tapa). Cobalto: encima, y se van hacia los lados dejando
            // ver el mapa que ya ocupa todo
            if (cobT) board.addView(iv, lp); else board.addView(iv, 0, lp);
            iv.setTag(b);
            dirs.add(i == 0 || !cobT ? 0 : vert ? 2 : 1);
            leaving.add(iv);
        }
        // estado inicial ya puesto (el primer cuadro se ve como antes); las animaciones arrancan después de él
        if (mapCardView != null && rise > 0) mapCardView.setTranslationY(rise);
        if (premTopRow != null && !cobT) premTopRow.setAlpha(0f);   // Cobalto: la hora ya estaba arriba
        final View mapV = mapCardView, topV = cobT ? null : premTopRow;
        afterFirstFrame(new Runnable() { public void run() {
            for (int j = 0; j < leaving.size(); j++) {
                final ImageView iv = leaving.get(j);
                final Bitmap b = (Bitmap) iv.getTag();
                int dir = dirs.get(j);
                android.view.ViewPropertyAnimator an = iv.animate().alpha(0f);
                if (dir == 1) an.translationX(48 * s); else if (dir == 2) an.translationY(48 * s); else an.translationY(-36 * s);
                an.setStartDelay(dur(45L * j)).setDuration(dur(300)).setInterpolator(acc)
                    .withLayer().withEndAction(new Runnable() { public void run() {
                        if (iv.getParent() instanceof ViewGroup) ((ViewGroup) iv.getParent()).removeView(iv);
                        iv.setImageDrawable(null);
                        b.recycle();
                    }});
            }
            // el mapa sube por encima de las tarjetas (que quedaron debajo) dentro de su marco
            if (mapV != null && rise > 0 && mapV == mapCardView) slideMap(mapV, rise, dec, dur(40), dur(420), null);
            // la hora pequeña de arriba aparece cuando la grande ya se fue (sin dos horas a la vez)
            if (topV != null) topV.animate().alpha(1f).setStartDelay(dur(cobT ? 0 : 200)).setDuration(dur(cobT ? 1 : 220));
        }});
    }

    private void toggleMapOnly() {
        if (!Prem.mapOnlyOk()) return;   // Cobalto y Faro no tienen «Solo mapa»
        if (!mapOnly) { enterMapOnly(); return; }
        Bitmap snap = null;
        FrameLayout.LayoutParams olp = null;
        // Volver: solo se funde la barra de arriba (hora chica → saludo). Antes era una foto del inicio entero que
        // bajaba al desvanecerse, y por eso la barra de apps salía duplicada un instante. La barra no se toca.
        try {
            int sh = Math.min(board != null ? board.getHeight() : 0, (int) ((premHeadY - 4) * s));
            if (board != null && board.getWidth() > 0 && sh > 0) {
                snap = Bitmap.createBitmap(board.getWidth(), sh, Bitmap.Config.ARGB_8888);
                board.draw(new android.graphics.Canvas(snap));
                olp = new FrameLayout.LayoutParams(board.getWidth(), sh);
                olp.gravity = Gravity.TOP | Gravity.LEFT;
            }
        } catch (Throwable e) { if (snap != null) snap.recycle(); snap = null; }
        mapOnly = !mapOnly;
        prefs.edit().putBoolean("prem_map_only", mapOnly).apply();
        rebuild();
        final Bitmap shot = olp != null ? snap : null;
        final ImageView ov = new ImageView(this);
        if (shot != null) {
            ov.setImageBitmap(shot);
            ov.setScaleType(ImageView.ScaleType.MATRIX);
            board.addView(ov, olp);
        }
        final android.view.animation.DecelerateInterpolator dec = new android.view.animation.DecelerateInterpolator(1.6f);
        // estado inicial (lo que se ve en el primer cuadro del inicio nuevo)
        final List<View> heads = new ArrayList<View>(premHeadViews);
        final boolean cobT = Prem.cob();
        for (int i = 0; i < heads.size(); i++) {
            View v = heads.get(i);
            v.setAlpha(0f);
            // Cobalto: la temperatura baja desde arriba y las tarjetas entran desde el lado (desde abajo en vertical)
            if (cobT && i > 0) { if (vertical) v.setTranslationY(40 * s); else v.setTranslationX(40 * s); }
            else v.setTranslationY(-22 * s);
        }
        // al volver baja el marco del mapa completo, con todo lo de adentro: el panel de Android Auto / CarPlay
        // se mueve junto con el mapa si está abierto y sigue escondido si está plegado
        final View mv = mapClipView != null ? mapClipView : mapCardView;
        final float from = Prem.cob() ? -(cobTempH > 0 ? cobTempH + 8 : 0) * s : -premHeadH * 0.45f * s;
        if (mv != null) mv.setTranslationY(from);
        afterFirstFrame(new Runnable() { public void run() {
            if (shot != null) ov.animate().alpha(0f).setDuration(dur(240)).withEndAction(new Runnable() { public void run() {
                if (ov.getParent() instanceof ViewGroup) ((ViewGroup) ov.getParent()).removeView(ov);
                ov.setImageDrawable(null);
                shot.recycle();
            }});
            // la cabecera baja desde arriba, una tarjeta tras otra, y el mapa baja a su sitio
            int k = 0;
            for (View v : heads)
                v.animate().alpha(1f).translationX(0).translationY(0).setStartDelay(dur(60 + 45 * k++)).setDuration(dur(320)).setInterpolator(dec).withLayer();
            if (mv != null && from != 0 && (mv == mapClipView || mv == mapCardView)) slideMap(mv, from, dec, 0, dur(380), null);
        }});
    }

    /** Tema premium elegido y desbloqueado. */
    private boolean premiumActive() {
        String id = prefs.getString("premium_theme", null);
        return id != null && premiumUnlocked(id);
    }

    /** Aurora y Cobalto son gratis para todos desde la 2.24. Los temas siguientes: en StradaOS Prueba sin código
     *  y, en la versión normal, con el código atado al ID de la pantalla (Fase 3). */
    private boolean premiumUnlocked(String id) {
        return Prem.known(id) && (Prem.free(id) || debugUnlock() || Lic.has(this, id));
    }

    /** StradaOS Prueba aplica los temas sin código, salvo que se quiera probar el desbloqueo como en la versión normal. */
    private boolean debugUnlock() {
        return getPackageName().endsWith(".debug") && !prefs.getBoolean("lic_test", false);
    }

    /** Tarjeta translúcida con un borde fino de luz. */
    private GradientDrawable glass(float r) {
        GradientDrawable d;
        if (Prem.cob()) {
            d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Cobalto.glassTop(LIGHT), Cobalto.glassBottom(LIGHT)});
            d.setCornerRadius(r * s);
        } else d = round(Prem.glass(LIGHT), r);
        d.setStroke(Math.max(1, (int) s), Prem.glassEdge(LIGHT));
        return d;
    }

    /** Ancho del lienzo del inicio (720 en vertical; 1024 + lo que sobre en pantallas anchas). */
    private float premW() { return vertical ? 720 : 1024 + ex; }

    /** Medidas: cabecera arriba, el mapa a lo ancho (desde arriba con «Solo mapa») y la píldora abajo. */
    private void premiumMetrics() {
        premPillH = noDock ? 0 : vertical ? 68 : 64;
        dockTop = noDock ? boardH : boardH - 16 - premPillH;
        premHeadY = 60;
        if (Prem.carb()) { carbMetrics(); return; }
        if (Prem.faro()) { faroMetrics(); return; }
        if (Prem.cob()) { cobaltoMetrics(); return; }
        if (mapOnly) premHeadH = 0;
        else if (vertical) premHeadH = 92 + 8 + (shortVertical ? 150 : 172);
        else premHeadH = 150;
        mapX = 16;
        mapW = premW() - 32;
        mapY = mapOnly ? premHeadY : premHeadY + premHeadH + 12;
        premWide = !vertical && !mapOnly && ex >= 380;
        if (premWide) {
            premColW = 372 + Math.min(120, (ex - 380) / 3f);
            mapX = 16 + premColW + 12;
            mapW = premW() - 32 - premColW - 12;
            mapY = premHeadY;
        }
        mapH = Math.max(120, dockTop - (noDock ? 16 : 12) - mapY);
        cardH = mapH; rowY = mapY; insL = insR = 0;
        carX = audX = 16; audW = 248;
    }

    private void buildPremium() {
        carCard = null; mapGauge = null; mapGaugeBox = null; carGauge = null; chipTv = null; carDataTv = null;
        faroGauge = null; faroBar = null; faroWxIc = null; faroMaintBox = null; faroPillTv = null; carbMaintBox = null;
        if (Prem.carb()) { buildCarbono(); setGps(false, 0); return; }
        if (Prem.faro()) { buildFaro(); setGps(false, 0); return; }
        buildPremiumTop();
        buildAaCard();
        if (mapOnly) premiumDetached();
        else if (Prem.cob()) buildCobalto();
        else buildPremiumHeader();
        if (!noDock) buildPremiumDock();
        setGps(false, 0);
    }

    // ───────────────────────── tema premium «Cobalto» (2.24) ─────────────────────────
    // Horizontal: temperatura arriba del mapa; el mapa se difumina hasta la tarjeta del reproductor; reproductor con la
    // carátula redonda; vehículo con el velocímetro arriba, el auto en el medio y el mantenimiento abajo. Vertical: el
    // mapa arriba (se difumina hacia las tarjetas) y reproductor y vehículo lado a lado. Reusa la barra de arriba, la
    // barra de apps, «Solo mapa» y las transiciones de Aurora.

    private float cobTempH, cobColW, cobCardsY, cobCardsH;

    private void cobaltoMetrics() {
        float W0 = premW(), bottom = dockTop - (noDock ? 16 : 12);
        premHeadH = 0;
        premWide = false;
        cobTempH = prefs.getBoolean("weather_on", true) ? 52 : 0;
        mapX = 16;
        if (mapOnly) {
            mapY = premHeadY;
            mapW = W0 - 32;
        } else if (vertical) {
            cobCardsH = shortVertical ? 360 : 450;   // más altas: llegan hasta el mapa
            cobCardsY = bottom - cobCardsH;
            mapY = premHeadY + (cobTempH > 0 ? cobTempH + 8 : 0);
            mapW = W0 - 32;
            bottom = cobCardsY;   // el difuminado termina donde empiezan las tarjetas
        } else {
            cobColW = 256 + Math.min(60, Math.max(0, ex) / 6f);
            float px = W0 - 16 - 2 * cobColW;   // reproductor y vehículo pegados (como la referencia)
            mapY = premHeadY + (cobTempH > 0 ? cobTempH + 8 : 0);
            // el difuminado termina donde empieza el reproductor (un poco por debajo de su borde inclinado)
            mapW = px - 16 + cobSkew(cobColW, bottom - premHeadY) * 0.5f;
        }
        mapH = Math.max(120, bottom - mapY);
        cardH = mapH; rowY = mapY; insL = insR = 0;
        carX = audX = 16; audW = 248;
    }

    private void buildCobalto() {
        premHeadViews.clear();
        float W0 = premW();
        if (cobTempH > 0) buildCobTemp(24, premHeadY, (vertical ? W0 - 32 : mapW) - 8, cobTempH);
        if (vertical) {
            float cw = (W0 - 32) / 2;
            buildCobMusic(16, cobCardsY, cw, cobCardsH);
            buildCobCar(16 + cw, cobCardsY, cw, cobCardsH);
        } else {
            float top = premHeadY, hh = dockTop - (noDock ? 16 : 12) - top;
            float vx = W0 - 16 - cobColW, px = vx - cobColW;
            buildCobMusic(px, top, cobColW, hh);
            buildCobCar(vx, top, cobColW, hh);
        }
    }

    /** Temperatura sobre el mapa: ícono, grados en grande, estado y máxima/mínima. */
    private void buildCobTemp(float x, float y, float w, float hh) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        panelWxIc = icon("cloud", 34);
        row.addView(panelWxIc, new LinearLayout.LayoutParams((int) (34 * s), (int) (34 * s)));
        panelWxTemp = tv("--°", 30, TEXT, true);
        panelWxTemp.setPadding((int) (10 * s), 0, (int) (12 * s), 0);
        row.addView(panelWxTemp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        panelWxDesc = tv("Clima", 14, TEXT, false);
        col.addView(panelWxDesc);
        panelWxHiLo = tv("", 12, MUTED, false);
        col.addView(panelWxHiLo);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        panelWxHours = null;
        panelWx = row;
        place(board, row, x, y, w, hh);
        premHeadViews.add(row);
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showWeatherDetail(); } });
        showPanelWeather();
    }

    /** Reproductor: carátula redonda con el avance alrededor, canción y artista centrados y los controles abajo. */
    /** Inclinación de las tarjetas de Cobalto (el borde de arriba corrido a la derecha), en unidades del lienzo. */
    private float cobSkew(float w, float hh) { return Math.min(w * 0.22f, hh * 0.15f); }

    /** Centro horizontal de una tarjeta inclinada a la altura y (el borde izquierdo se corre hacia la derecha al bajar). */
    private static float cobMid(float w, float hh, float sk, float y) { return (w + sk * Math.max(0, Math.min(1, y / hh))) / 2; }

    private void buildCobMusic(float x, float y, float w, float hh) {
        final FrameLayout card = new FrameLayout(this);
        final float sk = vertical ? 0 : cobSkew(w, hh);   // en vertical, rectas (mismas luces y línea)
        card.setBackground(new Cobalto.SkewCard(LIGHT, 24 * s, sk * s, s));
        place(board, card, x, y, w, hh);
        premHeadViews.add(card);
        float m = 16;
        placeWrap(card, tv("Música", 12, MUTED, true), m + 6, 16);
        sourceTv = tv("", 11, RED, true);
        sourceTv.setGravity(Gravity.RIGHT);
        place(card, sourceTv, w * 0.38f, 14, w * 0.62f - m, 16);
        // elementos grandes: carátula, títulos y botones
        float d = vertical ? Math.min(w * 0.56f, hh * 0.40f) : Math.min(w * 0.72f, hh * 0.44f);
        float ps = vertical ? 56 : 60, bs = ps - 8;
        float tH = vertical ? 24 : 28, aH = vertical ? 20 : 22;
        float block = d + 14 + tH + 2 + aH + 4 + 16, avail = hh - 38 - 16, total = block + 16 + ps;
        float y0 = 38 + Math.max(0, (avail - total) / 2);
        playerArt = new PlayerArt(this, s, asset("ic/music_note.png"));
        // todo centrado en la parte visible de la tarjeta a esa altura (el borde izquierdo está inclinado)
        place(card, playerArt, cobMid(w, hh, sk, y0 + d / 2) - d / 2, y0, d, d);
        float ty = y0 + d + 14;
        float tx = sk * (ty + 30) / hh + m, tw = w - m - tx;
        trackTv = tv("Nada sonando", vertical ? 18 : 20, TEXT, true);
        trackTv.setGravity(Gravity.CENTER_HORIZONTAL);
        place(card, trackTv, tx, ty, tw, tH);
        artistTv = tv("Radio, Bluetooth o USB", vertical ? 14 : 15, MUTED, false);
        artistTv.setGravity(Gravity.CENTER_HORIZONTAL);
        place(card, artistTv, tx, ty + tH + 2, tw, aH);
        timePosTv = tv("", 11, MUTED, false);
        timePosTv.setGravity(Gravity.CENTER_HORIZONTAL);
        place(card, timePosTv, tx, ty + tH + 2 + aH + 4, tw, 16);
        float ry = y0 + block + 16, cx = cobMid(w, hh, sk, ry + ps / 2);
        float aw = w - sk * (ry + ps) / hh - 16;   // ancho visible a la altura de los botones
        if (ps + 2 * bs + 16 > aw) { bs = Math.max(36, (aw - ps - 16) / 2); }
        float sp = Math.max(4, Math.min(20, (aw - ps - 2 * bs) / 4));
        final View prev = circleIcon("skip_previous", bs, bs * 0.56f, 0), next = circleIcon("skip_next", bs, bs * 0.56f, 0);
        prev.setBackground(round(Prem.soft(LIGHT), bs / 2));
        next.setBackground(round(Prem.soft(LIGHT), bs / 2));
        View play = circleIcon("play_arrow", ps, ps * 0.5f, RED);
        place(card, prev, cx - ps / 2 - sp - bs, ry + (ps - bs) / 2, bs, bs);
        place(card, play, cx - ps / 2, ry, ps, ps);
        place(card, next, cx + ps / 2 + sp, ry + (ps - bs) / 2, bs, bs);
        playIc = (ImageView) ((FrameLayout) play).getChildAt(0);
        final View.OnClickListener goPrev = mediaClick(KeyEvent.KEYCODE_MEDIA_PREVIOUS), goNext = mediaClick(KeyEvent.KEYCODE_MEDIA_NEXT);
        prev.setOnClickListener(goPrev);
        play.setOnClickListener(mediaClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        next.setOnClickListener(goNext);
        card.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getX(); y0 = e.getY(); return false; }
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return false;
                float dx = e.getX() - x0, dy = e.getY() - y0;
                if (Math.abs(dx) < 50 * s || Math.abs(dx) < 2 * Math.abs(dy)) return false;
                MotionEvent c = MotionEvent.obtain(e);
                c.setAction(MotionEvent.ACTION_CANCEL);
                v.onTouchEvent(c);
                c.recycle();
                (dx < 0 ? goNext : goPrev).onClick(v);
                return true;
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { showPlayerDiag(); return true; } });
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openMusicApp(); } });
        renderTrack();
    }

    /** Vehículo: velocímetro arriba (digital, analógico o mixto), el auto en el medio y el mantenimiento abajo. */
    private void buildCobCar(float x, float y, float w, float hh) {
        FrameLayout card = new FrameLayout(this);
        final float sk = vertical ? 0 : cobSkew(w, hh);
        card.setBackground(new Cobalto.SkewCard(LIGHT, 24 * s, sk * s, s));
        place(board, card, x, y, w, hh);
        premHeadViews.add(card);
        float m = 14;
        // nombre del vehículo y su etiqueta en el color de acento (la de Tu vehículo, como en el inicio de siempre)
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        carTv = tv(prefs.getString("car_name", "Mi vehículo"), 12, MUTED, true);
        carTv.setMaxWidth((int) ((w - 2 * m - 80) * 0.6f * s));
        nameRow.addView(carTv);
        String tag = prefs.getString("car_tag", "").trim();
        TextView chip = tv(tag, 10, Color.WHITE, true);
        chip.setPadding((int) (7 * s), (int) (2 * s), (int) (7 * s), (int) (2 * s));
        chip.setBackground(round(RED, 7));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = (int) (8 * s);
        nameRow.addView(chip, clp);
        if (tag.length() == 0) chip.setVisibility(View.GONE);
        chipTv = chip;
        place(card, nameRow, m + 8, 13, w - 2 * m - 80, 22);
        LinearLayout g = new LinearLayout(this);
        g.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        gpsDot = new View(this);
        gpsDot.setBackground(round(MUTED, 4));
        g.addView(gpsDot, new LinearLayout.LayoutParams((int) (7 * s), (int) (7 * s)));
        gpsTv = tv("GPS", 10, MUTED, false);
        gpsTv.setPadding((int) (5 * s), 0, 0, 0);
        g.addView(gpsTv);
        place(card, g, w - m - 100, 16, 100, 16);
        float stripH = 38, stripY = hh - stripH - 14;
        float top = 36, gh = carSpeed ? Math.min(hh * 0.42f, w * 0.78f) : 0;
        // velocímetro
        final int speedo = carSpeed ? prefs.getInt("speedo", 0) : 0;
        LinearLayout sv = new LinearLayout(this);
        sv.setBaselineAligned(true);
        sv.setGravity(Gravity.CENTER);
        speedTv = tv("0", vertical ? 52 : 60, TEXT, true);
        TextView unit = tv(useMph() ? "mph" : "km/h", 14, MUTED, false);
        unit.setPadding((int) (6 * s), 0, 0, 0);
        sv.addView(speedTv);
        sv.addView(unit);
        carGauge = null;
        if (carSpeed && speedo > 0) {
            carGauge = new Gauge(this, s);
            carGauge.setColors(RED, TEXT, SOFT);
            carGauge.setUnit(useMph());
            carGauge.setNumber(speedo == 2);
            carGauge.setCompact(gh < 170);
            float gx = sk * (top + gh / 2) / hh;   // centrado en la parte visible (borde inclinado)
            place(card, carGauge, 10 + gx, top, w - 20 - gx, gh);
        } else if (carSpeed) {
            float gx = sk * (top + gh / 2) / hh;
            place(card, sv, 10 + gx, top, w - 20 - gx, gh);
        }
        // la foto de Tu vehículo (como el inicio de siempre). Recortada (fondo transparente): completa y centrada, con
        // sombra suave. Con fondo: recuadro redondeado. Sin foto: tocar para ponerla.
        float cy = top + gh + (carSpeed ? 4 : 0), ch = (oilHome ? stripY - 8 : hh - 12) - cy;
        float px0 = sk * (cy + ch) / hh + m, pw = w - m - px0;
        final boolean oilTap = oilHome;
        View.OnClickListener cardTap = new View.OnClickListener() { public void onClick(View v) {
            if (oilTap) badgeMenu(); else showSettings(C_VEHICLE);
        }};
        View.OnLongClickListener photoLong = new View.OnLongClickListener() {
            public boolean onLongClick(View v) { photoMenu(); return true; }
        };
        Bitmap photo = CarPhoto.load(this);
        if (photo != null) {
            boolean cut = false;
            try { cut = CarPhoto.hasTransparency(photo); } catch (Throwable ignored) { }
            View pv;
            // en el mismo recuadro que «Toca para poner la foto»: completa, centrada y sin cortarse
            if (cut) {
                pv = new Cobalto.CarFit(this, photo);
                place(card, pv, px0, cy + 4, pw, ch - 8);
            } else {
                ImageView car = new ImageView(this);
                car.setImageBitmap(CarPhoto.cardCrop(photo, (int) (pw * s), (int) ((ch - 8) * s), 16 * s));
                car.setScaleType(ImageView.ScaleType.FIT_CENTER);
                place(card, car, px0, cy + 4, pw, ch - 8);
                pv = car;
            }
            pv.setOnClickListener(cardTap);
            pv.setOnLongClickListener(photoLong);
        } else if (emptyPhotoSlot) {
            final View slot = new View(this);
            place(card, slot, px0, cy + 4, pw, ch - 8);
            slot.postDelayed(new Runnable() { public void run() {
                int[] l = new int[2];
                slot.getLocationOnScreen(l);
                android.util.Log.w("StradaPromo", "hueco " + l[0] + "," + l[1] + "," + slot.getWidth() + "," + slot.getHeight());
            }}, 2500);
        } else {
            LinearLayout ph = new LinearLayout(this);
            ph.setOrientation(LinearLayout.VERTICAL);
            ph.setGravity(Gravity.CENTER);
            ph.setBackground(round(Prem.soft(LIGHT), 16));
            float is = Math.min(56, ch * 0.45f);
            ImageView ic = icon("directions_car", is);
            ic.setAlpha(0.4f);
            ph.addView(ic, new LinearLayout.LayoutParams((int) (is * s), (int) (is * s)));
            TextView t = tv("Toca para poner la foto de tu vehículo", 12, MUTED, false);
            t.setSingleLine(false);
            t.setMaxLines(2);
            t.setGravity(Gravity.CENTER);
            t.setPadding((int) (10 * s), (int) (4 * s), (int) (10 * s), 0);
            ph.addView(t, new LinearLayout.LayoutParams(-1, -2));
            if (ch < 90) t.setVisibility(View.GONE);
            place(card, ph, px0, cy + 4, pw, ch - 8);
            ph.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickPhoto(); } });
        }
        float ox = sk * (stripY + stripH) / hh + m;   // la barrita del mantenimiento sigue el borde inclinado
        if (oilHome) premOilStrip(card, ox, stripY, w - ox - m, stripH);
        else { oilValTv = null; oilBar = null; oilBarBg = null; oilLblTv = null; oilIc = null; }
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (oilHome) badgeMenu(); else showSettings(C_VEHICLE);
        }});
    }

    // ───────────────────────── tema premium «Faro» (2.25, Bloque 16) ─────────────────────────
    // Horizontal: columna izquierda con el velocímetro arriba y el carro visto desde arriba (sin tarjeta, sobre el
    // fondo); la barra de estado arriba a la derecha; el mapa en trazo en el centro; clima y mantenimientos a la
    // derecha; el reproductor horizontal debajo del mapa y los mantenimientos; la barra de apps compacta abajo.
    // Vertical: el mapa arriba; debajo, la columna (velocímetro + carro) junto al clima y los mantenimientos; el
    // reproductor y la barra. Sin «Solo mapa» (tema fijo, como Cobalto).

    private float faroColX, faroColY, faroColW, faroColH, faroRX, faroRW, faroWxY, faroWxH, faroMtY, faroMtH,
        faroPlX, faroPlY, faroPlW, faroPlH, faroTopX;
    private FaroGauge faroGauge;
    private Faro.Timeline faroBar;
    private Faro.Icon faroWxIc;
    private TextView faroCityTv, faroTempTv, faroHiLoTv, faroDescTv, faroRainTv, faroHumTv, faroPillTv;
    private LinearLayout faroHours, faroRainBox, faroHumBox;
    private FrameLayout faroMaintBox;
    private float faroMaintW, faroMaintH, faroHoursW;
    private Faro.Icon faroPillIc;
    /** Modo del último inicio armado con Faro (null = ninguno todavía): para encender o apagar los faros. */
    private static Boolean faroLastLight;

    private void faroMetrics() {
        float W0 = premW();
        premHeadH = 0;
        premWide = false;
        premPillH = noDock ? 0 : vertical ? 60 : 56;
        dockTop = noDock ? boardH : boardH - 10 - premPillH;
        float bottom = dockTop - (noDock ? 12 : 10), top = 8, topH = 34;
        faroPlH = showPlayer ? (vertical ? 80 : 74) : 0;
        faroPlY = bottom - faroPlH;
        float above = faroPlY - (showPlayer ? 10 : 0);   // donde terminan el mapa y las tarjetas
        boolean wx = prefs.getBoolean("weather_on", true);
        if (vertical) {
            float rh = shortVertical ? 300 : Math.max(300, Math.min(470, boardH * 0.36f));
            float ry = above - rh;
            faroTopX = 12;
            mapX = 12; mapY = top + topH + 4; mapW = W0 - 24;
            mapH = Math.max(120, ry - 10 - mapY);
            faroColX = 12; faroColY = ry; faroColW = 290; faroColH = rh;
            faroRX = faroColX + faroColW + 12; faroRW = W0 - 12 - faroRX;
            faroWxY = ry; faroWxH = wx ? Math.round(rh * 0.54f) : 0;
            faroPlX = 12; faroPlW = W0 - 24;
        } else {
            // la columna llega hasta el borde de abajo: la barra de apps va solo debajo del reproductor
            faroColX = 12; faroColY = top; faroColW = 292 + Math.min(64, ex / 6f); faroColH = boardH - top;
            float rx0 = faroColX + faroColW + 12;
            faroRW = 324 + Math.min(48, ex / 8f);
            faroRX = W0 - 12 - faroRW;
            faroTopX = rx0;
            mapX = rx0; mapY = top + topH + 4; mapW = faroRX - 12 - rx0;
            mapH = Math.max(120, above - mapY);
            faroWxY = mapY; faroWxH = wx ? Math.round(mapH * 0.54f) : 0;
            faroPlX = rx0; faroPlW = W0 - 12 - rx0;
        }
        faroMtY = wx ? faroWxY + faroWxH + 10 : faroWxY;
        faroMtH = (vertical ? faroColY + faroColH : mapY + mapH) - faroMtY;
        cardH = mapH; rowY = mapY; insL = insR = 0;
        carX = audX = 16; audW = 248;
    }

    private void buildFaro() {
        premHeadViews.clear();
        faroGauge = null; faroBar = null; faroWxIc = null; faroMaintBox = null; faroPillTv = null; faroPillIc = null;
        buildFaroTop();
        // en claro, el área del mapa con la misma sombra que las tarjetas (el mapa la tapa por dentro)
        if (LIGHT) {
            View sh = new View(this);
            sh.setBackground(new Faro.Card(true, 20 * s, 8 * s, s));
            place(board, sh, mapX - 8, mapY - 8, mapW + 16, mapH + 16);
        }
        buildAaCard();
        buildFaroColumn();
        if (faroWxH > 0) buildFaroWeather(faroRX, faroWxY, faroRW, faroWxH);
        else { faroWxIc = null; panelWx = null; }
        buildFaroMaint(faroRX, faroMtY, faroRW, faroMtH);
        if (showPlayer) buildFaroPlayer(faroPlX, faroPlY, faroPlW, faroPlH);
        else { trackTv = null; artistTv = null; sourceTv = null; timePosTv = null; playerArt = null; playIc = null; }
        if (!noDock) buildFaroDock();
        updateOilBadge();
    }

    /** Tarjeta translúcida de Faro. Devuelve el marco interior (w×hh) donde van las vistas. */
    private FrameLayout faroCard(float x, float y, float w, float hh) {
        float m = 8;
        FrameLayout outer = new FrameLayout(this);
        outer.setBackground(new Faro.Card(LIGHT, 18 * s, m * s, s));
        place(board, outer, x - m, y - m, w + 2 * m, hh + 2 * m);
        FrameLayout inner = new FrameLayout(this);
        place(outer, inner, m, m, w, hh);
        return inner;
    }

    /** Botón con un ícono de trazo centrado (área de toque más grande que el ícono). */
    private FrameLayout faroIconBtn(int type, float box, float ic, int bg) {
        FrameLayout f = new FrameLayout(this);
        if (bg != 0) {
            GradientDrawable d = round(bg, box * 0.3f);
            if (LIGHT) d.setStroke(Math.max(1, (int) s), 0x14000000);
            f.setBackground(d);
        }
        Faro.Icon i = new Faro.Icon(this, type, TEXT);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (ic * s), (int) (ic * s));
        lp.gravity = Gravity.CENTER;
        f.addView(i, lp);
        f.setTag(i);
        return f;
    }

    /** Barra de estado: hora y fecha a la izquierda; Wi-Fi, Bluetooth (y Apps sin barra) y Ajustes a la derecha. */
    private void buildFaroTop() {
        float W0 = premW(), x = faroTopX, w = W0 - 12 - x;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        greetTv = tv("", 13, MUTED, false);   // no se muestra (el reloj lo actualiza)
        timeTv = tv("", 17, TEXT, true);
        dateTv = tv("", 13, MUTED, false);
        dateTv.setPadding((int) (10 * s), 0, 0, 0);
        row.addView(timeTv);
        row.addView(dateTv);
        place(board, row, x + 4, 8, w * 0.6f, 34);
        premTopRow = row;
        topSpeedTv = null; topOilTv = null; topIconName = null; weatherBox = null;
        topNavPill = null; topNavIc = null; topNavDistTv = null;

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        wifiIc = null;
        btIc = null;
        addCarData(right);
        if (prefs.getBoolean("status_icons", true)) {
            LinearLayout st = new LinearLayout(this);
            st.setGravity(Gravity.CENTER_VERTICAL);
            wifiIc = statusIcon(st, "wifi", new Runnable() { public void run() { toggleWifi(); } },
                new Runnable() { public void run() { openSettingsPage(Settings.ACTION_WIFI_SETTINGS, new Runnable() { public void run() { openSystemSettings(); } }); } });
            btIc = statusIcon(st, "bluetooth", new Runnable() { public void run() { toggleBt(); } },
                new Runnable() { public void run() {
                    if (clwBt) openSlot(1);
                    else openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS, new Runnable() { public void run() { openSlot(1); } });
                } });
            right.addView(st);
        }
        if (noDock) {
            FrameLayout apps = faroIconBtn(Faro.I_APPS, 32, 16, Faro.tile(LIGHT));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams((int) (32 * s), (int) (32 * s));
            alp.leftMargin = (int) (8 * s);
            right.addView(apps, alp);
            apps.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openDrawer(); } });
        }
        FrameLayout gear = circleIcon("settings", 32, 18, 0);
        GradientDrawable gd = round(Faro.tile(LIGHT), 16);
        if (LIGHT) gd.setStroke(Math.max(1, (int) s), 0x14000000);
        gear.setBackground(gd);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams((int) (32 * s), (int) (32 * s));
        glp.leftMargin = (int) (8 * s);
        right.addView(gear, glp);
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openSettings(); } });
        place(board, right, x + w * 0.4f, 9, w * 0.6f, 32);
    }

    /** Columna izquierda: velocímetro arriba, nombre del vehículo con el GPS y el carro visto desde arriba. */
    private void buildFaroColumn() {
        float x = faroColX, y = faroColY, w = faroColW, hh = faroColH;
        float D = carSpeed ? Math.min(w - 36, vertical ? hh * 0.36f : (boardH - 16) * 0.33f) : 0;
        float gx = x + (w - D) / 2, gy = y + 6;
        float labelH = 20, carMax = Math.min(hh * 0.70f, 380);
        float labelY = carSpeed ? gy + D + 2 : y + Math.max(4, (hh - carMax - labelH - 4) * 0.40f);
        // el carro (y su sombra) llega hasta abajo: en horizontal, hasta el borde de la pantalla
        float carY = labelY + labelH + 2, carH = Math.max(60, carSpeed ? y + hh - carY : Math.min(carMax, y + hh - carY));
        String col = Faro.car(prefs.getString("faro_car", "silver"));
        Faro.Car car = new Faro.Car(this, col, LIGHT);
        place(board, car, x, carY, w, carH);
        car.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSettings(C_VEHICLE); } });
        car.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { chooseFaroCar(); return true; } });
        // faros: encendidos en oscuro; al pasar de claro a oscuro se encienden y de oscuro a claro se apagan
        android.graphics.RectF cr = Faro.carRect(w * s, carH * s);
        float lampY = carY + (cr.top + cr.height() * Faro.LAMP_Y) / s;
        float lxL = (cr.left + cr.width() * Faro.LAMP_X) / s, lxR = (cr.left + cr.width() * (1 - Faro.LAMP_X)) / s;
        float bTop = carSpeed ? y : Math.max(y, lampY - 160), bH = lampY + 16 - bTop;
        Boolean prev = faroLastLight;
        faroLastLight = LIGHT;
        boolean fadeOut = LIGHT && prev != null && !prev;
        if ((!LIGHT || fadeOut) && bH > 40) {
            float poolY = carSpeed ? gy + D * 0.80f - bTop : (lampY - bTop) * 0.25f;
            final Faro.Beams beams = new Faro.Beams(this, s, new float[]{lxL * s, (lampY - bTop) * s, lxR * s, (lampY - bTop) * s},
                (carSpeed ? gy + D * 0.5f - bTop : 0) * s, w / 2 * s, poolY * s, w * 0.42f * s);
            place(board, beams, x, bTop, w, bH);
            if (fadeOut) {
                beams.setAlpha(1f);
                afterFirstFrame(new Runnable() { public void run() {
                    beams.animate().alpha(0f).setStartDelay(dur(150)).setDuration(dur(700)).withLayer().withEndAction(new Runnable() { public void run() {
                        if (beams.getParent() instanceof ViewGroup) ((ViewGroup) beams.getParent()).removeView(beams);
                    }});
                }});
            } else if (prev == null || prev) {
                beams.setAlpha(0f);
                afterFirstFrame(new Runnable() { public void run() {
                    beams.animate().alpha(1f).setStartDelay(dur(150)).setDuration(dur(900))
                        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).withLayer();
                }});
            }
        }
        // velocímetro (mixto por defecto en Faro si nunca se eligió uno)
        speedTv = tv("0", 12, TEXT, true);   // no se muestra: el número va en el velocímetro
        carGauge = null; mapGauge = null;
        if (carSpeed) {
            faroGauge = new FaroGauge(this, s);
            faroGauge.setTheme(LIGHT, TEXT, MUTED);
            faroGauge.setUnit(useMph());
            faroGauge.setMode(prefs.contains("speedo") ? prefs.getInt("speedo", 2) : 2);
            place(board, faroGauge, gx, gy, D, D);
        }
        // nombre del vehículo y el GPS (donde la referencia dice «Comfort»)
        LinearLayout lab = new LinearLayout(this);
        lab.setGravity(Gravity.CENTER);
        carTv = tv(prefs.getString("car_name", "Mi vehículo"), 13, MUTED, true);
        carTv.setMaxWidth((int) ((w - 90) * s));
        lab.addView(carTv);
        gpsDot = new View(this);
        gpsDot.setBackground(round(MUTED, 4));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int) (7 * s), (int) (7 * s));
        dlp.leftMargin = (int) (10 * s);
        lab.addView(gpsDot, dlp);
        gpsTv = tv("GPS", 11, MUTED, false);
        gpsTv.setPadding((int) (5 * s), 0, 0, 0);
        lab.addView(gpsTv);
        place(board, lab, x, labelY, w, labelH);
        chipTv = null;
        oilValTv = null; oilBar = null; oilBarBg = null; oilLblTv = null; oilIc = null;
    }

    /**
     * Pantalla sencilla (la típica de los radios: 1024×600, 1280×720…): lado corto ≤ 720 px y densidad ≤ 240. En
     * esas pantallas el mapa de Faro va más fuerte por defecto; en celulares y tabletas de alta resolución, suave.
     */
    private boolean simpleScreen() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.min(dm.widthPixels, dm.heightPixels) <= 720 && dm.densityDpi <= 240;
    }

    /** Elegir el color del carro (presión larga sobre el carro o Ajustes → Pantalla de inicio). */
    private void chooseFaroCar() {
        final String[] cols = Faro.CAR_COLORS;
        String[] names = new String[cols.length];
        for (int i = 0; i < cols.length; i++) names[i] = I18n.t(Faro.CAR_NAMES[i]);
        int sel = Math.max(0, java.util.Arrays.asList(cols).indexOf(Faro.car(prefs.getString("faro_car", "silver"))));
        new TB(this, dlg()).raw()
            .setTitle(I18n.t("Color del carro"))
            .setSingleChoiceItems(names, sel, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                d.dismiss();
                prefs.edit().putString("faro_car", cols[which]).apply();
                rebuild();
                if (settingsView != null) showSettings(settingsCat);
            }})
            .show();
    }

    /** Clima: ciudad, temperatura grande, máxima/mínima, estado, lluvia y humedad, y las próximas horas abajo. */
    private void buildFaroWeather(float x, float y, float w, float hh) {
        faroHrIc = 22; faroHrGap = 8;
        FrameLayout card = faroCard(x, y, w, hh);
        float m = 16;
        faroCityTv = tv("Clima", 13, MUTED, true);
        place(card, faroCityTv, m, 14, w * 0.56f - m, 18);
        faroTempTv = tv("--°", 46, TEXT, false);
        faroTempTv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        place(card, faroTempTv, m - 2, 34, w * 0.52f, 54);
        faroHiLoTv = tv("", 12, MUTED, false);
        place(card, faroHiLoTv, m, 92, w * 0.5f, 16);
        float is = 40;
        faroWxIc = new Faro.Icon(this, Faro.I_CLOUD, TEXT);
        faroWxIc.setStroke(1.35f);
        place(card, faroWxIc, w - m - is, 12, is, is);
        faroDescTv = tv("", 14, TEXT, false);
        faroDescTv.setGravity(Gravity.RIGHT);
        place(card, faroDescTv, w * 0.36f, 58, w * 0.64f - m, 19);
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        faroRainBox = new LinearLayout(this);
        faroRainBox.setGravity(Gravity.CENTER_VERTICAL);
        Faro.Icon ui = new Faro.Icon(this, Faro.I_UMBRELLA, MUTED);
        faroRainBox.addView(ui, new LinearLayout.LayoutParams((int) (13 * s), (int) (13 * s)));
        faroRainTv = tv("", 12, MUTED, false);
        faroRainTv.setPadding((int) (4 * s), 0, 0, 0);
        faroRainBox.addView(faroRainTv);
        meta.addView(faroRainBox);
        faroHumBox = new LinearLayout(this);
        faroHumBox.setGravity(Gravity.CENTER_VERTICAL);
        Faro.Icon di = new Faro.Icon(this, Faro.I_DROP, MUTED);
        faroHumBox.addView(di, new LinearLayout.LayoutParams((int) (13 * s), (int) (13 * s)));
        faroHumTv = tv("", 12, MUTED, false);
        faroHumTv.setPadding((int) (4 * s), 0, 0, 0);
        faroHumBox.addView(faroHumTv);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.leftMargin = (int) (12 * s);
        meta.addView(faroHumBox, hlp);
        place(card, meta, w * 0.3f, 82, w * 0.7f - m, 18);
        // próximas horas
        float hy = Math.max(116, hh * 0.52f);
        faroHours = new LinearLayout(this);
        faroHours.setOrientation(LinearLayout.HORIZONTAL);
        faroHoursW = w - 2 * m + 8;
        place(card, faroHours, m - 4, hy, faroHoursW, Math.max(40, hh - hy - 10));
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showWeatherDetail(); } });
        renderFaroWx();
    }

    private void renderFaroWx() {
        if (faroWxIc == null) return;
        Weather.Now n = weatherNow;
        faroHours.removeAllViews();
        if (n == null) {
            faroCityTv.setText(I18n.t("Clima"));
            faroTempTv.setText("--°");
            faroHiLoTv.setText(I18n.t(Double.isNaN(wLat) ? "Esperando la ubicación…" : "Cargando…"));
            faroDescTv.setText("");
            faroRainBox.setVisibility(View.GONE);
            faroHumBox.setVisibility(View.GONE);
            faroWxIc.set(Faro.I_CLOUD);
            return;
        }
        faroCityTv.setText(n.city != null && n.city.length() > 0 ? n.city : I18n.t("Clima"));
        faroTempTv.setText(deg(n.temp));
        Weather.Day d = n.today();
        faroHiLoTv.setText(d != null ? deg(d.hi) + " / " + deg(d.lo) : "");
        faroDescTv.setText(I18n.t(Weather.describe(n.code)));
        faroWxIc.set(Faro.weatherIcon(n.code, n.day));
        int rain = !n.hours.isEmpty() && n.hours.get(0).rain >= 0 ? n.hours.get(0).rain : d != null ? d.rain : -1;
        faroRainBox.setVisibility(rain >= 0 ? View.VISIBLE : View.GONE);
        if (rain >= 0) faroRainTv.setText(I18n.integer(rain) + "%");
        faroHumBox.setVisibility(n.humidity >= 0 ? View.VISIBLE : View.GONE);
        if (n.humidity >= 0) faroHumTv.setText(I18n.integer(n.humidity) + "%");
        int cols = Math.max(3, Math.min(6, (int) (faroHoursW / 48)));
        for (int i = 0; i < n.hours.size() && i < cols; i++) {
            Weather.Hour hr = n.hours.get(i);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            TextView ht = tv(hourLabel(hr.hour), 11, MUTED, false);
            ht.setGravity(Gravity.CENTER);
            c.addView(ht, new LinearLayout.LayoutParams(-1, -2));
            Faro.Icon ic = new Faro.Icon(this, Faro.weatherIcon(hr.code, hr.day), TEXT);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams((int) (faroHrIc * s), (int) (faroHrIc * s));
            ilp.topMargin = (int) (faroHrGap * s);
            ilp.bottomMargin = (int) (faroHrGap * s);
            c.addView(ic, ilp);
            TextView tt = tv(deg(hr.temp), 13, TEXT, true);
            tt.setGravity(Gravity.CENTER);
            c.addView(tt, new LinearLayout.LayoutParams(-1, -2));
            faroHours.addView(c, new LinearLayout.LayoutParams(0, -1, 1f));
        }
    }

    /** Próximos mantenimientos (los de Ajustes → Mantenimientos), del más urgente al más lejano. */
    private void buildFaroMaint(float x, float y, float w, float hh) {
        FrameLayout card = faroCard(x, y, w, hh);
        place(card, tv("Mantenimientos", 13, TEXT, true), 16, 12, w - 32, 18);
        faroMaintBox = new FrameLayout(this);
        faroMaintW = w - 24;
        faroMaintH = Math.max(32, hh - 38 - 6);
        place(card, faroMaintBox, 12, 38, faroMaintW, faroMaintH);
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSettings(C_MAINT); } });
        renderFaroMaint();
    }

    private void renderFaroMaint() {
        if (carbMaintBox != null) { renderCarbMaint(); return; }
        if (faroMaintBox == null) return;
        faroMaintBox.removeAllViews();
        final List<org.json.JSONObject> l = maint();
        List<double[]> items = new ArrayList<double[]>();   // {lo que falta, índice} (índice -1 = aceite)
        if (oilOn()) items.add(new double[]{(oilNextKm() - odoKm()) / Math.max(1, prefs.getFloat("oil_interval_km", 5000)), -1});
        for (int i = 0; i < l.size(); i++) items.add(new double[]{taskFrac(l.get(i)), i});
        java.util.Collections.sort(items, new java.util.Comparator<double[]>() {
            public int compare(double[] a, double[] b) { return Double.compare(a[0], b[0]); }
        });
        if (items.isEmpty()) {
            LinearLayout e = new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL);
            e.setGravity(Gravity.CENTER);
            TextView t1 = tv("Sin mantenimientos programados", 13, MUTED, false);
            t1.setGravity(Gravity.CENTER);
            e.addView(t1, new LinearLayout.LayoutParams(-1, -2));
            TextView t2 = tv("Toca para agregar uno", 12, RED, true);
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, (int) (6 * s), 0, 0);
            e.addView(t2, new LinearLayout.LayoutParams(-1, -2));
            place(faroMaintBox, e, 0, 0, faroMaintW, faroMaintH);
            return;
        }
        int fit = Math.max(1, (int) (faroMaintH / 31));
        float rowH = faroMaintH / fit, lw = Math.min(124, faroMaintW * 0.36f);
        for (int k = 0; k < items.size() && k < fit; k++) {
            double f = items.get(k)[0];
            final int idx = (int) items.get(k)[1];
            String name, left, ic;
            if (idx < 0) {
                float lk = oilNextKm() - odoKm();
                name = I18n.t("Cambio de aceite");
                left = Math.round(toUnit(lk)) <= 0 ? I18n.t("¡Ya toca!") : fmtDist(lk);
                ic = "water_drop";
            } else {
                org.json.JSONObject o = l.get(idx);
                name = taskName(o);
                left = f <= 0 ? I18n.t("¡Ya toca!") : taskLeftText(o);
                ic = taskIcon(o);
            }
            int uc = Faro.urgency(f, LIGHT);
            FrameLayout row = new FrameLayout(this);
            View bar = new View(this);
            bar.setBackground(round(uc, 2));
            place(row, bar, 4, rowH * 0.16f, 4, rowH * 0.68f);
            ImageView iv = icon(ic, 16);
            iv.setAlpha(0.85f);
            place(row, iv, 16, (rowH - 16) / 2, 16, 16);
            TextView nt = tv(name, 14, TEXT, false);
            nt.setTypeface(Typeface.DEFAULT);
            place(row, nt, 40, (rowH - 18) / 2, faroMaintW - 40 - lw - 8, 18);
            TextView lt = tv(left, 13, f < 0.15 ? uc : MUTED, f < 0.15);
            lt.setGravity(Gravity.RIGHT);
            place(row, lt, faroMaintW - lw - 4, (rowH - 17) / 2, lw, 17);
            place(faroMaintBox, row, 0, k * rowH, faroMaintW, rowH);
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (idx < 0) oilMenu(); else taskMenu(idx);
            }});
        }
    }

    /** Reproductor horizontal: anterior, reproducir y siguiente; carátula; canción y artista; la línea de tiempo. */
    private void buildFaroPlayer(float x, float y, float w, float hh) {
        final FrameLayout card = faroCard(x, y, w, hh);
        float cy = hh / 2, x0 = 14;
        FrameLayout prev = faroIconBtn(Faro.I_PREV, 44, 22, 0), next = faroIconBtn(Faro.I_NEXT, 44, 22, 0);
        FrameLayout play = new FrameLayout(this);
        playIc = new ImageView(this);
        playIc.setImageBitmap(asset("ic/play_arrow.png"));
        playIc.setColorFilter(TEXT);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams((int) (34 * s), (int) (34 * s));
        plp.gravity = Gravity.CENTER;
        play.addView(playIc, plp);
        place(card, prev, x0, cy - 22, 44, 44);
        place(card, play, x0 + 48, cy - 24, 48, 48);
        place(card, next, x0 + 100, cy - 22, 44, 44);
        float ax = x0 + 100 + 44 + 16, as = hh - 20;
        playerArt = new PlayerArt(this, s, asset("ic/music_note.png"));
        playerArt.setTile(LIGHT ? 0x14000000 : 0x1FFFFFFF);
        place(card, playerArt, ax, (hh - as) / 2, as, as);
        float tlW = Math.max(150, Math.min(460, w * 0.36f)), tlX = w - 18 - tlW;
        float tx = ax + as + 14, tw = Math.max(60, tlX - 20 - tx);
        trackTv = tv("Nada sonando", 16, TEXT, true);
        place(card, trackTv, tx, cy - 20, tw, 20);
        artistTv = tv("Radio, Bluetooth o USB", 13, MUTED, false);
        place(card, artistTv, tx, cy + 3, tw, 17);
        sourceTv = tv("", 10, MUTED, true);
        place(card, sourceTv, tlX, cy - 18, tlW * 0.5f, 14);
        timePosTv = tv("", 12, MUTED, false);
        timePosTv.setGravity(Gravity.RIGHT);
        place(card, timePosTv, tlX + tlW * 0.4f, cy - 19, tlW * 0.6f, 16);
        faroBar = new Faro.Timeline(this, s, Faro.track(LIGHT), TEXT);
        place(card, faroBar, tlX - 5, cy + 3, tlW + 10, 18);
        final View.OnClickListener goPrev = mediaClick(KeyEvent.KEYCODE_MEDIA_PREVIOUS), goNext = mediaClick(KeyEvent.KEYCODE_MEDIA_NEXT);
        prev.setOnClickListener(goPrev);
        play.setOnClickListener(mediaClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        next.setOnClickListener(goNext);
        card.setOnTouchListener(new View.OnTouchListener() {
            float x0t, y0t;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0t = e.getX(); y0t = e.getY(); return false; }
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return false;
                float dx = e.getX() - x0t, dy = e.getY() - y0t;
                if (Math.abs(dx) < 50 * s || Math.abs(dx) < 2 * Math.abs(dy)) return false;
                MotionEvent c = MotionEvent.obtain(e);
                c.setAction(MotionEvent.ACTION_CANCEL);
                v.onTouchEvent(c);
                c.recycle();
                (dx < 0 ? goNext : goPrev).onClick(v);
                return true;
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { showPlayerDiag(); return true; } });
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openMusicApp(); } });
        renderTrack();
    }

    /** Barra de apps compacta y centrada: cada función en un recuadro redondeado; las apps con su propio ícono. */
    private void buildFaroDock() {
        final int n = dockCount;
        int isz = prefs.getInt("dock_icon_size", 1);
        float ik = isz == 0 ? 0.86f : isz == 2 ? 1.12f : 1f;
        float pillH = premPillH;
        // centrada en el reproductor y sin pasarse de sus bordes: crece desde el centro hacia los lados
        float areaX = faroPlX, areaW = faroPlW;
        float itemW = Math.min(vertical ? 66 : 62, (areaW - 16) / n), pillW = itemW * n + 16;
        float px = areaX + (areaW - pillW) / 2, py = dockTop;
        View bg = new View(this);
        bg.setBackground(new Faro.Card(LIGHT, Math.min(20, pillH * 0.36f) * s, 8 * s, s));
        place(board, bg, px - 8, py - 8, pillW + 16, pillH + 16);
        float tsz = Math.min(Math.min(itemW - 14, pillH - 12) * ik, pillH - 6);
        final boolean appStyle = "apps".equals(prefs.getString("dock_style", "circle"));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String t = dockTarget(i);
            Drawable appIc = t.startsWith("app:") ? appIcon(t.substring(4)) : appStyle ? fnAppIcon(t.substring(3)) : null;
            FrameLayout b = new FrameLayout(this);
            View inner;
            if (appIc != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(appIc);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                inner = iv;
            } else {
                String id = t.startsWith("fn:") ? t.substring(3) : dockDefault(i);
                FrameLayout tile = new FrameLayout(this);
                GradientDrawable d = round(Faro.tile(LIGHT), tsz * 0.26f);
                if (LIGHT) d.setStroke(Math.max(1, (int) s), 0x14000000);
                tile.setBackground(d);
                View g;
                float gs;
                if ("apps".equals(id)) { g = new Faro.Icon(this, Faro.I_APPS, Faro.pillIcon(LIGHT)); gs = tsz * 0.52f; }
                else {
                    ImageView iv = icon(dockFn(id)[2], tsz * 0.5f);
                    iv.setColorFilter(Faro.pillIcon(LIGHT));
                    g = iv;
                    gs = tsz * 0.5f;
                }
                FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams((int) (gs * s), (int) (gs * s));
                glp.gravity = Gravity.CENTER;
                tile.addView(g, glp);
                inner = tile;
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (tsz * s), (int) (tsz * s));
            lp.gravity = Gravity.CENTER;
            b.addView(inner, lp);
            place(board, b, px + 8 + itemW * i, py, itemW, pillH);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { runDock(idx); } });
            b.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { dockMenu(idx, null); return true; } });
        }
    }

    /**
     * Arriba del mapa: buscador discreto a la izquierda (con la navegación integrada abre «Ir a…»; si no, la app de
     * navegación) y, a la derecha, centrar (zoom automático) y acercar/alejar. Sin GPS el buscador muestra el estado.
     */
    private void buildFaroMapTop(FrameLayout card) {
        FrameLayout box = new FrameLayout(this);
        place(card, box, 0, 0, mapW, mapH);
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pd = round(LIGHT ? 0xF2FFFFFF : 0xEB1C1E23, 12);
        if (LIGHT) pd.setStroke(Math.max(1, (int) s), 0x14000000);
        pill.setBackground(pd);
        pill.setPadding((int) (12 * s), 0, (int) (12 * s), 0);
        faroPillIc = new Faro.Icon(this, Faro.I_SEARCH, TEXT);
        pill.addView(faroPillIc, new LinearLayout.LayoutParams((int) (18 * s), (int) (18 * s)));
        faroPillTv = tv("", 14, TEXT, false);
        faroPillTv.setPadding((int) (10 * s), 0, 0, 0);
        pill.addView(faroPillTv, new LinearLayout.LayoutParams(0, -2, 1f));
        float pw = Math.min(250, Math.max(140, mapW - 12 - 12 - 40 - 12));
        place(box, pill, 12, 12, pw, 40);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (liveMap == null || !liveMap.hasFix()) { fixGps(); return; }
            if ("internal".equals(prefs.getString("nav_app", null))) openNavSearch(); else openNavApp();
        }});
        pill.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { pickFor("nav_app", "Navegación"); return true; } });
        // centrar + acercar / alejar
        FrameLayout loc = faroIconBtn(Faro.I_LOCATE, 40, 19, LIGHT ? 0xF2FFFFFF : 0xEB1C1E23);
        place(box, loc, mapW - 12 - 40, 12, 40, 40);
        loc.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (liveMap != null) liveMap.autoZoom(); } });
        LinearLayout zc = new LinearLayout(this);
        zc.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable zd = round(LIGHT ? 0xF2FFFFFF : 0xEB1C1E23, 12);
        if (LIGHT) zd.setStroke(Math.max(1, (int) s), 0x14000000);
        zc.setBackground(zd);
        FrameLayout zin = faroIconBtn(Faro.I_PLUS, 40, 18, 0), zout = faroIconBtn(Faro.I_MINUS, 40, 18, 0);
        zc.addView(zin, new LinearLayout.LayoutParams((int) (40 * s), (int) (42 * s)));
        zc.addView(zout, new LinearLayout.LayoutParams((int) (40 * s), (int) (42 * s)));
        place(box, zc, mapW - 12 - 40, 60, 40, 84);
        zin.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (liveMap != null) liveMap.zoomBy(1); } });
        zout.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (liveMap != null) liveMap.zoomBy(-1); } });
        if (mapH < 200) zc.setVisibility(View.GONE);
        mapTagTv = tv("", 13, TEXT, true);   // no se muestra: el estado del GPS va en el buscador
        navSearchBtn = null;
        mapTagBox = box;
    }

    /** Faro: botón de Android Auto / CarPlay en un recuadro neutro con borde fino; el acento solo en el ícono. */
    private void faroProj(View b, boolean accentIcon) {
        if (LIGHT && b.getBackground() instanceof GradientDrawable) ((GradientDrawable) b.getBackground()).setStroke(Math.max(1, (int) s), 0x14000000);
        if (b instanceof LinearLayout && ((LinearLayout) b).getChildAt(0) instanceof ImageView) {
            ImageView ic = (ImageView) ((LinearLayout) b).getChildAt(0);
            if (accentIcon) ic.setColorFilter(RED); else ic.setColorFilter(TEXT);
        }
    }

    /** El texto del buscador: el estado del GPS mientras no hay ubicación; luego «Ir a…» o «Navegar». */
    private void updateFaroPill() {
        if (faroPillTv == null) return;
        boolean fix = liveMap != null && liveMap.hasFix();
        if (!fix) {
            faroPillTv.setText(mapTagTv != null ? mapTagTv.getText() : "");
            faroPillTv.setTextColor(MUTED);
            faroPillIc.set(Faro.I_LOCATE);
        } else {
            faroPillTv.setText(I18n.t("internal".equals(prefs.getString("nav_app", null)) ? "Ir a…" : "Navegar"));
            faroPillTv.setTextColor(TEXT);
            faroPillIc.set(Faro.I_SEARCH);
        }
    }

    // ───────────────────────── tema premium «Carbono» (2.33) ─────────────────────────
    // Horizontal: barra de arriba (Wi-Fi/Bluetooth, Android Auto y CarPlay, hora y fecha, Ajustes); barra de apps vertical
    // tipo píldora a la izquierda; tarjeta del vehículo (velocímetro grande, el carro y los dos mantenimientos más
    // cercanos); el mapa en su tarjeta con el reproductor y el clima flotando abajo. Vertical: el vehículo arriba (aro,
    // mantenimientos y carro en tres columnas), el mapa, reproductor + clima en una fila y la barra abajo. Reusa las
    // piezas de Faro (tarjeta, velocímetro, buscador del mapa, línea de tiempo, clima) con los colores de Carbono.

    private float cbTopH, cbDockX, cbDockY, cbDockW, cbDockH, cbVX, cbVY, cbVW, cbVH, cbOvY, cbOvH, cbPlX, cbPlW, cbWxX, cbWxW;
    private FrameLayout carbMaintBox;
    /** Caja del tamaño del mapa donde flotan el reproductor y el clima de Carbono (solo mientras se arma). */
    private FrameLayout carbOv;
    private final List<View> carbOvViews = new ArrayList<View>();
    /** [0] mostrar, [1] esconder el reproductor y el clima de Carbono. */
    private Runnable[] carbSetOpen;

    /** Tarjeta de Carbono dentro de la caja flotante del mapa (coordenadas del tablero). */
    private FrameLayout carbOvCard(float x, float y, float w, float hh) {
        if (carbOv == null) return faroCard(x, y, w, hh);
        float m = 8;
        FrameLayout outer = new FrameLayout(this);
        outer.setBackground(new Faro.Card(LIGHT, 18 * s, m * s, s));
        place(carbOv, outer, x - mapX - m, y - mapY - m, w + 2 * m, hh + 2 * m);
        FrameLayout inner = new FrameLayout(this);
        place(outer, inner, m, m, w, hh);
        carbOvViews.add(outer);
        return inner;
    }

    /**
     * Flechita sobre el reproductor y el clima de Carbono: los esconde hacia abajo para ver todo el mapa (como la de
     * Android Auto y CarPlay en los otros temas). Tocar o deslizar la flechita los muestra o esconde; deslizar el
     * reproductor hacia abajo también los esconde. Se recuerda («carb_ov_open»).
     */
    private void buildCarbOvHandle() {
        final FrameLayout handle = new FrameLayout(this);
        handle.setBackground(round(Faro.glass(LIGHT), 11));
        final ImageView chev = icon("chevron_right", 18);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams((int) (18 * s), (int) (18 * s));
        clp.gravity = Gravity.CENTER;
        handle.addView(chev, clp);
        place(carbOv, handle, (mapW - 64) / 2, cbOvY - mapY - 32, 64, 24);
        final float shift = (cbOvH + 14) * s;
        final List<View> moving = new ArrayList<View>(carbOvViews);
        moving.add(handle);
        final boolean open0 = prefs.getBoolean("carb_ov_open", true);
        for (View v : moving) v.setTranslationY(open0 ? 0 : shift);
        chev.setRotation(open0 ? 90 : -90);
        carbSetOpen = new Runnable[2];
        for (int k = 0; k < 2; k++) {
            final boolean show = k == 0;
            carbSetOpen[k] = new Runnable() { public void run() {
                prefs.edit().putBoolean("carb_ov_open", show).apply();
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
                boolean isOpen = prefs.getBoolean("carb_ov_open", true);
                if (d < -12 * s) carbSetOpen[0].run();
                else if (d > 12 * s) carbSetOpen[1].run();
                else carbSetOpen[isOpen ? 1 : 0].run();
                return true;
            }
        });
    }
    private float carbMaintW, carbMaintH;
    /** Íconos de las horas del clima (Faro: 22 y 8 de aire; Carbono: más chicos para caber en la tarjeta flotante). */
    private float faroHrIc = 22, faroHrGap = 8;

    private void carbMetrics() {
        float W0 = premW(), H = boardH, m = 12;
        premHeadH = 0;
        premWide = false;
        cbTopH = 38;
        float top = 8 + cbTopH + 6;
        boolean wx = prefs.getBoolean("weather_on", true);
        if (vertical) {
            premPillH = noDock ? 0 : 66;
            cbDockX = m; cbDockW = W0 - 2 * m; cbDockH = premPillH; cbDockY = H - m - cbDockH;
            dockTop = noDock ? H : cbDockY;
            cbVX = m; cbVY = top; cbVW = W0 - 2 * m; cbVH = shortVertical ? 268 : 332;
            float bottom = noDock ? H - m : cbDockY - m;
            cbOvH = showPlayer || wx ? (shortVertical ? 126 : 134) : 0;
            mapX = m; mapY = cbVY + cbVH + m; mapW = W0 - 2 * m;
            mapH = Math.max(120, bottom - mapY);
            cbOvY = mapY + mapH - 12 - cbOvH;
            float gap = 10, rowW = mapW - 24;
            cbPlX = mapX + 12; cbPlW = !wx ? rowW : !showPlayer ? 0 : (rowW - gap) * 0.58f;
            cbWxX = cbPlX + (cbPlW > 0 ? cbPlW + gap : 0); cbWxW = wx ? mapX + 12 + rowW - cbWxX : 0;
        } else {
            premPillH = noDock ? 0 : 62;
            cbDockW = premPillH; cbDockX = m;
            cbVX = noDock ? m : m + cbDockW + m; cbVY = top; cbVW = 392 + Math.min(110, Math.max(0, ex) / 4f); cbVH = H - m - top;
            int n = Math.max(1, dockCount);
            cbDockH = Math.min(H - top - m, n * 58 + 16);
            cbDockY = top + (H - top - m - cbDockH) / 2;
            dockTop = H;
            mapX = cbVX + cbVW + m; mapY = top; mapW = W0 - m - mapX; mapH = H - m - top;
            cbOvH = showPlayer || wx ? 126 : 0;
            cbOvY = mapY + mapH - 12 - cbOvH;
            float gap = 10, rowW = mapW - 24;
            cbPlX = mapX + 12; cbPlW = !wx ? rowW : !showPlayer ? 0 : (rowW - gap) * 0.6f;
            cbWxX = cbPlX + (cbPlW > 0 ? cbPlW + gap : 0); cbWxW = wx ? mapX + 12 + rowW - cbWxX : 0;
        }
        cardH = mapH; rowY = mapY; insL = insR = 0;
        carX = audX = 16; audW = 248;
    }

    private void buildCarbono() {
        premHeadViews.clear();
        faroGauge = null; faroBar = null; faroWxIc = null; faroMaintBox = null; faroPillTv = null; faroPillIc = null;
        carbMaintBox = null;
        carbSetOpen = null;
        buildCarbTop();
        // en claro, el área del mapa con la misma sombra que las tarjetas (el mapa la tapa por dentro)
        if (LIGHT) {
            View sh = new View(this);
            sh.setBackground(new Faro.Card(true, 22 * s, 8 * s, s));
            place(board, sh, mapX - 8, mapY - 8, mapW + 16, mapH + 16);
        }
        buildAaCard();
        buildCarbVehicle();
        // el reproductor y el clima flotan sobre el mapa dentro de una caja del tamaño del mapa: al esconderlos bajan y
        // quedan recortados por su borde (la caja no toma toques: el mapa sigue respondiendo)
        carbOv = new FrameLayout(this);
        place(board, carbOv, mapX, mapY, mapW, mapH);
        carbOvViews.clear();
        if (cbPlW > 0) buildCarbPlayer(cbPlX, cbOvY, cbPlW, cbOvH);
        else { trackTv = null; artistTv = null; sourceTv = null; timePosTv = null; playerArt = null; playIc = null; }
        if (cbWxW > 0) buildCarbWeather(cbWxX, cbOvY, cbWxW, cbOvH);
        else { faroWxIc = null; panelWx = null; }
        if (cbOvH > 0) buildCarbOvHandle();
        carbOv = null;
        if (!noDock) buildCarbDock();
        updateOilBadge();
    }

    /** Píldora de Android Auto o CarPlay para la barra de arriba (el nombre de la app elegida, chiquito, debajo). */
    private LinearLayout carbProj(String ic, String title, boolean accent) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable d = round(Faro.tile(LIGHT), cbTopH / 2);
        d.setStroke(Math.max(1, (int) s), LIGHT ? 0x14000000 : 0x14FFFFFF);
        b.setBackground(d);
        b.setPadding((int) (12 * s), 0, (int) (14 * s), 0);
        ImageView pic = icon(ic, 20);
        pic.setColorFilter(accent ? RED : TEXT);
        b.addView(pic, new LinearLayout.LayoutParams((int) (20 * s), (int) (20 * s)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (9 * s), 0, 0, 0);
        TextView t = tv(title, 13, TEXT, true);
        col.addView(t);
        TextView sub = tv("", 10, MUTED, false);
        sub.setPadding(0, (int) (2 * s), 0, 0);
        sub.setMaxWidth((int) (118 * s));
        col.addView(sub);
        b.addView(col, new LinearLayout.LayoutParams(-2, -2));
        b.setTag(sub);
        return b;
    }

    /** Barra de arriba: Wi-Fi y Bluetooth, Android Auto y CarPlay a la izquierda; la hora y la fecha y Ajustes a la derecha. */
    private void buildCarbTop() {
        float W0 = premW(), x = 12, w = W0 - 24, y = 8;
        topSpeedTv = null; topOilTv = null; topIconName = null; weatherBox = null;
        topNavPill = null; topNavIc = null; topNavDistTv = null;
        greetTv = tv("", 13, MUTED, false);   // no se muestra (el reloj lo actualiza)
        LinearLayout left = new LinearLayout(this);
        left.setGravity(Gravity.CENTER_VERTICAL);
        wifiIc = null;
        btIc = null;
        if (prefs.getBoolean("status_icons", true)) {
            wifiIc = statusIcon(left, "wifi", new Runnable() { public void run() { toggleWifi(); } },
                new Runnable() { public void run() { openSettingsPage(Settings.ACTION_WIFI_SETTINGS, new Runnable() { public void run() { openSystemSettings(); } }); } });
            btIc = statusIcon(left, "bluetooth", new Runnable() { public void run() { toggleBt(); } },
                new Runnable() { public void run() {
                    if (clwBt) openSlot(1);
                    else openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS, new Runnable() { public void run() { openSlot(1); } });
                } });
        }
        place(board, left, x, y, w * 0.3f, cbTopH);
        // Android Auto y CarPlay: siempre a la vista en la barra, centrados sobre la tarjeta del vehículo en horizontal
        LinearLayout proj = new LinearLayout(this);
        proj.setGravity(vertical ? Gravity.LEFT | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        String cm = prefs.getString("conn_buttons", "both");
        aaBtnTv = null; aaSubTv = null; cpSubTv = null;
        if ("both".equals(cm) || "aa".equals(cm)) {
            LinearLayout aa = carbProj("navigation", "Android Auto", true);
            aaBtnTv = (TextView) aa.getTag();
            aaSubTv = aaBtnTv;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, (int) (cbTopH * s));
            proj.addView(aa, lp);
            aa.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openAndroidAuto(); } });
            aa.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { chooseConnection(null); return true; } });
        }
        if ("both".equals(cm) || "cp".equals(cm)) {
            LinearLayout cp = carbProj("phone_iphone", "CarPlay", false);
            cpSubTv = (TextView) cp.getTag();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, (int) (cbTopH * s));
            lp.leftMargin = (int) (8 * s);
            proj.addView(cp, lp);
            cp.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openCarPlay(); } });
            cp.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { pickFor("carplay", "CarPlay"); return true; } });
        }
        float icEnd = x + left.getChildCount() * 36 + 8;
        if (vertical) place(board, proj, icEnd, y, w * 0.64f - icEnd, cbTopH);
        else {
            float px0 = Math.max(cbVX, icEnd), px1 = cbVX + cbVW;
            place(board, proj, px0, y, px1 - px0, cbTopH);
        }

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        addCarData(right);
        timeTv = tv("", 20, TEXT, true);
        right.addView(timeTv);
        dateTv = tv("", 13, MUTED, false);
        dateTv.setPadding((int) (10 * s), 0, (int) (4 * s), 0);
        right.addView(dateTv);
        premTopRow = right;
        if (noDock) {
            FrameLayout apps = faroIconBtn(Faro.I_APPS, 34, 16, Faro.tile(LIGHT));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams((int) (34 * s), (int) (34 * s));
            alp.leftMargin = (int) (10 * s);
            right.addView(apps, alp);
            apps.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openDrawer(); } });
        }
        FrameLayout gear = circleIcon("settings", 34, 18, 0);
        GradientDrawable gd = round(Faro.tile(LIGHT), 17);
        gd.setStroke(Math.max(1, (int) s), LIGHT ? 0x14000000 : 0x14FFFFFF);
        gear.setBackground(gd);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams((int) (34 * s), (int) (34 * s));
        glp.leftMargin = (int) (10 * s);
        right.addView(gear, glp);
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openSettings(); } });
        place(board, right, x + w * 0.36f, y, w * 0.64f, cbTopH);
    }

    /**
     * Tarjeta del vehículo. Horizontal: el velocímetro a la izquierda y el carro grande a la derecha, con los dos
     * mantenimientos más cercanos abajo a todo el ancho. Vertical: velocímetro, mantenimientos uno sobre otro y el carro.
     */
    private void buildCarbVehicle() {
        float w = cbVW, hh = cbVH, in = 16;
        FrameLayout card = faroCard(cbVX, cbVY, w, hh);
        carTv = tv(prefs.getString("car_name", "Mi vehículo"), 13, MUTED, true);   // no se muestra
        chipTv = null;
        oilValTv = null; oilBar = null; oilBarBg = null; oilLblTv = null; oilIc = null;
        carGauge = null; mapGauge = null; faroGauge = null;
        String col = Carbono.car(prefs.getString("carb_car", "silver"));
        // el carro «sale» de la tarjeta: su parte de atrás queda cortada por el borde de arriba
        final float CUT = 0.2f;
        float speedH = carSpeed ? 84 : 0, mh, mx, my, mw, sx, sy, sw, cw, chF, carCx;
        boolean stack;
        if (vertical) {
            // izquierda: velocidad arriba y los dos mantenimientos abajo; derecha: el carro
            chF = (hh - 6) / (1 - CUT);
            cw = chF * Carbono.CAR_ASPECT;
            float colW = cw + 2 * in;
            carCx = w - colW / 2;
            mx = in; mw = w - colW - in; mh = Math.min(124, hh * 0.42f); my = hh - in - mh;
            sx = in; sy = in; sw = mw;
            if (!carSpeed) { mh = hh - 2 * in; my = in; }
            stack = false;
        } else {
            mh = Math.max(98, Math.min(116, hh * 0.21f));
            my = hh - in - mh; mx = in; mw = w - 2 * in;
            sy = my - 10 - speedH; sx = in + 4; sw = w - 2 * in;
            float vis = sy - 4;
            chF = vis / (1 - CUT);
            cw = Math.min(w * 0.66f, chF * Carbono.CAR_ASPECT);
            chF = cw / Carbono.CAR_ASPECT;
            carCx = w / 2;
            stack = false;
        }
        Carbono.Car car = new Carbono.Car(this, col, LIGHT);
        float vw = cw * (1 + 2 * Carbono.SH_X * 0.75f), vh = chF * (1 + 2 * Carbono.SH_Y * 0.75f);
        float top = -chF * CUT;
        place(card, car, carCx - vw / 2, top - (vh - chF) / 2, vw, vh);
        car.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSettings(C_VEHICLE); } });
        car.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { chooseCarbCar(); return true; } });
        // velocímetro digital: «Velocidad» (y el GPS/OBD) y el número grande con su unidad
        if (carSpeed) {
            LinearLayout lab = new LinearLayout(this);
            lab.setGravity(Gravity.CENTER_VERTICAL);
            lab.addView(tv("Velocidad", 14, MUTED, false));
            gpsDot = new View(this);
            gpsDot.setBackground(round(MUTED, 4));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int) (7 * s), (int) (7 * s));
            dlp.leftMargin = (int) (12 * s);
            lab.addView(gpsDot, dlp);
            gpsTv = tv("GPS", 11, MUTED, false);
            gpsTv.setPadding((int) (5 * s), 0, 0, 0);
            lab.addView(gpsTv);
            place(card, lab, sx, sy, sw, 20);
            LinearLayout num = new LinearLayout(this);
            num.setGravity(Gravity.BOTTOM);
            speedTv = tv("0", 58, TEXT, false);
            speedTv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            speedTv.setIncludeFontPadding(false);
            num.addView(speedTv);
            TextView unit = tv(useMph() ? "mph" : "km/h", 17, MUTED, false);
            unit.setPadding((int) (8 * s), 0, 0, (int) (8 * s));
            num.addView(unit);
            place(card, num, sx - 3, sy + 22, sw, 62);
        } else {
            speedTv = tv("0", 12, TEXT, true);   // no se muestra
            gpsDot = new View(this);
            gpsTv = tv("GPS", 11, MUTED, false);
        }
        carbMaintBox = new FrameLayout(this);
        carbMaintW = mw; carbMaintH = mh;
        carbMaintBox.setTag(stack ? "v" : "h");
        place(card, carbMaintBox, mx, my, mw, mh);
        renderCarbMaint();
    }

    /** Elegir el color del carro de Carbono (presión larga sobre el carro o Ajustes → Pantalla de inicio). */
    private void chooseCarbCar() {
        final String[] cols = Carbono.CAR_COLORS;
        String[] names = new String[cols.length];
        for (int i = 0; i < cols.length; i++) names[i] = I18n.t(Carbono.CAR_NAMES[i]);
        int sel = Math.max(0, java.util.Arrays.asList(cols).indexOf(Carbono.car(prefs.getString("carb_car", "silver"))));
        new TB(this, dlg()).raw()
            .setTitle(I18n.t("Color del carro"))
            .setSingleChoiceItems(names, sel, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                d.dismiss();
                prefs.edit().putString("carb_car", cols[which]).apply();
                rebuild();
                if (settingsView != null) showSettings(settingsCat);
            }})
            .show();
    }

    /** Los dos mantenimientos más cercanos (aceite + los de Ajustes → Mantenimientos), del más urgente al siguiente. */
    private void renderCarbMaint() {
        if (carbMaintBox == null) return;
        carbMaintBox.removeAllViews();
        final List<org.json.JSONObject> l = maint();
        List<double[]> items = new ArrayList<double[]>();   // {lo que falta, índice} (índice -1 = aceite)
        if (oilOn()) items.add(new double[]{(oilNextKm() - odoKm()) / Math.max(1, prefs.getFloat("oil_interval_km", 5000)), -1});
        for (int i = 0; i < l.size(); i++) items.add(new double[]{taskFrac(l.get(i)), i});
        java.util.Collections.sort(items, new java.util.Comparator<double[]>() {
            public int compare(double[] a, double[] b) { return Double.compare(a[0], b[0]); }
        });
        boolean stack = "v".equals(carbMaintBox.getTag());
        float gap = 10, cw = stack ? carbMaintW : (carbMaintW - gap) / 2, ch = stack ? (carbMaintH - gap) / 2 : carbMaintH;
        for (int k = 0; k < 2; k++) {
            float x = stack ? 0 : k * (cw + gap), y = stack ? k * (ch + gap) : 0;
            if (k >= items.size()) {
                // sin mantenimiento (o solo uno): una tarjeta para agregar
                FrameLayout e = carbInset();
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER);
                TextView t1 = tv(k == 0 ? "Sin mantenimientos programados" : "Próximo mantenimiento", 12, MUTED, false);
                t1.setGravity(Gravity.CENTER);
                t1.setSingleLine(false);
                t1.setMaxLines(2);
                col.addView(t1, new LinearLayout.LayoutParams(-1, -2));
                TextView t2 = tv("Toca para agregar uno", 12, RED, true);
                t2.setGravity(Gravity.CENTER);
                t2.setPadding(0, (int) (6 * s), 0, 0);
                col.addView(t2, new LinearLayout.LayoutParams(-1, -2));
                place(e, col, 8, 0, cw - 16, ch);
                place(carbMaintBox, e, x, y, cw, ch);
                e.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSettings(C_MAINT); } });
                continue;
            }
            double f = items.get(k)[0];
            final int idx = (int) items.get(k)[1];
            String name, left, ic;
            if (idx < 0) {
                float lk = oilNextKm() - odoKm();
                name = I18n.t("Cambio de aceite");
                left = Math.round(toUnit(lk)) <= 0 ? I18n.t("¡Ya toca!") : fmtDist(lk);
                ic = "water_drop";
            } else {
                org.json.JSONObject o = l.get(idx);
                name = taskName(o);
                left = f <= 0 ? I18n.t("¡Ya toca!") : taskLeftText(o);
                ic = taskIcon(o);
            }
            int uc = Faro.urgency(f, LIGHT);
            FrameLayout c = carbInset();
            float p = 12;
            ImageView iv = icon(ic, 20);
            iv.setColorFilter(TEXT);
            iv.setAlpha(0.9f);
            place(c, iv, p, p, 20, 20);
            View dot = new View(this);
            dot.setBackground(round(uc, 4));
            place(c, dot, cw - p - 8, p + 6, 8, 8);
            TextView nt = tv(name, 14, TEXT, true);
            nt.setSingleLine(false);
            nt.setMaxLines(ch >= 100 ? 2 : 1);
            float ny = p + 20 + 8, nh = ch >= 100 ? 36 : 18;
            place(c, nt, p, ny, cw - 2 * p, nh);
            TextView lt = tv(left, 12, f < 0.15 ? uc : MUTED, f < 0.15);
            place(c, lt, p, ch - p - 8 - 16, cw - 2 * p, 16);
            // barra de lo que falta (llena = recién hecho; vacía = ya toca)
            View bg = new View(this);
            bg.setBackground(round(Faro.track(LIGHT), 2));
            place(c, bg, p, ch - p - 4, cw - 2 * p, 4);
            View fg = new View(this);
            fg.setBackground(round(uc, 2));
            float fr = (float) Math.max(0.04, Math.min(1, f));
            place(c, fg, p, ch - p - 4, (cw - 2 * p) * fr, 4);
            place(carbMaintBox, c, x, y, cw, ch);
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (idx < 0) oilMenu(); else taskMenu(idx); } });
        }
    }

    /** Recuadro interior de Carbono (mantenimientos): un poco más claro que la tarjeta, con borde fino. */
    private FrameLayout carbInset() {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable d = round(Carbono.inset(LIGHT), 16);
        d.setStroke(Math.max(1, (int) s), Carbono.insetEdge(LIGHT));
        f.setBackground(d);
        return f;
    }

    /**
     * Reproductor (flota sobre el mapa en horizontal): carátula, canción y artista arriba; anterior, reproducir y
     * siguiente con la línea de tiempo abajo. Deslizar a los lados cambia de canción; presión larga = diagnóstico.
     */
    private void buildCarbPlayer(float x, float y, float w, float hh) {
        final FrameLayout card = carbOvCard(x, y, w, hh);
        float p = 12, as = Math.min(56, hh * 0.46f);
        playerArt = new PlayerArt(this, s, asset("ic/music_note.png"));
        playerArt.setTile(LIGHT ? 0x14000000 : 0x1FFFFFFF);
        place(card, playerArt, p, p, as, as);
        float tx = p + as + 12, tw = w - tx - p;
        trackTv = tv("Nada sonando", 15, TEXT, true);
        place(card, trackTv, tx, p + as / 2 - 19, tw, 19);
        artistTv = tv("Radio, Bluetooth o USB", 12, MUTED, false);
        place(card, artistTv, tx, p + as / 2 + 3, tw, 16);
        sourceTv = tv("", 10, MUTED, true);   // no se muestra: no hay espacio para la fuente
        float cy = p + as + (hh - p - as) / 2;
        FrameLayout prev = faroIconBtn(Faro.I_PREV, 38, 20, 0), next = faroIconBtn(Faro.I_NEXT, 38, 20, 0);
        FrameLayout play = new FrameLayout(this);
        GradientDrawable pd = new GradientDrawable();
        pd.setShape(GradientDrawable.OVAL);
        pd.setColor(LIGHT ? 0xFF1C1E23 : 0xFFF2F3F5);
        play.setBackground(pd);
        playIc = new ImageView(this);
        playIc.setImageBitmap(asset("ic/play_arrow.png"));
        playIc.setColorFilter(LIGHT ? 0xFFFFFFFF : 0xFF15171B);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams((int) (24 * s), (int) (24 * s));
        plp.gravity = Gravity.CENTER;
        play.addView(playIc, plp);
        place(card, prev, p - 4, cy - 19, 38, 38);
        place(card, play, p + 36, cy - 20, 40, 40);
        place(card, next, p + 80, cy - 19, 38, 38);
        float tlX = p + 128, tlW = w - tlX - p;
        timePosTv = tv("", 11, MUTED, false);
        timePosTv.setGravity(Gravity.RIGHT);
        place(card, timePosTv, tlX, cy - 17, tlW, 14);
        faroBar = new Faro.Timeline(this, s, Faro.track(LIGHT), TEXT);
        place(card, faroBar, tlX - 5, cy - 1, tlW + 10, 18);
        if (tlW < 60) { timePosTv.setVisibility(View.GONE); faroBar.setVisibility(View.GONE); }
        final View.OnClickListener goPrev = mediaClick(KeyEvent.KEYCODE_MEDIA_PREVIOUS), goNext = mediaClick(KeyEvent.KEYCODE_MEDIA_NEXT);
        prev.setOnClickListener(goPrev);
        play.setOnClickListener(mediaClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        next.setOnClickListener(goNext);
        card.setOnTouchListener(new View.OnTouchListener() {
            float x0t, y0t;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0t = e.getX(); y0t = e.getY(); return false; }
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return false;
                float dx = e.getX() - x0t, dy = e.getY() - y0t;
                if (dy > 40 * s && dy > 2 * Math.abs(dx) && carbSetOpen != null) {
                    MotionEvent c = MotionEvent.obtain(e);
                    c.setAction(MotionEvent.ACTION_CANCEL);
                    v.onTouchEvent(c);
                    c.recycle();
                    carbSetOpen[1].run();
                    return true;
                }
                if (Math.abs(dx) < 50 * s || Math.abs(dx) < 2 * Math.abs(dy)) return false;
                MotionEvent c = MotionEvent.obtain(e);
                c.setAction(MotionEvent.ACTION_CANCEL);
                v.onTouchEvent(c);
                c.recycle();
                (dx < 0 ? goNext : goPrev).onClick(v);
                return true;
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { showPlayerDiag(); return true; } });
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openMusicApp(); } });
        renderTrack();
    }

    /** Clima compacto: ciudad y máx/mín arriba, temperatura con su ícono y estado, y las próximas horas abajo. */
    private void buildCarbWeather(float x, float y, float w, float hh) {
        FrameLayout card = carbOvCard(x, y, w, hh);
        float m = 12;
        faroCityTv = tv("Clima", 12, MUTED, true);
        place(card, faroCityTv, m, 10, w * 0.6f - m, 16);
        faroHiLoTv = tv("", 11, MUTED, false);
        faroHiLoTv.setGravity(Gravity.RIGHT);
        place(card, faroHiLoTv, w * 0.5f, 11, w * 0.5f - m, 15);
        faroTempTv = tv("--°", 32, TEXT, false);
        faroTempTv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        place(card, faroTempTv, m - 2, 28, Math.min(90, w * 0.42f), 38);
        float is = 26;
        faroWxIc = new Faro.Icon(this, Faro.I_CLOUD, TEXT);
        faroWxIc.setStroke(1.35f);
        float ix = m + Math.min(84, w * 0.40f);
        place(card, faroWxIc, ix, 33, is, is);
        faroDescTv = tv("", 12, TEXT, false);
        place(card, faroDescTv, ix + is + 8, 39, Math.max(10, w - (ix + is + 8) - m), 16);
        // lluvia y humedad no caben: van ocultas (renderFaroWx las sigue llenando)
        LinearLayout hidden = new LinearLayout(this);
        hidden.setVisibility(View.GONE);
        faroRainBox = new LinearLayout(this);
        faroRainTv = tv("", 11, MUTED, false);
        faroRainBox.addView(faroRainTv);
        faroHumBox = new LinearLayout(this);
        faroHumTv = tv("", 11, MUTED, false);
        faroHumBox.addView(faroHumTv);
        hidden.addView(faroRainBox);
        hidden.addView(faroHumBox);
        card.addView(hidden);
        float hy = 66;
        faroHours = new LinearLayout(this);
        faroHours.setOrientation(LinearLayout.HORIZONTAL);
        faroHoursW = w - 2 * m + 8;
        place(card, faroHours, m - 4, hy, faroHoursW, Math.max(30, hh - hy - 6));
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showWeatherDetail(); } });
        faroHrIc = 16; faroHrGap = 3;
        renderFaroWx();
    }

    /**
     * Barra de apps tipo píldora: vertical a la izquierda en horizontal y abajo en vertical. Si no caben todas, se
     * desliza (hacia abajo o hacia los lados).
     */
    private void buildCarbDock() {
        final int n = dockCount;
        int isz = prefs.getInt("dock_icon_size", 1);
        float ik = isz == 0 ? 0.86f : isz == 2 ? 1.12f : 1f;
        boolean vert = !vertical;
        float thick = premPillH, item = vert ? 58 : Math.max(60, Math.min(84, (cbDockW - 16) / Math.max(1, n)));
        View bg = new View(this);
        bg.setBackground(new Faro.Card(LIGHT, thick / 2 * s, 8 * s, s));
        place(board, bg, cbDockX - 8, cbDockY - 8, cbDockW + 16, cbDockH + 16);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(vert ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        list.setGravity(Gravity.CENTER);
        float tsz = Math.min(thick - 16, item - 12) * ik;
        tsz = Math.min(tsz, thick - 8);
        final boolean appStyle = "apps".equals(prefs.getString("dock_style", "circle"));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String t = dockTarget(i);
            Drawable appIc = t.startsWith("app:") ? appIcon(t.substring(4)) : appStyle ? fnAppIcon(t.substring(3)) : null;
            FrameLayout b = new FrameLayout(this);
            View inner;
            if (appIc != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(appIc);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                inner = iv;
            } else {
                String id = t.startsWith("fn:") ? t.substring(3) : dockDefault(i);
                FrameLayout tile = new FrameLayout(this);
                View g;
                float gs;
                if ("apps".equals(id)) { g = new Faro.Icon(this, Faro.I_APPS, Faro.pillIcon(LIGHT)); gs = tsz * 0.52f; }
                else {
                    ImageView iv = icon(dockFn(id)[2], tsz * 0.54f);
                    iv.setColorFilter(Faro.pillIcon(LIGHT));
                    g = iv;
                    gs = tsz * 0.54f;
                }
                FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams((int) (gs * s), (int) (gs * s));
                glp.gravity = Gravity.CENTER;
                tile.addView(g, glp);
                inner = tile;
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (tsz * s), (int) (tsz * s));
            lp.gravity = Gravity.CENTER;
            b.addView(inner, lp);
            list.addView(b, vert ? new LinearLayout.LayoutParams((int) (thick * s), (int) (item * s))
                : new LinearLayout.LayoutParams((int) (item * s), (int) (thick * s)));
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { runDock(idx); } });
            b.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { dockMenu(idx, null); return true; } });
        }
        float content = n * item;
        float pad = 8;
        if (vert) {
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sv.setFillViewport(true);
            sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
            place(board, sv, cbDockX, cbDockY + pad, cbDockW, cbDockH - 2 * pad);
            if (content <= cbDockH - 2 * pad) list.setMinimumHeight((int) ((cbDockH - 2 * pad) * s));
        } else {
            android.widget.HorizontalScrollView sv = new android.widget.HorizontalScrollView(this);
            sv.setHorizontalScrollBarEnabled(false);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sv.setFillViewport(true);
            sv.addView(list, new FrameLayout.LayoutParams(-2, -1));
            place(board, sv, cbDockX + pad, cbDockY, cbDockW - 2 * pad, cbDockH);
            if (content <= cbDockW - 2 * pad) list.setMinimumWidth((int) ((cbDockW - 2 * pad) * s));
        }
    }

    /** Barra de arriba: saludo (o la hora con «Solo mapa»), íconos, «Solo mapa» y Ajustes. */
    private void buildPremiumTop() {
        float W0 = premW();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        greetTv = tv("", 16, SOFT, false);
        greetTv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openSettings(); } });
        if (mapOnly || Prem.cob()) {
            // sin cabecera: la hora y la fecha vuelven a la barra de arriba, como en el inicio de siempre
            // (Cobalto no tiene hora grande: siempre va aquí)
            row.setBaselineAligned(true);
            row.setGravity(Gravity.NO_GRAVITY);
            timeTv = tv("", 30, TEXT, true);
            dateTv = tv("", 14, RED, true);
            dateTv.setPadding((int) (12 * s), 0, 0, 0);
            row.addView(timeTv);
            row.addView(dateTv);
            if (vertical) dateTv.setVisibility(View.GONE);
        } else row.addView(greetTv);
        topSpeedTv = null;   // con «Solo mapa» la velocidad va en el velocímetro del mapa
        topOilTv = null;
        topIconName = null;
        if (mapOnly && oilHome) {
            topOilTv = tv("", 13, TEXT, true);
            topOilTv.setBackground(glass(12));
            topOilTv.setPadding((int) (10 * s), (int) (4 * s), (int) (12 * s), (int) (4 * s));
            setTopIcon("water_drop");
            topOilTv.setCompoundDrawablePadding((int) (6 * s));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
            olp.leftMargin = (int) (14 * s);
            row.addView(topOilTv, olp);
            topOilTv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { badgeMenu(); } });
        }
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(glass(14));
        pill.setPadding((int) (8 * s), (int) (4 * s), (int) (11 * s), (int) (4 * s));
        FrameLayout pIcBox = new FrameLayout(this);
        pIcBox.setBackground(round(RED, 8));
        topNavIc = new Nav.ManeuverView(this);
        pIcBox.addView(topNavIc, new FrameLayout.LayoutParams(-1, -1));
        pill.addView(pIcBox, new LinearLayout.LayoutParams((int) (22 * s), (int) (22 * s)));
        topNavDistTv = tv("", 13, TEXT, true);
        topNavDistTv.setPadding((int) (7 * s), 0, 0, 0);
        pill.addView(topNavDistTv);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.leftMargin = (int) (12 * s);
        plp.gravity = Gravity.CENTER_VERTICAL;
        pill.setVisibility(View.GONE);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openNavMenu(); } });
        topNavPill = pill;
        row.addView(pill, plp);
        placeWrap(board, row, 24, mapOnly ? 10 : 20);
        premTopRow = row;

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        wifiIc = null;
        btIc = null;
        addCarData(right);
        if (prefs.getBoolean("status_icons", true)) {
            LinearLayout st = new LinearLayout(this);
            st.setGravity(Gravity.CENTER_VERTICAL);
            st.setPadding(0, 0, (int) (4 * s), 0);
            wifiIc = statusIcon(st, "wifi", new Runnable() { public void run() { toggleWifi(); } },
                new Runnable() { public void run() { openSettingsPage(Settings.ACTION_WIFI_SETTINGS, new Runnable() { public void run() { openSystemSettings(); } }); } });
            btIc = statusIcon(st, "bluetooth", new Runnable() { public void run() { toggleBt(); } },
                new Runnable() { public void run() {
                    if (clwBt) openSlot(1);
                    else openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS, new Runnable() { public void run() { openSlot(1); } });
                } });
            right.addView(st);
        }
        // el clima de arriba solo con «Solo mapa» (con la cabecera ya tiene su tarjeta)
        weatherBox = null;
        if (mapOnly) {
            weatherBox = new LinearLayout(this);
            weatherBox.setGravity(Gravity.CENTER_VERTICAL);
            weatherIc = icon("cloud", 20);
            weatherBox.addView(weatherIc, new LinearLayout.LayoutParams((int) (20 * s), (int) (20 * s)));
            weatherTv = tv("", 15, TEXT, true);
            weatherTv.setPadding((int) (6 * s), 0, 0, 0);
            weatherBox.addView(weatherTv);
            weatherCityTv = tv("", 13, MUTED, false);
            weatherCityTv.setPadding((int) (6 * s), 0, (int) (8 * s), 0);
            weatherBox.addView(weatherCityTv);
            if (vertical) weatherCityTv.setVisibility(View.GONE);
            weatherBox.setVisibility(View.GONE);
            weatherBox.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showWeatherDetail(); } });
            right.addView(weatherBox);
        }
        float bx = W0 - 60;   // Ajustes; a su izquierda «Solo mapa» y, sin barra, Apps
        View gear = circleIcon("settings", 40, 22, 0);
        gear.setBackground(glass(20));
        place(board, gear, bx, 12, 40, 40);
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openSettings(); } });
        if (!Prem.cob()) {
            bx -= 50;
            FrameLayout tg = new FrameLayout(this);
            tg.setBackground(glass(20));
            Aurora.Corners cr = new Aurora.Corners(this, ICON, 2.2f * s);
            cr.inward = mapOnly;
            tg.addView(cr, new FrameLayout.LayoutParams(-1, -1));
            place(board, tg, bx, 12, 40, 40);
            tg.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleMapOnly(); } });
        }
        if (noDock) {
            bx -= 50;
            View appsBtn = circleIcon("apps", 40, 22, 0);
            appsBtn.setBackground(glass(20));
            place(board, appsBtn, bx, 12, 40, 40);
            appsBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openDrawer(); } });
        }
        place(board, right, bx - 10 - 560, 16, 560, 32);
    }

    /** Con «Solo mapa» no hay tarjetas: la velocidad y el GPS se siguen calculando (para la de arriba) sin mostrarse. */
    private void premiumDetached() {
        speedTv = tv("0", 12, TEXT, true);
        gpsTv = tv("", 10, MUTED, false);
        gpsDot = new View(this);
        carTv = null;
        oilValTv = null; oilBar = null; oilBarBg = null; oilLblTv = null; oilIc = null;
        trackTv = null; artistTv = null; sourceTv = null; timePosTv = null; playerArt = null; playIc = null;
        updateOilBadge();
    }

    /** Cabecera: hora grande con la fecha en el acento, y las tarjetas de música, vehículo y clima. */
    private void buildPremiumHeader() {
        premHeadViews.clear();
        float cw = premW() - 32, y = premHeadY, gap = 12;
        boolean wxOn = prefs.getBoolean("weather_on", true);
        int k = wxOn ? 3 : 2;
        timeTv = tv("", vertical ? 54 : 56, TEXT, true);
        dateTv = tv("", 17, RED, true);
        dateTv.setPadding((int) (2 * s), (int) (6 * s), 0, 0);
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        clock.addView(timeTv);
        clock.addView(dateTv);
        premHeadViews.add(clock);
        float wy, wh, wW, wx0;
        if (premWide) {
            // columna izquierda: la hora, la música a lo ancho y debajo el vehículo y el clima
            float colW = premColW, bottom = dockTop - (noDock ? 16 : 12), wy0 = y + 96;
            float rowH = (bottom - wy0 - gap) / 2;
            place(board, clock, 24, y, colW - 8, 88);
            buildPremMusic(16, wy0, colW, rowH);
            float hw = wxOn ? (colW - gap) / 2 : colW;
            buildPremCar(16, wy0 + rowH + gap, hw, rowH);
            if (wxOn) buildPremWeather(16 + hw + gap, wy0 + rowH + gap, hw, rowH);
            return;
        }
        if (vertical) {
            place(board, clock, 24, y, cw - 16, 92);
            wy = y + 100;
            wh = shortVertical ? 150 : 172;
            wW = (cw - (k - 1) * gap) / k;
            wx0 = 16;
        } else {
            wh = premHeadH;
            wW = Math.min(240, 188 + Math.max(0, ex) / 6f);
            if (k == 2) wW = Math.min(260, wW + 30);
            wx0 = 16 + cw - k * wW - (k - 1) * gap;
            wy = y;
            place(board, clock, 24, y, wx0 - 24 - gap, wh);
        }
        float x = wx0;
        buildPremMusic(x, wy, wW, wh);
        x += wW + gap;
        buildPremCar(x, wy, wW, wh);
        x += wW + gap;
        if (wxOn) buildPremWeather(x, wy, wW, wh);
    }

    private void buildPremMusic(float x, float y, float w, float hh) {
        final FrameLayout card = new FrameLayout(this);
        card.setBackground(glass(18));
        place(board, card, x, y, w, hh);
        premHeadViews.add(card);
        float m = 14;
        placeWrap(card, tv("Música", 11, MUTED, true), m, 12);
        sourceTv = tv("", 10, RED, true);
        sourceTv.setGravity(Gravity.RIGHT);
        sourceTv.setPadding(0, 0, (int) (4 * s), 0);
        place(card, sourceTv, w * 0.38f, 12, w * 0.62f - m + 4, 16);
        // horizontal: carátula un poco más chica para dejar aire entre la línea de tiempo y los botones
        float A = vertical ? Math.min(hh * 0.46f, w * 0.36f) : Math.min(hh * 0.39f, w * 0.34f), ay = vertical ? 32 : 30;
        playerArt = new PlayerArt(this, s, asset("ic/music_note.png"));
        // carátula cuadrada con esquinas redondas y el avance alrededor con la misma forma (como la referencia)
        playerArt.setSquare(LIGHT ? 0x1F000000 : 0x2EFFFFFF, LIGHT ? 0x14000000 : 0x1AFFFFFF);
        place(card, playerArt, m, ay, A, A);
        float tx = m + A + 10, tw = w - tx - m + 2;
        trackTv = tv("Nada sonando", 14, TEXT, true);
        place(card, trackTv, tx, ay + A * 0.5f - 21, tw, 20);
        artistTv = tv("Radio, Bluetooth o USB", 11, MUTED, false);
        place(card, artistTv, tx, ay + A * 0.5f + 3, tw, 16);
        timePosTv = tv("", 10, MUTED, false);   // no se muestra: el avance va en el anillo de la carátula
        float rowTop = ay + A + (vertical ? 2 : 10), rowH = hh - rowTop - (vertical ? 6 : 8), ps = Math.min(40, rowH), bs = ps;
        float cx = w / 2, ry = rowTop + (rowH - ps) / 2, sp = Math.min(14, (w - 3 * ps) / 4);
        final View prev = circleIcon("skip_previous", bs, bs * 0.58f, 0), next = circleIcon("skip_next", bs, bs * 0.58f, 0);
        prev.setBackground(round(Prem.soft(LIGHT), bs / 2));
        next.setBackground(round(Prem.soft(LIGHT), bs / 2));
        View play = circleIcon("play_arrow", ps, ps * 0.52f, RED);
        place(card, prev, cx - ps / 2 - sp - bs, ry, bs, bs);
        place(card, play, cx - ps / 2, ry, ps, ps);
        place(card, next, cx + ps / 2 + sp, ry, bs, bs);
        playIc = (ImageView) ((FrameLayout) play).getChildAt(0);
        final View.OnClickListener goPrev = mediaClick(KeyEvent.KEYCODE_MEDIA_PREVIOUS), goNext = mediaClick(KeyEvent.KEYCODE_MEDIA_NEXT);
        prev.setOnClickListener(goPrev);
        play.setOnClickListener(mediaClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        next.setOnClickListener(goNext);
        // deslizar sobre la tarjeta: ← siguiente canción, → anterior (igual que el reproductor de siempre)
        card.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getX(); y0 = e.getY(); return false; }
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return false;
                float dx = e.getX() - x0, dy = e.getY() - y0;
                if (Math.abs(dx) < 50 * s || Math.abs(dx) < 2 * Math.abs(dy)) return false;
                MotionEvent c = MotionEvent.obtain(e);
                c.setAction(MotionEvent.ACTION_CANCEL);
                v.onTouchEvent(c);
                c.recycle();
                (dx < 0 ? goNext : goPrev).onClick(v);
                return true;
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { showPlayerDiag(); return true; } });
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openMusicApp(); } });
        renderTrack();
    }

    private void buildPremCar(float x, float y, float w, float hh) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(glass(18));
        place(board, card, x, y, w, hh);
        premHeadViews.add(card);
        float m = 14;
        // velocímetro elegido en Pantalla de inicio: digital (número) o analógico / mixto (el aro de StradaOS)
        final int speedo = carSpeed ? prefs.getInt("speedo", 0) : 0;
        float stripH = 34, stripY = hh - stripH - 8;
        carTv = tv(prefs.getString("car_name", "Mi vehículo"), 11, MUTED, true);
        LinearLayout g = new LinearLayout(this);
        g.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        gpsDot = new View(this);
        gpsDot.setBackground(round(MUTED, 4));
        g.addView(gpsDot, new LinearLayout.LayoutParams((int) (7 * s), (int) (7 * s)));
        gpsTv = tv("GPS", 10, MUTED, false);
        gpsTv.setPadding((int) (5 * s), 0, 0, 0);
        g.addView(gpsTv);
        LinearLayout sv = new LinearLayout(this);
        sv.setBaselineAligned(true);
        speedTv = tv("0", oilHome ? 44 : 54, TEXT, true);
        TextView unit = tv(useMph() ? "mph" : "km/h", 13, MUTED, false);
        unit.setPadding((int) (6 * s), 0, 0, 0);
        sv.addView(speedTv);
        sv.addView(unit);
        carGauge = null;
        if (speedo > 0) {
            // el aro ocupa la tarjeta (sin el nombre, que no cabría); el GPS queda como un punto en la esquina
            float gb = oilHome ? stripY - 2 : hh - 6;
            carGauge = new Gauge(this, s);
            carGauge.setColors(RED, TEXT, SOFT);
            carGauge.setUnit(useMph());
            carGauge.setNumber(speedo == 2);
            carGauge.setCompact(true);
            place(card, carGauge, 6, 6, w - 12, gb - 6);
            gpsTv.setVisibility(View.GONE);
            place(card, g, w - 22, 12, 12, 12);
        } else {
            place(card, carTv, m, 12, w - 2 * m - 70, 16);
            place(card, g, w - m - 100, 12, 100, 16);
            if (oilHome) placeWrap(card, sv, m, 34 + Math.max(0, (stripY - 34 - 50) / 2));
            else placeWrap(card, sv, m, 34 + Math.max(0, (hh - 34 - 64) / 2));
        }
        if (!carSpeed) {
            // carros sin GPS: la foto del vehículo (o su ícono) en lugar de la velocidad
            sv.setVisibility(View.GONE);
            g.setVisibility(View.GONE);
            Bitmap photo = CarPhoto.load(this);
            float pw = w - 2 * m + 8, ph = hh - 34 - 10;
            if (photo != null) {
                ImageView car = new ImageView(this);
                boolean cut = false;
                try { cut = CarPhoto.hasTransparency(photo); } catch (Throwable ignored) { }
                if (cut) car.setImageBitmap(photo);
                else car.setImageBitmap(CarPhoto.cardCrop(photo, (int) (pw * s), (int) (ph * s), 12 * s));
                car.setScaleType(ImageView.ScaleType.FIT_CENTER);
                place(card, car, m - 4, 34, pw, ph);
            } else {
                ImageView ic = icon("directions_car", 56);
                ic.setAlpha(0.4f);
                place(card, ic, (w - 56) / 2, 34 + (ph - 56) / 2, 56, 56);
            }
        }
        if (oilHome) premOilStrip(card, m - 4, stripY, w - 2 * m + 8, stripH);
        else { oilValTv = null; oilBar = null; oilBarBg = null; oilLblTv = null; oilIc = null; }
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (oilHome) badgeMenu(); else showSettings(C_VEHICLE);
        }});
    }

    /** Mantenimiento más próximo dentro de la tarjeta del vehículo: ícono, nombre, lo que falta y la barra. */
    private void premOilStrip(FrameLayout card, float x, float y, float w, float h) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(round(Prem.soft(LIGHT), 11));
        col.setPadding((int) (9 * s), (int) (5 * s), (int) (9 * s), (int) (6 * s));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = icon("water_drop", 13);
        ic.setAlpha(0.75f);
        row.addView(ic, new LinearLayout.LayoutParams((int) (13 * s), (int) (13 * s)));
        TextView t = tv("Aceite", 11, MUTED, false);
        t.setPadding((int) (5 * s), 0, (int) (5 * s), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        oilLblTv = t;
        oilIc = ic;
        oilValTv = tv("", 12, TEXT, true);
        row.addView(oilValTv, new LinearLayout.LayoutParams(-2, -2));
        col.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout bar = new FrameLayout(this);
        oilBarBg = bar;
        bar.setBackground(round(LIGHT ? 0x1F000000 : 0x29FFFFFF, 2));
        oilBar = new View(this);
        bar.addView(oilBar, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, (int) (3 * s));
        blp.topMargin = (int) (3 * s);
        col.addView(bar, blp);
        place(card, col, x, y, w, h);
        col.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { badgeMenu(); } });
        updateOilBadge();
    }

    private void buildPremWeather(float x, float y, float w, float hh) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(glass(18));
        card.setPadding((int) (10 * s), (int) (12 * s), (int) (10 * s), (int) (8 * s));
        place(board, card, x, y, w, hh);
        premHeadViews.add(card);
        TextView wl = tv("Clima", 11, MUTED, true);
        wl.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(wl);
        float is = hh >= 170 ? 46 : 40;
        panelWxIc = icon("cloud", is);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams((int) (is * s), (int) (is * s));
        ilp.topMargin = (int) ((hh >= 170 ? 10 : 6) * s);
        card.addView(panelWxIc, ilp);
        panelWxTemp = tv("--°", hh >= 170 ? 30 : 26, TEXT, true);
        panelWxTemp.setPadding(0, (int) (4 * s), 0, 0);
        panelWxTemp.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(panelWxTemp);
        panelWxDesc = tv("Clima", 12, TEXT, false);
        panelWxDesc.setPadding(0, (int) (4 * s), 0, 0);
        panelWxDesc.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(panelWxDesc);
        panelWxHiLo = tv("", 11, MUTED, false);
        panelWxHiLo.setPadding(0, (int) (3 * s), 0, 0);
        panelWxHiLo.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(panelWxHiLo);
        panelWxHours = null;
        panelWx = card;
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showWeatherDetail(); } });
        showPanelWeather();
    }

    /** Barra de apps tipo píldora, centrada, con los íconos sin círculos (mismos botones y menús que la barra normal). */
    private void buildPremiumDock() {
        final int n = dockCount;
        int isz = prefs.getInt("dock_icon_size", 1);
        float ik = isz == 0 ? 0.82f : isz == 2 ? 1.2f : 1f;
        float W0 = premW(), avail = W0 - 32, pillH = premPillH;
        float itemW = Math.min(vertical ? 80 : 84, (avail - 24) / n), pillW = itemW * n + 24;
        float px = (W0 - pillW) / 2, py = dockTop;
        View bg = new View(this);
        GradientDrawable d = round(Prem.pill(LIGHT), pillH / 2);
        d.setStroke(Math.max(1, (int) s), Prem.pillEdge(LIGHT));
        bg.setBackground(d);
        place(board, bg, px, py, pillW, pillH);
        float ic = Math.min(itemW - 18, 30 * ik);
        final boolean appStyle = "apps".equals(prefs.getString("dock_style", "circle"));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String t = dockTarget(i);
            Drawable appIc = t.startsWith("app:") ? appIcon(t.substring(4)) : appStyle ? fnAppIcon(t.substring(3)) : null;
            FrameLayout b = new FrameLayout(this);
            ImageView iv;
            float sz = ic;
            if (appIc != null) {
                iv = new ImageView(this);
                iv.setImageDrawable(appIc);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                sz = Math.min(itemW - 14, ic * 1.18f);
            } else {
                String id = t.startsWith("fn:") ? t.substring(3) : dockDefault(i);
                iv = icon(dockFn(id)[2], ic);
                iv.setColorFilter(Prem.pillIcon(LIGHT));
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (sz * s), (int) (sz * s));
            lp.gravity = Gravity.CENTER;
            b.addView(iv, lp);
            place(board, b, px + 12 + itemW * i, py, itemW, pillH);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { runDock(idx); } });
            b.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { dockMenu(idx, null); return true; } });
        }
    }

    // ── galería premium en Personalización ──

    /** id del tema premium o null para quitarlo. */
    private void applyPremium(String id) {
        if (id != null && !Prem.known(id)) id = null;
        SharedPreferences.Editor e = prefs.edit();
        if (id != null) e.putString("premium_theme", id); else e.remove("premium_theme");
        e.apply();
        rebuild();
        showSettings(C_STYLE);
        if (id != null) toast(I18n.t("Tema aplicado:") + " " + I18n.t(Prem.name(id)));
        else toast("Tema premium quitado");
    }

    private void premiumGallery(LinearLayout body, boolean portrait) {
        TextView h2 = tv(I18n.t("Temas premium").toUpperCase(I18n.loc), 11, MUTED, true);
        h2.setPadding((int) (6 * s), (int) (10 * s), 0, (int) (8 * s));
        body.addView(h2);
        for (String id : Prem.IDS) premiumCell(body, portrait, id);
    }

    private void premiumCell(LinearLayout body, boolean portrait, final String id) {
        final boolean unlocked = premiumUnlocked(id), inUse = premiumActive() && id.equals(prefs.getString("premium_theme", null));
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(portrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        cell.setBackground(round(inUse ? RED_DARK : CARD, 16));
        int cp = (int) (10 * s);
        cell.setPadding(cp, cp, cp, (int) (12 * s));
        LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(-1, -2);
        cellLp.bottomMargin = (int) (10 * s);
        body.addView(cell, cellLp);
        // las dos versiones, oscura y clara: el tema sigue el modo de Apariencia
        LinearLayout thumbs = new LinearLayout(this);
        thumbs.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            View th;
            boolean sel = inUse && (i == 1) == LIGHT;
            if (Cobalto.ID.equals(id)) { Cobalto.Thumb t = new Cobalto.Thumb(this, s, portrait, i == 1); t.selected = sel; th = t; }
            else if (Faro.ID.equals(id)) { Faro.Thumb t = new Faro.Thumb(this, s, portrait, i == 1); t.selected = sel; th = t; }
            else if (Carbono.ID.equals(id)) { Carbono.Thumb t = new Carbono.Thumb(this, s, portrait, i == 1); t.selected = sel; th = t; }
            else { Aurora.Thumb t = new Aurora.Thumb(this, s, portrait, i == 1); t.selected = sel; th = t; }
            col.addView(th, new LinearLayout.LayoutParams(-1, -2));
            TextView cap = tv(i == 0 ? "Oscuro" : "Claro", 11, MUTED, false);
            cap.setGravity(Gravity.CENTER);
            cap.setPadding(0, (int) (5 * s), 0, 0);
            col.addView(cap, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i == 0) lp.rightMargin = (int) (10 * s);
            thumbs.addView(col, lp);
        }
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        if (portrait) {
            cell.addView(thumbs, new LinearLayout.LayoutParams(0, -2, 1f));
            info.setPadding((int) (14 * s), (int) (4 * s), (int) (4 * s), 0);
            cell.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        } else {
            cell.addView(thumbs, new LinearLayout.LayoutParams(-1, -2));
            info.setPadding((int) (4 * s), (int) (10 * s), (int) (4 * s), 0);
            cell.addView(info, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView nm = tv(Prem.name(id), 16, TEXT, true);
        nameRow.addView(nm, new LinearLayout.LayoutParams(0, -2, 1));
        final boolean free = Prem.free(id);
        TextView badge = tv(inUse ? "En uso" : free ? "Gratis" : "Premium", 11, inUse ? 0xFFFFFFFF : RED, true);
        badge.setBackground(inUse ? round(RED, 10) : round(RED_DARK, 10));
        badge.setPadding((int) (8 * s), (int) (2 * s), (int) (8 * s), (int) (2 * s));
        nameRow.addView(badge);
        info.addView(nameRow);
        TextView ds = tv(Prem.desc(id), 12, MUTED, false);
        multiLine(ds);
        ds.setMaxLines(8);
        ds.setLineSpacing(2 * s, 1f);
        ds.setPadding(0, (int) (5 * s), 0, 0);
        info.addView(ds);
        TextView mode = tv("Sigue el modo claro u oscuro de Apariencia.", 12, SOFT, false);
        multiLine(mode);
        mode.setPadding(0, (int) (6 * s), 0, 0);
        info.addView(mode);
        if (unlocked) {
            TextView btn = tv(inUse ? "Quitar el tema premium" : "Aplicar", 14, inUse ? TEXT : 0xFFFFFFFF, true);
            btn.setText(btn.getText() + " ");   // en negrita la última letra se recortaba
            btn.setEllipsize(null);
            btn.setGravity(Gravity.CENTER);
            btn.setBackground(round(inUse ? BTN : RED, 18));
            btn.setPadding((int) (18 * s), (int) (9 * s), (int) (18 * s), (int) (9 * s));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = (int) (10 * s);
            info.addView(btn, blp);
            btn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { applyPremium(inUse ? null : id); } });
            if (!inUse) cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { applyPremium(id); } });
            TextView test = tv(free ? "Gratis para todos. Los próximos temas premium se desbloquearán con una donación."
                : debugUnlock() ? "Versión de prueba: se puede aplicar sin código." : "Desbloqueado en esta pantalla. ¡Gracias por tu donación!", 11, MUTED, false);
            multiLine(test);
            test.setPadding(0, (int) (8 * s), 0, 0);
            info.addView(test);
        } else {
            TextView btn = tv("Desbloquear", 14, 0xFFFFFFFF, true);
            btn.setText(btn.getText() + " ");
            btn.setEllipsize(null);
            btn.setGravity(Gravity.CENTER);
            btn.setBackground(round(RED, 18));
            btn.setPadding((int) (18 * s), (int) (9 * s), (int) (18 * s), (int) (9 * s));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = (int) (10 * s);
            info.addView(btn, blp);
            View.OnClickListener open = new View.OnClickListener() { public void onClick(View v) { unlockDialog(id); } };
            btn.setOnClickListener(open);
            cell.setOnClickListener(open);
            TextView soon = tv(Prem.bundle() ? "Se desbloquea con una donación de 5 USD, o 8 USD por dos temas. Todas las funciones de StradaOS siguen siendo gratis."
                : "Se desbloquea con una donación. Todas las funciones de StradaOS siguen siendo gratis.", 11, MUTED, false);
            multiLine(soon);
            soon.setPadding(0, (int) (8 * s), 0, 0);
            info.addView(soon);
        }
        if (getPackageName().endsWith(".debug") && !free) {
            // solo en StradaOS Prueba: probar el desbloqueo de verdad (con el ID y el código de esta pantalla)
            final boolean t = prefs.getBoolean("lic_test", false);
            TextView tt = tv(t ? "Prueba: volver a aplicar sin código" : "Prueba: desbloquear como en la versión normal", 11, RED, true);
            multiLine(tt);
            tt.setPadding(0, (int) (10 * s), 0, 0);
            info.addView(tt);
            tt.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                prefs.edit().putBoolean("lic_test", !t).apply();
                showSettings(C_STYLE); }});
        }
    }

    // ── Desbloqueo con donación (Fase 3, 2.27): ID de la pantalla → donación en Ko-fi → código STRD-XXXX-XXXX ──

    private void unlockDialog(final String id) {
        String du = Updates.donateUrl(this);
        final String url = du.startsWith("https://ko-fi.com/") ? du : "https://ko-fi.com/stradaos";
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (20 * s);
        box.setPadding(pad, pad, pad, (int) (6 * s));
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        boolean narrow = dm.widthPixels < dm.heightPixels;
        box.setOrientation(narrow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        ImageView qr = new ImageView(this);
        qr.setImageBitmap(qrBitmap(url, (int) (170 * s)));
        box.addView(qr, new LinearLayout.LayoutParams((int) (170 * s), (int) (170 * s)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(narrow ? 0 : (int) (20 * s), narrow ? (int) (14 * s) : 0, 0, 0);
        final boolean pack = Prem.bundle();   // con dos o más temas de pago: 8 USD por dos
        TextView m1 = tv(I18n.t(pack ? "1. Dona en Ko-fi 5 USD por un tema o 8 USD por dos: escanea el código con tu celular o entra a {}."
            : "1. Dona 5 USD en Ko-fi: escanea el código con tu celular o entra a {}.").replace("{}", url.replace("https://", "")), 13, SOFT, false);
        multiLine(m1);
        col.addView(m1);
        TextView m2 = tv(pack ? "2. En el mensaje de la donación escribe el ID de esta pantalla y qué temas quieres:"
            : "2. En el mensaje de la donación escribe el ID de esta pantalla:", 13, SOFT, false);
        multiLine(m2);
        m2.setPadding(0, (int) (10 * s), 0, (int) (4 * s));
        col.addView(m2);
        TextView idv = tv("", 24, RED, true);
        idv.setText(Lic.shown(this));
        idv.setPadding(0, 0, 0, (int) (4 * s));
        col.addView(idv);
        TextView m3 = tv("3. Te enviamos un código STRD-XXXX-XXXX, casi siempre el mismo día. Escríbelo en «Tengo un código».", 13, SOFT, false);
        multiLine(m3);
        m3.setPadding(0, (int) (6 * s), 0, 0);
        col.addView(m3);
        TextView m4 = tv("El tema queda en esta pantalla. Si cambias de radio o la restableces, escríbenos con el ID nuevo y te enviamos otro código sin costo.", 11, MUTED, false);
        multiLine(m4);
        m4.setPadding(0, (int) (10 * s), 0, 0);
        col.addView(m4);
        box.addView(col, narrow ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        new TB(this, dlg()).wide()
            .setTitle(I18n.t("Desbloquear {}").replace("{}", Prem.name(id)))
            .setView(sv)
            .setPositiveButton("Tengo un código", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { codeDialog(id); } })
            .setNeutralButton("Archivo por USB", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { importLicense(id); } })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private void codeDialog(final String id) {
        final android.widget.EditText et = field();
        et.setSingleLine(true);
        et.setHint("STRD-XXXX-XXXX");
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * s);
        box.setPadding(pad, (int) (10 * s), pad, 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = tv(I18n.t("ID de esta pantalla: {}. Necesitas internet solo esta vez.").replace("{}", Lic.shown(this)), 12, MUTED, false);
        multiLine(hint);
        hint.setPadding(0, (int) (8 * s), 0, 0);
        box.addView(hint);
        new TB(this, dlg())
            .setTitle("Escribe tu código")
            .setView(box)
            .setPositiveButton("Desbloquear", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { checkCode(id, et.getText().toString()); } })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void checkCode(final String id, String typed) {
        final String code = Lic.normCode(typed);
        if (code == null) { toast("El código tiene 8 letras o números después de STRD, por ejemplo STRD-7KQ2-M9XA."); return; }
        toast("Comprobando el código…");
        new Thread(new Runnable() { public void run() {
            final int r = Lic.fetch(Main.this, code);
            h.post(new Runnable() { public void run() { codeResult(id, r, Lic.lastThemes); } });
        }}).start();
    }

    private void codeResult(final String id, int r, java.util.List<String> themes) {
        if (r == Lic.OK) { unlocked(id, themes); return; }
        String msg = r == Lic.NO_NET
            ? "No hay internet. Conéctate un momento (por ejemplo, con el celular como módem) y vuelve a intentarlo, o pide el archivo de licencia y pásalo por USB."
            : I18n.t("No encontramos ese código para esta pantalla. Revisa que esté bien escrito y que el ID que enviaste sea {}.").replace("{}", Lic.shown(this));
        new TB(this, dlg()).setTitle("No se pudo desbloquear").setMessage(msg)
            .setPositiveButton("Intentar de nuevo", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { codeDialog(id); } })
            .setNegativeButton("Cerrar", null).show();
    }

    private void importLicense(final String id) {
        toast("Buscando la licencia en la pantalla y en las USB…");
        new Thread(new Runnable() { public void run() {
            final java.util.List<String> got = Lic.importUsb(Main.this);
            h.post(new Runnable() { public void run() {
                if (!got.isEmpty()) { unlocked(id, got); return; }
                new TB(Main.this, dlg()).setTitle("No encontramos la licencia")
                    .setMessage(I18n.t("Copia el archivo que te enviamos (stradaos-licencia.txt) en la raíz de una USB, conéctala y vuelve a intentarlo. Tiene que ser para el ID {}.").replace("{}", Lic.shown(Main.this)))
                    .setPositiveButton("Intentar de nuevo", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { importLicense(id); } })
                    .setNegativeButton("Cerrar", null).show();
            }});
        }}).start();
    }

    private void unlocked(final String id, java.util.List<String> themes) {
        final String th = themes != null && themes.contains(id) ? id : themes != null && !themes.isEmpty() ? themes.get(0) : id;
        if (settingsView != null && settingsCat == C_STYLE) showSettings(C_STYLE);
        new TB(this, dlg())
            .setTitle("¡Listo!")
            .setMessage(I18n.t("{} quedó desbloqueado en esta pantalla. Gracias por apoyar StradaOS.").replace("{}", Prem.known(th) ? Prem.name(th) : th))
            .setPositiveButton("Aplicar ahora", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { if (Prem.known(th)) applyPremium(th); } })
            .setNegativeButton("Después", null)
            .show();
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
        else if ("nav_home".equals(id)) navigateSavedSlot("nav_home", "Casa");
        else if ("nav_work".equals(id)) navigateSavedSlot("nav_work", "Trabajo");
        else if ("obd".equals(id)) openVehiclePanel();
        else openDrawer();
    }

    private void navigateSavedSlot(String key, String label) {
        Nav.Place p = Nav.savedPlace(this, key);
        if (p == null) {
            toast("Configura «" + label + "» primero en Navegación");
            if (prefs.getBoolean("map_live", true)) openNavSearch();
            return;
        }
        startInternalNav(p);
    }


    /** Menú del botón N: cambiar app o función, cambiar nombre, volver a lo de siempre. */
    private void dockMenu(final int i, final Runnable after) {
        final String def = dockFn(dockDefault(i))[1];
        new TB(this, dlg())
            .setTitle("Botón " + (i + 1) + " · " + dockName(i))
            .setItems(new String[]{"Cambiar app o función", "Cambiar el nombre", "Restablecer (" + ("web".equals(dockDefault(i)) ? "Navegador" : def) + ")"},
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
                    if (d == null) { try { d = iconOf(r); } catch (Throwable e) { d = null; } if (d != null) icons.put(pos, d); }
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
    private boolean notifHint;

    /** Android 5+: suena música pero StradaOS no tiene «Acceso a notificaciones», así que no puede ver la canción. */
    private boolean wantsNotifAccess() {
        if (SDK < 21 || curTitle != null || curArtist != null || prefs.getBoolean("notif_hint_off", false)) return false;
        try {
            if (Modern.notificationAccess(this)) return false;
            return ((AudioManager) getSystemService(AUDIO_SERVICE)).isMusicActive();
        } catch (Throwable e) { return false; }
    }

    /** Explica el permiso y abre el ajuste; si la pantalla no lo tiene, da el comando de ADB. */
    private void askNotifAccess() {
        new TB(this, dlg())
            .setTitle("Ver la canción de tus apps")
            .setMessage("Para mostrar la canción, el artista y la carátula de Spotify, YouTube Music y otras apps, StradaOS necesita «Acceso a notificaciones». Solo lo usa para leer lo que suena: no guarda ni envía nada.")
            .setPositiveButton("Activar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Throwable e) { notifAccessAdb(); }
            }})
            .setNeutralButton("No volver a mostrar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                prefs.edit().putBoolean("notif_hint_off", true).apply();
                renderTrack();
            }})
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Pantallas sin el ajuste de «Acceso a notificaciones»: se activa desde una computadora con ADB. */
    private void notifAccessAdb() {
        String cn = getPackageName() + "/" + MediaListener.class.getName();
        String cmd = SDK >= 27 ? "adb shell cmd notification allow_listener " + cn
            : "adb shell settings put secure enabled_notification_listeners " + cn;
        new TB(this, dlg()).raw()
            .setTitle(I18n.t("Ver la canción de tus apps"))
            .setMessage(I18n.t("Esta pantalla no tiene el ajuste de «Acceso a notificaciones». Se puede activar una sola vez desde una computadora con ADB:") + "\n\n" + cmd)
            .setPositiveButton("OK", null)
            .show();
    }

    private void openMusicApp() {
        if (notifHint && wantsNotifAccess()) { askNotifAccess(); return; }
        if (launchSaved("music_app")) return;
        if ("Android Auto".equals(curSource) && launchPkg(PKG_HUR)) return;
        if ("Bluetooth".equals(curSource)) { ComponentName cn = btMusicApp(); if (cn != null && launchComponent(cn)) return; }
        String p = firstInstalled(MUSIC_PKGS);
        if (p != null && launchPkg(p)) return;
        toast("Elige tu app de música");
        pickFor("music_app", "Música", null);
    }

    /** App de navegación elegida; si se elige integrada en el mapa, abre búsqueda/menú interno. */
    private void openNavApp() {
        String navPref = prefs.getString("nav_app", null);
        if (prefs.getBoolean("map_live", true) && "internal".equals(navPref)) {
            if (Nav.active != null) openNavMenu();
            else openNavSearch();
            return;
        }
        openExternalNavApp();
    }

    private void openExternalNavApp() {
        String navPref = prefs.getString("nav_app", null);
        if (navPref != null && !"internal".equals(navPref) && launchSaved("nav_app")) return;
        String p = firstInstalled(NAV_PKGS);
        if (p != null && launchPkg(p)) return;
        toast("Elige tu app de navegación");
        pickFor("nav_app", "Navegación", null);
    }

    private void openNavSearch() {
        openNavSearch(false);
    }

    private void openNavSearch(final boolean insertActive) {
        if (!prefs.getBoolean("map_live", true)) { toast("Activa el mapa en vivo en Ajustes → Mapa y clima"); return; }
        final android.app.AlertDialog[] dref = new android.app.AlertDialog[1];
        final boolean[] addStopMode = {false};
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * s);
        wrap.setPadding(pad, pad / 2, pad, 0);

        final android.widget.EditText et = field();
        et.setSingleLine(true);
        et.setHint(I18n.t(insertActive ? "Buscar parada intermedia" : "Escribe una dirección o lugar"));
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                    searchNav(et.getText().toString(), insertActive, addStopMode[0]);
                    return true;
                }
                return false;
            }
        });
        wrap.addView(et, new LinearLayout.LayoutParams(-1, -2));

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, (int) (10 * s), 0, 0);

        if (!insertActive && !pendingStops.isEmpty()) {
            LinearLayout hdrRow = new LinearLayout(this);
            hdrRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView stHdr = tv(I18n.t("PARADAS") + " (" + pendingStops.size() + ")", 11, RED, true);
            hdrRow.addView(stHdr, new LinearLayout.LayoutParams(0, -2, 1));
            TextView clrBtn = tv("Limpiar", 11, MUTED, true);
            clrBtn.setPadding((int) (8 * s), (int) (4 * s), (int) (4 * s), (int) (4 * s));
            clrBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    pendingStops.clear();
                    if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                    openNavSearch(false);
                }
            });
            hdrRow.addView(clrBtn);
            body.addView(hdrRow);
            for (int i = 0; i < pendingStops.size(); i++) {
                final int stopIdx = i;
                Nav.Place st = pendingStops.get(i);
                LinearLayout sRow = new LinearLayout(this);
                sRow.setGravity(Gravity.CENTER_VERTICAL);
                sRow.setBackground(round(INNER, 10));
                sRow.setPadding((int) (12 * s), (int) (8 * s), (int) (12 * s), (int) (8 * s));
                TextView sTv = tv("", 13, TEXT, true);
                sTv.setText((i + 1) + ". " + st.name);
                sRow.addView(sTv, new LinearLayout.LayoutParams(0, -2, 1));
                TextView del = tv("✕", 13, MUTED, true);
                del.setPadding((int) (10 * s), (int) (2 * s), (int) (2 * s), (int) (2 * s));
                del.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (stopIdx < pendingStops.size()) pendingStops.remove(stopIdx);
                        if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                        openNavSearch(false);
                    }
                });
                sRow.addView(del);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
                slp.topMargin = (int) (4 * s);
                slp.bottomMargin = (int) (4 * s);
                body.addView(sRow, slp);
            }
        }

        if (!insertActive) {
            final TextView addStopPill = tv("+ Añadir parada intermedia", 12, SOFT, true);
            addStopPill.setGravity(Gravity.CENTER);
            addStopPill.setBackground(round(INNER, 10));
            addStopPill.setPadding((int) (12 * s), (int) (7 * s), (int) (12 * s), (int) (7 * s));
            addStopPill.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    addStopMode[0] = !addStopMode[0];
                    addStopPill.setBackground(round(addStopMode[0] ? RED : INNER, 10));
                    addStopPill.setTextColor(addStopMode[0] ? Color.WHITE : SOFT);
                    addStopPill.setText(I18n.t(addStopMode[0] ? "Modo parada activo: elige una parada" : "+ Añadir parada intermedia"));
                }
            });
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
            alp.bottomMargin = (int) (8 * s);
            body.addView(addStopPill, alp);
        }

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        addQuickSlot(quickRow, "nav_home", "Casa", true, dref, insertActive, addStopMode);
        addQuickSlot(quickRow, "nav_work", "Trabajo", false, dref, insertActive, addStopMode);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-1, -2);
        qlp.bottomMargin = (int) (8 * s);
        body.addView(quickRow, qlp);


        if (LiveMapView.fileMap != null) {
            LinearLayout catRow = new LinearLayout(this);
            catRow.setOrientation(LinearLayout.HORIZONTAL);
            String[][] cats = {
                {"fuel", "Gasolina"},
                {"health", "Salud"},
                {"parking", "Parqueo"},
                {"food", "Comida"}
            };
            for (int i = 0; i < cats.length; i++) {
                final String cKey = cats[i][0];
                final String cLbl = cats[i][1];
                TextView pill = tv(cLbl, 12, TEXT, true);
                pill.setGravity(Gravity.CENTER);
                pill.setBackground(round(INNER, 10));
                pill.setPadding((int) (6 * s), (int) (8 * s), (int) (6 * s), (int) (8 * s));
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -2, 1);
                if (i + 1 < cats.length) plp.rightMargin = (int) (6 * s);
                catRow.addView(pill, plp);
                pill.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                        searchNavCategory(cKey, cLbl, insertActive, addStopMode[0]);
                    }
                });
            }
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
            clp.bottomMargin = (int) (6 * s);
            body.addView(catRow, clp);
        }

        addPlaceSection(body, "FAVORITOS", Nav.favorites(this), dref, insertActive, addStopMode);
        addPlaceSection(body, "RECIENTES", Nav.recents(this), dref, insertActive, addStopMode);

        sc.addView(body, new LinearLayout.LayoutParams(-1, -2));
        wrap.addView(sc, new LinearLayout.LayoutParams(-1, -2));

        TB b = new TB(this, dlg()).wide()
            .setTitle(insertActive ? "Añadir parada en ruta" : (!pendingStops.isEmpty() ? "Elegir destino final" : "¿A dónde vamos?"))
            .setView(wrap)
            .setPositiveButton("Buscar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { searchNav(et.getText().toString(), insertActive, addStopMode[0]); }
            })
            .setNegativeButton("Cancelar", null);
        if (!insertActive && Nav.active != null) {
            b.setNeutralButton("Detener", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { stopInternalNav(); }
            });
        }
        dref[0] = b.show();
    }

    private void addQuickSlot(LinearLayout parent, final String key, final String label, boolean leftMarginRight,
                              final android.app.AlertDialog[] dref, final boolean insertActive, final boolean[] addStopMode) {
        final Nav.Place saved = Nav.savedPlace(this, key);
        LinearLayout slot = new LinearLayout(this);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setBackground(round(INNER, 12));
        slot.setPadding((int) (12 * s), (int) (9 * s), (int) (12 * s), (int) (9 * s));
        slot.addView(tv(label, 14, TEXT, true));
        TextView sub = tv("", 11, saved != null ? SOFT : MUTED, false);
        sub.setText(saved != null ? saved.name : I18n.t("Toca para guardar GPS"));
        sub.setPadding(0, (int) (2 * s), 0, 0);
        slot.addView(sub);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (leftMarginRight) lp.rightMargin = (int) (8 * s);
        parent.addView(slot, lp);
        slot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                if (saved != null) {
                    handlePickedPlace(saved, insertActive, addStopMode != null && addStopMode[0]);
                } else if (saveCurrentGpsAs(key, label)) {
                    openNavSearch(insertActive);
                }
            }
        });
        if (saved != null) {
            slot.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                    String[] items = {
                        I18n.t("Ir ahora"),
                        I18n.t("Añadir como parada"),
                        I18n.t("Actualizar con GPS actual"),
                        I18n.t("Borrar")
                    };
                    new TB(Main.this, dlg()).raw()
                        .setTitle(I18n.t(label))
                        .setItems(items, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                if (w == 0) startInternalNav(saved);
                                else if (w == 1) handlePickedPlace(saved, insertActive, true);
                                else if (w == 2) { if (saveCurrentGpsAs(key, label)) openNavSearch(insertActive); }
                                else {
                                    Nav.setSavedPlace(Main.this, key, null);
                                    toast("Eliminado");
                                    openNavSearch(insertActive);
                                }
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                    return true;
                }
            });
        }
    }

    private boolean saveCurrentGpsAs(String key, String label) {
        if (Double.isNaN(wLat) || Double.isNaN(wLon)) seedLastLocation();
        if (Double.isNaN(wLat) || Double.isNaN(wLon)) {
            toast("Esperando señal GPS para guardar ubicación");
            return false;
        }
        Nav.Place p = new Nav.Place();
        p.name = I18n.t(label);
        p.sub = String.format(java.util.Locale.US, "%.5f, %.5f", wLat, wLon);
        p.lat = wLat;
        p.lon = wLon;
        Nav.setSavedPlace(this, key, p);
        toast("nav_home".equals(key) ? "Guardado en Casa" : "Guardado en Trabajo");
        return true;
    }

    private void addPlaceSection(LinearLayout body, String title, List<Nav.Place> items,
                                 final android.app.AlertDialog[] dref, final boolean insertActive, final boolean[] addStopMode) {
        if (items == null || items.isEmpty()) return;
        TextView hdr = tv(title, 11, MUTED, true);
        hdr.setPadding(0, (int) (8 * s), 0, (int) (6 * s));
        body.addView(hdr);
        for (int i = 0; i < items.size(); i++) {
            final Nav.Place p = items.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(round(INNER, 12));
            row.setPadding((int) (14 * s), (int) (10 * s), (int) (14 * s), (int) (10 * s));
            TextView nt = tv("", 15, TEXT, true);
            nt.setText(p.name);
            row.addView(nt);
            if (p.sub != null && p.sub.length() > 0) {
                TextView st = tv("", 12, MUTED, false);
                st.setText(p.sub);
                st.setPadding(0, (int) (2 * s), 0, 0);
                row.addView(st);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = (int) (6 * s);
            body.addView(row, rlp);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                    handlePickedPlace(p, insertActive, addStopMode != null && addStopMode[0]);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                    openPlaceOptions(p);
                    return true;
                }
            });
        }
    }

    private void openPlaceOptions(final Nav.Place p) {
        final boolean fav = Nav.isFavorite(this, p);
        String[] items = {
            I18n.t("Ir ahora"),
            I18n.t("Añadir como parada"),
            I18n.t(fav ? "Quitar de favoritos" : "Añadir a favoritos"),
            I18n.t("Guardar como Casa"),
            I18n.t("Guardar como Trabajo")
        };
        new TB(this, dlg()).raw()
            .setTitle(p.name)
            .setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) startInternalNav(p);
                    else if (w == 1) handlePickedPlace(p, false, true);
                    else if (w == 2) {
                        boolean added = Nav.toggleFavorite(Main.this, p);
                        toast(added ? "Añadido a favoritos" : "Quitado de favoritos");
                    } else if (w == 3) {
                        Nav.setSavedPlace(Main.this, "nav_home", p);
                        toast("Guardado en Casa");
                    } else {
                        Nav.setSavedPlace(Main.this, "nav_work", p);
                        toast("Guardado en Trabajo");
                    }
                }
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private void handlePickedPlace(Nav.Place p, boolean insertActive, boolean asStop) {
        if (p == null) return;
        if (insertActive) {
            insertStopIntoActive(p);
            return;
        }
        if (asStop) {
            pendingStops.add(p);
            toast(I18n.t("Parada añadida") + ": " + p.name);
            openNavSearch(false);
            return;
        }
        startInternalNav(p);
    }

    private void insertStopIntoActive(Nav.Place stop) {
        Nav.Route cur = Nav.active;
        if (cur == null) { startInternalNav(stop); return; }
        List<Nav.Place> stops = new ArrayList<Nav.Place>();
        if (cur.waypoints != null) stops.addAll(cur.waypoints);
        stops.add(stop);
        Nav.Place dest = new Nav.Place();
        dest.name = cur.destName != null && cur.destName.length() > 0
                ? cur.destName : String.format(java.util.Locale.US, "%.5f, %.5f", cur.destLat, cur.destLon);
        dest.lat = cur.destLat;
        dest.lon = cur.destLon;
        stops.add(dest);
        startRouteWithStops(stops, dest, false);
    }

    private void searchNav(String query, final boolean insertActive, final boolean asStop) {
        final String q = query == null ? "" : query.trim();
        if (q.length() == 0) { toast("Escribe una dirección o lugar"); return; }
        toast("Buscando…");
        Nav.search(this, q, wLat, wLon, useMph(), new Nav.SearchCb() {
            public void onResult(List<Nav.Place> places, String error) {
                if (isFinishing()) return;
                if (places == null) { toast(error != null ? error : "No se pudo buscar"); return; }
                if (places.isEmpty()) { toast("No se encontraron resultados"); return; }
                String pfx = I18n.t(insertActive || asStop ? "Elegir parada" : "Resultados");
                showSearchResults(pfx + ": " + q, places, insertActive, asStop);
            }
        });
    }

    private void searchNavCategory(String cat, final String label, final boolean insertActive, final boolean asStop) {
        if (Double.isNaN(wLat) || Double.isNaN(wLon)) seedLastLocation();
        toast("Buscando…");
        Nav.searchCategory(cat, wLat, wLon, useMph(), new Nav.SearchCb() {
            public void onResult(List<Nav.Place> places, String error) {
                if (isFinishing()) return;
                if (places == null || places.isEmpty()) {
                    toast(error != null ? error : "No se encontraron resultados");
                    return;
                }
                String tit = insertActive || asStop ? I18n.t("Elegir parada") + " · " + I18n.t(label) : I18n.t(label);
                showSearchResults(tit, places, insertActive, asStop);
            }
        });
    }

    private void showSearchResults(String title, final List<Nav.Place> places, final boolean insertActive, final boolean asStop) {
        BaseAdapter ad = new BaseAdapter() {
            public int getCount() { return places.size(); }
            public Object getItem(int pos) { return places.get(pos); }
            public long getItemId(int pos) { return pos; }
            public View getView(int pos, View cv, ViewGroup parent) {
                Nav.Place p = places.get(pos);
                LinearLayout row = new LinearLayout(Main.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding((int) (22 * s), (int) (12 * s), (int) (22 * s), (int) (12 * s));
                TextView t = tv("", 16, TEXT, true);
                t.setText(p.name);
                row.addView(t);
                if (p.sub != null && p.sub.length() > 0) {
                    TextView sub = tv("", 13, MUTED, false);
                    sub.setText(p.sub);
                    sub.setPadding(0, (int) (2 * s), 0, 0);
                    row.addView(sub);
                }
                return row;
            }
        };
        new TB(Main.this, dlg()).raw()
            .setTitle(title)
            .setAdapter(ad, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { handlePickedPlace(places.get(w), insertActive, asStop); }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void startInternalNav(final Nav.Place dest) {
        List<Nav.Place> stops = new ArrayList<Nav.Place>(pendingStops.size() + 1);
        stops.addAll(pendingStops);
        stops.add(dest);
        boolean wantAlt = stops.size() == 1 && prefs.getBoolean("nav_alternatives", true);
        startRouteWithStops(stops, dest, wantAlt);
    }

    private void startRouteWithStops(final List<Nav.Place> stops, final Nav.Place dest, boolean wantAlt) {
        if (Double.isNaN(wLat) || Double.isNaN(wLon)) seedLastLocation();
        if (Double.isNaN(wLat) || Double.isNaN(wLon)) {
            toast("Esperando señal GPS para calcular la ruta");
            return;
        }
        Nav.saveRecent(this, dest);
        if (Nav.active == null) Nav.savedHeadingUp = prefs.getBoolean("map_heading", false);
        toast("Calculando ruta…");
        Nav.fetchRoutes(this, wLat, wLon, stops, wantAlt && stops.size() == 1, new Nav.RoutesCb() {
            public void onRoutes(List<Nav.Route> routes, String error) {
                if (isFinishing()) return;
                if (routes == null || routes.isEmpty()) {
                    android.util.Log.w("StradaNav", "sin ruta: " + error);
                    toast(error != null ? error : "No se pudo calcular la ruta");
                    return;
                }
                pendingStops.clear();
                if (routes.size() == 1) activateChosenRoute(routes.get(0));
                else showRouteChooser(routes);
            }
        });
    }

    private void activateChosenRoute(Nav.Route r) {
        Nav.activate(this, r);
        Nav.announceStart(this, r);
        LiveMapView.headingUp = true;
        updateNavHud();
        if (liveMap != null) liveMap.invalidate();
        if (!r.incidents.isEmpty()) {
            toast(r.incidents.size() + " " + I18n.t(r.incidents.size() == 1 ? "incidente en la ruta" : "incidentes en la ruta"));
        } else if ("cache".equals(r.source)) {
            toast("Ruta guardada (sin conexión)");
        } else if (r.offline) {
            toast("Ruta aproximada (mapa local sin conexión)");
        }
    }

    private void showRouteChooser(final List<Nav.Route> routes) {
        final Nav.Route prevActive = Nav.active;
        Nav.active = routes.get(0);
        Nav.altRoutes = new ArrayList<Nav.Route>(routes.subList(1, routes.size()));
        if (liveMap != null) liveMap.invalidate();

        final boolean mph = useMph();
        BaseAdapter ad = new BaseAdapter() {
            public int getCount() { return routes.size(); }
            public Object getItem(int pos) { return routes.get(pos); }
            public long getItemId(int pos) { return pos; }
            public View getView(int pos, View cv, ViewGroup parent) {
                Nav.Route rt = routes.get(pos);
                LinearLayout row = new LinearLayout(Main.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding((int) (22 * s), (int) (12 * s), (int) (22 * s), (int) (12 * s));
                TextView t = tv("", 16, pos == 0 ? RED : TEXT, true);
                t.setText(I18n.t("Ruta") + " " + (pos + 1) + (pos == 0 ? " · " + I18n.t("Más rápida") : ""));
                row.addView(t);
                String detail = Nav.fmtEta(Main.this, rt.totalDurS, rt.totalDistM, mph);
                int dMin = (int) Math.round(rt.trafficDelayS / 60.0);
                if (dMin >= 1) detail += " (+" + dMin + " min)";
                if (!rt.incidents.isEmpty()) {
                    detail += " · " + rt.incidents.size() + " " + I18n.t(rt.incidents.size() == 1 ? "incidente" : "incidentes");
                }
                TextView sub = tv("", 13, MUTED, false);
                sub.setText(detail);
                sub.setPadding(0, (int) (2 * s), 0, 0);
                row.addView(sub);
                return row;
            }
        };
        final boolean[] chosen = {false};
        new TB(Main.this, dlg()).raw()
            .setTitle(I18n.t("Elegir ruta"))
            .setAdapter(ad, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    chosen[0] = true;
                    activateChosenRoute(routes.get(w));
                }
            })
            .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (!chosen[0]) {
                        Nav.active = prevActive;
                        Nav.altRoutes = null;
                        updateNavHud();
                        if (liveMap != null) liveMap.invalidate();
                    }
                }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface d) {
                    if (!chosen[0]) {
                        Nav.active = prevActive;
                        Nav.altRoutes = null;
                        updateNavHud();
                        if (liveMap != null) liveMap.invalidate();
                    }
                }
            })
            .show();
    }

    private void showIncidentsDialog(Nav.Route r) {
        if (r == null || r.incidents.isEmpty()) return;
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding((int) (20 * s), (int) (8 * s), (int) (20 * s), (int) (8 * s));
        for (int i = 0; i < r.incidents.size(); i++) {
            Nav.Incident inc = r.incidents.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(round(INNER, 10));
            row.setPadding((int) (12 * s), (int) (9 * s), (int) (12 * s), (int) (9 * s));
            TextView t = tv("", 14, TEXT, true);
            t.setSingleLine(false);
            t.setText(inc.desc != null && inc.desc.length() > 0 ? inc.desc : Nav.incidentLabel(inc.category));
            row.addView(t);
            if (inc.delayS >= 60) {
                TextView sub = tv("", 12, 0xFFF5A623, false);
                sub.setText("+" + Math.round(inc.delayS / 60.0) + " min");
                sub.setPadding(0, (int) (2 * s), 0, 0);
                row.addView(sub);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = (int) (6 * s);
            lay.addView(row, rlp);
        }
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(lay);
        new TB(this, dlg()).wide()
            .setTitle("Incidentes en la ruta")
            .setView(sc)
            .setPositiveButton("Cerrar", null)
            .show();
    }

    private void stopInternalNav() {
        Nav.stop(this);
        LiveMapView.headingUp = prefs.getBoolean("map_heading", false);
        updateNavHud();
        if (liveMap != null) liveMap.invalidate();
    }

    private void openNavMenu() {
        final Nav.Route r = Nav.active;
        if (r == null) { openNavSearch(); return; }
        final boolean voice = prefs.getBoolean("nav_voice", true);
        final boolean clockEta = prefs.getBoolean("nav_eta_clock", false);
        final boolean heading = LiveMapView.headingUp;
        final Nav.Place curDest = new Nav.Place();
        curDest.name = r.destName != null && r.destName.length() > 0
                ? r.destName : String.format(java.util.Locale.US, "%.5f, %.5f", r.destLat, r.destLon);
        curDest.lat = r.destLat;
        curDest.lon = r.destLon;
        final boolean favNow = Nav.isFavorite(this, curDest);

        final java.util.List<String> labels = new java.util.ArrayList<String>();
        final java.util.List<Runnable> actions = new java.util.ArrayList<Runnable>();
        if (!r.incidents.isEmpty()) {
            labels.add(I18n.t("Incidentes en la ruta") + " (" + r.incidents.size() + ")");
            actions.add(new Runnable() { public void run() { showIncidentsDialog(r); } });
        }
        labels.add(I18n.t("Cambiar destino"));
        actions.add(new Runnable() { public void run() { openNavSearch(false); } });
        labels.add(I18n.t("Añadir parada en ruta"));
        actions.add(new Runnable() { public void run() { openNavSearch(true); } });
        labels.add(I18n.t(clockEta ? "Mostrar tiempo restante" : "Mostrar hora de llegada"));
        actions.add(new Runnable() {
            public void run() {
                prefs.edit().putBoolean("nav_eta_clock", !clockEta).apply();
                updateNavHud();
            }
        });
        labels.add(I18n.t(voice ? "Silenciar voz de guía" : "Activar voz de guía"));
        actions.add(new Runnable() {
            public void run() {
                prefs.edit().putBoolean("nav_voice", !voice).apply();
                toast(!voice ? "Voz activada" : "Voz silenciada");
            }
        });
        labels.add(I18n.t(heading ? "Vista Norte arriba" : "Vista en dirección de marcha"));
        actions.add(new Runnable() {
            public void run() {
                LiveMapView.headingUp = !heading;
                if (liveMap != null) liveMap.invalidate();
            }
        });
        labels.add(I18n.t("Recalcular ruta"));
        actions.add(new Runnable() {
            public void run() {
                List<Nav.Place> stops = new ArrayList<Nav.Place>();
                if (r.waypoints != null) stops.addAll(r.waypoints);
                stops.add(curDest);
                startRouteWithStops(stops, curDest, false);
            }
        });
        labels.add(I18n.t(favNow ? "Quitar de favoritos" : "Añadir a favoritos"));
        actions.add(new Runnable() {
            public void run() {
                boolean added = Nav.toggleFavorite(Main.this, curDest);
                toast(added ? "Añadido a favoritos" : "Quitado de favoritos");
            }
        });
        labels.add(I18n.t("Guardar como Casa"));
        actions.add(new Runnable() {
            public void run() {
                Nav.setSavedPlace(Main.this, "nav_home", curDest);
                toast("Guardado en Casa");
            }
        });
        labels.add(I18n.t("Guardar como Trabajo"));
        actions.add(new Runnable() {
            public void run() {
                Nav.setSavedPlace(Main.this, "nav_work", curDest);
                toast("Guardado en Trabajo");
            }
        });
        labels.add(I18n.t("Abrir en otra app"));
        actions.add(new Runnable() { public void run() { openExternalNavApp(); } });
        labels.add(I18n.t("Detener navegación"));
        actions.add(new Runnable() { public void run() { stopInternalNav(); } });

        String title = r.destName != null && r.destName.length() > 0 ? r.destName : I18n.t("Navegación en el mapa");
        if ("cache".equals(r.source)) title = title + " (" + I18n.t("ruta guardada") + ")";
        else if (r.offline) title = title + " (" + I18n.t("sin conexión · aproximada") + ")";
        else if ("tomtom".equals(r.source)) title = title + " (" + I18n.t("con tráfico") + ")";
        new TB(this, dlg()).raw()
            .setTitle(title)
            .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w >= 0 && w < actions.size()) actions.get(w).run();
                }
            })
            .setNegativeButton("Cerrar", null)
            .show();
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
        if ("nav_app".equals(key) && "internal".equals(c)) {
            return I18n.t("En el mapa de StradaOS");
        }
        if (c != null) return labelOf(c);
        String p = firstInstalled(pkgs);
        return p != null ? "Automático · " + labelOf(p) : none;
    }

    // ───────────────────────── helpers de UI ─────────────────────────

    private TextView tv(String t, float size, int color, boolean medium) {
        TextView v = new TextView(this);
        v.setText(I18n.t(t));
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s * FONT);
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
        updateIdleClock();
        if (timeTv == null) return;
        Date now = new Date();
        String fmt = prefs.getString("clock", "auto");
        boolean h24 = "24".equals(fmt) || ("auto".equals(fmt) && android.text.format.DateFormat.is24HourFormat(this));
        timeTv.setText(I18n.sdf(h24 ? "HH:mm" : "h:mm a").format(now));
        String d = I18n.sdf(vertical && !(premium && !mapOnly) ? "EEE d MMM" : I18n.datePattern()).format(now);
        dateTv.setText(d.substring(0, 1).toUpperCase(I18n.loc) + d.substring(1));
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String saludo = I18n.t(hr >= 5 && hr < 12 ? "Buenos días" : (hr >= 12 && hr < 19 ? "Buenas tardes" : "Buenas noches"));
        String nombre = prefs.getString("user_name", "").trim();
        greetTv.setText(nombre.length() > 0 ? saludo + ", " + nombre : saludo);
    }

    private final LocationListener gpsListener = new LocationListener() {
        public void onLocationChanged(Location l) {
            if (!validLoc(l)) return;   // 0,0 sin señal (#37): cuenta como «sin GPS»
            lastFix = SystemClock.elapsedRealtime();
            lastFixAny = lastFix;
            setGps(true, l.hasSpeed() ? l.getSpeed() * 3.6f : 0);
            feedMap(l);
            sunSpot(l.getLatitude(), l.getLongitude());
            saveLoc(l);
        }
        public void onStatusChanged(String p, int st, Bundle e) { }
        public void onProviderEnabled(String p) { }
        public void onProviderDisabled(String p) { setGps(false, 0); }
    };

    /** Ubicación por red (WiFi/datos): solo para ubicar el mapa mientras llega el GPS. */
    private final LocationListener netListener = new LocationListener() {
        public void onLocationChanged(Location l) { if (lastFix == 0 && validLoc(l)) feedMap(l); }
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

    private float lastKmh;
    private boolean lastOk;

    private void setGps(boolean ok, float kmh) {
        lastKmh = kmh;
        lastOk = ok;
        if (!ok && speedTv != null) lastFix = 0;
        showSpeed();
    }

    /**
     * 2.29: con el escáner OBD-II conectado, el velocímetro del inicio usa la velocidad del carro (más exacta y sin el
     * retraso del GPS, también en túneles). Sin datos frescos del escáner (menos de 3 s) vuelve solo al GPS.
     * El auto simulado no cuenta, para no mostrar una velocidad falsa (salvo en StradaOS Prueba con «obd-vel-demo:1»).
     * Devuelve km/h o NaN.
     */
    private float obdKmh() {
        if (!obdOn() || !prefs.getBoolean("obd_speed", true) || Obd.status != Obd.LIVE || !Obd.SPEED.has()) return Float.NaN;
        if (SystemClock.elapsedRealtime() - Obd.SPEED.at > 3000) return Float.NaN;
        if ("demo".equals(prefs.getString("obd_addr", "demo")) && !obdSpeedDemo
            && !(getPackageName().endsWith(".debug") && prefs.getBoolean("obd_speed_demo", false))) return Float.NaN;
        return Obd.SPEED.value;
    }
    private boolean obdSpeedDemo;
    private boolean speedFromObd;

    private void showSpeed() {
        float ob = obdKmh();
        boolean fromObd = !Float.isNaN(ob);
        speedFromObd = fromObd;
        boolean ok = fromObd || lastOk;
        float kmh = fromObd ? ob : lastKmh;
        float conv = useMph() ? kmh * 0.621371f : kmh;
        if (!fromObd) conv = speedFix(conv);      // la corrección del velocímetro es para el GPS
        if (idleView != null) {
            float iv = conv;
            if (idleSpeedTv != null) idleSpeedTv.setText(ok ? String.valueOf(Math.round(kmh < 2 ? 0 : iv)) : "--");
            if (idleGauge != null) idleGauge.setSpeed(kmh < 2 ? 0 : iv, ok);
        }
        if (speedTv == null) return;
        float v = conv;
        speedTv.setText(ok ? String.valueOf(Math.round(kmh < 2 ? 0 : v)) : "--");
        if (carGauge != null) carGauge.setSpeed(kmh < 2 ? 0 : v, ok);
        if (mapGauge != null) mapGauge.setSpeed(kmh < 2 ? 0 : v, ok);
        if (faroGauge != null) {
            faroGauge.setSpeed(kmh < 2 ? 0 : v, ok);
            int lim = Nav.active != null && Nav.curSpeedLimitKmh > 0
                ? (useMph() ? Math.max(5, (int) (Math.round((Nav.curSpeedLimitKmh / 1.60934) / 5.0) * 5)) : Nav.curSpeedLimitKmh) : 0;
            faroGauge.setLimit(lim);
        }
        if (topSpeedTv != null) topSpeedTv.setText((ok ? String.valueOf(Math.round(kmh < 2 ? 0 : v)) : "--") + " " + (useMph() ? "mph" : "km/h"));
        int gs = ok ? 0 : gpsState();
        gpsTv.setText(fromObd ? "OBD" : ok ? "GPS" : I18n.t(gs == 1 ? "Sin permiso" : gs == 2 ? "GPS apagado" : "Buscando GPS"));
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
        // 2.30 (#50): con Android Auto o CarPlay conectado la música viene del celular; arrancar la app de la pantalla
        // hacía sonar dos canciones a la vez. ▶ queda como tecla multimedia normal.
        if (projectionActive()) { BtBridge.log("▶ no abre la app de música: Android Auto / CarPlay conectado"); return false; }
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

    /**
     * Android Auto o CarPlay sonando: Open Headunit vivo, celular por USB en modo Android Auto, o una app de proyección
     * (ZLink, AutoKit…) con notificación multimedia. Una app de proyección solo abierta, esperando el celular, no cuenta.
     */
    private boolean projectionActive() {
        try {
            if (aaRunning()) return true;
            if (aaUsbState() == 1) return true;
            return !Projection.media.isEmpty();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Si suena Bluetooth, controla directamente la app Bluetooth de fábrica (com.clw.btmusic). */
    private boolean sendBtCommand(int code) {
        if (!"Bluetooth".equals(curSource)) return false;
        if (bt != null && bt.command(code == KeyEvent.KEYCODE_MEDIA_NEXT ? 1 : code == KeyEvent.KEYCODE_MEDIA_PREVIOUS ? -1 : 0)) {
            h.postDelayed(new Runnable() { public void run() { if (bt != null) bt.refresh(); } }, 700);
            return true;
        }
        if (!clwBt) return false; // sin la app de Clinkway (issue #30)
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
        if ("bt".equals(t.origin) && t.positionMs >= 0) { btPosMs = t.positionMs; btPosAt = now; }
        // Pantalla HCN con una canción local sonando (issue #5): el celular conectado por Bluetooth manda su canción
        // aunque esté en pausa; no reemplaza la del equipo. Al cambiar de fuente, el equipo borra el archivo y entra.
        if ("bt".equals(t.origin) && "hcn".equals(curOrigin) && Hcn.blocksBt()) return;
        boolean hasContent = t.title != null || t.artist != null;
        // Un aviso sin pista de otra fuente (p. ej. el sistema cuando entra la cámara de reversa)
        // no debe borrar ni pausar lo que muestra la fuente actual.
        if (curOrigin != null && !curOrigin.equals(t.origin) && !hasContent) return;
        // Con Android Auto conectado el celular manda la misma canción también por Bluetooth (AVRCP), a cada segundo
        // y a veces sin título (solo tiempo o estado). Mientras Android Auto sea la fuente, Bluetooth solo aporta
        // tiempo y estado: nunca cambia la etiqueta ni borra la pista. Antes esos avisos sin título ponían
        // «Bluetooth» y la notificación de Open Headunit volvía a poner «Android Auto»: la etiqueta titilaba.
        // Al quitar el cable, la canción de Bluetooth (cada 2 s) puede llegar antes que checkAaGone: antes cambiaba la
        // etiqueta a «Bluetooth» y la carátula de Android Auto se quedaba (Andrés, 16 nov.). Primero se hace la
        // desconexión completa (carátula fuera, registro, app Bluetooth) y después sigue lo de Bluetooth.
        if ("bt".equals(t.origin) && "Android Auto".equals(curSource) && !"rcc".equals(curOrigin) && !aaRunning())
            checkAaGone(false);
        boolean aaOwns = "rcc".equals(curOrigin) || ("Android Auto".equals(curSource) && aaRunning());
        // La canción vino de la notificación de Open Headunit (celular solo por cable, sin Bluetooth): lo que mande
        // Bluetooth con otro título es de otro celular o lo que la app Bluetooth recordaba. No lo reemplaza.
        // Excepción: Android Auto en pausa y el Bluetooth sí está sonando otra cosa.
        if ("aa".equals(curOrigin) && "bt".equals(t.origin) && aaRunning()
                && (t.clear || (t.title != null && !sameTitle(t.title, NotifArt.lastTitle)
                    && !textsMatch(NotifArt.lastTexts, t.title)))) {
            boolean aaPaused = Boolean.FALSE.equals(NotifArt.lastPlaying) || now - NotifArt.lastAt > 60000;
            if (t.clear || !aaPaused || !Boolean.TRUE.equals(t.playing)) return;
        }
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
            posSrc = "canción nueva";
            durMs = -1;
            if (!t.artSet) curArt = null;
            // ¿La notificación de Open Headunit ya trae la carátula de esta canción?
            if (!t.artSet && NotifArt.lastArt != null && textsMatch(NotifArt.lastTexts, t.title) && aaRunning()) {
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
        if (t.positionMs >= 0) {
            // Con Android Auto, el tiempo bueno es el de la notificación de Open Headunit: si otra fuente (Bluetooth)
            // manda uno que se aleja más de 3 s, es el que no se corrigió al adelantar en el celular y se ignora.
            long aa = "aa".equals(t.origin) ? -1 : aaPosNow();
            if (aa >= 0 && Math.abs(t.positionMs - aa) > 3000) { pos = aa; posKnown = true; posSrc = "Android Auto"; }
            else { pos = t.positionMs; posKnown = true; posSrc = String.valueOf(t.origin); }
        }
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
    private static boolean aaSim;   // gancho «aa-simular:1» (pruebas)

    private boolean aaRunning() {
        if (aaSim) return true;
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
        if ("aa".equals(curOrigin)) {
            // La canción solo venía por Android Auto: se borra y entra lo que tenga Bluetooth (si hay).
            curOrigin = null;
            curTitle = curArtist = curSource = null;
            durMs = -1;
            posKnown = false;
            playingState = false;
        } else curSource = "Bluetooth";
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

    /**
     * Notificación de Open Headunit. Si es la canción que ya se muestra, pone la carátula (y «Android Auto»).
     * Si es otra canción y Android Auto está conectado, la notificación pasa a ser la fuente: es el caso del
     * celular conectado solo por cable, sin Bluetooth, donde el título nunca llega por Bluetooth.
     */
    private void applyAaNotif(Bitmap art, java.util.List<String> texts) {
        long now = SystemClock.elapsedRealtime();
        String nt = NotifArt.lastTitle;
        boolean fresh = nt != null && NotifArt.lastAt > 0 && now - NotifArt.lastAt < 15000;
        boolean match = curTitle != null && (sameTitle(nt, curTitle) || textsMatch(texts, curTitle));
        if (!match) {
            if (!fresh || "rcc".equals(curOrigin) || "ms".equals(curOrigin) || !aaRunning()) return;
            BtBridge.log("Android Auto: " + nt);
            curOrigin = "aa";
            curTitle = nt;
            curArtist = NotifArt.lastArtist;
            curSource = "Android Auto";
            curArt = art;
            durMs = NotifArt.lastDurMs > 0 ? NotifArt.lastDurMs : -1;
            posBaseMs = Math.max(0, NotifArt.lastPosMs);
            posBaseAt = now;
            posKnown = NotifArt.lastPosMs >= 0;
            if (posKnown) posSrc = "Android Auto";
            if (NotifArt.lastPlaying != null) playingState = NotifArt.lastPlaying;
            aaWaitTitle = null;
            h.removeCallbacks(aaWait);
            renderTrack();
            return;
        }
        boolean own = "aa".equals(curOrigin);
        // Al quitar el cable Open Headunit manda una última notificación: sin Android Auto no se pone su carátula.
        if (!aaRunning()) return;
        if (art == null && !own) {
            // sin carátula no cambia la etiqueta ni la carátula, pero el tiempo sí se corrige
            if (fresh && aaFixPos(now)) renderTrack();
            return;
        }
        if (aaWaitTitle != null && sameTitle(aaWaitTitle, curTitle)) { aaWaitTitle = null; h.removeCallbacks(aaWait); }
        if (art != null) curArt = art;
        curSource = "Android Auto";
        if (own && fresh) {
            // Sin Bluetooth, el tiempo y el estado también salen de la notificación.
            if (curArtist == null && NotifArt.lastArtist != null) curArtist = NotifArt.lastArtist;
            if (NotifArt.lastDurMs > 0) durMs = NotifArt.lastDurMs;
            if (NotifArt.lastPosMs >= 0) { posBaseMs = NotifArt.lastPosMs; posBaseAt = now; posKnown = true; posSrc = "Android Auto"; }
            if (NotifArt.lastPlaying != null) playingState = NotifArt.lastPlaying;
        } else if (fresh) {
            aaFixPos(now);
        }
        renderTrack();
    }

    /**
     * La canción llega por otra fuente (el control remoto de Open Headunit, «rcc», o Bluetooth), que no trae el
     * tiempo o no lo corrige al adelantar desde el celular (Andrés, 18 nov.: adelantó de 0:46 a 2:34 y StradaOS
     * siguió en 1:11). La notificación de Open Headunit sí lo trae cada segundo: si se aleja más de 3 s de lo que
     * cuenta StradaOS, manda la notificación. Con 3 s de margen no se mueve por el redondeo. Devuelve si corrigió.
     */
    private boolean aaFixPos(long now) {
        if (NotifArt.lastPosMs < 0) return false;
        if (durMs <= 0 && NotifArt.lastDurMs > 0) durMs = NotifArt.lastDurMs;
        if (posKnown && Math.abs(currentPos() - NotifArt.lastPosMs) <= 3000) return false;
        posBaseMs = NotifArt.lastPosMs;
        posBaseAt = now;
        posKnown = true;
        posSrc = "Android Auto (corregido)";
        return true;
    }

    /** Tiempo de la notificación de Open Headunit si es de la canción actual y de hace 5 s o menos; si no, -1. */
    private long aaPosNow() {
        if (NotifArt.lastPosMs < 0 || NotifArt.lastAt == 0 || curTitle == null) return -1;
        long age = SystemClock.elapsedRealtime() - NotifArt.lastAt;
        if (age > 5000 || !aaRunning()) return -1;
        if (!sameTitle(NotifArt.lastTitle, curTitle) && !textsMatch(NotifArt.lastTexts, curTitle)) return -1;
        return NotifArt.lastPosMs + (Boolean.FALSE.equals(NotifArt.lastPlaying) ? 0 : age);
    }

    private void showPlayerDiag() {
        String msg = "Fuente: " + (curOrigin != null ? curOrigin : "—") + " / " + (curSource != null ? curSource : "—")
                + "\nPista: " + curTitle + " — " + curArtist
                + "\nCarátula: " + (curArt != null ? curArt.getWidth() + "x" + curArt.getHeight() : "no")
                + "\nTiempo en StradaOS: " + (currentPos() / 1000) + "/" + (durMs / 1000) + " s · " + posSrc
                + "\nÚltimo tiempo por Bluetooth: " + (btPosMs < 0 ? "ninguno"
                    : (btPosMs / 1000) + " s, hace " + ((SystemClock.elapsedRealtime() - btPosAt) / 1000) + " s")
                + "\n\nLector de notificaciones (accesibilidad): " + (NotifArt.running ? "ACTIVO" : "apagado")
                + "\nÚltima notificación de Open Headunit: " + (NotifArt.lastAt == 0 ? "ninguna"
                    : ((SystemClock.elapsedRealtime() - NotifArt.lastAt) / 1000) + " s, carátula "
                    + (NotifArt.lastArt != null ? "sí" : "no") + ", textos " + NotifArt.lastTexts
                    + "\n  leída: «" + NotifArt.lastTitle + "» — " + NotifArt.lastArtist
                    + " · " + (NotifArt.lastPosMs / 1000) + "/" + (NotifArt.lastDurMs / 1000) + " s · "
                    + (NotifArt.lastPlaying == null ? "estado ?" : NotifArt.lastPlaying ? "sonando" : "en pausa"))
                + "\nOpen Headunit corriendo: " + (processRunning(PKG_HUR) ? "sí" : "no")
                + " · celular por USB en modo Android Auto: " + (aaUsbState() == 1 ? "sí" : aaUsbState() == 0 ? "no" : "no se sabe")
                + "\nAndroid Auto conectado (para StradaOS): " + (aaRunning() ? "sí" : "no")
                + "\nApps de Android Auto / CarPlay con notificación: " + Projection.describe(Projection.any)
                + " · multimedia: " + Projection.describe(Projection.media)
                + "\n▶ sin nada sonando abre tu app de música: " + (projectionActive() ? "no (Android Auto / CarPlay conectado)" : "sí")
                + "\n\nMúsica de cualquier app (Android 5+): " + (SDK < 21 ? "no aplica en este Android"
                    : "acceso a notificaciones " + (Modern.notificationAccess(this) ? "SÍ" : "NO") + " · lector conectado " + (MediaListener.connected ? "sí" : "no")
                    + "\nSonido de música en el sistema: " + (((AudioManager) getSystemService(AUDIO_SERVICE)).isMusicActive() ? "sí" : "no")
                    + "\nSesiones multimedia: " + MediaWatcher.report())
                + "\n\n" + Hcn.report()
                + "\nBluetooth del sistema (AVRCP): " + (NowPlaying.avrcpAt == 0 ? "ningún aviso"
                    : NowPlaying.avrcpCount + " avisos, el último hace " + ((SystemClock.elapsedRealtime() - NowPlaying.avrcpAt) / 1000)
                    + " s: " + NowPlaying.avrcpLast)
                + "\nApp Bluetooth conectada: " + (bt != null && bt.isConnected() ? "sí" : "no")
                + "\nAudio Bluetooth automático: " + (clwBt ? (btAutoOn() ? "activado" : "desactivado") : "no aplica (otra marca)")
                + (clwBt ? "" : "\nApp de música Bluetooth: " + (btMusicApp() != null ? btMusicApp().getPackageName() : "no encontrada")
                    + " · abrir al conectar: " + (prefs.getBoolean("bt_open_connect", false) ? "sí" : "no"))
                + "\nSonido en Bluetooth (estimado): " + (btRouted ? "sí" : "no") + " · app al frente: " + topPackage()
                + "\nActivaciones: " + BtBridge.activations
                + "\n\nEventos Bluetooth:\n" + BtBridge.logText();
        new TB(this, dlg()).diag()
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
            notifHint = wantsNotifAccess();
            trackTv.setText(I18n.t(notifHint ? "Música sonando" : "Nada sonando"));
            artistTv.setText(I18n.t(notifHint ? "Toca para ver la canción" : "Radio, Bluetooth o USB"));
            sourceTv.setText("");
            timePosTv.setText("");
            playerArt.setArt(null);
            playerArt.setProgress(-1);
            if (faroBar != null) faroBar.set(-1);
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
            if (faroBar != null) faroBar.set(p / (float) durMs);
        } else if (durMs > 0) {
            playerArt.setProgress(-1);
            timePosTv.setText(fmt(durMs));
            if (faroBar != null) faroBar.set(-1);
        } else {
            playerArt.setProgress(-1);
            timePosTv.setText("");
            if (faroBar != null) faroBar.set(-1);
        }
    }

    private final Runnable progressTick = new Runnable() {
        public void run() {
            updateProgress();
            h.postDelayed(this, 1000);
        }
    };

    // ───────────────────────── configuración ─────────────────────────

    private void openSettings() { showSettings(C_VEHICLE); }

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

    interface TextCb { void on(String v); }

    /** Texto libre en una ventana (nombre de un mantenimiento, etc.). */
    private void askText(String title, String def, final TextCb cb) {
        final android.widget.EditText et = field();
        et.setSingleLine(true);
        et.setText(def);
        et.setSelection(et.getText().length());
        FrameLayout wrap = new FrameLayout(this);
        int pad = (int) (20 * s);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(et);
        new TB(this, dlg())
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { cb.on(et.getText().toString()); }
            })
            .setNegativeButton("Cancelar", null)
            .show();
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
                    if ("tomtom_key".equals(key) || "traffic_url".equals(key)) {
                        if ("tomtom_key".equals(key) && et.getText().toString().trim().length() > 0) {
                            prefs.edit().putBoolean("map_traffic", true).apply();
                            toast("Tráfico en vivo activado");
                        }
                        LiveMapView.clearTrafficCache();
                        if (liveMap != null) liveMap.invalidate();
                    }
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

    /** Cambia el idioma («auto», «es», «en», «pt», «it», «de», «ms», «ar») y reconstruye la pantalla. */
    private void setLanguage(String code) {
        prefs.edit().putString("lang", code).apply();
        I18n.set(this, "auto".equals(code) ? I18n.system() : code);
        rebuild();
    }

    /** Primer paso del asistente: idioma (preseleccionado el del sistema). */
    private void wizard() {
        int sel = Math.max(0, java.util.Arrays.asList(I18n.CODES).indexOf(I18n.lang));
        wizardDlg = new TB(this, dlg()).raw()
            .setTitle("Idioma · Language · Langue · Lingua · Sprache · Bahasa · Dil · Nyelv · اللغة")
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
                    chooseFontSize(new Runnable() { public void run() {
                        prefs.edit().putBoolean("setup_done", true).apply();
                        rebuild();
                        reviewShortcuts(new Runnable() { public void run() {
                            if (!askDefaultHome(new Runnable() { public void run() { pickPhoto(); } })) pickPhoto();
                        } });
                    }});
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

    /** Asistente (2.19): tamaño de la letra. */
    private void chooseFontSize(final Runnable next) {
        String[] o = {I18n.t("Pequeña"), I18n.t("Normal"), I18n.t("Grande"), I18n.t("Muy grande")};
        new TB(this, dlg()).raw()
            .setTitle(I18n.t("Tamaño de la letra"))
            .setSingleChoiceItems(o, prefs.getInt("font_size", 1), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                d.dismiss();
                prefs.edit().putInt("font_size", w).apply();
                rebuild();
                next.run();
            }})
            .setCancelable(false)
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
        if (req == REQ_BACKUP_CREATE || req == REQ_BACKUP_OPEN) {
            if (res == RESULT_OK && data != null && data.getData() != null) backupDocument(data.getData(), req == REQ_BACKUP_CREATE);
            return;
        }
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
        // 2.18 (Andrés): ya no se quita el fondo solo; la foto se usa tal cual (derecha) y quitarlo es opcional
        final ProgressDialogLite pd = new ProgressDialogLite("Abriendo la foto…");
        new Thread(new Runnable() {
            public void run() {
                Bitmap src = null;
                String err = null;
                boolean already = false;
                try {
                    src = CarPhoto.decode(opener);
                    already = CarPhoto.hasTransparency(src);
                } catch (Throwable e) {
                    err = e.getMessage();
                }
                final Bitmap fsrc = src;
                final String ferr = err;
                final boolean falready = already;
                runOnUiThread(new Runnable() {
                    public void run() {
                        pd.dismiss();
                        if (fsrc == null) { toast("No se pudo abrir la imagen" + (ferr != null ? ": " + ferr : "")); return; }
                        previewPhoto(fsrc, null, 0, falready);
                    }
                });
            }
        }).start();
    }

    /**
     * Vista previa de la foto. Sin recortar: se guarda tal cual (achicada). «Quitar el fondo» es opcional y se puede
     * repetir con más fuerza; «Usar la foto original» vuelve a la foto completa.
     */
    private void previewPhoto(final Bitmap src, final Bitmap cut, final int strength, final boolean already) {
        final Bitmap shown = cut != null ? CarPhoto.fitToCard(cut) : already ? CarPhoto.fitToCard(src) : CarPhoto.scaled(src, 900);
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(CARD);
        int pad = (int) (16 * s);
        box.setPadding(pad, pad, pad, pad);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(shown);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ivlp = new FrameLayout.LayoutParams((int) (396 * s), (int) (240 * s));
        ivlp.gravity = Gravity.CENTER;   // en pantallas anchas la foto quedaba pegada a la izquierda
        box.addView(iv, ivlp);
        String title = already ? "La foto ya trae fondo transparente"
                : cut == null ? "Vista previa"
                : "Sin fondo · nivel " + (strength + 1) + " de 4" + (strength >= 2 ? " (también quita el reflejo del piso)" : "");
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(box);
        final AlertDialog[] dref = new AlertDialog[1];
        if (cut != null) {
            // botón de verdad (no un letrero): volver a la foto completa
            TextView back = tv("Usar la foto original, con fondo", 14, TEXT, true);
            back.setGravity(Gravity.CENTER);
            back.setBackground(round(BTN, 12));
            back.setPadding(pad, (int) (12 * s), pad, (int) (12 * s));
            back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (dref[0] != null) try { dref[0].dismiss(); } catch (Throwable ignored) { }
                previewPhoto(src, null, 0, false);
            }});
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(pad, pad / 2, pad, 0);
            col.addView(back, lp);
        }
        AlertDialog.Builder b = new TB(this, dlg())
            .setTitle(title)
            .setView(col)
            .setPositiveButton("Usar esta", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (CarPhoto.save(Main.this, shown)) { toast("Foto guardada"); rebuild(); }
                    else toast("No se pudo guardar la foto");
                }
            })
            .setNegativeButton("Cancelar", null);
        if (!already && strength < 3) {
            b.setNeutralButton(cut == null ? "Quitar el fondo" : "Quitar más fondo", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    final int next = cut == null ? 1 : strength + 1;
                    final ProgressDialogLite pd = new ProgressDialogLite("Quitando el fondo…");
                    new Thread(new Runnable() {
                        public void run() {
                            Bitmap c2 = null;
                            try { c2 = CarPhoto.removeBackground(src, next); } catch (Throwable ignored) { }
                            final Bitmap fc2 = c2;
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    pd.dismiss();
                                    if (fc2 == null && cut == null) { toast("No se detectó un fondo liso: se usa la foto tal cual"); previewPhoto(src, null, 0, false); }
                                    else previewPhoto(src, fc2 != null ? fc2 : cut, fc2 != null ? next : strength, false);
                                }
                            });
                        }
                    }).start();
                }
            });
        }
        dref[0] = b.show();
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
        LIGHT = !themeDark(mode);
        if (LIGHT) {
            BG = 0xFFE9ECF1; CARD = 0xFFFFFFFF; INNER = 0xFFF1F3F7; BTN = 0xFFE2E6EC; DOCK = 0xFFDDE1E8;
            TEXT = 0xFF14161B; MUTED = 0xFF6B7280; SOFT = 0xFF3A404C; CIRCLE = 0xFFFFFFFF; ICON = 0xFF2B303A;
        } else {
            BG = 0xFF15171C; CARD = 0xFF1E2128; INNER = 0xFF262A33; BTN = 0xFF2A2E37; DOCK = 0xFF0D0E11;
            TEXT = 0xFFF2F3F5; MUTED = 0xFF8B9099; SOFT = 0xFFC5C8CE; CIRCLE = 0xFF23262E; ICON = 0xFFFFFFFF;
        }
    }

    // ── Espera antes de cambiar de tema (Auto, Sistema y Luces) ──
    // Caso típico: de noche, al apagar el carro se apagan las luces y la pantalla pasaba a claro de golpe.
    // Cada sentido tiene su espera (por defecto 0 = como siempre). El tema que se ve se guarda en theme_last_dark,
    // así que al encender de nuevo la pantalla arranca con el último tema y la espera también se respeta.
    static final int[] THEME_DELAYS = {0, 1, 2, 3, 4, 5};
    static final String[] THEME_DELAY_LBL = {"Sin espera", "1 s", "2 s", "3 s", "4 s", "5 s"};
    private Boolean themeShown, themeWant;
    private long themeWantAt;
    private boolean themeForce;

    /** Lo que pide el modo elegido, sin espera: true = oscuro. */
    private boolean themeWanted(String mode) {
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        if ("system".equals(mode)) return systemNight();
        return darkHours();   // auto
    }

    /** Espera activada (Apariencia → Tema → «Espera para cambiar de tema»); apagada por defecto. */
    private boolean themeDelayOn() { return prefs.getBoolean("theme_delay_on", false); }

    /** ¿Oscuro? Aplica la espera elegida para cada sentido; los modos Oscuro y Claro cambian al instante. */
    private boolean themeDark(String mode) {
        boolean want = themeWanted(mode);
        boolean manual = "dark".equals(mode) || "light".equals(mode);
        int dDark = Math.min(5, prefs.getInt("theme_delay_dark", 0)), dLight = Math.min(5, prefs.getInt("theme_delay_light", 0));
        if (manual || themeForce || !themeDelayOn() || (dDark <= 0 && dLight <= 0)) { themeForce = false; return themeSettle(want); }
        if (themeShown == null) themeShown = prefs.getBoolean("theme_last_dark", want);   // al arrancar: el último tema
        if (want == themeShown) {
            themeWant = null;
            h.removeCallbacks(themeRecheck);
            return want;
        }
        long now = SystemClock.elapsedRealtime();
        if (themeWant == null || themeWant != want) { themeWant = want; themeWantAt = now; }
        long left = themeWantAt + (want ? dDark : dLight) * 1000L - now;
        if (left <= 0) return themeSettle(want);
        h.removeCallbacks(themeRecheck);
        h.postDelayed(themeRecheck, left + 100);
        return themeShown;
    }

    private boolean themeSettle(boolean dark) {
        themeShown = dark;
        themeWant = null;
        h.removeCallbacks(themeRecheck);
        if (prefs.getBoolean("theme_last_dark", !dark) != dark) prefs.edit().putBoolean("theme_last_dark", dark).apply();
        return dark;
    }

    /** Cambio pedido por el usuario (modo, horario…): se aplica ya, sin espera. */
    private void applyThemeNow() {
        themeForce = true;
        applyTheme();
    }

    private final Runnable themeRecheck = new Runnable() { public void run() {
        try {
            if (root == null) return;
            boolean was = LIGHT;
            applyTheme();
            if (was != LIGHT) { applyAccent(); rebuild(); }
        } catch (Throwable e) { Guard.log("Tema con espera: " + e); }
    }};

    // ── Modo «Sistema» en pantallas FYT: sigue las luces (Settings.System car_mode = 1 con las luces encendidas) ──
    // En estas pantallas el modo oscuro de Android está bloqueado (mNightModeLocked), así que uiMode nunca cambia.

    /** Luces del carro si la pantalla es FYT y las informa: 1 encendidas, 0 apagadas; -1 si no (se usa uiMode). */
    private int fytLights() { return fytOn() ? Fyt.lights(this) : -1; }

    /** Encendieron o apagaron las luces: en el modo «Sistema» de una FYT se revisa el tema (con la espera elegida). */
    private final android.database.ContentObserver lightsObs = new android.database.ContentObserver(new Handler(android.os.Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) {
            try {
                if (prefs == null || root == null || !"system".equals(prefs.getString("theme", "dark")) || !fytOn()) return;
                h.removeCallbacks(brightApply);
                h.postDelayed(brightApply, 300);
            } catch (Throwable ignored) { }
        }
    };

    // ── Equipo: marca de la pantalla (Ajustes → Tu vehículo → Equipo) ──

    /**
     * Pantalla FYT: elegida a mano o detectada (pref hw = auto | generic | fyt).
     * Por defecto «generic»: nada cambia hasta que el usuario elige FYT o Automático.
     */
    private boolean fytOn() {
        String hw = prefs.getString("hw", "generic");
        return "fyt".equals(hw) || ("auto".equals(hw) && Fyt.detect(this));
    }

    private Fyt fyt;
    private TextView carDataTv;

    /** Datos del carro de la FYT (batería y temperatura exterior): solo con la opción activada. */
    private void startFyt() {
        if (fyt != null || !fytOn() || !prefs.getBoolean("fyt_data", false)) return;
        try {
            fyt = new Fyt(this, new Fyt.Listener() { public void onCarData() { updateCarData(); } });
            fyt.start();
        } catch (Throwable e) { Guard.log("FYT: " + e); fyt = null; }
    }

    private void stopFyt() {
        if (fyt != null) try { fyt.stop(); } catch (Throwable ignored) { }
        fyt = null;
        updateCarData();
    }

    /** Datos del carro en la barra de arriba, a la izquierda de los íconos de Wi-Fi y Bluetooth. */
    private void addCarData(LinearLayout bar) {
        carDataTv = tv("", 15, TEXT, true);
        carDataTv.setPadding(0, 0, (int) (12 * s), 0);
        bar.addView(carDataTv);
        updateCarData();
    }

    /** «13,9 V · 17 °C»: lo que esté activado y haya llegado, o "" sin datos. */
    private String carDataText() { return carDataText(false); }

    /** all = true: todo lo que llegó, aunque no se muestre en el inicio (para Ajustes). */
    private String carDataText(boolean all) {
        if (fyt == null) return "";
        StringBuilder b = new StringBuilder();
        if (fyt.hasVolt() && (all || prefs.getBoolean("fyt_volt", false)))
            b.append(String.format(java.util.Locale.getDefault(), "%.1f V", fyt.voltDeci / 10f));
        if (fyt.hasTemp() && (all || prefs.getBoolean("fyt_temp", false))) {
            if (b.length() > 0) b.append("  ·  ");
            float c = fyt.tempDeci / 10f;
            boolean f = "F".equals(prefs.getString("temp_unit", "C"));
            b.append(Math.round(f ? c * 9f / 5f + 32 : c)).append(f ? " °F" : " °C");
        }
        return b.toString();
    }

    private void updateCarData() {
        if (carDataTv == null) return;
        String t = carDataText();
        carDataTv.setText(t);
        carDataTv.setVisibility(t.length() == 0 ? View.GONE : View.VISIBLE);
    }

    /** Guarda la ubicación para el amanecer/atardecer solo si cambió bastante (~25 km): no escribe a cada lectura. */
    private void sunSpot(double la, double lo) {
        if (!Double.isNaN(sunLat) && Math.abs(la - sunLat) < 0.25 && Math.abs(lo - sunLon) < 0.25) return;
        sunLat = la; sunLon = lo;
        prefs.edit().putFloat("sun_lat", (float) la).putFloat("sun_lon", (float) lo).apply();
    }

    /**
     * Minutos del día (hora local) de la salida [0] y la puesta [1] del sol, sin internet (fórmulas de NOAA,
     * error de 1–2 min). null si ese día no sale o no se pone (zonas polares).
     */
    static int[] sunTimes(double lat, double lon, Calendar c) {
        double g = 2 * Math.PI / 365.0 * (c.get(Calendar.DAY_OF_YEAR) - 1);
        double eqt = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double la = Math.toRadians(lat);
        double cosH = Math.cos(Math.toRadians(90.833)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl);
        if (cosH > 1 || cosH < -1) return null;
        double ha = Math.toDegrees(Math.acos(cosH));
        double tz = (c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 60000.0;
        int rise = (int) Math.round(720 - 4 * (lon + ha) - eqt + tz), set = (int) Math.round(720 - 4 * (lon - ha) - eqt + tz);
        return new int[]{((rise % 1440) + 1440) % 1440, ((set % 1440) + 1440) % 1440};
    }

    /** Amanecer y atardecer de hoy si el modo automático los usa y ya hay ubicación; si no, null (horas fijas). */
    private int[] sunToday() {
        if (!prefs.getBoolean("auto_sun", false) || Double.isNaN(sunLat)) return null;
        return sunTimes(sunLat, sunLon, Calendar.getInstance());
    }

    /** Modo automático: ¿estamos en el horario oscuro? (por defecto de 6 p. m. a 6 a. m., como siempre). */
    private boolean darkHours() {
        // #36 (RGalindo2004): pantallas que bajan el brillo al prender las luces → oscuro con poco brillo.
        // Si la pantalla no informa su brillo, siguen las horas fijas.
        if (prefs.getBoolean("auto_bright", false)) {
            int b = brightnessPct();
            if (b >= 0) {
                int thr = prefs.getInt("bright_dark", 40);
                // 5 puntos de margen para volver a claro: el brillo automático de algunas pantallas oscila
                if (brightDark == null) brightDark = b < thr;
                else if (brightDark && b >= thr + 5) brightDark = false;
                else if (!brightDark && b < thr) brightDark = true;
                return brightDark;
            }
        }
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int a = prefs.getInt("dark_start", 18 * 60), b = prefs.getInt("dark_end", 6 * 60);
        int[] sun = sunToday();
        if (sun != null) { a = sun[1]; b = sun[0]; }
        if (a == b) return false;
        return a < b ? (m >= a && m < b) : (m >= a || m < b);
    }

    private Boolean brightDark;

    /** Brillo de la pantalla en % (0–100), o -1 si la pantalla no lo informa. */
    private int brightnessPct() {
        try {
            int v = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, -1);
            if (v < 0) return -1;
            return Math.min(100, Math.round(v * 100f / 255f));
        } catch (Throwable e) { return -1; }
    }

    /** Cambió el brillo: en «Auto · Brillo de la pantalla» se revisa el tema enseguida (con 0,8 s de espera). */
    private final android.database.ContentObserver brightObs = new android.database.ContentObserver(new Handler(android.os.Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) {
            try {
                if (prefs == null || root == null || !"auto".equals(prefs.getString("theme", "dark")) || !prefs.getBoolean("auto_bright", false)) return;
                h.removeCallbacks(brightApply);
                h.postDelayed(brightApply, 800);
            } catch (Throwable ignored) { }
        }
    };

    private final Runnable brightApply = new Runnable() { public void run() {
        try {
            boolean was = LIGHT;
            applyTheme();
            if (was != LIGHT) { applyAccent(); rebuild(); }
            else if (settingsView != null && settingsCat == C_LOOK) showSettings(settingsCat);   // «Brillo actual» al día
        } catch (Throwable e) { Guard.log("Tema por brillo: " + e); }
    }};

    /** Modo «Sistema»: sigue el modo oscuro de la pantalla (algunas lo activan al encender las luces del carro). */
    private boolean systemNight() {
        int l = fytLights();   // FYT: las luces del carro (car_mode); las demás pantallas, el modo oscuro de Android
        if (l >= 0) return l == 1;
        try {
            return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable e) { return false; }
    }

    /** Hora del día (minutos) con el formato elegido en Ajustes → Reloj. */
    private String fmtMinutes(int m) {
        String fmt = prefs.getString("clock", "auto");
        boolean h24 = "24".equals(fmt) || ("auto".equals(fmt) && android.text.format.DateFormat.is24HourFormat(this));
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, m / 60);
        c.set(Calendar.MINUTE, m % 60);
        return I18n.sdf(h24 ? "HH:mm" : "h:mm a").format(c.getTime());
    }

    private void askDarkTime(final String key, int def, String title) {
        int cur = prefs.getInt(key, def);
        String fmt = prefs.getString("clock", "auto");
        boolean h24 = "24".equals(fmt) || ("auto".equals(fmt) && android.text.format.DateFormat.is24HourFormat(this));
        pickTime(title, cur, h24, new IntCb() { public void on(int minutes) {
            prefs.edit().putInt(key, minutes).apply();
            boolean was = LIGHT;
            applyThemeNow();   // horario elegido a mano: sin espera
            if (was != LIGHT) { applyAccent(); rebuild(); }
            showSettings(settingsCat);
        }});
    }

    interface LongCb { void on(long v); }

    /**
     * Columna «▲ valor ▼» para elegir un número con el dedo (ventanas de hora y fecha, 2.21). Mantener presionado
     * repite. Reemplaza a los selectores del sistema, que cambian con cada versión de Android y no siguen el tema.
     */
    private LinearLayout stepper(final Runnable up, final Runnable down, TextView value, float w) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        for (int k = 0; k < 2; k++) {
            final Runnable act = k == 0 ? up : down;
            final FrameLayout b = circleIcon("chevron_right", 50, 26, BTN);
            b.getChildAt(0).setRotation(k == 0 ? -90 : 90);
            b.setOnTouchListener(new View.OnTouchListener() {
                final Runnable rep = new Runnable() { public void run() {
                    if (b.getWindowToken() == null) return;   // la ventana se cerró con el dedo puesto
                    act.run();
                    h.postDelayed(this, 110);
                }};
                public boolean onTouch(View v, MotionEvent e) {
                    int a = e.getActionMasked();
                    if (a == MotionEvent.ACTION_DOWN) { act.run(); h.postDelayed(rep, 450); v.setAlpha(0.6f); }
                    else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { h.removeCallbacks(rep); v.setAlpha(1f); }
                    return true;
                }
            });
            if (k == 1) col.addView(value, new LinearLayout.LayoutParams((int) (w * s), (int) (58 * s)));
            col.addView(b, new LinearLayout.LayoutParams((int) (50 * s), (int) (50 * s)));
        }
        return col;
    }

    /** Ventana para elegir una hora del día (devuelve los minutos desde la medianoche). Los minutos van de 5 en 5. */
    private void pickTime(String title, int minutes, final boolean h24, final IntCb cb) {
        final int[] v = {Math.max(0, Math.min(23, minutes / 60)), Math.max(0, Math.min(55, (minutes % 60) / 5 * 5))};
        final TextView hv = tv("", 34, TEXT, true), mv = tv("", 34, TEXT, true);
        hv.setGravity(Gravity.CENTER);
        mv.setGravity(Gravity.CENTER);
        final Runnable show = new Runnable() { public void run() {
            hv.setText(h24 ? String.format(Locale.US, "%02d", v[0]) : ((v[0] + 11) % 12 + 1) + (v[0] < 12 ? " AM" : " PM"));
            mv.setText(String.format(Locale.US, "%02d", v[1]));
        }};
        show.run();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, (int) (6 * s), 0, (int) (2 * s));
        row.addView(stepper(new Runnable() { public void run() { v[0] = (v[0] + 1) % 24; show.run(); } },
            new Runnable() { public void run() { v[0] = (v[0] + 23) % 24; show.run(); } }, hv, h24 ? 110 : 170));
        TextView colon = tv(":", 34, MUTED, true);
        colon.setGravity(Gravity.CENTER);
        row.addView(colon, new LinearLayout.LayoutParams((int) (28 * s), -2));
        row.addView(stepper(new Runnable() { public void run() { v[1] = (v[1] + 5) % 60; show.run(); } },
            new Runnable() { public void run() { v[1] = (v[1] + 55) % 60; show.run(); } }, mv, 110));
        new TB(this, dlg())
            .setTitle(title)
            .setView(row)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { cb.on(v[0] * 60 + v[1]); } })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Ventana para elegir una fecha (día, mes y año) que no pase de hoy. */
    private void pickDate(String title, long when, final LongCb cb) {
        final Calendar c = Calendar.getInstance();
        if (when > 0) c.setTimeInMillis(when);
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        String[] names;
        try { names = new java.text.DateFormatSymbols(I18n.loc).getShortMonths(); } catch (Throwable e) { names = null; }
        final String[] months = names;
        final TextView dv = tv("", 32, TEXT, true), mv = tv("", 26, TEXT, true), yv = tv("", 32, TEXT, true);
        final TextView[] tvs = {dv, mv, yv};
        for (TextView t : tvs) t.setGravity(Gravity.CENTER);
        final Runnable show = new Runnable() { public void run() {
            long now = System.currentTimeMillis();
            if (c.getTimeInMillis() > now) c.setTimeInMillis(now);
            if (c.get(Calendar.YEAR) < 1990) c.set(Calendar.YEAR, 1990);
            int m = c.get(Calendar.MONTH);
            dv.setText(String.valueOf(c.get(Calendar.DAY_OF_MONTH)));
            mv.setText(months != null && m < months.length && months[m].length() > 0 ? months[m].replace(".", "") : String.valueOf(m + 1));
            yv.setText(String.valueOf(c.get(Calendar.YEAR)));
        }};
        show.run();
        final int[] fields = {Calendar.DAY_OF_MONTH, Calendar.MONTH, Calendar.YEAR};
        final float[] widths = {96, 120, 124};
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, (int) (6 * s), 0, (int) (2 * s));
        // en inglés el mes va primero (Nov 14 2026); en los demás idiomas, el día
        int[] order = "en".equals(I18n.lang) ? new int[]{1, 0, 2} : new int[]{0, 1, 2};
        for (final int i : order) {
            LinearLayout col = stepper(new Runnable() { public void run() { c.add(fields[i], 1); show.run(); } },
                new Runnable() { public void run() { c.add(fields[i], -1); show.run(); } }, tvs[i], widths[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = lp.rightMargin = (int) (6 * s);
            row.addView(col, lp);
        }
        new TB(this, dlg())
            .setTitle(title)
            .setView(row)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { cb.on(c.getTimeInMillis()); } })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    // ───────────────────────── clima ─────────────────────────

    private void maybeWeather() {
        if (!prefs.getBoolean("weather_on", true) || Double.isNaN(wLat)) { showWeather(); return; }
        if (SystemClock.elapsedRealtime() - weatherAt < 30 * 60 * 1000L && weatherNow != null) return;
        weatherAt = SystemClock.elapsedRealtime();
        Weather.fetch(this, wLat, wLon, new Weather.Listener() {
            public void onWeather(final Weather.Now n, String err) {
                h.post(new Runnable() { public void run() {
                    if (n != null) weatherNow = n;
                    // sin datos, o con el pronóstico guardado (#46): reintenta en 5 min para traer el de internet
                    if (n == null || n.cachedAt > 0) weatherAt = SystemClock.elapsedRealtime() - 25 * 60 * 1000L;
                    showWeather();
                }});
            }
        });
    }

    private void refreshWeatherNow() { weatherAt = 0; weatherNow = null; maybeWeather(); }

    private void showWeather() {
        showPanelWeather();
        if (weatherBox == null) return;
        if (!prefs.getBoolean("weather_on", true) || weatherNow == null) { weatherBox.setVisibility(View.GONE); return; }
        weatherBox.setVisibility(View.VISIBLE);
        weatherIc.setImageBitmap(asset("ic/" + Weather.icon(weatherNow.code, weatherNow.day) + ".png"));
        weatherTv.setText(Math.round(weatherNow.temp) + "°" + weatherNow.unit);
        weatherCityTv.setText(weatherNow.city != null ? weatherNow.city : I18n.t(Weather.describe(weatherNow.code)));
    }

    // ───────────────────────── panel de ajustes ─────────────────────────

    interface IntCb { void on(int i); }

    // 2.20: Ajustes reordenados (Andrés): cada opción en el apartado donde uno la busca
    static final String[] CAT_NAMES = {"Volver al inicio", "Tu vehículo", "Mantenimientos", "Pantalla de inicio", "Barra de apps", "Apariencia",
        "Conexión", "Mapa y clima", "Música", "Sistema", "Acerca de", "Personalización"};
    static final String[] CAT_ICONS = {"arrow_back", "directions_car", "car_repair", "dehaze", "apps", "palette", "usb", "map", "music_note", "tune", "info", "star"};
    static final int C_VEHICLE = 1, C_MAINT = 2, C_HOME = 3, C_DOCK = 4, C_LOOK = 5, C_CONN = 6, C_MAP = 7, C_MUSIC = 8, C_SYS = 9, C_ABOUT = 10,
        C_STYLE = 11;
    /** Orden en pantalla (los números de categoría no cambian: los usan los ganchos de prueba «ajustes:N»). */
    static final int[] CAT_ORDER = {0, C_STYLE, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    private android.widget.ScrollView settingsScroll;
    private int settingsScrollCat = -1;
    private String settingsScrollSub = "";
    private int settingsPendingY = -1;   // posición por restaurar en una página que aún no se ha acomodado

    private void closeSettings() {
        settingsCat = -1;
        settingsSub = null;
        Obd.removeListener(obdSettingsL);
        settingsScroll = null; settingsScrollCat = -1; settingsPendingY = -1;
        if (settingsView != null) { root.removeView(settingsView); settingsView = null; }
        if (board != null) board.setVisibility(View.VISIBLE);
    }

    private void showSettings(int cat) {
        float keep = s;
        s = uiS;
        try { showSettings0(cat); } finally { s = keep; }
    }

    private void showSettings0(int cat) {
        if (cat == 0) { closeSettings(); return; }
        if (cat != C_VEHICLE) settingsSub = null;
        Obd.removeListener(obdSettingsL);
        obdLive.clear();
        // al cambiar un ajuste se redibuja la página: que se quede donde estaba, no que vuelva arriba
        // (algunos ajustes redibujan dos veces seguidas: la segunda vez la página anterior aún no se acomodó y su
        // posición sigue pendiente; se usa esa y no un 0)
        String subKey = settingsSub == null ? "" : settingsSub;
        final int keepY = settingsScroll == null || settingsScrollCat != cat || !subKey.equals(settingsScrollSub) ? 0
                : settingsPendingY >= 0 ? settingsPendingY : settingsScroll.getScrollY();
        settingsPendingY = keepY > 0 ? keepY : -1;
        settingsCat = cat;
        if (settingsView != null) root.removeView(settingsView);
        settingsView = new FrameLayout(this);
        settingsView.setBackgroundColor(BG);
        settingsView.setClickable(true);
        root.addView(settingsView, new FrameLayout.LayoutParams(-1, -1));
        // la pantalla de inicio no se dibuja detrás de los Ajustes: en algunas pantallas (Picanto, Android 4.2) se
        // colaba un borde de las tarjetas a través del fondo, y además ahorra trabajo mientras los Ajustes están abiertos
        if (board != null) board.setVisibility(View.INVISIBLE);

        FrameLayout panel = new FrameLayout(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams((int) ((vertical ? 720 : 1024) * s), (int) ((vertical ? boardH : 600) * s));
        plp.gravity = Gravity.CENTER;
        settingsView.addView(panel, plp);

        if (vertical) settingsTabs(panel, cat);
        else {
        // barra lateral
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding((int) (10 * s), (int) (12 * s), (int) (10 * s), (int) (12 * s));
        android.widget.ScrollView sideSc = new android.widget.ScrollView(this);
        sideSc.setVerticalScrollBarEnabled(false);
        sideSc.setBackground(round(CARD, 20));
        sideSc.addView(side, new FrameLayout.LayoutParams(-1, -2));
        place(panel, sideSc, 16, 16, 240, 568);
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding((int) (8 * s), 0, 0, (int) (8 * s));
        ImageView logo = new ImageView(this);
        logo.setImageBitmap(asset("logo.png"));
        brand.addView(logo, new LinearLayout.LayoutParams((int) (30 * s), (int) (30 * s)));
        TextView bt = tv("StradaOS", 18, TEXT, true);
        bt.setPadding((int) (10 * s), 0, 0, 0);
        brand.addView(bt);
        side.addView(brand);
        for (int k = 0; k < CAT_ORDER.length; k++) {
            final int i = CAT_ORDER[k];
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
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, (int) (38 * s));
            ilp.bottomMargin = (int) (3 * s);
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
                public void onClick(View v) { settingsSub = null; showSettings(idx); }
            });
        }
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
        if (vertical) place(panel, sc, 16, 84, 688, boardH - 96);
        else place(panel, sc, 272, 16, 736, 568);
        settingsScroll = sc; settingsScrollCat = cat; settingsScrollSub = subKey;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding((int) (8 * s), (int) (6 * s), (int) (8 * s), (int) (24 * s));
        sc.addView(body, new FrameLayout.LayoutParams(-1, -2));
        if (cat == C_VEHICLE && settingsSub != null) obdHeader(body);
        else {
        TextView title = tv(CAT_NAMES[cat], 24, TEXT, true);
        title.setPadding((int) (4 * s), 0, 0, (int) (14 * s));
        body.addView(title);
        }

        switch (cat) {
            case C_VEHICLE: if (settingsSub != null) obdPage(body, settingsSub); else catVehicle(body); break;
            case C_MAINT: catMaint(body); break;
            case C_HOME: catHome(body); break;
            case C_DOCK: catDock(body); break;
            case C_LOOK: catLook(body); break;
            case C_CONN: catConnection(body); break;
            case C_MAP: catMapWeather(body); break;
            case C_MUSIC: catMusic(body); break;
            case C_SYS: catSystem(body); break;
            case C_ABOUT: catAbout(body); break;
            case C_STYLE: catStyle(body); break;
        }
    }

    /** Ajustes en vertical: las categorías van arriba, en una fila que se desliza, con la elegida a la vista. */
    private void settingsTabs(FrameLayout panel, final int cat) {
        final LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        final View[] chosen = new View[1];
        for (int k = 0; k < CAT_ORDER.length; k++) {
            final int i = CAT_ORDER[k];
            final int idx = i;
            boolean sel = i == cat;
            LinearLayout it = new LinearLayout(this);
            it.setGravity(Gravity.CENTER_VERTICAL);
            it.setPadding((int) (14 * s), 0, (int) (16 * s), 0);
            it.setBackground(round(sel ? RED_DARK : CARD, 16));
            ImageView ic = icon(CAT_ICONS[i], 20);
            if (sel) ic.setColorFilter(RED);
            else if (ICON == 0xFFFFFFFF) ic.setAlpha(0.7f);
            it.addView(ic, new LinearLayout.LayoutParams((int) (20 * s), (int) (20 * s)));
            if (i > 0) {   // «Volver al inicio» es solo la flecha
                TextView lt = tv(CAT_NAMES[i], 14, sel ? RED : SOFT, sel);
                lt.setPadding((int) (10 * s), 0, 0, 0);
                it.addView(lt);
            }
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, (int) (48 * s));
            ilp.rightMargin = (int) (8 * s);
            tabs.addView(it, ilp);
            if (sel) chosen[0] = it;
            it.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { settingsSub = null; showSettings(idx); }
            });
        }
        // la categoría elegida se deja a la vista durante el acomodo, antes de dibujar (sin salto)
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this) {
            private boolean placed;
            @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                if (!placed && chosen[0] != null && chosen[0].getWidth() > 0) {
                    placed = true;
                    scrollTo(Math.max(0, chosen[0].getLeft() - (getWidth() - chosen[0].getWidth()) / 2), 0);
                }
            }
        };
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tabs, new FrameLayout.LayoutParams(-2, -1));
        place(panel, hs, 16, 18, 688, 52);
    }

    // ── piezas de diseño ──

    // ───────────────────────── Personalización (2.24, Bloque 14) ─────────────────────────
    // Temas gratis: aplican con un toque combinaciones de lo que ya existe en Ajustes. No guardan nada propio:
    // el tema «en uso» es el que coincide con los ajustes actuales (si el usuario cambia algo, ninguno queda marcado).

    static final class FreeTheme {
        final String name, descH, descV, corner; final boolean car, left, gauge, compact, panel;
        /** corner: esquina del velocímetro sobre el mapa (bl/br) cuando no hay tarjeta del vehículo; panel: conexión abierta. */
        FreeTheme(String name, String descH, String descV, boolean car, boolean left, boolean gauge, String corner,
                  boolean compact, boolean panel) {
            this.name = name; this.descH = descH; this.descV = descV; this.car = car; this.left = left; this.gauge = gauge;
            this.corner = corner; this.compact = compact; this.panel = panel;
        }
    }

    // Cada tema fija todo lo que se ve en su miniatura (lado de la música, velocímetro y su esquina, lado del botón del
    // navegador, panel de conexión y barra), para que la miniatura y la descripción coincidan con lo que se aplica.
    static final FreeTheme[] FREE_THEMES = {
        new FreeTheme("Clásico",
            "Vehículo a la izquierda, mapa en el centro y música a la derecha",
            "Mapa arriba; debajo, vehículo a la izquierda y música a la derecha",
            true, false, false, null, false, true),
        new FreeTheme("Música a la izquierda",
            "Música a la izquierda, mapa en el centro y vehículo a la derecha",
            "Mapa arriba; debajo, música a la izquierda y vehículo a la derecha",
            true, true, false, null, false, true),
        new FreeTheme("Mapa amplio",
            "Sin tarjeta del vehículo: mapa más ancho, velocímetro abajo a la izquierda y música a la derecha",
            "Sin tarjeta del vehículo: mapa más alto con el velocímetro abajo a la izquierda y la música a lo ancho",
            false, false, true, "bl", false, true),
        new FreeTheme("Minimalista",
            "Música a la izquierda, velocímetro abajo a la derecha, panel de conexión plegado y barra compacta",
            "Mapa casi completo: velocímetro abajo a la derecha, panel de conexión plegado, música a lo ancho y barra compacta",
            false, true, true, "br", true, false),
    };

    /** Vista previa elegida en Personalización: -1 = la orientación actual, 0 = horizontal, 1 = vertical. */
    private int stylePreview = -1;
    private boolean stylePreviewFor;   // orientación en la que se eligió: al girar la pantalla vuelve a la actual

    // El panel de conexión se abre y se pliega a mano desde el mapa: no cuenta para marcar el tema «en uso».
    private boolean themeInUse(FreeTheme t) {
        return !premiumActive() && prefs.getBoolean("show_car", true) == t.car && prefs.getBoolean("show_player", true)
            && "left".equals(prefs.getString("player_side", "right")) == t.left
            && "auto".equals(prefs.getString("nav_btn_side", "auto"))
            && (t.car || (prefs.getBoolean("map_gauge", false) == t.gauge && prefs.getBoolean("car_speed", true)
                && t.corner.equals(prefs.getString("map_gauge_corner", "auto"))))
            && (t.compact ? "compact" : "normal").equals(prefs.getString("dock_mode", "normal"));
    }

    private void applyFreeTheme(FreeTheme t) {
        SharedPreferences.Editor e = prefs.edit()
            .putBoolean("show_car", t.car).putBoolean("show_player", true)
            .putString("player_side", t.left ? "left" : "right")
            .putString("nav_btn_side", "auto")
            .putBoolean("aa_panel_open", t.panel)
            .putString("dock_mode", t.compact ? "compact" : "normal")
            .remove("premium_theme");   // un tema gratis quita el premium
        if (!t.car) e.putBoolean("map_gauge", t.gauge).putBoolean("car_speed", true).putString("map_gauge_corner", t.corner);
        e.apply();
        rebuild();
        showSettings(C_STYLE);
        toast(I18n.t("Tema aplicado:") + " " + I18n.t(t.name));
    }

    private void catStyle(LinearLayout body) {
        TextView intro = tv("Cambia el aspecto de la pantalla de inicio con un toque. Puedes seguir ajustando cada detalle en las demás secciones.", 13, MUTED, false);
        multiLine(intro);
        intro.setPadding((int) (6 * s), 0, (int) (6 * s), (int) (6 * s));
        body.addView(intro);
        TextView h2 = tv(I18n.t("Temas gratis").toUpperCase(I18n.loc), 11, MUTED, true);
        h2.setPadding((int) (6 * s), (int) (10 * s), 0, (int) (8 * s));
        body.addView(h2);

        // Subsección Horizontal | Vertical: el mismo tema sirve en las dos orientaciones; aquí se ve cómo queda en cada una
        if (stylePreview >= 0 && stylePreviewFor != vertical) stylePreview = -1;
        final boolean portrait = stylePreview < 0 ? vertical : stylePreview == 1;
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout g = new LinearLayout(this);
        g.setBackground(round(BTN, 18));
        int pp = (int) (3 * s);
        g.setPadding(pp, pp, pp, pp);
        String[] opts = {"Horizontal", "Vertical"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            boolean sel = (i == 1) == portrait;
            TextView o = tv(opts[i], 13, sel ? Color.WHITE : SOFT, sel);
            o.setGravity(Gravity.CENTER);
            o.setText(o.getText() + " ");
            o.setEllipsize(null);
            o.setPadding((int) (18 * s), (int) (7 * s), (int) (15 * s), (int) (7 * s));
            if (sel) o.setBackground(round(RED, 15));
            o.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                stylePreview = idx;
                stylePreviewFor = vertical;
                showSettings(C_STYLE);
            }});
            g.addView(o);
        }
        tabs.addView(g);
        TextView note = tv("Cada tema se adapta solo a la orientación de la pantalla.", 12, MUTED, false);
        multiLine(note);
        note.setPadding((int) (12 * s), 0, 0, 0);
        tabs.addView(note, new LinearLayout.LayoutParams(0, -2, 1));
        tabs.setPadding((int) (2 * s), 0, 0, (int) (10 * s));
        body.addView(tabs);

        // mismas reglas que la pantalla de inicio, para que la miniatura muestre lo que de verdad aparece
        boolean navShown = prefs.getInt("nav_btn_size", 0) != 3;
        String cm = prefs.getString("conn_buttons", "both");
        boolean hasPanel = !"none".equals(cm) || prefs.getBoolean("weather_on", true);
        boolean labels = prefs.getBoolean("dock_labels", true);
        boolean forceCompact = portrait && vertical && shortVertical;
        // horizontal: 2 por fila; vertical: las 4 en una fila (son altas)
        int perRow = portrait ? 4 : 2;
        float cellGap = portrait ? 10 : 12;
        LinearLayout rowL = null;
        for (int i = 0; i < FREE_THEMES.length; i++) {
            final FreeTheme t = FREE_THEMES[i];
            if (i % perRow == 0) {
                rowL = new LinearLayout(this);
                rowL.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.bottomMargin = (int) (cellGap * s);
                body.addView(rowL, rlp);
            }
            boolean inUse = themeInUse(t);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setBackground(round(inUse ? RED_DARK : CARD, 16));
            int cp = (int) ((portrait ? 8 : 10) * s);
            cell.setPadding(cp, cp, cp, (int) (12 * s));
            ThemeThumb th = new ThemeThumb(this, s, portrait, t.car, t.left, t.gauge, "bl".equals(t.corner),
                t.compact || forceCompact, t.left, navShown, labels, t.panel && hasPanel);
            th.selected = inUse;
            cell.addView(th, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout nameRow = new LinearLayout(this);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            nameRow.setPadding((int) (4 * s), (int) (10 * s), (int) (4 * s), 0);
            TextView nm = tv(t.name, portrait ? 14 : 15, TEXT, true);
            multiLine(nm);
            nameRow.addView(nm, new LinearLayout.LayoutParams(0, -2, 1));
            if (inUse && !portrait) {
                TextView badge = tv("En uso", 11, 0xFFFFFFFF, true);
                badge.setBackground(round(RED, 10));
                badge.setPadding((int) (8 * s), (int) (2 * s), (int) (8 * s), (int) (2 * s));
                nameRow.addView(badge);
            }
            cell.addView(nameRow);
            if (inUse && portrait) {
                // celda angosta: la etiqueta va debajo del nombre para que no lo corte
                TextView badge = tv("En uso", 11, 0xFFFFFFFF, true);
                badge.setBackground(round(RED, 10));
                badge.setPadding((int) (8 * s), (int) (2 * s), (int) (8 * s), (int) (2 * s));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
                blp.leftMargin = (int) (4 * s);
                blp.topMargin = (int) (4 * s);
                cell.addView(badge, blp);
            }
            // la descripción completa, sin cortar: dice exactamente lo que cambia al aplicar el tema
            TextView ds = tv(portrait ? t.descV : t.descH, 12, MUTED, false);
            multiLine(ds);
            ds.setLineSpacing(2 * s, 1f);
            ds.setPadding((int) (4 * s), (int) (3 * s), (int) (4 * s), 0);
            cell.addView(ds);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % perRow != perRow - 1) clp.rightMargin = (int) (cellGap * s);
            rowL.addView(cell, clp);
            cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { applyFreeTheme(t); }
            });
        }
        premiumGallery(body, portrait);
    }

    private static void multiLine(TextView v) {
        v.setSingleLine(false);
        v.setMaxLines(6);
        v.setEllipsize(null);
    }

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
        // separación con el control de la derecha: con letra XL el texto quedaba pegado a los botones (2.19)
        col.setPadding(0, 0, (int) (16 * s), 0);
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

    /** Fila que muestra la opción elegida y abre una lista para cambiarla (cuando las opciones no caben en botones). */
    private void choice(LinearLayout card, final String title, String sub, final String[] opts, final int sel, final IntCb cb) {
        row(card, title, sub, I18n.t(opts[Math.max(0, Math.min(opts.length - 1, sel))]), new Runnable() { public void run() {
            String[] tr = new String[opts.length];
            for (int i = 0; i < opts.length; i++) tr[i] = I18n.t(opts[i]);
            new TB(Main.this, dlg()).raw()
                .setTitle(I18n.t(title))
                .setSingleChoiceItems(tr, sel, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    d.dismiss();
                    cb.on(w);
                }})
                .show();
        }});
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
            // en Android 11+ la «o» final salía cortada («Clarc»): un espacio al final le da aire a la última letra
            o.setText(o.getText() + " ");
            o.setEllipsize(null);
            o.setPadding((int) (15 * s), (int) (7 * s), (int) (12 * s), (int) (7 * s));
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
        row(f, "Foto del vehículo", "Se usa tal cual; si quieres, le puedes quitar el fondo", CarPhoto.load(this) != null ? "Configurada" : "Sin foto",
            new Runnable() { public void run() { photoMenu(); } });
        LinearLayout un = section(body, "Unidades");
        String su = prefs.getString("speed_unit", "auto");
        segmented(un, "Velocidad", "Automático: mph en EE. UU. y Reino Unido", new String[]{"Auto", "km/h", "mph"}, "kmh".equals(su) ? 1 : "mph".equals(su) ? 2 : 0,
            new IntCb() { public void on(int i) {
                prefs.edit().putString("speed_unit", i == 1 ? "kmh" : i == 2 ? "mph" : "auto").apply(); rebuild(); showSettings(settingsCat);
            }});
        final String unitLbl = useMph() ? " mph" : " km/h";
        String[] offLbl = new String[SPEED_OFFSETS.length];
        for (int i = 0; i < offLbl.length; i++)
            offLbl[i] = i == 0 ? I18n.t("Ninguna") : SPEED_OFFSETS[i].endsWith("%") ? SPEED_OFFSETS[i].replace("%", " %") : SPEED_OFFSETS[i] + unitLbl;
        choice(un, "Corrección del velocímetro", "Suma o resta a la velocidad del GPS para que coincida con el tablero de tu carro",
            offLbl, Math.max(0, java.util.Arrays.asList(SPEED_OFFSETS).indexOf(prefs.getString("speed_offset", "0"))), new IntCb() { public void on(int i) {
                prefs.edit().putString("speed_offset", SPEED_OFFSETS[i]).apply(); rebuild(); showSettings(settingsCat);
            }});
        // Equipo: marca de la pantalla. Automático detecta FYT (com.syu.ms); con FYT se ofrecen las luces y los datos del carro
        LinearLayout eq = section(body, "Equipo");
        final String[] hws = {"auto", "generic", "fyt"};
        boolean det = Fyt.detect(this);
        String detTxt = det ? I18n.t("Detectada:") + " FYT" + (Fyt.platform().length() > 0 ? " · " + Fyt.platform() : "")
                : I18n.t("No se detectó una marca con funciones propias");
        segmented(eq, "Marca de la pantalla", detTxt, new String[]{"Auto", "Genérica", "FYT"},
            Math.max(0, java.util.Arrays.asList(hws).indexOf(prefs.getString("hw", "generic"))), new IntCb() { public void on(int i) {
                prefs.edit().putString("hw", hws[i]).apply();
                stopFyt(); startFyt();
                applyThemeNow(); applyAccent(); rebuild(); showSettings(settingsCat);   // «Sistema» pasa a seguir (o no) las luces
            }});
        if (fytOn()) {
            toggle(eq, "Datos del carro", "Elige qué datos del carro se ven arriba, junto al Wi-Fi",
                prefs.getBoolean("fyt_data", false), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("fyt_data", i == 1).apply();
                    if (i == 1) startFyt(); else stopFyt();
                    rebuild(); showSettings(settingsCat);
                }});
            if (prefs.getBoolean("fyt_data", false)) {
                toggle(eq, "Tensión de la batería", "Medida por la pantalla; no necesita la caja CAN",
                    prefs.getBoolean("fyt_volt", false), new IntCb() { public void on(int i) {
                        prefs.edit().putBoolean("fyt_volt", i == 1).apply(); updateCarData(); showSettings(settingsCat);
                    }});
                toggle(eq, "Temperatura exterior", "Del sensor del carro; necesita la caja CAN",
                    prefs.getBoolean("fyt_temp", false), new IntCb() { public void on(int i) {
                        prefs.edit().putBoolean("fyt_temp", i == 1).apply(); updateCarData(); showSettings(settingsCat);
                    }});
            }
            if (fyt != null) {
                String now = carDataText(true);
                row(eq, "Lectura actual", I18n.t("Servicio com.syu.ms:") + " " + I18n.t(fyt.state)
                        + (fyt.canType > 0 ? " · CAN " + fyt.canType : fyt.canType == 0 ? " · " + I18n.t("sin caja CAN") : ""),
                    now.length() > 0 ? now : I18n.t("Esperando datos"), new Runnable() { public void run() { showSettings(settingsCat); } });
            }
            int l = Fyt.lights(this);
            row(eq, "Luces del carro", "Para seguirlas, elige Apariencia → Tema → Modo → Sistema",
                l < 0 ? I18n.t("Sin datos") : I18n.t(l == 1 ? "Encendidas" : "Apagadas"), new Runnable() { public void run() { showSettings(C_LOOK); } });
        }
        // 2.26: escáner OBD-II (motor del PR #42 de Jaacern). Apagado por defecto: sin él, nada cambia en el inicio
        LinearLayout ob = section(body, "Escáner OBD-II");
        row(ob, "Guía del escáner", "Qué comprar, cómo conectarlo y qué significa cada cosa · sin internet", null, obdGo("obd_guide"));
        toggle(ob, "Monitor del motor", "Con un escáner ELM327 por Wi-Fi o Bluetooth: temperatura, batería, alertas por voz y códigos de falla",
            obdOn(), new IntCb() { public void on(int i) { if (i == 1) obdDialogs().turnOn(); else obdDialogs().turnOff(); }});
        if (obdOn()) {
            row(ob, "Escáner OBD-II", ObdDialogs.deviceName(this) + " · " + obdStateText(), null, obdGo("obd"));
            row(ob, "Abrir el panel del vehículo", "También con la pestaña del borde o con un botón de la barra", null,
                new Runnable() { public void run() { openVehiclePanel(); } });
        }
        LinearLayout mt = section(body, "Mantenimientos");
        row(mt, "Aceite, filtros, SOAT y más", "Ahora están en su propia sección", null, new Runnable() { public void run() { showSettings(C_MAINT); } });
    }

    /** Mantenimientos (2.20): el kilometraje, el aceite de siempre (sin perder lo configurado) y los demás. */
    private void catMaint(LinearLayout body) {
        LinearLayout k = section(body, "Kilometraje");
        if (prefs.contains("odo_base_km"))
            rowIc(k, "speed", "Kilometraje actual", "Estimado con el GPS. Corrígelo con el del tablero cuando quieras", "≈ " + fmtDist(odoKm()),
                new Runnable() { public void run() { askOdo(null); } });
        else
            rowIc(k, "speed", "Kilometraje del tablero", "Escríbelo una vez y StradaOS le suma lo que recorras con el GPS", I18n.t("Configurar"),
                new Runnable() { public void run() { askOilUnit(new Runnable() { public void run() { askOdo(null); } }); } });
        LinearLayout o = section(body, "Cambio de aceite");
        toggle(o, "Recordatorio de cambio de aceite", "Avisa cuando toca el cambio", oilOn(), new IntCb() { public void on(int i) {
            if (i == 1 && !prefs.contains("odo_base_km")) { oilSetup(); return; }
            prefs.edit().putBoolean("oil_on", i == 1).apply(); updateOilBadge(); showSettings(settingsCat);
        }});
        if (oilOn()) {
            row(o, "Cada cuánto", null, fmtDist(prefs.getFloat("oil_interval_km", 5000)), new Runnable() { public void run() { askInterval(null); } });
            row(o, "Último cambio", "Kilometraje del tablero cuando se hizo", fmtDist(prefs.getFloat("oil_last_km", 0)), new Runnable() { public void run() { askLast(null); } });
            row(o, "Próximo cambio", null, fmtDist(oilNextKm()), new Runnable() { public void run() { oilMenu(); } });
            row(o, "Ya hice el cambio de aceite", null, null, new Runnable() { public void run() { oilDone(); } });
        }
        addIcon(o, 0, "oil_barrel");
        maintSection(body);
        LinearLayout h = section(body, "En el inicio");
        if (carSpeed)
            toggle(h, "Mantenimiento en el inicio", "Junto a la velocidad se ve el más próximo (el aceite u otro). Los avisos siguen saliendo aunque lo ocultes",
                prefs.getBoolean("oil_home", true), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("oil_home", i == 1).apply(); rebuild();
                }});
        else
            row(h, "Mantenimiento en el inicio", "Se muestra junto a la velocidad: activa «Velocidad y servicio» en Pantalla de inicio", null,
                new Runnable() { public void run() { showSettings(C_HOME); } });
    }

    /** Ícono al comienzo de la fila «n» de una tarjeta de Ajustes (las filas van separadas por líneas). */
    private void addIcon(LinearLayout card, int rowIndex, String ic) {
        try {
            int seen = -1;
            for (int i = 0; i < card.getChildCount(); i++) {
                View v = card.getChildAt(i);
                if (!(v instanceof LinearLayout)) continue;
                if (++seen != rowIndex) continue;
                ImageView iv = icon(ic, 22);
                iv.setColorFilter(RED);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (22 * s), (int) (22 * s));
                lp.rightMargin = (int) (14 * s);
                ((LinearLayout) v).addView(iv, 0, lp);
                return;
            }
        } catch (Throwable ignored) { }
    }

    private void rowIc(LinearLayout card, String ic, String title, String sub, String value, Runnable onClick) {
        int before = 0;
        for (int i = 0; i < card.getChildCount(); i++) if (card.getChildAt(i) instanceof LinearLayout) before++;
        row(card, title, sub, value, onClick);
        addIcon(card, before, ic);
    }

    private void catLook(LinearLayout body) {
        LinearLayout c = section(body, "Tema");
        String t = prefs.getString("theme", "dark");
        final String[] themes = {"dark", "light", "auto", "system"};
        final boolean fy = fytOn();
        segmented(c, "Modo", fy ? "Auto: por horario. Sistema: sigue las luces del carro (pantalla FYT)"
                : "Auto: por horario. Sistema: sigue el modo oscuro de la pantalla (algunas lo cambian al encender las luces)",
            new String[]{"Oscuro", "Claro", "Auto", "Sistema"},
            Math.max(0, java.util.Arrays.asList(themes).indexOf(t)), new IntCb() { public void on(int i) {
                prefs.edit().putString("theme", themes[i]).apply();
                applyThemeNow(); applyAccent(); rebuild(); showSettings(settingsCat);
            }});
        if ("auto".equals(t)) {
            final boolean sunOn = prefs.getBoolean("auto_sun", false);
            final boolean brOn = prefs.getBoolean("auto_bright", false);
            choice(c, "Horario", "Amanecer y atardecer: se calcula con el GPS, sin internet. Brillo: para pantallas que bajan el brillo al encender las luces",
                new String[]{"Horas fijas", "Amanecer y atardecer", "Brillo de la pantalla"},
                brOn ? 2 : sunOn ? 1 : 0, new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("auto_sun", i == 1).putBoolean("auto_bright", i == 2).apply();
                    brightDark = null;
                    applyThemeNow(); applyAccent(); rebuild(); showSettings(settingsCat);
                }});
            int[] sun = sunToday();
            final int bright = brOn ? brightnessPct() : -1;
            if (brOn && bright >= 0) {
                row(c, "Brillo actual", "Prende y apaga las luces del carro para ver cuánto cambia", bright + " %", null);
                final int[] lv = {10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90};
                String[] lo = new String[lv.length];
                int cur = prefs.getInt("bright_dark", 40), sel = 6;
                for (int i = 0; i < lv.length; i++) { lo[i] = lv[i] + " %"; if (lv[i] == cur) sel = i; }
                choice(c, "Oscuro con brillo menor a", "Elige un nivel entre el brillo de día y el de noche", lo, sel, new IntCb() { public void on(int i) {
                    prefs.edit().putInt("bright_dark", lv[i]).apply();
                    brightDark = null;
                    applyThemeNow(); applyAccent(); rebuild(); showSettings(settingsCat);
                }});
            } else if (!brOn && sunOn && sun != null) {
                row(c, "Hoy", I18n.t("Amanecer") + " " + fmtMinutes(sun[0]) + " · " + I18n.t("Atardecer") + " " + fmtMinutes(sun[1])
                    + " · " + I18n.t("oscuro desde el atardecer hasta el amanecer"), null, null);
            } else {
                if (brOn) row(c, "Esta pantalla no informa su brillo", "Mientras tanto usa las horas fijas", "", null);
                else if (sunOn) row(c, "Esperando la ubicación del GPS", "Mientras tanto usa las horas fijas", "", null);
                row(c, "Oscuro desde", null, fmtMinutes(prefs.getInt("dark_start", 18 * 60)), new Runnable() { public void run() {
                    askDarkTime("dark_start", 18 * 60, "Oscuro desde");
                }});
                row(c, "Claro desde", null, fmtMinutes(prefs.getInt("dark_end", 6 * 60)), new Runnable() { public void run() {
                    askDarkTime("dark_end", 6 * 60, "Claro desde");
                }});
            }
        }
        // Espera antes de cambiar de tema, en cada sentido (por defecto sin espera, como siempre)
        // primero solo el interruptor; las dos esperas aparecen al activarlo (menos cosas para quien no lo usa)
        if ("auto".equals(t) || "system".equals(t)) {
            final boolean dOn = themeDelayOn();
            toggle(c, "Espera para cambiar de tema", "Evita cambios de golpe, p. ej. al apagar el carro de noche",
                dOn, new IntCb() { public void on(int i) {
                    SharedPreferences.Editor e = prefs.edit().putBoolean("theme_delay_on", i == 1);
                    // al activarla por primera vez: 5 s para volver a claro, el caso típico
                    if (i == 1 && prefs.getInt("theme_delay_dark", 0) <= 0 && prefs.getInt("theme_delay_light", 0) <= 0)
                        e.putInt("theme_delay_light", 5);
                    e.apply();
                    applyThemeNow(); applyAccent(); rebuild(); showSettings(settingsCat);
                }});
        }
        if (("auto".equals(t) || "system".equals(t)) && themeDelayOn()) {
            final int dd = prefs.getInt("theme_delay_dark", 0), dl = prefs.getInt("theme_delay_light", 0);
            int sd = 0, sl = 0;
            for (int i = 0; i < THEME_DELAYS.length; i++) { if (THEME_DELAYS[i] == dd) sd = i; if (THEME_DELAYS[i] == dl) sl = i; }
            choice(c, "Espera para pasar a oscuro", "Cuánto debe durar el cambio (luces encendidas, poco brillo…) antes de oscurecer",
                THEME_DELAY_LBL, sd, new IntCb() { public void on(int i) {
                    prefs.edit().putInt("theme_delay_dark", THEME_DELAYS[i]).apply();
                    showSettings(settingsCat);
                }});
            choice(c, "Espera para pasar a claro", "Útil si al apagar el carro de noche se apagan las luces y la pantalla pasa a claro de golpe",
                THEME_DELAY_LBL, sl, new IntCb() { public void on(int i) {
                    prefs.edit().putInt("theme_delay_light", THEME_DELAYS[i]).apply();
                    showSettings(settingsCat);
                }});
        }
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
        LinearLayout tx = section(body, "Texto");
        segmented(tx, "Tamaño de la letra", "Para leer mejor de lejos o que quepa más", new String[]{"S", "M", "L", "XL"}, prefs.getInt("font_size", 1), new IntCb() { public void on(int i) {
            prefs.edit().putInt("font_size", i).apply(); rebuild(); showSettings(settingsCat);
        }});
        LinearLayout ic = section(body, "Íconos");
        boolean appStyle = "apps".equals(prefs.getString("dock_style", "circle"));
        segmented(ic, "Íconos de la barra", "Apps: el ícono de la app que abre cada botón (radio, cámara…)", new String[]{"StradaOS", "Apps"}, appStyle ? 1 : 0, new IntCb() { public void on(int i) {
            prefs.edit().putString("dock_style", i == 1 ? "apps" : "circle").apply(); rebuild(); showSettings(settingsCat);
        }});
        final List<String[]> packs = IconPack.installed(this);
        String curPack = prefs.getString("icon_pack", "");
        String curLabel = I18n.t("Ninguno");
        for (String[] pk : packs) if (pk[0].equals(curPack)) curLabel = pk[1];
        row(ic, "Pack de íconos", packs.isEmpty() ? "Instala uno (los de Nova o ADW sirven) y elígelo aquí" : "Cambia los íconos de la barra y del cajón de apps",
            curLabel, new Runnable() { public void run() {
                if (packs.isEmpty()) { toast("No hay packs de íconos instalados"); return; }
                String[] names = new String[packs.size() + 1];
                names[0] = I18n.t("Ninguno (íconos de siempre)");
                int sel = 0;
                String cp = prefs.getString("icon_pack", "");
                for (int i = 0; i < packs.size(); i++) { names[i + 1] = packs.get(i)[1]; if (packs.get(i)[0].equals(cp)) sel = i + 1; }
                new TB(Main.this, dlg()).raw()
                    .setTitle(I18n.t("Pack de íconos"))
                    .setSingleChoiceItems(names, sel, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                        if (w == 0) prefs.edit().remove("icon_pack").apply();
                        else {
                            String pk = packs.get(w - 1)[0];
                            IconPack ip = IconPack.get(Main.this, pk);
                            if (ip == null) { toast("Ese pack no trae la lista de íconos (appfilter)"); return; }
                            prefs.edit().putString("icon_pack", pk).apply();
                            if (!"apps".equals(prefs.getString("dock_style", "circle"))) prefs.edit().putString("dock_style", "apps").apply();
                            toast(I18n.t("Pack listo: {} íconos").replace("{}", String.valueOf(ip.size())));
                        }
                        rebuild(); showSettings(settingsCat);
                    }})
                    .show();
            }});
    }

    /** Pantalla de inicio: qué tarjetas se ven, el mapa y la barra de arriba. */
    private void catHome(LinearLayout body) {
        if (premiumActive()) {
            LinearLayout pn = section(body, "Tema premium");
            String pid = prefs.getString("premium_theme", Aurora.ID);
            LinearLayout r = rowBase(pn, I18n.t(Prem.name(pid)) + " · " + I18n.t("En uso"), "El tema arma la pantalla de inicio. Las tarjetas, la barra y el velocímetro de aquí se usan otra vez al quitarlo en Personalización.");
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSettings(C_STYLE); } });
            if (Carbono.ID.equals(pid)) {
                final String[] ccols = Carbono.CAR_COLORS;
                int cci = Math.max(0, java.util.Arrays.asList(ccols).indexOf(Carbono.car(prefs.getString("carb_car", "silver"))));
                choice(pn, "Color del carro", "El carro del tema Carbono, visto desde arriba. También se cambia con una presión larga sobre el carro.", Carbono.CAR_NAMES, cci, new IntCb() { public void on(int i) {
                    prefs.edit().putString("carb_car", ccols[i]).apply(); rebuild(); showSettings(C_HOME);
                }});
            }
            if (Faro.ID.equals(pid) || Carbono.ID.equals(pid)) {
                if (Faro.ID.equals(pid)) {
                final String[] cols = Faro.CAR_COLORS;
                int ci = Math.max(0, java.util.Arrays.asList(cols).indexOf(Faro.car(prefs.getString("faro_car", "silver"))));
                choice(pn, "Color del carro", "El carro del tema Faro, visto desde arriba. También se cambia con una presión larga sobre el carro.", Faro.CAR_NAMES, ci, new IntCb() { public void on(int i) {
                    prefs.edit().putString("faro_car", cols[i]).apply(); rebuild(); showSettings(C_HOME);
                }});
                }
                segmented(pn, "Intensidad del mapa", "Qué tan visible es el mapa sobre el fondo. Auto lo pone más fuerte en las pantallas sencillas de los radios y suave en las de alta gama. Súbela si al sol no se ve bien.",
                    new String[]{"Auto", "Baja", "Media", "Alta"}, Math.max(-1, Math.min(2, prefs.getInt("faro_mapa", -1))) + 1, new IntCb() { public void on(int i) {
                        if (i == 0) prefs.edit().remove("faro_mapa").apply(); else prefs.edit().putInt("faro_mapa", i - 1).apply();
                        rebuild(); showSettings(C_HOME);
                    }});
            }
        }
        if (boxW > 0 && canVertical(boxW, boxH)) {
            // Solo en pantallas casi cuadradas o verticales (en las horizontales no tiene sentido).
            final String[] lk = {"auto", "horizontal", "vertical"};
            LinearLayout ds = section(body, "Diseño");
            segmented(ds, "Diseño de la pantalla", "Automático: según la forma de la pantalla. Elige Vertical si tu pantalla es vertical y se ve chica en el medio",
                new String[]{"Automático", "Horizontal", "Vertical"},
                Math.max(0, java.util.Arrays.asList(lk).indexOf(prefs.getString("layout_mode", "auto"))), new IntCb() { public void on(int i) {
                    prefs.edit().putString("layout_mode", lk[i]).apply(); rebuild();
                }});
        }
        LinearLayout lay = section(body, "Tarjetas");
        segmented(lay, "Reproductor de música", "Ponlo del lado del conductor para tenerlo a la mano", new String[]{"Izquierda", "Derecha"},
            playerLeft ? 0 : 1, new IntCb() { public void on(int i) {
                // botones en el orden de la pantalla: izquierda, derecha
                prefs.edit().putString("player_side", i == 0 ? "left" : "right").apply(); rebuild();
            }});
        toggle(lay, "Tarjeta del reproductor", "Si la ocultas, el mapa gana espacio", showPlayer, new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("show_player", i == 1).apply(); rebuild();
        }});
        toggle(lay, "Tarjeta del vehículo", "Si la ocultas, la velocidad pasa arriba y el mapa y el reproductor ganan espacio", showCar, new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("show_car", i == 1).apply(); rebuild();
        }});
        toggle(lay, "Velocidad y servicio", "En la tarjeta del vehículo (o arriba si la ocultas). Apágalo si tu carro o pantalla no tiene GPS: la foto ocupa ese espacio",
            carSpeed, new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("car_speed", i == 1).apply(); rebuild();
            }});
        if (carSpeed && showCar) {
            segmented(lay, "Velocímetro", "En la tarjeta del vehículo. Toma el color de acento", new String[]{"Digital", "Analógico", "Mixto"},
                prefs.getInt("speedo", 0), new IntCb() { public void on(int i) {
                    prefs.edit().putInt("speedo", i).apply(); rebuild();
                }});
        }
        LinearLayout mp = section(body, "Mapa");
        final String[] connKeys = {"both", "aa", "cp", "none"};
        choice(mp, "Botones de conexión", "Con uno o ninguno, el clima ocupa el espacio libre del mapa",
            new String[]{"Android Auto y CarPlay", "Solo Android Auto", "Solo CarPlay", "Ninguno"},
            Math.max(0, java.util.Arrays.asList(connKeys).indexOf(prefs.getString("conn_buttons", "both"))), new IntCb() { public void on(int i) {
                prefs.edit().putString("conn_buttons", connKeys[i]).apply(); rebuild();
            }});
        final int nbSize = prefs.getInt("nav_btn_size", 0);
        choice(mp, "Botón del navegador", "El botón redondo del mapa que abre tu app de navegación",
            new String[]{"Normal", "Grande", "Muy grande", "Oculto"}, nbSize, new IntCb() { public void on(int i) {
                prefs.edit().putInt("nav_btn_size", i).apply(); rebuild();
            }});
        if (nbSize != 3) {
            final String[] sideKeys = {"auto", "left", "right"};
            segmented(mp, "Lado del botón del navegador", "Automático: del lado del reproductor (el del conductor)",
                new String[]{"Automático", "Izquierda", "Derecha"},
                Math.max(0, java.util.Arrays.asList(sideKeys).indexOf(prefs.getString("nav_btn_side", "auto"))), new IntCb() { public void on(int i) {
                    prefs.edit().putString("nav_btn_side", sideKeys[i]).apply(); rebuild();
                }});
        }
        if (carSpeed && !showCar) {
            final boolean mg = prefs.getBoolean("map_gauge", false);
            toggle(mp, "Velocímetro sobre el mapa", "Redondo, en una esquina del mapa (en vez de la velocidad de arriba)", mg, new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("map_gauge", i == 1).apply(); rebuild();
            }});
            if (mg) {
                final String[] corners = {"auto", "tl", "tr", "bl", "br"};
                choice(mp, "Esquina del velocímetro", "Automático: arriba, en el lado contrario al botón del navegador",
                    new String[]{"Automático", "Arriba a la izquierda", "Arriba a la derecha", "Abajo a la izquierda", "Abajo a la derecha"},
                    Math.max(0, java.util.Arrays.asList(corners).indexOf(prefs.getString("map_gauge_corner", "auto"))), new IntCb() { public void on(int i) {
                        prefs.edit().putString("map_gauge_corner", corners[i]).apply(); rebuild();
                    }});
                segmented(mp, "Tamaño del velocímetro", "En pantallas bajas se limita para no tapar el mapa", new String[]{"Pequeño", "Normal", "Grande"},
                    prefs.getInt("map_gauge_size", 1), new IntCb() { public void on(int i) {
                        prefs.edit().putInt("map_gauge_size", i).apply(); rebuild();
                    }});
            }
        }
        LinearLayout top = section(body, "Barra de arriba");
        toggle(top, "Íconos de Wi-Fi y Bluetooth", "Arriba, junto al clima. Toca para encender o apagar; déjalo presionado para abrir sus ajustes",
            prefs.getBoolean("status_icons", true), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("status_icons", i == 1).apply(); rebuild();
            }});
        String ck = prefs.getString("clock", "auto");
        segmented(top, "Formato de hora", null, new String[]{"Auto", "12 h", "24 h"}, "12".equals(ck) ? 1 : "24".equals(ck) ? 2 : 0,
            new IntCb() { public void on(int i) {
                prefs.edit().putString("clock", i == 1 ? "12" : i == 2 ? "24" : "auto").apply(); updateClock(); showSettings(settingsCat);
            }});
        // 2.22: pantalla en reposo (#27, #28), apagada por defecto
        LinearLayout idl = section(body, "Pantalla en reposo");
        final boolean idleOn = prefs.getBoolean("idle_on", false);
        toggle(idl, "Pantalla en reposo", "Si no tocas la pantalla un rato, se pone negra con la hora, la fecha y la velocidad. Un toque la quita",
            idleOn, new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("idle_on", i == 1).apply(); idleReset(); showSettings(settingsCat);
            }});
        if (idleOn) {
            String[] mins = new String[IDLE_MIN.length];
            int mSel = 1;
            for (int i = 0; i < IDLE_MIN.length; i++) {
                mins[i] = I18n.t(IDLE_MIN[i] == 1 ? "1 minuto" : IDLE_MIN[i] + " minutos");
                if (IDLE_MIN[i] == prefs.getInt("idle_min", 2)) mSel = i;
            }
            choice(idl, "Después de", "Tiempo sin tocar la pantalla", mins, mSel, new IntCb() { public void on(int i) {
                prefs.edit().putInt("idle_min", IDLE_MIN[i]).apply(); idleReset(); showSettings(settingsCat);
            }});
            choice(idl, "Velocidad", "Con el diseño de velocímetro que prefieras", new String[]{"Digital", "Analógico", "Mixto", "Sin velocidad"},
                prefs.getInt("idle_speed", prefs.getInt("speedo", 0)), new IntCb() { public void on(int i) {
                    prefs.edit().putInt("idle_speed", i).apply(); showSettings(settingsCat);
                }});
            segmented(idl, "Tamaño", null, new String[]{"Normal", "Grande", "Muy grande"}, prefs.getInt("idle_size", 0), new IntCb() { public void on(int i) {
                prefs.edit().putInt("idle_size", i).apply(); showSettings(settingsCat);
            }});
            choice(idl, "Color de la letra", null, new String[]{"Blanco", "Color de acento", "Gris suave", "Ámbar"},
                prefs.getInt("idle_color", 0), new IntCb() { public void on(int i) {
                    prefs.edit().putInt("idle_color", i).apply(); showSettings(settingsCat);
                }});
            row(idl, "Ver ahora", "Toca la pantalla para volver", null, new Runnable() { public void run() { showIdle(); } });
        }
        if (SDK >= 19) {
            LinearLayout scr = section(body, "Pantalla completa");
            toggle(scr, "Ocultar la barra del sistema", "Esconde también la barra de navegación de tu pantalla (abajo o al costado). Desliza desde el borde para verla un momento",
                prefs.getBoolean("hide_navbar", false), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("hide_navbar", i == 1).apply();
                    noImmersive = false; stormCount = 0;
                    immersive(); rebuild(); showSettings(settingsCat);
                }});
        }
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

    /** Barra de apps: cómo se ve la barra, qué abre cada botón y el cajón. */
    private void catDock(LinearLayout body) {
        LinearLayout lay = section(body, "Diseño de la barra");
        segmented(lay, "Barra de botones", "Lateral: los botones van al costado y todo se ve más grande en pantallas bajas como 1280×480. Oculta: el botón Apps va arriba", new String[]{"Normal", "Compacta", "Lateral", "Oculta"},
            noDock ? 3 : sideDock ? 2 : compactDock ? 1 : 0, new IntCb() { public void on(int i) {
                prefs.edit().putString("dock_mode", i == 3 ? "none" : i == 2 ? "side" : i == 1 ? "compact" : "normal").apply(); rebuild();
            }});
        if (!noDock) {
            if (sideDock) {
                String rs = prefs.getString("rail_side", "auto");
                segmented(lay, "Lado de la barra lateral", "Automático: del lado del reproductor", new String[]{"Automático", "Izquierda", "Derecha"},
                    "left".equals(rs) ? 1 : "right".equals(rs) ? 2 : 0, new IntCb() { public void on(int i) {
                        prefs.edit().putString("rail_side", i == 1 ? "left" : i == 2 ? "right" : "auto").apply(); rebuild();
                    }});
            }
            toggle(lay, "Nombres de los botones", "Debajo de cada ícono de la barra", dockLabels, new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("dock_labels", i == 1).apply(); rebuild();
            }});
            final String[] counts = {"4", "5", "6", "7", "8", "9", "10"};
            choice(lay, "Cantidad de botones", "«Apps» siempre queda al final", counts, dockCount - 4, new IntCb() { public void on(int i) {
                prefs.edit().putInt("dock_count", i + 4).apply(); rebuild(); showSettings(settingsCat);
            }});
            segmented(lay, "Tamaño de los íconos", null, new String[]{"Pequeño", "Normal", "Grande"}, prefs.getInt("dock_icon_size", 1), new IntCb() { public void on(int i) {
                prefs.edit().putInt("dock_icon_size", i).apply(); rebuild(); showSettings(settingsCat);
            }});
            LinearLayout c = section(body, "Botones (déjalos presionados en el inicio para cambiarlos)");
        for (int i = 0; i < dockCount; i++) {
            final int idx = i;
            String t = dockTarget(i);
            String what = t.startsWith("app:") ? "App: " + labelOf(t.substring(4)) : "Función: " + ("web".equals(t.substring(3)) ? "Navegador web" : dockFn(t.substring(3))[1]);
            row(c, "Botón " + (i + 1) + " · " + dockName(i), what, null, new Runnable() { public void run() {
                dockMenu(idx, new Runnable() { public void run() { if (settingsView != null) showSettings(settingsCat); } });
            }});
        }
        row(c, "Revisar todos los botones", "Lista rápida de qué abre cada uno, como en el asistente", null, new Runnable() { public void run() {
            reviewShortcuts(new Runnable() { public void run() { if (settingsView != null) showSettings(settingsCat); } });
        }});
        }
        LinearLayout fa = section(body, "Apps de cada función");
        String[][] items = {{"radio", "Radio"}, {"bt", "Bluetooth"}, {"cam", "Cámara"}, {"web", "Navegador web"}, {"files", "Archivos"}};
        for (final String[] it : items) {
            row(fa, it[1], null, "web".equals(it[0]) ? appLabel(it[0], "Automático") : shortcutLabel(it[0]), new Runnable() { public void run() { pickFor(it[0], it[1]); } });
        }
        row(fa, "Música Bluetooth", "La app que hace sonar el Bluetooth (se usa para pasar el sonido)", shortcutLabel("btaudio"), new Runnable() { public void run() { pickFor("btaudio", "Música Bluetooth"); } });
        LinearLayout dw = section(body, "Cajón de apps");
        int nh = hiddenApps().size();
        row(dw, "Apps ocultas", "No se ven en el cajón, pero siguen instaladas y se pueden poner en los botones", nh == 0 ? I18n.t("Ninguna") : String.valueOf(nh),
            new Runnable() { public void run() { manageHidden(new Runnable() { public void run() { if (settingsView != null) showSettings(settingsCat); } }); } });
        LinearLayout r = section(body, "Restablecer");
        row(r, "Restablecer accesos", "La barra vuelve a lo de siempre y se buscan las apps de fábrica", null, new Runnable() { public void run() {
            SharedPreferences.Editor e = prefs.edit();
            for (String k : new String[]{"radio", "bt", "cam", "files", "settings", "aa", "web", "carplay", "music_app", "nav_app"}) e.remove(k);
            for (int i = 0; i < 10; i++) e.remove("dock_" + i).remove("dock_" + i + "_name");
            e.apply();
            toast("Accesos restablecidos");
            refreshStates();
            rebuild();
            showSettings(settingsCat);
        }});
    }

    private void catMapWeather(LinearLayout body) {
        LinearLayout w = section(body, "Clima");
        toggle(w, "Mostrar el clima", "Arriba, junto al saludo (necesita internet)", prefs.getBoolean("weather_on", true), new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("weather_on", i == 1).apply(); refreshWeatherNow(); showSettings(settingsCat);
        }});
        segmented(w, "Unidad", null, new String[]{"°C", "°F"}, "F".equals(prefs.getString("temp_unit", "C")) ? 1 : 0, new IntCb() { public void on(int i) {
            prefs.edit().putString("temp_unit", i == 1 ? "F" : "C").apply(); refreshWeatherNow(); showSettings(settingsCat);
        }});
        String manual = prefs.getString("city_manual", "");
        row(w, "Ciudad", "Vacío = automática por GPS", manual.length() > 0 ? manual : (weatherNow != null && weatherNow.city != null ? weatherNow.city + " (GPS)" : "Automática"),
            edit("city_manual", "Ciudad (vacío = automática por GPS)", ""));
        LinearLayout mo = section(body, "Mapa");
        LinearLayout ml = mo, nv = mo;
        segmented(mo, "El mapa apunta", "Según tu dirección: gira con el carro mientras te mueves", new String[]{"Norte arriba", "Según tu dirección"},
            prefs.getBoolean("map_heading", false) ? 1 : 0, new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("map_heading", i == 1).apply(); LiveMapView.headingUp = i == 1;
                if (liveMap != null) liveMap.invalidate(); showSettings(settingsCat);
            }});
        toggle(ml, "Mapa en vivo", "Apágalo si la pantalla va lenta: queda un mapa de adorno y el velocímetro sigue funcionando", prefs.getBoolean("map_live", true), new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("map_live", i == 1).apply(); rebuild(); showSettings(settingsCat);
        }});
        row(nv, "App de navegación preferida", "Se abre con el botón del mapa (Waze, Maps, OsmAnd… o integrada en el mapa)", autoLabel("nav_app", NAV_PKGS, "Toca para elegir"),
            new Runnable() { public void run() { pickFor("nav_app", "Navegación"); } });
        final boolean navIn = "internal".equals(prefs.getString("nav_app", null));
        if (navIn) {
            toggle(nv, "Voz de navegación", "Indicaciones paso a paso habladas durante el recorrido", prefs.getBoolean("nav_voice", true), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("nav_voice", i == 1).apply(); showSettings(settingsCat);
            }});
            toggle(nv, "Tráfico en vivo", "Muestra congestión en el mapa y evita trancones (usa clave gratis de TomTom)", prefs.getBoolean("map_traffic", false), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("map_traffic", i == 1).apply();
                LiveMapView.clearTrafficCache();
                if (liveMap != null) liveMap.invalidate();
                showSettings(settingsCat);
            }});
            String ttKey = prefs.getString("tomtom_key", "").trim();
            row(nv, "Clave gratis de TomTom", "Para capas de tráfico y rutas con tráfico (regístrate gratis en developer.tomtom.com)",
                ttKey.length() > 0 ? "Configurada" : "Ninguna",
                edit("tomtom_key", "Clave API gratis de TomTom", ""));
        }
        LinearLayout m = mo;
        segmented(m, "Brillo del mapa", "Solo en modo oscuro", new String[]{"Bajo", "Medio", "Alto"}, prefs.getInt("map_bright", 1), new IntCb() { public void on(int i) {
            prefs.edit().putInt("map_bright", i).apply(); rebuild();
        }});
        if (navIn) {
            LinearLayout nvp = section(body, "Navegación");
            toggle(nvp, "Evitar peajes", "Intenta evitar vías de peaje al calcular la ruta", prefs.getBoolean("nav_avoid_tolls", false), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("nav_avoid_tolls", i == 1).apply(); showSettings(settingsCat);
            }});
            toggle(nvp, "Evitar autopistas", "Intenta evitar autopistas o vías rápidas", prefs.getBoolean("nav_avoid_motorways", false), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("nav_avoid_motorways", i == 1).apply(); showSettings(settingsCat);
            }});
            toggle(nvp, "Rutas alternativas", "Muestra 2 o 3 opciones de ruta antes de iniciar", prefs.getBoolean("nav_alternatives", true), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("nav_alternatives", i == 1).apply(); showSettings(settingsCat);
            }});
            toggle(nvp, "Mostrar hora de llegada", "Muestra la hora estimada en lugar del tiempo restante", prefs.getBoolean("nav_eta_clock", false), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("nav_eta_clock", i == 1).apply(); updateNavHud(); showSettings(settingsCat);
            }});
        }
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
        m = section(body, "Avanzado");
        row(m, "Diagnóstico del mapa", "Fuentes probadas y errores", null, new Runnable() { public void run() {
            new TB(Main.this, dlg()).setTitle("Diagnóstico del mapa")
                .setMessage(mapDiag()).setPositiveButton("OK", null).show();
        }});
        row(m, "Fuente del mapa (avanzado)", "URL propia con {z}/{x}/{y}", prefs.getString("map_url", "").length() > 0 ? "Personalizada" : "Automática",
            edit("map_url", "URL de teselas {z}/{x}/{y} (vacío = automático)", ""));
        if (navIn) {
            row(m, "Capa de tráfico propia (avanzado)", "URL transparente de tráfico con {z}/{x}/{y}", prefs.getString("traffic_url", "").trim().length() > 0 ? "Personalizada" : "Automática",
                edit("traffic_url", "URL capa de tráfico {z}/{x}/{y} (vacío = automático)", ""));
            row(m, "Servidor de rutas OSRM (avanzado)", "URL propia compatible con OSRM (vacío = automático)",
                prefs.getString("nav_route_url", "").trim().length() > 0 ? "Personalizada" : "Automática",
                edit("nav_route_url", "URL servidor OSRM (vacío = automático)", ""));
            toggle(m, "Compatibilidad TLS en rutas", "Para radios antiguas donde falle el HTTPS de rutas o búsqueda",
                prefs.getBoolean("nav_tls_compat", false), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("nav_tls_compat", i == 1).apply(); showSettings(settingsCat);
                }});
        }
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
        java.text.DateFormat df = I18n.latin(java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, I18n.loc));
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
            if (settingsView != null) { rebuild(); showSettings(settingsCat); }
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
                rebuild(); showSettings(settingsCat);
            }})
            .setNegativeButton("Cancelar", null);
        if (ours) b.setNeutralButton("Borrar el archivo", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            prefs.edit().remove("map_file").apply();
            LiveMapView.loadFile(Main.this);
            f.delete();
            toast("Mapa borrado");
            rebuild(); showSettings(settingsCat);
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
            if (settingsView != null) showSettings(settingsCat);
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
                if (settingsView != null) showSettings(settingsCat);
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
        if (SDK >= 18) row(c, "Música de cualquier app", "Spotify, YouTube Music y otras apps de la pantalla (acceso a notificaciones)",
            Modern.notificationAccess(this) ? "Activado" : "Desactivado",
            new Runnable() { public void run() {
                try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Throwable e) { notifAccessAdb(); }
            }});
        row(c, "Diagnóstico del reproductor", "Fuente activa, lector de notificaciones y Bluetooth", null, new Runnable() { public void run() { showPlayerDiag(); } });
        LinearLayout b = section(body, "Audio Bluetooth");
        row(b, "¿La música Bluetooth se corta o no aparece?", "Qué hacer en cada caso", null, new Runnable() { public void run() { btHelp(); } });
        toggle(b, "Volver al Bluetooth al desconectar Android Auto", "Al quitar el cable, abre un instante la app de música Bluetooth para que el sonido siga por Bluetooth",
            prefs.getBoolean("aa_off_bt", true), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("aa_off_bt", i == 1).apply(); showSettings(settingsCat);
            }});
        if (clwBt) {
            row(b, "Escuchar el Bluetooth", "En esta pantalla, toca ▶ en el inicio: StradaOS pasa el sonido a Bluetooth (por ejemplo, si venías de la radio)", null, null);
            toggle(b, "Pasar a Bluetooth al dar play en el celular (en prueba)", "Abre un instante «Audio Bluetooth» y vuelve. En algunas pantallas no detecta el play del celular",
                prefs.getBoolean("bt_auto3", false), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("bt_auto3", i == 1).apply(); showSettings(settingsCat);
                }});
            row(b, "Probar ahora", "Abre «Audio Bluetooth» un instante y vuelve al inicio", null,
                new Runnable() { public void run() { closeSettings(); openBtAudioApp(true); } });
        } else {
            toggle(b, "Abrir la app de música Bluetooth al conectar el celular", "Para pantallas que solo dejan sonar el Bluetooth con su app abierta: la abre un momento y vuelve al inicio",
                prefs.getBoolean("bt_open_connect", false), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("bt_open_connect", i == 1).apply(); showSettings(settingsCat);
                }});
            ComponentName cn = btMusicApp();
            row(b, "App de música Bluetooth", "La que hace sonar el Bluetooth en esta pantalla",
                cn != null ? labelOf(cn.flattenToString()) : "No encontrada",
                new Runnable() { public void run() { pickFor("btaudio", "Música Bluetooth"); } });
            segmented(b, "Tiempo antes de volver al inicio", "Súbelo si el audio se corta al volver", new String[]{"1,5 s", "2,5 s", "4 s"},
                prefs.getInt("bt_return_ms", 2500) <= 1500 ? 0 : prefs.getInt("bt_return_ms", 2500) >= 4000 ? 2 : 1, new IntCb() { public void on(int i) {
                    prefs.edit().putInt("bt_return_ms", i == 0 ? 1500 : i == 2 ? 4000 : 2500).apply(); showSettings(settingsCat);
                }});
            row(b, "Probar ahora", "Abre la app de música Bluetooth y vuelve al inicio", null,
                new Runnable() { public void run() { closeSettings(); openBtAudioApp(true); } });
        }
        LinearLayout a = section(body, "Avanzado");
        row(a, "Avisos extra de música", "Nombres de avisos (broadcast) de otros equipos", prefs.getString("extra_actions", "").length() > 0 ? "Configurados" : "Ninguno",
            edit("extra_actions", "Acciones de aviso extra (separadas por coma)", ""));
    }

    private void catSystem(LinearLayout body) {
        LinearLayout lg = section(body, "Idioma");
        String lpref = I18n.pref(prefs);
        int li = java.util.Arrays.asList(I18n.CODES).indexOf(lpref);
        row(lg, "Idioma", "Automático: el del sistema", li >= 0 ? I18n.NAMES[li] : I18n.t("Automático"), new Runnable() { public void run() {
            final String[] opts = new String[I18n.NAMES.length + 1];
            opts[0] = I18n.t("Automático");
            System.arraycopy(I18n.NAMES, 0, opts, 1, I18n.NAMES.length);
            int sel = java.util.Arrays.asList(I18n.CODES).indexOf(I18n.pref(prefs)) + 1;
            new TB(Main.this, dlg()).raw()
                .setTitle(I18n.t("Idioma"))
                .setSingleChoiceItems(opts, sel, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    d.dismiss();
                    setLanguage(w == 0 ? "auto" : I18n.CODES[w - 1]);
                    showSettings(settingsCat);
                }})
                .show();
        }});
        LinearLayout bk = section(body, "Copia de seguridad");
        long lastBk = prefs.getLong("backup_last", 0);
        row(bk, "Guardar una copia", "Ajustes, mantenimientos, kilometraje y la foto del vehículo en un archivo, en la pantalla o en una USB. Nada se sube a internet",
            lastBk > 0 ? I18n.sdf("d MMM, HH:mm").format(new Date(lastBk)) : null, new Runnable() { public void run() { backupSave(); } });
        row(bk, "Restaurar una copia", "Recupera todo después de restablecer la pantalla, o pásalo a otra pantalla", null, new Runnable() { public void run() { backupRestore(); } });
        toggle(bk, "Copia automática", "Cada vez que cambias algo se actualiza una copia en la pantalla y en la USB que esté conectada", prefs.getBoolean("backup_auto", false), new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("backup_auto", i == 1).apply();
            if (i == 1) { h.removeCallbacks(autoBackup); h.postDelayed(autoBackup, 500); }
            showSettings(settingsCat);
        }});
        LinearLayout hm = section(body, "Al encender");
        final boolean def = isDefaultHome();
        row(hm, "Launcher predeterminado", "La que aparece al encender y al tocar Inicio", def ? "StradaOS ✓" : "Otra · toca para cambiar", new Runnable() { public void run() {
            if (isDefaultHome()) homeHelp(); else makeDefaultHome();
        }});
        toggle(hm, "Abrir StradaOS al encender", "Para pantallas que siempre arrancan en su menú de fábrica (Android 4 a 9)", prefs.getBoolean("boot_open", false), new IntCb() { public void on(int i) {
            prefs.edit().putBoolean("boot_open", i == 1).apply(); showSettings(settingsCat);
        }});
        // 2.21 (rvilonen-spec, #15): abrir una app al encender (la radio, para que empiece a sonar) y volver al inicio
        String bootApp = prefs.getString("boot_app", "");
        row(hm, "Abrir una app al encender", "StradaOS la abre un momento al arrancar (por ejemplo la radio, para que empiece a sonar) y vuelve al inicio",
            bootApp.length() > 0 ? labelOf(bootApp) : I18n.t("Ninguna"), new Runnable() { public void run() {
                List<String[]> extra = new ArrayList<String[]>();
                extra.add(new String[]{"none", "Ninguna", null});
                pickApp("Abrir una app al encender", extra, null, true, new TargetCb() { public void on(String t) {
                    if (t.startsWith("app:")) prefs.edit().putString("boot_app", t.substring(4)).apply();
                    else prefs.edit().remove("boot_app").apply();
                    showSettings(settingsCat);
                }});
            }});
        // Salidas (issue #9): una pantalla Android 11 quedó atrapada en StradaOS tras elegirlo como launcher desde su
        // modo fábrica (toda app, incluso la ventana de desinstalar, se abría y volvía a StradaOS).
        LinearLayout out = section(body, "Salir de StradaOS");
        row(out, "Abrir el launcher de fábrica", "Sale de StradaOS sin desinstalarlo", null, new Runnable() { public void run() { openFactoryLauncher(); } });
        row(out, "Desactivar StradaOS", "Si la pantalla quedó atrapada en StradaOS: se apaga solo, sin ventanas de Android", null, new Runnable() { public void run() { confirmDisableSelf(); } });
        row(out, "Desinstalar StradaOS", "Android pide confirmar", null, new Runnable() { public void run() { uninstallSelf(); } });
        if (getPackageName().endsWith(".debug")) {
            LinearLayout sm = section(body, "Versión de prueba");
            choice(sm, "Simular pantalla", "Para probar otros tamaños en una tablet o celular", new String[]{"Real", "1024×600", "1280×720", "1920×720", "2000×1200", "1280×480", "768×1024", "1080×1920"},
                prefs.getInt("sim_screen", 0), new IntCb() { public void on(int i) {
                    prefs.edit().putInt("sim_screen", i).apply(); rebuild(); showSettings(settingsCat);
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

    // ───────────────────────── copia de seguridad (2.21) ─────────────────────────
    // Idea de ashoksoft (Reddit): su pantalla hay que restablecerla de fábrica cada tanto y perdía el cambio de aceite,
    // el seguro y los mantenimientos. Todo va a un archivo en la memoria de la pantalla o en una USB; nada a la nube.

    private static final int REQ_BACKUP_W = 81, REQ_BACKUP_R = 82, REQ_BACKUP_CREATE = 83, REQ_BACKUP_OPEN = 84;
    private boolean askedBackupRead;

    /** Selector de archivos de Android (4.4+), para guardar o abrir la copia en cualquier carpeta o memoria. */
    private Intent backupDocIntent(boolean create) {
        if (SDK < 19) return null;
        Intent i = new Intent(create ? "android.intent.action.CREATE_DOCUMENT" : "android.intent.action.OPEN_DOCUMENT");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(create ? "application/json" : "*/*");
        if (create) i.putExtra(Intent.EXTRA_TITLE, Backup.name());
        try { return getPackageManager().resolveActivity(i, 0) != null ? i : null; } catch (Throwable e) { return null; }
    }

    private void backupSave() {
        // Android 6 a 9: permiso para escribir en la memoria (en Android 10+ se usa el selector de archivos)
        if (SDK >= 23 && SDK <= 28 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_BACKUP_W);
            return;
        }
        backupSave0();
    }

    private void backupSave0() {
        if (isFinishing()) return;
        final List<File> roots = new ArrayList<File>();
        final List<String> items = new ArrayList<String>();
        roots.add(Backup.primary());
        items.add(I18n.t("Memoria de la pantalla") + "\n" + new File(Backup.primary(), "StradaOS").getAbsolutePath());
        for (File u : Backup.removable(this)) {
            roots.add(u);
            items.add(I18n.t("Memoria USB") + "  ·  " + u.getName() + "\n" + u.getAbsolutePath());
        }
        final Intent doc = backupDocIntent(true);
        if (doc != null) items.add(I18n.t("Elegir otra carpeta…"));
        if (items.size() == 1) { backupWrite(roots.get(0)); return; }
        new TB(this, dlg()).raw().wide()
            .setTitle(I18n.t("¿Dónde guardar la copia?"))
            .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w < roots.size()) backupWrite(roots.get(w));
                else try { startActivityForResult(doc, REQ_BACKUP_CREATE); } catch (Throwable e) { toast("No se pudo abrir el selector de archivos"); }
            }})
            .setNegativeButton(I18n.t("Cancelar"), null)
            .show();
    }

    private void backupWrite(final File root) {
        new Thread(new Runnable() { public void run() {
            File f = null;
            String err = null;
            try { f = Backup.save(Main.this, root, Backup.name(), Backup.build(Main.this)); }
            catch (Throwable e) { err = String.valueOf(e.getMessage() != null ? e.getMessage() : e); }
            final File ff = f;
            final String fe = err;
            h.post(new Runnable() { public void run() { backupSaved(ff != null ? ff.getAbsolutePath() : null, fe, root.equals(Backup.primary())); } });
        }}).start();
    }

    private void backupSaved(String where, String err, boolean onScreen) {
        if (isFinishing()) return;
        if (where == null) {
            new TB(this, dlg()).raw()
                .setTitle(I18n.t("No se pudo guardar la copia"))
                .setMessage(I18n.t("Esa memoria no dejó escribir el archivo. Prueba con otra memoria o con «Elegir otra carpeta…».") + "\n\n" + err)
                .setPositiveButton("OK", null).show();
            return;
        }
        prefs.edit().putLong("backup_last", System.currentTimeMillis()).apply();
        new TB(this, dlg()).raw()
            .setTitle(I18n.t("Copia guardada"))
            .setMessage(I18n.t("Se guardó en:") + "\n" + where + (onScreen
                ? "\n\n" + I18n.t("Al restablecer la pantalla de fábrica también se puede borrar su memoria: guarda otra copia en una USB para tenerla a salvo.")
                : ""))
            .setPositiveButton("OK", null).show();
        if (settingsView != null) showSettings(settingsCat);
    }

    /** Guardar o abrir la copia con el selector de archivos de Android. */
    private void backupDocument(final Uri uri, final boolean create) {
        new Thread(new Runnable() { public void run() {
            String text = null, err = null;
            try {
                if (create) Backup.write(getContentResolver().openOutputStream(uri), Backup.build(Main.this));
                else text = new String(Backup.readAll(getContentResolver().openInputStream(uri)), "UTF-8");
            } catch (Throwable e) { err = String.valueOf(e.getMessage() != null ? e.getMessage() : e); }
            final String ft = text, fe = err;
            h.post(new Runnable() { public void run() {
                if (isFinishing()) return;
                if (fe != null) {
                    new TB(Main.this, dlg()).raw().setTitle(I18n.t(create ? "No se pudo guardar la copia" : "No se pudo leer el archivo"))
                        .setMessage(fe).setPositiveButton("OK", null).show();
                } else if (create) {
                    prefs.edit().putLong("backup_last", System.currentTimeMillis()).apply();
                    toast("Copia guardada");
                    if (settingsView != null) showSettings(settingsCat);
                } else confirmRestore(ft);
            }});
        }}).start();
    }

    private void backupRestore() {
        if (SDK >= 23 && !askedBackupRead && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            askedBackupRead = true;
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_BACKUP_R);
            return;
        }
        backupRestore0();
    }

    private void backupRestore0() {
        if (isFinishing()) return;
        toast("Buscando copias…");
        new Thread(new Runnable() { public void run() {
            final List<Backup.Info> found = Backup.find(Main.this);
            h.post(new Runnable() { public void run() { showBackups(found); } });
        }}).start();
    }

    private void showBackups(final List<Backup.Info> found) {
        if (isFinishing()) return;
        final Intent doc = backupDocIntent(false);
        if (found.isEmpty() && doc == null) {
            new TB(this, dlg())
                .setTitle("No se encontró ninguna copia")
                .setMessage("No hay copias de StradaOS en la memoria de la pantalla ni en las USB conectadas. El archivo se llama «StradaOS-backup» y termina en .json.")
                .setPositiveButton("OK", null).show();
            return;
        }
        List<String> items = new ArrayList<String>();
        java.text.DateFormat df = I18n.sdf("d MMM yyyy, HH:mm");
        for (Backup.Info i : found) {
            items.add(df.format(new Date(i.created)) + "  ·  StradaOS " + i.version
                + (Backup.AUTO.equals(i.file.getName()) ? "  ·  " + I18n.t("automática") : "") + "\n" + i.file.getParent());
        }
        if (doc != null) items.add(I18n.t("Buscar el archivo…"));
        new TB(this, dlg()).raw().wide()
            .setTitle(I18n.t(found.isEmpty() ? "No se encontró ninguna copia" : "Elige la copia"))
            .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w >= found.size()) {
                    try { startActivityForResult(doc, REQ_BACKUP_OPEN); } catch (Throwable e) { toast("No se pudo abrir el selector de archivos"); }
                    return;
                }
                try { confirmRestore(new String(Backup.readAll(new java.io.FileInputStream(found.get(w).file)), "UTF-8")); }
                catch (Throwable e) { toast("No se pudo leer el archivo"); }
            }})
            .setNegativeButton(I18n.t("Cancelar"), null)
            .show();
    }

    private void confirmRestore(final String json) {
        Backup.Info i = json == null ? null : Backup.peek(json);
        if (i == null) {
            new TB(this, dlg()).setTitle("No se pudo restaurar")
                .setMessage("Este archivo no es una copia de seguridad de StradaOS").setPositiveButton("OK", null).show();
            return;
        }
        String when = i.created > 0 ? I18n.sdf("d MMM yyyy, HH:mm").format(new Date(i.created)) : "—";
        String msg = I18n.t("Copia del") + " " + when + "  ·  StradaOS " + i.version
            + (i.device.length() > 0 ? "\n" + I18n.t("Pantalla:") + " " + i.device : "")
            + "\n" + I18n.t("Ajustes guardados:") + " " + i.settings + (i.photo ? "  ·  " + I18n.t("con la foto del vehículo") : "")
            + "\n\n" + I18n.t("Se reemplaza toda la configuración actual de StradaOS por la de la copia.");
        new TB(this, dlg()).raw()
            .setTitle(I18n.t("¿Restaurar esta copia?"))
            .setMessage(msg)
            .setPositiveButton(I18n.t("Restaurar"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String err = Backup.apply(Main.this, json);
                if (err != null) {
                    new TB(Main.this, dlg()).setTitle("No se pudo restaurar").setMessage(err).setPositiveButton("OK", null).show();
                    return;
                }
                reloadAll();
                toast("Copia restaurada");
            }})
            .setNegativeButton(I18n.t("Cancelar"), null)
            .show();
    }

    /** Después de restaurar: idioma, tema, color, odómetro y pantalla se vuelven a armar con lo restaurado. */
    private void reloadAll() {
        I18n.init(this, prefs);
        LiveMapView.headingUp = prefs.getBoolean("map_heading", false);
        applyThemeNow();
        applyAccent();
        try { lm.removeUpdates(odoListener); } catch (Throwable ignored) { }
        odoRunning = false;
        startOdo();
        weatherAt = 0;
        rebuild();
        maybeWeather();
    }

    /** Copia automática: se actualiza poco después de cada cambio (y cada media hora si solo avanzó el kilometraje). */
    private long backupDue;
    private final Runnable autoBackup = new Runnable() { public void run() {
        backupDue = 0;
        if (!prefs.getBoolean("backup_auto", false)) return;
        new Thread(new Runnable() { public void run() {
            if (Backup.auto(Main.this) > 0) prefs.edit().putLong("backup_last", System.currentTimeMillis()).apply();
        }}).start();
    }};
    private final SharedPreferences.OnSharedPreferenceChangeListener backupWatch = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            if (key == null || Backup.skip(key) || !sp.getBoolean("backup_auto", false)) return;
            long delay = "odo_gps_m".equals(key) ? 30 * 60000L : 8000L, due = SystemClock.uptimeMillis() + delay;
            if (backupDue != 0 && backupDue <= due) return;   // ya hay una programada para antes
            backupDue = due;
            h.removeCallbacks(autoBackup);
            h.postDelayed(autoBackup, delay);
        }
    };

    // ───────────────────────── app al encender (2.21, #15) ─────────────────────────

    private static boolean bootAppDone;

    /**
     * Abre la app elegida un momento y vuelve al inicio. Solo al encender la pantalla (los primeros minutos desde
     * que arrancó Android): si StradaOS se reinicia más tarde (una actualización, un cierre) no se abre nada.
     */
    private void maybeBootApp() {
        if (bootAppDone) return;
        bootAppDone = true;
        if (SystemClock.elapsedRealtime() > 4 * 60000L) return;
        runBootApp(2000);
    }

    /**
     * Muchas pantallas no se apagan del todo con el carro: se duermen. Al despertar después de un buen rato
     * (el carro estuvo apagado) también cuenta como «encender», si StradaOS es lo que está a la vista.
     */
    private long screenOffAt;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) { screenOffAt = SystemClock.elapsedRealtime(); return; }
            if (screenOffAt > 0 && SystemClock.elapsedRealtime() - screenOffAt > 5 * 60000L && hasWindowFocus()) runBootApp(1500);
            screenOffAt = 0;
        }
    };

    private void runBootApp(long delay) {
        final String comp = prefs.getString("boot_app", "");
        if (comp.length() == 0 || !prefs.getBoolean("setup_done", false)) return;
        h.postDelayed(new Runnable() { public void run() {
            if (isFinishing()) return;
            final ComponentName cn = ComponentName.unflattenFromString(comp);
            if (cn == null || !launchComponent(cn)) return;
            h.postDelayed(new Runnable() { public void run() {
                // si el usuario ya abrió otra cosa, no se le quita
                String now = topPackage();
                if (now != null && !now.equals(cn.getPackageName()) && !now.equals(getPackageName())) return;
                try {
                    startActivity(new Intent(Main.this, Main.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                } catch (Throwable ignored) { }
            }}, 3500);
        }}, delay);
    }

    /** Tabla de compatibilidad: qué función sirve desde qué Android o marca de pantalla, y cómo está aquí. */
    private void showCompat() {
        StringBuilder b = new StringBuilder();
        b.append(I18n.t("Esta pantalla:")).append(" Android ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(SDK).append(")")
         .append(" · ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
        // «Android 8.1» → «8.1» (antes se comparaba con la palabra «Android» y el aviso salía en todas las pantallas)
        if (!android.os.Build.VERSION.RELEASE.startsWith(androidName(SDK).replace("Android ", "").replace("W", "").replace("L", "")))
            b.append(I18n.t("Ojo: la pantalla dice Android " + android.os.Build.VERSION.RELEASE + ", pero por dentro es " + androidName(SDK) + ".")).append("\n");
        b.append("\n");
        compatLine(b, "Inicio, mapa, clima, velocímetro", "Android 4.1+", true, null);
        compatLine(b, "Pista y carátula por Bluetooth", "Android 4.1+", true, clwBt ? "app Bluetooth de fábrica detectada" : "depende de la marca");
        compatLine(b, "Botón ▶ del inicio pasa el sonido a Bluetooth", "Pantallas Clinkway / Autochips", clwBt, clwBt ? "funciona aunque vengas de la radio" : "en otras marcas: «Abrir la app de música Bluetooth al conectar»");
        compatLine(b, "Pasar a Bluetooth al dar play en el celular (en prueba)", "Pantallas Clinkway / Autochips", clwBt, clwBt ? (btAutoOn() ? "activado" : "desactivado; en algunas pantallas no detecta el play del celular") : null);
        boolean fy = fytOn();
        compatLine(b, "Tema según las luces, batería y temperatura exterior", "Pantallas FYT (com.syu.ms)", fy,
            fy ? (Fyt.lights(this) >= 0 ? "luces detectadas" : "esta pantalla no informa las luces")
               : Fyt.detect(this) ? "pantalla FYT detectada: elígela en Tu vehículo → Equipo" : null);
        compatLine(b, "Carátula de Android Auto (Open Headunit)", "Android 4.1+ · accesibilidad", true, NotifArt.running ? "activada" : "falta activar la accesibilidad");
        compatLine(b, "Música de cualquier app (Spotify, YouTube…)", "Android 5+", SDK >= 21, SDK >= 21 ? (MediaListener.connected ? "activada" : "falta dar acceso a notificaciones") : "no disponible en " + androidName(SDK));
        compatLine(b, "Android Auto con Open Headunit", "Android 4.1+ · cable USB", true, installed(PKG_HUR) ? "Open Headunit instalado" : "falta instalar Open Headunit");
        compatLine(b, "Android Auto / CarPlay de la pantalla", "Si la pantalla lo trae", nativeProjectionApp() != null, nativeProjectionApp() != null ? labelOf(nativeProjectionApp()) : "no detectado");
        compatLine(b, "CarPlay", "Pantalla con CarPlay o adaptador USB", carPlayApp() != null, carPlayApp() != null ? labelOf(carPlayApp()) : "no detectado");
        compatLine(b, "Mapa con internet seguro (TLS 1.2)", "Android 4.1+ (StradaOS lo activa)", true, SDK < 20 ? "en Android 4.x solo algunos servidores de mapas responden" : null);
        compatLine(b, "Instalar actualizaciones desde la app", "Android 4.1+", true, SDK >= 26 ? "pide permiso «Instalar apps desconocidas»" : null);
        compatLine(b, "Íconos de Wi-Fi y Bluetooth arriba", "Android 4.1+", true, clwBt ? "Bluetooth: estado del celular desde la app de fábrica; toca para abrirla" : null);
        compatLine(b, "Tema según el modo oscuro del sistema", "Pantallas que lo cambian (p. ej. con las luces)", true,
            systemNight() ? "el sistema está en modo oscuro" : "el sistema está en modo claro");
        compatLine(b, "Copia de seguridad en un archivo (pantalla o USB)", "Android 4.1+", true, SDK >= 29 ? "en Android 10+ la carpeta se elige con el selector de archivos" : null);
        compatLine(b, "Diseño vertical", "Pantallas en vertical (tipo Tesla)", true, vertical ? "en uso"
            : canVertical(boxW, boxH) ? "se puede elegir en Ajustes → Pantalla de inicio → Diseño" : "esta pantalla es horizontal");
        b.append(I18n.t("Área útil de la pantalla:")).append(" ").append(boxW).append(" × ").append(boxH).append(" px\n");
        new TB(this, dlg()).diag().setTitle("Compatibilidad").setMessage(b.toString().trim()).setPositiveButton("OK", null).show();
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
        // el registro es para quien desarrolla StradaOS: los avisos se guardan en español, sea cual sea el idioma elegido
        TextView v = tv(I18n.t("Registro técnico para enviar al desarrollador (se guarda en español).") + "\n\n" + t, 12, TEXT, false);
        v.setSingleLine(false);
        v.setTextIsSelectable(true);
        v.setPadding((int) (16 * s), (int) (8 * s), (int) (16 * s), (int) (8 * s));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(v);
        new TB(this, dlg())
            .setTitle("Registro de fallos")
            .setView(sv)
            .setPositiveButton("OK", null)
            .setNeutralButton("Borrar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                Guard.clear(Main.this); toast("Registro borrado"); if (settingsView != null) showSettings(settingsCat); } })
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
                            prefs.edit().putBoolean("auto_update", true).apply(); showSettings(settingsCat); } })
                        .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { showSettings(settingsCat); } })
                        .show();
                    return;
                }
                prefs.edit().putBoolean("auto_update", i == 1).apply(); showSettings(settingsCat);
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
        if (maintAlert()) return;
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
                    if (settingsView != null && settingsCat == C_ABOUT) showSettings(C_ABOUT);
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
        if (navSearchBtn != null) {
            boolean showGo = prefs.getBoolean("map_live", true) && "internal".equals(prefs.getString("nav_app", null));
            navSearchBtn.setVisibility(showGo ? View.VISIBLE : View.GONE);
        }
        if (aaBtnTv == null && cpSubTv == null) return;
        if (aaBtnTv != null) {   // 2.18: con «Solo CarPlay» no hay botón de Android Auto
            String nat = nativeProjectionApp();
            String aaSaved = prefs.getString("aa", null);
            String chosen = prefs.getString("conn_app", null);
            if (aaSaved != null) aaBtnTv.setText(labelOf(aaSaved));
            else if (chosen != null && installed(chosen)) aaBtnTv.setText(labelOf(chosen));
            else if (connCandidates().size() > 1) aaBtnTv.setText(I18n.t("Toca para elegir"));
            else if (nat != null) aaBtnTv.setText(labelOf(nat));
            else if (installed(PKG_HUR)) aaBtnTv.setText("Open Headunit · USB");
            else aaBtnTv.setText(I18n.t("Toca para instalar"));
        }
        String cpApp = carPlayApp();
        if (cpSubTv != null) cpSubTv.setText(cpApp != null ? labelOf(cpApp) : I18n.t("Pantalla o adaptador"));
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
            ComponentName cn0 = ComponentName.unflattenFromString(c);
            if (cn0 != null) markUsed(cn0.getPackageName());
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
            markUsed(r.activityInfo.packageName);
        } catch (Exception e) {
            toast("No se pudo abrir la app");
        }
    }

    // ── orden del cajón (2.22, #27): A-Z (como siempre), Z-A, usadas o instaladas recientemente, o a mano ──

    static final String[] APP_ORDERS = {"az", "za", "used", "installed", "manual"};
    static final String[] APP_ORDER_NAMES = {"A-Z", "Z-A", "Usadas recientemente", "Instaladas recientemente", "A mano"};

    /** Anota cuándo se abrió una app desde StradaOS (para «Usadas recientemente»; se guardan las últimas 60). */
    private void markUsed(String pkg) {
        if (pkg == null) return;
        java.util.LinkedHashMap<String, Long> m = usedApps();
        m.remove(pkg);
        m.put(pkg, System.currentTimeMillis());
        StringBuilder b = new StringBuilder();
        int skip = Math.max(0, m.size() - 60), n = 0;
        for (java.util.Map.Entry<String, Long> e : m.entrySet()) {
            if (n++ < skip) continue;
            if (b.length() > 0) b.append('\n');
            b.append(e.getKey()).append('\t').append(e.getValue());
        }
        prefs.edit().putString("app_used", b.toString()).apply();
    }

    private java.util.LinkedHashMap<String, Long> usedApps() {
        java.util.LinkedHashMap<String, Long> m = new java.util.LinkedHashMap<String, Long>();
        for (String l : prefs.getString("app_used", "").split("\n")) {
            int t = l.indexOf('\t');
            if (t <= 0) continue;
            try { m.put(l.substring(0, t), Long.parseLong(l.substring(t + 1))); } catch (Throwable ignored) { }
        }
        return m;
    }

    private java.util.List<String> manualOrder() {
        java.util.List<String> out = new ArrayList<String>();
        for (String k : prefs.getString("apps_manual", "").split("\n")) if (k.length() > 0) out.add(k);
        return out;
    }

    private void saveManual(java.util.List<ResolveInfo> list) {
        StringBuilder b = new StringBuilder();
        for (ResolveInfo r : list) { if (b.length() > 0) b.append('\n'); b.append(compKey(r)); }
        prefs.edit().putString("apps_manual", b.toString()).putString("apps_order", "manual").apply();
    }

    /** Ordena la lista del cajón (llega en A-Z). Lo que no tenga dato (nunca abierta, fuera del orden a mano) va al final en A-Z. */
    private void sortApps(java.util.List<ResolveInfo> list) {
        String o = prefs.getString("apps_order", "az");
        if ("za".equals(o)) { Collections.reverse(list); return; }
        final HashMap<ResolveInfo, Long> key = new HashMap<ResolveInfo, Long>();
        if ("used".equals(o)) {
            java.util.LinkedHashMap<String, Long> u = usedApps();
            for (ResolveInfo r : list) { Long t = u.get(r.activityInfo.packageName); key.put(r, t != null ? -t : 0L); }
        } else if ("installed".equals(o)) {
            PackageManager pm = getPackageManager();
            for (ResolveInfo r : list) {
                long t = 0;
                try { t = pm.getPackageInfo(r.activityInfo.packageName, 0).firstInstallTime; } catch (Throwable ignored) { }
                key.put(r, -t);
            }
        } else if ("manual".equals(o)) {
            java.util.List<String> m = manualOrder();
            for (ResolveInfo r : list) { int ix = m.indexOf(compKey(r)); key.put(r, ix >= 0 ? (long) ix - 100000 : 0L); }
        } else return;
        final HashMap<ResolveInfo, Integer> az = new HashMap<ResolveInfo, Integer>();
        for (int i = 0; i < list.size(); i++) az.put(list.get(i), i);
        Collections.sort(list, new Comparator<ResolveInfo>() {
            public int compare(ResolveInfo a, ResolveInfo b) {
                int c = key.get(a).compareTo(key.get(b));
                return c != 0 ? c : az.get(a).compareTo(az.get(b));
            }
        });
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
        if ("nav_app".equals(key)) extra.add(new String[]{"internal", "★  En el mapa de StradaOS (integrada)", "navigation"});
        String[] pin = "nav_app".equals(key) ? NAV_PKGS : "music_app".equals(key) ? MUSIC_PKGS : null;
        pickApp("¿Qué app abre «" + name + "»?", extra, pin, true, new TargetCb() { public void on(String target) {
            if ("auto".equals(target)) {
                prefs.edit().remove(key).apply();
                toast("Restablecido");
            } else if ("internal".equals(target)) {
                prefs.edit().putString(key, "internal").apply();
                toast("Guardado: Navegación en el mapa");
            } else {
                String c = target.substring(4);
                prefs.edit().putString(key, c).apply();
                toast("Guardado: " + labelOf(c));
            }
            // si deja la navegación integrada, la ruta en curso se detiene (si no, el recuadro de guía quedaba en el mapa)
            if ("nav_app".equals(key) && !"internal".equals(prefs.getString("nav_app", null)) && Nav.active != null) {
                Nav.stop(Main.this);
                // sin esto el recuadro de guía seguía en el mapa hasta tocarlo (Andrés, Picanto 16 nov.)
                LiveMapView.headingUp = prefs.getBoolean("map_heading", false);
                updateNavHud();
                if (liveMap != null) liveMap.invalidate();
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
        sortApps(shown);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        final int oi = Math.max(0, java.util.Arrays.asList(APP_ORDERS).indexOf(prefs.getString("apps_order", "az")));
        TextView ord = tv(I18n.t("Orden") + ": " + I18n.t(APP_ORDER_NAMES[oi]), 15, SOFT, false);
        ord.setGravity(Gravity.CENTER);
        ord.setBackground(round(BTN, 18));
        ord.setPadding((int) (16 * s), (int) (8 * s), (int) (16 * s), (int) (8 * s));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.rightMargin = (int) (10 * s);
        bar.addView(ord, olp);
        ord.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String[] tr = new String[APP_ORDER_NAMES.length];
                for (int i = 0; i < tr.length; i++) tr[i] = I18n.t(APP_ORDER_NAMES[i]);
                new TB(Main.this, dlg()).raw()
                    .setTitle(I18n.t("Orden de las apps"))
                    .setSingleChoiceItems(tr, oi, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                        if (w == 4 && manualOrder().isEmpty()) {
                            saveManual(shown);   // arranca del orden que se ve
                            toast("Deja presionada una app y elige «Mover» para cambiarla de lugar");
                        }
                        prefs.edit().putString("apps_order", APP_ORDERS[w]).apply();
                        closeDrawer(); openDrawer();
                    }})
                    .show();
            }
        });
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
        grid.setNumColumns(vertical ? 4 : 6);
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
                Drawable d = iconOf(r);
                im.setImageDrawable(d);
                lb.setText(r.loadLabel(pm));
                return cell;
            }
        });
        final ResolveInfo[] moving = new ResolveInfo[1];
        final TextView titleTv = title;
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int p, long id) {
                if (moving[0] != null) {
                    // modo «Mover»: la app elegida pasa al lugar tocado
                    ResolveInfo m = moving[0];
                    moving[0] = null;
                    shown.remove(m);
                    shown.add(Math.min(p, shown.size()), m);
                    saveManual(shown);
                    closeDrawer(); openDrawer();
                    return;
                }
                launch(shown.get(p));
            }
        });
        // Dejar presionada una app: ocultarla del cajón o abrir su información en Android.
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> a, View v, int p, long id) {
                final ResolveInfo r = shown.get(p);
                new TB(Main.this, dlg())
                    .setTitle(String.valueOf(r.loadLabel(pm)))
                    .setItems(new String[]{"Ocultar del cajón", "Desinstalar", "Información de la app", "Mover"}, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            if (w == 3) {
                                moving[0] = r;
                                titleTv.setText(I18n.t("Toca el lugar donde quieres poner «" + r.loadLabel(pm) + "»"));
                                titleTv.setTextColor(RED);
                                return;
                            }
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
                if (d == null) { try { d = iconOf(r); } catch (Throwable e) { d = null; } if (d != null) icons.put(k, d); }
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

    // ───────────────────────── Escáner OBD-II (2.26) ─────────────────────────
    // Motor del PR #42 (Jaacern). Nada cambia en las pantallas de inicio: el vehículo se ve en un panel que se despliega
    // encima de cualquier tema (VehiclePanel) y se configura en Ajustes → Tu vehículo → Escáner OBD-II.

    private VehiclePanel vpanel;
    private VehiclePanel.Handle vhandle;
    private ObdDialogs obdDlg;
    /** Subpágina de «Tu vehículo» (null = la categoría): obd, obd_conn, obd_alerts, obd_codes, obd_live, obd_ai, obd_adv. */
    private String settingsSub;
    private final java.util.HashMap<String, TextView> obdLive = new java.util.HashMap<String, TextView>();
    private String obdPageKey = "";
    private final java.util.HashSet<String> critShown = new java.util.HashSet<String>();

    static final String[][] OBD_PAGES = {
        {"obd", "Escáner OBD-II"}, {"obd_conn", "Conexión"}, {"obd_alerts", "Alertas"}, {"obd_codes", "Códigos de falla"},
        {"obd_live", "Datos en vivo"}, {"obd_ai", "Inteligencia artificial"}, {"obd_adv", "Avanzado"},
    };

    private boolean obdOn() { return prefs.getBoolean("obd_on", false); }

    private ObdDialogs obdDialogs() {
        if (obdDlg == null) obdDlg = new ObdDialogs(this, new Runnable() { public void run() { obdChanged(); } });
        return obdDlg;
    }

    /** Después de un cambio hecho en una ventana del escáner: redibuja Ajustes y el panel o la pestaña. */
    private void obdChanged() {
        if (settingsView != null && settingsCat == C_VEHICLE) showSettings(C_VEHICLE);
        attachVehicle();
    }

    private boolean vehicleLeft() {
        String side = prefs.getString("obd_panel_side", "auto");
        return "left".equals(side) || ("auto".equals(side) && playerLeft);
    }

    private final VehiclePanel.Host vhost = new VehiclePanel.Host() {
        public void openSettings(String sub) { settingsSub = sub; showSettings(C_VEHICLE); }
        public void openChart(String key) { openObdChart(key); }
        public ObdDialogs dialogs() { return obdDialogs(); }
        public View backdrop() { return root; }
        public void onClosed() { }
    };

    /** Pone (o quita) el panel y la pestaña del borde según el ajuste; van encima del tema y debajo de Ajustes. */
    private void attachVehicle() {
        if (root == null) return;
        boolean wasOpen = vpanel != null && vpanel.isOpen();
        if (vhandle != null) { root.removeView(vhandle); vhandle = null; }
        if (vpanel != null) { vpanel.closeNow(); root.removeView(vpanel); vpanel = null; }
        if (!obdOn() || root.getWidth() <= 0 || s <= 0) return;
        boolean left = vehicleLeft();
        vpanel = new VehiclePanel(this, s, root.getWidth() / s, root.getHeight() / s, left, vhost);
        int at = settingsView != null && settingsView.getParent() == root ? root.indexOfChild(settingsView) : -1;
        if (at >= 0) root.addView(vpanel, at, new FrameLayout.LayoutParams(-1, -1));
        else root.addView(vpanel, new FrameLayout.LayoutParams(-1, -1));
        if (prefs.getBoolean("obd_handle", true)) {
            vhandle = new VehiclePanel.Handle(this, s, left, new Runnable() { public void run() { openVehiclePanel(); } });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (26 * s), (int) (120 * s));
            lp.gravity = (left ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
            at = settingsView != null && settingsView.getParent() == root ? root.indexOfChild(settingsView) : -1;
            if (at >= 0) root.addView(vhandle, at, lp);
            else root.addView(vhandle, lp);
        }
        if (wasOpen) vpanel.open();
    }

    /** Abre el panel del vehículo; con el monitor apagado lleva a sus ajustes. */
    private void openVehiclePanel() {
        if (!obdOn()) { settingsSub = null; showSettings(C_VEHICLE); toast(I18n.t("Enciende el monitor del motor en «Escáner OBD-II»")); return; }
        if (settingsView != null) closeSettings();
        closeDrawer();
        if (vpanel == null) attachVehicle();
        if (vpanel != null) vpanel.open();
    }

    private void openObdChart(String key) {
        try { startActivity(new Intent(this, ObdActivity.class).putExtra("chart", key)); } catch (Throwable e) { toast("No se pudo abrir la app"); }
    }

    private String obdStateText() {
        if (!Obd.running()) return I18n.t(prefs.contains("obd_addr") ? "Apagado" : "Elige tu escáner");
        if (Obd.status == Obd.LIVE) return I18n.t("En vivo") + (Obd.vin != null ? " · VIN " + Obd.vin : "");
        if (Obd.statusMsg.length() > 0) return I18n.t(Obd.statusMsg);
        return I18n.t("Conectando…");
    }

    /** Alertas graves: abren el panel aunque se esté en otra cosa (una vez por alerta); también avisa a las ventanas. */
    private final Obd.Listener obdWatch = new Obd.Listener() { public void onObd() {
        if (obdDlg != null) obdDlg.onObd();
        if (speedFromObd || !Float.isNaN(obdKmh())) showSpeed();   // velocímetro del inicio con la velocidad del escáner
        java.util.HashSet<String> now = new java.util.HashSet<String>();
        boolean fresh = false;
        synchronized (Obd.alerts) {
            for (Obd.Alert a : Obd.alerts) if (a.level == Obd.CRIT) { now.add(a.key); if (!critShown.contains(a.key)) fresh = true; }
        }
        critShown.retainAll(now);
        if (!fresh || vpanel == null) return;
        // Se da por vista solo cuando el usuario la ve: con el panel abierto o en las páginas del escáner. Si llega
        // mientras está en otros ajustes, el panel se abre al volver al inicio (antes se perdía y nunca se abría).
        boolean seen = vpanel.isOpen() || (settingsView != null && settingsSub != null && settingsSub.startsWith("obd")
            && !settingsSub.startsWith("obd_guide"));
        if (!seen && obdOn() && prefs.getBoolean("obd_panel_auto", true) && settingsView == null && !isFinishing()) {
            vpanel.open();
            seen = true;
        }
        if (seen || !prefs.getBoolean("obd_panel_auto", true)) critShown.addAll(now);
    }};

    /** Mientras se ve una página del escáner en Ajustes: los valores cambian en su sitio; si cambia lo que hay, se redibuja. */
    private final Obd.Listener obdSettingsL = new Obd.Listener() { public void onObd() {
        if (settingsView == null || settingsCat != C_VEHICLE) return;
        String k = obdStructure();
        if (!k.equals(obdPageKey)) { showSettings(C_VEHICLE); return; }
        for (java.util.Map.Entry<String, TextView> e : obdLive.entrySet()) {
            String v = obdLiveValue(e.getKey());
            if (v != null && !v.contentEquals(e.getValue().getText())) e.getValue().setText(v);
        }
    }};

    private String obdStructure() {
        StringBuilder b = new StringBuilder(settingsSub == null ? "" : settingsSub).append(Obd.running()).append(Obd.status == Obd.LIVE);
        if ("obd_codes".equals(settingsSub) || "obd".equals(settingsSub)) b.append(Obd.dtcs).append(Obd.pending).append(Obd.lastDtcCheck > 0);
        if ("obd_alerts".equals(settingsSub) || "obd".equals(settingsSub)) synchronized (Obd.alerts) { for (Obd.Alert a : Obd.alerts) b.append(a.key).append(a.level); }
        if ("obd_live".equals(settingsSub)) for (Obd.Sensor x : Obd.ALL) if (x.has()) b.append(x.key);
        return b.toString();
    }

    private String obdLiveValue(String key) {
        if ("status".equals(key)) return obdStateText();
        Obd.Sensor x = ObdDialogs.sensorOf(key);
        return x == null ? null : x.has() ? x.text() + " " + x.unit : "—";
    }

    /** Fila cuyo valor se actualiza solo (estado o un dato del motor). */
    private void liveRow(LinearLayout card, String title, String sub, String key, Runnable onClick) {
        row(card, title, sub, obdLiveValue(key), onClick);
        LinearLayout r = (LinearLayout) card.getChildAt(card.getChildCount() - 1);
        if (r.getChildCount() > 1 && r.getChildAt(1) instanceof TextView) obdLive.put(key, (TextView) r.getChildAt(1));
    }

    private Runnable obdGo(final String sub) {
        return new Runnable() { public void run() { settingsSub = sub; showSettings(C_VEHICLE); } };
    }

    private void settingsBack() {
        if (settingsSub != null && settingsSub.startsWith("obd_guide:")) settingsSub = "obd_guide";
        else if ("obd_guide".equals(settingsSub)) settingsSub = obdOn() ? "obd" : null;
        else settingsSub = "obd".equals(settingsSub) ? null : "obd";
        showSettings(C_VEHICLE);
    }

    /** Encabezado de una subpágina: volver y el título. */
    private void obdHeader(LinearLayout body) {
        String title = "", back = "Tu vehículo";
        for (String[] pg : OBD_PAGES) if (pg[0].equals(settingsSub)) title = pg[1];
        if (!"obd".equals(settingsSub)) back = "Escáner OBD-II";
        boolean raw = false;
        if ("obd_guide".equals(settingsSub)) { title = "Guía del escáner"; back = obdOn() ? "Escáner OBD-II" : "Tu vehículo"; }
        else if (settingsSub != null && settingsSub.startsWith("obd_guide:")) {
            ObdGuide.Section g = ObdGuide.get(this, settingsSub.substring(10));
            title = g != null ? g.title : "";
            raw = true;                                    // el título de la guía ya viene en el idioma elegido
            back = "Guía del escáner";
        }
        TextView b = tv("‹  " + I18n.t(back), 14, RED, true);
        b.setPadding((int) (4 * s), (int) (2 * s), (int) (12 * s), (int) (6 * s));
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { settingsBack(); } });
        body.addView(b, new LinearLayout.LayoutParams(-2, -2));
        TextView t = tv(raw ? "" : title, 24, TEXT, true);
        if (raw) { t.setText(title); t.setSingleLine(false); }
        t.setPadding((int) (4 * s), 0, 0, (int) (14 * s));
        body.addView(t);
    }

    private void obdPage(LinearLayout body, String sub) {
        final ObdDialogs d = obdDialogs();
        if (sub.startsWith("obd_guide")) { guidePage(body, sub); return; }   // se lee también con el monitor apagado
        obdPageKey = obdStructure();
        Obd.addListener(obdSettingsL);
        if (!obdOn() && !"obd_ai".equals(sub) && !"obd_adv".equals(sub)) {
            LinearLayout c = section(body, "Monitor del motor");
            toggle(c, "Monitor del motor", "Está apagado: enciéndelo para conectar el escáner", false,
                new IntCb() { public void on(int i) { d.turnOn(); }});
            return;
        }
        if ("obd".equals(sub)) {
            LinearLayout gu = section(body, "Guía");
            row(gu, "Guía del escáner", "Qué comprar, cómo conectarlo y qué significa cada cosa · sin internet", null, obdGo("obd_guide"));
            LinearLayout st = section(body, "Estado");
            row(st, "Escáner", null, ObdDialogs.deviceName(this), obdGo("obd_conn"));
            liveRow(st, "Conexión", null, "status", obdGo("obd_conn"));
            row(st, "Abrir el panel del vehículo", "Medidores, alertas y códigos encima del tema que tengas", null,
                new Runnable() { public void run() { openVehiclePanel(); } });
            List<Obd.Alert> al;
            synchronized (Obd.alerts) { al = new ArrayList<Obd.Alert>(Obd.alerts); }
            int nc = Obd.dtcs.size();
            for (String x : Obd.pending) if (!Obd.dtcs.contains(x)) nc++;
            LinearLayout dg = section(body, "Diagnóstico");
            row(dg, "Alertas", "Qué vigila StradaOS y cuándo avisa por voz",
                al.isEmpty() ? I18n.t("Todo normal") : I18n.t(al.size() == 1 ? "1 activa" : al.size() + " activas"), obdGo("obd_alerts"));
            row(dg, "Códigos de falla", "Qué significan y qué hacer; borrarlos con el motor apagado",
                nc == 0 ? I18n.t(Obd.lastDtcCheck > 0 ? "Ninguno" : "—") : String.valueOf(nc), obdGo("obd_codes"));
            row(dg, "Datos en vivo", "Cada dato que entrega tu auto, con gráficos", null, obdGo("obd_live"));
            LinearLayout mo = section(body, "Más");
            row(mo, "Inteligencia artificial", "Opcional: explica los códigos para tu auto en particular",
                I18n.t(DtcInfo.aiReady(this) ? "Configurada" : "Sin configurar"), obdGo("obd_ai"));
            row(mo, "Avanzado", "Motor, protocolo, panel y escaneo completo", null, obdGo("obd_adv"));
        } else if ("obd_conn".equals(sub)) {
            LinearLayout c = section(body, "Escáner");
            row(c, "Escáner", "Bluetooth emparejado, Wi-Fi (IP y puerto), cable USB o un auto simulado para probar", ObdDialogs.deviceName(this),
                new Runnable() { public void run() { d.pickAdapter(); } });
            row(c, "Buscar escáneres Bluetooth cercanos", "Empareja el escáner sin salir de StradaOS (clave 1234 o 0000)", null,
                new Runnable() { public void run() { d.discover(); } });
            // 2.31 (#51): escáner por cable USB (pantallas cuyo Bluetooth no ve el escáner, como muchas FYT)
            row(c, "Escáner por cable USB", "Cable ELM327 USB (chips CH340, FTDI, PL2303 o CP2102)", null,
                new Runnable() { public void run() { d.pickUsb(); } });
            liveRow(c, "Conexión", null, "status", new Runnable() { public void run() { d.reconnect(); } });
            row(c, "Reconectar", null, null, new Runnable() { public void run() { d.reconnect(); } });
            LinearLayout o = section(body, "Al encender");
            toggle(o, "Conectar al iniciar StradaOS", "Las alertas funcionan aunque estés en otra app (con una notificación fija)",
                prefs.getBoolean("obd_autostart", true), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("obd_autostart", i == 1).apply(); showSettings(C_VEHICLE); }});
            LinearLayout h2 = section(body, "Ayuda");
            row(h2, "Cómo conectar el escáner", null, null, new Runnable() { public void run() {
                new TB(Main.this, dlg()).setTitle("Cómo conectar el escáner")
                    .setMessage("1. Enchufa el escáner ELM327 en el conector OBD (bajo el volante) y pon el contacto.\n2. Bluetooth: emparéjalo con la clave 1234 o 0000. Wi-Fi: conecta la pantalla a la red del escáner y escribe su IP y puerto (casi siempre 192.168.0.10:35000). Cable USB: conéctalo a la pantalla y acepta el permiso que pide Android.\n3. Elígelo arriba en «Escáner».")
                    .setNegativeButton("Cerrar", null).show(); }});
            row(h2, "Registro de la conexión", "Lo que respondió el escáner (para reportar un problema)", null,
                new Runnable() { public void run() { d.showLog(); } });
        } else if ("obd_alerts".equals(sub)) {
            LinearLayout now = section(body, "Ahora");
            List<Obd.Alert> al;
            synchronized (Obd.alerts) { al = new ArrayList<Obd.Alert>(Obd.alerts); }
            if (al.isEmpty()) row(now, "Todo normal por ahora", null, null, null);
            for (final Obd.Alert a : al) row(now, a.title(), a.advice(), I18n.t(a.level == Obd.CRIT ? "Alerta" : "Aviso"),
                new Runnable() { public void run() { d.showAlert(a); } });
            LinearLayout av = section(body, "Avisos");
            toggle(av, "Avisos por voz", "Habla aunque estés en Waze o con música", prefs.getBoolean("obd_voice", true), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("obd_voice", i == 1).apply(); Obd.reloadPrefs(Main.this); showSettings(C_VEHICLE); }});
            toggle(av, "Abrir el panel con una alerta grave", "Por ejemplo, el motor muy caliente o la batería que no carga",
                prefs.getBoolean("obd_panel_auto", true), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("obd_panel_auto", i == 1).apply(); showSettings(C_VEHICLE); }});
            toggle(av, "Subida rápida del refrigerante", "Más de 4 °C por minuto con el motor caliente",
                prefs.getBoolean("obd_alert_coolant_rise", true), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("obd_alert_coolant_rise", i == 1).apply(); Obd.reloadPrefs(Main.this); showSettings(C_VEHICLE); }});
            toggle(av, "Códigos de falla nuevos (aviso por voz)", null,
                prefs.getBoolean("obd_alert_dtc", true), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("obd_alert_dtc", i == 1).apply(); Obd.reloadPrefs(Main.this); showSettings(C_VEHICLE); }});
            LinearLayout th = section(body, "Cuándo avisa cada dato");
            for (final Obd.Sensor x : ObdDialogs.alertSensors(prefs)) row(th, I18n.t(x.label), null, ObdDialogs.limits(x),
                new Runnable() { public void run() { d.editAlert(x); } });
        } else if ("obd_codes".equals(sub)) {
            List<String> codes = new ArrayList<String>(Obd.dtcs);
            for (String x : Obd.pending) if (!codes.contains(x)) codes.add(x);
            LinearLayout c = section(body, "Códigos guardados en el auto");
            if (codes.isEmpty()) row(c, Obd.lastDtcCheck > 0 ? "El auto no tiene códigos guardados" : "Se revisan al conectar y cada 30 segundos", null, null, null);
            for (final String code : codes) {
                DtcInfo.Entry e = DtcInfo.get(this, code);
                boolean pend = !Obd.dtcs.contains(code);
                String what = e.es != null ? e.es : e.en != null ? e.en : DtcInfo.system(code);
                row(c, code, what, I18n.t(pend ? "Pendiente" : e.severity >= 3 ? "Grave" : e.severity == 1 ? "Leve" : "Revisar"),
                    new Runnable() { public void run() { d.showCode(code); } });
            }
            if (Obd.mil) row(c, "Luz del motor encendida", "El computador del motor confirmó una falla", null, null);
            if (!Obd.dtcs.isEmpty()) {
                LinearLayout b = section(body, "Borrar");
                row(b, "Borrar códigos", "Con el motor apagado y el contacto puesto. Anota los códigos antes: no se puede deshacer", null,
                    new Runnable() { public void run() { d.confirmClear(); } });
            }
            String rd = Scan.readiness(Obd.status01);
            if (rd != null) {
                LinearLayout m = section(body, "Monitores de emisiones");
                row(m, rd, "✓ listo · ✗ aún no termina su prueba (normal después de borrar códigos o desconectar la batería).", null, null);
            }
        } else if ("obd_live".equals(sub)) {
            LinearLayout c = section(body, "Datos del motor");
            int n = 0;
            for (final Obd.Sensor x : Obd.ALL) {
                if (!x.has()) continue;
                n++;
                liveRow(c, I18n.t(x.label) + (x.pid == -2 ? " (" + I18n.t("calculado") + ")" : ""), null, x.key,
                    new Runnable() { public void run() { openObdChart(x.key); } });
            }
            if (n == 0) row(c, Obd.status == Obd.LIVE ? "Esperando datos" : "Conecta el escáner para ver los datos", null, null, null);
            LinearLayout g = section(body, "Gráficos");
            row(g, "Gráficos en vivo", "Hasta 3 datos a la vez, de 1 a 30 minutos", null, new Runnable() { public void run() { openObdChart("coolant"); } });
        } else if ("obd_ai".equals(sub)) {
            final String[] pr = DtcInfo.preset(this);
            LinearLayout c = section(body, "Proveedor");
            row(c, "Proveedor", null, I18n.t(pr[1]), new Runnable() { public void run() { d.pickProvider(); } });
            row(c, "API key", "La tuya: cada consulta la cobra tu proveedor", ObdDialogs.mask(prefs.getString("ai_key", "")),
                new Runnable() { public void run() { d.askKey(); } });
            row(c, "Modelo", null, DtcInfo.model(this).length() > 0 ? DtcInfo.model(this) : I18n.t("elegir…"),
                new Runnable() { public void run() { d.pickModel(); } });
            if ("custom".equals(pr[0])) row(c, "Dirección de la API", null, DtcInfo.baseUrl(this).length() > 0 ? DtcInfo.baseUrl(this) : "—",
                new Runnable() { public void run() { d.askUrl(); } });
            LinearLayout pv = section(body, "Privacidad");
            row(pv, "Qué se envía", "Solo el código, el vehículo que escribas y los datos del motor de ese momento; nunca tu ubicación. Las respuestas se guardan en la pantalla", null, null);
            row(pv, "Tu vehículo (para la IA)", "Marca, modelo, año, motor y caja", prefs.getString("obd_vehicle", "").length() > 0 ? prefs.getString("obd_vehicle", "") : "—",
                new Runnable() { public void run() { d.askVehicle(); } });
            toggle(pv, "Enviar el VIN a la IA", "Identifica tu auto de forma única. Apagado por defecto", prefs.getBoolean("obd_ai_vin", false), new IntCb() { public void on(int i) {
                prefs.edit().putBoolean("obd_ai_vin", i == 1).apply(); showSettings(C_VEHICLE); }});
            LinearLayout t = section(body, "Probar");
            row(t, "Probar la IA", "Pregunta por un código de ejemplo", null, new Runnable() { public void run() { d.testAi(); } });
            if (prefs.getString("ai_key", "").length() > 0) row(t, "Borrar la API key", null, null, new Runnable() { public void run() {
                prefs.edit().remove("ai_key").apply(); showSettings(C_VEHICLE); }});
        } else if ("obd_adv".equals(sub)) {
            LinearLayout m = section(body, "Motor");
            row(m, "Cilindrada del motor", "Para calcular el consumo en motores sin sensor MAF",
                String.format(java.util.Locale.US, "%.1f L", prefs.getFloat("obd_displacement", 2.4f)), new Runnable() { public void run() { d.askDisplacement(); } });
            row(m, "Protocolo", "Automático casi siempre; fuerza uno si el escáner no lo detecta", ObdDialogs.protocolName(prefs.getString("obd_protocol", "auto")),
                new Runnable() { public void run() { d.pickProtocol(); } });
            LinearLayout ve = section(body, "Velocímetro");
            toggle(ve, "Usar la velocidad del escáner", "Con el escáner conectado, el inicio muestra la velocidad del carro en vez de la del GPS: más exacta y sin retraso",
                prefs.getBoolean("obd_speed", true), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("obd_speed", i == 1).apply(); showSpeed(); showSettings(C_VEHICLE); }});
            // Solo en StradaOS Prueba: ver el velocímetro con el auto simulado, para probar sin escáner
            if (getPackageName().endsWith(".debug"))
                toggle(ve, "Prueba: usar también el auto simulado", "Solo en StradaOS Prueba. Elige «Auto simulado» en Conexión y mira el inicio",
                    prefs.getBoolean("obd_speed_demo", false), new IntCb() { public void on(int i) {
                        prefs.edit().putBoolean("obd_speed_demo", i == 1).apply(); showSpeed(); showSettings(C_VEHICLE); }});
            LinearLayout pn = section(body, "Panel del vehículo");
            final String[] sides = {"auto", "left", "right"};
            segmented(pn, "Lado del panel", "Automático: del lado del reproductor (el del conductor)", new String[]{"Automático", "Izquierda", "Derecha"},
                Math.max(0, java.util.Arrays.asList(sides).indexOf(prefs.getString("obd_panel_side", "auto"))), new IntCb() { public void on(int i) {
                    prefs.edit().putString("obd_panel_side", sides[i]).apply(); attachVehicle(); showSettings(C_VEHICLE); }});
            toggle(pn, "Pestaña en el borde", "Toca o desliza la pestaña para abrir el panel. Sin ella, ábrelo con un botón de la barra",
                prefs.getBoolean("obd_handle", true), new IntCb() { public void on(int i) {
                    prefs.edit().putBoolean("obd_handle", i == 1).apply(); attachVehicle(); showSettings(C_VEHICLE); }});
            LinearLayout sc = section(body, "Diagnóstico");
            row(sc, "Escanear todo lo que entrega el auto", "Informe de cada dato estándar y los propios de la marca. Solo lee", null,
                new Runnable() { public void run() { d.scanAll(); } });
            if (Obd.report != null) row(sc, "Ver el último informe", null, null, new Runnable() { public void run() { d.showReport(); } });
        }
    }

    // ── Guía del escáner (2.26): textos ya traducidos en assets/guide (ObdGuide), sin internet ──

    /** Fila con textos que ya vienen en el idioma elegido (no pasan por I18n.t). */
    private void rowRaw(LinearLayout card, String title, String sub, final Runnable onClick) {
        LinearLayout r = rowBase(card, "", null);
        LinearLayout col = (LinearLayout) r.getChildAt(0);
        ((TextView) col.getChildAt(0)).setText(title);
        if (sub != null && sub.length() > 0) {
            TextView st = tv("", 12, MUTED, false);
            st.setText(sub);
            st.setSingleLine(false);
            st.setPadding(0, (int) (3 * s), 0, 0);
            col.addView(st);
        }
        if (onClick != null) {
            ImageView ch = icon("chevron_right", 20);
            ch.setAlpha(0.6f);
            r.addView(ch, new LinearLayout.LayoutParams((int) (20 * s), (int) (20 * s)));
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onClick.run(); } });
        }
    }

    private TextView guideText(String text, float size, int color, boolean medium) {
        TextView v = tv("", size, color, medium);
        // en árabe cada párrafo va de derecha a izquierda aunque empiece con una palabra latina (Groq, Claude…)
        v.setText("ar".equals(I18n.lang) ? "\u200F" + text : text);
        v.setSingleLine(false);
        v.setEllipsize(null);
        v.setLineSpacing(4 * s, 1f);
        return v;
    }

    private void guidePage(LinearLayout body, String sub) {
        if ("obd_guide".equals(sub)) {
            LinearLayout c = section(body, "Guía");
            for (final ObdGuide.Section g : ObdGuide.load(this))
                rowRaw(c, g.title, g.sub, obdGo("obd_guide:" + g.id));
            return;
        }
        String id = sub.substring(10);
        ObdGuide.Section g = ObdGuide.get(this, id);
        if (g == null) { settingsSub = "obd_guide"; showSettings(C_VEHICLE); return; }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, 16));
        card.setPadding((int) (20 * s), (int) (16 * s), (int) (20 * s), (int) (8 * s));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = (int) (10 * s);
        body.addView(card, clp);
        List<ObdGuide.Item> acts = new ArrayList<ObdGuide.Item>();
        for (ObdGuide.Item it : g.items) {
            if (it.type == ObdGuide.ACTION) { acts.add(it); continue; }
            if (it.type == ObdGuide.SUBHEAD) {
                TextView h = guideText(it.text, 15, TEXT, true);
                h.setPadding(0, (int) ((card.getChildCount() > 0 ? 10 : 0) * s), 0, (int) (8 * s));
                card.addView(h);
            } else if (it.type == ObdGuide.BULLET) {
                LinearLayout r = new LinearLayout(this);
                boolean rtl = "ar".equals(I18n.lang);
                TextView dot = guideText("•", 14, RED, true);
                dot.setPadding(rtl ? (int) (10 * s) : 0, 0, rtl ? 0 : (int) (10 * s), 0);
                TextView bt = guideText(it.text, 14, SOFT, false);
                if (rtl) bt.setGravity(Gravity.RIGHT);
                if (!rtl) r.addView(dot);
                r.addView(bt, new LinearLayout.LayoutParams(0, -2, 1f));
                if (rtl) r.addView(dot);                       // la viñeta, a la derecha
                r.setPadding(rtl ? 0 : (int) (4 * s), 0, rtl ? (int) (4 * s) : 0, (int) (8 * s));
                card.addView(r);
            } else {
                TextView pt = guideText(it.text, 14, SOFT, false);
                pt.setPadding(0, 0, 0, (int) (10 * s));
                card.addView(pt);
            }
        }
        if (!acts.isEmpty()) {
            int h = body.getChildCount();
            LinearLayout a = section(body, "");
            body.removeViewAt(h);                              // sin encabezado: los botones van justo debajo del texto
            for (final ObdGuide.Item it : acts) rowRaw(a, it.text, null, guideAction(it.action));
        }
        final ObdGuide.Section nx = ObdGuide.next(this, id);
        if (nx != null) {
            LinearLayout n = section(body, "Siguiente");
            rowRaw(n, nx.title, nx.sub, obdGo("obd_guide:" + nx.id));
        }
    }

    private Runnable guideAction(final String action) {
        if ("demo".equals(action)) return new Runnable() { public void run() {
            String cur = prefs.getString("obd_addr", "");
            // con un escáner real ya elegido no se cambia sin preguntar: se lleva a Conexión, donde se elige a propósito
            if (cur.length() > 0 && !"demo".equals(cur)) { settingsSub = "obd_conn"; showSettings(C_VEHICLE); return; }
            obdDialogs().choose("demo", I18n.t("Auto simulado"));
            settingsSub = "obd"; showSettings(C_VEHICLE); }};
        if ("panel".equals(action)) return new Runnable() { public void run() { openVehiclePanel(); } };
        if (action.startsWith("obd")) return obdGo(action);
        return null;
    }
}
