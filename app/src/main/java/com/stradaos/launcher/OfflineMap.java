package com.stradaos.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Mapa sin conexión: lee un archivo .map de mapsforge (p. ej. colombia.map de download.mapsforge.org) y dibuja
 * teselas de 256 px con el estilo de StradaOS (claro u oscuro), sin internet.
 *
 * El lector sigue la especificación del formato y está adaptado del lector de mapsforge
 * (https://github.com/mapsforge/mapsforge, LGPL-3.0; © mapsforge.org y colaboradores), reducido a lo que el mapa
 * del inicio necesita. Los datos del mapa son © colaboradores de OpenStreetMap (ODbL).
 */
public class OfflineMap {
    // ── archivo ──
    private final RandomAccessFile raf;
    private final FileChannel ch;
    final File file;
    final long fileSize, mapDate;
    final double minLat, minLon, maxLat, maxLon;
    private final boolean debug;
    private String[] poiK, poiV, wayK, wayV;
    private Sub[] subs;                 // por nivel de zoom
    private int zMin = 127, zMax = -128;

    static class Sub {
        int base, zMin, zMax;
        long start, size, indexStart, indexEnd;
        long left, top, right, bottom, w, h, blocks;
    }

    private final LinkedHashMap<Long, byte[]> indexCache = new LinkedHashMap<Long, byte[]>(32, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<Long, byte[]> e) { return size() > 48; }
    };
    private long blockBytes;
    private final LinkedHashMap<Long, byte[]> blockCache = new LinkedHashMap<Long, byte[]>(16, 0.75f, true);

    // estadísticas para el diagnóstico
    volatile int rendered;
    volatile long renderMs;

    private OfflineMap(File f) throws IOException {
        file = f;
        raf = new RandomAccessFile(f, "r");
        ch = raf.getChannel();
        fileSize = ch.size();
        try {
            byte[] head = read(0, 24);
            String magic = new String(head, 0, 20, "UTF-8");
            if (!"mapsforge binary OSM".equals(magic)) throw new IOException("no es un mapa de mapsforge (.map)");
            int rest = getInt(head, 20);
            if (rest < 70 || rest > 1000000) throw new IOException("cabecera inválida");
            Buf b = new Buf(read(24, rest));
            int version = b.int32();
            if (version < 3 || version > 5) throw new IOException("versión de mapa no compatible: " + version);
            if (b.int64() != fileSize) throw new IOException("el archivo está incompleto (¿se copió entero?)");
            mapDate = b.int64();
            minLat = b.int32() / 1e6; minLon = b.int32() / 1e6; maxLat = b.int32() / 1e6; maxLon = b.int32() / 1e6;
            b.short16();                                   // tamaño de tesela del archivo (no se usa)
            String proj = b.str();
            if (!"Mercator".equals(proj)) throw new IOException("proyección no compatible: " + proj);
            int flags = b.byte8();
            debug = (flags & 0x80) != 0;
            if ((flags & 0x40) != 0) { b.int32(); b.int32(); }
            if ((flags & 0x20) != 0) b.byte8();
            if ((flags & 0x10) != 0) b.str();
            if ((flags & 0x08) != 0) b.str();
            if ((flags & 0x04) != 0) b.str();
            String[][] pt = tags(b); poiK = pt[0]; poiV = pt[1];
            String[][] wt = tags(b); wayK = wt[0]; wayV = wt[1];
            int n = b.byte8();
            if (n < 1) throw new IOException("mapa sin datos");
            Sub[] tmp = new Sub[n];
            for (int i = 0; i < n; i++) {
                Sub s = new Sub();
                s.base = b.byte8(); s.zMin = b.byte8(); s.zMax = b.byte8();
                s.start = b.int64(); s.size = b.int64();
                if (s.base < 0 || s.base > 20 || s.zMin > s.zMax || s.start < 70 || s.start >= fileSize) throw new IOException("subarchivo inválido");
                s.indexStart = s.start + (debug ? 16 : 0);
                s.left = tileX(minLon, s.base); s.right = tileX(maxLon, s.base);
                s.top = tileY(maxLat, s.base); s.bottom = tileY(minLat, s.base);
                s.w = s.right - s.left + 1; s.h = s.bottom - s.top + 1;
                s.blocks = s.w * s.h;
                s.indexEnd = s.indexStart + s.blocks * 5;
                tmp[i] = s;
                zMin = Math.min(zMin, s.zMin); zMax = Math.max(zMax, s.zMax);
            }
            subs = new Sub[zMax + 1];
            for (Sub s : tmp) for (int z = s.zMin; z <= s.zMax; z++) subs[z] = s;
        } catch (IOException e) {
            close();
            throw e;
        } catch (RuntimeException e) {
            close();
            throw new IOException("archivo dañado (" + e.getClass().getSimpleName() + ")");
        }
    }

    static OfflineMap open(File f) throws IOException {
        if (!f.isFile()) throw new IOException("no se encuentra el archivo");
        if (!f.canRead()) throw new IOException("sin permiso para leer el archivo");
        return new OfflineMap(f);
    }

    void close() {
        try { ch.close(); } catch (Throwable ignored) { }
        try { raf.close(); } catch (Throwable ignored) { }
    }

    /** ¿La tesela cae (al menos en parte) dentro del área del archivo? */
    boolean covers(int z, int x, int y) {
        double n = 1 << z;
        double lon0 = x / n * 360 - 180, lon1 = (x + 1) / n * 360 - 180;
        double lat0 = tileLat(y + 1, z), lat1 = tileLat(y, z);
        return lon1 >= minLon && lon0 <= maxLon && lat1 >= minLat && lat0 <= maxLat;
    }

    private final long[][] range = new long[23][];   // por zoom: {x0, x1, y0, y1} de las teselas del archivo

    /** Como covers(), pero con los límites ya calculados (se llama en cada cuadro). */
    boolean coversFast(int z, int x, int y) {
        if (z < 0 || z > 22) return false;
        long[] r = range[z];
        if (r == null) { r = new long[]{tileX(minLon, z), tileX(maxLon, z), tileY(maxLat, z), tileY(minLat, z)}; range[z] = r; }
        return x >= r[0] && x <= r[1] && y >= r[2] && y <= r[3];
    }

    /** ¿El punto está dentro del área del archivo? */
    boolean contains(double lat, double lon) {
        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }

    // ── lectura de bajo nivel ──

    private byte[] read(long pos, int len) throws IOException {
        byte[] out = new byte[len];
        ByteBuffer bb = ByteBuffer.wrap(out);
        synchronized (ch) {
            ch.position(pos);
            while (bb.hasRemaining()) if (ch.read(bb) < 0) throw new IOException("fin de archivo inesperado");
        }
        return out;
    }

    private static int getInt(byte[] b, int o) {
        return (b[o] << 24) | ((b[o + 1] & 0xff) << 16) | ((b[o + 2] & 0xff) << 8) | (b[o + 3] & 0xff);
    }

    private String[][] tags(Buf b) {
        int n = b.short16();
        String[] k = new String[n], v = new String[n];
        for (int i = 0; i < n; i++) {
            String t = b.str();
            int eq = t == null ? -1 : t.indexOf('=');
            k[i] = eq < 0 ? t : t.substring(0, eq);
            v[i] = eq < 0 ? "" : t.substring(eq + 1);
        }
        return new String[][]{k, v};
    }

    /** Búfer con las lecturas del formato (enteros de largo variable, cadenas UTF-8…). */
    static final class Buf {
        final byte[] d;
        int p;
        Buf(byte[] d) { this.d = d; }
        int byte8() { return d[p++]; }
        int short16() { p += 2; return (d[p - 2] << 8) | (d[p - 1] & 0xff); }
        int int32() { p += 4; return getInt(d, p - 4); }
        long int64() {
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (d[p + i] & 0xffL);
            p += 8;
            return v;
        }
        int uvar() {
            int v = 0, sh = 0;
            while ((d[p] & 0x80) != 0) { v |= (d[p++] & 0x7f) << sh; sh += 7; }
            return v | (d[p++] << sh);
        }
        int svar() {
            int v = 0, sh = 0;
            while ((d[p] & 0x80) != 0) { v |= (d[p++] & 0x7f) << sh; sh += 7; }
            if ((d[p] & 0x40) != 0) return -(v | ((d[p++] & 0x3f) << sh));
            return v | ((d[p++] & 0x3f) << sh);
        }
        String str() { return str(uvar()); }
        String str(int len) {
            if (len < 0 || p + len > d.length) throw new IndexOutOfBoundsException("cadena");
            p += len;
            try { return new String(d, p - len, len, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) { return ""; }
        }
    }

    private long indexEntry(Sub s, long block) throws IOException {
        long ib = block / 128;
        long key = s.start * 31 + ib;
        byte[] blk;
        synchronized (indexCache) { blk = indexCache.get(key); }
        if (blk == null) {
            long pos = s.indexStart + ib * 640;
            int len = (int) Math.min(640, s.indexEnd - pos);
            blk = read(pos, len);
            synchronized (indexCache) { indexCache.put(key, blk); }
        }
        int o = (int) (block % 128) * 5;
        return ((blk[o] & 0xffL) << 32) | ((blk[o + 1] & 0xffL) << 24) | ((blk[o + 2] & 0xffL) << 16) | ((blk[o + 3] & 0xffL) << 8) | (blk[o + 4] & 0xffL);
    }

    private byte[] block(Sub s, long block) throws IOException {
        long key = s.start * 1000003L + block;
        synchronized (blockCache) {
            byte[] c = blockCache.get(key);
            if (c != null) return c;
        }
        long cur = indexEntry(s, block) & 0x7FFFFFFFFFL;
        if (cur < 1 || cur > s.size) return null;
        long next = block + 1 == s.blocks ? s.size : indexEntry(s, block + 1) & 0x7FFFFFFFFFL;
        if (next > s.size) return null;
        long len = next - cur;
        if (len <= 0 || len > 10000000 || s.start + cur + len > fileSize) return null;
        byte[] data = read(s.start + cur, (int) len);
        synchronized (blockCache) {
            blockCache.put(key, data);
            blockBytes += data.length;
            // caché de bloques: ~12 MB (los bloques de una ciudad grande pueden pesar 1-3 MB)
            java.util.Iterator<Map.Entry<Long, byte[]>> it = blockCache.entrySet().iterator();
            while ((blockBytes > 12L * 1024 * 1024 || blockCache.size() > 12) && it.hasNext()) {
                Map.Entry<Long, byte[]> e = it.next();
                if (e.getKey() == key) continue;
                blockBytes -= e.getValue().length;
                it.remove();
            }
        }
        return data;
    }

    // ── proyección (Mercator, en unidades de tesela) ──

    static double lonX(double lon, int z) { return (lon + 180) / 360 * (1 << z); }
    static double latY(double lat, int z) {
        double sn = Math.sin(Math.toRadians(lat));
        double y = 0.5 - Math.log((1 + sn) / (1 - sn)) / (4 * Math.PI);
        return Math.min(Math.max(0, y), 1) * (1 << z);
    }
    static double tileLat(long y, int z) {
        double v = 0.5 - (double) y / (1L << z);
        return 90 - 360 * Math.atan(Math.exp(-v * 2 * Math.PI)) / Math.PI;
    }
    static long tileX(double lon, int z) { return (long) Math.min(Math.max(0, Math.floor(lonX(lon, z))), (1L << z) - 1); }
    static long tileY(double lat, int z) { return (long) Math.min(Math.max(0, Math.floor(latY(lat, z))), (1L << z) - 1); }

    private static int bitmask(long tx, long ty, int diff) {
        if (diff == 1) {
            if (tx % 2 == 0 && ty % 2 == 0) return 0xcc00;
            if (tx % 2 == 1 && ty % 2 == 0) return 0x3300;
            if (tx % 2 == 0) return 0xcc;
            return 0x33;
        }
        long sx = tx >>> (diff - 2), sy = ty >>> (diff - 2);
        long px = sx >>> 1, py = sy >>> 1;
        int[] q;
        if (px % 2 == 0 && py % 2 == 0) q = new int[]{0x8000, 0x4000, 0x800, 0x400};
        else if (px % 2 == 1 && py % 2 == 0) q = new int[]{0x2000, 0x1000, 0x200, 0x100};
        else if (px % 2 == 0) q = new int[]{0x80, 0x40, 0x8, 0x4};
        else q = new int[]{0x20, 0x10, 0x2, 0x1};
        return q[(int) (sx % 2) + 2 * (int) (sy % 2)];
    }

    // ── datos de una tesela ──

    /** Elemento ya clasificado y en píxeles de la tesela. */
    static final class W {
        int kind, layer;
        boolean tunnel;
        String name;
        float[][] pts;
    }

    // tipos de elemento, en orden de dibujo
    static final int SEA = 1, LAND = 2, URBAN = 3, GREEN = 4, FOREST = 5, WATER = 6, RIVER = 7, STREAM = 8,
            BUILDING = 9, RAIL = 10, ROAD = 20;   // ROAD + clase de vía (0 = la menor)
    // clases de vía, de menor a mayor
    static final String[] ROADS = {"track", "service", "living_street", "residential", "unclassified", "road",
            "tertiary", "secondary", "primary", "trunk", "motorway"};

    private int roadClass(String v) {
        if (v.endsWith("_link")) v = v.substring(0, v.length() - 5);
        if ("pedestrian".equals(v)) return 2;
        for (int i = 0; i < ROADS.length; i++) if (ROADS[i].equals(v)) return i;
        return -1;
    }

    private int classify(int[] ids, int n, int z) {
        int kind = 0;
        for (int i = 0; i < n; i++) {
            String k = wayK[ids[i]], v = wayV[ids[i]];
            int c = 0;
            if ("highway".equals(k)) {
                int rc = roadClass(v);
                if (rc >= 0) {
                    int minZ = rc >= 6 ? 0 : rc >= 3 ? 13 : rc == 2 ? 14 : 15;
                    if (z >= minZ) c = ROAD + rc;
                }
            } else if ("natural".equals(k)) {
                if ("sea".equals(v) || "issea".equals(v)) c = SEA;
                else if ("nosea".equals(v) || "land".equals(v)) c = LAND;
                else if ("water".equals(v) || "bay".equals(v)) c = WATER;
                else if ("wood".equals(v)) c = FOREST;
                else if ("scrub".equals(v) || "grassland".equals(v) || "heath".equals(v)) c = GREEN;
            } else if ("waterway".equals(k)) {
                if ("riverbank".equals(v) || "dock".equals(v)) c = WATER;
                else if ("river".equals(v) || "canal".equals(v)) c = RIVER;
                else if (z >= 14 && ("stream".equals(v) || "drain".equals(v) || "ditch".equals(v))) c = STREAM;
            } else if ("landuse".equals(k)) {
                if ("reservoir".equals(v) || "basin".equals(v)) c = WATER;
                else if ("forest".equals(v)) c = FOREST;
                else if ("grass".equals(v) || "meadow".equals(v) || "recreation_ground".equals(v) || "village_green".equals(v) || "cemetery".equals(v)) c = GREEN;
                else if ("residential".equals(v) || "commercial".equals(v) || "retail".equals(v) || "industrial".equals(v)) c = URBAN;
            } else if ("leisure".equals(k)) {
                if ("park".equals(v) || "garden".equals(v) || "pitch".equals(v) || "golf_course".equals(v) || "nature_reserve".equals(v) || "playground".equals(v) || "stadium".equals(v)) c = GREEN;
            } else if ("building".equals(k)) {
                if (z >= 16 && !"no".equals(v)) c = BUILDING;
            } else if ("railway".equals(k)) {
                if ("rail".equals(v) || "light_rail".equals(v) || "narrow_gauge".equals(v)) c = RAIL;
            }
            if (c > kind) kind = c;   // una vía con building=* y highway=* queda como vía
        }
        return kind;
    }

    private static final class Data {
        final ArrayList<W> ways = new ArrayList<W>();
        final ArrayList<Object[]> places = new ArrayList<Object[]>();   // {nombre, x, y, rango}
    }

    private Data read(int z, int x, int y, float size) throws IOException {
        Data out = new Data();
        int qz = Math.max(zMin, Math.min(zMax, z));
        Sub s = subs[qz];
        if (s == null) return out;
        long fx, fy, tx, ty;
        boolean useMask = false;
        int mask = 0;
        if (qz < s.base) {
            int d = s.base - qz;
            fx = (long) x << d; fy = (long) y << d;
            tx = fx + (1L << d) - 1; ty = fy + (1L << d) - 1;
        } else if (qz > s.base) {
            int d = qz - s.base;
            fx = tx = x >>> d; fy = ty = y >>> d;
            useMask = true;
            mask = bitmask(x, y, d);
        } else { fx = tx = x; fy = ty = y; }
        long bx0 = Math.max(fx - s.left, 0), by0 = Math.max(fy - s.top, 0);
        long bx1 = Math.min(tx - s.left, s.w - 1), by1 = Math.min(ty - s.top, s.h - 1);
        double scale = 1 << z;
        for (long row = by0; row <= by1; row++) {
            for (long col = bx0; col <= bx1; col++) {
                long bn = row * s.w + col;
                byte[] data = block(s, bn);
                if (data == null) continue;
                double tLat = tileLat(s.top + row, s.base);
                double tLon = (double) (s.left + col) / (1L << s.base) * 360 - 180;
                try {
                    parseBlock(new Buf(data), s, qz, useMask, mask, tLat, tLon, z, x, y, size, scale, out);
                } catch (RuntimeException e) {
                    // bloque dañado o fuera de rango: se ignora, el resto de la tesela se dibuja igual
                }
            }
        }
        return out;
    }

    private void parseBlock(Buf b, Sub s, int qz, boolean useMask, int mask, double tLat, double tLon,
                       int z, int x, int y, float size, double scale, Data out) {
        if (debug) b.str(32);
        int rows = s.zMax - s.zMin + 1, r = qz - s.zMin;
        int pois = 0, ways = 0, cp = 0, cw = 0;
        for (int i = 0; i < rows; i++) {
            cp += b.uvar(); cw += b.uvar();
            if (i == r) { pois = cp; ways = cw; }
        }
        int firstWay = b.uvar();
        firstWay += b.p;
        if (firstWay > b.d.length) return;
        int[] tagIds = new int[16];
        // POI: solo nombres de lugares (ciudad, barrio…)
        for (int i = 0; i < pois; i++) {
            if (debug) b.str(32);
            double la = tLat + b.svar() / 1e6, lo = tLon + b.svar() / 1e6;
            int sp = b.byte8();
            int nt = sp & 0x0f;
            int rank = 0;
            for (int t = 0; t < nt; t++) {
                int id = b.uvar();
                if (id < 0 || id >= poiK.length) return;
                tagIds[t] = id;
            }
            for (int t = 0; t < nt; t++) {
                int id = tagIds[t];
                skipVarValue(b, poiV[id], poiK[id]);
                if ("place".equals(poiK[id])) {
                    String v = poiV[id];
                    rank = "city".equals(v) ? 5 : "town".equals(v) ? 4 : "village".equals(v) || "suburb".equals(v) ? 3
                            : "neighbourhood".equals(v) || "quarter".equals(v) ? 2 : "hamlet".equals(v) ? 1 : 0;
                }
            }
            int fb = b.byte8();
            String name = (fb & 0x80) != 0 ? local(b.str()) : null;
            if ((fb & 0x40) != 0) b.str();
            if ((fb & 0x20) != 0) b.svar();
            if (rank > 0 && name != null) {
                int minZ = rank >= 4 ? 0 : rank == 3 ? 13 : 15;
                if (z >= minZ) out.places.add(new Object[]{name, (float) ((lonX(lo, z) - x) * size), (float) ((latY(la, z) - y) * size), rank});
            }
        }
        if (b.p > firstWay) return;
        b.p = firstWay;
        float margin = size * 0.15f;
        for (int i = 0; i < ways; i++) {
            if (debug) b.str(32);
            int sz = b.uvar();
            if (sz < 0) return;
            int end = b.p + sz;
            if (useMask) {
                int tm = b.short16();
                if ((mask & tm) == 0) { b.p = end; continue; }
            } else b.p += 2;
            int sp = b.byte8();
            int layer = (sp & 0xf0) >>> 4, nt = sp & 0x0f;
            boolean tunnel = false;
            for (int t = 0; t < nt; t++) {
                int id = b.uvar();
                if (id < 0 || id >= wayK.length) return;
                tagIds[t] = id;
            }
            for (int t = 0; t < nt; t++) {
                int id = tagIds[t];
                skipVarValue(b, wayV[id], wayK[id]);
                if ("tunnel".equals(wayK[id]) && !"no".equals(wayV[id])) tunnel = true;
            }
            int kind = classify(tagIds, nt, z);
            if (kind == 0) { b.p = end; continue; }
            int fb = b.byte8();
            String name = (fb & 0x80) != 0 ? local(b.str()) : null;
            if ((fb & 0x40) != 0) b.str();
            if ((fb & 0x20) != 0) b.str();
            if ((fb & 0x10) != 0) { b.svar(); b.svar(); }
            int blocks = (fb & 0x08) != 0 ? b.uvar() : 1;
            boolean dd = (fb & 0x04) != 0;
            for (int k = 0; k < blocks; k++) {
                int nc = b.uvar();
                if (nc < 1 || nc > Short.MAX_VALUE) return;
                float[][] pts = new float[nc][];
                float minx = Float.MAX_VALUE, miny = Float.MAX_VALUE, maxx = -Float.MAX_VALUE, maxy = -Float.MAX_VALUE;
                for (int c = 0; c < nc; c++) {
                    int nn = b.uvar();
                    if (nn < 2 || nn > Short.MAX_VALUE) return;
                    float[] p = new float[nn * 2];
                    double la = tLat + b.svar() / 1e6, lo = tLon + b.svar() / 1e6;
                    double dla = 0, dlo = 0;
                    for (int q = 0; q < nn; q++) {
                        if (q > 0) {
                            double a = b.svar() / 1e6, o = b.svar() / 1e6;
                            if (dd) { dla += a; dlo += o; la += dla; lo += dlo; } else { la += a; lo += o; }
                        }
                        float px = (float) ((lonX(lo, z) - x) * size), py = (float) ((latY(la, z) - y) * size);
                        p[q * 2] = px; p[q * 2 + 1] = py;
                        if (px < minx) minx = px; if (px > maxx) maxx = px;
                        if (py < miny) miny = py; if (py > maxy) maxy = py;
                    }
                    pts[c] = p;
                }
                if (maxx < -margin || minx > size + margin || maxy < -margin || miny > size + margin) continue;
                W w = new W();
                w.kind = kind; w.layer = layer; w.tunnel = tunnel; w.name = name; w.pts = pts;
                out.ways.add(w);
            }
            b.p = end;
        }
    }

    static final int FLAG_TOLL = 1;
    static final int FLAG_MOTORWAY = 2;

    /** Mapas v5: algunas etiquetas guardan su valor dentro del elemento («%b», «%i»…); hay que saltarlo o leerlo. */
    private static void skipVarValue(Buf b, String v, String k) {
        readTagVar(b, v, k, false);
    }

    private static String readTagVar(Buf b, String v, String k, boolean wantValue) {
        if (v.length() != 2 || v.charAt(0) != '%') return v;
        switch (v.charAt(1)) {
            case 'b': {
                int raw = b.byte8() & 0xff;
                return wantValue ? String.valueOf(raw) : v;
            }
            case 'h': {
                int raw = b.short16();
                return wantValue ? String.valueOf(raw) : v;
            }
            case 'i': {
                int raw = b.int32();
                return wantValue ? String.valueOf(raw) : v;
            }
            case 'f': {
                float raw = Float.intBitsToFloat(b.int32());
                return wantValue ? String.valueOf(Math.round(raw)) : v;
            }
            case 's': {
                String raw = b.str();
                return wantValue ? raw : v;
            }
            default:
                return v;
        }
    }

    static short parseMaxSpeedKmh(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().toLowerCase(Locale.US);
        if (s.length() == 0) return 0;
        boolean mph = s.indexOf("mph") >= 0;
        int i = 0;
        while (i < s.length() && !Character.isDigit(s.charAt(i))) {
            // solo aceptar números al inicio o después de prefijo de país como «ES:urban»
            if (i > 4) return 0;
            i++;
        }
        int val = 0, digits = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            val = val * 10 + (s.charAt(i) - '0');
            digits++;
            i++;
            if (digits > 3) return 0;
        }
        if (digits == 0) return 0;
        if (mph) val = (int) Math.round(val * 1.60934);
        return (val >= 5 && val <= 220) ? (short) val : 0;
    }

    /** Nombres en varios idiomas («Base\ren\bEnglish…»): se usa el principal (el local). */
    private static String local(String s) {
        if (s == null) return null;
        int r = s.indexOf('\r');
        String n = r >= 0 ? s.substring(0, r) : s;
        return n.trim().length() == 0 ? null : n;
    }

    // ── dibujo ──

    static final class Palette {
        int land, sea, urban, green, forest, building, rail, text, halo, casing, minor;
        int[] road = new int[ROADS.length];
    }

    static Palette palette(boolean light, int bright) {
        Palette p = new Palette();
        if (light) {
            p.land = 0xFFF2EFE9; p.sea = 0xFFAAD3DF; p.urban = 0xFFE9E5DF; p.green = 0xFFCDEBC0; p.forest = 0xFFB5D9A4;
            p.building = 0xFFDCD3CC; p.rail = 0xFF9A9A9A; p.text = 0xFF2B2E34; p.halo = 0xF0FFFFFF; p.casing = 0xFFC4BCB2;
            int[] r = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
                    0xFFFFFFFF, 0xFFF7FABF, 0xFFFCD6A4, 0xFFF9B29C, 0xFFE892A2};
            p.road = r;
        } else {
            p.land = 0xFF1C1F25; p.sea = 0xFF15293B; p.urban = 0xFF20232A; p.green = 0xFF1C2B23; p.forest = 0xFF1A2A21;
            p.building = 0xFF2A2E36; p.rail = 0xFF4E545F; p.text = 0xFFD5D9E0; p.halo = 0xE0101216; p.casing = 0xFF111317;
            int[] r = {0xFF353A44, 0xFF3A3F49, 0xFF3F4550, 0xFF444A56, 0xFF444A56, 0xFF444A56,
                    0xFF59606D, 0xFF6A7180, 0xFF8A6D45, 0xFFA2703F, 0xFFB8773E};
            p.road = r;
            float lift = bright <= 0 ? 0f : bright == 1 ? 0.06f : 0.13f;
            if (lift > 0) {
                p.land = mix(p.land, lift); p.urban = mix(p.urban, lift); p.green = mix(p.green, lift); p.forest = mix(p.forest, lift);
                p.building = mix(p.building, lift); p.sea = mix(p.sea, lift);
                for (int i = 0; i < r.length; i++) r[i] = mix(r[i], lift * 1.6f);
            }
        }
        p.minor = p.road[0];
        return p;
    }

    private static int mix(int c, float t) {
        int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
        r += (int) ((255 - r) * t); g += (int) ((255 - g) * t); b += (int) ((255 - b) * t);
        return (c & 0xff000000) | (r << 16) | (g << 8) | b;
    }

    // ancho de cada clase de vía a zoom 16 (px en tesela de 256)
    private static final float[] ROAD_W = {1.4f, 2.6f, 3.6f, 4.4f, 4.4f, 4.4f, 5.4f, 6.2f, 7.2f, 8.2f, 9.0f};

    /** Dibuja la tesela z/x/y en un mapa de bits de size×size. */
    Bitmap render(int z, int x, int y, int size, boolean light, int bright) throws IOException {
        long t0 = System.currentTimeMillis();
        Data d = read(z, x, y, size);
        Palette pal = palette(light, bright);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        c.drawColor(pal.land);
        float k = size / 256f;
        float zf = (float) Math.pow(2, (z - 16) * 0.8);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        Path path = new Path();

        // áreas y líneas que no son vías, en orden de tipo
        int[] order = {SEA, LAND, URBAN, GREEN, FOREST, WATER, RIVER, STREAM, BUILDING, RAIL};
        for (int kind : order) {
            for (W w : d.ways) {
                if (w.kind != kind) continue;
                boolean area = kind <= WATER || kind == BUILDING;
                if (area) {
                    fill.setColor(kind == SEA || kind == WATER ? pal.sea : kind == LAND ? pal.land : kind == URBAN ? pal.urban
                            : kind == GREEN ? pal.green : kind == FOREST ? pal.forest : pal.building);
                    path.reset();
                    path.setFillType(Path.FillType.EVEN_ODD);   // el primer anillo es el exterior; los demás, huecos
                    for (float[] p : w.pts) addPoly(path, p, true);
                    c.drawPath(path, fill);
                } else {
                    if (kind == RAIL) {
                        line.setColor(pal.rail);
                        line.setStrokeWidth(Math.max(1f, 1.6f * k * Math.max(zf, 0.6f)));
                    } else {
                        line.setColor(pal.sea);
                        line.setStrokeWidth(Math.max(1f, (kind == RIVER ? 5f : 1.6f) * k * zf));
                    }
                    path.reset();
                    for (float[] p : w.pts) addPoly(path, p, false);
                    c.drawPath(path, line);
                }
            }
        }

        // vías: por capa (túneles abajo, puentes arriba); en cada capa, bordes y luego relleno, de menor a mayor
        ArrayList<W> roads = new ArrayList<W>();
        for (W w : d.ways) if (w.kind >= ROAD) roads.add(w);
        java.util.Collections.sort(roads, new java.util.Comparator<W>() {
            public int compare(W a, W b) { return a.layer != b.layer ? a.layer - b.layer : a.kind - b.kind; }
        });
        int i = 0;
        while (i < roads.size()) {
            int j = i;
            while (j < roads.size() && roads.get(j).layer == roads.get(i).layer) j++;
            for (int pass = 0; pass < 2; pass++) {
                for (int q = i; q < j; q++) {
                    W w = roads.get(q);
                    int rc = w.kind - ROAD;
                    float wd = Math.max(rc <= 1 ? 0.8f : 1.1f, ROAD_W[rc] * k * zf);
                    if (pass == 0) {
                        if (rc == 0) continue;   // las trochas van sin borde
                        line.setColor(pal.casing);
                        line.setStrokeWidth(wd + Math.max(1.2f, 1.6f * k));
                    } else {
                        line.setColor(pal.road[rc]);
                        line.setStrokeWidth(wd);
                    }
                    line.setAlpha(w.tunnel ? 110 : 255);
                    path.reset();
                    for (float[] p : w.pts) addPoly(path, p, false);
                    c.drawPath(path, line);
                }
            }
            i = j;
        }
        line.setAlpha(255);

        drawLabels(c, d, z, size, k, pal);
        rendered++;
        renderMs += System.currentTimeMillis() - t0;
        return bmp;
    }

    private static void addPoly(Path path, float[] p, boolean close) {
        path.moveTo(p[0], p[1]);
        for (int q = 2; q < p.length; q += 2) path.lineTo(p[q], p[q + 1]);
        if (close) path.close();
    }

    private void drawLabels(Canvas c, Data d, int z, int size, float k, Palette pal) {
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(pal.text);
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setColor(pal.halo);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeJoin(Paint.Join.ROUND);
        ArrayList<RectF> used = new ArrayList<RectF>();
        float pad = 3 * k;

        // nombres de calles, siguiendo la vía (solo si caben enteros dentro de la tesela)
        if (z >= 14) {
            txt.setTextSize((z >= 16 ? 11f : 10f) * k);
            halo.setTextSize(txt.getTextSize());
            halo.setStrokeWidth(3f * k);
            HashSet<String> done = new HashSet<String>();
            ArrayList<W> named = new ArrayList<W>();
            for (W w : d.ways) if (w.kind >= ROAD && w.name != null && (z >= 15 || w.kind - ROAD >= 6)) named.add(w);
            java.util.Collections.sort(named, new java.util.Comparator<W>() {
                public int compare(W a, W b) { return b.kind - a.kind; }   // primero las vías principales
            });
            PathMeasure pm = new PathMeasure();
            Path run = new Path();
            for (W w : named) {
                if (done.contains(w.name)) continue;
                float tw = txt.measureText(w.name);
                for (float[] p : w.pts) {
                    // tramo más largo con todos sus puntos dentro de la tesela
                    int bestA = -1, bestB = -1;
                    float bestLen = 0;
                    int a = -1;
                    float len = 0;
                    for (int q = 0; q <= p.length / 2; q++) {
                        boolean in = q < p.length / 2 && p[q * 2] >= pad && p[q * 2] <= size - pad && p[q * 2 + 1] >= pad && p[q * 2 + 1] <= size - pad;
                        if (in) {
                            if (a < 0) { a = q; len = 0; }
                            else len += (float) Math.hypot(p[q * 2] - p[q * 2 - 2], p[q * 2 + 1] - p[q * 2 - 1]);
                        } else {
                            if (a >= 0 && q - 1 > a && len > bestLen) { bestLen = len; bestA = a; bestB = q - 1; }
                            a = -1;
                        }
                    }
                    if (bestA < 0 || bestLen < tw + 8 * k) continue;
                    boolean rev = p[bestB * 2] < p[bestA * 2];   // que el texto no quede al revés
                    run.reset();
                    float minx = Float.MAX_VALUE, miny = Float.MAX_VALUE, maxx = -Float.MAX_VALUE, maxy = -Float.MAX_VALUE;
                    for (int q = 0; q <= bestB - bestA; q++) {
                        int idx = rev ? bestB - q : bestA + q;
                        float px = p[idx * 2], py = p[idx * 2 + 1];
                        if (q == 0) run.moveTo(px, py); else run.lineTo(px, py);
                        minx = Math.min(minx, px); maxx = Math.max(maxx, px); miny = Math.min(miny, py); maxy = Math.max(maxy, py);
                    }
                    RectF box = new RectF(minx - 4 * k, miny - 6 * k, maxx + 4 * k, maxy + 6 * k);
                    boolean hit = false;
                    for (RectF u : used) if (RectF.intersects(u, box)) { hit = true; break; }
                    if (hit) continue;
                    pm.setPath(run, false);
                    float off = (pm.getLength() - tw) / 2;
                    float vo = txt.getTextSize() * 0.35f;
                    c.drawTextOnPath(w.name, run, off, vo, halo);
                    c.drawTextOnPath(w.name, run, off, vo, txt);
                    used.add(box);
                    done.add(w.name);
                    break;
                }
            }
        }

        // ciudades, pueblos y barrios
        java.util.Collections.sort(d.places, new java.util.Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) { return (Integer) b[3] - (Integer) a[3]; }
        });
        Paint bold = new Paint(txt);
        bold.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        Paint bhalo = new Paint(halo);
        bhalo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        for (Object[] pl : d.places) {
            String name = (String) pl[0];
            float px = (Float) pl[1], py = (Float) pl[2];
            int rank = (Integer) pl[3];
            float ts = (rank >= 5 ? 15f : rank == 4 ? 13.5f : rank == 3 ? 12f : 11f) * k;
            bold.setTextSize(ts); bhalo.setTextSize(ts); bhalo.setStrokeWidth(3.5f * k);
            float tw = bold.measureText(name);
            RectF box = new RectF(px - tw / 2 - 2 * k, py - ts, px + tw / 2 + 2 * k, py + ts * 0.35f);
            if (box.left < pad || box.top < pad || box.right > size - pad || box.bottom > size - pad) continue;
            boolean hit = false;
            for (RectF u : used) if (RectF.intersects(u, box)) { hit = true; break; }
            if (hit) continue;
            c.drawText(name, px - tw / 2, py, bhalo);
            c.drawText(name, px - tw / 2, py, bold);
            used.add(box);
        }
    }

    String describe() {
        java.text.DateFormat df = I18n.latin(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, I18n.loc));
        return file.getName() + " · " + (fileSize / (1024 * 1024)) + " MB · " + df.format(new java.util.Date(mapDate));
    }

    // ───────────────────────── lectura directa y utilidades ─────────────────────────

    private byte[] blockRaw(Sub s, long block) throws IOException {
        synchronized (blockCache) {
            byte[] c = blockCache.get(s.start * 1000003L + block);
            if (c != null) return c;
        }
        long cur = indexEntry(s, block) & 0x7FFFFFFFFFL;
        if (cur < 1 || cur > s.size) return null;
        long next = block + 1 == s.blocks ? s.size : indexEntry(s, block + 1) & 0x7FFFFFFFFFL;
        if (next > s.size) return null;
        long len = next - cur;
        if (len <= 0 || len > 10000000 || s.start + cur + len > fileSize) return null;
        return read(s.start + cur, (int) len);
    }

    static double distM(double la1, double lo1, double la2, double lo2) {
        double ky = 111320.0;
        double kx = ky * Math.cos(Math.toRadians((la1 + la2) * 0.5));
        double dy = (la2 - la1) * ky, dx = (lo2 - lo1) * kx;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static String fold(String s) {
        if (s == null) return "";
        String norm = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(norm.length());
        for (int i = 0; i < norm.length(); i++) {
            char ch = norm.charAt(i);
            if (Character.getType(ch) != Character.NON_SPACING_MARK) sb.append(Character.toLowerCase(ch));
        }
        return sb.toString().trim();
    }

    // ───────────────────────── búsqueda sin conexión (lugares, POIs y calles) ─────────────────────────

    static final class SearchHit {
        String name, tag;
        double lat, lon, distM;
        int score;
    }

    /** Busca ciudades, POIs o calles dentro del archivo .map sin usar red. */
    ArrayList<SearchHit> searchOffline(String query, String cat, double nearLat, double nearLon, int limit) {
        ArrayList<SearchHit> out = new ArrayList<SearchHit>();
        if (subs == null || subs.length == 0) return out;
        if (Double.isNaN(nearLat) || Double.isNaN(nearLon) || !contains(nearLat, nearLon)) {
            nearLat = (minLat + maxLat) * 0.5;
            nearLon = (minLon + maxLon) * 0.5;
        }
        String qFold = fold(query);
        String[] tokens = qFold.length() > 0 ? qFold.split("\\s+") : new String[0];
        if (cat == null && tokens.length == 0) return out;

        HashMap<String, SearchHit> bestByKey = new HashMap<String, SearchHit>();
        HashSet<Long> visitedSubBlocks = new HashSet<Long>();
        long deadline = System.currentTimeMillis() + 1400L;

        // 1. Subarchivos generales (ciudades y pueblos en toda la región/país cuando busca por texto)
        if (cat == null) {
            ArrayList<Sub> overviews = new ArrayList<Sub>();
            for (Sub s : subs) {
                if (s != null && s.zMax < zMax && !overviews.contains(s)) overviews.add(s);
            }
            for (Sub s : overviews) {
                if (System.currentTimeMillis() > deadline) break;
                if (s.blocks <= 48) {
                    for (long bn = 0; bn < s.blocks; bn++) {
                        if (System.currentTimeMillis() > deadline) break;
                        long col = bn % s.w, row = bn / s.w;
                        scanSearchBlock(s, (int) row, (int) col, false, qFold, tokens, null, nearLat, nearLon, bestByKey, visitedSubBlocks);
                    }
                } else {
                    scanSpiral(s, nearLat, nearLon, 3, false, qFold, tokens, null, bestByKey, visitedSubBlocks, deadline);
                }
            }
        }

        // 2. Subarchivo de detalle alrededor de la posición (POIs y calles con nombre)
        Sub detail = subs[zMax];
        if (detail != null) {
            int rad = cat != null ? 3 : 4; // hasta (2*rad+1)^2 bloques alrededor (~15-22 km)
            scanSpiral(detail, nearLat, nearLon, rad, cat == null, qFold, tokens, cat, bestByKey, visitedSubBlocks, deadline);
        }

        out.addAll(bestByKey.values());
        Collections.sort(out, new Comparator<SearchHit>() {
            public int compare(SearchHit a, SearchHit b) {
                if (b.score != a.score) return b.score > a.score ? 1 : -1;
                return a.distM < b.distM ? -1 : a.distM > b.distM ? 1 : 0;
            }
        });
        if (out.size() > limit) return new ArrayList<SearchHit>(out.subList(0, limit));
        return out;
    }

    private void scanSpiral(Sub s, double nearLat, double nearLon, int maxRad, boolean scanWays,
                            String qFold, String[] tokens, String cat,
                            HashMap<String, SearchHit> best, HashSet<Long> visited, long deadline) {
        int cCol = (int) (tileX(nearLon, s.base) - s.left);
        int cRow = (int) (tileY(nearLat, s.base) - s.top);
        for (int r = 0; r <= maxRad; r++) {
            for (int dr = -r; dr <= r; dr++) {
                for (int dc = -r; dc <= r; dc++) {
                    if (Math.abs(dr) != r && Math.abs(dc) != r) continue;
                    if (System.currentTimeMillis() > deadline) return;
                    int rr = cRow + dr, cc = cCol + dc;
                    if (rr < 0 || rr >= s.h || cc < 0 || cc >= s.w) continue;
                    scanSearchBlock(s, rr, cc, scanWays, qFold, tokens, cat, nearLat, nearLon, best, visited);
                }
            }
        }
    }

    private void scanSearchBlock(Sub s, int row, int col, boolean scanWays,
                                 String qFold, String[] tokens, String cat,
                                 double nearLat, double nearLon,
                                 HashMap<String, SearchHit> best, HashSet<Long> visited) {
        long bn = (long) row * s.w + col;
        long bKey = s.start * 1000003L + bn;
        if (!visited.add(bKey)) return;
        byte[] data;
        try { data = blockRaw(s, bn); } catch (Throwable e) { return; }
        if (data == null) return;
        double tLat = tileLat(s.top + row, s.base);
        double tLon = (double) (s.left + col) / (1L << s.base) * 360 - 180;
        Buf b = new Buf(data);
        try {
            if (debug) b.str(32);
            int rows = s.zMax - s.zMin + 1;
            int totalPois = 0, totalWays = 0;
            for (int i = 0; i < rows; i++) { totalPois += b.uvar(); totalWays += b.uvar(); }
            int firstWay = b.uvar() + b.p;
            if (firstWay > b.d.length) return;
            int[] tagIds = new int[16];
            for (int i = 0; i < totalPois; i++) {
                if (debug) b.str(32);
                double la = tLat + b.svar() / 1e6, lo = tLon + b.svar() / 1e6;
                int sp = b.byte8();
                int nt = sp & 0x0f;
                for (int t = 0; t < nt; t++) {
                    int id = b.uvar();
                    if (id < 0 || id >= poiK.length) return;
                    tagIds[t] = id;
                }
                String tagLabel = null;
                int boost = 0;
                boolean catOk = cat == null;
                for (int t = 0; t < nt; t++) {
                    int id = tagIds[t];
                    String k = poiK[id], v = poiV[id];
                    skipVarValue(b, v, k);
                    if ("place".equals(k)) {
                        tagLabel = v;
                        if ("city".equals(v)) boost = 50;
                        else if ("town".equals(v)) boost = 40;
                        else if ("village".equals(v) || "suburb".equals(v)) boost = 30;
                        else boost = 15;
                    } else if ("amenity".equals(k) || "shop".equals(k) || "tourism".equals(k) || "railway".equals(k) || "aeroway".equals(k)) {
                        if (tagLabel == null) tagLabel = v;
                        boost = Math.max(boost, 22);
                        if (cat != null && matchesCat(cat, k, v)) catOk = true;
                    }
                }
                int fb = b.byte8();
                String name = (fb & 0x80) != 0 ? local(b.str()) : null;
                if ((fb & 0x40) != 0) b.str();
                if ((fb & 0x20) != 0) b.svar();
                if (!catOk) continue;
                if (name == null) {
                    if (cat == null) continue;
                    name = prettyTag(tagLabel);
                }
                int matchSc = cat != null ? 100 : matchScore(name, qFold, tokens);
                if (matchSc <= 0) continue;
                SearchHit h = new SearchHit();
                h.name = name;
                h.tag = tagLabel != null ? tagLabel : "poi";
                h.lat = la; h.lon = lo;
                h.distM = distM(nearLat, nearLon, la, lo);
                h.score = matchSc + boost - (int) Math.min(45, h.distM / 5000.0);
                addHit(best, h);
            }
            if (!scanWays || b.p > firstWay) return;
            b.p = firstWay;
            for (int i = 0; i < totalWays; i++) {
                if (debug) b.str(32);
                int sz = b.uvar();
                if (sz < 0) return;
                int end = b.p + sz;
                b.p += 2;
                int sp = b.byte8();
                int nt = sp & 0x0f;
                for (int t = 0; t < nt; t++) {
                    int id = b.uvar();
                    if (id < 0 || id >= wayK.length) return;
                    tagIds[t] = id;
                }
                String hw = null;
                for (int t = 0; t < nt; t++) {
                    int id = tagIds[t];
                    skipVarValue(b, wayV[id], wayK[id]);
                    if ("highway".equals(wayK[id])) hw = wayV[id];
                }
                if (roadRank(hw) < 0) { b.p = end; continue; }
                int fb = b.byte8();
                String name = (fb & 0x80) != 0 ? local(b.str()) : null;
                if ((fb & 0x40) != 0) b.str();
                String ref = (fb & 0x20) != 0 ? b.str() : null;
                if ((fb & 0x10) != 0) { b.svar(); b.svar(); }
                String label = name != null ? name : (ref != null && ref.trim().length() > 0 ? ref.trim() : null);
                int matchSc = label != null ? matchScore(label, qFold, tokens) : 0;
                if (matchSc <= 0) { b.p = end; continue; }
                int bc = (fb & 0x08) != 0 ? b.uvar() : 1;
                if (bc >= 1 && b.p < end) {
                    int nc = b.uvar();
                    if (nc >= 1 && b.p < end) {
                        b.uvar();
                        double la = tLat + b.svar() / 1e6, lo = tLon + b.svar() / 1e6;
                        SearchHit h = new SearchHit();
                        h.name = label;
                        h.tag = "street";
                        h.lat = la; h.lon = lo;
                        h.distM = distM(nearLat, nearLon, la, lo);
                        h.score = matchSc + 5 - (int) Math.min(35, h.distM / 3000.0);
                        addHit(best, h);
                    }
                }
                b.p = end;
            }
        } catch (RuntimeException ignored) { }
    }

    private static void addHit(HashMap<String, SearchHit> best, SearchHit h) {
        String k = fold(h.name) + "|" + ("street".equals(h.tag) ? "s" : "p");
        SearchHit prev = best.get(k);
        if (prev == null || h.score > prev.score || (h.score == prev.score && h.distM < prev.distM)) {
            best.put(k, h);
        }
    }

    private static int matchScore(String name, String qFold, String[] tokens) {
        String nf = fold(name);
        if (nf.length() == 0) return 0;
        if (nf.equals(qFold)) return 140;
        if (nf.startsWith(qFold)) return 115;
        if (nf.contains(qFold)) return 95;
        for (String t : tokens) if (!nf.contains(t)) return 0;
        return 80;
    }

    private static boolean matchesCat(String cat, String k, String v) {
        if ("fuel".equals(cat)) return "amenity".equals(k) && "fuel".equals(v);
        if ("health".equals(cat)) return "amenity".equals(k) && ("hospital".equals(v) || "clinic".equals(v) || "pharmacy".equals(v) || "doctors".equals(v));
        if ("parking".equals(cat)) return "amenity".equals(k) && "parking".equals(v);
        if ("food".equals(cat)) return ("amenity".equals(k) && ("restaurant".equals(v) || "cafe".equals(v) || "fast_food".equals(v)))
                || ("shop".equals(k) && ("supermarket".equals(v) || "convenience".equals(v)));
        return false;
    }

    private static String prettyTag(String v) {
        if ("fuel".equals(v)) return "Estación de servicio";
        if ("hospital".equals(v)) return "Hospital";
        if ("clinic".equals(v)) return "Clínica";
        if ("pharmacy".equals(v)) return "Farmacia";
        if ("parking".equals(v)) return "Parqueadero";
        if ("restaurant".equals(v)) return "Restaurante";
        if ("supermarket".equals(v)) return "Supermercado";
        return v != null ? v : "Lugar";
    }

    // ───────────────────────── enrutamiento sin conexión sobre .map (A* por corredor) ─────────────────────────

    static final class RawHop {
        double lat, lon, distM, durS;
        String name;
        int rank, deg;
        boolean roundabout;
        short maxSpeedKmh;
        byte flags;
    }

    static final class RawRoute {
        double startLat, startLon, totalDistM, totalDurS;
        final ArrayList<RawHop> hops = new ArrayList<RawHop>();
    }

    private static int roadRank(String hw) {
        if (hw == null) return -1;
        if (hw.endsWith("_link")) hw = hw.substring(0, hw.length() - 5);
        if ("service".equals(hw)) return 1;
        if ("living_street".equals(hw)) return 2;
        if ("residential".equals(hw)) return 3;
        if ("unclassified".equals(hw) || "road".equals(hw)) return 4;
        if ("tertiary".equals(hw)) return 5;
        if ("secondary".equals(hw)) return 6;
        if ("primary".equals(hw)) return 7;
        if ("trunk".equals(hw)) return 8;
        if ("motorway".equals(hw)) return 9;
        return -1;
    }

    private static double speedMps(int rank) {
        switch (rank) {
            case 9: return 27.0;
            case 8: return 22.0;
            case 7: return 18.0;
            case 6: return 15.0;
            case 5: return 12.0;
            case 4: return 10.0;
            case 3: return 8.0;
            case 2: return 5.5;
            default: return 4.5;
        }
    }

    private static double[] ptSeg(double pla, double plo, double ala, double alo, double bla, double blo) {
        double ky = 111320.0, kx = ky * Math.cos(Math.toRadians(pla));
        double ax = (alo - plo) * kx, ay = (ala - pla) * ky;
        double bx = (blo - plo) * kx, by = (bla - pla) * ky;
        double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
        double t = len2 > 1e-6 ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2)) : 0;
        double px = ax + dx * t, py = ay + dy * t;
        return new double[]{Math.sqrt(px * px + py * py), t};
    }

    private static boolean isRoundaboutName(String name) {
        if (name == null || name.length() == 0) return false;
        String lf = fold(name);
        return lf.contains("rotonda") || lf.contains("roundabout") || lf.contains("glorieta") || lf.contains("retonda") || lf.contains("rotary");
    }

    private static final class REdge {
        int to; float d, cost; String name; byte rank, flags; boolean roundabout; short maxSpeedKmh;
        REdge(int t, float d, float c, String n, int r, boolean rb, byte fl, short ms) {
            to = t; this.d = d; cost = c; name = n; rank = (byte) r; roundabout = rb; flags = fl; maxSpeedKmh = ms;
        }
    }

    private static final class BEp {
        int node, blkId, rank; byte dir;
    }

    private static final class BSeg {
        int u, v, blkId, rank; String name; boolean oneway, roundabout; byte flags; short maxSpeedKmh;
    }

    private final class CorridorGraph {
        final Sub sub;
        final int minRow, maxRow, minCol, maxCol;
        final HashMap<Long, Integer> keyToId = new HashMap<Long, Integer>(8192);
        double[] latArr = new double[4096], lonArr = new double[4096];
        byte[] nodeRank = new byte[4096];
        @SuppressWarnings("unchecked")
        ArrayList<REdge>[] adj = new ArrayList[4096];
        int nNodes, blocksLoaded;
        final HashSet<Integer> loadedBlks = new HashSet<Integer>();
        final HashMap<Long, ArrayList<BEp>> epBuckets = new HashMap<Long, ArrayList<BEp>>();
        final HashMap<Long, ArrayList<BSeg>> segBuckets = new HashMap<Long, ArrayList<BSeg>>();

        CorridorGraph(Sub s, int r0, int r1, int c0, int c1) {
            sub = s;
            minRow = Math.max(0, r0); maxRow = Math.min((int) s.h - 1, r1);
            minCol = Math.max(0, c0); maxCol = Math.min((int) s.w - 1, c1);
        }

        int nodeId(double la, double lo, int rank) {
            long qla = Math.round(la * 200000.0), qlo = Math.round(lo * 200000.0);
            long k = (qla << 32) | (qlo & 0xFFFFFFFFL);
            Integer ex = keyToId.get(k);
            if (ex != null) {
                int id = ex;
                if (rank > nodeRank[id]) nodeRank[id] = (byte) rank;
                return id;
            }
            int id = nNodes++;
            if (id >= latArr.length) {
                int nl = latArr.length * 2;
                latArr = Arrays.copyOf(latArr, nl);
                lonArr = Arrays.copyOf(lonArr, nl);
                nodeRank = Arrays.copyOf(nodeRank, nl);
                adj = Arrays.copyOf(adj, nl);
            }
            keyToId.put(k, id);
            latArr[id] = la; lonArr[id] = lo; nodeRank[id] = (byte) rank;
            return id;
        }

        void addEdge(int u, int v, float d, float cost, String nm, int rank, boolean rb, byte flags, short maxSp) {
            ArrayList<REdge> l = adj[u];
            if (l == null) adj[u] = l = new ArrayList<REdge>(4);
            for (int i = 0; i < l.size(); i++) {
                REdge ex = l.get(i);
                if (ex.to == v) {
                    if (cost < ex.cost) {
                        ex.cost = cost; ex.d = d; ex.name = nm; ex.rank = (byte) rank;
                        ex.roundabout = rb; ex.flags = flags; ex.maxSpeedKmh = maxSp;
                    }
                    return;
                }
            }
            l.add(new REdge(v, d, cost, nm, rank, rb, flags, maxSp));
        }

        void ensureAround(double la, double lo, int padBlocks) throws IOException {
            int col = (int) (tileX(lo, sub.base) - sub.left);
            int row = (int) (tileY(la, sub.base) - sub.top);
            for (int r = row - padBlocks; r <= row + padBlocks; r++) {
                for (int c = col - padBlocks; c <= col + padBlocks; c++) {
                    if (blocksLoaded >= 120 || nNodes >= 85000) return;
                    loadBlock(r, c);
                }
            }
        }

        void loadBlock(int row, int col) throws IOException {
            if (row < minRow || row > maxRow || col < minCol || col > maxCol) return;
            int blkId = (row << 16) | col;
            if (!loadedBlks.add(blkId)) return;
            blocksLoaded++;
            long bn = (long) row * sub.w + col;
            byte[] data = blockRaw(sub, bn);
            if (data == null) return;
            double tLat = tileLat(sub.top + row, sub.base), bLat = tileLat(sub.top + row + 1, sub.base);
            double tLon = (double) (sub.left + col) / (1L << sub.base) * 360 - 180;
            double rLon = (double) (sub.left + col + 1) / (1L << sub.base) * 360 - 180;
            Buf blk = new Buf(data);
            if (debug) blk.str(32);
            int rows = sub.zMax - sub.zMin + 1;
            int maxPois = 0, maxWays = 0;
            for (int z = 0; z < rows; z++) { maxPois += blk.uvar(); maxWays += blk.uvar(); }
            int firstWay = blk.uvar() + blk.p;
            if (firstWay > data.length) return;
            blk.p = firstWay;
            int[] tagIds = new int[16];
            ArrayList<BEp> newEps = new ArrayList<BEp>();
            ArrayList<BSeg> newSegs = new ArrayList<BSeg>();

            for (int i = 0; i < maxWays; i++) {
                if (debug) blk.str(32);
                int sz = blk.uvar();
                if (sz < 0) return;
                int end = blk.p + sz;
                blk.p += 2;
                int sp = blk.byte8();
                int nt = sp & 0x0f;
                for (int t = 0; t < nt; t++) {
                    int id = blk.uvar();
                    if (id < 0 || id >= wayK.length) return;
                    tagIds[t] = id;
                }
                String hw = null; int ow = 0; boolean blocked = false, rb = false, toll = false;
                short maxSp = 0;
                for (int t = 0; t < nt; t++) {
                    int tid = tagIds[t];
                    String k = wayK[tid], v = wayV[tid];
                    boolean want = "maxspeed".equals(k) || "maxspeed:forward".equals(k) || "toll".equals(k);
                    String val = readTagVar(blk, v, k, want);
                    if ("highway".equals(k)) hw = v;
                    else if ("oneway".equals(k)) ow = "-1".equals(v) ? -1 : "yes".equals(v) ? 1 : 0;
                    else if ("junction".equals(k) && ("roundabout".equals(v) || "circular".equals(v))) rb = true;
                    else if (("motor_vehicle".equals(k) || "access".equals(k)) && ("no".equals(v) || "private".equals(v))) blocked = true;
                    else if ("toll".equals(k)) {
                        if (val != null && !"no".equals(val) && !"false".equals(val)) toll = true;
                    } else if ("maxspeed".equals(k)) {
                        short ms = parseMaxSpeedKmh(val);
                        if (ms > 0) maxSp = ms;
                    } else if ("maxspeed:forward".equals(k) && maxSp == 0) {
                        short ms = parseMaxSpeedKmh(val);
                        if (ms > 0) maxSp = ms;
                    }
                }
                int rank = roadRank(hw);
                if (rank < 0 || blocked) { blk.p = end; continue; }
                final byte edgeFlags = (byte) ((toll ? FLAG_TOLL : 0) | (rank >= 9 ? FLAG_MOTORWAY : 0));
                int fb = blk.byte8();
                String nm = (fb & 0x80) != 0 ? local(blk.str()) : "";
                if ((fb & 0x40) != 0) blk.str();
                String ref = (fb & 0x20) != 0 ? blk.str() : "";
                if (nm == null || nm.length() == 0) nm = ref != null ? ref.trim() : "";
                if (!rb && isRoundaboutName(nm)) rb = true;
                if ((fb & 0x10) != 0) { blk.svar(); blk.svar(); }
                int bc = (fb & 0x08) != 0 ? blk.uvar() : 1;
                boolean dd = (fb & 0x04) != 0;
                for (int k = 0; k < bc; k++) {
                    int nc = blk.uvar();
                    for (int c = 0; c < nc; c++) {
                        int nn = blk.uvar();
                        double la = tLat + blk.svar() / 1e6, lo = tLon + blk.svar() / 1e6;
                        double dla = 0, dlo = 0;
                        int prevId = -1; double prevLa = 0, prevLo = 0;
                        for (int q = 0; q < nn; q++) {
                            if (q > 0) {
                                double a = blk.svar() / 1e6, o = blk.svar() / 1e6;
                                if (dd) { dla += a; dlo += o; la += dla; lo += dlo; } else { la += a; lo += o; }
                            }
                            int id = nodeId(la, lo, rank);
                            if ((q == 0 || q == nn - 1) && nearBorder(la, lo, tLat, bLat, tLon, rLon)) {
                                BEp ep = new BEp();
                                ep.node = id; ep.blkId = blkId; ep.rank = rank;
                                ep.dir = (byte) (ow == 0 ? 0 : ((q == 0 && ow == 1) || (q == nn - 1 && ow == -1) ? 1 : 2));
                                long bk = bucketKey(la, lo);
                                ArrayList<BEp> eb = epBuckets.get(bk);
                                if (eb == null) { eb = new ArrayList<BEp>(4); epBuckets.put(bk, eb); }
                                eb.add(ep);
                                newEps.add(ep);
                            }
                            if (q > 0 && id != prevId) {
                                double d = distM(prevLa, prevLo, la, lo);
                                double spd = maxSp > 0 ? Math.min(speedMps(rank), maxSp / 3.6) : speedMps(rank);
                                double cost = d / spd;
                                if (ow >= 0) addEdge(prevId, id, (float) d, (float) cost, nm, rank, rb, edgeFlags, maxSp);
                                if (ow <= 0) addEdge(id, prevId, (float) d, (float) cost, nm, rank, rb, edgeFlags, maxSp);
                                if (nearBorder(la, lo, tLat, bLat, tLon, rLon) || nearBorder(prevLa, prevLo, tLat, bLat, tLon, rLon)) {
                                    BSeg sr = new BSeg();
                                    sr.u = ow == -1 ? id : prevId; sr.v = ow == -1 ? prevId : id;
                                    sr.blkId = blkId; sr.rank = rank; sr.name = nm; sr.oneway = ow != 0; sr.roundabout = rb;
                                    sr.flags = edgeFlags; sr.maxSpeedKmh = maxSp;
                                    long bk = bucketKey((prevLa + la) * 0.5, (prevLo + lo) * 0.5);
                                    ArrayList<BSeg> sb = segBuckets.get(bk);
                                    if (sb == null) { sb = new ArrayList<BSeg>(4); segBuckets.put(bk, sb); }
                                    sb.add(sr);
                                    newSegs.add(sr);
                                }
                            }
                            prevId = id; prevLa = la; prevLo = lo;
                        }
                    }
                }
                blk.p = end;
            }
            for (BEp ep : newEps) stitchEp(ep);
            for (BSeg sr : newSegs) {
                long bLa = (long) Math.floor(latArr[sr.u] * 1000.0), bLo = (long) Math.floor(lonArr[sr.u] * 1000.0);
                for (long dla = -1; dla <= 1; dla++) {
                    for (long dlo = -1; dlo <= 1; dlo++) {
                        ArrayList<BEp> eps = epBuckets.get(((bLa + dla) << 32) | ((bLo + dlo) & 0xFFFFFFFFL));
                        if (eps != null) for (BEp ep : eps) tryStitchPair(ep, sr);
                    }
                }
            }
        }

        private boolean nearBorder(double la, double lo, double tLat, double bLat, double tLon, double rLon) {
            return Math.abs(la - tLat) < 0.0007 || Math.abs(la - bLat) < 0.0007
                    || Math.abs(lo - tLon) < 0.0008 || Math.abs(lo - rLon) < 0.0008;
        }

        private long bucketKey(double la, double lo) {
            return ((long) Math.floor(la * 1000.0) << 32) | ((long) Math.floor(lo * 1000.0) & 0xFFFFFFFFL);
        }

        private void stitchEp(BEp ep) {
            long bLa = (long) Math.floor(latArr[ep.node] * 1000.0), bLo = (long) Math.floor(lonArr[ep.node] * 1000.0);
            for (long dla = -1; dla <= 1; dla++) {
                for (long dlo = -1; dlo <= 1; dlo++) {
                    ArrayList<BSeg> srs = segBuckets.get(((bLa + dla) << 32) | ((bLo + dlo) & 0xFFFFFFFFL));
                    if (srs != null) for (BSeg sr : srs) tryStitchPair(ep, sr);
                }
            }
        }

        private void tryStitchPair(BEp ep, BSeg sr) {
            if (sr.blkId == ep.blkId) return;
            if (Math.abs(sr.rank - ep.rank) > 1 && !(sr.rank >= 6 && ep.rank >= 6)) return;
            double ela = latArr[ep.node], elo = lonArr[ep.node];
            double[] pt = ptSeg(ela, elo, latArr[sr.u], lonArr[sr.u], latArr[sr.v], lonArr[sr.v]);
            if (pt[0] >= 3.5) return;
            double dU = distM(ela, elo, latArr[sr.u], lonArr[sr.u]);
            double dV = distM(ela, elo, latArr[sr.v], lonArr[sr.v]);
            int tgt = dU < dV ? sr.u : sr.v;
            if (tgt == ep.node) return;
            double spd = sr.maxSpeedKmh > 0 ? Math.min(speedMps(sr.rank), sr.maxSpeedKmh / 3.6) : speedMps(sr.rank);
            if (ep.dir == 0 && !sr.oneway) {
                double d = Math.min(dU, dV);
                addEdge(ep.node, tgt, (float) d, (float) (d / spd), sr.name, sr.rank, sr.roundabout, sr.flags, sr.maxSpeedKmh);
                addEdge(tgt, ep.node, (float) d, (float) (d / spd), sr.name, sr.rank, sr.roundabout, sr.flags, sr.maxSpeedKmh);
            } else {
                if (ep.dir == 2 || ep.dir == 0) addEdge(ep.node, sr.v, (float) dV, (float) (dV / spd), sr.name, sr.rank, sr.roundabout, sr.flags, sr.maxSpeedKmh);
                if (ep.dir == 1 || ep.dir == 0) addEdge(sr.u, ep.node, (float) dU, (float) (dU / spd), sr.name, sr.rank, sr.roundabout, sr.flags, sr.maxSpeedKmh);
            }
        }
    }

    /** Calcula una ruta en auto sin conexión dentro del archivo .map (A* sobre las vías del subarchivo de detalle). */
    RawRoute routeOffline(double la1, double lo1, double la2, double lo2) {
        return routeOffline(la1, lo1, la2, lo2, false, false);
    }

    RawRoute routeOffline(double la1, double lo1, double la2, double lo2, boolean avoidTolls, boolean avoidHighways) {
        if (subs == null || subs.length == 0 || !contains(la1, lo1) || !contains(la2, lo2)) return null;
        if (distM(la1, lo1, la2, lo2) > 65000) return null;
        Sub detail = subs[zMax];
        if (detail == null) return null;
        try {
            double pad = Math.max(0.035, distM(la1, lo1, la2, lo2) / 111320.0 * 0.35);
            int c0 = (int) (tileX(Math.min(lo1, lo2) - pad, detail.base) - detail.left);
            int c1 = (int) (tileX(Math.max(lo1, lo2) + pad, detail.base) - detail.left);
            int r0 = (int) (tileY(Math.max(la1, la2) + pad, detail.base) - detail.top);
            int r1 = (int) (tileY(Math.min(la1, la2) - pad, detail.base) - detail.top);
            CorridorGraph g = new CorridorGraph(detail, r0, r1, c0, c1);
            g.ensureAround(la1, lo1, 1);
            g.ensureAround(la2, lo2, 1);

            HashMap<Integer, Double> starts = topCandidates(g, la1, lo1, 6);
            HashMap<Integer, Double> goals = topCandidates(g, la2, lo2, 6);
            if (starts.isEmpty() || goals.isEmpty()) return null;

            float[] gCost = new float[Math.max(8192, g.nNodes * 2)];
            Arrays.fill(gCost, Float.MAX_VALUE);
            int[] prevN = new int[gCost.length];
            REdge[] prevE = new REdge[gCost.length];
            Arrays.fill(prevN, -1);

            PriorityQueue<double[]> pq = new PriorityQueue<double[]>(128, new Comparator<double[]>() {
                public int compare(double[] a, double[] b) { return Double.compare(a[0], b[0]); }
            });
            for (Map.Entry<Integer, Double> se : starts.entrySet()) {
                int sid = se.getKey();
                float ic = (float) (se.getValue() / 8.0);
                gCost[sid] = ic;
                pq.add(new double[]{ic + distM(g.latArr[sid], g.lonArr[sid], la2, lo2) / 27.0, ic, sid});
            }

            long deadline = System.currentTimeMillis() + 3500L;
            int reached = -1, exp = 0;
            while (!pq.isEmpty() && exp < 75000 && System.currentTimeMillis() < deadline) {
                double[] cur = pq.poll();
                int u = (int) cur[2];
                float ug = (float) cur[1];
                if (ug > gCost[u] + 1e-4f) continue;
                exp++;
                if (goals.containsKey(u)) { reached = u; break; }
                g.ensureAround(g.latArr[u], g.lonArr[u], 1);
                if (g.nNodes >= gCost.length) {
                    int nl = gCost.length * 2;
                    int ol = gCost.length;
                    gCost = Arrays.copyOf(gCost, nl); Arrays.fill(gCost, ol, nl, Float.MAX_VALUE);
                    prevN = Arrays.copyOf(prevN, nl); Arrays.fill(prevN, ol, nl, -1);
                    prevE = Arrays.copyOf(prevE, nl);
                }
                ArrayList<REdge> es = g.adj[u];
                if (es == null) continue;
                for (int i = 0; i < es.size(); i++) {
                    REdge ed = es.get(i);
                    float mult = 1f;
                    if (avoidTolls && (ed.flags & FLAG_TOLL) != 0) mult *= 30f;
                    if (avoidHighways && ((ed.flags & FLAG_MOTORWAY) != 0 || ed.rank >= 9)) mult *= 25f;
                    float ng = ug + ed.cost * mult;
                    if (ng < gCost[ed.to]) {
                        gCost[ed.to] = ng;
                        prevN[ed.to] = u;
                        prevE[ed.to] = ed;
                        double h = distM(g.latArr[ed.to], g.lonArr[ed.to], la2, lo2) / 27.0;
                        pq.add(new double[]{ng + h * 1.15, ng, ed.to});
                    }
                }
            }
            if (reached < 0) return null;

            ArrayList<REdge> rev = new ArrayList<REdge>();
            int cur = reached;
            while (prevN[cur] != -1) {
                rev.add(prevE[cur]);
                cur = prevN[cur];
            }
            RawRoute rr = new RawRoute();
            rr.startLat = g.latArr[cur];
            rr.startLon = g.lonArr[cur];
            for (int i = rev.size() - 1; i >= 0; i--) {
                REdge ed = rev.get(i);
                RawHop hop = new RawHop();
                hop.lat = g.latArr[ed.to];
                hop.lon = g.lonArr[ed.to];
                hop.distM = ed.d;
                hop.durS = ed.cost;
                hop.name = ed.name;
                hop.rank = ed.rank;
                hop.roundabout = ed.roundabout;
                hop.maxSpeedKmh = ed.maxSpeedKmh;
                hop.flags = ed.flags;
                hop.deg = g.adj[ed.to] != null ? g.adj[ed.to].size() : 1;
                rr.hops.add(hop);
                rr.totalDistM += ed.d;
                rr.totalDurS += ed.cost;
            }
            return rr.hops.isEmpty() ? null : rr;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Escanea POIs de radares («highway=speed_camera» / «enforcement=maxspeed») dentro de una caja geográfica. */
    ArrayList<double[]> findSpeedCameras(double minLa, double minLo, double maxLa, double maxLo) {
        ArrayList<double[]> out = new ArrayList<double[]>();
        if (subs == null || zMax < 0 || zMax >= subs.length) return out;
        Sub detail = subs[zMax];
        if (detail == null) return out;
        double qMinLa = Math.max(minLat, minLa), qMaxLa = Math.min(maxLat, maxLa);
        double qMinLo = Math.max(minLon, minLo), qMaxLo = Math.min(maxLon, maxLo);
        if (qMinLa > qMaxLa || qMinLo > qMaxLo) return out;

        int c0 = Math.max(0, (int) (tileX(qMinLo, detail.base) - detail.left));
        int c1 = Math.min((int) detail.w - 1, (int) (tileX(qMaxLo, detail.base) - detail.left));
        int r0 = Math.max(0, (int) (tileY(qMaxLa, detail.base) - detail.top));
        int r1 = Math.min((int) detail.h - 1, (int) (tileY(qMinLa, detail.base) - detail.top));
        long deadline = System.currentTimeMillis() + 900L;
        HashSet<Long> seen = new HashSet<Long>();
        int blocks = 0;
        int[] tagIds = new int[16];
        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) {
                if (System.currentTimeMillis() > deadline || blocks++ > 160) return out;
                long bn = (long) row * detail.w + col;
                byte[] data;
                try { data = blockRaw(detail, bn); } catch (Throwable e) { continue; }
                if (data == null) continue;
                double tLat = tileLat(detail.top + row, detail.base);
                double tLon = (double) (detail.left + col) / (1L << detail.base) * 360 - 180;
                Buf b = new Buf(data);
                try {
                    if (debug) b.str(32);
                    int rows = detail.zMax - detail.zMin + 1;
                    int totalPois = 0;
                    for (int z = 0; z < rows; z++) { totalPois += b.uvar(); b.uvar(); }
                    int firstWay = b.uvar() + b.p;
                    if (firstWay > b.d.length) continue;
                    for (int i = 0; i < totalPois; i++) {
                        if (debug) b.str(32);
                        double la = tLat + b.svar() / 1e6, lo = tLon + b.svar() / 1e6;
                        int sp = b.byte8();
                        int nt = sp & 0x0f;
                        for (int t = 0; t < nt; t++) {
                            int id = b.uvar();
                            if (id < 0 || id >= poiK.length) { nt = -1; break; }
                            tagIds[t] = id;
                        }
                        if (nt < 0) break;
                        boolean isCam = false;
                        short camSp = 0;
                        for (int t = 0; t < nt; t++) {
                            int id = tagIds[t];
                            String k = poiK[id], v = poiV[id];
                            String val = readTagVar(b, v, k, "maxspeed".equals(k));
                            if (("highway".equals(k) && "speed_camera".equals(v))
                                    || ("enforcement".equals(k) && "maxspeed".equals(v))) {
                                isCam = true;
                            } else if ("maxspeed".equals(k)) {
                                camSp = parseMaxSpeedKmh(val);
                            }
                        }
                        int fb = b.byte8();
                        if ((fb & 0x80) != 0) b.str();
                        if ((fb & 0x40) != 0) b.str();
                        if ((fb & 0x20) != 0) b.svar();
                        if (isCam && la >= qMinLa && la <= qMaxLa && lo >= qMinLo && lo <= qMaxLo) {
                            long key = (Math.round(la * 1e5) << 32) | (Math.round(lo * 1e5) & 0xFFFFFFFFL);
                            if (seen.add(key)) out.add(new double[]{la, lo, camSp});
                        }
                    }
                } catch (RuntimeException ignored) { }
            }
        }
        return out;
    }

    private HashMap<Integer, Double> topCandidates(CorridorGraph g, double la, double lo, int k) {
        ArrayList<double[]> cands = new ArrayList<double[]>();
        for (int i = 0; i < g.nNodes; i++) {
            if (g.adj[i] == null || g.adj[i].isEmpty()) continue;
            double d = distM(la, lo, g.latArr[i], g.lonArr[i]);
            if (d > 450) continue;
            double pen = g.nodeRank[i] < 3 ? 60 : 0;
            cands.add(new double[]{d + pen, i, d});
        }
        Collections.sort(cands, new Comparator<double[]>() {
            public int compare(double[] a, double[] b) { return Double.compare(a[0], b[0]); }
        });
        HashMap<Integer, Double> out = new HashMap<Integer, Double>();
        for (int i = 0; i < Math.min(k, cands.size()); i++) out.put((int) cands.get(i)[1], cands.get(i)[2]);
        return out;
    }
}
