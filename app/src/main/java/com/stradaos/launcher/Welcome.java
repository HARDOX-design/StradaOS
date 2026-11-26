package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
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
 * 2.38 (#80 de Audex-ia, saludo por voz #48): bienvenida al encender, unos 7 segundos.
 *
 * Fondo negro; el logo oficial (assets/logo.png, tal cual) queda fijo en el centro, encima del nombre y separado de él.
 * Por detrás de todo pasa una luz suave, de un lado a otro y despacio: el logo, las letras de «StradaOS» y el saludo
 * se iluminan solo donde la luz queda detrás de ellos. La segunda pasada termina en el centro, la luz se abre y todo
 * queda iluminado por completo; luego se desvanece hacia el launcher. Si suena la voz, el final la espera (hasta unos
 * segundos más) para que el nombre se diga entero.
 *
 * Se dibuja en código (sin video ni librerías) y solo con lo que el dibujo acelerado de Android 4.1 ya soporta: texto
 * y rectángulos con degradado y el mapa de bits del logo. Cada degradado tiene su propio objeto (en Android 4.x el
 * dibujo acelerado guarda una sola matriz por degradado y por cuadro). Tocar la pantalla o Atrás la salta.
 */
final class Welcome extends View {

    static final long DUR = 7600;
    // tiempos (ms)
    private static final long FADE_IN = 700, P1A = 400, P1B = 3200, P2B = 5200, REV_A = 4900, REV_B = 6100,
            HOLD = 6900, OUT = 700, VOICE_MAX = 5000;

    /** Cuando empieza a iluminarse todo (cuando suena la voz). */
    Runnable onImpact;
    /** Al terminar (quien la puso la quita). */
    Runnable onEnd;
    /** La voz, si hay: el final la espera. */
    Voice voice;
    /** Solo pruebas: la animación queda quieta en ese instante (ms). */
    long freezeAt = -1;

    private final String word = "StradaOS", greet;
    private Bitmap src, logo;
    private long t0 = -1, outA = HOLD;
    private boolean impacted, ended;

    private final Paint dimP = text(0xFF202227), litP = text(Color.WHITE), fullP = text(Color.WHITE);
    private final Paint gDimP = text(0xFF1F2126), gLitP = text(Color.WHITE), gFullP = text(0xFFD3D6DD);
    private final Paint backP = new Paint(Paint.ANTI_ALIAS_FLAG), glowP = new Paint(Paint.ANTI_ALIAS_FLAG),
            logoP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private RadialGradient litG, gLitG, backG, glowG;
    private final Matrix mx = new Matrix();

    // medidas (px de la pantalla)
    private float W, H, u, ts, gs, by, gy, midY, gMidY, L, logoY, wordW, blockMid;

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
        L = 96 * u;
        // bloque centrado en la pantalla: logo, espacio, nombre y saludo (medidas desde la línea base del nombre)
        float gap = 38 * u;
        float top = ts * 0.74f + gap + L;           // del renglón del nombre hacia arriba
        float bottom = ts * 0.62f + gs * 0.3f;      // hacia abajo, hasta el pie del saludo
        by = H / 2f + (top - bottom) / 2f;
        midY = by - ts * 0.36f;
        gy = by + ts * 0.62f;
        gMidY = gy - gs * 0.35f;
        logoY = by - ts * 0.74f - gap - L / 2f;
        blockMid = (logoY + gMidY) / 2f;
        if (src != null) {
            int px = Math.max(16, Math.round(L));
            try { logo = Bitmap.createScaledBitmap(src, px, px, true); } catch (Throwable e) { logo = src; }
        }
        // luz de las letras: blanca en el centro, un poco rosada en el borde (el rojo del logo) y transparente afuera
        int[] lc = {0xFFFFFFFF, 0xFFFFDDD4, 0x00FE3211};
        float[] lp = {0f, 0.5f, 1f};
        litG = new RadialGradient(0, 0, GR, lc, lp, Shader.TileMode.CLAMP);
        gLitG = new RadialGradient(0, 0, GR, lc, lp, Shader.TileMode.CLAMP);
        litP.setShader(litG);
        gLitP.setShader(gLitG);
        // la luz de atrás: blanca cálida en el centro que se vuelve roja y se apaga
        // (solo 3 colores, como todos los degradados de aquí: con 4 el dibujo por software de Android 14 tumbaba el emulador)
        backG = new RadialGradient(0, 0, GR, new int[]{0x3CFFF3EE, 0x18FE5A3C, 0x00FE3211},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
        backP.setShader(backG);
        // brillo rojo final detrás de todo el bloque
        glowG = new RadialGradient(0, 0, GR, new int[]{0x46FE3211, 0x1AFE3211, 0x00FE3211}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        glowP.setShader(glowG);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) skip();
        return true;
    }

    /** Salta al final (se desvanece). Si aún no había empezado la voz, tampoco suena. */
    void skip() {
        long now = SystemClock.uptimeMillis();
        if (freezeAt >= 0) { freezeAt = -1; impacted = true; outA = HOLD; t0 = now - HOLD; invalidate(); return; }
        long t = t0 < 0 ? 0 : now - t0;
        if (t >= outA) return;
        impacted = true;
        outA = Math.max(t, REV_B);
        t0 = now - outA;
        invalidate();
    }

    /** Milisegundos que le faltan (para no abrir otras cosas encima). */
    long remaining() {
        if (ended) return 0;
        long t = t0 < 0 ? 0 : SystemClock.uptimeMillis() - t0;
        return Math.max(0, outA + OUT - t);
    }

    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    private static float easeInOut(float p) { return (float) (0.5 - 0.5 * Math.cos(Math.PI * p)); }
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
        // el final espera a que termine la voz (como mucho VOICE_MAX más)
        if (freezeAt < 0 && t >= outA && t < HOLD + VOICE_MAX && voice != null && voice.busy()) outA = (long) t + 1;

        float ga = t < outA ? 1f : 1f - clamp((t - outA) / (float) OUT);
        float dimA = clamp(t / FADE_IN);
        float cx = W / 2;

        // la luz de atrás: entra por la izquierda, cruza despacio, vuelve y se queda en el centro
        float lx;
        if (t < P1B) lx = -0.25f * W + 1.5f * W * easeInOut(clamp((t - P1A) / (float) (P1B - P1A)));
        else lx = 1.25f * W - 0.75f * W * (1 - (1 - clamp((t - P1B) / (float) (P2B - P1B))) * (1 - clamp((t - P1B) / (float) (P2B - P1B))));
        float full = easeInOut(clamp((t - REV_A) / (float) (REV_B - REV_A)));
        float gFull = easeInOut(clamp((t - REV_A - 300) / (float) (REV_B - REV_A)));
        if (t >= REV_A && !impacted && freezeAt < 0) {
            impacted = true;
            if (onImpact != null) try { onImpact.run(); } catch (Throwable ignored) { }
        }

        c.drawColor(Color.argb(a(ga), 0, 0, 0));

        // 1) la luz detrás de todo; al final se abre y deja un brillo rojo detrás del bloque
        if (full < 1) {
            float rx = ts * 2.3f * (1 + 1.2f * full), ry = ts * 1.7f * (1 + 0.8f * full);
            place(backG, lx, blockMid, rx, ry);
            backP.setAlpha(a(ga * dimA * (1 - full)));
            c.drawRect(lx - rx, blockMid - ry, lx + rx, blockMid + ry, backP);
        }
        if (full > 0) {
            float rx = Math.max(wordW * 0.85f, L * 2), ry = ts * 1.9f;
            place(glowG, cx, blockMid, rx, ry);
            glowP.setAlpha(a(ga * full));
            c.drawRect(cx - rx, blockMid - ry, cx + rx, blockMid + ry, glowP);
        }

        // 2) el logo: apagado, se enciende cuando la luz pasa detrás, y entero al final
        if (logo != null) {
            float d = (lx - cx) / (ts * 1.6f);
            float lit = Math.max(full, (float) Math.exp(-d * d));
            logoP.setAlpha(a(ga * Math.max(dimA * 0.16f, lit)));
            c.drawBitmap(logo, cx - logo.getWidth() / 2f, logoY - logo.getHeight() / 2f, logoP);
        }

        // 3) el nombre: apagado, iluminado donde la luz queda detrás, y entero al final
        dimP.setAlpha(a(dimA * ga * (1 - full)));
        c.drawText(word, cx, by, dimP);
        if (full < 1) {
            place(litG, lx, midY, ts * 1.6f, ts * 1.2f);
            litP.setAlpha(a(ga * dimA * (1 - full)));
            c.drawText(word, cx, by, litP);
        }
        if (full > 0) {
            fullP.setAlpha(a(ga * full));
            c.drawText(word, cx, by, fullP);
        }

        // 4) el saludo, igual (un poco menos de luz)
        if (greet.length() > 0) {
            gDimP.setAlpha(a(dimA * ga * (1 - gFull)));
            c.drawText(greet, cx, gy, gDimP);
            if (gFull < 1) {
                place(gLitG, lx, gMidY, ts * 1.5f, ts * 1.0f);
                gLitP.setAlpha(a(ga * dimA * 0.85f * (1 - gFull)));
                c.drawText(greet, cx, gy, gLitP);
            }
            if (gFull > 0) {
                gFullP.setAlpha(a(ga * gFull));
                c.drawText(greet, cx, gy, gFullP);
            }
        }

        if (freezeAt >= 0) return;
        if (t >= outA + OUT) {
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
        private long wantAt, saidAt;

        Voice(Context c, String text, Locale loc) {
            this.text = text;
            this.loc = loc;
            try { tts = new TextToSpeech(c.getApplicationContext(), this); }
            catch (Throwable e) { voiceNote = "No se encontró una voz en esta pantalla"; tts = null; }
            h.postDelayed(new Runnable() { public void run() { shutdown(); } }, 30000);
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
                // un silencio detrás: en algunas radios el sonido se corta al terminar y se comía el final del nombre
                tts.playSilence(900, TextToSpeech.QUEUE_ADD, p);
            } catch (Throwable ignored) { }
            want = false;
            saidAt = SystemClock.uptimeMillis();
            h.postDelayed(new Runnable() { public void run() { shutdown(); } }, 15000);
        }

        /** Todavía va a hablar o está hablando. */
        boolean busy() {
            if (done || tts == null) return false;
            long now = SystemClock.uptimeMillis();
            if (want) return now - wantAt < 6000;
            if (saidAt > 0 && now - saidAt < 1500) return true;   // el motor tarda un momento en empezar
            try { return tts.isSpeaking(); } catch (Throwable e) { return false; }
        }

        void shutdown() {
            if (done) return;
            done = true;
            try { if (tts != null) tts.shutdown(); } catch (Throwable ignored) { }
            tts = null;
        }
    }
}
