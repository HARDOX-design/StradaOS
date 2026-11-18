#!/bin/bash
# Issue #5 (2.24.2): pantallas HCN / Hengchen. Simula el servicio «sourceinfo» del equipo (archivo local, estado y
# tiempo) y los avisos del Bluetooth del sistema (AVRCP, Android 5+). Uso: hcn.sh API APK CARPETA
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
adb shell mkdir -p /sdcard/Music
adb push musica/prueba_hcn.mp3 /sdcard/Music/prueba_hcn.mp3 >/dev/null
adb push musica/Mi_Cancion-Sin_Etiquetas.mp3 /sdcard/Music/Mi_Cancion-Sin_Etiquetas.mp3 >/dev/null
adb logcat -c
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
go idioma:es 6; go tema:oscuro 6; go inicio 4
shot h00-inicio-sin-musica

# ── reproductor de fábrica con un archivo con etiquetas (título, artista, carátula) ──
go 'hcn-sim:/sdcard/Music/prueba_hcn.mp3,start,44,244' 5; shot h01-local-con-etiquetas
sleep 8; shot h02-sigue-contando
go 'hcn-sim:/sdcard/Music/prueba_hcn.mp3,paused,60,244' 4; shot h03-pausa-en-1-00
go 'hcn-sim:/sdcard/Music/prueba_hcn.mp3,start,150,244' 4; shot h04-adelanto-a-2-30
# el celular por Bluetooth en pausa manda su canción: no reemplaza la del equipo
go 'avrcp-sim:Otra_Cancion_Del_Celular,Celular,2,10,200' 4; shot h05-bluetooth-en-pausa-no-reemplaza
# archivo sin etiquetas: el nombre del archivo
go 'hcn-sim:/sdcard/Music/Mi_Cancion-Sin_Etiquetas.mp3,start,5,180' 5; shot h06-sin-etiquetas
# el equipo cambia de fuente (Bluetooth, ZLink…): borra el archivo y StradaOS quita la canción
go 'hcn-sim:,,0,0' 4; shot h07-cambio-de-fuente

# ── Bluetooth del sistema (AVRCP, solo Android 5+) ──
go 'avrcp-sim:Cancion_Por_Bluetooth,Artista_BT,3,30,200' 4; shot h08-avrcp-cancion
go 'avrcp-sim:-,,2,45,0' 4; shot h09-avrcp-pausa
go 'avrcp-sim:-,,3,120,0' 4; shot h10-avrcp-adelanto-a-2-00
go diag-reproductor 5; shot h11-diagnostico
back

# ── un tema premium con la canción del equipo ──
go tema-premium:aurora 6; go 'hcn-sim:/sdcard/Music/prueba_hcn.mp3,start,30,244' 5; shot h12-aurora
go tema-premium:cobalto 6; go inicio 4; shot h13-cobalto
go tema-premium: 6

adb logcat -d -v brief | grep -E "AndroidRuntime|FATAL|StradaHcn" > $OUT/errores.txt || true
adb logcat -d -v brief | grep -iE "stradaos" | tail -200 > $OUT/logcat.txt || true
echo "errores: $(grep -c . $OUT/errores.txt)"
