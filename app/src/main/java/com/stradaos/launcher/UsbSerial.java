package com.stradaos.launcher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2.31 (#51): puerto serie por USB para los escáneres ELM327 de cable, sin librerías. En muchas pantallas (FYT y
 * otras) el Bluetooth no es el de Android sino un módulo aparte, así que el escáner Bluetooth no empareja; el cable USB
 * sí lo ve Android. Entiende los chips de casi todos esos cables: CH340/CH341, FTDI (FT232), Prolific PL2303,
 * Silicon Labs CP210x y los que se presentan como puerto serie estándar (CDC-ACM).
 *
 * Dirección: «usb:VVVV:PPPP» (fabricante y producto en hexadecimal), así sigue sirviendo aunque el cable cambie de
 * puerto. Todo es bloqueante y se usa desde el hilo de {@link Obd}. USB host existe desde Android 3.1 (API 12).
 */
final class UsbSerial {
    static final int CH34X = 1, FTDI = 2, PL2303 = 3, CP210X = 4, CDC = 5;
    static final String ACTION_PERMISSION = "com.stradaos.launcher.USB_PERMISSION";
    private static final int TIMEOUT = 2000;
    /** Cuándo se pidió el permiso por última vez (para no abrir dos ventanas de permiso seguidas). */
    private static long askedAt;

    private UsbDeviceConnection conn;
    private UsbInterface data, control;
    private UsbEndpoint epIn, epOut;
    private int type;
    private final byte[] rx = new byte[4096];
    private final byte[] buf = new byte[4096];
    private int pos, len;
    final String chipName;

    private UsbSerial(int type) {
        this.type = type;
        this.chipName = chipName(type);
    }

    // ── qué chip es ──

    static int chip(UsbDevice d) {
        int v = d.getVendorId(), p = d.getProductId();
        if (v == 0x1A86 && (p == 0x7523 || p == 0x5523 || p == 0x7522 || p == 0x5512 || p == 0x55D4)) return CH34X;
        if (v == 0x4348 && p == 0x5523) return CH34X;
        if (v == 0x0403) return FTDI;                                  // FT232R, FT231X, FT230X…
        if (v == 0x067B && p == 0x2303) return PL2303;                // PL2303 clásico y HX (los HXN nuevos usan otro arranque)
        if (v == 0x10C4 && (p == 0xEA60 || p == 0xEA70 || p == 0xEA71 || p == 0xEA80)) return CP210X;
        // puerto serie estándar: una interfaz de datos (clase 10) con dos extremos «bulk»
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface f = d.getInterface(i);
            if (f.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA && bulkPair(f) != null) return CDC;
        }
        return 0;
    }

    static String chipName(int t) {
        switch (t) {
            case CH34X: return "CH340";
            case FTDI: return "FTDI";
            case PL2303: return "PL2303";
            case CP210X: return "CP210x";
            case CDC: return "USB serie";
            default: return "USB";
        }
    }

    static String addr(UsbDevice d) {
        return String.format(Locale.US, "usb:%04X:%04X", d.getVendorId(), d.getProductId());
    }

    static String label(UsbDevice d) {
        String n = null;
        try { if (android.os.Build.VERSION.SDK_INT >= 21) n = Modern.usbProductName(d); } catch (Throwable ignored) { }
        String chip = chipName(chip(d));
        return (n != null && n.trim().length() > 0 ? n.trim() + " · " : "") + chip
            + String.format(Locale.US, " (%04X:%04X)", d.getVendorId(), d.getProductId());
    }

    /** Equipos USB conectados que parecen un puerto serie (un escáner ELM327 de cable). */
    static List<UsbDevice> find(Context c) {
        List<UsbDevice> out = new ArrayList<UsbDevice>();
        try {
            UsbManager m = (UsbManager) c.getSystemService(Context.USB_SERVICE);
            if (m == null || m.getDeviceList() == null) return out;
            for (UsbDevice d : m.getDeviceList().values()) if (chip(d) != 0) out.add(d);
        } catch (Throwable ignored) { }
        return out;
    }

    static UsbDevice byAddr(Context c, String addr) {
        for (UsbDevice d : find(c)) if (addr(d).equalsIgnoreCase(addr)) return d;
        return null;
    }

    /** Pide el permiso de Android para usar el cable (una ventana en la pantalla). No espera la respuesta. */
    static void askPermission(Context c, UsbDevice d) {
        try {
            UsbManager m = (UsbManager) c.getSystemService(Context.USB_SERVICE);
            if (m.hasPermission(d)) return;
            askedAt = SystemClock.elapsedRealtime();
            Intent i = new Intent(ACTION_PERMISSION).setPackage(c.getPackageName());
            m.requestPermission(d, PendingIntent.getBroadcast(c, 0, i, 0));
        } catch (Throwable ignored) { }
    }

    /** Espera hasta 30 s a que el usuario dé el permiso (lo pide si nadie lo pidió hace poco). */
    static boolean waitPermission(Context c, UsbDevice d) {
        UsbManager m = (UsbManager) c.getSystemService(Context.USB_SERVICE);
        if (m == null) return false;
        if (m.hasPermission(d)) return true;
        // la ventana de permiso se abre como mucho una vez cada 10 minutos (si el usuario dijo que no, no insistir en
        // cada reintento); elegir el cable de nuevo en Conexión la vuelve a abrir al momento
        if (askedAt == 0 || SystemClock.elapsedRealtime() - askedAt > 600000) askPermission(c, d);
        long end = SystemClock.elapsedRealtime() + 30000;
        while (SystemClock.elapsedRealtime() < end) {
            try { Thread.sleep(300); } catch (InterruptedException e) { return false; }
            try { if (m.hasPermission(d)) return true; } catch (Throwable ignored) { }
        }
        return false;
    }

    // ── abrir y configurar ──

    static UsbSerial open(Context c, UsbDevice d) throws IOException {
        int t = chip(d);
        if (t == 0) throw new IOException("Este cable USB no parece un escáner");
        UsbManager m = (UsbManager) c.getSystemService(Context.USB_SERVICE);
        if (m == null) throw new IOException("Este equipo no tiene USB para accesorios");
        UsbDeviceConnection cn = m.openDevice(d);
        if (cn == null) throw new IOException("Android no dejó abrir el cable USB");
        UsbSerial s = new UsbSerial(t);
        s.conn = cn;
        try {
            s.claim(d);
            s.init(d);
        } catch (IOException e) {
            s.close();
            throw e;
        } catch (Throwable e) {
            s.close();
            throw new IOException("No se pudo preparar el cable USB");
        }
        return s;
    }

    private static UsbEndpoint[] bulkPair(UsbInterface f) {
        UsbEndpoint in = null, out = null;
        for (int i = 0; i < f.getEndpointCount(); i++) {
            UsbEndpoint e = f.getEndpoint(i);
            if (e.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
            if (e.getDirection() == UsbConstants.USB_DIR_IN) { if (in == null) in = e; }
            else if (out == null) out = e;
        }
        return in != null && out != null ? new UsbEndpoint[]{in, out} : null;
    }

    private void claim(UsbDevice d) throws IOException {
        if (type == CDC) {
            for (int i = 0; i < d.getInterfaceCount(); i++) {
                UsbInterface f = d.getInterface(i);
                if (f.getInterfaceClass() == UsbConstants.USB_CLASS_COMM && control == null) control = f;
                if (f.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA && data == null && bulkPair(f) != null) data = f;
            }
            if (control != null) conn.claimInterface(control, true);
        } else {
            for (int i = 0; i < d.getInterfaceCount() && data == null; i++) {
                UsbInterface f = d.getInterface(i);
                if (bulkPair(f) != null) data = f;
            }
        }
        if (data == null) throw new IOException("El cable USB no tiene canal de datos");
        if (!conn.claimInterface(data, true)) throw new IOException("Otra app está usando el cable USB");
        UsbEndpoint[] p = bulkPair(data);
        epIn = p[0];
        epOut = p[1];
    }

    private int ctrl(int reqType, int req, int value, int index, byte[] b, int n) {
        return conn.controlTransfer(reqType, req, value, index, b, n, TIMEOUT);
    }

    private void init(UsbDevice d) throws IOException {
        switch (type) {
            case CH34X: {
                byte[] b = new byte[8];
                ctrl(0xC0, 0x5F, 0, 0, b, 2);
                ctrl(0x40, 0xA1, 0, 0, null, 0);
                ctrl(0x40, 0x9A, 0x2518, 0x00C3, null, 0);      // 8 bits, sin paridad, 1 bit de parada; recibe y envía
                ctrl(0x40, 0xA1, 0x501F, 0xD90A, null, 0);
                break;
            }
            case FTDI:
                ctrl(0x40, 0, 0, 1, null, 0);                    // reinicio (índice 1 = primer puerto)
                ctrl(0x40, 4, 0x0008, 1, null, 0);               // 8N1
                ctrl(0x40, 2, 0, 1, null, 0);                    // sin control de flujo
                break;
            case PL2303: {
                byte[] raw = null;
                try { raw = conn.getRawDescriptors(); } catch (Throwable ignored) { }
                boolean hx = raw == null || raw.length < 8 || ((raw[4] & 0xFF) != 0x02 && (raw[7] & 0xFF) == 0x40);
                byte[] b = new byte[1];
                ctrl(0xC0, 1, 0x8484, 0, b, 1);
                ctrl(0x40, 1, 0x0404, 0, null, 0);
                ctrl(0xC0, 1, 0x8484, 0, b, 1);
                ctrl(0xC0, 1, 0x8383, 0, b, 1);
                ctrl(0xC0, 1, 0x8484, 0, b, 1);
                ctrl(0x40, 1, 0x0404, 1, null, 0);
                ctrl(0xC0, 1, 0x8484, 0, b, 1);
                ctrl(0xC0, 1, 0x8383, 0, b, 1);
                ctrl(0x40, 1, 0, 1, null, 0);
                ctrl(0x40, 1, 1, 0, null, 0);
                ctrl(0x40, 1, 2, hx ? 0x44 : 0x24, null, 0);
                break;
            }
            case CP210X:
                ctrl(0x41, 0x00, 0x0001, 0, null, 0);            // encender el puerto
                ctrl(0x41, 0x03, 0x0800, 0, null, 0);            // 8N1
                break;
            case CDC:
                break;
        }
        setLines(true);
    }

    /** DTR y RTS encendidos: algunos cables no hablan sin ellos. */
    private void setLines(boolean on) {
        switch (type) {
            case CH34X: ctrl(0x40, 0xA4, on ? ~((1 << 5) | (1 << 6)) & 0xFFFF : 0xFFFF, 0, null, 0); break;
            case FTDI: ctrl(0x40, 1, on ? 0x0303 : 0x0300, 1, null, 0); break;
            case PL2303: ctrl(0x21, 0x22, on ? 3 : 0, 0, null, 0); break;
            case CP210X: ctrl(0x41, 0x07, on ? 0x0303 : 0x0300, 0, null, 0); break;
            case CDC: ctrl(0x21, 0x22, on ? 3 : 0, control != null ? control.getId() : 0, null, 0); break;
        }
    }

    void setBaud(int baud) throws IOException {
        int r;
        switch (type) {
            case CH34X: {
                long factor, divisor;
                factor = 1532620800L / baud;
                divisor = 3;
                while (factor > 0xFFF0 && divisor > 0) { factor >>= 3; divisor--; }
                if (factor > 0xFFF0) throw new IOException("El cable USB no aceptó la velocidad");
                factor = 0x10000 - factor;
                divisor |= 0x0080;
                ctrl(0x40, 0x9A, 0x1312, (int) ((factor & 0xFF00) | divisor), null, 0);
                r = ctrl(0x40, 0x9A, 0x0F2C, (int) (factor & 0xFF), null, 0);
                break;
            }
            case FTDI: {
                int div = (24000000 << 1) / baud;
                div = (div + 1) >> 1;
                int sub = div & 7;
                div >>= 3;
                int value = div, index = 0;
                switch (sub) {
                    case 4: value |= 0x4000; break;
                    case 2: value |= 0x8000; break;
                    case 1: value |= 0xC000; break;
                    case 3: index = 1; break;
                    case 5: value |= 0x4000; index = 1; break;
                    case 6: value |= 0x8000; index = 1; break;
                    case 7: value |= 0xC000; index = 1; break;
                    default: break;
                }
                r = ctrl(0x40, 3, value, index, null, 0);
                break;
            }
            case CP210X: {
                byte[] b = {(byte) baud, (byte) (baud >> 8), (byte) (baud >> 16), (byte) (baud >> 24)};
                r = ctrl(0x41, 0x1E, 0, 0, b, 4);
                break;
            }
            default: {   // PL2303 y CDC: «line coding» estándar de 7 bytes
                byte[] b = {(byte) baud, (byte) (baud >> 8), (byte) (baud >> 16), (byte) (baud >> 24), 0, 0, 8};
                int idx = type == CDC && control != null ? control.getId() : 0;
                r = ctrl(0x21, 0x20, 0, idx, b, 7);
                break;
            }
        }
        if (r < 0) throw new IOException("El cable USB no aceptó la velocidad");
        pos = len = 0;
    }

    // ── leer y escribir ──

    /** Llena el búfer con lo que haya llegado, esperando como mucho waitMs. FTDI antepone 2 bytes de estado a cada paquete. */
    private void fill(int waitMs) {
        if (pos < len || conn == null) return;
        int n = conn.bulkTransfer(epIn, rx, rx.length, Math.max(1, waitMs));
        pos = len = 0;
        if (n <= 0) return;
        if (type == FTDI) {
            int mp = epIn.getMaxPacketSize();
            if (mp <= 2) mp = 64;
            for (int off = 0; off < n; off += mp) {
                int end = Math.min(n, off + mp);
                for (int i = off + 2; i < end; i++) buf[len++] = rx[i];
            }
        } else {
            System.arraycopy(rx, 0, buf, 0, n);
            len = n;
        }
    }

    final InputStream in = new InputStream() {
        @Override public int available() {
            fill(30);
            return len - pos;
        }
        @Override public int read() throws IOException {
            long end = SystemClock.elapsedRealtime() + 8000;
            while (pos >= len) {
                if (conn == null) return -1;
                if (SystemClock.elapsedRealtime() > end) throw new IOException("El escáner no responde");
                fill(200);
            }
            return buf[pos++] & 0xFF;
        }
    };

    final OutputStream out = new OutputStream() {
        @Override public void write(int b) throws IOException { write(new byte[]{(byte) b}, 0, 1); }
        @Override public void write(byte[] b, int off, int n) throws IOException {
            if (conn == null) throw new IOException("Sin conexión");
            byte[] chunk = off == 0 ? b : java.util.Arrays.copyOfRange(b, off, off + n);
            int done = conn.bulkTransfer(epOut, chunk, n, TIMEOUT);
            if (done < 0) throw new IOException("Se desconectó el cable USB");
        }
    };

    /** Descarta lo que haya en el cable. */
    void drain() {
        pos = len = 0;
        for (int i = 0; i < 10; i++) {
            fill(20);
            if (len == 0) break;
            pos = len = 0;
        }
    }

    /** Lee lo que llegue durante ms (para reconocer al ELM327 al probar velocidades). */
    String readFor(int ms) {
        StringBuilder sb = new StringBuilder();
        long end = SystemClock.elapsedRealtime() + ms;
        while (SystemClock.elapsedRealtime() < end) {
            fill(50);
            while (pos < len) sb.append((char) (buf[pos++] & 0xFF));
            if (sb.indexOf(">") >= 0) break;
        }
        return sb.toString();
    }

    void close() {
        try { setLines(false); } catch (Throwable ignored) { }
        try { if (data != null) conn.releaseInterface(data); } catch (Throwable ignored) { }
        try { if (control != null) conn.releaseInterface(control); } catch (Throwable ignored) { }
        try { if (conn != null) conn.close(); } catch (Throwable ignored) { }
        conn = null;
    }
}
