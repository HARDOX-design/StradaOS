package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * Velocímetro analógico (2.20, diseño de Andrés): aro, marcas con números, relleno del color de acento que crece con
 * la velocidad y se apaga al frenar, y aguja. La aguja no salta entre lecturas del GPS (una por segundo): se mueve
 * fotograma a fotograma hacia la velocidad nueva con una curva suave, así que nunca da tirones. Solo dibuja mientras
 * se mueve; quieta no gasta batería ni procesador. Funciona desde Android 4.1 (postInvalidateOnAnimation es API 16).
 */
public class Gauge extends View {

    private static final float START = 150f, SWEEP = 240f;   // de las 8 a las 4 del reloj
    private static final float TAU = 0.42f;                  // segundos: cuanto más alto, más suave

    private final float k;                 // escala del lienzo de StradaOS (px por unidad)
    private int accent, ring, soft;
    private float max = 200, step = 20;
    private String unit = "km/h";
    private boolean number = true, ok = false, small = false;
    private float target = 0, val = 0;
    private long last = 0;

    private final Paint pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNum = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBig = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pUnit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNeedle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix rot = new Matrix();

    public Gauge(Context c, float scale) {
        super(c);
        k = scale;
        for (Paint p : new Paint[]{pRing, pFill, pEdge, pTick, pNeedle, pGlow}) p.setStyle(Paint.Style.STROKE);
        pRing.setStrokeCap(Paint.Cap.ROUND);
        pEdge.setStrokeCap(Paint.Cap.BUTT);
        pNeedle.setStrokeCap(Paint.Cap.ROUND);
        pGlow.setStrokeCap(Paint.Cap.ROUND);
        pNum.setTextAlign(Paint.Align.CENTER);
        pBig.setTextAlign(Paint.Align.CENTER);
        pBig.setTypeface(Typeface.DEFAULT_BOLD);
        pUnit.setTextAlign(Paint.Align.CENTER);
        setColors(0xFFE5332A, Color.WHITE, 0xFFB8BEC8);
    }

    public void setColors(int accent, int ring, int soft) {
        this.accent = accent;
        this.ring = ring;
        this.soft = soft;
        invalidate();
    }

    /** Escala y unidad: 0–200 km/h (marcas cada 20) o 0–120 mph (cada 20). */
    public void setUnit(boolean mph) {
        unit = mph ? "mph" : "km/h";
        max = mph ? 120 : 200;
        step = 20;
        invalidate();
    }

    /** Mixto: con el número grande en el centro. Analógico: solo la aguja. */
    public void setNumber(boolean show) { number = show; invalidate(); }

    /** Versión chica (sobre el mapa): menos números y trazos más finos. */
    public void setSmall(boolean v) { small = v; invalidate(); }

    /** Velocidad ya en la unidad elegida. Sin GPS la aguja vuelve a cero y el número muestra «--». */
    public void setSpeed(float v, boolean hasFix) {
        ok = hasFix;
        float t = hasFix ? Math.max(0, Math.min(max * 1.05f, v)) : 0;
        if (t == target) { if (!hasFix) invalidate(); return; }
        target = t;
        if (last == 0) last = SystemClock.uptimeMillis() - 16;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        // ── animación: acercamiento exponencial, independiente de los fotogramas por segundo ──
        long now = SystemClock.uptimeMillis();
        if (last != 0) {
            float dt = Math.min(0.1f, Math.max(0f, (now - last) / 1000f));
            val += (target - val) * (1f - (float) Math.exp(-dt / TAU));
            if (Math.abs(target - val) < 0.04f) { val = target; last = 0; }
            else { last = now; postInvalidateOnAnimation(); }
        }

        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h) / 2f * 0.96f, cx = w / 2f, cy = h / 2f + r * 0.04f;
        float frac = Math.max(0, Math.min(1, val / max));
        float sweepNow = SWEEP * frac;

        // aro exterior (abierto abajo, como el del diseño)
        pRing.setColor(ring);
        pRing.setStrokeWidth(Math.max(1.5f, r * 0.03f));
        rect.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(rect, START - 22, SWEEP + 44, false, pRing);

        // relleno: más transparente al inicio y más intenso junto a la aguja
        float rb = r * 0.77f, bw = r * 0.30f;
        rect.set(cx - rb, cy - rb, cx + rb, cy + rb);
        pFill.setStrokeWidth(bw);
        // pista vacía muy tenue para que se vea el recorrido
        pFill.setShader(null);
        pFill.setColor((ring & 0x00FFFFFF) | 0x10000000);
        c.drawArc(rect, START, SWEEP, false, pFill);
        if (sweepNow > 0.6f) {
            float f = sweepNow / 360f;
            int a0 = (accent & 0x00FFFFFF) | 0x10000000, a1 = (accent & 0x00FFFFFF) | 0x80000000, a2 = (accent & 0x00FFFFFF) | 0xF2000000;
            SweepGradient sg = new SweepGradient(cx, cy, new int[]{a0, a1, a2, a2}, new float[]{0f, f * 0.55f, f, 1f});
            rot.setRotate(START, cx, cy);
            sg.setLocalMatrix(rot);
            pFill.setColor(0xFF000000);   // opaco: el color de la pista (casi transparente) atenuaba el degradado
            pFill.setShader(sg);
            c.drawArc(rect, START, sweepNow, false, pFill);
            pFill.setShader(null);
            // borde exterior encendido
            float re = r * 0.925f;
            rect.set(cx - re, cy - re, cx + re, cy + re);
            pEdge.setColor(accent);
            pEdge.setStrokeWidth(Math.max(1.5f, r * 0.022f));
            c.drawArc(rect, START, sweepNow, false, pEdge);
        }

        // marcas y números
        int n = Math.round(max / (step / 2));
        float labelEvery = small ? step * 2 : step;
        pNum.setColor(soft);
        pNum.setTextSize(r * (small ? 0.15f : 0.11f));
        for (int i = 0; i <= n; i++) {
            float v = i * step / 2;
            boolean major = i % 2 == 0;
            double a = Math.toRadians(START + SWEEP * v / max);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            float r1 = r * (major ? 0.80f : 0.86f), r2 = r * 0.92f;
            pTick.setColor((ring & 0x00FFFFFF) | (major ? 0xB0000000 : 0x55000000));
            pTick.setStrokeWidth(Math.max(1f, r * (major ? 0.018f : 0.010f)));
            c.drawLine(cx + cs * r1, cy + sn * r1, cx + cs * r2, cy + sn * r2, pTick);
            if (major && (v % labelEvery == 0 || v == max)) {
                float rl = r * 0.64f;
                float ty = cy + sn * rl - (pNum.ascent() + pNum.descent()) / 2f;
                c.drawText(String.valueOf(Math.round(v)), cx + cs * rl, ty, pNum);
            }
        }

        // aguja con brillo
        double na = Math.toRadians(START + sweepNow);
        float ncs = (float) Math.cos(na), nsn = (float) Math.sin(na);
        float n1 = number ? r * 0.50f : r * 0.06f, n2 = r * 0.97f;
        pGlow.setColor((accent & 0x00FFFFFF) | 0x66000000);
        pGlow.setStrokeWidth(r * 0.08f);
        c.drawLine(cx + ncs * n1, cy + nsn * n1, cx + ncs * n2, cy + nsn * n2, pGlow);
        pNeedle.setColor(ring);
        pNeedle.setStrokeWidth(Math.max(2f, r * 0.028f));
        c.drawLine(cx + ncs * n1, cy + nsn * n1, cx + ncs * n2, cy + nsn * n2, pNeedle);

        // centro: número grande (mixto) y la unidad
        pUnit.setColor(accent);
        pUnit.setTextSize(r * (small ? 0.16f : 0.12f));
        if (number) {
            pBig.setColor(ring);
            pBig.setTextSize(r * (small ? 0.46f : 0.40f));
            String s = ok ? String.valueOf(Math.round(val < 1.5f && target == 0 ? 0 : val)) : "--";
            c.drawText(s, cx, cy + r * (small ? 0.16f : 0.14f), pBig);
            c.drawText(unit, cx, cy - r * (small ? 0.24f : 0.26f), pUnit);
        } else {
            c.drawText(unit, cx, cy + r * 0.30f, pUnit);
            pBig.setColor(ring);
            c.drawCircle(cx, cy, r * 0.06f, pBig);
        }
    }
}
