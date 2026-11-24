package com.stradaos.launcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Conversación con un adaptador ELM327 (el escáner OBD-II barato de Bluetooth o Wi-Fi). Solo usa los modos genéricos
 * de OBD-II, que entienden todos los autos bencineros desde ~2001 en EE. UU. / ~2008 en Latinoamérica:
 * 01 (datos en vivo), 03 y 07 (códigos de falla guardados y pendientes), 04 (borrar códigos) y 09 (VIN).
 *
 * Dirección: "bt:AA:BB:CC:DD:EE:FF" (Bluetooth clásico SPP, emparejado antes en los ajustes del equipo),
 * "tcp:192.168.0.10:35000" (adaptadores Wi-Fi, o el emulador ELM327 del PC: 10.0.2.2 desde el emulador de Android)
 * "usb:VVVV:PPPP" (cable ELM327 USB, 2.31: ver {@link UsbSerial}) o "demo" (un auto simulado, para probar sin el auto).
 *
 * Todo es bloqueante: se usa desde el hilo de {@link Obd}.
 */
public class Elm {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket bt;
    private Socket tcp;
    private UsbSerial usb;
    private InputStream in;
    private OutputStream out;
    private Demo demo;
    /** Protocolo CAN (ISO 15765): cambia cómo vienen los códigos de falla. */
    boolean can;
    String protocol = "";
    String version = "";
    /** Protocolo forzado desde Ajustes: «auto» o el número de ATSP (6 = CAN 11 bits/500 kbps). */
    private String protocolPref = "auto";
    /** PIDs del modo 01 que el auto dice tener (bit i = PID i). */
    private final boolean[] supported = new boolean[0xE1];
    /** Cuántos computadores dicen tener cada PID (en el Sorento, el 01, 30 y 31 los tienen el motor y la caja). */
    private final int[] responders = new int[0xE1];
    /** Se pueden pedir varios PIDs en una consulta (solo CAN); se apaga si el escáner o el auto no lo entienden. */
    boolean multi;
    /** Terminar la consulta con el número de tramas de la respuesta; se apaga si el escáner corta antes de tiempo. */
    boolean counted = true;

    /**
     * Bytes de datos de cada PID del modo 01 (SAE J1979), para separar una respuesta con varios PIDs. 0 = desconocido
     * o mapa de PIDs disponibles: esos se piden de a uno.
     */
    static final int[] LEN = new int[0x60];
    static {
        int[][] t = {{0x01, 4}, {0x02, 2}, {0x03, 2}, {0x04, 1}, {0x05, 1}, {0x06, 1}, {0x07, 1}, {0x08, 1}, {0x09, 1},
            {0x0A, 1}, {0x0B, 1}, {0x0C, 2}, {0x0D, 1}, {0x0E, 1}, {0x0F, 1}, {0x10, 2}, {0x11, 1}, {0x12, 1}, {0x13, 1},
            {0x1C, 1}, {0x1D, 1}, {0x1E, 1}, {0x1F, 2}, {0x21, 2}, {0x22, 2}, {0x23, 2}, {0x2C, 1}, {0x2D, 1}, {0x2E, 1},
            {0x2F, 1}, {0x30, 1}, {0x31, 2}, {0x32, 2}, {0x33, 1}, {0x41, 4}, {0x42, 2}, {0x43, 2}, {0x44, 2}, {0x45, 1},
            {0x46, 1}, {0x47, 1}, {0x48, 1}, {0x49, 1}, {0x4A, 1}, {0x4B, 1}, {0x4C, 1}, {0x4D, 2}, {0x4E, 2}, {0x4F, 4},
            {0x50, 4}, {0x51, 1}, {0x52, 1}, {0x53, 2}, {0x54, 2}, {0x59, 2}, {0x5A, 1}, {0x5B, 1}, {0x5C, 1}, {0x5D, 2},
            {0x5E, 2}, {0x5F, 1}};
        for (int[] e : t) LEN[e[0]] = e[1];
        for (int p = 0x14; p <= 0x1B; p++) LEN[p] = 2;      // sondas O2 (voltaje y corrección)
        for (int p = 0x24; p <= 0x2B; p++) LEN[p] = 4;      // sondas O2 de banda ancha
        for (int p = 0x34; p <= 0x3B; p++) LEN[p] = 4;
        for (int p = 0x3C; p <= 0x3F; p++) LEN[p] = 2;      // temperaturas del catalizador
        for (int p = 0x55; p <= 0x58; p++) LEN[p] = 2;      // correcciones de la sonda 2
    }

    /** true si el PID se puede pedir junto con otros. */
    static boolean groupable(int pid) { return pid > 0 && pid < LEN.length && LEN[pid] > 0; }

    void open(String addr) throws IOException {
        close();
        if ("demo".equals(addr)) {
            demo = new Demo();
        } else if (addr.startsWith("tcp:")) {
            String hp = addr.substring(4);
            int c = hp.lastIndexOf(':');
            openTcp(hp.substring(0, c), Integer.parseInt(hp.substring(c + 1)));
        } else if (addr.startsWith("bt:")) {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) throw new IOException("Este equipo no tiene Bluetooth");
            if (!a.isEnabled()) throw new IOException("El Bluetooth está apagado");
            BluetoothDevice d = a.getRemoteDevice(addr.substring(3));
            try { a.cancelDiscovery(); } catch (Throwable ignored) { }
            IOException last = null;
            // los clones del ELM327 a veces solo aceptan la conexión insegura, otros solo el canal 1 por reflexión
            for (int i = 0; i < 3 && bt == null; i++) {
                BluetoothSocket s = null;
                try {
                    if (i == 0) s = d.createInsecureRfcommSocketToServiceRecord(SPP);
                    else if (i == 1) s = d.createRfcommSocketToServiceRecord(SPP);
                    else s = (BluetoothSocket) d.getClass().getMethod("createRfcommSocket", int.class).invoke(d, 1);
                    s.connect();
                    bt = s;
                } catch (Throwable t) {
                    try { if (s != null) s.close(); } catch (Throwable ignored) { }
                    last = t instanceof IOException ? (IOException) t : new IOException(t.toString());
                }
            }
            if (bt == null) throw last != null ? last : new IOException("No se pudo conectar");
            in = bt.getInputStream();
            out = bt.getOutputStream();
        } else if (addr.startsWith("usb:")) {
            openUsb(addr);
        } else {
            throw new IOException("Dirección no válida: " + addr);
        }
        init();
    }

    /** Velocidades que usan los cables ELM327 USB; 38400 es la más común. La que funcionó se recuerda. */
    private static final int[] BAUDS = {38400, 115200, 9600, 57600, 230400, 500000};

    /**
     * 2.31 (#51): cable USB. Busca el cable, espera el permiso de Android (una ventana en la pantalla) y prueba las
     * velocidades hasta que el ELM327 conteste. Sin el cable conectado falla como un escáner Bluetooth apagado.
     */
    private void openUsb(String addr) throws IOException {
        android.content.Context c = Obd.ctx();
        if (c == null) throw new IOException("Sin conexión");
        android.hardware.usb.UsbDevice d = UsbSerial.byAddr(c, addr);
        if (d == null) throw new IOException("No veo el cable USB del escáner: revisa que esté conectado");
        if (!UsbSerial.waitPermission(c, d)) throw new IOException("Falta el permiso para usar el cable USB: acéptalo en la pantalla");
        usb = UsbSerial.open(c, d);
        android.content.SharedPreferences p = Obd.prefs(c);
        int saved = p.getInt("obd_usb_baud", 0);
        List<Integer> order = new ArrayList<Integer>();
        if (saved > 0) order.add(saved);
        for (int b : BAUDS) if (b != saved) order.add(b);
        for (int b : order) {
            try {
                usb.setBaud(b);
                usb.drain();
                usb.out.write("\rATI\r".getBytes("US-ASCII"));
                String r = usb.readFor(1500).toUpperCase(Locale.US);
                if (r.contains("ELM") || r.contains("OBD") || r.contains(">")) {
                    if (b != saved) p.edit().putInt("obd_usb_baud", b).apply();
                    in = usb.in;
                    out = usb.out;
                    return;
                }
            } catch (IOException e) {
                usb.close();
                usb = null;
                throw e;
            }
        }
        usb.close();
        usb = null;
        throw new IOException("El cable USB no responde como un escáner ELM327");
    }

    /**
     * Conexión TCP al ELM327. Primero prueba lo normal; si falla (por ejemplo, porque el equipo tiene internet por
     * otra Wi-Fi y la del escáner no es la red por defecto), vuelve a probar atando el socket a cada dirección local,
     * empezando por la de la misma subred que el escáner. Así funciona con dos tarjetas Wi-Fi a la vez.
     */
    private void openTcp(String host, int port) throws IOException {
        IOException last = null;
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), 6000);
            use(s);
            return;
        } catch (IOException e) {
            last = e;
            try { s.close(); } catch (Throwable ignored) { }
        }
        InetAddress target = null;
        try { target = InetAddress.getByName(host); } catch (Throwable ignored) { }
        for (InetAddress local : localAddresses(target)) {
            s = new Socket();
            try {
                s.bind(new InetSocketAddress(local, 0));
                s.connect(new InetSocketAddress(host, port), 4000);
                use(s);
                return;
            } catch (IOException e) {
                last = e;
                try { s.close(); } catch (Throwable ignored) { }
            } catch (Throwable t) {
                try { s.close(); } catch (Throwable ignored) { }
            }
        }
        throw last != null ? last : new IOException("No se pudo conectar");
    }

    private void use(Socket s) throws IOException {
        tcp = s;
        s.setSoTimeout(8000);
        in = s.getInputStream();
        out = s.getOutputStream();
    }

    /** Direcciones IPv4 locales, primero las que comparten los tres primeros números con el escáner. */
    private static List<InetAddress> localAddresses(final InetAddress target) {
        List<InetAddress> out = new ArrayList<InetAddress>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) out.add(a);
                }
            }
        } catch (Throwable ignored) { }
        final byte[] t = target != null ? target.getAddress() : null;
        Collections.sort(out, new Comparator<InetAddress>() {
            public int compare(InetAddress a, InetAddress b) { return same24(b, t) - same24(a, t); }
        });
        return out;
    }

    private static int same24(InetAddress a, byte[] t) {
        if (t == null || t.length != 4) return 0;
        byte[] x = a.getAddress();
        return x.length == 4 && x[0] == t[0] && x[1] == t[1] && x[2] == t[2] ? 1 : 0;
    }

    void protocol(String p) { protocolPref = p != null ? p : "auto"; }

    private void init() throws IOException {
        String z = cmd("ATZ", 4000);          // reinicio: responde «ELM327 v1.5»
        version = z.replace("ATZ", "").trim();
        cmd("ATE0", 1500);                    // sin eco
        cmd("ATL0", 1500);                    // sin saltos de línea extra
        cmd("ATS0", 1500);                    // sin espacios
        cmd("ATH0", 1500);                    // sin encabezados
        cmd("ATAT1", 1500);                   // tiempos de espera adaptativos
        // protocolo automático o forzado (Kia Sorento 2010: ATSP6 = CAN 11 bits/500 kbps)
        String sp = "6".equals(protocolPref) ? "ATSP6" : "7".equals(protocolPref) ? "ATSP7"
            : "8".equals(protocolPref) ? "ATSP8" : "9".equals(protocolPref) ? "ATSP9" : "ATSP0";
        cmd(sp, 1500);
        // la primera consulta busca el protocolo del auto (puede tardar varios segundos)
        String r = cmd("0100", 15000);
        if (r.contains("UNABLETOCONNECT") || r.contains("NODATA") || r.contains("ERROR"))
            throw new IOException("El escáner no logra hablar con el auto. ¿Está el contacto puesto?");
        String dp = cmd("ATDPN", 1500).replace("A", "");
        can = dp.length() > 0 && dp.charAt(0) >= '6' && dp.charAt(0) <= '9';
        protocol = cmd("ATDP", 1500);
        java.util.Arrays.fill(supported, false);
        java.util.Arrays.fill(responders, 0);
        readSupported(r, 0x00);
        for (int base = 0x20; base <= 0xC0; base += 0x20) {
            if (!supported[base]) break;      // el último PID de cada bloque dice si existe el siguiente bloque
            readSupported(cmd(String.format(Locale.US, "01%02X", base), 3000), base);
        }
        multi = can;
        counted = true;
    }

    /**
     * PIDs disponibles. Pueden responder varios computadores a la vez (motor y caja automática, como en el Kia
     * Sorento): se juntan todos, si no, quedaría solo lo que dice el primero en contestar.
     */
    private void readSupported(String resp, int base) {
        for (byte[] b : allData(resp, 0x41, base)) {
            if (b.length < 4) continue;
            for (int i = 0; i < 32; i++)
                if ((b[i / 8] & (0x80 >> (i % 8))) != 0) { supported[base + i + 1] = true; responders[base + i + 1]++; }
        }
    }

    boolean supports(int pid) { return pid > 0 && pid < supported.length && supported[pid]; }

    /**
     * Consulta un PID del modo 01. Devuelve los bytes de datos (sin «41 PID») o null si el auto no responde. Si
     * contestan varios computadores, se queda con la respuesta más completa (la del motor, en el PID 01).
     */
    byte[] pid(int pid) throws IOException {
        byte[] best = null;
        int bestScore = -1;
        for (byte[] b : allData(cmd(String.format(Locale.US, "01%02X", pid), 3000), 0x41, pid)) {
            int score = 0;
            for (byte x : b) if (x != 0) score++;
            if (score > bestScore) { best = b; bestScore = score; }
        }
        return best;
    }

    /**
     * Hasta 6 PIDs del modo 01 en una sola consulta (ISO 15765-4, solo CAN). Tarda casi lo mismo que un PID suelto:
     * en un Kia Sorento 2010 con un ELM327 Wi-Fi, 6 PIDs en ~60 ms contra ~85 ms por cada PID. Si los PIDs los tiene
     * un solo computador, la consulta termina con el número de tramas de la respuesta y el ELM327 no se queda
     * esperando por si contesta otro. Devuelve PID → bytes de datos; vacío si no llegó nada que corresponda.
     */
    java.util.Map<Integer, byte[]> pids(int[] list) throws IOException {
        StringBuilder q = new StringBuilder("01");
        int bytes = 1;
        boolean alone = true;
        for (int p : list) {
            q.append(String.format(Locale.US, "%02X", p));
            bytes += 1 + LEN[p];
            if (responders[p] > 1) alone = false;
        }
        // una trama CAN lleva 7 bytes; si no cabe, la primera lleva 6 y las siguientes 7
        String plain = q.toString();
        boolean withCount = alone && counted;
        if (withCount) q.append(Integer.toHexString(bytes <= 7 ? 1 : 1 + bytes / 7).toUpperCase(Locale.US));
        // el Sorento contesta en ~60 ms (lo más lento medido: ~600 ms). Sin el número de tramas el escáner espera por
        // si contesta otro computador: más margen
        lastTimeout = false;
        String r = soft(q.toString(), withCount ? 1200 : 2000);
        // un clon que no entiende el número de tramas contesta «?»: se deja de indicar y se repite sin él (sin esto,
        // cinco «?» seguidos apagarían la lectura agrupada aunque el escáner sí la entienda)
        if (withCount && r != null && r.contains("?")) {
            counted = false;
            Obd.log("el escáner no entiende el número de tramas: se deja de indicar");
            r = soft(plain, 2000);
        }
        if (r == null) {
            // #77 (ELM327 v1.5 clon, Golf VI): hay clones que nunca contestan una consulta de varios PIDs. Antes esto
            // contaba como «respuesta perdida» y a la tercera se reconectaba (cada ~5 s, sin fin). Ahora no: queda
            // marcado y Obd decide si vuelve a la lectura de a uno
            lastTimeout = true;
            lastSilent = false;
            return new java.util.HashMap<Integer, byte[]>();
        }
        lastSilent = r.length() == 0 || r.contains("NODATA");
        return multiData(r, list);
    }

    /** La última consulta agrupada no trajo nada porque el auto no contestó (contacto apagado), no por no entenderla. */
    boolean lastSilent;
    /** La última consulta agrupada se quedó sin respuesta (ni el «>»): el escáner no la entiende o se colgó con ella. */
    boolean lastTimeout;

    /**
     * Como {@link #cmd}, pero si no llega el «>» no cuenta como respuesta perdida: devuelve null después de comprobar
     * que el escáner sigue vivo. Solo lanza la excepción si tampoco contesta a eso.
     */
    private synchronized String soft(String c, int timeoutMs) throws IOException {
        if (demo != null) return demo.answer(c);
        if (out == null) throw new IOException("Sin conexión");
        while (in.available() > 0) in.read();
        out.write((c + "\r").getBytes("US-ASCII"));
        out.flush();
        String r = readPrompt(timeoutMs);
        if (r == null) {
            if (!resync()) throw new IOException("El escáner no responde");
            return null;
        }
        String s = r.toUpperCase(Locale.US).replace("SEARCHING...", "").replace(" ", "");
        if (s.contains("STOPPED")) return "";
        return s.trim();
    }

    /**
     * Separa la respuesta a una consulta de varios PIDs. Larga: «00E», «0:41…», «1:…» (tramas ISO-TP que se juntan
     * hasta el largo de la primera línea); corta: una sola línea «41…». Si contestan dos computadores se queda con la
     * respuesta que trae más PIDs de los pedidos; si las tramas vienen mezcladas, con ninguna (se piden de a uno).
     */
    static java.util.Map<Integer, byte[]> multiData(String resp, int[] asked) {
        List<String> candidates = new ArrayList<String>();
        StringBuilder frames = new StringBuilder();
        int total = -1, next = 0;
        boolean mixed = false;
        for (String line : resp.split("[\r\n]+")) {
            String l = line.trim();
            if (l.length() > 2 && l.charAt(1) == ':') {
                if (Character.digit(l.charAt(0), 16) != (next & 0xF)) mixed = true;
                next++;
                frames.append(l.substring(2));
            } else if (l.matches("[0-9A-F]{3}")) {
                if (total >= 0) mixed = true;
                total = Integer.parseInt(l, 16);
            } else if (l.startsWith("41")) {
                candidates.add(l);
            }
        }
        if (frames.length() > 0 && !mixed)
            candidates.add(total > 0 && frames.length() > total * 2 ? frames.substring(0, total * 2) : frames.toString());
        java.util.Map<Integer, byte[]> best = new java.util.HashMap<Integer, byte[]>();
        for (String c : candidates) {
            java.util.Map<Integer, byte[]> m = split(c.replaceAll("[^0-9A-F]", ""), asked);
            if (m.size() > best.size()) best = m;
        }
        return best;
    }

    /** «41 PID datos PID datos…» → PID → datos, solo con los PIDs pedidos y mientras los largos calcen. */
    private static java.util.Map<Integer, byte[]> split(String hex, int[] asked) {
        java.util.Map<Integer, byte[]> out = new java.util.HashMap<Integer, byte[]>();
        if (!hex.startsWith("41")) return out;
        int i = 2;
        while (i + 2 <= hex.length()) {
            int p = Integer.parseInt(hex.substring(i, i + 2), 16);
            boolean wanted = false;
            for (int a : asked) if (a == p) wanted = true;
            if (!wanted || !groupable(p) || i + 2 + LEN[p] * 2 > hex.length()) break;
            byte[] b = new byte[LEN[p]];
            for (int k = 0; k < b.length; k++) b[k] = (byte) Integer.parseInt(hex.substring(i + 2 + k * 2, i + 4 + k * 2), 16);
            out.put(p, b);
            i += 2 + LEN[p] * 2;
        }
        return out;
    }

    /**
     * Valor de un PID del modo 01 a partir de sus bytes de datos; null si no es un dato que se muestre. El PID 03
     * (sistema de combustible) da el primer byte: 1 lazo abierto en frío, 2 lazo cerrado, 4 lazo abierto por
     * desaceleración o carga, 8 lazo abierto por falla, 16 lazo cerrado con falla de una sonda.
     */
    static Float value(int pid, byte[] b) {
        if (b == null || b.length == 0) return null;
        int a = b[0] & 0xFF, c = b.length > 1 ? b[1] & 0xFF : 0;
        switch (pid) {
            case 0x04: case 0x11: case 0x2E: case 0x2F: case 0x45: case 0x47: case 0x49: case 0x4A: case 0x4C:
                return a * 100f / 255f;
            case 0x43: return (a * 256 + c) * 100f / 255f;
            case 0x05: case 0x0F: case 0x46: case 0x5C: return a - 40f;
            case 0x06: case 0x07: case 0x55: case 0x56: return (a - 128) * 100f / 128f;
            case 0x0B: case 0x30: case 0x33: return (float) a;
            case 0x0C: return (a * 256 + c) / 4f;
            case 0x0D: case 0x03: return (float) a;
            case 0x0E: return a / 2f - 64f;
            case 0x10: return (a * 256 + c) / 100f;
            case 0x14: case 0x15: return a / 200f;
            case 0x1F: case 0x21: case 0x31: case 0x4D: case 0x4E: return (float) (a * 256 + c);
            case 0x3C: case 0x3E: return (a * 256 + c) / 10f - 40f;
            case 0x44: return (a * 256 + c) / 32768f;
            case 0x42: return b.length >= 2 ? (a * 256 + c) / 1000f : null;
            case 0x5E: return (a * 256 + c) / 20f;
            // odómetro (#52): 4 bytes en décimas de km
            case 0xA6: return b.length >= 4 ? ((long) a << 24 | (long) c << 16 | (long) (b[2] & 0xFF) << 8 | (b[3] & 0xFF)) / 10f : null;
            default: return null;
        }
    }

    /** Todas las respuestas a un PID (una por computador que contesta). */
    java.util.List<byte[]> pidAll(int pid) throws IOException {
        return allData(cmd(String.format(Locale.US, "01%02X", pid), 3000), 0x41, pid);
    }

    /** Códigos de falla: modo 03 (confirmados) o 07 (pendientes). */
    List<String> dtcs(boolean pending) throws IOException {
        String r = cmd(pending ? "07" : "03", 5000);
        List<String> codes = new ArrayList<String>();
        int head = pending ? 0x47 : 0x43;
        // respuesta larga en CAN (varios códigos): viene en tramas «0:…», «1:…» que hay que juntar
        List<String> chunks = new ArrayList<String>();
        if (r.indexOf(':') > 0) {
            StringBuilder all = new StringBuilder();
            for (String line : r.split("[\r\n]+")) if (line.indexOf(':') > 0) all.append(line.substring(line.indexOf(':') + 1));
            chunks.add(all.toString());
        } else {
            for (String line : r.split("[\r\n]+")) chunks.add(line);      // una línea por computador (motor, caja…)
        }
        for (String chunk : chunks) {
            String l = chunk.replaceAll("[^0-9A-F]", "");
            if (!l.startsWith(String.format(Locale.US, "%02X", head))) continue;
            l = l.substring(2);
            if (can && l.length() >= 2) l = l.substring(2);            // en CAN el primer byte es la cantidad
            for (int i = 0; i + 4 <= l.length(); i += 4) {
                int v = Integer.parseInt(l.substring(i, i + 4), 16);
                if (v == 0) continue;
                String c = "PCBU".charAt(v >> 14) + String.format(Locale.US, "%04X", v & 0x3FFF);
                if (!codes.contains(c)) codes.add(c);
            }
        }
        return codes;
    }

    /** Dato guardado al registrarse el código (modo 02, cuadro 0). Devuelve los bytes del dato o null. */
    byte[] freeze(int pid) throws IOException {
        byte[] b = data(cmd(String.format(Locale.US, "02%02X00", pid), 3000), 0x42, pid);
        if (b == null || b.length < 2) return null;
        byte[] out = new byte[b.length - 1];                 // el primer byte es el número de cuadro
        System.arraycopy(b, 1, out, 0, out.length);
        return out;
    }

    /** Borra los códigos y apaga la luz del motor (modo 04). */
    boolean clear() throws IOException {
        return cmd("04", 5000).contains("44");
    }

    /** VIN (modo 09 PID 02); null si el auto no lo entrega (muchos anteriores a 2008). */
    String vin() throws IOException {
        String r = cmd("0902", 5000);
        StringBuilder hex = new StringBuilder();
        boolean frames = r.indexOf(':') > 0;   // CAN: tramas «0:…» que se juntan; sin CAN: una línea por trozo
        for (String line : r.split("[\r\n]+")) {
            if (frames && line.indexOf(':') < 0) continue;      // la primera línea es el largo total
            String l = (frames ? line.substring(line.indexOf(':') + 1) : line).replaceAll("[^0-9A-F]", "");
            if (l.startsWith("4902")) l = l.length() >= 6 ? l.substring(6) : "";   // «49 02» + nº de mensaje
            hex.append(l);
        }
        StringBuilder vin = new StringBuilder();
        for (int i = 0; i + 2 <= hex.length(); i += 2) {
            int ch = Integer.parseInt(hex.substring(i, i + 2), 16);
            if ((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z')) vin.append((char) ch);
        }
        String v = vin.toString();
        return v.length() >= 17 ? v.substring(v.length() - 17) : null;
    }

    /** Bytes de datos de cada línea que trae «modo PID» (una por computador). */
    private static java.util.List<byte[]> allData(String resp, int mode, int pid) {
        java.util.List<byte[]> out = new ArrayList<byte[]>();
        String key = String.format(Locale.US, "%02X%02X", mode, pid);
        for (String line : resp.split("[\r\n]+")) {
            String l = line.replaceAll("[^0-9A-F]", "");
            int at = l.indexOf(key);
            if (at < 0) continue;
            l = l.substring(at + 4);
            byte[] b = new byte[l.length() / 2];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(l.substring(i * 2, i * 2 + 2), 16);
            out.add(b);
        }
        return out;
    }

    /** Bytes de datos de la primera línea que empieza con «modo PID». */
    private static byte[] data(String resp, int mode, int pid) {
        String key = String.format(Locale.US, "%02X%02X", mode, pid);
        for (String line : resp.split("[\r\n]+")) {
            String l = line.replaceAll("[^0-9A-F]", "");
            int at = l.indexOf(key);
            if (at < 0) continue;
            l = l.substring(at + 4);
            byte[] b = new byte[l.length() / 2];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(l.substring(i * 2, i * 2 + 2), 16);
            return b;
        }
        return null;
    }

    /** Envía un comando y lee hasta el «>» del ELM327. Devuelve la respuesta en mayúsculas, sin espacios. */
    synchronized String cmd(String c, int timeoutMs) throws IOException {
        if (demo != null) return demo.answer(c);
        if (out == null) throw new IOException("Sin conexión");
        while (in.available() > 0) in.read();                      // restos de la respuesta anterior
        out.write((c + "\r").getBytes("US-ASCII"));
        out.flush();
        String r = readPrompt(timeoutMs);
        if (r == null) {
            // No llegó el «>». El ELM327 clon a veces se traga una respuesta (viaje del 21-nov en el Sorento: 4 veces en
            // 20 min). Antes de darlo por perdido (reconectar tarda ~10 s y avisa por voz) se le pregunta algo
            // inofensivo: si contesta, esta consulta queda vacía y se sigue. A la tercera seguida, sí se reconecta.
            if (++lost >= 3 || !resync()) throw new IOException("El escáner no responde");
            return "";
        }
        lost = 0;
        String s = r.toUpperCase(Locale.US).replace("SEARCHING...", "").replace(" ", "");
        if (s.contains("STOPPED")) return "";
        return s.trim();
    }

    /** Respuestas seguidas que no llegaron. */
    private int lost;

    /** Lee hasta el «>»; null si no llega a tiempo. */
    private String readPrompt(int timeoutMs) throws IOException {
        StringBuilder b = new StringBuilder();
        long end = System.currentTimeMillis() + timeoutMs;
        if (tcp != null) tcp.setSoTimeout(Math.max(200, timeoutMs));
        while (true) {
            if (in.available() > 0 || tcp != null) {
                int ch;
                try { ch = in.read(); } catch (java.net.SocketTimeoutException e) { return null; }
                if (ch < 0) throw new IOException("El escáner cerró la conexión");
                if (ch == '>') return b.toString();
                b.append((char) ch);
                if (System.currentTimeMillis() > end + 2000) return null;     // habla sin terminar nunca
            } else {
                if (System.currentTimeMillis() > end) return null;
                try { Thread.sleep(8); } catch (InterruptedException e) { throw new IOException("Interrumpido"); }
            }
        }
    }

    /** ¿Sigue vivo? Le pide su versión y descarta lo que llegue tarde (la respuesta perdida, si aparece). */
    private boolean resync() {
        try {
            Thread.sleep(150);
            while (in.available() > 0) in.read();
            out.write("ATI\r".getBytes("US-ASCII"));
            out.flush();
            if (readPrompt(1500) == null) return false;
            Thread.sleep(100);
            while (in.available() > 0) in.read();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    void close() {
        try { if (bt != null) bt.close(); } catch (Throwable ignored) { }
        try { if (tcp != null) tcp.close(); } catch (Throwable ignored) { }
        try { if (usb != null) usb.close(); } catch (Throwable ignored) { }
        bt = null; tcp = null; usb = null; in = null; out = null; demo = null;
    }

    /**
     * Auto simulado: motor que calienta normal, sube una cuesta (más carga y temperatura), deja de cargar el
     * alternador y guarda un código de la caja. Sirve para ver todas las alertas sin salir de la casa.
     */
    static class Demo {
        final long t0 = System.currentTimeMillis();
        boolean cleared;

        double t() { return (System.currentTimeMillis() - t0) / 1000.0; }

        String answer(String c) {
            try { Thread.sleep(25); } catch (InterruptedException ignored) { }
            double t = t();
            double volt = t > 100 ? Math.max(11.6, 14.2 - (t - 100) * 0.04) : 14.2;
            if (c.equals("ATRV")) return String.format(Locale.US, "%.1fV", volt);
            if (c.startsWith("AT")) return c.equals("ATZ") ? "ELM327v1.5(demo)" : c.equals("ATDPN") ? "A6" : c.equals("ATDP") ? "ISO15765-4(CAN11/500)" : "OK";
            double climb = t > 40 && t < 140 ? 1 : 0;
            if (c.equals("0100")) return "4100BE3FA813";
            if (c.equals("0120")) return "41208003A011";
            if (c.equals("0140")) return "4140F4000004";   // incluye 5E (consumo del motor)
            if (c.equals("03")) return !cleared && t > 70 ? "43010711" : "4300";
            if (c.equals("07")) return !cleared && t > 55 ? "47010711" : "4700";
            if (c.equals("04")) { cleared = true; return "44"; }
            if (c.startsWith("21")) return c.equals("2101") ? "6101FF00A55A3C7B0000" : "7F2112";   // dato propio de la marca de ejemplo
            if (c.startsWith("22")) return "7F2231";
            if (c.equals("0902")) return "014\r0:49020144454D\r1:4F305354524144\r2:414F5330303031";
            if (c.length() == 6 && c.startsWith("02") && c.endsWith("00")) {
                // freeze frame: lo que había cuando se guardó el P0711 (motor caliente subiendo la cuesta)
                if (cleared || t < 70) return "NODATA";
                int fp = Integer.parseInt(c.substring(2, 4), 16);
                switch (fp) {
                    case 0x02: return "4202000711";
                    case 0x04: return "420400" + hex1(88 * 2.55);
                    case 0x05: return "420500" + hex1(98 + 40);
                    case 0x0C: return "420C00" + hex2(3300 * 4);
                    case 0x0D: return "420D00" + hex1(38);
                    case 0x11: return "421100" + hex1(46 * 2.55);
                    case 0x42: return "424200" + hex2(14100);
                    default: return "NODATA";
                }
            }
            if (c.startsWith("01") && c.length() > 4) {
                // varios PIDs en una consulta (el número final de tramas, si viene, se ignora): una línea «41…»
                StringBuilder all = new StringBuilder("41");
                for (int i = 2; i + 2 <= c.length(); i += 2) {
                    String one = answer("01" + c.substring(i, i + 2));
                    if (one.startsWith("41")) all.append(one.substring(2));
                }
                return all.length() > 2 ? all.toString() : "NODATA";
            }
            if (c.length() != 4 || !c.startsWith("01")) return "NODATA";
            int pid = Integer.parseInt(c.substring(2), 16);
            double rpm = 1900 + climb * 1500 + 120 * Math.sin(t / 3);
            // calienta hasta 90 °C, en la cuesta sube hasta 110 °C y después baja de nuevo a 90 °C
            double cool = t < 60 ? Math.min(25 + t * 3, 90) : t < 140 ? 90 + (t - 60) * 0.25 : Math.max(90, 110 - (t - 140) * 0.5);
            switch (pid) {
                case 0x01: return "4101" + (!cleared && t > 70 ? "81" : "00") + "076500";
                case 0x04: return "4104" + hex1((50 + climb * 40 + 5 * Math.sin(t)) * 2.55);
                case 0x05: return "4105" + hex1(cool + 40);
                case 0x06: return "4106" + hex1(128 + (2 + 3 * Math.sin(t / 2)) * 1.28);
                case 0x07: return "4107" + hex1(128 + (t > 30 ? 12 : 4) * 1.28);
                case 0x0C: return "410C" + hex2(rpm * 4);
                case 0x0D: return "410D" + hex1(55 - climb * 20 + 5 * Math.sin(t / 5));
                case 0x0F: return "410F" + hex1(32 + 40);
                case 0x10: return "4110" + hex2((9 + climb * 12) * 100);
                case 0x11: return "4111" + hex1((18 + climb * 30) * 2.55);
                case 0x2F: return "412F" + hex1(Math.max(5, 40 - t * 0.05) * 2.55);
                case 0x42: return "4142" + hex2(volt * 1000);
                case 0x46: return "4146" + hex1(14 + 40);
                case 0x03: return "41030200";
                case 0x0B: return "410B" + hex1(35 + climb * 50);
                case 0x0E: return "410E" + hex1((12 + 4 * Math.sin(t)) * 2 + 128);
                case 0x13: return "411303";
                case 0x15: return "4115" + hex1(0.45 / 0.005 + 40 * Math.sin(t * 2)) + "FF";
                case 0x1C: return "411C06";
                case 0x1F: return "411F" + hex2(t);
                case 0x21: return "4121" + hex2(!cleared && t > 70 ? (t - 70) / 60 : 0);
                case 0x30: return "41300C";
                case 0x31: return "4131" + hex2(1840);
                case 0x33: return "4133" + hex1(78);
                case 0x3C: return "413C" + hex2((480 + climb * 120 + 40) * 10);
                case 0x41: return "414100076504";
                case 0x43: return "4143" + hex2((40 + climb * 35) * 2.55);
                case 0x44: return "4144" + hex2(32768);
                case 0x5E: return "415E" + hex2((9 + climb * 12) * 3600 / (14.7 * 745) * 20);
                default: return "NODATA";
            }
        }

        static String hex1(double v) { return String.format(Locale.US, "%02X", Math.max(0, Math.min(255, (int) Math.round(v)))); }
        static String hex2(double v) { return String.format(Locale.US, "%04X", Math.max(0, Math.min(65535, (int) Math.round(v)))); }
    }
}
