package com.stradaos.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;

/**
 * Temas premium (2.24): el tema en uso y sus colores. Main pide aquí los colores y el fondo, así Aurora y Cobalto
 * comparten la barra de arriba, «Solo mapa», las transiciones y la barra de apps.
 */
final class Prem {
    private Prem() { }

    static final String[] IDS = {Aurora.ID, Cobalto.ID, Faro.ID, Carbono.ID};
    static String style = Aurora.ID;

    static boolean known(String id) { return Aurora.ID.equals(id) || Cobalto.ID.equals(id) || Faro.ID.equals(id) || Carbono.ID.equals(id); }

    /** Temas premium que se regalan a todos (lanzamiento de la 2.24). Los que vengan después se desbloquean con una donación (Fase 3). */
    static boolean free(String id) { return Aurora.ID.equals(id) || Cobalto.ID.equals(id); }
    /** Cuántos temas se desbloquean con donación. Con dos o más, la app ofrece sola el paquete de 8 USD por dos temas. */
    static int paidCount() {
        int n = 0;
        for (String id : IDS) if (!free(id)) n++;
        return n;
    }
    /** Solo para las pruebas en el emulador (gancho «paquete:1»): ver el paquete antes de que exista el segundo tema. */
    static boolean forceBundle;
    static boolean bundle() { return forceBundle || paidCount() >= 2; }
    static boolean cob() { return Cobalto.ID.equals(style); }
    static boolean faro() { return Faro.ID.equals(style); }
    static boolean carb() { return Carbono.ID.equals(style); }
    /** Faro y Carbono comparten el mapa limpio, el buscador de arriba y las piezas de las tarjetas. */
    static boolean faroFam() { return faro() || carb(); }
    /** Solo Aurora tiene «Solo mapa» (Cobalto y Faro son fijos). */
    static boolean mapOnlyOk() { return Aurora.ID.equals(style); }

    static String name(String id) { return Cobalto.ID.equals(id) ? "Cobalto" : Faro.ID.equals(id) ? "Faro" : Carbono.ID.equals(id) ? "Carbono" : "Aurora"; }
    static String desc(String id) {
        if (Carbono.ID.equals(id))
            return "Tarjetas limpias sobre un fondo carbón con textura y una luz suave desde abajo (o gris perla en claro). Tu vehículo en grande con el velocímetro y los dos mantenimientos más cercanos, el mapa con el reproductor y el clima flotando encima, y una barra de apps tipo píldora.";
        if (Faro.ID.equals(id))
            return "Fondo carbón (o perla en claro) con un haz de luz diagonal. A la izquierda, el velocímetro y un carro visto desde arriba que enciende los faros de noche; en el centro, el mapa integrado al fondo; a la derecha, el clima por horas y tus próximos mantenimientos, con el reproductor y la barra de apps debajo.";
        return Cobalto.ID.equals(id)
            ? "Azul noche en tres columnas: el mapa se difumina hasta el reproductor de carátula redonda, y tu vehículo muestra el velocímetro arriba, su foto en el medio y el mantenimiento abajo. La temperatura va sobre el mapa."
            : "Fondo con un arco de color difuminado y granulado, tarjetas translúcidas, la hora en grande con música, vehículo y clima arriba, el mapa a lo ancho y una barra tipo píldora. El botón «Solo mapa» agranda el mapa.";
    }

    static int base(boolean l) { return faroFam() ? Faro.base(l) : cob() ? Cobalto.base(l) : Aurora.base(l); }
    static int glass(boolean l) { return faroFam() ? Faro.glass(l) : cob() ? Cobalto.glassTop(l) : Aurora.glass(l); }
    static int glassEdge(boolean l) { return faroFam() ? Faro.glassEdge(l) : cob() ? Cobalto.glassEdge(l) : Aurora.glassEdge(l); }
    static int pill(boolean l) { return faroFam() ? Faro.pill(l) : cob() ? Cobalto.pill(l) : Aurora.pill(l); }
    static int pillEdge(boolean l) { return faroFam() ? Faro.pillEdge(l) : cob() ? Cobalto.pillEdge(l) : Aurora.pillEdge(l); }
    static int pillIcon(boolean l) { return faroFam() ? Faro.pillIcon(l) : cob() ? Cobalto.pillIcon(l) : Aurora.pillIcon(l); }
    static int soft(boolean l) { return faroFam() ? Faro.soft(l) : cob() ? Cobalto.soft(l) : Aurora.soft(l); }
    static float mapAlpha(boolean l) { return faroFam() ? Faro.mapAlpha(l) : cob() ? Cobalto.mapAlpha(l) : Aurora.mapAlpha(l); }

    private static Bitmap cache;
    private static String cacheKey;

    /** Fondo del tema en uso a media resolución, con grano; se guarda hasta que cambie el tamaño, el modo o el tema. */
    static Bitmap background(int w, int h, boolean light, int accent) {
        int bw = Math.max(64, w / 2), bh = Math.max(64, h / 2);
        String key = style + ":" + bw + "x" + bh + ":" + light + ":" + accent;
        if (cache != null && !cache.isRecycled() && key.equals(cacheKey)) return cache;
        Bitmap b;
        try {
            b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (Throwable e) {
            b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            b.eraseColor(base(light));
            return b;
        }
        Canvas c = new Canvas(b);
        if (carb()) Carbono.paint(c, bw, bh, light, accent);
        else if (faro()) Faro.paint(c, bw, bh, light, accent);
        else if (cob()) Cobalto.paint(c, bw, bh, light, accent);
        else Aurora.paint(c, bw, bh, light, accent);
        if (carb()) Carbono.texture(b, light);
        else if (faro()) Aurora.grain(b, Faro.grain(light)); else Aurora.grain(b, light);
        cache = b;
        cacheKey = key;
        return b;
    }
}
