package com.stradaos.launcher;

import java.util.Locale;

/**
 * «Escanear todo lo que entrega el auto»: arma un informe de texto con todo lo que el auto responde por el escáner.
 *
 * - Escáner, protocolo, voltaje, VIN y número de calibración.
 * - Cada PID estándar del modo 01 que el auto dice tener, con su nombre, los bytes crudos y el valor si se conoce.
 * - PIDs de información del modo 09.
 * - Lecturas propias de la marca (Hyundai/Kia y otras): servicio 21 (01–0F) y algunos 22 a los computadores
 *   7E0 (motor) y 7E1 (caja), que es donde suelen estar datos como la temperatura del aceite de la caja. Se guardan
 *   los bytes crudos para compararlos con lo que muestra Torque.
 *
 * Solo usa servicios de lectura (01, 09, 21, 22): nunca escribe, borra ni abre sesiones de diagnóstico.
 */
public class Scan {

    public interface Progress { void on(String step); }

    /** Nombres de los PIDs del modo 01 (SAE J1979). */
    private static final String[][] NAMES = {
        {"01", "Estado de la luz del motor y monitores"}, {"03", "Estado del sistema de combustible"},
        {"04", "Carga del motor (%)"}, {"05", "Temperatura del refrigerante (°C)"},
        {"06", "Corrección corta de mezcla, banco 1 (%)"}, {"07", "Corrección larga de mezcla, banco 1 (%)"},
        {"08", "Corrección corta de mezcla, banco 2 (%)"}, {"09", "Corrección larga de mezcla, banco 2 (%)"},
        {"0A", "Presión de combustible (kPa)"}, {"0B", "Presión del múltiple de admisión MAP (kPa)"},
        {"0C", "RPM"}, {"0D", "Velocidad (km/h)"}, {"0E", "Avance del encendido (°)"},
        {"0F", "Temperatura del aire de admisión (°C)"}, {"10", "Flujo de aire MAF (g/s)"},
        {"11", "Posición del acelerador (%)"}, {"12", "Aire secundario"}, {"13", "Sensores de oxígeno presentes"},
        {"14", "Sensor de oxígeno 1 (banco 1)"}, {"15", "Sensor de oxígeno 2 (banco 1)"},
        {"16", "Sensor de oxígeno 3"}, {"17", "Sensor de oxígeno 4"}, {"1C", "Norma OBD del vehículo"},
        {"1F", "Tiempo desde el arranque (s)"}, {"21", "Distancia con la luz del motor encendida (km)"},
        {"23", "Presión del riel de combustible (kPa)"}, {"24", "Sensor de oxígeno de banda ancha 1"},
        {"2C", "EGR comandada (%)"}, {"2E", "Purga EVAP comandada (%)"}, {"2F", "Nivel de combustible (%)"},
        {"30", "Arranques desde que se borraron los códigos"}, {"31", "Distancia desde que se borraron los códigos (km)"},
        {"32", "Presión de vapor EVAP (Pa)"}, {"33", "Presión barométrica (kPa)"},
        {"34", "Sensor de oxígeno de banda ancha 1 (corriente)"}, {"3C", "Temperatura del catalizador, banco 1 (°C)"},
        {"3E", "Temperatura del catalizador, banco 1 sensor 2 (°C)"}, {"41", "Estado de los monitores en este ciclo"},
        {"42", "Voltaje del computador (V)"}, {"43", "Carga absoluta (%)"}, {"44", "Relación aire/combustible comandada"},
        {"45", "Posición relativa del acelerador (%)"}, {"46", "Temperatura exterior (°C)"},
        {"47", "Posición absoluta del acelerador B (%)"}, {"49", "Posición del pedal D (%)"},
        {"4A", "Posición del pedal E (%)"}, {"4C", "Acelerador comandado (%)"},
        {"4D", "Tiempo con la luz del motor encendida (min)"}, {"4E", "Tiempo desde que se borraron los códigos (min)"},
        {"51", "Tipo de combustible"}, {"52", "Porcentaje de etanol (%)"},
        {"55", "Corrección corta de la sonda O2 secundaria, banco 1 (%)"},
        {"56", "Corrección larga de la sonda O2 secundaria, banco 1 (%)"},
        {"5A", "Posición relativa del pedal (%)"},
        {"5B", "Batería híbrida (%)"}, {"5C", "Temperatura del aceite del motor (°C)"},
        {"5D", "Avance de inyección (°)"}, {"5E", "Consumo de combustible (L/h)"},
        {"61", "Torque pedido (%)"}, {"62", "Torque real (%)"}, {"63", "Torque de referencia (Nm)"},
        {"A6", "Odómetro (km)"},
    };

    static String name(int pid) {
        String k = String.format(Locale.US, "%02X", pid);
        for (String[] n : NAMES) if (n[0].equals(k)) return n[1];
        return "PID " + k;
    }

    /** Valor legible de los PIDs más comunes (null = solo bytes crudos). */
    static String value(int pid, byte[] b) {
        if (b == null || b.length == 0) return null;
        int a = b[0] & 0xFF, c = b.length > 1 ? b[1] & 0xFF : 0;
        switch (pid) {
            case 0x01: return ((a & 0x80) != 0 ? "luz encendida" : "luz apagada") + ", " + (a & 0x7F) + " códigos";
            case 0x04: case 0x11: case 0x2C: case 0x2E: case 0x2F: case 0x45: case 0x47: case 0x49: case 0x4A: case 0x4C: case 0x52: case 0x5A: case 0x5B:
                return fmt(a * 100f / 255f) + " %";
            case 0x05: case 0x0F: case 0x46: case 0x5C: return (a - 40) + " °C";
            case 0x06: case 0x07: case 0x08: case 0x09: return fmt((a - 128) * 100f / 128f) + " %";
            case 0x0A: return (a * 3) + " kPa";
            case 0x0B: case 0x33: return a + " kPa";
            case 0x0C: return fmt((a * 256 + c) / 4f) + " rpm";
            case 0x0D: return a + " km/h";
            case 0x0E: return fmt(a / 2f - 64) + " °";
            case 0x10: return fmt((a * 256 + c) / 100f) + " g/s";
            case 0x1F: return (a * 256 + c) + " s";
            case 0x21: case 0x31: return (a * 256 + c) + " km";
            case 0x23: return ((a * 256 + c) * 10) + " kPa";
            case 0x30: return a + "";
            case 0x3C: case 0x3E: return fmt((a * 256 + c) / 10f - 40) + " °C";
            case 0x41: return String.format(Locale.US, "%02X %02X %02X %02X", a, c, b.length > 2 ? b[2] & 0xFF : 0, b.length > 3 ? b[3] & 0xFF : 0);
            case 0x42: return fmt((a * 256 + c) / 1000f) + " V";
            case 0x43: return fmt((a * 256 + c) * 100f / 255f) + " %";
            case 0x4D: case 0x4E: return (a * 256 + c) + " min";
            case 0x55: case 0x56: return fmt((a - 128) * 100f / 128f) + " %";
            case 0x5E: return fmt((a * 256 + c) / 20f) + " L/h";
            case 0x03: return a == 1 ? "lazo abierto (motor frío)" : a == 2 ? "lazo cerrado (normal)" : a == 4 ? "lazo abierto (carga o freno motor)"
                : a == 8 ? "lazo abierto por falla" : a == 16 ? "lazo cerrado con falla de sensor" : "código " + a;
            case 0x14: case 0x15: case 0x16: case 0x17: return fmt(a / 200f) + " V";
            case 0x1C: return a == 1 ? "OBD-II (California)" : a == 2 ? "OBD (EPA)" : a == 3 ? "OBD y OBD-II" : a == 6 ? "EOBD (Europa)"
                : a == 7 ? "EOBD y OBD-II" : a == 9 ? "EOBD, OBD y OBD-II" : a == 13 ? "JOBD (Japón)" : "tipo " + a;
            case 0x44: return fmt((a * 256 + c) / 32768f) + " λ";
            default: return null;
        }
    }

    /**
     * Monitores de emisiones del PID 01 (motor bencinero): cuáles tiene el auto y si ya terminaron su prueba.
     * Sirve para saber si pasará la revisión técnica y si se borraron los códigos hace poco.
     */
    static String readiness(byte[] st) {
        if (st == null || st.length < 4) return null;
        int b = st[1] & 0xFF, c = st[2] & 0xFF, d = st[3] & 0xFF;
        StringBuilder r = new StringBuilder();
        String[] cont = {"Fallas de encendido", "Sistema de combustible", "Componentes"};
        for (int i = 0; i < 3; i++) if ((b & (1 << i)) != 0)
            r.append((b & (0x10 << i)) != 0 ? "✗ " : "✓ ").append(I18n.t(cont[i])).append('\n');
        String[] spark = {"Catalizador", "Catalizador calefaccionado", "Vapores de combustible (EVAP)", "Aire secundario",
            "Aire acondicionado", "Sensores de oxígeno", "Calefactor de sensores de oxígeno", "Recirculación de gases (EGR)"};
        if ((b & 0x08) == 0) for (int i = 0; i < 8; i++) if ((c & (1 << i)) != 0)
            r.append((d & (1 << i)) != 0 ? "✗ " : "✓ ").append(I18n.t(spark[i])).append('\n');
        return r.toString().trim();
    }

    private static String fmt(float v) { return String.format(Locale.US, "%.1f", v); }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.US, "%02X ", x & 0xFF));
        return s.toString().trim();
    }

    /** Recorre todo y devuelve el informe. Tarda entre 20 s y 2 min según el auto. */
    static String run(Elm elm, Progress p) throws java.io.IOException {
        StringBuilder r = new StringBuilder();
        r.append("Informe OBD-II de StradaOS · ")
            .append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new java.util.Date())).append('\n');
        r.append("Escáner: ").append(elm.version).append('\n');
        r.append("Protocolo: ").append(elm.protocol).append(elm.can ? " (CAN)" : "").append('\n');
        r.append("Voltaje en el conector: ").append(elm.cmd("ATRV", 1500)).append('\n');
        String vin = null;
        try { vin = elm.vin(); } catch (Throwable ignored) { }
        r.append("VIN: ").append(vin != null ? vin : "el auto no lo entrega").append("\n\n");

        // ── modo 01: datos en vivo estándar ──
        p.on("Datos estándar (modo 01)…");
        r.append("== Datos estándar que entrega el auto (modo 01) ==\n");
        int n = 0;
        for (int pid = 1; pid <= 0xE0; pid++) {
            if (pid % 0x20 == 0 || !elm.supports(pid)) continue;    // 20, 40… solo dicen qué PIDs hay
            p.on("Modo 01 · PID " + String.format(Locale.US, "%02X", pid));
            byte[] b = elm.pid(pid);
            String v = value(pid, b);
            r.append(String.format(Locale.US, "01%02X  ", pid)).append(name(pid)).append(": ")
                .append(b == null ? "sin respuesta" : (v != null ? v + "   [" + hex(b) + "]" : "[" + hex(b) + "]")).append('\n');
            n++;
        }
        r.append(n).append(" datos estándar.\n\n");
        String rd = readiness(elm.pid(0x01));
        if (rd != null) r.append("== Monitores de emisiones (✓ listo, ✗ incompleto) ==\n").append(rd).append("\n\n");

        // ── modo 09: información del vehículo ──
        p.on("Información del vehículo (modo 09)…");
        r.append("== Información (modo 09) ==\n");
        String m9 = clean(elm.cmd("0900", 3000));
        r.append("0900 PIDs disponibles: ").append(m9.contains("NODATA") ? "el auto no lo entrega" : m9).append('\n');
        r.append("0904 Calibración: ").append(ascii(elm.cmd("0904", 4000))).append('\n');
        r.append("090A Nombre del computador: ").append(ascii(elm.cmd("090A", 4000))).append("\n\n");

        // ── datos propios de la marca ──
        r.append("== Datos propios de la marca (crudos) ==\n");
        r.append("Para encontrar un dato (por ejemplo la temperatura del aceite de la caja), compara estos bytes con lo que muestra Torque.\n");
        String[][] heads = {{"7E0", "motor"}, {"7E1", "caja automática"}};
        if (elm.can) {
            for (String[] h : heads) {
                elm.cmd("ATSH" + h[0], 1500);
                for (int lid = 1; lid <= 0x0F; lid++) probe(elm, p, r, h, String.format(Locale.US, "21%02X", lid));
                for (String did : new String[]{"220100", "220101", "220102", "221E1C", "22E001", "22E002"}) probe(elm, p, r, h, did);
            }
            elm.cmd("ATSH7DF", 1500);             // de vuelta a la consulta general
        } else {
            r.append("(El auto no usa CAN: estas lecturas no se prueban en este protocolo.)\n");
        }
        return r.toString();
    }

    private static void probe(Elm elm, Progress p, StringBuilder r, String[] h, String cmd) throws java.io.IOException {
        p.on(h[0] + " · " + cmd);
        String resp = elm.cmd(cmd, 2000);
        String c = clean(resp);
        if (c.length() == 0 || c.contains("NODATA") || c.contains("ERROR") || c.contains("?") || c.startsWith("7F")) return;   // 7F = no lo soporta
        r.append(h[0]).append(" (").append(h[1]).append(") ").append(cmd).append(": ").append(c).append('\n');
    }

    private static String clean(String s) { return s.replace("\r", " | ").replaceAll("\\s+\\|\\s*$", "").trim(); }

    /** Respuesta del modo 09 con texto (calibración, nombre): deja solo letras y números. */
    private static String ascii(String resp) {
        if (resp.contains("NODATA")) return "el auto no lo entrega";
        StringBuilder out = new StringBuilder();
        for (String line : resp.split("[\r\n]+")) {
            String l = (line.indexOf(':') > 0 ? line.substring(line.indexOf(':') + 1) : line).replaceAll("[^0-9A-F]", "");
            if (l.startsWith("49")) l = l.length() >= 6 ? l.substring(6) : "";
            for (int i = 0; i + 2 <= l.length(); i += 2) {
                int ch = Integer.parseInt(l.substring(i, i + 2), 16);
                if (ch >= 0x20 && ch < 0x7F) out.append((char) ch);
            }
        }
        String s = out.toString().trim();
        return s.length() > 0 ? s : clean(resp);
    }
}
