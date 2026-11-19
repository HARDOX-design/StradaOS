package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Un dato del monitor OBD en grande, para el recuadro de la velocidad del inicio (Ajustes → Pantalla de inicio):
 * nombre, valor y unidad, en ámbar o rojo si está fuera de rango.
 */
public class ObdValue extends LinearLayout implements Obd.Listener {
    private final Obd.Sensor s;
    private final float k;
    private final TextView label, value, unit;
    private long drawn = -1;
    private int drawnLevel = -1;
    private boolean drawnOn;

    public ObdValue(Context c, Obd.Sensor s, float scale) {
        super(c);
        this.s = s;
        k = scale;
        setOrientation(VERTICAL);
        setPadding(px(16), px(12), px(16), px(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Main.INNER);
        bg.setCornerRadius(px(16));
        setBackground(bg);
        label = tv(I18n.t(s.label), 13, Main.MUTED, false);
        addView(label);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.BOTTOM);
        value = tv("—", 50, Main.TEXT, true);
        unit = tv(s.unit, 15, Main.MUTED, false);
        unit.setPadding(px(8), 0, 0, px(8));
        row.addView(value);
        row.addView(unit);
        addView(row);
    }

    private int px(float v) { return Math.round(v * k); }

    private TextView tv(String t, float size, int color, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k * Main.FONT);
        v.setTextColor(color);
        v.setSingleLine(true);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); Obd.addListener(this); onObd(); }
    @Override protected void onDetachedFromWindow() { Obd.removeListener(this); super.onDetachedFromWindow(); }

    @Override public void onObd() {
        boolean on = Obd.running();
        if (drawn == s.pushes && drawnLevel == s.level && drawnOn == on) return;
        drawn = s.pushes; drawnLevel = s.level; drawnOn = on;
        value.setText(on ? s.text() : "—");
        label.setText(I18n.t(s.label) + (on ? "" : " · " + I18n.t("monitor apagado")));
        value.setTextColor(s.level == Obd.CRIT ? ObdActivity.DANGER : s.level == Obd.WARN ? ObdActivity.AMBER : Main.TEXT);
    }
}
