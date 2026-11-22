package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Tema premium «Carbono» (Bloque 20, 2.33). Tarjetas limpias que flotan sobre un fondo carbón con micro-grano y una
 * luz suave que sube desde abajo a la izquierda (o gris perla con tarjetas blancas en el modo claro).
 *
 * Horizontal: barra de apps vertical tipo píldora a la izquierda; la tarjeta del vehículo con el velocímetro grande,
 * el carro visto desde arriba y los dos mantenimientos más cercanos; el mapa en su tarjeta con el reproductor y el
 * clima flotando abajo. Vertical: el vehículo arriba, el mapa, reproductor + clima y la barra abajo.
 *
 * Reusa las piezas de Faro (tarjeta, íconos de trazo, línea de tiempo, velocímetro, clima y mantenimientos): Faro
 * pregunta a {@link Prem#carb()} para devolver los colores de Carbono. Como Faro, el fondo y el carro con su sombra se
 * pintan UNA vez y quedan guardados (Picanto: Android 4.2).
 */
final class Carbono {
    private Carbono() { }

    static final String ID = "carbono";

    // ── colores (oscuro / claro) ──
    static int base(boolean l) { return l ? 0xFFE6E7EA : 0xFF121316; }
    static int glass(boolean l) { return l ? 0xF7FFFFFF : 0xEB1C1D21; }
    static int glassEdge(boolean l) { return l ? 0xFFFFFFFF : 0x21FFFFFF; }
    static int pill(boolean l) { return l ? 0xF2FFFFFF : 0xEB1A1B1F; }
    static int pillEdge(boolean l) { return l ? 0xFFFFFFFF : 0x1FFFFFFF; }
    static int pillIcon(boolean l) { return l ? 0xFF1C1E23 : 0xFFECEDEF; }
    static int soft(boolean l) { return l ? 0x0F000000 : 0x14FFFFFF; }
    static int tile(boolean l) { return l ? 0xFFF1F2F4 : 0xFF26282D; }
    static int track(boolean l) { return l ? 0xFFDCDFE3 : 0xFF2E3137; }
    /** Recuadro interior (mantenimientos, botón de reproducir en claro). */
    static int inset(boolean l) { return l ? 0xFFF4F5F7 : 0x0FFFFFFF; }
    static int insetEdge(boolean l) { return l ? 0x12000000 : 0x1AFFFFFF; }

    /** Avance del velocímetro en oscuro: plata (del gris al blanco). */
    static final int SILVER_DEEP = 0xFF6E747C, SILVER = 0xFFB9BEC6, SILVER_LIGHT = 0xFFF2F3F5;

    static final String[] CAR_COLORS = {"silver", "white", "black", "platinum", "graphite", "red", "blue", "beige"};
    static final String[] CAR_NAMES = {"Gris magnético", "Blanco perla", "Negro profundo", "Gris platino", "Grafito",
        "Rojo carrera", "Azul ártico", "Beige moderno"};
    static String car(String pref) {
        for (String c : CAR_COLORS) if (c.equals(pref)) return c;
        return "silver";
    }

    // ── fondo ──

    // Fondos hechos a mano con manchas difusas (sin imágenes, a la resolución exacta de la pantalla):
    // {x, y (fracción del ancho/alto), radio x, radio y (fracción del lado mayor), ángulo, color RGB, alfa}
    private static final float[][] DARK_BLOBS = {
        {0.55f, 0.06f, 0.70f, 0.16f, -8, 0x1E4D6B, 0.85f},
        {0.15f, 0.04f, 0.38f, 0.09f, -4, 0x3C8C9C, 0.55f},
        {0.98f, 0.56f, 0.55f, 0.13f, -12, 0x2B6A85, 0.75f},
        {0.62f, 0.34f, 0.55f, 0.11f, -10, 0x02050B, 0.85f},
        {0.72f, 0.92f, 0.60f, 0.22f, 0, 0x14344D, 0.65f},
        {0.06f, 0.56f, 0.46f, 0.40f, 0, 0x2F7F8F, 0.75f},
        {0.01f, 0.56f, 0.27f, 0.26f, 0, 0x9FD3D8, 0.95f},
        {-0.02f, 0.55f, 0.14f, 0.15f, 0, 0xDDE8E6, 0.85f},
        {0.30f, 0.80f, 0.40f, 0.10f, 8, 0x03070E, 0.6f},
    };
    /** Bandas curvas: {x0, y0, x1, y1 (control), x2, y2, ancho, color, alfa}. */
    private static final float[][] DARK_BANDS = {
        {0.25f, 0.0f, 0.6f, 0.22f, 1.02f, 0.12f, 0.07f, 0x2A6F86, 0.3f},
    };
    private static final float[][] LIGHT_BLOBS = {
        {0.22f, 0.12f, 0.5f, 0.3f, 0, 0x3E6E9C, 0.55f},
        {0.92f, 0.20f, 0.30f, 0.22f, 0, 0xA9CFE3, 0.6f},
    };
    private static final float[][] LIGHT_BANDS = {
        {0.36f, 1.02f, 0.72f, 0.52f, 1.02f, 0.78f, 0.12f, 0xBFE0EE, 0.35f},
        {0.64f, -0.02f, 0.42f, 0.58f, -0.02f, 0.45f, 0.16f, 0xF2F8FC, 0.42f},
        {-0.02f, 0.12f, 0.28f, 0.30f, 0.12f, 0.80f, 0.12f, 0xFFFFFF, 0.36f},
        {0.97f, 0.04f, 0.58f, 0.30f, 0.46f, 0.72f, 0.045f, 0x2E5C8A, 0.3f},
    };
    private static final float[][] LIGHT_BLOBS_TOP = {
        {0.80f, 0.72f, 0.05f, 0.16f, 15, 0x2E5A85, 0.6f},
        {0.04f, 0.96f, 0.30f, 0.14f, 0, 0xD9ECF6, 0.7f},
    };

    /** Mancha difusa: elipse con un degradado radial suave (el mismo perfil en el prototipo). */
    private static void blob(Canvas c, Paint p, float cx, float cy, float rx, float ry, float deg, int rgb, float al) {
        if (rx < 1 || ry < 1) return;
        int col = 0xFF000000 | rgb;
        p.setShader(new RadialGradient(0, 0, ry,
            new int[]{Aurora.a(col, al), Aurora.a(col, al * 0.62f), Aurora.a(col, al * 0.22f), Aurora.a(col, 0f)},
            new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.translate(cx, cy);
        c.rotate(deg);
        c.scale(rx / ry, 1f);
        c.drawCircle(0, 0, ry, p);
        c.restore();
    }

    private static void blobs(Canvas c, Paint p, float[][] list, float w, float h) {
        float u = Math.max(w, h);
        for (float[] b : list) blob(c, p, b[0] * w, b[1] * h, b[2] * u, b[3] * u, b[4], (int) b[5], b[6]);
    }

    /** Banda curva: manchas alargadas a lo largo de una curva cuadrática, orientadas con ella. */
    private static void bands(Canvas c, Paint p, float[][] list, float w, float h) {
        float u = Math.max(w, h);
        int n = 14;
        for (float[] b : list) {
            float x0 = b[0] * w, y0 = b[1] * h, x1 = b[2] * w, y1 = b[3] * h, x2 = b[4] * w, y2 = b[5] * h, wd = b[6] * u;
            for (int i = 0; i <= n; i++) {
                float t = i / (float) n, mt = 1 - t;
                float px = mt * mt * x0 + 2 * mt * t * x1 + t * t * x2, py = mt * mt * y0 + 2 * mt * t * y1 + t * t * y2;
                float tx = 2 * mt * (x1 - x0) + 2 * t * (x2 - x1), ty = 2 * mt * (y1 - y0) + 2 * t * (y2 - y1);
                float step = (float) Math.hypot(tx, ty) / n;
                blob(c, p, px, py, Math.max(step * 1.6f, wd), wd, (float) Math.toDegrees(Math.atan2(ty, tx)), (int) b[7], b[8]);
            }
        }
    }

    /**
     * Oscuro: azul noche casi negro con un resplandor turquesa a la izquierda y bandas azules difusas. Claro: azul
     * acero con remolinos blancos y celestes, como seda. Se pinta una vez al tamaño exacto de la pantalla.
     */
    static void paint(Canvas c, float w, float h, boolean light, int accent) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        c.drawColor(light ? 0xFF5F8DB8 : 0xFF050A14);
        if (light) {
            blobs(c, p, LIGHT_BLOBS, w, h);
            bands(c, p, LIGHT_BANDS, w, h);
            blobs(c, p, LIGHT_BLOBS_TOP, w, h);
        } else {
            float[][] first = {DARK_BLOBS[0], DARK_BLOBS[1], DARK_BLOBS[2]};
            blobs(c, p, first, w, h);
            bands(c, p, DARK_BANDS, w, h);
            float[][] rest = new float[DARK_BLOBS.length - 3][];
            System.arraycopy(DARK_BLOBS, 3, rest, 0, rest.length);
            blobs(c, p, rest, w, h);
        }
        p.setShader(null);
    }

    /**
     * Textura grumosa: ruido a media resolución estirado con filtro (grumos de ~2 px) + el grano fino de siempre.
     * Se pinta una sola vez con el fondo.
     */
    static void texture(Bitmap b, boolean light) {
        try {
            int w = b.getWidth(), h = b.getHeight(), nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
            int[] px = new int[nw * nh];
            java.util.Random r = new java.util.Random(7);
            int amp = light ? 3 : 4;
            for (int i = 0; i < px.length; i++) {
                int v = (int) (r.nextGaussian() * amp);
                v = Math.max(-24, Math.min(24, v));
                px[i] = v >= 0 ? (v << 24) | 0xFFFFFF : ((-v) << 24);
            }
            Bitmap n = Bitmap.createBitmap(px, nw, nh, Bitmap.Config.ARGB_8888);
            new Canvas(b).drawBitmap(n, null, new RectF(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
            n.recycle();
        } catch (Throwable ignored) { }
        Aurora.grain(b, grain(light));
    }

    /** Micro-grano: un poco más marcado que Faro para la textura del carbón. */
    static int grain(boolean light) { return light ? 5 : 9; }

    // ── carro en vista cenital ──

    static final float CAR_ASPECT = 520f / 1000f;         // ancho / alto de las imágenes
    /** Margen de la sombra (assets/carbono/shadow.png) respecto al carro, en fracción del ancho y del alto. */
    static final float SH_X = 93f / 520f, SH_Y = 93f / 1000f;

    /** Recuadro del carro dentro de una vista de vw×vh px: lo más grande posible con la sombra adentro. */
    static RectF carRect(float vw, float vh) {
        float ch = Math.min(vh / (1 + 2 * SH_Y * 0.75f), vw / (1 + 2 * SH_X * 0.75f) / CAR_ASPECT), cw = ch * CAR_ASPECT;
        float l = (vw - cw) / 2, t = (vh - ch) / 2;
        return new RectF(l, t, l + cw, t + ch);
    }

    private static final HashMap<String, Bitmap> carCache = new HashMap<String, Bitmap>();

    private static Bitmap asset(Context c, String path) {
        try {
            InputStream in = c.getAssets().open(path);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Achica de a mitades hasta quedar a menos del doble y luego al tamaño exacto. */
    private static Bitmap scaled(Bitmap src, int w, int h) {
        Bitmap cur = src;
        while (cur.getWidth() > w * 2 && cur.getHeight() > h * 2) {
            Bitmap nx = Bitmap.createScaledBitmap(cur, cur.getWidth() / 2, cur.getHeight() / 2, true);
            if (cur != src) cur.recycle();
            cur = nx;
        }
        Bitmap out = Bitmap.createScaledBitmap(cur, Math.max(1, w), Math.max(1, h), true);
        if (cur != src && cur != out) cur.recycle();
        return out;
    }

    /**
     * El carro del color elegido al tamaño exacto: JPG + una sola máscara para todos los colores (los ocho carros
     * están alineados). Android 4.1/4.2 no lee WebP con transparencia, por eso no va un PNG por color.
     */
    static Bitmap carBitmap(Context ctx, String color, int w, int h) {
        String key = color + ":" + w + "x" + h;
        Bitmap b = carCache.get(key);
        if (b != null && !b.isRecycled()) return b;
        Bitmap jpg = asset(ctx, "carbono/car_" + color + ".jpg"), mask = asset(ctx, "carbono/car_a.png");
        if (jpg == null || mask == null || w <= 0 || h <= 0) return null;
        try {
            Bitmap sj = scaled(jpg, w, h), sm = scaled(mask, w, h);
            if (sj != jpg) jpg.recycle();
            if (sm != mask) mask.recycle();
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.drawBitmap(sj, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            Paint pm = new Paint(Paint.FILTER_BITMAP_FLAG);
            pm.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            pm.setColorFilter(new ColorMatrixColorFilter(new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0}));
            cv.drawBitmap(sm, 0, 0, pm);
            sj.recycle();
            sm.recycle();
        } catch (Throwable e) {
            return null;
        }
        if (carCache.size() > 6) carCache.clear();
        carCache.put(key, b);
        return b;
    }

    /** El carro con su sombra, pintado una vez al tamaño de la vista. */
    static final class Car extends View {
        private final String color;
        private final boolean light;
        private Bitmap frame;

        Car(Context c, String color, boolean light) {
            super(c);
            this.color = color;
            this.light = light;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            frame = null;
            if (w <= 0 || h <= 0) return;
            try {
                frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(frame);
                RectF r = carRect(w, h);
                Bitmap sh = asset(getContext(), "carbono/shadow.png");
                if (sh != null) {
                    float px = r.width() * SH_X, py = r.height() * SH_Y;
                    RectF sr = new RectF(r.left - px, r.top - py, r.right + px, r.bottom + py);
                    sr.offset(light ? r.width() * 0.06f : 0, light ? r.height() * 0.025f : r.height() * 0.012f);
                    float op = light ? 0.38f : 0.75f;
                    Paint ps = new Paint(Paint.FILTER_BITMAP_FLAG);
                    ps.setColorFilter(new ColorMatrixColorFilter(new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, op, 0, 0, 0, 0}));
                    cv.drawBitmap(sh, null, sr, ps);
                    sh.recycle();
                }
                Bitmap car = carBitmap(getContext(), color, Math.round(r.width()), Math.round(r.height()));
                if (car != null) cv.drawBitmap(car, Math.round(r.left), Math.round(r.top), new Paint(Paint.FILTER_BITMAP_FLAG));
            } catch (Throwable e) {
                frame = null;
            }
        }

        @Override protected void onDraw(Canvas c) {
            if (frame != null && !frame.isRecycled()) c.drawBitmap(frame, 0, 0, null);
        }
    }

    // ── miniatura para la galería ──

    /** Miniatura con la misma distribución que buildCarbono en Main (y el carro de verdad). */
    static final class Thumb extends View {
        final boolean portrait, light;
        boolean selected;
        private final float s;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private Bitmap bg, car;

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
            if (light) rr(c, l, t + rad * 0.15f, rt, b + rad * 0.25f, rad, 0x0F1C2230);
            rr(c, l, t, rt, b, rad, glass(light));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, s * 0.6f));
            p.setColor(light ? 0x0F000000 : glassEdge(false));
            r.set(l, t, rt, b);
            c.drawRoundRect(r, rad, rad, p);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float u = portrait ? w : h, rad = u * 0.06f, crad = u * 0.04f;
            if (bg == null || bg.getWidth() != w || bg.getHeight() != h) {
                try {
                    bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    paint(new Canvas(bg), w, h, light, Main.RED);
                    texture(bg, light);
                } catch (Throwable e) { bg = null; }
            }
            r.set(0, 0, w, h);
            if (bg != null) {
                p.setStyle(Paint.Style.FILL);
                p.setShader(new android.graphics.BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, rad, rad, p);
                p.setShader(null);
            } else rr(c, 0, 0, w, h, rad, base(light));
            int text = light ? 0xFF15171B : 0xFFF2F3F5, muted = light ? 0xFF6C717A : 0xFF8C919A;
            float pad = u * 0.035f, gap = u * 0.028f, topH = u * 0.07f;
            // barra de arriba: hora a la derecha
            rr(c, w - pad - u * 0.22f, pad + topH * 0.25f, w - pad - u * 0.06f, pad + topH * 0.62f, u * 0.01f, Aurora.a(text, 0.85f));
            float top = pad + topH;
            if (!portrait) {
                float dockW = u * 0.11f, dh = (h - top - pad) * 0.72f, dy = top + (h - top - pad - dh) / 2;
                dock(c, pad, dy, dockW, dh, true);
                float vx = pad + dockW + gap, vw = w * 0.36f;
                vehicle(c, vx, top, vw, h - pad - top, crad, text, muted);
                float mx = vx + vw + gap;
                map(c, mx, top, w - pad, h - pad, crad, u);
                float oy = h - pad - gap - u * 0.2f, ow = w - pad - gap - mx - gap;
                player(c, mx + gap, oy, ow * 0.6f, u * 0.2f, crad * 0.8f, text, muted);
                weather(c, mx + gap + ow * 0.6f + gap * 0.6f, oy, ow * 0.4f - gap * 0.6f, u * 0.2f, crad * 0.8f, text, muted);
            } else {
                float vh = h * 0.27f;
                vehicle(c, pad, top, w - 2 * pad, vh, crad, text, muted);
                float dockH = u * 0.10f, dockY = h - pad - dockH;
                float rowH = u * 0.2f, rowY = dockY - gap - gap - rowH;
                map(c, pad, top + vh + gap, w - pad, dockY - gap, crad, u);
                float pw = (w - 2 * pad - 3 * gap) * 0.58f;
                player(c, pad + gap, rowY, pw, rowH, crad, text, muted);
                weather(c, pad + gap + pw + gap, rowY, w - 2 * pad - pw - 3 * gap, rowH, crad, text, muted);
                dock(c, pad, dockY, w - 2 * pad, dockH, false);
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

        private void dock(Canvas c, float x, float y, float w, float hh, boolean vert) {
            float rad = Math.min(w, hh) / 2;
            rr(c, x, y, x + w, y + hh, rad, pill(light));
            int n = 5;
            float tsz = Math.min(w, hh) * 0.5f;
            for (int i = 0; i < n; i++) {
                float cx = vert ? x + w / 2 : x + w * (i + 0.5f) / n, cy = vert ? y + hh * (i + 0.5f) / n : y + hh / 2;
                                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, tsz * 0.09f));
                p.setColor(Aurora.a(pillIcon(light), 0.9f));
                c.drawCircle(cx, cy, tsz * 0.24f, p);
            }
        }

        private void vehicle(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted) {
            card(c, x, y, x + w, y + hh, rad);
            float in = Math.min(w, hh) * 0.06f;
            c.save();
            c.clipRect(x, y, x + w, y + hh);
            float mh, cw, ch, cx, sx, sy;
            if (!portrait) {
                mh = hh * 0.2f;
                sy = y + hh - in - mh - in * 0.6f - hh * 0.14f;
                ch = (sy - y) / 0.8f; cw = ch * CAR_ASPECT; cx = x + w / 2; sx = x + in;
            } else {
                ch = hh / 0.8f; cw = ch * CAR_ASPECT; cx = x + w - cw / 2 - in; sx = x + in; sy = y + in;
                mh = hh * 0.4f;
            }
            int bw = Math.max(1, Math.round(cw)), bh = Math.max(1, Math.round(ch));
            if (car == null || car.getWidth() != bw || car.getHeight() != bh) car = carBitmap(getContext(), "silver", bw, bh);
            if (car != null) c.drawBitmap(car, cx - cw / 2, y - ch * 0.2f, null);
            c.restore();
            float th = hh * 0.14f;
            rr(c, sx, sy, sx + w * 0.16f, sy + th * 0.16f, th * 0.05f, Aurora.a(muted, 0.8f));
            rr(c, sx, sy + th * 0.3f, sx + w * 0.2f, sy + th * 0.95f, th * 0.1f, Aurora.a(text, 0.9f));
            float leftW = portrait ? w - cw - 3 * in : w - 2 * in;
            float my = y + hh - in - mh, mw = (leftW - in * 0.6f) / 2;
            int[] cols = {Faro.urgency(0.1, light), Faro.urgency(0.6, light)};
            for (int i = 0; i < 2; i++) {
                float mx = x + in + i * (mw + in * 0.6f);
                rr(c, mx, my, mx + mw, my + mh, rad * 0.6f, inset(light));
                rr(c, mx + mw * 0.12f, my + mh * 0.18f, mx + mw * 0.32f, my + mh * 0.34f, mh * 0.05f, Aurora.a(text, 0.8f));
                rr(c, mx + mw * 0.12f, my + mh * 0.48f, mx + mw * 0.78f, my + mh * 0.58f, mh * 0.04f, Aurora.a(text, 0.85f));
                rr(c, mx + mw * 0.12f, my + mh * 0.76f, mx + mw * 0.88f, my + mh * 0.82f, mh * 0.03f, cols[i]);
            }
        }

        private void map(Canvas c, float l, float t, float rt, float b, float crad, float u) {
            float mw = rt - l, mh = b - t;
            rr(c, l, t, rt, b, crad, light ? 0xFFF1F1EE : 0xFF1F2227);
            c.save();
            c.clipRect(l, t, rt, b);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, u * 0.012f));
            p.setColor(light ? 0xFFFFFFFF : 0xFF353A42);
            for (int i = 1; i < 7; i++) c.drawLine(l, t + mh * i / 7f, rt, t + mh * i / 7f - mh * 0.15f, p);
            for (int i = 1; i < 7; i++) c.drawLine(l + mw * i / 7f, t, l + mw * i / 7f + mw * 0.06f, b, p);
            c.restore();
            rr(c, l + mw * 0.04f, t + mh * 0.05f, l + mw * 0.42f, t + mh * 0.05f + u * 0.05f, u * 0.014f, tile(light));
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF2F8CFF);
            android.graphics.Path a = new android.graphics.Path();
            float ax = l + mw * 0.5f, ay = t + mh * 0.38f, as = u * 0.03f;
            a.moveTo(ax, ay - as); a.lineTo(ax + as * 0.7f, ay + as * 0.7f); a.lineTo(ax, ay + as * 0.35f); a.lineTo(ax - as * 0.7f, ay + as * 0.7f); a.close();
            c.drawPath(a, p);
        }

        private void player(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted) {
            card(c, x, y, x + w, y + hh, rad);
            float as = hh * 0.42f;
            rr(c, x + hh * 0.12f, y + hh * 0.1f, x + hh * 0.12f + as, y + hh * 0.1f + as, as * 0.18f, Aurora.a(Main.RED, 0.85f));
            rr(c, x + hh * 0.2f + as, y + hh * 0.16f, x + w * 0.8f, y + hh * 0.26f, hh * 0.04f, Aurora.a(text, 0.9f));
            rr(c, x + hh * 0.2f + as, y + hh * 0.34f, x + w * 0.6f, y + hh * 0.42f, hh * 0.04f, Aurora.a(muted, 0.8f));
            p.setStyle(Paint.Style.FILL);
            p.setColor(Aurora.a(text, 0.85f));
            float cy = y + hh * 0.75f;
            for (int i = 0; i < 3; i++) c.drawCircle(x + hh * (0.2f + i * 0.26f), cy, hh * (i == 1 ? 0.11f : 0.07f), p);
            rr(c, x + hh * 0.9f, cy - hh * 0.02f, x + w - hh * 0.12f, cy + hh * 0.02f, hh * 0.02f, track(light));
            rr(c, x + hh * 0.9f, cy - hh * 0.02f, x + hh * 0.9f + (w - hh * 1.02f) * 0.45f, cy + hh * 0.02f, hh * 0.02f, Aurora.a(text, 0.85f));
        }

        private void weather(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted) {
            card(c, x, y, x + w, y + hh, rad);
            rr(c, x + w * 0.1f, y + hh * 0.14f, x + w * 0.42f, y + hh * 0.22f, hh * 0.03f, Aurora.a(muted, 0.8f));
            rr(c, x + w * 0.1f, y + hh * 0.32f, x + w * 0.38f, y + hh * 0.58f, hh * 0.05f, Aurora.a(text, 0.9f));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, hh * 0.03f));
            p.setColor(Aurora.a(text, 0.85f));
            c.drawCircle(x + w * 0.8f, y + hh * 0.3f, hh * 0.1f, p);
            for (int i = 0; i < 3; i++) c.drawCircle(x + w * (0.2f + i * 0.3f), y + hh * 0.8f, hh * 0.06f, p);
        }
    }
}
