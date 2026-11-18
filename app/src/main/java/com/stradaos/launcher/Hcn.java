package com.stradaos.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Pantallas HCN / Hengchen (issue #5: Android 8.1, chip AC8227L). El reproductor de fábrica (com.hcn.AutoMediaPlayer)
 * no pone la canción en su MediaSession, pero el sistema trae un servicio propio, «sourceinfo» (clases
 * android.sourceservice.*), que dice qué fuente suena y, para archivos locales, la ruta del archivo, el estado
 * («start», «paused», «stopped»), la duración y el tiempo. El launcher de fábrica lee las etiquetas del archivo
 * (título, artista, carátula) y StradaOS hace lo mismo.
 *
 * Todo va por reflexión: en cualquier otra pantalla la clase no existe, start() devuelve false y no hace nada más.
 * Solo se lee; nunca se le manda nada al servicio (ni se pide audio ni se tocan los efectos).
 */
class Hcn {
    static final String PLAYER = "com.hcn.AutoMediaPlayer";

    // ── para el diagnóstico ──
    static String status = "no se probó";
    static String lastInfo = "ninguna";
    static String sourcePkg = "";
    static String tagStatus = "—";
    static long lastAt;
    static int events;
    static boolean present;      // la pantalla tiene el servicio
    private static Hcn instance;

    private final Context ctx;
    private final NowPlaying.Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Object info;                       // android.sourceservice.SourceInfo
    private Method getMusicInfo, getSourcePackage, setListener;
    private boolean running;

    // lo último que se mandó a la pantalla
    private String curFile, curState;
    private long rawPos = -1, rawAt;           // último tiempo que dio el servicio y cuándo cambió
    private long sentPos = -1, sentAt;
    private boolean sentPlaying, shown;
    private String tagFile, tTitle, tArtist, tAlbum;
    private Bitmap tArt;
    private int loadSeq;
    private String labelPkg, labelText;

    Hcn(Context c, NowPlaying.Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
        instance = this;
    }

    /** Se llama en el hilo principal (SourceInfo arma su Handler con el hilo que lo crea). */
    boolean start() {
        Class<?> c;
        try {
            c = Class.forName("android.sourceservice.SourceInfo");
        } catch (Throwable e) {
            status = "no aplica (esta pantalla no es HCN)";
            return false;
        }
        present = true;
        try {
            info = c.getMethod("getInstance").invoke(null);
            getMusicInfo = c.getMethod("getMusicInfo");
        } catch (InvocationTargetException e) {
            status = "el servicio sourceinfo no respondió: " + err(e.getCause());
            return false;
        } catch (Throwable e) {
            status = "no se pudo abrir SourceInfo: " + err(e);
            return false;
        }
        try { getSourcePackage = c.getMethod("getSourcePackage"); } catch (Throwable ignored) { }
        running = true;
        status = "conectado, se consulta cada 2 s (sin avisos)";
        try {
            Class<?> l = Class.forName("android.sourceservice.SourceInfo$OnEventListener");
            setListener = c.getMethod("setOnEventListener", l);
            Object proxy = Proxy.newProxyInstance(Hcn.class.getClassLoader(), new Class<?>[]{l}, new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) {
                    String n = m.getName();
                    if ("onMusicEvent".equals(n)) {
                        events++;
                        final Object mi = a != null && a.length > 0 ? a[0] : null;
                        ui.post(new Runnable() { public void run() { if (running) process(mi, true); } });
                    } else if ("onSourceChanged".equals(n)) {
                        events++;
                        ui.post(poll);
                    } else if ("hashCode".equals(n)) {
                        return System.identityHashCode(p);
                    } else if ("equals".equals(n)) {
                        return a != null && a.length > 0 && p == a[0];
                    } else if ("toString".equals(n)) {
                        return "StradaOS";
                    }
                    return null;
                }
            });
            setListener.invoke(info, proxy);
            status = "conectado, con avisos del servicio";
        } catch (Throwable e) {
            setListener = null;
            status = "conectado, sin avisos (" + err(e) + "): se consulta cada 2 s";
        }
        ui.post(poll);
        return true;
    }

    void stop() {
        running = false;
        ui.removeCallbacks(poll);
        if (setListener != null) try { setListener.invoke(info, (Object) null); } catch (Throwable ignored) { }
    }

    /** Hay una canción local de la pantalla sonando: lo que mande el Bluetooth (celular en pausa) no la reemplaza. */
    static boolean blocksBt() {
        Hcn h = instance;
        return h != null && h.shown && "start".equalsIgnoreCase(h.curState);
    }

    private final Runnable poll = new Runnable() {
        public void run() {
            ui.removeCallbacks(poll);
            if (!running) return;
            try {
                process(getMusicInfo.invoke(info), false);
            } catch (Throwable e) {
                lastInfo = "error al leer: " + err(e instanceof InvocationTargetException ? e.getCause() : e);
            }
            ui.postDelayed(poll, shown || PLAYER.equals(sourcePkg) ? 2000 : 5000);
        }
    };

    /** Lee la información del servicio (MusicInfo: className, fileName, state, duration, position). */
    private void process(Object mi, boolean event) {
        long now = SystemClock.elapsedRealtime();
        lastAt = now;
        try { if (getSourcePackage != null) sourcePkg = String.valueOf(getSourcePackage.invoke(info)); } catch (Throwable ignored) { }
        if (mi == null) { lastInfo = "vacía"; hide(); return; }
        String cls = sf(mi, "className"), file = sf(mi, "fileName"), state = sf(mi, "state");
        long dur = nf(mi, "duration"), pos = nf(mi, "position");
        lastInfo = cls + " · " + (file == null || file.length() == 0 ? "(sin archivo)" : file) + " · " + state
                + " · " + (pos / 1000) + "/" + (dur / 1000) + " s" + (event ? " (aviso)" : "");
        if (file == null || file.trim().length() == 0) { hide(); return; }
        boolean playing = "start".equalsIgnoreCase(state);
        boolean newFile = !file.equals(curFile);
        boolean newState = state == null ? curState != null : !state.equals(curState);
        // ¿El tiempo que da el servicio está vivo? Si no cambió desde la lectura anterior, es el de la última orden
        // (empezar, pausa, adelantar) y no sirve para corregir: StradaOS sigue contando el suyo.
        boolean posFresh = event || newFile || (pos >= 0 && pos != rawPos);
        if (pos != rawPos) { rawPos = pos; rawAt = now; }
        curState = state;
        if (newFile) {
            curFile = file;
            loadTags(file, dur);
            return;
        }
        if (!file.equals(tagFile)) return;   // aún leyendo las etiquetas
        long expected = sentPos < 0 ? -1 : sentPos + (sentPlaying ? now - sentAt : 0);
        boolean drift = posFresh && pos >= 0 && (expected < 0 || Math.abs(expected - pos) > 2500);
        if (!newState && !drift) return;
        send(playing, dur, posFresh ? pos : -1, false);
    }

    private void send(boolean playing, long dur, long pos, boolean withTags) {
        long now = SystemClock.elapsedRealtime();
        NowPlaying.Track t = new NowPlaying.Track();
        t.origin = "hcn";
        t.source = label();
        t.title = tTitle;
        t.artist = tArtist != null ? tArtist : withTags ? "" : null;   // archivo sin artista: no queda el del anterior
        t.album = tAlbum;
        if (withTags) { t.art = tArt; t.artSet = true; }
        t.playing = playing;
        if (dur > 0) t.durationMs = dur;
        if (pos >= 0) { t.positionMs = pos; sentPos = pos; }
        else if (sentPos >= 0 && sentPlaying) sentPos += now - sentAt;
        sentAt = now;
        sentPlaying = playing;
        shown = true;
        listener.onTrack(t);
    }

    private void hide() {
        curFile = curState = null;
        rawPos = sentPos = -1;
        if (!shown) return;
        shown = false;
        NowPlaying.Track t = new NowPlaying.Track();
        t.origin = "hcn";
        t.clear = true;
        t.artSet = true;
        listener.onTrack(t);
    }

    /** Título, artista, álbum y carátula del archivo (en otro hilo: puede tardar). Sin etiquetas, el nombre del archivo. */
    private void loadTags(final String file, final long dur) {
        final int seq = ++loadSeq;
        new Thread(new Runnable() {
            public void run() {
                String ti = null, ar = null, al = null, st;
                Bitmap art = null;
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(file);
                    ti = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                    ar = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
                    if (ar == null) ar = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
                    al = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
                    byte[] pic = r.getEmbeddedPicture();
                    if (pic != null) art = decode(pic, 480);
                    st = ti != null ? "leídas" + (art != null ? ", con carátula" : ", sin carátula") : "el archivo no trae título: se usa su nombre";
                } catch (Throwable e) {
                    st = "no se pudieron leer (" + err(e) + "): se usa el nombre del archivo";
                } finally {
                    try { r.release(); } catch (Throwable ignored) { }
                }
                if (ti == null) ti = fromFileName(file);
                final String fTi = ti, fAr = ar, fAl = al, fSt = st;
                final Bitmap fArt = art;
                ui.post(new Runnable() {
                    public void run() {
                        if (seq != loadSeq || !file.equals(curFile)) return;
                        tagFile = file;
                        tTitle = fTi; tArtist = fAr; tAlbum = fAl; tArt = fArt;
                        tagStatus = fSt;
                        boolean playing = "start".equalsIgnoreCase(curState);
                        long pos = rawPos;
                        if (pos >= 0 && playing) pos += SystemClock.elapsedRealtime() - rawAt;
                        send(playing, dur, pos, true);
                    }
                });
            }
        }, "StradaHcn").start();
    }

    private String label() {
        String pkg = sourcePkg != null && sourcePkg.length() > 0 ? sourcePkg : PLAYER;
        if (pkg.equals(labelPkg)) return labelText;
        String l;
        try {
            PackageManager pm = ctx.getPackageManager();
            l = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Throwable e) {
            l = I18n.t("Música");
        }
        labelPkg = pkg;
        labelText = l;
        return l;
    }

    // ── pruebas (solo StradaOS Prueba): «hcn-sim:ruta,estado,posición_s,duración_s» ──
    static class Fake {
        public String className = "com.hcn.MediaActivity.MusicPlayerUiActivity", fileName, state;
        public int duration, position;
    }

    static void simulate(String file, String state, int posS, int durS) {
        Hcn h = instance;
        if (h == null) return;
        Fake f = new Fake();
        f.fileName = file;
        f.state = state;
        f.position = posS * 1000;
        f.duration = durS * 1000;
        if (!h.running) status = "simulado (gancho de prueba)";
        sourcePkg = file == null || file.length() == 0 ? "" : PLAYER;
        h.process(f, true);
    }

    static String report() {
        StringBuilder b = new StringBuilder("Pantalla HCN (servicio sourceinfo): ").append(status);
        if (!present && instance != null && !instance.shown) return b.toString();
        b.append("\n  fuente del equipo: ").append(sourcePkg == null || sourcePkg.length() == 0 ? "—" : sourcePkg);
        b.append("\n  última lectura: ").append(lastInfo);
        if (lastAt > 0) b.append(", hace ").append((SystemClock.elapsedRealtime() - lastAt) / 1000).append(" s");
        b.append("\n  avisos recibidos: ").append(events);
        b.append("\n  etiquetas del archivo: ").append(tagStatus);
        return b.toString();
    }

    // ── utilidades ──
    private static String sf(Object o, String name) {
        try {
            Field f = o.getClass().getField(name);
            Object v = f.get(o);
            return v == null ? null : String.valueOf(v);
        } catch (Throwable e) {
            return null;
        }
    }

    private static long nf(Object o, String name) {
        try {
            Object v = o.getClass().getField(name).get(o);
            return v instanceof Number ? ((Number) v).longValue() : -1;
        } catch (Throwable e) {
            return -1;
        }
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() == 0 || "<unknown>".equalsIgnoreCase(s) || "unknown".equalsIgnoreCase(s)) return null;
        return s;
    }

    static String fromFileName(String path) {
        String n = path.substring(path.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replace('_', ' ').replaceAll("\\s+", " ").trim();
        return n.length() == 0 ? path : n;
    }

    private static Bitmap decode(byte[] b, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= max && o.outHeight / (s * 2) >= max) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        return BitmapFactory.decodeByteArray(b, 0, b.length, o2);
    }

    private static String err(Throwable e) {
        if (e == null) return "?";
        String m = e.getMessage();
        return e.getClass().getSimpleName() + (m != null ? ": " + m : "");
    }
}
