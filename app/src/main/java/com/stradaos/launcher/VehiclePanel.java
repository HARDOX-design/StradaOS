package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel del vehículo (OBD-II) que se despliega desde un lado sobre cualquier tema, sin tocar el inicio: ocupa ~75 %
 * del ancho y deja ver el tema difuminado detrás. Muestra solo lo importante: tres medidores (refrigerante, batería,
 * RPM), tres datos chicos, las alertas activas y los códigos de falla. Lo demás está en Ajustes → Tu vehículo.
 *
 * El difuminado funciona desde Android 4.1: al abrir se toma una foto del inicio en baja resolución y se difumina
 * una sola vez (unos milisegundos incluso en procesadores sencillos).
 */
class VehiclePanel extends FrameLayout implements Obd.Listener {

    interface Host {
        /** Ajustes → Tu vehículo → Escáner OBD-II (sub = página: "obd", "obd_codes"…). */
        void openSettings(String sub);
        /** Gráfico grande de un dato. */
        void openChart(String key);
        ObdDialogs dialogs();
        /** Vista que se fotografía para el fondo difuminado (el inicio). */
        View backdrop();
        void onClosed();
    }

    private static final Obd.Sensor[] BIG = {Obd.COOLANT, Obd.VOLT, Obd.RPM};

    private final float k;
    private final Host host;
    private final boolean left;
    private final ImageView blur;
    private final View dim;
    private final LinearLayout sheet;
    private final float sheetW;
    private final TextView status;
    private final Ring[] rings = new Ring[BIG.length];
    private final LinearLayout minis, alertsBox, codesBox;
    private final TextView milPill;
    private String listKey = "", miniKey = "";
    private boolean open;
    private Bitmap shot;

    VehiclePanel(Context c, float scale, float boardW, float boardH, boolean left, Host host) {
        super(c);
        this.k = scale;
        this.host = host;
        this.left = left;
        setVisibility(GONE);
        setClickable(true);

        blur = new ImageView(c);
        blur.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(blur, new LayoutParams(-1, -1));
        dim = new View(c);
        dim.setBackgroundColor(Main.LIGHT ? 0x66FFFFFF : 0x77000000);
        addView(dim, new LayoutParams(-1, -1));
        dim.setOnClickListener(new OnClickListener() { public void onClick(View v) { close(); } });

        boolean narrow = boardW < boardH;
        sheetW = (narrow ? 0.92f : 0.75f) * boardW;
        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(px(20), px(16), px(20), px(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((Main.CARD & 0x00FFFFFF) | (Main.LIGHT ? 0xF2000000 : 0xEB000000));
        bg.setCornerRadius(px(24));
        bg.setStroke(Math.max(1, px(1)), Main.LIGHT ? 0x14000000 : 0x1FFFFFFF);
        sheet.setBackground(bg);
        sheet.setClickable(true);   // los toques dentro del panel no lo cierran
        LayoutParams slp = new LayoutParams(px(sheetW), -1);
        slp.gravity = left ? Gravity.LEFT : Gravity.RIGHT;
        slp.topMargin = slp.bottomMargin = px(14);
        if (left) slp.leftMargin = px(14); else slp.rightMargin = px(14);
        addView(sheet, slp);
        // deslizar el panel hacia su borde lo cierra
        sheet.setOnTouchListener(new OnTouchListener() {
            float x0; boolean drag;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getRawX(); drag = false; }
                else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dx = e.getRawX() - x0;
                    if (Math.abs(dx) > px(24)) drag = true;
                    if (drag) sheet.setTranslationX(left ? Math.min(0, dx) : Math.max(0, dx));
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    if (drag && Math.abs(sheet.getTranslationX()) > px(90)) close();
                    else sheet.animate().translationX(0).setDuration(160).start();
                }
                return drag;
            }
        });

        // ── encabezado ──
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(tv(I18n.t("Vehículo"), 22, Main.TEXT, true));
        status = tv("", 13, Main.MUTED, false);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(status);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        milPill = pill(I18n.t("Luz del motor"), ObdDialogs.DANGER, 0xFFFFFFFF);
        milPill.setVisibility(GONE);
        head.addView(milPill, lp(-2, px(36), px(8), 0));
        TextView set = pill(I18n.t("Ajustes"), Main.BTN, Main.TEXT);
        set.setOnClickListener(new OnClickListener() { public void onClick(View v) { closeNow(); VehiclePanel.this.host.openSettings("obd"); } });
        head.addView(set, lp(-2, px(40), px(8), 0));
        TextView x = pill("✕", Main.BTN, Main.TEXT);
        x.setOnClickListener(new OnClickListener() { public void onClick(View v) { close(); } });
        head.addView(x, lp(px(44), px(40), px(8), 0));
        sheet.addView(head);

        // ── tres medidores ──
        LinearLayout gauges = new LinearLayout(c);
        for (int i = 0; i < BIG.length; i++) {
            final Obd.Sensor sensor = BIG[i];
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, px(10), 0, px(8));
            cell.setBackground(round(Main.LIGHT ? 0x0A000000 : 0x0FFFFFFF, 18));
            rings[i] = new Ring(c, sensor);
            cell.addView(rings[i], new LinearLayout.LayoutParams(px(118), px(118)));
            TextView l = tv(I18n.t(sensor.label), 13, Main.SOFT, false);
            l.setGravity(Gravity.CENTER);
            cell.addView(l, new LinearLayout.LayoutParams(-1, -2));
            cell.setOnClickListener(new OnClickListener() { public void onClick(View v) { closeNow(); VehiclePanel.this.host.openChart(sensor.key); } });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) cl.leftMargin = px(12);
            gauges.addView(cell, cl);
        }
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = px(14);
        sheet.addView(gauges, gl);

        // ── datos chicos (los que el auto entrega) ──
        minis = new LinearLayout(c);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, px(58));
        ml.topMargin = px(10);
        sheet.addView(minis, ml);

        // ── alertas y códigos ──
        LinearLayout cols = new LinearLayout(c);
        alertsBox = new LinearLayout(c);
        alertsBox.setOrientation(LinearLayout.VERTICAL);
        codesBox = new LinearLayout(c);
        codesBox.setOrientation(LinearLayout.VERTICAL);
        cols.addView(alertsBox, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams cbl = new LinearLayout.LayoutParams(0, -1, 1);
        cbl.leftMargin = px(12);
        cols.addView(codesBox, cbl);
        LinearLayout.LayoutParams colsLp = new LinearLayout.LayoutParams(-1, 0, 1);
        colsLp.topMargin = px(12);
        sheet.addView(cols, colsLp);
    }

    boolean isOpen() { return open; }

    /**
     * Cuánto tarda en este equipo dibujar el inicio difuminado (ms, -1 = aún no se sabe). En los equipos rápidos
     * (celulares, pantallas nuevas) el difuminado se calcula antes y entra junto con el panel, en un solo movimiento.
     * En los lentos (Android 4, AC8227) el panel entra primero y el difuminado aparece cuando ya llegó, para que la
     * animación no se trabe.
     */
    private static long shotMs = -1;

    private static boolean slowShot() {
        if (Build.VERSION.SDK_INT < 21) return true;    // Android 4: siempre en dos tiempos (así quedó fluido en el Picanto)
        return shotMs > 28;                            // Android 5+: en un solo movimiento, salvo que el equipo resulte lento
    }

    void open() {
        if (open) return;
        open = true;
        boolean deferred = slowShot();
        blur.setImageDrawable(null);
        blur.setAlpha(0f); dim.setAlpha(0f);
        if (!deferred) timedShot();
        setVisibility(VISIBLE);
        Obd.addListener(this);
        listKey = ""; miniKey = "";
        onObd();
        float from = (left ? -1 : 1) * (px(sheetW) + px(30));
        sheet.setTranslationX(from);
        android.view.ViewPropertyAnimator an = sheet.animate().translationX(0).setDuration(240)
            .setInterpolator(new DecelerateInterpolator(1.6f)).withLayer();
        if (deferred) an.withEndAction(new Runnable() { public void run() { post(new Runnable() { public void run() {
            if (!open) return;
            timedShot();
            blur.animate().alpha(1f).setDuration(200).start();
        }}); }});
        else blur.animate().alpha(1f).setDuration(240).start();
        an.start();
        dim.animate().alpha(1f).setDuration(240).start();
    }

    private void timedShot() {
        long t0 = android.os.SystemClock.uptimeMillis();
        takeShot();
        long ms = android.os.SystemClock.uptimeMillis() - t0;
        shotMs = shotMs < 0 ? ms : (shotMs + ms) / 2;
    }

    void close() {
        if (!open) return;
        open = false;
        Obd.removeListener(this);
        float to = (left ? -1 : 1) * (px(sheetW) + px(30));
        sheet.animate().translationX(to).setDuration(200).setInterpolator(new DecelerateInterpolator()).withLayer().start();
        dim.animate().alpha(0f).setDuration(200).start();
        blur.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() { public void run() { finishClose(); } }).start();
    }

    /** Sin animación (al ir a Ajustes o a un gráfico). */
    void closeNow() {
        if (!open) return;
        open = false;
        Obd.removeListener(this);
        finishClose();
    }

    private void finishClose() {
        setVisibility(GONE);
        blur.setImageDrawable(null);
        if (shot != null) { shot.recycle(); shot = null; }
        host.onClosed();
    }

    /** Foto del inicio en 1/6 de resolución y difuminada (tres pasadas de caja ≈ gaussiano). */
    private void takeShot() {
        View b = host.backdrop();
        if (b == null || b.getWidth() <= 0) return;
        try {
            int f = 6;
            int w = Math.max(1, b.getWidth() / f), h = Math.max(1, b.getHeight() / f);
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            cv.drawColor(Main.BG);
            cv.scale(1f / f, 1f / f);
            // el panel ya está a la vista: se oculta solo mientras se dibuja el fondo (en el mismo instante, sin parpadeo)
            int vis = getVisibility();
            if (b != this) setVisibility(INVISIBLE);
            try { b.draw(cv); } finally { setVisibility(vis); }
            boxBlur(bm, 3);
            boxBlur(bm, 3);
            boxBlur(bm, 3);
            shot = bm;
            blur.setImageBitmap(bm);
        } catch (Throwable t) {
            blur.setImageDrawable(null);
            blur.setBackgroundColor(Main.BG);
        }
    }

    static void boxBlur(Bitmap bm, int r) {
        int w = bm.getWidth(), h = bm.getHeight();
        int[] p = new int[w * h], o = new int[w * h];
        bm.getPixels(p, 0, w, 0, 0, w, h);
        pass(p, o, w, h, r, true);
        pass(o, p, w, h, r, false);
        bm.setPixels(p, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horiz) {
        int lines = horiz ? h : w, len = horiz ? w : h;
        for (int L = 0; L < lines; L++) {
            int ra = 0, ga = 0, ba = 0, n = 0;
            for (int i = -r; i <= r; i++) {
                int j = Math.max(0, Math.min(len - 1, i));
                int c = in[horiz ? L * w + j : j * w + L];
                ra += (c >> 16) & 255; ga += (c >> 8) & 255; ba += c & 255; n++;
            }
            for (int i = 0; i < len; i++) {
                out[horiz ? L * w + i : i * w + L] = 0xFF000000 | ((ra / n) << 16) | ((ga / n) << 8) | (ba / n);
                int add = Math.min(len - 1, i + r + 1), rem = Math.max(0, i - r);
                int ca = in[horiz ? L * w + add : add * w + L], cr = in[horiz ? L * w + rem : rem * w + L];
                ra += ((ca >> 16) & 255) - ((cr >> 16) & 255);
                ga += ((ca >> 8) & 255) - ((cr >> 8) & 255);
                ba += (ca & 255) - (cr & 255);
            }
        }
    }

    // ───────────────────────── actualización ─────────────────────────

    @Override public void onObd() {
        if (!open) return;
        boolean on = Obd.running();
        int st = Obd.status;
        String dev = ObdDialogs.deviceName(getContext());
        status.setText(!on ? I18n.t("Monitor apagado") + " · " + dev
            : st == Obd.LIVE ? I18n.t("En vivo") + " · " + dev
            : Obd.statusMsg.length() > 0 ? I18n.t(Obd.statusMsg) : I18n.t("Conectando…"));
        status.setTextColor(st == Obd.LIVE && on ? ObdDialogs.GOOD : st == Obd.ERROR ? ObdDialogs.DANGER : Main.MUTED);
        milPill.setVisibility(Obd.mil ? VISIBLE : GONE);
        for (Ring r : rings) if (r.drawn != r.s.pushes || r.drawnLevel != r.s.level) r.invalidate();
        fillMinis();
        fillLists(on);
    }

    private void fillMinis() {
        List<Obd.Sensor> show = new ArrayList<Obd.Sensor>();
        Obd.Sensor[] want = {Obd.SPEED, Obd.LOAD, Obd.CONSUMPTION, Obd.FUEL, Obd.INTAKE, Obd.OILT};
        for (Obd.Sensor s : want) if (s.has() && show.size() < 3) show.add(s);
        if (show.isEmpty()) { show.add(Obd.SPEED); show.add(Obd.LOAD); show.add(Obd.FUEL); }
        StringBuilder key = new StringBuilder();
        for (Obd.Sensor s : show) key.append(s.key).append(',');
        if (!key.toString().equals(miniKey)) {
            miniKey = key.toString();
            minis.removeAllViews();
            for (int i = 0; i < show.size(); i++) {
                final Obd.Sensor s = show.get(i);
                LinearLayout cell = new LinearLayout(getContext());
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_VERTICAL);
                cell.setPadding(px(14), 0, px(10), 0);
                cell.setBackground(round(Main.LIGHT ? 0x0A000000 : 0x0FFFFFFF, 14));
                TextView lbl = tv(I18n.t(s.label), 12, Main.MUTED, false);
                lbl.setSingleLine(true);
                lbl.setEllipsize(TextUtils.TruncateAt.END);
                cell.addView(lbl);
                TextView val = tv("", 19, Main.TEXT, true);
                val.setSingleLine(true);
                val.setTag(s);
                cell.addView(val);
                cell.setOnClickListener(new OnClickListener() { public void onClick(View v) { closeNow(); host.openChart(s.key); } });
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1);
                if (i > 0) cl.leftMargin = px(12);
                minis.addView(cell, cl);
            }
        }
        for (int i = 0; i < minis.getChildCount(); i++) {
            TextView val = (TextView) ((LinearLayout) minis.getChildAt(i)).getChildAt(1);
            Obd.Sensor s = (Obd.Sensor) val.getTag();
            String t = s.has() ? s.text() + " " + s.unit : "—";
            if (!t.contentEquals(val.getText())) val.setText(t);
            val.setTextColor(s.level == Obd.CRIT ? ObdDialogs.DANGER : s.level == Obd.WARN ? ObdDialogs.AMBER : Main.TEXT);
        }
    }

    private void fillLists(boolean on) {
        List<Obd.Alert> al;
        synchronized (Obd.alerts) { al = new ArrayList<Obd.Alert>(Obd.alerts); }
        List<String> codes = new ArrayList<String>(Obd.dtcs);
        for (String p : Obd.pending) if (!codes.contains(p)) codes.add(p);
        StringBuilder key = new StringBuilder(on ? "on" : "off");
        for (Obd.Alert a : al) key.append(a.key).append(a.level).append(a.title()).append(',');
        key.append('|').append(codes).append(Obd.lastDtcCheck > 0);
        if (key.toString().equals(listKey)) return;
        listKey = key.toString();

        alertsBox.removeAllViews();
        alertsBox.addView(section(I18n.t("Alertas"), al.size() > 3 ? "obd_alerts" : null));
        if (al.isEmpty()) alertsBox.addView(okLine(I18n.t(on ? "Todo normal por ahora" : "Sin monitor activo")));
        for (int i = 0; i < al.size() && i < 3; i++) {
            final Obd.Alert a = al.get(i);
            LinearLayout c = item(a.level == Obd.CRIT ? ObdDialogs.DANGER : ObdDialogs.AMBER);
            TextView t = tv(a.title(), 14, Main.TEXT, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(t);
            TextView d = tv(a.advice(), 12, Main.SOFT, false);
            d.setMaxLines(2);
            d.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(d);
            c.setOnClickListener(new OnClickListener() { public void onClick(View v) { host.dialogs().showAlert(a); } });
            alertsBox.addView(c, itemLp());
        }

        codesBox.removeAllViews();
        codesBox.addView(section(I18n.t("Códigos de falla"), "obd_codes"));
        if (codes.isEmpty()) codesBox.addView(okLine(I18n.t(on && Obd.lastDtcCheck > 0 ? "Sin códigos guardados" : "Se revisan al conectar")));
        for (int i = 0; i < codes.size() && i < 3; i++) {
            final String code = codes.get(i);
            DtcInfo.Entry e = DtcInfo.get(getContext(), code);
            boolean pend = !Obd.dtcs.contains(code);
            int col = pend ? Main.MUTED : e.severity >= 3 ? ObdDialogs.DANGER : e.severity == 1 ? Main.MUTED : ObdDialogs.AMBER;
            LinearLayout c = item(col);
            LinearLayout top = new LinearLayout(getContext());
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(tv(code, 15, Main.TEXT, true));
            TextView sev = tv("  ·  " + I18n.t(pend ? "Pendiente" : e.severity >= 3 ? "Grave" : e.severity == 1 ? "Leve" : "Revisar"), 12, col, true);
            top.addView(sev);
            c.addView(top);
            String what = e.es != null ? e.es : e.en != null ? e.en : DtcInfo.system(code);
            TextView d = tv(what, 12, Main.SOFT, false);
            d.setMaxLines(2);
            d.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(d);
            c.setOnClickListener(new OnClickListener() { public void onClick(View v) { host.dialogs().showCode(code); } });
            codesBox.addView(c, itemLp());
        }
    }

    // ───────────────────────── piezas ─────────────────────────

    private View section(String title, final String more) {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(2), 0, 0, px(6));
        TextView tt = tv(title.toUpperCase(I18n.loc), 11, Main.MUTED, true);
        tt.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);   // en árabe quedaba pegado a «Ver todo»
        r.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        if (more != null) {
            TextView m = tv(I18n.t("Ver todo") + " ›", 12, Main.RED, true);
            m.setOnClickListener(new OnClickListener() { public void onClick(View v) { closeNow(); host.openSettings(more); } });
            r.addView(m);
        }
        return r;
    }

    private View okLine(String s) {
        TextView t = tv("✓  " + s, 13, Main.SOFT, false);
        t.setPadding(px(4), px(4), 0, 0);
        return t;
    }

    private LinearLayout item(int color) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(12), px(8), px(10), px(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor((color & 0x00FFFFFF) | (Main.LIGHT ? 0x14000000 : 0x1F000000));
        g.setCornerRadius(px(12));
        g.setStroke(Math.max(1, px(1)), (color & 0x00FFFFFF) | 0x66000000);
        c.setBackground(g);
        return c;
    }

    private LinearLayout.LayoutParams itemLp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.bottomMargin = px(8);
        return l;
    }

    private int px(float v) { return Math.round(v * k); }

    private TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k * Main.FONT);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pill(String s, int bg, int fg) {
        TextView t = tv(s, 14, fg, true);
        t.setSingleLine(true);              // en alemán «Motorkontrolle» se partía y quedaba cortado
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(14), 0, px(14), 0);
        // ancho del texto medido más un margen: algunos equipos miden la negrita unos píxeles corta y la cortaban
        t.setMinWidth((int) Math.ceil(t.getPaint().measureText(s)) + px(28) + px(6));
        t.setBackground(round(bg, 12));
        return t;
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(r));
        return d;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.leftMargin = l; p.topMargin = t;
        return p;
    }

    /** Medidor de arco (270°) con el valor al centro; el color sigue el nivel de la alerta. */
    private class Ring extends View {
        final Obd.Sensor s;
        long drawn = -1; int drawnLevel = -1;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        Ring(Context c, Obd.Sensor s) {
            super(c);
            this.s = s;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeCap(Paint.Cap.ROUND);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            txt.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onDraw(Canvas c) {
            drawn = s.pushes; drawnLevel = s.level;
            float w = getWidth(), h = getHeight(), sw = px(9);
            float d = Math.min(w, h) - sw;
            box.set((w - d) / 2, (h - d) / 2, (w + d) / 2, (h + d) / 2);
            track.setStrokeWidth(sw);
            track.setColor(Main.LIGHT ? 0x1A000000 : 0x24FFFFFF);
            c.drawArc(box, 135, 270, false, track);
            boolean has = s.has() && Obd.running();
            if (has) {
                float f = Math.max(0f, Math.min(1f, (s.value - s.min) / (s.max - s.min)));
                arc.setStrokeWidth(sw);
                // verde = normal, ámbar = aviso, rojo = alerta (no el color de acento: rojo por defecto, se leería como peligro)
                arc.setColor(s.level == Obd.CRIT ? ObdDialogs.DANGER : s.level == Obd.WARN ? ObdDialogs.AMBER : ObdDialogs.GOOD);
                if (f > 0.004f) c.drawArc(box, 135, 270 * f, false, arc);
            }
            txt.setColor(has ? Main.TEXT : Main.MUTED);
            txt.setTypeface(Typeface.DEFAULT_BOLD);
            String v = has ? s.text() : "—";
            txt.setTextSize(px(v.length() > 4 ? 24 : 30) * Main.FONT);
            c.drawText(v, w / 2, h / 2 + px(8), txt);
            txt.setTypeface(Typeface.DEFAULT);
            txt.setColor(Main.MUTED);
            txt.setTextSize(px(12) * Main.FONT);
            c.drawText(s.unit, w / 2, h / 2 + px(28), txt);
        }
    }

    /** Pestaña del borde: abre el panel con un toque o deslizando; un punto avisa si hay alertas. */
    static class Handle extends View implements Obd.Listener {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean left;
        private final float k;
        private int level = -1;
        private final RectF r = new RectF();

        Handle(Context c, float scale, boolean left, final Runnable open) {
            super(c);
            this.k = scale;
            this.left = left;
            setOnTouchListener(new OnTouchListener() {
                float x0; boolean fired;
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getRawX(); fired = false; return true; }
                    if (e.getActionMasked() == MotionEvent.ACTION_MOVE && !fired) {
                        float dx = e.getRawX() - x0;
                        if ((Handle.this.left ? dx : -dx) > 20 * k) { fired = true; open.run(); }
                        return true;
                    }
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && !fired) { open.run(); return true; }
                    return true;
                }
            });
        }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); Obd.addListener(this); onObd(); }
        @Override protected void onDetachedFromWindow() { Obd.removeListener(this); super.onDetachedFromWindow(); }

        @Override public void onObd() {
            int lv = -1;
            synchronized (Obd.alerts) { for (Obd.Alert a : Obd.alerts) lv = Math.max(lv, a.level); }
            if (Obd.mil) lv = Math.max(lv, Obd.WARN);
            if (lv != level) { level = lv; invalidate(); }
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), bw = 7 * k;
            float x = left ? 3 * k : w - bw - 3 * k;
            r.set(x, h * 0.18f, x + bw, h * 0.82f);
            p.setColor(Main.LIGHT ? 0x55000000 : 0x66FFFFFF);
            c.drawRoundRect(r, bw / 2, bw / 2, p);
            if (level >= 0) {
                p.setColor(level == Obd.CRIT ? ObdDialogs.DANGER : ObdDialogs.AMBER);
                c.drawCircle(x + bw / 2, h * 0.18f - 7 * k, 5 * k, p);
            }
        }
    }

    static ViewGroup.LayoutParams match() { return new LayoutParams(-1, -1); }
}
