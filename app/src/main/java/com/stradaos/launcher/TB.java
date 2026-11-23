package com.stradaos.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.ListAdapter;

/**
 * Constructor de las ventanas de StradaOS. Se usa igual que AlertDialog.Builder (por eso lo extiende: el resto del
 * código no cambia), pero no arma la ventana del sistema: guarda lo pedido y lo dibuja {@link Dlg} con el estilo
 * del launcher (tarjeta redondeada, colores del tema y botones con el acento). Además traduce títulos, mensajes,
 * botones y listas al idioma elegido (ver I18n).
 */
public class TB extends AlertDialog.Builder {
    final Context ctx;
    final int theme;
    CharSequence title, message, posT, negT, neuT;
    DialogInterface.OnClickListener posL, negL, neuL, itemsL;
    CharSequence[] items;
    int checked = -1;
    boolean single;
    ListAdapter adapter;
    View view;
    boolean cancelable = true, translate = true, diag, wide;
    DialogInterface.OnCancelListener onCancel;

    public TB(Context c, int theme) { super(c, theme); ctx = c; this.theme = theme; }

    /** Los textos ya vienen en el idioma del usuario (nombres propios o textos traducidos antes): no se traducen. */
    public TB raw() { translate = false; return this; }
    /** Ventana de diagnóstico: el mensaje se traduce línea por línea y se muestra como «dato: valor». */
    public TB diag() { diag = true; wide = true; return this; }
    /** Ventana más ancha (listas con íconos, textos largos). */
    public TB wide() { wide = true; return this; }

    CharSequence t(CharSequence s) { return translate && s != null ? I18n.t(s) : s; }

    @Override public TB setTitle(CharSequence t) { title = t; return this; }
    @Override public TB setMessage(CharSequence m) { message = m; return this; }
    @Override public TB setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { posT = t; posL = l; return this; }
    @Override public TB setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { negT = t; negL = l; return this; }
    @Override public TB setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { neuT = t; neuL = l; return this; }
    @Override public TB setItems(CharSequence[] it, DialogInterface.OnClickListener l) { items = it; itemsL = l; single = false; return this; }
    @Override public TB setSingleChoiceItems(CharSequence[] it, int sel, DialogInterface.OnClickListener l) {
        items = it; checked = sel; itemsL = l; single = true; return this;
    }
    @Override public TB setAdapter(ListAdapter a, DialogInterface.OnClickListener l) { adapter = a; itemsL = l; wide = true; return this; }
    @Override public TB setView(View v) { view = v; return this; }
    /** Encabezado propio (el menú del aceite y de cada mantenimiento: nombre, kilometraje y cuánto falta). */
    View customTitle;
    @Override public TB setCustomTitle(View v) { customTitle = v; return this; }
    @Override public TB setCancelable(boolean c) { cancelable = c; return this; }
    @Override public TB setOnCancelListener(DialogInterface.OnCancelListener l) { onCancel = l; return this; }

    @Override public AlertDialog create() { return new Dlg(this); }
    @Override public AlertDialog show() {
        AlertDialog d = create();
        d.show();
        return d;
    }
}
