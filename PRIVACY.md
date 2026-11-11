# Privacidad

StradaOS **no tiene cuentas, anuncios, rastreadores ni analíticas**, y no envía tus datos a servidores propios.

## Qué usa y para qué

| Dato | Para qué | ¿Sale del equipo? |
|---|---|---|
| Ubicación (GPS) | Velocímetro, mapa, clima y, si lo activas, el recordatorio de cambio de aceite (solo se guarda en el equipo la distancia recorrida, no los lugares) | Para el mapa, los servidores de mapas reciben qué zona descargar, como en cualquier mapa web. Para el clima, la latitud y longitud aproximadas se envían a [Open-Meteo](https://open-meteo.com) y, si el equipo no sabe el nombre de la ciudad, a [BigDataCloud](https://www.bigdatacloud.com). |
| Pista en reproducción | Mostrar la canción en el reproductor | No. Se lee del sistema y de la notificación de Open Headunit solo en el equipo. |
| Foto del vehículo | Mostrarla en la pantalla de inicio | No. Se procesa y se guarda en el equipo. |
| Lista de apps instaladas | Accesos directos y cajón de apps | No. |

## Conexiones de red

- **Mapas:** OpenStreetMap (servidores comunitarios), OpenTopoMap y Esri.
- **Clima:** Open-Meteo (y BigDataCloud para el nombre de la ciudad).
- **Mapa sin conexión:** solo si eliges *Descargar un mapa*, el archivo del país se baja de `download.mapsforge.org`. Después el mapa se dibuja en el equipo, sin conexión.
- **Actualizaciones:** GitHub (`api.github.com`, `raw.githubusercontent.com`) y jsDelivr, para saber si hay una versión nueva. Se puede desactivar en *Ajustes → Acerca de*.

## Permisos

- **Accesibilidad** («Carátula de Android Auto»): solo escucha las notificaciones de Open Headunit para leer la carátula. Es opcional.
- **Acceso a notificaciones** (Android 5+): solo para leer la canción que suena. Es opcional.
- **Almacenamiento:** solo para buscar o guardar el archivo del mapa sin conexión (`Download/StradaOS`). Es opcional.

El diagnóstico de *Ajustes → Sistema → Exportar diagnóstico* se guarda en la memoria del equipo y solo lo compartes tú, si quieres.
