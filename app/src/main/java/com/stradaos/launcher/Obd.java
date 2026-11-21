package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Monitor OBD-II en vivo: lee los sensores del auto a través de {@link Elm}, decide cuándo algo está fuera de lo
 * normal y avisa con un tono y por voz, en el idioma de StradaOS. Corre en su propio hilo dentro de
 * {@link ObdService}, así que sigue avisando aunque esté abierta otra app (Waze, música…).
 *
 * Una alerta solo salta si el valor se mantiene fuera de rango un rato (hold), para no gritar por un pico de un
 * segundo. Los umbrales sirven para la mayoría de los motores bencineros; los que dependen del motor caliente
 * o en marcha (mezcla, voltaje de carga) solo se miran en esas condiciones.
 */
public class Obd {

    public interface Listener { void onObd(); }

    static final int OFF = 0, CONNECTING = 1, LIVE = 2, WAITING = 3, ERROR = 4;
    static final int OK = 0, WARN = 1, CRIT = 2;

    /** Un dato del auto: cómo se pide, cómo se lee y cuándo avisar. */
    static class Sensor {
        final String key, label, unit;
        final int pid;            // PID del modo 01; -1 = voltaje leído por el propio escáner (ATRV)
        final boolean fast;       // se lee en cada vuelta (si no, cada 4)
        final float min, max;     // escala de la barra y el gráfico
        float hi1 = Float.NaN, hi2 = Float.NaN, lo1 = Float.NaN, lo2 = Float.NaN, abs1 = Float.NaN, abs2 = Float.NaN;
        int holdS = 3;
        /** El usuario puede apagar la alerta de este dato o cambiar sus umbrales (Ajustes → Alertas a vigilar). */
        boolean enabled = true;
        /** Muestra un decimal aunque la unidad no sea «V» (avance, lambda, correcciones de mezcla). */
        boolean oneDecimal;
        float[] def;
        float value = Float.NaN;
        int level = OK;
        long badSince;
        final float[] hist = new float[120];
        int histN;
        /** Cuántas lecturas van: la pantalla redibuja la tarjeta solo cuando cambia. */
        volatile long pushes;
        /** Hora (SystemClock.elapsedRealtime) de la última lectura: para no mostrar un dato viejo si el escáner se calla. */
        volatile long at;
        /** Historial largo con la hora de cada lectura (~30 min con una lectura cada 0,7 s), para los gráficos grandes. */
        static final int LONG = 2700;
        private final float[] lv = new float[LONG];
        private final long[] lt = new long[LONG];
        private int lHead, lN;

        Sensor(String key, String label, String unit, int pid, boolean fast, float min, float max) {
            this.key = key; this.label = label; this.unit = unit; this.pid = pid; this.fast = fast; this.min = min; this.max = max;
        }
        Sensor high(float warn, float crit) { hi1 = warn; hi2 = crit; return this; }
        Sensor low(float warn, float crit) { lo1 = warn; lo2 = crit; return this; }
        Sensor abs(float warn, float crit) { abs1 = warn; abs2 = crit; return this; }
        Sensor hold(int s) { holdS = s; return this; }
        Sensor dec() { oneDecimal = true; return this; }

        boolean has() { return !Float.isNaN(value); }

        /** true si este dato puede dar alertas. */
        boolean alerts() { return !Float.isNaN(hi1) || !Float.isNaN(lo1) || !Float.isNaN(abs1); }

        /** Umbrales guardados por el usuario (o los de fábrica). */
        void load(SharedPreferences p) {
            if (def == null) def = new float[]{hi1, hi2, lo1, lo2, abs1, abs2};
            enabled = p.getBoolean("obd_alert_" + key, true);
            hi1 = p.getFloat("obd_thr_" + key + "_hi1", def[0]); hi2 = p.getFloat("obd_thr_" + key + "_hi2", def[1]);
            lo1 = p.getFloat("obd_thr_" + key + "_lo1", def[2]); lo2 = p.getFloat("obd_thr_" + key + "_lo2", def[3]);
            abs1 = p.getFloat("obd_thr_" + key + "_abs1", def[4]); abs2 = p.getFloat("obd_thr_" + key + "_abs2", def[5]);
        }

        void push(float v) {
            value = v;
            at = android.os.SystemClock.elapsedRealtime();
            System.arraycopy(hist, 1, hist, 0, hist.length - 1);
            hist[hist.length - 1] = v;
            if (histN < hist.length) histN++;
            pushes++;
            synchronized (this) {
                lv[lHead] = v;
                lt[lHead] = System.currentTimeMillis();
                lHead = (lHead + 1) % LONG;
                if (lN < LONG) lN++;
            }
        }

        /** Copia las lecturas desde «since» (de la más antigua a la más nueva). Devuelve cuántas copió. */
        synchronized int since(long since, float[] vo, long[] to) {
            int n = 0;
            for (int i = 0; i < lN; i++) {
                int at = (lHead - lN + i + LONG) % LONG;
                if (lt[at] < since) continue;
                vo[n] = lv[at];
                to[n] = lt[at];
                n++;
            }
            return n;
        }

        synchronized void clearHistory() { lN = 0; lHead = 0; histN = 0; }

        /** Nivel del valor actual según los umbrales, sin esperar el hold. */
        int rawLevel() {
            float v = value;
            if (Float.isNaN(v)) return OK;
            if (!Float.isNaN(abs2) && Math.abs(v) >= abs2) return CRIT;
            if (!Float.isNaN(hi2) && v >= hi2) return CRIT;
            if (!Float.isNaN(lo2) && v <= lo2) return CRIT;
            if (!Float.isNaN(abs1) && Math.abs(v) >= abs1) return WARN;
            if (!Float.isNaN(hi1) && v >= hi1) return WARN;
            if (!Float.isNaN(lo1) && v <= lo1) return WARN;
            return OK;
        }

        String text() {
            if (!has()) return "—";
            if (oneDecimal || "V".equals(unit) || pid == -2 || pid == -3) return String.format(I18n.num, "%.1f", value);
            if (!Float.isNaN(abs1)) return String.format(I18n.num, "%+.0f", value);
            return I18n.integer(Math.round(value));
        }
    }

    static final Sensor COOLANT = new Sensor("coolant", "Refrigerante", "°C", 0x05, true, 0, 130).high(100, 106).hold(3);
    static final Sensor VOLT = new Sensor("volt", "Batería", "V", -1, true, 10, 16).low(12.6f, 11.8f).high(15.2f, 15.8f).hold(15);
    static final Sensor RPM = new Sensor("rpm", "RPM", "rpm", 0x0C, true, 0, 7000).high(5200, 6000).hold(2);
    static final Sensor SPEED = new Sensor("speed", "Velocidad", "km/h", 0x0D, true, 0, 200);
    static final Sensor LOAD = new Sensor("load", "Carga del motor", "%", 0x04, true, 0, 100).high(92, 101).hold(20);
    static final Sensor THROTTLE = new Sensor("throttle", "Acelerador", "%", 0x11, true, 0, 100);
    static final Sensor INTAKE = new Sensor("intake", "Aire de admisión", "°C", 0x0F, false, -20, 90).high(60, 75).hold(20);
    static final Sensor FUEL = new Sensor("fuel", "Combustible", "%", 0x2F, false, 0, 100).low(15, 8).hold(30);
    static final Sensor STFT = new Sensor("stft", "Mezcla corta", "%", 0x06, false, -30, 30).abs(15, 25).hold(30);
    static final Sensor LTFT = new Sensor("ltft", "Mezcla larga", "%", 0x07, false, -30, 30).abs(10, 20).hold(60);
    static final Sensor MAF = new Sensor("maf", "Flujo de aire", "g/s", 0x10, false, 0, 150);
    /** Presión absoluta del múltiple: la usan los motores sin MAF, como muchos Kia/Hyundai 2.4. */
    static final Sensor MAP = new Sensor("map", "Presión del múltiple", "kPa", 0x0B, true, 0, 255);
    /** Consumo que entrega el propio auto (PID 5E), si lo tiene. */
    static final Sensor FUEL_RATE = new Sensor("fuel_rate", "Consumo del motor", "L/h", 0x5E, false, 0, 40);
    static final Sensor OILT = new Sensor("oilt", "Aceite del motor", "°C", 0x5C, false, 0, 150).high(125, 135).hold(10);
    static final Sensor AMBIENT = new Sensor("ambient", "Temperatura exterior", "°C", 0x46, false, -20, 50);
    // Los demás datos estándar que entrega el Sorento 2010 (y muchos otros autos). No son tan urgentes como los de
    // arriba, así que se leen cada 4 vueltas: sirven para «Todos los datos», el panel y los gráficos.
    static final Sensor TIMING = new Sensor("timing", "Avance del encendido", "°", 0x0E, false, -20, 60).dec();
    static final Sensor O2_1 = new Sensor("o2_1", "Sonda O2 banco 1", "V", 0x14, false, 0, 1.2f);
    static final Sensor O2_2 = new Sensor("o2_2", "Sonda O2 banco 1 sensor 2", "V", 0x15, false, 0, 1.2f);
    static final Sensor RUN_TIME = new Sensor("run_time", "Tiempo de marcha", "s", 0x1F, false, 0, 86400);
    static final Sensor MIL_DIST = new Sensor("mil_dist", "Distancia con la luz del motor", "km", 0x21, false, 0, 100000);
    static final Sensor EGR = new Sensor("egr", "EGR comandada", "%", 0x2E, false, 0, 100);
    static final Sensor WARMUPS = new Sensor("warmups", "Arranques desde borrar códigos", "", 0x30, false, 0, 255);
    static final Sensor CLEAR_DIST = new Sensor("clear_dist", "Distancia desde borrar códigos", "km", 0x31, false, 0, 100000);
    static final Sensor BARO = new Sensor("baro", "Presión barométrica", "kPa", 0x33, false, 0, 120);
    static final Sensor CAT1 = new Sensor("cat1", "Catalizador banco 1", "°C", 0x3C, false, -40, 1000);
    static final Sensor CAT2 = new Sensor("cat2", "Catalizador banco 1 sensor 2", "°C", 0x3E, false, -40, 1000);
    static final Sensor ABS_LOAD = new Sensor("abs_load", "Carga absoluta", "%", 0x43, false, 0, 100);
    static final Sensor LAMBDA = new Sensor("lambda", "Mezcla comandada", "λ", 0x44, false, 0.5f, 1.5f).dec();
    static final Sensor THROTTLE_REL = new Sensor("throttle_rel", "Acelerador relativo", "%", 0x45, false, 0, 100);
    static final Sensor THROTTLE_B = new Sensor("throttle_b", "Acelerador B", "%", 0x47, false, 0, 100);
    static final Sensor PEDAL_D = new Sensor("pedal_d", "Pedal D", "%", 0x49, false, 0, 100);
    static final Sensor PEDAL_E = new Sensor("pedal_e", "Pedal E", "%", 0x4A, false, 0, 100);
    static final Sensor THROTTLE_CMD = new Sensor("throttle_cmd", "Acelerador comandado", "%", 0x4C, false, 0, 100);
    static final Sensor MIL_TIME = new Sensor("mil_time", "Tiempo con la luz del motor", "min", 0x4D, false, 0, 100000);
    static final Sensor CLEAR_TIME = new Sensor("clear_time", "Tiempo desde borrar códigos", "min", 0x4E, false, 0, 100000);
    static final Sensor O2_TRIM_1 = new Sensor("o2_trim_1", "Mezcla O2 banco 1", "%", 0x55, false, -100, 100).dec();
    static final Sensor O2_TRIM_2 = new Sensor("o2_trim_2", "Mezcla O2 banco 1 sensor 2", "%", 0x56, false, -100, 100).dec();
    /** Calculado con el MAF, el PID 5E o el MAP y la velocidad (bencina, mezcla 14,7:1): no lo entrega el auto. */
    static final Sensor CONSUMPTION = new Sensor("consumption", "Consumo", "L/100 km", -2, true, 0, 30);
    /** Calculado en litros por hora; también se usa para el combustible del viaje. */
    static final Sensor FUEL_H = new Sensor("fuel_h", "Consumo por hora", "L/h", -3, true, 0, 40);
    static final Sensor[] ALL = {COOLANT, VOLT, RPM, SPEED, LOAD, THROTTLE, INTAKE, FUEL, STFT, LTFT, MAF, MAP, CONSUMPTION, FUEL_H, OILT, AMBIENT, FUEL_RATE,
        TIMING, O2_1, O2_2, RUN_TIME, MIL_DIST, EGR, WARMUPS, CLEAR_DIST, BARO, CAT1, CAT2, ABS_LOAD, LAMBDA, THROTTLE_REL, THROTTLE_B, PEDAL_D, PEDAL_E,
        THROTTLE_CMD, MIL_TIME, CLEAR_TIME, O2_TRIM_1, O2_TRIM_2};

    /** Todos los demás datos estándar que el auto entrega (PID → bytes), leídos de a poco para no frenar las alertas. */
    static final java.util.TreeMap<Integer, byte[]> extra = new java.util.TreeMap<Integer, byte[]>();
    /** Respuesta del PID 01 (luz del motor y monitores de emisiones). */
    static volatile byte[] status01;
    /** Datos guardados por el computador en el momento en que se registró el código (modo 02, «freeze frame»). */
    static volatile java.util.LinkedHashMap<Integer, byte[]> freeze;
    static volatile String freezeCode;
    static volatile boolean freezeDone;
    private static volatile boolean freezeRequested;

    /** Lee los umbrales y alertas que eligió el usuario. */
    static void reloadPrefs(Context c) {
        SharedPreferences p = prefs(c);
        for (Sensor s : ALL) s.load(p);
    }

    /** Alerta activa o reciente, para la lista de la pantalla. */
    /**
     * Una alerta. Se guarda en español y se traduce al mostrarla: si el usuario cambia de idioma, las alertas que
     * ya estaban salen en el idioma nuevo (antes quedaban en el idioma en que se crearon).
     */
    static class Alert {
        String key, titleEs, adviceEs;
        String label;                 // nombre del sensor cuando el título es «nombre: valor» (límites a medida)
        String code;                  // códigos de falla: el texto sale de la tabla, en el idioma de ahora
        boolean pending;
        int level;
        long at;

        String title() {
            if (code != null) return I18n.t((pending ? "Código pendiente " : "Código de falla ") + code);
            if (label != null && titleEs.startsWith(label + ": ")) return I18n.t(label) + titleEs.substring(label.length());
            return I18n.t(titleEs);
        }

        String advice() {
            if (code != null && app != null) {
                DtcInfo.Entry e = DtcInfo.get(app, code);
                return e.es != null ? e.es + " " + e.advice : e.en != null ? e.en : "";
            }
            return I18n.t(adviceEs);
        }
    }

    // ── estado (lo lee la pantalla) ──
    static volatile int status = OFF;
    static volatile String statusMsg = "";
    static volatile String adapterInfo = "", vin;
    static volatile boolean mil;
    static volatile List<String> dtcs = new ArrayList<String>(), pending = new ArrayList<String>();
    static final List<Alert> alerts = new ArrayList<Alert>();
    static volatile long lastDtcCheck;
    static final List<String> LOG = new ArrayList<String>();

    private static final List<Listener> listeners = new ArrayList<Listener>();
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static Thread thread;
    private static volatile boolean running, clearRequested, scanRequested;
    /** Último informe de «Escanear todo» y dónde se guardó. */
    static volatile String report, reportPath;
    static volatile boolean scanning;
    private static Elm elm;
    private static TextToSpeech tts;
    private static boolean ttsReady;
    private static java.util.Locale ttsLoc;
    private static ToneGenerator tone;
    private static final HashMap<String, Long> lastSaid = new HashMap<String, Long>();
    private static Context app;

    static void addListener(Listener l) { synchronized (listeners) { if (!listeners.contains(l)) listeners.add(l); } }
    static void removeListener(Listener l) { synchronized (listeners) { listeners.remove(l); } }

    private static void changed() {
        ui.post(new Runnable() { public void run() {
            List<Listener> copy;
            synchronized (listeners) { copy = new ArrayList<Listener>(listeners); }
            for (Listener l : copy) try { l.onObd(); } catch (Throwable ignored) { }
        }});
    }

    static void log(String m) {
        synchronized (LOG) {
            LOG.add(new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date()) + " " + m);
            while (LOG.size() > 40) LOG.remove(0);
        }
    }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("launcher", Context.MODE_PRIVATE); }

    static boolean running() { return running; }

    static synchronized void start(Context c) {
        if (running) return;
        app = c.getApplicationContext();
        running = true;
        for (Sensor s : ALL) { s.value = Float.NaN; s.level = OK; s.clearHistory(); s.badSince = 0; }
        synchronized (alerts) { alerts.clear(); }
        dtcs = new ArrayList<String>(); pending = new ArrayList<String>(); mil = false; vin = null;
        synchronized (extra) { extra.clear(); }
        status01 = null; freeze = null; freezeCode = null; freezeDone = false;
        reloadPrefs(app);
        initVoice();
        thread = new Thread(new Runnable() { public void run() { loop(); } }, "StradaOBD");
        thread.start();
    }

    static synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
        Elm e = elm;
        if (e != null) e.close();
        status = OFF;
        statusMsg = "";
        changed();
    }

    /** Pide el «freeze frame» (datos al momento del código); llega en la siguiente vuelta. */
    static void requestFreeze() { freezeDone = false; freezeRequested = true; }

    private static void readFreeze() throws java.io.IOException {
        java.util.LinkedHashMap<Integer, byte[]> f = new java.util.LinkedHashMap<Integer, byte[]>();
        String code = null;
        byte[] d = elm.freeze(0x02);
        if (d != null && d.length >= 2 && (d[0] != 0 || d[1] != 0)) {
            int v = ((d[0] & 0xFF) << 8) | (d[1] & 0xFF);
            code = "PCBU".charAt(v >> 14) + String.format(Locale.US, "%04X", v & 0x3FFF);
            for (int pid : new int[]{0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x1F, 0x2F, 0x42, 0x46}) {
                byte[] b = elm.freeze(pid);
                if (b != null && b.length > 0) f.put(pid, b);
            }
        }
        freezeCode = code;
        freeze = f;
        freezeDone = true;
        log(code != null ? "freeze frame de " + code + ": " + f.size() + " datos" : "el auto no guardó freeze frame");
    }

    /** Escanear todo lo que entrega el auto: lo hace el hilo en la siguiente vuelta (pausa las alertas mientras tanto). */
    static void requestScan() { scanRequested = true; }

    private static void doScan() {
        scanning = true;
        String before = statusMsg;
        try {
            report = Scan.run(elm, new Scan.Progress() { public void on(String step) {
                statusMsg = "Escaneando: " + step;          // se traduce al mostrarlo (patrón «Escaneando: {}»)
                changed();
            }});
            reportPath = saveReport(report);
            log("escaneo terminado" + (reportPath != null ? ": " + reportPath : ""));
            say("scan", OK, I18n.t("Escaneo terminado."));
        } catch (Throwable t) {
            report = I18n.t("El escaneo se cortó") + ": " + t.getMessage();
            reportPath = null;
            log("escaneo cortado: " + t);
        } finally {
            scanning = false;
            statusMsg = before;
            changed();
        }
    }

    /** Guarda el informe en Android/data/…/files/obd (se puede sacar por USB o adb). */
    private static String saveReport(String text) {
        try {
            java.io.File dir = app.getExternalFilesDir("obd");
            if (dir == null) dir = new java.io.File(app.getFilesDir(), "obd");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "informe-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new java.util.Date()) + ".txt");
            java.io.OutputStream o = new java.io.FileOutputStream(f);
            o.write(text.getBytes("UTF-8"));
            o.close();
            return f.getAbsolutePath();
        } catch (Throwable t) { return null; }
    }

    /** Borrar los códigos guardados: lo hace el hilo en la siguiente vuelta. */
    static void requestClear() { clearRequested = true; }

    private static void loop() {
        String addr = prefs(app).getString("obd_addr", "demo");
        int fails = 0;
        boolean wasLive = false;
        while (running) {
            status = CONNECTING;
            statusMsg = "Conectando con el escáner…";
            changed();
            elm = new Elm();
            elm.protocol(prefs(app).getString("obd_protocol", "auto"));
            try {
                elm.open(addr);
                adapterInfo = elm.version + " · " + elm.protocol;
                log("conectado: " + adapterInfo);
                status = LIVE;
                statusMsg = "Conectado";
                fails = 0;
                if (!wasLive) say("obd", OK, I18n.t("Escáner conectado. Vigilando el motor."));
                wasLive = true;
                changed();
                try { vin = elm.vin(); } catch (Throwable ignored) { }
                poll();
            } catch (Throwable t) {
                if (!running) break;
                log("error: " + t.getMessage());
                fails++;
                status = wasLive ? ERROR : fails > 1 ? ERROR : CONNECTING;
                statusMsg = t.getMessage() != null ? t.getMessage() : t.toString();
                if (wasLive) {
                    say("lost", WARN, I18n.t("Se perdió la conexión con el escáner."));
                    wasLive = false;
                }
                changed();
            } finally {
                elm.close();
            }
            sleep(fails > 6 ? 15000 : 5000);
        }
    }

    private static void poll() throws java.io.IOException {
        int round = 0, empty = 0;
        lastDtcCheck = 0;
        while (running) {
            long t0 = System.currentTimeMillis();
            if (scanRequested) { scanRequested = false; doScan(); }
            if (freezeRequested) { freezeRequested = false; readFreeze(); }
            int got = 0;
            for (Sensor s : ALL) {
                if (!s.fast && round % 4 != 0) continue;
                Float v = read(s);
                if (v != null) { s.push(v); got++; }
            }
            // consumo: el dato del auto (PID 5E), el MAF o, en los Kia/Hyundai sin MAF, la presión del múltiple
            float lph = fuelRateLph();
            if (!Float.isNaN(lph)) {
                FUEL_H.push(lph);
                if (SPEED.has() && SPEED.value > 3)
                    CONSUMPTION.push(Math.min(99, lph / SPEED.value * 100f));
            }
            // de a dos por vuelta, el resto de los datos que el auto entrega (para «Todos los datos»)
            readExtra(round);
            round++;
            if (got <= 1) {                       // solo responde el voltaje: contacto apagado
                if (++empty == 3) { status = WAITING; statusMsg = "Esperando al auto (¿contacto apagado?)"; changed(); }
                sleep(2000);
                continue;
            }
            if (empty >= 3) { status = LIVE; statusMsg = "Conectado"; }
            empty = 0;
            evaluate();
            if (clearRequested) {
                clearRequested = false;
                boolean ok = elm.clear();
                log(ok ? "códigos borrados" : "no se pudieron borrar los códigos");
                say("clear", OK, I18n.t(ok ? "Códigos borrados." : "El auto no aceptó borrar los códigos."));
                lastDtcCheck = 0;
            }
            if (System.currentTimeMillis() - lastDtcCheck > 30000) checkDtcs();
            changed();
            sleep(Math.max(50, 700 - (System.currentTimeMillis() - t0)));
        }
    }

    private static int extraAt;

    private static void readExtra(int round) throws java.io.IOException {
        java.util.List<Integer> todo = new java.util.ArrayList<Integer>();
        for (int pid = 2; pid <= 0xE0; pid++) {
            if (pid % 0x20 == 0 || !elm.supports(pid)) continue;
            boolean known = false;
            for (Sensor s : ALL) if (s.pid == pid) known = true;
            if (!known) todo.add(pid);
        }
        if (todo.isEmpty()) return;
        for (int i = 0; i < 2; i++) {
            int pid = todo.get(extraAt++ % todo.size());
            byte[] b = elm.pid(pid);
            if (b != null) synchronized (extra) { extra.put(pid, b); }
        }
    }

    /**
     * Litros por hora. Primero el dato del auto (PID 5E); si no, el flujo de aire (MAF); si no, la presión del
     * múltiple (MAP) con la cilindrada. Los Kia/Hyundai 2.4 no siempre traen MAF: sin el MAP el consumo quedaría «—».
     */
    static float fuelRateLph() {
        if (FUEL_RATE.has() && FUEL_RATE.value > 0) return FUEL_RATE.value;
        if (MAF.has() && MAF.value > 0) return MAF.value * 3600f / (14.7f * 745f);
        if (MAP.has() && RPM.has() && RPM.value > 200 && MAP.value > 5) {
            float t = INTAKE.has() ? INTAKE.value : 25f;
            if (t < -30 || t > 120) t = 25f;
            SharedPreferences p = app != null ? prefs(app) : null;
            float disp = p != null ? p.getFloat("obd_displacement", 2.4f) : 2.4f;
            if (disp < 0.5f) disp = 2.4f;
            // caudal másico (g/s) por la densidad del aire: MAP·RPM·cilindrada·rendimiento volumétrico / (34,446·T)
            float gps = MAP.value * RPM.value * disp * 0.85f / (34.446f * (t + 273.15f));
            if (gps > 0) return gps * 3600f / (14.7f * 745f);
        }
        return Float.NaN;
    }

    private static Float read(Sensor s) throws java.io.IOException {
        if (s.pid == -2 || s.pid == -3) return null;          // calculados, no se le piden al auto
        if (s.pid < 0) {
            // voltaje: el que mide el computador del motor (PID 42) si lo entrega; el del escáner (ATRV) solo si no.
            // Los ELM327 clon suelen medir casi 1 V de menos (Kia Sorento: ATRV 11,3 V con el computador en 12,2 V).
            if (elm.supports(0x42)) {
                byte[] b = elm.pid(0x42);
                if (b != null && b.length >= 2) return ((b[0] & 0xFF) * 256 + (b[1] & 0xFF)) / 1000f;
            }
            String r = elm.cmd("ATRV", 1500).replace("V", "");
            try { return Float.parseFloat(r); } catch (Throwable e) { return null; }
        }
        if (!elm.supports(s.pid)) return null;
        byte[] b = elm.pid(s.pid);
        if (b == null || b.length == 0) return null;
        int a = b[0] & 0xFF, c = b.length > 1 ? b[1] & 0xFF : 0;
        switch (s.pid) {
            case 0x04: case 0x11: case 0x2E: case 0x2F: case 0x45: case 0x47: case 0x49: case 0x4A: case 0x4C:
                return a * 100f / 255f;
            case 0x43: return (a * 256 + c) * 100f / 255f;
            case 0x05: case 0x0F: case 0x46: case 0x5C: return a - 40f;
            case 0x06: case 0x07: case 0x55: case 0x56: return (a - 128) * 100f / 128f;
            case 0x0B: case 0x30: case 0x33: return (float) a;
            case 0x0C: return (a * 256 + c) / 4f;
            case 0x0D: return (float) a;
            case 0x0E: return a / 2f - 64f;
            case 0x10: return (a * 256 + c) / 100f;
            case 0x14: case 0x15: return a / 200f;
            case 0x1F: case 0x21: case 0x31: case 0x4D: case 0x4E: return (float) (a * 256 + c);
            case 0x3C: case 0x3E: return (a * 256 + c) / 10f - 40f;
            case 0x44: return (a * 256 + c) / 32768f;
            case 0x5E: return (a * 256 + c) / 20f;
            default: return null;
        }
    }

    /** Decide las alertas con los valores actuales. */
    private static void evaluate() {
        long now = System.currentTimeMillis();
        boolean engineOn = RPM.has() && RPM.value > 400;
        boolean warm = COOLANT.has() && COOLANT.value > 70;
        for (Sensor s : ALL) {
            int raw = s.rawLevel();
            // el voltaje bajo con el motor apagado es normal; la mezcla solo cuenta con el motor caliente
            if (s == VOLT && !engineOn && s.value > 11.8f) raw = OK;
            if ((s == STFT || s == LTFT) && !(engineOn && warm)) raw = OK;
            if (s == LOAD && !engineOn) raw = OK;
            if (!s.enabled) raw = OK;
            if (raw == OK) { s.badSince = 0; s.level = OK; continue; }
            if (s.badSince == 0) s.badSince = now;
            if (now - s.badSince < s.holdS * 1000L) continue;
            s.level = raw;
            Alert a = raise(s.key, raw, title(s, raw), advice(s, raw));
            if (a != null) synchronized (alerts) { a.label = s.label; }
        }
        // subida rápida del refrigerante: avisa antes de llegar al límite (cuestas largas, ventilador que no prende)
        if (COOLANT.histN > 80) {
            float rise = COOLANT.value - COOLANT.hist[COOLANT.hist.length - 80];   // ~1 minuto atrás
            float perMin = rise * 60f / (80 * 0.7f);
            if (perMin >= 4 && COOLANT.value >= 95 && COOLANT.level == OK && prefs(app).getBoolean("obd_alert_coolant_rise", true))
                raise("coolant_rise", WARN, "El refrigerante sube rápido: " + I18n.integer(Math.round(COOLANT.value)) + " °C",
                    "Baja la exigencia: usa una marcha más baja, apaga el aire acondicionado y vigila que el electroventilador encienda.");
        }
    }

    /** Título de la alerta, en español (se traduce al mostrarlo). */
    private static String title(Sensor s, int lvl) {
        String v = s.text() + " " + s.unit;
        if (s == COOLANT) return (lvl == CRIT ? "Motor sobrecalentado: " : "Refrigerante alto: ") + v;
        if (s == VOLT) return (s.value >= s.hi1 ? "Voltaje alto: " : "Voltaje bajo: ") + v;
        if (s == RPM) return "RPM muy altas: " + I18n.integer(Math.round(s.value));
        if (s == LOAD) return "Motor muy exigido: " + v;
        if (s == INTAKE) return "Aire de admisión muy caliente: " + v;
        if (s == FUEL) return "Poco combustible: " + v;
        if (s == STFT || s == LTFT) return (s.value > 0 ? "Mezcla pobre" : "Mezcla rica") + " (" + s.label + "): " + v;
        if (s == OILT) return "Aceite del motor muy caliente: " + v;
        return s.label + ": " + v;
    }

    /** Qué hacer, en español (se traduce al mostrarlo). */
    private static String advice(Sensor s, int lvl) {
        if (s == COOLANT) return lvl == CRIT
            ? "Detente en un lugar seguro y apaga el motor. No abras la tapa del radiador en caliente. Espera que enfríe y revisa el nivel de refrigerante, el electroventilador y la correa."
            : "Baja la exigencia: usa una marcha más baja, apaga el aire acondicionado y, si sigue subiendo, pon la calefacción al máximo y busca dónde parar.";
        if (s == VOLT) return s.value > 14.9f
            ? "El alternador está cargando de más y puede dañar la batería. Revísalo pronto."
            : "El alternador no está cargando bien: apaga lo que no necesites (aire, luces extra) y ve a un taller antes de que se descargue la batería. Revisa la correa.";
        if (s == RPM) return "Baja la velocidad o usa una marcha más alta. En bajadas, frena con motor sin pasar la zona roja.";
        if (s == LOAD) return "El motor va al máximo hace rato. En cuestas largas usa una marcha más baja y deja descansar el motor y la caja.";
        if (s == INTAKE) return "El aire que entra al motor está muy caliente: pierde potencia. Si estás detenido, avanza para que circule aire.";
        if (s == FUEL) return "Carga combustible en la próxima estación. En la montaña puede no haber más adelante.";
        if (s == STFT || s == LTFT) return s.value > 0
            ? "El motor recibe menos combustible del que necesita: puede ser una fuga de vacío, el filtro de combustible o el sensor MAF. Puede perder fuerza en subidas."
            : "El motor recibe más combustible del necesario: revisa el filtro de aire, el sensor MAF o un inyector que gotee.";
        if (s == OILT) return "Baja la exigencia y revisa el nivel de aceite cuando puedas parar.";
        return "";
    }

    /** Anota o actualiza una alerta (textos en español) y la dice en voz alta en el idioma de ahora. */
    private static Alert raise(String key, int level, String titleEs, String adviceEs) {
        Alert a = null;
        synchronized (alerts) {
            for (Alert x : alerts) if (x.key.equals(key)) a = x;
            if (a == null) { a = new Alert(); a.key = key; alerts.add(0, a); while (alerts.size() > 30) alerts.remove(alerts.size() - 1); }
            a.level = level;                       // la gravedad de ahora (el título también es el de ahora)
            a.titleEs = titleEs;
            a.adviceEs = adviceEs;
            a.at = System.currentTimeMillis();
        }
        say(key, level, a.title() + ". " + firstSentence(a.advice()));
        return a;
    }

    private static void checkDtcs() throws java.io.IOException {
        lastDtcCheck = System.currentTimeMillis();
        // luz del motor: encendida si la pide cualquiera de los computadores (motor o caja)
        byte[] st = null;
        boolean anyMil = false;
        if (elm.supports(0x01)) {
            for (byte[] b : elm.pidAll(0x01)) if (b.length > 0 && (b[0] & 0x80) != 0) anyMil = true;
            st = elm.pid(0x01);
        }
        mil = anyMil;
        if (st != null) status01 = st;
        List<String> now = elm.dtcs(false), pen = elm.dtcs(true);
        for (String c : now) if (!dtcs.contains(c)) newCode(c, false);
        for (String c : pen) if (!pending.contains(c) && !now.contains(c)) newCode(c, true);
        dtcs = now;
        pending = pen;
    }

    private static void newCode(String code, boolean isPending) {
        DtcInfo.Entry e = DtcInfo.get(app, code);
        int lvl = isPending ? WARN : e.severity >= 3 ? CRIT : WARN;
        if (!prefs(app).getBoolean("obd_alert_dtc", true)) { log("código " + code + " (aviso apagado)"); DtcInfo.autoExplain(app, code); return; }
        Alert a = null;
        synchronized (alerts) {
            for (Alert x : alerts) if (x.key.equals("dtc_" + code)) a = x;
            if (a == null) { a = new Alert(); a.key = "dtc_" + code; alerts.add(0, a); while (alerts.size() > 30) alerts.remove(alerts.size() - 1); }
            a.code = code;
            a.pending = isPending;
            a.level = lvl;
            a.at = System.currentTimeMillis();
        }
        say(a.key, lvl, a.title() + ". " + firstSentence(e.es != null ? e.es : I18n.t("revisa el detalle en la pantalla")));
        log((isPending ? "pendiente " : "código ") + code);
        if (!isPending) requestFreeze();      // la «foto» del motor de cuando se guardó el código
        DtcInfo.autoExplain(app, code);
    }

    private static String firstSentence(String s) {
        if (s == null) return "";
        int i = s.indexOf(". ");
        return i > 0 ? s.substring(0, i + 1) : s;
    }

    // ── voz y tono ──

    private static void initVoice() {
        if (tts != null) return;
        try {
            tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
        } catch (Throwable ignored) { }
        try {
            tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() { public void onInit(int st) {
                ttsReady = st == TextToSpeech.SUCCESS;
                if (ttsReady) try { tts.setLanguage(I18n.loc); ttsLoc = I18n.loc; } catch (Throwable ignored) { }
            }});
        } catch (Throwable t) { log("sin voz: " + t); }
    }

    /** Avisa con tono y voz, sin repetir la misma alerta muy seguido (críticas cada 20 s, el resto cada 2 min). */
    static void say(String key, int level, String text) {
        long now = System.currentTimeMillis();
        Long last = lastSaid.get(key);
        long gap = level == CRIT ? 20000 : level == WARN ? 120000 : 5000;
        if (last != null && now - last < gap) return;
        lastSaid.put(key, now);
        log("aviso: " + text);
        try {
            if (tone != null && level > OK) tone.startTone(level == CRIT ? ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK : ToneGenerator.TONE_PROP_BEEP2, level == CRIT ? 900 : 300);
        } catch (Throwable ignored) { }
        if (app == null || !prefs(app).getBoolean("obd_voice", true) || tts == null || !ttsReady) return;
        try {
            // la voz toma el idioma de StradaOS de ahora: si el usuario cambió de idioma, leía los números en el anterior
            if (!I18n.loc.equals(ttsLoc)) { ttsLoc = I18n.loc; tts.setLanguage(I18n.loc); }
            HashMap<String, String> p = new HashMap<String, String>();
            p.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
            sleepQuiet(level > OK ? 700 : 0);
            //noinspection deprecation (la versión con Bundle es API 21; StradaOS corre desde 4.1)
            tts.speak(text, level == CRIT ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, p);
        } catch (Throwable ignored) { }
    }

    private static void sleepQuiet(long ms) { if (ms > 0 && Looper.myLooper() != Looper.getMainLooper()) sleep(ms); }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    /** Resumen de una línea para la notificación. */
    static String summary() {
        if (status != LIVE) return I18n.t(statusMsg);
        StringBuilder b = new StringBuilder();
        for (Sensor s : new Sensor[]{COOLANT, VOLT, RPM}) if (s.has()) {
            if (b.length() > 0) b.append(" · ");
            b.append(s.text()).append(' ').append(s.unit);
        }
        if (!dtcs.isEmpty()) b.append(" · ").append(dtcs.size()).append(' ').append(I18n.t(dtcs.size() == 1 ? "código" : "códigos"));
        return b.toString();
    }
}
