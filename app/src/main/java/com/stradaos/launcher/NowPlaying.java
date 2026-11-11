package com.stradaos.launcher;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Pista en reproducción.
 * 1) Pantalla de control remoto del sistema (API oculta de Android 4.0–4.3): Open Headunit, PlayerPro, etc.
 * 2) Avisos (broadcasts) de reproductores comunes y de apps Bluetooth de radios Android.
 */
public class NowPlaying {

    public static class Track {
        public String title, artist, album, source;
        public Boolean playing;          // null = desconocido
        public long durationMs = -1;     // -1 = desconocido
        public long positionMs = -1;     // -1 = desconocido
        public Bitmap art;
        public boolean artSet;           // true si este aviso trae carátula (o la borra)
        public boolean clear;            // no hay nada sonando
        public String origin = "bc";     // rcc = control remoto del sistema, bt = Bluetooth, unit = apps del equipo, bc = reproductores
        public boolean polled;           // leído al servicio Bluetooth (no aviso del celular): su play/pausa es local, no del A2DP real
    }

    public interface Listener { void onTrack(Track t); }

    static final String[] ACTIONS = {
        // reproductores
        "com.android.music.metachanged", "com.android.music.playstatechanged",
        "com.android.music.playbackcomplete", "com.android.music.queuechanged",
        "com.spotify.music.metadatachanged", "com.spotify.music.playbackstatechanged",
        "com.tbig.playerpro.metachanged", "com.tbig.playerpro.playstatechanged",
        "com.maxmpz.audioplayer.TRACK_CHANGED", "com.maxmpz.audioplayer.STATUS_CHANGED",
        "com.htc.music.metachanged", "com.htc.music.playstatechanged",
        "com.sec.android.app.music.metachanged", "com.miui.player.metachanged",
        "com.andrew.apollo.metachanged", "com.andrew.apollo.playstatechanged",
        "com.jrtstudio.AnotherMusicPlayer.metachanged", "com.jrtstudio.music.metachanged",
        "com.doubleTwist.androidPlayer.metachanged", "com.real.IMP.metachanged",
        "com.amazon.mp3.metachanged", "fm.last.android.metachanged",
        "deezer.android.app.metachanged", "com.lge.music.metachanged",
        "com.sonyericsson.music.metachanged", "com.rdio.android.metachanged",
        "com.samsung.sec.android.MusicPlayer.metachanged", "com.nullsoft.winamp.metachanged",
        "org.videolan.vlc.metachanged",
        // Bluetooth (AVRCP) de Android y de radios chinas conocidas
        "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT",
        "com.android.bluetooth.a2dpsink.action.METADATA_CHANGED",
        "com.android.music.bluetooth.metachanged",
        "com.microntek.music.report", "com.microntek.bt.report",
        "com.syu.bt.music.id3", "com.syu.ms.bt.id3",
        "com.goodocom.gocsdk.music.info", "com.goodocom.gocsdk.action.MUSIC_INFO",
        "com.ts.bt.music.info", "com.fyt.bt.music.id3",
        "com.autochips.bluetooth.MUSIC_INFO", "com.autochips.bt.music.id3",
        "com.nwd.action.ACTION_BT_MUSIC_ID3",
    };

    // ── Bluetooth de esta pantalla (Autochips AC8227 / apps Clinkway "com.clw") ──
    static final String AVRCP = "com.autochips.bluetooth.avrcpct.AvrcpCtPlayerUtility.";
    static final String AVRCP_MEDIA = AVRCP + "action.ACTION_MEDIA_DATA_UPDATE";
    static final String AVRCP_PLAYBACK = AVRCP + "action.ACTION_PLAYBACK_DATA_UPDATE";
    static final String CLW_INFO = "com.clw.intent.MEDIA_INFO";
    static final String CLW_STATE = "com.clw.intent.MEDIA_PLAY_STATE";
    static final String CLW_TIME = "com.clw.intent.MEDIA_PLAY_TIME_INFO";
    static final String CLW_IMAGE = "com.clw.intent.MEDIA_PLAY_IMAGE_INFO";

    private final Context ctx;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Object display;
    private boolean registered;
    private String lastSource;
    private Bundle lastMeta;
    private String btTitle, btArtist;

    public NowPlaying(Context c, Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
    }

    public void start() {
        IntentFilter f = new IntentFilter();
        for (String a : ACTIONS) f.addAction(a);
        for (String a : new String[]{AVRCP_MEDIA, AVRCP_PLAYBACK, CLW_INFO, CLW_STATE, CLW_TIME, CLW_IMAGE}) f.addAction(a);
        String[] extra = ctx.getSharedPreferences("launcher", 0).getString("extra_actions", "").split("[,\\s]+");
        for (String a : extra) if (a.trim().length() > 3) f.addAction(a.trim());
        try { ctx.registerReceiver(receiver, f); registered = true; } catch (Throwable ignored) { }
        registerDisplay();
    }

    public void stop() {
        if (registered) { try { ctx.unregisterReceiver(receiver); } catch (Throwable ignored) { } registered = false; }
        unregisterDisplay();
    }

    // ── avisos de reproductores ──
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            try {
                Bundle x = i.getExtras();
                if (x == null) return;
                String a = i.getAction();
                if (handleVendor(a, x)) return;
                Track t = new Track();
                t.title = str(x, "track", "title", "song", "name", "com.maxmpz.audioplayer.TRACK", "id3_title", "music_title", "TITLE");
                t.artist = str(x, "artist", "singer", "id3_artist", "music_artist", "ARTIST");
                t.album = str(x, "album", "id3_album", "music_album", "ALBUM");
                if (x.containsKey("playing")) t.playing = bool(x.get("playing"));
                else if (x.containsKey("isPlaying")) t.playing = bool(x.get("isPlaying"));
                else if (x.containsKey("playstate")) t.playing = bool(x.get("playstate"));
                t.durationMs = num(x, "duration", "length", "total_time", "DURATION");
                t.positionMs = num(x, "position", "playbackPosition", "current_time", "POSITION");
                if (t.durationMs > 0 && t.durationMs < 36000) t.durationMs *= 1000; // venía en segundos
                if (t.positionMs > 0 && t.durationMs > 0 && t.positionMs < 36000 && t.durationMs >= 36000 && t.positionMs * 1000 <= t.durationMs) t.positionMs *= 1000;
                t.source = sourceFromAction(a);
                if (t.title == null && t.artist == null && t.playing == null) return;
                emit(t);
            } catch (Throwable ignored) { }
        }
    };

    /** Avisos propios de la pantalla (Bluetooth Autochips y widget multimedia Clinkway). */
    private boolean handleVendor(String a, Bundle x) {
        if (AVRCP_MEDIA.equals(a)) {
            Track t = new Track();
            t.origin = "bt";
            t.source = "Bluetooth";
            t.title = str(x, AVRCP + "extra.MEDIA_TITLE");
            t.artist = str(x, AVRCP + "extra.MEDIA_ARTIST");
            if (t.title != null) btTitle = t.title;
            if (t.artist != null) btArtist = t.artist;
            t.album = str(x, AVRCP + "extra.MEIDA_ALBUM", AVRCP + "extra.MEDIA_ALBUM");
            emit(t);
            return true;
        }
        if (AVRCP_PLAYBACK.equals(a)) {
            Track t = new Track();
            t.origin = "bt";
            t.source = "Bluetooth";
            t.title = btTitle;   // el aviso de tiempo no trae título: se usa el último conocido
            t.artist = btArtist;
            long len = num(x, AVRCP + "extra.MEDIA_LENGTH");
            long pos = num(x, AVRCP + "extra.MEDIA_POSITION");
            if (len > 0) t.durationMs = len;
            if (pos >= 0) t.positionMs = pos;
            Object st = x.get(AVRCP + "extra.PLAYBACK_STATUS");
            if (st instanceof Number) t.playing = ((Number) st).intValue() == 1;
            emit(t);
            return true;
        }
        if (CLW_INFO.equals(a) || CLW_STATE.equals(a) || CLW_TIME.equals(a) || CLW_IMAGE.equals(a)) {
            Track t = new Track();
            int type = x.containsKey("Media_Type") ? x.getInt("Media_Type", -1) : -1;
            t.source = type == 3 ? "Bluetooth" : "Equipo";
            t.origin = type == 3 ? "bt" : "unit";
            if (CLW_INFO.equals(a)) {
                t.title = str(x, "Media_Title");
                t.artist = str(x, "Media_Artist");
                t.album = str(x, "Media_Album");
                if (type == 3) { if (t.title != null) btTitle = t.title; if (t.artist != null) btArtist = t.artist; }
            } else if (CLW_STATE.equals(a)) {
                Object st = x.get("Play_State");
                if (st != null) t.playing = bool(st);
            } else if (CLW_TIME.equals(a)) {
                long cur = num(x, "Media_Current_Time");
                long tot = num(x, "Media_Total_Time");
                if (tot > 0) t.durationMs = tot;
                if (cur >= 0) t.positionMs = cur;
            } else {
                Object img = x.get("Media_Image");
                if (img == null) img = x.get("artwork");
                if (img instanceof Bitmap) { t.art = (Bitmap) img; t.artSet = true; }
                else if (img instanceof String) {
                    try { t.art = android.graphics.BitmapFactory.decodeFile((String) img); t.artSet = t.art != null; } catch (Throwable ignored) { }
                } else if (img instanceof byte[]) {
                    byte[] b = (byte[]) img;
                    try { t.art = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length); t.artSet = t.art != null; } catch (Throwable ignored) { }
                }
                if (!t.artSet) return true;
            }
            emit(t);
            return true;
        }
        return false;
    }

    private String sourceFromAction(String a) {
        String l = a.toLowerCase();
        if (l.contains("bluetooth") || l.contains(".bt") || l.contains("avrcp") || l.contains("a2dp")) return "Bluetooth";
        if (l.contains("spotify")) return "Spotify";
        if (l.contains("playerpro")) return "PlayerPro";
        if (l.contains("maxmpz")) return "Poweramp";
        if (l.contains("deezer")) return "Deezer";
        if (l.contains("vlc")) return "VLC";
        if (l.contains("com.android.music")) return "Música";
        String s = a.substring(0, a.lastIndexOf('.'));
        String[] p = s.split("\\.");
        String n = p.length > 1 ? p[1] : p[0];
        return n.substring(0, 1).toUpperCase() + n.substring(1);
    }

    private static String str(Bundle x, String... keys) {
        for (String k : keys) {
            Object o = x.get(k);
            if (o instanceof String && ((String) o).trim().length() > 0
                    && !"Not Provided".equalsIgnoreCase(((String) o).trim()) && !"unknown".equalsIgnoreCase(((String) o).trim())) return ((String) o).trim();
        }
        return null;
    }

    private static long num(Bundle x, String... keys) {
        for (String k : keys) {
            Object o = x.get(k);
            if (o instanceof Number) return ((Number) o).longValue();
            if (o instanceof String) try { return Long.parseLong(((String) o).trim()); } catch (Exception ignored) { }
        }
        return -1;
    }

    private static Boolean bool(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) { int v = ((Number) o).intValue(); return v == 1 || v == 3; }
        if (o instanceof String) return "true".equalsIgnoreCase((String) o) || "1".equals(o) || "playing".equalsIgnoreCase((String) o);
        return null;
    }

    private void emit(final Track t) {
        ui.post(new Runnable() { public void run() { listener.onTrack(t); } });
    }

    // ── pantalla de control remoto del sistema ──
    private void registerDisplay() {
        if (android.os.Build.VERSION.SDK_INT < 14 || android.os.Build.VERSION.SDK_INT > 18) return;
        try {
            Class<?> rcd = Class.forName("android.media.IRemoteControlDisplay");
            display = Class.forName("com.stradaos.launcher.RcDisplay").getConstructor(RcCallback.class).newInstance(new Display());
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            try {
                AudioManager.class.getMethod("registerRemoteControlDisplay", rcd).invoke(am, display);
            } catch (NoSuchMethodException e) {
                AudioManager.class.getMethod("registerRemoteControlDisplay", rcd, int.class, int.class)
                    .invoke(am, display, 300, 300);
            }
            try { // pedir carátula (Android 4.0–4.2)
                AudioManager.class.getMethod("remoteControlDisplayUsesBitmapSize", rcd, int.class, int.class)
                    .invoke(am, display, 300, 300);
            } catch (Throwable ignored) { }
        } catch (Throwable e) {
            display = null;
        }
    }

    private void unregisterDisplay() {
        if (display == null) return;
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            AudioManager.class.getMethod("unregisterRemoteControlDisplay", Class.forName("android.media.IRemoteControlDisplay")).invoke(am, display);
        } catch (Throwable ignored) { }
        display = null;
    }

    private String sourceFrom(PendingIntent pi) {
        try {
            String pkg = pi.getTargetPackage();
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Throwable e) {
            return null;
        }
    }

    private Track fromMeta(Bundle m) {
        Track t = new Track();
        t.origin = "rcc";
        t.source = lastSource;
        if (m != null) {
            t.title = m.getString("7");                       // TITLE
            t.artist = m.getString("2");                      // ARTIST
            if (t.artist == null) t.artist = m.getString("13"); // ALBUMARTIST
            t.album = m.getString("1");                       // ALBUM
            try { long d = m.getLong("9", -1); if (d > 0) t.durationMs = d; } catch (Throwable ignored) { }
        }
        return t;
    }

    private class Display implements RcCallback {
        public void clientId(PendingIntent pi, boolean clearing) {
            try {
                if (clearing) {
                    lastSource = null;
                    lastMeta = null;
                    Track t = new Track();
                    t.origin = "rcc";
                    t.clear = true;
                    t.artSet = true;
                    emit(t);
                } else if (pi != null) {
                    lastSource = sourceFrom(pi);
                }
            } catch (Throwable ignored) { }
        }
        public void playbackState(int state, long pos) {
            try {
                Track tr = fromMeta(lastMeta);
                tr.playing = state == 3 || state == 4 || state == 5 || state == 8;
                if (pos >= 0) tr.positionMs = pos;
                emit(tr);
            } catch (Throwable ignored) { }
        }
        public void metadata(Bundle m) {
            try { lastMeta = m; emit(fromMeta(m)); } catch (Throwable ignored) { }
        }
        public void artwork(Bitmap b) {
            try {
                Track t = fromMeta(lastMeta);
                t.art = b == null ? null : b.copy(Bitmap.Config.ARGB_8888, false);
                t.artSet = true;
                emit(t);
            } catch (Throwable ignored) { }
        }
        public void allMetadata(Bundle m, Bitmap b) {
            try {
                lastMeta = m;
                Track t = fromMeta(m);
                t.art = b == null ? null : b.copy(Bitmap.Config.ARGB_8888, false);
                t.artSet = true;
                emit(t);
            } catch (Throwable ignored) { }
        }
    }
}
