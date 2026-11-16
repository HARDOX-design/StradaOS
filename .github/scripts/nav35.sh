#!/bin/bash
# Revisión del PR #35: navegación integrada (capturas con ruta real en Valledupar).
# Uso: nav35.sh API APK CARPETA
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
back() { adb shell input keyevent 4; sleep 2; }
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
fix() { adb emu geo fix $2 $1 >/dev/null 2>&1; sleep ${3:-2}; }   # fix LAT LON
go idioma:es 6; go tema:oscuro 5
fix 10.4769 -73.2433 4; fix 10.4768 -73.2434 4
go inicio 6
shot n01-inicio-sin-cambios
go pref:nav_app=internal 5; go inicio 4
shot n02-inicio-boton-ir-a
go nav-buscar 6; shot n03-buscar
adb shell input text "Aeropuerto"; sleep 1; adb shell input keyevent 66; sleep 10; shot n04-resultados
back; back
go pref:nav_alternatives=true 3
go "nav-ir:10.4350,-73.2495,Aeropuerto Alfonso López" 25; shot n05-rutas-alternas
back; sleep 2
go pref:nav_alternatives=false 3
go "nav-ir:10.4350,-73.2495,Aeropuerto Alfonso López" 25; shot n06-ruta-iniciada
fix 10.4760 -73.2436 3; fix 10.4750 -73.2440 3; fix 10.4740 -73.2443 5; shot n07-andando
go pref:map_heading=true 5; go inicio 4; fix 10.4730 -73.2446 3; fix 10.4720 -73.2449 5; shot n08-segun-direccion
go pref:theme=light 6; go inicio 4; fix 10.4710 -73.2452 5; shot n09-ruta-tema-claro
go pref:theme=dark 5
adb shell input tap 70 600 >/dev/null 2>&1; sleep 1
go ajustes:7 5; shot n10-ajustes-mapa-1
for i in 1 2 3; do adb shell input swipe 600 500 600 150 400; sleep 1; done; sleep 2; shot n11-ajustes-mapa-2
for i in 1 2 3; do adb shell input swipe 600 500 600 150 400; sleep 1; done; sleep 2; shot n12-ajustes-mapa-3
for i in 1 2 3; do adb shell input swipe 600 500 600 150 400; sleep 1; done; sleep 2; shot n13-ajustes-mapa-4
go inicio 4
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
adb logcat -d | grep -iE "stradaos|Nav" | grep -iE "route|osrm|photon|ssl|tls|nav" | tail -80 > $OUT/nav-log.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
