package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Navegación paso a paso en vivo sobre el mapa de StradaOS (sin Play Services ni SDKs pesados).
 * Búsqueda con Photon/Nominatim, rutas con OSRM y guía por voz con TextToSpeech del sistema.
 */
public class Nav {

    public static class Place {
        public String name, sub;
        public double lat, lon;
    }

    public static class Step {
        public double lat, lon, distM, durS;
        /** 0 recto, 1 derecha suave, 2 derecha, 3 derecha cerrada, 4 retorno, 5 izquierda cerrada, 6 izquierda, 7 izquierda suave, 8 rotonda, 9 llegada. */
        public int icon;
        public String street, instruction;
    }

    public static class Route {
        public double[] pts;        // lat0, lon0, lat1, lon1, ...
        public int[] stepPtIdx;     // índice de vértice (0..pts.length/2 - 1) para cada paso
        public double[] cumDistM;   // distancia acumulada en metros desde el inicio hasta cada vértice
        public final List<Step> steps = new ArrayList<Step>();
        public double totalDistM, totalDurS;
        public String destName;
        public double destLat, destLon;
    }

    public interface SearchCb { void onResult(List<Place> places, String error); }
    public interface RouteCb { void onRoute(Route r, String error); }

    public static volatile Route active;
    public static volatile int curPtIdx, curStepIdx;
    public static volatile double distToStepM, remDistM, remDurS;
    public static volatile boolean rerouting;
    public static boolean savedHeadingUp;

    private static volatile int routeGen;
    private static int offRouteCount;
    private static long lastRerouteAt;
    private static int[] spokenMask;
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private static TextToSpeech tts;
    private static boolean ttsReady;
    private static String ttsLang;

    // ───────────────────────── búsqueda de destinos ─────────────────────────

    public static void search(final String query, final double biasLat, final double biasLon, final SearchCb cb) {
        final String q = query == null ? "" : query.trim();
        if (q.length() == 0) { cb.onResult(new ArrayList<Place>(), null); return; }
        Place direct = parseCoords(q);
        if (direct != null) {
            List<Place> one = new ArrayList<Place>();
            one.add(direct);
            cb.onResult(one, null);
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                List<Place> out = null;
                String err = null;
                try {
                    out = searchPhoton(q, biasLat, biasLon);
                } catch (Throwable e) {
                    err = shortErr(e);
                }
                if (out == null || out.isEmpty()) {
                    try {
                        List<Place> nom = searchNominatim(q);
                        if (nom != null && !nom.isEmpty()) { out = nom; err = null; }
                        else if (out == null) out = nom;
                    } catch (Throwable e) {
                        if (err == null) err = shortErr(e);
                    }
                }
                final List<Place> fout = out;
                final String ferr = fout == null ? err : null;
                UI.post(new Runnable() {
                    public void run() { cb.onResult(fout, ferr); }
                });
            }
        }).start();
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

    private static List<Place> searchPhoton(String q, double biasLat, double biasLon) throws Exception {
        StringBuilder url = new StringBuilder("https://photon.komoot.io/api/?limit=6&q=");
        url.append(URLEncoder.encode(q, "UTF-8"));
        if ("en".equals(I18n.lang) || "de".equals(I18n.lang) || "fr".equals(I18n.lang) || "it".equals(I18n.lang)) {
            url.append("&lang=").append(I18n.lang);
        }
        if (!Double.isNaN(biasLat) && !Double.isNaN(biasLon)) {
            url.append(String.format(Locale.US, "&lat=%.4f&lon=%.4f", biasLat, biasLon));
        }
        String raw = new String(Net.get(url.toString(), 10000), "UTF-8");
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

    private static List<Place> searchNominatim(String q) throws Exception {
        String url = "https://nominatim.openstreetmap.org/search?format=json&limit=6&addressdetails=1&q="
                + URLEncoder.encode(q, "UTF-8") + "&accept-language=" + URLEncoder.encode(I18n.lang, "UTF-8");
        String raw = new String(Net.get(url, 10000), "UTF-8");
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

    // ───────────────────────── destinos recientes ─────────────────────────

    public static List<Place> recents(Context c) {
        List<Place> out = new ArrayList<Place>();
        try {
            SharedPreferences sp = c.getSharedPreferences("launcher", 0);
            JSONArray a = new JSONArray(sp.getString("nav_recents", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Place p = new Place();
                p.name = o.optString("n", "");
                p.sub = o.optString("s", "");
                p.lat = o.optDouble("la", Double.NaN);
                p.lon = o.optDouble("lo", Double.NaN);
                if (p.name.length() > 0 && !Double.isNaN(p.lat) && !Double.isNaN(p.lon)) out.add(p);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public static void saveRecent(Context c, Place p) {
        if (p == null || p.name == null || p.name.length() == 0) return;
        List<Place> list = recents(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            Place e = list.get(i);
            if (e.name.equalsIgnoreCase(p.name) || (Math.abs(e.lat - p.lat) < 0.0005 && Math.abs(e.lon - p.lon) < 0.0005)) {
                list.remove(i);
            }
        }
        list.add(0, p);
        while (list.size() > 8) list.remove(list.size() - 1);
        JSONArray a = new JSONArray();
        try {
            for (Place e : list) {
                JSONObject o = new JSONObject();
                o.put("n", e.name).put("s", e.sub != null ? e.sub : "").put("la", e.lat).put("lo", e.lon);
                a.put(o);
            }
            c.getSharedPreferences("launcher", 0).edit().putString("nav_recents", a.toString()).apply();
        } catch (Throwable ignored) { }
    }

    // ───────────────────────── cálculo y activación de ruta ─────────────────────────

    public static void startRoute(final Context c, double fromLat, double fromLon, final Place dest, final RouteCb cb) {
        final int gen = ++routeGen;
        fetchRoute(fromLat, fromLon, dest.lat, dest.lon, dest.name, new RouteCb() {
            public void onRoute(Route r, String error) {
                if (gen != routeGen) return;
                if (r != null) {
                    activate(r);
                    if (!r.steps.isEmpty()) speakStep(c, r.steps.get(0), -1, false);
                }
                if (cb != null) cb.onRoute(r, error);
            }
        });
    }

    public static void activate(Route r) {
        active = r;
        curPtIdx = 0;
        curStepIdx = r != null && r.steps.size() > 1 ? 1 : 0;
        remDistM = r != null ? r.totalDistM : 0;
        remDurS = r != null ? r.totalDurS : 0;
        distToStepM = r != null && !r.steps.isEmpty() ? r.steps.get(0).distM : 0;
        if (r != null && r.steps.size() > 1 && r.stepPtIdx != null && r.cumDistM != null && r.stepPtIdx[1] < r.cumDistM.length) {
            distToStepM = r.cumDistM[r.stepPtIdx[1]];
        }
        offRouteCount = 0;
        rerouting = false;
        spokenMask = r != null ? new int[r.steps.size()] : null;
    }

    public static void stop() {
        routeGen++;
        active = null;
        curPtIdx = 0;
        curStepIdx = 0;
        distToStepM = 0;
        remDistM = 0;
        remDurS = 0;
        rerouting = false;
        offRouteCount = 0;
        spokenMask = null;
    }

    public static void fetchRoute(final double lat1, final double lon1, final double lat2, final double lon2,
                                  final String destName, final RouteCb cb) {
        new Thread(new Runnable() {
            public void run() {
                Route r = null;
                String err = null;
                String[] hosts = {
                    "https://router.project-osrm.org/route/v1/driving/",
                    "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
                };
                String tail = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline&steps=true",
                        lon1, lat1, lon2, lat2);
                for (String host : hosts) {
                    try {
                        String raw = new String(Net.get(host + tail, 12000), "UTF-8");
                        r = parseOsrm(raw, lat2, lon2, destName);
                        if (r != null) { err = null; break; }
                    } catch (Throwable e) {
                        err = shortErr(e);
                    }
                }
                final Route fr = r;
                final String fe = r == null ? (err != null ? err : I18n.t("No se encontró ruta")) : null;
                UI.post(new Runnable() {
                    public void run() { cb.onRoute(fr, fe); }
                });
            }
        }).start();
    }

    private static Route parseOsrm(String json, double destLat, double destLon, String destName) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"Ok".equalsIgnoreCase(root.optString("code", ""))) {
            throw new Exception(root.optString("message", "No route"));
        }
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null || routes.length() == 0) return null;
        JSONObject r0 = routes.getJSONObject(0);
        String poly = r0.optString("geometry", "");
        double[] pts = decodePolyline(poly);
        if (pts.length < 4) return null;

        Route r = new Route();
        r.pts = pts;
        r.totalDistM = r0.optDouble("distance", 0);
        r.totalDurS = r0.optDouble("duration", 0);
        r.destLat = destLat;
        r.destLon = destLon;
        r.destName = destName;

        int nPts = pts.length / 2;
        r.cumDistM = new double[nPts];
        r.cumDistM[0] = 0;
        for (int i = 1; i < nPts; i++) {
            r.cumDistM[i] = r.cumDistM[i - 1] + approxDist(pts[(i - 1) * 2], pts[(i - 1) * 2 + 1], pts[i * 2], pts[i * 2 + 1]);
        }

        JSONArray legs = r0.optJSONArray("legs");
        if (legs != null && legs.length() > 0) {
            JSONArray stArr = legs.getJSONObject(0).optJSONArray("steps");
            if (stArr != null) {
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
                        st.lon = loc.optDouble(0, destLon);
                        st.lat = loc.optDouble(1, destLat);
                    } else {
                        st.lat = destLat;
                        st.lon = destLon;
                    }
                    st.icon = maneuverIcon(type, mod);
                    st.instruction = maneuverText(type, mod, exit, st.street);
                    r.steps.add(st);
                }
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
        }

        // asociar cada paso al vértice más cercano avanzando en orden
        r.stepPtIdx = new int[r.steps.size()];
        int searchFrom = 0;
        for (int i = 0; i < r.steps.size(); i++) {
            Step st = r.steps.get(i);
            if (st.icon == 9) {
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
        return r;
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
        remDurS = totalM > 1 ? r.totalDurS * (remDistM / totalM) : 0;

        // llegada al destino
        double distDestM = approxDist(lat, lon, r.destLat, r.destLon);
        if (distDestM < 35 && (remDistM < 60 || segIdx >= nPts - 4)) {
            if (!r.steps.isEmpty()) speakStep(c, r.steps.get(r.steps.size() - 1), r.steps.size() - 1, false);
            stop();
            if (onUpdate != null) onUpdate.run();
            return;
        }

        // próximo paso por delante de alongM
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

        // desvío de ruta -> recalcular
        boolean goodFix = !l.hasAccuracy() || l.getAccuracy() <= 55;
        if (crossM > 65 && goodFix) {
            offRouteCount++;
            long now = SystemClock.elapsedRealtime();
            if (offRouteCount >= 3 && !rerouting && now - lastRerouteAt > 12000) {
                rerouting = true;
                lastRerouteAt = now;
                final int gen = routeGen;
                final Place dest = new Place();
                dest.name = r.destName;
                dest.lat = r.destLat;
                dest.lon = r.destLon;
                fetchRoute(lat, lon, dest.lat, dest.lon, dest.name, new RouteCb() {
                    public void onRoute(Route nr, String error) {
                        if (gen != routeGen || active == null) return;
                        rerouting = false;
                        if (nr != null) {
                            activate(nr);
                            speakRaw(c, I18n.t("Ruta recalculada"));
                        }
                        if (onUpdate != null) onUpdate.run();
                    }
                });
            }
        } else {
            offRouteCount = 0;
        }
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

    public static Step currentStep() {
        Route r = active;
        if (r == null || r.steps.isEmpty()) return null;
        int idx = Math.max(0, Math.min(r.steps.size() - 1, curStepIdx));
        return r.steps.get(idx);
    }

    // ───────────────────────── guía por voz (TTS) ─────────────────────────

    private static void speakStep(Context c, Step st, int idx, boolean prefix) {
        speakStep(c, st, idx, prefix, null);
    }

    private static void speakStep(Context c, Step st, int idx, boolean prefix, String distWord) {
        if (st == null || st.instruction == null) return;
        String inst = I18n.t(st.instruction);
        String msg = prefix && distWord != null ? I18n.t("En " + distWord) + ", " + inst : inst;
        speakRaw(c, msg);
    }

    public static void speakRaw(final Context c, final String text) {
        if (c == null || text == null || text.length() == 0) return;
        if (!c.getSharedPreferences("launcher", 0).getBoolean("nav_voice", true)) return;
        try {
            if (tts == null || !I18n.lang.equals(ttsLang)) {
                shutdownTts();
                ttsLang = I18n.lang;
                tts = new TextToSpeech(c.getApplicationContext(), new TextToSpeech.OnInitListener() {
                    public void onInit(int status) {
                        if (status == TextToSpeech.SUCCESS && tts != null) {
                            try {
                                int res = tts.setLanguage(I18n.loc);
                                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                                    tts.setLanguage(new Locale(I18n.lang));
                                }
                            } catch (Throwable ignored) { }
                            ttsReady = true;
                            doSpeak(text);
                        }
                    }
                });
            } else if (ttsReady) {
                doSpeak(text);
            }
        } catch (Throwable ignored) { }
    }

    @SuppressWarnings("deprecation")
    private static void doSpeak(String text) {
        if (tts == null || !ttsReady) return;
        try {
            if (Build.VERSION.SDK_INT >= 21) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "stradaos_nav");
            else tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        } catch (Throwable ignored) { }
    }

    public static void shutdownTts() {
        ttsReady = false;
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

    // ───────────────────────── ícono vectorial de maniobra ─────────────────────────

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
}
