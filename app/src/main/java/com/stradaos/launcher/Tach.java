package com.stradaos.launcher;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;

/**
 * Cuentarrevoluciones chico que acompaña al velocímetro (Ajustes → Tu vehículo → «Revoluciones junto al velocímetro»,
 * apagado de fábrica): aro abierto abajo como el del velocímetro, relleno del color de acento, zona roja desde 6000 rpm
 * y el número al centro. Lo dibujan {@link Gauge} (a la izquierda si sobra ancho, si no en la esquina de abajo a la
 * izquierda) y {@link FaroGauge} (centrado debajo de «km/h»). Carbono usa su versión digital ({@link Carbono.Rpm}). Solo aparece
 * mientras el escáner OBD entrega las RPM.
 *
 * La aguja no salta entre lecturas (~5 por segundo con la lectura agrupada): se acerca con la misma curva suave que el
 * velocímetro. El dueño de la vista llama a {@link #step()} en cada fotograma y vuelve a dibujar mientras devuelva true.
 */
final class Tach {
    static final float MAX = 7000, RED = 6000;
    private static final float START = 150f, SWEEP = 240f, TAU = 0.25f;
    private float target = Float.NaN, val;
    private long last;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
        red = new Paint(Paint.ANTI_ALIAS_FLAG), tick = new Paint(Paint.ANTI_ALIAS_FLAG), big = new Paint(Paint.ANTI_ALIAS_FLAG),
        small = new Paint(Paint.ANTI_ALIAS_FLAG), needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    Tach() {
        for (Paint p : new Paint[]{ring, fill, red, tick, needle}) { p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); }
        fill.setStrokeCap(Paint.Cap.BUTT);
        red.setStrokeCap(Paint.Cap.BUTT);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        small.setTextAlign(Paint.Align.CENTER);
    }

    /** RPM del escáner, o NaN para esconderlo. Devuelve true si cambió algo que haya que dibujar. */
    boolean set(float rpm) {
        boolean had = !Float.isNaN(target);
        if (Float.isNaN(rpm)) { target = Float.NaN; return had; }
        float t = Math.max(0, Math.min(MAX * 1.05f, rpm));
        if (!had) { val = t; target = t; return true; }
        if (t == target) return false;
        target = t;
        if (last == 0) last = SystemClock.uptimeMillis() - 16;
        return true;
    }

    boolean visible() { return !Float.isNaN(target); }

    /** Avanza la animación; true mientras la aguja se sigue moviendo. */
    boolean step() {
        if (last == 0 || Float.isNaN(target)) return false;
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.1f, Math.max(0f, (now - last) / 1000f));
        val += (target - val) * (1f - (float) Math.exp(-dt / TAU));
        if (Math.abs(target - val) < 5) { val = target; last = 0; return false; }
        last = now;
        return true;
    }

    /** Dibuja el cuentarrevoluciones de radio r centrado en (cx, cy). */
    void draw(Canvas c, float cx, float cy, float r, int accent, int ringColor, int soft) {
        if (Float.isNaN(target) || r < 8) return;
        float frac = Math.max(0, Math.min(1, val / MAX));
        ring.setColor((ringColor & 0x00FFFFFF) | 0xB0000000);
        ring.setStrokeWidth(Math.max(1.2f, r * 0.045f));
        rect.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(rect, START, SWEEP, false, ring);
        // pista, zona roja y relleno
        float rb = r * 0.78f, bw = r * 0.22f;
        rect.set(cx - rb, cy - rb, cx + rb, cy + rb);
        fill.setStrokeWidth(bw);
        fill.setColor((ringColor & 0x00FFFFFF) | 0x14000000);
        c.drawArc(rect, START, SWEEP, false, fill);
        red.setStrokeWidth(bw * 0.35f);
        red.setColor(0xB0E5332A);
        float rr = r * 0.92f;
        rect.set(cx - rr, cy - rr, cx + rr, cy + rr);
        c.drawArc(rect, START + SWEEP * RED / MAX, SWEEP * (1 - RED / MAX), false, red);
        rect.set(cx - rb, cy - rb, cx + rb, cy + rb);
        float sweep = SWEEP * frac;
        if (sweep > 0.5f) {
            fill.setColor(val >= RED ? 0xF2E5332A : (accent & 0x00FFFFFF) | 0xD0000000);
            c.drawArc(rect, START, sweep, false, fill);
        }
        // marcas cada 1000 rpm
        tick.setStrokeWidth(Math.max(1f, r * 0.03f));
        for (int i = 0; i <= (int) (MAX / 1000); i++) {
            double a = Math.toRadians(START + SWEEP * i * 1000 / MAX);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a), r1 = r * 0.84f, r2 = r * 0.96f;
            tick.setColor(i * 1000 >= RED ? 0xFFE5332A : (ringColor & 0x00FFFFFF) | 0x90000000);
            c.drawLine(cx + cs * r1, cy + sn * r1, cx + cs * r2, cy + sn * r2, tick);
        }
        // aguja corta
        double na = Math.toRadians(START + sweep);
        float ncs = (float) Math.cos(na), nsn = (float) Math.sin(na);
        needle.setColor(ringColor);
        needle.setStrokeWidth(Math.max(1.5f, r * 0.05f));
        c.drawLine(cx + ncs * r * 0.55f, cy + nsn * r * 0.55f, cx + ncs * r * 0.98f, cy + nsn * r * 0.98f, needle);
        // número (de a 50 rpm, para que no tiemble) y «rpm»
        big.setColor(ringColor);
        big.setTextSize(r * 0.40f);
        c.drawText(I18n.integer(Math.round(val / 50f) * 50), cx, cy + r * 0.14f, big);
        small.setColor(soft);
        small.setTextSize(r * 0.20f);
        c.drawText("rpm", cx, cy + r * 0.48f, small);
    }
}
