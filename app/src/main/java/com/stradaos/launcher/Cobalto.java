package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Tema premium «Cobalto» (2.24, Bloque 14). Diseño propio de StradaOS en azul noche: tres columnas en horizontal
 * (mapa que se difumina hasta la tarjeta del reproductor, reproductor con la carátula redonda y tarjeta del vehículo
 * con el velocímetro arriba, el auto en el medio y el mantenimiento abajo) y la barra de apps abajo. En vertical: el
 * mapa arriba (se difumina hacia las tarjetas), reproductor y vehículo lado a lado y la barra.
 *
 * Igual que Aurora: el fondo se pinta una sola vez a media resolución (Aurora.background lo guarda), sin desenfoques
 * en tiempo real. Usa el resto de la maquinaria de Aurora (barra de arriba, «Solo mapa», transiciones).
 */
final class Cobalto {
    private Cobalto() { }

    static final String ID = "cobalto";
    static final int BLUE = 0xFF3D7BFF, ICE = 0xFF8FD3FF;

    static int base(boolean light) { return light ? 0xFFE6ECF6 : 0xFF060B18; }
    // tarjetas: vidrio azul con el borde de arriba un poco más claro (degradado vertical)
    static int glassTop(boolean light) { return light ? 0xD9FFFFFF : 0x6E2D4C88; }
    static int glassBottom(boolean light) { return light ? 0x99E4ECFA : 0x4A0F1E44; }
    static int glassEdge(boolean light) { return light ? 0xE6FFFFFF : 0x4D6E9BFF; }
    static int pill(boolean light) { return light ? 0x4D8FA3C7 : 0xE60A1328; }
    static int pillEdge(boolean light) { return light ? 0xB3FFFFFF : 0x4D6E9BFF; }
    static int pillIcon(boolean light) { return light ? 0xFF1C2740 : 0xFFE4ECFF; }
    static int soft(boolean light) { return light ? 0x14000000 : 0x1F8FB4FF; }
    // el mapa casi transparente: domina el color del tema y se leen las calles (como la referencia)
    static float mapAlpha(boolean light) { return light ? 0.50f : 0.45f; }

    /** Fondo: azul noche con un resplandor detrás de las tarjetas y un reflejo suave abajo, como un piso brillante. */
    static void paint(Canvas c, float w, float h, boolean light, int accent) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boolean tall = h > w * 1.05f;
        float big = Math.max(w, h);
        p.setShader(new LinearGradient(0, 0, 0, h,
            light ? new int[]{0xFFF1F5FC, 0xFFE2E9F5, 0xFFD5DFEF} : new int[]{0xFF0C1834, 0xFF070D1F, 0xFF04070F},
            new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        glow(c, p, w * (tall ? 0.5f : 0.68f), h * (tall ? 0.62f : 0.28f), big * 0.55f, BLUE, light ? 0.20f : 0.34f);
        glow(c, p, w * (tall ? 0.15f : 0.12f), h * (tall ? 0.18f : 0.30f), big * 0.40f, ICE, light ? 0.16f : 0.14f);
        // reflejo: una franja clara muy suave cerca de la barra de apps
        float ry = h * (tall ? 0.90f : 0.86f);
        p.setShader(new LinearGradient(0, ry - h * 0.08f, 0, ry + h * 0.08f,
            new int[]{0, Aurora.a(light ? 0xFFFFFFFF : ICE, light ? 0.55f : 0.10f), 0}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, ry - h * 0.08f, w, ry + h * 0.08f, p);
        p.setShader(null);
    }

    private static void glow(Canvas c, Paint p, float x, float y, float r, int color, float alpha) {
        p.setShader(new RadialGradient(x, y, r, new int[]{Aurora.a(color, alpha), Aurora.a(color, alpha * 0.4f), 0},
            new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, c.getWidth(), c.getHeight(), p);
    }

    /**
     * Forma de las tarjetas (como la referencia): el borde izquierdo inclinado (baja hacia la derecha, sk) con las
     * esquinas de ese lado bien redondeadas; el lado derecho recto, casi sin redondear (se difumina y no lleva línea).
     */
    static Path shape(float l, float t, float r, float b, float sk, float rad) {
        float[] xs = {l, r, r, l + sk}, ys = {t, t, b, b}, rr = {rad, rad * 0.3f, rad * 0.3f, rad};
        Path p = new Path();
        for (int i = 0; i < 4; i++) {
            float px = xs[(i + 3) % 4], py = ys[(i + 3) % 4], cx = xs[i], cy = ys[i], nx = xs[(i + 1) % 4], ny = ys[(i + 1) % 4];
            float d1 = (float) Math.hypot(cx - px, cy - py), d2 = (float) Math.hypot(nx - cx, ny - cy);
            float k1 = Math.min(rr[i], d1 / 2) / d1, k2 = Math.min(rr[i], d2 / 2) / d2;
            float ax = cx + (px - cx) * k1, ay = cy + (py - cy) * k1, bx = cx + (nx - cx) * k2, by = cy + (ny - cy) * k2;
            if (i == 0) p.moveTo(ax, ay); else p.lineTo(ax, ay);
            p.quadTo(cx, cy, bx, by);
        }
        p.close();
        return p;
    }

    /** Solo el lado inclinado (con sus dos esquinas y un tramo de arriba y de abajo): ahí va la línea de la tarjeta. */
    static Path edge(float l, float t, float r, float b, float sk, float rad) {
        float w = r - l, len = (float) Math.hypot(sk, b - t), k = Math.min(rad, len / 2) / len;
        Path p = new Path();
        p.moveTo(l + w * 0.6f, t);
        p.lineTo(l + rad, t);
        p.quadTo(l, t, l + sk * k, t + (b - t) * k);
        p.lineTo(l + sk - sk * k, b - (b - t) * k);
        p.quadTo(l + sk, b, l + sk + rad, b);
        p.lineTo(l + sk + w * 0.6f, b);
        return p;
    }

    /** Fondo de la tarjeta: vidrio azul que se difumina hacia la derecha, línea solo en el lado inclinado y, en oscuro,
     *  el brillo de luz en las dos esquinas redondeadas. */
    static final class SkewCard extends android.graphics.drawable.Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            glow = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean light;
        private final float rad, sk, stroke;
        private Path path, side;
        private float g1x, g1y, g2x, g2y, gr;
        private Shader g1, g2, h1, h2;

        SkewCard(boolean light, float rad, float sk, float stroke) {
            this.light = light;
            this.rad = rad;
            this.sk = sk;
            this.stroke = Math.max(1f, stroke);
            for (Paint p : new Paint[]{line, glow, halo}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
            }
            line.setStrokeWidth(this.stroke);
            glow.setStrokeWidth(this.stroke * 2f);
            halo.setStrokeWidth(this.stroke * 8f);
        }

        @Override protected void onBoundsChange(android.graphics.Rect b) {
            float e = stroke * 4, l = b.left + e, t = b.top + e, r = b.right, bt = b.bottom - e, w = r - l;
            path = shape(l, t, r, bt, sk, rad);
            side = edge(l, t, r, bt, sk, rad);
            int top = glassTop(light), bot = glassBottom(light);
            fill.setShader(new LinearGradient(l, t, r, bt * 0.4f + t * 0.6f,
                new int[]{top, bot, bot & 0x00FFFFFF | ((((bot >>> 24) * 30) / 100) << 24), bot & 0x00FFFFFF},
                new float[]{0f, 0.55f, 0.85f, 1f}, Shader.TileMode.CLAMP));
            int ec = glassEdge(light);
            line.setShader(new LinearGradient(l + sk, 0, l + w * 0.62f, 0, ec, ec & 0x00FFFFFF, Shader.TileMode.CLAMP));
            // brillo en las esquinas redondeadas (arriba a la izquierda y abajo, donde termina el lado inclinado)
            g1x = l + rad * 0.3f; g1y = t + rad * 0.3f;
            g2x = l + sk + rad * 0.3f; g2y = bt - rad * 0.3f;
            gr = rad * 2.6f;
            int hot = light ? 0xCCFFFFFF : 0xF2D6ECFF, mid = light ? 0x40FFFFFF : 0x807FB6FF, soft = light ? 0x30FFFFFF : 0x557FB6FF;
            g1 = new android.graphics.RadialGradient(g1x, g1y, gr, new int[]{hot, mid, 0}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            g2 = new android.graphics.RadialGradient(g2x, g2y, gr, new int[]{hot, mid, 0}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            h1 = new android.graphics.RadialGradient(g1x, g1y, gr * 0.8f, new int[]{soft, 0}, null, Shader.TileMode.CLAMP);
            h2 = new android.graphics.RadialGradient(g2x, g2y, gr * 0.8f, new int[]{soft, 0}, null, Shader.TileMode.CLAMP);
        }

        @Override public void draw(Canvas c) {
            if (path == null) return;
            c.drawPath(path, fill);
            c.drawPath(side, line);
            if (!light) {   // en oscuro: la luz que se ve en las puntas redondeadas
                halo.setShader(h1); c.drawPath(side, halo);
                halo.setShader(h2); c.drawPath(side, halo);
                glow.setShader(g1); c.drawPath(side, glow);
                glow.setShader(g2); c.drawPath(side, glow);
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** Recuadro donde está el dibujo en una foto con fondo transparente (para que el auto llegue de verdad al borde). */
    static android.graphics.Rect trim(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), minX = w, minY = h, maxX = -1, maxY = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y += 2) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x += 2) {
                if ((row[x] >>> 24) > 24) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return new android.graphics.Rect(0, 0, w, h);
        return new android.graphics.Rect(minX, minY, Math.min(w, maxX + 2), Math.min(h, maxY + 2));
    }

    /**
     * La foto recortada (fondo transparente) de Tu vehículo, completa y centrada en su recuadro, como en el inicio de
     * siempre, con una sombra suave en el piso. Se quita el margen transparente de la foto para que el auto llene el
     * recuadro sin cortarse. Se compone una vez en un Bitmap.
     */
    static final class CarFit extends View {
        private final Bitmap car;
        private Bitmap out;

        CarFit(Context c, Bitmap car) {
            super(c);
            this.car = car;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (out != null) { out.recycle(); out = null; }
            if (w <= 0 || h <= 0 || car == null) return;
            try { out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); } catch (Throwable e) { return; }
            Canvas c = new Canvas(out);
            android.graphics.Rect src = trim(car);
            float k = Math.min(w * 0.96f / Math.max(1, src.width()), h * 0.90f / Math.max(1, src.height()));
            float cw = src.width() * k, ch = src.height() * k;
            float left = (w - cw) / 2, top = (h - ch) / 2 - h * 0.02f;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            float sy = top + ch * 0.97f;
            p.setShader(new RadialGradient(w / 2f, sy, cw * 0.5f, new int[]{0x80000000, 0x26000000, 0}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.save();
            c.scale(1f, 0.10f, w / 2f, sy);
            c.drawCircle(w / 2f, sy, cw * 0.5f, p);
            c.restore();
            p.setShader(null);
            c.drawBitmap(car, src, new RectF(left, top, left + cw, top + ch), p);
        }

        @Override protected void onDraw(Canvas c) {
            if (out != null) c.drawBitmap(out, 0, 0, null);
        }
    }

    /** Miniatura para la galería (mismas proporciones que buildCobalto en Main). */
    static final class Thumb extends View {
        final boolean portrait, light;
        boolean selected;
        private final float s;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
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

        private void card(Canvas c, float l, float t, float rt, float b, float rad) {
            float sk = portrait ? 0 : Math.min((rt - l) * 0.22f, (b - t) * 0.15f);   // en vertical, rectas
            Path path = shape(l, t, rt, b, sk, rad * 1.3f);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(l, t, rt, t, glassTop(light), glassBottom(light) & 0x30FFFFFF, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, s * 0.8f));
            p.setShader(new LinearGradient(l + sk, 0, l + (rt - l) * 0.6f, 0, glassEdge(light), 0, Shader.TileMode.CLAMP));
            c.drawPath(edge(l, t, rt, b, sk, rad * 1.3f), p);
            p.setShader(null);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float u = portrait ? w : h, rad = u * 0.06f, crad = u * 0.04f;
            if (bg == null || bg.getWidth() != w || bg.getHeight() != h) {
                try {
                    bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    paint(new Canvas(bg), w, h, light, Main.RED);
                    Aurora.grain(bg, light);
                } catch (Throwable e) { bg = null; }
            }
            r.set(0, 0, w, h);
            if (bg != null) {
                p.setStyle(Paint.Style.FILL);
                p.setShader(new android.graphics.BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, rad, rad, p);
                p.setShader(null);
            } else rr(c, 0, 0, w, h, rad, base(light));
            int text = light ? 0xFF16203A : 0xFFEAF0FF, muted = light ? 0xFF6E7A93 : 0xFF8494B5;
            float pad = w * (portrait ? 0.035f : 0.022f), gap = u * 0.02f, cw = w - 2 * pad;
            float hy = h * (portrait ? 0.022f : 0.035f), hh = u * 0.035f;
            rr(c, pad, hy, pad + w * (portrait ? 0.24f : 0.14f), hy + hh, hh / 2, Aurora.a(muted, 0.85f));
            p.setColor(Aurora.a(text, 0.35f));
            c.drawCircle(w - pad - hh * 0.6f, hy + hh / 2, hh * 0.75f, p);
            c.drawCircle(w - pad - hh * 2.4f, hy + hh / 2, hh * 0.75f, p);
            float top = hy + hh + gap * 1.6f;
            float pillH = portrait ? w * 0.09f : h * 0.11f, pillY = h - pad - pillH, bottom = pillY - gap;
            float tempH = u * 0.07f;
            if (!portrait) {
                float vw = cw * 0.26f, pw = cw * 0.26f;
                float vx = pad + cw - vw, px = vx - pw;
                temp(c, pad, top, px - pad, tempH, text, muted);
                map(c, pad, top + tempH + gap, px - gap * 0.3f, bottom, crad, u, true);
                player(c, px, top, pw, bottom - top, crad, text, muted);
                vehicle(c, vx, top, vw, bottom - top, crad, text, muted);
            } else {
                float cardsH = h * 0.32f, cy = bottom - cardsH, cwh = cw / 2;
                temp(c, pad, top, cw, tempH, text, muted);
                map(c, pad, top + tempH + gap, pad + cw, cy - gap * 0.3f, crad, u, false);
                player(c, pad, cy, cwh, cardsH, crad, text, muted);
                vehicle(c, pad + cwh, cy, cwh, cardsH, crad, text, muted);
            }
            float pw2 = cw * (portrait ? 0.78f : 0.52f), px2 = (w - pw2) / 2;
            rr(c, px2, pillY, px2 + pw2, pillY + pillH, pillH / 2, pill(light));
            p.setColor(Aurora.a(pillIcon(light), 0.9f));
            for (int i = 0; i < 7; i++) c.drawCircle(px2 + pw2 * (i + 0.5f) / 7f, pillY + pillH / 2, pillH * 0.16f, p);
            if (selected) {
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f * s);
                p.setColor(Main.RED);
                r.set(1.25f * s, 1.25f * s, w - 1.25f * s, h - 1.25f * s);
                c.drawRoundRect(r, rad, rad, p);
            }
        }

        private void temp(Canvas c, float x, float y, float w, float hh, int text, int muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Aurora.a(text, 0.75f));
            c.drawCircle(x + hh * 0.45f, y + hh / 2, hh * 0.28f, p);
            rr(c, x + hh, y + hh * 0.22f, x + hh + w * 0.12f, y + hh * 0.78f, hh * 0.12f, Aurora.a(text, 0.9f));
            rr(c, x + hh + w * 0.15f, y + hh * 0.38f, x + hh + w * 0.38f, y + hh * 0.62f, hh * 0.1f, Aurora.a(muted, 0.8f));
        }

        private void map(Canvas c, float l, float t, float rt, float b, float crad, float u, boolean fadeRight) {
            int road = light ? 0xCCFFFFFF : 0x804E6A9E;
            float mw = rt - l, mh = b - t;
            c.save();
            c.clipRect(l, t, rt, b);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, u * 0.016f));
            p.setColor(road);
            for (int i = 1; i < 4; i++) c.drawLine(l, t + mh * i / 4f, rt, t + mh * i / 4f - mh * 0.15f, p);
            for (int i = 1; i < 6; i++) c.drawLine(l + mw * i / 6f, t, l + mw * i / 6f + mw * 0.05f, b, p);
            p.setColor(Aurora.a(ICE, 0.9f));
            p.setStrokeWidth(Math.max(2f, u * 0.026f));
            c.drawLine(l + mw * 0.2f, b, l + mw * 0.55f, t + mh * 0.3f, p);
            c.restore();
            // difuminado hacia la tarjeta del reproductor (o hacia las tarjetas de abajo en vertical)
            p.setStyle(Paint.Style.FILL);
            int bc = base(light);
            if (fadeRight) {
                float fx = rt - mw * 0.5f;
                p.setShader(new LinearGradient(fx, 0, rt, 0, bc & 0x00FFFFFF, bc, Shader.TileMode.CLAMP));
                c.drawRect(fx, t, rt + 1, b, p);
            } else {
                float fy = b - mh * 0.4f;
                p.setShader(new LinearGradient(0, fy, 0, b, bc & 0x00FFFFFF, bc, Shader.TileMode.CLAMP));
                c.drawRect(l, fy, rt, b + 1, p);
            }
            p.setShader(null);
        }

        private void player(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted) {
            card(c, x, y, x + w, y + hh, rad);
            float d = Math.min(w * 0.55f, hh * 0.42f), cx = x + w / 2, cy = y + hh * (portrait ? 0.34f : 0.30f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Aurora.a(Main.RED, 0.85f));
            c.drawCircle(cx, cy, d / 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, d * 0.05f));
            p.setColor(Aurora.a(ICE, 0.7f));
            c.drawCircle(cx, cy, d / 2 + d * 0.09f, p);
            rr(c, cx - w * 0.28f, y + hh * 0.60f, cx + w * 0.28f, y + hh * 0.60f + hh * 0.045f, hh * 0.02f, Aurora.a(text, 0.9f));
            rr(c, cx - w * 0.18f, y + hh * 0.68f, cx + w * 0.18f, y + hh * 0.68f + hh * 0.03f, hh * 0.015f, Aurora.a(muted, 0.8f));
            p.setStyle(Paint.Style.FILL);
            float by = y + hh * 0.84f;
            p.setColor(Aurora.a(text, 0.7f));
            c.drawCircle(cx - w * 0.25f, by, hh * 0.03f, p);
            c.drawCircle(cx + w * 0.25f, by, hh * 0.03f, p);
            p.setColor(Main.RED);
            c.drawCircle(cx, by, hh * 0.055f, p);
        }

        private void vehicle(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted) {
            card(c, x, y, x + w, y + hh, rad);
            float cx = x + w / 2, gr = Math.min(w * 0.33f, hh * 0.2f), gy = y + hh * 0.08f + gr;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, gr * 0.12f));
            p.setColor(Aurora.a(text, 0.35f));
            r.set(cx - gr, gy - gr, cx + gr, gy + gr);
            c.drawArc(r, 140, 260, false, p);
            p.setColor(Main.RED);
            c.drawArc(r, 140, 90, false, p);
            // auto
            p.setStyle(Paint.Style.FILL);
            float cy0 = y + hh * 0.56f, cw0 = w * 0.78f;
            p.setColor(Aurora.a(light ? 0xFF7C88A0 : 0xFFAAB6CC, 0.9f));
            r.set(cx - cw0 / 2, cy0, cx + cw0 / 2, cy0 + hh * 0.07f);
            c.drawRoundRect(r, hh * 0.035f, hh * 0.035f, p);
            r.set(cx - cw0 * 0.15f, cy0 - hh * 0.05f, cx + cw0 * 0.22f, cy0 + hh * 0.01f);
            c.drawRoundRect(r, hh * 0.03f, hh * 0.03f, p);
            p.setColor(0xFF0B0F18);
            c.drawCircle(cx - cw0 * 0.3f, cy0 + hh * 0.075f, hh * 0.03f, p);
            c.drawCircle(cx + cw0 * 0.3f, cy0 + hh * 0.075f, hh * 0.03f, p);
            rr(c, x + w * 0.08f, y + hh * 0.80f, x + w * 0.92f, y + hh * 0.92f, hh * 0.05f, soft(light));
            rr(c, x + w * 0.12f, y + hh * 0.885f, x + w * 0.6f, y + hh * 0.9f, hh * 0.01f, 0xFF2DBE6C);
        }
    }
}
