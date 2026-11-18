package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Miniatura de un tema de Personalización (2.24): dibuja con los colores del tema actual cómo queda la pantalla
 * de inicio, en horizontal o en vertical, siguiendo la misma distribución que layoutColumns() en Main:
 * horizontal = columnas (vehículo 300, reproductor 248/300); vertical = mapa arriba y debajo vehículo + música
 * (o la música sola a lo ancho). El velocímetro va en la esquina que fija el tema y el botón del mapa en su lado.
 * Se dibuja en código: no suma imágenes al APK y sigue el modo claro u oscuro. El alto sale del ancho (no se corta).
 */
class ThemeThumb extends View {
    final boolean car, playerLeft, gauge, compact, portrait, gaugeLeft, navLeft, navShown, labels, panel;
    boolean selected;
    private final float s;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    ThemeThumb(Context c, float s, boolean portrait, boolean car, boolean playerLeft, boolean gauge, boolean gaugeLeft,
               boolean compact, boolean navLeft, boolean navShown, boolean labels, boolean panel) {
        super(c);
        this.s = s;
        this.portrait = portrait;
        this.car = car;
        this.playerLeft = playerLeft;
        this.gauge = gauge;
        this.gaugeLeft = gaugeLeft;
        this.compact = compact;
        this.navLeft = navLeft;
        this.navShown = navShown;
        this.labels = labels;
        this.panel = panel;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, (int) (w * (portrait ? 1.42f : 0.586f)));
    }

    private int a(int color, float alpha) { return (color & 0x00FFFFFF) | ((int) (alpha * 255) << 24); }

    private void rr(Canvas c, float l, float t, float rt, float b, float rad, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        r.set(l, t, rt, b);
        c.drawRoundRect(r, rad, rad, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float u = portrait ? w : h;          // unidad: el lado corto
        float rad = u * 0.06f;
        rr(c, 0, 0, w, h, rad, Main.BG);
        float pad = w * (portrait ? 0.035f : 0.022f), gap = u * 0.02f;

        // cabecera: hora, fecha y ajustes
        float hy = portrait ? h * 0.025f : h * 0.035f, hh = u * 0.05f;
        rr(c, pad, hy, pad + w * (portrait ? 0.22f : 0.13f), hy + hh, hh / 2, a(Main.TEXT, 0.85f));
        rr(c, pad + w * (portrait ? 0.25f : 0.15f), hy + hh * 0.3f, pad + w * (portrait ? 0.42f : 0.25f), hy + hh * 0.8f, hh / 4, a(Main.MUTED, 0.8f));
        p.setColor(a(Main.MUTED, 0.6f));
        c.drawCircle(w - pad - hh / 2, hy + hh / 2, hh / 2, p);

        // barra: 144 / 88 del lienzo (600 de alto en horizontal, 720 de ancho en vertical)
        float dockH = portrait ? (compact ? w * 0.10f : w * 0.16f) : (compact ? h * 0.15f : h * 0.24f);
        float top = hy + hh + gap * 1.6f, bottom = h - dockH - gap;
        float cw = w - 2 * pad, crad = u * 0.045f;

        float mapX, mapY, mapW, mapH;
        if (!portrait) {
            float carW = car ? cw * 0.30f : 0, audW = cw * (car ? 0.25f : 0.30f);
            float leftW = playerLeft ? audW : carW, rightW = playerLeft ? carW : audW;
            mapX = pad + (leftW > 0 ? leftW + gap : 0);
            mapW = cw - leftW - rightW - (leftW > 0 ? gap : 0) - (rightW > 0 ? gap : 0);
            mapY = top; mapH = bottom - top;
            float rightX = mapX + mapW + gap;
            float carX = playerLeft ? rightX : pad, audX = playerLeft ? pad : rightX;
            if (car) carCard(c, carX, top, carW, bottom - top, crad);
            playerTall(c, audX, top, audW, bottom - top, crad);
        } else {
            float rowH = car ? (bottom - top) * 0.36f : (bottom - top) * 0.15f;
            mapX = pad; mapW = cw; mapY = top; mapH = bottom - top - rowH - gap;
            float rowY = mapY + mapH + gap;
            if (car) {
                float carW = cw * 0.44f, audW = cw - carW - gap;
                float carX = playerLeft ? pad + audW + gap : pad, audX = playerLeft ? pad : pad + carW + gap;
                carCard(c, carX, rowY, carW, rowH, crad);
                playerTall(c, audX, rowY, audW, rowH, crad);
            } else playerWide(c, pad, rowY, cw, rowH, crad);
        }
        map(c, mapX, mapY, mapW, mapH, crad, u);
        dock(c, pad, cw, h, dockH, u);

        if (selected) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f * s);
            p.setColor(Main.RED);
            r.set(1.25f * s, 1.25f * s, w - 1.25f * s, h - 1.25f * s);
            c.drawRoundRect(r, rad, rad, p);
        }
    }

    private void map(Canvas c, float mx, float my, float mw, float mh, float crad, float u) {
        int mapBg = Main.LIGHT ? 0xFFE4E8ED : 0xFF262C37, road = Main.LIGHT ? 0xFFFFFFFF : 0xFF3B4454;
        rr(c, mx, my, mx + mw, my + mh, crad, mapBg);
        c.save();
        c.clipRect(mx, my, mx + mw, my + mh);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, u * 0.018f));
        p.setColor(road);
        for (int i = 1; i < 4; i++) c.drawLine(mx, my + mh * i / 4f, mx + mw, my + mh * i / 4f - mh * 0.15f, p);
        for (int i = 1; i < 4; i++) c.drawLine(mx + mw * i / 4f, my, mx + mw * i / 4f + mw * 0.08f, my + mh, p);
        p.setColor(Main.LIGHT ? 0xFF9ED29B : 0xFF4E7A3F);
        p.setStrokeWidth(Math.max(2f, u * 0.03f));
        c.drawLine(mx + mw * 0.2f, my + mh, mx + mw * 0.75f, my, p);
        c.restore();
        float ax = mx + mw * 0.5f, ay = my + mh * 0.5f, as = u * 0.045f;
        path.reset();
        path.moveTo(ax, ay - as);
        path.lineTo(ax + as * 0.75f, ay + as);
        path.lineTo(ax, ay + as * 0.45f);
        path.lineTo(ax - as * 0.75f, ay + as);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Main.RED);
        c.drawPath(path, p);
        float m = u * 0.03f;
        // panel de conexión (Android Auto / CarPlay / clima) abajo del mapa; plegado solo queda la pestaña
        float ph = Math.min(mh * 0.22f, u * 0.14f), base;
        if (panel) {
            float py = my + mh - m - ph;
            rr(c, mx + m, py, mx + mw - m, my + mh - m, crad * 0.8f, a(Main.CARD, 0.92f));
            float bw = (mw - 2 * m) * 0.3f, bh = ph * 0.56f, by = py + (ph - bh) / 2;
            rr(c, mx + m * 2, by, mx + m * 2 + bw, by + bh, bh / 2, a(Main.BTN, 1f));
            rr(c, mx + m * 3 + bw, by, mx + m * 3 + bw * 2, by + bh, bh / 2, a(Main.BTN, 1f));
            rr(c, mx + mw - m * 2 - bw * 0.6f, by + bh * 0.25f, mx + mw - m * 2, by + bh * 0.75f, bh / 4, a(Main.MUTED, 0.7f));
            base = py - m;
        } else {
            float tw = mw * 0.1f, th = ph * 0.22f;
            rr(c, mx + (mw - tw) / 2, my + mh - m - th, mx + (mw + tw) / 2, my + mh - m, th / 2, a(Main.CARD, 0.92f));
            base = my + mh - m * 1.6f - th;
        }
        // botón del mapa (navegador) encima del panel, en su lado
        float nb = u * 0.085f;
        if (navShown) {
            float nx = navLeft ? mx + m + nb / 2 : mx + mw - m - nb / 2, ny = base - nb / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(Main.CARD, 0.92f));
            c.drawCircle(nx, ny, nb / 2, p);
            rr(c, nx - nb * 0.18f, ny - nb * 0.2f, nx + nb * 0.18f, ny + nb * 0.2f, nb * 0.05f, a(Main.TEXT, 0.8f));
        }
        if (gauge) {
            // velocímetro abajo en la esquina del tema; si cae del lado del botón, se corre hacia adentro (como Main)
            float gr = Math.min(mh * 0.45f, Math.min(mw * 0.42f, u * 0.22f)) / 2;
            float shift = navShown && gaugeLeft == navLeft ? nb + m : 0;
            float gx = gaugeLeft ? mx + m + shift + gr : mx + mw - m - shift - gr, gy = base - gr;
            p.setStyle(Paint.Style.FILL);
            p.setColor(a(Main.CARD, 0.92f));
            c.drawCircle(gx, gy, gr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.2f, gr * 0.13f));
            p.setColor(a(Main.TEXT, 0.5f));
            r.set(gx - gr * 0.72f, gy - gr * 0.72f, gx + gr * 0.72f, gy + gr * 0.72f);
            c.drawArc(r, 135, 270, false, p);
            p.setColor(Main.RED);
            c.drawArc(r, 135, 70, false, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void carCard(Canvas c, float x, float y, float w, float h, float crad) {
        rr(c, x, y, x + w, y + h, crad, Main.CARD);
        float cx = x + w / 2;
        rr(c, x + w * 0.1f, y + h * 0.1f, x + w * 0.9f, y + h * 0.55f, crad * 0.7f, a(Main.BTN, 0.9f));
        rr(c, cx - w * 0.16f, y + h * 0.25f, cx + w * 0.16f, y + h * 0.4f, crad * 0.5f, a(Main.SOFT, 0.55f));
        rr(c, x + w * 0.1f, y + h * 0.68f, x + w * 0.28f, y + h * 0.88f, crad * 0.3f, a(Main.TEXT, 0.85f));
        rr(c, x + w * 0.32f, y + h * 0.8f, x + w * 0.48f, y + h * 0.87f, crad * 0.25f, a(Main.MUTED, 0.8f));
    }

    /** Reproductor en columna: carátula arriba, título y botones abajo. */
    private void playerTall(Canvas c, float x, float y, float w, float h, float crad) {
        rr(c, x, y, x + w, y + h, crad, Main.CARD);
        float pcx = x + w / 2;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, h * 0.008f));
        p.setColor(a(Main.SOFT, 0.5f));
        c.drawCircle(pcx, y + h * 0.3f, Math.min(w * 0.28f, h * 0.22f), p);
        rr(c, pcx - w * 0.25f, y + h * 0.58f, pcx + w * 0.25f, y + h * 0.63f, crad * 0.3f, a(Main.TEXT, 0.8f));
        rr(c, pcx - w * 0.18f, y + h * 0.67f, pcx + w * 0.18f, y + h * 0.70f, crad * 0.25f, a(Main.MUTED, 0.7f));
        buttons(c, pcx, y + h * 0.84f, Math.min(w * 0.09f, h * 0.075f));
    }

    /** Reproductor a lo ancho (vertical sin vehículo): carátula a la izquierda y botones a la derecha. */
    private void playerWide(Canvas c, float x, float y, float w, float h, float crad) {
        rr(c, x, y, x + w, y + h, crad, Main.CARD);
        float cy = y + h / 2;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, h * 0.02f));
        p.setColor(a(Main.SOFT, 0.5f));
        c.drawCircle(x + h * 0.55f, cy, h * 0.34f, p);
        rr(c, x + h * 1.05f, cy - h * 0.14f, x + w * 0.52f, cy - h * 0.04f, crad * 0.3f, a(Main.TEXT, 0.8f));
        rr(c, x + h * 1.05f, cy + h * 0.06f, x + w * 0.44f, cy + h * 0.13f, crad * 0.25f, a(Main.MUTED, 0.7f));
        buttons(c, x + w - h * 0.95f, cy, h * 0.17f);
    }

    private void buttons(Canvas c, float cx, float cy, float br) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(a(Main.BTN, 1f));
        c.drawCircle(cx - br * 2.4f, cy, br * 0.75f, p);
        c.drawCircle(cx + br * 2.4f, cy, br * 0.75f, p);
        p.setColor(Main.RED);
        c.drawCircle(cx, cy, br, p);
    }

    /** Barra de botones: normal (círculos, con nombre si el usuario los tiene) o compacta (píldora con puntos). */
    private void dock(Canvas c, float pad, float cw, float h, float dockH, float u) {
        float dTop = h - dockH, n = 7, step = cw / n;
        if (compact) rr(c, pad, dTop + dockH * 0.12f, pad + cw, h - dockH * 0.18f, dockH * 0.35f, a(Main.CARD, 0.9f));
        float dr = compact ? dockH * 0.2f : dockH * (labels ? 0.22f : 0.27f);
        float dy = compact ? dTop + dockH * 0.47f : dTop + dockH * (labels ? 0.4f : 0.48f);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float dx = pad + step * (i + 0.5f);
            p.setColor(a(Main.BTN, 1f));
            c.drawCircle(dx, dy, dr, p);
            if (!compact && labels) rr(c, dx - step * 0.18f, dTop + dockH * 0.72f, dx + step * 0.18f, dTop + dockH * 0.8f, u * 0.008f, a(Main.MUTED, 0.7f));
        }
    }
}
