package com.stradaos.launcher;

import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Android 5+ (solo se llama ahí): la canción que manda el Bluetooth del sistema (AVRCP) en el aviso
 * «android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT». Trae la canción como MediaMetadata y el estado
 * como PlaybackState (no como textos), por eso antes StradaOS lo recibía pero no lo entendía (issue #5: pantallas
 * HCN con el celular por Bluetooth o con Android Auto inalámbrico por ZLink, que manda la canción por AVRCP).
 */
class AvrcpMeta {
    static final String ACTION = "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT";
    static final String EXTRA_METADATA = "android.bluetooth.avrcp-controller.profile.extra.METADATA";
    static final String EXTRA_PLAYBACK = "android.bluetooth.avrcp-controller.profile.extra.PLAYBACK";
    static final int META = 1, STATE = 2;

    /** Llena la pista con lo que traiga el aviso: devuelve META y/o STATE según lo que traía (0 = nada). */
    static int fill(Bundle x, NowPlaying.Track t) {
        int got = 0;
        Object m = x.get(EXTRA_METADATA);
        if (m instanceof MediaMetadata) {
            MediaMetadata md = (MediaMetadata) m;
            t.title = s(md.getString(MediaMetadata.METADATA_KEY_TITLE));
            t.artist = s(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
            if (t.artist == null) t.artist = s(md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
            t.album = s(md.getString(MediaMetadata.METADATA_KEY_ALBUM));
            long d = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (d > 0) t.durationMs = d;
            got |= META;
        }
        Object p = x.get(EXTRA_PLAYBACK);
        if (p instanceof PlaybackState) {
            PlaybackState st = (PlaybackState) p;
            int s = st.getState();
            if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
                    || s == PlaybackState.STATE_FAST_FORWARDING || s == PlaybackState.STATE_REWINDING) t.playing = true;
            else if (s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_STOPPED) t.playing = false;
            long pos = st.getPosition();
            if (pos >= 0) {
                if (Boolean.TRUE.equals(t.playing) && st.getLastPositionUpdateTime() > 0) {
                    long ago = SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime();
                    if (ago > 0 && ago < 60000) pos += (long) (ago * (st.getPlaybackSpeed() > 0 ? st.getPlaybackSpeed() : 1f));
                }
                t.positionMs = pos;
            }
            got |= STATE;
        }
        return got;
    }

    private static String s(String v) {
        if (v == null) return null;
        v = v.trim();
        return v.length() == 0 || "Not Provided".equalsIgnoreCase(v) || "unknown".equalsIgnoreCase(v) ? null : v;
    }

    /** Pruebas (StradaOS Prueba): arma el mismo aviso que manda el sistema. estado: 3 sonando, 2 en pausa. */
    static Intent testIntent(String title, String artist, int state, long posMs, long durMs) {
        Intent i = new Intent(ACTION);
        if (title != null) {
            MediaMetadata.Builder b = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
            if (durMs > 0) b.putLong(MediaMetadata.METADATA_KEY_DURATION, durMs);
            i.putExtra(EXTRA_METADATA, b.build());
        }
        if (state >= 0) {
            i.putExtra(EXTRA_PLAYBACK, new PlaybackState.Builder()
                    .setState(state, posMs, 1f, SystemClock.elapsedRealtime()).build());
        }
        return i;
    }
}
