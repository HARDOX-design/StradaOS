package com.stradaos.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Ventana de StradaOS (2.21): una tarjeta redondeada con los colores del tema, el acento y botones grandes para
 * tocar con el carro en marcha, igual en Android 4.1 que en Android 15. Reemplaza el contenido de las ventanas
 * del sistema (Holo en las pantallas viejas, Material en las nuevas), que no combinaban con el launcher.
 *
 * Sigue siendo un AlertDialog flotante (misma ventana, mismo oscurecido de fondo, mismo comportamiento con el
 * teclado y con Atrás): lo único propio es lo que se dibuja adentro. La arma {@link TB}.
 */
class Dlg extends AlertDialog {
    private final TB p;
    private float S = 1f;

    Dlg(TB p) {
        super(p.ctx, p.theme);
        this.p = p;
        setCancelable(p.cancelable);
        if (p.cancelable) setCanceledOnTouchOutside(true);
        if (p.onCancel != null) setOnCancelListener(p.onCancel);
    }

    @Override
    protected void onCreate(Bundle b) {
        // A propósito sin super.onCreate(): ahí AlertDialog instala su diseño del sistema; aquí el contenido es propio.
        Window w = getWindow();
        try { w.requestFeature(Window.FEATURE_NO_TITLE); } catch (Throwable ignored) { }
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // en Android 4.x la ventana del diálogo es opaca: sin esto, las esquinas redondeadas salen negras
        w.setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        int sw = dm.widthPixels, sh = dm.heightPixels;
        S = sw >= sh ? Math.min(sw / 1024f, sh / 600f) : Math.min(sw / 600f, sh / 1024f);
        if (S <= 0) S = 1f;
        int width = (int) Math.min(sw * 0.94f, (p.wide ? 640 : 540) * S);
        setContentView(card((int) (sh * 0.9f)), new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int px(float v) { return (int) (v * S); }

    private TextView text(CharSequence t, float size, int color, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * S * Main.FONT);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(r * S);
        return d;
    }

    /** Mezcla un color con negro (oscuro) o blanco (claro) para el estado «presionado». */
    static int pressedOf(int color) {
        int k = Main.LIGHT ? 0 : 255;
        float f = 0.14f;
        int r = (int) (Color.red(color) + (k - Color.red(color)) * f), g = (int) (Color.green(color) + (k - Color.green(color)) * f),
            b = (int) (Color.blue(color) + (k - Color.blue(color)) * f);
        return Color.argb(255, r, g, b);
    }

    private Drawable button(int color, float r) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, round(pressedOf(color), r));
        d.addState(new int[0], round(color, r));
        return d;
    }

    /** Fondo de una fila de lista: transparente, y al tocarla un recuadro redondeado que no llega a los bordes. */
    private Drawable rowBg() {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, new InsetDrawable(round(Main.INNER, 14), px(10), px(1), px(10), px(1)));
        d.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    private View card(int maxH) {
        Context c = getContext();
        Card card = new Card(c, maxH);
        GradientDrawable bg = round(Main.CARD, 28);
        bg.setStroke(Math.max(1, px(1)), Main.LIGHT ? 0x1A000000 : 0x26FFFFFF);
        card.setBackground(bg);

        CharSequence title = p.t(p.title);
        boolean hasTitle = title != null && title.length() > 0;
        boolean hasButtons = p.posT != null || p.negT != null || p.neuT != null;
        boolean hasList = p.items != null || p.adapter != null;
        boolean custom = !hasTitle && p.customTitle != null;
        card.setPadding(0, px(hasTitle ? 22 : custom ? 0 : 14), 0, px(hasButtons ? 16 : 14));

        if (custom) {
            // encabezado propio (desde 2.26 se perdía: TB no lo guardaba y el menú salía sin el kilometraje)
            View ct = p.customTitle;
            if (ct.getParent() instanceof ViewGroup) ((ViewGroup) ct.getParent()).removeView(ct);
            card.addView(ct);
        }

        if (hasTitle) {
            TextView t = text(title, 21, Main.TEXT, true);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setMaxLines(3);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(px(28), 0, px(28), 0);
            card.addView(t);
            // rayita con el color de acento: la firma de las ventanas de StradaOS
            FrameLayout line = new FrameLayout(c);
            View pill = new View(c);
            pill.setBackground(round(Main.RED, 2));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(38), px(4));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = px(11);
            lp.bottomMargin = px(hasList ? 8 : 10);
            line.addView(pill, lp);
            card.addView(line);
        }

        CharSequence msg = p.diag && p.message != null ? pretty(p.translate ? I18n.lines(p.message.toString()) : p.message.toString())
                : p.t(p.message);
        if (msg != null && msg.length() > 0) {
            boolean brief = !p.diag && msg.length() <= 160 && msg.toString().indexOf('\n') < 0;
            TextView m = text(msg, p.diag ? 14.5f : 16, p.diag ? Main.TEXT : Main.SOFT, false);
            m.setLineSpacing(0, 1.22f);
            m.setGravity(brief ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT);
            m.setPadding(px(28), px(4), px(28), px(hasList || p.view != null ? 8 : 12));
            if (!hasList && p.view == null) {
                ScrollView sv = new ScrollView(c);
                sv.addView(m);
                card.addView(sv);
                card.flex = sv;
            } else card.addView(m);
        }

        if (hasList) {
            ListView lv = list();
            card.addView(lv);
            card.flex = lv;
        } else if (p.view != null) {
            View v = p.view;
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            if (!scrolls(v)) {
                // contenido que no se desplaza solo: en pantallas bajas se puede deslizar en vez de quedar cortado
                ScrollView sv = new ScrollView(c);
                sv.addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                v = sv;
            }
            card.addView(v);
            card.flex = v;
        }

        if (hasButtons) card.addView(buttons(c));
        return card;
    }

    /** ¿La vista ya trae su propio desplazamiento (lista o ScrollView)? */
    private static boolean scrolls(View v) {
        if (v instanceof ScrollView || v instanceof AbsListView) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (scrolls(g.getChildAt(i))) return true;
        }
        return false;
    }

    private ListView list() {
        final Context c = getContext();
        ListView lv = new ListView(c);
        lv.setCacheColorHint(Color.TRANSPARENT);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setDivider(new InsetDrawable(new ColorDrawable(Main.LIGHT ? 0x14000000 : 0x18FFFFFF), px(26), 0, px(26), 0));
        lv.setDividerHeight(Math.max(1, px(1)));
        lv.setVerticalFadingEdgeEnabled(false);
        if (p.adapter != null) {
            lv.setSelector(rowBg());
            lv.setAdapter(p.adapter);
        } else {
            final CharSequence[] it = new CharSequence[p.items.length];
            for (int i = 0; i < it.length; i++) it[i] = p.t(p.items[i]);
            lv.setAdapter(new BaseAdapter() {
                public int getCount() { return it.length; }
                public Object getItem(int pos) { return it[pos]; }
                public long getItemId(int pos) { return pos; }
                public View getView(int pos, View cv, ViewGroup parent) {
                    boolean sel = p.single && pos == p.checked;
                    LinearLayout row = new LinearLayout(c);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setMinimumHeight(px(54));
                    row.setBackground(rowBg());   // antes del margen: en Android 4.x el fondo reemplaza el margen interno
                    row.setPadding(px(28), px(9), px(24), px(9));
                    TextView t = text(it[pos], 17, sel ? Main.RED : Main.TEXT, sel);
                    row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    if (p.single) {
                        // elegido: círculo con el acento y un punto; los demás, un aro tenue
                        FrameLayout dot = new FrameLayout(c);
                        GradientDrawable ring = new GradientDrawable();
                        ring.setShape(GradientDrawable.OVAL);
                        if (sel) ring.setColor(Main.RED);
                        else { ring.setColor(Color.TRANSPARENT); ring.setStroke(Math.max(2, px(2)), Main.MUTED); }
                        dot.setBackground(ring);
                        if (sel) {
                            View in = new View(c);
                            GradientDrawable g = new GradientDrawable();
                            g.setShape(GradientDrawable.OVAL);
                            g.setColor(Color.WHITE);
                            in.setBackground(g);
                            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(9), px(9));
                            lp.gravity = Gravity.CENTER;
                            dot.addView(in, lp);
                        }
                        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(px(22), px(22));
                        dlp.leftMargin = px(12);
                        row.addView(dot, dlp);
                    }
                    return row;
                }
            });
            if (p.single && p.checked > 3) lv.setSelection(p.checked - 2);
        }
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                if (p.itemsL != null) p.itemsL.onClick(Dlg.this, pos);
                if (!p.single) dismiss();   // igual que AlertDialog: las listas de una sola opción las cierra quien las abrió
            }
        });
        return lv;
    }

    private View buttons(Context c) {
        CharSequence[] txt = {p.t(p.neuT), p.t(p.negT), p.t(p.posT)};
        final DialogInterface.OnClickListener[] ls = {p.neuL, p.negL, p.posL};
        final int[] which = {DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_POSITIVE};
        int n = 0;
        float longest = 0;
        TextView probe = text("", 16, Color.WHITE, true);
        for (CharSequence t : txt) if (t != null) { n++; longest = Math.max(longest, probe.getPaint().measureText(t.toString())); }
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float cardW = Math.min(dm.widthPixels * 0.94f, (p.wide ? 640 : 540) * S);
        float slot = (cardW - px(18) * 2 - px(10) * (n - 1)) / n;
        boolean stack = n > 1 && longest + px(28) > slot;   // textos largos: un botón debajo del otro
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        bar.setPadding(px(18), px(12), px(18), 0);
        int added = 0;
        for (int k = 0; k < 3; k++) {
            final int i = stack ? 2 - k : k;   // apilados: el principal arriba
            if (txt[i] == null) continue;
            boolean main = i == 2;
            TextView b = text(txt[i], 16, main ? Color.WHITE : Main.TEXT, true);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setBackground(button(main ? Main.RED : Main.BTN, 25));
            b.setPadding(px(12), 0, px(12), 0);
            b.setClickable(true);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (ls[i] != null) ls[i].onClick(Dlg.this, which[i]);
                dismiss();
            }});
            LinearLayout.LayoutParams lp = stack ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(50))
                    : new LinearLayout.LayoutParams(0, px(50), 1);
            if (added > 0) { if (stack) lp.topMargin = px(10); else lp.leftMargin = px(10); }
            bar.addView(b, lp);
            added++;
        }
        return bar;
    }

    /**
     * Texto de diagnóstico con formato: en cada línea (y en cada parte separada por « · ») el nombre del dato va
     * en gris y el valor en el color del texto; ✓ en verde y ✗ en rojo.
     */
    static CharSequence pretty(String s) {
        if (s == null) return null;
        SpannableStringBuilder b = new SpannableStringBuilder(s);
        int at = 0;
        for (String line : s.split("\n", -1)) {
            int lead = 0;
            while (lead < line.length() && line.charAt(lead) == ' ') lead++;
            if (lead < line.length()) {
                char first = line.charAt(lead);
                if (first == '✓') b.setSpan(new ForegroundColorSpan(0xFF3DDC84), at + lead, at + lead + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                else if (first == '✗') b.setSpan(new ForegroundColorSpan(0xFFFF5A4F), at + lead, at + lead + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int seg = lead;
            while (seg < line.length()) {
                int end = line.indexOf(" · ", seg);
                if (end < 0) end = line.length();
                int colon = line.indexOf(": ", seg);
                if (colon < 0 && line.endsWith(":") && end == line.length()) colon = line.length() - 1;
                if (colon > seg && colon < end && colon - seg <= 64)
                    b.setSpan(new ForegroundColorSpan(Main.MUTED), at + seg, at + colon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                seg = end + 3;
            }
            at += line.length() + 1;
        }
        return b;
    }

    /**
     * La tarjeta: apila sus partes y, si no caben en el alto disponible, solo se encoge la parte «flexible»
     * (el mensaje, la lista o el contenido propio, que se desplazan); el título y los botones siempre se ven.
     */
    static class Card extends ViewGroup {
        private final int maxH;
        View flex;

        Card(Context c, int maxH) { super(c); this.maxH = maxH; }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            int limit = maxH;
            if (MeasureSpec.getMode(hSpec) != MeasureSpec.UNSPECIFIED) limit = Math.min(limit, MeasureSpec.getSize(hSpec));
            int cw = MeasureSpec.makeMeasureSpec(Math.max(0, w - getPaddingLeft() - getPaddingRight()), MeasureSpec.EXACTLY);
            int used = getPaddingTop() + getPaddingBottom();
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch == flex || ch.getVisibility() == GONE) continue;
                ch.measure(cw, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                used += ch.getMeasuredHeight();
            }
            if (flex != null && flex.getVisibility() != GONE) {
                int room = Math.max(limit - used, Math.min(limit / 3, 160));
                flex.measure(cw, MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST));
                used += flex.getMeasuredHeight();
            }
            setMeasuredDimension(w, used);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int y = getPaddingTop(), x = getPaddingLeft();
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                ch.layout(x, y, x + ch.getMeasuredWidth(), y + ch.getMeasuredHeight());
                y += ch.getMeasuredHeight();
            }
        }
    }
}
