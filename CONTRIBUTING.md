# Cómo contribuir

¡Gracias por querer mejorar StradaOS! 🚗

## Reportar problemas
Usa la plantilla de [issues](../../issues/new/choose). Lo más útil:
- La versión de StradaOS y el **Android real** de la pantalla (en *Ajustes → Acerca de*).
- Fotos de la pantalla.
- El diagnóstico (*Ajustes → Sistema → Exportar diagnóstico*).

## Traducir StradaOS · Translating StradaOS

**Español.** No hace falta saber programar para traducir la app:

1. Copia `app/src/main/assets/i18n/en.txt` como `xx.txt` (código ISO del idioma: `tr.txt` para turco, `fr.txt` para francés…).
2. Cada línea es `español<TAB>traducción`. **Traduce solo lo que va después del tabulador** y deja la parte en español igual, letra por letra.
3. Respeta los marcadores: `{}` (ahí va un dato, como un número o un nombre), `{z}/{x}/{y}`, `%s`, `%d` y `\n` (salto de línea). Las líneas que empiezan con `#` son comentarios.
4. Abre un pull request con ese archivo. Si quieres, agrega también `app/src/main/res/values-xx/strings.xml` (copia el de `values-en`) y el idioma en `I18n.java` (`CODES`, `NAMES`, el `Locale` en `set()` y el formato de fecha en `datePattern()`); si no, lo hago yo.

Las frases que falten salen en español, así que una traducción parcial también sirve. Cuando salgan textos nuevos en una versión, se agregan al final de `en.txt`: basta con traducir esas líneas.

**English.** You don't need to code to translate the app:

1. Copy `app/src/main/assets/i18n/en.txt` to `xx.txt` (ISO language code: `tr.txt` for Turkish, `fr.txt` for French…).
2. Each line is `spanish<TAB>translation`. **Only translate the part after the tab**; keep the Spanish part exactly as it is.
3. Keep the placeholders: `{}` (a value such as a number or a name goes there), `{z}/{x}/{y}`, `%s`, `%d` and `\n` (line break). Lines starting with `#` are comments.
4. Open a pull request with that file. Optionally also add `app/src/main/res/values-xx/strings.xml` (copy `values-en`) and the language in `I18n.java` (`CODES`, `NAMES`, the `Locale` in `set()` and the date format in `datePattern()`); otherwise I'll do it.

Missing lines fall back to Spanish, so a partial translation helps too. New strings in each release are appended at the end of `en.txt`: just translate those lines.

## Compilar

Requisitos: JDK 17 y el SDK de Android.

```bash
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/
```

## Reglas del código
- **Compatibilidad con Android 4.1 (API 16):** cualquier API más nueva va detrás de `if (Build.VERSION.SDK_INT >= …)`.
- Java 8 **sin lambdas** ni `java.util.Objects`/`StandardCharsets` (no existen en API 16–18).
- Sin dependencias de Google Play Services, para poder estar en F-Droid.
- La interfaz se dibuja por código en una «mesa» de 1024×600 que se escala (ver `Main.place()`).

## Estructura

```
app/src/main/java/com/stradaos/launcher/
  Main.java          pantalla de inicio, ajustes, diálogos
  LiveMapView.java   mapa en vivo (teselas OSM, caché, modo oscuro)
  NowPlaying.java    pista en reproducción (Android 4.x + avisos de reproductores y Bluetooth)
  MediaWatcher.java  pista en reproducción en Android 5+ (MediaSession)
  BtBridge.java      consulta directa a la app Bluetooth de fábrica (Autochips/Clinkway)
  NotifArt.java      carátula de Android Auto desde la notificación de Open Headunit
  Weather.java       clima (Open-Meteo)
  Updates.java       avisos de versiones nuevas (GitHub Releases)
  CarPhoto.java      quitar fondo y ajustar la foto del vehículo
  Net.java           HTTPS con TLS 1.2 y certificados actuales en Android 4.x
app/src/hiddenapi/   stub de API oculta de Android 4.x (solo para compilar, no entra al APK)
app/src/legacy/      RcDisplay: única clase que extiende la API oculta (se carga por reflexión)
```

## Publicar una versión (mantenedores)
1. Sube `versionCode` y `versionName` en `app/build.gradle`.
2. Escribe `changelogs/X.Y.Z.md` y `fastlane/metadata/android/*/changelogs/<versionCode>.txt`.
3. Haz commit y súbelo a `main` (o sube una etiqueta `vX.Y.Z`).
4. GitHub Actions compila y firma el APK, crea el Release y actualiza `update.json`, el archivo que la app usa para avisar a los usuarios.
