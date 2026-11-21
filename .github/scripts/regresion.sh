#!/bin/bash
# Prueba de regresión: recorre TODAS las pantallas, subpáginas y diálogos que tienen gancho de prueba, en orden,
# y anota en qué paso se cierra la app (el proceso cambia de número) además del registro de cierres de Android.
# Corre en cada push a una rama wip-* (regresion.yml). Uso: regresion.sh API APK CARPETA
API=$1; APK=$2; OUT=$3; mkdir -p $OUT
PKG=com.stradaos.launcher.debug
ACT=$PKG/com.stradaos.launcher.Main
adb wait-for-device
for i in $(seq 1 100); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 15
adb shell input keyevent 82 || true
if [ "$API" -ge 18 ]; then adb shell wm size 1024x600; adb shell wm density 160
else adb shell am display-size 1024x600; adb shell am display-density 160; fi
sleep 6
if [ "$API" -ge 23 ]; then adb install -r -g "$APK" | tail -1; else adb install -r "$APK" | tail -1; fi
adb logcat -v brief '*:E' 'dalvikvm:I' 'DEBUG:I' 'AndroidRuntime:I' > $OUT/cierres-raw.txt 2>&1 &
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
# ── escáner OBD-II: panel, subpáginas y guía ──
step obd-demo obd-demo 20
step obd-panel obd-panel 4 1
for p in obd obd_conn obd_alerts obd_codes obd_live obd_ai obd_adv obd_guide; do step "obd-$p" "obd-ajustes:$p" 4; scroll; scroll; done
for s in comprar conector panel alertas codigos datos ia limites; do step "guia-$s" "obd-ajustes:obd_guide:$s" 4; done
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
# ── navegación ──
step nav-buscar nav-buscar 6 1
step solo-mapa solo-mapa:1 6
step solo-mapa-off solo-mapa:0 4
step nav-salir nav-salir 4
# ── idiomas: inicio y ajustes en cada uno ──
for l in en pt it de ms ar tr fr zh hu pl es; do step "idioma-$l" "idioma:$l" 6; step "ajustes-$l" "ajustes:9" 4; done
step final inicio 4
# el asistente va al final: su primer paso (idioma) no se cierra con «atrás» y taparía los pasos siguientes
step asistente asistente 5
sleep 3; kill $GEO $CR 2>/dev/null
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
