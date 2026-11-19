package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Tema premium «Faro» (Bloque 16, 2.25). Diseño propio de StradaOS: fondo carbón (o perla en claro) con un haz de luz
 * diagonal que cruza de abajo a la izquierda hasta arriba a la derecha; columna izquierda con el velocímetro arriba y
 * un carro en vista cenital abajo (con los faros encendidos en el modo oscuro); el mapa en trazo fino en el centro;
 * clima y mantenimientos a la derecha; reproductor horizontal debajo y una barra de apps compacta.
 *
 * Rendimiento (Picanto: Android 4.2, GPU modesta): el fondo, el carro con su sombra y la capa de los faros se pintan
 * UNA vez en imágenes del tamaño exacto de la pantalla y quedan guardadas. Encender o apagar los faros solo cambia la
 * opacidad de su capa. Los íconos se dibujan con trazos (nítidos a cualquier tamaño, sin PNG nuevos).
 */
final class Faro {
    private Faro() { }

    static final String ID = "faro";

    // ── colores (oscuro / claro) ──
    static int base(boolean l) { return l ? 0xFFECEEF1 : 0xFF0B0C0F; }
    static int glass(boolean l) { return l ? 0xCCFFFFFF : 0x9E1A1C21; }
    static int glassEdge(boolean l) { return l ? 0xE6FFFFFF : 0x17FFFFFF; }
    static int pill(boolean l) { return l ? 0xD9FFFFFF : 0xE6141519; }
    static int pillEdge(boolean l) { return l ? 0xF2FFFFFF : 0x1AFFFFFF; }
    static int pillIcon(boolean l) { return l ? 0xFF1C1E23 : 0xFFEDEEF1; }
    static int soft(boolean l) { return l ? 0x0F000000 : 0x14FFFFFF; }
    /** Recuadro de los botones de la barra y de los controles del mapa. */
    static int tile(boolean l) { return l ? 0xFFFFFFFF : 0xFF202328; }
    static int track(boolean l) { return l ? 0xFFD8DBE0 : 0xFF2A2D33; }
    /**
     * Mapa como en Cobalto: las teselas de siempre, translúcidas sobre el fondo y en el tono del tema. Nivel ya resuelto
     * (0 baja, 1 media, 2 alta; «Auto» se resuelve en Main según la pantalla). Baja es el aspecto suave que se ve bien
     * en pantallas buenas (S23). En las pantallas sencillas de los radios los grises oscuros se aplastan hacia el negro
     * y los claros se lavan: media y alta suben la opacidad, el contraste y devuelven parte del color.
     */
    static int mapLevel;
    static float mapAlpha(boolean l) {
        if (mapLevel <= 0) return 0.50f;
        if (mapLevel == 1) return l ? 0.72f : 0.80f;
        return l ? 0.92f : 1f;
    }

    /** Filtro de color del mapa: saturación, contraste (alrededor del tono del terreno) y un toque azul en oscuro. */
    static android.graphics.ColorMatrixColorFilter mapTone(boolean l) {
        float sat, con, pivot = l ? 240f : 50f;
        float[] tint;
        if (mapLevel <= 0) { sat = l ? 0.22f : 0.18f; con = 1f; tint = l ? new float[]{0.98f, 0.99f, 1.03f} : new float[]{0.92f, 0.98f, 1.12f}; }
        else if (mapLevel == 1) { sat = l ? 0.45f : 0.40f; con = l ? 1.20f : 1.32f; tint = l ? new float[]{0.98f, 0.995f, 1.025f} : new float[]{0.93f, 0.98f, 1.10f}; }
        else { sat = l ? 0.80f : 0.85f; con = l ? 1.12f : 1.25f; tint = new float[]{1f, 1f, l ? 1.01f : 1.03f}; }
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(sat);
        float off = pivot * (1 - con);
        m.postConcat(new ColorMatrix(new float[]{con, 0, 0, 0, off, 0, con, 0, 0, off, 0, 0, con, 0, off, 0, 0, 0, 1, 0}));
        ColorMatrix t = new ColorMatrix();
        t.setScale(tint[0], tint[1], tint[2], 1f);
        m.postConcat(t);
        return new android.graphics.ColorMatrixColorFilter(m);
    }
    static final int COPPER = 0xFFE7B08A, COPPER_LIGHT = 0xFFF6E7DA, COPPER_DEEP = 0xFFC98B5E;
    static final int WARM = 0xFFFFF4E0, LAVENDER = 0xFFC9C4FF;

    /** Barra de color de un mantenimiento según lo que falta (fracción del intervalo). */
    static int urgency(double f, boolean l) {
        if (f <= 0) return l ? 0xFFE5372C : 0xFFFF453A;      // vencido
        if (f < 0.15) return l ? 0xFFF08A00 : 0xFFFF9F0A;    // ya casi
        if (f < 0.40) return l ? 0xFFD9A400 : 0xFFFFD60A;    // se acerca
        if (f < 0.70) return l ? 0xFF0A6CE0 : 0xFF0A84FF;    // programado
        return l ? 0xFF22A447 : 0xFF30D158;                  // lejos
    }

    static final String[] CAR_COLORS = {"silver", "white", "black", "red", "blue"};
    static final String[] CAR_NAMES = {"Plata", "Blanco", "Negro", "Rojo", "Azul"};
    static String car(String pref) {
        for (String c : CAR_COLORS) if (c.equals(pref)) return c;
        return "silver";
    }

    // ── fondo ──

    /**
     * Base carbón o perla con un haz de luz diagonal (de abajo a la izquierda hasta arriba a la derecha) que se apaga
     * hacia abajo a la izquierda, un reflejo de borde nítido (como un vidrio) y un brillo suave detrás del velocímetro.
     */
    static void paint(Canvas c, float w, float h, boolean light, int accent) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boolean tall = h > w * 1.05f;
        int base = base(light);
        p.setShader(new LinearGradient(0, 0, w, h,
            light ? new int[]{0xFFF4F5F7, 0xFFECEEF1, 0xFFE1E4E8} : new int[]{0xFF121317, 0xFF0B0C0F, 0xFF060709},
            new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // brillo detrás del velocímetro (arriba a la izquierda; en vertical, a la izquierda del medio)
        float gx = tall ? w * 0.22f : w * 0.155f, gy = tall ? h * 0.56f : h * 0.20f, gr = Math.min(w, h) * (tall ? 0.36f : 0.36f);
        p.setShader(new RadialGradient(gx, gy, gr, new int[]{Aurora.a(light ? 0xFFFFFFFF : LAVENDER, light ? 0.85f : 0.05f),
            Aurora.a(light ? 0xFFFFFFFF : LAVENDER, light ? 0.30f : 0.015f), 0}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // haz diagonal: banda suave perpendicular a la diagonal (0,h)→(w,0)
        float diag = (float) Math.hypot(w, h), nx = h / diag, ny = w / diag;
        float cx = w * 0.56f, cy = h * 0.54f, bw = diag * (light ? 0.24f : 0.20f);
        int core = light ? 0xFFFFFFFF : 0xFFC9D3E6;
        float a = light ? 0.80f : 0.17f;
        p.setShader(new LinearGradient(cx - nx * bw, cy - ny * bw, cx + nx * bw, cy + ny * bw,
            new int[]{0, Aurora.a(core, a * 0.30f), Aurora.a(core, a), Aurora.a(core, a * 0.30f), 0},
            new float[]{0f, 0.28f, 0.5f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // reflejo de borde nítido, paralelo al haz y un poco más abajo
        float ox = cx + nx * bw * 0.55f, oy = cy + ny * bw * 0.55f, sw = diag * 0.10f;
        float a2 = light ? 0.45f : 0.06f;
        p.setShader(new LinearGradient(ox - nx * sw, oy - ny * sw, ox + nx * sw, oy + ny * sw,
            new int[]{0, Aurora.a(core, a2 * 0.5f), Aurora.a(core, a2), 0, 0},
            new float[]{0f, 0.40f, 0.5f, 0.506f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // el haz se apaga hacia abajo a la izquierda (encima, el color de la base cada vez más opaco)
        p.setShader(new LinearGradient(0, h, w * 0.5f, h * 0.5f, new int[]{Aurora.a(base, light ? 0.55f : 0.70f), Aurora.a(base, 0)},
            null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    /** Grano muy fino (menos que Aurora): evita las bandas del degradado sin ensuciar el carbón. */
    static int grain(boolean light) { return light ? 5 : 7; }

    // ── tarjetas ──

    /**
     * Tarjeta translúcida con borde de luz. En claro lleva una sombra suave (capas de rectángulos redondeados muy
     * tenues, sin desenfoque: barato en Android 4.1). La tarjeta se dibuja «m» px adentro de la vista: ese margen es
     * para la sombra.
     */
    static final class Card extends Drawable {
        private final boolean light;
        private final float r, m, k;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF b = new RectF();

        Card(boolean light, float radiusPx, float marginPx, float scale) {
            this.light = light;
            r = radiusPx;
            m = marginPx;
            k = scale;
        }

        @Override public void draw(Canvas c) {
            Rect bd = getBounds();
            b.set(bd.left + m, bd.top + m, bd.right - m, bd.bottom - m);
            p.setStyle(Paint.Style.FILL);
            if (light) {
                // sombra: 6 capas que crecen y bajan un poco, cada vez más tenues
                for (int i = 5; i >= 0; i--) {
                    float g = (i + 1) * 1.1f * k;
                    p.setColor(Aurora.a(0xFF1C2230, 0.018f + (5 - i) * 0.004f));
                    b.set(bd.left + m - g, bd.top + m - g * 0.5f + 2.5f * k, bd.right - m + g, bd.bottom - m + g + 2.5f * k);
                    c.drawRoundRect(b, r + g, r + g, p);
                }
                b.set(bd.left + m, bd.top + m, bd.right - m, bd.bottom - m);
            }
            p.setColor(glass(light));
            c.drawRoundRect(b, r, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, k));
            p.setColor(glassEdge(light));
            c.drawRoundRect(b, r, r, p);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ── íconos de trazo fino ──

    static final int I_SUN = 1, I_MOON = 2, I_CLOUD = 3, I_SUN_CLOUD = 4, I_MOON_CLOUD = 5, I_RAIN = 6, I_DRIZZLE = 7,
        I_STORM = 8, I_SNOW = 9, I_FOG = 10, I_DROP = 11, I_UMBRELLA = 12, I_SEARCH = 13, I_PLUS = 14, I_MINUS = 15,
        I_LOCATE = 16, I_PREV = 17, I_NEXT = 18, I_PLAY = 19, I_PAUSE = 20, I_APPS = 21;

    /** Código del clima (WMO) → ícono de trazo. */
    static int weatherIcon(int code, boolean day) {
        if (code == 0) return day ? I_SUN : I_MOON;
        if (code <= 2) return day ? I_SUN_CLOUD : I_MOON_CLOUD;
        if (code == 3) return I_CLOUD;
        if (code == 45 || code == 48) return I_FOG;
        if (code >= 51 && code <= 57) return I_DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return I_RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return I_SNOW;
        if (code >= 95) return I_STORM;
        return I_CLOUD;
    }

    /** Vista con un ícono de trazo (caja de 24 unidades escalada a la vista). */
    static final class Icon extends View {
        private int type, color;
        private float stroke = 1.6f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF o = new RectF();

        Icon(Context c, int type, int color) {
            super(c);
            this.type = type;
            this.color = color;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            // por software: el recorte de la nube (clipPath) no funciona con la GPU en Android 4.1–4.2. Son vistas chicas.
            if (type == I_SUN_CLOUD || type == I_MOON_CLOUD) setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void set(int t) {
            if (t == type) return;
            type = t;
            if ((t == I_SUN_CLOUD || t == I_MOON_CLOUD) && getLayerType() != LAYER_TYPE_SOFTWARE) setLayerType(LAYER_TYPE_SOFTWARE, null);
            invalidate();
        }
        void setColor(int c) { if (c != color) { color = c; invalidate(); } }
        /** Grosor del trazo en unidades de la caja de 24 (1,6 por defecto). */
        void setStroke(float st) { stroke = st; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), sz = Math.min(w, h);
            if (sz <= 0) return;
            c.save();
            c.translate((w - sz) / 2, (h - sz) / 2);
            c.scale(sz / 24f, sz / 24f);
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            draw(c, type);
            c.restore();
        }

        private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, p); }

        private final Path cl = new Path();
        private final android.graphics.Matrix mx = new android.graphics.Matrix();

        /** Nube: círculo grande (centro 9,12 r 8) y uno chico a la derecha (centro 18,15 r 5), base recta. */
        private Path cloudPath(float dx, float dy, float k) {
            cl.reset();
            cl.moveTo(18, 10);
            cl.lineTo(16.74f, 10);
            o.set(1, 4, 17, 20);
            cl.arcTo(o, -14.5f, -255.5f);
            cl.lineTo(18, 20);
            o.set(13, 10, 23, 20);
            cl.arcTo(o, 90, -180);
            cl.close();
            mx.setScale(k, k, 12, 12);
            mx.postTranslate(dx, dy);
            cl.transform(mx);
            return cl;
        }

        private void cloud(Canvas c, float dx, float dy, float k) { c.drawPath(cloudPath(dx, dy, k), p); }

        /** Sol o luna detrás de la nube: lo que queda adentro de la nube no se dibuja (recorte; la vista va por software). */
        private void behindCloud(Canvas c, boolean sun, float dx, float dy, float k) {
            Path cp = cloudPath(dx, dy, k);
            c.save();
            try { c.clipPath(cp, android.graphics.Region.Op.DIFFERENCE); } catch (Throwable ignored) { }
            if (sun) sun(c, 8.4f, 8.4f, 3.4f); else moon(c, 8.8f, 8.8f, 5.6f);
            c.restore();
            c.drawPath(cloudPath(dx, dy, k), p);
        }

        private void sun(Canvas c, float cx, float cy, float r) {
            c.drawCircle(cx, cy, r, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                line(c, cx + cs * (r + 2.2f), cy + sn * (r + 2.2f), cx + cs * (r + 4f), cy + sn * (r + 4f));
            }
        }

        private static float norm(float a) { a %= 360; return a < 0 ? a + 360 : a; }

        /**
         * Media luna: el círculo grande menos otro que lo «muerde» arriba a la derecha. Contorno = arco largo del
         * grande (afuera del mordisco) y, de vuelta, el arco del chico que queda adentro del grande.
         */
        private void moon(Canvas c, float cx, float cy, float r) {
            float ox = cx + r * 0.55f, oy = cy - r * 0.45f, orr = r * 0.85f;
            double d = Math.hypot(ox - cx, oy - cy);
            double a = (r * r - orr * orr + d * d) / (2 * d), hh = Math.sqrt(Math.max(0, r * r - a * a));
            double ux = (ox - cx) / d, uy = (oy - cy) / d;
            double px = cx + a * ux, py = cy + a * uy;
            double x1 = px - hh * uy, y1 = py + hh * ux, x2 = px + hh * uy, y2 = py - hh * ux;
            float bite = (float) Math.toDegrees(Math.atan2(uy, ux));
            float a1 = (float) Math.toDegrees(Math.atan2(y1 - cy, x1 - cx)), a2 = (float) Math.toDegrees(Math.atan2(y2 - cy, x2 - cx));
            // del punto 1 al 2 por el lado que NO pasa por el mordisco
            float s1 = norm(a2 - a1);
            if (norm(bite - a1) < s1) s1 = s1 - 360;
            path.reset();
            o.set(cx - r, cy - r, cx + r, cy + r);
            path.arcTo(o, a1, s1, true);
            // del punto 2 al 1 por el arco del círculo chico que pasa hacia el centro del grande
            float b1 = (float) Math.toDegrees(Math.atan2(y2 - oy, x2 - ox)), b2 = (float) Math.toDegrees(Math.atan2(y1 - oy, x1 - ox));
            float s2 = norm(b2 - b1);
            if (norm(bite + 180 - b1) >= s2) s2 = s2 - 360;
            o.set(ox - orr, oy - orr, ox + orr, oy + orr);
            path.arcTo(o, b1, s2);
            path.close();
            c.drawPath(path, p);
        }

        private void draw(Canvas c, int t) {
            switch (t) {
                case I_SUN: sun(c, 12, 12, 4.6f); break;
                case I_MOON: moon(c, 12, 12.5f, 8f); break;
                case I_CLOUD: cloud(c, 0, 0, 0.95f); break;
                case I_SUN_CLOUD: behindCloud(c, true, 1.8f, 2.6f, 0.74f); break;
                case I_MOON_CLOUD: behindCloud(c, false, 1.8f, 2.6f, 0.74f); break;
                case I_RAIN:
                    cloud(c, 0, -3, 0.82f);
                    line(c, 8, 18.5f, 6.8f, 21.5f); line(c, 12.5f, 18.5f, 11.3f, 21.5f); line(c, 17, 18.5f, 15.8f, 21.5f);
                    break;
                case I_DRIZZLE:
                    cloud(c, 0, -3, 0.82f);
                    line(c, 8, 19, 7.6f, 20.2f); line(c, 12.5f, 19.5f, 12.1f, 20.7f); line(c, 17, 19, 16.6f, 20.2f);
                    break;
                case I_STORM:
                    cloud(c, 0, -3, 0.82f);
                    path.reset(); path.moveTo(13, 16.5f); path.lineTo(10.5f, 20); path.lineTo(13.5f, 20); path.lineTo(11, 23.2f);
                    c.drawPath(path, p);
                    break;
                case I_SNOW:
                    cloud(c, 0, -3, 0.82f);
                    for (float x : new float[]{8, 12.5f, 17}) { float y = x == 12.5f ? 21 : 19.6f; line(c, x - 1.1f, y, x + 1.1f, y); line(c, x, y - 1.1f, x, y + 1.1f); }
                    break;
                case I_FOG:
                    line(c, 4, 8, 20, 8); line(c, 6, 12, 18, 12); line(c, 4, 16, 16, 16); line(c, 8, 20, 20, 20);
                    break;
                case I_DROP:
                    path.reset(); path.moveTo(12, 3.5f);
                    path.cubicTo(12, 3.5f, 5.5f, 10.5f, 5.5f, 14.5f);
                    o.set(5.5f, 8, 18.5f, 21);
                    path.arcTo(o, 180, -180);
                    path.cubicTo(18.5f, 10.5f, 12, 3.5f, 12, 3.5f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                case I_UMBRELLA:
                    o.set(3.5f, 4, 20.5f, 21);
                    path.reset(); path.arcTo(o, 180, 180, true); path.close();
                    c.drawPath(path, p);
                    line(c, 12, 4, 12, 3); line(c, 12, 12.5f, 12, 19);
                    o.set(9, 17, 12, 20.5f); c.drawArc(o, 0, 180, false, p);
                    break;
                case I_SEARCH: c.drawCircle(10.5f, 10.5f, 6, p); line(c, 15, 15, 20, 20); break;
                case I_PLUS: line(c, 12, 5, 12, 19); line(c, 5, 12, 19, 12); break;
                case I_MINUS: line(c, 5, 12, 19, 12); break;
                case I_LOCATE:
                    path.reset(); path.moveTo(20.5f, 3.5f); path.lineTo(3.5f, 10.8f); path.lineTo(11, 13); path.lineTo(13.2f, 20.5f); path.close();
                    c.drawPath(path, p);
                    break;
                case I_PREV:
                    line(c, 6, 6, 6, 18);
                    path.reset(); path.moveTo(18, 6); path.lineTo(9.5f, 12); path.lineTo(18, 18); path.close();
                    c.drawPath(path, p);
                    break;
                case I_NEXT:
                    line(c, 18, 6, 18, 18);
                    path.reset(); path.moveTo(6, 6); path.lineTo(14.5f, 12); path.lineTo(6, 18); path.close();
                    c.drawPath(path, p);
                    break;
                case I_PLAY:
                    p.setStyle(Paint.Style.FILL_AND_STROKE);
                    path.reset(); path.moveTo(8, 5.5f); path.lineTo(19, 12); path.lineTo(8, 18.5f); path.close();
                    c.drawPath(path, p);
                    break;
                case I_PAUSE:
                    p.setStyle(Paint.Style.FILL);
                    o.set(6.5f, 5, 10, 19); c.drawRoundRect(o, 1, 1, p);
                    o.set(14, 5, 17.5f, 19); c.drawRoundRect(o, 1, 1, p);
                    break;
                case I_APPS:
                    p.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) c.drawCircle(6 + 6 * i, 6 + 6 * j, 1.9f, p);
                    break;
                default: break;
            }
        }
    }

    // ── carro en vista cenital ──

    static final float CAR_ASPECT = 279f / 640f;          // ancho / alto de las imágenes
    static final float LAMP_X = 0.21f, LAMP_Y = 0.066f;   // faros (fracción del recuadro del carro)
    static final float TAIL_X = 0.24f, TAIL_Y = 0.955f;   // luces traseras

    /**
     * Recuadro del carro dentro de una vista de vw×vh px. La sombra (difuminada, corrida a la derecha y abajo) queda
     * entera DENTRO de la vista: así nunca se ve cortada en la base ni a los lados.
     */
    static RectF carRect(float vw, float vh) {
        float ch = Math.min(vh / 1.16f, vw / 1.62f / CAR_ASPECT), cw = ch * CAR_ASPECT;
        float slack = Math.max(0, vh - ch * 1.16f);
        float l = (vw - cw) / 2, t = ch * 0.045f + slack * 0.3f;
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

    /** Achica de a mitades hasta quedar a menos del doble y luego al tamaño exacto (sin dientes al reducir mucho). */
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

    /** El carro del color elegido, recortado (JPG + máscara de transparencia), al tamaño exacto. */
    static Bitmap carBitmap(Context ctx, String color, int w, int h, boolean dark) {
        String key = color + ":" + w + "x" + h + ":" + dark;
        Bitmap b = carCache.get(key);
        if (b != null && !b.isRecycled()) return b;
        Bitmap jpg = asset(ctx, "faro/car_" + color + ".jpg"), mask = asset(ctx, "faro/car_" + color + "_a.png");
        if (jpg == null || mask == null || w <= 0 || h <= 0) return null;
        try {
            Bitmap sj = scaled(jpg, w, h), sm = scaled(mask, w, h);
            if (sj != jpg) jpg.recycle();
            if (sm != mask) mask.recycle();
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            Paint pp = new Paint(Paint.FILTER_BITMAP_FLAG);
            if (dark) {
                float k = 0.86f;
                pp.setColorFilter(new ColorMatrixColorFilter(new float[]{k, 0, 0, 0, 0, 0, k, 0, 0, 0, 0, 0, k, 0, 0, 0, 0, 0, 1, 0}));
            }
            cv.drawBitmap(sj, 0, 0, pp);
            // máscara: el canal rojo de la imagen en grises pasa a ser la transparencia (DST_IN)
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

    /**
     * El carro con su sombra (y, en oscuro, las luces traseras encendidas), pintado una vez al tamaño de la vista. La
     * sombra es la silueta difuminada (assets/faro/shadow.png), corrida a la derecha y abajo en el modo claro.
     */
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
                Bitmap sh = asset(getContext(), "faro/shadow.png");
                if (sh != null) {
                    float px = r.width() * 0.174f, py = r.height() * 0.076f;
                    RectF sr = new RectF(r.left - px, r.top - py, r.right + px, r.bottom + py);
                    sr.offset(light ? r.width() * 0.13f : 0, light ? r.height() * 0.035f : r.height() * 0.015f);
                    float op = light ? 0.34f : 0.62f;
                    Paint ps = new Paint(Paint.FILTER_BITMAP_FLAG);
                    ps.setColorFilter(new ColorMatrixColorFilter(new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, op, 0, 0, 0, 0}));
                    cv.drawBitmap(sh, null, sr, ps);
                    sh.recycle();
                }
                Bitmap car = carBitmap(getContext(), color, Math.round(r.width()), Math.round(r.height()), !light);
                if (car != null) cv.drawBitmap(car, Math.round(r.left), Math.round(r.top), new Paint(Paint.FILTER_BITMAP_FLAG));
                if (!light) {
                    // luces traseras rojas encendidas
                    Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
                    float tr = r.width() * 0.17f;
                    for (int i = 0; i < 2; i++) {
                        float tx = r.left + r.width() * (i == 0 ? TAIL_X : 1 - TAIL_X), ty = r.top + r.height() * TAIL_Y;
                        pt.setShader(new RadialGradient(tx, ty, tr, new int[]{0x99FF2A2A, 0x33FF2A2A, 0}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                        cv.drawCircle(tx, ty, tr, pt);
                    }
                }
            } catch (Throwable e) {
                frame = null;
            }
        }

        @Override protected void onDraw(Canvas c) {
            if (frame != null && !frame.isRecycled()) c.drawBitmap(frame, 0, 0, null);
        }
    }

    /**
     * Capa de los faros: dos conos de luz cálida que suben desde los faros hacia el velocímetro, un pozo de luz suave
     * en la base del aro y el destello de cada faro. Se pinta una vez (con desenfoque por software sobre una imagen)
     * y luego solo cambia la opacidad de la vista para encender o apagar.
     */
    static final class Beams extends View {
        private final float k;
        private final float[] lamps;       // x1, y1, x2, y2 (px de la vista)
        private final float topY, poolX, poolY, poolR;
        private Bitmap frame;

        Beams(Context c, float scale, float[] lamps, float topY, float poolX, float poolY, float poolR) {
            super(c);
            k = scale;
            this.lamps = lamps;
            this.topY = topY;
            this.poolX = poolX;
            this.poolY = poolY;
            this.poolR = poolR;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            frame = null;
            if (w <= 0 || h <= 0) return;
            try {
                frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(frame);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                // pozo de luz en la base del velocímetro (lavanda muy suave, como la referencia)
                // el degradado termina antes de los bordes de la capa (si no, se ve el rectángulo de la vista)
                float ry = Math.max(8, Math.min(poolR, Math.min(poolY, h - poolY) - 6 * k));
                float rx = Math.max(8, Math.min(poolR * 1.35f, Math.min(poolX, w - poolX) - 6 * k));
                c.save();
                c.scale(rx / ry, 1f, poolX, poolY);
                p.setShader(new RadialGradient(poolX, poolY, ry, new int[]{Aurora.a(LAVENDER, 0.24f), Aurora.a(LAVENDER, 0.08f), 0},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(poolX, poolY, ry, p);
                c.restore();
                for (int i = 0; i < 2; i++) {
                    float lx = lamps[i * 2], ly = lamps[i * 2 + 1];
                    boolean left = i == 0;
                    float blur = Math.max(2, 11 * k), half = 3 * k;
                    float out = Math.max(w * 0.05f, Math.min(w * 0.30f, (left ? lx : w - lx) - blur * 1.6f)), in = w * 0.10f;
                    Path cone = new Path();
                    cone.moveTo(lx - half, ly);
                    cone.lineTo(left ? lx - out : lx - in, topY);
                    cone.lineTo(left ? lx + in : lx + out, topY);
                    cone.lineTo(lx + half, ly);
                    cone.close();
                    p.setShader(new LinearGradient(0, ly, 0, topY, new int[]{Aurora.a(WARM, 0.55f), Aurora.a(WARM, 0.17f), 0},
                        new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
                    p.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
                    c.drawPath(cone, p);
                    // destello del faro
                    p.setShader(null);
                    p.setMaskFilter(new BlurMaskFilter(Math.max(1, 6 * k), BlurMaskFilter.Blur.NORMAL));
                    p.setColor(Aurora.a(WARM, 0.85f));
                    c.drawOval(new RectF(lx - 8 * k, ly - 3.5f * k, lx + 8 * k, ly + 3.5f * k), p);
                    p.setMaskFilter(null);
                    p.setColor(0xFFFFFFFF);
                    c.drawOval(new RectF(lx - 3.2f * k, ly - 1.3f * k, lx + 3.2f * k, ly + 1.3f * k), p);
                }
            } catch (Throwable e) {
                frame = null;
            }
        }

        @Override protected void onDraw(Canvas c) {
            if (frame != null && !frame.isRecycled()) c.drawBitmap(frame, 0, 0, null);
        }
    }

    // ── línea de tiempo del reproductor ──

    /** Barra fina con el avance y un puntito; sin tiempo (radio) no se ve. Solo muestra: no adelanta la canción. */
    static final class Timeline extends View {
        private float progress = -1;
        private final int track, fill;
        private final float k;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Timeline(Context c, float scale, int track, int fill) {
            super(c);
            k = scale;
            this.track = track;
            this.fill = fill;
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(float f) {
            if (Math.abs(f - progress) < 0.0005f) return;
            progress = f;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (progress < 0) return;
            float w = getWidth(), h = getHeight(), r = 5 * k, y = h / 2, x0 = r + 1, x1 = w - r - 1;
            float x = x0 + (x1 - x0) * Math.max(0, Math.min(1, progress));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, 2.5f * k));
            p.setColor(track);
            c.drawLine(x0, y, x1, y, p);
            p.setColor(fill);
            if (x > x0 + 0.5f) c.drawLine(x0, y, x, y, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(x, y, r, p);
        }
    }

    // ── miniatura para la galería ──

    /** Miniatura de la galería con la misma distribución que buildFaro en Main (y el carro de verdad). */
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
            if (light) rr(c, l, t + rad * 0.15f, rt, b + rad * 0.25f, rad, 0x0D1C2230);
            rr(c, l, t, rt, b, rad, glass(light));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, s * 0.6f));
            p.setColor(glassEdge(light));
            r.set(l, t, rt, b);
            c.drawRoundRect(r, rad, rad, p);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float u = portrait ? w : h, rad = u * 0.06f, crad = u * 0.035f;
            if (bg == null || bg.getWidth() != w || bg.getHeight() != h) {
                try {
                    bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    paint(new Canvas(bg), w, h, light, Main.RED);
                    Aurora.grain(bg, grain(light));
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
            float pad = u * 0.035f, gap = u * 0.025f;
            float pillH = u * (portrait ? 0.075f : 0.10f), pillY = h - pad * 0.8f - pillH;
            float playH = u * (portrait ? 0.085f : 0.12f), playY = pillY - gap - playH;
            float dockX = pad, dockW = w - 2 * pad;
            if (!portrait) {
                float colW = w * 0.29f, rx = pad + colW + gap, rw = w - pad - rx, mw = (rw - gap) * 0.52f, top = pad + u * 0.06f;
                column(c, pad, pad, colW, h - pad, text, muted);
                dockX = rx; dockW = w - pad - rx;
                map(c, rx, top, rx + mw, playY - gap, crad, u);
                float wx = rx + mw + gap, wh = (playY - gap - top) * 0.55f;
                weather(c, wx, top, w - pad - wx, wh, crad, text, muted);
                maint(c, wx, top + wh + gap, w - pad - wx, playY - gap - (top + wh + gap), crad, text);
                player(c, rx, playY, w - pad - rx, playH, crad, text, muted);
            } else {
                float top = pad + u * 0.06f, mapB = h * 0.42f;
                map(c, pad, top, w - pad, mapB, crad, u);
                float rowY = mapB + gap, rowH = playY - gap - rowY, colW = w * 0.40f;
                column(c, pad, rowY, colW, rowH, text, muted);
                float wx = pad + colW + gap, wh = rowH * 0.55f;
                weather(c, wx, rowY, w - pad - wx, wh, crad, text, muted);
                maint(c, wx, rowY + wh + gap, w - pad - wx, rowH - wh - gap, crad, text);
                player(c, pad, playY, w - 2 * pad, playH, crad, text, muted);
            }
            float pw2 = Math.min(dockW, w * (portrait ? 0.72f : 0.46f)), px2 = dockX + (dockW - pw2) / 2;
            rr(c, px2, pillY, px2 + pw2, pillY + pillH, pillH * 0.32f, pill(light));
            float tsz = pillH * 0.62f;
            for (int i = 0; i < 6; i++) {
                float cx = px2 + pw2 * (i + 0.5f) / 6f;
                rr(c, cx - tsz / 2, pillY + (pillH - tsz) / 2, cx + tsz / 2, pillY + (pillH + tsz) / 2, tsz * 0.25f,
                    i == 5 ? tile(light) : Aurora.a(i % 2 == 0 ? 0xFF3D8BFF : 0xFF34C759, 0.9f));
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

        private void column(Canvas c, float x, float y, float w, float hh, int text, int muted) {
            float gr = Math.min(w * 0.30f, hh * 0.17f), cx = x + w / 2, gy = y + hh * 0.06f + gr;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(1f, gr * 0.06f));
            p.setColor(track(light));
            r.set(cx - gr, gy - gr, cx + gr, gy + gr);
            c.drawArc(r, 135, 270, false, p);
            p.setStrokeWidth(Math.max(1.5f, gr * 0.13f));
            p.setColor(light ? text : COPPER);
            c.drawArc(r, 135, 70, false, p);
            p.setStrokeCap(Paint.Cap.BUTT);
            rr(c, cx - gr * 0.35f, gy - gr * 0.22f, cx + gr * 0.35f, gy + gr * 0.18f, gr * 0.08f, Aurora.a(text, 0.9f));
            float ct = gy + gr * 1.25f, ch = y + hh - ct;
            if (!light) {
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, ct + ch * 0.1f, 0, gy, Aurora.a(WARM, 0.35f), 0, Shader.TileMode.CLAMP));
                Path cone = new Path();
                cone.moveTo(cx - w * 0.10f, ct + ch * 0.1f);
                cone.lineTo(cx - w * 0.38f, gy);
                cone.lineTo(cx + w * 0.38f, gy);
                cone.lineTo(cx + w * 0.10f, ct + ch * 0.1f);
                cone.close();
                c.drawPath(cone, p);
                p.setShader(null);
            }
            RectF cr = carRect(w, ch);
            int cw = Math.max(1, Math.round(cr.width())), chh = Math.max(1, Math.round(cr.height()));
            if (car == null || car.getWidth() != cw || car.getHeight() != chh)
                car = carBitmap(getContext(), "silver", cw, chh, !light);
            if (car != null) c.drawBitmap(car, x + cr.left, ct + cr.top, null);
        }

        private void map(Canvas c, float l, float t, float rt, float b, float crad, float u) {
            float mw = rt - l, mh = b - t;
            // como un mapa de verdad, translúcido sobre el fondo (igual que Cobalto)
            rr(c, l, t, rt, b, crad, light ? 0x80F1F1EE : 0x8026292F);
            c.save();
            c.clipRect(l, t, rt, b);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, u * 0.014f));
            p.setColor(light ? 0xB3FFFFFF : 0x8C3C414B);
            for (int i = 1; i < 6; i++) c.drawLine(l, t + mh * i / 6f, rt, t + mh * i / 6f - mh * 0.18f, p);
            for (int i = 1; i < 6; i++) c.drawLine(l + mw * i / 6f, t, l + mw * i / 6f + mw * 0.08f, b, p);
            p.setStrokeWidth(Math.max(2f, u * 0.022f));
            p.setColor(light ? 0x99F3D9A6 : 0x8C5A5240);
            c.drawLine(l, t + mh * 0.62f, rt, t + mh * 0.40f, p);
            c.restore();
            rr(c, l + mw * 0.05f, t + mh * 0.04f, l + mw * 0.55f, t + mh * 0.04f + u * 0.045f, u * 0.012f, tile(light));
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF2F8CFF);
            Path a = new Path();
            float ax = l + mw * 0.5f, ay = t + mh * 0.55f, as = u * 0.03f;
            a.moveTo(ax, ay - as); a.lineTo(ax + as * 0.7f, ay + as * 0.7f); a.lineTo(ax, ay + as * 0.35f); a.lineTo(ax - as * 0.7f, ay + as * 0.7f); a.close();
            c.drawPath(a, p);
        }

        private void weather(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted) {
            card(c, x, y, x + w, y + hh, rad);
            rr(c, x + w * 0.08f, y + hh * 0.12f, x + w * 0.3f, y + hh * 0.17f, hh * 0.02f, Aurora.a(muted, 0.8f));
            rr(c, x + w * 0.08f, y + hh * 0.24f, x + w * 0.32f, y + hh * 0.42f, hh * 0.04f, Aurora.a(text, 0.9f));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, hh * 0.02f));
            p.setColor(Aurora.a(text, 0.85f));
            c.drawCircle(x + w * 0.82f, y + hh * 0.22f, hh * 0.08f, p);
            for (int i = 0; i < 6; i++) {
                float cx = x + w * (0.12f + i * 0.152f);
                c.drawCircle(cx, y + hh * 0.72f, hh * 0.045f, p);
            }
        }

        private void maint(Canvas c, float x, float y, float w, float hh, float rad, int text) {
            card(c, x, y, x + w, y + hh, rad);
            int[] cols = {urgency(-1, light), urgency(0.3, light), urgency(0.6, light), urgency(0.9, light)};
            for (int i = 0; i < 4; i++) {
                float ry = y + hh * (0.22f + i * 0.19f);
                rr(c, x + w * 0.07f, ry, x + w * 0.085f, ry + hh * 0.12f, w * 0.01f, cols[i]);
                rr(c, x + w * 0.13f, ry + hh * 0.035f, x + w * (0.55f + (i % 2) * 0.15f), ry + hh * 0.085f, hh * 0.02f, Aurora.a(text, 0.8f));
            }
        }

        private void player(Canvas c, float x, float y, float w, float hh, float rad, int text, int muted) {
            card(c, x, y, x + w, y + hh, rad);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Aurora.a(text, 0.85f));
            for (int i = 0; i < 3; i++) c.drawCircle(x + w * (0.06f + i * 0.055f), y + hh / 2, hh * (i == 1 ? 0.13f : 0.09f), p);
            rr(c, x + w * 0.24f, y + hh * 0.18f, x + w * 0.24f + hh * 0.64f, y + hh * 0.82f, hh * 0.12f, Aurora.a(Main.RED, 0.85f));
            rr(c, x + w * 0.24f + hh * 0.8f, y + hh * 0.32f, x + w * 0.52f, y + hh * 0.44f, hh * 0.05f, Aurora.a(text, 0.9f));
            rr(c, x + w * 0.24f + hh * 0.8f, y + hh * 0.56f, x + w * 0.46f, y + hh * 0.66f, hh * 0.05f, Aurora.a(muted, 0.8f));
            rr(c, x + w * 0.6f, y + hh * 0.6f, x + w * 0.94f, y + hh * 0.64f, hh * 0.02f, track(light));
            rr(c, x + w * 0.6f, y + hh * 0.6f, x + w * 0.76f, y + hh * 0.64f, hh * 0.02f, Aurora.a(text, 0.85f));
        }
    }
}
