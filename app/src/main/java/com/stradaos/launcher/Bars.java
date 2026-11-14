package com.stradaos.launcher;

import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/**
 * Barras del sistema en Android 11+ (2.21.1). Solo se llama cuando SDK_INT >= 30: en estas versiones la barra de
 * navegación (y la barra de tareas de las pantallas grandes en Android 12L+) se oculta con WindowInsetsController;
 * las banderas viejas de setSystemUiVisibility ya no alcanzan para la barra de tareas.
 */
class Bars {
    static void navigation(Window w, boolean hide) {
        WindowInsetsController c = w.getInsetsController();
        if (c == null) return;
        if (hide) {
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsets.Type.navigationBars());
        } else {
            c.show(WindowInsets.Type.navigationBars());
        }
    }
}
