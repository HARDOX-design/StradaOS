#!/bin/bash
# Polaco (2.28): recorre todas las pantallas en polaco, toma capturas para ver que los textos quepan
# y junta los textos que quedaron sin traducir (StradaOS Prueba los anota en el logcat con la etiqueta StradaI18n).
# Uso: pl.sh API APK CARPETA
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
adb logcat -G 4M >/dev/null 2>&1
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
# Varsovia
( while true; do adb emu geo fix 21.0122 52.2297 >/dev/null 2>&1; adb emu geo fix 21.0122 52.2297 120 8 26 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
adb logcat -c
adb logcat -v raw -s StradaI18n:W > $OUT/faltan-raw.txt &
LC=$!
go idioma:pl 6; go inicio 4; sleep 12
go pista-demo:Night_Drive 3; shot p01-inicio
go tema:claro 6; go pista-demo:Night_Drive 3; shot p02-inicio-claro; go tema:oscuro 6
for c in 0 1 2 3 4 5 6 7 8 9 10 11; do n=$(printf %02d $c); go ajustes:$c 5; shot p1$n-a; sc; shot p1$n-b; sc; sc; shot p1$n-c; done
go inicio 3
go clima 5; shot p21-clima; back
go obd-demo 20; sleep 60
go obd-panel 4; shot p30-panel-obd
adb shell input tap 377 150; sleep 4; shot p31-grafico; back
for p in obd obd_conn obd_alerts obd_codes obd_live obd_ai obd_adv; do go obd-ajustes:$p 4; shot p32-$p; sc; sc; done
go obd-ajustes:obd_guide 4; shot p40-guia
n=1
for S in comprar conector panel alertas codigos datos ia limites; do
  go obd-ajustes:obd_guide:$S 4; shot p4$n-$S; sc; sc; sc; shot p4$n-$S-fin; n=$((n+1))
done
go obd-off 4; go inicio 3
go tema-premium:aurora 6; go pista-demo:Night_Drive 3; shot p50-aurora
go tema-premium:cobalto 6; go pista-demo:Night_Drive 3; shot p51-cobalto
go tema-gratis:1 6; go inicio 3
go lic-prueba:1 3; go desbloquear:faro 5; shot p52-desbloquear; back
# 2.28: paquete de 6 USD por dos temas (aparece solo con dos temas de pago; aquí se fuerza con «paquete:1»)
go paquete:1 2; go desbloquear:faro 5; shot p53-paquete; back
go ajustes:0 5; sc; sc; sc; sc; shot p54-paquete-tarjeta
for L in es en hu ar; do go idioma:$L 6; go desbloquear:faro 5; shot p55-paquete-$L; back; done
go idioma:hu 6; go inicio 4; shot p56-hu-inicio; go idioma:pl 6
go paquete:0 2; go lic-prueba:0 3
go nav-demo 12; shot p60-nav; back
go inicio 3
sleep 3; kill $LC $GEO 2>/dev/null
sort -u $OUT/faltan-raw.txt | grep -v "^-----" > $OUT/faltan.txt
wc -l $OUT/faltan.txt
go idioma:es 6
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT | wc -l
