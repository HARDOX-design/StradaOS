#!/bin/bash
# Bloque 11 (2.22): pantalla en reposo, orden de apps, diseño forzado, tema por amanecer y chino.
# Uso: bloque11.sh API APK CARPETA
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
adb emu geo fix -73.25 10.46 >/dev/null 2>&1; sleep 3   # Valledupar: para el amanecer/atardecer
go pista-demo 4; go vel:62 3

# ── horizontal 1024x600: el inicio no cambia ──
shot h01-inicio
go pref:idle_speed=0 4; go vel:62 2; go reposo 6; shot h02-reposo-digital
go pref:idle_speed=1 4; go vel:62 2; go reposo 6; shot h03-reposo-analogico
go pref:idle_speed=2 4; go pref:idle_size=2 4; go vel:85 2; go reposo 6; shot h04-reposo-mixto-muy-grande
go pref:idle_speed=3 4; go pref:idle_color=3 4; go pref:idle_size=1 4; go reposo 6; shot h05-reposo-ambar-sin-velocidad
adb shell input tap 500 300; sleep 3; shot h06-reposo-quitado
go pref:idle_color=0 3; go pref:idle_on=true 4
go ajustes:3 5; for i in 1 2 3 4 5; do adb shell input swipe 600 500 600 100 400; sleep 1; done; sleep 2; shot h07-ajustes-reposo
go pref:theme=auto 4; go pref:auto_sun=true 4; go ajustes:5 5; shot h08-tema-amanecer
go pref:auto_sun=false 4; go ajustes:5 5; shot h09-tema-horas-fijas
go pref:theme=dark 4; go inicio 3
go cajon 5; shot h10-cajon-a-z; back
go pref:apps_order=za 3; go cajon 5; shot h11-cajon-z-a; back
go pref:apps_order=installed 3; go cajon 5; shot h12-cajon-instaladas; back
go pref:apps_order=az 3
go compat 4; shot h13-compatibilidad; back
go idioma:zh 6; go inicio 3; go pista-demo 3; shot h14-inicio-zh
go ajustes:3 5; for i in 1 2 3 4 5; do adb shell input swipe 600 500 600 100 400; sleep 1; done; sleep 2; shot h15-ajustes-zh
go pref:idle_speed=2 3; go vel:62 2; go reposo 6; shot h16-reposo-zh
adb shell input tap 500 300; sleep 2
go idioma:es 6; go inicio 3

# ── tipo Tesla con barra de clima: casi cuadrada (768x880) ──
if [ "$API" -ge 18 ]; then size 768x880; else go sim:8 6; fi
go inicio 6; go pista-demo 4; go vel:62 3; shot t01-automatico
go ajustes:3 5; shot t02-ajustes-diseno
go pref:layout_mode=vertical 5; go inicio 4; go pista-demo 3; go vel:62 3; shot t03-forzado-vertical
go pref:idle_speed=1 3; go vel:62 2; go reposo 6; shot t04-reposo-vertical
adb shell input tap 300 300; sleep 2
go compat 4; shot t05-compatibilidad; back
go pref:layout_mode=horizontal 5; go inicio 4; shot t06-forzado-horizontal
go pref:layout_mode=auto 4

# ── vertical 1080x1920 ──
if [ "$API" -ge 18 ]; then size 1080x1920; else go sim:7 6; fi
go inicio 6; go pista-demo 4; go pref:idle_speed=2 3; go pref:idle_size=2 3; go vel:62 2; go reposo 6; shot w01-reposo-1080
adb shell input tap 500 500; sleep 2; shot w02-inicio-1080

# ── vuelta a horizontal ──
if [ "$API" -ge 18 ]; then size 1024x600; else go sim:0 6; fi
go pref:idle_on=false 4; go inicio 6; go pista-demo 3; shot h30-inicio-de-vuelta
adb shell "run-as $PKG cat files/fallos.txt" > "$OUT/fallos.txt" 2>/dev/null || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; fi
ls -l $OUT
