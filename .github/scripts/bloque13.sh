#!/bin/bash
# Bloque 13 (2.23): navegación en el mapa de StradaOS (ruta real en Valledupar), en los 4 diseños.
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
ruta() {  # sin espacios: en Android 7+ «adb shell» parte los argumentos con espacios
  go "nav-ir:10.4350,-73.2495,Aeropuerto" ${1:-25}
}
# El emulador anuncia IPv6 pero la máquina de GitHub no tiene salida IPv6 (ENETUNREACH a los servidores de rutas).
# En una pantalla real sin IPv6, Android solo usa IPv4: aquí se apaga IPv6 para probar igual que en el carro.
if [ "$API" -ge 21 ]; then
  adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
  adb shell sysctl -w net.ipv6.conf.all.disable_ipv6=1 >/dev/null 2>&1
  adb shell sysctl -w net.ipv6.conf.default.disable_ipv6=1 >/dev/null 2>&1
  adb shell "cat /proc/sys/net/ipv6/conf/all/disable_ipv6" > $OUT/ipv6-apagado.txt 2>&1
  sleep 3
fi
go idioma:es 6; go tema:oscuro 5
fix 10.4769 -73.2433 4; fix 10.4768 -73.2434 4
go inicio 6
shot n01-inicio-sin-cambios
go ajustes:7 5; shot n02-ajustes-sin-navegacion
go pref:nav_app=internal 5; go inicio 4
shot n03-inicio-boton-ir-a
go ajustes:7 5; shot n04-ajustes-con-navegacion
for i in 1 2 3; do adb shell input swipe 600 500 600 150 400; sleep 1; done; sleep 2; shot n05-ajustes-navegacion-2
for i in 1 2 3 4; do adb shell input swipe 600 500 600 150 400; sleep 1; done; sleep 2; shot n06-ajustes-avanzado
go inicio 4
go nav-buscar 6; shot n07-buscar
adb shell input text "Aeropuerto"; sleep 1; adb shell input keyevent 66; sleep 10; shot n08-resultados
back; back
go pref:nav_alternatives=true 3
ruta; shot n09-rutas-alternas
back; sleep 2
go pref:nav_alternatives=false 3
ruta; shot n10-ruta-iniciada
fix 10.4760 -73.2436 3; fix 10.4750 -73.2440 3; fix 10.4740 -73.2443 5; shot n11-andando
go pref:map_heading=true 5; go inicio 4; fix 10.4730 -73.2446 3; fix 10.4720 -73.2449 5; shot n12-segun-direccion
go pref:map_heading=false 3
go pref:theme=light 6; go inicio 4; fix 10.4710 -73.2452 5; shot n13-tema-claro
go pref:theme=dark 5
go idioma:en 6; go inicio 4; shot n14-ingles
go idioma:zh 6; go inicio 4; shot n15-chino
go idioma:ar 6; go inicio 4; shot n16-arabe
go idioma:es 6
go pref:dock_mode=side 5; go inicio 4; shot n17-barra-lateral
go pref:dock_mode=compact 5; go inicio 4; shot n18-compacta
go pref:dock_mode=normal 5
if [ "$API" -ge 18 ]; then size 1080x1920; else go sim:7 6; fi
go inicio 6; fix 10.4705 -73.2454 4; shot n19-vertical
if [ "$API" -ge 18 ]; then size 768x880; else go sim:8 6; fi
go inicio 6; fix 10.4700 -73.2456 4; shot n20-vertical-bajo
if [ "$API" -ge 18 ]; then size 1024x600; else go sim:0 6; fi
go inicio 5
go pref:nav_app= 4; go inicio 4; shot n21-navegacion-apagada-de-nuevo
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
adb logcat -d -s StradaNav:W | tail -40 > $OUT/nav-log.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
