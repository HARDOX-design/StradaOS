package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Foto del vehículo: carga, quita el fondo (relleno desde los bordes), recorta y guarda como PNG. */
public class CarPhoto {

    static final String FILE = "car_custom.png";
    static final int MAX = 900;          // lado máximo al procesar (memoria/CPU de pantallas antiguas)

    public interface Opener { InputStream open() throws Exception; }

    /** Decodifica reduciendo tamaño y respetando la rotación básica. */
    public static Bitmap decode(Opener o) throws Exception {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        InputStream in = o.open();
        BitmapFactory.decodeStream(in, null, b);
        in.close();
        int sample = 1;
        while (b.outWidth / sample > MAX * 2 || b.outHeight / sample > MAX * 2) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.ARGB_8888;
        in = o.open();
        Bitmap bmp = BitmapFactory.decodeStream(in, null, d);
        in.close();
        if (bmp == null) throw new Exception("No es una imagen válida");
        float k = Math.min(1f, MAX / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
        if (k < 1f) {
            Bitmap sc = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * k), Math.round(bmp.getHeight() * k), true);
            if (sc != bmp) bmp.recycle();
            bmp = sc;
        }
        return bmp.isMutable() && bmp.getConfig() == Bitmap.Config.ARGB_8888 ? bmp : bmp.copy(Bitmap.Config.ARGB_8888, true);
    }

    /** true si la imagen ya trae fondo transparente (PNG recortado). */
    public static boolean hasTransparency(Bitmap b) {
        if (!b.hasAlpha()) return false;
        int w = b.getWidth(), h = b.getHeight(), clear = 0, total = 0;
        for (int x = 0; x < w; x += 4) { total += 2; if (alpha(b.getPixel(x, 0)) < 40) clear++; if (alpha(b.getPixel(x, h - 1)) < 40) clear++; }
        for (int y = 0; y < h; y += 4) { total += 2; if (alpha(b.getPixel(0, y)) < 40) clear++; if (alpha(b.getPixel(w - 1, y)) < 40) clear++; }
        return clear > total / 2;
    }

    private static int alpha(int c) { return (c >>> 24) & 0xFF; }

    /**
     * Quita el fondo creciendo desde los bordes por píxeles parecidos al color del borde.
     * strength 0..3: más alto = quita más (útil con sombras o degradados).
     * Devuelve null si no encontró un fondo claro.
     */
    public static Bitmap removeBackground(Bitmap src, int strength) {
        int w = src.getWidth(), h = src.getHeight(), n = w * h;
        int[] p = new int[n];
        src.getPixels(p, 0, w, 0, 0, w, h);

        int seedTol = 90 + strength * 45;   // distancia máxima al color del borde
        int stepTol = 26 + strength * 10;   // diferencia máxima entre vecinos (degradados)

        // color dominante del borde (promedio de los píxeles del borde más comunes)
        long sr = 0, sg = 0, sb = 0; int sc = 0;
        int[] border = new int[2 * (w + h)];
        int bi = 0;
        for (int x = 0; x < w; x++) { border[bi++] = p[x]; border[bi++] = p[(h - 1) * w + x]; }
        for (int y = 0; y < h; y++) { border[bi++] = p[y * w]; border[bi++] = p[y * w + w - 1]; }
        // mediana por canal (robusta a objetos que tocan el borde)
        int seed = medianColor(border, bi);
        for (int i = 0; i < bi; i++) if (dist(border[i], seed) < seedTol) { sr += (border[i] >> 16) & 255; sg += (border[i] >> 8) & 255; sb += border[i] & 255; sc++; }
        if (sc > 0) seed = 0xFF000000 | ((int) (sr / sc) << 16) | ((int) (sg / sc) << 8) | (int) (sb / sc);

        boolean[] bg = new boolean[n];
        int[] q = new int[n];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) { tail = push(p, bg, q, tail, x, seed, seedTol); tail = push(p, bg, q, tail, (h - 1) * w + x, seed, seedTol); }
        for (int y = 0; y < h; y++) { tail = push(p, bg, q, tail, y * w, seed, seedTol); tail = push(p, bg, q, tail, y * w + w - 1, seed, seedTol); }
        while (head < tail) {
            int i = q[head++];
            int x = i % w, y = i / w, c = p[i];
            if (x > 0) tail = grow(p, bg, q, tail, i - 1, c, seed, stepTol, seedTol);
            if (x < w - 1) tail = grow(p, bg, q, tail, i + 1, c, seed, stepTol, seedTol);
            if (y > 0) tail = grow(p, bg, q, tail, i - w, c, seed, stepTol, seedTol);
            if (y < h - 1) tail = grow(p, bg, q, tail, i + w, c, seed, stepTol, seedTol);
        }
        if (strength >= 2) removeReflection(p, bg, w, h, q);
        int removed = 0;
        for (int i = 0; i < n; i++) if (bg[i]) removed++;
        if (removed < n * 0.08 || removed > n * 0.97) return null;

        // quitar islas pequeñas de primer plano (ruido) y suavizar borde
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            if (bg[i]) { out[i] = 0; continue; }
            int x = i % w, y = i / w, around = 0;
            if (x > 0 && bg[i - 1]) around++;
            if (x < w - 1 && bg[i + 1]) around++;
            if (y > 0 && bg[i - w]) around++;
            if (y < h - 1 && bg[i + w]) around++;
            int a = around == 0 ? 255 : around == 1 ? 200 : around == 2 ? 130 : 60;
            out[i] = (a << 24) | (p[i] & 0x00FFFFFF);
        }
        Bitmap r = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        r.setPixels(out, 0, w, 0, 0, w, h);
        return r;
    }

    /** Quita el reflejo/sombra gris del piso: crece desde el fondo por píxeles grises claros en el tercio inferior. */
    private static void removeReflection(int[] p, boolean[] bg, int w, int h, int[] q) {
        int minY = h, maxY = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (!bg[y * w + x]) { if (y < minY) minY = y; maxY = y; }
        if (maxY < 0) return;
        int start = minY + (int) ((maxY - minY) * 0.70f);
        int head = 0, tail = 0;
        for (int y = start; y < h; y++) for (int x = 0; x < w; x++) if (bg[y * w + x]) q[tail++] = y * w + x;
        while (head < tail) {
            int i = q[head++];
            int x = i % w, y = i / w;
            int[] nb = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > start ? i - w : -1, y < h - 1 ? i + w : -1};
            for (int j : nb) {
                if (j < 0 || bg[j]) continue;
                int c = p[j], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
                if (mx > 90 && mx - mn < 45) { bg[j] = true; q[tail++] = j; }
            }
        }
    }

    private static int push(int[] p, boolean[] bg, int[] q, int tail, int i, int seed, int tol) {
        if (!bg[i] && dist(p[i], seed) < tol) { bg[i] = true; q[tail++] = i; }
        return tail;
    }

    private static int grow(int[] p, boolean[] bg, int[] q, int tail, int i, int from, int seed, int step, int tol) {
        if (!bg[i] && dist(p[i], from) < step && dist(p[i], seed) < tol) { bg[i] = true; q[tail++] = i; }
        return tail;
    }

    private static int dist(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
             + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
             + Math.abs((a & 255) - (b & 255));
    }

    private static int medianColor(int[] c, int len) {
        int[] r = new int[256], g = new int[256], b = new int[256];
        for (int i = 0; i < len; i++) { r[(c[i] >> 16) & 255]++; g[(c[i] >> 8) & 255]++; b[c[i] & 255]++; }
        return 0xFF000000 | (med(r, len) << 16) | (med(g, len) << 8) | med(b, len);
    }

    private static int med(int[] hist, int len) {
        int acc = 0;
        for (int v = 0; v < 256; v++) { acc += hist[v]; if (acc >= len / 2) return v; }
        return 255;
    }

    /** La foto tal cual (2.18), solo achicada para no gastar memoria: lado mayor ≤ max. */
    public static Bitmap scaled(Bitmap b, int max) {
        int w = b.getWidth(), h = b.getHeight();
        float k = Math.min(1f, max / (float) Math.max(w, h));
        if (k >= 1f) return b;
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)), true);
    }

    /** Recorta a lo visible y ajusta al recuadro del launcher (proporción 264x160), sin deformar. */
    public static Bitmap fitToCard(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                if (alpha(row[x]) > 30) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return b;
        int cw = maxX - minX + 1, ch = maxY - minY + 1;
        // lienzo con la proporción del recuadro (264:160) y un pequeño margen
        float ratio = 264f / 160f;
        int outW, outH;
        if (cw / (float) ch > ratio) { outW = Math.round(cw * 1.04f); outH = Math.round(outW / ratio); }
        else { outH = Math.round(ch * 1.04f); outW = Math.round(outH * ratio); }
        float k = Math.min(1f, 660f / outW);           // máx 660x400
        outW = Math.round(outW * k); outH = Math.round(outH * k);
        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Matrix m = new Matrix();
        m.postTranslate(-minX, -minY);
        m.postScale(k, k);
        m.postTranslate((outW - cw * k) / 2f, outH - ch * k - outH * 0.02f);   // apoyado abajo
        c.drawBitmap(b, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    public static boolean save(Context ctx, Bitmap b) {
        try {
            FileOutputStream os = new FileOutputStream(new File(ctx.getFilesDir(), FILE));
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static Bitmap load(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return null;
        try { return BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Throwable e) { return null; }
    }

    public static void delete(Context ctx) {
        new File(ctx.getFilesDir(), FILE).delete();
    }
}
