#!/bin/bash
# 2.29: velocímetro del inicio con la velocidad del escáner OBD-II. Uso: obdvel.sh API APK CARPETA
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
# GPS a 10 nudos (~19 km/h): así se distingue de la velocidad del auto simulado
( while true; do adb emu geo fix -74.0836 4.6533 2600 8 10 >/dev/null 2>&1; sleep 1; done ) &
GEO=$!
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 6
shot v01-gps
go obd-demo 20; sleep 40; go inicio 4
shot v02-demo-sin-usar
go obd-vel-demo:1 4; go inicio 3
shot v03-obd; sleep 3; shot v04-obd-2
go tema-premium:faro 6; go inicio 4; shot v05-faro-obd
go tema-gratis:1 6
go tema-premium:aurora 6; go inicio 4; shot v06-aurora-obd
go tema-gratis:1 6; go inicio 3
go obd-ajustes:obd_adv 4; adb shell input swipe 600 400 600 250 300; sleep 2; shot v07-ajuste
go obd-off 6; go inicio 4; shot v08-sin-escaner
go obd-vel-demo:0 2
kill $GEO 2>/dev/null
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls $OUT
