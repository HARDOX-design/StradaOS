#!/bin/bash
# Bloque 5 (2.17): capturas del inicio con las opciones nuevas (íconos Wi-Fi/Bluetooth, barra compacta, sin nombres,
# lateral a cada lado, velocidad arriba sin GPS), Apariencia, diagnóstico del mapa en inglés, árabe y tema del sistema.
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
shot 01-inicio
go diseno:compacta 5; shot 02-compacta
go diseno:sinnombres 5; shot 03-compacta-sin-nombres
go diseno:normal 5; shot 04-normal-sin-nombres
go diseno:base 4; go diseno:lateral 5; shot 05-lateral
go diseno:railizq 5; shot 06-lateral-fija-izquierda
go diseno:izq 5; go diseno:railder 5; shot 07-lateral-fija-derecha-reproductor-izq
go diseno:base 4; go diseno:sinauto 5; shot 08-sin-tarjeta-velocidad-arriba
go diseno:base 4
go ajustes:2 6; shot 09-apariencia
adb shell input swipe 640 480 640 180 500; sleep 2; shot 10-apariencia-abajo
go inicio 4
go idioma:en 6; go diag-mapa 5; shot 11-diagnostico-mapa-en
adb shell input keyevent 4; sleep 2
go idioma:ar 6; go inicio 4; shot 12-inicio-ar
go ajustes:2 6; shot 13-apariencia-ar
go inicio 4; go idioma:es 6
if [ "$API" -ge 29 ]; then
  go tema:sistema 5
  adb shell cmd uimode night yes; sleep 6; shot 14-sistema-noche
  adb shell cmd uimode night no; sleep 6; shot 15-sistema-dia
fi
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -40 > $OUT/logcat.txt
if grep -q "FATAL EXCEPTION" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
