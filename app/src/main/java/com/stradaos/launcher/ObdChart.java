package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/**
 * Gráfico grande de un dato: valor actual, mínimo, máximo y promedio del período, escala con grilla, eje de tiempo,
 * zonas de aviso y alerta sombreadas con sus umbrales, y lectura de cualquier punto deslizando el dedo.
 */
public class ObdChart extends View implements Obd.Listener {
    final Obd.Sensor s;
    final long win;
    long drawn = -1;
    private final float[] vs = new float[Obd.Sensor.LONG];
    private final long[] ts = new long[Obd.Sensor.LONG];
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint(Paint.ANTI_ALIAS_FLAG),
        line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), zone = new Paint(),
        lim = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), area = new Path();
    private final RectF r = new RectF();
    private float touchX = -1;

    private final float k;
    /** true: deslizar el dedo muestra el valor de cada momento (pantalla Vehículo). false: un toque es un clic (inicio). */
    private final boolean scrub;

    public ObdChart(Context c, Obd.Sensor s, long win, float scale, boolean scrub) {
        super(c);
        this.s = s;
        this.win = win;
        this.k = scale;
        this.scrub = scrub;
        grid.setStrokeWidth(Math.max(1, px(1)));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(px(2.5f));
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        lim.setStyle(Paint.Style.STROKE);
        lim.setStrokeWidth(px(1.5f));
        lim.setPathEffect(new DashPathEffect(new float[]{px(7), px(5)}, 0));
    }

    private int px(float v) { return Math.round(v * k); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); Obd.addListener(this); }
    @Override protected void onDetachedFromWindow() { Obd.removeListener(this); super.onDetachedFromWindow(); }
    @Override public void onObd() { if (drawn != s.pushes) invalidate(); }

    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if (!scrub) return super.onTouchEvent(e);
        int a = e.getActionMasked();
        if (a == android.view.MotionEvent.ACTION_DOWN || a == android.view.MotionEvent.ACTION_MOVE) {
            touchX = e.getX();
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
            touchX = -1;
        }
        invalidate();
        return true;
    }

    int color(int level) { return level == Obd.CRIT ? ObdActivity.DANGER : level == Obd.WARN ? ObdActivity.AMBER : Main.RED; }

    String fmt(float v) {
        float span = s.max - s.min;
        return span <= 20 || "V".equals(s.unit) || s.oneDecimal || s.pid == -2 || s.pid == -3 ? String.format(I18n.num, "%.1f", v) : I18n.integer(Math.round(v));
    }

    /** Paso «redondo» de la grilla (1, 2, 2,5 o 5 × 10^n) para unas 4 divisiones. */
    float niceStep(float span) {
        double raw = span / 4.0, mag = Math.pow(10, Math.floor(Math.log10(raw))), f = raw / mag;
        return (float) ((f < 1.5 ? 1 : f < 2.25 ? 2 : f < 3.5 ? 2.5 : f < 7.5 ? 5 : 10) * mag);
    }

    @Override protected void onDraw(Canvas c) {
        drawn = s.pushes;
        float w = getWidth(), h = getHeight();
        bg.setColor(Main.CARD);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, px(16), px(16), bg);
        long now = System.currentTimeMillis();
        int n = s.since(now - win, vs, ts);
        boolean small = h < px(230);

        // ── encabezado: nombre, valor actual y estadísticas del período ──
        float pad = px(14);
        int lvl = s.level;
        txt.setTypeface(Typeface.DEFAULT);
        txt.setTextAlign(Paint.Align.LEFT);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE, sum = 0;
        for (int i = 0; i < n; i++) { min = Math.min(min, vs[i]); max = Math.max(max, vs[i]); sum += vs[i]; }
        String val = s.has() ? fmt(s.value) : "—";
        float headH;
        if (small) {
            txt.setTextSize(px(15)); txt.setColor(Main.SOFT);
            c.drawText(I18n.t(s.label), pad, pad + px(20), txt);
            float lw = txt.measureText(I18n.t(s.label));
            txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextSize(px(26)); txt.setColor(lvl == Obd.OK ? Main.TEXT : color(lvl));
            c.drawText(val, pad + lw + px(12), pad + px(22), txt);
            float vw = txt.measureText(val);
            txt.setTypeface(Typeface.DEFAULT); txt.setTextSize(px(14)); txt.setColor(Main.MUTED);
            c.drawText(s.unit, pad + lw + px(16) + vw, pad + px(22), txt);
            headH = px(40);
        } else {
            txt.setTextSize(px(16)); txt.setColor(Main.SOFT);
            c.drawText(I18n.t(s.label), pad, pad + px(14), txt);
            txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextSize(px(44)); txt.setColor(lvl == Obd.OK ? Main.TEXT : color(lvl));
            c.drawText(val, pad, pad + px(60), txt);
            float vw = txt.measureText(val);
            txt.setTypeface(Typeface.DEFAULT); txt.setTextSize(px(18)); txt.setColor(Main.MUTED);
            c.drawText(s.unit, pad + vw + px(8), pad + px(60), txt);
            headH = px(84);
        }
        if (n > 0) {
            String st = I18n.t("mín") + " " + fmt(min) + "   " + I18n.t("máx") + " " + fmt(max) + "   " + I18n.t("prom") + " " + fmt(sum / n);
            txt.setTextSize(px(small ? 13 : 15)); txt.setColor(Main.SOFT);
            // tarjeta angosta (inicio): las estadísticas van en su propia línea, debajo del valor
            boolean narrow = txt.measureText(st) + txt.measureText(I18n.t(s.label)) + px(small ? 160 : 40) > w - pad * 2;
            if (narrow) {
                txt.setTextAlign(Paint.Align.LEFT); txt.setTextSize(px(13));
                c.drawText(st, pad, headH + px(6), txt);
                headH += px(16);
            } else {
                txt.setTextAlign(Paint.Align.RIGHT);
                c.drawText(st, w - pad, pad + px(small ? 20 : 14), txt);
            }
        }

        // ── zona del gráfico ──
        float left = px(58), right = w - px(18), top = headH + px(12), bottom = h - px(30);
        if (bottom - top < px(30)) return;
        // escala: los datos del período con margen, sin hacer ruido de las variaciones mínimas
        float lo = n > 0 ? min : s.min, hi = n > 0 ? max : s.max;
        float minSpan = (s.max - s.min) * 0.08f;
        if (hi - lo < minSpan) { float mid = (hi + lo) / 2; lo = mid - minSpan / 2; hi = mid + minSpan / 2; }
        float span = hi - lo;
        lo -= span * 0.12f; hi += span * 0.12f;
        // si un umbral está cerca, que se vea
        for (float t : new float[]{s.hi1, s.hi2, s.lo1, s.lo2}) if (!Float.isNaN(t) && t > lo - span * 0.6f && t < hi + span * 0.6f) { lo = Math.min(lo, t - span * 0.05f); hi = Math.max(hi, t + span * 0.05f); }
        if (!Float.isNaN(s.abs1)) { lo = Math.min(lo, -s.abs1 * 1.1f); hi = Math.max(hi, s.abs1 * 1.1f); }
        final float flo = lo, fhi = hi, ftop = top, fbot = bottom;
        float plotH = bottom - top, plotW = right - left;

        // zonas de aviso (ámbar) y alerta (rojo)
        zone.setColor((ObdActivity.AMBER & 0x00FFFFFF) | 0x1C000000);
        if (!Float.isNaN(s.hi1)) c.drawRect(left, yOf(Float.isNaN(s.hi2) ? fhi : Math.min(s.hi2, fhi), flo, fhi, ftop, fbot), right, yOf(s.hi1, flo, fhi, ftop, fbot), zone);
        if (!Float.isNaN(s.lo1)) c.drawRect(left, yOf(s.lo1, flo, fhi, ftop, fbot), right, yOf(Float.isNaN(s.lo2) ? flo : Math.max(s.lo2, flo), flo, fhi, ftop, fbot), zone);
        if (!Float.isNaN(s.abs1)) {
            c.drawRect(left, yOf(Math.min(s.abs2, fhi), flo, fhi, ftop, fbot), right, yOf(s.abs1, flo, fhi, ftop, fbot), zone);
            c.drawRect(left, yOf(-s.abs1, flo, fhi, ftop, fbot), right, yOf(Math.max(-s.abs2, flo), flo, fhi, ftop, fbot), zone);
        }
        zone.setColor((ObdActivity.DANGER & 0x00FFFFFF) | 0x24000000);
        if (!Float.isNaN(s.hi2) && s.hi2 < fhi) c.drawRect(left, top, right, yOf(s.hi2, flo, fhi, ftop, fbot), zone);
        if (!Float.isNaN(s.lo2) && s.lo2 > flo) c.drawRect(left, yOf(s.lo2, flo, fhi, ftop, fbot), right, bottom, zone);
        if (!Float.isNaN(s.abs2)) {
            if (s.abs2 < fhi) c.drawRect(left, top, right, yOf(s.abs2, flo, fhi, ftop, fbot), zone);
            if (-s.abs2 > flo) c.drawRect(left, yOf(-s.abs2, flo, fhi, ftop, fbot), right, bottom, zone);
        }

        // grilla horizontal con los valores
        float step = niceStep(hi - lo);
        txt.setTextAlign(Paint.Align.RIGHT); txt.setTextSize(px(13)); txt.setColor(Main.MUTED); txt.setTypeface(Typeface.DEFAULT);
        grid.setColor((Main.MUTED & 0x00FFFFFF) | 0x33000000);
        for (float g = (float) Math.ceil(lo / step) * step; g <= hi; g += step) {
            float y = yOf(g, flo, fhi, ftop, fbot);
            c.drawLine(left, y, right, y, grid);
            c.drawText(step < 1 ? String.format(I18n.num, "%.1f", g) : I18n.integer(Math.round(g)), left - px(8), y + px(5), txt);
        }
        // eje de tiempo
        txt.setTextAlign(Paint.Align.CENTER);
        boolean fewTicks = plotW < px(300);       // tarjeta angosta: solo inicio, medio y «ahora»
        for (int i = 0; i <= 4; i++) {
            float x = left + plotW * i / 4f;
            c.drawLine(x, top, x, bottom, grid);
            if (fewTicks && i % 2 == 1) continue;
            long ago = win * (4 - i) / 4;
            String t = i == 4 ? I18n.t("ahora") : ago >= 60000 ? "-" + (ago / 60000) + " min" : "-" + (ago / 1000) + " s";
            c.drawText(t, Math.max(left + px(16), Math.min(right - px(16), x)), bottom + px(20), txt);
        }
        // umbrales con su nombre
        txt.setTextAlign(Paint.Align.LEFT); txt.setTextSize(px(12));
        float[][] th = {{s.hi1, 1}, {s.hi2, 2}, {s.lo1, 1}, {s.lo2, 2}, {s.abs1, 1}, {s.abs2, 2}, {-s.abs1, 1}, {-s.abs2, 2}};
        for (float[] t : th) {
            if (Float.isNaN(t[0]) || t[0] < lo || t[0] > hi) continue;
            float y = yOf(t[0], flo, fhi, ftop, fbot);
            lim.setColor(t[1] == 2 ? ObdActivity.DANGER : ObdActivity.AMBER);
            c.drawLine(left, y, right, y, lim);
            txt.setColor(t[1] == 2 ? ObdActivity.DANGER : ObdActivity.AMBER);
            c.drawText(I18n.t(t[1] == 2 ? "alerta" : "aviso") + " " + fmt(t[0]), left + px(6), y - px(4), txt);
        }

        if (n < 2) {
            txt.setTextAlign(Paint.Align.CENTER); txt.setTextSize(px(14)); txt.setColor(Main.MUTED);
            String msg = I18n.t(Obd.running() ? "Esperando datos…" : "Monitor apagado · toca para conectar");
            if (txt.measureText(msg) > right - left - px(8)) msg = I18n.t(Obd.running() ? "Esperando datos…" : "Monitor apagado");
            c.drawText(msg, (left + right) / 2, (top + bottom) / 2, txt);
            return;
        }
        // línea (se corta si faltaron datos más de 10 s) y relleno con degradado
        path.reset(); area.reset();
        boolean open = false;
        float firstX = 0, lastX = 0, lastY = 0;
        for (int i = 0; i < n; i++) {
            float x = right - (now - ts[i]) / (float) win * plotW, y = yOf(vs[i], flo, fhi, ftop, fbot);
            if (x < left) continue;
            if (open && i > 0 && ts[i] - ts[i - 1] > 10000) {
                area.lineTo(lastX, bottom); area.lineTo(firstX, bottom); area.close();
                open = false;
            }
            if (!open) { path.moveTo(x, y); area.moveTo(x, bottom); area.lineTo(x, y); firstX = x; open = true; }
            else { path.lineTo(x, y); area.lineTo(x, y); }
            lastX = x; lastY = y;
        }
        if (open) { area.lineTo(lastX, bottom); area.lineTo(firstX, bottom); area.close(); }
        int col = color(lvl);
        fill.setShader(new android.graphics.LinearGradient(0, top, 0, bottom, (col & 0x00FFFFFF) | 0x66000000, col & 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
        c.drawPath(area, fill);
        line.setColor(col);
        c.drawPath(path, line);
        // punto del valor actual con halo
        dot.setColor((col & 0x00FFFFFF) | 0x44000000);
        c.drawCircle(lastX, lastY, px(10), dot);
        dot.setColor(col);
        c.drawCircle(lastX, lastY, px(5), dot);

        // lectura con el dedo: línea vertical, punto y globo con valor y hora
        if (touchX >= left && touchX <= right) {
            long t = now - (long) ((right - touchX) / plotW * win);
            int best = -1;
            long bd = Long.MAX_VALUE;
            for (int i = 0; i < n; i++) { long dd = Math.abs(ts[i] - t); if (dd < bd) { bd = dd; best = i; } }
            if (best >= 0) {
                float x = right - (now - ts[best]) / (float) win * plotW, y = yOf(vs[best], flo, fhi, ftop, fbot);
                grid.setColor(Main.SOFT);
                c.drawLine(x, top, x, bottom, grid);
                dot.setColor(Main.TEXT);
                c.drawCircle(x, y, px(6), dot);
                String a = fmt(vs[best]) + " " + s.unit, b = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date(ts[best]))
                    + " · " + I18n.t("hace") + " " + ((now - ts[best]) >= 60000 ? ((now - ts[best]) / 60000) + " min" : ((now - ts[best]) / 1000) + " s");
                txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextSize(px(16));
                float bw = Math.max(txt.measureText(a), txt.measureText(b) * 0.85f) + px(20), bh = px(48);
                float bx = Math.max(left, Math.min(right - bw, x - bw / 2)), by = Math.max(top, y - bh - px(14));
                if (by + bh > y - px(6) && y - bh - px(14) < top) by = y + px(14);
                bg.setColor(Main.INNER);
                r.set(bx, by, bx + bw, by + bh);
                c.drawRoundRect(r, px(10), px(10), bg);
                txt.setTextAlign(Paint.Align.LEFT); txt.setColor(Main.TEXT);
                c.drawText(a, bx + px(10), by + px(21), txt);
                txt.setTypeface(Typeface.DEFAULT); txt.setTextSize(px(13)); txt.setColor(Main.SOFT);
                c.drawText(b, bx + px(10), by + px(39), txt);
            }
        }
    }

    float yOf(float v, float lo, float hi, float top, float bottom) {
        float f = (v - lo) / (hi - lo);
        return bottom - Math.max(0, Math.min(1, f)) * (bottom - top);
    }
}
