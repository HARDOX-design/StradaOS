#!/bin/bash
# Bloque 21 (2.34): escáner OBD con un ELM327 Wi-Fi simulado (lectura agrupada, respuestas perdidas, clones
# que no entienden el número de tramas o varios PIDs, kilometraje con el odómetro y con la velocidad) y las
# revoluciones junto al velocímetro en los temas.
# Uso: bloque21.sh API APK CARPETA
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
back() { adb shell input keyevent 4; sleep 2; }
FALLAS=0
falla() { echo "FALLA · $1" | tee -a $OUT/RESULTADO.txt; FALLAS=$((FALLAS + 1)); }
okk() { echo "OK    · $1" | tee -a $OUT/RESULTADO.txt; }
vivo() { adb shell ps | grep -q "$PKG" || falla "la app no está corriendo ($1)"; }
prefsxml() {  # preferencias de «launcher» (run-as; en los emuladores viejos adb ya es root)
  local x
  x=$(adb shell run-as $PKG cat shared_prefs/launcher.xml 2>/dev/null | tr -d '\r')
  echo "$x" | grep -q "<map" || x=$(adb shell cat /data/data/$PKG/shared_prefs/launcher.xml 2>/dev/null | tr -d '\r')
  echo "$x"
}
pref() {  # valor de una preferencia float/int/string
  prefsxml | grep "name=\"$1\"" | sed -E 's/.*value="([^"]*)".*/\1/; s/.*>([^<]*)<\/string>.*/\1/' | head -1
}

# ELM327 Wi-Fi simulado en la máquina; desde el emulador es 10.0.2.2
export ELM_MODE=/tmp/elm-mode ELM_SPEED=/tmp/elm-speed ELM_LOG=$OUT/elm-log.jsonl
echo multi > $ELM_MODE; rm -f $ELM_SPEED
python3 .github/scripts/elm327sim.py > $OUT/elm-sim.txt 2>&1 &
SIM=$!
sleep 2
fase() { echo "{\"t\": 0, \"c\": \"#FASE $1\", \"m\": \"$1\"}" >> $ELM_LOG; echo "── fase $1"; }
ADDR=tcp:10.0.2.2:35000

adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
go idioma:es 6; go tema:oscuro 6; go inicio 4
go demo-aceite 5                      # recordatorio de aceite con 44.200 km (con el odómetro simulado ya toca el cambio)
go pref:speedo_rpm=true 5

# 1. lectura agrupada, odómetro del auto (PID A6) y revoluciones en el inicio
fase multi
go obd-addr:$ADDR 45
vivo multi
shot b01-clasico-rpm
B=$(pref odo_base_km); echo "odo_base_km=$B"
python3 -c "import sys; b=float('$B' or 0); sys.exit(0 if 45678 <= b <= 45700 else 1)" \
  && okk "odómetro del auto: $B km (el simulado arrancó en 45678.9)" || falla "odómetro del auto: odo_base_km=$B (esperado ~45679)"
shot b01b-aviso-aceite-odometro      # con el odómetro del auto ya toca el cambio: el aviso dice «del auto»
back; sleep 2                         # «Aún no»
go menu-aceite 4; shot b02-menu-aceite-odometro; back
go pref:speedo=1 5; go inicio 4; shot b02b-clasico-analogico-rpm
go ajustes:3 5; shot b02c-ajustes-inicio; adb shell input swipe 600 520 600 160 400; sleep 2; shot b02d-ajustes-inicio-rpm
go inicio 3; go pref:speedo=0 4
go tema-premium:carbono 10; go inicio 4; shot b03-carbono-rpm
go tema:claro 6; go inicio 4; shot b04-carbono-claro-rpm; go tema:oscuro 6
go tema-premium:faro 8; go inicio 4; shot b05-faro-rpm
go tema:claro 6; go inicio 4; shot b06-faro-claro-rpm; go tema:oscuro 6
go pref:speedo=1 4; go inicio 4; shot b06b-faro-analogico-rpm
go pref:speedo=0 4; go inicio 4; shot b06c-faro-digital-rpm
go tema-premium:aurora 8; go inicio 4; shot b07-aurora-rpm
go tema-gratis:1 6; go inicio 4
# velocímetro sobre el mapa (tarjeta del vehículo oculta): sin círculo de fondo
go diseno:sinauto 4; go diseno:velomapa 4; go pref:speedo=2 4; go inicio 4; shot b07b-mapa-velocimetro-rpm
go diseno:esquina-bl 4; go inicio 4; shot b07c-mapa-velocimetro-abajo
go diseno:conauto 4; go pref:speedo=0 4; go inicio 4
go obd-panel 4; shot b08-panel-vivo; back
go obd-ajustes:obd_live 6; shot b09-todos-los-datos
go inicio 3
vivo temas

# 2. el escáner se traga respuestas: se rescata con ATI, sin reconectar
fase lose; echo lose > $ELM_MODE; sleep 40; vivo lose
# 3. clon que no entiende el número de tramas
fase nocount; echo nocount > $ELM_MODE; sleep 20; vivo nocount
# 4. clon que no entiende varios PIDs por consulta
fase nomulti; echo nomulti > $ELM_MODE; sleep 30; vivo nomulti
go obd-ajustes:obd_adv 5; shot b10-avanzado-registro; go inicio 3

# 4b. #77: clon que se queda callado con varios PIDs (ELM327 v1.5 del Golf VI). Antes: reconectaba cada ~5 s sin fin.
# Ahora: vuelve a la lectura de a uno sin reconectar, lo recuerda y la conexión siguiente ya no agrupa
go obd-off 5
fase hang; echo hang > $ELM_MODE
go obd-addr:$ADDR 45
vivo hang
[ "$(pref obd_multi_bad)" = "$ADDR" ] && okk "#77: recordó que este escáner no agrupa" || falla "#77: obd_multi_bad=$(pref obd_multi_bad)"
go obd-ajustes:obd_adv 5; shot b10b-avanzado-no-agrupa; go inicio 3
go obd-off 5
fase hang2
go obd-addr:$ADDR 30
vivo hang2
go obd-off 5
go pref:obd_multi_bad= 3

# 5. auto sin odómetro: el kilometraje se suma con la velocidad del escáner (60 km/h ≈ 1 km por minuto)
go obd-off 5
fase noodo; echo noodo > $ELM_MODE; echo 60 > $ELM_SPEED
go demo-aceite 5
go obd-addr:$ADDR 80
vivo noodo
B=$(pref odo_base_km); M=$(pref odo_gps_m); echo "odo_base_km=$B odo_gps_m=$M"
python3 -c "import sys; b=float('$B' or 0); m=float('$M' or 0); sys.exit(0 if abs(b-44200)<0.01 and 500 <= m <= 1600 else 1)" \
  && okk "sin odómetro: base $B km, sumó $M m con la velocidad del escáner" || falla "sin odómetro: base=$B gps_m=$M (esperado 44200 y 500–1600 m)"
go menu-aceite 4; shot b11-menu-aceite-estimado; back
go obd-off 4

kill $SIM 2>/dev/null
python3 .github/scripts/elmcheck.py $ELM_LOG $OUT/elm-resumen.txt || FALLAS=$((FALLAS + 1))
cat $OUT/elm-resumen.txt >> $OUT/RESULTADO.txt
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: (java|Caused|\tat com.stradaos)" | tail -60 > $OUT/logcat.txt
if grep -q "com.stradaos" $OUT/logcat.txt; then echo "CIERRE" > $OUT/CIERRES.txt; falla "la app se cerró (logcat.txt)"; fi
ls -l $OUT
echo "fallas: $FALLAS"
exit $FALLAS
