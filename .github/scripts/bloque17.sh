#!/bin/bash
# Bloque 17 (2.30): clima sin internet (#46), volver al inicio tras la app Bluetooth (#47), ▶ con Android Auto (#50)
# y un recorrido por los ajustes para ver que nada se rompió. Uso: bloque17.sh API APK CARPETA
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
back() { adb shell input keyevent 4; sleep 3; }
( while true; do adb emu geo fix -74.0836 4.6533 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
# los cierres se anotan desde el principio: en Android 4.1 el registro es chico y se pierde si se lee solo al final
adb logcat -v brief AndroidRuntime:E '*:S' > $OUT/cierres-raw.txt 2>&1 &
CR=$!
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 30
go idioma:es 6; go inicio 20
shot c01-clima-con-internet
adb shell run-as $PKG ls -l files/ > $OUT/archivos.txt 2>&1
# ── #46: sin internet, el clima sale del pronóstico guardado ──
adb shell svc data disable; adb shell svc wifi disable
[ "$API" -ge 30 ] && adb shell cmd connectivity airplane-mode enable
sleep 8
adb shell am force-stop $PKG; sleep 3
adb shell am start -n $ACT >/dev/null; sleep 25
shot c02-clima-sin-internet
go clima 5; shot c03-detalle-sin-internet; back
[ "$API" -ge 30 ] && adb shell cmd connectivity airplane-mode disable
adb shell svc data enable; adb shell svc wifi enable
sleep 10
# ── #47: abre la «app de música Bluetooth» (aquí Ajustes de Android) y debe volver solo al inicio ──
# en una pantalla real StradaOS es el inicio (Android 10+ solo deja traer al frente desde atrás a la app de inicio)
[ "$API" -ge 24 ] && adb shell cmd package set-home-activity $PKG/com.stradaos.launcher.Main
go inicio 3
adb shell am start -n $ACT --es prueba "bt-probar:com.android.settings/.Settings" >/dev/null
sleep 2; shot b01-app-bt-abierta
sleep 6; shot b02-volvio-al-inicio
sleep 6; shot b03-despues
adb logcat -d -s StradaBt:I | tail -20 > $OUT/bt-log.txt
adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|ResumedActivity" | head -3 > $OUT/al-frente.txt
# ── #50: con Android Auto simulado, ▶ ya no abre la app de música ──
go inicio 4; go aa-simular:1 4; go diag-reproductor 6; shot m01-diag-aa; back
go aa-simular:0 4; go diag-reproductor 4; shot m02-diag-sin-aa; back
# ── recorrido por todo para ver que nada se rompió ──
for c in 0 1 2 3 4 5 6 7 8 9 10 11; do n=$(printf %02d $c); go ajustes:$c 4; shot r$n; done
go inicio 3; go tema-premium:faro 6; go inicio 4; shot r20-faro
go tema-premium:aurora 6; go inicio 4; shot r21-aurora
go tema-gratis:1 6; go inicio 4; shot r22-clasico
sleep 3; kill $GEO $CR 2>/dev/null
grep -E "FATAL|Exception|Error|at com\.stradaos" $OUT/cierres-raw.txt | head -80 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls $OUT
