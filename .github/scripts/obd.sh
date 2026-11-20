#!/bin/bash
# 2.26: escáner OBD-II (panel del vehículo sobre los temas y ajustes) con el auto simulado.
# Uso: obd.sh API APK CARPETA
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
tap() { adb shell input tap $1 $2; sleep ${3:-3}; }
back() { adb shell input keyevent 4; sleep 2; }
( while true; do adb emu geo fix -75.5680 6.2442 1495 8 26 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4; sleep 8
shot v00-inicio-sin-obd
go ajustes:1 5; sc; sc; sc; shot v01-tu-vehiculo-apagado
go obd-demo 3; go ajustes:1 190     # batería baja (alerta grave) a los ~3 min, con Ajustes abierto
go inicio 6; shot v02-panel-solo-al-volver   # debe abrirse solo
back; sleep 3; shot v02-inicio-pestana
go obd-panel 4; shot v03-panel-20s
back; sleep 50
go obd-panel 4; shot v04-panel-75s-codigos
tap 377 150 5; shot v05-grafico; back; sleep 2
go ajustes:1 5; sc; sc; sc; shot v06-tu-vehiculo-encendido
for p in obd obd_conn obd_alerts obd_codes obd_live obd_ai obd_adv; do go obd-ajustes:$p 5; shot v1-$p; sc; shot v1-$p-b; done
go tema-premium:aurora 7; go inicio 3; go obd-panel 4; shot v20-aurora-panel; back
go tema-premium:cobalto 7; go inicio 3; go obd-panel 4; shot v21-cobalto-panel; back
go tema-premium:faro 8; go inicio 3; shot v22-faro-pestana; go obd-panel 4; shot v23-faro-panel; back
go tema:claro 6; go inicio 3; go obd-panel 4; shot v24-faro-claro-panel; back
go tema-gratis:1 6; go inicio 3; go obd-panel 4; shot v25-clasico-claro-panel; back
go pref:obd_panel_side=left 4; go inicio 3; go obd-panel 4; shot v26-panel-izquierda; back
go pref:obd_panel_side=auto 3; go tema:oscuro 5
go idioma:en 6; go inicio 3; go obd-panel 4; shot v40-en-panel; back; go obd-ajustes:obd 5; shot v41-en-ajustes
go idioma:ar 6; go inicio 3; go obd-panel 4; shot v42-ar-panel; back; go obd-ajustes:obd_alerts 5; shot v43-ar-alertas
go idioma:hu 6; go inicio 3; go obd-panel 4; shot v44-hu-panel; back
go idioma:es 6
go obd-off 4; go inicio 3; shot v30-apagado-de-nuevo
kill $GEO 2>/dev/null
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
adb logcat -d | grep -iE "StradaOBD|Obd" | tail -40 > $OUT/logcat-obd.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
