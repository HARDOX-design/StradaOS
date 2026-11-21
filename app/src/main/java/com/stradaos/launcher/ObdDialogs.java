package com.stradaos.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ventanas del escáner OBD-II que usan los Ajustes («Tu vehículo → Escáner OBD-II») y el panel del vehículo:
 * elegir y emparejar el escáner, umbrales de las alertas, detalle de un código, IA, protocolo y escaneo completo.
 * La lógica viene de la pantalla «Vehículo» del PR #42 (Jaacern); aquí no depende de ninguna pantalla en particular:
 * después de cada cambio llama a {@link #refresh} para que quien la abrió se redibuje.
 */
class ObdDialogs {

    static final int AMBER = 0xFFFFB020, DANGER = 0xFFFF3B30, GOOD = 0xFF34C759;
    static final int REQ_BT_SCAN = 41;

    private final Activity a;
    private final SharedPreferences prefs;
    private final Runnable refresh;

    ObdDialogs(Activity a, Runnable refresh) {
        this.a = a;
        this.prefs = Obd.prefs(a);
        this.refresh = refresh;
    }

    private void done() { if (refresh != null) refresh.run(); }

    private TB tb() { return new TB(a, Main.dlg()); }

    void toast(String s) { android.widget.Toast.makeText(a, I18n.t(s), android.widget.Toast.LENGTH_LONG).show(); }

    // ───────────────────────── encender y elegir el escáner ─────────────────────────

    /** Enciende el monitor; si todavía no hay escáner elegido, primero lo pide. */
    void turnOn() {
        prefs.edit().putBoolean("obd_on", true).apply();
        if (!prefs.contains("obd_addr")) { pickAdapter(); done(); return; }
        if (!Obd.running()) ObdService.start(a);
        done();
    }

    void turnOff() {
        prefs.edit().putBoolean("obd_on", false).apply();
        if (Obd.running()) ObdService.stop(a);
        done();
    }

    /** Reconecta con el escáner elegido (después de cambiar el protocolo o si se cortó). */
    void reconnect() {
        if (Obd.running()) ObdService.stop(a);
        if (prefs.getBoolean("obd_on", false) && prefs.contains("obd_addr")) ObdService.start(a);
        done();
    }

    static String deviceName(Context c) {
        SharedPreferences p = Obd.prefs(c);
        String addr = p.getString("obd_addr", null);
        if (addr == null) return I18n.t("Sin elegir");
        return "demo".equals(addr) ? I18n.t("Auto simulado") : p.getString("obd_name", addr);
    }

    void pickAdapter() {
        final List<String[]> opts = new ArrayList<String[]>();   // dirección, nombre
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad != null) for (BluetoothDevice d : ad.getBondedDevices()) {
                String n = d.getName() != null ? d.getName() : d.getAddress();
                String[] o = {"bt:" + d.getAddress(), n};
                if (looksObd(n)) opts.add(0, o);   // los escáneres primero
                else opts.add(o);
            }
        } catch (Throwable ignored) { }
        final int nBt = opts.size();
        // 2.31 (#51): escáneres por cable USB conectados ahora mismo
        final List<android.hardware.usb.UsbDevice> usbs = UsbSerial.find(a);
        final int nUsb = usbs.size();
        String[] labels = new String[nBt + nUsb + 4];
        for (int i = 0; i < nBt; i++) labels[i] = "Bluetooth · " + opts.get(i)[1];
        for (int i = 0; i < nUsb; i++) labels[nBt + i] = "USB · " + UsbSerial.label(usbs.get(i));
        final int base = nBt + nUsb;
        labels[base] = I18n.t("Buscar escáneres Bluetooth cercanos");
        labels[base + 1] = I18n.t("Escáner por cable USB");
        labels[base + 2] = I18n.t("Escáner Wi-Fi (escribir IP y puerto)");
        labels[base + 3] = I18n.t("Auto simulado (para probar)");
        tb().raw().wide()
            .setTitle(I18n.t(nBt == 0 && nUsb == 0 ? "No hay escáneres Bluetooth emparejados: emparéjalo en los ajustes del equipo" : "Elige tu escáner"))
            .setItems(labels, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w < nBt) choose(opts.get(w)[0], opts.get(w)[1]);
                else if (w < base) chooseUsb(usbs.get(w - nBt));
                else if (w == base + 1) pickUsb();
                else if (w == base) discover();
                else if (w == base + 2) {
                    String cur = prefs.getString("obd_addr", "");
                    askText(I18n.t("IP y puerto del escáner Wi-Fi"), cur.startsWith("tcp:") ? cur.substring(4) : "192.168.0.10:35000",
                        "192.168.0.10:35000", new TextCb() { public void on(String v) {
                            v = v.trim();
                            if (!v.matches("[^:\\s]+:\\d{1,5}")) { toast("Escribe IP:puerto, por ejemplo 192.168.0.10:35000"); return; }
                            choose("tcp:" + v, "Wi-Fi " + v);
                        }});
                } else choose("demo", I18n.t("Auto simulado"));
            }})
            .setNegativeButton(I18n.t("Cancelar"), null).show();
    }

    /** 2.31 (#51): cable USB elegido. Android pide permiso para usarlo (una ventana); StradaOS espera la respuesta. */
    void chooseUsb(android.hardware.usb.UsbDevice dev) {
        UsbSerial.askPermission(a, dev);
        choose(UsbSerial.addr(dev), "USB · " + UsbSerial.chipName(UsbSerial.chip(dev)));
    }

    /** «Escáner por cable USB»: el que esté conectado, o cómo conectarlo. */
    void pickUsb() {
        List<android.hardware.usb.UsbDevice> usbs = UsbSerial.find(a);
        if (usbs.size() == 1) { chooseUsb(usbs.get(0)); return; }
        if (usbs.isEmpty()) {
            tb().setTitle(I18n.t("Escáner por cable USB"))
                .setMessage(I18n.t("No veo ningún escáner USB conectado. Conecta el cable ELM327 USB a la pantalla (si hace falta, con un adaptador OTG), enchúfalo en el conector OBD con el contacto puesto y vuelve a intentar. Sirven los cables con chip CH340, FTDI, PL2303 o CP2102."))
                .setNegativeButton(I18n.t("Cerrar"), null).show();
            return;
        }
        pickAdapter();
    }

    private static boolean looksObd(String n) {
        return n != null && n.toUpperCase(Locale.US).matches(".*(OBD|ELM|VLINK|V-LINK|VGATE|ICAR|KONNWEI|VEEPEAK|SCAN|327).*");
    }

    void choose(String addr, String name) {
        prefs.edit().putString("obd_addr", addr).putString("obd_name", name).putBoolean("obd_on", true).apply();
        if (Obd.running()) ObdService.stop(a);
        ObdService.start(a);
        done();
    }

    // ── buscar escáneres cercanos y emparejarlos sin salir de StradaOS ──

    private android.content.BroadcastReceiver btRx;
    private AlertDialog btDlg;
    private final List<BluetoothDevice> found = new ArrayList<BluetoothDevice>();
    private BluetoothDevice pairing;

    void discover() {
        final BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) { toast("Este equipo no tiene Bluetooth"); return; }
        if (!ad.isEnabled()) {
            try { a.startActivity(new android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); } catch (Throwable e) { toast("El Bluetooth está apagado"); }
            return;
        }
        // Android 6+: buscar equipos Bluetooth pide el permiso de ubicación
        if (!Modern.hasPermissions(a, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            Modern.requestPermissions(a, REQ_BT_SCAN, android.Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        found.clear();
        stopDiscovery();
        btRx = new android.content.BroadcastReceiver() { public void onReceive(Context c, android.content.Intent i) {
            String act = i.getAction();
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_FOUND.equals(act) && d != null) {
                for (BluetoothDevice x : found) if (x.getAddress().equals(d.getAddress())) return;
                found.add(d);
                showFound(false);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(act)) {
                showFound(true);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(act) && d != null && pairing != null && d.getAddress().equals(pairing.getAddress())) {
                if (d.getBondState() == BluetoothDevice.BOND_BONDED) {
                    toast(I18n.t("Emparejado") + ": " + name(d));
                    pairing = null;
                    stopDiscovery();
                    choose("bt:" + d.getAddress(), name(d));
                } else if (d.getBondState() == BluetoothDevice.BOND_NONE) {
                    pairing = null;
                    toast("No se pudo emparejar. Prueba con la clave 1234 o 0000.");
                }
            }
        }};
        android.content.IntentFilter f = new android.content.IntentFilter(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        a.registerReceiver(btRx, f);
        ad.startDiscovery();
        showFound(false);
    }

    void onPermission(boolean granted) {
        if (granted) discover();
        else toast("Sin el permiso de ubicación Android no deja buscar equipos Bluetooth");
    }

    private static String name(BluetoothDevice d) {
        String n = null;
        try { n = d.getName(); } catch (Throwable ignored) { }
        return n != null && n.length() > 0 ? n : d.getAddress();
    }

    private void showFound(boolean finished) {
        if (a.isFinishing()) return;
        try { if (btDlg != null) btDlg.dismiss(); } catch (Throwable ignored) { }
        final List<BluetoothDevice> list = new ArrayList<BluetoothDevice>();
        for (BluetoothDevice d : found) {
            if (looksObd(name(d))) list.add(0, d);
            else list.add(d);
        }
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            BluetoothDevice d = list.get(i);
            labels[i] = (looksObd(name(d)) ? "★ " : "") + name(d) + (d.getBondState() == BluetoothDevice.BOND_BONDED ? "  · " + I18n.t("emparejado") : "");
        }
        TB b = (TB) tb().raw().wide()
            .setTitle(I18n.t(finished ? (list.isEmpty() ? "No se encontraron equipos. ¿Está el escáner enchufado y el contacto puesto?" : "Elige tu escáner")
                : "Buscando… (enchufa el escáner y pon el contacto)"));
        if (labels.length > 0) b.setItems(labels, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            BluetoothDevice dev = list.get(w);
            if (dev.getBondState() == BluetoothDevice.BOND_BONDED) { stopDiscovery(); choose("bt:" + dev.getAddress(), name(dev)); return; }
            pairing = dev;
            try { BluetoothAdapter.getDefaultAdapter().cancelDiscovery(); } catch (Throwable ignored) { }
            boolean ok = false;
            try { ok = (Boolean) dev.getClass().getMethod("createBond").invoke(dev); } catch (Throwable ignored) { }   // API 19 (en 4.x por reflexión)
            if (ok) toast("Emparejando… si pide clave, escribe 1234 o 0000");
            else {
                pairing = null;
                toast("Emparéjalo en los ajustes de Bluetooth del equipo");
                try { a.startActivity(new android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); } catch (Throwable ignored) { }
            }
        }});
        else b.setMessage(I18n.t("Los escáneres suelen llamarse OBDII, OBD2, V-LINK o Vgate."));
        if (finished) b.setPositiveButton(I18n.t("Buscar de nuevo"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { discover(); } });
        b.setNegativeButton(I18n.t("Cerrar"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { if (pairing == null) stopDiscovery(); } });
        btDlg = b.show();
    }

    void stopDiscovery() {
        try { BluetoothAdapter.getDefaultAdapter().cancelDiscovery(); } catch (Throwable ignored) { }
        if (btRx != null) try { a.unregisterReceiver(btRx); } catch (Throwable ignored) { }
        btRx = null;
    }

    // ───────────────────────── alertas ─────────────────────────

    static Obd.Sensor sensorOf(String key) {
        for (Obd.Sensor s : Obd.ALL) if (s.key.equals(key)) return s;
        return "coolant_rise".equals(key) ? Obd.COOLANT : null;
    }

    /** Datos que tienen alerta (para la lista de «Alertas»). */
    static List<Obd.Sensor> alertSensors(SharedPreferences p) {
        List<Obd.Sensor> list = new ArrayList<Obd.Sensor>();
        for (Obd.Sensor s : Obd.ALL) {
            if (s.def == null) s.load(p);
            if (!s.alerts() && (s.def == null || (Float.isNaN(s.def[0]) && Float.isNaN(s.def[2]) && Float.isNaN(s.def[4])))) continue;
            list.add(s);
        }
        return list;
    }

    void showAlert(final Obd.Alert al) {
        if (al.key.startsWith("dtc_")) { showCode(al.key.substring(4)); return; }
        final Obd.Sensor sensor = sensorOf(al.key);
        TB b = (TB) tb().raw().wide().setTitle(al.title()).setMessage(al.advice());
        b.setPositiveButton(I18n.t("Preguntar a la IA"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { askAiAlert(al); } });
        if (sensor != null) b.setNeutralButton(I18n.t("Ajustar esta alerta"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { editAlert(sensor); } });
        b.setNegativeButton(I18n.t("Cerrar"), null);
        b.show();
    }

    private void askAiAlert(final Obd.Alert al) {
        if (!DtcInfo.aiReady(a)) { askAi(null); return; }
        final AlertDialog wait = tb().setTitle(al.title()).setMessage("Preguntando a la IA…").setNegativeButton("Cerrar", null).show();
        DtcInfo.explainAlert(a, al.title(), new DtcInfo.AiCb() { public void on(String text, String error) {
            try { wait.dismiss(); } catch (Throwable ignored) { }
            if (a.isFinishing()) return;
            tb().raw().wide().setTitle(text != null ? al.title() : I18n.t("No se pudo consultar la IA"))
                .setMessage(text != null ? text : I18n.t(error)).setNegativeButton(I18n.t("Cerrar"), null).show();
        }});
    }

    static String limits(Obd.Sensor s) {
        if (!s.enabled) return I18n.t("Apagada");
        StringBuilder b = new StringBuilder();
        if (!Float.isNaN(s.hi1)) b.append(num(s.hi1)).append(" / ").append(num(s.hi2)).append(' ').append(s.unit);
        if (!Float.isNaN(s.lo1)) { if (b.length() > 0) b.append(" · "); b.append("< ").append(num(s.lo1)).append(" / ").append(num(s.lo2)).append(' ').append(s.unit); }
        if (!Float.isNaN(s.abs1)) { if (b.length() > 0) b.append(" · "); b.append("±").append(num(s.abs1)).append(" / ±").append(num(s.abs2)).append(" %"); }
        return b.toString();
    }

    static String num(float v) { return v == Math.round(v) ? I18n.integer(Math.round(v)) : String.format(I18n.num, "%.1f", v); }

    /** Encender o apagar la alerta de un dato y cambiar sus umbrales. */
    void editAlert(final Obd.Sensor s) {
        if (s.def == null) s.load(prefs);
        final List<String[]> fields = new ArrayList<String[]>();   // sufijo, nombre
        if (!Float.isNaN(s.def[0])) { fields.add(new String[]{"hi1", "Aviso sobre"}); fields.add(new String[]{"hi2", "Alerta sobre"}); }
        if (!Float.isNaN(s.def[2])) { fields.add(new String[]{"lo1", "Aviso bajo"}); fields.add(new String[]{"lo2", "Alerta bajo"}); }
        if (!Float.isNaN(s.def[4])) { fields.add(new String[]{"abs1", "Aviso sobre ±"}); fields.add(new String[]{"abs2", "Alerta sobre ±"}); }
        final float[] cur = {s.hi1, s.hi2, s.lo1, s.lo2, s.abs1, s.abs2};
        final String[] sufs = {"hi1", "hi2", "lo1", "lo2", "abs1", "abs2"};
        List<String> labels = new ArrayList<String>();
        labels.add(I18n.t("Vigilar") + ": " + I18n.t(s.enabled ? "Sí" : "No"));
        for (String[] f : fields) labels.add(I18n.t(f[1]) + ": " + num(cur[java.util.Arrays.asList(sufs).indexOf(f[0])]) + " " + s.unit);
        labels.add(I18n.t("Valores de fábrica"));
        tb().raw().wide().setTitle(I18n.t(s.label))
            .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w == 0) { prefs.edit().putBoolean("obd_alert_" + s.key, !s.enabled).apply(); applyAlerts(); editAlert(s); }
                else if (w <= fields.size()) {
                    final String[] f = fields.get(w - 1);
                    final float v = cur[java.util.Arrays.asList(sufs).indexOf(f[0])];
                    askText(I18n.t(f[1]) + " (" + s.unit + ")", num(v).replace(" ", ""), "", new TextCb() { public void on(String t) {
                        try {
                            float nv = Float.parseFloat(t.trim().replace(',', '.'));
                            prefs.edit().putFloat("obd_thr_" + s.key + "_" + f[0], nv).apply();
                            applyAlerts();
                        } catch (Throwable e) { toast("Escribe un número"); }
                        editAlert(s);
                    }});
                } else {
                    SharedPreferences.Editor e = prefs.edit().remove("obd_alert_" + s.key);
                    for (String suf : sufs) e.remove("obd_thr_" + s.key + "_" + suf);
                    e.apply();
                    applyAlerts();
                    editAlert(s);
                }
            }})
            .setNegativeButton(I18n.t("Listo"), null)
            .show();
    }

    private void applyAlerts() {
        Obd.reloadPrefs(a);
        done();
    }

    // ───────────────────────── códigos ─────────────────────────

    void showCode(final String code) {
        final DtcInfo.Entry e = DtcInfo.get(a, code);
        StringBuilder m = new StringBuilder();
        m.append(DtcInfo.system(code)).append("\n\n");
        if (e.es != null) m.append(e.es).append("\n\n").append(I18n.t("Qué hacer")).append(": ").append(e.advice).append("\n\n");
        if (e.en != null) m.append("SAE: ").append(e.en).append("\n\n");
        if (Obd.freezeDone && code.equals(Obd.freezeCode) && Obd.freeze != null && !Obd.freeze.isEmpty()) {
            m.append(I18n.t("Datos del motor cuando se registró la falla")).append(":\n");
            for (java.util.Map.Entry<Integer, byte[]> f : Obd.freeze.entrySet()) {
                String v = Scan.value(f.getKey(), f.getValue());
                if (v != null) m.append("· ").append(I18n.t(Scan.name(f.getKey()))).append(": ").append(v).append('\n');
            }
            m.append('\n');
        } else if (Obd.running() && !Obd.freezeDone) Obd.requestFreeze();
        if (e.ai != null) m.append(I18n.t("Explicación de la IA")).append(":\n").append(e.ai);
        else if (e.es == null && e.en == null) m.append(I18n.t("Este código no está en la tabla sin conexión: suele ser un código propio de la marca. La IA puede explicarlo."));
        if (code.charAt(0) == 'B' || code.charAt(0) == 'C')
            m.append("\n\n").append(I18n.t("Ojo: los códigos de airbag, ABS y carrocería normalmente no se leen con OBD-II genérico; si aparece aquí es porque el auto lo reporta al computador del motor."));
        TB b = (TB) tb().raw().wide().setTitle(code).setMessage(m.toString().trim());
        b.setNegativeButton(I18n.t("Cerrar"), null);
        b.setPositiveButton(I18n.t(e.ai != null ? "Volver a preguntar a la IA" : "Explicar con IA"), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { askAi(code); }
        });
        b.show();
    }

    void askAi(final String code) {
        if (!DtcInfo.aiReady(a)) {
            tb().setTitle("Explicar con IA")
                .setMessage("Para que una IA explique los códigos, pon tu API key (Claude, Groq, Gemini u otra) en Ajustes → Tu vehículo → Escáner OBD-II → Inteligencia artificial. Las explicaciones se guardan en el equipo y después funcionan sin internet.")
                .setNegativeButton("Cerrar", null).show();
            return;
        }
        if (code == null) return;
        final AlertDialog wait = tb().setTitle(code).setMessage("Preguntando a la IA…").setNegativeButton("Cerrar", null).show();
        DtcInfo.explain(a, code, new DtcInfo.AiCb() { public void on(String text, String error) {
            try { wait.dismiss(); } catch (Throwable ignored) { }
            if (a.isFinishing()) return;
            if (text == null) {
                tb().setTitle("No se pudo consultar la IA").setMessage(I18n.t(error)).setNegativeButton("Cerrar", null).show();
                return;
            }
            done();
            showCode(code);
        }});
    }

    void confirmClear() {
        if (Obd.RPM.has() && Obd.RPM.value > 400) {
            tb().setTitle("Borrar códigos").setMessage("Apaga el motor y deja solo el contacto puesto para borrar los códigos.")
                .setNegativeButton("Cerrar", null).show();
            return;
        }
        tb().setTitle("¿Borrar los códigos de falla?")
            .setMessage("Se apaga la luz del motor y se borran los datos que usa el taller para diagnosticar; también se reinician los monitores de emisiones. No se puede deshacer y, si la falla sigue, el código volverá a aparecer. Anota los códigos antes.")
            .setPositiveButton("Borrar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { Obd.requestClear(); } })
            .setNegativeButton("Cancelar", null).show();
    }

    // ───────────────────────── motor y protocolo ─────────────────────────

    static String protocolName(String p) {
        if ("6".equals(p)) return I18n.t("CAN 11 bits · 500 kbps");
        if ("7".equals(p)) return I18n.t("CAN 29 bits · 500 kbps");
        if ("8".equals(p)) return I18n.t("CAN 11 bits · 250 kbps");
        if ("9".equals(p)) return I18n.t("CAN 29 bits · 250 kbps");
        return I18n.t("Automático");
    }

    /** Cilindrada para calcular el consumo con el MAP en motores que no traen MAF. */
    void askDisplacement() {
        askText(I18n.t("Cilindrada del motor (litros)"),
            String.format(Locale.US, "%.1f", prefs.getFloat("obd_displacement", 2.4f)), "2.4", new TextCb() { public void on(String v) {
                try {
                    float n = Float.parseFloat(v.trim().replace(',', '.'));
                    if (n < 0.5f || n > 8f) { toast("Escribe un número entre 0,5 y 8"); return; }
                    prefs.edit().putFloat("obd_displacement", n).apply();
                    done();
                } catch (Throwable e) { toast("Escribe un número"); }
            }});
    }

    void pickProtocol() {
        final String[] keys = {"auto", "6", "7", "8", "9"};
        String[] labels = new String[keys.length];
        int cur = 0;
        String v = prefs.getString("obd_protocol", "auto");
        for (int i = 0; i < keys.length; i++) { labels[i] = protocolName(keys[i]); if (keys[i].equals(v)) cur = i; }
        tb().raw().wide().setTitle(I18n.t("Protocolo"))
            .setSingleChoiceItems(labels, cur, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                d.dismiss();
                prefs.edit().putString("obd_protocol", keys[w]).apply();
                reconnect();
            }})
            .setNegativeButton(I18n.t("Cancelar"), null).show();
    }

    void askVehicle() {
        askText(I18n.t("Tu vehículo: marca, modelo, año, motor y caja"), prefs.getString("obd_vehicle", ""),
            "Ej.: Kia Picanto 2019 1.2 gasolina mecánico", new TextCb() { public void on(String v) {
                prefs.edit().putString("obd_vehicle", v.trim()).apply(); done(); }});
    }

    // ───────────────────────── IA ─────────────────────────

    void askKey() {
        String[] pr = DtcInfo.preset(a);
        askText(I18n.t("API key"), "", "anthropic".equals(pr[0]) ? "sk-ant-…" : "groq".equals(pr[0]) ? "gsk_…" : "sk-…", new TextCb() { public void on(String v) {
            prefs.edit().putString("ai_key", v.trim()).apply(); done(); }});
    }

    void askUrl() {
        askText(I18n.t("Dirección de la API"), DtcInfo.baseUrl(a), "https://…/v1", new TextCb() { public void on(String v) {
            prefs.edit().putString("ai_url", v.trim()).apply(); done(); }});
    }

    void pickProvider() {
        String[] names = new String[DtcInfo.PRESETS.length];
        int cur = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = I18n.t(DtcInfo.PRESETS[i][1]);
            if (DtcInfo.PRESETS[i][0].equals(DtcInfo.preset(a)[0])) cur = i;
        }
        tb().raw().setTitle(I18n.t("Proveedor"))
            .setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                d.dismiss();
                // otro proveedor: el modelo y la key del anterior no sirven
                if (!DtcInfo.PRESETS[w][0].equals(DtcInfo.preset(a)[0]))
                    prefs.edit().putString("ai_preset", DtcInfo.PRESETS[w][0]).remove("ai_model").remove("ai_key").apply();
                done();
            }})
            .setNegativeButton(I18n.t("Cancelar"), null).show();
    }

    /** Lista de modelos que ofrece el proveedor (pide la API key primero); siempre se puede escribir otro a mano. */
    void pickModel() {
        if (!DtcInfo.aiReady(a)) { toast("Primero escribe la API key"); return; }
        final AlertDialog wait = tb().setTitle("Modelo").setMessage("Buscando los modelos del proveedor…").setNegativeButton("Cerrar", null).show();
        DtcInfo.models(a, new DtcInfo.AiCb() { public void on(String text, String error) {
            try { wait.dismiss(); } catch (Throwable ignored) { }
            if (a.isFinishing()) return;
            final List<String> ids = new ArrayList<String>();
            if (text != null) for (String id : text.split("\n")) if (id.length() > 0) ids.add(id);
            if (error != null) toast(error);
            final String rec = DtcInfo.preset(a)[3];
            String[] labels = new String[ids.size() + 1];
            int cur = -1;
            for (int i = 0; i < ids.size(); i++) {
                labels[i] = ids.get(i) + (ids.get(i).equals(rec) ? "  · " + I18n.t("recomendado") : "");
                if (ids.get(i).equals(DtcInfo.model(a))) cur = i;
            }
            labels[ids.size()] = I18n.t("Escribir otro…");
            tb().raw().wide().setTitle(I18n.t("Modelo"))
                .setSingleChoiceItems(labels, cur, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    d.dismiss();
                    if (w < ids.size()) { prefs.edit().putString("ai_model", ids.get(w)).apply(); done(); }
                    else askText(I18n.t("Modelo"), DtcInfo.model(a), "", new TextCb() { public void on(String v) {
                        prefs.edit().putString("ai_model", v.trim()).apply(); done(); }});
                }})
                .setNegativeButton(I18n.t("Cancelar"), null).show();
        }});
    }

    void testAi() {
        if (!DtcInfo.aiReady(a)) { toast("Primero escribe la API key"); return; }
        final AlertDialog wait = tb().setTitle("Probar la IA").setMessage("Preguntando por el código de ejemplo P0711…").setNegativeButton("Cerrar", null).show();
        DtcInfo.test(a, new DtcInfo.AiCb() { public void on(String text, String error) {
            try { wait.dismiss(); } catch (Throwable ignored) { }
            if (a.isFinishing()) return;
            tb().raw().wide()
                .setTitle(I18n.t(text != null ? "La IA funciona · P0711 de ejemplo" : "No se pudo consultar la IA"))
                .setMessage(text != null ? text : I18n.t(error))
                .setNegativeButton(I18n.t("Cerrar"), null).show();
        }});
    }

    static String mask(String k) {
        k = k == null ? "" : k.trim();
        if (k.length() == 0) return I18n.t("Sin poner");
        return k.length() <= 8 ? "••••" : k.substring(0, 4) + "••••" + k.substring(k.length() - 4);
    }

    // ───────────────────────── escaneo completo y registro ─────────────────────────

    private boolean waitingScan;

    void scanAll() {
        if (Obd.status != Obd.LIVE) {
            tb().setTitle("Escanear todo lo que entrega el auto")
                .setMessage("Conecta el escáner con el contacto puesto (mejor con el motor en marcha) y vuelve a intentarlo.")
                .setNegativeButton("Cerrar", null).show();
            return;
        }
        tb().setTitle("Escanear todo lo que entrega el auto")
            .setMessage("Pregunta al auto por cada dato estándar y por los datos propios de la marca del motor y la caja. Tarda hasta 2 minutos y mientras tanto las alertas se pausan: hazlo detenido. Solo lee, no cambia nada en el auto.")
            .setPositiveButton("Escanear", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                Obd.report = null;
                waitingScan = true;
                Obd.requestScan();
                toast("Escaneando… el avance se ve en el estado del escáner");
            }})
            .setNegativeButton("Cancelar", null).show();
    }

    /** Lo llama quien escucha a {@link Obd}: muestra el informe en cuanto termina el escaneo pedido aquí. */
    void onObd() {
        if (waitingScan && !Obd.scanning && Obd.report != null) showReport();
    }

    void showReport() {
        waitingScan = false;
        if (Obd.report == null) { toast("Todavía no hay un informe"); return; }
        String text = Obd.report + (Obd.reportPath != null ? "\n\n" + I18n.t("Guardado en") + ":\n" + Obd.reportPath : "");
        tb().raw().wide().setTitle(I18n.t("Lo que entrega tu auto")).setMessage(text)
            .setNegativeButton(I18n.t("Cerrar"), null).show();
    }

    void showLog() {
        StringBuilder b = new StringBuilder();
        b.append(I18n.t("Escáner")).append(": ").append(Obd.adapterInfo.length() > 0 ? Obd.adapterInfo : "—").append('\n');
        synchronized (Obd.LOG) { for (String l : Obd.LOG) b.append(l).append('\n'); }
        tb().raw().wide().setTitle(I18n.t("Registro de la conexión")).setMessage(b.toString().trim())
            .setNegativeButton(I18n.t("Cerrar"), null).show();
    }

    interface TextCb { void on(String v); }

    void askText(String title, String value, String hint, final TextCb cb) {
        final EditText et = new EditText(a);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setText(value);
        et.setHint(I18n.t(hint));
        et.setTextColor(Main.TEXT);
        et.setHintTextColor(Main.MUTED);
        et.setSelection(et.getText().length());
        FrameLayout box = new FrameLayout(a);
        int p = Math.round(8 * a.getResources().getDisplayMetrics().density);
        box.setPadding(p / 2, p, p / 2, 0);
        box.addView(et);
        tb().raw().wide().setTitle(title).setView(box)
            .setPositiveButton(I18n.t("Guardar"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { cb.on(et.getText().toString()); } })
            .setNegativeButton(I18n.t("Cancelar"), null).show();
    }
}
