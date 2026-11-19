package com.stradaos.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla «Vehículo»: datos del motor en vivo con gráficos, alertas activas y códigos de falla con su explicación
 * (tabla sin conexión + IA opcional). El monitor en sí corre en {@link ObdService}; esta pantalla solo lo muestra,
 * así que se puede cerrar y las alertas siguen sonando.
 */
public class ObdActivity extends Activity implements Obd.Listener {

    static final int AMBER = 0xFFFFB020, DANGER = 0xFFFF3B30, GOOD = 0xFF34C759;

    private SharedPreferences prefs;
    private TextView viewBtn;
    /** false = tarjetas con gráficos; true = lista con todos los datos que entrega el auto. */
    private boolean allMode;
    private final java.util.HashMap<String, TextView> allRows = new java.util.HashMap<String, TextView>();
    private float u = 1f;
    private boolean wide;
    private TextView statusTv, connectBtn, milTv;
    private LinearLayout grid, side;
    private final List<Tile> tiles = new ArrayList<Tile>();
    private String gridKey = "", sideKey = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Obd.prefs(this);
        allMode = prefs.getBoolean("obd_all_view", false);
        I18n.init(this, prefs);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        Obd.addListener(this);
        onObd();
    }

    @Override protected void onPause() {
        Obd.removeListener(this);
        super.onPause();
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        build();
        onObd();
    }

    private int px(float v) { return Math.round(v * u); }

    private void build() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        int w = m.widthPixels, h = m.heightPixels;
        wide = w > h;
        u = Math.min(Math.max(w, h) / 1024f, Math.min(w, h) / 600f);
        tiles.clear();
        gridKey = sideKey = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Main.BG);
        root.setPadding(px(16), px(10), px(16), px(10));

        // ── encabezado ──
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("←", Main.BTN, Main.TEXT);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        head.addView(back, new LinearLayout.LayoutParams(px(56), px(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(px(14), 0, 0, 0);
        titles.addView(tv(I18n.t("Vehículo"), 22, Main.TEXT, true));
        statusTv = tv("", 14, Main.MUTED, false);
        statusTv.setSingleLine(true);
        titles.addView(statusTv);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        milTv = pill(I18n.t("Luz del motor"), DANGER, 0xFFFFFFFF);
        milTv.setTextSize(0, px(13));
        milTv.setVisibility(View.GONE);
        head.addView(milTv, lp(ViewGroup.LayoutParams.WRAP_CONTENT, px(40), 0, px(10)));
        viewBtn = pill("", Main.BTN, Main.TEXT);
        viewBtn.setTextSize(0, px(14));
        viewBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            allMode = !allMode;
            prefs.edit().putBoolean("obd_all_view", allMode).apply();
            gridKey = "";
            onObd();
        }});
        head.addView(viewBtn, lp(ViewGroup.LayoutParams.WRAP_CONTENT, px(48), 0, px(10)));
        connectBtn = pill("", Main.RED, 0xFFFFFFFF);
        connectBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggle(); } });
        head.addView(connectBtn, lp(ViewGroup.LayoutParams.WRAP_CONTENT, px(48), 0, px(10)));
        TextView gear = pill("⚙", Main.BTN, Main.TEXT);
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { settings(); } });
        head.addView(gear, new LinearLayout.LayoutParams(px(56), px(48)));
        root.addView(head);

        // ── cuerpo: sensores a la izquierda, alertas y códigos a la derecha (o abajo en vertical) ──
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        bodyLp.topMargin = px(10);
        if (wide) {
            LinearLayout body = new LinearLayout(this);
            ScrollView l = scroll(grid), r = scroll(side);
            body.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2);
            rl.leftMargin = px(12);
            body.addView(r, rl);
            root.addView(body, bodyLp);
        } else {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(grid);
            col.addView(side);
            root.addView(scroll(col), bodyLp);
        }
        frame = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);
        detail = null;
        if (detailSel.size() > 0) openDetail(null);     // se giró la pantalla con los gráficos abiertos
    }

    private ScrollView scroll(View v) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setVerticalScrollBarEnabled(false);
        s.addView(v);
        return s;
    }

    // ───────────────────────── actualización ─────────────────────────

    @Override public void onObd() {
        if (statusTv == null) return;
        boolean on = Obd.running();
        connectBtn.setText(I18n.t(on ? "Detener" : "Conectar"));
        connectBtn.setBackground(round(on ? Main.BTN : Main.RED, 12));
        String addr = prefs.getString("obd_addr", "demo");
        String dev = "demo".equals(addr) ? I18n.t("Auto simulado") : prefs.getString("obd_name", addr);
        int st = Obd.status;
        statusTv.setText(!on ? I18n.t("Apagado") + " · " + dev
            : st == Obd.LIVE ? I18n.t("En vivo") + " · " + dev + (Obd.vin != null ? " · VIN " + Obd.vin : "")
            : I18n.t(Obd.statusMsg));
        statusTv.setTextColor(st == Obd.LIVE ? GOOD : st == Obd.ERROR ? DANGER : Main.MUTED);
        milTv.setVisibility(Obd.mil ? View.VISIBLE : View.GONE);
        viewBtn.setText(I18n.t(allMode ? "Panel" : "Todos los datos"));
        viewBtn.setVisibility(on ? View.VISIBLE : View.GONE);
        if (waitingScan && !Obd.scanning && Obd.report != null) showReport();
        fillGrid(on);
        for (Tile t : tiles) if (t.drawn != t.s.pushes || t.drawnLevel != t.s.level) t.invalidate();
        if (detail != null) for (ObdChart c : charts) if (c.drawn != c.s.pushes) c.invalidate();
        fillSide(on);
    }

    private void fillGrid(boolean on) {
        if (on && allMode) { fillAll(); return; }
        List<Obd.Sensor> show = new ArrayList<Obd.Sensor>();
        for (Obd.Sensor s : Obd.ALL) {
            boolean main = s == Obd.COOLANT || s == Obd.VOLT || s == Obd.RPM || s == Obd.SPEED || s == Obd.LOAD || s == Obd.FUEL;
            if (main || s.has()) show.add(s);
        }
        StringBuilder k = new StringBuilder(on ? "on" : "off");
        for (Obd.Sensor s : show) k.append(s.key).append(',');
        if (k.toString().equals(gridKey)) return;
        gridKey = k.toString();
        grid.removeAllViews();
        tiles.clear();
        if (!on) { grid.addView(welcome()); return; }
        int cols = wide ? 3 : 2;
        LinearLayout row = null;
        for (int i = 0; i < show.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(150));
                rl.bottomMargin = px(10);
                grid.addView(row, rl);
            }
            Tile t = new Tile(this, show.get(i));
            tiles.add(t);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i % cols != 0) tl.leftMargin = px(10);
            row.addView(t, tl);
        }
        for (int i = show.size() % cols; i > 0 && i < cols; i++) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    }

    /**
     * «Todos los datos»: cada dato que el auto entrega (los del panel, los demás PIDs estándar y el consumo calculado),
     * más los monitores de emisiones. Los valores se actualizan en su lugar; solo se rearma si aparece un dato nuevo.
     */
    private void fillAll() {
        java.util.LinkedHashMap<String, String[]> rows = new java.util.LinkedHashMap<String, String[]>();   // clave → nombre, valor
        for (Obd.Sensor s : Obd.ALL) if (s.has()) rows.put(s.key, new String[]{I18n.t(s.label) + (s.pid == -2 ? " (" + I18n.t("calculado") + ")" : ""), s.text() + " " + s.unit});
        java.util.TreeMap<Integer, byte[]> ex;
        synchronized (Obd.extra) { ex = new java.util.TreeMap<Integer, byte[]>(Obd.extra); }
        for (java.util.Map.Entry<Integer, byte[]> e : ex.entrySet()) {
            String v = Scan.value(e.getKey(), e.getValue());
            StringBuilder hex = new StringBuilder();
            for (byte b : e.getValue()) hex.append(String.format(Locale.US, "%02X ", b & 0xFF));
            rows.put("pid" + e.getKey(), new String[]{I18n.t(Scan.name(e.getKey())), v != null ? v : hex.toString().trim()});
        }
        String rd = Scan.readiness(Obd.status01);
        StringBuilder k = new StringBuilder("all");
        for (String key : rows.keySet()) k.append(key).append(',');
        k.append(rd != null);
        if (k.toString().equals(gridKey)) {
            for (java.util.Map.Entry<String, String[]> e : rows.entrySet()) {
                TextView t = allRows.get(e.getKey());
                if (t != null) setIfChanged(t, e.getValue()[1]);
            }
            TextView rt = allRows.get("readiness");
            if (rt != null && rd != null) setIfChanged(rt, rd);
            return;
        }
        gridKey = k.toString();
        grid.removeAllViews();
        tiles.clear();
        allRows.clear();
        grid.addView(section(I18n.t("Datos en vivo") + " · " + rows.size()));
        LinearLayout c = card();
        for (java.util.Map.Entry<String, String[]> e : rows.entrySet()) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, px(6), 0, px(6));
            TextView name = tv(e.getValue()[0], 14, Main.SOFT, false);
            r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView val = tv(e.getValue()[1], 15, Main.TEXT, true);
            val.setGravity(Gravity.RIGHT);
            r.addView(val, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            allRows.put(e.getKey(), val);
            final Obd.Sensor rs = sensorOf(e.getKey());
            if (rs != null) r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openDetail(rs); } });
            c.addView(r);
        }
        grid.addView(c, cardLp());
        if (rd != null) {
            grid.addView(section(I18n.t("Monitores de emisiones")));
            LinearLayout m = card();
            TextView rt = tv(rd, 14, Main.SOFT, false);
            rt.setLineSpacing(px(4), 1f);
            allRows.put("readiness", rt);
            m.addView(rt);
            TextView note = tv(I18n.t("✓ listo · ✗ aún no termina su prueba (normal después de borrar códigos o desconectar la batería)."), 12, Main.MUTED, false);
            note.setPadding(0, px(8), 0, 0);
            m.addView(note);
            grid.addView(m, cardLp());
        }
    }

    /** Sin conexión: cómo empezar. */
    private View welcome() {
        LinearLayout c = card();
        c.addView(tv(I18n.t("Conecta tu escáner OBD-II"), 20, Main.TEXT, true));
        TextView t = tv(I18n.t("1. Enchufa el escáner ELM327 en el conector OBD (bajo el volante) y pon el contacto.\n"
            + "2. Si es Bluetooth, emparéjalo primero en los ajustes de Bluetooth del equipo (clave 1234 o 0000).\n"
            + "3. Toca ⚙ para elegirlo y luego Conectar.\n\n"
            + "StradaOS vigila la temperatura, el voltaje, la mezcla y los códigos de falla, y te avisa por voz aunque estés en otra app."), 15, Main.SOFT, false);
        t.setPadding(0, px(10), 0, px(14));
        c.addView(t);
        LinearLayout row = new LinearLayout(this);
        TextView pick = pill(I18n.t("Elegir escáner"), Main.RED, 0xFFFFFFFF);
        pick.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickAdapter(); } });
        row.addView(pick, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(48)));
        TextView demo = pill(I18n.t("Probar con un auto simulado"), Main.BTN, Main.TEXT);
        demo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            prefs.edit().putString("obd_addr", "demo").apply();
            ObdService.start(ObdActivity.this);
        }});
        row.addView(demo, lp(ViewGroup.LayoutParams.WRAP_CONTENT, px(48), px(10), 0));
        c.addView(row);
        return c;
    }

    private void fillSide(boolean on) {
        List<Obd.Alert> al;
        synchronized (Obd.alerts) { al = new ArrayList<Obd.Alert>(Obd.alerts); }
        List<String> codes = new ArrayList<String>(Obd.dtcs);
        for (String p : Obd.pending) if (!codes.contains(p)) codes.add(p);
        long now = System.currentTimeMillis();
        StringBuilder k = new StringBuilder(on ? "on" : "off");
        for (Obd.Alert a : al) k.append(a.key).append(a.level).append(now - a.at < 60000).append(',');
        k.append('|').append(codes).append(Obd.lastDtcCheck > 0);
        for (String code : codes) k.append(DtcInfo.cached(this, code) != null);
        if (k.toString().equals(sideKey)) {
            // misma estructura: solo cambian los valores de los títulos y el «hace N min»
            for (Obd.Alert a : al) {
                TextView[] v = alertViews.get(a.key);
                if (v == null) continue;
                setIfChanged(v[0], a.title);
                setIfChanged(v[1], a.advice);
                setIfChanged(v[2], ago(a.at));
            }
            return;
        }
        sideKey = k.toString();
        side.removeAllViews();
        alertViews.clear();

        side.addView(section(I18n.t("Alertas")));
        if (al.isEmpty()) side.addView(note(on ? I18n.t("Todo normal por ahora.") : I18n.t("Sin monitor activo.")));
        for (final Obd.Alert a : al) {
            boolean fresh = now - a.at < 60000;
            LinearLayout c = card();
            c.setBackground(stripe(a.level == Obd.CRIT ? DANGER : AMBER, fresh));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showAlert(a); } });
            TextView title = tv(a.title, 16, Main.TEXT, true), adv = tv(a.advice, 14, Main.SOFT, false), when = tv(ago(a.at), 12, Main.MUTED, false);
            adv.setPadding(0, px(4), 0, 0);
            c.addView(title);
            c.addView(adv);
            c.addView(when);
            alertViews.put(a.key, new TextView[]{title, adv, when});
            side.addView(c, cardLp());
        }

        side.addView(section(I18n.t("Códigos de falla")));
        if (codes.isEmpty()) side.addView(note(on && Obd.lastDtcCheck > 0 ? I18n.t("El auto no tiene códigos guardados.") : I18n.t("Se revisan al conectar y cada 30 segundos.")));
        for (final String code : codes) {
            DtcInfo.Entry e = DtcInfo.get(this, code);
            boolean pend = !Obd.dtcs.contains(code);
            LinearLayout c = card();
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(tv(code, 18, Main.TEXT, true));
            TextView sev = pill(I18n.t(pend ? "Pendiente" : e.severity >= 3 ? "Grave" : e.severity == 1 ? "Leve" : "Revisar"),
                pend ? Main.BTN : e.severity >= 3 ? DANGER : e.severity == 1 ? Main.BTN : AMBER, pend || e.severity == 1 ? Main.TEXT : 0xFF000000);
            sev.setTextSize(0, px(12));
            top.addView(sev, lp(ViewGroup.LayoutParams.WRAP_CONTENT, px(28), px(10), 0));
            c.addView(top);
            String what = e.es != null ? e.es : e.ai != null ? firstLine(e.ai) : e.en != null ? e.en : DtcInfo.system(code);
            TextView d = tv(what, 14, Main.SOFT, false);
            d.setPadding(0, px(4), 0, 0);
            c.addView(d);
            c.addView(tv(I18n.t("Toca para ver qué hacer"), 12, Main.RED, false));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showCode(code); } });
            side.addView(c, cardLp());
        }
        if (on && !Obd.dtcs.isEmpty()) {
            TextView clr = pill(I18n.t("Borrar códigos"), Main.BTN, Main.TEXT);
            clr.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmClear(); } });
            side.addView(clr, lp(ViewGroup.LayoutParams.MATCH_PARENT, px(48), 0, 0));
        }
    }

    private final java.util.HashMap<String, TextView[]> alertViews = new java.util.HashMap<String, TextView[]>();

    private static void setIfChanged(TextView t, String s) {
        if (s != null && !s.contentEquals(t.getText())) t.setText(s);
    }

    private String ago(long at) {
        long s = (System.currentTimeMillis() - at) / 1000;
        return s < 60 ? I18n.t("Ahora") : I18n.t("Hace " + I18n.integer(s / 60) + " min");
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i > 0 ? s.substring(0, i) : s;
    }

    // ───────────────────────── gráficos grandes ─────────────────────────

    private FrameLayout frame;
    private View detail;
    /** Datos que se ven en los gráficos grandes (1 a 3, apilados). */
    private final List<Obd.Sensor> detailSel = new ArrayList<Obd.Sensor>();
    private final List<ObdChart> charts = new ArrayList<ObdChart>();
    static final long[] WINDOWS = {60000, 300000, 900000, 1800000};

    /** Abre (o rearma) la vista de gráficos grandes. Con un dato: lo agrega a la selección (o lo deja solo). */
    private void openDetail(Obd.Sensor s) {
        if (s != null && detail == null) { detailSel.clear(); detailSel.add(s); }
        if (detail != null) frame.removeView(detail);
        charts.clear();
        final long win = prefs.getLong("obd_chart_win", WINDOWS[1]);

        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setBackgroundColor(Main.BG);
        d.setPadding(px(16), px(10), px(16), px(10));
        d.setClickable(true);       // que los toques no pasen a la pantalla de abajo

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("←", Main.BTN, Main.TEXT);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { closeDetail(); } });
        head.addView(back, new LinearLayout.LayoutParams(px(56), px(44)));
        TextView title = tv(I18n.t("Gráficos en vivo"), 20, Main.TEXT, true);
        title.setPadding(px(14), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String[] wl = {"1 min", "5 min", "15 min", "30 min"};
        for (int i = 0; i < WINDOWS.length; i++) {
            final long w = WINDOWS[i];
            TextView c = pill(wl[i], w == win ? Main.RED : Main.BTN, w == win ? 0xFFFFFFFF : Main.TEXT);
            c.setTextSize(0, px(14));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                prefs.edit().putLong("obd_chart_win", w).apply();
                openDetail(null);
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
            if (!x.has() && !detailSel.contains(x)) continue;
            boolean sel = detailSel.contains(x);
            TextView c = pill((sel ? "✓ " : "") + I18n.t(x.label), sel ? Main.RED : Main.BTN, sel ? 0xFFFFFFFF : Main.TEXT);
            c.setTextSize(0, px(14));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (detailSel.contains(x)) { if (detailSel.size() > 1) detailSel.remove(x); }
                else { if (detailSel.size() >= 3) detailSel.remove(0); detailSel.add(x); }
                openDetail(null);
            }});
            chips.addView(c, lp(ViewGroup.LayoutParams.WRAP_CONTENT, px(38), 0, px(8)));
        }
        hs.addView(chips);
        d.addView(hs);

        for (int i = 0; i < detailSel.size(); i++) {
            ObdChart c = new ObdChart(this, detailSel.get(i), win, u, true);
            charts.add(c);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            if (i > 0) l.topMargin = px(10);
            d.addView(c, l);
        }
        TextView hint = tv(I18n.t("Desliza el dedo sobre un gráfico para ver el valor de cada momento. Toca los datos de arriba para comparar hasta 3."), 12, Main.MUTED, false);
        hint.setPadding(px(4), px(6), 0, 0);
        d.addView(hint);

        detail = d;
        frame.addView(d, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closeDetail() {
        if (detail != null) frame.removeView(detail);
        detail = null;
        charts.clear();
        detailSel.clear();
    }

    @Override public void onBackPressed() {
        if (detail != null) { closeDetail(); return; }
        super.onBackPressed();
    }

    // ───────────────────────── alertas ─────────────────────────

    private void showAlert(final Obd.Alert a) {
        if (a.key.startsWith("dtc_")) { showCode(a.key.substring(4)); return; }
        final Obd.Sensor sensor = sensorOf(a.key);
        TB b = (TB) new TB(this, Main.dlg()).raw().wide().setTitle(a.title).setMessage(a.advice);
        b.setPositiveButton(I18n.t("Preguntar a la IA"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { askAiAlert(a); } });
        if (sensor != null) b.setNeutralButton(I18n.t("Ajustar esta alerta"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { editAlert(sensor); } });
        b.setNegativeButton(I18n.t("Cerrar"), null);
        b.show();
    }

    private static Obd.Sensor sensorOf(String key) {
        for (Obd.Sensor s : Obd.ALL) if (s.key.equals(key)) return s;
        return "coolant_rise".equals(key) ? Obd.COOLANT : null;
    }

    private void askAiAlert(final Obd.Alert a) {
        if (!DtcInfo.aiReady(this)) { askAi(null); return; }
        final AlertDialog wait = new TB(this, Main.dlg()).setTitle(a.title).setMessage("Preguntando a la IA…").setNegativeButton("Cerrar", null).show();
        DtcInfo.explainAlert(this, a.title, new DtcInfo.AiCb() { public void on(String text, String error) {
            try { wait.dismiss(); } catch (Throwable ignored) { }
            if (isFinishing()) return;
            new TB(ObdActivity.this, Main.dlg()).raw().wide().setTitle(text != null ? a.title : I18n.t("No se pudo consultar la IA"))
                .setMessage(text != null ? text : I18n.t(error)).setNegativeButton(I18n.t("Cerrar"), null).show();
        }});
    }

    /** Ajustes → Alertas a vigilar: cada dato con alertas, la subida rápida del refrigerante y los códigos de falla. */
    private void alertSettings() {
        final List<Obd.Sensor> list = new ArrayList<Obd.Sensor>();
        List<String> labels = new ArrayList<String>();
        for (Obd.Sensor s : Obd.ALL) {
            if (s.def == null) s.load(prefs);
            if (!s.alerts() && (s.def == null || (Float.isNaN(s.def[0]) && Float.isNaN(s.def[2]) && Float.isNaN(s.def[4])))) continue;
            list.add(s);
            labels.add((s.enabled ? "✓  " : "✗  ") + I18n.t(s.label) + " · " + limits(s));
        }
        final int n = list.size();
        labels.add((prefs.getBoolean("obd_alert_coolant_rise", true) ? "✓  " : "✗  ") + I18n.t("Subida rápida del refrigerante") + " · 4 °C/min");
        labels.add((prefs.getBoolean("obd_alert_dtc", true) ? "✓  " : "✗  ") + I18n.t("Códigos de falla nuevos (aviso por voz)"));
        new TB(this, Main.dlg()).raw().wide().setTitle(I18n.t("Alertas a vigilar"))
            .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w < n) editAlert(list.get(w));
                else {
                    String key = w == n ? "obd_alert_coolant_rise" : "obd_alert_dtc";
                    prefs.edit().putBoolean(key, !prefs.getBoolean(key, true)).apply();
                    alertSettings();
                }
            }})
            .setNegativeButton(I18n.t("Cerrar"), null).show();
    }

    private String limits(Obd.Sensor s) {
        StringBuilder b = new StringBuilder();
        if (!Float.isNaN(s.hi1)) b.append(I18n.t("aviso")).append(' ').append(num(s.hi1)).append(" · ").append(I18n.t("alerta")).append(' ').append(num(s.hi2)).append(' ').append(s.unit);
        if (!Float.isNaN(s.lo1)) { if (b.length() > 0) b.append(" · "); b.append(I18n.t("bajo")).append(' ').append(num(s.lo1)).append(" / ").append(num(s.lo2)).append(' ').append(s.unit); }
        if (!Float.isNaN(s.abs1)) b.append("±").append(num(s.abs1)).append(" / ±").append(num(s.abs2)).append(" %");
        return b.toString();
    }

    private static String num(float v) { return v == Math.round(v) ? I18n.integer(Math.round(v)) : String.format(I18n.num, "%.1f", v); }

    /** Encender o apagar la alerta de un dato y cambiar sus umbrales. */
    private void editAlert(final Obd.Sensor s) {
        if (s.def == null) s.load(prefs);
        final List<String[]> fields = new ArrayList<String[]>();   // sufijo, nombre
        if (!Float.isNaN(s.def[0])) { fields.add(new String[]{"hi1", "Aviso sobre"}); fields.add(new String[]{"hi2", "Alerta sobre"}); }
        if (!Float.isNaN(s.def[2])) { fields.add(new String[]{"lo1", "Aviso bajo"}); fields.add(new String[]{"lo2", "Alerta bajo"}); }
        if (!Float.isNaN(s.def[4])) { fields.add(new String[]{"abs1", "Aviso sobre ±"}); fields.add(new String[]{"abs2", "Alerta sobre ±"}); }
        final float[] cur = {s.hi1, s.hi2, s.lo1, s.lo2, s.abs1, s.abs2};
        final String[] sufs = {"hi1", "hi2", "lo1", "lo2", "abs1", "abs2"};
        List<String> labels = new ArrayList<String>();
        labels.add(I18n.t("Vigilar") + ": " + I18n.t(s.enabled ? "Sí" : "No"));
        for (String[] f : fields) labels.add(I18n.t(f[1]) + ": " + num(cur[java.util.Arrays.asList(sufs).indexOf(f[0])]) + " " + s.unit);
        labels.add(I18n.t("Valores de fábrica"));
        new TB(this, Main.dlg()).raw().wide().setTitle(I18n.t(s.label))
            .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w == 0) { prefs.edit().putBoolean("obd_alert_" + s.key, !s.enabled).apply(); applyAlerts(); editAlert(s); }
                else if (w <= fields.size()) {
                    final String[] f = fields.get(w - 1);
                    final float v = cur[java.util.Arrays.asList(sufs).indexOf(f[0])];
                    askText(I18n.t(f[1]) + " (" + s.unit + ")", num(v).replace(" ", ""), "", new TextCb() { public void on(String t) {
                        try {
                            float nv = Float.parseFloat(t.trim().replace(',', '.'));
                            prefs.edit().putFloat("obd_thr_" + s.key + "_" + f[0], nv).apply();
                            applyAlerts();
                        } catch (Throwable e) { toast(I18n.t("Escribe un número")); }
                        editAlert(s);
                    }});
                } else {
                    SharedPreferences.Editor e = prefs.edit().remove("obd_alert_" + s.key);
                    for (String suf : sufs) e.remove("obd_thr_" + s.key + "_" + suf);
                    e.apply();
                    applyAlerts();
                    editAlert(s);
                }
            }})
            .setNegativeButton(I18n.t("Volver"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { alertSettings(); } })
            .show();
    }

    private void applyAlerts() {
        Obd.reloadPrefs(this);
        gridKey = "";
        onObd();
    }

    // ───────────────────────── códigos ─────────────────────────

    private void showCode(final String code) {
        final DtcInfo.Entry e = DtcInfo.get(this, code);
        StringBuilder m = new StringBuilder();
        m.append(DtcInfo.system(code)).append("\n\n");
        if (e.es != null) m.append(e.es).append("\n\n").append(I18n.t("Qué hacer")).append(": ").append(e.advice).append("\n\n");
        if (e.en != null) m.append("SAE: ").append(e.en).append("\n\n");
        if (Obd.freezeDone && code.equals(Obd.freezeCode) && Obd.freeze != null && !Obd.freeze.isEmpty()) {
            m.append(I18n.t("Datos del motor cuando se registró la falla")).append(":\n");
            for (java.util.Map.Entry<Integer, byte[]> f : Obd.freeze.entrySet()) {
                String v = Scan.value(f.getKey(), f.getValue());
                if (v != null) m.append("· ").append(I18n.t(Scan.name(f.getKey()))).append(": ").append(v).append('\n');
            }
            m.append('\n');
        } else if (Obd.running() && !Obd.freezeDone) Obd.requestFreeze();
        if (e.ai != null) m.append(I18n.t("Explicación de la IA")).append(":\n").append(e.ai);
        else if (e.es == null && e.en == null) m.append(I18n.t("Este código no está en la tabla sin conexión: suele ser un código propio de la marca. La IA puede explicarlo."));
        if (code.charAt(0) == 'B' || code.charAt(0) == 'C')
            m.append("\n\n").append(I18n.t("Ojo: los códigos de airbag, ABS y carrocería normalmente no se leen con OBD-II genérico; si aparece aquí es porque el auto lo reporta al computador del motor."));
        TB b = (TB) new TB(this, Main.dlg()).raw().wide().setTitle(code).setMessage(m.toString().trim());
        b.setNegativeButton(I18n.t("Cerrar"), null);
        b.setPositiveButton(I18n.t(e.ai != null ? "Volver a preguntar a la IA" : "Explicar con IA"), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { askAi(code); }
        });
        b.show();
    }

    private void askAi(final String code) {
        if (!DtcInfo.aiReady(this)) {
            new TB(this, Main.dlg()).setTitle("Explicar con IA")
                .setMessage("Para que una IA explique los códigos, pon tu API key (Claude, Groq, Gemini u otra) en ⚙ → Inteligencia artificial. Las explicaciones se guardan en el equipo y después funcionan sin internet.")
                .setPositiveButton("Configurar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { aiSettings(); } })
                .setNegativeButton("Cerrar", null).show();
            return;
        }
        final AlertDialog wait = new TB(this, Main.dlg()).setTitle(code).setMessage("Preguntando a la IA…").setNegativeButton("Cerrar", null).show();
        DtcInfo.explain(this, code, new DtcInfo.AiCb() { public void on(String text, String error) {
            try { wait.dismiss(); } catch (Throwable ignored) { }
            if (isFinishing()) return;
            if (text == null) {
                new TB(ObdActivity.this, Main.dlg()).setTitle("No se pudo consultar la IA").setMessage(I18n.t(error)).setNegativeButton("Cerrar", null).show();
                return;
            }
            sideKey = "";
            onObd();
            showCode(code);
        }});
    }

    private void confirmClear() {
        if (Obd.RPM.has() && Obd.RPM.value > 400) {
            new TB(this, Main.dlg()).setTitle("Borrar códigos").setMessage("Apaga el motor y deja solo el contacto puesto para borrar los códigos.")
                .setNegativeButton("Cerrar", null).show();
            return;
        }
        new TB(this, Main.dlg()).setTitle("¿Borrar los códigos de falla?")
            .setMessage("Se apaga la luz del motor y se borran los datos que usa el taller para diagnosticar; también se reinician los monitores de emisiones. No se puede deshacer y, si la falla sigue, el código volverá a aparecer. Anota los códigos antes.")
            .setPositiveButton("Borrar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { Obd.requestClear(); } })
            .setNegativeButton("Cancelar", null).show();
    }

    // ───────────────────────── conexión y ajustes ─────────────────────────

    private void toggle() {
        if (Obd.running()) ObdService.stop(this);
        else ObdService.start(this);
        statusTv.postDelayed(new Runnable() { public void run() { onObd(); } }, 300);
    }

    private void settings() {
        String addr = prefs.getString("obd_addr", "demo");
        String[] items = {
            I18n.t("Escáner") + ": " + ("demo".equals(addr) ? I18n.t("Auto simulado") : prefs.getString("obd_name", addr)),
            I18n.t("Mi vehículo (para la IA)") + ": " + (prefs.getString("obd_vehicle", "").length() > 0 ? prefs.getString("obd_vehicle", "") : "—"),
            I18n.t("Inteligencia artificial") + ": " + (DtcInfo.aiReady(this) ? I18n.t("Configurada") : I18n.t("Sin configurar")),
            I18n.t("Avisos por voz") + ": " + I18n.t(prefs.getBoolean("obd_voice", true) ? "Sí" : "No"),
            I18n.t("Encender el monitor al iniciar StradaOS") + ": " + I18n.t(prefs.getBoolean("obd_autostart", false) ? "Sí" : "No"),
            I18n.t("Cilindrada del motor") + ": " + String.format(Locale.US, "%.1f L", prefs.getFloat("obd_displacement", 2.4f)),
            I18n.t("Protocolo") + ": " + protocolName(prefs.getString("obd_protocol", "auto")),
            I18n.t("Registro de la conexión"),
            I18n.t("Escanear todo lo que entrega el auto"),
            I18n.t("Alertas a vigilar"),
        };
        new TB(this, Main.dlg()).raw().wide().setTitle(I18n.t("Ajustes del vehículo"))
            .setItems(items, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w == 0) pickAdapter();
                else if (w == 1) askText(I18n.t("Tu vehículo: marca, modelo, año, motor y caja"), prefs.getString("obd_vehicle", ""),
                        "Ej.: Kia Sorento 2010 2.4 bencina 4x4 automático", new TextCb() { public void on(String v) {
                    prefs.edit().putString("obd_vehicle", v.trim()).apply(); settings(); }});
                else if (w == 2) aiSettings();
                else if (w == 3) { prefs.edit().putBoolean("obd_voice", !prefs.getBoolean("obd_voice", true)).apply(); settings(); }
                else if (w == 4) { prefs.edit().putBoolean("obd_autostart", !prefs.getBoolean("obd_autostart", false)).apply(); settings(); }
                else if (w == 5) askDisplacement();
                else if (w == 6) pickProtocol();
                else if (w == 7) showLog();
                else if (w == 8) scanAll();
                else alertSettings();
            }})
            .setNegativeButton(I18n.t("Cerrar"), null).show();
    }

    private String protocolName(String p) {
        if ("6".equals(p)) return I18n.t("CAN 11 bits · 500 kbps");
        if ("7".equals(p)) return I18n.t("CAN 29 bits · 500 kbps");
        if ("8".equals(p)) return I18n.t("CAN 11 bits · 250 kbps");
        if ("9".equals(p)) return I18n.t("CAN 29 bits · 250 kbps");
        return I18n.t("Automático");
    }

    /** Cilindrada para calcular el consumo con el MAP en motores que no traen MAF (Kia Sorento 2.4: 2,4 L). */
    private void askDisplacement() {
        askText(I18n.t("Cilindrada del motor (litros)"),
            String.format(Locale.US, "%.1f", prefs.getFloat("obd_displacement", 2.4f)), "2.4", new TextCb() { public void on(String v) {
                try {
                    float n = Float.parseFloat(v.trim().replace(',', '.'));
                    if (n < 0.5f || n > 8f) { toast(I18n.t("Escribe un número entre 0,5 y 8")); return; }
                    prefs.edit().putFloat("obd_displacement", n).apply();
                    settings();
                } catch (Throwable e) { toast(I18n.t("Escribe un número")); }
            }});
    }

    /** Protocolo del escáner: Automático o uno de los CAN forzados (el Sorento 2010 usa CAN 11 bits/500 kbps). */
    private void pickProtocol() {
        final String[] keys = {"auto", "6", "7", "8", "9"};
        String[] labels = {
            I18n.t("Automático"), I18n.t("CAN 11 bits · 500 kbps"), I18n.t("CAN 29 bits · 500 kbps"),
            I18n.t("CAN 11 bits · 250 kbps"), I18n.t("CAN 29 bits · 250 kbps")
        };
        int cur = 0;
        String v = prefs.getString("obd_protocol", "auto");
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(v)) cur = i;
        new TB(this, Main.dlg()).raw().wide().setTitle(I18n.t("Protocolo"))
            .setSingleChoiceItems(labels, cur, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                d.dismiss();
                prefs.edit().putString("obd_protocol", keys[w]).apply();
                if (Obd.running()) { ObdService.stop(ObdActivity.this); ObdService.start(ObdActivity.this); }
                settings();
            }})
            .setNegativeButton(I18n.t("Cancelar"), null).show();
    }

    private void pickAdapter() {
        final List<String[]> opts = new ArrayList<String[]>();   // dirección, nombre
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null) for (BluetoothDevice d : a.getBondedDevices()) {
                String n = d.getName() != null ? d.getName() : d.getAddress();
                String[] o = {"bt:" + d.getAddress(), n};
                // los escáneres primero
                if (n.toUpperCase(Locale.US).matches(".*(OBD|ELM|VLINK|V-LINK|VGATE|ICAR|KONNWEI|VEEPEAK|SCAN).*")) opts.add(0, o);
                else opts.add(o);
            }
        } catch (Throwable ignored) { }
        final int nBt = opts.size();
        String[] labels = new String[nBt + 3];
        for (int i = 0; i < nBt; i++) labels[i] = "Bluetooth · " + opts.get(i)[1];
        labels[nBt] = "🔍  " + I18n.t("Buscar escáneres Bluetooth cercanos");
        labels[nBt + 1] = I18n.t("Escáner Wi-Fi (escribir IP y puerto)");
        labels[nBt + 2] = I18n.t("Auto simulado (para probar)");
        new TB(this, Main.dlg()).raw().wide()
            .setTitle(I18n.t(nBt == 0 ? "No hay escáneres Bluetooth emparejados: emparéjalo en los ajustes del equipo" : "Elige tu escáner"))
            .setItems(labels, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w < nBt) choose(opts.get(w)[0], opts.get(w)[1]);
                else if (w == nBt) discover();
                else if (w == nBt + 1) {
                    String cur = prefs.getString("obd_addr", "");
                    askText(I18n.t("IP y puerto del escáner Wi-Fi"), cur.startsWith("tcp:") ? cur.substring(4) : "192.168.0.10:35000",
                        "192.168.0.10:35000", new TextCb() { public void on(String v) {
                            v = v.trim();
                            if (!v.matches("[^:\\s]+:\\d{1,5}")) { toast(I18n.t("Escribe IP:puerto, por ejemplo 192.168.0.10:35000")); return; }
                            choose("tcp:" + v, "Wi-Fi " + v);
                        }});
                } else choose("demo", I18n.t("Auto simulado"));
            }})
            .setNegativeButton(I18n.t("Cancelar"), null).show();
    }

    // ── vinculación: buscar escáneres cercanos y emparejarlos sin salir de StradaOS ──

    private android.content.BroadcastReceiver btRx;
    private AlertDialog btDlg;
    private final List<BluetoothDevice> found = new ArrayList<BluetoothDevice>();
    private BluetoothDevice pairing;

    private void discover() {
        final BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) { toast(I18n.t("Este equipo no tiene Bluetooth")); return; }
        if (!a.isEnabled()) {
            try { startActivity(new android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); } catch (Throwable e) { toast(I18n.t("El Bluetooth está apagado")); }
            return;
        }
        // Android 6+: buscar equipos Bluetooth pide el permiso de ubicación
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 41);
            return;
        }
        found.clear();
        stopDiscovery();
        btRx = new android.content.BroadcastReceiver() { public void onReceive(Context c, android.content.Intent i) {
            String act = i.getAction();
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_FOUND.equals(act) && d != null) {
                for (BluetoothDevice x : found) if (x.getAddress().equals(d.getAddress())) return;
                found.add(d);
                showFound(false);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(act)) {
                showFound(true);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(act) && d != null && pairing != null && d.getAddress().equals(pairing.getAddress())) {
                if (d.getBondState() == BluetoothDevice.BOND_BONDED) {
                    toast(I18n.t("Emparejado") + ": " + name(d));
                    pairing = null;
                    stopDiscovery();
                    choose("bt:" + d.getAddress(), name(d));
                } else if (d.getBondState() == BluetoothDevice.BOND_NONE) {
                    pairing = null;
                    toast(I18n.t("No se pudo emparejar. Prueba con la clave 1234 o 0000."));
                }
            }
        }};
        android.content.IntentFilter f = new android.content.IntentFilter(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(btRx, f);
        a.startDiscovery();
        showFound(false);
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == 41 && res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) discover();
        else if (req == 41) toast(I18n.t("Sin el permiso de ubicación Android no deja buscar equipos Bluetooth"));
    }

    private static String name(BluetoothDevice d) {
        String n = null;
        try { n = d.getName(); } catch (Throwable ignored) { }
        return n != null && n.length() > 0 ? n : d.getAddress();
    }

    /** Lista de lo encontrado (se rearma con cada equipo nuevo); los que parecen escáneres OBD van primero. */
    private void showFound(boolean done) {
        if (isFinishing()) return;
        try { if (btDlg != null) btDlg.dismiss(); } catch (Throwable ignored) { }
        final List<BluetoothDevice> list = new ArrayList<BluetoothDevice>();
        for (BluetoothDevice d : found) {
            if (name(d).toUpperCase(Locale.US).matches(".*(OBD|ELM|VLINK|V-LINK|VGATE|ICAR|KONNWEI|VEEPEAK|SCAN|327).*")) list.add(0, d);
            else list.add(d);
        }
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            BluetoothDevice d = list.get(i);
            boolean obd = name(d).toUpperCase(Locale.US).matches(".*(OBD|ELM|VLINK|V-LINK|VGATE|ICAR|KONNWEI|VEEPEAK|SCAN|327).*");
            labels[i] = (obd ? "★ " : "") + name(d) + (d.getBondState() == BluetoothDevice.BOND_BONDED ? "  · " + I18n.t("emparejado") : "");
        }
        TB b = (TB) new TB(this, Main.dlg()).raw().wide()
            .setTitle(I18n.t(done ? (list.isEmpty() ? "No se encontraron equipos. ¿Está el escáner enchufado y el contacto puesto?" : "Elige tu escáner")
                : "Buscando… (enchufa el escáner y pon el contacto)"));
        if (labels.length > 0) b.setItems(labels, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            BluetoothDevice dev = list.get(w);
            if (dev.getBondState() == BluetoothDevice.BOND_BONDED) { stopDiscovery(); choose("bt:" + dev.getAddress(), name(dev)); return; }
            pairing = dev;
            try { BluetoothAdapter.getDefaultAdapter().cancelDiscovery(); } catch (Throwable ignored) { }
            boolean ok = false;
            try { ok = (Boolean) dev.getClass().getMethod("createBond").invoke(dev); } catch (Throwable ignored) { }   // API 19 (en 4.x antiguos por reflexión)
            if (ok) toast(I18n.t("Emparejando… si pide clave, escribe 1234 o 0000"));
            else {
                pairing = null;
                toast(I18n.t("Emparéjalo en los ajustes de Bluetooth del equipo"));
                try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); } catch (Throwable ignored) { }
            }
        }});
        else b.setMessage(I18n.t(done ? "Los escáneres suelen llamarse OBDII, OBD2, V-LINK o Vgate." : "Los escáneres suelen llamarse OBDII, OBD2, V-LINK o Vgate."));
        if (done) b.setPositiveButton(I18n.t("Buscar de nuevo"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { discover(); } });
        b.setNegativeButton(I18n.t("Cerrar"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { if (pairing == null) stopDiscovery(); } });
        btDlg = b.show();
    }

    private void stopDiscovery() {
        try { BluetoothAdapter.getDefaultAdapter().cancelDiscovery(); } catch (Throwable ignored) { }
        if (btRx != null) try { unregisterReceiver(btRx); } catch (Throwable ignored) { }
        btRx = null;
    }

    @Override protected void onDestroy() {
        stopDiscovery();
        super.onDestroy();
    }

    private void choose(String addr, String name) {
        prefs.edit().putString("obd_addr", addr).putString("obd_name", name).apply();
        if (Obd.running()) ObdService.stop(this);
        ObdService.start(this);
        gridKey = "";
        onObd();
    }

    private void aiSettings() {
        final String[] pr = DtcInfo.preset(this);
        final boolean custom = "custom".equals(pr[0]);
        final List<String> list = new ArrayList<String>();
        list.add(I18n.t("Proveedor") + ": " + I18n.t(pr[1]));
        list.add(I18n.t("API key") + ": " + mask(prefs.getString("ai_key", "")));
        list.add(I18n.t("Modelo") + ": " + (DtcInfo.model(this).length() > 0 ? DtcInfo.model(this) : I18n.t("elegir…")));
        if (custom) list.add(I18n.t("Dirección de la API") + ": " + (DtcInfo.baseUrl(this).length() > 0 ? DtcInfo.baseUrl(this) : "—"));
        final int iVin = list.size();
        list.add(I18n.t("Enviar el VIN a la IA") + ": " + I18n.t(prefs.getBoolean("obd_ai_vin", false) ? "Sí" : "No"));
        final int iTest = list.size();
        list.add("▶  " + I18n.t("Probar la IA"));
        final int iDelete = list.size();
        list.add(I18n.t("Borrar la API key"));
        new TB(this, Main.dlg()).raw().wide().setTitle(I18n.t("Inteligencia artificial"))
            .setMessage(I18n.t("La IA recibe solo el código, el vehículo que escribiste y los datos del motor; nunca tu ubicación. El VIN solo se envía si lo activas aquí. Cada consulta la cobra tu proveedor con tu API key."))
            .setItems(list.toArray(new String[0]), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w == 0) pickProvider();
                else if (w == 1) askText(I18n.t("API key"), "", "anthropic".equals(pr[0]) ? "sk-ant-…" : "groq".equals(pr[0]) ? "gsk_…" : "sk-…", new TextCb() { public void on(String v) {
                    prefs.edit().putString("ai_key", v.trim()).apply(); aiSettings(); }});
                else if (w == 2) pickModel();
                else if (custom && w == 3) askText(I18n.t("Dirección de la API"), DtcInfo.baseUrl(ObdActivity.this), "https://…/v1", new TextCb() { public void on(String v) {
                    prefs.edit().putString("ai_url", v.trim()).apply(); aiSettings(); }});
                else if (w == iVin) {
                    boolean on = !prefs.getBoolean("obd_ai_vin", false);
                    prefs.edit().putBoolean("obd_ai_vin", on).apply();
                    toast(I18n.t(on ? "El VIN se envía con cada consulta: identifica tu auto de forma única" : "El VIN no se envía"));
                    aiSettings();
                }
                else if (w == iTest) testAi();
                else if (w == iDelete) { prefs.edit().remove("ai_key").apply(); aiSettings(); }
            }})
            .setNegativeButton(I18n.t("Cerrar"), null).show();
    }

    private void pickProvider() {
        String[] names = new String[DtcInfo.PRESETS.length];
        int cur = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = I18n.t(DtcInfo.PRESETS[i][1]);
            if (DtcInfo.PRESETS[i][0].equals(DtcInfo.preset(this)[0])) cur = i;
        }
        new TB(this, Main.dlg()).raw().setTitle(I18n.t("Proveedor"))
            .setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                d.dismiss();
                // otro proveedor: el modelo y la key del anterior no sirven
                if (!DtcInfo.PRESETS[w][0].equals(DtcInfo.preset(ObdActivity.this)[0]))
                    prefs.edit().putString("ai_preset", DtcInfo.PRESETS[w][0]).remove("ai_model").remove("ai_key").apply();
                aiSettings();
            }})
            .setNegativeButton(I18n.t("Cancelar"), null).show();
    }

    /** Lista de modelos que ofrece el proveedor (pide la API key primero); siempre se puede escribir otro a mano. */
    private void pickModel() {
        if (!DtcInfo.aiReady(this)) { toast(I18n.t("Primero escribe la API key")); aiSettings(); return; }
        final AlertDialog wait = new TB(this, Main.dlg()).setTitle("Modelo").setMessage("Buscando los modelos del proveedor…").setNegativeButton("Cerrar", null).show();
        DtcInfo.models(this, new DtcInfo.AiCb() { public void on(String text, String error) {
            try { wait.dismiss(); } catch (Throwable ignored) { }
            if (isFinishing()) return;
            final List<String> ids = new ArrayList<String>();
            if (text != null) for (String id : text.split("\n")) if (id.length() > 0) ids.add(id);
            if (error != null) toast(I18n.t(error));
            final String rec = DtcInfo.preset(ObdActivity.this)[3];
            String[] labels = new String[ids.size() + 1];
            int cur = -1;
            for (int i = 0; i < ids.size(); i++) {
                labels[i] = ids.get(i) + (ids.get(i).equals(rec) ? "  · " + I18n.t("recomendado") : "");
                if (ids.get(i).equals(DtcInfo.model(ObdActivity.this))) cur = i;
            }
            labels[ids.size()] = I18n.t("Escribir otro…");
            new TB(ObdActivity.this, Main.dlg()).raw().wide().setTitle(I18n.t("Modelo"))
                .setSingleChoiceItems(labels, cur, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    d.dismiss();
                    if (w < ids.size()) { prefs.edit().putString("ai_model", ids.get(w)).apply(); aiSettings(); }
                    else askText(I18n.t("Modelo"), DtcInfo.model(ObdActivity.this), "", new TextCb() { public void on(String v) {
                        prefs.edit().putString("ai_model", v.trim()).apply(); aiSettings(); }});
                }})
                .setNegativeButton(I18n.t("Cancelar"), null).show();
        }});
    }

    private void testAi() {
        if (!DtcInfo.aiReady(this)) { toast(I18n.t("Primero escribe la API key")); aiSettings(); return; }
        final AlertDialog wait = new TB(this, Main.dlg()).setTitle("Probar la IA").setMessage("Preguntando por el código de ejemplo P0711…").setNegativeButton("Cerrar", null).show();
        DtcInfo.test(this, new DtcInfo.AiCb() { public void on(String text, String error) {
            try { wait.dismiss(); } catch (Throwable ignored) { }
            if (isFinishing()) return;
            new TB(ObdActivity.this, Main.dlg()).raw().wide()
                .setTitle(I18n.t(text != null ? "La IA funciona · P0711 de ejemplo" : "No se pudo consultar la IA"))
                .setMessage(text != null ? text : I18n.t(error))
                .setPositiveButton(I18n.t("Volver"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { aiSettings(); } })
                .setNegativeButton(I18n.t("Cerrar"), null).show();
        }});
    }

    private static String mask(String k) {
        k = k.trim();
        if (k.length() == 0) return "—";
        return k.length() <= 8 ? "••••" : k.substring(0, 4) + "••••" + k.substring(k.length() - 4);
    }

    /** Pide el escaneo completo y muestra el informe cuando termina (el avance se ve en la línea de estado). */
    private void scanAll() {
        if (Obd.report != null && !Obd.scanning && waitingScan) { showReport(); return; }
        if (Obd.status != Obd.LIVE) {
            new TB(this, Main.dlg()).setTitle("Escanear todo lo que entrega el auto")
                .setMessage("Conecta el escáner con el contacto puesto (mejor con el motor en marcha) y vuelve a intentarlo.")
                .setNegativeButton("Cerrar", null).show();
            return;
        }
        new TB(this, Main.dlg()).setTitle("Escanear todo lo que entrega el auto")
            .setMessage("Pregunta al auto por cada dato estándar y por los datos propios de la marca del motor y la caja. Tarda hasta 2 minutos y mientras tanto las alertas se pausan: hazlo detenido. Solo lee, no cambia nada en el auto.")
            .setPositiveButton("Escanear", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                Obd.report = null;
                waitingScan = true;
                Obd.requestScan();
            }})
            .setNegativeButton("Cancelar", null).show();
    }

    private boolean waitingScan;

    private void showReport() {
        waitingScan = false;
        String text = Obd.report + (Obd.reportPath != null ? "\n\n" + I18n.t("Guardado en") + ":\n" + Obd.reportPath : "");
        new TB(this, Main.dlg()).raw().wide().setTitle(I18n.t("Lo que entrega tu auto")).setMessage(text)
            .setNegativeButton(I18n.t("Cerrar"), null).show();
    }

    private void showLog() {
        StringBuilder b = new StringBuilder();
        b.append(I18n.t("Escáner")).append(": ").append(Obd.adapterInfo.length() > 0 ? Obd.adapterInfo : "—").append('\n');
        synchronized (Obd.LOG) { for (String l : Obd.LOG) b.append(l).append('\n'); }
        new TB(this, Main.dlg()).raw().wide().setTitle(I18n.t("Registro de la conexión")).setMessage(b.toString().trim())
            .setNegativeButton(I18n.t("Cerrar"), null).show();
    }

    interface TextCb { void on(String v); }

    private void askText(String title, String value, String hint, final TextCb cb) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setText(value);
        et.setHint(I18n.t(hint));
        et.setTextColor(Main.TEXT);
        et.setHintTextColor(Main.MUTED);
        et.setSelection(et.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(px(4), px(8), px(4), 0);
        box.addView(et);
        new TB(this, Main.dlg()).raw().wide().setTitle(title).setView(box)
            .setPositiveButton(I18n.t("Guardar"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { cb.on(et.getText().toString()); } })
            .setNegativeButton(I18n.t("Cancelar"), null).show();
    }

    private void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show(); }

    // ───────────────────────── piezas de la interfaz ─────────────────────────

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
        t.setBackground(round(bg, 12));
        return t;
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(r));
        return g;
    }

    /** Tarjeta de alerta: fondo de tarjeta con borde del color de la gravedad (más grueso si es reciente). */
    private GradientDrawable stripe(int color, boolean fresh) {
        GradientDrawable g = round(Main.CARD, 14);
        g.setStroke(px(fresh ? 3 : 1.5f), color);
        return g;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(14), px(12), px(14), px(12));
        c.setBackground(round(Main.CARD, 14));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.bottomMargin = px(8);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int left, int right) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
        l.leftMargin = left;
        l.rightMargin = right;
        return l;
    }

    private TextView section(String s) {
        TextView t = tv(s.toUpperCase(I18n.loc), 13, Main.RED, true);
        t.setPadding(px(4), px(side.getChildCount() > 0 ? 12 : 0), 0, px(6));
        return t;
    }

    private TextView note(String s) {
        TextView t = tv(s, 14, Main.MUTED, false);
        t.setPadding(px(4), 0, 0, px(6));
        return t;
    }

    /**
     * Tarjeta de un sensor: nombre, valor grande, barra con las zonas de alerta y el gráfico del último minuto y medio.
     * El borde se pinta ámbar o rojo cuando el valor está fuera de rango.
     */
    class Tile extends View {
        final Obd.Sensor s;
        final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), lim = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final Path p = new Path();
        long drawn = -1;
        int drawnLevel = -1;

        Tile(Context c, Obd.Sensor s) {
            super(c);
            this.s = s;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(px(2.5f));
            line.setStrokeJoin(Paint.Join.ROUND);
            lim.setStyle(Paint.Style.STROKE);
            lim.setStrokeWidth(px(1.5f));
            lim.setPathEffect(new DashPathEffect(new float[]{px(6), px(5)}, 0));
            setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openDetail(Tile.this.s); } });
            setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { explain(); return true; } });
        }

        int color(int level) { return level == Obd.CRIT ? DANGER : level == Obd.WARN ? AMBER : Main.RED; }

        float y(float v, float top, float h) {
            float f = (v - s.min) / (s.max - s.min);
            return top + h - Math.max(0, Math.min(1, f)) * h;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), pad = px(14);
            int lvl = s.level;
            drawn = s.pushes;
            drawnLevel = lvl;
            bg.setStyle(Paint.Style.FILL);
            bg.setColor(Main.CARD);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, px(14), px(14), bg);
            if (lvl != Obd.OK) {
                bg.setStyle(Paint.Style.STROKE);
                bg.setStrokeWidth(px(3));
                bg.setColor(color(lvl));
                r.inset(px(1.5f), px(1.5f));
                c.drawRoundRect(r, px(13), px(13), bg);
            }
            // nombre y valor
            txt.setTypeface(Typeface.DEFAULT);
            txt.setTextSize(px(14));
            txt.setColor(Main.SOFT);
            c.drawText(I18n.t(s.label), pad, pad + px(12), txt);
            txt.setTypeface(Typeface.DEFAULT_BOLD);
            txt.setTextSize(px(38));
            txt.setColor(lvl == Obd.OK ? Main.TEXT : color(lvl));
            String v = s.text();
            c.drawText(v, pad, pad + px(54), txt);
            float vw = txt.measureText(v);
            txt.setTypeface(Typeface.DEFAULT);
            txt.setTextSize(px(15));
            txt.setColor(Main.MUTED);
            c.drawText(s.unit, pad + vw + px(6), pad + px(54), txt);
            // gráfico
            float gt = pad + px(66), gh = h - gt - px(10), gl = pad, gw = w - pad * 2;
            if (gh < px(20)) return;
            lim.setColor(AMBER);
            for (float t : new float[]{s.hi1, s.lo1}) if (!Float.isNaN(t)) c.drawLine(gl, y(t, gt, gh), gl + gw, y(t, gt, gh), lim);
            if (!Float.isNaN(s.abs1)) {
                c.drawLine(gl, y(s.abs1, gt, gh), gl + gw, y(s.abs1, gt, gh), lim);
                c.drawLine(gl, y(-s.abs1, gt, gh), gl + gw, y(-s.abs1, gt, gh), lim);
            }
            int n = s.histN;
            if (n < 2) return;
            p.reset();
            float step = gw / (s.hist.length - 1);
            int first = s.hist.length - n;
            for (int i = first; i < s.hist.length; i++) {
                float x = gl + i * step, yy = y(s.hist[i], gt, gh);
                if (i == first) p.moveTo(x, yy); else p.lineTo(x, yy);
            }
            line.setColor(color(lvl));
            c.drawPath(p, line);
            p.lineTo(gl + gw, gt + gh);
            p.lineTo(gl + first * step, gt + gh);
            p.close();
            fill.setColor((color(lvl) & 0x00FFFFFF) | 0x30000000);
            c.drawPath(p, fill);
        }

        void explain() {
            StringBuilder m = new StringBuilder();
            if (!Float.isNaN(s.hi1)) m.append(I18n.t("Aviso sobre")).append(' ').append(fmt(s.hi1)).append(' ').append(s.unit)
                .append(" · ").append(I18n.t("alerta sobre")).append(' ').append(fmt(s.hi2)).append(' ').append(s.unit).append('\n');
            if (!Float.isNaN(s.lo1)) m.append(I18n.t("Aviso bajo")).append(' ').append(fmt(s.lo1)).append(' ').append(s.unit)
                .append(" · ").append(I18n.t("alerta bajo")).append(' ').append(fmt(s.lo2)).append(' ').append(s.unit).append('\n');
            if (!Float.isNaN(s.abs1)) m.append(I18n.t("Aviso sobre ±")).append(fmt(s.abs1)).append(" % · ").append(I18n.t("alerta sobre ±")).append(fmt(s.abs2)).append(" %\n");
            if (s == Obd.VOLT) m.append(I18n.t("Con el motor en marcha debe estar entre 13,5 y 14,8 V. Con el motor apagado, 12,4 a 12,8 V es normal.")).append('\n');
            if (s == Obd.COOLANT) m.append(I18n.t("Normal en marcha: 85 a 98 °C. También avisa si sube más de 4 °C por minuto sobre 95 °C.")).append('\n');
            if (s == Obd.STFT || s == Obd.LTFT) m.append(I18n.t("Cuánto corrige el computador la mezcla. Positivo = le falta combustible (fuga de aire); negativo = le sobra.")).append('\n');
            if (m.length() == 0) m.append(I18n.t("Solo informativo: este dato no genera alertas."));
            new TB(ObdActivity.this, Main.dlg()).raw().setTitle(I18n.t(s.label)).setMessage(m.toString().trim()).setNegativeButton(I18n.t("Cerrar"), null).show();
        }

        String fmt(float v) { return v == Math.round(v) ? I18n.integer(Math.round(v)) : String.format(I18n.num, "%.1f", v); }
    }
}
