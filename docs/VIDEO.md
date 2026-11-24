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
- **Carpeta:** «Carpeta de videos» (Ajustes) lista las carpetas que tienen videos (con cuántos) o «Todas» y busca también
  en sus subcarpetas.
- **Elegir video (☰ o tocar el título), 2.35 r3:** `VideoPicker`, un gestor de archivos dentro de una ventana de
  StradaOS. Arriba los almacenamientos (memoria de la pantalla y cada USB o tarjeta de `Backup.removable`); adentro solo
  las carpetas que llevan a algún video (con cuántos) y los videos con miniatura recortada al centro, duración y tamaño.
  «‹» o Atrás sube una carpeta (en los almacenamientos, Atrás cierra). Abre en la carpeta del video que suena (marcado
  con el borde del acento y «Reproduciendo»). Al elegir un video, la lista de reproducción pasa a ser **esa carpeta,
  sin subcarpetas** (`video_dir` + `video_flat = true`); si después esa carpeta ya no está (USB quitada), vuelve a buscar
  en todo. Elegir una carpeta en Ajustes deja `video_flat = false`. Miniaturas: `MediaMetadataRetriever` en un hilo de
  baja prioridad, de a una y primero las filas a la vista; cuadro del 15 % del video (máx. 15 s; el primero suele ser
  negro), 192 px de ancho (Android 8.1+ con `getScaledFrameAtTime`, sin armar el cuadro completo en memoria); caché en
  `cache/vthumbs/` (`<hash>.jpg` + `<hash>.d` con la duración, hasta 300) y en memoria (RGB_565, 1/16 de la memoria
  de la app entre 3 y 12 MB).
- **Selector rápido (2.35 r4):** la búsqueda queda en memoria (`VideoPicker.cache`) y se arma sola 4 s después de que
  carga la tarjeta (`prewarm`): al abrir se ve al instante y en otro hilo se pone al día sin mover la carpeta ni el
  lugar de la lista. La primera vez, mientras busca, muestra lo que Android ya tiene indexado (`MediaStore`). Las
  duraciones salen de `MediaStore` (aparecen apenas se ve la fila) y las miniaturas, de las que Android ya hizo
  (`loadThumbnail` en Android 10+, `Video.Thumbnails` antes); solo si no hay, se saca un cuadro del video. Dos hilos de
  miniaturas en equipos de 4 núcleos o más con Android 5+, uno en los demás. Las USB de muchas radios no se indexan:
  para ellas sigue la búsqueda en carpetas.
- **Reintento al cambiar de video (2.35 r4):** si un video falla al abrir, se reintenta una vez, sin aviso, a los
  0,5 s (con videos 4K/8K el decodificador del anterior a veces todavía no está libre). Si vuelve a fallar: «No se
  puede reproducir aquí» y a los 2 s el siguiente, salvo que ya se haya cambiado de video.
- **Radios con un solo decodificador (2.35 r5):** en el Picanto (Android 4.2, Autochips AC8227) un video 720p «no se
  podía reproducir», otro de 51 MB sonaba sin imagen (quedaba el último cuadro del anterior) y, después de un fallo, ni
  el video chico abría hasta cambiar de tema. Causas y arreglos:
  - **Superficie nueva para cada video** (`newTexture`): cada video y cada reintento usan un `TextureView` nuevo. La
    superficie de un video que no abrió quedaba inservible (cambiar de tema la recreaba) y conservaba el último cuadro.
  - **Miniaturas y reproductor por turnos:** muchas radios tienen un solo decodificador de video, y el selector saca
    miniaturas con él (en Android 4.x, `MediaMetadataRetriever` prueba el decodificador por software, que solo lee
    H.264 Baseline, y si no, el de la radio). Ahora el reproductor espera hasta 3 s a que terminen las miniaturas en
    curso (`VideoCard.extracting`) y las miniaturas no empiezan mientras un video abre (`VideoCard.openingUntil`; se
    anotan antes de mirar la hora, así uno de los dos siempre espera al otro). Una miniatura que falla con un video
    sonando no se guarda como «sin miniatura» (marca `<hash>.n` solo si falló sin video sonando); las de antes se
    reintentan.
  - **Reintentos:** hasta 2, a los 0,7 s y 1,5 s, con superficie nueva. Si suena pero en 4 s no llega ningún cuadro
    (`pictureCheck`, por `onSurfaceTextureUpdated`), cuenta como fallo. Al final: aviso en el centro del video (no se
    esconde con los controles) con el formato («1080×1920 · H.264 High 4.0») y «Mantén presionado el botón de pantalla
    completa para abrirlo en VLC u otra app». Si no abre ninguno de la lista, deja de saltar.
  - **Duración y posición solo con el reproductor listo** (`prepared`): antes de estar listo, la radio daba 514:54:48.
  - **Diagnóstico del video** (Ajustes → Mapa y clima, y `StradaDiag/video.txt`, `VideoLog`): la pantalla, sus
    decodificadores de video (H.264: nivel máximo por perfil; Android 5+: tamaño máximo) y por cada video el formato
    (códec, tamaño, perfil/nivel de H.264 leído del SPS de `csd-0`), si la pantalla dice que lo decodifica, y qué pasó
    (preparado, primer cuadro, errores `what/extra`, avisos `info`, reintentos). Los decodificadores se leen una sola vez
    antes del primer video: en Android 4.x leerlos abre cada decodificador.
- **Lo que mostró el diagnóstico del Picanto (2.35 r6):** `ATC AC8227 (autochipsac83xx)`; su decodificador de H.264
  (`OMX.mtk.video.decoder.avc`) declara **hasta nivel 4.1** (Baseline/Main/High/High10). `lv_0…mp4` (CapCut) es
  **1080×1920 H.264 High 4.2**: el reproductor queda «Preparado 0×0» (descarta la pista de video, `info 1000/0`) y suena
  sin imagen; el reproductor de la radio también lo muestra negro. Es el límite de la radio. `Naturaleza - Pixabay.mp4`
  (1280×720 Main 3.1, dentro de lo que declara) falla al instante con `error 1/303` (propio de MediaTek). El `.mov` se
  ve, pero `MediaExtractor` no encontraba su pista de video por la ruta. Cambios:
  - **Veredicto correcto:** el decodificador `OMX.mtkwfd…avc` (el de duplicar la pantalla por Wi-Fi) no declara
    niveles y hacía que todo saliera «OK»; ahora los que no declaran no cuentan si otro del mismo códec sí. `Info.limit`
    («H.264 4.1») y `why` traducidos.
  - **Si la pantalla dice que no puede:** no se reintenta; aviso con el formato, el porqué y «Conviértelo a H.264 de 720p
    o 1080p horizontal, a 30 cuadros por segundo».
  - **En el selector:** cada video se lee sin decodificar (`VideoLog.probeCached`, un hilo aparte) y los que no son
    compatibles lo dicen en rojo: «No compatible con esta pantalla · 1080×1920 · H.264 High 4.2».
  - **Por descriptor:** el primer reintento abre el archivo y pasa el `FileDescriptor` en vez de la ruta; el lector de
    formato y las miniaturas también lo prueban si por la ruta no leen nada.
  - **Miniaturas en Android 4:** con el selector abierto, el video suelta el decodificador (`holdForPicker`; queda el
    último cuadro y, si se cierra sin elegir, sigue donde iba). Las miniaturas que fallan sin video sonando se anotan en
    el diagnóstico («Sin miniatura»).
  - El diagnóstico anota también fps, la pista de audio y si se leyó por descriptor (`longText`).
- **Línea de tiempo (2.35 r3):** `VideoCard.Timeline`, encima de los botones: lo que va, la barra con su punto y lo que
  dura. Tocar o arrastrar mueve el punto y al soltar salta ahí (`seekTo`); mientras se arrastra el carrusel no se lleva
  el gesto (`requestDisallowInterceptTouchEvent`) y los controles no se esconden. Se esconde con los controles.
- **Videos HDR (2.35 r3):** los `.MOV` del iPhone (HLG o Dolby Vision) y los HDR10 se veían lavados: Android los dibuja
  sin convertir en una pantalla normal. `VideoCard.isHdr` lee la pista con `MediaExtractor` (`color-transfer` 6 o 7, o
  `video/dolby-vision`) y, si es HDR, `tex.setLayerPaint` con más saturación (×1,35) y contraste (×1,15). Aproximado,
  no es una conversión exacta; los demás videos no se tocan. Android 4.2+.
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

Sobre **5 km/h** (`currentKmh()`: la velocidad del GPS de hace menos de 5 s o la del escáner OBD, la mayor). **Sin datos
de velocidad** (sin GPS ni escáner) cuenta como en movimiento («Solo se muestra con el carro detenido» + «Sin GPS ni
escáner no se sabe si el carro va andando»). Según «Video en movimiento» (`video_moving`):

| Modo | Qué pasa |
|---|---|
| **Preguntar** (`ask`, de fábrica) | tapa con el video difuminado detrás y «¿Quieres seguir viendo el video?» **Sí / No**. Sí → aviso «Cuidado al manejar» (`Main.confirmVideoMoving`); solo con «Seguir viendo» se ve el video, hasta la próxima detención (5 s detenido). No → queda la tapa sin pregunta (tocarla vuelve a preguntar). |
| **Tapar siempre** (`cover`) | la tapa, sin pregunta |

(`warn`, de la primera Prueba, cuenta como `ask`.) El audio sigue siempre. VLC (mantener ⛶) solo detenido.

**El difuminado de la tapa (2.35 r3):** en **Android 12+** el video mismo se difumina en vivo en la GPU
(`VideoBlur31`, `RenderEffect.createBlurEffect`, radio 34 × escala) y la tapa queda transparente con el oscurecido
encima: fluido. En **Android 4–11** se copia un cuadro chico (`tex.getBitmap(32, h)`, ×2, `boxBlur` r3 dos veces) unas
8 veces por segundo; si en la pantalla tarda más de 30 ms copia menos seguido (hasta ~3/s) y si va sobrado, hasta ~12/s.
Cada cuadro aparece sobre el anterior con un fundido de la misma duración (dos `ImageView`), así no se ve a saltos. En
la r2 era una copia cada 0,7 s, sin fundido.

## Preferencias

| Clave | Tipo | Defecto | Descripción |
|---|---|---|---|
| `center_card` | String | `map` | `map` (solo el mapa) o `both` (carrusel mapa y video) |
| `center_page` | int | `0` | página del carrusel: 0 mapa, 1 video |
| `video_dir` | String | `""` | carpeta de videos; vacía = toda la memoria y las USB |
| `video_flat` | boolean | `false` | `true` = solo los videos de `video_dir`, sin subcarpetas (elegido en «Elegir video») |
| `video_moving` | String | `ask` | `ask` o `cover` |
| `video_last`, `video_pos` | String, int | — | último video y segundo donde quedó |

## Probado

**2.35:** pasos nuevos en `regresion.sh` con un MP4 de prueba (`.github/scripts/prueba-video.mp4`, 480×270, 12 s):
carrusel en cada tema, «Solo video», pantalla completa, velocidad simulada (`video-vel:`), aviso y «Explorar».
Ronda 5: un «video» dañado en `Movies/Viajes` (bytes al azar): reintentos, aviso y salto; y el diagnóstico del video.
Ronda 3: una subcarpeta `Movies/Viajes` con otro video; el selector en la carpeta del video, entrar a Viajes, Atrás
hasta cerrar, abrir en los almacenamientos (`video-elegir:arriba`), elegir el video de Viajes (`video-fila:N`) y la
línea de tiempo (`video-controles`, `video-ir:6`).


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

## Ronda 7 (24-nov): conclusiones del Picanto y cierre

Diagnóstico de Andrés con la r6 (Picanto, Autochips AC8227, API 17; `OMX.mtk.video.decoder.avc` hasta H.264 4.1):

- `lv_0_…mp4` (1080×1920, H.264 High 4.2, de CapCut): el selector ya lo marca «No compatible con esta pantalla»; se
  prepara como 0×0 y suena sin imagen; tampoco se ve en el reproductor de la radio. **Límite del decodificador.**
- `naturaleza.mp4` (1280×720, H.264 Main 3.1, de Pixabay; también renombrado sin espacios): `error 1/303` al instante
  por ruta, por descriptor y en el segundo reintento. Dentro de lo que declara la pantalla, pero el reproductor de la
  radio lo rechaza (algo del archivo que el Stagefright de 4.2 no lee). **Límite de la radio**, no de StradaOS.
- `.mov` 480×270: se ve (aunque `MediaExtractor` diga «sin video» en 4.2).
- Miniaturas: `MediaMetadataRetriever` no saca ningún cuadro en esta radio, ni con el video detenido.

Hecho en la r7: los videos que no abrieron quedan marcados (`vthumbs/<clave>.x`, se borra si un día se ve) y salen en
rojo en el selector; ⏮ ⏭ y el salto automático se los saltan (desde el selector se pueden volver a probar). La miniatura
se toma del video mientras suena (`VideoCard.grabThumb`: a los 2,5 s, reintenta si sale casi negra) y se guarda como las
demás (`Thumbs.fromPlayer`).

**Recomendación para radios viejas** (va en las notas de la 2.35.0): H.264, horizontal, 720p o 1080p, 30 fps, audio
AAC (HandBrake «Fast 720p30»/«Fast 1080p30»; CapCut a 1080p 30 fps). Nada vertical, 60 fps, 4K/8K, HEVC ni HDR. VLC con
la aceleración por hardware apagada como último recurso. Las pantallas Android 9+ con chips actuales (UIS7862, SC9863A,
PX6) no tienen estos límites; la versión real de Android se ve en Ajustes → Acerca de (API).
