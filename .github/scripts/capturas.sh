#!/bin/bash
# Capturas para imágenes de redes y de la página: StradaOS Prueba a doble resolución (2048x1200 = 1024x600 x2),
# en español e inglés, tema claro y oscuro, con una ciudad de ejemplo (Medellín) y canción de ejemplo.
PKG=com.stradaos.launcher.debug
ACT=$PKG/com.stradaos.launcher.Main
OUT=capturas; mkdir -p $OUT
adb wait-for-device
for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 10
adb shell input keyevent 82 || true
adb shell wm size 2048x1200; adb shell wm density 320; sleep 5
adb install -r -g prueba.apk | tail -1
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
# GPS fijo en Medellín (ciudad de ejemplo), repetido para que el mapa se vea «en vivo»
( while true; do adb emu geo fix -75.5680 6.2442 >/dev/null 2>&1; adb emu geo fix -75.5680 6.2442 1495 8 26 >/dev/null 2>&1; sleep 2; done ) &
GEO=$!
shot() { adb shell screencap -p /data/local/tmp/s.png && adb pull /data/local/tmp/s.png "$OUT/$1.png" >/dev/null; echo "captura $1"; }
go() { adb shell am start -n $ACT --es prueba "$1" >/dev/null; sleep "${2:-5}"; }
prep() { go pista-demo 3; }   # la canción de ejemplo se vuelve a poner tras cada cambio de diseño
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 15
go demo-aceite 6
if [ "$MODO" = promo-224 ] || [ "$MODO" = promo-224-v ]; then
  # Imágenes promocionales de la 2.24 (nov. 2026), en español e inglés: los temas Aurora y Cobalto (gratis),
  # la navegación en el mapa (Medellín, ciudad de ejemplo), el velocímetro y los mantenimientos.
  # En Cobalto también va una toma con el recuadro de la foto vacío (foto-vacia:1): la foto se pone al armar la imagen.
  geo() { kill $GEO 2>/dev/null; ( while true; do adb emu geo fix $2 $1 1495 8 ${3:-26} >/dev/null 2>&1; sleep 2; done ) & GEO=$!; sleep 3; }
  geo 6.2442 -75.5680
  # el emulador anuncia IPv6 pero la máquina de GitHub no tiene salida IPv6: sin esto no responden los servidores de rutas
  adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
  adb shell sysctl -w net.ipv6.conf.all.disable_ipv6=1 >/dev/null 2>&1
  adb shell sysctl -w net.ipv6.conf.default.disable_ipv6=1 >/dev/null 2>&1
  setprefs() {
    adb shell am force-stop $PKG; sleep 2
    adb shell "run-as $PKG cat shared_prefs/launcher.xml" > p.xml
    python3 - <<'PYX'
import re
s=open('p.xml',encoding='utf-8').read()
for k,v in [('user_name','Andrés'),('car_name','Kia Picanto'),('car_tag','GT Line')]:
    s=re.sub(r'\s*<string name="%s">[^<]*</string>'%k,'',s)
    s=s.replace('</map>','    <string name="%s">%s</string>\n</map>'%(k,v))
open('p.xml','w',encoding='utf-8').write(s)
PYX
    adb push p.xml /data/local/tmp/p.xml >/dev/null; adb shell chmod 644 /data/local/tmp/p.xml
    adb shell "run-as $PKG cp /data/local/tmp/p.xml shared_prefs/launcher.xml"
    adb shell am start -n $ACT >/dev/null; sleep 8
  }
  if [ -s colombia.map ]; then
    adb shell mkdir -p /sdcard/Download
    adb push colombia.map /sdcard/Download/colombia.map | tail -1
    go mapa-archivo:/sdcard/Download/colombia.map 8
  fi
  go idioma:es 8; go unidad:kmh 4; go tema:oscuro 6; go diseno:base 5; go diseno:velo-2 5
  go pref:nav_app=internal 4; go demo-aceite 4; go demo-mant 4
  setprefs
  song() { go pista-demo:Night_Drive 3; }
  hueco() { adb logcat -d -s StradaPromo:W | grep hueco | tail -1 | sed "s/.*hueco /$1 /" >> $OUT/huecos.txt; }
  if [ "$MODO" = promo-224-v ]; then
    adb shell wm size 1080x1920; adb shell wm density 160; sleep 10
    SW="540 1700 540 400"
  else
    SW="1200 1040 1200 200"
  fi
  for L in es en; do
    go idioma:$L 8; go inicio 4
    # ── Aurora ──
    go tema-premium:aurora 6; go pref:speedo=2 4; go solo-mapa:0 5
    go tema:oscuro 7; song; go vel:48 3; sleep 20; shot $L-aurora-oscuro
    go tema:claro 7; song; go vel:48 3; sleep 15; shot $L-aurora-claro
    go tema:oscuro 7; go solo-mapa:1 6; song; go vel:62 3; sleep 12; shot $L-aurora-solomapa
    go solo-mapa:0 5
    # ── Cobalto ──
    go tema-premium:cobalto 6; go pref:speedo=1 4
    go tema:oscuro 7; song; go vel:48 3; sleep 15; shot $L-cobalto-oscuro
    adb logcat -c; go foto-vacia:1 6; song; go vel:48 3; sleep 6; shot $L-cobalto-oscuro-vacio; hueco $L-cobalto-oscuro-vacio
    go tema:claro 7; song; go vel:48 3; sleep 8; shot $L-cobalto-claro-vacio; hueco $L-cobalto-claro-vacio
    go foto-vacia:0 6; song; go vel:48 3; sleep 5; shot $L-cobalto-claro
    go tema:oscuro 7
    # ── Personalización (con Aurora y Cobalto gratis) ──
    go ajustes:11 6; shot $L-personalizacion
    adb shell input swipe $SW 400; sleep 2; adb shell input swipe $SW 400; sleep 3; shot $L-galeria-premium
    adb shell input swipe $SW 400; sleep 3; shot $L-galeria-premium-2
    adb shell input keyevent 4; sleep 2
    # ── Navegación: ruta real del centro de Medellín al Parque Explora ──
    go tema-premium: 6; go tema:oscuro 6; go diseno:base 5; go diseno:velo-2 5; go inicio 4; song
    geo 6.2442 -75.5680
    go pref:nav_alternatives=false 3
    go 'nav-ir:6.2702,-75.5660,Parque\ Explora' 25; go vel:42 3; sleep 4; shot $L-nav-clasico
    go tema:claro 7; song; go vel:42 3; sleep 8; shot $L-nav-clasico-claro
    go tema:oscuro 7
    go tema-premium:aurora 6; go solo-mapa:1 6; song; go vel:42 3; sleep 10; shot $L-nav-aurora-solomapa
    go solo-mapa:0 5; song; go vel:42 3; sleep 8; shot $L-nav-aurora
    go tema-premium:cobalto 6; song; go vel:42 3; sleep 10; shot $L-nav-cobalto
    go tema-premium: 6
    adb shell input keyevent 4; sleep 2
    # ── velocímetro y mantenimientos (diseño clásico) ──
    go nav-salir 3; go inicio 4; go tema:oscuro 6; song; go vel:48 3; sleep 10; shot $L-clasico-oscuro
    go tema:claro 7; song; go vel:48 3; sleep 10; shot $L-clasico-claro
    go tema:oscuro 7; go diseno:velomapa 6; song; go vel:62 3; sleep 10; shot $L-velocimetro-en-mapa
    go diseno:base 4; go diseno:velo-2 4
    go ajustes:2 6; shot $L-mantenimientos
    go menu-mant 4; shot $L-menu-mantenimiento; adb shell input keyevent 4; sleep 2; adb shell input keyevent 4; sleep 2
    go ajustes:7 6; shot $L-ajustes-mapa; adb shell input swipe $SW 400; sleep 3; shot $L-ajustes-navegacion; adb shell input keyevent 4; sleep 2
  done
  kill $GEO 2>/dev/null
  adb logcat -d | grep -i "StradaNav\|FATAL\|AndroidRuntime" | tail -80 > $OUT/registro.txt
  ls -l $OUT
  exit 0
fi
if [ "$MODO" = web ]; then
  # Imágenes de la página (2.26, nov. 2026) en español, inglés y portugués: los temas (Clásico, Aurora, Cobalto, Faro),
  # la navegación guiada con el mapa sin conexión (Medellín, ciudad de ejemplo) y el escáner OBD-II con el auto simulado.
  geo() { kill $GEO 2>/dev/null; ( while true; do adb emu geo fix $2 $1 1495 8 ${3:-26} >/dev/null 2>&1; sleep 2; done ) & GEO=$!; sleep 3; }
  geo 6.2442 -75.5680
  adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
  adb shell sysctl -w net.ipv6.conf.all.disable_ipv6=1 >/dev/null 2>&1
  adb shell sysctl -w net.ipv6.conf.default.disable_ipv6=1 >/dev/null 2>&1
  setprefs() {
    adb shell am force-stop $PKG; sleep 2
    adb shell "run-as $PKG cat shared_prefs/launcher.xml" > p.xml
    python3 - <<'PYW'
import re
s=open('p.xml',encoding='utf-8').read()
for k,v in [('user_name','Andrés'),('car_name','Kia Picanto'),('car_tag','GT Line')]:
    s=re.sub(r'\s*<string name="%s">[^<]*</string>'%k,'',s)
    s=s.replace('</map>','    <string name="%s">%s</string>\n</map>'%(k,v))
open('p.xml','w',encoding='utf-8').write(s)
PYW
    adb push p.xml /data/local/tmp/p.xml >/dev/null; adb shell chmod 644 /data/local/tmp/p.xml
    adb shell "run-as $PKG cp /data/local/tmp/p.xml shared_prefs/launcher.xml"
    adb shell am start -n $ACT >/dev/null; sleep 8
  }
  if [ -s colombia.map ]; then
    adb shell mkdir -p /sdcard/Download
    adb push colombia.map /sdcard/Download/colombia.map | tail -1
    go mapa-archivo:/sdcard/Download/colombia.map 8
  fi
  go idioma:es 8; go unidad:kmh 4; go tema:oscuro 6; go diseno:base 5; go diseno:velo-2 5
  go pref:nav_app=internal 4; go demo-aceite 4; go demo-mant 4
  setprefs
  song() { go pista-demo:Night_Drive 3; }
  SW="1200 1040 1200 200"
  for L in es en pt; do
    go idioma:$L 8; go inicio 4; go nav-salir 3; go obd-off 3
    # ── temas ──
    go tema-premium: 6; go diseno:base 4; go diseno:velo-2 4
    go tema:oscuro 7; song; go vel:48 3; sleep 20; shot $L-tema-clasico-oscuro
    go tema:claro 7; song; go vel:48 3; sleep 12; shot $L-tema-clasico-claro
    go tema-premium:aurora 6; go pref:speedo=2 4; go solo-mapa:0 5
    go tema:oscuro 7; song; go vel:48 3; sleep 15; shot $L-tema-aurora-oscuro
    go tema:claro 7; song; go vel:48 3; sleep 12; shot $L-tema-aurora-claro
    go tema-premium:cobalto 6; go pref:speedo=1 4
    go tema:oscuro 7; song; go vel:48 3; sleep 12; shot $L-tema-cobalto-oscuro
    go tema:claro 7; song; go vel:48 3; sleep 12; shot $L-tema-cobalto-claro
    go tema-premium:faro 8
    go tema:oscuro 7; song; go vel:48 3; sleep 15; shot $L-tema-faro-oscuro
    go tema:claro 7; song; go vel:48 3; sleep 12; shot $L-tema-faro-claro
    go tema:oscuro 7
    go ajustes:11 6; shot $L-personalizacion
    adb shell input swipe $SW 400; sleep 2; adb shell input swipe $SW 400; sleep 3; shot $L-galeria-temas
    adb shell input keyevent 4; sleep 2
    # ── navegación guiada: centro de Medellín → Parque Explora ──
    go tema-premium: 6; go tema:oscuro 6; go diseno:base 5; go diseno:velo-2 5; go inicio 4; song
    geo 6.2442 -75.5680
    go pref:nav_alternatives=true 3
    go 'nav-ir:6.2702,-75.5660,Parque\ Explora' 25; shot $L-nav-alternas
    go nav-salir 3; go pref:nav_alternatives=false 3
    go 'nav-ir:6.2702,-75.5660,Parque\ Explora' 25; go vel:42 3; sleep 4; shot $L-nav-clasico
    go tema:claro 7; song; go vel:42 3; sleep 8; shot $L-nav-clasico-claro
    go tema:oscuro 7
    go tema-premium:faro 8; song; go vel:42 3; sleep 10; shot $L-nav-faro
    go tema-premium:aurora 6; go solo-mapa:1 6; song; go vel:42 3; sleep 10; shot $L-nav-aurora-solomapa
    go solo-mapa:0 5; go nav-salir 3
    # ── escáner OBD-II con el auto simulado ──
    go tema-premium:aurora 6; go tema:oscuro 6; go inicio 4; song
    go obd-demo 10; sleep 40
    go obd-panel 5; shot $L-obd-panel-aurora
    adb shell input tap 754 300; sleep 6; shot $L-obd-grafico; adb shell input keyevent 4; sleep 3
    adb shell input keyevent 4; sleep 2
    go tema-premium:faro 8; go inicio 3; go obd-panel 5; shot $L-obd-panel-faro; adb shell input keyevent 4; sleep 2
    sleep 60
    go tema-premium:aurora 6; go inicio 3; go obd-panel 5; shot $L-obd-panel-alertas; adb shell input keyevent 4; sleep 2
    go obd-ajustes:obd_guide 5; shot $L-obd-guia
    go obd-ajustes:obd_guide:panel 5; shot $L-obd-guia-panel
    go obd-ajustes:obd 5; shot $L-obd-ajustes
    adb shell input keyevent 4; sleep 2; adb shell input keyevent 4; sleep 2
    go obd-off 4; go tema-premium: 6; go inicio 3
  done
  kill $GEO 2>/dev/null
  adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime" | tail -60 > $OUT/registro.txt
  ls -l $OUT
  exit 0
fi
if [ "$MODO" = promo ] || [ "$MODO" = promo-v ]; then
  # Imágenes promocionales (nov. 2026): todo lo de la 2.21 con el vehículo de ejemplo, mapa sin conexión de Medellín
  # y velocímetro mixto. La foto del vehículo se pone después, al armar las imágenes (no se sube al repo).
  kill $GEO 2>/dev/null
  ( while true; do adb emu geo fix -75.5680 6.2442 1495 8 34 >/dev/null 2>&1; sleep 2; done ) &
  GEO=$!
  setprefs() {   # nombre, vehículo y etiqueta en las preferencias de la app (versión de prueba: run-as)
    adb shell am force-stop $PKG; sleep 2
    adb shell "run-as $PKG cat shared_prefs/launcher.xml" > p.xml
    python3 - <<'PY'
import re
s=open('p.xml',encoding='utf-8').read()
for k,v in [('user_name','Andrés'),('car_name','Kia Picanto'),('car_tag','GT Line')]:
    s=re.sub(r'\s*<string name="%s">[^<]*</string>'%k,'',s)
    s=s.replace('</map>','    <string name="%s">%s</string>\n</map>'%(k,v))
open('p.xml','w',encoding='utf-8').write(s)
PY
    adb push p.xml /data/local/tmp/p.xml >/dev/null; adb shell chmod 644 /data/local/tmp/p.xml
    adb shell "run-as $PKG cp /data/local/tmp/p.xml shared_prefs/launcher.xml"
    adb shell am start -n $ACT >/dev/null; sleep 8
  }
  if [ -s colombia.map ]; then
    adb shell mkdir -p /sdcard/Download
    adb push colombia.map /sdcard/Download/colombia.map | tail -1
    go mapa-archivo:/sdcard/Download/colombia.map 8
  fi
  go idioma:es 8; go unidad:kmh 4; go tema:oscuro 6; go diseno:base 5; go diseno:velo-2 5
  go demo-aceite 4; go demo-mant 4
  setprefs
  down() { adb shell input swipe 1300 1000 1300 250 500; sleep 2; }
  # ── horizontal (2048x1200 = 1024x600 x2) ──
  if [ "$MODO" = promo ]; then
  prep; sleep 30; shot p01-inicio-oscuro
  go tema:claro 6; prep; sleep 20; shot p02-inicio-claro
  go tema:oscuro 6; go ajustes:2 6; shot p03-mantenimientos
  go menu-mant 4; shot p04-menu-mantenimiento; adb shell input keyevent 4; sleep 2
  adb shell input keyevent 4; sleep 2
  go mapa-lista:south-america/ 14; shot p05-descargar-mapa; adb shell input keyevent 4; sleep 2; adb shell input keyevent 4; sleep 2
  go ajustes:9 6; down; down; sleep 2; shot p06-ajustes-sistema; adb shell input keyevent 4; sleep 2
  go copia-guardar 6; shot p07-copia-guardar; adb shell input keyevent 4; sleep 2
  go diag-reproductor 5; shot p08-diagnostico; adb shell input keyevent 4; sleep 2
  go ajustes:1 6; shot p09-ajustes-vehiculo; adb shell input keyevent 4; sleep 2
  go inicio 4; go diseno:velomapa 6; prep; sleep 15; shot p10-velocimetro-en-mapa; go diseno:base 4; go diseno:velo-2 4
  fi
  # ── vertical (1080x1920, como en las pruebas del bloque 9: lienzo de 720 de ancho) ──
  adb shell wm size 1080x1920; adb shell wm density 160; sleep 10
  go inicio 6; prep; sleep 30; shot v01-vertical-oscuro
  go tema:claro 6; prep; sleep 20; shot v02-vertical-claro
  go tema:oscuro 6; go diseno:sinreproductor 6; sleep 15; shot v03-vertical-vehiculo-flotante
  go diseno:base 4; go diseno:velo-2 4; go ajustes:2 6; shot v04-vertical-ajustes; adb shell input keyevent 4; sleep 2
  adb shell wm size 2048x1200; adb shell wm density 320; sleep 5
  kill $GEO 2>/dev/null
  ls -l $OUT
  exit 0
fi
if [ "$MODO" = proyecto ]; then
  # Imágenes del proyecto (README, tiendas, página): una de cada función, alternando oscuro y claro
  if [ -s colombia.map ]; then
    adb shell mkdir -p /sdcard/Download
    adb push colombia.map /sdcard/Download/colombia.map | tail -1
    go mapa-archivo:/sdcard/Download/colombia.map 8
  fi
  down() { adb shell input swipe 1300 1000 1300 250 500; sleep 2; }
  for L in es en; do
    go idioma:$L 8
    if [ $L = es ]; then go unidad:kmh 4; else go unidad:auto 4; fi
    go tema:oscuro 6; go diseno:base 6; prep; sleep 30; shot $L-1-oscuro-inicio
    go tema:claro 6; go diseno:compacta 4; go diseno:plegar 6; prep; sleep 20; shot $L-2-claro-mapa
    go tema:oscuro 6; go diseno:base 4; go diseno:izq 4; go diseno:sinauto 4; go diseno:lateral 6; prep; sleep 20; shot $L-3-oscuro-lateral
    go tema:claro 6; go diseno:base 4; go diseno:izq 6; prep; sleep 20; shot $L-4-claro-izquierda
    go diseno:base 6; go tema:oscuro 6; go ajustes:5 6; down; down; down; sleep 2; shot $L-5-oscuro-ajustes-mapa
    go mapa-lista:south-america/ 14; shot $L-6-oscuro-descarga
    adb shell input keyevent 4; sleep 2; adb shell input keyevent 4; sleep 2
    go tema:claro 6; go ajustes:2 6; down; sleep 2; shot $L-7-claro-ajustes-diseno
    adb shell input keyevent 4; sleep 2
  done
  kill $GEO 2>/dev/null
  ls -l $OUT
  exit 0
fi
for L in es en; do
  go idioma:$L 8
  if [ $L = es ]; then go unidad:kmh 4; else go unidad:auto 4; fi
  go tema:oscuro 6; go diseno:base 6; prep; sleep 20; shot $L-oscuro-normal
  go diseno:izq 4; go diseno:sinauto 4; go diseno:lateral 6; prep; shot $L-oscuro-lateral-izq
  go diseno:der 6; prep; shot $L-oscuro-lateral-der
  go tema:claro 6; prep; shot $L-claro-lateral-der
  go diseno:izq 6; prep; shot $L-claro-lateral-izq
  go diseno:base 4; go diseno:compacta 4; go diseno:plegar 6; prep; shot $L-claro-compacta-plegado
  go diseno:base 4; go diseno:izq 6; prep; shot $L-claro-normal-izq
  go diseno:base 4; go diseno:sinauto 4; go diseno:compacta 6; prep; shot $L-claro-sinauto-compacta
  go tema:oscuro 6; prep; shot $L-oscuro-sinauto-compacta
  go diseno:base 4; go diseno:izq 4; go diseno:compacta 6; prep; shot $L-oscuro-compacta-izq
  go diseno:base 6; go ajustes:2 6; adb shell input swipe 1300 900 1300 400 600; sleep 3; shot $L-oscuro-ajustes-diseno
  adb shell input keyevent 4; sleep 2
  go tema:claro 6; go ajustes:2 6; adb shell input swipe 1300 900 1300 400 600; sleep 3; shot $L-claro-ajustes-diseno
  adb shell input keyevent 4; sleep 2
  go tema:oscuro 4
done
kill $GEO 2>/dev/null
ls -l $OUT
