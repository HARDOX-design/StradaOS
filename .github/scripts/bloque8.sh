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
go diseno:velo-2 5; go vel:0 1; shot 01-mixto-0
go vel:62 3; shot 02-mixto-62
go vel:148 3; shot 03-mixto-148
go diseno:velo-1 5; go vel:95 3; shot 04-analogico-95
go tema:claro 6; go diseno:velo-2 5; go vel:80 3; shot 05-claro-mixto-80
go tema:oscuro 6
go diseno:sinauto 4; go diseno:velomapa 5; go vel:72 3; shot 06-mapa-72
go diseno:base 4; go diseno:velo-2 4; go diseno:compacta 5; go vel:110 3; shot 07-compacta-110
go diseno:base 4; go diseno:velo-2 4; go diseno:lateral 5; go vel:40 3; shot 08-lateral-40
go diseno:base 4; go diseno:velo-2 4
go demo-aceite 5; go demo-mant 6; shot 09-mantenimiento-inicio
go menu-mant 4; shot 10-menu-mantenimiento
adb shell input keyevent 4; sleep 2
go aviso-mant 4; shot 11-aviso-mantenimiento
adb shell input keyevent 4; sleep 2
go ajustes:1 6; adb shell input swipe 640 500 640 100 600; sleep 2; adb shell input swipe 640 500 640 100 600; sleep 2; shot 12-ajustes-mantenimientos
go inicio 3; go idioma:en 6; go vel:55 3; shot 13-ingles; go idioma:es 6
go ajustes:2 6; adb shell input swipe 640 480 640 200 500; sleep 2; shot 14-apariencia
go inicio 3; go diseno:base 4
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -40 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
