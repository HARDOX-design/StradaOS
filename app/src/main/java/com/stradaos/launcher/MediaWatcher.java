package com.stradaos.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/** Pista en reproducción en Android 5+: cualquier app de música, Android Auto/CarPlay nativos, Bluetooth del sistema. */
public class MediaWatcher {
    private static MediaWatcher instance;
    private final Context ctx;
    private final NowPlaying.Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private MediaSessionManager msm;
    private MediaController current;
    /** Todas las sesiones activas, cada una con su escucha: si otra app empieza a sonar (p. ej. un video que
     *  se puso en segundo plano), el reproductor pasa a ella aunque la lista de sesiones no haya cambiado. */
    private final List<MediaController> all = new ArrayList<MediaController>();
    private final List<MediaController.Callback> allCb = new ArrayList<MediaController.Callback>();

    public MediaWatcher(Context c, NowPlaying.Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
        instance = this;
    }

    static void kick(Context c) { if (instance != null) instance.start(); }

    public boolean start() {
        try {
            msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(ctx, MediaListener.class);
            msm.removeOnActiveSessionsChangedListener(sessions);
            msm.addOnActiveSessionsChangedListener(sessions, cn);
            pick(msm.getActiveSessions(cn));
            return true;
        } catch (Throwable e) {
            return false;   // sin "Acceso a notificaciones"
        }
    }

    public void stop() {
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions); } catch (Throwable ignored) { }
        if (current != null) try { current.unregisterCallback(cb); } catch (Throwable ignored) { }
        current = null;
        watchAll(null);
    }

    private static boolean playing(MediaController m) {
        try {
            PlaybackState st = m.getPlaybackState();
            return st != null && (st.getState() == PlaybackState.STATE_PLAYING || st.getState() == PlaybackState.STATE_BUFFERING);
        } catch (Throwable e) { return false; }
    }

    /** Escucha el estado de todas las sesiones (no solo la que se muestra). */
    private void watchAll(List<MediaController> list) {
        for (int i = 0; i < all.size(); i++) try { all.get(i).unregisterCallback(allCb.get(i)); } catch (Throwable ignored) { }
        all.clear();
        allCb.clear();
        if (list == null) return;
        for (final MediaController m : list) {
            MediaController.Callback c = new MediaController.Callback() {
                public void onPlaybackStateChanged(PlaybackState st) {
                    // otra app empezó a sonar y la que se muestra no: pasar a la que suena
                    if (st != null && st.getState() == PlaybackState.STATE_PLAYING
                            && (current == null || (!current.getSessionToken().equals(m.getSessionToken()) && !playing(current)))) {
                        switchTo(m);
                    }
                }
            };
            try { m.registerCallback(c, ui); all.add(m); allCb.add(c); } catch (Throwable ignored) { }
        }
    }

    private void switchTo(MediaController best) {
        if (current != null && best != null && current.getSessionToken().equals(best.getSessionToken())) { emit(); return; }
        if (current != null) try { current.unregisterCallback(cb); } catch (Throwable ignored) { }
        current = best;
        if (current != null) current.registerCallback(cb, ui);
        emit();
    }

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = new MediaSessionManager.OnActiveSessionsChangedListener() {
        public void onActiveSessionsChanged(List<MediaController> list) { pick(list); }
    };

    private void pick(List<MediaController> list) {
        watchAll(list);
        MediaController best = null;
        if (list != null) {
            for (MediaController m : list) if (playing(m)) { best = m; break; }
            // nada suena: se mantiene la que se mostraba si sigue activa; si no, la primera de la lista
            if (best == null && current != null)
                for (MediaController m : list) if (m.getSessionToken().equals(current.getSessionToken())) { best = m; break; }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        switchTo(best);
    }

    /** true si hay una sesión activa de cualquier app (aunque esté en pausa). */
    public boolean hasSession() { return current != null; }

    private final MediaController.Callback cb = new MediaController.Callback() {
        public void onMetadataChanged(MediaMetadata m) { emit(); }
        public void onPlaybackStateChanged(PlaybackState s) { emit(); }
        public void onSessionDestroyed() { start(); }
    };

    private void emit() {
        final NowPlaying.Track t = new NowPlaying.Track();
        t.origin = "ms";
        if (current == null) {
            t.clear = true;
            t.artSet = true;
        } else {
            try {
                t.source = String.valueOf(ctx.getPackageManager().getApplicationLabel(
                        ctx.getPackageManager().getApplicationInfo(current.getPackageName(), 0)));
            } catch (Throwable e) { t.source = current.getPackageName(); }
            MediaMetadata m = current.getMetadata();
            if (m != null) {
                t.title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (t.title == null) t.title = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                t.artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (t.artist == null) t.artist = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                t.album = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
                long d = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (d > 0) t.durationMs = d;
                Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                t.art = art;
                t.artSet = true;
            }
            PlaybackState st = current.getPlaybackState();
            if (st != null) {
                t.playing = st.getState() == PlaybackState.STATE_PLAYING || st.getState() == PlaybackState.STATE_BUFFERING;
                long pos = st.getPosition();
                if (pos >= 0) {
                    if (t.playing && st.getLastPositionUpdateTime() > 0)
                        pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
                    t.positionMs = pos;
                }
            }
        }
        listener.onTrack(t);
    }

    /** Controles: 1 siguiente, -1 anterior, 0 play/pausa. Devuelve false si no hay sesión. */
    public boolean command(int which) {
        MediaController c = current;
        if (c == null) return false;
        MediaController.TransportControls tc = c.getTransportControls();
        if (which == 1) tc.skipToNext();
        else if (which == -1) tc.skipToPrevious();
        else {
            PlaybackState st = c.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) tc.pause(); else tc.play();
        }
        return true;
    }
}
