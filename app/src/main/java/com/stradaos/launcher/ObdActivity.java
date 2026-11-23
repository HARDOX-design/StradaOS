package com.stradaos.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Gráficos grandes del escáner OBD-II (los abre el panel del vehículo o Ajustes → Datos en vivo): escala con grilla,
 * zonas de aviso y alerta, mín/máx/promedio, períodos de 1 a 30 min y hasta 3 datos apilados. Diseño de los
 * gráficos: PR #42 (Jaacern). El monitor corre en {@link ObdService}: cerrar esta pantalla no lo detiene.
 */
public class ObdActivity extends Activity implements Obd.Listener {

    static final int AMBER = ObdDialogs.AMBER, DANGER = ObdDialogs.DANGER, GOOD = ObdDialogs.GOOD;
    /** Ventanas de los gráficos; la de 15 s sirve con la lectura agrupada (varias lecturas por segundo). */
    static final long[] WINDOWS = {15000, 60000, 300000, 900000, 1800000};

    private SharedPreferences prefs;
    private float u = 1f;
    private FrameLayout frame;
    private final List<Obd.Sensor> sel = new ArrayList<Obd.Sensor>();
    private final List<ObdChart> charts = new ArrayList<ObdChart>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Obd.prefs(this);
        I18n.init(this, prefs);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Obd.Sensor first = ObdDialogs.sensorOf(getIntent().getStringExtra("chart") != null ? getIntent().getStringExtra("chart") : "coolant");
        sel.add(first != null ? first : Obd.COOLANT);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        Obd.addListener(this);
    }

    @Override protected void onPause() {
        Obd.removeListener(this);
        super.onPause();
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        build();
    }

    @Override public void onObd() {
        for (ObdChart c : charts) if (c.drawn != c.s.pushes) c.invalidate();
    }

    private int px(float v) { return Math.round(v * u); }

    private void build() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        int w = m.widthPixels, h = m.heightPixels;
        u = Math.min(Math.max(w, h) / 1024f, Math.min(w, h) / 600f);
        charts.clear();
        final long win = prefs.getLong("obd_chart_win", 300000);
        if (frame == null) { frame = new FrameLayout(this); setContentView(frame); }
        frame.removeAllViews();

        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setBackgroundColor(Main.BG);
        d.setPadding(px(16), px(10), px(16), px(10));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("←", Main.BTN, Main.TEXT);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        head.addView(back, new LinearLayout.LayoutParams(px(56), px(44)));
        TextView title = tv(I18n.t("Gráficos en vivo"), 20, Main.TEXT, true);
        title.setPadding(px(14), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String[] wl = {"15 s", "1 min", "5 min", "15 min", "30 min"};
        for (int i = 0; i < WINDOWS.length; i++) {
            final long wv = WINDOWS[i];
            TextView c = pill(wl[i], wv == win ? Main.RED : Main.BTN, wv == win ? 0xFFFFFFFF : Main.TEXT);
            c.setTextSize(0, px(14));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                prefs.edit().putLong("obd_chart_win", wv).apply();
                build();
            }});
            head.addView(c, lp(ViewGroup.LayoutParams.WRAP_CONTENT, px(40), px(8), 0));
        }
        d.addView(head);

        // botones de los datos: tocar agrega o quita (hasta 3 a la vez)
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, px(8), 0, px(8));
        for (final Obd.Sensor x : Obd.ALL) {
            if (!x.has() && !sel.contains(x)) continue;
            boolean on = sel.contains(x);
            TextView c = pill((on ? "✓ " : "") + I18n.t(x.label), on ? Main.RED : Main.BTN, on ? 0xFFFFFFFF : Main.TEXT);
            c.setTextSize(0, px(14));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (sel.contains(x)) { if (sel.size() > 1) sel.remove(x); }
                else { if (sel.size() >= 3) sel.remove(0); sel.add(x); }
                build();
            }});
            chips.addView(c, lp(ViewGroup.LayoutParams.WRAP_CONTENT, px(38), 0, px(8)));
        }
        hs.addView(chips);
        d.addView(hs);

        for (int i = 0; i < sel.size(); i++) {
            ObdChart c = new ObdChart(this, sel.get(i), win, u, true);
            charts.add(c);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            if (i > 0) l.topMargin = px(10);
            d.addView(c, l);
        }
        TextView hint = tv(I18n.t("Desliza el dedo sobre un gráfico para ver el valor de cada momento. Toca los datos de arriba para comparar hasta 3."), 12, Main.MUTED, false);
        hint.setPadding(px(4), px(6), 0, 0);
        d.addView(hint);
        frame.addView(d, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(0, px(size));
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pill(String s, int bg, int fg) {
        TextView t = tv(s, 16, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(16), 0, px(16), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(px(12));
        t.setBackground(g);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int left, int right) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
        l.leftMargin = left;
        l.rightMargin = right;
        return l;
    }
}
