package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Qué significa un código de falla (P0711, P0300…), en tres capas:
 * 1. Sin internet: la tabla en español de los códigos más comunes (assets/obd/dtc_es.tsv, con gravedad y qué hacer)
 *    y la lista genérica SAE en inglés (assets/obd/dtc_en.tsv, ~2100 códigos P0/P2/P3).
 * 2. Con internet y una API key (Ajustes del vehículo): una IA lo explica para ese auto en el idioma de StradaOS.
 * 3. La respuesta de la IA se guarda en el equipo: la próxima vez sale aunque no haya señal (en la montaña).
 *
 * La IA solo recibe el código, su descripción genérica, el auto que el usuario escribió y los datos del motor en
 * ese momento. Nunca la ubicación.
 */
public class DtcInfo {

    static class Entry {
        String code, es, advice, en;
        int severity;                 // 1 = se puede seguir, 2 = revisar pronto, 3 = detenerse o taller ya; 0 = no se sabe
        String ai;                    // explicación guardada de la IA (o null)
    }

    public interface AiCb { void on(String text, String error); }

    private static HashMap<String, String[]> es;
    private static HashMap<String, String> en;
    private static JSONObject cache;
    private static final HashSet<String> asking = new HashSet<String>();

    private static synchronized void load(Context c) {
        if (es != null) return;
        es = new HashMap<String, String[]>();
        en = new HashMap<String, String>();
        for (String line : lines(c, "obd/dtc_es.tsv")) {
            String[] p = line.split("\t");
            if (p.length >= 4) es.put(p[0], new String[]{p[1], p[2], p[3]});
        }
        for (String line : lines(c, "obd/dtc_en.tsv")) {
            int t = line.indexOf('\t');
            if (t == 5) en.put(line.substring(0, 5), line.substring(6));
        }
    }

    private static java.util.List<String> lines(Context c, String asset) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open(asset), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) if (l.length() > 0 && l.charAt(0) != '#') out.add(l);
            r.close();
        } catch (Throwable ignored) { }
        return out;
    }

    static Entry get(Context c, String code) {
        load(c);
        Entry e = new Entry();
        e.code = code;
        String[] s = es.get(code);
        if (s != null) {
            e.severity = Integer.parseInt(s[0]);
            // la tabla propia está escrita en español y traducida en assets/i18n; si un idioma no la tiene,
            // se usa la descripción SAE en inglés (y la IA, si hay). «es» = la explicación en el idioma de ahora.
            if (I18n.has(s[1])) {
                e.es = I18n.t(s[1]);
                e.advice = I18n.t(s[2]);
            }
        }
        e.en = en.get(code);
        e.ai = cached(c, code);
        return e;
    }

    /** Sistema del auto según la forma del código (SAE J2012), para cuando no hay más información. */
    static String system(String code) {
        if (code == null || code.length() < 5) return "";
        char k = code.charAt(0), d1 = code.charAt(1), d2 = code.charAt(2);
        boolean maker = d1 == '1' || (k == 'P' && d1 == '3');
        String fam = k == 'B' ? "Carrocería (airbag, cinturones, luces, cierre…)" : k == 'C' ? "Chasis (frenos ABS, dirección, suspensión, 4x4)"
            : k == 'U' ? "Comunicación entre computadores del auto" : null;
        if (fam == null) {
            switch (d2) {
                case '0': case '1': case '2': fam = "Motor: mezcla de aire y combustible"; break;
                case '3': fam = "Motor: encendido y fallas de encendido"; break;
                case '4': fam = "Control de emisiones"; break;
                case '5': fam = "Velocidad, ralentí y entradas auxiliares"; break;
                case '6': fam = "Computador del motor y sus salidas"; break;
                case '7': case '8': case '9': fam = "Caja de cambios"; break;
                default: fam = "Motor o caja (sistema híbrido u otro)";
            }
        }
        return I18n.t(fam) + (maker ? " · " + I18n.t("código propio de la marca") : "");
    }

    // ── IA ──

    /** Proveedores: id, nombre, dirección de la API, modelo por defecto ("" = el usuario lo elige de la lista). */
    static final String[][] PRESETS = {
        {"anthropic", "Claude (Anthropic)", "https://api.anthropic.com/v1", "claude-opus-5-5"},
        {"groq", "Groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b"},
        {"openai", "OpenAI", "https://api.openai.com/v1", ""},
        {"gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", ""},
        {"openrouter", "OpenRouter", "https://openrouter.ai/api/v1", ""},
        {"custom", "Otra compatible con OpenAI", "", ""},
    };

    static String[] preset(Context c) {
        String id = Obd.prefs(c).getString("ai_preset", "anthropic");
        for (String[] p : PRESETS) if (p[0].equals(id)) return p;
        return PRESETS[0];
    }

    static boolean anthropic(Context c) { return "anthropic".equals(preset(c)[0]); }

    static String baseUrl(Context c) {
        String[] p = preset(c);
        String u = "custom".equals(p[0]) ? Obd.prefs(c).getString("ai_url", "").trim() : p[2];
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    static String model(Context c) {
        String m = Obd.prefs(c).getString("ai_model", "").trim();
        return m.length() > 0 ? m : preset(c)[3];
    }

    static boolean aiReady(Context c) {
        SharedPreferences p = Obd.prefs(c);
        return p.getString("ai_key", "").trim().length() > 0;
    }

    private static File cacheFile(Context c) { return new File(c.getFilesDir(), "obd_ai_cache.json"); }

    private static synchronized JSONObject cache(Context c) {
        if (cache != null) return cache;
        try {
            InputStream in = new FileInputStream(cacheFile(c));
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            cache = new JSONObject(new String(b.toByteArray(), "UTF-8"));
        } catch (Throwable e) { cache = new JSONObject(); }
        return cache;
    }

    private static String key(Context c, String code) {
        return I18n.lang + "|" + Obd.prefs(c).getString("obd_vehicle", "").trim().toLowerCase(java.util.Locale.US) + "|" + code;
    }

    static synchronized String cached(Context c, String code) {
        String v = cache(c).optString(key(c, code), "");
        return v.length() > 0 ? v : null;
    }

    private static synchronized void store(Context c, String code, String text) {
        try {
            cache(c).put(key(c, code), text);
            OutputStream o = new FileOutputStream(cacheFile(c));
            o.write(cache.toString().getBytes("UTF-8"));
            o.close();
        } catch (Throwable ignored) { }
    }

    /** Código nuevo con la IA configurada: se pide la explicación en segundo plano para tenerla lista (y guardada). */
    static void autoExplain(final Context c, final String code) {
        if (!aiReady(c) || cached(c, code) != null) return;
        explain(c, code, new AiCb() { public void on(String text, String error) {
            if (text != null) Obd.log("IA explicó " + code);
        }});
    }

    /** Pide la explicación a la IA. El callback llega en el hilo principal. */
    static void explain(final Context c, final String code, final AiCb cb) {
        synchronized (asking) { if (!asking.add(code)) return; }
        final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(new Runnable() { public void run() {
            String text = null, err = null;
            try {
                text = ask(c, prompt(c, code));
                store(c, code, text);
            } catch (Throwable t) {
                err = t.getMessage() != null ? t.getMessage() : t.toString();
            } finally {
                synchronized (asking) { asking.remove(code); }
            }
            final String ft = text, fe = err;
            ui.post(new Runnable() { public void run() { cb.on(ft, fe); } });
        }}, "StradaIA").start();
    }

    private static String prompt(Context c, String code) {
        Entry e = get(c, code);
        String vehicle = Obd.prefs(c).getString("obd_vehicle", "").trim();
        // El VIN identifica el auto de forma única: solo se envía si el usuario lo activa en ⚙ → Inteligencia artificial.
        boolean sendVin = Obd.prefs(c).getBoolean("obd_ai_vin", false);
        StringBuilder live = new StringBuilder();
        for (Obd.Sensor s : Obd.ALL) if (s.has()) live.append(I18n.t(s.label)).append(' ').append(s.text()).append(' ').append(s.unit).append("; ");
        return "Vehículo: " + (vehicle.length() > 0 ? vehicle : "no indicado") + (sendVin && Obd.vin != null ? " (VIN " + Obd.vin + ")" : "") + "\n"
            + "Código de falla OBD-II: " + code + (Obd.pending.contains(code) && !Obd.dtcs.contains(code) ? " (pendiente, aún no confirmado)" : "") + "\n"
            + "Descripción genérica: " + (e.en != null ? e.en : e.es != null ? e.es : "desconocida (" + system(code) + ")") + "\n"
            + "Luz del motor encendida: " + (Obd.mil ? "sí" : "no") + "\n"
            + (live.length() > 0 ? "Datos del motor ahora: " + live + "\n" : "");
    }

    private static String systemPrompt() {
        String lang = I18n.NAMES[Math.max(0, java.util.Arrays.asList(I18n.CODES).indexOf(I18n.lang))];
        return "Eres un mecánico automotriz que ayuda a un conductor por la pantalla de su auto. El conductor puede estar "
            + "en carretera o en la montaña, lejos de un taller. Explica el código de falla para ese vehículo en particular "
            + "(si conoces fallas típicas de ese modelo y motor, menciónalas). Responde en " + lang + ", en texto simple sin "
            + "markdown, en menos de 170 palabras, con este orden:\n"
            + "1. Primera línea: GRAVEDAD: SEGUIR / REVISAR PRONTO / DETENERSE, y en la misma línea una frase de qué hacer ahora.\n"
            + "2. Qué significa, en palabras simples.\n"
            + "3. Causas más probables, de la más común a la menos común.\n"
            + "4. Qué puede revisar el conductor sin herramientas, y qué necesita taller.\n"
            + "Si los datos del motor muestran algo preocupante, dilo. Si no estás seguro de algo propio de la marca, dilo.";
    }

    /** Explica una alerta de sensor (temperatura, voltaje, mezcla…) con los datos del motor de ese momento. */
    static void explainAlert(final Context c, final String title, final AiCb cb) {
        background(cb, new Job() { public String run() throws Exception {
            String vehicle = Obd.prefs(c).getString("obd_vehicle", "").trim();
            StringBuilder live = new StringBuilder();
            for (Obd.Sensor s : Obd.ALL) if (s.has()) live.append(I18n.t(s.label)).append(' ').append(s.text()).append(' ').append(s.unit).append("; ");
            String user = "Vehículo: " + (vehicle.length() > 0 ? vehicle : "no indicado") + "\n"
                + "Alerta del monitor: " + title + "\n"
                + "Datos del motor ahora: " + live + "\n"
                + "Códigos de falla guardados: " + codesText(c) + "\n";
            return ask(c, alertPrompt(), user);
        }});
    }

    /** Códigos guardados con su descripción, para que la IA no los confunda. */
    private static String codesText(Context c) {
        if (Obd.dtcs.isEmpty()) return "ninguno";
        StringBuilder b = new StringBuilder();
        for (String code : Obd.dtcs) {
            Entry e = get(c, code);
            b.append(code).append(" (").append(e.en != null ? e.en : e.es != null ? e.es : system(code)).append("); ");
        }
        return b.toString();
    }

    private static String alertPrompt() {
        String lang = I18n.NAMES[Math.max(0, java.util.Arrays.asList(I18n.CODES).indexOf(I18n.lang))];
        return "Eres un mecánico automotriz que ayuda a un conductor por la pantalla de su auto, quizás en la montaña y lejos "
            + "de un taller. El monitor OBD del auto mostró una alerta. Con los datos del motor, explica qué puede estar pasando "
            + "en ese vehículo. Responde en " + lang + ", en texto simple sin markdown, en menos de 150 palabras:\n"
            + "1. Primera línea: GRAVEDAD: SEGUIR / REVISAR PRONTO / DETENERSE, y qué hacer ahora mismo.\n"
            + "2. Qué indican los datos (relaciona los valores entre sí: temperatura, carga, RPM, voltaje, mezcla).\n"
            + "3. Causas probables y qué revisar al detenerse.\n"
            + "Si los datos son normales y parece una falsa alarma, dilo.\n"
            + "Ojo con la corrección de mezcla (fuel trim): positiva significa que el computador AGREGA combustible porque "
            + "detecta mezcla pobre (fuga de vacío, poca presión de combustible, MAF sucio); negativa, que lo quita por mezcla rica. "
            + "Usa la descripción de cada código tal como viene; no la cambies.";
    }

    /** Prueba la IA con un código de ejemplo (P0711), sin guardar la respuesta. Devuelve el texto y los segundos. */
    static void test(final Context c, final AiCb cb) {
        background(cb, new Job() { public String run() throws Exception {
            long t0 = System.currentTimeMillis();
            String text = ask(c, prompt(c, "P0711"));
            return text + "\n\n(" + String.format(I18n.num, "%.1f", (System.currentTimeMillis() - t0) / 1000f) + " s · " + model(c) + ")";
        }});
    }

    /** Modelos de texto que ofrece el proveedor (para elegir sin escribir el nombre). Uno por línea. */
    static void models(final Context c, final AiCb cb) {
        background(cb, new Job() { public String run() throws Exception {
            HttpURLConnection h = Net.open(baseUrl(c) + "/models", 30000);
            auth(c, h);
            try {
                int code = h.getResponseCode();
                String resp = read(code >= 400 ? h.getErrorStream() : h.getInputStream());
                if (code == 401 || code == 403) throw new Exception(I18n.t("La API key no es válida"));
                if (code >= 400) throw new Exception("HTTP " + code);
                JSONArray data = new JSONObject(resp).getJSONArray("data");
                java.util.List<String> ids = new java.util.ArrayList<String>();
                for (int i = 0; i < data.length(); i++) {
                    String id = data.getJSONObject(i).optString("id");
                    // fuera los que no escriben texto: voz, transcripción, imágenes, embeddings, filtros
                    if (id.length() == 0 || id.matches("(?i).*(whisper|tts|orpheus|guard|safeguard|embed|dall-e|image|audio|moderation|realtime|transcribe).*")) continue;
                    ids.add(id.startsWith("models/") ? id.substring(7) : id);
                }
                java.util.Collections.sort(ids);
                StringBuilder b = new StringBuilder();
                for (String id : ids) b.append(id).append('\n');
                return b.toString().trim();
            } finally {
                h.disconnect();
            }
        }});
    }

    private interface Job { String run() throws Exception; }

    /** Corre una consulta de red en otro hilo y entrega el resultado (o el error) en el hilo principal. */
    private static void background(final AiCb cb, final Job job) {
        final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(new Runnable() { public void run() {
            String text = null, err = null;
            try { text = job.run(); }
            catch (Throwable t) { err = t.getMessage() != null ? t.getMessage() : t.toString(); }
            final String ft = text, fe = err;
            ui.post(new Runnable() { public void run() { cb.on(ft, fe); } });
        }}, "StradaIA").start();
    }

    private static void auth(Context c, HttpURLConnection h) {
        String apiKey = Obd.prefs(c).getString("ai_key", "").trim();
        if (anthropic(c)) {
            h.setRequestProperty("x-api-key", apiKey);
            h.setRequestProperty("anthropic-version", "2026-10-21");
        } else {
            h.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
    }

    /** Llama al proveedor elegido: la API de mensajes de Claude o cualquier API compatible con OpenAI (Groq, Gemini…). */
    private static String ask(Context c, String user) throws Exception { return ask(c, systemPrompt(), user); }

    private static String ask(Context c, String system, String user) throws Exception {
        Net.init(c);                                   // TLS 1.2 y certificados actuales en Android 4.x
        boolean claude = anthropic(c);
        String model = model(c);
        if (model.length() == 0) throw new Exception(I18n.t("Elige el modelo de la IA en los ajustes"));
        if (baseUrl(c).length() == 0) throw new Exception(I18n.t("Escribe la dirección de la API en los ajustes"));
        JSONObject body = new JSONObject();
        HttpURLConnection h;
        if (!claude) {
            h = Net.open(baseUrl(c) + "/chat/completions", 60000);
            body.put("model", model);
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", system));
            msgs.put(new JSONObject().put("role", "user").put("content", user));
            body.put("messages", msgs);
        } else {
            h = Net.open(baseUrl(c) + "/messages", 90000);
            h.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01");
            body.put("model", model);
            body.put("max_tokens", 4000);
            body.put("output_config", new JSONObject().put("effort", "low"));     // respuesta corta: poco razonamiento
            body.put("fallbacks", "default");
            body.put("system", system);
            body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", user)));
        }
        auth(c, h);
        h.setRequestMethod("POST");
        h.setDoOutput(true);
        h.setRequestProperty("Content-Type", "application/json");
        try {
            OutputStream o = h.getOutputStream();
            o.write(body.toString().getBytes("UTF-8"));
            o.close();
            int code = h.getResponseCode();
            String resp = read(code >= 400 ? h.getErrorStream() : h.getInputStream());
            if (code == 401 || code == 403) throw new Exception(I18n.t("La API key no es válida"));
            if (code == 429) throw new Exception(I18n.t("La IA está ocupada o sin saldo. Intenta más tarde."));
            if (code >= 400) {
                String msg = resp;
                try { msg = new JSONObject(resp).getJSONObject("error").optString("message", resp); } catch (Throwable ignored) { }
                throw new Exception("HTTP " + code + ": " + msg);
            }
            JSONObject r = new JSONObject(resp);
            if (!claude) return r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
            if ("refusal".equals(r.optString("stop_reason"))) throw new Exception(I18n.t("La IA no quiso responder"));
            StringBuilder out = new StringBuilder();
            JSONArray content = r.getJSONArray("content");
            for (int i = 0; i < content.length(); i++) {
                JSONObject b = content.getJSONObject(i);
                if ("text".equals(b.optString("type"))) out.append(b.optString("text"));
            }
            if (out.length() == 0) throw new Exception(I18n.t("La IA respondió vacío"));
            return out.toString().trim();
        } finally {
            h.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return new String(b.toByteArray(), "UTF-8");
    }
}
