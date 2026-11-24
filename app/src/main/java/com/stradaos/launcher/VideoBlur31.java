package com.stradaos.launcher;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.view.View;

/**
 * Difuminado en vivo de Android 12+ (API 31) para la tapa del video en movimiento: el sistema difumina cada cuadro del
 * video en la GPU, sin copias ni saltos. Va en una clase aparte porque RenderEffect no existe en Android 4–11: solo se
 * llama detrás de {@code Build.VERSION.SDK_INT >= 31} (en Android 4.x Dalvik ni la carga).
 */
final class VideoBlur31 {
    private VideoBlur31() { }

    static void on(View v, float radius) {
        v.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
    }

    static void off(View v) { v.setRenderEffect(null); }
}
