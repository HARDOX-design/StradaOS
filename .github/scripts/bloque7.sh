#!/bin/bash
# Bloque 7 (2.19): cantidad y tamaño de botones, íconos de apps, pack de íconos, barra oculta y tamaño de letra.
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
[ -s pack.apk ] && adb install -r pack.apk | tail -1
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
shot 01-inicio
go diseno:botones-10 5; shot 02-diez-botones
go diseno:botones-4 5; shot 03-cuatro-botones
go diseno:botones-7 4; go diseno:iconos-2 5; shot 04-iconos-grandes
go diseno:iconos-0 5; shot 05-iconos-pequenos
go diseno:base 4; go diseno:compacta 4; go diseno:botones-10 4; go diseno:iconos-2 5; shot 06-compacta-10-grandes
go diseno:base 4; go diseno:lateral 4; go diseno:botones-10 5; shot 07-lateral-10
go diseno:sinnombres 5; shot 08-lateral-10-sin-nombres
go diseno:base 4; go diseno:sinbarra 5; shot 09-sin-barra
go diseno:base 4; go diseno:estiloapps 5; shot 10-iconos-de-apps
go pack:com.donnnno.arcticons 8; shot 11-pack-de-iconos
go cajon 5; shot 12-cajon-con-pack
adb shell input keyevent 4; sleep 2; go inicio 3
go diseno:base 4; prefs_clear=1
go diseno:letra-3 5; shot 13-letra-xl
go ajustes:2 6; shot 14-letra-xl-ajustes
go inicio 3; go diseno:letra-0 5; shot 15-letra-s
go diseno:base 4
go ajustes:4 6; shot 16-accesos
adb shell input swipe 640 300 640 300 50; sleep 1
go inicio 3; go idioma:en 6; go diseno:botones-9 5; shot 17-ingles-9; go idioma:es 6; go diseno:base 4
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -40 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
