#!/bin/bash
# Bloque 14 (2.24): Personalización (temas gratis).
# Uso: bloque14.sh API APK CARPETA
API=$1; APK=$2; OUT=$3; mkdir -p $OUT
PKG=com.stradaos.launcher.debug
ACT=$PKG/com.stradaos.launcher.Main
adb wait-for-device
for i in $(seq 1 100); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 15
adb shell input keyevent 82 || true
size() {  # tamaño de la pantalla, densidad 160
  if [ "$API" -ge 18 ]; then adb shell wm size $1; adb shell wm density 160
  else adb shell am display-size $1; adb shell am display-density 160; fi
  sleep 6
}
size 1024x600
SW=1024; SH=600
if [ "$API" -ge 23 ]; then adb install -r -g "$APK" | tail -1; else adb install -r "$APK" | tail -1; fi
shot() {
  rm -f /tmp/shot.png; rm -rf /tmp/emushot; mkdir -p /tmp/emushot
  adb shell screencap -p /data/local/tmp/s.png >/dev/null 2>&1 && adb pull /data/local/tmp/s.png /tmp/shot.png >/dev/null 2>&1
  if [ ! -s /tmp/shot.png ] || [ "$(stat -c %s /tmp/shot.png)" -lt 15000 ]; then
    adb emu screenrecord screenshot /tmp/emushot >/dev/null 2>&1; sleep 2
    f=$(ls -t /tmp/emushot/*.png 2>/dev/null | head -1)
    [ -n "$f" ] && cp "$f" /tmp/shot.png
  fi
  [ -s /tmp/shot.png ] && cp /tmp/shot.png "$OUT/$1.png" && echo "captura $1"
}
go() { adb shell am start -n $ACT --es prueba "$1" >/dev/null; sleep "${2:-5}"; }
back() { adb shell input keyevent 4; sleep 2; }
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
go idioma:es 6; go tema:oscuro 6; go inicio 4
demo() { go inicio 4; go pista-demo 3; go vel:62 3; }
# Fase 2: tema premium «Aurora» (en la versión de prueba se aplica sin código)
go ajustes:11 5; adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2
shot a01-galeria-premium
go tema-premium:aurora 6; shot a02-aplicado-ajustes
adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2; shot a03-galeria-en-uso
demo; shot a10-aurora-oscuro
go pref:speedo=2 4; demo; shot a10b-mixto
go solo-mapa-anim 1; shot a10c-transicion
sleep 2; go pista-demo 2; go vel:62 3; shot a10d-solo-mapa-mixto
go solo-mapa-anim 1; shot a10e-transicion-vuelta
sleep 3
go solo-mapa:1 6; go pista-demo 3; go vel:62 3; shot a11-solo-mapa
go solo-mapa:0 6; go tema:claro 7; demo; shot a12-aurora-claro
go solo-mapa:1 6; shot a13-claro-solo-mapa
go solo-mapa:0 5
go ajustes:3 5; shot a14-pantalla-inicio-aviso
go ajustes:11 5; go vista-temas:v 4; adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2; shot a15-galeria-vista-vertical
go vista-temas:h 3
go tema:oscuro 6
go pref:weather_on=false 4; demo; shot a16-sin-clima
go pref:weather_on=true 4
go pref:dock_count=10 4; demo; shot a17-diez-botones
go pref:dock_count=7 4
go idioma:en 6; demo; shot a18-ingles
go idioma:ar 6; demo; shot a19-arabe
go idioma:es 6
if [ "$API" -ge 18 ]; then
  size 1280x480; demo; shot a20-1280x480
  size 1920x720; demo; shot a21-1920x720
  size 1080x1920; demo; shot a30-vertical
  go solo-mapa:1 6; shot a31-vertical-solo-mapa
  go solo-mapa:0 5; go tema:claro 7; demo; shot a32-vertical-claro
  go tema:oscuro 6
  go ajustes:11 6; adb shell input swipe 500 1700 500 300 400; sleep 2; adb shell input swipe 500 1700 500 300 400; sleep 2; shot a33-vertical-galeria
  size 768x880; demo; shot a34-vertical-bajo
  size 1024x600
else
  go sim:7 6; demo; shot a30-vertical
  go solo-mapa:1 6; shot a31-vertical-solo-mapa
  go solo-mapa:0 5; go sim:0 6
fi
# Cobalto
go tema:oscuro 6; go tema-premium:cobalto 6; demo; shot c10-cobalto-oscuro
go pref:speedo=2 4; demo; shot c11-cobalto-mixto
go pref:speedo=1 4; demo; shot c12-cobalto-analogico
go pref:speedo=0 4; demo; shot c13-cobalto-digital
go pref:speedo=2 4
go tema:claro 7; demo; shot c16-cobalto-claro
go foto-demo:recorte 5; demo; shot c18-foto-recortada
go foto-demo:normal 5; demo; shot c19-foto-con-fondo
go tema:oscuro 6; go foto-demo:recorte 5; demo; shot c18b-foto-recortada-oscuro
go tema:claro 6
go ajustes:11 5; adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2; shot c17-galeria
go tema:oscuro 6
if [ "$API" -ge 18 ]; then
  size 1280x480; demo; shot c20-1280x480
  size 1080x1920; demo; shot c30-vertical
  go foto-demo: 5; demo; shot c30b-vertical-sin-foto
  go foto-demo:recorte 5
  go tema:claro 7; demo; shot c31-vertical-claro
  go tema:oscuro 6; size 768x880; demo; shot c32-vertical-bajo
  size 1024x600
else
  go sim:7 6; demo; shot c30-vertical
  go sim:0 6
fi
# un tema gratis quita el premium y todo vuelve a lo de siempre
go tema-gratis:1 6; demo; shot a40-gratis-quita-premium
go inicio 5
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
