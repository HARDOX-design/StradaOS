package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * 2.35 (ronda 3): «Elegir video» como un gestor de archivos. Arriba, los almacenamientos (memoria de la pantalla y cada
 * USB o tarjeta conectada); adentro, las carpetas que tienen videos (con cuántos) y los videos con su miniatura y su
 * duración. «‹» (o Atrás) sube una carpeta. Abre en la carpeta del video que suena. Al elegir un video, la lista de
 * reproducción pasa a ser esa carpeta ({@link Cb#chosen}).
 *
 * Solo muestra carpetas que llevan a algún video: así no hay que atravesar Android, DCIM vacías, etc. La búsqueda es la
 * misma de la tarjeta (en otro hilo, con límite de carpetas). Las miniaturas se sacan de a una en otro hilo y se guardan
 * en la caché de la app (no se vuelven a calcular al abrir de nuevo).
 */
class VideoPicker extends LinearLayout {

    interface Cb { void chosen(File folder, List<File> videos, int index); }

    /** Una carpeta (o un almacenamiento) con sus videos directos y las subcarpetas que llevan a videos. */
    static final class Node {
        final File dir;
        final String label;
        /** 0 carpeta, 1 memoria de la pantalla, 2 USB o tarjeta. */
        final int kind;
        final Node parent;
        final TreeMap<String, Node> kids = new TreeMap<String, Node>();
        final ArrayList<File> videos = new ArrayList<File>();
        int total;
        Node(File d, String l, int k, Node p) { dir = d; label = l; kind = k; parent = p; }
    }

    private final float S;
    private final Cb cb;
    private final String playing;
    private final String startAt;
    private final TextView back, crumb, msg;
    private final ListView lv;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Thumbs thumbs;
    /** Raíz virtual: sus hijos son los almacenamientos. */
    private Node top, cur;
    private final List<Object> rows = new ArrayList<Object>();
    private final BaseAdapter adapter;
    private volatile boolean stopped;
    /** Ya entró o salió de alguna carpeta (al ponerse al día la lista, no se le cambia de lugar). */
    private boolean moved;

    /**
     * @param playing ruta del video que suena (se marca y se abre en su carpeta)
     * @param start   null = la carpeta del video que suena; "" = los almacenamientos; otra = esa carpeta (gancho de prueba)
     */
    VideoPicker(Context c, String playing, String start, Cb cb) {
        super(c);
        this.cb = cb;
        this.playing = playing != null ? playing : "";
        this.startAt = start;
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int sw = dm.widthPixels, sh = dm.heightPixels;
        float k = sw >= sh ? Math.min(sw / 1024f, sh / 600f) : Math.min(sw / 600f, sh / 1024f);
        S = k > 0 ? k : 1f;
        thumbs = new Thumbs(c, h);
        setOrientation(VERTICAL);

        // barra de ruta: «‹» para subir y dónde está (se corta por el principio para que se vea la carpeta de ahora)
        LinearLayout path = new LinearLayout(c);
        path.setGravity(Gravity.CENTER_VERTICAL);
        path.setPadding(px(18), 0, px(22), px(6));
        back = text("‹", 28, Main.TEXT, true);
        back.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bb = new android.graphics.drawable.GradientDrawable();
        bb.setColor(Main.BTN);
        bb.setCornerRadius(px(14));
        back.setBackground(bb);
        back.setPadding(0, 0, 0, px(3));
        back.setOnClickListener(new OnClickListener() { public void onClick(View v) { up(); } });
        path.addView(back, new LayoutParams(px(46), px(40)));
        crumb = text("", 15, Main.SOFT, false);
        crumb.setSingleLine(true);
        crumb.setEllipsize(TextUtils.TruncateAt.START);
        LayoutParams cl = new LayoutParams(0, -2, 1);
        cl.leftMargin = px(12);
        path.addView(crumb, cl);
        addView(path, new LayoutParams(-1, -2));

        FrameLayout box = new FrameLayout(c);
        lv = new ListView(c);
        lv.setCacheColorHint(Color.TRANSPARENT);
        lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        lv.setDivider(new android.graphics.drawable.InsetDrawable(new android.graphics.drawable.ColorDrawable(Main.LIGHT ? 0x14000000 : 0x18FFFFFF), px(26), 0, px(26), 0));
        lv.setDividerHeight(Math.max(1, px(1)));
        lv.setVerticalFadingEdgeEnabled(false);
        adapter = new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int p) { return rows.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View cv, ViewGroup parent) { return row(p, cv); }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int p, long id) { tap(p); }
        });
        box.addView(lv, new FrameLayout.LayoutParams(-1, -1));
        msg = text(I18n.t("Buscando videos…"), 16, Main.MUTED, false);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(px(24), 0, px(24), 0);
        box.addView(msg, new FrameLayout.LayoutParams(-1, -1));
        addView(box, new LayoutParams(-1, 0, 1));
        back.setAlpha(0.35f);
        load(c);
    }

    private int px(float v) { return Math.round(v * S); }

    private TextView text(CharSequence t, float size, int color, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * S * Main.FONT);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    /** Alto fijo (lo que quepa, hasta 420): la ventana no salta de tamaño al cambiar de carpeta. */
    @Override protected void onMeasure(int wSpec, int hSpec) {
        int want = px(420);
        int mode = MeasureSpec.getMode(hSpec);
        int hh = mode == MeasureSpec.UNSPECIFIED ? want : Math.min(want, MeasureSpec.getSize(hSpec));
        super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(hh, MeasureSpec.EXACTLY));
    }

    /** Ya no se ve: deja de sacar miniaturas. */
    void stop() { stopped = true; thumbs.stop(); }

    /** Sube una carpeta; false si ya está en los almacenamientos (Atrás cierra la ventana). */
    boolean up() {
        if (cur == null || cur.parent == null) return false;
        moved = true;
        show(cur.parent, cur);
        return true;
    }

    // ───────────────────────── búsqueda ─────────────────────────

    /** Última búsqueda (se muestra al instante al abrir; luego se pone al día sola). */
    private static Node cache;
    private static long cacheAt;
    private static volatile boolean building;
    /** Lo que Android ya sabe de cada video (MediaStore): ruta → {id, duración ms}. */
    static final java.util.HashMap<String, long[]> MS = new java.util.HashMap<String, long[]>();

    /** Arma la búsqueda en segundo plano (al cargar la tarjeta), para que «Elegir video» abra al instante. */
    static void prewarm(final Context c) {
        synchronized (VideoPicker.class) {
            if (building || (cache != null && android.os.SystemClock.uptimeMillis() - cacheAt < 60000)) return;
            building = true;
        }
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() { public void run() {
            try {
                Node t = index(app, true);
                synchronized (VideoPicker.class) { cache = t; cacheAt = android.os.SystemClock.uptimeMillis(); }
            } catch (Throwable ignored) {
            } finally { building = false; }
        }}, "StradaVideoIndex").start();
    }

    /**
     * Muestra al instante la última búsqueda (o, la primera vez, lo que Android ya tiene indexado) y en otro hilo
     * vuelve a buscar en la memoria y las USB; al terminar, pone la lista al día sin moverla de carpeta ni de lugar.
     */
    private void load(final Context c) {
        final Node cached;
        synchronized (VideoPicker.class) { cached = cache; }
        if (cached != null) apply(cached);
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() { public void run() {
            if (cached == null) {
                final Node quick = index(app, false);
                if (quick.total > 0) h.post(new Runnable() { public void run() { apply(quick); } });
            }
            final Node full = index(app, true);
            synchronized (VideoPicker.class) { cache = full; cacheAt = android.os.SystemClock.uptimeMillis(); }
            h.post(new Runnable() { public void run() { apply(full); } });
        }}, "StradaVideoPicker").start();
    }

    /** Pone un árbol nuevo: la primera vez abre donde toca; después conserva la carpeta y el lugar de la lista. */
    private void apply(Node t) {
        if (stopped || t == null) return;
        // mientras no haya navegado, se vuelve a abrir donde toca (la búsqueda completa puede traer la carpeta del video)
        boolean first = top == null || !moved;
        Node was = cur;
        top = t;
        if (first) {
            Node start = top;
            String target = startAt == null ? new File(playing).getParent() : startAt;
            if (target != null && target.length() > 0) {
                Node n = find(top, target);
                if (n != null) start = n;
            }
            // con una sola memoria y sin USB, se entra directo a ella
            if (start == top && top.kids.size() == 1 && !"".equals(startAt)) start = top.kids.values().iterator().next();
            show(start, null);
            return;
        }
        Node n = top;
        if (was != null && was.parent != null && was.dir != null) {
            Node f = find(top, was.dir.getAbsolutePath());
            if (f != null) n = f;
        }
        int pos = lv.getFirstVisiblePosition();
        View v0 = lv.getChildAt(0);
        int off = v0 != null ? v0.getTop() : 0;
        show(n, null);
        if (n.dir != null && was != null && was.dir != null && n.dir.getAbsolutePath().equals(was.dir.getAbsolutePath())) lv.setSelectionFromTop(pos, off);
    }

    /** Los videos que Android ya tiene indexados (instantáneo); llena {@link #MS} con su id y duración. */
    static List<File> msVideos(Context c) {
        List<File> out = new ArrayList<File>();
        android.database.Cursor cu = null;
        try {
            cu = c.getContentResolver().query(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[]{android.provider.MediaStore.Video.Media._ID, android.provider.MediaStore.Video.Media.DATA,
                            android.provider.MediaStore.Video.Media.DURATION},
                    android.provider.MediaStore.Video.Media.SIZE + " > 102400", null, null);
            if (cu != null) {
                java.util.HashMap<String, long[]> m = new java.util.HashMap<String, long[]>();
                while (cu.moveToNext()) {
                    String path = cu.getString(1);
                    if (path == null || !VideoCard.isVideo(new File(path).getName())) continue;
                    m.put(path, new long[]{cu.getLong(0), cu.isNull(2) ? -1 : cu.getLong(2)});
                    out.add(new File(path));
                }
                synchronized (MS) { MS.putAll(m); }
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (cu != null) cu.close(); } catch (Throwable ignored) { }
        }
        return out;
    }

    /**
     * Arma el árbol de carpetas con videos de cada almacenamiento. Junta lo que Android ya tiene indexado con la
     * búsqueda en las carpetas (fs = true; las USB de muchas radios no se indexan); con fs = false, solo lo indexado.
     */
    static Node index(Context c, boolean fs) {
        Node top = new Node(null, "", 0, null);
        ArrayList<File> roots = new ArrayList<File>();
        ArrayList<Integer> kinds = new ArrayList<Integer>();
        roots.add(Backup.primary()); kinds.add(1);
        try { for (File u : Backup.removable(c)) { roots.add(u); kinds.add(2); } } catch (Throwable ignored) { }
        java.util.LinkedHashMap<String, File> all = new java.util.LinkedHashMap<String, File>();
        for (File f : msVideos(c)) if (f.exists()) all.put(f.getAbsolutePath(), f);
        if (fs) {
            java.util.HashSet<String> seen = new java.util.HashSet<String>();
            int[] budget = {6000};
            for (File r : roots) {
                List<File> found = new ArrayList<File>();
                try { VideoCard.scan(r, 0, found, seen, budget); } catch (Throwable ignored) { }
                for (File f : found) all.put(f.getAbsolutePath(), f);
            }
        }
        for (int i = 0; i < roots.size(); i++) {
            File r = roots.get(i);
            int kind = kinds.get(i);
            String label = kind == 1 ? I18n.t("Memoria de la pantalla") : I18n.t("Memoria USB") + " · " + r.getName();
            Node rn = new Node(r, label, kind, top);
            top.kids.put(String.format(Locale.US, "%03d", i), rn);
            String base = r.getAbsolutePath();
            for (File f : all.values()) {
                String p = f.getAbsolutePath();
                if (!p.startsWith(base + "/")) continue;
                String[] parts = p.substring(base.length() + 1).split("/");
                Node n = rn;
                n.total++;
                top.total++;
                File d = r;
                for (int k = 0; k < parts.length - 1; k++) {
                    d = new File(d, parts[k]);
                    String key = parts[k].toLowerCase(Locale.US) + "\u0000" + parts[k];
                    Node ch = n.kids.get(key);
                    if (ch == null) { ch = new Node(d, parts[k], 0, n); n.kids.put(key, ch); }
                    n = ch;
                    n.total++;
                }
                n.videos.add(f);
            }
        }
        sortAll(top);
        return top;
    }

    private static void sortAll(Node n) {
        Collections.sort(n.videos, new Comparator<File>() { public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); } });
        for (Node k : n.kids.values()) sortAll(k);
    }

    /** El nodo de esa carpeta (o el más cercano que la contiene). */
    private static Node find(Node top, String path) {
        for (Node r : top.kids.values()) {
            String base = r.dir.getAbsolutePath();
            if (!path.equals(base) && !path.startsWith(base + "/")) continue;
            Node n = r;
            if (path.length() > base.length()) {
                for (String part : path.substring(base.length() + 1).split("/")) {
                    Node ch = n.kids.get(part.toLowerCase(Locale.US) + "\u0000" + part);
                    if (ch == null) break;
                    n = ch;
                }
            }
            return n;
        }
        return null;
    }

    // ───────────────────────── lista ─────────────────────────

    private void show(Node n, Node from) {
        cur = n;
        rows.clear();
        rows.addAll(n.kids.values());
        rows.addAll(n.videos);
        adapter.notifyDataSetChanged();
        back.setAlpha(n.parent == null ? 0.35f : 1f);
        StringBuilder sb = new StringBuilder();
        for (Node k = n; k != null && k.parent != null; k = k.parent) {
            if (sb.length() > 0) sb.insert(0, "  ›  ");
            sb.insert(0, k.label);
        }
        crumb.setText(n.parent == null ? I18n.t("Almacenamientos") : sb.toString());
        if (rows.isEmpty()) { msg.setText(I18n.t("No hay videos en esta carpeta")); msg.setVisibility(VISIBLE); }
        else msg.setVisibility(GONE);
        // al subir, queda a la vista la carpeta de donde venía; al entrar, el video que suena
        int sel = 0;
        if (from != null) sel = Math.max(0, rows.indexOf(from));
        else for (int i = 0; i < rows.size(); i++) if (rows.get(i) instanceof File && ((File) rows.get(i)).getAbsolutePath().equals(playing)) sel = i;
        lv.setSelection(Math.max(0, sel - 1));
    }

    /** Gancho de prueba: tocar la fila n. */
    void tapRow(int p) { tap(p); }

    private void tap(int p) {
        if (p < 0 || p >= rows.size()) return;
        Object o = rows.get(p);
        if (o instanceof Node) { moved = true; show((Node) o, null); }
        else if (o instanceof File && cb != null) cb.chosen(cur.dir, new ArrayList<File>(cur.videos), cur.videos.indexOf(o));
    }

    private static final class Holder { Thumb thumb; TextView name, sub, arrow; }

    private View row(int p, View cv) {
        Holder hd;
        LinearLayout r;
        if (cv == null) {
            Context c = getContext();
            r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setMinimumHeight(px(70));
            r.setBackground(rowBg());
            r.setPadding(px(22), px(8), px(20), px(8));
            hd = new Holder();
            hd.thumb = new Thumb(c, S);
            r.addView(hd.thumb, new LayoutParams(px(96), px(54)));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            hd.name = text("", 16, Main.TEXT, true);
            hd.name.setMaxLines(2);
            hd.name.setEllipsize(TextUtils.TruncateAt.END);
            hd.sub = text("", 13, Main.MUTED, false);
            hd.sub.setSingleLine(true);
            col.addView(hd.name);
            col.addView(hd.sub);
            LayoutParams ll = new LayoutParams(0, -2, 1);
            ll.leftMargin = px(14);
            r.addView(col, ll);
            hd.arrow = text("›", 24, Main.MUTED, false);
            LayoutParams al = new LayoutParams(-2, -2);
            al.leftMargin = px(8);
            r.addView(hd.arrow, al);
            r.setTag(hd);
            r.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        } else { r = (LinearLayout) cv; hd = (Holder) r.getTag(); }
        Object o = rows.get(p);
        if (o instanceof Node) {
            Node n = (Node) o;
            hd.thumb.setIcon(n.kind == 0 ? Thumb.FOLDER : n.kind == 1 ? Thumb.MEMORY : Thumb.USB);
            thumbs.cancel(hd.thumb);
            hd.name.setText(n.label);
            hd.name.setTextColor(Main.TEXT);
            hd.sub.setText(n.total == 0 ? I18n.t("Sin videos") : n.total + " " + I18n.t("videos"));
            hd.arrow.setVisibility(VISIBLE);
        } else {
            File f = (File) o;
            boolean now = f.getAbsolutePath().equals(playing);
            hd.thumb.setIcon(Thumb.FILM);
            hd.thumb.setPlaying(now);
            thumbs.request(f, hd.thumb);
            hd.name.setText(f.getName());
            hd.name.setTextColor(now ? Main.RED : Main.TEXT);
            hd.sub.setText(now ? I18n.t("Reproduciendo") : size(f.length()));
            hd.sub.setTextColor(Main.MUTED);
            hd.arrow.setVisibility(GONE);
            verdict(f, hd.thumb, hd.sub, now);
        }
        return r;
    }

    /** 2.35 r6: lee el formato de los videos de la lista sin decodificar nada (en un hilo aparte, de a uno). */
    private static final java.util.concurrent.ExecutorService INFO = java.util.concurrent.Executors.newSingleThreadExecutor();

    /**
     * 2.35 r6: si la pantalla dice que no puede con ese video (p. ej. 1080×1920 H.264 High 4.2 en una radio que llega a
     * 4.1), la fila lo avisa en rojo antes de elegirlo: «No compatible con esta pantalla · 1080×1920 · H.264 High 4.2».
     */
    private void verdict(final File f, final Thumb v, final TextView sub, final boolean now) {
        // 2.35 r7: ya se probó y no abrió en esta pantalla (lo marca la tarjeta del video)
        if (!now && VideoCard.badMark(getContext(), f).exists()) {
            VideoLog.Info c = VideoLog.cached(f);
            sub.setText(I18n.t("No se puede reproducir aquí") + (c != null && c.shortText().length() > 0 ? " · " + c.shortText() : ""));
            sub.setTextColor(Main.RED);
            if (c != null) return;
        }
        VideoLog.Info in = VideoLog.cached(f);
        if (in != null) { showVerdict(in, sub, now); return; }
        final String k = v.key;
        try {
            INFO.execute(new Runnable() { public void run() {
                if (stopped || k == null || !k.equals(v.key)) return;
                final VideoLog.Info r = VideoLog.probeCached(f);
                h.post(new Runnable() { public void run() { if (!stopped && k.equals(v.key)) showVerdict(r, sub, now); } });
            }});
        } catch (Throwable ignored) { }
    }

    private static void showVerdict(VideoLog.Info in, TextView sub, boolean now) {
        if (in == null || now) return;
        // marcado como «no se puede reproducir aquí»: se le suma el formato en cuanto se sabe
        if (sub.getCurrentTextColor() == Main.RED && in.shortText().length() > 0 && (in.ok == null || in.ok)) {
            sub.setText(I18n.t("No se puede reproducir aquí") + " · " + in.shortText());
            return;
        }
        if (in.ok == null || in.ok) return;
        sub.setText(I18n.t("No compatible con esta pantalla") + " · " + in.shortText());
        sub.setTextColor(Main.RED);
    }

    private android.graphics.drawable.Drawable rowBg() {
        android.graphics.drawable.StateListDrawable d = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(Main.INNER);
        g.setCornerRadius(px(14));
        d.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.InsetDrawable(g, px(10), px(1), px(10), px(1)));
        d.addState(new int[0], new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    static String size(long b) {
        if (b >= 1024L * 1024 * 1024) {
            char sep = ',';
            try { sep = new java.text.DecimalFormatSymbols(I18n.loc).getDecimalSeparator(); } catch (Throwable ignored) { }
            return String.format(Locale.US, "%.1f GB", b / (1024f * 1024 * 1024)).replace('.', sep == ',' ? ',' : '.');
        }
        return Math.max(1, Math.round(b / (1024f * 1024))) + " MB";
    }

    static String time(long ms) {
        long t = Math.max(0, ms / 1000);
        long hh = t / 3600, m = (t / 60) % 60, s = t % 60;
        return hh > 0 ? String.format(Locale.US, "%d:%02d:%02d", hh, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }

    // ───────────────────────── miniatura ─────────────────────────

    /** Cuadrito redondeado: la miniatura del video (recortada al centro) con su duración, o un ícono dibujado. */
    static final class Thumb extends View {
        static final int FOLDER = 0, MEMORY = 1, USB = 2, FILM = 3;
        volatile String key;
        private Bitmap bmp;
        private long dur = -1;
        private int icon = FILM;
        private boolean playing;
        private final float s;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
                line = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final Path path = new Path();
        private final Matrix m = new Matrix();

        Thumb(Context c, float s) {
            super(c);
            this.s = s;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setStrokeCap(Paint.Cap.ROUND);
            txt.setTypeface(Typeface.DEFAULT_BOLD);
            txt.setTextSize(11 * s);
            txt.setColor(Color.WHITE);
        }

        void setIcon(int i) { icon = i; bmp = null; shader = null; dur = -1; playing = false; invalidate(); }
        void setPlaying(boolean on) { playing = on; invalidate(); }
        private BitmapShader shader;

        void set(Bitmap b, long d) {
            if (b != bmp) shader = b != null ? new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) : null;
            bmp = b; dur = d; invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), r = 10 * s;
            rf.set(0, 0, w, hh);
            fill.setShader(null);
            fill.setColor(icon == FILM ? 0xFF15171B : Main.INNER);
            c.drawRoundRect(rf, r, r, fill);
            if (bmp != null && shader != null && !bmp.isRecycled()) {
                float k = Math.max(w / bmp.getWidth(), hh / bmp.getHeight());
                m.setScale(k, k);
                m.postTranslate((w - bmp.getWidth() * k) / 2f, (hh - bmp.getHeight() * k) / 2f);
                shader.setLocalMatrix(m);
                img.setShader(shader);
                c.drawRoundRect(rf, r, r, img);
            } else drawIcon(c, w, hh);
            if (icon == FILM && dur > 0) {
                String t = time(dur);
                float tw = txt.measureText(t), pad = 4 * s, bh = 16 * s;
                rf.set(w - tw - pad * 2 - 4 * s, hh - bh - 4 * s, w - 4 * s, hh - 4 * s);
                fill.setColor(0xCC000000);
                c.drawRoundRect(rf, 5 * s, 5 * s, fill);
                c.drawText(t, rf.left + pad, rf.bottom - 4.5f * s, txt);
            }
            if (playing) {
                line.setColor(Main.RED);
                line.setStrokeWidth(2.5f * s);
                rf.set(1.25f * s, 1.25f * s, w - 1.25f * s, hh - 1.25f * s);
                c.drawRoundRect(rf, r, r, line);
            }
        }

        /** Íconos simples dibujados (Android 4.1 no tiene emojis de color). */
        private void drawIcon(Canvas c, float w, float hh) {
            float cx = w / 2, cy = hh / 2, u = Math.min(w, hh) / 54f;
            int col = icon == FILM ? 0xFF5A6070 : Main.RED;
            fill.setColor(col);
            line.setColor(col);
            line.setStrokeWidth(2.4f * u);
            path.reset();
            if (icon == FOLDER) {
                float l = cx - 20 * u, t = cy - 14 * u, rr = cx + 20 * u, b = cy + 14 * u;
                path.moveTo(l, t + 4 * u);
                path.lineTo(l + 13 * u, t + 4 * u);
                path.lineTo(l + 17 * u, t);
                path.lineTo(rr, t);
                path.lineTo(rr, b);
                path.lineTo(l, b);
                path.close();
                fill.setAlpha(255);
                c.drawPath(path, fill);
                fill.setColor(0x33FFFFFF);
                c.drawRect(l, t + 9 * u, rr, t + 11 * u, fill);
            } else if (icon == MEMORY) {
                rf.set(cx - 14 * u, cy - 18 * u, cx + 14 * u, cy + 18 * u);
                c.drawRoundRect(rf, 4 * u, 4 * u, line);
                for (int i = -1; i <= 1; i++) c.drawLine(cx + i * 6 * u, cy - 12 * u, cx + i * 6 * u, cy - 4 * u, line);
            } else if (icon == USB) {
                rf.set(cx - 10 * u, cy - 6 * u, cx + 10 * u, cy + 20 * u);
                c.drawRoundRect(rf, 4 * u, 4 * u, fill);
                rf.set(cx - 7 * u, cy - 19 * u, cx + 7 * u, cy - 6 * u);
                c.drawRect(rf, line);
                fill.setColor(0xFF15171B);
                c.drawRect(cx - 4 * u, cy - 15 * u, cx - 1 * u, cy - 11 * u, fill);
                c.drawRect(cx + 1 * u, cy - 15 * u, cx + 4 * u, cy - 11 * u, fill);
            } else {
                // película: triángulo de «play» en un círculo
                c.drawCircle(cx, cy, 14 * u, line);
                path.moveTo(cx - 4 * u, cy - 7 * u);
                path.lineTo(cx + 7 * u, cy);
                path.lineTo(cx - 4 * u, cy + 7 * u);
                path.close();
                c.drawPath(path, fill);
            }
        }
    }

    /**
     * Saca las miniaturas y la duración en otro hilo (dos a la vez en equipos de 4 núcleos o más con Android 5+; las
     * que se piden último van primero: las filas a la vista). Orden: la caché de la app (`vthumbs/`, unas 300) → la
     * miniatura que Android ya tiene hecha (la de la Galería, instantánea) → sacar un cuadro del video (lo más lento).
     * La duración sale de lo indexado por Android cuando la tiene, así aparece en cuanto se ve la fila.
     */
    static final class Thumbs {
        private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(
                (int) Math.max(3L * 1024 * 1024, Math.min(12L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16))) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
        };
        private static final java.util.HashMap<String, Long> DUR = new java.util.HashMap<String, Long>();
        /** Videos de los que no se pudo sacar miniatura (no se reintentan). */
        private static final java.util.HashSet<String> NONE = new java.util.HashSet<String>();
        private final File dir;
        private final Context ctx;
        private final Handler ui;
        private final LinkedList<Object[]> queue = new LinkedList<Object[]>();
        /** Claves que un hilo ya está sacando (ListView pide la misma fila varias veces; no se hace dos veces). */
        private final java.util.HashSet<String> busy = new java.util.HashSet<String>();
        private int workers;
        private volatile boolean stopped;

        Thumbs(Context c, Handler ui) {
            this.ui = ui;
            ctx = c.getApplicationContext();
            dir = new File(c.getCacheDir(), "vthumbs");
        }

        static String key(File f) {
            String s = f.getAbsolutePath() + "|" + f.length() + "|" + f.lastModified();
            long hsh = 0xcbf29ce484222325L;
            for (int i = 0; i < s.length(); i++) { hsh ^= s.charAt(i); hsh *= 0x100000001b3L; }
            return Long.toHexString(hsh);
        }

        void cancel(Thumb v) { v.key = null; }

        /**
         * 2.35 r7: miniatura tomada por la tarjeta del video mientras suena ({@code VideoCard.grabThumb}). Reemplaza
         * la marca «no se pudo sacar» (.n): en el Picanto sacar un cuadro aparte nunca funciona.
         */
        static void fromPlayer(Context c, final File f, final Bitmap b, final long d) {
            final String k = key(f);
            final File dir = new File(c.getCacheDir(), "vthumbs");
            MEM.put(k, b);
            synchronized (DUR) { NONE.remove(k); if (d > 0) DUR.put(k, (long) d); }
            new Thread(new Runnable() { public void run() {
                try {
                    dir.mkdirs();
                    File jpg = new File(dir, k + ".jpg");
                    FileOutputStream o = new FileOutputStream(jpg);
                    try { b.compress(Bitmap.CompressFormat.JPEG, 82, o); } finally { o.close(); }
                    File txt = new File(dir, k + ".d");
                    long dd = d;
                    if (dd <= 0 && txt.exists()) dd = readLong(txt);
                    o = new FileOutputStream(txt);
                    try { o.write(String.valueOf(dd).getBytes("UTF-8")); } finally { o.close(); }
                    new File(dir, k + ".n").delete();
                    VideoLog.log(I18n.t("Miniatura tomada del video") + " · " + f.getName());
                } catch (Throwable ignored) { }
            }}, "StradaThumbSave").start();
        }

        void request(File f, Thumb v) {
            String k = key(f);
            v.key = k;
            Bitmap b = MEM.get(k);
            Long d;
            boolean none;
            synchronized (DUR) { d = DUR.get(k); none = NONE.contains(k); }
            if (d == null) {
                long[] ms;
                synchronized (MS) { ms = MS.get(f.getAbsolutePath()); }
                if (ms != null && ms[1] > 0) d = ms[1];
            }
            v.set(b, d != null ? d : -1);
            if (b != null || none) return;
            synchronized (queue) {
                queue.addFirst(new Object[]{f, k, v});
                if (queue.size() > 60) queue.removeLast();
                queue.notifyAll();
            }
            int max = Build.VERSION.SDK_INT >= 21 && Runtime.getRuntime().availableProcessors() >= 4 ? 2 : 1;
            while (workers < max) {
                workers++;
                Thread t = new Thread(new Runnable() { public void run() { loop(); } }, "StradaThumbs");
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
            }
        }

        void stop() { stopped = true; synchronized (queue) { queue.clear(); queue.notifyAll(); } }

        private void loop() {
            trim();
            while (!stopped) {
                Object[] job;
                synchronized (queue) {
                    while (queue.isEmpty() && !stopped) { try { queue.wait(); } catch (InterruptedException e) { return; } }
                    if (stopped) return;
                    job = queue.removeFirst();
                    if (!((String) job[1]).equals(((Thumb) job[2]).key) || !busy.add((String) job[1])) continue;   // la fila ya muestra otro video, o ya se está sacando
                }
                final File f = (File) job[0];
                final String k = (String) job[1];
                final Thumb v = (Thumb) job[2];
                long d = -1;
                Bitmap b = null;
                try {
                    File jpg = new File(dir, k + ".jpg"), txt = new File(dir, k + ".d"), none = new File(dir, k + ".n");
                    // 2.35 r5: «.d» sin imagen ni «.n» = una miniatura que falló antes (quizá con un video sonando): se
                    // vuelve a intentar. «.n» = de verdad no se pudo sacar (sin video sonando): no se reintenta.
                    if (txt.exists() && (jpg.exists() || none.exists())) {
                        d = readLong(txt);
                        if (jpg.exists()) b = decode(jpg);
                    } else {
                        // 2.35 r5: en muchas radios hay un solo decodificador de video. No se saca un cuadro (ni se le pide a
                        // Android, que también lo saca con el decodificador) mientras el video de la tarjeta está abriendo:
                        // le quitaría el decodificador. Y el reproductor espera a que termine el que está en curso
                        // (VideoCard.extracting).
                        // (se anota antes de mirar la hora: así, o el reproductor ve esta miniatura y espera, o la
                        // miniatura ve que el reproductor está abriendo y espera ella)
                        boolean got = false;
                        while (!stopped && !got) {
                            while (!stopped && android.os.SystemClock.uptimeMillis() < VideoCard.openingUntil) {
                                try { Thread.sleep(150); } catch (InterruptedException e) { break; }
                            }
                            if (stopped) break;
                            VideoCard.extracting.incrementAndGet();
                            if (android.os.SystemClock.uptimeMillis() >= VideoCard.openingUntil) got = true;
                            else VideoCard.extracting.decrementAndGet();
                        }
                        if (!got) { synchronized (queue) { busy.remove(k); } continue; }
                        boolean playing = VideoCard.playerBusy, busyFail = false;
                        long[] ms;
                        synchronized (MS) { ms = MS.get(f.getAbsolutePath()); }
                        try {
                            if (ms != null) { b = system(ms[0]); if (ms[1] > 0) d = ms[1]; }
                            if (b == null || d <= 0) {
                                boolean frame = b == null;
                                Object[] r = extract(f, frame);
                                if (d <= 0) d = (Long) r[0];
                                if (b == null) b = (Bitmap) r[1];
                                // con un video sonando, el decodificador puede estar ocupado: se reintenta más tarde
                                busyFail = frame && b == null && (playing || VideoCard.playerBusy);
                                if (frame && b == null && !busyFail) VideoLog.log(I18n.t("Sin miniatura") + " · " + f.getName());
                            }
                        } finally { VideoCard.extracting.decrementAndGet(); }
                        dir.mkdirs();
                        if (b == null && !busyFail) new FileOutputStream(none).close();
                        if (b != null) {
                            FileOutputStream o = new FileOutputStream(jpg);
                            try { b.compress(Bitmap.CompressFormat.JPEG, 82, o); } finally { o.close(); }
                            b = small(b);
                        }
                        FileOutputStream o = new FileOutputStream(txt);
                        try { o.write(String.valueOf(d).getBytes("UTF-8")); } finally { o.close(); }
                    }
                } catch (Throwable ignored) { }
                final Bitmap fb = b;
                final long fd = d;
                final boolean retry = b == null && !new File(dir, k + ".n").exists();
                synchronized (queue) { busy.remove(k); }
                ui.post(new Runnable() { public void run() {
                    if (fb != null) MEM.put(k, fb);
                    synchronized (DUR) { DUR.put(k, fd); if (fb == null && !retry) NONE.add(k); }
                    if (k.equals(v.key)) v.set(fb, fd);
                }});
            }
            synchronized (queue) { workers--; }
        }

        /** La miniatura que Android ya tiene hecha para ese video (de la Galería). */
        private Bitmap system(long id) {
            try {
                Bitmap b;
                if (Build.VERSION.SDK_INT >= 29) b = Modern.videoThumb(ctx, id, 192, 192);
                else {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;
                    b = android.provider.MediaStore.Video.Thumbnails.getThumbnail(ctx.getContentResolver(), id,
                            android.provider.MediaStore.Video.Thumbnails.MINI_KIND, o);
                }
                if (b == null) return null;
                if (b.getWidth() > 192) {
                    Bitmap s = Bitmap.createScaledBitmap(b, 192, Math.max(1, Math.round(192f * b.getHeight() / b.getWidth())), true);
                    if (s != b) b.recycle();
                    b = s;
                }
                return b;
            } catch (Throwable e) { return null; }
        }

        /** En memoria van en RGB_565 (la mitad): caben el doble sin que se pierdan al ir y volver en la lista. */
        private static Bitmap small(Bitmap b) {
            if (b.getConfig() == Bitmap.Config.RGB_565) return b;
            try {
                Bitmap c = b.copy(Bitmap.Config.RGB_565, false);
                if (c != null) { b.recycle(); return c; }
            } catch (Throwable ignored) { }
            return b;
        }

        private static Bitmap decode(File jpg) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(jpg.getAbsolutePath(), o);
        }

        /**
         * Duración y (si frame) un cuadro chico (192 px de ancho como mucho) del 15 % del video (el primero suele ser
         * negro). Sin frame solo lee los datos del archivo: no decodifica nada.
         */
        static Object[] extract(File f, boolean frame) {
            Object[] a = extract(f, frame, false);
            // 2.35 r6: si por la ruta no se leyó nada, se prueba abriendo el archivo (algunas radios MediaTek leen
            // distinto por la ruta y por el archivo abierto)
            if ((Long) a[0] <= 0 && a[1] == null) {
                Object[] b = extract(f, frame, true);
                if ((Long) b[0] > 0 || b[1] != null) return b;
            }
            return a;
        }

        private static Object[] extract(File f, boolean frame, boolean fd) {
            long d = -1;
            Bitmap out = null;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            java.io.FileInputStream is = null;
            try {
                if (fd) { is = new java.io.FileInputStream(f); r.setDataSource(is.getFD()); }
                else r.setDataSource(f.getAbsolutePath());
                try { d = Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); } catch (Throwable ignored) { }
                if (frame) {
                    long us = d > 0 ? Math.min(d * 150, 15000000L) : 1000000L;
                    Bitmap full = Build.VERSION.SDK_INT >= 27 ? Modern.scaledFrame(r, us, 192, 192) : r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (full != null) {
                        int w = full.getWidth(), hh = full.getHeight();
                        if (w > 192) {
                            out = Bitmap.createScaledBitmap(full, 192, Math.max(1, Math.round(192f * hh / w)), true);
                            if (out != full) full.recycle();
                        } else out = full;
                    }
                }
            } catch (Throwable ignored) {     // formato que la radio no lee, o sin memoria para un video 4K
            } finally {
                try { r.release(); } catch (Throwable ignored) { }
                try { if (is != null) is.close(); } catch (Throwable ignored) { }
            }
            return new Object[]{d, out};
        }

        private static long readLong(File f) {
            try {
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                try {
                    byte[] b = new byte[32];
                    int n = in.read(b);
                    return n > 0 ? Long.parseLong(new String(b, 0, n, "UTF-8").trim()) : -1;
                } finally { in.close(); }
            } catch (Throwable e) { return -1; }
        }

        /** Deja las 300 miniaturas más nuevas (600 archivos: imagen + duración). */
        private void trim() {
            try {
                File[] fs = dir.listFiles();
                if (fs == null || fs.length <= 600) return;
                java.util.Arrays.sort(fs, new Comparator<File>() { public int compare(File a, File b) { return Long.valueOf(b.lastModified()).compareTo(a.lastModified()); } });
                for (int i = 600; i < fs.length; i++) fs[i].delete();
            } catch (Throwable ignored) { }
        }
    }
}
