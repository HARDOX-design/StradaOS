#!/bin/bash
# 2.26: recorre el panel del vehículo y los ajustes del escáner en los 10 idiomas y junta los textos que quedaron
# sin traducir (StradaOS Prueba los anota en el logcat con la etiqueta StradaI18n).
# Uso: obd-i18n.sh API APK CARPETA
API=$1; APK=$2; OUT=$3; mkdir -p $OUT
PKG=com.stradaos.launcher.debug
ACT=$PKG/com.stradaos.launcher.Main
adb wait-for-device
for i in $(seq 1 100); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 15
adb shell input keyevent 82 || true
adb shell wm size 1024x600; adb shell wm density 160; sleep 6
adb install -r -g "$APK" | tail -1
adb logcat -G 4M >/dev/null 2>&1
shot() {
  adb shell screencap -p /data/local/tmp/s.png >/dev/null 2>&1 && adb pull /data/local/tmp/s.png "$OUT/$1.png" >/dev/null 2>&1 && echo "captura $1"
}
go() { adb shell am start -n $ACT --es prueba "$1" >/dev/null; sleep "${2:-5}"; }
sc() { adb shell input swipe 600 520 600 120 400; sleep 2; }
back() { adb shell input keyevent 4; sleep 2; }
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:claro 6; go inicio 4
go obd-demo 20; sleep 70        # que aparezcan alertas y el código de ejemplo
adb logcat -c
adb logcat -v raw -s StradaI18n:W > $OUT/faltan-raw.txt &
LC=$!
for L in en pt it de ms ar tr fr zh hu; do
  go idioma:$L 6; go inicio 3
  go obd-panel 4; shot $L-panel
  adb shell input tap 377 150; sleep 4; shot $L-grafico; back
  go ajustes:1 5; sc; sc; sc; shot $L-tu-vehiculo
  for p in obd obd_conn obd_alerts obd_codes obd_live obd_ai obd_adv; do go obd-ajustes:$p 4; sc; sc; done
  shot $L-avanzado
done
go idioma:es 6
sleep 3; kill $LC 2>/dev/null
sort -u $OUT/faltan-raw.txt | grep -v "^-----" > $OUT/faltan.txt
wc -l $OUT/faltan.txt
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
