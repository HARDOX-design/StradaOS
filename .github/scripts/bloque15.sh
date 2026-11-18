#!/bin/bash
# Bloque 15 (2.24.1): #37 (sin GPS no ir al 0,0), #34 (nombres de la lista de mapas), #36 (tema por el brillo)
# y las traducciones de navegación (PR #38 + 7 idiomas). Además, el inicio de siempre y los temas, para ver que nada cambió.
# Uso: bloque15.sh API APK CARPETA
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
if [ "$API" -ge 23 ]; then adb install -r -g "$APK" | tail -1; else adb install -r "$APK" | tail -1; fi
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1
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
fix() { adb emu geo fix $2 $1 1495 8 0 >/dev/null 2>&1; }
setsys() {  # ajuste del sistema (API 16 no trae el comando settings)
  if [ "$API" -ge 17 ]; then adb shell settings put system $1 $2
  else adb shell content insert --uri content://settings/system --bind name:s:$1 --bind value:i:$2; fi
}
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
go idioma:es 6; go tema:oscuro 6; go inicio 4

# ── el inicio de siempre (nada debe cambiar) ──
for i in 1 2 3 4 5; do fix 6.2442 -75.5680; sleep 2; done
go pista-demo 3; go vel:48 3; sleep 8; shot b01-inicio-clasico
go tema-premium:aurora 6; go pista-demo 3; sleep 6; shot b02-aurora
go tema-premium:cobalto 6; go pista-demo 3; sleep 6; shot b03-cobalto
go tema-premium: 6

# ── Línea de tiempo con Android Auto (caso de Andrés, 18 nov.): canción por «rcc» sin tiempo y la
# notificación de Open Headunit con el tiempo. Al adelantar en el celular, StradaOS debe seguir a la notificación.
go aa-simular:1 2
go 'aa-rcc:Todo_Va_a_Estar_Bien,191' 3
go 'aa-notif:Todo_Va_a_Estar_Bien,2,191' 3; shot a01-tiempo-al-empezar
go 'aa-notif:Todo_Va_a_Estar_Bien,154,191' 2; shot a02-adelanto-a-2-34
go 'aa-notif:Todo_Va_a_Estar_Bien,156,191' 2; shot a03-sigue-sin-saltos
go 'aa-notif:Todo_Va_a_Estar_Bien,30,191' 2; shot a04-atraso-a-0-30
go 'aa-rcc:La_Bondad_De_Dios,294' 2; go 'aa-notif:La_Bondad_De_Dios,1,294' 2; shot a05-cancion-nueva
go 'aa-notif:La_Bondad_De_Dios,207,294' 2; shot a06-adelanto-a-3-27
go tema-premium:cobalto 6; go 'aa-notif:La_Bondad_De_Dios,120,294' 3; shot a07-cobalto-adelanto-2-00
go tema-premium: 6
go aa-simular:0 2

# ── #37: con GPS en 0,0 (sin señal) el mapa no se va al mar ──
# Medellín ya quedó guardada como última posición buena; se cierra la app y la pantalla solo manda 0,0.
adb shell am force-stop $PKG; sleep 2
( for i in $(seq 1 40); do fix 0 0; sleep 2; done ) & ZERO=$!
sleep 6
adb shell am start -n $ACT >/dev/null; sleep 20
shot c01-gps-cero-al-arrancar
go pista-demo 3; sleep 10; shot c02-gps-cero-sigue
kill $ZERO 2>/dev/null
# con GPS bueno vuelve a seguir la ubicación real (Bogotá, lejos de Medellín)
for i in 1 2 3 4 5 6; do fix 4.6533 -74.0836; sleep 2; done
sleep 4; shot c03-gps-bueno-otra-vez
# y si en un túnel llega un 0,0, se queda en la última buena
for i in 1 2 3 4; do fix 0 0; sleep 2; done
sleep 4; shot c04-tunel-cero
for i in 1 2 3; do fix 6.2442 -75.5680; sleep 2; done

# ── #36: tema por el brillo de la pantalla ──
setsys screen_brightness_mode 0
setsys screen_brightness 230
go pref:theme=auto 5; go pref:auto_bright=true 5; go pref:bright_dark=40 5
go inicio 4; sleep 4; shot d01-brillo-alto-claro
setsys screen_brightness 40; sleep 6
shot d02-brillo-bajo-oscuro
setsys screen_brightness 110; sleep 6
shot d03-brillo-medio-claro
go ajustes:5 6; shot d04-apariencia-brillo
setsys screen_brightness 30; sleep 6; shot d05-apariencia-oscuro-al-vuelo
go idioma:de 6; go ajustes:5 6; shot d06-apariencia-aleman
go idioma:ar 6; go ajustes:5 6; shot d07-apariencia-arabe
go idioma:es 6
# volver como estaba: horario fijo y tema oscuro
go pref:auto_bright=false 4; go tema:oscuro 6; back
setsys screen_brightness 200

# ── #34: nombres de la lista de mapas (registro StradaMapas) ──
adb logcat -c
for L in es en ar; do
  go idioma:$L 6
  for c in africa asia australia-oceania central-america europe north-america south-america russia; do go mapa-nombres:$c/ 7; done
  adb logcat -d -s StradaMapas:W | sed 's/^.*StradaMapas[^:]*: //' > "$OUT/mapas-$L.txt"; adb logcat -c
done
go idioma:es 6
go mapa-lista:asia/ 12; shot e01-lista-asia
adb shell input swipe 512 450 512 120 400; sleep 2; shot e02-lista-asia-2
back; back

# ── traducciones de navegación (PR #38 y los otros 7 idiomas) ──
go pref:nav_app=internal 4
for L in tr pt de fr zh; do go idioma:$L 6; go nav-buscar 6; shot f-$L-buscar-destino; back; done
go idioma:es 6; go pref:nav_app= 4

adb logcat -d | grep -E "FATAL|AndroidRuntime" | head -40 > $OUT/cierres.txt
[ -s $OUT/cierres.txt ] && cp $OUT/cierres.txt $OUT/CIERRES.txt
adb logcat -d -t 3000 > $OUT/logcat.txt 2>/dev/null
ls $OUT | head -80
