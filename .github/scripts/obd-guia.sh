#!/bin/bash
# 2.26: «Guía del escáner» (Ajustes → Tu vehículo → Escáner OBD-II) en español y en varios idiomas, con el monitor apagado.
# Uso: obd-guia.sh API APK CARPETA
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
sc() { adb shell input swipe 600 520 600 120 400; sleep 2; }
back() { adb shell input keyevent 4; sleep 3; }
adb logcat -c
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go obd-off 4; go inicio 4
go ajustes:1 5; sc; sc; sc; shot es-tu-vehiculo
go obd-ajustes:obd_guide 4; shot es-0-indice
n=1
for S in comprar conector panel alertas codigos datos ia limites; do
  go obd-ajustes:obd_guide:$S 4; shot es-$n-$S; sc; shot es-$n-$S-b; sc; sc; shot es-$n-$S-fin
  n=$((n+1))
done
back; shot es-volver-indice
back; shot es-volver-tu-vehiculo
go tema:claro 5; go obd-ajustes:obd_guide:panel 4; shot es-claro-panel; go tema:oscuro 5
for L in en ar zh de hu; do
  go idioma:$L 6
  go obd-ajustes:obd_guide 4; shot $L-0-indice
  go obd-ajustes:obd_guide:comprar 4; shot $L-1-comprar
  go obd-ajustes:obd_guide:ia 4; shot $L-7-ia; sc; shot $L-7-ia-b; sc; sc; shot $L-7-ia-fin
done
go idioma:es 6; go inicio 3
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
