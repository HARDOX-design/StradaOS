#!/bin/bash
# Bloque 16 (2.25): tema premium «Faro» (en StradaOS Prueba se aplica sin código).
# Uso: bloque16.sh API APK CARPETA
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
# Medellín, a unos 48 km/h (26 nudos): mapa, clima y velocímetro
( while true; do adb emu geo fix -75.5680 6.2442 >/dev/null 2>&1; adb emu geo fix -75.5680 6.2442 1495 8 26 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
go demo-aceite 4; go demo-mant 4
demo() { go inicio 3; go pista-demo:Night_Drive 3; }
go tema-premium:faro 8
sleep 20   # que lleguen el clima y las teselas
demo; shot f01-faro-oscuro-mixto
go pref:speedo=1 5; demo; shot f02-oscuro-analogico
go pref:speedo=0 5; demo; shot f03-oscuro-digital
go pref:speedo=2 5
go tema:claro 0; sleep 1.6; shot f04a-apagando-faros; sleep 6; demo; shot f04-faro-claro
go pref:speedo=1 5; demo; shot f05-claro-analogico
go pref:speedo=0 5; demo; shot f06-claro-digital
go pref:speedo=2 5
go tema:oscuro 0; sleep 1.8; shot f07a-encendiendo-faros; sleep 5; demo
for c in red blue black white; do go pref:faro_car=$c 5; demo; shot f10-color-$c; done
go tema:claro 6; for c in black red; do go pref:faro_car=$c 5; demo; shot f11-claro-color-$c; done
go pref:faro_car=silver 5
# intensidad del mapa: Auto (en 1024×600 = media), baja (la del S23), alta
go pref:faro_mapa=0 8; demo; shot f12-claro-mapa-baja
go pref:faro_mapa=2 8; demo; shot f13-claro-mapa-alta
go pref:faro_mapa= 8; demo; shot f13b-claro-mapa-auto
go tema:oscuro 6
go pref:faro_mapa=0 8; demo; shot f14-oscuro-mapa-baja
go pref:faro_mapa=2 8; demo; shot f15-oscuro-mapa-alta
go pref:faro_mapa= 8; demo; shot f15b-oscuro-mapa-auto
go mapa-zoom:15 3; shot f16-zoom-15
go mapa-zoom:17 3; shot f17-zoom-17
# navegación integrada: límite de la vía en el velocímetro (si OSRM lo trae)
go pref:nav_app=internal 5; go inicio 4; go 'nav-ir:6.2702,-75.5660,Parque\ Explora' 25; go pista-demo:Night_Drive 3; sleep 4; shot f18-navegando
sleep 8; shot f18b-navegando-2
go nav-salir 4; go pref:nav_app= 5
# sin mantenimientos, sin aceite, sin clima
go pref:maint= 4; go pref:oil_on=false 5; demo; shot f19-sin-mantenimientos
go demo-aceite 4; go demo-mant 4
go pref:weather_on=false 5; demo; shot f20-sin-clima
go pref:weather_on=true 5
go pref:show_player=false 5; shot f21-sin-reproductor
go pref:show_player=true 5
go pref:dock_count=10 5; demo; shot f22-diez-botones
go pref:dock_style=apps 5; demo; shot f23-estilo-apps
go pref:dock_style= 4; go pref:dock_count=7 4
go pref:dock_mode=none 5; demo; shot f24-sin-barra
go pref:dock_mode= 5
go pref:car_speed=false 5; demo; shot f25-sin-velocidad
go pref:car_speed=true 5
# galería y ajustes
go ajustes:11 5; adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2; shot f30-galeria
adb shell input swipe 600 520 600 80 400; sleep 2; shot f30b-galeria-2
go ajustes:3 5; shot f31-pantalla-inicio-faro
go clima 5; shot f32-detalle-clima; adb shell input keyevent 4; sleep 3
go idioma:en 6; demo; shot f40-ingles
go idioma:ar 6; demo; shot f41-arabe
go idioma:zh 6; demo; shot f42-chino
go idioma:es 6
if [ "$API" -ge 18 ]; then
  size 1280x480; demo; shot f50-1280x480
  size 1920x720; demo; shot f51-1920x720
  size 800x480; demo; shot f52-800x480
  size 1280x720; demo; shot f53-1280x720
  size 1080x1920; demo; shot f60-vertical
  go tema:claro 7; demo; shot f61-vertical-claro
  go tema:oscuro 6
  size 768x880; demo; shot f62-vertical-bajo
  size 1024x600
else
  go sim:5 6; demo; shot f50-1280x480
  go sim:7 6; demo; shot f60-vertical
  go sim:0 6
fi
# diagnóstico del reproductor: permiso de notificaciones y sesiones multimedia
go inicio 3; go diag-reproductor 5; shot f80-diagnostico-sin-permiso; adb shell input keyevent 4; sleep 2
if [ "$API" -ge 27 ]; then
  adb shell cmd notification allow_listener $PKG/com.stradaos.launcher.MediaListener; sleep 4
  go inicio 3; go diag-reproductor 5; shot f81-diagnostico-con-permiso; adb shell input keyevent 4; sleep 2
fi
# un tema gratis quita el premium y todo vuelve a lo de siempre
go tema-gratis:1 6; demo; shot f90-gratis-quita-premium
go tema-premium:aurora 6; demo; shot f91-aurora-sigue-igual
go tema-premium:cobalto 6; demo; shot f92-cobalto-sigue-igual
go inicio 5
kill $GEO 2>/dev/null
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
