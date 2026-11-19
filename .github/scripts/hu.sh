#!/bin/bash
# Húngaro (2.25.2): que los textos largos quepan en la pantalla.
# Uso: hu.sh API APK CARPETA
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
# El emulador anuncia IPv6 pero la máquina de GitHub no tiene salida IPv6: sin esto no responden los servidores de rutas
if [ "$API" -ge 21 ]; then
  adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
  adb shell sysctl -w net.ipv6.conf.all.disable_ipv6=1 >/dev/null 2>&1
  adb shell sysctl -w net.ipv6.conf.default.disable_ipv6=1 >/dev/null 2>&1
  sleep 3
fi
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
sc() { adb shell input swipe 600 520 600 120 400; sleep 2; }
# Budapest
( while true; do adb emu geo fix 19.0402 47.4979 >/dev/null 2>&1; adb emu geo fix 19.0402 47.4979 120 8 26 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:hu 6; go tema:oscuro 6; go inicio 4; sleep 12
go pista-demo:Night_Drive 3; shot h01-inicio
for c in 0 1 2 3 4 5 6 7 8 9 10 11; do n=$(printf %02d $c); go ajustes:$c 5; shot h1$n-a; sc; shot h1$n-b; sc; sc; shot h1$n-c; done
go inicio 3; go asistente 5; shot h20-asistente; adb shell input keyevent 4; sleep 2; go inicio 3
go tema-premium:aurora 6; go pista-demo:Night_Drive 3; shot h31-aurora
go tema-premium:cobalto 6; go pista-demo:Night_Drive 3; shot h32-cobalto
go tema-premium:faro 8; go pista-demo:Night_Drive 3; shot h33-faro
go demo-mant 4; shot h34-faro-mant
go tema:claro 6; shot h35-faro-claro
go clima 5; shot h36-clima; adb shell input keyevent 4; sleep 2
go tema-gratis:1 6; go tema:oscuro 5; go inicio 3
kill $GEO 2>/dev/null
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
