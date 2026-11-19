package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Mapa en vivo: teselas de OpenStreetMap centradas en tu posición GPS (en modo oscuro se oscurecen).
 * Guarda las teselas en el equipo para verlas sin internet por donde ya pasaste, y si hay un mapa sin conexión
 * (archivo .map, ver OfflineMap) lo dibuja desde el archivo.
 *
 * Movimiento: el GPS da una posición por segundo; entre una y otra la posición y el rumbo se interpolan y el mapa
 * se redibuja a ~30 cuadros por segundo, para que la flecha avance suave y no a saltos.
 */
public class LiveMapView extends View {
    // proveedores en orden: si uno falla en esta pantalla (TLS antiguo, bloqueo), se prueba el siguiente
    // {z} {x} {y}. CARTO ya exige clave (devolvía "API KEY REQUIRED"), así que se usan fuentes sin clave.
    static final String ESRI = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/";
    // Las fuentes por http redirigen a https (HTTP 301), y los CDN grandes (Esri, OSM) exigen cifrados
    // que Android 4.x no tiene. Los servidores comunitarios (Alemania, Francia, OpenTopoMap) suelen
    // aceptar cifrados más antiguos. El usuario también puede poner su propia fuente.
    static final String[] URLS = {
        "",                                                          // 0: fuente personalizada (ajustes)
        "https://tile.openstreetmap.de/{z}/{x}/{y}.png",
        "https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png",
        "https://tile-a.openstreetmap.fr/hot/{z}/{x}/{y}.png",
        "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
        ESRI + "World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}",
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    };
    static final String[] LABELS = {null, null, null, null, null, ESRI + "World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}", null};
    static final boolean[] DARKEN = {true, true, true, true, true, false, true};
    static final String[] NAMES = {"Personalizada", "OSM Alemania", "OSM Francia", "OSM Humanitario", "OpenTopoMap", "Esri gris oscuro", "OpenStreetMap"};
    static final int[] MAXZ = {18, 18, 18, 18, 17, 16, 18};
    static final String[] ERR = new String[7];
    static int rounds;      // vueltas completas sin éxito
    static int provider = 1;             // índice del que funciona
    static String lastError = "—";
    static int okCount;

    // capas de una tesela: 0 mapa, 1 nombres (Esri), 2 mapa sin conexión (archivo), 3 tráfico en vivo
    private static final int L_BASE = 0, L_LABELS = 1, L_FILE = 2, L_TRAFFIC = 3;
    private static final int FRAME_MS = 33;   // ~30 cuadros por segundo mientras se mueve
    private static final long TRAFFIC_TTL_MS = 240000L; // refresco cada 4 minutos
    static volatile int trafficGen;
    public static void clearTrafficCache() { trafficGen++; mem.evictAll(); }

    // ── mapa sin conexión (archivo .map), compartido entre vistas ──
    static OfflineMap fileMap;
    static String fileErr;

    /** Abre (o cierra) el mapa sin conexión según el ajuste «map_file». */
    static void loadFile(Context c) {
        String p = c.getSharedPreferences("launcher", 0).getString("map_file", "");
        if (p.length() == 0) {
            if (fileMap != null) { fileMap.close(); mem.evictAll(); }
            fileMap = null; fileErr = null;
            return;
        }
        if (fileMap != null && fileMap.file.getAbsolutePath().equals(p)) return;
        if (fileMap != null) fileMap.close();
        mem.evictAll();   // otro archivo: lo ya dibujado no sirve
        fileMap = null;
        try {
            fileMap = OfflineMap.open(new File(p));
            fileErr = null;
        } catch (Throwable e) {
            fileErr = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    private final float s;
    private final float radius;
    private final int fileTile;                 // tamaño en px de las teselas dibujadas desde el archivo
    private final File dir;
    private final Handler ui = new Handler(Looper.getMainLooper());
    // compartida entre vistas: al reconstruir el inicio (cambiar un ajuste) no se vuelve a dibujar todo
    private static final LruCache<Long, Bitmap> mem = new LruCache<Long, Bitmap>(20 * 1024 * 1024) {   // ~20 MB
        @Override protected int sizeOf(Long k, Bitmap b) { return b.getRowBytes() * b.getHeight(); }
    };
    private final HashSet<Long> pending = new HashSet<Long>();
    private final HashSet<Long> failed = new HashSet<Long>();
    private final LinkedList<Long> queue = new LinkedList<Long>();
    private int workers;

    // posición: destino (última lectura del GPS) y la que se está mostrando, que avanza hacia él
    private double tLat = Double.NaN, tLon = Double.NaN;
    private float tBr = Float.NaN;
    private double aLat, aLon, dLat = Double.NaN, dLon = Double.NaN;
    private float aBr, dBr = Float.NaN;
    private long aStart, aDur = 1, lastFixAt;
    private int zoom = 16;
    private int netErrors;

    private final Paint tile = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint grid = new Paint();
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeOut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeIn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeBack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint destPin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path corners = new Path();
    // Tema Aurora (2.24): el fondo de la pantalla detrás del mapa. Con él se tapan las esquinas (en vez del color
    // liso, que en Aurora se veía como puntas) y las teselas se dibujan un poco transparentes encima. Barato: es un
    // rectángulo con un BitmapShader del fondo ya pintado; no hay capas por software ni clipPath.
    private Bitmap backdrop;
    private View backHolder;
    private final Paint back = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final android.graphics.Matrix backM = new android.graphics.Matrix();
    private final int[] locA = new int[2], locB = new int[2];

    // Tema Faro (2.25): el mapa en el tono del tema. Mismas teselas que Cobalto, con menos color (gris azulado en
    // oscuro, suave en claro). Es un filtro de color al dibujar (la GPU lo hace gratis); el tráfico conserva sus colores.
    private final Paint tileTone = new Paint(Paint.FILTER_BITMAP_FLAG);
    private boolean toned;
    void setTone(android.graphics.ColorFilter f) {
        toned = f != null;
        tileTone.setColorFilter(f);
        invalidate();
    }

    // Tema Faro (2.25): dónde va la atribución de OSM (px; alineada a la izquierda), si no se navega.
    private float attrX = -1, attrY = -1;
    void setAttribution(float x, float y) { attrX = x; attrY = y; invalidate(); }

    // Tema Cobalto: el mapa se difumina hacia un borde (1 = derecha, 2 = abajo) hasta quedar en el fondo.
    private int fadeEdge;
    private float fadeLen;

    void setFade(int edge, float len) {
        fadeEdge = edge;
        fadeLen = len;
        invalidate();
    }

    /** Franjas del fondo con opacidad creciente (sin capas ni ComposeShader: barato también en Android 4.1). */
    private void drawFade(Canvas c, float w, float h) {
        if (fadeEdge == 0 || fadeLen <= 0) return;
        int n = 32;
        float len = Math.min(fadeLen, fadeEdge == 1 ? w : h), step = len / n, e0 = (fadeEdge == 1 ? w : h) - len;
        for (int i = 0; i < n; i++) {
            float t = (i + 1f) / n;
            back.setAlpha(Math.round(255 * t * t * (3 - 2 * t)));   // curva suave
            // bordes en píxeles enteros y sin pisarse: si se pisan quedan rayitas más oscuras
            float a0 = Math.round(e0 + i * step), a1 = i == n - 1 ? (fadeEdge == 1 ? w : h) : Math.round(e0 + (i + 1) * step);
            if (fadeEdge == 1) c.drawRect(a0, 0, a1, h, back);
            else c.drawRect(0, a0, w, a1, back);
        }
        back.setAlpha(255);
    }

    /** holder: la vista que tiene el fondo estirado a su tamaño. alpha: opacidad de las teselas (1 = como siempre). */
    void setBackdrop(Bitmap bmp, View holder, float alpha) {
        backdrop = bmp;
        backHolder = holder;
        if (bmp != null) back.setShader(new android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        else back.setShader(null);
        tile.setAlpha(bmp != null ? Math.round(255 * Math.max(0.5f, Math.min(1f, alpha))) : 255);
        invalidate();
    }

    /** Ubica el fondo según dónde está el mapa en la ventana (también mientras se anima). */
    private boolean syncBackdrop() {
        if (backdrop == null || backHolder == null || backHolder.getWidth() <= 0) return false;
        getLocationInWindow(locA);
        backHolder.getLocationInWindow(locB);
        backM.setScale(backHolder.getWidth() / (float) backdrop.getWidth(), backHolder.getHeight() / (float) backdrop.getHeight());
        backM.postTranslate(locB[0] - locA[0], locB[1] - locA[1]);
        back.getShader().setLocalMatrix(backM);
        return true;
    }
    private final Path tri = new Path();
    private final Path routePath = new Path();
    private final Rect dst = new Rect();

    public LiveMapView(Context c, float scale, float radiusPx) {
        super(c);
        s = scale;
        radius = radiusPx;
        fileTile = Math.round(256 * Math.max(1f, Math.min(2f, scale)));   // nítidas también en pantallas densas
        dir = new File(c.getCacheDir(), "tiles_v3");   // v3: se guarda la tesela original; el estilo se aplica al mostrarla
        dir.mkdirs();
        deleteOld(new File(c.getCacheDir(), "tiles"));
        deleteOld(new File(c.getCacheDir(), "tiles_v2"));
        bright = c.getSharedPreferences("launcher", 0).getInt("map_bright", 1);
        grid.setColor(Main.LIGHT ? 0xFFD5D9E0 : 0xFF22252C);
        grid.setStrokeWidth(Math.max(1, s));
        text.setColor(Main.LIGHT ? 0x99000000 : 0x99FFFFFF);
        text.setTextSize(10 * s);
        corner.setColor(Main.BG);
        routeOut.setStyle(Paint.Style.STROKE);
        routeOut.setStrokeCap(Paint.Cap.ROUND);
        routeOut.setStrokeJoin(Paint.Join.ROUND);
        routeOut.setStrokeWidth(11 * s);
        routeIn.setStyle(Paint.Style.STROKE);
        routeIn.setStrokeCap(Paint.Cap.ROUND);
        routeIn.setStrokeJoin(Paint.Join.ROUND);
        routeIn.setStrokeWidth(6 * s);
        routeBack.setStyle(Paint.Style.STROKE);
        routeBack.setStrokeCap(Paint.Cap.ROUND);
        routeBack.setStrokeWidth(3.5f * s);
        routeBack.setPathEffect(new android.graphics.DashPathEffect(new float[]{10 * s, 8 * s}, 0));
        // Sin capa por software: el mapa se dibuja con la GPU (mucho más fluido). Las esquinas redondeadas no se
        // recortan (clipPath no funciona con GPU antes de Android 4.3): se tapan con el color del fondo.
        Net.init(c);
        sp = c.getSharedPreferences("launcher", 0);
        String custom = sp.getString("map_url", "").trim();
        URLS[0] = custom;
        if (custom.length() > 0) provider = 0;
        else if (okCount == 0) {
            // empezar por la fuente que funcionó la última vez: así, sin internet, se encuentra lo que ya está en caché
            int saved = sp.getInt("map_provider", 0);
            if (saved > 0 && saved < URLS.length) provider = saved;
        }
        loadFile(c);
    }

    private final android.content.SharedPreferences sp;

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        corners.reset();
        corners.setFillType(Path.FillType.EVEN_ODD);
        corners.addRect(-2, -2, w + 2, h + 2, Path.Direction.CW);
        corners.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
    }

    private File tileFile(int pv, int layer, int z, int x, int y) {
        return new File(dir, pv + "_" + layer + "_" + z + "_" + x + "_" + y + (Main.LIGHT && pv == 5 ? "L" : "") + ".png");
    }

    /** Clave de una tesela: capa, estilo (claro/oscuro y brillo), fuente, zoom, x, y. */
    private long key(int layer, int pv, int z, int x, int y) {
        int style = (Main.LIGHT ? 4 : 0) | (bright & 3);
        return ((long) layer << 61) | ((long) style << 58) | ((long) pv << 55) | ((long) z << 48) | ((long) x << 24) | (long) y;
    }

    /** Tesela guardada de cualquier fuente (sin internet vale cualquiera). Devuelve {bitmap, fuente} o null. */
    private Object[] cachedAny(int z, int x, int y) {
        for (int q = 1; q < URLS.length; q++) {
            File g = tileFile(q, 0, z, x, y);
            if (!g.exists()) continue;
            Bitmap b = BitmapFactory.decodeFile(g.getAbsolutePath());
            if (b != null) return new Object[]{b, q};
        }
        return null;
    }

    public boolean hasFix() { return !Double.isNaN(tLat); }
    private final int bright;

    /** ¿La posición actual se dibuja desde el mapa sin conexión? */
    public boolean usingFile() {
        OfflineMap f = fileMap;
        return f != null && !Double.isNaN(tLat) && f.contains(tLat, tLon);
    }

    /** Mapa claro → estilo oscuro (invierte y gira el tono 180°, como el "modo noche" de muchos mapas). */
    /** Modo oscuro para mapas claros: invierte, gira el tono 180° (el agua sigue azul, los parques verdes,
     *  el texto queda blanco) y aclara según el nivel de brillo (0 bajo, 1 medio, 2 alto). */
    static Bitmap darken(Bitmap src, int level) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.ColorMatrix m = new android.graphics.ColorMatrix(new float[]{
                -1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0});
        m.postConcat(new android.graphics.ColorMatrix(new float[]{
                -0.574f, 1.43f, 0.144f, 0, 0,
                0.426f, 0.43f, 0.144f, 0, 0,
                0.426f, 1.43f, -0.856f, 0, 0,
                0, 0, 0, 1, 0}));
        float lift = level <= 0 ? 14 : level == 1 ? 40 : 64, k = level <= 0 ? 0.85f : level == 1 ? 0.80f : 0.78f;
        m.postConcat(new android.graphics.ColorMatrix(new float[]{
                k, 0, 0, 0, lift, 0, k, 0, 0, lift, 0, 0, k * 1.05f, 0, lift + 4, 0, 0, 0, 1, 0}));
        Paint pt = new Paint(Paint.FILTER_BITMAP_FLAG);
        pt.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
        new Canvas(out).drawBitmap(src, 0, 0, pt);
        src.recycle();
        return out;
    }

    private static void deleteOld(final File d) {
        if (!d.exists()) return;
        new Thread(new Runnable() { public void run() {
            File[] fs = d.listFiles();
            if (fs != null) for (File f : fs) f.delete();
            d.delete();
        }}).start();
    }

    public String diag() {
        int cached = 0;
        File[] fs = dir.listFiles();
        if (fs != null) cached = fs.length;
        OfflineMap f = fileMap;
        String file = f != null
                ? f.describe() + (usingFile() ? " · en uso" : " · fuera del área del archivo")
                    + (f.rendered > 0 ? "\nTeselas dibujadas desde el archivo: " + f.rendered + " · " + (f.renderMs / f.rendered) + " ms cada una" : "")
                : fileErr != null ? "error: " + fileErr : "ninguno";
        return "Posición: " + (Double.isNaN(tLat) ? "sin GPS" : String.format(java.util.Locale.US, "%.5f, %.5f", tLat, tLon))
                + "\nZoom: " + zoom + "\nMapa sin conexión: " + file
                + "\nProveedor: " + NAMES[provider]
                + (okCount == 0 && rounds > 0 ? " · ninguna fuente respondió (" + rounds + " vueltas)" : "")
                + "\nTráfico en vivo: " + (trafficTpl() != null ? "activo" : "inactivo")
                + "\nTeselas descargadas: " + okCount + " · en caché: " + cached
                + "\nErrores seguidos: " + netErrors + "\nÚltimo error: " + lastError
                + "\n\nFuentes probadas:" + sources()
                + "\n" + Net.initInfo + "\nAndroid " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")";
    }
    static volatile boolean netOffline;
    public boolean offline() { return netErrors >= 3; }
    static boolean isOffline() { return netOffline; }

    private static String sources() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < URLS.length; i++) {
            if (URLS[i].length() == 0) continue;
            b.append("\n• ").append(NAMES[i]).append(": ").append(ERR[i] == null ? "sin probar" : ERR[i]);
        }
        return b.toString();
    }

    private static String shortErr(Throwable e) {
        String m = String.valueOf(e.getMessage());
        if (m.contains("handshake") || m.contains("SSL")) return "cifrado no compatible";
        if (m.startsWith("HTTP")) return m;
        if (e instanceof java.net.UnknownHostException) return "sin internet / DNS";
        if (e instanceof java.net.SocketTimeoutException) return "tiempo agotado";
        if (e instanceof java.security.cert.CertificateException || m.contains("Certificate") || m.contains("Trust")) return "certificado";
        return e.getClass().getSimpleName();
    }

    public void setPosition(double la, double lo, float br, float speedKmh) {
        long now = SystemClock.uptimeMillis();
        step(now);
        boolean snap = Double.isNaN(dLat) || now - lastFixAt > 5000
                || Math.abs(la - dLat) > 0.003 || Math.abs(lo - dLon) > 0.003;   // ~300 m: salto, no movimiento
        tLat = la;
        tLon = lo;
        if (!Float.isNaN(br)) tBr = br;
        if (snap) {
            dLat = aLat = la; dLon = aLon = lo; dBr = aBr = tBr;
            aDur = 1;
        } else {
            aLat = dLat; aLon = dLon; aBr = Float.isNaN(dBr) ? tBr : dBr;
            // la animación dura lo que tardó en llegar esta lectura: así la flecha llega justo cuando llega la siguiente
            aDur = Math.max(300, Math.min(1500, now - lastFixAt));
        }
        aStart = now;
        lastFixAt = now;
        if (!userZoom) zoom = speedKmh > 70 ? 15 : speedKmh > 35 ? 16 : 17;
        trimDisk();
        invalidate();
    }

    /** Calcula la posición que se muestra ahora. Devuelve true si todavía va en camino. */
    private boolean step(long now) {
        if (Double.isNaN(tLat)) return false;
        float f = Math.min(1f, Math.max(0f, (now - aStart) / (float) aDur));
        dLat = aLat + (tLat - aLat) * f;
        dLon = aLon + (tLon - aLon) * f;
        if (!Float.isNaN(tBr)) {
            if (Float.isNaN(aBr)) aBr = tBr;
            float d = ((tBr - aBr) % 360 + 540) % 360 - 180;     // giro más corto
            dBr = aBr + d * f;
        }
        return f < 1f;
    }

    private boolean userZoom;
    /** true: el mapa gira según la dirección del carro (arriba = hacia donde vas). false: norte arriba. */
    static boolean headingUp;

    /** Toque: cambia el nivel de zoom (14→17). */
    public void cycleZoom() {
        userZoom = true;
        zoom = zoom >= 17 ? 14 : zoom + 1;
        invalidate();
    }

    /** Faro: acercar (+1) o alejar (-1) con los botones del mapa. */
    void zoomBy(int d) { userZoom = true; zoom = Math.max(11, Math.min(18, zoom + d)); invalidate(); }

    /** Faro: botón de centrar: vuelve al zoom automático según la velocidad. */
    void autoZoom() { userZoom = false; zoom = 16; invalidate(); }

    /** Pruebas: fija el zoom (14–18). */
    void setZoom(int z) { userZoom = true; zoom = Math.max(10, Math.min(18, z)); invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        boolean bd = syncBackdrop();
        if (bd) c.drawRect(0, 0, w, h, back);
        else c.drawColor(Main.LIGHT ? 0xFFE8EBF0 : 0xFF15171C);
        boolean moving = step(SystemClock.uptimeMillis());

        if (!Double.isNaN(dLat)) {
            OfflineMap fm = fileMap;
            boolean fileHere = fm != null && fm.contains(dLat, dLon);
            if (!fileHere && zoom > MAXZ[provider]) zoom = MAXZ[provider];
            int n = 1 << zoom;
            double fx = (dLon + 180.0) / 360.0 * n;
            double latR = Math.toRadians(dLat);
            double fy = (1.0 - Math.log(Math.tan(latR) + 1.0 / Math.cos(latR)) / Math.PI) / 2.0 * n;
            float ts = 256 * s;                                    // tamaño de tesela en pantalla
            boolean rot = headingUp && !Float.isNaN(dBr);
            float cx = w / 2f, cy = Nav.active != null ? (rot ? h * 0.54f : h * 0.50f) : h * 0.42f;
            int tx0 = (int) Math.floor(fx - cx / ts) - 1, tx1 = (int) Math.floor(fx + (w - cx) / ts) + 1;
            int ty0 = (int) Math.floor(fy - cy / ts) - 1, ty1 = (int) Math.floor(fy + (h - cy) / ts) + 1;
            if (rot) {
                // al girar hacen falta teselas en un círculo alrededor de la posición (hasta la esquina más lejana)
                float rr = (float) Math.sqrt(Math.max(cx, w - cx) * Math.max(cx, w - cx) + Math.max(cy, h - cy) * Math.max(cy, h - cy)) / ts;
                tx0 = (int) Math.floor(fx - rr); tx1 = (int) Math.floor(fx + rr);
                ty0 = (int) Math.floor(fy - rr); ty1 = (int) Math.floor(fy + rr);
                c.save();
                c.rotate(-dBr, cx, cy);
            }
            boolean anyFile = false;
            String trTpl = trafficTpl();
            int trPv = trTpl != null ? trafficEpochPv() : 0;
            for (int tx = tx0; tx <= tx1; tx++) {
                for (int ty = ty0; ty <= ty1; ty++) {
                    if (ty < 0 || ty >= n) continue;
                    int wx = ((tx % n) + n) % n;
                    float left = cx + (float) ((tx - fx) * ts), top = cy + (float) ((ty - fy) * ts);
                    dst.set(Math.round(left), Math.round(top), Math.round(left + ts) + 1, Math.round(top + ts) + 1);
                    boolean fromFile = fm != null && fm.coversFast(zoom, wx, ty);
                    Bitmap b = get(zoom, wx, ty, fromFile ? L_FILE : L_BASE);
                    if (b != null) {
                        tile.setFilterBitmap(rot || b.getWidth() != dst.width());
                        Paint tp = tile;
                        if (toned) { tileTone.setAlpha(tile.getAlpha()); tileTone.setFilterBitmap(tile.isFilterBitmap()); tp = tileTone; }
                        c.drawBitmap(b, null, dst, tp);
                        if (trTpl != null && zoom >= 11) {
                            Bitmap tr = getTraffic(zoom, wx, ty, trPv);
                            if (tr != null) c.drawBitmap(tr, null, dst, tile);
                        }
                        if (fromFile) anyFile = true;
                        else if (LABELS[provider] != null) {
                            Bitmap l = get(zoom, wx, ty, L_LABELS);
                            if (l != null) c.drawBitmap(l, null, dst, toned ? tileTone : tile);
                        }
                    } else {
                        c.drawLine(dst.left, dst.top, dst.right, dst.top, grid);
                        c.drawLine(dst.left, dst.top, dst.left, dst.bottom, grid);
                    }
                }
            }
            drawRoute(c, fx, fy, cx, cy, ts, n);
            if (rot) c.restore();
            drawMarker(c, cx, cy, rot);
            if (bd) drawFade(c, w, h);
            String attr = anyFile ? I18n.t("© colaboradores de OpenStreetMap") + " · mapsforge"
                    : provider == 5 ? "© Esri, HERE, Garmin, © OpenStreetMap" : provider == 4 ? "© OpenStreetMap, SRTM · © OpenTopoMap" : I18n.t("© colaboradores de OpenStreetMap");
            float ax = w - text.measureText(attr) - 10 * s, ay = (Nav.active != null ? 96 : 14) * s;
            if (attrY >= 0 && Nav.active == null) { ax = attrX; ay = attrY; }
            if (bd && fadeEdge == 1 && fadeLen > 0) {
                // Cobalto: antes del difuminado (si no, queda detrás de la tarjeta del reproductor) y un poco más
                // arriba para no rozar los botones de la esquina
                ax = Math.max(12 * s, w - fadeLen * 0.72f - text.measureText(attr));
                if (Nav.active == null) ay = 13 * s;
            }
            c.drawText(attr, ax, ay, text);
        }
        if (bd) c.drawPath(corners, back);
        else {
            corner.setColor(Main.BG);
            c.drawPath(corners, corner);
        }
        if (moving) postInvalidateDelayed(FRAME_MS);
    }

    private void drawMarker(Canvas c, float x, float y, boolean rotated) {
        halo.setColor((Main.RED & 0x00FFFFFF) | 0x40000000);
        c.drawCircle(x, y, 22 * s, halo);
        c.save();
        c.rotate(rotated || Float.isNaN(dBr) ? 0 : dBr, x, y);
        tri.reset();
        tri.moveTo(x, y - 14 * s);
        tri.lineTo(x + 10 * s, y + 11 * s);
        tri.lineTo(x, y + 5 * s);
        tri.lineTo(x - 10 * s, y + 11 * s);
        tri.close();
        arrow.setStyle(Paint.Style.FILL);
        arrow.setColor(Main.RED);
        c.drawPath(tri, arrow);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeWidth(2 * s);
        arrow.setColor(0xFFFFFFFF);
        c.drawPath(tri, arrow);
        c.restore();
    }

    private float mapX(double lon, double fx, float cx, float ts, int n) {
        double dx = (lon + 180.0) / 360.0 * n - fx;
        if (dx > n * 0.5) dx -= n;
        else if (dx < -n * 0.5) dx += n;
        return cx + (float) (dx * ts);
    }

    private float mapY(double lat, double fy, float cy, float ts, int n) {
        double cLat = Math.max(-85.0511, Math.min(85.0511, lat));
        double lr = Math.toRadians(cLat);
        double py = (1.0 - Math.log(Math.tan(lr) + 1.0 / Math.cos(lr)) / Math.PI) / 2.0 * n;
        return cy + (float) ((py - fy) * ts);
    }

    private void drawRoute(Canvas c, double fx, double fy, float cx, float cy, float ts, int n) {
        java.util.List<Nav.Route> alts = Nav.altRoutes;
        if (alts != null && alts.size() > 1) {
            routeBack.setColor(Main.LIGHT ? 0x554A5568 : 0x779AA2B1);
            for (int ai = 0; ai < alts.size(); ai++) {
                Nav.Route ar = alts.get(ai);
                if (ar == null || ar == Nav.active || ar.pts == null || ar.pts.length < 4) continue;
                int an = ar.pts.length / 2;
                routePath.reset();
                float lx = mapX(ar.pts[1], fx, cx, ts, n), ly = mapY(ar.pts[0], fy, cy, ts, n);
                routePath.moveTo(lx, ly);
                for (int i = 1; i < an; i++) {
                    float sx = mapX(ar.pts[i * 2 + 1], fx, cx, ts, n);
                    float sy = mapY(ar.pts[i * 2], fy, cy, ts, n);
                    if (i == an - 1 || Math.abs(sx - lx) >= 2f || Math.abs(sy - ly) >= 2f) {
                        routePath.lineTo(sx, sy);
                        lx = sx;
                        ly = sy;
                    }
                }
                c.drawPath(routePath, routeBack);
            }
        }
        Nav.Route r = Nav.active;
        if (r == null || r.pts == null || r.pts.length < 4) return;
        int nPts = r.pts.length / 2;
        int start = Math.max(0, Math.min(nPts - 1, Nav.curPtIdx));
        boolean offRoute = Nav.offlineOffRoute;
        float startX = offRoute ? mapX(r.pts[start * 2 + 1], fx, cx, ts, n) : cx;
        float startY = offRoute ? mapY(r.pts[start * 2], fy, cy, ts, n) : cy;
        if (offRoute) {
            routePath.reset();
            routePath.moveTo(cx, cy);
            routePath.lineTo(startX, startY);
            routeBack.setColor(Main.LIGHT ? 0xAA000000 : 0xCCFFFFFF);
            c.drawPath(routePath, routeBack);
        }
        routePath.reset();
        routePath.moveTo(startX, startY);
        float lastX = startX, lastY = startY;
        // Pantallas lentas (Picanto, Android 4.2): solo se dibuja lo que cae cerca de la vista, y se saltan puntos a menos
        // de 3 px. Antes se armaba la ruta entera en cada cuadro (~30 por segundo mientras el mapa se mueve).
        float mg = getWidth() + getHeight();
        float minX = -mg, minY = -mg, maxX = getWidth() + mg, maxY = getHeight() + mg;
        boolean penUp = false;
        for (int i = start + 1; i < nPts; i++) {
            float sx = mapX(r.pts[i * 2 + 1], fx, cx, ts, n);
            float sy = mapY(r.pts[i * 2], fy, cy, ts, n);
            boolean out = sx < minX || sx > maxX || sy < minY || sy > maxY;
            if (out) {
                // fuera de la vista: no se dibuja, pero se recuerda el punto para retomar la línea si la ruta vuelve
                lastX = sx; lastY = sy; penUp = true;
                continue;
            }
            if (penUp) { routePath.moveTo(lastX, lastY); penUp = false; }
            if (i == nPts - 1 || Math.abs(sx - lastX) >= 3f || Math.abs(sy - lastY) >= 3f) {
                routePath.lineTo(sx, sy);
                lastX = sx;
                lastY = sy;
            }
        }
        routeOut.setColor(Main.LIGHT ? 0x66000000 : 0xCC0D0E11);
        routeIn.setColor(Main.RED);
        c.drawPath(routePath, routeOut);
        c.drawPath(routePath, routeIn);

        // paradas intermedias
        if (!r.waypoints.isEmpty()) {
            destPin.setStyle(Paint.Style.FILL);
            for (int wi = 0; wi < r.waypoints.size(); wi++) {
                Nav.Place wp = r.waypoints.get(wi);
                float wx = mapX(wp.lon, fx, cx, ts, n);
                float wy = mapY(wp.lat, fy, cy, ts, n);
                destPin.setColor(0xFFFFFFFF);
                c.drawCircle(wx, wy, 7.5f * s, destPin);
                destPin.setColor(0xFFF5A623);
                c.drawCircle(wx, wy, 4.8f * s, destPin);
            }
        }

        // radares fijos en la ruta
        if (!r.cameras.isEmpty()) {
            destPin.setStyle(Paint.Style.FILL);
            for (int ci = 0; ci < r.cameras.size(); ci++) {
                Nav.Camera cam = r.cameras.get(ci);
                float kx = mapX(cam.lon, fx, cx, ts, n);
                float ky = mapY(cam.lat, fy, cy, ts, n);
                destPin.setColor(0xFFFFFFFF);
                c.drawCircle(kx, ky, 6f * s, destPin);
                destPin.setColor(0xFFE63946);
                c.drawCircle(kx, ky, 3.8f * s, destPin);
            }
        }

        // incidentes de tráfico reportados
        if (!r.incidents.isEmpty()) {
            destPin.setStyle(Paint.Style.FILL);
            for (int ii = 0; ii < r.incidents.size(); ii++) {
                Nav.Incident inc = r.incidents.get(ii);
                float ix = mapX(inc.lon, fx, cx, ts, n);
                float iy = mapY(inc.lat, fy, cy, ts, n);
                destPin.setColor(0xFF1A1D24);
                c.drawCircle(ix, iy, 7f * s, destPin);
                destPin.setColor(inc.category == 8 || inc.category == 1 ? 0xFFE63946 : 0xFFF5A623);
                c.drawCircle(ix, iy, 4.5f * s, destPin);
            }
        }

        // destino
        float dxS = mapX(r.destLon, fx, cx, ts, n);
        float dyS = mapY(r.destLat, fy, cy, ts, n);
        destPin.setStyle(Paint.Style.FILL);
        destPin.setColor(0xFFFFFFFF);
        c.drawCircle(dxS, dyS, 10 * s, destPin);
        destPin.setColor(Main.RED);
        c.drawCircle(dxS, dyS, 6.5f * s, destPin);
    }

    // ── teselas ──

    private Bitmap get(int z, int x, int y, int layer) {
        return getKeyed(key(layer, layer == L_FILE ? 0 : provider, z, x, y));
    }

    private Bitmap getTraffic(int z, int x, int y, int tpv) {
        return getKeyed(key(L_TRAFFIC, tpv, z, x, y));
    }

    private Bitmap getKeyed(long k) {
        Bitmap b = mem.get(k);
        if (b != null) return b;
        synchronized (queue) {
            if (!pending.contains(k) && !failed.contains(k)) {
                pending.add(k);
                queue.addFirst(k);
                if (workers < 2) { workers++; new Thread(worker).start(); }
            }
        }
        return null;
    }

    private int trafficEpochPv() {
        return (int) ((trafficGen + (System.currentTimeMillis() / TRAFFIC_TTL_MS)) & 7L);
    }

    public String trafficTpl() {
        if (sp == null || !sp.getBoolean("map_traffic", false)) return null;
        String custom = sp.getString("traffic_url", "").trim();
        if (custom.length() > 0) return custom;
        String key = sp.getString("tomtom_key", "").trim();
        if (key.length() == 0) return null;
        try {
            String ek = java.net.URLEncoder.encode(key, "UTF-8");
            return "https://api.tomtom.com/traffic/map/4/tile/flow/"
                    + (Main.LIGHT ? "relative0" : "relative0-dark")
                    + "/{z}/{x}/{y}.png?tileSize=256&key=" + ek;
        } catch (Throwable e) {
            return null;
        }
    }

    private Bitmap fetchTrafficTile(int z, int x, int y) {
        String tpl = trafficTpl();
        if (tpl == null) return null;
        int tag = tpl.hashCode() & 0xFFFF;
        File f = new File(dir, "tr_" + (Main.LIGHT ? "l" : "d") + "_" + tag + "_" + z + "_" + x + "_" + y + ".png");
        long now = System.currentTimeMillis();
        if (f.exists() && Math.abs(now - f.lastModified()) < TRAFFIC_TTL_MS) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) return b;
        }
        if (offline()) return null;
        try {
            String url = tpl.replace("{z}", String.valueOf(z)).replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
            byte[] data = Net.get(url, 6000);
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (b != null) {
                FileOutputStream os = new FileOutputStream(f);
                os.write(data);
                os.close();
                return b;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private final Runnable worker = new Runnable() {
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            while (true) {
                long k;
                synchronized (queue) {
                    if (queue.isEmpty()) { workers--; return; }
                    k = queue.removeFirst();
                }
                Bitmap b = null;
                int layer = (int) (k >>> 61) & 3, pv = (int) (k >>> 55) & 7, z = (int) (k >>> 48) & 31;
                int x = (int) (k >>> 24) & 0xFFFFFF, y = (int) k & 0xFFFFFF;
                try {
                    if (layer == L_FILE) {
                        OfflineMap fm = fileMap;
                        if (fm != null) b = fm.render(z, x, y, fileTile, Main.LIGHT, bright);
                    } else if (layer == L_TRAFFIC) {
                        b = fetchTrafficTile(z, x, y);
                    } else {
                        File f = tileFile(pv, layer, z, x, y);
                        if (f.exists()) b = BitmapFactory.decodeFile(f.getAbsolutePath());
                        // sin internet: no esperar a que falle la descarga si ya hay una tesela guardada de otra fuente
                        if (b == null && layer == 0 && offline()) {
                            Object[] any = cachedAny(z, x, y);
                            if (any != null) { b = (Bitmap) any[0]; pv = (Integer) any[1]; }
                        }
                        if (b == null) {
                            String tpl = layer == 1 ? LABELS[pv] : URLS[pv];
                            if (Main.LIGHT) tpl = tpl.replace("Dark_Gray", "Light_Gray");
                            String url = tpl.replace("{z}", String.valueOf(z)).replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
                            byte[] data;
                            try {
                                data = Net.get(url, 8000);
                            } catch (Throwable ex) {
                                lastError = "[" + NAMES[pv] + "] " + ex;
                                if (layer == 0) ERR[pv] = shortErr(ex);
                                // Sin internet (no resuelve el nombre) no es culpa de la fuente: no se cambia de fuente,
                                // y si hay una tesela guardada de otra fuente se muestra esa.
                                boolean noNet = ex instanceof java.net.UnknownHostException || ex instanceof java.net.ConnectException;
                                if (layer == 0) {
                                    Object[] any = cachedAny(z, x, y);
                                    if (any != null) {
                                        netErrors++;
                                        netOffline = netErrors >= 3;
                                        final Bitmap ab = DARKEN[(Integer) any[1]] && !Main.LIGHT ? darken((Bitmap) any[0], bright) : (Bitmap) any[0];
                                        final long fk0 = k;
                                        ui.post(new Runnable() { public void run() {
                                            synchronized (queue) { pending.remove(fk0); }
                                            mem.put(fk0, ab); invalidate();
                                        }});
                                        continue;
                                    }
                                }
                                if (!noNet && provider == pv && okCount == 0 && layer == 0) {
                                    int nx = pv;
                                    do { nx = (nx + 1) % URLS.length; if (nx == 1) rounds++; } while (URLS[nx].length() == 0);
                                    provider = nx;
                                }
                                throw ex;
                            }
                            okCount++;
                            if (layer == 0) ERR[pv] = "OK";
                            if (layer == 0 && pv > 0 && sp.getInt("map_provider", 0) != pv) sp.edit().putInt("map_provider", pv).apply();
                            b = BitmapFactory.decodeByteArray(data, 0, data.length);
                            if (b != null) {
                                FileOutputStream os = new FileOutputStream(f);
                                os.write(data);
                                os.close();
                                netErrors = 0;
                                netOffline = false;
                            }
                        }
                        if (b != null && DARKEN[pv] && layer == 0 && !Main.LIGHT) b = darken(b, bright);
                    }
                } catch (Throwable e) {
                    if (layer != L_FILE && layer != L_TRAFFIC) { netErrors++; netOffline = netErrors >= 3; }
                    else if (layer == L_FILE) lastError = "[archivo] " + e;
                }
                final long fk = k;
                final Bitmap fb = b;
                ui.post(new Runnable() {
                    public void run() {
                        synchronized (queue) { pending.remove(fk); }
                        if (fb != null) { mem.put(fk, fb); invalidate(); }
                        else retryLater(fk);
                    }
                });
            }
        }
    };

    private void retryLater(final long k) {
        synchronized (queue) { failed.add(k); }
        ui.postDelayed(new Runnable() {
            public void run() { synchronized (queue) { failed.remove(k); } invalidate(); }
        }, rounds >= 2 ? 60000 : 15000);
        trimDisk();
    }

    private long lastTrim;

    /** Limita la caché en disco a ~60 MB. */
    private void trimDisk() {
        long now = System.currentTimeMillis();
        if (now - lastTrim < 600000) return;
        lastTrim = now;
        new Thread(new Runnable() {
            public void run() {
                File[] fs = dir.listFiles();
                if (fs == null) return;
                long total = 0;
                for (File f : fs) total += f.length();
                if (total < 60L * 1024 * 1024) return;
                java.util.Arrays.sort(fs, new java.util.Comparator<File>() {
                    public int compare(File a, File b) { long d = a.lastModified() - b.lastModified(); return d > 0 ? 1 : d < 0 ? -1 : 0; }
                });
                for (File f : fs) { if (total < 45L * 1024 * 1024) break; total -= f.length(); f.delete(); }
            }
        }).start();
    }
}
