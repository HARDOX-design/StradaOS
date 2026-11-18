package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Tema premium «Aurora» (2.24, Bloque 14, fase 2). Diseño propio de StradaOS: fondo oscuro o claro con un arco de
 * color difuminado (el acento y un violeta) y grano fino, tarjetas translúcidas y barra tipo píldora.
 *
 * Todo se dibuja en código y una sola vez: el fondo se pinta a media resolución en un Bitmap (con el grano ya puesto)
 * y se estira a la pantalla con filtro, así en el Picanto (API 17) no cuesta nada al dibujar cada cuadro. No hay
 * desenfoques en tiempo real: el «difuminado» sale de degradados radiales.
 */
final class Aurora {
    private Aurora() { }

    static final String ID = "aurora";
    static final int VIOLET = 0xFF7C5CFF;

    // Colores propios del tema (el resto de StradaOS sigue con su paleta normal: Ajustes, ventanas, etc.)
    static int base(boolean light) { return light ? 0xFFE6DFEE : 0xFF110E17; }
    static int glass(boolean light) { return light ? 0x8CFFFFFF : 0x1FFFFFFF; }
    static int glassEdge(boolean light) { return light ? 0xCCFFFFFF : 0x26FFFFFF; }
    // claro: píldora gris translúcida (deja ver el fondo) con íconos oscuros; oscuro: casi negra con íconos claros
    static int pill(boolean light) { return light ? 0x529C93AD : 0xE01B1724; }
    static int pillEdge(boolean light) { return light ? 0xA6FFFFFF : 0x24FFFFFF; }
    static int pillIcon(boolean light) { return light ? 0xFF2A2533 : 0xFFEDEAF2; }
    /** Opacidad de las teselas del mapa: se alcanza a ver el fondo sin perder la lectura del mapa. */
    static float mapAlpha(boolean light) { return light ? 0.70f : 0.72f; }
    /** Botones dentro de las tarjetas translúcidas (anterior/siguiente, fondo de la barrita del mantenimiento). */
    static int soft(boolean light) { return light ? 0x14000000 : 0x1AFFFFFF; }

    private static Bitmap cache;
    private static String cacheKey;

    /** Fondo para una pantalla de w×h px. Se guarda: al volver a armar el inicio no se repinta. */
    static Bitmap background(int w, int h, boolean light, int accent) {
        int bw = Math.max(64, w / 2), bh = Math.max(64, h / 2);
        String key = bw + "x" + bh + ":" + light + ":" + accent;
        if (cache != null && !cache.isRecycled() && key.equals(cacheKey)) return cache;
        Bitmap b;
        try {
            b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (Throwable e) {   // poca memoria: un color liso
            b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            b.eraseColor(base(light));
            return b;
        }
        Canvas c = new Canvas(b);
        paint(c, bw, bh, light, accent);
        grain(b, light);
        cache = b;
        cacheKey = key;
        return b;
    }

    static int a(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, (int) (alpha * 255))) << 24);
    }

    /** Fondo: base, resplandor del acento arriba a la derecha y el arco (anillo difuminado) que cruza la pantalla. */
    static void paint(Canvas c, float w, float h, boolean light, int accent) {
        c.drawColor(base(light));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boolean tall = h > w * 1.05f;
        float big = Math.max(w, h);
        glow(c, p, w * 0.86f, -h * 0.08f, big * (tall ? 0.62f : 0.52f), accent, light ? 0.36f : 0.26f);
        // claro: un resplandor violeta grande detrás de la cabecera (como la referencia), para que el fondo se note
        if (light) glow(c, p, w * (tall ? 0.45f : 0.55f), h * (tall ? 0.12f : 0.18f), big * (tall ? 0.55f : 0.42f), VIOLET, 0.42f);
        // arco: centro fuera de la pantalla, abajo a la izquierda; el anillo pasa por detrás de las tarjetas y el mapa
        float cx = tall ? -w * 0.35f : w * 0.18f, cy = tall ? h * 0.92f : h * 1.32f, R = tall ? w * 1.55f : big * 0.95f;
        p.setShader(new RadialGradient(cx, cy, R,
            new int[]{0, 0, a(VIOLET, light ? 0.46f : 0.50f), a(accent, light ? 0.32f : 0.26f), 0},
            new float[]{0f, 0.58f, 0.74f, 0.86f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        glow(c, p, w * 0.04f, h * 0.98f, big * 0.42f, VIOLET, light ? 0.32f : 0.30f);
        p.setShader(null);
    }

    private static void glow(Canvas c, Paint p, float x, float y, float r, int color, float alpha) {
        p.setShader(new RadialGradient(x, y, r, new int[]{a(color, alpha), a(color, alpha * 0.45f), 0},
            new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, c.getWidth(), c.getHeight(), p);
    }

    /** Grano: ruido monocromo de ±amp/2 por píxel (xorshift: rápido y siempre igual). */
    static void grain(Bitmap b, boolean light) { grain(b, light ? 12 : 16); }

    /** Grano con la intensidad dada (Faro usa uno más fino). */
    static void grain(Bitmap b, int amp) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w];
        int seed = 0x2545F491;
        for (int y = 0; y < h; y++) {
            b.getPixels(px, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
                int n = (((seed >>> 24) & 255) * amp >> 8) - amp / 2;
                int c = px[x];
                int r = clamp(((c >> 16) & 255) + n), g = clamp(((c >> 8) & 255) + n), bl = clamp((c & 255) + n);
                px[x] = 0xFF000000 | (r << 16) | (g << 8) | bl;
            }
            b.setPixels(px, 0, w, 0, y, w, 1);
        }
    }

    private static int clamp(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }

    /** Botón «Solo mapa»: cuatro esquinas hacia afuera (agrandar el mapa) o hacia adentro (volver a mostrar todo). */
    static final class Corners extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        boolean inward;

        Corners(Context c, int color, float stroke) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(color);
            p.setStrokeWidth(stroke);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), m = Math.min(w, h);
            float l = (w - m * 0.5f) / 2, t = (h - m * 0.5f) / 2, r = l + m * 0.5f, b = t + m * 0.5f, k = m * 0.17f;
            path.reset();
            if (!inward) {
                corner(l, t + k, l, t, l + k, t); corner(r - k, t, r, t, r, t + k);
                corner(r, b - k, r, b, r - k, b); corner(l + k, b, l, b, l, b - k);
            } else {
                float cx = (l + r) / 2, cy = (t + b) / 2, g = m * 0.06f;
                corner(cx - g - k, cy - g, cx - g, cy - g, cx - g, cy - g - k);
                corner(cx + g, cy - g - k, cx + g, cy - g, cx + g + k, cy - g);
                corner(cx + g + k, cy + g, cx + g, cy + g, cx + g, cy + g + k);
                corner(cx - g, cy + g + k, cx - g, cy + g, cx - g - k, cy + g);
            }
            c.drawPath(path, p);
        }

        private void corner(float x0, float y0, float x1, float y1, float x2, float y2) {
            path.moveTo(x0, y0); path.lineTo(x1, y1); path.lineTo(x2, y2);
        }
    }

    /**
     * Miniatura del tema para la galería, en horizontal o vertical y en su versión clara u oscura (no depende del
     * modo actual: se muestran las dos). Sigue las mismas proporciones que buildPremium() en Main.
     */
    static final class Thumb extends View {
        final boolean portrait, light;
        boolean selected;
        private final float s;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();
        private Bitmap bg;

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

        private void glassCard(Canvas c, float l, float t, float rt, float b, float rad) {
            rr(c, l, t, rt, b, rad, glass(light));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, s * 0.8f));
            p.setColor(glassEdge(light));
            r.set(l, t, rt, b);
            c.drawRoundRect(r, rad, rad, p);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float u = portrait ? w : h, rad = u * 0.06f;
            if (bg == null || bg.getWidth() != w || bg.getHeight() != h) {
                try {
                    bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    paint(new Canvas(bg), w, h, light, Main.RED);
                    grain(bg, light);
                } catch (Throwable e) { bg = null; }
            }
            // esquinas redondas sin clipPath (en Android 4.1/4.2 con aceleración por GPU no funciona)
            r.set(0, 0, w, h);
            if (bg != null) {
                p.setStyle(Paint.Style.FILL);
                p.setShader(new android.graphics.BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, rad, rad, p);
                p.setShader(null);
            } else rr(c, 0, 0, w, h, rad, base(light));
            int text = light ? 0xFF1B1722 : 0xFFF2F3F5, muted = light ? 0xFF7A7389 : 0xFF9A93A8;
            float pad = w * (portrait ? 0.035f : 0.022f), gap = u * 0.02f, cw = w - 2 * pad, crad = u * 0.04f;
            // barra de arriba: saludo, íconos y botones
            float hy = h * (portrait ? 0.022f : 0.035f), hh = u * 0.035f;
            rr(c, pad, hy, pad + w * (portrait ? 0.24f : 0.14f), hy + hh, hh / 2, a(muted, 0.85f));
            p.setColor(a(text, 0.35f));
            c.drawCircle(w - pad - hh * 0.6f, hy + hh / 2, hh * 0.75f, p);
            c.drawCircle(w - pad - hh * 2.4f, hy + hh / 2, hh * 0.75f, p);
            float top = hy + hh + gap * 1.6f;
            float pillH = portrait ? w * 0.09f : h * 0.11f, pillY = h - pad - pillH;
            float mapY, mapB = pillY - gap;
            if (!portrait) {
                float hhd = h * 0.25f, wW = cw * 0.184f;
                float wx = pad + cw - 3 * wW - 2 * gap;
                clock(c, pad + cw * 0.02f, top, wx - gap - pad, hhd, text);
                for (int i = 0; i < 3; i++) widget(c, wx + i * (wW + gap), top, wW, hhd, crad, i, text, muted);
                mapY = top + hhd + gap;
            } else {
                float clockH = h * 0.075f;
                clock(c, pad + cw * 0.03f, top, cw * 0.6f, clockH, text);
                float wy = top + clockH + gap, wh = h * 0.135f, wW = (cw - 2 * gap) / 3;
                for (int i = 0; i < 3; i++) widget(c, pad + i * (wW + gap), wy, wW, wh, crad, i, text, muted);
                mapY = wy + wh + gap;
            }
            map(c, pad, mapY, pad + cw, mapB, crad, u);
            // barra tipo píldora con íconos sin círculos
            float pw = cw * (portrait ? 0.78f : 0.52f), px = (w - pw) / 2;
            rr(c, px, pillY, px + pw, pillY + pillH, pillH / 2, pill(light));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, s * 0.8f));
            p.setColor(pillEdge(light));
            r.set(px, pillY, px + pw, pillY + pillH);
            c.drawRoundRect(r, pillH / 2, pillH / 2, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(pillIcon(light), 0.9f));
            for (int i = 0; i < 7; i++) {
                float ix = px + pw * (i + 0.5f) / 7f;
                c.drawCircle(ix, pillY + pillH / 2, pillH * 0.16f, p);
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

        /** Hora grande y la fecha en el color de acento. */
        private void clock(Canvas c, float x, float y, float w, float hh, int text) {
            float th = Math.min(hh * 0.30f, w * 0.16f), ty = y + hh * 0.5f - th * 0.8f;
            rr(c, x, ty, x + w * 0.62f, ty + th, th * 0.3f, a(text, 0.92f));
            rr(c, x, ty + th * 1.35f, x + w * 0.45f, ty + th * 1.75f, th * 0.2f, a(Main.RED, 0.9f));
        }

        /** 0 música (carátula y controles), 1 vehículo (velocidad y mantenimiento), 2 clima. */
        private void widget(Canvas c, float x, float y, float w, float hh, float rad, int kind, int text, int muted) {
            glassCard(c, x, y, x + w, y + hh, rad);
            float m = w * 0.1f;
            rr(c, x + m, y + m, x + m + w * 0.32f, y + m + hh * 0.06f, hh * 0.03f, a(muted, 0.8f));
            if (kind == 0) {
                float as = Math.min(w * 0.36f, hh * 0.38f);
                p.setShader(null);
                rr(c, x + m, y + hh * 0.25f, x + m + as, y + hh * 0.25f + as, as * 0.18f, a(Main.RED, 0.85f));
                rr(c, x + m * 1.6f + as, y + hh * 0.30f, x + w - m, y + hh * 0.30f + hh * 0.07f, hh * 0.035f, a(text, 0.85f));
                rr(c, x + m * 1.6f + as, y + hh * 0.43f, x + w - m * 2, y + hh * 0.43f + hh * 0.05f, hh * 0.025f, a(muted, 0.8f));
                float cy = y + hh * 0.80f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(a(text, 0.7f));
                c.drawCircle(x + w * 0.28f, cy, hh * 0.045f, p);
                c.drawCircle(x + w * 0.72f, cy, hh * 0.045f, p);
                p.setColor(Main.RED);
                c.drawCircle(x + w * 0.5f, cy, hh * 0.08f, p);
            } else if (kind == 1) {
                rr(c, x + m, y + hh * 0.30f, x + w * 0.55f, y + hh * 0.30f + hh * 0.2f, hh * 0.04f, a(text, 0.9f));
                rr(c, x + m, y + hh * 0.68f, x + w - m, y + hh * 0.86f, hh * 0.06f, soft(light));
                rr(c, x + m * 1.4f, y + hh * 0.80f, x + w * 0.6f, y + hh * 0.83f, hh * 0.015f, 0xFF2DBE6C);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(a(text, 0.75f));
                c.drawCircle(x + w * 0.5f, y + hh * 0.42f, Math.min(w, hh) * 0.13f, p);
                rr(c, x + w * 0.3f, y + hh * 0.66f, x + w * 0.7f, y + hh * 0.66f + hh * 0.08f, hh * 0.04f, a(text, 0.9f));
                rr(c, x + w * 0.25f, y + hh * 0.80f, x + w * 0.75f, y + hh * 0.80f + hh * 0.05f, hh * 0.025f, a(muted, 0.8f));
            }
        }

        private void map(Canvas c, float l, float t, float rt, float b, float crad, float u) {
            int mapBg = light ? 0xD6EDE9F1 : 0xDB1F1B29, road = light ? 0xFFFFFFFF : 0xFF3A3448;
            rr(c, l, t, rt, b, crad, mapBg);
            float mw = rt - l, mh = b - t;
            c.save();
            c.clipRect(l, t, rt, b);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, u * 0.016f));
            p.setColor(road);
            for (int i = 1; i < 4; i++) c.drawLine(l, t + mh * i / 4f, rt, t + mh * i / 4f - mh * 0.15f, p);
            for (int i = 1; i < 6; i++) c.drawLine(l + mw * i / 6f, t, l + mw * i / 6f + mw * 0.05f, b, p);
            p.setColor(a(Main.RED, 0.75f));
            p.setStrokeWidth(Math.max(2f, u * 0.026f));
            c.drawLine(l + mw * 0.25f, b, l + mw * 0.7f, t, p);
            c.restore();
            float ax = l + mw * 0.47f, ay = t + mh * 0.55f, as = u * 0.04f;
            path.reset();
            path.moveTo(ax, ay - as);
            path.lineTo(ax + as * 0.75f, ay + as);
            path.lineTo(ax, ay + as * 0.45f);
            path.lineTo(ax - as * 0.75f, ay + as);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Main.RED);
            c.drawPath(path, p);
            // botón de «solo mapa» y del navegador
            p.setColor(light ? 0xE6FFFFFF : 0xE02A2533);
            float m = u * 0.03f, br = u * 0.035f;
            c.drawCircle(rt - m - br, b - m - br, br, p);
        }
    }
}
