#!/bin/bash
# Bloque 9 (2.21): ventanas propias, diagnósticos traducidos, copia de seguridad, francés y diseño vertical.
# Uso: bloque9.sh API APK CARPETA
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
go demo-aceite 5; go demo-mant 5; go pista-demo 4; go vel:62 3

# ── horizontal: nada cambia en el inicio; ventanas nuevas ──
shot h01-inicio
go boton:2 4; shot h02-ventana-lista; back
go elegir:2 5; shot h03-ventana-apps; back
go aviso-aceite 4; shot h04-ventana-aviso; back
go diag-reproductor 4; shot h05-diag-reproductor-es; back
go compat 4; shot h06-compatibilidad; back
go fecha 4; shot h07-fecha; back
go hora 4; shot h08-hora; back
go tiempo-mant 4; shot h09-campo-de-texto; back; back
go ayuda-bt 4; shot h10-texto-largo; back
go menu-mant 4; shot h11-menu-mantenimiento; back
go ajustes:9 5; shot h12-ajustes-sistema
go copia-guardar 6; shot h13-copia-guardar; back; back
go copia-prueba 6
go copia-restaurar 8; shot h14-copia-restaurar; back
go inicio 3
go idioma:en 6; go diag-reproductor 4; shot h15-diag-reproductor-en; back
go diag-mapa 4; shot h16-diag-mapa-en; back
go idioma:fr 6; go inicio 3; shot h17-inicio-fr
go diag-reproductor 4; shot h18-diag-reproductor-fr; back
go ajustes:9 5; shot h19-ajustes-fr
go inicio 3; go idioma:ar 6; go diag-reproductor 4; shot h20-diag-reproductor-ar; back
go idioma:es 6; go tema:claro 6; go boton:2 4; shot h21-ventana-claro; back
go fecha 4; shot h22-fecha-claro; back
go asistente 4; shot h23-asistente-idioma; back
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 6
go tema:oscuro 6; go inicio 3

# ── vertical 768x1024 (tipo Tesla 9,7") ──
# en Android 4.1 el emulador no cambia de tamaño: se usa la simulación de pantalla de la versión de prueba
if [ "$API" -ge 18 ]; then size 768x1024; else go sim:6 6; fi
go inicio 6; go pista-demo 4; go vel:62 3; shot v01-vertical
go diseno:izq 5; go pista-demo 3; shot v02-reproductor-izquierda
go diseno:der 4; go diseno:sinauto 5; go pista-demo 3; go vel:62 3; shot v03-sin-vehiculo
go diseno:conauto 4; go diseno:sinreproductor 5; go vel:62 3; shot v04-sin-reproductor
go diseno:sinauto 5; shot v05-solo-mapa
go diseno:base 5; go diseno:compacta 5; go diseno:botones-10 5; go pista-demo 3; shot v06-compacta-10-botones
go diseno:base 5; go diseno:sinbarra 5; go pista-demo 3; shot v07-sin-barra
go diseno:base 5; go diseno:velo-2 5; go pista-demo 3; go vel:85 3; shot v08-velocimetro-mixto
go diseno:base 5; go ajustes:3 5; shot v09-ajustes
go ajustes:9 5; shot v10-ajustes-sistema
go inicio 3; go cajon 5; shot v11-cajon; back
go boton:2 4; shot v12-ventana; back
go diag-reproductor 4; shot v13-diagnostico; back
go fecha 4; shot v14-fecha; back
go tema:claro 6; go pista-demo 3; go vel:62 3; shot v15-vertical-claro
go tema:oscuro 6

# ── vertical 1080x1920 ──
if [ "$API" -ge 18 ]; then size 1080x1920; else go sim:7 6; fi
go inicio 6; go pista-demo 4; go vel:62 3; shot w01-vertical-1080
go diseno:sinauto 5; go pista-demo 3; shot w02-sin-vehiculo
go diseno:conauto 4; go diseno:sinreproductor 5; shot w03-sin-reproductor
go diseno:base 5; go ajustes:5 5; shot w04-ajustes
go inicio 3

# ── vuelta a horizontal: todo como antes ──
if [ "$API" -ge 18 ]; then size 1024x600; else go sim:0 6; fi
go inicio 6; go pista-demo 3; shot h30-inicio-de-vuelta
# ── 2.21.1: ocultar la barra del sistema y corrección del velocímetro ──
go diseno:base 5; go velofix:+5% 3; go vel:60 3; shot x01-velocidad-mas-5
go velofix:0 3
go barra:1 6; shot x02-barra-sistema-oculta
go ajustes:1 5; shot x03-ajustes-vehiculo
go ajustes:3 5; adb shell input swipe 600 500 600 100 400; sleep 1; adb shell input swipe 600 500 600 100 400; sleep 2; shot x04-ajustes-pantalla
go barra:0 5; go inicio 4; shot x05-barra-de-vuelta
adb shell "run-as $PKG cat files/fallos.txt" > "$OUT/fallos.txt" 2>/dev/null || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
grep "PRUEBA copia" "$OUT/fallos.txt" || echo "sin resultado de la copia"
ls -l $OUT
