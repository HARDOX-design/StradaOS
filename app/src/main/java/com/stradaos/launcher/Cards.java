package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Tema premium «Premium Cards» (Bloque 25, 2.39). Tarjetas sobrias en tres columnas sobre un fondo casi negro (o gris
 * claro con tarjetas blancas y sombra suave en el modo claro), con el color de acento de cada usuario.
 *
 * Horizontal: el reproductor alto a la izquierda (se esconde deslizándolo hacia la izquierda y el mapa se agranda),
 * el mapa en el centro con la ciudad arriba, y a la derecha la hora en grande con la fecha, el clima con los próximos
 * días y el próximo mantenimiento con tres accesos a apps. La barra de apps va abajo, a la izquierda o a la derecha
 * (a los costados, las tarjetas llegan hasta abajo). Vertical: el mapa con el reproductor arriba y debajo las tarjetas.
 *
 * Reusa las piezas de Faro (tarjeta con sombra, íconos de trazo, línea de tiempo, buscador del mapa): Faro pregunta a
 * {@link Prem#cards()} para devolver los colores de este tema. El fondo se pinta una vez (Picanto: Android 4.2).
 */
final class Cards {
    private Cards() { }

    static final String ID = "cards";

    // ── colores (oscuro / claro) ──
    static int base(boolean l) { return l ? 0xFFE8EAED : 0xFF0D0E10; }
    static int glass(boolean l) { return l ? 0xFFFFFFFF : 0xFF1A1B1E; }
    static int glassEdge(boolean l) { return l ? 0xFFFFFFFF : 0x12FFFFFF; }
    static int pill(boolean l) { return l ? 0xF7FFFFFF : 0xF01A1B1E; }
    static int pillEdge(boolean l) { return l ? 0xFFFFFFFF : 0x14FFFFFF; }
    static int pillIcon(boolean l) { return l ? 0xFF1E2024 : 0xFFE9EAEC; }
    static int soft(boolean l) { return l ? 0x0D000000 : 0x12FFFFFF; }
    static int tile(boolean l) { return l ? 0xFFF1F2F4 : 0xFF25262A; }
    static int track(boolean l) { return l ? 0xFFDCDFE3 : 0xFF33353A; }
    /** Franja de la barra de apps cuando va a un costado (como la barra lateral de la referencia). */
    static int rail(boolean l) { return l ? 0xFFF8F9FA : 0xFF141518; }
    static int railEdge(boolean l) { return l ? 0x14000000 : 0x0FFFFFFF; }

    // ── fondo ──

    /** Casi plano: la base con una luz muy suave arriba al centro y las esquinas un poco más oscuras. */
    static void paint(Canvas c, float w, float h, boolean light, int accent) {
        c.drawColor(base(light));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        float u = Math.max(w, h);
        int glow = light ? 0xFFFFFFFF : 0xFF2A2C31;
        p.setShader(new RadialGradient(w * 0.5f, -h * 0.15f, u * 0.75f,
            new int[]{Aurora.a(glow, light ? 0.55f : 0.55f), Aurora.a(glow, light ? 0.18f : 0.16f), Aurora.a(glow, 0f)},
            new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // una pizca del acento abajo a la izquierda (casi no se nota: da profundidad sin competir con las tarjetas)
        p.setShader(new RadialGradient(0, h, u * 0.55f,
            new int[]{Aurora.a(accent, light ? 0.06f : 0.07f), Aurora.a(accent, 0f)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int dark = light ? 0xFF8C93A0 : 0xFF000000;
        p.setShader(new RadialGradient(w * 0.5f, h * 0.45f, u * 0.72f,
            new int[]{Aurora.a(dark, 0f), Aurora.a(dark, 0f), Aurora.a(dark, light ? 0.10f : 0.35f)},
            new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    static int grain(boolean light) { return light ? 4 : 6; }

    /**
     * Sombra y fondo de la tarjeta del mapa (modo claro) que siguen el borde izquierdo del mapa mientras el reproductor
     * entra o sale: el borde se mueve con {@link #setInset} sin rearmar nada.
     */
    static final class MapShadow extends View {
        private final Faro.Card card;
        private float inset;

        MapShadow(Context c, boolean light, float radiusPx, float marginPx, float scale) {
            super(c);
            card = new Faro.Card(light, radiusPx, marginPx, scale);
        }

        void setInset(float px) {
            if (px == inset) return;
            inset = px;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            card.setBounds(Math.round(inset), 0, getWidth(), getHeight());
            card.draw(c);
        }
    }

    /** Parlante de trazo fino para el volumen (sin PNG nuevos). */
    static final class Speaker extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF o = new RectF();

        Speaker(Context c, int color) {
            super(c);
            p.setColor(color);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), k = Math.min(w, h) / 24f;
            c.save();
            c.translate((w - 24 * k) / 2, (h - 24 * k) / 2);
            path.reset();
            path.moveTo(4 * k, 9.5f * k);
            path.lineTo(7.5f * k, 9.5f * k);
            path.lineTo(12 * k, 5.5f * k);
            path.lineTo(12 * k, 18.5f * k);
            path.lineTo(7.5f * k, 14.5f * k);
            path.lineTo(4 * k, 14.5f * k);
            path.close();
            p.setStyle(Paint.Style.FILL);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f * k);
            o.set(10.5f * k, 8.5f * k, 17.5f * k, 15.5f * k);
            c.drawArc(o, -50, 100, false, p);
            o.set(9 * k, 5 * k, 21 * k, 19 * k);
            c.drawArc(o, -50, 100, false, p);
            c.restore();
        }
    }

    // ── miniatura para la galería ──

    /** Miniatura de la galería con la misma distribución que buildCards en Main. */
    static final class Thumb extends View {
        final boolean portrait, light;
        boolean selected;
        private final float s;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Thumb(Context c, float s, boolean portrait, boolean light) {
            super(c);
            this.s = s;
            this.portrait = portrait;
            this.light = light;
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            setMeasuredDimension(w, (int) (w * (portrait ? 1.42f : 0.586f)));
        }

        private void rr(Canvas c, float l, float t, float rt, float b, float rad, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(color);
            r.set(l, t, rt, b);
            c.drawRoundRect(r, rad, rad, p);
        }

        private void card(Canvas c, float l, float t, float rt, float b, float rad) {
            if (light) rr(c, l, t + rad * 0.2f, rt, b + rad * 0.3f, rad, 0x121C2230);
            rr(c, l, t, rt, b, rad, glass(light));
            if (!light) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, s * 0.6f));
                p.setColor(glassEdge(false));
                r.set(l, t, rt, b);
                c.drawRoundRect(r, rad, rad, p);
            }
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float u = portrait ? w : h, rad = u * 0.06f, crad = u * 0.035f;
            rr(c, 0, 0, w, h, rad, base(light));
            int text = light ? 0xFF15171B : 0xFFF2F3F5, muted = light ? 0xFF7A7F88 : 0xFF7E838C;
            int acc = Main.RED;
            float pad = u * 0.035f, gap = u * 0.026f, topH = u * 0.075f;
            // barra de arriba: el logo al centro, los íconos a la derecha
            rr(c, w / 2f - u * 0.06f, pad + topH * 0.2f, w / 2f + u * 0.06f, pad + topH * 0.55f, u * 0.01f, Aurora.a(text, 0.8f));
            for (int i = 0; i < 3; i++) {
                p.setColor(Aurora.a(muted, 0.9f));
                c.drawCircle(w - pad - u * 0.02f - i * u * 0.045f, pad + topH * 0.38f, u * 0.011f, p);
            }
            float top = pad + topH;
            if (!portrait) {
                float dockH = u * 0.13f, dockY = h - pad - dockH, bottom = dockY - gap;
                float cw = (w - 2 * pad - 2 * gap) / 3f;
                float px = pad, mx = pad + cw + gap, rx = mx + cw + gap;
                player(c, px, top, cw, bottom - top, crad, text, muted, acc);
                map(c, mx, top, cw, bottom - top, crad, muted, acc);
                float ch = (bottom - top - 2 * gap);
                clock(c, rx, top, cw, ch * 0.3f, crad, text, muted);
                weather(c, rx, top + ch * 0.3f + gap, cw, ch * 0.37f, crad, text, muted);
                maint(c, rx, top + ch * 0.67f + 2 * gap, cw, ch * 0.33f, crad, text, muted, acc);
                dock(c, pad, dockY, w - 2 * pad, dockH, text);
            } else {
                float dockH = u * 0.13f, dockY = h - pad - dockH;
                float mh = h * 0.40f, mapTop = top, rowY = mapTop + mh + gap;
                float pw = (w - 2 * pad) * 0.45f;
                map(c, pad, mapTop, w - 2 * pad, mh, crad, muted, acc);
                player(c, pad, mapTop, pw, mh, crad, text, muted, acc);
                float rowH = (dockY - gap - rowY - gap) * 0.55f, cw = (w - 2 * pad - gap) / 2f;
                clock(c, pad, rowY, cw, rowH, crad, text, muted);
                weather(c, pad + cw + gap, rowY, cw, rowH, crad, text, muted);
                maint(c, pad, rowY + rowH + gap, w - 2 * pad, dockY - gap - rowY - rowH - gap, crad, text, muted, acc);
                dock(c, pad, dockY, w - 2 * pad, dockH, text);
            }
            if (selected) {
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f * s);
                p.setColor(Main.RED);
                r.set(1.25f * s, 1.25f * s, w - 1.25f * s, h - 1.25f * s);
                c.drawRoundRect(r, rad, rad, p);
            }
        }

        private void player(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted, int acc) {
            card(c, x, y, x + w, y + hh, rad);
            float a = Math.min(w * 0.62f, hh * 0.42f), ax = x + (w - a) / 2, ay = y + hh * 0.08f;
            rr(c, ax, ay, ax + a, ay + a, a * 0.1f, light ? 0xFFD9DCE1 : 0xFF34363C);
            float ly = ay + a + hh * 0.08f, lw = w * 0.5f;
            rr(c, x + (w - lw) / 2, ly, x + (w + lw) / 2, ly + hh * 0.025f, hh * 0.012f, Aurora.a(muted, 0.6f));
            ly += hh * 0.08f;
            p.setColor(text);
            for (int i = -1; i <= 1; i++) c.drawCircle(x + w / 2 + i * w * 0.22f, ly, (i == 0 ? 0.045f : 0.03f) * w, p);
            ly += hh * 0.09f;
            float bx0 = x + w * 0.14f, bx1 = x + w * 0.86f;
            rr(c, bx0, ly, bx1, ly + hh * 0.012f, hh * 0.006f, Aurora.a(muted, 0.4f));
            rr(c, bx0, ly, bx0 + (bx1 - bx0) * 0.55f, ly + hh * 0.012f, hh * 0.006f, acc);
            ly += hh * 0.07f;
            float tw = w * 0.4f;
            rr(c, x + (w - tw) / 2, ly, x + (w + tw) / 2, ly + hh * 0.035f, hh * 0.015f, Aurora.a(text, 0.85f));
        }

        private void map(Canvas c, float x, float y, float w, float hh, float rad, int muted, int acc) {
            card(c, x, y, x + w, y + hh, rad);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, s * 0.7f));
            p.setColor(Aurora.a(muted, 0.35f));
            c.save();
            c.clipRect(x, y, x + w, y + hh);
            for (int i = 1; i < 6; i++) {
                c.drawLine(x, y + hh * i / 6f, x + w, y + hh * (i / 6f + 0.08f), p);
                c.drawLine(x + w * i / 5f, y, x + w * (i / 5f - 0.1f), y + hh, p);
            }
            Path route = new Path();
            route.moveTo(x + w * 0.45f, y + hh * 0.86f);
            route.lineTo(x + w * 0.42f, y + hh * 0.6f);
            route.lineTo(x + w * 0.5f, y + hh * 0.42f);
            route.lineTo(x + w * 0.46f, y + hh * 0.3f);
            route.lineTo(x + w * 0.7f, y + hh * 0.22f);
            p.setStrokeWidth(Math.max(1.5f, u() * 0.012f));
            p.setColor(acc);
            c.drawPath(route, p);
            c.restore();
            p.setStyle(Paint.Style.FILL);
            rr(c, x + w * 0.08f, y + hh * 0.06f, x + w * 0.55f, y + hh * 0.06f + u() * 0.03f, u() * 0.01f, Aurora.a(light ? 0xFF15171B : 0xFFF2F3F5, 0.8f));
        }

        private float u() { return portrait ? getWidth() : getHeight(); }

        private void clock(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted) {
            card(c, x, y, x + w, y + hh, rad);
            float th = hh * 0.38f, tw = w * 0.62f;
            rr(c, x + w * 0.09f, y + hh * 0.2f, x + w * 0.09f + tw, y + hh * 0.2f + th, th * 0.18f, Aurora.a(text, 0.9f));
            rr(c, x + w * 0.09f, y + hh * 0.68f, x + w * 0.5f, y + hh * 0.68f + hh * 0.1f, hh * 0.05f, Aurora.a(muted, 0.7f));
        }

        private void weather(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted) {
            card(c, x, y, x + w, y + hh, rad);
            rr(c, x + w * 0.09f, y + hh * 0.4f, x + w * 0.38f, y + hh * 0.62f, hh * 0.05f, Aurora.a(text, 0.9f));
            p.setColor(Aurora.a(text, 0.85f));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, s * 0.8f));
            c.drawCircle(x + w * 0.72f, y + hh * 0.28f, hh * 0.12f, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 3; i++)
                rr(c, x + w * 0.58f, y + hh * (0.52f + i * 0.13f), x + w * 0.88f, y + hh * (0.52f + i * 0.13f) + hh * 0.05f, hh * 0.025f, Aurora.a(muted, 0.6f));
        }

        private void maint(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted, int acc) {
            card(c, x, y, x + w, y + hh, rad);
            float b = hh * 0.34f, bx = x + w * 0.07f, by = y + hh * 0.12f;
            rr(c, bx, by, bx + b, by + b, b * 0.3f, tile(light));
            rr(c, bx + b * 1.3f, by + b * 0.15f, x + w * 0.7f, by + b * 0.4f, b * 0.12f, Aurora.a(text, 0.8f));
            rr(c, bx + b * 1.3f, by + b * 0.6f, x + w * 0.55f, by + b * 0.8f, b * 0.1f, acc);
            float sz = Math.min(hh * 0.36f, (w - w * 0.14f) / 3.6f), sy = y + hh - hh * 0.1f - sz;
            for (int i = 0; i < 3; i++) {
                float sx = x + w * 0.07f + i * (sz + (w * 0.86f - 3 * sz) / 2f);
                rr(c, sx, sy, sx + sz, sy + sz, sz * 0.25f, tile(light));
                p.setColor(Aurora.a(muted, 0.9f));
                p.setStrokeWidth(Math.max(1f, s * 0.8f));
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(sx + sz * 0.35f, sy + sz / 2, sx + sz * 0.65f, sy + sz / 2, p);
                c.drawLine(sx + sz / 2, sy + sz * 0.35f, sx + sz / 2, sy + sz * 0.65f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        private void dock(Canvas c, float x, float y, float w, float hh, int text) {
            int n = 6;
            float d = hh * 0.42f;
            for (int i = 0; i < n; i++) {
                float cx = x + w * (i + 0.5f) / n, cy = y + hh * 0.42f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, d * 0.12f));
                p.setColor(Aurora.a(text, 0.85f));
                c.drawCircle(cx, cy, d * 0.4f, p);
                p.setStyle(Paint.Style.FILL);
                rr(c, cx - d * 0.5f, cy + d * 0.7f, cx + d * 0.5f, cy + d * 0.85f, d * 0.08f, Aurora.a(text, 0.4f));
            }
        }
    }
}
