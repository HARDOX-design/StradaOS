# Escáner OBD-II

Motor del módulo (conexión ELM327, lectura, alertas, códigos e IA): PR #42 de Jaacern. Desde la 2.26 no cambia ninguna
pantalla de inicio: el vehículo se ve en un **panel que se despliega encima de cualquier tema** y se configura en
**Ajustes → Tu vehículo → Escáner OBD-II**. Viene apagado (regla del proyecto: lo nuevo arranca apagado).

## Cómo se ve

- **Ajustes → Tu vehículo → Escáner OBD-II:** interruptor «Monitor del motor» (`obd_on`). Al encenderlo por primera vez
  pide elegir el escáner. La fila «Escáner OBD-II» abre sus subpáginas (`Main.settingsSub`): Estado, **Conexión**,
  **Alertas**, **Códigos de falla**, **Datos en vivo**, **Inteligencia artificial** y **Avanzado**. Los valores cambian en
  su sitio mientras se miran (`obdSettingsL`); si cambia lo que hay (aparece un código, se conecta), la página se redibuja.
- **Panel del vehículo** (`VehiclePanel`): sale desde el lado del conductor (lado del reproductor, o el que se elija en
  Avanzado), ocupa ~75 % del ancho (92 % en pantallas verticales) y deja ver el tema difuminado detrás. Muestra tres
  medidores (refrigerante, batería, RPM: verde normal, ámbar aviso, rojo alerta), tres datos chicos (los primeros que el
  auto entregue entre velocidad, carga, consumo, combustible, admisión y aceite), la luz del motor, las alertas y los
  códigos con «Ver todo». Tocar un medidor abre los gráficos grandes. Se abre con la **pestaña del borde** (toque o
  deslizar; `obd_handle`), con el botón de barra **Vehículo (OBD-II)** (función `obd`), al tocar la notificación del
  monitor, o solo cuando aparece una **alerta grave** (`obd_panel_auto`, una vez por alerta). Se cierra con ✕, Atrás,
  tocando fuera o deslizándolo hacia su borde.
- **Difuminado sin GPU especial (Android 4.1+):** al terminar de entrar el panel se dibuja el inicio en 1/6 de
  resolución (ocultando el panel en ese instante) y se difumina con tres pasadas de caja; aparece en 200 ms. El panel
  se mueve con capa de hardware (`withLayer`), así que la animación no espera al dibujo en pantallas lentas.
- **Gráficos grandes** (`ObdActivity`, solo gráficos): escala con grilla, zonas de aviso y alerta, mín/máx/promedio,
  1/5/15/30 min y hasta 3 datos apilados.
- **Ventanas** (`ObdDialogs`): elegir y emparejar el escáner, umbrales de cada alerta, detalle de un código, IA,
  protocolo, cilindrada, escaneo completo y registro de la conexión.

## Qué hace

- **Datos en vivo con gráfico** (último minuto y medio): refrigerante, voltaje de batería, RPM, velocidad, carga del
  motor y combustible. Si el auto los tiene, también aire de admisión, corrección de mezcla corta y larga,
  acelerador, flujo de aire (MAF), aceite y temperatura exterior.
- **Alertas con tono y voz**, aunque esté abierta otra app (Waze, música): corre en un servicio en primer plano con
  una notificación. Una alerta salta solo si el valor sigue fuera de rango un rato, para que un pico de un segundo
  no la dispare.

  | Dato | Aviso | Alerta | Condición |
  |---|---|---|---|
  | Refrigerante | 100 °C | 106 °C | también si sube más de 4 °C/min sobre 95 °C |
  | Voltaje (ATRV del escáner) | 12,6 V o menos / 15,2 V o más | 11,8 V o menos / 15,8 V o más | lo bajo solo con el motor en marcha |
  | Mezcla corta / larga | ±15 % / ±10 % | ±25 % / ±20 % | motor caliente y en marcha, por 30 s / 60 s |
  | RPM | 5200 | 6000 | |
  | Carga del motor | 92 % por 20 s | — | |
  | Aire de admisión | 60 °C | 75 °C | |
  | Aceite (PID 5C) | 125 °C | 135 °C | pocos autos lo entregan |
  | Combustible | 15 % | 8 % | |
  | Código de falla nuevo | pendiente / leve | grave (según la tabla) | se revisa cada 30 s |

- **Códigos de falla:** se leen los confirmados (modo 03) y los pendientes (modo 07), con la luz del motor (MIL).
  Cada código muestra:
  1. sin internet, `assets/obd/dtc_es.tsv`: 82 códigos comunes en español, con gravedad y qué hacer (escrito para StradaOS);
  2. sin internet, `assets/obd/dtc_en.tsv`: ~2100 códigos genéricos SAE P0/P2/P3 en inglés (de
     [fabiovila/OBDIICodes](https://github.com/fabiovila/OBDIICodes), licencia MIT);
  3. con internet y API key: la explicación de una IA para ese auto en particular. Se guarda en el equipo
     (`files/obd_ai_cache.json`) y la próxima vez sale sin señal.
- **Borrar códigos** (modo 04), solo con el motor apagado y con confirmación.
- **VIN** (modo 09) si el auto lo entrega.
- **Alertas a vigilar** (Ajustes → … → Alertas → «Cuándo avisa cada dato»): cada alerta se puede apagar y sus umbrales se pueden cambiar (o volver a los de fábrica).
- **IA en las alertas:** al tocar una alerta, «Preguntar a la IA» la explica con todos los datos del motor de ese momento.
- **Freeze frame** (modo 02): con un código nuevo se leen los datos del motor guardados en el momento de la falla, y
  aparecen en el detalle del código.
- **Vinculación:** *Conexión → Buscar escáneres Bluetooth cercanos* busca y empareja desde la app (pide la clave
  1234 o 0000 si hace falta).
- **Editor táctil del inicio** (presión larga → «Ajustar con el dedo»): bloques con manijas; arrastrar una manija cambia el
  ancho y los vecinos se ajustan en vivo; arrastrar un bloque lo cambia de lugar. Límites en unidades del lienzo
  (vehículo 300–440, reproductor 248–460, centro mínimo 300); al llegar a uno la manija se pone gris y dice «límite».
  Solo dibuja rectángulos mientras se arrastra; el inicio se rearma una vez al tocar «Listo» (también «Restablecer» y
  «Cancelar»). Se guarda en `card_order`, `car_w` y `player_w`.
- **Escanear todo lo que entrega el auto** (Avanzado): informe con cada PID estándar que el auto tiene (nombre, valor y bytes),
  el modo 09 y lecturas crudas propias de la marca (servicio 21 01–0F y algunos 22) a los computadores 7E0 (motor) y
  7E1 (caja). Solo lectura. Se guarda en `Android/data/<paquete>/files/obd/informe-*.txt` para sacarlo por USB o con
  `adb pull`, y compararlo con Torque para encontrar datos como la temperatura del aceite de la caja.

## IA (opcional)

*Ajustes → Tu vehículo → Escáner OBD-II → Inteligencia artificial*. Dos proveedores:

- **Claude (Anthropic):** solo la API key. El modelo por defecto es `claude-opus-5-5`; se puede cambiar en *Modelo*.
- **Groq, OpenAI, Gemini, OpenRouter** u otra API compatible con OpenAI. La lista de modelos se pide al proveedor (*Modelo*),
  así no hay que escribir nombres. Groq recomienda `openai/gpt-oss-120b`, que responde en unos 2 segundos.
- **Probar la IA:** pregunta por un P0711 de ejemplo y muestra la respuesta, el tiempo y el modelo.

En *Mi vehículo* conviene escribir marca, modelo, año, motor y caja (por ejemplo «Kia Sorento 2010 2.4 bencina 4x4
automático»): la IA usa ese dato para dar fallas típicas del modelo. A la IA no se le envía la ubicación (ver `PRIVACY.md`).

La llamada a Claude es HTTP directo (`HttpURLConnection` a través de `Net`) porque el SDK oficial de Java no corre
en Android 4.1 y pesaría más que todo StradaOS.

## Escáneres

- **Compatibles:** ELM327 por **Bluetooth clásico (SPP)** o **Wi-Fi**. Los que son **solo BLE / Bluetooth 4.0** (varios hechos para iPhone: algunos Vgate iCar Pro 4.0, OBDLink CX, Veepeak BLE) **no** funcionan todavía: no se usa BluetoothGatt.
- **Presión de llantas (TPMS): no** se lee con OBD-II genérico (cada marca la guarda en su propio módulo). Si se agrega, sería con un kit TPMS aparte (receptor USB o sensores Bluetooth), no con el escáner.

Cualquier ELM327 por Bluetooth clásico o Wi-Fi sirve:

- **Bluetooth clásico:** primero se empareja en los ajustes del equipo (clave 1234 o 0000) y después se elige en
  *Conexión → Escáner*. Se prueban tres formas de conectar, porque cada clon acepta una distinta.
- **Wi-Fi:** IP y puerto, normalmente `192.168.0.10:35000`.
- **Clones baratos:** los «v2.1» suelen fallar. Responden mejor un OBDLink, un Vgate iCar Pro o un Veepeak.

### Kia Sorento 2010 (CAN)

El Sorento 2010 usa **ISO 15765-4 CAN, 11 bits, 500 kbps**. Lo normal es que el ELM327 lo detecte solo con
`ATSP0`; si el clon no lo logra, en *Avanzado → Protocolo* elige **CAN 11 bits · 500 kbps** (`ATSP6`). Si el auto es
diésel o de otra motorización, la cilindrada se cambia en *Avanzado → Cilindrada del motor*.

- **Comprobado en un Sorento 2010 2.4 bencina (computador del motor `7E8`):** el auto responde a `01 00`, `01 20`
  y `01 40` con estos PIDs:
  `01 03 04 05 06 07 0B 0C 0D 0E 0F 11 13 14 15 1C 1F 21 2E 30 31 33 3C 3E 41 42 43 44 45 46 47 49 4A 4C 4D 4E 55 56`.
  En este motor **no** están MAF (`10`), nivel de combustible (`2F`), temperatura del aceite (`5C`) ni consumo
  (`5E`). El VIN (`09 02`) viene en blanco (`FF`); sí entrega calibración (`09 04`) y CVN (`09 06`).
- **Consumo:** como este motor no trae MAF ni PID 5E, se calcula con la presión del múltiple (0B), las RPM, la
  temperatura del aire de admisión (0F) y la cilindrada (2,4 L por defecto). Es una estimación: el rendimiento
  volumétrico se toma en 0,85. Si el auto sí trae MAF o 5E, se usan esos primero.
- **Dos Wi-Fi a la vez:** si el equipo tiene internet por una Wi-Fi y el ELM327 por otra (interna o USB), la app
  intenta primero la conexión normal y, si falla, ata el socket TCP a la dirección local de la red del escáner.
  En *Conexión → Escáner → Wi-Fi* usa la IP del ELM327 (normalmente `192.168.0.10:35000`).
- **Lectura real verificada (motor al ralentí, 2026-11-18):** refrigerante 92 °C, RPM 680, MAP 29 kPa, avance 6°,
  aire de admisión 66 °C, carga 29,4 %, carga absoluta 16,5 %, lambda 1,000, voltaje 14,3 V, catalizador 600,7 °C,
  exterior 16 °C, EGR 2,7 %, pedal 14,9 %, tiempos 0/531 min, mezclas O2 1,6/0,0 %, MIL apagada y 0 códigos.

## Qué no se puede leer con OBD-II genérico

- **Airbag (SRS), ABS y carrocería** (códigos B y C): cada marca usa sus propios módulos y protocolos, y un ELM327
  en modo genérico no los ve. Para eso hace falta un escáner con soporte de la marca.
- **Temperatura del aceite de la caja automática:** no es un PID estándar. Los códigos de la caja (P07xx, como
  P0711) sí llegan por el modo 03.
- **Presión de aceite:** casi ningún auto la entrega, solo enciende la luz del tablero.

## Probar sin el auto

1. **Auto simulado (lo más rápido):** *Vehículo → Probar con un auto simulado*. Calienta el motor, sube una cuesta
   (la temperatura llega a 110 °C), deja de cargar el alternador y guarda el código P0711. Así se ven todas las alertas.
2. **Desde el PC con adb:** `adb shell am start -n com.stradaos.launcher.debug/com.stradaos.launcher.Main --es prueba obd-demo`
   (versión de prueba) abre la pantalla con el auto simulado encendido.
3. **Emulador ELM327 de Python** (habla el protocolo real por TCP), en el PC:
   ```
   pip install ELM327-emulator      # pide Python 3.12 o anterior
   python -m elm -n 35000 -s car    # escenario «car»
   ```
   En la app: *Conexión → Escáner → Wi-Fi* con `10.0.2.2:35000` desde el emulador de Android, o con la IP del PC desde un
   celular en la misma red.

## Sensores y PIDs en vivo

El monitor lee los sensores en un hilo de fondo cada ~700 ms. Los sensores marcados como **rápidos** se leen en cada vuelta; los demás, una de cada cuatro (~2,8 s).

### Rápidos (cada vuelta, ~700 ms)

| Sensor | PID | Fórmula | Unidad | Alertas |
|--------|-----|---------|--------|---------|
| Temperatura refrigerante | `0x05` | A − 40 | °C | >100 °C aviso / >106 °C crítico |
| Tensión de batería | `0x42` / ATRV | A×256+B / 1000 | V | <12,6 V / >15,2 V aviso |
| RPM | `0x0C` | (A×256+B) / 4 | rpm | >5200 aviso / >6000 crítico |
| Velocidad | `0x0D` | A | km/h | — |
| Carga del motor | `0x04` | A × 100/255 | % | >92 % aviso (hold 20 s) |
| Posición acelerador | `0x11` | A × 100/255 | % | — |
| Presión múltiple (MAP) | `0x0B` | A | kPa | — |
| Consumo calculado | — | MAF o MAP+RPM | L/100 km | — |
| Consumo por hora | — | MAF o MAP+RPM | L/h | — |

> **Voltaje:** Se prefiere el PID `0x42` (medido por el computador del motor) sobre `ATRV` (medido por el escáner). Los clones ELM327 suelen medir ~1 V de menos con `ATRV`.

### Lentos (una de cada 4 vueltas, ~2,8 s)

| Sensor | PID | Fórmula | Unidad | Alertas |
|--------|-----|---------|--------|---------|
| Temperatura aire admisión | `0x0F` | A − 40 | °C | >60 °C aviso / >75 °C (hold 20 s) |
| Nivel de combustible | `0x2F` | A × 100/255 | % | <15 % aviso / <8 % crítico (hold 30 s) |
| Corrección mezcla corta | `0x06` | (A−128) × 100/128 | % | ±15 % aviso / ±25 % crítico (hold 30 s) |
| Corrección mezcla larga | `0x07` | (A−128) × 100/128 | % | ±10 % aviso / ±20 % crítico (hold 60 s) |
| Flujo de masa de aire (MAF) | `0x10` | (A×256+B) / 100 | g/s | — |
| Consumo directo del motor | `0x5E` | (A×256+B) / 20 | L/h | — |
| Temperatura aceite motor | `0x5C` | A − 40 | °C | >125 °C aviso / >135 °C crítico |
| Temperatura exterior | `0x46` | A − 40 | °C | — |
| Avance del encendido | `0x0E` | A/2 − 64 | ° | — |
| Sonda O2 banco 1 sensor 1 | `0x14` | A / 200 | V | — |
| Sonda O2 banco 1 sensor 2 | `0x15` | A / 200 | V | — |
| Tiempo de marcha | `0x1F` | A×256+B | s | — |
| Distancia con MIL encendida | `0x21` | A×256+B | km | — |
| EGR comandada | `0x2E` | A × 100/255 | % | — |
| Arranques desde borrar códigos | `0x30` | A | — | — |
| Distancia desde borrar códigos | `0x31` | A×256+B | km | — |
| Presión barométrica | `0x33` | A | kPa | — |
| Temperatura catalizador banco 1 | `0x3C` | (A×256+B)/10 − 40 | °C | — |
| Temperatura catalizador banco 1 s2 | `0x3E` | (A×256+B)/10 − 40 | °C | — |
| Carga absoluta | `0x43` | (A×256+B) × 100/255 | % | — |
| Lambda comandada | `0x44` | (A×256+B) / 32768 | λ | — |
| Acelerador relativo | `0x45` | A × 100/255 | % | — |
| Acelerador B | `0x47` | A × 100/255 | % | — |
| Pedal D | `0x49` | A × 100/255 | % | — |
| Pedal E | `0x4A` | A × 100/255 | % | — |
| Acelerador comandado | `0x4C` | A × 100/255 | % | — |
| Tiempo con MIL encendida | `0x4D` | A×256+B | min | — |
| Tiempo desde borrar códigos | `0x4E` | A×256+B | min | — |
| Corrección O2 banco 1 | `0x55` | (A−128) × 100/128 | % | — |
| Corrección O2 banco 1 s2 | `0x56` | (A−128) × 100/128 | % | — |

### Cálculo del consumo

El consumo se calcula con la primera fuente disponible en este orden:

1. **PID `0x5E`** (consumo que entrega el propio motor, si lo tiene).
2. **MAF** (`0x10`): `L/h = MAF × 3600 / (14,7 × 745)` (mezcla estequiométrica de bencina).
3. **MAP + RPM + cilindrada**: para motores sin MAF (común en Kia/Hyundai 2.4).  
   Fórmula: `gps = MAP × RPM × cilindrada × 0,85 / (34,446 × (T_aire + 273,15))`  
   La cilindrada se configura en Ajustes → **Cilindrada del motor**.

---

## Referencia de preferencias

Nombre del archivo de preferencias: `launcher` (modo `MODE_PRIVATE`).

| Clave | Tipo | Valor por defecto | Descripción |
|-------|------|------------------|-------------|
| `obd_on` | boolean | `false` | Monitor del motor encendido (sin esto no se conecta ni aparece nada) |
| `obd_addr` | String | — | Dirección del adaptador: `"demo"`, `"tcp:IP:PUERTO"` o `"bt:MAC"` |
| `obd_name` | String | — | Nombre legible del dispositivo (solo informativo) |
| `obd_protocol` | String | `"auto"` | Protocolo CAN: `"auto"`, `"6"`, `"7"`, `"8"`, `"9"` |
| `obd_displacement` | float | `2.4` | Cilindrada del motor en litros (para el cálculo del consumo por MAP) |
| `obd_voice` | boolean | `true` | Avisos por voz (TTS) activados |
| `obd_autostart` | boolean | `true` | Iniciar el monitor automáticamente al abrir StradaOS |
| `obd_vehicle` | String | `""` | Descripción del vehículo para la IA (ej: `"Kia Sorento 2010 2.4"`) |
| `obd_panel_side` | String | `"auto"` | Lado del panel: `auto` (el del reproductor), `left`, `right` |
| `obd_handle` | boolean | `true` | Pestaña en el borde para abrir el panel |
| `obd_panel_auto` | boolean | `true` | Una alerta grave abre el panel |
| `obd_ai_vin` | boolean | `false` | Enviar el VIN a la IA |
| `ai_key` | String | — | API key de la IA (no entra en la copia de seguridad) |
| `obd_chart_win` | long | `300000` | Ventana del gráfico en ms (60000/300000/900000/1800000) |
| `obd_alert_<key>` | boolean | `true` | Alerta del sensor `<key>` activada |
| `obd_thr_<key>_hi1` | float | (fábrica) | Umbral de aviso alto del sensor `<key>` |
| `obd_thr_<key>_hi2` | float | (fábrica) | Umbral crítico alto del sensor `<key>` |
| `obd_thr_<key>_lo1` | float | (fábrica) | Umbral de aviso bajo del sensor `<key>` |
| `obd_thr_<key>_lo2` | float | (fábrica) | Umbral crítico bajo del sensor `<key>` |
| `obd_thr_<key>_abs1` | float | (fábrica) | Umbral de aviso por valor absoluto del sensor `<key>` |
| `obd_thr_<key>_abs2` | float | (fábrica) | Umbral crítico por valor absoluto del sensor `<key>` |
| `obd_alert_coolant_rise` | boolean | `true` | Alerta de subida rápida del refrigerante |
| `obd_alert_dtc` | boolean | `true` | Aviso por voz al detectar un DTC nuevo |

### Claves de sensor (`<key>`)

`coolant` · `volt` · `rpm` · `speed` · `load` · `throttle` · `intake` · `fuel` · `stft` · `ltft` · `maf` · `map` · `fuel_rate` · `oilt` · `ambient` · `timing` · `o2_1` · `o2_2` · `run_time` · `mil_dist` · `egr` · `warmups` · `clear_dist` · `baro` · `cat1` · `cat2` · `abs_load` · `lambda` · `throttle_rel` · `throttle_b` · `pedal_d` · `pedal_e` · `throttle_cmd` · `mil_time` · `clear_time` · `o2_trim_1` · `o2_trim_2`

---

*Documentación basada en el código fuente de StradaOS. Las fórmulas y valores corresponden a la implementación en `Obd.java`, `Elm.java` y `DtcInfo.java`.*
