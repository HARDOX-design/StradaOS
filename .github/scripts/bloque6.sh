#!/bin/bash
# Bloque 6 (2.18): botones de conexión, clima en el panel y su detalle, ocultar velocidad/aceite/reproductor,
# botón del navegador (tamaño y lado) y Apariencia.
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
sleep 20   # que llegue el clima
shot 01-inicio
go diseno:conexion-aa 5; shot 02-solo-android-auto-con-clima
go diseno:conexion-cp 5; shot 03-solo-carplay-con-clima
go diseno:conexion-none 5; shot 04-sin-botones-clima-ancho
go clima 4; shot 05-detalle-clima
adb shell input swipe 640 480 640 160 500; sleep 2; shot 06-detalle-clima-abajo
adb shell input keyevent 4; sleep 2
go diseno:base 4
go diseno:sinvelocidad 5; shot 07-sin-velocidad
go diseno:sinauto 5; shot 08-sin-velocidad-sin-tarjeta
go diseno:base 4; go diseno:sinaceite 5; shot 09-sin-aceite
go diseno:base 4; go diseno:sinreproductor 5; shot 10-sin-reproductor
go diseno:sinauto 5; shot 11-sin-reproductor-sin-tarjeta
go diseno:base 4; go diseno:nav-1 5; shot 12-navegador-grande
go diseno:nav-2 4; go diseno:navizq 5; shot 13-navegador-muy-grande-izq
go diseno:nav-3 5; shot 14-navegador-oculto
go diseno:base 4
go tema:claro 6; go diseno:conexion-aa 5; shot 15-claro-solo-aa
go diseno:base 4; go tema:oscuro 6
go ajustes:2 6; adb shell input swipe 640 480 640 200 500; sleep 2; shot 16-apariencia
adb shell input swipe 640 480 640 200 500; sleep 2; shot 17-apariencia-abajo
go inicio 4; go idioma:en 6; go diseno:conexion-none 5; shot 18-ingles-clima; go clima 4; shot 19-ingles-detalle
adb shell input keyevent 4; sleep 2; go idioma:es 6; go diseno:base 4
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -40 > $OUT/logcat.txt
if grep -q "AndroidRuntime: \tat com.stradaos\|com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
