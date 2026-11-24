#!/bin/bash
# Prueba de regresión: recorre TODAS las pantallas, subpáginas y diálogos que tienen gancho de prueba, en orden,
# y anota en qué paso se cierra la app (el proceso cambia de número) además del registro de cierres de Android.
# Se lanza a mano antes de entregar una Prueba y antes de publicar (regresion.yml), partida en 3 trabajos por Android
# que corren en paralelo. Uso: regresion.sh API APK CARPETA [PARTE: 1, 2, 3 o all]
API=$1; APK=$2; OUT=$3; PART=${4:-all}; mkdir -p $OUT
part() { [ "$PART" = all ] || [ "$PART" = "$1" ]; }
PKG=com.stradaos.launcher.debug
ACT=$PKG/com.stradaos.launcher.Main
adb wait-for-device
for i in $(seq 1 100); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 15
adb shell input keyevent 82 || true
if [ "$API" -ge 18 ]; then adb shell wm size 1024x600; adb shell wm density 160
else adb shell am display-size 1024x600; adb shell am display-density 160; fi
sleep 6
# a veces el emulador de Android 4.1 se queda trabado instalando: se reintenta con límite de tiempo
for t in 1 2 3; do
  if [ "$API" -ge 23 ]; then r=$(timeout 180 adb install -r -g "$APK" 2>&1 | tail -1); else r=$(timeout 180 adb install -r "$APK" 2>&1 | tail -1); fi
  echo "instalación $t: $r"; echo "$r" | grep -q Success && break
  adb kill-server; sleep 5; adb wait-for-device; sleep 20
done
adb logcat -v brief '*:E' 'dalvikvm:I' 'DEBUG:I' 'AndroidRuntime:I' 'StradaI18n:W' > $OUT/cierres-raw.txt 2>&1 &
CR=$!
( while true; do adb emu geo fix -74.0836 4.6533 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
# número del proceso de StradaOS Prueba: «ps» lista todo en Android 4–7; desde Android 8 hace falta «ps -A»
pid() {
  local p=$(adb shell ps 2>/dev/null | tr -d '\r' | awk -v p=$PKG '$NF==p {print $2}' | head -1)
  [ -z "$p" ] && p=$(adb shell ps -A 2>/dev/null | tr -d '\r' | awk -v p=$PKG '$NF==p {print $2}' | head -1)
  echo "$p"
}
shot() {
  rm -f /tmp/shot.png; rm -rf /tmp/emushot; mkdir -p /tmp/emushot
  adb shell screencap -p /data/local/tmp/s.png >/dev/null 2>&1 && adb pull /data/local/tmp/s.png /tmp/shot.png >/dev/null 2>&1
  # en Android 4.2 «screencap» da una imagen en blanco: se usa la captura del emulador
  if [ ! -s /tmp/shot.png ] || [ "$(stat -c %s /tmp/shot.png)" -lt 15000 ]; then
    adb emu screenrecord screenshot /tmp/emushot >/dev/null 2>&1; sleep 1
    f=$(ls -t /tmp/emushot/*.png 2>/dev/null | head -1)
    [ -n "$f" ] && cp "$f" /tmp/shot.png
  fi
  [ -s /tmp/shot.png ] && cp /tmp/shot.png "$OUT/$1.png"
}
N=0
# paso: abre el gancho, espera, toma captura, cierra con «atrás» si se pide, y revisa que el proceso siga vivo
step() {
  local name=$1 hook=$2 wait=${3:-4} back=${4:-0}
  local before=$(pid)
  adb shell am start -n $ACT --es prueba "$hook" >/dev/null 2>&1; sleep $wait
  N=$((N+1)); local tag=$(printf %03d $N)-$(echo "$name" | tr ':' '_')
  shot $tag
  [ "$back" = "1" ] && { adb shell input keyevent 4; sleep 2; }
  local after=$(pid)
  echo "$tag $hook pid:$before→$after" >> $OUT/pasos.txt
  if [ -n "$before" ] && [ "$before" != "$after" ]; then echo "CIERRE en el paso $tag ($hook)" | tee -a $OUT/CIERRES.txt; fi
  if [ -z "$after" ]; then echo "APP CERRADA tras el paso $tag ($hook)" | tee -a $OUT/CIERRES.txt; adb shell am start -n $ACT >/dev/null; sleep 8; fi
}
scroll() { adb shell input swipe 600 520 600 120 400; sleep 1; }
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 30
step inicio idioma:es 6
# ════ Parte 1: temas, ajustes y navegación ════
if part 1; then
  # ── temas y apariencias ──
  for t in tema:oscuro tema:claro tema:sistema tema:oscuro; do step tema "$t" 5; done
  for t in faro aurora cobalto; do step "premium-$t" "tema-premium:$t" 6; step "premium-$t-pista" pista-demo:Night_Drive 3; done
  for i in 0 1 2 3 4 5; do step "gratis-$i" "tema-gratis:$i" 5; done
  step gratis-1 tema-gratis:1 5
  # ── ajustes: las 12 secciones, arriba, en medio y abajo ──
  for c in 0 1 2 3 4 5 6 7 8 9 10 11; do
    step "ajustes-$c" "ajustes:$c" 4
    scroll; scroll; shot "$(printf %03d $N)-ajustes-$c-medio"
    scroll; scroll; scroll; shot "$(printf %03d $N)-ajustes-$c-fin"
  done
  step inicio inicio 3
  # ── navegación ──
  step nav-buscar nav-buscar 6 1
  # en Android 4.x el primer «atrás» solo cierra el teclado: otro más cierra el buscador
  adb shell input keyevent 4; sleep 1
  step solo-mapa solo-mapa:1 6
  step solo-mapa-off solo-mapa:0 4
  step nav-salir nav-salir 4
  # ── 2.35: tarjeta del centro con mapa y video (carrusel), mapa explorable y video detenido / en movimiento ──
  adb shell mkdir -p /sdcard/Movies >/dev/null 2>&1
  adb push "$(dirname "$0")/prueba-video.mp4" /sdcard/Movies/prueba-stradaos.mp4 >/dev/null 2>&1
  # una subcarpeta con otro video, para el selector tipo gestor de archivos
  adb shell mkdir -p /sdcard/Movies/Viajes >/dev/null 2>&1
  adb push "$(dirname "$0")/prueba-video.mp4" /sdcard/Movies/Viajes/otro-stradaos.mp4 >/dev/null 2>&1
  # 2.35 r5: un «video» dañado en Viajes (no abre): reintentos con superficie nueva, aviso en el centro y salto al siguiente
  head -c 300000 /dev/urandom > /tmp/roto-stradaos.mp4
  adb push /tmp/roto-stradaos.mp4 /sdcard/Movies/Viajes/roto-stradaos.mp4 >/dev/null 2>&1
  [ "$API" -ge 23 ] && adb shell pm grant $PKG android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1
  step centro-mapa centro:mapa 6
  step explorar explorar:1 4
  step explorar-lejos explorar-zoom:-8 6
  step explorar-cerca explorar-zoom:4 5
  adb shell input keyevent 4; sleep 2; shot "$(printf %03d $N)-explorar-atras"
  step seguir explorar:0 3
  # toques reales sobre la tarjeta: tocar (entra a Explorar), arrastrar el mapa y doble toque
  adb shell input tap 520 190; sleep 2; adb shell input swipe 520 190 420 240 300; sleep 1
  adb shell input tap 520 190; adb shell input tap 520 190; sleep 2
  shot "$(printf %03d $N)-explorar-toques"
  step seguir-2 explorar:0 2
  # explorar con una ruta activa (la ruta sale del carro, no del centro de la vista)
  step nav-demo-ruta nav-demo 8
  step nav-explorar explorar:1 3
  step nav-explorar-lejos explorar-zoom:-2 5
  adb shell input swipe 520 190 400 260 300; sleep 2; shot "$(printf %03d $N)-nav-explorar-arrastre"
  step nav-seguir explorar:0 2
  step nav-salir-2 nav-salir 3
  step video-vel-0 video-vel:0 1
  step centro-video centro:video 9
  step video-solo video-modo:1 5
  step video-completo video-modo:2 5
  step video-tarjeta video-modo:0 4
  step video-andando video-vel:40 4
  step video-cuidado video-cuidado 3 1
  step video-tapar-siempre video-aviso:cover 3
  step video-sin-vel video-vel:nan 3
  step video-preguntar video-aviso:ask 3
  step video-detenido video-vel:0 8
  # selector tipo gestor de archivos: abre en la carpeta del video (Viajes + el video, con miniatura y duración)
  step video-elegir video-elegir 6
  step video-elegir-viajes video-fila:0 5
  # Atrás sube de carpeta (Viajes → Movies → memoria → almacenamientos) y en los almacenamientos cierra
  adb shell input keyevent 4; sleep 2; shot "$(printf %03d $N)-video-elegir-atras"
  adb shell input keyevent 4; sleep 2; shot "$(printf %03d $N)-video-elegir-memoria"
  adb shell input keyevent 4; sleep 2; shot "$(printf %03d $N)-video-elegir-almacenamientos"
  adb shell input keyevent 4; sleep 2; shot "$(printf %03d $N)-video-elegir-cerrado"
  step video-elegir-arriba video-elegir:arriba 5
  step video-elegir-memoria video-fila:0 4
  adb shell input keyevent 4; sleep 1; adb shell input keyevent 4; sleep 2
  # elegir el video de Viajes: la lista pasa a ser esa carpeta
  step video-elegir-2 video-elegir 4
  step video-elegir-entrar video-fila:0 4
  step video-elegir-video video-fila:0 6
  # línea de tiempo: a la vista con los controles y saltar al segundo 6
  step video-controles video-controles 2
  step video-ir video-ir:6 3
  # 2.35 r5: el video termina (12 s) y pasa al dañado: 3 intentos, aviso y vuelta al primero; y el diagnóstico del video
  sleep 8; shot "$(printf %03d $N)-video-danado"
  sleep 6; shot "$(printf %03d $N)-video-danado-aviso"
  step video-diag video-diag 3 1
  step pagina-mapa pagina:1 3
  # deslizar de verdad sobre la tarjeta (de mapa a video y de vuelta)
  adb shell input swipe 700 200 330 200 250; sleep 3
  shot "$(printf %03d $N)-deslizar-video"
  adb shell input swipe 330 200 700 200 250; sleep 3
  shot "$(printf %03d $N)-deslizar-mapa"
  # el carrusel en cada tema (página del video y del mapa)
  for t in tema-premium:aurora tema-premium:cobalto tema-premium:faro tema-premium:carbono tema-premium: ; do
    n=${t#tema-premium:}; [ -z "$n" ] && n=clasico
    step "centro-$n" "$t" 6
    step "centro-$n-video" centro:video 6
    step "centro-$n-solo-video" video-modo:1 4
    step "centro-$n-tarjeta" video-modo:0 3
    step "centro-$n-mapa" centro:mapa 5
    step "centro-$n-explorar" explorar:1 3
    step "centro-$n-seguir" explorar:0 2
  done
  step ajustes-mapa-video ajustes:7 4
  scroll; scroll; shot "$(printf %03d $N)-ajustes-mapa-video-medio"
  step centro-solo centro:solo 5
  step video-aviso-preguntar video-aviso:ask 1
  step video-vel-real video-vel:x 2
fi
# ════ Parte 2: diálogos, música, idiomas y asistente ════
if part 2; then
  # ── diálogos y pantallas sueltas ──
  for d in diag-reproductor diag-mapa clima cajon ocultas musica demo-aceite reposo; do step "$d" "$d" 5 1; done
  step inicio inicio 3
  # ── música: fuentes simuladas y diagnóstico con cada una ──
  MUS="aa-simular:1 aa-notif:Cancion_de_prueba,30,200 aa-rcc:Cancion_de_prueba,200 hcn-sim:/sdcard/Music/prueba.mp3,start,10,200"
  # el aviso del Bluetooth del sistema solo se puede simular hasta Android 7 (después Android lo protege)
  [ "$API" -ge 21 ] && [ "$API" -lt 26 ] && MUS="$MUS avrcp-sim:Cancion_de_prueba,StradaOS,3,10,200"
  for m in $MUS; do step "musica-${m%%:*}" "$m" 4; step "diag-${m%%:*}" diag-reproductor 5 1; done
  step musica-hcn-off "hcn-sim:,stop,0,0" 3
  step musica-aa-off aa-simular:0 3
  step diag-final diag-reproductor 5 1
  # ── idiomas: inicio y ajustes en cada uno ──
  # los diagnósticos también, para que StradaOS Prueba anote las frases sin traducir (sin-traducir.txt)
  for l in en pt it de ms ar tr fr zh hu pl es; do
    step "idioma-$l" "idioma:$l" 6; step "ajustes-$l" "ajustes:9" 4
    [ "$l" != es ] && { step "diagr-$l" diag-reproductor 4 1; step "diagm-$l" diag-mapa 4 1; }
    # 2.35 r7: la ventana de actualización con las notas en el idioma elegido (inglés si no hay sección del idioma)
    case $l in en|pt|es) step "actualizacion-$l" actualizacion-demo 4 1;; esac
  done
  step final inicio 4
fi
# ════ Parte 3: escáner OBD y temas de pago ════
if part 3; then
  # ── escáner OBD-II: panel, subpáginas y guía ──
  step obd-demo obd-demo 20
  step obd-panel obd-panel 4 1
  for p in obd obd_conn obd_alerts obd_codes obd_live obd_ai obd_adv obd_guide; do step "obd-$p" "obd-ajustes:$p" 4; scroll; scroll; done
  for s in comprar conector panel alertas codigos datos ia limites; do step "guia-$s" "obd-ajustes:obd_guide:$s" 4; done
  # 2.31: escáner por cable USB (en el emulador no hay cable: se prueban la lista, el aviso y el error sin cable)
  step obd-elegir obd-elegir 4 1
  step obd-usb obd-usb 4 1
  step obd-usb-sin-cable obd-addr:usb:1A86:7523 12
  step obd-usb-estado obd-ajustes:obd_conn 4
  step obd-demo-otra-vez obd-addr:demo 15
  step obd-vel obd-vel-demo:1 4
  step obd-vel-off obd-vel-demo:0 3
  step obd-off obd-off 4
  # ── temas de pago: desbloqueo, código y paquete ──
  step lic-prueba lic-prueba:1 3
  step desbloquear desbloquear:faro 5 1
  step paquete paquete:1 2
  step desbloquear-paquete desbloquear:faro 5 1
  step paquete-off paquete:0 2
  step lic-prueba-off lic-prueba:0 3
fi
# el asistente va al final: su primer paso (idioma) no se cierra con «atrás» y taparía los pasos siguientes
if part 2; then step asistente asistente 5; fi
sleep 3; kill $GEO $CR 2>/dev/null
# frases que se quedaron en español (solo informa, no hace fallar la prueba)
grep "StradaI18n" $OUT/cierres-raw.txt | sed -E 's/^[^:]*StradaI18n[^:]*: //' | sort -u > $OUT/sin-traducir.txt
grep -E -A30 "FATAL EXCEPTION|VM aborting|Fatal signal|overflow of stack" $OUT/cierres-raw.txt | head -300 > $OUT/logcat.txt
grep -qE "VM aborting|overflow of stack" $OUT/cierres-raw.txt && echo "CIERRE de la máquina virtual (ver logcat.txt)" >> $OUT/CIERRES.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE en el registro de Android (ver logcat.txt)" >> $OUT/CIERRES.txt; fi
# el registro de fallos de la propia app (Guard) también cuenta
adb shell run-as $PKG cat files/fallos.txt > $OUT/fallos.txt 2>/dev/null
if grep -q "CIERRE INESPERADO" $OUT/fallos.txt 2>/dev/null; then echo "CIERRE en el registro de fallos de StradaOS (ver fallos.txt)" >> $OUT/CIERRES.txt; fi
# los ganchos de prueba atrapan el error para seguir (en la pantalla real la app se cerraría): también es un fallo
grep "Prueba «.*» falló" $OUT/fallos.txt 2>/dev/null | sed 's/^/FALLO (en la pantalla real se cerraría): /' >> $OUT/CIERRES.txt
[ -s $OUT/CIERRES.txt ] || rm -f $OUT/CIERRES.txt
echo "pasos: $N"; [ -f $OUT/CIERRES.txt ] && cat $OUT/CIERRES.txt || echo "sin cierres"
