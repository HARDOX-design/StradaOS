package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Carátula circular con anillo de progreso (estilo de la referencia). */
public class PlayerArt extends View {
    private final float s;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint prog = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint art = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private Bitmap bitmap, placeholder;
    private float progress = -1f; // -1 = sin duración
    /** Tema Aurora: carátula cuadrada con esquinas redondas y el avance alrededor, con la misma forma. */
    private boolean square;
    private final android.graphics.Path sqPath = new android.graphics.Path(), sqSeg = new android.graphics.Path();
    private final float[] sqPos = new float[2];
    private float sqW, sqH;

    public PlayerArt(Context c, float scale, Bitmap placeholderIcon) {
        super(c);
        s = scale;
        placeholder = placeholderIcon;
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Main.BTN);
        ring.setStrokeWidth(4 * s);
        prog.setStyle(Paint.Style.STROKE);
        prog.setColor(Main.RED);
        prog.setStrokeWidth(4 * s);
        prog.setStrokeCap(Paint.Cap.ROUND);
        knob.setColor(Main.RED);
        bg.setColor(Main.INNER);
    }

    /** Tema Faro: carátula cuadrada con esquinas redondas que llena la vista, sin anillo (el avance va aparte). */
    private boolean tile;
    void setTile(int fill) {
        tile = true;
        bg.setColor(fill);
        invalidate();
    }

    private void drawTile(Canvas c, float w, float h) {
        float side = Math.min(w, h), k = side * 0.18f;
        box.set((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2);
        if (bitmap != null && !bitmap.isRecycled()) {
            BitmapShader sh = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = Math.max(box.width() / bitmap.getWidth(), box.height() / bitmap.getHeight());
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            m.postTranslate(box.centerX() - bitmap.getWidth() * scale / 2f, box.centerY() - bitmap.getHeight() * scale / 2f);
            sh.setLocalMatrix(m);
            art.setShader(sh);
            c.drawRoundRect(box, k, k, art);
        } else {
            c.drawRoundRect(box, k, k, bg);
            if (placeholder != null) {
                float sz = box.width() * 0.46f;
                RectF d = new RectF(box.centerX() - sz / 2, box.centerY() - sz / 2, box.centerX() + sz / 2, box.centerY() + sz / 2);
                Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                p.setAlpha(150);
                if (Main.LIGHT) p.setColorFilter(new android.graphics.PorterDuffColorFilter(Main.MUTED, android.graphics.PorterDuff.Mode.SRC_IN));
                c.drawBitmap(placeholder, null, d, p);
            }
        }
    }

    void setSquare(int track, int fill) {
        square = true;
        ring.setColor(track);
        bg.setColor(fill);
        invalidate();
    }

    public void setArt(Bitmap b) {
        bitmap = b;
        invalidate();
    }

    public void setProgress(float p) {
        progress = p;
        invalidate();
    }

    /** Contorno redondeado que empieza arriba al centro y va en el sentido del reloj (para medir el avance). */
    private void buildSquarePath(float l, float t, float r, float b, float k) {
        android.graphics.Path p = sqPath;
        p.reset();
        float cx = (l + r) / 2;
        p.moveTo(cx, t);
        p.lineTo(r - k, t);
        p.arcTo(new RectF(r - 2 * k, t, r, t + 2 * k), -90, 90);
        p.lineTo(r, b - k);
        p.arcTo(new RectF(r - 2 * k, b - 2 * k, r, b), 0, 90);
        p.lineTo(l + k, b);
        p.arcTo(new RectF(l, b - 2 * k, l + 2 * k, b), 90, 90);
        p.lineTo(l, t + k);
        p.arcTo(new RectF(l, t, l + 2 * k, t + 2 * k), 180, 90);
        p.lineTo(cx, t);
    }

    private void drawSquare(Canvas c, float w, float h) {
        // margen = radio del puntito + un poco: así el puntito del avance nunca se corta en el borde de la vista
        float in = 5.5f * s;
        float side = Math.min(w, h), l = (w - side) / 2 + in, t = (h - side) / 2 + in;
        float r = l + side - 2 * in, b = t + side - 2 * in, k = side * 0.22f;
        if (w != sqW || h != sqH) { buildSquarePath(l, t, r, b, k); sqW = w; sqH = h; }
        ring.setStrokeWidth(3 * s);
        prog.setStrokeWidth(3 * s);
        c.drawPath(sqPath, ring);
        if (progress >= 0) {
            android.graphics.PathMeasure pm = new android.graphics.PathMeasure(sqPath, false);
            float len = pm.getLength() * Math.max(0f, Math.min(1f, progress));
            sqSeg.reset();
            pm.getSegment(0, len, sqSeg, true);
            sqSeg.rLineTo(0, 0);   // Android 4.4 y antes: sin esto el tramo no se dibuja
            c.drawPath(sqSeg, prog);
            if (pm.getPosTan(len, sqPos, null)) c.drawCircle(sqPos[0], sqPos[1], 4f * s, knob);
        }
        float g = 6 * s, ik = Math.max(2 * s, k - g);
        box.set(l + g, t + g, r - g, b - g);
        if (bitmap != null && !bitmap.isRecycled()) {
            BitmapShader sh = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = Math.max(box.width() / bitmap.getWidth(), box.height() / bitmap.getHeight());
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            m.postTranslate(box.centerX() - bitmap.getWidth() * scale / 2f, box.centerY() - bitmap.getHeight() * scale / 2f);
            sh.setLocalMatrix(m);
            art.setShader(sh);
            c.drawRoundRect(box, ik, ik, art);
        } else {
            c.drawRoundRect(box, ik, ik, bg);
            if (placeholder != null) {
                float sz = box.width() * 0.4f;
                RectF d = new RectF(box.centerX() - sz / 2, box.centerY() - sz / 2, box.centerX() + sz / 2, box.centerY() + sz / 2);
                Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                p.setAlpha(150);
                if (Main.LIGHT) p.setColorFilter(new android.graphics.PorterDuffColorFilter(Main.MUTED, android.graphics.PorterDuff.Mode.SRC_IN));
                c.drawBitmap(placeholder, null, d, p);
            }
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (tile) { drawTile(c, w, h); return; }
        if (square) { drawSquare(c, w, h); return; }
        float cx = w / 2f, cy = h / 2f;
        float rr = Math.min(w, h) / 2f - 8 * s;      // radio del anillo
        float ra = rr - 12 * s;                       // radio de la carátula

        box.set(cx - rr, cy - rr, cx + rr, cy + rr);
        c.drawCircle(cx, cy, rr, ring);
        if (progress >= 0) {
            float sweep = Math.max(0f, Math.min(1f, progress)) * 360f;
            c.drawArc(box, -90f, sweep, false, prog);
            double a = Math.toRadians(-90f + sweep);
            c.drawCircle(cx + (float) (rr * Math.cos(a)), cy + (float) (rr * Math.sin(a)), 6 * s, knob);
        }

        if (bitmap != null && !bitmap.isRecycled()) {
            BitmapShader sh = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = Math.max(2 * ra / bitmap.getWidth(), 2 * ra / bitmap.getHeight());
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            m.postTranslate(cx - bitmap.getWidth() * scale / 2f, cy - bitmap.getHeight() * scale / 2f);
            sh.setLocalMatrix(m);
            art.setShader(sh);
            c.drawCircle(cx, cy, ra, art);
        } else {
            c.drawCircle(cx, cy, ra, bg);
            if (placeholder != null) {
                float sz = ra * 0.8f;
                RectF d = new RectF(cx - sz / 2, cy - sz / 2, cx + sz / 2, cy + sz / 2);
                Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                p.setAlpha(150);
                if (Main.LIGHT) p.setColorFilter(new android.graphics.PorterDuffColorFilter(Main.MUTED, android.graphics.PorterDuff.Mode.SRC_IN));
                c.drawBitmap(placeholder, null, d, p);
            }
        }
    }
}
