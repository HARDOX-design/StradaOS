package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Carrusel de la tarjeta del centro (2.35, idea de Andrés sobre el PR #64): el mapa y el video en la misma tarjeta.
 * Es sin fin: deslizar a cualquier lado pasa a la otra página. Unos puntitos abajo indican cuál se ve; se desvanecen
 * a los pocos segundos y vuelven al tocar la tarjeta.
 *
 * Solo una página está activa: la que no se ve queda invisible (el mapa deja de dibujar y de pedir teselas) y Main
 * pausa el video y suelta su reproductor ({@link Listener#onPage}). Mientras se arrastra se ven las dos.
 *
 * El mapa en «Explorar» pide que el carrusel no se lleve sus arrastres (requestDisallowInterceptTouchEvent): ahí
 * deslizar mueve el mapa, no cambia de página.
 */
public class CenterPager extends FrameLayout {

    interface Listener { void onPage(int page); }

    private static final long DOTS_MS = 3500, FADE_MS = 450;

    private final float s;
    private final int slop, minFling;
    private int page;
    private Listener listener;
    private float downX, downY, dx;
    private boolean dragging, settling;
    private VelocityTracker vt;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long dotsUntil;

    public CenterPager(Context c, float scale) {
        super(c);
        s = scale;
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
        setWillNotDraw(false);
        dotsUntil = SystemClock.uptimeMillis() + DOTS_MS;
    }

    void setListener(Listener l) { listener = l; }

    int page() { return page; }

    /** Agrega una página del tamaño de la tarjeta. */
    void addPage(View v) {
        addView(v, new LayoutParams(-1, -1));
        v.setVisibility(getChildCount() - 1 == page ? VISIBLE : INVISIBLE);
    }

    /** Muestra una página sin animación (al armar la tarjeta o desde las pruebas). */
    void setPage(int p) {
        int n = getChildCount();
        if (n == 0) return;
        page = ((p % n) + n) % n;
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            v.animate().cancel();
            v.setTranslationX(0);
            v.setVisibility(i == page ? VISIBLE : INVISIBLE);
        }
        showDots();
        if (listener != null) listener.onPage(page);
    }

    /** Pasa a la página siguiente (+1) o a la anterior (-1) con la animación de siempre. */
    void flip(int dir) {
        if (getChildCount() < 2 || settling) return;
        dx = dir > 0 ? -1 : 1;
        layoutDrag();
        settle(true);
    }

    private void showDots() {
        dotsUntil = SystemClock.uptimeMillis() + DOTS_MS;
        invalidate();
    }

    private int other() {
        int n = getChildCount();
        return dx < 0 ? (page + 1) % n : (page - 1 + n) % n;
    }

    /** Durante el arrastre: la página actual sigue al dedo y la otra entra por el lado contrario. */
    private void layoutDrag() {
        int n = getChildCount(), w = getWidth();
        if (n < 2 || w <= 0) return;
        int o = other();
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            if (i == page) { v.setVisibility(VISIBLE); v.setTranslationX(dx); }
            else if (i == o) { v.setVisibility(VISIBLE); v.setTranslationX(dx + (dx < 0 ? w : -w)); }
            else v.setVisibility(INVISIBLE);
        }
    }

    /** Suelta el dedo: se queda en la página o termina de pasar a la otra. */
    private void settle(boolean go) {
        final int n = getChildCount(), w = getWidth();
        if (n < 2 || w <= 0) { dragging = false; return; }
        final int o = other();
        final View cur = getChildAt(page), nxt = getChildAt(o);
        settling = true;
        long dur = 240;
        if (go) {
            cur.animate().translationX(dx < 0 ? -w : w).setDuration(dur);
            nxt.animate().translationX(0).setDuration(dur).withEndAction(new Runnable() { public void run() {
                settling = false;
                cur.setVisibility(INVISIBLE);
                cur.setTranslationX(0);
                page = o;
                showDots();
                if (listener != null) listener.onPage(page);
            }});
        } else {
            cur.animate().translationX(0).setDuration(dur);
            nxt.animate().translationX(dx < 0 ? w : -w).setDuration(dur).withEndAction(new Runnable() { public void run() {
                settling = false;
                nxt.setVisibility(INVISIBLE);
                nxt.setTranslationX(0);
            }});
        }
        dragging = false;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (getChildCount() < 2) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); dragging = false;
                track(e, true);
                showDots();
                return settling;
            case MotionEvent.ACTION_MOVE:
                track(e, false);
                if (startDrag(e)) return true;
                break;
            default:
                break;
        }
        return false;
    }

    private boolean startDrag(MotionEvent e) {
        float ddx = e.getX() - downX, ddy = e.getY() - downY;
        if (settling || Math.abs(ddx) < slop || Math.abs(ddx) < Math.abs(ddy) * 1.3f) return false;
        dragging = true;
        downX += ddx > 0 ? slop : -slop;    // que no salte la distancia de arranque
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    private void track(MotionEvent e, boolean reset) {
        if (vt == null || reset) { if (vt != null) vt.recycle(); vt = VelocityTracker.obtain(); }
        vt.addMovement(e);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (getChildCount() < 2) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); dragging = false;
                track(e, true);
                showDots();
                return true;
            case MotionEvent.ACTION_MOVE:
                track(e, false);
                if (!dragging && !startDrag(e)) return true;
                dx = e.getX() - downX;
                if (dx == 0) dx = -0.01f;
                layoutDrag();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    float v = 0;
                    if (vt != null) { vt.addMovement(e); vt.computeCurrentVelocity(1000); v = vt.getXVelocity(); }
                    boolean go = e.getActionMasked() == MotionEvent.ACTION_UP
                            && (Math.abs(dx) > getWidth() * 0.22f || (Math.abs(v) > minFling && Math.signum(v) == Math.signum(dx)));
                    settle(go);
                }
                if (vt != null) { vt.recycle(); vt = null; }
                return true;
            default:
                return true;
        }
    }

    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        int n = getChildCount();
        if (n < 2) return;
        long now = SystemClock.uptimeMillis();
        float a = now < dotsUntil ? 1f : Math.max(0f, 1f - (now - dotsUntil) / (float) FADE_MS);
        if (a <= 0) return;
        float r = 3.2f * s, gap = 12 * s, y = getHeight() - 8 * s;
        float x0 = getWidth() / 2f - (n - 1) * gap / 2f;
        shade.setColor(0x000000);
        shade.setAlpha(Math.round(90 * a));
        dot.setColor(0xFFFFFF);
        for (int i = 0; i < n; i++) {
            float x = x0 + i * gap;
            c.drawCircle(x, y, r + 1.4f * s, shade);
            dot.setAlpha(Math.round((i == page ? 255 : 110) * a));
            c.drawCircle(x, y, r, dot);
        }
        removeCallbacks(redraw);
        postDelayed(redraw, now >= dotsUntil ? 32 : dotsUntil - now + 16);
    }

    private final Runnable redraw = new Runnable() { public void run() { invalidate(); } };

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(redraw);
    }
}
