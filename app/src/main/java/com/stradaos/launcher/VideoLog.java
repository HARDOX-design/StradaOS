package com.stradaos.launcher;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Locale;

/**
 * 2.35 r5: diagnóstico del video de la tarjeta del centro. Por cada video: el formato (códec, tamaño, perfil y nivel de
 * H.264), si la pantalla dice que lo puede decodificar, y qué pasó al abrirlo (errores, reintentos, primer cuadro). Lo
 * último queda en memoria para «Diagnóstico del video» (Ajustes → Mapa y clima) y todo se guarda en
 * StradaDiag/video.txt, para pedírselo a quien tenga un problema con un video.
 *
 * Todo con APIs de Android 4.1 (MediaExtractor, MediaCodecList); el tamaño máximo de cada decodificador solo se sabe
 * en Android 5+ ({@link Modern#videoMax}).
 */
final class VideoLog {
    private VideoLog() { }

    private static final LinkedList<String> LINES = new LinkedList<String>();
    private static final int KEEP = 60;

    /** Lo que se sabe de un archivo de video (lo arma {@link #probe} en otro hilo). */
    static final class Info {
        String mime = "", codec = "", profile = "";
        int w, h;
        boolean hdr, video;
        /** null = no se sabe; false = la pantalla dice que no lo puede decodificar. */
        Boolean ok;
        String why = "";
        /** Lo máximo que declara la pantalla para ese códec, p. ej. «H.264 hasta 4.1» (para el aviso). */
        String limit = "";
        /** Pista de audio («mp4a-latm», «sin audio») y cuadros por segundo, para el diagnóstico. */
        String audio = "", fps = "";
        /** La pista de video solo se vio abriendo el archivo (no por la ruta). */
        boolean fd;

        /** «1080×1920 · H.264 High 4.0», para los avisos. */
        String shortText() {
            StringBuilder b = new StringBuilder();
            if (w > 0 && h > 0) b.append(w).append('×').append(h);
            if (codec.length() > 0) { if (b.length() > 0) b.append(" · "); b.append(codec); }
            if (profile.length() > 0) b.append(' ').append(profile);
            return b.toString();
        }

        /** Para el diagnóstico: lo de {@link #shortText} y además fps, audio y si se leyó por el archivo abierto. */
        String longText() {
            StringBuilder b = new StringBuilder(shortText());
            if (fps.length() > 0) b.append(" · ").append(fps).append(" fps");
            if (audio.length() > 0) b.append(" · audio ").append(audio);
            if (fd) b.append(" · fd");
            return b.toString();
        }
    }

    static synchronized void log(String s) {
        String line = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date()) + "  " + s;
        LINES.add(line);
        while (LINES.size() > KEEP) LINES.removeFirst();
        android.util.Log.i("StradaVideo", s);
        final String l = line;
        try { IO.execute(new Runnable() { public void run() { save(l); } }); } catch (Throwable ignored) { }
    }

    /** El archivo se escribe en otro hilo (en las radios la memoria es lenta). */
    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor();

    /** Lo último, para la ventana del diagnóstico. */
    static synchronized String text() {
        StringBuilder b = new StringBuilder();
        for (String l : LINES) b.append(l).append('\n');
        return b.toString();
    }

    /** Agrega al archivo de StradaDiag (si pasa de 256 KB, empieza de nuevo y guarda el anterior como video-1.txt). */
    private static boolean wroteDecoders;

    private static void save(String line) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "StradaDiag");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File f = new File(dir, "video.txt");
            boolean head = !f.exists();
            if (f.length() > 256 * 1024) { File old = new File(dir, "video-1.txt"); old.delete(); f.renameTo(old); head = true; }
            FileOutputStream o = new FileOutputStream(f, true);
            try {
                if (head) o.write((device() + "\n" + (decs != null ? decoders() : "") + "\n").getBytes("UTF-8"));
                else if (!wroteDecoders && decs != null) o.write((decoders() + "\n").getBytes("UTF-8"));
                wroteDecoders = wroteDecoders || decs != null;
                o.write((new java.text.SimpleDateFormat("yyyy-MM-dd ", Locale.US).format(new java.util.Date()) + line + "\n").getBytes("UTF-8"));
            } finally { o.close(); }
        } catch (Throwable ignored) { }
    }

    static String device() {
        return Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.HARDWARE + ") · Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")";
    }

    /** Un decodificador de video y lo que declara (se lee una sola vez: en Android 4.x leerlo abre el decodificador). */
    private static final class Dec {
        String name, mime;
        int[][] pl;          // {perfil, nivel}
        Object caps;         // CodecCapabilities (Android 5+: tamaño máximo)
    }

    private static volatile java.util.List<Dec> decs;

    /**
     * Lee los decodificadores de video. En Android 4.x, preguntar lo que soporta un decodificador lo abre por un momento:
     * con un video sonando, en radios con un solo decodificador eso podría trabarlo. Por eso se lee una vez, antes del
     * primer video (VideoCard.load) y nunca con el reproductor abierto en Android 4.x.
     */
    /** Candado propio (no el de la clase): leer los decodificadores tarda y no debe frenar a {@link #log}. */
    private static final Object DEC = new Object();

    static boolean ensureDecoders() {
        if (decs != null) return true;
        synchronized (DEC) { return ensureDecodersLocked(); }
    }

    private static boolean ensureDecodersLocked() {
        if (decs != null) return true;
        if (Build.VERSION.SDK_INT < 21 && VideoCard.playerBusy) return false;
        VideoCard.extracting.incrementAndGet();
        try { return readDecoders(); } finally { VideoCard.extracting.decrementAndGet(); }
    }

    private static boolean readDecoders() {
        java.util.List<Dec> out = new java.util.ArrayList<Dec>();
        try {
            int n = MediaCodecList.getCodecCount();
            for (int i = 0; i < n; i++) {
                MediaCodecInfo ci = MediaCodecList.getCodecInfoAt(i);
                if (ci.isEncoder()) continue;
                for (String type : ci.getSupportedTypes()) {
                    if (!type.toLowerCase(Locale.US).startsWith("video/")) continue;
                    Dec d = new Dec();
                    d.name = ci.getName();
                    d.mime = type;
                    d.pl = new int[0][];
                    try {
                        MediaCodecInfo.CodecCapabilities cc = ci.getCapabilitiesForType(type);
                        d.caps = cc;
                        if (cc.profileLevels != null) {
                            d.pl = new int[cc.profileLevels.length][];
                            for (int k = 0; k < d.pl.length; k++) d.pl[k] = new int[]{cc.profileLevels[k].profile, cc.profileLevels[k].level};
                        }
                    } catch (Throwable ignored) { }
                    out.add(d);
                }
            }
        } catch (Throwable ignored) { }
        decs = out;
        return true;
    }

    /** Los decodificadores de video de la pantalla, con lo que declaran (H.264: nivel máximo por perfil). */
    static String decoders() {
        if (!ensureDecoders()) return "(" + I18n.t("se leen con el video detenido") + ")\n";
        java.util.List<Dec> decs = VideoLog.decs;
        StringBuilder b = new StringBuilder();
        for (Dec d : decs) {
            b.append(d.name).append(": ").append(codecName(d.mime));
            if ("video/avc".equalsIgnoreCase(d.mime)) b.append(' ').append(avcLevels(d.pl));
            if (Build.VERSION.SDK_INT >= 21 && d.caps != null) { try { b.append(' ').append(Modern.videoMax((MediaCodecInfo.CodecCapabilities) d.caps)); } catch (Throwable ignored) { } }
            b.append('\n');
        }
        if (decs.isEmpty()) b.append("?\n");
        return b.toString();
    }

    /** «(Baseline 5.1, Main 4.1, High 4.1)»: el nivel más alto que declara para cada perfil. */
    private static String avcLevels(int[][] pls) {
        int[] profs = {1, 2, 8, 16};
        String[] names = {"Baseline", "Main", "High", "High10"};
        StringBuilder b = new StringBuilder("(");
        for (int k = 0; k < profs.length; k++) {
            int max = 0;
            for (int[] pl : pls) if (pl[0] == profs[k]) max = Math.max(max, pl[1]);
            if (max == 0) continue;
            if (b.length() > 1) b.append(", ");
            b.append(names[k]).append(' ').append(levelName(max));
        }
        return b.append(')').toString();
    }

    /** Nivel de MediaCodecInfo (bit) → «4.1». */
    private static String levelName(int bit) {
        int[] bits = {1, 2, 4, 8, 0x10, 0x20, 0x40, 0x80, 0x100, 0x200, 0x400, 0x800, 0x1000, 0x2000, 0x4000, 0x8000, 0x10000};
        String[] n = {"1", "1b", "1.1", "1.2", "1.3", "2", "2.1", "2.2", "3", "3.1", "3.2", "4", "4.1", "4.2", "5", "5.1", "5.2"};
        for (int i = bits.length - 1; i >= 0; i--) if (bit >= bits[i]) return n[i];
        return "?";
    }

    /** level_idc del SPS (31 = 3.1) → bit de MediaCodecInfo. */
    private static int levelBit(int idc) {
        int[] idcs = {10, 11, 12, 13, 20, 21, 22, 30, 31, 32, 40, 41, 42, 50, 51, 52};
        int[] bits = {1, 4, 8, 0x10, 0x20, 0x40, 0x80, 0x100, 0x200, 0x400, 0x800, 0x1000, 0x2000, 0x4000, 0x8000, 0x10000};
        for (int i = idcs.length - 1; i >= 0; i--) if (idc >= idcs[i]) return bits[i];
        return 1;
    }

    static String codecName(String mime) {
        String m = mime.toLowerCase(Locale.US);
        if (m.equals("video/avc")) return "H.264";
        if (m.equals("video/hevc")) return "H.265";
        if (m.contains("dolby-vision")) return "Dolby Vision";
        if (m.equals("video/mp4v-es")) return "MPEG-4";
        if (m.equals("video/3gpp")) return "H.263";
        if (m.contains("vp8")) return "VP8";
        if (m.contains("vp9")) return "VP9";
        if (m.contains("av01")) return "AV1";
        if (m.contains("mpeg2")) return "MPEG-2";
        return mime;
    }

    /**
     * Lee el archivo (sin decodificar nada): códec, tamaño, perfil/nivel de H.264 (del SPS en «csd-0»), si es HDR y si
     * algún decodificador de la pantalla dice que puede con él. Tarda unos milisegundos; va en otro hilo.
     */
    static Info probe(File f) {
        Info in = probeWith(f, false);
        // 2.35 r6: en el Picanto el lector de Android no veía la pista de video de un .mov por la ruta; por el archivo
        // abierto (FileDescriptor) a veces sí
        if (!in.video) {
            Info b = probeWith(f, true);
            if (b.video) { b.fd = true; return b; }
        }
        return in;
    }

    private static Info probeWith(File f, boolean fd) {
        Info in = new Info();
        MediaExtractor ex = new MediaExtractor();
        java.io.FileInputStream is = null;
        try {
            if (fd) { is = new java.io.FileInputStream(f); ex.setDataSource(is.getFD()); }
            else ex.setDataSource(f.getAbsolutePath());
            int videoTrack = -1;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat fm = ex.getTrackFormat(i);
                String mime = fm.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/") && in.audio.length() == 0) in.audio = mime.substring(6);
                if (mime != null && mime.startsWith("video/") && videoTrack < 0) videoTrack = i;
            }
            if (in.audio.length() == 0) in.audio = "-";
            if (videoTrack >= 0) {
                MediaFormat fm = ex.getTrackFormat(videoTrack);
                String mime = fm.getString(MediaFormat.KEY_MIME);
                in.video = true;
                in.mime = mime;
                in.codec = codecName(mime);
                try { in.w = fm.getInteger(MediaFormat.KEY_WIDTH); in.h = fm.getInteger(MediaFormat.KEY_HEIGHT); } catch (Throwable ignored) { }
                try { if (fm.containsKey("frame-rate")) in.fps = String.valueOf(fm.getInteger("frame-rate")); } catch (Throwable ignored) { }
                if (mime.contains("dolby-vision")) in.hdr = true;
                try { if (fm.containsKey("color-transfer")) { int t = fm.getInteger("color-transfer"); if (t == 6 || t == 7) in.hdr = true; } } catch (Throwable ignored) { }
                int prof = -1, lvl = -1;
                if ("video/avc".equalsIgnoreCase(mime)) {
                    try {
                        ByteBuffer sps = fm.getByteBuffer("csd-0");
                        if (sps != null) {
                            byte[] b = new byte[Math.min(sps.remaining(), 64)];
                            sps.duplicate().get(b);
                            for (int k = 0; k + 3 < b.length; k++) if ((b[k] & 0x1F) == 7 && (k == 0 || b[k - 1] == 1)) {
                                prof = b[k + 1] & 0xFF; lvl = b[k + 3] & 0xFF; break;
                            }
                        }
                    } catch (Throwable ignored) { }
                    if (prof > 0) in.profile = avcProfile(prof) + " " + (lvl / 10) + "." + (lvl % 10);
                }
                check(in, prof, lvl);
            }
        } catch (Throwable e) {
            in.why = "MediaExtractor: " + e.getClass().getSimpleName();
        } finally {
            try { ex.release(); } catch (Throwable ignored) { }
            try { if (is != null) is.close(); } catch (Throwable ignored) { }
        }
        return in;
    }

    private static String avcProfile(int idc) {
        switch (idc) {
            case 66: return "Baseline";
            case 77: return "Main";
            case 88: return "Extended";
            case 100: return "High";
            case 110: return "High10";
            case 122: return "High422";
            case 244: return "High444";
            default: return "perfil " + idc;
        }
    }

    /** ¿Algún decodificador declara que puede con este video? (las radios a veces declaran de menos: es una pista) */
    private static void check(Info in, int profIdc, int lvlIdc) {
        java.util.List<Dec> decs = VideoLog.decs;
        if (decs == null) return;     // se leen antes del primer video (VideoCard.load), nunca mientras abre uno
        int want = profIdc == 66 ? 1 : profIdc == 77 ? 2 : profIdc == 100 ? 8 : profIdc == 110 ? 16 : 0;
        // 2.35 r6: los decodificadores que no declaran niveles (en el Picanto, OMX.mtkwfd…avc: el de duplicar la
        // pantalla por Wi-Fi) no cuentan si otro del mismo códec sí declara; si no, el aviso diría «OK» de más
        boolean anyDeclared = false;
        for (Dec d : decs) if (d.mime.equalsIgnoreCase(in.mime) && d.pl.length > 0) anyDeclared = true;
        boolean any = false, fits = false;
        int maxLevel = 0;
        for (Dec d : decs) {
            if (!d.mime.equalsIgnoreCase(in.mime)) continue;
            if (anyDeclared && d.pl.length == 0) continue;
            any = true;
            boolean ok = true;
            if (want > 0 && lvlIdc > 0 && d.pl.length > 0) {
                ok = false;
                int lb = levelBit(lvlIdc);
                for (int[] pl : d.pl) {
                    if (pl[0] >= want) maxLevel = Math.max(maxLevel, pl[1]);
                    if (pl[0] >= want && pl[1] >= lb) ok = true;   // un perfil mayor cubre los menores
                }
            }
            if (ok && Build.VERSION.SDK_INT >= 21 && d.caps != null) {
                try { ok = Modern.videoSizeOk((MediaCodecInfo.CodecCapabilities) d.caps, in.w, in.h); } catch (Throwable ignored) { }
            }
            if (ok) fits = true;
        }
        in.ok = fits;
        if (maxLevel > 0) in.limit = in.codec + " " + levelName(maxLevel);
        if (!any) in.why = I18n.t("la pantalla no tiene decodificador") + " " + in.codec;
        else if (!fits) in.why = I18n.t("pide más de lo que decodifica la pantalla") + (in.limit.length() > 0 ? " (" + in.limit + ")" : "");
    }

    /** Lo ya leído de cada archivo (ruta|tamaño|fecha), para el selector y para no volver a leerlo. */
    private static final java.util.HashMap<String, Info> CACHE = new java.util.HashMap<String, Info>();

    private static String key(File f) { return f.getAbsolutePath() + "|" + f.length() + "|" + f.lastModified(); }

    /** El veredicto ya leído (null si todavía no se leyó o si no hay decodificadores leídos). */
    static Info cached(File f) { synchronized (CACHE) { return CACHE.get(key(f)); } }

    /** {@link #probe} con caché (el veredicto solo se guarda si ya se leyeron los decodificadores). */
    static Info probeCached(File f) {
        String k = key(f);
        synchronized (CACHE) { Info i = CACHE.get(k); if (i != null) return i; }
        Info i = probe(f);
        if (decs != null) synchronized (CACHE) { CACHE.put(k, i); }
        return i;
    }
}
