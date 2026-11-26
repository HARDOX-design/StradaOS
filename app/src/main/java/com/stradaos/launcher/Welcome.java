package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.View;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

/**
 * 2.38 (#80 de Audex-ia, saludo por voz #48): bienvenida al encender, unos 5 segundos.
 *
 * Fondo negro; el logo oficial (assets/logo.png, tal cual) pasa de un lado a otro por encima de «StradaOS» como un
 * foco: solo se iluminan las letras que quedan bajo su luz, y el saludo de abajo también. Sale, vuelve en sentido
 * contrario, sale otra vez y cae al centro del nombre; rebota, todo se ilumina por completo y se desvanece hacia el
 * launcher. Se dibuja en código (sin video ni librerías) y solo con lo que el dibujo acelerado de Android 4.1 ya
 * soporta: texto y rectángulos con degradado, un trazo, un círculo y el mapa de bits del logo.
 *
 * Tocar la pantalla la salta. Cada degradado tiene su propio objeto (en Android 4.x el dibujo acelerado guarda una
 * sola matriz por degradado y por cuadro: si dos dibujos compartieran uno, ambos usarían la última).
 */
final class Welcome extends View {

    static final long DUR = 5200;
    private static final int LOGO_RED = 0xFFFE3211;
    // tiempos (ms)
    private static final long FADE_IN = 350, P1A = 300, P1B = 1550, P2A = 1650, P2B = 2750,
            FALL_A = 2950, FALL_B = 3350, B1 = 330, B2 = 200, OUT_A = 4700;

    /** Al caer el logo (cuando suena la voz). */
    Runnable onImpact;
    /** Al terminar (quien la puso la quita). */
    Runnable onEnd;
    /** Solo pruebas: la animación queda quieta en ese instante (ms). */
    long freezeAt = -1;

    private final String word = "StradaOS", greet;
    private Bitmap src, logo;
    private long t0 = -1;
    private boolean impacted, ended;

    private final Paint dimP = text(0xFF202227), litP = text(Color.WHITE), fullP = text(Color.WHITE);
    private final Paint gDimP = text(0xFF1F2126), gLitP = text(Color.WHITE), gFullP = text(0xFFD3D6DD);
    private final Paint haloP = new Paint(Paint.ANTI_ALIAS_FLAG), wordGlowP = new Paint(Paint.ANTI_ALIAS_FLAG),
            logoGlowP = new Paint(Paint.ANTI_ALIAS_FLAG), coneP = new Paint(Paint.ANTI_ALIAS_FLAG),
            ringP = new Paint(Paint.ANTI_ALIAS_FLAG), logoP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private RadialGradient litG, gLitG, haloG, wordGlowG, logoGlowG;
    private final Matrix mx = new Matrix();
    private final Path cone = new Path();

    // medidas (px de la pantalla)
    private float W, H, u, ts, gs, by, gy, midY, gMidY, L, ly, restY, wordW;

    private static final float GR = 100f;   // radio base de los degradados (se escalan con la matriz)

    Welcome(Context c, String greet) {
        super(c);
        this.greet = greet == null ? "" : greet;
        try {
            InputStream in = c.getAssets().open("logo.png");
            src = BitmapFactory.decodeStream(in);
            in.close();
        } catch (Throwable ignored) { }
        setClickable(true);
        Typeface bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        for (Paint p : new Paint[]{dimP, litP, fullP}) p.setTypeface(bold);
        ringP.setStyle(Paint.Style.STROKE);
        ringP.setColor(LOGO_RED);
    }

    private static Paint text(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        W = w; H = h;
        u = Math.min(w / 1024f, h / 600f);
        boolean tall = h > w;
        ts = 124 * u;
        dimP.setTextSize(ts);
        wordW = dimP.measureText(word);
        if (wordW > W * 0.8f) { ts *= W * 0.8f / wordW; dimP.setTextSize(ts); wordW = dimP.measureText(word); }
        litP.setTextSize(ts); fullP.setTextSize(ts);
        gs = 30 * u;
        gDimP.setTextSize(gs);
        float gw = gDimP.measureText(greet);
        if (gw > W * 0.9f) gs *= W * 0.9f / gw;
        gDimP.setTextSize(gs); gLitP.setTextSize(gs); gFullP.setTextSize(gs);

        by = H * (tall ? 0.52f : 0.585f);
        midY = by - ts * 0.36f;
        gy = by + ts * 0.62f;
        gMidY = gy - gs * 0.35f;
        L = 96 * u;
        ly = by - ts * 0.80f - L * 0.62f;
        restY = by - ts * 0.74f - L * 0.5f - 8 * u;
        ringP.setStrokeWidth(Math.max(2, 3 * u));

        if (src != null) {
            int px = Math.max(16, Math.round(L));
            try { logo = Bitmap.createScaledBitmap(src, px, px, true); } catch (Throwable e) { logo = src; }
        }
        // luz: blanca en el centro, un poco rosada en el borde (el rojo del logo) y transparente afuera
        int[] lc = {0xFFFFFFFF, 0xFFFFD9CF, 0x00FE3211};
        float[] lp = {0f, 0.5f, 1f};
        litG = new RadialGradient(0, 0, GR, lc, lp, Shader.TileMode.CLAMP);
        gLitG = new RadialGradient(0, 0, GR, lc, lp, Shader.TileMode.CLAMP);
        litP.setShader(litG);
        gLitP.setShader(gLitG);
        haloG = new RadialGradient(0, 0, GR, new int[]{0x2EFFE9E2, 0x10FFE9E2, 0x00FFE9E2}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        haloP.setShader(haloG);
        wordGlowG = new RadialGradient(0, 0, GR, new int[]{0x50FE3211, 0x1CFE3211, 0x00FE3211}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        wordGlowP.setShader(wordGlowG);
        logoGlowG = new RadialGradient(0, 0, GR, new int[]{0x90FE3211, 0x30FE3211, 0x00FE3211}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        logoGlowP.setShader(logoGlowG);
        // haz de luz: trapecio desde el logo hasta debajo de las letras (coordenadas con el logo en 0,0)
        float coneLen = (by + ts * 0.18f) - ly;
        cone.reset();
        cone.moveTo(-L * 0.22f, L * 0.25f);
        cone.lineTo(L * 0.22f, L * 0.25f);
        cone.lineTo(ts * 1.25f, coneLen);
        cone.lineTo(-ts * 1.25f, coneLen);
        cone.close();
        coneP.setShader(new LinearGradient(0, L * 0.25f, 0, coneLen, 0x30FFF1EA, 0x00FFF1EA, Shader.TileMode.CLAMP));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) skip();
        return true;
    }

    /** Salta al final (se desvanece). Si aún no había caído el logo, tampoco suena la voz. */
    void skip() {
        if (freezeAt >= 0) { freezeAt = -1; t0 = SystemClock.uptimeMillis() - OUT_A; impacted = true; invalidate(); return; }
        long t = t0 < 0 ? 0 : SystemClock.uptimeMillis() - t0;
        if (t >= OUT_A) return;
        impacted = true;
        t0 = SystemClock.uptimeMillis() - OUT_A;
        invalidate();
    }

    /** Milisegundos que le faltan (para no abrir otras cosas encima). */
    long remaining() {
        if (ended) return 0;
        long t = t0 < 0 ? 0 : SystemClock.uptimeMillis() - t0;
        return Math.max(0, DUR - t);
    }

    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    private static float easeInOut(float p) { return (float) (0.5 - 0.5 * Math.cos(Math.PI * p)); }
    private static float easeOut(float p) { float q = 1 - p; return 1 - q * q * q; }
    private static int a(float f) { return Math.max(0, Math.min(255, Math.round(255 * f))); }

    /** Pone el degradado centrado en (x, y) con radios rx, ry. */
    private void place(Shader g, float x, float y, float rx, float ry) {
        mx.setScale(rx / GR, ry / GR);
        mx.postTranslate(x, y);
        g.setLocalMatrix(mx);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (W <= 0) return;
        long now = SystemClock.uptimeMillis();
        if (t0 < 0) t0 = now;
        float t = freezeAt >= 0 ? freezeAt : now - t0;

        float ga = t < OUT_A ? 1f : 1f - clamp((t - OUT_A) / (float) (DUR - OUT_A));
        float dimA = clamp(t / FADE_IN);

        // dónde está el logo (la luz)
        boolean show = false;
        float lx = 0, lyy = ly, rot = 0;
        if (t >= P1A && t < P1B) {
            float p = (t - P1A) / (float) (P1B - P1A);
            lx = -L + (W + 2 * L) * easeInOut(p);
            lyy = ly - 16 * u * (float) Math.sin(Math.PI * p);
            rot = 8 * (float) Math.sin(Math.PI * p);       // la luz se queda un poco atrás, como una lámpara colgada
            show = true;
        } else if (t >= P2A && t < P2B) {
            float p = (t - P2A) / (float) (P2B - P2A);
            lx = W + L - (W + 2 * L) * easeInOut(p);
            lyy = ly + 4 * u - 10 * u * (float) Math.sin(Math.PI * p);
            rot = -8 * (float) Math.sin(Math.PI * p);
            show = true;
        } else if (t >= FALL_A) {
            lx = W / 2;
            show = true;
            if (t < FALL_B) {
                float p = (t - FALL_A) / (float) (FALL_B - FALL_A);
                lyy = -L + (restY + L) * p * p;            // cae con gravedad
            } else if (t < FALL_B + B1) {
                float q = (t - FALL_B) / (float) B1;
                lyy = restY - 34 * u * 4 * q * (1 - q);
            } else if (t < FALL_B + B1 + B2) {
                float q = (t - FALL_B - B1) / (float) B2;
                lyy = restY - 10 * u * 4 * q * (1 - q);
            } else lyy = restY;
        }
        if (t >= FALL_B && !impacted && freezeAt < 0) {
            impacted = true;
            if (onImpact != null) try { onImpact.run(); } catch (Throwable ignored) { }
        }
        float full = t < FALL_B ? 0 : easeOut(clamp((t - FALL_B) / 450f));
        float gFull = clamp((t - FALL_B - 200) / 500f);
        // punto de la luz sobre las letras (la luz va inclinada)
        double rad = Math.toRadians(rot);
        float spotX = lx - (float) Math.tan(rad) * (midY - lyy);
        float gSpotX = lx - (float) Math.tan(rad) * (gMidY - lyy);

        c.drawColor(Color.argb(a(ga), 0, 0, 0));
        float cx = W / 2;

        // brillo de fondo bajo la luz y, al final, detrás del nombre
        if (show && full < 1) {
            float r = ts * 1.7f;
            place(haloG, spotX, midY, r, r * 0.8f);
            haloP.setAlpha(a(ga * (1 - full)));
            c.drawRect(spotX - r, midY - r, spotX + r, midY + r, haloP);
        }
        if (full > 0) {
            float rx = wordW * 0.78f, ry = ts * 1.1f;
            place(wordGlowG, cx, midY, rx, ry);
            wordGlowP.setAlpha(a(ga * full));
            c.drawRect(cx - rx, midY - ry, cx + rx, midY + ry, wordGlowP);
        }
        // haz de luz
        if (show && full < 1) {
            c.save();
            c.translate(lx, lyy);
            c.rotate(rot);
            coneP.setAlpha(a(ga * (1 - full)));
            c.drawPath(cone, coneP);
            c.restore();
        }
        // nombre: apagado, iluminado bajo la luz, y entero al final
        dimP.setAlpha(a(dimA * ga * (1 - full)));
        c.drawText(word, cx, by, dimP);
        if (show && full < 1) {
            place(litG, spotX, midY, ts * 1.25f, ts * 0.95f);
            litP.setAlpha(a(ga));
            c.drawText(word, cx, by, litP);
        }
        if (full > 0) {
            fullP.setAlpha(a(ga * full));
            c.drawText(word, cx, by, fullP);
        }
        // saludo: igual, un poco menos de luz (está más lejos)
        if (greet.length() > 0) {
            gDimP.setAlpha(a(dimA * ga * (1 - gFull)));
            c.drawText(greet, cx, gy, gDimP);
            if (show && gFull < 1) {
                place(gLitG, gSpotX, gMidY, ts * 1.15f, ts * 0.85f);
                gLitP.setAlpha(a(ga * 0.85f * (1 - gFull)));
                c.drawText(greet, cx, gy, gLitP);
            }
            if (gFull > 0) {
                gFullP.setAlpha(a(ga * gFull));
                c.drawText(greet, cx, gy, gFullP);
            }
        }
        // onda al caer
        if (t >= FALL_B && t < FALL_B + 800) {
            float q = (t - FALL_B) / 800f;
            ringP.setAlpha(a(ga * 0.8f * (1 - q)));
            c.drawCircle(cx, restY, L * 0.55f + W * 0.5f * easeOut(q), ringP);
        }
        // el logo con su resplandor rojo
        if (show) {
            float r = L * 1.05f;
            place(logoGlowG, lx, lyy, r, r);
            logoGlowP.setAlpha(a(ga));
            c.drawRect(lx - r, lyy - r, lx + r, lyy + r, logoGlowP);
            if (logo != null) {
                c.save();
                c.translate(lx, lyy);
                c.rotate(rot);
                logoP.setAlpha(a(ga));
                c.drawBitmap(logo, -logo.getWidth() / 2f, -logo.getHeight() / 2f, logoP);
                c.restore();
            }
        }

        if (freezeAt >= 0) return;
        if (t >= DUR) {
            if (!ended) {
                ended = true;
                post(new Runnable() { public void run() { if (onEnd != null) onEnd.run(); } });
            }
            return;
        }
        postInvalidateOnAnimation();
    }

    // ───────────────────────── voz ─────────────────────────

    /** Estado de la última voz (para Ajustes): null = bien o sin probar. */
    static volatile String voiceNote;

    /**
     * Saludo por voz con el motor de voz del sistema (sin internet). Arranca al empezar la bienvenida (el motor tarda
     * en cargar en las radios lentas) y habla cuando se le pide, si ya está listo; si llega tarde, habla al estar listo
     * dentro de unos segundos o no habla. Si no hay voz para el idioma, solo queda el texto.
     */
    static final class Voice implements TextToSpeech.OnInitListener {
        private final Handler h = new Handler(Looper.getMainLooper());
        private final String text;
        private final Locale loc;
        private TextToSpeech tts;
        private boolean ready, want, done;
        private long wantAt;

        Voice(Context c, String text, Locale loc) {
            this.text = text;
            this.loc = loc;
            try { tts = new TextToSpeech(c.getApplicationContext(), this); }
            catch (Throwable e) { voiceNote = "No se encontró una voz en esta pantalla"; tts = null; }
            h.postDelayed(new Runnable() { public void run() { shutdown(); } }, 25000);
        }

        public void onInit(final int status) {
            // en algunas versiones llega dentro del propio constructor (sin motor de voz): se atiende después
            h.post(new Runnable() { public void run() { init(status); } });
        }

        private void init(int status) {
            if (done || tts == null) return;
            if (status != TextToSpeech.SUCCESS) { voiceNote = "No se encontró una voz en esta pantalla"; shutdown(); return; }
            try {
                Locale use = loc;
                if (tts.isLanguageAvailable(use) < TextToSpeech.LANG_AVAILABLE) {
                    use = new Locale(loc.getLanguage());
                    if (tts.isLanguageAvailable(use) < TextToSpeech.LANG_AVAILABLE) use = null;
                }
                if (use == null) { voiceNote = "No hay voz para este idioma en esta pantalla: solo se muestra el texto"; shutdown(); return; }
                tts.setLanguage(use);
                ready = true;
                voiceNote = null;
                if (want && SystemClock.uptimeMillis() - wantAt < 6000) say();
            } catch (Throwable e) { voiceNote = "No se encontró una voz en esta pantalla"; shutdown(); }
        }

        void speak() {
            want = true;
            wantAt = SystemClock.uptimeMillis();
            if (ready) say();
        }

        @SuppressWarnings("deprecation")
        private void say() {
            if (done || tts == null) return;
            try {
                HashMap<String, String> p = new HashMap<String, String>();
                p.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, p);
            } catch (Throwable ignored) { }
            want = false;
            h.postDelayed(new Runnable() { public void run() { shutdown(); } }, 12000);
        }

        void shutdown() {
            if (done) return;
            done = true;
            try { if (tts != null) tts.shutdown(); } catch (Throwable ignored) { }
            tts = null;
        }
    }
}
