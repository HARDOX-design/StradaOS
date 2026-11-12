package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import java.util.List;
import java.util.Locale;

/** Clima actual (Open-Meteo, sin clave) y nombre de la ciudad. */
public class Weather {

    public static class Now {
        public double temp;
        public int code;
        public boolean day = true;
        public String city;
        public String unit = "C";
        // 2.18: detalle (si Open-Meteo no lo manda, quedan vacíos y el inicio muestra solo lo de antes)
        public double feels = Double.NaN, wind = Double.NaN;
        public int humidity = -1;
        public String windUnit = "km/h";
        public final List<Hour> hours = new ArrayList<Hour>();   // próximas 12 horas desde la actual
        public final List<Day> days = new ArrayList<Day>();      // hoy y los 4 días siguientes
        public Day today() { return days.isEmpty() ? null : days.get(0); }
    }

    public static class Hour { public int hour; public double temp; public int code; public int rain = -1; public boolean day = true; }
    public static class Day { public String date; public double hi, lo; public int code; public int rain = -1; }

    public interface Listener { void onWeather(Now n, String error); }

    static String lastError = "—";

    /** Se ejecuta en un hilo aparte; el resultado llega por el listener (en ese mismo hilo). */
    public static void fetch(final Context c, final double lat, final double lon, final Listener l) {
        new Thread(new Runnable() {
            public void run() {
                SharedPreferences p = c.getSharedPreferences("launcher", 0);
                boolean f = "F".equals(p.getString("temp_unit", "C"));
                Now n = null;
                for (String scheme : new String[]{"https://", "http://"}) {
                    // sin cifrar (solo si falla https, en Android 4.x) se envía la posición redondeada a ~10 km
                    boolean plain = scheme.startsWith("http:");
                    String q = String.format(Locale.US,
                            "api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m"
                            + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=5&timezone=auto%s",
                            coord(lat, plain), coord(lon, plain), f ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "");
                    try {
                        JSONObject root = new JSONObject(new String(Net.get(scheme + q, 10000), "UTF-8"));
                        JSONObject cur = root.getJSONObject("current");
                        n = new Now();
                        n.temp = cur.getDouble("temperature_2m");
                        n.code = cur.optInt("weather_code", 0);
                        n.day = cur.optInt("is_day", 1) == 1;
                        n.unit = f ? "F" : "C";
                        n.windUnit = f ? "mph" : "km/h";
                        try { parseDetail(root, cur, n); } catch (Throwable ignored) { }
                        break;
                    } catch (Throwable e) {
                        lastError = e.toString();
                    }
                }
                if (n == null) { l.onWeather(null, lastError); return; }
                n.city = city(c, lat, lon);
                l.onWeather(n, null);
            }
        }).start();
    }

    /** Sensación, humedad, viento, próximas horas y días. Cualquier dato que falte se deja vacío. */
    static void parseDetail(JSONObject root, JSONObject cur, Now n) throws Exception {
        n.feels = cur.optDouble("apparent_temperature", Double.NaN);
        n.humidity = cur.has("relative_humidity_2m") ? (int) Math.round(cur.optDouble("relative_humidity_2m", -1)) : -1;
        n.wind = cur.optDouble("wind_speed_10m", Double.NaN);
        String now = cur.optString("time", "");
        JSONObject hr = root.optJSONObject("hourly");
        if (hr != null) {
            JSONArray t = hr.getJSONArray("time"), te = hr.getJSONArray("temperature_2m");
            JSONArray wc = hr.optJSONArray("weather_code"), pr = hr.optJSONArray("precipitation_probability"), dy = hr.optJSONArray("is_day");
            String key = now.length() >= 13 ? now.substring(0, 13) : "";   // «2026-11-12T14»
            int start = 0;
            for (int i = 0; i < t.length(); i++) if (t.getString(i).compareTo(key) >= 0) { start = i; break; }
            for (int i = start; i < t.length() && n.hours.size() < 12; i++) {
                if (te.isNull(i)) continue;
                Hour h = new Hour();
                String ts = t.getString(i);
                h.hour = ts.length() >= 13 ? Integer.parseInt(ts.substring(11, 13)) : 0;
                h.temp = te.getDouble(i);
                h.code = wc != null ? wc.optInt(i, 0) : 0;
                h.rain = pr != null && !pr.isNull(i) ? pr.optInt(i, -1) : -1;
                h.day = dy == null || dy.optInt(i, 1) == 1;
                n.hours.add(h);
            }
        }
        JSONObject d = root.optJSONObject("daily");
        if (d != null) {
            JSONArray t = d.getJSONArray("time"), hi = d.getJSONArray("temperature_2m_max"), lo = d.getJSONArray("temperature_2m_min");
            JSONArray wc = d.optJSONArray("weather_code"), pr = d.optJSONArray("precipitation_probability_max");
            for (int i = 0; i < t.length() && n.days.size() < 5; i++) {
                if (hi.isNull(i) || lo.isNull(i)) continue;
                Day x = new Day();
                x.date = t.getString(i);
                x.hi = hi.getDouble(i);
                x.lo = lo.getDouble(i);
                x.code = wc != null ? wc.optInt(i, 0) : 0;
                x.rain = pr != null && !pr.isNull(i) ? pr.optInt(i, -1) : -1;
                n.days.add(x);
            }
        }
    }

    /** Coordenada para la URL: 4 decimales por https; 1 decimal (~10 km) si la conexión va sin cifrar. */
    static String coord(double v, boolean plain) {
        return String.format(Locale.US, plain ? "%.1f" : "%.4f", v);
    }

    /** Ciudad: la escrita por el usuario, la guardada para esa zona, Geocoder o BigDataCloud. */
    static String city(Context c, double lat, double lon) {
        SharedPreferences p = c.getSharedPreferences("launcher", 0);
        String manual = p.getString("city_manual", "").trim();
        if (manual.length() > 0) return manual;
        String key = String.format(Locale.US, "%.2f,%.2f", lat, lon);
        if (key.equals(p.getString("city_key", ""))) return p.getString("city_name", null);
        String name = null;
        try {
            if (Geocoder.isPresent()) {
                List<Address> a = new Geocoder(c, new Locale(I18n.lang)).getFromLocation(lat, lon, 1);
                if (a != null && !a.isEmpty()) {
                    name = a.get(0).getLocality();
                    if (name == null) name = a.get(0).getSubAdminArea();
                }
            }
        } catch (Throwable ignored) { }
        if (name == null) {
            for (String scheme : new String[]{"https://", "http://"}) {
                boolean plain = scheme.startsWith("http:");
                String q = String.format(Locale.US,
                        "api.bigdatacloud.net/data/reverse-geocode-client?latitude=%s&longitude=%s&localityLanguage=%s",
                        coord(lat, plain), coord(lon, plain), I18n.lang);
                try {
                    JSONObject o = new JSONObject(new String(Net.get(scheme + q, 8000), "UTF-8"));
                    name = o.optString("city", "");
                    if (name.length() == 0) name = o.optString("locality", "");
                    if (name.length() == 0) name = null;
                    if (name != null) break;
                } catch (Throwable ignored) { }
            }
        }
        if (name != null) p.edit().putString("city_key", key).putString("city_name", name).apply();
        return name;
    }

    /** Código WMO → ícono. */
    public static String icon(int code, boolean day) {
        if (code == 0) return day ? "wb_sunny" : "nights_stay";
        if (code <= 2) return day ? "filter_drama" : "nights_stay";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "dehaze";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "umbrella";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "ac_unit";
        if (code >= 95) return "thunderstorm";
        return "cloud";
    }

    public static String describe(int code) {
        if (code == 0) return "Despejado";
        if (code <= 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Niebla";
        if (code >= 51 && code <= 57) return "Llovizna";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Lluvia";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Nieve";
        if (code >= 95) return "Tormenta";
        return "";
    }
}
