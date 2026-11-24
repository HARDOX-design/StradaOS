# Mapa y video en la tarjeta del centro

PR #64 de Jaacern, integrado en la 2.35 (Bloque 22) como carrusel. Opción nueva (de fábrica sigue solo el mapa):
*Ajustes → Mapa y clima → Mapa → «En la tarjeta del centro: Mapa y video»*. La tarjeta del mapa pasa a ser un carrusel
sin fin con dos páginas: el mapa (con todo lo de siempre: botones, clima, navegación) y los videos de la memoria y de
las USB. Deslizar a cualquier lado pasa a la otra; unos puntitos abajo indican cuál se ve y se desvanecen a los 3,5 s.

Código: [`VideoCard.java`](../app/src/main/java/com/stradaos/launcher/VideoCard.java),
[`CenterPager.java`](../app/src/main/java/com/stradaos/launcher/CenterPager.java) (el carrusel); en `Main.java`:
`buildAaCard()` (arma las páginas), `startCenterPager()`, `applyCenterOverlays()`, `currentKmh()`, `videoMode()`,
`onPause()`/`onResume()`, `pickVideoDir()` y los ajustes de la sección «Mapa».

## Carrusel

- **Solo una página activa:** la que no se ve queda invisible (el mapa no dibuja ni pide teselas) y el video se pausa,
  guarda el segundo y suelta el `MediaPlayer` (`VideoCard.setActive(false)`). La tarjeta del video no busca archivos
  hasta que se muestra por primera vez. La página se recuerda (`center_page`).
- **Mapa «Seguir» / «Explorar»** (`LiveMapView`): tocar el mapa entra a «Explorar»: se arrastra con el dedo, se acerca o
  aleja con dos dedos, doble toque o los botones + − (zoom 4 a 18, o al máximo de la fuente), con el norte arriba. En
  «Explorar» deslizar no cambia de página. El botón de centrar o «atrás» vuelven a «Seguir»; con el carro andando
  (más de 5 km/h) vuelve solo tras 12 s sin tocarlo (`RETURN_MS`). Faro y Carbono usan sus propios botones de zoom y
  centrar; los demás temas dibujan los suyos a la derecha. Sin carrusel, arrastrar el mapa también entra a «Explorar».
  Lejos (zoom < 9) el mapa sin conexión no se dibuja desde el archivo (serían teselas enormes).
- **Carbono:** el reproductor y el clima flotantes se esconden en la página del video y mientras se explora el mapa.
- Si la pantalla tiene el mapa en vivo apagado, la página del mapa muestra lo de siempre (el dibujo de adorno).

## Cómo funciona

- **Dónde busca:** la memoria interna, `SECONDARY_STORAGE`, `/storage` y `/mnt` (las USB), hasta 5 niveles y 1000
  videos, igual que el buscador de mapas `.map`. Formatos por extensión: `.mp4 .m4v .mkv .webm .3gp .mov .avi .ts`
  (más de 100 KB). En Android 6+ pide el permiso de almacenamiento al elegir «Video».
- **Carpeta:** «Carpeta de videos» lista las carpetas que tienen videos (con cuántos) o «Todas».
- **Reproducción:** `MediaPlayer` dibujando en un **`TextureView`** (el reproductor de Android de la radio: MP4/H.264 y
  la mayoría de los MKV, según el hardware). No se usa `VideoView` porque va en una capa aparte (`SurfaceView`) que no
  admite transparencia.
- **Aspecto y bordes:** el video se ajusta a la tarjeta sin deformarse (entra completo y centrado, `fit()`), y sus
  bordes se desvanecen hacia el fondo del tema con `VideoCard.Feather`: el contenido se dibuja en una capa
  (`saveLayer`) y una máscara `ALPHA_8` (opaca al centro, transparente en el borde, curva suave en el 8 % del lado
  corto) le recorta la transparencia con `DST_IN`. La tarjeta no tiene fondo propio ni el mapa de adorno detrás. La tapa
  de «en movimiento» usa el mismo difuminado.
- **Borde iluminado:** encima del video va `VideoCard.GlowEdge`, la misma luz de las tarjetas «Música» y «Mi vehículo»
  del tema Cobalto (`Cobalto.SkewCard`): una línea fina (`Cobalto.glassEdge`) que se apaga hacia el medio de los lados y
  un brillo radial (núcleo casi blanco y halo azul) en las esquinas de arriba a la izquierda y abajo a la derecha. En el
  tema claro, solo la línea blanca. Va fuera del difuminado, así que se ve nítida aunque el video se desvanezca debajo.
- **Lista:** pasa al siguiente al terminar; si un video no se puede leer, lo avisa y salta al siguiente. Recuerda el
  video y el segundo donde quedó (`video_last`, `video_pos`) al salir de la pantalla de inicio.
- **Controles dentro del video:** el título arriba y los botones (anterior, pausa, siguiente, pantalla completa) abajo
  al centro, en una capa del tamaño del video. Aparecen al tocar el video y se esconden solos a los 4 s mientras
  reproduce (en pausa quedan a la vista). Antes iban en el borde de la tarjeta, y cada tema pone ahí sus propios botones:
  en **Faro** la barra de búsqueda, el botón de navegación y el zoom tapaban los del video (el ⛶ no respondía), y en
  **Aurora** el título se montaba sobre la etiqueta del GPS.
- **Solo video (ícono de cuatro esquinas, el mismo del «Solo mapa» de Aurora):** en **todos los temas** el video ocupa el
  inicio entre la hora y el dock, sobre el color de fondo del tema (`Main.videoMode(v, 1)`: una capa dentro del tablero,
  de `y = 62` a `dockTop − 12/16`, con elevación para quedar sobre las tarjetas con sombra). En **Carbono** ocupa la
  tarjeta del vehículo y el mapa, debajo de su barra de arriba y sin tapar la barra de apps.
- **Pantalla completa (⛶):** dentro de StradaOS. `Main.videoMode(v, 2)` mueve la tarjeta a una capa negra sobre todo
  el inicio; el video sigue desde el mismo segundo (al cambiar de lugar, el `TextureView` recrea su superficie y la
  tarjeta reabre el video donde iba). ✕ o «atrás» la devuelven a su lugar. **Mantener presionado ⛶** abre el video en
  **VLC** desde el segundo donde iba (solo con el carro detenido: en VLC no hay tapa) (extra `position`), o en otra app de video si VLC no está (esas apps no saben de la
  posición: empiezan de cero). El archivo se entrega con
  `ApkProvider.share(file, ext)`, que ahora da el tipo de video correcto.
- No se puede meter VLC dentro de la tarjeta: VLC no ofrece un widget y su motor (libVLC) pesa ~30 MB.

## En movimiento

Sobre **5 km/h** (`currentKmh()`: la velocidad del GPS de hace menos de 5 s o la del escáner OBD, la mayor), según
«Video en movimiento» (`video_moving`):

| Modo | Qué pasa |
|---|---|
| **Tapar la imagen** (`cover`, de fábrica) | una tapa opaca «Imagen en pausa mientras manejas · el audio sigue»; al detenerse vuelve la imagen |
| **Seguir con un aviso** (`warn`) | «No te distraigas mientras conduces» con **OK**; al tocarlo el video sigue a la vista hasta la próxima detención (5 s detenido vuelve a armar el aviso: sale cada vez que el carro arranca) |

La ley de tránsito prohíbe ver video mientras se conduce: de fábrica queda `cover`, y `warn` es una elección explícita del
conductor (avisada en el propio ajuste); Andrés decidió conservarlo. **Sin datos de velocidad** (sin GPS ni escáner)
cuenta como en movimiento: con `cover` se tapa con «Solo se muestra con el carro detenido» y con `warn` sale el aviso,
las dos con la línea «Sin GPS ni escáner no se sabe si el carro va andando».

## Preferencias

| Clave | Tipo | Defecto | Descripción |
|---|---|---|---|
| `center_card` | String | `map` | `map` (solo el mapa) o `both` (carrusel mapa y video) |
| `center_page` | int | `0` | página del carrusel: 0 mapa, 1 video |
| `video_dir` | String | `""` | carpeta de videos; vacía = toda la memoria y las USB |
| `video_moving` | String | `cover` | `cover` o `warn` |
| `video_last`, `video_pos` | String, int | — | último video y segundo donde quedó |

## Probado

**2.35:** pasos nuevos en `regresion.sh` con un MP4 de prueba (`.github/scripts/prueba-video.mp4`, 480×270, 12 s):
carrusel en cada tema, «Solo video», pantalla completa, velocidad simulada (`video-vel:`), aviso y «Explorar».


**Temas (22-nov):** con los controles dentro del video, sin choques en Aurora, Cobalto, Faro y el clásico; pantalla
completa dentro de StradaOS y vuelta con «atrás» probadas en Faro (el que fallaba). En Aurora, «Solo mapa» agranda la
tarjeta con el video y funciona.

**El emulador se cae con video 1080p** (`gfxstream … glestranslator … null ctx`, código 139): pasó 3 veces, con la
Galería y con StradaOS reproduciendo el MP4 1080p, con `-gpu auto` y con `-gpu swiftshader_indirect`. Es la emulación
gráfica del emulador, no la app; para probar en el emulador conviene un video chico (640×360). En la radio decodifica el
hardware real.

En el emulador (API 28, 1024×600) con un MP4 de prueba y un MP4 H.264 1080p de 63 min (420 MB) en `/sdcard/Movies`:
bordes desvanecidos y proporción 16:9 correctas, sin errores en el registro. Se ve en la tarjeta del mapa con sus
controles; con el auto simulado andando aparece la tapa (modo `cover`) o el aviso con OK (modo `warn`), y al tocar OK el
video sigue a la vista. **Falta:** probarlo en la radio real (formatos que decodifica, USB de verdad) y la pantalla
completa en VLC.

Textos: traducidos a los 11 idiomas (`i18n_check.py` sin errores).
