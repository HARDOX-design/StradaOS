#!/bin/bash
# Bloque 25 (2.39): tema premium «Premium Cards» (en StradaOS Prueba se aplica sin código).
# Uso: bloque25.sh API APK CARPETA
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
# Medellín, a unos 48 km/h (26 nudos): mapa, clima y velocidad
( while true; do adb emu geo fix -75.5680 6.2442 >/dev/null 2>&1; adb emu geo fix -75.5680 6.2442 1495 8 26 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
go demo-aceite 4; go demo-mant 4
demo() { go inicio 3; go pista-demo:Night_Drive 3; }
go pref:accent=-13779348 4
go tema-premium:cards 8
sleep 20   # que lleguen el clima y las teselas
demo; shot k01-oscuro
go tema:claro 7; demo; shot k02-claro
go tema:oscuro 6
# accesos directos: dos puestos y uno vacío
go pref:cards_app_0=com.android.settings/com.android.settings.Settings 4
go pref:cards_app_1=com.android.deskclock/com.android.deskclock.DeskClock 4
demo; shot k03-accesos
# esconder el reproductor deslizándolo hacia la izquierda y sacarlo con la pestaña
adb shell input swipe 260 330 40 330 250; sleep 1; shot k04-escondiendo
sleep 2; shot k05-escondido
go tema:claro 7; demo; shot k06-claro-escondido
go tema:oscuro 6
adb shell input tap 34 287; sleep 2; shot k07-vuelve
go pref:cards_player_open=false 5; demo; shot k08-escondido-pref
go pref:cards_player_open=true 5
# barra de apps a los costados (las tarjetas llegan hasta abajo)
go pref:cards_dock=left 5; demo; shot k10-barra-izquierda
go tema:claro 7; demo; shot k11-barra-izquierda-claro
go tema:oscuro 6
go pref:cards_dock=right 5; demo; shot k12-barra-derecha
go pref:dock_count=10 5; demo; shot k13-derecha-diez
go pref:dock_count=7 4; go pref:cards_dock= 5
go pref:dock_count=10 5; demo; shot k14-abajo-diez
go pref:dock_style=apps 5; demo; shot k15-estilo-apps
go pref:dock_style= 4; go pref:dock_count=7 4
go pref:dock_labels=false 5; demo; shot k16-sin-nombres
go pref:dock_labels= 4
go pref:dock_mode=none 5; demo; shot k17-sin-barra
go pref:dock_mode= 5
# acentos
go pref:accent=-13665034 5; demo; shot k20-acento-azul
go pref:accent=-673792 5; demo; shot k21-acento-amarillo
go pref:accent= 5; demo; shot k22-acento-rojo
go pref:accent=-13779348 5
# casos
go pref:maint= 4; demo; shot k30-solo-aceite
go pref:oil_on=false 5; demo; shot k31-sin-mantenimientos
go demo-aceite 4; go demo-mant 4
go pref:weather_on=false 5; demo; shot k32-sin-clima
go pref:weather_on=true 5
go pref:show_player=false 5; shot k33-sin-reproductor
go pref:show_player=true 5
go pref:car_speed=false 5; demo; shot k34-sin-velocidad
go pref:car_speed=true 5
go pref:conn_buttons=aa 5; demo; shot k35-solo-aa
go pref:conn_buttons= 5
go pref:clock=s:12 5; demo; shot k36-reloj-12h
go pref:clock= 5
go pref:font_size=3 5; demo; shot k37-letra-enorme
go pref:font_size= 5
go pref:nav_app=internal 5; go inicio 4; go 'nav-ir:6.2702,-75.5660,Parque\ Explora' 25; go pista-demo:Night_Drive 3; sleep 4; shot k40-navegando
adb shell input swipe 260 330 40 330 250; sleep 3; shot k41-navegando-escondido
go pref:cards_player_open=true 4
go nav-salir 4; go pref:nav_app= 5
go pref:center_card=both 5; go pagina:1 4; shot k42-pagina-video
go pagina:0 4; shot k43-vuelve-mapa
go pref:center_card= 5
go ajustes:11 5; adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2; adb shell input swipe 600 520 600 80 400; sleep 2; shot k50-galeria
adb shell input swipe 600 520 600 80 400; sleep 2; shot k50b-galeria-2
go ajustes:3 5; shot k51-pantalla-inicio
go inicio 4
go idioma:en 6; demo; shot k60-ingles
go idioma:ar 6; demo; shot k61-arabe
go idioma:de 6; demo; shot k62-aleman
go idioma:es 6
if [ "$API" -ge 18 ]; then
  size 1280x480; demo; shot k70-1280x480
  size 1920x720; demo; shot k71-1920x720
  go pref:cards_dock=left 5; demo; shot k71b-1920x720-izquierda; go pref:cards_dock= 5
  size 800x480; demo; shot k72-800x480
  size 1280x720; demo; shot k73-1280x720
  size 1080x1920; demo; shot k80-vertical
  go tema:claro 7; demo; shot k81-vertical-claro
  go tema:oscuro 6
  adb shell input swipe 300 500 30 500 250; sleep 3; shot k82-vertical-escondido
  go pref:cards_player_open=true 5
  go pref:cards_dock=left 5; demo; shot k83-vertical-izquierda; go pref:cards_dock= 5
  go pref:dock_count=10 5; demo; shot k84-vertical-diez
  go pref:dock_count=7 4
  size 768x1024; demo; shot k85-vertical-768x1024
  size 768x880; demo; shot k86-vertical-bajo
  size 1024x600
else
  go sim:5 6; demo; shot k70-1280x480
  go sim:7 6; demo; shot k80-vertical
  go sim:0 6
fi
go tema-premium:carbono 6; demo; shot k90-carbono-sigue-igual
go tema-premium:faro 6; demo; shot k91-faro-sigue-igual
go inicio 5
kill $GEO 2>/dev/null
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
