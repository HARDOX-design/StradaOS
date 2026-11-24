package com.stradaos.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;

/** Entrega al instalador de Android 7+ solo los APK que el usuario eligió (content:// en vez de file://). */
public class ApkProvider extends ContentProvider {
    static String AUTH = "com.stradaos.launcher.apk";
    private static final HashMap<String, File> files = new HashMap<String, File>();

    static Uri share(File f) { return share(f, ".apk"); }

    /** Igual, para otros archivos (un video para abrirlo en VLC); el tipo sale de la extensión. */
    static Uri share(File f, String ext) {
        String token = Long.toHexString(System.nanoTime()) + ext;
        synchronized (files) { files.put(token, f); }
        return Uri.parse("content://" + AUTH + "/" + token);
    }

    @Override public boolean onCreate() { AUTH = getContext().getPackageName() + ".apk"; return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f;
        synchronized (files) { f = files.get(uri.getLastPathSegment()); }
        if (f == null) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        String s = uri.getLastPathSegment();
        if (s != null && (s.endsWith(".mp4") || s.endsWith(".m4v"))) return "video/mp4";
        if (s != null && s.endsWith(".mkv")) return "video/x-matroska";
        if (s != null && VideoCard.isVideo(s)) return "video/*";
        return "application/vnd.android.package-archive";
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
