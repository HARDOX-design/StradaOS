package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.format.DateFormat;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Navegación paso a paso en vivo sobre el mapa de StradaOS (sin Play Services ni SDKs pesados).
 * Búsqueda con Photon/Nominatim + mapa .map local, rutas con OSRM + A* sin conexión + caché de rutas,
 * y guía por voz con TextToSpeech del sistema.
 */
public class Nav {

    public static final int LANE_STRAIGHT     = 1;
    public static final int LANE_SLIGHT_LEFT  = 2;
    public static final int LANE_LEFT         = 4;
    public static final int LANE_SHARP_LEFT   = 8;
    public static final int LANE_SLIGHT_RIGHT = 16;
    public static final int LANE_RIGHT        = 32;
    public static final int LANE_SHARP_RIGHT  = 64;
    public static final int LANE_UTURN        = 128;
    public static final int LANE_VALID        = 256;

    public static class Place {
        public String name, sub;
        public double lat, lon;
        public boolean offline;
    }

    public static class Incident {
        public double lat, lon;
        public int category, delayS;
        public String desc;
    }

    public static class Camera {
        public double lat, lon, alongM;
        public int maxSpeedKmh;
    }

    public static class Step {
        public double lat, lon, distM, durS;
        /** 0 recto, 1 derecha suave, 2 derecha, 3 derecha cerrada, 4 retorno, 5 izquierda cerrada, 6 izquierda, 7 izquierda suave, 8 rotonda, 9 llegada. */
        public int icon;
        public String street, instruction;
        public int[] lanes;
    }

    public static class Route {
        public double[] pts;        // lat0, lon0, lat1, lon1, ...
        public int[] stepPtIdx;     // índice de vértice (0..pts.length/2 - 1) para cada paso
        public double[] cumDistM;   // distancia acumulada en metros desde el inicio hasta cada vértice
        public short[] segMaxSpeedKmh; // límite km/h por segmento (pts.length/2 - 1), 0 = desconocido
        public final List<Step> steps = new ArrayList<Step>();
        public final List<Place> waypoints = new ArrayList<Place>();
        public final List<Incident> incidents = new ArrayList<Incident>();
        public final List<Camera> cameras = new ArrayList<Camera>();
        public double totalDistM, totalDurS;
        public int trafficDelayS;   // retraso por tráfico (segundos, 0 si no hay o no aplica)
        public String destName;
        public double destLat, destLon;
        public boolean offline;
        public String source;       // "tomtom", "osrm", "map", "cache"
    }

    public interface SearchCb { void onResult(List<Place> places, String error); }
    public interface RouteCb { void onRoute(Route r, String error); }
    public interface RoutesCb { void onRoutes(List<Route> routes, String error); }

    public static volatile Route active;
    public static volatile List<Route> altRoutes;
    public static volatile int curPtIdx, curStepIdx;
    public static volatile double distToStepM, remDistM, remDurS;
    public static volatile int trafficDelayS;
    public static volatile boolean rerouting;
    public static volatile boolean offlineOffRoute;
    public static volatile double offRouteM;
    public static volatile int curSpeedLimitKmh;
    public static volatile boolean speedingAlert;
    public static volatile Camera nearbyCamera;
    public static volatile double nearbyCameraDistM;
    public static boolean savedHeadingUp;

    private static volatile int routeGen;
    private static int offRouteCount;
    private static long lastRerouteAt;
    private static int[] spokenMask;
    private static double smoothSpeedMs;
    private static int smoothSpeedSamples;
    private static long lastSpeedAt;
    private static long lastSpeedChimeAt;
    private static long lastCamChimeAt;
    private static int lastCamIdx = -1;
    private static volatile boolean osrmNoMaxspeed;
    private static volatile boolean osrmNoExclude;
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private static TextToSpeech tts;
    private static boolean ttsReady;
    private static String ttsLang;
    private static AudioManager duckAm;
    private static boolean hasAudioFocus;
    private static int speakSeq;
    private static volatile String curUttId;
    private static final AudioManager.OnAudioFocusChangeListener FOCUS_LS = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                hasAudioFocus = false;
            }
        }
    };
    private static final Runnable ABANDON_FOCUS = new Runnable() {
        public void run() { abandonNavFocus(); }
    };

    // ───────────────────────── conectividad y búsqueda de destinos ─────────────────────────

    @SuppressWarnings("deprecation")
    public static boolean hasNetwork(Context c) {
        if (c == null) return true;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable e) {
            return true;
        }
    }

    public static void search(final String query, final double biasLat, final double biasLon, final SearchCb cb) {
        search(null, query, biasLat, biasLon, false, cb);
    }

    public static void search(final Context c, final String query, final double biasLat, final double biasLon,
                              final boolean mph, final SearchCb cb) {
        final String q = query == null ? "" : query.trim();
        if (q.length() == 0) { cb.onResult(new ArrayList<Place>(), null); return; }
        Place direct = parseCoords(q);
        if (direct != null) {
            List<Place> one = new ArrayList<Place>();
            one.add(direct);
            cb.onResult(one, null);
            return;
        }
        final Context app = c != null ? c.getApplicationContext() : null;
        new Thread(new Runnable() {
            public void run() {
                if (app != null) Net.init(app);
                List<Place> out = null;
                String err = null;
                boolean tryOfflineFirst = !hasNetwork(app) || LiveMapView.isOffline();
                if (tryOfflineFirst) {
                    out = searchOfflineMap(q, null, biasLat, biasLon, mph, 10);
                }
                if (out == null || out.isEmpty()) {
                    try {
                        out = searchPhoton(app, q, biasLat, biasLon);
                    } catch (Throwable e) {
                        err = shortErr(e);
                    }
                    if (out == null || out.isEmpty()) {
                        try {
                            List<Place> nom = searchNominatim(app, q);
                            if (nom != null && !nom.isEmpty()) { out = nom; err = null; }
                            else if (out == null) out = nom;
                        } catch (Throwable e) {
                            if (err == null) err = shortErr(e);
                        }
                    }
                }
                if ((out == null || out.isEmpty()) && !tryOfflineFirst) {
                    List<Place> off = searchOfflineMap(q, null, biasLat, biasLon, mph, 10);
                    if (off != null && !off.isEmpty()) { out = off; err = null; }
                }
                final List<Place> fout = out;
                final String ferr = fout == null ? err : null;
                UI.post(new Runnable() {
                    public void run() { cb.onResult(fout, ferr); }
                });
            }
        }).start();
    }

    public static void searchCategory(final String cat, final double biasLat, final double biasLon,
                                      final boolean mph, final SearchCb cb) {
        new Thread(new Runnable() {
            public void run() {
                final List<Place> out = searchOfflineMap(null, cat, biasLat, biasLon, mph, 12);
                UI.post(new Runnable() {
                    public void run() { cb.onResult(out, null); }
                });
            }
        }).start();
    }

    private static List<Place> searchOfflineMap(String q, String cat, double lat, double lon, boolean mph, int limit) {
        OfflineMap fm = LiveMapView.fileMap;
        if (fm == null) return null;
        ArrayList<OfflineMap.SearchHit> hits = fm.searchOffline(q, cat, lat, lon, limit);
        if (hits == null || hits.isEmpty()) return new ArrayList<Place>();
        List<Place> out = new ArrayList<Place>(hits.size());
        for (OfflineMap.SearchHit h : hits) {
            Place p = new Place();
            p.name = h.name;
            p.lat = h.lat;
            p.lon = h.lon;
            p.offline = true;
            String kind = translateTag(h.tag);
            String d = Double.isNaN(lat) || Double.isNaN(lon) ? "" : fmtStepDist(h.distM, mph);
            p.sub = kind + (d.length() > 0 ? " · " + d : "") + " · " + I18n.t("Mapa local");
            out.add(p);
        }
        return out;
    }

    private static String translateTag(String tag) {
        if ("city".equals(tag)) return I18n.t("Ciudad");
        if ("town".equals(tag) || "village".equals(tag) || "hamlet".equals(tag)) return I18n.t("Pueblo");
        if ("suburb".equals(tag) || "neighbourhood".equals(tag) || "quarter".equals(tag)) return I18n.t("Barrio");
        if ("street".equals(tag)) return I18n.t("Calle");
        if ("fuel".equals(tag)) return I18n.t("Estación de servicio");
        if ("hospital".equals(tag)) return I18n.t("Hospital");
        if ("clinic".equals(tag) || "doctors".equals(tag)) return I18n.t("Clínica");
        if ("pharmacy".equals(tag)) return I18n.t("Farmacia");
        if ("parking".equals(tag)) return I18n.t("Parqueadero");
        if ("restaurant".equals(tag) || "fast_food".equals(tag)) return I18n.t("Restaurante");
        if ("cafe".equals(tag)) return I18n.t("Cafetería");
        if ("supermarket".equals(tag) || "convenience".equals(tag)) return I18n.t("Supermercado");
        if ("hotel".equals(tag)) return I18n.t("Hotel");
        if ("aerodrome".equals(tag) || "airport".equals(tag)) return I18n.t("Aeropuerto");
        if ("station".equals(tag)) return I18n.t("Estación");
        return I18n.t("Lugar");
    }

    private static Place parseCoords(String q) {
        String s = q.replace(';', ',');
        String[] parts = s.contains(",") ? s.split(",") : s.split("\\s+");
        if (parts.length != 2) return null;
        try {
            double la = Double.parseDouble(parts[0].trim());
            double lo = Double.parseDouble(parts[1].trim());
            if (la < -90 || la > 90 || lo < -180 || lo > 180) return null;
            Place p = new Place();
            p.lat = la;
            p.lon = lo;
            p.name = String.format(Locale.US, "%.5f, %.5f", la, lo);
            p.sub = I18n.t("Coordenadas");
            return p;
        } catch (Throwable e) {
            return null;
        }
    }

    private static List<Place> searchPhoton(Context c, String q, double biasLat, double biasLon) throws Exception {
        StringBuilder url = new StringBuilder("https://photon.komoot.io/api/?limit=6&q=");
        url.append(URLEncoder.encode(q, "UTF-8"));
        if ("en".equals(I18n.lang) || "de".equals(I18n.lang) || "fr".equals(I18n.lang) || "it".equals(I18n.lang)) {
            url.append("&lang=").append(I18n.lang);
        }
        if (!Double.isNaN(biasLat) && !Double.isNaN(biasLon)) {
            url.append(String.format(Locale.US, "&lat=%.4f&lon=%.4f", biasLat, biasLon));
        }
        String raw = new String(navGet(c, url.toString(), 10000), "UTF-8");
        JSONObject root = new JSONObject(raw);
        JSONArray feats = root.optJSONArray("features");
        List<Place> out = new ArrayList<Place>();
        if (feats == null) return out;
        for (int i = 0; i < feats.length(); i++) {
            JSONObject f = feats.optJSONObject(i);
            if (f == null) continue;
            JSONObject geom = f.optJSONObject("geometry");
            JSONArray coords = geom != null ? geom.optJSONArray("coordinates") : null;
            if (coords == null || coords.length() < 2) continue;
            double lon = coords.optDouble(0, Double.NaN);
            double lat = coords.optDouble(1, Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            JSONObject pr = f.optJSONObject("properties");
            String name = pr != null ? pr.optString("name", "").trim() : "";
            String street = pr != null ? pr.optString("street", "").trim() : "";
            String num = pr != null ? pr.optString("housenumber", "").trim() : "";
            if (name.length() == 0 && street.length() > 0) {
                name = num.length() > 0 ? street + " " + num : street;
                street = "";
            }
            if (name.length() == 0) name = String.format(Locale.US, "%.5f, %.5f", lat, lon);
            StringBuilder sub = new StringBuilder();
            if (street.length() > 0 && !street.equals(name)) sub.append(num.length() > 0 ? street + " " + num : street);
            appendPart(sub, pr != null ? firstNonEmpty(pr.optString("city", ""), pr.optString("town", ""), pr.optString("village", ""), pr.optString("county", "")) : "");
            appendPart(sub, pr != null ? pr.optString("state", "") : "");
            appendPart(sub, pr != null ? pr.optString("country", "") : "");
            Place p = new Place();
            p.name = name;
            p.sub = sub.toString();
            p.lat = lat;
            p.lon = lon;
            if (!dupPlace(out, p)) out.add(p);
        }
        return out;
    }

    private static List<Place> searchNominatim(Context c, String q) throws Exception {
        String url = "https://nominatim.openstreetmap.org/search?format=json&limit=6&addressdetails=1&q="
                + URLEncoder.encode(q, "UTF-8") + "&accept-language=" + URLEncoder.encode(I18n.lang, "UTF-8");
        String raw = new String(navGet(c, url, 10000), "UTF-8");
        JSONArray arr = new JSONArray(raw);
        List<Place> out = new ArrayList<Place>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            double lat = Double.parseDouble(o.getString("lat"));
            double lon = Double.parseDouble(o.getString("lon"));
            String disp = o.optString("display_name", "");
            String name = o.optString("name", "").trim();
            String sub = "";
            int comma = disp.indexOf(',');
            if (name.length() == 0) {
                name = comma > 0 ? disp.substring(0, comma).trim() : disp;
                sub = comma > 0 ? disp.substring(comma + 1).trim() : "";
            } else {
                sub = disp.startsWith(name) && comma > 0 ? disp.substring(comma + 1).trim() : disp;
            }
            if (name.length() == 0) name = String.format(Locale.US, "%.5f, %.5f", lat, lon);
            Place p = new Place();
            p.name = name;
            p.sub = sub;
            p.lat = lat;
            p.lon = lon;
            if (!dupPlace(out, p)) out.add(p);
        }
        return out;
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && v.trim().length() > 0) return v.trim();
        return "";
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null) return;
        part = part.trim();
        if (part.length() == 0) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(part);
    }

    private static boolean dupPlace(List<Place> list, Place p) {
        for (Place e : list) {
            if (e.name.equalsIgnoreCase(p.name) && Math.abs(e.lat - p.lat) < 0.0008 && Math.abs(e.lon - p.lon) < 0.0008) return true;
        }
        return false;
    }

    // ───────────────────────── destinos recientes, casa/trabajo y favoritos ─────────────────────────

    public static List<Place> recents(Context c) {
        return readPlaces(c, "nav_recents");
    }

    public static void saveRecent(Context c, Place p) {
        addPlaceToList(c, "nav_recents", p, 8);
    }

    public static Place savedPlace(Context c, String key) {
        if (c == null || key == null) return null;
        try {
            String raw = c.getSharedPreferences("launcher", 0).getString(key, "");
            if (raw.length() == 0) return null;
            return placeFromJson(new JSONObject(raw));
        } catch (Throwable e) {
            return null;
        }
    }

    public static void setSavedPlace(Context c, String key, Place p) {
        if (c == null || key == null) return;
        SharedPreferences.Editor ed = c.getSharedPreferences("launcher", 0).edit();
        if (p == null || p.name == null || p.name.length() == 0) {
            ed.remove(key).apply();
            return;
        }
        try {
            ed.putString(key, placeToJson(p).toString()).apply();
        } catch (Throwable ignored) { }
    }

    public static List<Place> favorites(Context c) {
        return readPlaces(c, "nav_favs");
    }

    public static boolean isFavorite(Context c, Place p) {
        if (c == null || p == null) return false;
        for (Place e : favorites(c)) {
            if (samePlace(e, p)) return true;
        }
        return false;
    }

    public static boolean toggleFavorite(Context c, Place p) {
        if (c == null || p == null || p.name == null || p.name.length() == 0) return false;
        List<Place> list = favorites(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (samePlace(list.get(i), p)) {
                list.remove(i);
                writePlaces(c, "nav_favs", list);
                return false;
            }
        }
        list.add(0, p);
        while (list.size() > 16) list.remove(list.size() - 1);
        writePlaces(c, "nav_favs", list);
        return true;
    }

    private static List<Place> readPlaces(Context c, String key) {
        List<Place> out = new ArrayList<Place>();
        if (c == null) return out;
        try {
            SharedPreferences sp = c.getSharedPreferences("launcher", 0);
            JSONArray a = new JSONArray(sp.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                Place p = placeFromJson(a.optJSONObject(i));
                if (p != null) out.add(p);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static void addPlaceToList(Context c, String key, Place p, int max) {
        if (c == null || p == null || p.name == null || p.name.length() == 0) return;
        List<Place> list = readPlaces(c, key);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (samePlace(list.get(i), p)) list.remove(i);
        }
        list.add(0, p);
        while (list.size() > max) list.remove(list.size() - 1);
        writePlaces(c, key, list);
    }

    private static void writePlaces(Context c, String key, List<Place> list) {
        JSONArray a = new JSONArray();
        try {
            for (Place e : list) a.put(placeToJson(e));
            c.getSharedPreferences("launcher", 0).edit().putString(key, a.toString()).apply();
        } catch (Throwable ignored) { }
    }

    private static Place placeFromJson(JSONObject o) {
        if (o == null) return null;
        Place p = new Place();
        p.name = o.optString("n", "");
        p.sub = o.optString("s", "");
        p.lat = o.optDouble("la", Double.NaN);
        p.lon = o.optDouble("lo", Double.NaN);
        return p.name.length() > 0 && !Double.isNaN(p.lat) && !Double.isNaN(p.lon) ? p : null;
    }

    private static JSONObject placeToJson(Place e) throws Exception {
        return new JSONObject().put("n", e.name).put("s", e.sub != null ? e.sub : "").put("la", e.lat).put("lo", e.lon);
    }

    private static boolean samePlace(Place a, Place b) {
        return a.name.equalsIgnoreCase(b.name) || (Math.abs(a.lat - b.lat) < 0.0005 && Math.abs(a.lon - b.lon) < 0.0005);
    }

    // ───────────────────────── carro estacionado ─────────────────────────

    // ───────────────────────── cálculo y activación de ruta ─────────────────────────

    public static void startRoute(final Context c, double fromLat, double fromLon, final Place dest, final RouteCb cb) {
        List<Place> stops = new ArrayList<Place>(1);
        stops.add(dest);
        startRoute(c, fromLat, fromLon, stops, cb);
    }

    public static void startRoute(final Context c, double fromLat, double fromLon, final List<Place> stops, final RouteCb cb) {
        final int gen = ++routeGen;
        fetchRoutes(c, fromLat, fromLon, stops, false, new RoutesCb() {
            public void onRoutes(List<Route> routes, String error) {
                if (gen != routeGen) return;
                Route r = routes != null && !routes.isEmpty() ? routes.get(0) : null;
                if (r != null) {
                    activate(c, r);
                    if (!r.steps.isEmpty()) speakStep(c, r.steps.get(0), -1, false);
                }
                if (cb != null) cb.onRoute(r, error);
            }
        });
    }

    public static void activate(Route r) {
        activate(null, r);
    }

    public static void activate(final Context c, final Route r) {
        active = r;
        altRoutes = null;
        curPtIdx = 0;
        curStepIdx = r != null && r.steps.size() > 1 ? 1 : 0;
        remDistM = r != null ? r.totalDistM : 0;
        remDurS = r != null ? r.totalDurS : 0;
        trafficDelayS = r != null ? r.trafficDelayS : 0;
        distToStepM = r != null && !r.steps.isEmpty() ? r.steps.get(0).distM : 0;
        if (r != null && r.steps.size() > 1 && r.stepPtIdx != null && r.cumDistM != null && r.stepPtIdx[1] < r.cumDistM.length) {
            distToStepM = r.cumDistM[r.stepPtIdx[1]];
        }
        offRouteCount = 0;
        offRouteM = 0;
        offlineOffRoute = false;
        rerouting = false;
        smoothSpeedMs = 0;
        smoothSpeedSamples = 0;
        lastSpeedAt = 0;
        curSpeedLimitKmh = r != null && r.segMaxSpeedKmh != null && r.segMaxSpeedKmh.length > 0 ? r.segMaxSpeedKmh[0] : 0;
        speedingAlert = false;
        nearbyCamera = null;
        nearbyCameraDistM = 0;
        lastCamIdx = -1;
        spokenMask = r != null ? new int[r.steps.size()] : null;
        if (c != null && r != null) {
            final Context app = c.getApplicationContext();
            new Thread(new Runnable() {
                public void run() {
                    saveActiveFile(app, r);
                    if (!"cache".equals(r.source)) cacheDestRoute(app, r);
                }
            }).start();
        }
    }

    public static void announceStart(Context c, Route r) {
        if (c != null && r != null && !r.steps.isEmpty()) speakStep(c, r.steps.get(0), -1, false);
    }

    public static void stop() {
        stop(null);
    }

    public static void stop(Context c) {
        routeGen++;
        active = null;
        altRoutes = null;
        curPtIdx = 0;
        curStepIdx = 0;
        distToStepM = 0;
        remDistM = 0;
        remDurS = 0;
        trafficDelayS = 0;
        rerouting = false;
        offlineOffRoute = false;
        offRouteM = 0;
        offRouteCount = 0;
        smoothSpeedMs = 0;
        smoothSpeedSamples = 0;
        lastSpeedAt = 0;
        curSpeedLimitKmh = 0;
        speedingAlert = false;
        nearbyCamera = null;
        nearbyCameraDistM = 0;
        lastCamIdx = -1;
        spokenMask = null;
        abandonNavFocus();
        if (c != null) {
            try { new File(c.getFilesDir(), "nav_active.json").delete(); } catch (Throwable ignored) { }
        }
    }

    public static boolean restore(Context c) {
        if (c == null || active != null) return active != null;
        try {
            File f = new File(c.getFilesDir(), "nav_active.json");
            if (!f.isFile() || f.length() == 0) return false;
            JSONObject root = new JSONObject(readUtf8(f));
            long savedAt = root.optLong("savedAt", 0);
            if (savedAt <= 0 || Math.abs(System.currentTimeMillis() - savedAt) > 6L * 3600 * 1000) {
                f.delete();
                return false;
            }
            Route r = routeFromJson(root);
            if (r == null) { f.delete(); return false; }
            active = r;
            int nPts = r.pts.length / 2;
            curPtIdx = Math.max(0, Math.min(nPts - 1, root.optInt("ptIdx", 0)));
            curStepIdx = Math.max(0, Math.min(r.steps.size() - 1, root.optInt("stepIdx", r.steps.size() > 1 ? 1 : 0)));
            double along = curPtIdx < r.cumDistM.length ? r.cumDistM[curPtIdx] : 0;
            double totalM = r.cumDistM[nPts - 1];
            remDistM = Math.max(0, totalM - along);
            remDurS = totalM > 1 ? r.totalDurS * (remDistM / totalM) : 0;
            trafficDelayS = totalM > 1 && r.trafficDelayS > 0 ? (int) Math.round(r.trafficDelayS * (remDistM / totalM)) : 0;
            int spi = curStepIdx < r.stepPtIdx.length ? r.stepPtIdx[curStepIdx] : nPts - 1;
            distToStepM = Math.max(0, r.cumDistM[spi] - along);
            curSpeedLimitKmh = r.segMaxSpeedKmh != null && curPtIdx < r.segMaxSpeedKmh.length ? r.segMaxSpeedKmh[curPtIdx] : 0;
            spokenMask = new int[r.steps.size()];
            offRouteCount = 0;
            offlineOffRoute = false;
            offRouteM = 0;
            rerouting = false;
            smoothSpeedMs = 0;
            smoothSpeedSamples = 0;
            lastSpeedAt = 0;
            speedingAlert = false;
            nearbyCamera = null;
            nearbyCameraDistM = 0;
            lastCamIdx = -1;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void fetchRoute(final double lat1, final double lon1, final double lat2, final double lon2,
                                  final String destName, final RouteCb cb) {
        fetchRoute(null, lat1, lon1, lat2, lon2, destName, cb);
    }

    public static void fetchRoute(final Context c, final double lat1, final double lon1, final double lat2, final double lon2,
                                  final String destName, final RouteCb cb) {
        Place d = new Place();
        d.lat = lat2;
        d.lon = lon2;
        d.name = destName;
        List<Place> stops = new ArrayList<Place>(1);
        stops.add(d);
        fetchRoutes(c, lat1, lon1, stops, false, new RoutesCb() {
            public void onRoutes(List<Route> routes, String error) {
                Route first = routes != null && !routes.isEmpty() ? routes.get(0) : null;
                cb.onRoute(first, error);
            }
        });
    }

    public static void fetchRoutes(final Context c, final double lat1, final double lon1,
                                   final List<Place> stops, final boolean wantAlternatives, final RoutesCb cb) {
        final Context app = c != null ? c.getApplicationContext() : null;
        if (stops == null || stops.isEmpty()) {
            UI.post(new Runnable() { public void run() { cb.onRoutes(null, I18n.t("No se encontró ruta")); } });
            return;
        }
        final List<Place> stopsCopy = new ArrayList<Place>(stops);
        final Place dest = stopsCopy.get(stopsCopy.size() - 1);
        new Thread(new Runnable() {
            public void run() {
                if (app != null) Net.init(app);
                SharedPreferences sp = app != null ? app.getSharedPreferences("launcher", 0) : null;
                boolean avoidTolls = sp != null && sp.getBoolean("nav_avoid_tolls", false);
                boolean avoidHighways = sp != null && sp.getBoolean("nav_avoid_motorways", false);
                boolean useTraffic = sp != null && sp.getBoolean("map_traffic", false);
                boolean checkIncidents = sp != null && sp.getBoolean("nav_incidents", false);
                boolean checkCameras = sp != null && sp.getBoolean("nav_speed_cams", false);
                String ttKey = sp != null ? sp.getString("tomtom_key", "").trim() : "";
                String customOsrm = sp != null ? normalizeOsrmBase(sp.getString("nav_route_url", "")) : null;

                List<Route> routes = null;
                String err = null;
                boolean offlineMode = !hasNetwork(app) || LiveMapView.isOffline();

                if (offlineMode) {
                    routes = routeFromOfflineList(lat1, lon1, stopsCopy, avoidTolls, avoidHighways);
                    if ((routes == null || routes.isEmpty()) && app != null && stopsCopy.size() == 1) {
                        Route cached = loadCachedRoute(app, lat1, lon1, dest.lat, dest.lon);
                        if (cached != null) { routes = new ArrayList<Route>(1); routes.add(cached); }
                    }
                }
                if ((routes == null || routes.isEmpty()) && hasNetwork(app)) {
                    String[] errOut = new String[1];
                    if (useTraffic && ttKey.length() > 0 && customOsrm == null) {
                        routes = fetchTomTomList(app, ttKey, lat1, lon1, stopsCopy, wantAlternatives, avoidTolls, avoidHighways, errOut);
                    }
                    if (routes == null || routes.isEmpty()) {
                        routes = fetchOsrmList(app, customOsrm, lat1, lon1, stopsCopy, wantAlternatives, avoidTolls, avoidHighways, errOut);
                    }
                    if (routes == null || routes.isEmpty()) err = errOut[0];
                }
                if ((routes == null || routes.isEmpty()) && !offlineMode) {
                    routes = routeFromOfflineList(lat1, lon1, stopsCopy, avoidTolls, avoidHighways);
                    if ((routes == null || routes.isEmpty()) && app != null && stopsCopy.size() == 1) {
                        Route cached = loadCachedRoute(app, lat1, lon1, dest.lat, dest.lon);
                        if (cached != null) { routes = new ArrayList<Route>(1); routes.add(cached); }
                    }
                }
                if (routes != null && !routes.isEmpty()) {
                    for (Route rt : routes) {
                        rt.waypoints.clear();
                        for (int i = 0; i < stopsCopy.size() - 1; i++) rt.waypoints.add(stopsCopy.get(i));
                    }
                    if (ttKey.length() > 0 && checkIncidents && hasNetwork(app)) {
                        checkRouteIncidents(app, ttKey, routes);
                    }
                    if (checkCameras) {
                        scanRouteCameras(routes);
                    }
                }
                final List<Route> fr = routes != null && !routes.isEmpty() ? routes : null;
                String fallbackErr = err != null ? err : I18n.t("No se encontró ruta");
                if (fr == null && offlineMode && LiveMapView.fileMap == null) {
                    fallbackErr = I18n.t("Para rutas largas o sin mapa local hace falta internet");
                }
                final String fe = fr == null ? fallbackErr : null;
                UI.post(new Runnable() {
                    public void run() { cb.onRoutes(fr, fe); }
                });
            }
        }).start();
    }

    /** Normaliza una URL base de servidor de rutas OSRM (vacía = servidores públicos por defecto). */
    public static String normalizeOsrmBase(String raw) {
        if (raw == null) return null;
        String u = raw.trim();
        if (u.length() == 0) return null;
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        if (u.indexOf("/route/v1/") >= 0) return u.endsWith("/") ? u : u + "/";
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u + "/route/v1/driving/";
    }

    private static List<Route> fetchTomTomList(Context c, String key, double lat1, double lon1, List<Place> stops,
                                               boolean wantAlternatives, boolean avoidTolls, boolean avoidHighways, String[] errOut) {
        try {
            StringBuilder coords = new StringBuilder();
            coords.append(String.format(Locale.US, "%.6f,%.6f", lat1, lon1));
            for (Place st : stops) coords.append(String.format(Locale.US, ":%.6f,%.6f", st.lat, st.lon));
            StringBuilder base = new StringBuilder();
            base.append("https://api.tomtom.com/routing/1/calculateRoute/").append(coords)
                .append("/json?key=").append(URLEncoder.encode(key, "UTF-8"))
                .append("&traffic=true&instructionsType=text&language=").append(URLEncoder.encode(tomtomLang(), "UTF-8"))
                .append("&routeType=fastest&sectionType=speedLimit");
            if (avoidTolls) base.append("&avoid=tollRoads");
            if (avoidHighways) base.append("&avoid=motorways");
            String url = base.toString();
            if (wantAlternatives && stops.size() == 1) url += "&maxAlternatives=2";
            String raw;
            try {
                raw = new String(navGet(c, url, 12000), "UTF-8");
            } catch (Throwable firstErr) {
                if (wantAlternatives && stops.size() == 1) {
                    raw = new String(navGet(c, base.toString(), 12000), "UTF-8");
                } else {
                    throw firstErr;
                }
            }
            return parseTomTomList(raw, stops);
        } catch (Throwable e) {
            if (errOut != null) errOut[0] = shortErr(e);
            return null;
        }
    }

    private static String tomtomLang() {
        String lang = I18n.lang != null ? I18n.lang : "es";
        String country = I18n.loc != null ? I18n.loc.getCountry() : "";
        if ("es".equals(lang)) return "ES".equalsIgnoreCase(country) ? "es-ES" : "es-419";
        if ("en".equals(lang)) return "GB".equalsIgnoreCase(country) ? "en-GB" : "en-US";
        if ("pt".equals(lang)) return "PT".equalsIgnoreCase(country) ? "pt-PT" : "pt-BR";
        if ("fr".equals(lang)) return "fr-FR";
        if ("de".equals(lang)) return "de-DE";
        if ("it".equals(lang)) return "it-IT";
        return "en-US";
    }

    private static List<Route> fetchOsrmList(Context c, String customOsrm, double lat1, double lon1, List<Place> stops,
                                             boolean wantAlternatives, boolean avoidTolls, boolean avoidHighways, String[] errOut) {
        StringBuilder coords = new StringBuilder();
        coords.append(String.format(Locale.US, "%.6f,%.6f", lon1, lat1));
        for (Place st : stops) coords.append(String.format(Locale.US, ";%.6f,%.6f", st.lon, st.lat));
        boolean tryMaxspeed = !osrmNoMaxspeed;
        boolean tryExclude = (avoidTolls || avoidHighways) && !osrmNoExclude;

        for (int attempt = 0; attempt < 3; attempt++) {
            StringBuilder tail = new StringBuilder(coords);
            tail.append("?overview=full&geometries=polyline&steps=true");
            if (wantAlternatives && stops.size() == 1) tail.append("&alternatives=true");
            if (tryMaxspeed) tail.append("&annotations=maxspeed");
            if (tryExclude) {
                tail.append("&exclude=");
                if (avoidTolls && avoidHighways) tail.append("toll,motorway");
                else if (avoidTolls) tail.append("toll");
                else tail.append("motorway");
            }
            String tailStr = tail.toString();
            boolean[] sslOut = new boolean[1];
            boolean[] http400Out = new boolean[1];
            List<Route> res;
            if (customOsrm != null) {
                res = fetchOsrm(c, new String[]{customOsrm}, tailStr, stops, errOut, sslOut, http400Out);
            } else {
                res = fetchOsrm(c, new String[]{
                    "https://router.project-osrm.org/route/v1/driving/",
                    "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
                }, tailStr, stops, errOut, sslOut, http400Out);
                if ((res == null || res.isEmpty()) && sslOut[0]) {
                    res = fetchOsrm(c, new String[]{
                        "http://router.project-osrm.org/route/v1/driving/",
                        "http://routing.openstreetmap.de/routed-car/route/v1/driving/"
                    }, tailStr, stops, errOut, null, http400Out);
                }
            }
            if (res != null && !res.isEmpty()) return res;
            if (!http400Out[0]) break;
            if (tryMaxspeed) { osrmNoMaxspeed = true; tryMaxspeed = false; }
            else if (tryExclude) { osrmNoExclude = true; tryExclude = false; }
            else break;
        }
        return null;
    }

    private static List<Route> fetchOsrm(Context c, String[] hosts, String tail, List<Place> stops,
                                         String[] errOut, boolean[] sslOut, boolean[] http400Out) {
        for (String host : hosts) {
            try {
                String raw = new String(navGet(c, host + tail, 12000), "UTF-8");
                List<Route> rs = parseOsrmList(raw, stops);
                if (rs != null && !rs.isEmpty()) return rs;
            } catch (Throwable e) {
                android.util.Log.w("StradaNav", "ruta " + host + " → " + e + (e.getCause() != null ? " ← " + e.getCause() : ""));
                if (sslOut != null && isSsl(e)) sslOut[0] = true;
                String em = String.valueOf(e.getMessage());
                if (http400Out != null && (em.indexOf("400") >= 0 || em.indexOf("InvalidValue") >= 0)) http400Out[0] = true;
                if (errOut != null) errOut[0] = shortErr(e);
            }
        }
        return null;
    }

    private static boolean isSsl(Throwable e) {
        for (int i = 0; i < 5 && e != null; i++, e = e.getCause()) {
            if (e instanceof SSLException) return true;
        }
        return false;
    }

    private static volatile SSLSocketFactory navTlsFactory;
    private static volatile boolean navTlsInitDone;

    /** Descarga HTTPS/HTTP solo para rutas y búsqueda de navegación; TLS ampliado es opcional (apagado por defecto). */
    static byte[] navGet(Context c, String url, int timeoutMs) throws Exception {
        if (c != null && url != null && url.startsWith("https://")
                && c.getSharedPreferences("launcher", 0).getBoolean("nav_tls_compat", false)) {
            SSLSocketFactory sf = navSocketFactory(c);
            if (sf != null) {
                try {
                    return httpGetWithFactory(url, timeoutMs, sf);
                } catch (SSLException ignored) {
                    // continúa con el comportamiento estándar de Net.get()
                }
            }
        }
        return Net.get(url, timeoutMs);
    }

    private static synchronized SSLSocketFactory navSocketFactory(Context c) {
        if (navTlsInitDone) return navTlsFactory;
        navTlsInitDone = true;
        try {
            TrustManagerFactory sysTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            sysTmf.init((KeyStore) null);
            X509TrustManager sysTmp = null;
            for (TrustManager t : sysTmf.getTrustManagers()) {
                if (t instanceof X509TrustManager) { sysTmp = (X509TrustManager) t; break; }
            }
            if (sysTmp == null) return null;
            final X509TrustManager system = sysTmp;

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            InputStream in = c.getAssets().open("roots.pem");
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte[] rb = new byte[16384];
            int rn;
            while ((rn = in.read(rb)) > 0) raw.write(rb, 0, rn);
            in.close();
            String pem = new String(raw.toByteArray(), "US-ASCII");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            int i = 0, at = 0;
            while (true) {
                int s0 = pem.indexOf("-----BEGIN CERTIFICATE-----", at);
                if (s0 < 0) break;
                int e0 = pem.indexOf("-----END CERTIFICATE-----", s0);
                if (e0 < 0) break;
                at = e0 + 25;
                try {
                    Certificate cert = cf.generateCertificate(new java.io.ByteArrayInputStream(pem.substring(s0, at).getBytes("US-ASCII")));
                    ks.setCertificateEntry("root" + (i++), cert);
                } catch (Throwable ignored) { }
            }
            TrustManagerFactory bunTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            bunTmf.init(ks);
            X509TrustManager bunTmp = null;
            for (TrustManager t : bunTmf.getTrustManagers()) {
                if (t instanceof X509TrustManager) { bunTmp = (X509TrustManager) t; break; }
            }
            if (bunTmp == null) return null;
            final X509TrustManager bundled = bunTmp;
            X509TrustManager both = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
                    system.checkClientTrusted(chain, auth);
                }
                public void checkServerTrusted(X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
                    try { system.checkServerTrusted(chain, auth); }
                    catch (java.security.cert.CertificateException e) { bundled.checkServerTrusted(chain, auth); }
                }
                public X509Certificate[] getAcceptedIssuers() { return system.getAcceptedIssuers(); }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{both}, null);
            navTlsFactory = Build.VERSION.SDK_INT >= 21 ? ctx.getSocketFactory() : new NavTlsFactory(ctx.getSocketFactory());
        } catch (Throwable ignored) {
            navTlsFactory = null;
        }
        return navTlsFactory;
    }

    private static byte[] httpGetWithFactory(String url, int timeoutMs, SSLSocketFactory f) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (conn instanceof HttpsURLConnection && f != null) ((HttpsURLConnection) conn).setSSLSocketFactory(f);
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("User-Agent", "StradaOS/2.1 (Android head unit launcher)");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    /** Fábrica TLS opcional para navegación (solo si el usuario activa Compatibilidad TLS en rutas). */
    private static class NavTlsFactory extends SSLSocketFactory {
        private final SSLSocketFactory d;
        NavTlsFactory(SSLSocketFactory d) { this.d = d; }
        private Socket on(Socket s) { return on(s, null); }
        private Socket on(Socket s, String host) {
            if (s instanceof SSLSocket) {
                try {
                    ArrayList<String> ok = new ArrayList<String>();
                    for (String c : ((SSLSocket) s).getSupportedCipherSuites()) {
                        if (c.contains("SCSV") || c.contains("_anon_") || c.contains("NULL") || c.contains("EXPORT")
                                || c.contains("_RC4_") || c.contains("_DES_") || c.contains("_MD5")) continue;
                        ok.add(c);
                    }
                    if (!ok.isEmpty()) ((SSLSocket) s).setEnabledCipherSuites(ok.toArray(new String[0]));
                } catch (Throwable ignored) { }
                if (host != null) {
                    try { s.getClass().getMethod("setHostname", String.class).invoke(s, host); } catch (Throwable ignored) { }
                    try { s.getClass().getMethod("setUseSessionTickets", boolean.class).invoke(s, true); } catch (Throwable ignored) { }
                }
                try {
                    String[] sup = ((SSLSocket) s).getSupportedProtocols();
                    ArrayList<String> want = new ArrayList<String>();
                    for (String p : sup) if (p.equals("TLSv1.2") || p.equals("TLSv1.3") || p.equals("TLSv1.1")) want.add(p);
                    if (!want.isEmpty()) ((SSLSocket) s).setEnabledProtocols(want.toArray(new String[0]));
                } catch (Throwable ignored) { }
            }
            return s;
        }
        public String[] getDefaultCipherSuites() { return d.getDefaultCipherSuites(); }
        public String[] getSupportedCipherSuites() { return d.getSupportedCipherSuites(); }
        public Socket createSocket() throws java.io.IOException { return on(d.createSocket()); }
        public Socket createSocket(Socket s, String h, int p, boolean a) throws java.io.IOException { return on(d.createSocket(s, h, p, a), h); }
        public Socket createSocket(String h, int p) throws java.io.IOException { return on(d.createSocket(h, p), h); }
        public Socket createSocket(String h, int p, InetAddress l, int lp) throws java.io.IOException { return on(d.createSocket(h, p, l, lp), h); }
        public Socket createSocket(InetAddress h, int p) throws java.io.IOException { return on(d.createSocket(h, p)); }
        public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws java.io.IOException { return on(d.createSocket(h, p, l, lp)); }
    }

    private static List<Route> routeFromOfflineList(double lat1, double lon1, List<Place> stops,
                                                    boolean avoidTolls, boolean avoidHighways) {
        OfflineMap fm = LiveMapView.fileMap;
        if (fm == null || !fm.contains(lat1, lon1)) return null;
        for (Place p : stops) if (!fm.contains(p.lat, p.lon)) return null;
        OfflineMap.RawRoute combined = new OfflineMap.RawRoute();
        double curLa = lat1, curLo = lon1;
        for (int i = 0; i < stops.size(); i++) {
            Place st = stops.get(i);
            OfflineMap.RawRoute leg = fm.routeOffline(curLa, curLo, st.lat, st.lon, avoidTolls, avoidHighways);
            if (leg == null || leg.hops.isEmpty()) return null;
            if (i == 0) {
                combined.startLat = leg.startLat;
                combined.startLon = leg.startLon;
            }
            combined.hops.addAll(leg.hops);
            combined.totalDistM += leg.totalDistM;
            combined.totalDurS += leg.totalDurS;
            curLa = st.lat;
            curLo = st.lon;
        }
        Place last = stops.get(stops.size() - 1);
        Route r = fromOffline(combined, last.lat, last.lon, last.name);
        if (r == null) return null;
        List<Route> out = new ArrayList<Route>(1);
        out.add(r);
        return out;
    }

    /** Convierte una ruta A* sin conexión en pasos de navegación compatibles con HUD y voz TTS. */
    public static Route fromOffline(OfflineMap.RawRoute rr, double destLat, double destLon, String destName) {
        if (rr == null || rr.hops.isEmpty()) return null;
        int nHops = rr.hops.size();
        int nPts = nHops + 1;
        Route r = new Route();
        r.pts = new double[nPts * 2];
        r.cumDistM = new double[nPts];
        short[] segSp = new short[nHops];
        boolean hasAnySp = false;
        r.pts[0] = rr.startLat;
        r.pts[1] = rr.startLon;
        r.cumDistM[0] = 0;
        for (int i = 0; i < nHops; i++) {
            OfflineMap.RawHop h = rr.hops.get(i);
            r.pts[(i + 1) * 2] = h.lat;
            r.pts[(i + 1) * 2 + 1] = h.lon;
            r.cumDistM[i + 1] = r.cumDistM[i] + approxDist(r.pts[i * 2], r.pts[i * 2 + 1], h.lat, h.lon);
            segSp[i] = h.maxSpeedKmh;
            if (h.maxSpeedKmh > 0) hasAnySp = true;
        }
        if (hasAnySp) r.segMaxSpeedKmh = segSp;
        r.totalDistM = r.cumDistM[nPts - 1];
        r.totalDurS = Math.max(10.0, rr.totalDurS);
        r.destLat = destLat;
        r.destLon = destLon;
        r.destName = destName;
        r.offline = true;
        r.source = "map";

        List<Integer> ptIndices = new ArrayList<Integer>();
        String firstSt = rr.hops.get(0).name != null ? rr.hops.get(0).name.trim() : "";
        Step depart = new Step();
        depart.lat = rr.startLat;
        depart.lon = rr.startLon;
        depart.street = firstSt;
        depart.icon = 0;
        depart.instruction = firstSt.length() > 0 ? "Sal por " + firstSt : "Inicia el recorrido";
        r.steps.add(depart);
        ptIndices.add(0);

        String curStreet = firstSt;
        int lastStepPt = 0;
        int i = 0;
        while (i < nHops) {
            OfflineMap.RawHop h = rr.hops.get(i);
            int vIdx = i + 1; // vértice destino de hop i
            if (h.roundabout && i > 0) {
                int j = i;
                while (j < nHops && rr.hops.get(j).roundabout) j++;
                String exitSt = j < nHops && rr.hops.get(j).name != null ? rr.hops.get(j).name.trim() : "";
                Step st = new Step();
                st.lat = r.pts[vIdx * 2];
                st.lon = r.pts[vIdx * 2 + 1];
                st.icon = 8;
                st.street = exitSt;
                st.instruction = exitSt.length() > 0 ? "En la rotonda, sigue hacia " + exitSt : "En la rotonda, toma la salida";
                r.steps.add(st);
                ptIndices.add(vIdx);
                curStreet = exitSt;
                lastStepPt = Math.min(nPts - 1, j + 1);
                i = j + 1;
                continue;
            }
            if (vIdx < nPts - 1) {
                double legSince = r.cumDistM[vIdx] - r.cumDistM[lastStepPt];
                OfflineMap.RawHop nextHop = rr.hops.get(i + 1);
                String nextSt = nextHop.name != null ? nextHop.name.trim() : "";
                boolean nameChanged = nextSt.length() > 0 && !sameRoadName(curStreet, nextSt);
                double br1 = windowBearing(r.pts, r.cumDistM, vIdx, -1);
                double br2 = windowBearing(r.pts, r.cumDistM, vIdx, 1);
                double turn = ((br2 - br1 + 540.0) % 360.0) - 180.0;
                double absT = Math.abs(turn);
                boolean realJunction = h.deg >= 3 || nameChanged;
                boolean emit = (absT >= 36 && realJunction && legSince >= 25)
                        || (nameChanged && legSince >= 180 && (absT >= 18 || h.deg >= 3));
                if (emit) {
                    Step st = new Step();
                    st.lat = r.pts[vIdx * 2];
                    st.lon = r.pts[vIdx * 2 + 1];
                    st.street = nextSt.length() > 0 ? nextSt : curStreet;
                    st.icon = turnToIcon(turn);
                    st.instruction = offlineTurnText(st.icon, st.street);
                    r.steps.add(st);
                    ptIndices.add(vIdx);
                    if (nextSt.length() > 0) curStreet = nextSt;
                    lastStepPt = vIdx;
                } else if (nextSt.length() > 0) {
                    curStreet = nextSt;
                }
            }
            i++;
        }

        Step arr = new Step();
        arr.lat = r.pts[(nPts - 1) * 2];
        arr.lon = r.pts[(nPts - 1) * 2 + 1];
        arr.icon = 9;
        arr.street = destName != null ? destName : "";
        arr.instruction = "Llegaste a tu destino";
        r.steps.add(arr);
        ptIndices.add(nPts - 1);

        r.stepPtIdx = new int[r.steps.size()];
        for (int s = 0; s < r.steps.size(); s++) {
            int p0 = ptIndices.get(s);
            r.stepPtIdx[s] = p0;
            Step st = r.steps.get(s);
            if (s + 1 < r.steps.size()) {
                int p1 = ptIndices.get(s + 1);
                st.distM = Math.max(0, r.cumDistM[p1] - r.cumDistM[p0]);
                st.durS = r.totalDistM > 1 ? r.totalDurS * (st.distM / r.totalDistM) : 0;
            } else {
                st.distM = 0;
                st.durS = 0;
            }
        }
        return r;
    }

    private static boolean sameRoadName(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private static double windowBearing(double[] pts, double[] cum, int idx, int dir) {
        int nPts = pts.length / 2;
        int other = idx;
        while ((dir < 0 ? other > 0 : other < nPts - 1)) {
            other += dir;
            if (Math.abs(cum[other] - cum[idx]) >= 18.0) break;
        }
        if (other == idx) return 0;
        double la1 = dir < 0 ? pts[other * 2] : pts[idx * 2];
        double lo1 = dir < 0 ? pts[other * 2 + 1] : pts[idx * 2 + 1];
        double la2 = dir < 0 ? pts[idx * 2] : pts[other * 2];
        double lo2 = dir < 0 ? pts[idx * 2 + 1] : pts[other * 2 + 1];
        double kx = Math.cos(Math.toRadians((la1 + la2) * 0.5));
        double dx = (lo2 - lo1) * kx, dy = la2 - la1;
        return (Math.toDegrees(Math.atan2(dx, dy)) + 360.0) % 360.0;
    }

    private static int turnToIcon(double turnDeg) {
        double abs = Math.abs(turnDeg);
        if (abs >= 155) return 4;
        if (turnDeg >= 120) return 3;
        if (turnDeg >= 45) return 2;
        if (turnDeg >= 20) return 1;
        if (turnDeg <= -120) return 5;
        if (turnDeg <= -45) return 6;
        if (turnDeg <= -20) return 7;
        return 0;
    }

    private static String offlineTurnText(int icon, String street) {
        boolean hasSt = street != null && street.length() > 0;
        if (icon == 9) return "Llegaste a tu destino";
        if (icon == 8) return hasSt ? "En la rotonda, sigue hacia " + street : "En la rotonda, toma la salida";
        if (icon == 4) return hasSt ? "Haz un giro en U en " + street : "Haz un giro en U";
        if (icon == 1) return hasSt ? "Gira levemente a la derecha en " + street : "Gira levemente a la derecha";
        if (icon == 2) return hasSt ? "Gira a la derecha en " + street : "Gira a la derecha";
        if (icon == 3) return hasSt ? "Gira fuerte a la derecha en " + street : "Gira fuerte a la derecha";
        if (icon == 7) return hasSt ? "Gira levemente a la izquierda en " + street : "Gira levemente a la izquierda";
        if (icon == 6) return hasSt ? "Gira a la izquierda en " + street : "Gira a la izquierda";
        if (icon == 5) return hasSt ? "Gira fuerte a la izquierda en " + street : "Gira fuerte a la izquierda";
        return hasSt ? "Continúa por " + street : "Sigue recto";
    }

    private static void saveActiveFile(Context c, Route r) {
        try {
            File dst = new File(c.getFilesDir(), "nav_active.json");
            File tmp = new File(c.getFilesDir(), "nav_active.tmp");
            JSONObject obj = routeToJson(r);
            obj.put("savedAt", System.currentTimeMillis());
            obj.put("ptIdx", curPtIdx);
            obj.put("stepIdx", curStepIdx);
            FileOutputStream os = new FileOutputStream(tmp);
            os.write(obj.toString().getBytes("UTF-8"));
            os.close();
            if (!tmp.renameTo(dst)) {
                dst.delete();
                tmp.renameTo(dst);
            }
        } catch (Throwable ignored) { }
    }

    private static void cacheDestRoute(Context c, Route r) {
        try {
            File dir = new File(c.getCacheDir(), "nav_routes");
            dir.mkdirs();
            String name = Math.round(r.destLat * 10000.0) + "_" + Math.round(r.destLon * 10000.0) + ".json";
            File dst = new File(dir, name);
            FileOutputStream os = new FileOutputStream(dst);
            os.write(routeToJson(r).toString().getBytes("UTF-8"));
            os.close();
            File[] fs = dir.listFiles();
            if (fs != null && fs.length > 12) {
                Arrays.sort(fs, new Comparator<File>() {
                    public int compare(File a, File b) {
                        long d = a.lastModified() - b.lastModified();
                        return d > 0 ? 1 : d < 0 ? -1 : 0;
                    }
                });
                for (int i = 0; i < fs.length - 12; i++) fs[i].delete();
            }
        } catch (Throwable ignored) { }
    }

    private static Route loadCachedRoute(Context c, double lat1, double lon1, double lat2, double lon2) {
        try {
            File f = new File(new File(c.getCacheDir(), "nav_routes"),
                    Math.round(lat2 * 10000.0) + "_" + Math.round(lon2 * 10000.0) + ".json");
            if (!f.isFile() || f.length() == 0) return null;
            Route r = routeFromJson(new JSONObject(readUtf8(f)));
            if (r == null) return null;
            int nPts = r.pts.length / 2;
            double[] near = closestOnRange(r.pts, lat1, lon1, 0, nPts - 2);
            if (near[0] > 3000) return null; // solo útil si estás a <= 3 km del trazo guardado
            r.offline = true;
            r.source = "cache";
            r.trafficDelayS = 0;
            return r;
        } catch (Throwable e) {
            return null;
        }
    }

    private static JSONObject routeToJson(Route r) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("poly", encodePolyline(r.pts));
        obj.put("dist", r.totalDistM);
        obj.put("dur", r.totalDurS);
        obj.put("tdel", r.trafficDelayS);
        obj.put("dn", r.destName != null ? r.destName : "");
        obj.put("dla", r.destLat);
        obj.put("dlo", r.destLon);
        obj.put("off", r.offline);
        obj.put("src", r.source != null ? r.source : "osrm");
        if (r.segMaxSpeedKmh != null && r.segMaxSpeedKmh.length > 0) {
            JSONArray rle = new JSONArray();
            short cur = r.segMaxSpeedKmh[0];
            int run = 1;
            for (int i = 1; i < r.segMaxSpeedKmh.length; i++) {
                if (r.segMaxSpeedKmh[i] == cur) run++;
                else { rle.put(run).put(cur); cur = r.segMaxSpeedKmh[i]; run = 1; }
            }
            rle.put(run).put(cur);
            obj.put("sprle", rle);
        }
        if (!r.waypoints.isEmpty()) {
            JSONArray wa = new JSONArray();
            for (Place p : r.waypoints) wa.put(placeToJson(p));
            obj.put("wps", wa);
        }
        if (!r.cameras.isEmpty()) {
            JSONArray ca = new JSONArray();
            for (Camera cam : r.cameras) {
                ca.put(new JSONObject().put("la", cam.lat).put("lo", cam.lon)
                        .put("al", cam.alongM).put("sp", cam.maxSpeedKmh));
            }
            obj.put("cams", ca);
        }
        if (!r.incidents.isEmpty()) {
            JSONArray ia = new JSONArray();
            for (Incident inc : r.incidents) {
                ia.put(new JSONObject().put("la", inc.lat).put("lo", inc.lon)
                        .put("c", inc.category).put("d", inc.delayS)
                        .put("ds", inc.desc != null ? inc.desc : ""));
            }
            obj.put("inc", ia);
        }
        JSONArray stArr = new JSONArray();
        for (int i = 0; i < r.steps.size(); i++) {
            Step st = r.steps.get(i);
            JSONObject so = new JSONObject();
            so.put("la", st.lat).put("lo", st.lon).put("d", st.distM).put("t", st.durS)
              .put("ic", st.icon).put("st", st.street != null ? st.street : "")
              .put("in", st.instruction != null ? st.instruction : "")
              .put("pi", r.stepPtIdx != null && i < r.stepPtIdx.length ? r.stepPtIdx[i] : 0);
            if (st.lanes != null && st.lanes.length > 0) {
                JSONArray la = new JSONArray();
                for (int lm : st.lanes) la.put(lm);
                so.put("ln", la);
            }
            stArr.put(so);
        }
        obj.put("steps", stArr);
        return obj;
    }

    private static Route routeFromJson(JSONObject obj) {
        String poly = obj.optString("poly", "");
        double[] pts = decodePolyline(poly);
        if (pts.length < 4) return null;
        Route r = new Route();
        r.pts = pts;
        r.totalDistM = obj.optDouble("dist", 0);
        r.totalDurS = obj.optDouble("dur", 0);
        r.trafficDelayS = Math.max(0, obj.optInt("tdel", 0));
        r.destName = obj.optString("dn", "");
        r.destLat = obj.optDouble("dla", Double.NaN);
        r.destLon = obj.optDouble("dlo", Double.NaN);
        r.offline = obj.optBoolean("off", false);
        r.source = obj.optString("src", "osrm");
        if (Double.isNaN(r.destLat) || Double.isNaN(r.destLon)) return null;

        int nPts = pts.length / 2;
        r.cumDistM = new double[nPts];
        for (int i = 1; i < nPts; i++) {
            r.cumDistM[i] = r.cumDistM[i - 1] + approxDist(pts[(i - 1) * 2], pts[(i - 1) * 2 + 1], pts[i * 2], pts[i * 2 + 1]);
        }
        JSONArray rle = obj.optJSONArray("sprle");
        if (rle != null && rle.length() >= 2) {
            short[] sp = new short[Math.max(0, nPts - 1)];
            int idx = 0;
            for (int i = 0; i + 1 < rle.length() && idx < sp.length; i += 2) {
                int run = Math.max(0, rle.optInt(i, 0));
                short val = (short) rle.optInt(i + 1, 0);
                for (int k = 0; k < run && idx < sp.length; k++) sp[idx++] = val;
            }
            r.segMaxSpeedKmh = sp;
        }
        JSONArray wa = obj.optJSONArray("wps");
        if (wa != null) {
            for (int i = 0; i < wa.length(); i++) {
                Place wp = placeFromJson(wa.optJSONObject(i));
                if (wp != null) r.waypoints.add(wp);
            }
        }
        JSONArray ca = obj.optJSONArray("cams");
        if (ca != null) {
            for (int i = 0; i < ca.length(); i++) {
                JSONObject co = ca.optJSONObject(i);
                if (co == null) continue;
                Camera cam = new Camera();
                cam.lat = co.optDouble("la", Double.NaN);
                cam.lon = co.optDouble("lo", Double.NaN);
                cam.alongM = co.optDouble("al", 0);
                cam.maxSpeedKmh = co.optInt("sp", 0);
                if (!Double.isNaN(cam.lat) && !Double.isNaN(cam.lon)) r.cameras.add(cam);
            }
        }
        JSONArray ia = obj.optJSONArray("inc");
        if (ia != null) {
            for (int i = 0; i < ia.length(); i++) {
                JSONObject io = ia.optJSONObject(i);
                if (io == null) continue;
                Incident inc = new Incident();
                inc.lat = io.optDouble("la", Double.NaN);
                inc.lon = io.optDouble("lo", Double.NaN);
                inc.category = io.optInt("c", 0);
                inc.delayS = io.optInt("d", 0);
                inc.desc = io.optString("ds", "");
                if (!Double.isNaN(inc.lat) && !Double.isNaN(inc.lon)) r.incidents.add(inc);
            }
        }
        JSONArray stArr = obj.optJSONArray("steps");
        if (stArr != null) {
            r.stepPtIdx = new int[stArr.length()];
            for (int i = 0; i < stArr.length(); i++) {
                JSONObject so = stArr.optJSONObject(i);
                if (so == null) continue;
                Step st = new Step();
                st.lat = so.optDouble("la", r.destLat);
                st.lon = so.optDouble("lo", r.destLon);
                st.distM = so.optDouble("d", 0);
                st.durS = so.optDouble("t", 0);
                st.icon = so.optInt("ic", 0);
                st.street = so.optString("st", "");
                st.instruction = so.optString("in", "");
                JSONArray la = so.optJSONArray("ln");
                if (la != null && la.length() > 0) {
                    st.lanes = new int[la.length()];
                    for (int k = 0; k < la.length(); k++) st.lanes[k] = la.optInt(k, 0);
                }
                r.steps.add(st);
                r.stepPtIdx[r.steps.size() - 1] = Math.max(0, Math.min(nPts - 1, so.optInt("pi", 0)));
            }
        }
        if (r.steps.isEmpty()) return null;
        return r;
    }

    private static String encodePolyline(double[] pts) {
        StringBuilder sb = new StringBuilder(pts.length * 2);
        int pLat = 0, pLng = 0;
        for (int i = 0; i < pts.length; i += 2) {
            int lat = (int) Math.round(pts[i] * 1e5);
            int lng = (int) Math.round(pts[i + 1] * 1e5);
            encodeSigned(lat - pLat, sb);
            encodeSigned(lng - pLng, sb);
            pLat = lat;
            pLng = lng;
        }
        return sb.toString();
    }

    private static void encodeSigned(int v, StringBuilder sb) {
        int s = v < 0 ? ~(v << 1) : (v << 1);
        while (s >= 0x20) {
            sb.append((char) ((0x20 | (s & 0x1f)) + 63));
            s >>>= 5;
        }
        sb.append((char) (s + 63));
    }

    private static String readUtf8(File f) throws Exception {
        FileInputStream is = new FileInputStream(f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) >= 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    private static Route parseOsrm(String json, double destLat, double destLon, String destName) throws Exception {
        Place p = new Place(); p.lat = destLat; p.lon = destLon; p.name = destName;
        List<Place> stops = new ArrayList<Place>(1); stops.add(p);
        List<Route> rs = parseOsrmList(json, stops);
        return rs != null && !rs.isEmpty() ? rs.get(0) : null;
    }

    public static List<Route> parseOsrmList(String json, List<Place> stops) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"Ok".equalsIgnoreCase(root.optString("code", ""))) {
            throw new Exception(root.optString("message", "No route"));
        }
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null || routes.length() == 0) return null;
        Place last = stops.get(stops.size() - 1);
        int lim = Math.min(3, routes.length());
        List<Route> out = new ArrayList<Route>(lim);
        for (int ri = 0; ri < lim; ri++) {
            JSONObject r0 = routes.getJSONObject(ri);
            String poly = r0.optString("geometry", "");
            double[] pts = decodePolyline(poly);
            if (pts.length < 4) continue;

            Route r = new Route();
            r.pts = pts;
            r.totalDistM = r0.optDouble("distance", 0);
            r.totalDurS = r0.optDouble("duration", 0);
            r.destLat = last.lat;
            r.destLon = last.lon;
            r.destName = last.name;
            for (int wi = 0; wi + 1 < stops.size(); wi++) r.waypoints.add(stops.get(wi));
            r.source = "osrm";
            r.offline = false;

            int nPts = pts.length / 2;
            r.cumDistM = new double[nPts];
            r.cumDistM[0] = 0;
            for (int i = 1; i < nPts; i++) {
                r.cumDistM[i] = r.cumDistM[i - 1] + approxDist(pts[(i - 1) * 2], pts[(i - 1) * 2 + 1], pts[i * 2], pts[i * 2 + 1]);
            }

            List<Short> rawMaxSpeeds = new ArrayList<Short>();
            boolean hasAnySpeed = false;
            JSONArray legs = r0.optJSONArray("legs");
            if (legs != null) {
                for (int li = 0; li < legs.length(); li++) {
                    JSONObject leg = legs.getJSONObject(li);
                    JSONObject ann = leg.optJSONObject("annotation");
                    if (ann != null) {
                        JSONArray msArr = ann.optJSONArray("maxspeed");
                        if (msArr != null) {
                            for (int mi = 0; mi < msArr.length(); mi++) {
                                short kmh = parseOsrmMaxSpeedItem(msArr.opt(mi));
                                if (kmh > 0) hasAnySpeed = true;
                                rawMaxSpeeds.add(kmh);
                            }
                        }
                    }
                    JSONArray stArr = leg.optJSONArray("steps");
                    if (stArr != null) {
                        boolean midLeg = li < legs.length() - 1;
                        for (int i = 0; i < stArr.length(); i++) {
                            JSONObject sObj = stArr.getJSONObject(i);
                            Step st = new Step();
                            st.distM = sObj.optDouble("distance", 0);
                            st.durS = sObj.optDouble("duration", 0);
                            String name = sObj.optString("name", "").trim();
                            String ref = sObj.optString("ref", "").trim();
                            st.street = name.length() > 0 ? name : ref;
                            JSONObject man = sObj.optJSONObject("maneuver");
                            String type = man != null ? man.optString("type", "") : "";
                            String mod = man != null ? man.optString("modifier", "") : "";
                            int exit = man != null ? man.optInt("exit", 0) : 0;
                            JSONArray loc = man != null ? man.optJSONArray("location") : null;
                            if (loc != null && loc.length() >= 2) {
                                st.lon = loc.optDouble(0, last.lon);
                                st.lat = loc.optDouble(1, last.lat);
                            } else {
                                st.lat = last.lat;
                                st.lon = last.lon;
                            }
                            if ("arrive".equals(type) && midLeg) {
                                Place wp = li < stops.size() ? stops.get(li) : null;
                                st.icon = 9;
                                st.street = wp != null && wp.name != null ? wp.name : "";
                                st.instruction = st.street.length() > 0 ? "Parada: " + st.street : "Parada intermedia";
                            } else {
                                st.icon = maneuverIcon(type, mod);
                                st.instruction = maneuverText(type, mod, exit, st.street);
                            }
                            JSONArray ints = sObj.optJSONArray("intersections");
                            if (ints != null && ints.length() > 0) {
                                JSONObject i0 = ints.optJSONObject(0);
                                if (i0 != null) st.lanes = parseOsrmLanes(i0.optJSONArray("lanes"));
                            }
                            r.steps.add(st);
                        }
                    }
                }
            }
            if (hasAnySpeed && nPts >= 2) {
                int nSeg = nPts - 1;
                short[] sp = new short[nSeg];
                int limS = Math.min(nSeg, rawMaxSpeeds.size());
                for (int i = 0; i < limS; i++) sp[i] = rawMaxSpeeds.get(i);
                r.segMaxSpeedKmh = sp;
            }
            if (r.steps.isEmpty()) {
                Step arr = new Step();
                arr.lat = last.lat; arr.lon = last.lon;
                arr.distM = r.totalDistM; arr.durS = r.totalDurS;
                arr.icon = 9;
                arr.street = last.name != null ? last.name : "";
                arr.instruction = "Llegaste a tu destino";
                r.steps.add(arr);
            }

            // asociar cada paso al vértice más cercano avanzando en orden
            r.stepPtIdx = new int[r.steps.size()];
            int searchFrom = 0;
            for (int i = 0; i < r.steps.size(); i++) {
                Step st = r.steps.get(i);
                if (st.icon == 9 && i == r.steps.size() - 1) {
                    r.stepPtIdx[i] = nPts - 1;
                    continue;
                }
                int best = searchFrom;
                double bestD = Double.MAX_VALUE;
                for (int k = searchFrom; k < nPts; k++) {
                    double d = approxDist(st.lat, st.lon, pts[k * 2], pts[k * 2 + 1]);
                    if (d < bestD) { bestD = d; best = k; }
                    if (bestD < 15 && d > 120) break;
                }
                r.stepPtIdx[i] = best;
                searchFrom = best;
            }
            out.add(r);
        }
        return out.isEmpty() ? null : out;
    }

    private static short parseOsrmMaxSpeedItem(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) {
            int v = ((Number) obj).intValue();
            return (v >= 5 && v <= 220) ? (short) v : 0;
        }
        if (obj instanceof JSONObject) {
            JSONObject o = (JSONObject) obj;
            if (o.optBoolean("unknown", false) || o.optBoolean("none", false)) return 0;
            int sp = o.optInt("speed", 0);
            if (sp <= 0) return 0;
            if ("mph".equalsIgnoreCase(o.optString("unit", ""))) sp = (int) Math.round(sp * 1.60934);
            return (sp >= 5 && sp <= 220) ? (short) sp : 0;
        }
        return 0;
    }

    static int[] parseOsrmLanes(JSONArray la) {
        if (la == null || la.length() == 0) return null;
        int n = Math.min(8, la.length());
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            JSONObject lo = la.optJSONObject(i);
            if (lo == null) continue;
            int mask = lo.optBoolean("valid", false) ? LANE_VALID : 0;
            JSONArray ind = lo.optJSONArray("indications");
            if (ind != null) {
                for (int j = 0; j < ind.length(); j++) {
                    String s = ind.optString(j, "");
                    if ("straight".equals(s)) mask |= LANE_STRAIGHT;
                    else if ("slight left".equals(s)) mask |= LANE_SLIGHT_LEFT;
                    else if ("left".equals(s)) mask |= LANE_LEFT;
                    else if ("sharp left".equals(s)) mask |= LANE_SHARP_LEFT;
                    else if ("slight right".equals(s)) mask |= LANE_SLIGHT_RIGHT;
                    else if ("right".equals(s)) mask |= LANE_RIGHT;
                    else if ("sharp right".equals(s)) mask |= LANE_SHARP_RIGHT;
                    else if ("uturn".equals(s)) mask |= LANE_UTURN;
                }
            }
            if ((mask & 255) == 0) mask |= LANE_STRAIGHT;
            out[i] = mask;
        }
        return out;
    }

    private static int maneuverIcon(String type, String mod) {
        if ("arrive".equals(type)) return 9;
        if ("roundabout".equals(type) || "rotary".equals(type) || "exit roundabout".equals(type) || "exit rotary".equals(type)) return 8;
        if ("uturn".equals(mod)) return 4;
        if ("slight right".equals(mod)) return 1;
        if ("right".equals(mod)) return 2;
        if ("sharp right".equals(mod)) return 3;
        if ("sharp left".equals(mod)) return 5;
        if ("left".equals(mod)) return 6;
        if ("slight left".equals(mod)) return 7;
        return 0;
    }

    /** Convierte una respuesta de TomTom Routing v1 (con traffic=true) a Nav.Route. */
    public static Route parseTomTom(String json, double destLat, double destLon, String destName) throws Exception {
        Place p = new Place(); p.lat = destLat; p.lon = destLon; p.name = destName;
        List<Place> stops = new ArrayList<Place>(1); stops.add(p);
        List<Route> rs = parseTomTomList(json, stops);
        return rs != null && !rs.isEmpty() ? rs.get(0) : null;
    }

    public static List<Route> parseTomTomList(String json, List<Place> stops) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject de = root.optJSONObject("detailedError");
        String errTxt = root.optString("errorText", "");
        if (de != null || errTxt.length() > 0) {
            String msg = de != null ? de.optString("message", "") : "";
            if (msg.length() == 0) msg = errTxt.length() > 0 ? errTxt : "TomTom error";
            throw new Exception(msg);
        }
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null || routes.length() == 0) return null;
        Place last = stops.get(stops.size() - 1);
        int lim = Math.min(3, routes.length());
        List<Route> out = new ArrayList<Route>(lim);
        for (int ri = 0; ri < lim; ri++) {
            Route r = parseTomTomSingle(routes.getJSONObject(ri), last.lat, last.lon, last.name);
            if (r != null) {
                for (int wi = 0; wi + 1 < stops.size(); wi++) r.waypoints.add(stops.get(wi));
                out.add(r);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static Route parseTomTomSingle(JSONObject r0, double destLat, double destLon, String destName) throws Exception {
        JSONObject sum = r0.optJSONObject("summary");
        JSONArray legs = r0.optJSONArray("legs");
        if (legs == null || legs.length() == 0) return null;

        int nPts = 0;
        for (int li = 0; li < legs.length(); li++) {
            JSONArray pa = legs.getJSONObject(li).optJSONArray("points");
            if (pa != null) nPts += pa.length();
        }
        if (nPts < 2) return null;

        double[] pts = new double[nPts * 2];
        int pIdx = 0;
        for (int li = 0; li < legs.length(); li++) {
            JSONArray pa = legs.getJSONObject(li).optJSONArray("points");
            if (pa == null) continue;
            for (int pi = 0; pi < pa.length(); pi++) {
                JSONObject pt = pa.getJSONObject(pi);
                double la = pt.optDouble("latitude", Double.NaN);
                double lo = pt.optDouble("longitude", Double.NaN);
                if (Double.isNaN(la) || Double.isNaN(lo)) return null;
                pts[pIdx * 2] = la;
                pts[pIdx * 2 + 1] = lo;
                pIdx++;
            }
        }

        Route r = new Route();
        r.pts = pts;
        r.cumDistM = new double[nPts];
        for (int i = 1; i < nPts; i++) {
            r.cumDistM[i] = r.cumDistM[i - 1] + approxDist(pts[(i - 1) * 2], pts[(i - 1) * 2 + 1], pts[i * 2], pts[i * 2 + 1]);
        }
        r.totalDistM = sum != null ? sum.optDouble("lengthInMeters", 0) : 0;
        if (r.totalDistM <= 0) r.totalDistM = r.cumDistM[nPts - 1];
        r.totalDurS = sum != null ? sum.optDouble("travelTimeInSeconds", 0) : 0;
        r.trafficDelayS = sum != null ? Math.max(0, sum.optInt("trafficDelayInSeconds", 0)) : 0;
        r.destLat = destLat;
        r.destLon = destLon;
        r.destName = destName;
        r.source = "tomtom";
        r.offline = false;

        JSONArray secs = r0.optJSONArray("sections");
        if (secs != null && nPts >= 2) {
            short[] sp = new short[nPts - 1];
            boolean hasSp = false;
            for (int si = 0; si < secs.length(); si++) {
                JSONObject sc = secs.optJSONObject(si);
                if (sc == null || !"SPEED_LIMIT".equals(sc.optString("sectionType", ""))) continue;
                int kmh = sc.optInt("maxSpeedLimitInKmh", 0);
                if (kmh <= 0 || kmh > 220) continue;
                int a = Math.max(0, sc.optInt("startPointIndex", 0));
                int b = Math.min(nPts - 2, sc.optInt("endPointIndex", a));
                for (int k = a; k <= b; k++) sp[k] = (short) kmh;
                hasSp = true;
            }
            if (hasSp) r.segMaxSpeedKmh = sp;
        }

        List<Integer> stepPts = new ArrayList<Integer>();
        JSONObject guid = r0.optJSONObject("guidance");
        JSONArray insts = guid != null ? guid.optJSONArray("instructions") : null;
        if (insts != null) {
            int lastPt = 0;
            for (int i = 0; i < insts.length(); i++) {
                JSONObject ins = insts.getJSONObject(i);
                String man = ins.optString("maneuver", "");
                if ("WAYPOINT_REACHED".equals(man) && i < insts.length() - 1) continue;
                double offM = ins.optDouble("routeOffsetInMeters", 0);
                double tS = ins.optDouble("travelTimeInSeconds", 0);
                double nextOffM = r.totalDistM, nextTS = r.totalDurS;
                for (int j = i + 1; j < insts.length(); j++) {
                    JSONObject nxt = insts.getJSONObject(j);
                    if ("WAYPOINT_REACHED".equals(nxt.optString("maneuver", "")) && j < insts.length() - 1) continue;
                    nextOffM = nxt.optDouble("routeOffsetInMeters", r.totalDistM);
                    nextTS = nxt.optDouble("travelTimeInSeconds", r.totalDurS);
                    break;
                }
                Step st = new Step();
                st.distM = Math.max(0, nextOffM - offM);
                st.durS = Math.max(0, nextTS - tS);
                st.street = ins.optString("street", "").trim();
                JSONObject pt = ins.optJSONObject("point");
                if (pt != null) {
                    st.lat = pt.optDouble("latitude", destLat);
                    st.lon = pt.optDouble("longitude", destLon);
                } else {
                    st.lat = destLat;
                    st.lon = destLon;
                }
                st.icon = tomtomIcon(man);
                String msg = ins.optString("message", "").trim();
                st.instruction = msg.length() > 0 ? msg : offlineTurnText(st.icon, st.street);
                r.steps.add(st);

                int idx = ins.optInt("pointIndex", -1);
                if (idx >= 0 && idx < nPts) {
                    if (idx < lastPt) idx = lastPt;
                } else if (st.icon == 9) {
                    idx = nPts - 1;
                } else {
                    int best = lastPt;
                    double bestD = Double.MAX_VALUE;
                    for (int k = lastPt; k < nPts; k++) {
                        double d = approxDist(st.lat, st.lon, pts[k * 2], pts[k * 2 + 1]);
                        if (d < bestD) { bestD = d; best = k; }
                        if (bestD < 15 && d > 120) break;
                    }
                    idx = best;
                }
                stepPts.add(idx);
                lastPt = idx;
            }
        }
        if (r.steps.isEmpty()) {
            Step arr = new Step();
            arr.lat = destLat; arr.lon = destLon;
            arr.distM = r.totalDistM; arr.durS = r.totalDurS;
            arr.icon = 9;
            arr.street = destName != null ? destName : "";
            arr.instruction = "Llegaste a tu destino";
            r.steps.add(arr);
            stepPts.add(nPts - 1);
        }
        r.stepPtIdx = new int[r.steps.size()];
        for (int i = 0; i < r.steps.size(); i++) r.stepPtIdx[i] = stepPts.get(i);
        return r;
    }

    /** Consulta única de incidentes (cierres/accidentes) de TomTom v5 dentro de la caja de la ruta. */
    private static void checkRouteIncidents(Context c, String ttKey, List<Route> routes) {
        if (routes == null || routes.isEmpty()) return;
        Route r0 = routes.get(0);
        if (r0.pts == null || r0.pts.length < 4) return;
        double minLa = 90, maxLa = -90, minLo = 180, maxLo = -180;
        int nPts = r0.pts.length / 2;
        for (int i = 0; i < nPts; i++) {
            double la = r0.pts[i * 2], lo = r0.pts[i * 2 + 1];
            if (la < minLa) minLa = la; if (la > maxLa) maxLa = la;
            if (lo < minLo) minLo = lo; if (lo > maxLo) maxLo = lo;
        }
        minLa -= 0.008; maxLa += 0.008; minLo -= 0.008; maxLo += 0.008;
        if (maxLa - minLa > 0.85) { double cLat = r0.pts[0]; minLa = cLat - 0.42; maxLa = cLat + 0.42; }
        if (maxLo - minLo > 0.85) { double cLon = r0.pts[1]; minLo = cLon - 0.42; maxLo = cLon + 0.42; }
        try {
            String fields = "{incidents{type,geometry{type,coordinates},properties{iconCategory,magnitudeOfDelay,events{description},delay,roadNumbers}}}";
            String url = String.format(Locale.US,
                    "https://api.tomtom.com/traffic/services/5/incidentDetails?key=%s&bbox=%.5f,%.5f,%.5f,%.5f&fields=%s&language=%s&timeValidityFilter=present",
                    URLEncoder.encode(ttKey, "UTF-8"),
                    minLo, minLa, maxLo, maxLa,
                    URLEncoder.encode(fields, "UTF-8"),
                    URLEncoder.encode(tomtomLang(), "UTF-8"));
            String raw = new String(navGet(c, url, 5500), "UTF-8");
            List<Incident> all = parseTomTomIncidents(raw);
            if (all.isEmpty()) return;
            for (Route r : routes) {
                int np = r.pts.length / 2;
                if (np < 2) continue;
                for (Incident inc : all) {
                    if (r.incidents.size() >= 8) break;
                    double[] cl = closestOnRange(r.pts, inc.lat, inc.lon, 0, np - 2);
                    if (cl[0] <= 150.0) r.incidents.add(inc);
                }
            }
        } catch (Throwable ignored) { }
    }

    public static List<Incident> parseTomTomIncidents(String json) {
        List<Incident> out = new ArrayList<Incident>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("incidents");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                if (it == null) continue;
                JSONObject prop = it.optJSONObject("properties");
                if (prop == null) continue;
                int cat = prop.optInt("iconCategory", 0);
                int del = prop.optInt("delay", 0);
                // priorizar cierres (7,8), accidentes (1), obras (9) y atascos fuertes (6)
                if (cat != 1 && cat != 6 && cat != 7 && cat != 8 && cat != 9 && del < 180) continue;
                JSONObject geom = it.optJSONObject("geometry");
                JSONArray coords = geom != null ? geom.optJSONArray("coordinates") : null;
                if (coords == null || coords.length() == 0) continue;
                double lo = Double.NaN, la = Double.NaN;
                Object first = coords.opt(0);
                if (first instanceof Number) {
                    lo = coords.optDouble(0, Double.NaN);
                    la = coords.optDouble(1, Double.NaN);
                } else if (first instanceof JSONArray) {
                    JSONArray p0 = (JSONArray) first;
                    lo = p0.optDouble(0, Double.NaN);
                    la = p0.optDouble(1, Double.NaN);
                }
                if (Double.isNaN(la) || Double.isNaN(lo)) continue;
                Incident inc = new Incident();
                inc.lat = la;
                inc.lon = lo;
                inc.category = cat;
                inc.delayS = Math.max(0, del);
                String desc = "";
                JSONArray evs = prop.optJSONArray("events");
                if (evs != null && evs.length() > 0) {
                    JSONObject e0 = evs.optJSONObject(0);
                    if (e0 != null) desc = e0.optString("description", "").trim();
                }
                if (desc.length() == 0) desc = incidentLabel(cat);
                JSONArray roads = prop.optJSONArray("roadNumbers");
                String rd = roads != null && roads.length() > 0 ? roads.optString(0, "").trim() : "";
                if (rd.length() > 0 && !desc.contains(rd)) desc = rd + " · " + desc;
                inc.desc = desc;
                out.add(inc);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public static String incidentLabel(int category) {
        if (category == 8) return I18n.t("Vía cerrada");
        if (category == 7) return I18n.t("Carril cerrado");
        if (category == 1) return I18n.t("Accidente");
        if (category == 9) return I18n.t("Obras en la vía");
        if (category == 6) return I18n.t("Tráfico detenido");
        return I18n.t("Incidente en la vía");
    }

    /** Escanea radares fijos de velocidad en el mapa .map cerca de las rutas calculadas. */
    private static void scanRouteCameras(List<Route> routes) {
        OfflineMap fm = LiveMapView.fileMap;
        if (fm == null || routes == null || routes.isEmpty()) return;
        double minLa = 90, maxLa = -90, minLo = 180, maxLo = -180;
        for (Route r : routes) {
            if (r.pts == null) continue;
            for (int i = 0; i < r.pts.length; i += 2) {
                double la = r.pts[i], lo = r.pts[i + 1];
                if (la < minLa) minLa = la; if (la > maxLa) maxLa = la;
                if (lo < minLo) minLo = lo; if (lo > maxLo) maxLo = lo;
            }
        }
        ArrayList<double[]> rawCams = fm.findSpeedCameras(minLa - 0.004, minLo - 0.004, maxLa + 0.004, maxLo + 0.004);
        if (rawCams == null || rawCams.isEmpty()) return;
        attachCameras(routes, rawCams);
    }

    static void attachCameras(List<Route> routes, List<double[]> rawCams) {
        for (Route r : routes) {
            int nPts = r.pts != null ? r.pts.length / 2 : 0;
            if (nPts < 2) continue;
            for (double[] rc : rawCams) {
                double la = rc[0], lo = rc[1];
                int sp = rc.length >= 3 ? (int) Math.round(rc[2]) : 0;
                double[] cl = closestOnRange(r.pts, la, lo, 0, nPts - 2);
                if (cl[0] <= 45.0) {
                    int seg = (int) cl[1];
                    double segLen = r.cumDistM[seg + 1] - r.cumDistM[seg];
                    Camera cam = new Camera();
                    cam.lat = la;
                    cam.lon = lo;
                    cam.alongM = r.cumDistM[seg] + segLen * cl[2];
                    cam.maxSpeedKmh = sp;
                    r.cameras.add(cam);
                }
            }
            Collections.sort(r.cameras, new Comparator<Camera>() {
                public int compare(Camera a, Camera b) {
                    return a.alongM < b.alongM ? -1 : a.alongM > b.alongM ? 1 : 0;
                }
            });
        }
    }

    private static int tomtomIcon(String man) {
        if (man == null) return 0;
        if (man.startsWith("ARRIVE") || "WAYPOINT_REACHED".equals(man)) return 9;
        if (man.indexOf("ROUNDABOUT") >= 0 || man.indexOf("ROTARY") >= 0) return 8;
        if (man.indexOf("UTURN") >= 0) return 4;
        if ("SHARP_RIGHT".equals(man)) return 3;
        if ("SHARP_LEFT".equals(man)) return 5;
        if (man.indexOf("SLIGHT_RIGHT") >= 0 || man.indexOf("BEAR_RIGHT") >= 0
                || man.indexOf("KEEP_RIGHT") >= 0 || man.indexOf("FORK_RIGHT") >= 0
                || man.indexOf("EXIT_RIGHT") >= 0) return 1;
        if (man.indexOf("SLIGHT_LEFT") >= 0 || man.indexOf("BEAR_LEFT") >= 0
                || man.indexOf("KEEP_LEFT") >= 0 || man.indexOf("FORK_LEFT") >= 0
                || man.indexOf("EXIT_LEFT") >= 0) return 7;
        if (man.indexOf("RIGHT") >= 0) return 2;
        if (man.indexOf("LEFT") >= 0) return 6;
        return 0;
    }

    private static String maneuverText(String type, String mod, int exit, String street) {
        boolean hasSt = street != null && street.length() > 0;
        if ("arrive".equals(type)) return "Llegaste a tu destino";
        if ("depart".equals(type)) return hasSt ? "Sal por " + street : "Inicia el recorrido";
        if ("roundabout".equals(type) || "rotary".equals(type)) {
            if (exit > 0) return hasSt ? "En la rotonda, salida " + exit + " hacia " + street : "En la rotonda, toma la salida " + exit;
            return hasSt ? "En la rotonda, sigue hacia " + street : "En la rotonda, toma la salida";
        }
        if ("uturn".equals(mod)) return hasSt ? "Haz un giro en U en " + street : "Haz un giro en U";
        if ("slight right".equals(mod)) return hasSt ? "Gira levemente a la derecha en " + street : "Gira levemente a la derecha";
        if ("right".equals(mod)) return hasSt ? "Gira a la derecha en " + street : "Gira a la derecha";
        if ("sharp right".equals(mod)) return hasSt ? "Gira fuerte a la derecha en " + street : "Gira fuerte a la derecha";
        if ("slight left".equals(mod)) return hasSt ? "Gira levemente a la izquierda en " + street : "Gira levemente a la izquierda";
        if ("left".equals(mod)) return hasSt ? "Gira a la izquierda en " + street : "Gira a la izquierda";
        if ("sharp left".equals(mod)) return hasSt ? "Gira fuerte a la izquierda en " + street : "Gira fuerte a la izquierda";
        if ("merge".equals(type) || "on ramp".equals(type) || "off ramp".equals(type)) {
            return hasSt ? "Incorpórate a " + street : "Toma el desvío";
        }
        return hasSt ? "Continúa por " + street : "Sigue recto";
    }

    private static double[] decodePolyline(String encoded) {
        List<Double> coords = new ArrayList<Double>(256);
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < len);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            while (index < len) {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
                if (b < 0x20) break;
            }
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            coords.add(lat / 1e5);
            coords.add(lng / 1e5);
        }
        double[] out = new double[coords.size()];
        for (int i = 0; i < out.length; i++) out[i] = coords.get(i);
        return out;
    }

    // ───────────────────────── seguimiento GPS en vivo ─────────────────────────

    public static void onLocation(final Context c, Location l, boolean mph, final Runnable onUpdate) {
        final Route r = active;
        if (r == null || l == null || r.pts == null || r.pts.length < 4) return;
        double lat = l.getLatitude(), lon = l.getLongitude();
        int nPts = r.pts.length / 2;

        // buscar segmento más cercano primero en una ventana local alrededor de curPtIdx
        int lo = Math.max(0, curPtIdx - 8);
        int hi = Math.min(nPts - 2, curPtIdx + 60);
        double[] res = closestOnRange(r.pts, lat, lon, lo, hi);
        if (res[0] > 55 && (lo > 0 || hi < nPts - 2)) {
            double[] full = closestOnRange(r.pts, lat, lon, 0, nPts - 2);
            if (full[0] < res[0]) res = full;
        }
        double crossM = res[0];
        int segIdx = (int) res[1];
        double segT = res[2];

        curPtIdx = segIdx;
        double segLen = r.cumDistM[segIdx + 1] - r.cumDistM[segIdx];
        double alongM = r.cumDistM[segIdx] + segLen * segT;
        double totalM = r.cumDistM[nPts - 1];
        remDistM = Math.max(0, totalM - alongM);
        trafficDelayS = totalM > 1 && r.trafficDelayS > 0 ? (int) Math.round(r.trafficDelayS * (remDistM / totalM)) : 0;
        double baseRemS = totalM > 1 ? r.totalDurS * (remDistM / totalM) : 0;
        long nowSp = SystemClock.elapsedRealtime();
        if (l.hasSpeed()) {
            float sp = l.getSpeed();
            if (sp >= 0.8f && sp <= 55.0f) {
                if (lastSpeedAt == 0 || nowSp - lastSpeedAt > 30000L) {
                    smoothSpeedMs = sp;
                    smoothSpeedSamples = 1;
                } else {
                    smoothSpeedMs = smoothSpeedMs * 0.82 + sp * 0.18;
                    smoothSpeedSamples++;
                }
                lastSpeedAt = nowSp;
            } else if (lastSpeedAt > 0 && nowSp - lastSpeedAt > 30000L) {
                smoothSpeedSamples = 0;
            }
        }
        if (smoothSpeedSamples >= 8 && baseRemS > 1 && remDistM > 50) {
            double planSpeed = r.totalDistM > 1 && r.totalDurS > 1 ? r.totalDistM / r.totalDurS : 0;
            if (planSpeed > 1.0 && smoothSpeedMs > 0.8 && smoothSpeedMs < planSpeed * 0.78) {
                double nearM = Math.min(remDistM, 3000.0);
                double nearPlanS = baseRemS * (nearM / remDistM);
                double nearLiveS = nearM / smoothSpeedMs;
                double maxAdded = Math.min(2400.0, nearPlanS * 1.5);
                baseRemS += Math.min(maxAdded, Math.max(0.0, nearLiveS - nearPlanS));
            }
        }
        remDurS = baseRemS;

        // llegada al destino
        double distDestM = approxDist(lat, lon, r.destLat, r.destLon);
        if (distDestM < 35 && (remDistM < 60 || segIdx >= nPts - 4)) {
            if (!r.steps.isEmpty()) speakStep(c, r.steps.get(r.steps.size() - 1), r.steps.size() - 1, false);
            stop(c);
            if (onUpdate != null) onUpdate.run();
            return;
        }

        // próximo paso por delante de alongM
        int prevStep = curStepIdx;
        int nextStep = r.steps.size() - 1;
        for (int i = 1; i < r.steps.size(); i++) {
            int spi = r.stepPtIdx[i];
            if (r.cumDistM[spi] > alongM + 8) {
                nextStep = i;
                break;
            }
        }
        curStepIdx = nextStep;
        int targetVert = r.stepPtIdx[nextStep];
        distToStepM = Math.max(0, r.cumDistM[targetVert] - alongM);
        if (nextStep != prevStep && c != null) {
            final Context app = c.getApplicationContext();
            new Thread(new Runnable() {
                public void run() { saveActiveFile(app, r); }
            }).start();
        }

        // voz guía según distancia restante al giro
        if (nextStep >= 0 && nextStep < r.steps.size() && spokenMask != null && nextStep < spokenMask.length) {
            Step st = r.steps.get(nextStep);
            double prevDist = nextStep > 0 ? r.steps.get(nextStep - 1).distM : r.totalDistM;
            int m = spokenMask[nextStep];
            if (distToStepM <= 45 && (m & 4) == 0) {
                spokenMask[nextStep] = m | 4;
                speakStep(c, st, nextStep, false);
            } else if (distToStepM <= 160 && prevDist > 240 && (m & 2) == 0) {
                spokenMask[nextStep] = m | 2;
                speakStep(c, st, nextStep, true, mph ? "500 pies" : "150 metros");
            } else if (distToStepM <= 520 && prevDist > 750 && (m & 1) == 0) {
                spokenMask[nextStep] = m | 1;
                speakStep(c, st, nextStep, true, mph ? "un cuarto de milla" : "500 metros");
            }
        }

        // límite de velocidad y radar de velocidad cercano
        SharedPreferences sp = c != null ? c.getSharedPreferences("launcher", 0) : null;
        curSpeedLimitKmh = r.segMaxSpeedKmh != null && segIdx < r.segMaxSpeedKmh.length ? r.segMaxSpeedKmh[segIdx] : 0;
        boolean spAlertOn = sp != null && sp.getBoolean("nav_speed_alert", false);
        if (spAlertOn && curSpeedLimitKmh > 0 && l.hasSpeed()) {
            int tolKmh = sp != null ? sp.getInt("nav_speed_tol", 8) : 8;
            double curKmh = l.getSpeed() * 3.6;
            boolean over = curKmh >= curSpeedLimitKmh + tolKmh;
            if (over && (!speedingAlert || nowSp - lastSpeedChimeAt > 18000L)) {
                lastSpeedChimeAt = nowSp;
                if (sp != null && sp.getBoolean("nav_speed_sound", false)) {
                    playAlertTone(ToneGenerator.TONE_PROP_BEEP2, 240);
                }
            }
            speedingAlert = over;
        } else {
            speedingAlert = false;
        }

        Camera foundCam = null;
        int foundCamIdx = -1;
        double foundCamDist = 0;
        boolean camOn = sp != null && sp.getBoolean("nav_speed_cams", false);
        if (camOn && !r.cameras.isEmpty()) {
            for (int ci = 0; ci < r.cameras.size(); ci++) {
                Camera cam = r.cameras.get(ci);
                if (cam.alongM >= alongM - 25 && cam.alongM - alongM <= 350) {
                    foundCam = cam;
                    foundCamIdx = ci;
                    foundCamDist = Math.max(0, cam.alongM - alongM);
                    break;
                }
            }
        }
        nearbyCamera = foundCam;
        nearbyCameraDistM = foundCamDist;
        if (foundCam != null && (foundCamIdx != lastCamIdx || nowSp - lastCamChimeAt > 35000L)) {
            lastCamIdx = foundCamIdx;
            lastCamChimeAt = nowSp;
            playAlertTone(ToneGenerator.TONE_PROP_ACK, 260);
            if (c != null) speakRaw(c, I18n.t("Radar de velocidad adelante"));
        }

        // desvío de ruta -> recalcular (conservando paradas aún pendientes)
        boolean goodFix = !l.hasAccuracy() || l.getAccuracy() <= 55;
        if (crossM <= 45) {
            offRouteCount = 0;
            offlineOffRoute = false;
            offRouteM = 0;
        } else if (crossM > 65 && goodFix) {
            offRouteCount++;
            if (offlineOffRoute) offRouteM = crossM;
            long now = SystemClock.elapsedRealtime();
            long waitMs = offlineOffRoute ? 25000 : 12000;
            if (offRouteCount >= 3 && !rerouting && now - lastRerouteAt > waitMs) {
                rerouting = true;
                lastRerouteAt = now;
                final int gen = routeGen;
                List<Place> remStops = new ArrayList<Place>();
                for (Place wp : r.waypoints) {
                    double[] cl = closestOnRange(r.pts, wp.lat, wp.lon, 0, nPts - 2);
                    if ((int) cl[1] > segIdx + 2 && approxDist(lat, lon, wp.lat, wp.lon) > 65) {
                        remStops.add(wp);
                    }
                }
                final Place dest = new Place();
                dest.name = r.destName;
                dest.lat = r.destLat;
                dest.lon = r.destLon;
                remStops.add(dest);
                final double curCross = crossM;
                fetchRoutes(c, lat, lon, remStops, false, new RoutesCb() {
                    public void onRoutes(List<Route> routes, String error) {
                        if (gen != routeGen || active == null) return;
                        rerouting = false;
                        Route nr = routes != null && !routes.isEmpty() ? routes.get(0) : null;
                        if (nr != null) {
                            activate(c, nr);
                            speakRaw(c, I18n.t("Ruta recalculada"));
                        } else {
                            offlineOffRoute = true;
                            offRouteM = curCross;
                        }
                        if (onUpdate != null) onUpdate.run();
                    }
                });
            }
        } else if (offlineOffRoute) {
            offRouteM = crossM;
        }
    }

    private static void playAlertTone(final int tone, final int ms) {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75);
            tg.startTone(tone, ms);
            UI.postDelayed(new Runnable() {
                public void run() {
                    try { tg.release(); } catch (Throwable ignored) { }
                }
            }, ms + 120);
        } catch (Throwable ignored) { }
    }

    private static double[] closestOnRange(double[] pts, double lat, double lon, int lo, int hi) {
        double cos = Math.max(0.1, Math.cos(Math.toRadians(lat)));
        double kx = 111320.0 * cos, ky = 111320.0;
        double bestD = Double.MAX_VALUE;
        int bestI = lo;
        double bestT = 0;
        for (int i = lo; i <= hi; i++) {
            double ax = (pts[i * 2 + 1] - lon) * kx, ay = (pts[i * 2] - lat) * ky;
            double bx = (pts[(i + 1) * 2 + 1] - lon) * kx, by = (pts[(i + 1) * 2] - lat) * ky;
            double dx = bx - ax, dy = by - ay;
            double len2 = dx * dx + dy * dy;
            double t = len2 > 1e-6 ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2)) : 0;
            double px = ax + dx * t, py = ay + dy * t;
            double d = Math.sqrt(px * px + py * py);
            if (d < bestD) { bestD = d; bestI = i; bestT = t; }
        }
        return new double[]{bestD, bestI, bestT};
    }

    private static double approxDist(double lat1, double lon1, double lat2, double lon2) {
        double ky = 111320.0;
        double kx = ky * Math.cos(Math.toRadians((lat1 + lat2) * 0.5));
        double dy = (lat2 - lat1) * ky, dx = (lon2 - lon1) * kx;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ───────────────────────── formato de texto ─────────────────────────

    public static String fmtStepDist(double meters, boolean mph) {
        if (mph) {
            double ft = meters * 3.28084;
            if (ft < 800) return I18n.integer(Math.max(50, Math.round(ft / 50.0) * 50)) + " ft";
            double mi = meters / 1609.344;
            return mi < 10 ? String.format(I18n.num, "%.1f mi", mi) : I18n.integer(Math.round(mi)) + " mi";
        }
        if (meters < 950) return I18n.integer(Math.max(10, Math.round(meters / 10.0) * 10)) + " m";
        double km = meters / 1000.0;
        return km < 10 ? String.format(I18n.num, "%.1f km", km) : I18n.integer(Math.round(km)) + " km";
    }

    public static String fmtEta(double durSec, double distM, boolean mph) {
        long min = Math.max(1, Math.round(durSec / 60.0));
        String tStr = min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
        return tStr + " · " + fmtStepDist(distM, mph);
    }

    public static String fmtEta(Context c, double durSec, double distM, boolean mph) {
        if (c != null && c.getSharedPreferences("launcher", 0).getBoolean("nav_eta_clock", false)) {
            return fmtClockEta(c, durSec) + " · " + fmtStepDist(distM, mph);
        }
        return fmtEta(durSec, distM, mph);
    }

    public static String fmtClockEta(Context c, double durSec) {
        long arrivalMs = System.currentTimeMillis() + Math.max(60L, Math.round(durSec)) * 1000L;
        java.text.DateFormat df = c != null ? DateFormat.getTimeFormat(c) : new SimpleDateFormat("HH:mm", Locale.getDefault());
        return df.format(new Date(arrivalMs));
    }

    public static Step currentStep() {
        Route r = active;
        if (r == null || r.steps.isEmpty()) return null;
        int idx = Math.max(0, Math.min(r.steps.size() - 1, curStepIdx));
        return r.steps.get(idx);
    }

    // ───────────────────────── guía por voz (TTS) con atenuación de música ─────────────────────────

    private static void speakStep(Context c, Step st, int idx, boolean prefix) {
        speakStep(c, st, idx, prefix, null);
    }

    private static void speakStep(Context c, Step st, int idx, boolean prefix, String distWord) {
        if (st == null || st.instruction == null) return;
        String inst = I18n.t(st.instruction);
        String msg = prefix && distWord != null ? I18n.t("En " + distWord) + ", " + inst : inst;
        speakRaw(c, msg);
    }

    @SuppressWarnings("deprecation")
    private static void requestNavFocus(Context c, int approxMs) {
        if (c == null) return;
        try {
            if (duckAm == null) duckAm = (AudioManager) c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            if (duckAm == null) return;
            UI.removeCallbacks(ABANDON_FOCUS);
            int res = duckAm.requestAudioFocus(FOCUS_LS, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) hasAudioFocus = true;
            UI.postDelayed(ABANDON_FOCUS, Math.max(2500, Math.min(14000, approxMs)));
        } catch (Throwable ignored) { }
    }

    @SuppressWarnings("deprecation")
    static void abandonNavFocus() {
        UI.removeCallbacks(ABANDON_FOCUS);
        if (hasAudioFocus && duckAm != null) {
            try { duckAm.abandonAudioFocus(FOCUS_LS); } catch (Throwable ignored) { }
        }
        hasAudioFocus = false;
    }

    public static void speakRaw(final Context c, final String text) {
        if (c == null || text == null || text.length() == 0) return;
        if (!c.getSharedPreferences("launcher", 0).getBoolean("nav_voice", true)) return;
        final Context app = c.getApplicationContext();
        try {
            if (tts == null || !I18n.lang.equals(ttsLang)) {
                shutdownTts();
                ttsLang = I18n.lang;
                tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
                    public void onInit(final int status) {
                        Runnable initTask = new Runnable() {
                            public void run() {
                                if (status == TextToSpeech.SUCCESS && tts != null) {
                                    try {
                                        int res = tts.setLanguage(I18n.loc);
                                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                                            tts.setLanguage(new Locale(I18n.lang));
                                        }
                                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                                            public void onStart(String utteranceId) { }
                                            public void onDone(String utteranceId) {
                                                if (utteranceId != null && utteranceId.equals(curUttId)) UI.post(ABANDON_FOCUS);
                                            }
                                            public void onError(String utteranceId) {
                                                if (utteranceId != null && utteranceId.equals(curUttId)) UI.post(ABANDON_FOCUS);
                                            }
                                        });
                                    } catch (Throwable ignored) { }
                                    ttsReady = true;
                                    doSpeak(app, text);
                                }
                            }
                        };
                        if (tts != null) initTask.run();
                        else UI.post(initTask);
                    }
                });
            } else if (ttsReady) {
                doSpeak(app, text);
            }
        } catch (Throwable ignored) { }
    }

    @SuppressWarnings("deprecation")
    private static void doSpeak(Context c, String text) {
        if (tts == null || !ttsReady) return;
        try {
            int timeoutMs = Math.max(3000, Math.min(14000, text.length() * 95 + 1800));
            requestNavFocus(c, timeoutMs);
            String uttId = "stradaos_nav_" + (++speakSeq);
            curUttId = uttId;
            if (Build.VERSION.SDK_INT >= 21) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, uttId);
            } else {
                HashMap<String, String> params = new HashMap<String, String>(1);
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uttId);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            }
        } catch (Throwable ignored) { }
    }

    public static void shutdownTts() {
        ttsReady = false;
        abandonNavFocus();
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Throwable ignored) { }
            tts = null;
        }
    }

    private static String shortErr(Throwable e) {
        String m = String.valueOf(e.getMessage());
        if (e instanceof java.net.UnknownHostException) return I18n.t("sin internet / DNS");
        if (e instanceof java.net.SocketTimeoutException) return I18n.t("tiempo agotado");
        if (m.startsWith("HTTP")) return m;
        return e.getClass().getSimpleName();
    }

    // ───────────────────────── ícono vectorial de maniobra y carriles ─────────────────────────

    public static class ManeuverView extends View {
        private int icon = 0;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public ManeuverView(Context c) {
            super(c);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setIcon(int ic) {
            if (icon == ic) return;
            icon = ic;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float u = Math.min(w, h) / 24f;
            p.setStrokeWidth(2.6f * u);
            path.reset();

            if (icon == 9) {
                // destino: pin
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(w / 2f, 9.5f * u, 4.5f * u, p);
                path.moveTo(w / 2f, 14f * u);
                path.lineTo(w / 2f, 20f * u);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(w / 2f, 9.5f * u, 1.8f * u, p);
                p.setStyle(Paint.Style.STROKE);
                return;
            }

            if (icon == 8) {
                // rotonda
                c.drawCircle(w / 2f, 13f * u, 4.2f * u, p);
                path.moveTo(w / 2f, 20f * u); path.lineTo(w / 2f, 17.2f * u);
                path.moveTo(w / 2f + 3f * u, 10f * u); path.lineTo(17.5f * u, 5.5f * u);
                path.moveTo(13.5f * u, 5.5f * u); path.lineTo(17.5f * u, 5.5f * u); path.lineTo(17.5f * u, 9.5f * u);
                c.drawPath(path, p);
                return;
            }

            if (icon == 4) {
                // retorno en U
                path.moveTo(8f * u, 20f * u);
                path.lineTo(8f * u, 9f * u);
                path.cubicTo(8f * u, 4.5f * u, 16f * u, 4.5f * u, 16f * u, 9f * u);
                path.lineTo(16f * u, 17f * u);
                path.moveTo(13f * u, 14f * u); path.lineTo(16f * u, 17.5f * u); path.lineTo(19f * u, 14f * u);
                c.drawPath(path, p);
                return;
            }

            if (icon == 0) {
                // recto
                path.moveTo(12f * u, 20f * u); path.lineTo(12f * u, 5f * u);
                path.moveTo(7.5f * u, 9.5f * u); path.lineTo(12f * u, 5f * u); path.lineTo(16.5f * u, 9.5f * u);
                c.drawPath(path, p);
                return;
            }

            boolean left = icon >= 5 && icon <= 7;
            float dir = left ? -1f : 1f;
            int mag = icon == 1 || icon == 7 ? 1 : icon == 2 || icon == 6 ? 2 : 3; // 1 suave, 2 90°, 3 cerrada

            if (mag == 1) {
                // giro suave (45°)
                path.moveTo(12f * u, 20f * u);
                path.lineTo(12f * u, 12.5f * u);
                float tx = (12f + dir * 5.5f) * u, ty = 6.5f * u;
                path.lineTo(tx, ty);
                path.moveTo(tx - dir * 4f * u, ty);
                path.lineTo(tx, ty);
                path.lineTo(tx, ty + 4f * u);
            } else if (mag == 2) {
                // giro 90°
                float sx = (12f - dir * 3f) * u;
                float tx = (12f + dir * 6f) * u;
                path.moveTo(sx, 20f * u);
                path.lineTo(sx, 9.5f * u);
                path.lineTo(tx, 9.5f * u);
                path.moveTo(tx - dir * 3.8f * u, 5.7f * u);
                path.lineTo(tx, 9.5f * u);
                path.lineTo(tx - dir * 3.8f * u, 13.3f * u);
            } else {
                // giro cerrado (135°)
                float sx = (12f - dir * 3.5f) * u;
                float tx = (12f + dir * 5.5f) * u;
                path.moveTo(sx, 20f * u);
                path.lineTo(sx, 7.5f * u);
                path.lineTo(tx, 14f * u);
                path.moveTo(tx - dir * 4f * u, 14f * u);
                path.lineTo(tx, 14f * u);
                path.lineTo(tx, 10f * u);
            }
            c.drawPath(path, p);
        }
    }

    /** Tira compacta de carriles recomendados según OSRM intersections[].lanes. */
    public static class LaneStripView extends View {
        private int[] lanes;
        private int activeCol = 0xFFFFFFFF;
        private int dimCol = 0x55FFFFFF;
        private int pillBg = 0x28FFFFFF;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF box = new RectF();

        public LaneStripView(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            bgP.setStyle(Paint.Style.FILL);
        }

        public void setLanes(int[] l) {
            if (Arrays.equals(lanes, l)) return;
            lanes = l;
            invalidate();
        }

        public void setColors(int active, int dim, int pill) {
            activeCol = active;
            dimCol = dim;
            pillBg = pill;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (lanes == null || lanes.length == 0) return;
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            int n = lanes.length;
            float cw = Math.min(h * 1.15f, w / n);
            float totalW = cw * n;
            float startX = (w - totalW) * 0.5f;
            float u = Math.min(cw, h) / 20f;
            p.setStrokeWidth(Math.max(1.5f, 1.9f * u));

            for (int i = 0; i < n; i++) {
                int mask = lanes[i];
                boolean valid = (mask & LANE_VALID) != 0;
                float cx = startX + i * cw + cw * 0.5f;
                float cy = h * 0.5f;
                if (valid) {
                    box.set(startX + i * cw + 1.5f * u, 1.5f * u, startX + (i + 1) * cw - 1.5f * u, h - 1.5f * u);
                    bgP.setColor(pillBg);
                    c.drawRoundRect(box, 4f * u, 4f * u, bgP);
                }
                p.setColor(valid ? activeCol : dimCol);
                drawLaneGlyph(c, cx, cy, u, mask);
            }
        }

        private void drawLaneGlyph(Canvas c, float cx, float cy, float u, int mask) {
            float bot = cy + 6.5f * u;
            float top = cy - 6.5f * u;
            if ((mask & LANE_STRAIGHT) != 0) {
                path.reset();
                path.moveTo(cx, bot); path.lineTo(cx, top);
                path.moveTo(cx - 2.6f * u, top + 2.8f * u); path.lineTo(cx, top); path.lineTo(cx + 2.6f * u, top + 2.8f * u);
                c.drawPath(path, p);
            }
            if ((mask & (LANE_LEFT | LANE_SLIGHT_LEFT | LANE_SHARP_LEFT)) != 0) {
                float dx = (mask & LANE_SLIGHT_LEFT) != 0 ? -3.8f : -5.2f;
                float ty = (mask & LANE_SHARP_LEFT) != 0 ? cy + 0.5f * u : (mask & LANE_SLIGHT_LEFT) != 0 ? top + 1f * u : cy - 1f * u;
                path.reset();
                path.moveTo(cx, bot); path.lineTo(cx, cy); path.lineTo(cx + dx * u, ty);
                path.moveTo(cx + (dx + 2.2f) * u, ty - 1.5f * u); path.lineTo(cx + dx * u, ty); path.lineTo(cx + (dx + 1.8f) * u, ty + 2.4f * u);
                c.drawPath(path, p);
            }
            if ((mask & (LANE_RIGHT | LANE_SLIGHT_RIGHT | LANE_SHARP_RIGHT)) != 0) {
                float dx = (mask & LANE_SLIGHT_RIGHT) != 0 ? 3.8f : 5.2f;
                float ty = (mask & LANE_SHARP_RIGHT) != 0 ? cy + 0.5f * u : (mask & LANE_SLIGHT_RIGHT) != 0 ? top + 1f * u : cy - 1f * u;
                path.reset();
                path.moveTo(cx, bot); path.lineTo(cx, cy); path.lineTo(cx + dx * u, ty);
                path.moveTo(cx + (dx - 2.2f) * u, ty - 1.5f * u); path.lineTo(cx + dx * u, ty); path.lineTo(cx + (dx - 1.8f) * u, ty + 2.4f * u);
                c.drawPath(path, p);
            }
            if ((mask & LANE_UTURN) != 0) {
                path.reset();
                path.moveTo(cx + 2.2f * u, bot);
                path.lineTo(cx + 2.2f * u, cy - 2.5f * u);
                path.lineTo(cx - 2.8f * u, cy - 2.5f * u);
                path.lineTo(cx - 2.8f * u, bot - 1.5f * u);
                path.moveTo(cx - 4.5f * u, bot - 3.5f * u); path.lineTo(cx - 2.8f * u, bot - 1.5f * u); path.lineTo(cx - 1.1f * u, bot - 3.5f * u);
                c.drawPath(path, p);
            }
        }
    }
}
