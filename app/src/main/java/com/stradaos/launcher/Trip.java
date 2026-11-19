package com.stradaos.launcher;

import android.location.Location;

/**
 * Computador de viaje para la franja de widgets del inicio (2.24): distancia y tiempo en movimiento (GPS), altitud,
 * pendiente y rumbo (GPS) y combustible usado (calculado con el flujo de aire del escáner OBD, si está conectado).
 * Vive en memoria desde que arranca StradaOS; presión larga en el widget lo reinicia.
 */
public class Trip {
    static volatile double meters, fuelL;
    static volatile long movingMs, startAt = System.currentTimeMillis();
    /** Altitud suavizada (m), pendiente (%) de los últimos ~60 m recorridos y rumbo (°); NaN = aún no se sabe. */
    static volatile float alt = Float.NaN, grade = Float.NaN, bearing = Float.NaN, speedKmh;
    private static Location last;
    private static float baseAlt = Float.NaN;
    private static double gradeDist;
    private static long fuelAt;

    static synchronized void onLocation(Location l) {
        if (l == null || (l.hasAccuracy() && l.getAccuracy() > 50)) return;
        speedKmh = l.hasSpeed() ? l.getSpeed() * 3.6f : 0;
        if (l.hasBearing() && speedKmh > 4) bearing = l.getBearing();
        float a = l.hasAltitude() ? (float) l.getAltitude() : Float.NaN;
        if (last == null) { if (!Float.isNaN(a)) alt = a; last = l; baseAlt = alt; return; }
        float d = last.distanceTo(l);
        long dt = l.getTime() - last.getTime();
        if (dt <= 0) return;
        if (dt > 15000 || d / (dt / 1000f) > 70) {                         // salto: se empieza de nuevo (también la altitud)
            last = l;
            if (!Float.isNaN(a)) alt = a;
            baseAlt = alt; gradeDist = 0; grade = Float.NaN;
            return;
        }
        // altitud suavizada (el GPS la da con ruido); un cambio brusco de más de 80 m se toma tal cual
        if (!Float.isNaN(a)) alt = Float.isNaN(alt) || Math.abs(a - alt) > 80 ? a : alt * 0.8f + a * 0.2f;
        // GPS que no entrega velocidad o dirección: se calculan con las dos últimas posiciones
        if (!l.hasSpeed()) speedKmh = d / (dt / 1000f) * 3.6f;
        if ((!l.hasBearing() || Float.isNaN(bearing)) && d >= 8 && speedKmh > 4) bearing = last.bearingTo(l);
        if (d < 4 && speedKmh < 3) return;                                                             // quieto: ruido del GPS
        meters += d;
        if (speedKmh > 2) movingMs += dt;
        gradeDist += d;
        if (gradeDist >= 60 && !Float.isNaN(alt) && !Float.isNaN(baseAlt)) {
            float g = (float) ((alt - baseAlt) / gradeDist * 100);
            grade = Float.isNaN(grade) ? g : grade * 0.5f + g * 0.5f;
            baseAlt = alt;
            gradeDist = 0;
        }
        last = l;
    }

    /** Lo llama el monitor OBD con cada lectura del consumo instantáneo (litros por hora). */
    static synchronized void addFuel(float litersPerHour) {
        long now = System.currentTimeMillis();
        if (fuelAt > 0) fuelL += litersPerHour * Math.min(now - fuelAt, 5000) / 3600000.0;
        fuelAt = now;
    }

    static synchronized void reset() {
        meters = 0; fuelL = 0; movingMs = 0; fuelAt = 0;
        startAt = System.currentTimeMillis();
    }

    static String compass(float deg) {
        if (Float.isNaN(deg)) return "—";
        String[] p = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        return p[Math.round(((deg % 360) + 360) % 360 / 45f) % 8];
    }
}
