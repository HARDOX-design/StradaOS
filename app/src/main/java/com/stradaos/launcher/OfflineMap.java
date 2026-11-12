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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** Mapas v5: algunas etiquetas guardan su valor dentro del elemento («%b», «%i»…); hay que saltarlo. */
    private static void skipVarValue(Buf b, String v, String k) {
        if (v.length() != 2 || v.charAt(0) != '%') return;
        switch (v.charAt(1)) {
            case 'b': b.p += 1; break;
            case 'h': b.p += 2; break;
            case 'i': case 'f': b.p += 4; break;
            case 's': b.str(); break;
            default: break;
        }
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
}
