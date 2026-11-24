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
import android.graphics.RectF;
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
 * ver el video muy difuminado detrás (Android 12+: difuminado en vivo; Android 4–11: copias chiquitas unas 8 veces por
 * segundo con fundido). Según «video_moving»:
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
    /** Íconos de pausa/reproducir y de pantalla completa/cerrar (cambian según el estado). */
    private CtlIcon playIco, fullIco;
    /** «Solo video»: el ícono de cuatro esquinas del «Solo mapa» de Aurora (hacia afuera agranda, hacia adentro vuelve). */
    private Aurora.Corners bigIcon;
    /** 0 en su tarjeta, 1 «Solo video», 2 pantalla completa. */
    int mode;
    private final float s;
    /**
     * Donde se dibuja el video. 2.35 r5: se cambia por uno nuevo en cada video y en cada reintento. En la radio del
     * Picanto (Android 4.2), después de un video que no abrió, la misma superficie ya no recibía el siguiente (no se veía
     * nada hasta cambiar de tema, que la recreaba) y el último cuadro del video anterior se quedaba a la vista.
     */
    private TextureView tex;
    /** La transformación de {@link #fit} (se aplica también al TextureView nuevo). */
    private android.graphics.Matrix texMatrix;
    /** Cuadros que llegaron desde que se abrió el video de ahora (0 = todavía no se ve nada). */
    private int frames;
    /** El reproductor ya está listo: antes, en las radios MediaTek, getDuration/getCurrentPosition dan basura. */
    private boolean prepared;
    /** Reintentos del video de ahora (se reinician al cambiar de video). */
    private int tries;
    /** Videos seguidos que no se pudieron abrir (si son todos los de la lista, se deja de saltar). */
    private int failStreak;
    /** Lo que se sabe del archivo de ahora (códec, tamaño), para los avisos y el diagnóstico. */
    private VideoLog.Info info;
    private long openAt;
    /** Archivo esperando a que terminen las miniaturas para abrirse. */
    private File pending;
    private long waitSince;

    /**
     * 2.35 r5: en muchas radios hay un solo decodificador de video. Si el selector está sacando una miniatura cuando se
     * abre un video, el reproductor no consigue el decodificador y el video «no se puede reproducir» (o suena sin
     * imagen). Por eso: el reproductor espera a que terminen las miniaturas en curso ({@link #extracting}) y las
     * miniaturas no empiezan mientras un video está abriendo ({@link #openingUntil}).
     */
    static final java.util.concurrent.atomic.AtomicInteger extracting = new java.util.concurrent.atomic.AtomicInteger();
    static volatile long openingUntil;
    /** Hay un reproductor abierto (las miniaturas que fallen ahora se reintentan después). */
    static volatile boolean playerBusy;
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
    /** 2.35: barra de línea de tiempo (posición / duración; arrastrar para adelantar o retroceder). */
    private final Timeline line;
    /** Capa del tamaño del video con el título y los controles. */
    private final FrameLayout ui;
    private final FrameLayout cover;
    private final TextView coverText, coverSub, coverAsk;
    private final LinearLayout coverBtns;
    /**
     * El video difuminado detrás de la tapa, del tamaño y en el lugar del video (Android 4–11). Son dos: cada cuadro
     * nuevo aparece sobre el anterior con un fundido, así no se ve a saltos aunque se copie pocas veces.
     */
    private final android.widget.ImageView blurImg, blurImg2;
    /** Android 12+: el video mismo se difumina en vivo (RenderEffect), sin copias. */
    private static final boolean LIVE_BLUR = android.os.Build.VERSION.SDK_INT >= 31;
    /** Cada cuánto se copia un cuadro para el difuminado (se ajusta solo según lo que tarda la pantalla). */
    private int blurMs = 125;
    private boolean blurFront, liveBlurOn, liveBlurFailed;
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
        tex = makeTexture();
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
        // íconos dibujados en código: Android 4.1/4.2 no tienen ⏮ ⏭ ☰ ⛶ ▶ en su letra (salían cuadros vacíos)
        playBtn = button("");
        TextView prev = button(""), nxt = button(""), big = button(""), fb = button(""), pick = button("");
        playIco = new CtlIcon(c, CtlIcon.PAUSE);
        fullIco = new CtlIcon(c, CtlIcon.FULL);
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
        for (View b : new View[]{iconBox(pick, new CtlIcon(c, CtlIcon.LIST)), iconBox(prev, new CtlIcon(c, CtlIcon.PREV)), iconBox(playBtn, playIco),
                iconBox(nxt, new CtlIcon(c, CtlIcon.NEXT)), bigBox, iconBox(fb, fullIco)}) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Math.round(46 * s), Math.round(40 * s));
            l.setMargins(Math.round(4 * s), 0, Math.round(4 * s), 0);
            bar.addView(b, l);
        }
        LayoutParams bl = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Math.round(14 * s);
        ui.addView(bar, bl);
        bar.setVisibility(INVISIBLE);     // aparecen con el primer video (sin videos no hay controles vacíos)
        line = new Timeline(c, s, new Timeline.L() {
            public void seek(int ms) {
                try { if (mp != null && prepared) mp.seekTo(ms); else resumeMs = ms; } catch (Throwable ignored) { }
                controls(true);
            }
            public void touching() { h.removeCallbacks(hideControls); }
        });
        LayoutParams ll = new LayoutParams(-1, Math.round(30 * s), Gravity.BOTTOM);
        ll.setMargins(Math.round(16 * s), 0, Math.round(16 * s), Math.round(60 * s));
        ui.addView(line, ll);
        line.setVisibility(INVISIBLE);
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
        blurImg2 = new android.widget.ImageView(c);
        blurImg2.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        cover.addView(blurImg2, new LayoutParams(0, 0, Gravity.CENTER));
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

    /** Un TextureView con su escucha: solo el que está puesto ({@link #tex}) abre y cierra el reproductor. */
    private TextureView makeTexture() {
        final TextureView t = new TextureView(getContext());
        t.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            private Surface mine;
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int hh) {
                if (t != tex) return;
                mine = new Surface(st);
                surface = mine;
                if (current != null && active) open(current);
            }
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int hh) { }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                // la pantalla se fue al fondo o la tarjeta se quitó: se guarda dónde iba y se suelta el reproductor
                if (t == tex) {
                    if (mp != null) { resumeMs = position(); closePlayer(); }
                    surface = null;
                }
                if (mine != null) { mine.release(); mine = null; }
                return true;
            }
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
                if (t != tex) return;
                if (frames++ == 0) firstFrame();
            }
        });
        if (texMatrix != null) t.setTransform(texMatrix);
        return t;
    }

    /** Cambia el TextureView por uno nuevo (sin el último cuadro del video anterior y con una superficie limpia). */
    private void newTexture() {
        closePlayer();
        TextureView old = tex;
        tex = makeTexture();
        surface = null;
        hdrOn = null;
        videoBox.addView(tex, 0, new LayoutParams(-1, -1));
        if (old != null) videoBox.removeView(old);     // su escucha suelta su superficie
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
            h.removeCallbacks(timeTick);
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

    /** El botón con su ícono encima, centrado (el ícono no toma los toques: van al botón). */
    private View iconBox(TextView b, View icon) {
        FrameLayout box = new FrameLayout(getContext());
        box.addView(b, new LayoutParams(-1, -1));
        box.addView(icon, new LayoutParams(Math.round(22 * s), Math.round(22 * s), Gravity.CENTER));
        return box;
    }

    /** Íconos de los controles del video, dibujados (iguales en Android 4.1 y en Android 15). */
    static class CtlIcon extends View {
        static final int PREV = 0, NEXT = 1, LIST = 2, PLAY = 3, PAUSE = 4, FULL = 5, CLOSE = 6;
        private int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), st = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();

        CtlIcon(Context c, int kind) {
            super(c);
            this.kind = kind;
            p.setColor(Color.WHITE);
            st.setColor(Color.WHITE);
            st.setStyle(Paint.Style.STROKE);
            st.setStrokeCap(Paint.Cap.ROUND);
            st.setStrokeJoin(Paint.Join.ROUND);
        }

        void set(int k) { if (k != kind) { kind = k; invalidate(); } }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), u = Math.min(w, hh) / 22f, cx = w / 2, cy = hh / 2;
            st.setStrokeWidth(2.2f * u);
            path.reset();
            switch (kind) {
                case PREV: case NEXT: {
                    float d = kind == PREV ? -1 : 1;
                    c.drawRect(cx + d * 6 * u - u, cy - 7 * u, cx + d * 6 * u + u, cy + 7 * u, p);
                    path.moveTo(cx + d * 4 * u, cy);
                    path.lineTo(cx - d * 7 * u, cy - 7 * u);
                    path.lineTo(cx - d * 7 * u, cy + 7 * u);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case LIST:
                    for (int i = -1; i <= 1; i++) c.drawLine(cx - 7 * u, cy + i * 6 * u, cx + 7 * u, cy + i * 6 * u, st);
                    break;
                case PLAY:
                    path.moveTo(cx - 5 * u, cy - 8 * u);
                    path.lineTo(cx + 8 * u, cy);
                    path.lineTo(cx - 5 * u, cy + 8 * u);
                    path.close();
                    c.drawPath(path, p);
                    break;
                case PAUSE:
                    c.drawRect(cx - 6 * u, cy - 7 * u, cx - 2 * u, cy + 7 * u, p);
                    c.drawRect(cx + 2 * u, cy - 7 * u, cx + 6 * u, cy + 7 * u, p);
                    break;
                case FULL: {
                    float a = 8 * u, b = 3.5f * u;
                    for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
                        path.moveTo(cx + sx * a, cy + sy * (a - b));
                        path.lineTo(cx + sx * a, cy + sy * a);
                        path.lineTo(cx + sx * (a - b), cy + sy * a);
                    }
                    c.drawPath(path, st);
                    break;
                }
                case CLOSE:
                    c.drawLine(cx - 6 * u, cy - 6 * u, cx + 6 * u, cy + 6 * u, st);
                    c.drawLine(cx + 6 * u, cy - 6 * u, cx - 6 * u, cy + 6 * u, st);
                    break;
            }
        }
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
        final boolean flat = prefs.getBoolean("video_flat", false);
        new Thread(new Runnable() { public void run() {
            // lo que decodifica la pantalla, una sola vez y antes del primer video (en Android 4.x leerlo abre los
            // decodificadores: con un video sonando podría trabarlo)
            try { VideoLog.ensureDecoders(); } catch (Throwable ignored) { }
            List<File> f0 = flat ? folder(new File(dir)) : find(dir);
            // la carpeta elegida en «Elegir video» ya no está (USB quitada): se buscan todos
            if (flat && f0.isEmpty()) f0 = find("");
            final List<File> found = f0;
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
                // «Elegir video» abre al instante: su búsqueda se arma unos segundos después (sin estorbar al video)
                h.postDelayed(new Runnable() { public void run() { try { VideoPicker.prewarm(getContext()); } catch (Throwable ignored) { } } }, 4000);
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

    static void scan(File d, int depth, List<File> out, java.util.Set<String> seen, int[] budget) {
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

    /** 2.35: los videos de una sola carpeta (sin subcarpetas), por nombre: la lista que se eligió en «Elegir video». */
    static List<File> folder(File d) {
        List<File> out = new ArrayList<File>();
        File[] fs;
        try { fs = d.listFiles(); } catch (Throwable e) { fs = null; }
        if (fs != null) for (File f : fs) if (!f.getName().startsWith(".") && f.isFile() && isVideo(f.getName()) && f.length() > 100 * 1024) out.add(f);
        Collections.sort(out, new Comparator<File>() { public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); } });
        return out;
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
        tries = 0;
        h.removeCallbacks(pictureCheck);
        empty.setVisibility(GONE);
        // superficie nueva para cada video: sin el último cuadro del anterior y sin arrastrar un fallo
        if (frames > 0 || current != null) newTexture();
        open(f);
    }

    /** Abre el video en el TextureView; si todavía no hay superficie, se abre cuando aparezca. */
    private void open(final File f) {
        if (current != f) { info = null; tries = 0; probing = null; }
        current = f;
        closePlayer();
        h.removeCallbacks(pendingOpen);
        line.set(0, 0);
        if (surface == null || !active || held) return;
        long now = android.os.SystemClock.uptimeMillis();
        // las miniaturas no empiezan mientras abre; si hay una en curso, se espera a que suelte el decodificador (hasta 3 s)
        openingUntil = now + 6000;
        if (extracting.get() > 0) {
            if (pending != f) { pending = f; waitSince = now; }
            if (now - waitSince < 3000) { h.postDelayed(pendingOpen, 120); return; }
            VideoLog.log(I18n.t("Miniatura en curso: se abre igual") + " · " + f.getName());
        }
        pending = null;
        if (info == null && probing != f) { probing = f; probe(f); }
        final MediaPlayer m = new MediaPlayer();
        mp = m;
        playerBusy = true;
        prepared = false;
        frames = 0;
        openAt = now;
        VideoLog.log(I18n.t("Abrir") + " " + f.getName() + " · " + (f.length() / 1024) + " KB" + (tries > 0 ? " · " + I18n.t("reintento") + " " + tries : "") + (tries == 1 ? " · fd" : ""));
        // 2.35 r6: el primer reintento abre el archivo y le pasa el descriptor (FileDescriptor) en vez de la ruta: algunas
        // radios MediaTek leen distinto por la ruta (nombres con espacios, extensión) y por el archivo abierto
        final boolean byFd = tries == 1;
        try {
            if (byFd) { fdStream = new java.io.FileInputStream(f); m.setDataSource(fdStream.getFD()); }
            else m.setDataSource(f.getAbsolutePath());
            m.setSurface(surface);
            m.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { public void onPrepared(MediaPlayer p) {
                if (mp != m) return;
                prepared = true;
                openingUntil = android.os.SystemClock.uptimeMillis() + 1500;
                fit(m.getVideoWidth(), m.getVideoHeight());
                int start = resumeMs;
                if (resumeMs > 0) { m.seekTo(resumeMs); resumeMs = 0; }
                if (!paused) m.start();
                int d = duration(m);
                line.set(start, d);
                if (bar.getVisibility() == VISIBLE) line.setVisibility(line.dur > 0 ? VISIBLE : INVISIBLE);
                VideoLog.log(I18n.t("Preparado") + " " + m.getVideoWidth() + "×" + m.getVideoHeight() + " · " + VideoPicker.time(d)
                        + " · " + (android.os.SystemClock.uptimeMillis() - openAt) + " ms");
                // si en unos segundos no llega ningún cuadro (suena sin imagen), se reintenta o se avisa
                h.removeCallbacks(pictureCheck);
                h.postDelayed(pictureCheck, 4000);
            }});
            m.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() { public void onVideoSizeChanged(MediaPlayer p, int w, int hh) { if (mp == m) fit(w, hh); } });
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { public void onCompletion(MediaPlayer p) { if (mp == m) next(); } });
            m.setOnInfoListener(new MediaPlayer.OnInfoListener() { public boolean onInfo(MediaPlayer p, int what, int extra) {
                // 3 = primer cuadro (se anota aparte); el mismo aviso seguido se anota una vez
                String k = what + "/" + extra;
                if (mp == m && what != 3 && !k.equals(lastInfo)) { lastInfo = k; VideoLog.log("info " + k); }
                return false;
            }});
            m.setOnErrorListener(new MediaPlayer.OnErrorListener() { public boolean onError(MediaPlayer p, int what, int extra) {
                if (mp == m) failedOrRetry(f, "error " + what + "/" + extra);
                return true;
            }});
            m.prepareAsync();
        } catch (Throwable t) {
            failedOrRetry(f, t.getClass().getSimpleName());
        }
    }

    private String lastInfo;
    /** Archivo cuyo formato se está leyendo (no se lee dos veces al reintentar). */
    private File probing;

    /** Abre el video que esperaba a las miniaturas. */
    private final Runnable pendingOpen = new Runnable() { public void run() {
        File f = pending;
        if (f != null && active && current == f && mp == null && surface != null) open(f);
    }};

    /** Duración del reproductor ya listo (0 si no se sabe; las radios a veces dan cientos de horas). */
    private static int duration(MediaPlayer m) {
        try { int d = m.getDuration(); return d > 0 && d < 24 * 3600 * 1000 ? d : 0; } catch (Throwable t) { return 0; }
    }

    /** Llegó el primer cuadro del video de ahora. */
    private void firstFrame() {
        failStreak = 0;
        h.removeCallbacks(pictureCheck);
        if (current != null) {
            final File mark = badMark(current);
            new Thread(new Runnable() { public void run() { try { if (mark.exists()) mark.delete(); } catch (Throwable ignored) { } } }, "StradaVideoMark").start();
        }
        thumbTries = 0;
        h.removeCallbacks(grabThumb);
        h.postDelayed(grabThumb, 2500);
        if (empty.getVisibility() == VISIBLE && !list.isEmpty() && !scanning) empty.setVisibility(GONE);
        VideoLog.log(I18n.t("Primer cuadro") + " · " + (android.os.SystemClock.uptimeMillis() - openAt) + " ms");
    }

    private int thumbTries;

    /**
     * 2.35 r7: miniatura tomada del video que suena, si el selector no la tiene. En el Picanto (Android 4.2, un solo
     * decodificador) sacar un cuadro aparte no funciona con ningún video; así, los que se ven una vez ya quedan con
     * miniatura. Se toma a los 2,5 s (no el primer cuadro, que suele ser negro) y, si sale casi negra, se reintenta.
     */
    private final Runnable grabThumb = new Runnable() { public void run() {
        final File f = current;
        if (f == null || mp == null || !prepared || frames < 5 || tex == null || vw <= 0 || vh <= 0 || !active) return;
        if (new File(new File(getContext().getCacheDir(), "vthumbs"), VideoPicker.Thumbs.key(f) + ".jpg").exists()) return;
        Bitmap b = null;
        try {
            int w = 192, hh = Math.max(1, Math.round(192f * vh / vw));
            if (hh > 340) { hh = 340; w = Math.max(1, Math.round(340f * vw / vh)); }
            b = tex.getBitmap(w, hh);
        } catch (Throwable ignored) { }
        if (b == null) return;
        if (dark(b)) {
            b.recycle();
            if (++thumbTries < 4) h.postDelayed(this, 3000);
            return;
        }
        VideoPicker.Thumbs.fromPlayer(getContext(), f, b, line.dur);
    }};

    /** Casi negra (fundido de entrada o cuadro vacío). */
    private static boolean dark(Bitmap b) {
        long sum = 0;
        int n = 0, w = b.getWidth(), hh = b.getHeight();
        for (int y = 1; y < 6; y++) for (int x = 1; x < 6; x++) {
            int c = b.getPixel(x * w / 6, y * hh / 6);
            sum += ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
            n++;
        }
        return sum / (n * 3) < 10;
    }

    /** Listo y sonando, pero sin ningún cuadro: el decodificador de video no arrancó (el audio sí). */
    private final Runnable pictureCheck = new Runnable() { public void run() {
        if (mp == null || !prepared || frames > 0 || paused || !active || current == null) return;
        boolean playing;
        try { playing = mp.isPlaying(); } catch (Throwable t) { playing = false; }
        if (!playing) return;
        if (info != null && !info.video) return;      // solo audio (un .mp4 sin video): nada que mostrar
        final File f = current;
        // si la pantalla ya dijo que no puede con este video, no se reintenta (el audio sigue)
        if (tries < 2 && !knownBad()) { failedOrRetry(f, I18n.t("sin imagen")); return; }
        VideoLog.log(I18n.t("sin imagen") + " · " + f.getName());
        String d = detail();
        showProblem(I18n.t("La pantalla no puede mostrar la imagen de este video") + (d.length() > 0 ? "\n" + d : ""));
    }};

    /** Aviso en el centro del video (no se esconde con los controles) y la forma de verlo igual. */
    private void showProblem(String msg) {
        empty.setText(msg + "\n\n" + (knownBad() ? I18n.t("Conviértelo a H.264 de 720p o 1080p horizontal, a 30 cuadros por segundo") + "\n" : "")
                + I18n.t("Mantén presionado el botón de pantalla completa para abrirlo en VLC u otra app"));
        empty.setVisibility(VISIBLE);
    }

    /**
     * Si un video no abre (o suena sin imagen), se reintenta hasta dos veces con una superficie nueva: al cambiar de
     * video el decodificador del anterior a veces todavía no está libre (videos 4K/8K), y en radios con un solo
     * decodificador puede estar ocupado un momento. Solo si vuelve a fallar se avisa y se pasa al siguiente.
     */
    private void failedOrRetry(final File f, String why) {
        VideoLog.log("[!] " + why + " · " + f.getName() + (info != null && info.ok != null && !info.ok ? " · " + info.why : ""));
        final int pos = prepared ? position() : resumeMs;
        closePlayer();
        if (tries < 2 && !knownBad()) {
            tries++;
            h.postDelayed(new Runnable() { public void run() {
                if (!active || current != f || mp != null) return;
                resumeMs = pos;
                newTexture();         // el TextureView nuevo abre el video cuando tenga su superficie
                current = f;
            }}, tries == 1 ? 700 : 1500);
            return;
        }
        failed();
    }

    /** Formato que la radio no decodifica: se avisa y se pasa al siguiente (VLC u otra app quizá sí lo lean). */
    private void failed() {
        closePlayer();
        if (!active) return;
        failStreak++;
        if (current != null) {
            final File mark = badMark(current);
            new Thread(new Runnable() { public void run() {
                try { mark.getParentFile().mkdirs(); new java.io.FileOutputStream(mark).close(); } catch (Throwable ignored) { }
            }}, "StradaVideoMark").start();
        }
        String d = detail();
        title.setText(I18n.t("No se puede reproducir aquí") + ": " + name());
        showProblem(I18n.t("No se puede reproducir aquí") + ": " + name() + (d.length() > 0 ? "\n" + d : ""));
        if (failStreak >= list.size()) {
            // ninguno abre: no se sigue dando vueltas
            VideoLog.log(I18n.t("Ningún video de la lista se pudo abrir"));
            return;
        }
        final File f = current;
        h.postDelayed(new Runnable() { public void run() { if (current == f && mp == null && active) next(); } }, 3000);
    }

    private void closePlayer() {
        h.removeCallbacks(pictureCheck);
        h.removeCallbacks(grabThumb);
        prepared = false;
        playerBusy = false;
        if (mp != null) { try { mp.release(); } catch (Throwable ignored) { } mp = null; }
        if (fdStream != null) { try { fdStream.close(); } catch (Throwable ignored) { } fdStream = null; }
    }

    /** El selector está abierto en Android 4: el video soltó el decodificador para que se puedan hacer las miniaturas. */
    private boolean held;

    /**
     * 2.35 r6: en Android 4 (radios con un solo decodificador, como el Picanto), mientras el selector está abierto el
     * video se detiene y suelta el decodificador: con el video sonando, las miniaturas no salían. Queda el último
     * cuadro a la vista y, si se cierra sin elegir, sigue desde donde iba. Las miniaturas quedan guardadas.
     */
    void holdForPicker(boolean on) {
        if (on == held) return;
        held = on;
        if (on) {
            h.removeCallbacks(pendingOpen);
            if (mp != null) { resumeMs = position(); closePlayer(); }
        } else if (active && current != null && mp == null && surface != null) open(current);
    }

    /** El archivo abierto cuando el reproductor lee por descriptor (se cierra al soltarlo). */
    private java.io.FileInputStream fdStream;

    /** La pantalla dice que no puede con este video (ya se leyó su formato). */
    private boolean knownBad() { return info != null && info.ok != null && !info.ok; }

    /** El formato y, si se sabe, por qué no se puede: «1080×1920 · H.264 High 4.2\npide más de… (H.264 4.1)». */
    private String detail() {
        if (info == null) return "";
        String d = info.shortText();
        if (knownBad() && info.why.length() > 0) d += (d.length() > 0 ? "\n" : "") + info.why;
        return d;
    }

    private int position() {
        try { return mp != null && prepared ? mp.getCurrentPosition() : resumeMs; } catch (Throwable t) { return 0; }
    }

    /** Lee el formato del archivo en otro hilo (para los avisos, el diagnóstico y los videos HDR). */
    private void probe(final File f) {
        if (android.os.Build.VERSION.SDK_INT >= 17) setHdr(false);
        new Thread(new Runnable() { public void run() {
            final VideoLog.Info in = VideoLog.probeCached(f);
            VideoLog.log(f.getName() + " · " + (in.video ? in.longText() : I18n.t("sin video"))
                    + (in.ok == null ? "" : in.ok ? " · OK" : " · [!] " + in.why) + (in.hdr ? " · HDR" : ""));
            h.post(new Runnable() { public void run() {
                if (f != current) return;
                info = in;
                if (android.os.Build.VERSION.SDK_INT >= 17) setHdr(in.hdr);
            }});
        }}, "StradaVideoInfo").start();
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
        texMatrix = m;
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
        for (View bi : new View[]{blurImg, blurImg2}) {
            LayoutParams gl = (LayoutParams) bi.getLayoutParams();
            gl.width = l.width; gl.height = l.height; gl.gravity = Gravity.CENTER;
            bi.setLayoutParams(gl);
        }
    }

    private final Runnable hideControls = new Runnable() { public void run() { if (!paused) controls(false); } };

    /** Muestra u oculta el título y los botones; a la vista se esconden solos a los 4 s (salvo en pausa). */
    private void controls(boolean on) {
        bar.setVisibility(on ? VISIBLE : INVISIBLE);
        title.setVisibility(on ? VISIBLE : INVISIBLE);
        line.setVisibility(on && line.dur > 0 ? VISIBLE : INVISIBLE);
        h.removeCallbacks(hideControls);
        h.removeCallbacks(timeTick);
        if (on) { h.postDelayed(hideControls, 4000); timeTick.run(); }
    }

    /** Mientras se ven los controles, la línea de tiempo se pone al día dos veces por segundo. */
    private final Runnable timeTick = new Runnable() { public void run() {
        if (!active || bar.getVisibility() != VISIBLE) return;
        try {
            if (mp != null && prepared) {
                line.set(mp.getCurrentPosition(), duration(mp));
                line.setVisibility(line.dur > 0 ? VISIBLE : INVISIBLE);
            }
        } catch (Throwable ignored) { }
        h.postDelayed(this, 500);
    }};

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        if (vw > 0) post(new Runnable() { public void run() { fit(vw, vh); } });
    }

    /** Lo llama el host al cambiar de tamaño: el botón del modo activo pasa a ✕ (volver a la tarjeta). */
    void setMode(int m) {
        mode = m;
        bigIcon.inward = m == 1;
        bigIcon.invalidate();
        fullIco.set(m == 2 ? CtlIcon.CLOSE : CtlIcon.FULL);
        if (vw > 0) post(new Runnable() { public void run() { fit(vw, vh); } });
    }

    List<File> files() { return list; }
    /** Ruta del video de ahora (o el último que sonó). */
    String currentPath() { return at >= 0 && at < list.size() ? list.get(at).getAbsolutePath() : prefs.getString("video_last", ""); }

    /** 2.35: eligió un video en el selector: la lista pasa a ser su carpeta y arranca en ese video. */
    void playList(List<File> l, int i) {
        if (l == null || l.isEmpty()) return;
        list = new ArrayList<File>(l);
        loaded = true;
        empty.setVisibility(GONE);
        resumeMs = 0; paused = false; playIco.set(CtlIcon.PAUSE);
        failStreak = 0;
        held = false;
        i = Math.max(0, Math.min(i, list.size() - 1));
        if (active) play(i);
        else { at = i; current = list.get(i); prefs.edit().putString("video_last", current.getAbsolutePath()).putInt("video_pos", 0).apply(); }
    }
    int index() { return at; }
    /** Reproduce el video i de la lista (elegido en la lista de Main). */
    void playAt(int i) { if (!list.isEmpty() && active) { resumeMs = 0; paused = false; failStreak = 0; playIco.set(CtlIcon.PAUSE); play(i); } }

    /** Ganchos de prueba: controles a la vista y saltar a un momento (como soltar la línea de tiempo ahí). */
    void showControls() { controls(true); }
    void seekTest(int ms) { try { if (mp != null && prepared) mp.seekTo(ms); } catch (Throwable ignored) { } line.set(ms, line.dur); controls(true); }

    /** Revisa ya la velocidad y la tapa (sin duplicar la revisión de cada segundo). */
    private void refresh() { h.removeCallbacks(tick); if (active) tick.run(); }

    /** Confirmó el aviso de cuidado: el video se ve hasta que el carro se detenga. */
    void acceptMoving() { warnAccepted = true; declined = false; setCovered(false); }

    void next() { if (!list.isEmpty() && active) { resumeMs = 0; play(step(at, 1)); } }
    void prev() { if (!list.isEmpty()) { resumeMs = 0; play(step(at, -1)); } }

    /**
     * 2.35 r7: el siguiente (o anterior) que no se marcó como «no se pudo reproducir en esta pantalla»: en el Picanto
     * cada video que no abre costaba 3–5 s de aviso en cada vuelta. Si todos están marcados, el de al lado. Desde
     * «Elegir video» se puede volver a probar cualquiera.
     */
    private int step(int from, int dir) {
        int n = list.size();
        for (int k = 1; k < n; k++) {
            int i = ((from + dir * k) % n + n) % n;
            if (!badMark(list.get(i)).exists()) return i;
        }
        return from + dir;
    }

    /** Marca de «no se pudo reproducir en esta pantalla» (en la caché de las miniaturas; la lee también el selector). */
    private File badMark(File f) { return badMark(getContext(), f); }

    static File badMark(android.content.Context c, File f) {
        return new File(new File(c.getCacheDir(), "vthumbs"), VideoPicker.Thumbs.key(f) + ".x");
    }

    void toggle() {
        paused = !paused;
        try { if (mp != null && prepared) { if (paused) mp.pause(); else mp.start(); } } catch (Throwable ignored) { }
        playIco.set(paused ? CtlIcon.PLAY : CtlIcon.PAUSE);
    }

    /** Abrir en VLC (o la app de video que elija el usuario), desde donde iba: mantener presionado ⛶. */
    private void fullscreen() {
        if (at < 0 || at >= list.size()) return;
        File f = list.get(at);
        int pos = position();
        try { if (mp != null && prepared && mp.isPlaying()) mp.pause(); } catch (Throwable ignored) { }
        paused = true;
        playIco.set(CtlIcon.PLAY);
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
        try { if (mp != null && prepared && mp.isPlaying()) mp.pause(); } catch (Throwable ignored) { }
    }

    void onResume() { try { if (active && !paused && mp != null && prepared) mp.start(); } catch (Throwable ignored) { } }

    /** La tarjeta se reemplaza (cambio de tema o de ajustes): suelta el reproductor. */
    void release() {
        h.removeCallbacks(tick);
        h.removeCallbacks(blurTick);
        h.removeCallbacks(hideControls);
        h.removeCallbacks(timeTick);
        h.removeCallbacks(pendingOpen);
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

    /**
     * Línea de tiempo del video: a la izquierda lo que va, a la derecha lo que dura, y en medio la barra con su punto.
     * Tocar o arrastrar mueve el punto (muestra el tiempo al que va) y al soltar salta ahí. Mientras se arrastra, el
     * carrusel no se lleva el gesto ni se esconden los controles.
     */
    static class Timeline extends View {
        interface L { void seek(int ms); void touching(); }
        int pos, dur;
        private int drag = -1;
        private long seekAt;
        private final float s;
        private final L l;
        private final RectF rect = new RectF();
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
                knob = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);

        Timeline(Context c, float s, L l) {
            super(c);
            this.s = s; this.l = l;
            track.setColor(0x55FFFFFF);
            knob.setColor(Color.WHITE);
            knob.setShadowLayer(3 * s, 0, 0, 0x99000000);
            txt.setColor(Color.WHITE);
            txt.setTextSize(12 * s);
            txt.setTypeface(Typeface.DEFAULT_BOLD);
            txt.setShadowLayer(3 * s, 0, 0, Color.BLACK);
            if (android.os.Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);   // sombras en Android 4–8
        }

        /** Posición y duración del reproductor (mientras arrastra, o justo después de saltar, la posición no se pisa). */
        void set(int p, int d) {
            dur = d;
            if (drag < 0 && android.os.SystemClock.uptimeMillis() - seekAt > 900) pos = p;
            invalidate();
        }

        private float labelW() { return txt.measureText(dur >= 3600000 ? "0:00:00" : "00:00") + 10 * s; }

        @Override protected void onDraw(Canvas c) {
            if (dur <= 0) return;
            float w = getWidth(), hh = getHeight(), lw = labelW(), cy = hh / 2f;
            float x0 = lw, x1 = w - lw, th = 4 * s;
            int p = drag >= 0 ? drag : Math.min(pos, dur);
            float x = x0 + (x1 - x0) * (p / (float) dur);
            fill.setColor(Main.RED);
            RectF r = rect;
            r.set(x0, cy - th / 2, x1, cy + th / 2);
            c.drawRoundRect(r, th, th, track);
            r.right = x;
            c.drawRoundRect(r, th, th, fill);
            c.drawCircle(x, cy, (drag >= 0 ? 9 : 7) * s, knob);
            float ty = cy - (txt.descent() + txt.ascent()) / 2;
            txt.setTextAlign(Paint.Align.LEFT);
            c.drawText(VideoPicker.time(p), 0, ty, txt);
            txt.setTextAlign(Paint.Align.RIGHT);
            c.drawText(VideoPicker.time(dur), w, ty, txt);
        }

        private int at(float x) {
            float lw = labelW(), x0 = lw, x1 = getWidth() - lw;
            float f = (x - x0) / Math.max(1, x1 - x0);
            return Math.round(Math.max(0, Math.min(1, f)) * dur);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (dur <= 0) return false;
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    drag = at(e.getX()); l.touching(); invalidate(); return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    drag = at(e.getX()); l.touching(); invalidate(); return true;
                case android.view.MotionEvent.ACTION_UP:
                    int to = at(e.getX());
                    drag = -1; pos = to; seekAt = android.os.SystemClock.uptimeMillis(); invalidate();
                    l.seek(to);
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    drag = -1; invalidate(); l.seek(pos); return true;
            }
            return true;
        }
    }

    // ───────────────────────── videos HDR ─────────────────────────

    private Boolean hdrOn;

    private void setHdr(boolean on) {
        if (android.os.Build.VERSION.SDK_INT < 17 || (hdrOn != null && hdrOn == on)) return;
        hdrOn = on;
        try {
            if (!on) { tex.setLayerPaint(null); return; }
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(1.35f);
            float k = 1.15f, t = (-0.5f * k + 0.5f) * 255f;
            cm.postConcat(new android.graphics.ColorMatrix(new float[]{k, 0, 0, 0, t, 0, k, 0, 0, t, 0, 0, k, 0, t, 0, 0, 0, 1, 0}));
            Paint p = new Paint();
            p.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            tex.setLayerPaint(p);
        } catch (Throwable ignored) { }
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
     *
     * Android 12+: en vez de copias, el video mismo se difumina en vivo (fluido) y la tapa queda transparente con el
     * oscurecido encima; el difuminado es tan fuerte que no se distingue nada.
     */
    private void setCovered(boolean on) {
        boolean was = cover.getVisibility() == VISIBLE;
        cover.setVisibility(on ? VISIBLE : GONE);
        edge.setVisibility(on ? INVISIBLE : VISIBLE);
        h.removeCallbacks(blurTick);
        if (LIVE_BLUR && !liveBlurFailed) {
            if (on && !liveBlurOn) { try { VideoBlur31.on(videoBox, 34 * s); liveBlurOn = true; } catch (Throwable t) { liveBlurFailed = true; } }
            else if (!on && liveBlurOn) { try { VideoBlur31.off(videoBox); } catch (Throwable ignored) { } liveBlurOn = false; }
            if (!liveBlurFailed) {
                cover.setBackgroundColor(on ? Color.TRANSPARENT : 0xFF0B0C0E);
                videoBox.setAlpha(1f);
                return;
            }
            // si el sistema no lo deja, se usa lo de Android 4–11 (sin mostrar el video)
            cover.setBackgroundColor(0xFF0B0C0E);
        }
        videoBox.setAlpha(on ? 0.02f : 1f);
        if (on) {
            if (!was) { blurImg.animate().cancel(); blurImg2.animate().cancel(); blurImg.setAlpha(1f); blurImg2.setAlpha(0f); blurFront = false; blurTick.run(); }
            else h.postDelayed(blurTick, blurMs);
        }
    }

    /**
     * El video difuminado de la tapa (Android 4–11): una copia chiquita del cuadro de ahora (32 px de ancho), agrandada
     * al doble y pasada dos veces por un difuminado de caja; la ImageView la estira al tamaño del video. Unas 8 veces
     * por segundo, con un fundido entre una y otra para que se vea fluido. Si en la pantalla tarda mucho (radios
     * lentas), copia menos seguido (hasta 3 por segundo); si va sobrado, hasta 12.
     */
    private final Runnable blurTick = new Runnable() { public void run() {
        if (!active || cover.getVisibility() != VISIBLE) return;
        long t0 = android.os.SystemClock.uptimeMillis();
        try {
            if (vw > 0 && vh > 0 && mp != null && tex.isAvailable()) {
                int w0 = 32, h0 = Math.max(4, Math.round(32f * vh / vw));
                Bitmap small = tex.getBitmap(w0, h0);
                if (small != null) {
                    Bitmap big = Bitmap.createScaledBitmap(small, w0 * 2, h0 * 2, true);
                    if (big != small) small.recycle();
                    boxBlur(big, 3); boxBlur(big, 3);
                    // fundido del cuadro anterior al nuevo, sin cambiar el orden (el oscurecido y el texto siguen
                    // encima): si se ve la de arriba, el nuevo va abajo y la de arriba se desvanece; si no, el nuevo va
                    // arriba y aparece
                    if (blurFront) {
                        blurImg.setImageBitmap(big);
                        blurImg2.animate().alpha(0f).setDuration(blurMs).start();
                    } else {
                        blurImg2.setImageBitmap(big);
                        blurImg2.animate().cancel();
                        blurImg2.setAlpha(0f);
                        blurImg2.animate().alpha(1f).setDuration(blurMs).start();
                    }
                    blurFront = !blurFront;
                }
            }
        } catch (Throwable ignored) { }
        long took = android.os.SystemClock.uptimeMillis() - t0;
        if (took > 30) blurMs = Math.min(330, blurMs + 40);
        else if (took < 12) blurMs = Math.max(85, blurMs - 10);
        h.postDelayed(this, blurMs);
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
