package com.stradaos.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Video en lugar del mapa (Ajustes → Mapa y clima → «Tarjeta del centro: Video», opción nueva; de fábrica sigue el mapa):
 * reproduce los videos de la memoria y de las USB con el reproductor de Android (MP4/H.264 y la mayoría de los MKV, según
 * lo que decodifique la radio), pasa al siguiente al terminar y salta los que no puede leer. «Pantalla completa» abre el
 * mismo video en VLC (u otra app de video) desde donde iba.
 *
 * Se dibuja en un TextureView (no en un VideoView, que va en una capa aparte y no admite transparencia): el video se ajusta
 * a la tarjeta sin deformarse y sus bordes se desvanecen hacia el fondo del tema ({@link Feather}), sin cortes rectos.
 *
 * En movimiento (más de 5 km/h por el GPS o el escáner) la imagen se tapa y el audio sigue. La tapa es oscura y deja
 * ver el video muy difuminado detrás (una copia chiquita del cuadro, cada 0,7 s). Según «video_moving»:
 *   «ask» (de fábrica): pregunta «¿Quieres seguir viendo el video?» con Sí / No. «Sí» abre un aviso de cuidado al
 *   manejar (Main, {@link Host#confirmMoving}) y solo si el conductor lo confirma se ve el video, hasta la próxima
 *   detención. «No» deja la tapa (tocarla vuelve a preguntar). Así la decisión es del conductor, avisado.
 *   «cover»: tapa siempre, sin pregunta.
 * («warn», de la Prueba anterior, cuenta como «ask».) Sin datos de velocidad (sin GPS ni escáner) cuenta como en
 * movimiento. La pregunta vuelve a salir cada vez que el carro arranca después de estar detenido 5 s. Abrir el video
 * en VLC (mantener ⛶) solo funciona detenido.
 *
 * 2.35 (carrusel del centro): la tarjeta empieza inactiva y no busca videos hasta que se muestra por primera vez
 * ({@link #setActive}). Al pasar al mapa se guarda el segundo y se suelta el reproductor.
 */
public class VideoCard extends FrameLayout {

    /** La velocidad de ahora en km/h (GPS o escáner); NaN si no se sabe. */
    public interface Speed { float kmh(); }

    /** Quien muestra la tarjeta: la agranda dentro de StradaOS (0 en su lugar, 1 «Solo video», 2 pantalla completa). */
    public interface Host {
        void videoMode(VideoCard v, int mode);
        /** Lista para elegir un video ({@link #files()}, {@link #playAt}). */
        void pickVideo(VideoCard v);
        /** «Sí, seguir viendo» en movimiento: aviso de cuidado; si lo confirma, {@link #acceptMoving()}. */
        void confirmMoving(VideoCard v);
    }

    static final String[] EXT = {".mp4", ".m4v", ".mkv", ".webm", ".3gp", ".mov", ".avi", ".ts"};
    private static final float MOVING_KMH = 5;
    /** Detenido este tiempo, el próximo arranque vuelve a mostrar el aviso. */
    private static final long STOP_RESET_MS = 5000;

    private final SharedPreferences prefs;
    private final Speed speed;
    private final Host host;
    private TextView fullBtn;
    /** «Solo video»: el ícono de cuatro esquinas del «Solo mapa» de Aurora (hacia afuera agranda, hacia adentro vuelve). */
    private Aurora.Corners bigIcon;
    /** 0 en su tarjeta, 1 «Solo video», 2 pantalla completa. */
    int mode;
    private final float s;
    private final TextureView tex;
    /** El video, del tamaño que le toca para no deformarse, con los bordes desvanecidos. */
    private final Feather videoBox;
    /** La línea iluminada alrededor del video, como las tarjetas del tema Cobalto. */
    private final GlowEdge edge;
    private MediaPlayer mp;
    private Surface surface;
    private File current;
    private int vw, vh;
    private final TextView title, empty, playBtn;
    private final LinearLayout bar;
    /** Capa del tamaño del video con el título y los controles. */
    private final FrameLayout ui;
    private final FrameLayout cover;
    private final TextView coverText, coverSub, coverAsk;
    private final LinearLayout coverBtns;
    /** El video difuminado detrás de la tapa, del tamaño y en el lugar del video. */
    private final android.widget.ImageView blurImg;
    private boolean declined;
    private final Handler h = new Handler(Looper.getMainLooper());
    private List<File> list = new ArrayList<File>();
    private int at = -1, resumeMs;
    private boolean paused, warnAccepted, scanning, active, loaded;
    private long stoppedSince;

    public VideoCard(Context c, float scale, SharedPreferences p, Speed sp, Host hs) {
        super(c);
        s = scale; prefs = p; speed = sp; host = hs;
        // sin fondo: alrededor del video (y en sus bordes desvanecidos) se ve el fondo del tema
        tex = new TextureView(c);
        tex.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int hh) {
                surface = new Surface(st);
                if (current != null && active) open(current);
            }
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int hh) { }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                // la pantalla se fue al fondo o la tarjeta se quitó: se guarda dónde iba y se suelta el reproductor
                if (mp != null) { try { resumeMs = mp.getCurrentPosition(); } catch (Throwable ignored) { } closePlayer(); }
                if (surface != null) { surface.release(); surface = null; }
                return true;
            }
            public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
        videoBox = new Feather(c);
        videoBox.addView(tex, new LayoutParams(-1, -1));
        addView(videoBox, new LayoutParams(-1, -1, Gravity.CENTER));
        edge = new GlowEdge(c, Main.LIGHT, scale);
        addView(edge, new LayoutParams(-1, -1, Gravity.CENTER));

        // Controles dentro del video (título arriba, botones abajo al centro), no en el borde de la tarjeta: cada tema
        // pone ahí sus propios botones (Faro: búsqueda, navegación y zoom) y los tapaban. Aparecen al tocar el video y
        // se esconden solos a los 4 s mientras reproduce; en pausa quedan a la vista.
        ui = new FrameLayout(c);
        ui.setOnClickListener(new OnClickListener() { public void onClick(View v) { controls(bar.getVisibility() != VISIBLE); } });
        title = text(13, Color.WHITE, true);
        title.setShadowLayer(4 * s, 0, 0, Color.BLACK);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams tl = new LayoutParams(-1, -2, Gravity.TOP);
        tl.setMargins(Math.round(16 * s), Math.round(12 * s), Math.round(16 * s), 0);
        ui.addView(title, tl);
        bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER);
        playBtn = button("❚❚");
        TextView prev = button("⏮"), nxt = button("⏭"), big = button(""), fb = button("⛶"), pick = button("☰");
        pick.setOnClickListener(new OnClickListener() { public void onClick(View v) { if (host != null && !list.isEmpty()) host.pickVideo(VideoCard.this); controls(true); } });
        title.setOnClickListener(new OnClickListener() { public void onClick(View v) { if (host != null && !list.isEmpty()) host.pickVideo(VideoCard.this); controls(true); } });
        FrameLayout bigBox = new FrameLayout(c);
        bigIcon = new Aurora.Corners(c, Color.WHITE, 1.8f * s);
        LayoutParams il = new LayoutParams(Math.round(22 * s), Math.round(22 * s), Gravity.CENTER);
        bigBox.addView(big, new LayoutParams(-1, -1));
        bigBox.addView(bigIcon, il);
        prev.setOnClickListener(new OnClickListener() { public void onClick(View v) { prev(); controls(true); } });
        playBtn.setOnClickListener(new OnClickListener() { public void onClick(View v) { toggle(); controls(true); } });
        nxt.setOnClickListener(new OnClickListener() { public void onClick(View v) { next(); controls(true); } });
        fullBtn = fb;
        // «Solo video»: ocupa el inicio entre la hora y el dock, en todos los temas (como el «Solo mapa» de Aurora)
        big.setOnClickListener(new OnClickListener() { public void onClick(View v) { if (host != null) host.videoMode(VideoCard.this, mode == 1 ? 0 : 1); controls(true); } });
        // toque: pantalla completa dentro de StradaOS (sigue desde el mismo segundo); mantener: abrir en VLC (solo detenido:
        // en VLC no hay tapa que valga)
        fb.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            if (host != null) host.videoMode(VideoCard.this, mode == 2 ? 0 : 2); else if (stopped()) fullscreen();
            controls(true);
        }});
        fb.setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) {
            if (stopped()) fullscreen();
            else { title.setText(I18n.t("Solo se muestra con el carro detenido")); controls(true); }
            return true;
        }});
        for (View b : new View[]{pick, prev, playBtn, nxt, bigBox, fb}) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Math.round(46 * s), Math.round(40 * s));
            l.setMargins(Math.round(4 * s), 0, Math.round(4 * s), 0);
            bar.addView(b, l);
        }
        LayoutParams bl = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Math.round(14 * s);
        ui.addView(bar, bl);
        bar.setVisibility(INVISIBLE);     // aparecen con el primer video (sin videos no hay controles vacíos)
        addView(ui, new LayoutParams(-1, -1, Gravity.CENTER));

        empty = text(15, 0xFFB8BEC6, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Math.round(24 * s), 0, Math.round(24 * s), 0);
        addView(empty, new LayoutParams(-1, -1, Gravity.CENTER));

        // tapa para cuando el auto avanza (también con los bordes desvanecidos): oscura, con el video muy difuminado detrás
        cover = new FrameLayout(c);
        cover.setBackgroundColor(0xFF0B0C0E);
        cover.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            if (declined) { declined = false; refresh(); }    // dijo «No» y tocó: vuelve a preguntar
        }});
        blurImg = new android.widget.ImageView(c);
        blurImg.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        cover.addView(blurImg, new LayoutParams(0, 0, Gravity.CENTER));
        View scrim = new View(c);
        scrim.setBackgroundColor(0x8C000000);
        cover.addView(scrim, new LayoutParams(-1, -1));
        LinearLayout cv = new LinearLayout(c);
        cv.setOrientation(LinearLayout.VERTICAL);
        cv.setGravity(Gravity.CENTER);
        coverText = text(17, Color.WHITE, true);
        coverText.setGravity(Gravity.CENTER);
        coverText.setPadding(Math.round(20 * s), 0, Math.round(20 * s), 0);
        coverSub = text(13, 0xFFB8BEC6, false);
        coverSub.setGravity(Gravity.CENTER);
        coverSub.setPadding(Math.round(20 * s), Math.round(6 * s), Math.round(20 * s), 0);
        coverSub.setVisibility(GONE);
        coverText.setShadowLayer(6 * s, 0, 0, Color.BLACK);
        coverAsk = text(15, Color.WHITE, false);
        coverAsk.setGravity(Gravity.CENTER);
        coverAsk.setPadding(Math.round(20 * s), Math.round(16 * s), Math.round(20 * s), 0);
        coverAsk.setText(I18n.t("¿Quieres seguir viendo el video?"));
        coverBtns = new LinearLayout(c);
        coverBtns.setGravity(Gravity.CENTER);
        TextView yes = button(I18n.t("Sí")), no = button(I18n.t("No"));
        yes.setTextSize(0, 16 * s); no.setTextSize(0, 16 * s);
        yes.setOnClickListener(new OnClickListener() { public void onClick(View v) { if (host != null) host.confirmMoving(VideoCard.this); else acceptMoving(); } });
        no.setOnClickListener(new OnClickListener() { public void onClick(View v) { declined = true; refresh(); } });
        for (TextView b : new TextView[]{yes, no}) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Math.round(96 * s), Math.round(44 * s));
            bp.setMargins(Math.round(6 * s), 0, Math.round(6 * s), 0);
            coverBtns.addView(b, bp);
        }
        cv.addView(coverText, new LinearLayout.LayoutParams(-2, -2));
        cv.addView(coverSub, new LinearLayout.LayoutParams(-2, -2));
        cv.addView(coverAsk, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, -2);
        ol.topMargin = Math.round(12 * s);
        cv.addView(coverBtns, ol);
        cover.addView(cv, new LayoutParams(-1, -1, Gravity.CENTER));
        cover.setVisibility(GONE);
        Feather coverBox = new Feather(c);
        coverBox.addView(cover, new LayoutParams(-1, -1));
        addView(coverBox, new LayoutParams(-1, -1));

        empty.setVisibility(GONE);
    }

    /**
     * Página activa del carrusel (o la tarjeta sola): la primera vez busca los videos; al volver sigue desde donde iba.
     * Inactiva: guarda el segundo, suelta el reproductor y deja de revisar la velocidad.
     */
    void setActive(boolean on) {
        if (on == active) return;
        active = on;
        h.removeCallbacks(tick);
        if (on) {
            if (!loaded) { loaded = true; load(); }
            else if (current != null && surface != null) open(current);
            h.post(tick);
        } else {
            if (at >= 0) { resumeMs = position(); prefs.edit().putInt("video_pos", resumeMs).apply(); }
            closePlayer();
            h.removeCallbacks(hideControls);
        }
    }

    boolean isActive() { return active; }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setTextSize(0, sp * s);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String label) {
        TextView b = text(18, Color.WHITE, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xB0202428);
        g.setCornerRadius(12 * s);
        b.setBackgroundDrawable(g);
        return b;
    }

    // ───────────────────────── lista de videos ─────────────────────────

    /** Busca los videos en otro hilo (en una USB grande tarda unos segundos) y arranca donde había quedado. */
    void load() {
        if (scanning) return;
        scanning = true;
        empty.setText(I18n.t("Buscando videos en la memoria y las USB…"));
        empty.setVisibility(VISIBLE);
        final String dir = prefs.getString("video_dir", "");
        new Thread(new Runnable() { public void run() {
            final List<File> found = find(dir);
            h.post(new Runnable() { public void run() {
                scanning = false;
                list = found;
                if (list.isEmpty()) {
                    empty.setText(I18n.t("No hay videos. Copia videos (MP4 o MKV) a la memoria o a una USB, o elige la carpeta en Ajustes → Mapa y clima."));
                    empty.setVisibility(VISIBLE);
                    title.setText("");
                    h.removeCallbacks(hideControls);
                    bar.setVisibility(INVISIBLE);
                    return;
                }
                empty.setVisibility(GONE);
                String last = prefs.getString("video_last", "");
                int i = 0;
                for (int k = 0; k < list.size(); k++) if (list.get(k).getAbsolutePath().equals(last)) i = k;
                resumeMs = last.length() > 0 && list.get(i).getAbsolutePath().equals(last) ? prefs.getInt("video_pos", 0) : 0;
                play(i);
            }});
        }}, "StradaVideos").start();
    }

    /** Videos de una carpeta (o de toda la memoria y las USB si «dir» está vacía), ordenados por carpeta y nombre. */
    static List<File> find(String dir) {
        java.util.LinkedHashSet<String> roots = new java.util.LinkedHashSet<String>();
        if (dir != null && dir.length() > 0) roots.add(dir);
        else {
            roots.add(android.os.Environment.getExternalStorageDirectory().getAbsolutePath());
            String sec = System.getenv("SECONDARY_STORAGE");
            if (sec != null) for (String r : sec.split(":")) if (r.length() > 0) roots.add(r);
            roots.add("/storage");
            roots.add("/mnt");
        }
        List<File> out = new ArrayList<File>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        int[] budget = {6000};
        for (String r : roots) scan(new File(r), 0, out, seen, budget);
        Collections.sort(out, new Comparator<File>() { public int compare(File a, File b) { return a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()); } });
        return out;
    }

    private static void scan(File d, int depth, List<File> out, java.util.Set<String> seen, int[] budget) {
        if (depth > 5 || budget[0]-- <= 0 || out.size() >= 1000) return;
        String canon;
        try { canon = d.getCanonicalPath(); } catch (Throwable e) { return; }
        if (!seen.add(canon)) return;
        File[] fs;
        try { fs = d.listFiles(); } catch (Throwable e) { return; }
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName();
            if (n.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (n.equals("Android") || n.equals("obb") || n.equals("asec") || n.equals("secure") || n.equals("runtime")) continue;
                scan(f, depth + 1, out, seen, budget);
            } else if (isVideo(n) && f.length() > 100 * 1024) {
                String c;
                try { c = f.getCanonicalPath(); } catch (Throwable e) { continue; }
                if (seen.add(c)) out.add(f);
            }
        }
    }

    static boolean isVideo(String name) {
        String n = name.toLowerCase(Locale.US);
        for (String e : EXT) if (n.endsWith(e)) return true;
        return false;
    }

    // ───────────────────────── reproducción ─────────────────────────

    private String name() { return at >= 0 && at < list.size() ? list.get(at).getName() : ""; }

    private void play(int i) {
        if (list.isEmpty()) return;
        at = (i + list.size()) % list.size();
        File f = list.get(at);
        title.setText((at + 1) + "/" + list.size() + " · " + f.getName());
        controls(true);
        prefs.edit().putString("video_last", f.getAbsolutePath()).putInt("video_pos", 0).apply();
        open(f);
    }

    /** Abre el video en el TextureView; si todavía no hay superficie, se abre cuando aparezca. */
    private void open(final File f) {
        current = f;
        closePlayer();
        if (surface == null || !active) return;
        final MediaPlayer m = new MediaPlayer();
        mp = m;
        try {
            m.setDataSource(f.getAbsolutePath());
            m.setSurface(surface);
            m.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { public void onPrepared(MediaPlayer p) {
                if (mp != m) return;
                fit(m.getVideoWidth(), m.getVideoHeight());
                if (resumeMs > 0) { m.seekTo(resumeMs); resumeMs = 0; }
                if (!paused) m.start();
            }});
            m.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() { public void onVideoSizeChanged(MediaPlayer p, int w, int hh) { if (mp == m) fit(w, hh); } });
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { public void onCompletion(MediaPlayer p) { if (mp == m) next(); } });
            m.setOnErrorListener(new MediaPlayer.OnErrorListener() { public boolean onError(MediaPlayer p, int what, int extra) { if (mp == m) failed(); return true; } });
            m.prepareAsync();
        } catch (Throwable t) {
            failed();
        }
    }

    /** Formato que la radio no decodifica: se avisa y se pasa al siguiente (en pantalla completa, VLC sí lo lee). */
    private void failed() {
        closePlayer();
        if (!active) return;
        title.setText(I18n.t("No se puede reproducir aquí") + ": " + name() + " · " + I18n.t("ábrelo en pantalla completa"));
        h.postDelayed(new Runnable() { public void run() { next(); } }, 2500);
    }

    private void closePlayer() {
        if (mp == null) return;
        try { mp.release(); } catch (Throwable ignored) { }
        mp = null;
    }

    private int position() {
        try { return mp != null ? mp.getCurrentPosition() : resumeMs; } catch (Throwable t) { return 0; }
    }

    /**
     * Tamaño del video sin deformarlo. En su tarjeta y en pantalla completa entra completo y centrado; en «Solo video»
     * es panorámico: ocupa todo el ancho del área y, si no alcanza el alto, recorta un poco arriba y abajo (el
     * TextureView se escala con una matriz para no estirar la imagen; el difuminado disimula el recorte).
     */
    private void fit(int w, int hh) {
        if (w <= 0 || hh <= 0) return;
        vw = w; vh = hh;
        int cw = getWidth(), ch = getHeight();
        if (cw <= 0 || ch <= 0) return;
        int bw, bh;
        if (mode == 1) {
            bw = cw;
            bh = Math.min(ch, Math.round(cw * hh / (float) w));
        } else {
            float k = Math.min(cw / (float) w, ch / (float) hh);
            bw = Math.round(w * k);
            bh = Math.round(hh * k);
        }
        // el TextureView estira el video a su tamaño: se corrige para que quede con su proporción, recortado al centro
        float sx = 1, sy = 1, natH = bw * hh / (float) w;
        if (natH > bh + 1) sy = natH / bh;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(sx, sy, bw / 2f, bh / 2f);
        tex.setTransform(m);
        LayoutParams l = (LayoutParams) videoBox.getLayoutParams();
        l.width = bw;
        l.height = bh;
        l.gravity = Gravity.CENTER;
        videoBox.setLayoutParams(l);
        LayoutParams el = (LayoutParams) edge.getLayoutParams();
        el.width = l.width; el.height = l.height; el.gravity = Gravity.CENTER;
        edge.setLayoutParams(el);
        // los controles nunca más angostos que sus 6 botones (con videos verticales se cortaban)
        LayoutParams ul = (LayoutParams) ui.getLayoutParams();
        ul.width = Math.min(cw, Math.max(l.width, Math.round(6 * 54 * s + 16 * s)));
        ul.height = l.height; ul.gravity = Gravity.CENTER;
        ui.setLayoutParams(ul);
        LayoutParams gl = (LayoutParams) blurImg.getLayoutParams();
        gl.width = l.width; gl.height = l.height; gl.gravity = Gravity.CENTER;
        blurImg.setLayoutParams(gl);
    }

    private final Runnable hideControls = new Runnable() { public void run() { if (!paused) controls(false); } };

    /** Muestra u oculta el título y los botones; a la vista se esconden solos a los 4 s (salvo en pausa). */
    private void controls(boolean on) {
        bar.setVisibility(on ? VISIBLE : INVISIBLE);
        title.setVisibility(on ? VISIBLE : INVISIBLE);
        h.removeCallbacks(hideControls);
        if (on) h.postDelayed(hideControls, 4000);
    }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        if (vw > 0) post(new Runnable() { public void run() { fit(vw, vh); } });
    }

    /** Lo llama el host al cambiar de tamaño: el botón del modo activo pasa a ✕ (volver a la tarjeta). */
    void setMode(int m) {
        mode = m;
        bigIcon.inward = m == 1;
        bigIcon.invalidate();
        fullBtn.setText(m == 2 ? "✕" : "⛶");
        if (vw > 0) post(new Runnable() { public void run() { fit(vw, vh); } });
    }

    List<File> files() { return list; }
    int index() { return at; }
    /** Reproduce el video i de la lista (elegido en la lista de Main). */
    void playAt(int i) { if (!list.isEmpty() && active) { resumeMs = 0; paused = false; playBtn.setText("❚❚"); play(i); } }

    /** Revisa ya la velocidad y la tapa (sin duplicar la revisión de cada segundo). */
    private void refresh() { h.removeCallbacks(tick); if (active) tick.run(); }

    /** Confirmó el aviso de cuidado: el video se ve hasta que el carro se detenga. */
    void acceptMoving() { warnAccepted = true; declined = false; setCovered(false); }

    void next() { if (!list.isEmpty() && active) { resumeMs = 0; play(at + 1); } }
    void prev() { if (!list.isEmpty()) { resumeMs = 0; play(at - 1); } }

    void toggle() {
        paused = !paused;
        try { if (mp != null) { if (paused) mp.pause(); else mp.start(); } } catch (Throwable ignored) { }
        playBtn.setText(paused ? "▶" : "❚❚");
    }

    /** Abrir en VLC (o la app de video que elija el usuario), desde donde iba: mantener presionado ⛶. */
    private void fullscreen() {
        if (at < 0 || at >= list.size()) return;
        File f = list.get(at);
        int pos = position();
        try { if (mp != null && mp.isPlaying()) mp.pause(); } catch (Throwable ignored) { }
        paused = true;
        playBtn.setText("▶");
        String ext = f.getName().substring(f.getName().lastIndexOf('.'));
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(ApkProvider.share(f, ext), "video/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("position", (long) pos);           // VLC: empieza desde aquí
        i.putExtra("title", f.getName());
        try {
            getContext().getPackageManager().getPackageInfo("org.videolan.vlc", 0);
            i.setPackage("org.videolan.vlc");
            getContext().startActivity(i);
        } catch (Throwable notVlc) {
            try { getContext().startActivity(Intent.createChooser(i, I18n.t("Ver en pantalla completa")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Throwable t) { title.setText(I18n.t("No hay una app de video para la pantalla completa")); }
        }
    }

    /** La pantalla de inicio se va al fondo: se guarda dónde iba. */
    void onPause() {
        if (at >= 0 && active) prefs.edit().putInt("video_pos", position()).apply();
        try { if (mp != null && mp.isPlaying()) mp.pause(); } catch (Throwable ignored) { }
    }

    void onResume() { try { if (active && !paused && mp != null) mp.start(); } catch (Throwable ignored) { } }

    /** La tarjeta se reemplaza (cambio de tema o de ajustes): suelta el reproductor. */
    void release() {
        h.removeCallbacks(tick);
        h.removeCallbacks(blurTick);
        h.removeCallbacks(hideControls);
        onPause();
        closePlayer();
        active = false;
    }

    /**
     * Contenedor con los bordes desvanecidos: lo de adentro se dibuja en una capa y una máscara (opaca al centro,
     * transparente en el borde, con una curva suave) le recorta la transparencia. Así el video no termina en un corte recto.
     */
    static class Feather extends FrameLayout {
        private Bitmap mask;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

        Feather(Context c) {
            super(c);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
            super.onSizeChanged(w, hh, ow, oh);
            mask = build(w, hh);
        }

        @Override protected void dispatchDraw(Canvas c) {
            if (mask == null) { super.dispatchDraw(c); return; }
            //noinspection deprecation (la versión sin banderas es API 21; StradaOS corre desde 4.1)
            int sc = c.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);
            super.dispatchDraw(c);
            c.drawBitmap(mask, 0, 0, paint);
            c.restoreToCount(sc);
        }

        /** Opaca al centro y transparente en los bordes: el difuminado ocupa el 8 % del lado corto. */
        static Bitmap build(int w, int hh) {
            if (w <= 0 || hh <= 0) return null;
            float f = 0.08f * Math.min(w, hh);
            Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ALPHA_8);
            Canvas c = new Canvas(b);
            // curva suave (no lineal) para que no se note dónde empieza el difuminado
            int[] col = {0x00000000, 0x30000000, 0x90000000, 0xE0000000, 0xFF000000, 0xFF000000, 0xE0000000, 0x90000000, 0x30000000, 0x00000000};
            float fx = f / w, fy = f / hh;
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, w, 0, col, stops(fx), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hh, p);
            Paint q = new Paint();
            q.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            q.setShader(new LinearGradient(0, 0, 0, hh, col, stops(fy), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hh, q);
            return b;
        }

        private static float[] stops(float f) {
            return new float[]{0, f * 0.25f, f * 0.55f, f * 0.8f, f, 1 - f, 1 - f * 0.8f, 1 - f * 0.55f, 1 - f * 0.25f, 1};
        }
    }

    /**
     * Borde iluminado como el de las tarjetas «Música» y «Mi vehículo» del tema Cobalto ({@link Cobalto.SkewCard}): una
     * línea fina que se apaga hacia el medio de cada lado y, en las esquinas de arriba a la izquierda y abajo a la
     * derecha, un brillo (núcleo casi blanco y halo azul). En el tema claro, solo la línea blanca.
     */
    static class GlowEdge extends View {
        private final boolean light;
        private final float stroke, rad;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private Shader g1, g2, h1, h2;

        GlowEdge(Context c, boolean light, float s) {
            super(c);
            this.light = light;
            stroke = Math.max(1f, 1.3f * s);
            rad = 14 * s;
            for (Paint p : new Paint[]{line, glow, halo}) { p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); }
            line.setStrokeWidth(stroke);
            glow.setStrokeWidth(stroke * 2f);
            halo.setStrokeWidth(stroke * 8f);
        }

        @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
            super.onSizeChanged(w, hh, ow, oh);
            float e = stroke * 4, l = e, t = e, r = w - e, b = hh - e;
            path.reset();
            path.addRoundRect(new android.graphics.RectF(l, t, r, b), rad, rad, android.graphics.Path.Direction.CW);
            // la línea: fuerte en las dos esquinas con brillo y apagada en las otras dos
            int ec = Cobalto.glassEdge(light), clear = ec & 0x00FFFFFF;
            line.setShader(new LinearGradient(l, t, r, b, new int[]{ec, clear, clear, ec}, new float[]{0f, 0.38f, 0.62f, 1f}, Shader.TileMode.CLAMP));
            float gr = rad * 2.6f, x1 = l + rad * 0.3f, y1 = t + rad * 0.3f, x2 = r - rad * 0.3f, y2 = b - rad * 0.3f;
            int hot = light ? 0xCCFFFFFF : 0xF2D6ECFF, mid = light ? 0x40FFFFFF : 0x807FB6FF, soft = light ? 0x30FFFFFF : 0x557FB6FF;
            g1 = new android.graphics.RadialGradient(x1, y1, gr, new int[]{hot, mid, 0}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            g2 = new android.graphics.RadialGradient(x2, y2, gr, new int[]{hot, mid, 0}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            h1 = new android.graphics.RadialGradient(x1, y1, gr * 0.8f, new int[]{soft, 0}, null, Shader.TileMode.CLAMP);
            h2 = new android.graphics.RadialGradient(x2, y2, gr * 0.8f, new int[]{soft, 0}, null, Shader.TileMode.CLAMP);
        }

        @Override protected void onDraw(Canvas c) {
            if (g1 == null) return;
            c.drawPath(path, line);
            if (light) return;     // en claro: solo la línea, como Cobalto
            halo.setShader(h1); c.drawPath(path, halo);
            halo.setShader(h2); c.drawPath(path, halo);
            glow.setShader(g1); c.drawPath(path, glow);
            glow.setShader(g2); c.drawPath(path, glow);
        }
    }

    // ───────────────────────── en movimiento ─────────────────────────

    /** ¿Se sabe que el carro está detenido? (velocidad conocida y de 5 km/h o menos) */
    private boolean stopped() {
        float v = speed != null ? speed.kmh() : Float.NaN;
        return !Float.isNaN(v) && v <= MOVING_KMH;
    }

    /**
     * La tapa es opaca, pero sus bordes se desvanecen: mientras está puesta, el video queda casi transparente para que
     * no se asome por ahí. No se oculta del todo: el reproductor necesita que alguien siga tomando sus cuadros (si no,
     * en algunas radios se traba también el audio).
     */
    private void setCovered(boolean on) {
        boolean was = cover.getVisibility() == VISIBLE;
        cover.setVisibility(on ? VISIBLE : GONE);
        videoBox.setAlpha(on ? 0.02f : 1f);
        edge.setVisibility(on ? INVISIBLE : VISIBLE);
        h.removeCallbacks(blurTick);
        if (on) { if (!was) blurTick.run(); else h.postDelayed(blurTick, 700); }
    }

    /**
     * El video difuminado de la tapa: una copia chiquita del cuadro de ahora (48 px de ancho), agrandada al doble y
     * pasada dos veces por un difuminado de caja; la ImageView la estira al tamaño del video. Barato también en la radio.
     */
    private final Runnable blurTick = new Runnable() { public void run() {
        if (!active || cover.getVisibility() != VISIBLE) return;
        try {
            if (vw > 0 && vh > 0 && mp != null && tex.isAvailable()) {
                int w0 = 48, h0 = Math.max(4, Math.round(48f * vh / vw));
                Bitmap small = tex.getBitmap(w0, h0);
                if (small != null) {
                    Bitmap big = Bitmap.createScaledBitmap(small, w0 * 2, h0 * 2, true);
                    if (big != small) small.recycle();
                    boxBlur(big, 4); boxBlur(big, 4);
                    blurImg.setImageBitmap(big);
                }
            }
        } catch (Throwable ignored) { }
        h.postDelayed(this, 700);
    }};

    /** Difuminado de caja (horizontal y vertical) en el lugar; para bitmaps chiquitos. */
    static void boxBlur(Bitmap b, int r) {
        int w = b.getWidth(), hh = b.getHeight();
        int[] px = new int[w * hh], out = new int[w * hh];
        b.getPixels(px, 0, w, 0, 0, w, hh);
        for (int pass = 0; pass < 2; pass++) {
            boolean horiz = pass == 0;
            int lines = horiz ? hh : w, len = horiz ? w : hh;
            for (int L = 0; L < lines; L++) {
                for (int i = 0; i < len; i++) {
                    int rs = 0, gs = 0, bs = 0, n = 0;
                    for (int k = Math.max(0, i - r); k <= Math.min(len - 1, i + r); k++) {
                        int c = px[horiz ? L * w + k : k * w + L];
                        rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
                    }
                    out[horiz ? L * w + i : i * w + L] = 0xFF000000 | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
                }
            }
            int[] t = px; px = out; out = t;
        }
        b.setPixels(px, 0, w, 0, 0, w, hh);
    }

    private final Runnable tick = new Runnable() { public void run() {
        if (!active) return;
        float v = speed != null ? speed.kmh() : Float.NaN;
        boolean unknown = Float.isNaN(v), moving = unknown || v > MOVING_KMH;
        long now = android.os.SystemClock.elapsedRealtime();
        if (!moving) { if (stoppedSince == 0) stoppedSince = now; } else stoppedSince = 0;
        // detenido unos segundos: el próximo arranque vuelve a avisar (no solo la primera vez)
        if (stoppedSince != 0 && now - stoppedSince > STOP_RESET_MS) { warnAccepted = false; declined = false; }
        boolean ask = !"cover".equals(prefs.getString("video_moving", "ask"));
        if (moving && !list.isEmpty()) {
            coverText.setText(I18n.t(unknown ? "Solo se muestra con el carro detenido" : "Imagen en pausa mientras manejas · el audio sigue"));
            coverSub.setText(I18n.t("Sin GPS ni escáner no se sabe si el carro va andando"));
            coverSub.setVisibility(unknown ? VISIBLE : GONE);
            boolean showAsk = ask && !declined;
            coverAsk.setVisibility(showAsk ? VISIBLE : GONE);
            coverBtns.setVisibility(showAsk ? VISIBLE : GONE);
            setCovered(!(ask && warnAccepted));
        } else {
            setCovered(false);
        }
        h.postDelayed(this, 1000);
    }};
}
