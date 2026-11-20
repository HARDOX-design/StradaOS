#!/bin/bash
# 2.27: desbloqueo de los temas de pago (Fase 3) en StradaOS Prueba con «lic-prueba:1».
# En API 23 se fija el ANDROID_ID (a1b2c3d4e5f60718 → ID P0ZQ-SX0M) y se prueba de punta a punta con el código
# STRD-X3KM-H3GD que generó la Action «Licencia» (y luego con el archivo por USB).
# Uso: lic.sh API APK CARPETA
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
if [ "$API" = 23 ]; then adb root >/dev/null 2>&1; sleep 4; adb wait-for-device; adb shell settings put secure android_id a1b2c3d4e5f60718; echo "android_id: $(adb shell settings get secure android_id)"; fi
inst() { if [ "$API" -ge 23 ]; then adb install -r -g "$APK" | tail -1; else adb install -r "$APK" | tail -1; fi; }
inst
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
go idioma:es 6; go lic-prueba:1 3; go inicio 4
go ajustes:11 6; sc; sc; sc; shot 01-galeria-bloqueado; sc; sc; shot 02-faro-bloqueado
go desbloquear:faro 4; shot 03-dialogo
back
go lic-codigo:STRD-AAAA-AAAA 12; shot 04-codigo-malo
back
go lic-usb 10; shot 05-usb-sin-archivo
back
if [ "$API" = 23 ]; then
  go lic-codigo:STRD-X3KM-H3GD 15; shot 06-codigo-bueno
  back
  go ajustes:11 6; sc; sc; sc; sc; sc; shot 07-faro-desbloqueado
  go tema-premium:faro 6; go inicio 4; shot 08-faro-aplicado
  # reinstalar (se borran las licencias) y probar el archivo por USB
  adb uninstall $PKG >/dev/null; inst
  adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 20
  go idioma:es 6; go lic-prueba:1 3
  curl -sS -f -o /tmp/lic.txt https://raw.githubusercontent.com/lordnordic-debug/StradaOS/licencias/l/d6712d203ce2cee861b532b10263bfc6.txt && \
    adb push /tmp/lic.txt /sdcard/Download/stradaos-licencia.txt >/dev/null
  go lic-usb 12; shot 09-usb-bueno
  back
fi
go idioma:en 6; go desbloquear:faro 4; shot 10-dialogo-en; back
go idioma:ar 6; go desbloquear:faro 4; shot 11-dialogo-ar; back
go idioma:es 6; go inicio 3
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
adb logcat -d | grep -i "licencia" | tail -20 > $OUT/licencia.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
