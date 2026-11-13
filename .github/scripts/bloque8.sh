#!/bin/bash
# Bloque 8 (2.20): velocímetro analógico/mixto, sobre el mapa, y mantenimientos a medida.
API=$1; OUT=$2; mkdir -p $OUT
PKG=com.stradaos.launcher.debug
ACT=$PKG/com.stradaos.launcher.Main
adb wait-for-device
for i in $(seq 1 100); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 15
adb shell input keyevent 82 || true
if [ "$API" -ge 23 ]; then adb install -r -g prueba.apk | tail -1; else adb install -r prueba.apk | tail -1; fi
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
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
go diseno:velo-2 5; go vel:85 3; shot 01-mixto-85
go diseno:sinauto 4; go diseno:velomapa 4; go diseno:esquina-tl 5; go vel:60 3; shot 02-mapa-arriba-izq
go diseno:esquina-br 5; go vel:60 3; shot 03-mapa-abajo-der
go diseno:esquina-bl 4; go diseno:velotam-2 5; go vel:60 3; shot 04-mapa-abajo-izq-grande
go diseno:velotam-0 4; go diseno:esquina-tr 5; go vel:60 3; shot 05-mapa-arriba-der-pequeno
go diseno:base 4; go diseno:velo-2 4
go demo-aceite 5; go demo-mant 6; shot 06-inicio-mantenimiento
go menu-mant 4; shot 07-menu-mantenimiento
adb shell input keyevent 4; sleep 2
for c in 1 2 3 4 5 6 7 8 9 10; do go ajustes:$c 6; shot 1$((c+0))-ajustes-$c; adb shell input swipe 640 500 640 120 600; sleep 2; shot 1$((c+0))-ajustes-$c-b; done
go inicio 3; go diseno:base 4
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -40 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
