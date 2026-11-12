<div align="center">
<img src="fastlane/metadata/android/es-ES/images/featureGraphic.png" alt="StradaOS">
<h1>StradaOS</h1>
<b>Una nueva vida para la pantalla Android de tu carro</b>
<br><br>
<a href="https://github.com/lordnordic-debug/StradaOS/releases"><img src="https://img.shields.io/github/v/release/lordnordic-debug/StradaOS?color=FE3211&label=versi%C3%B3n"></a>
<a href="https://github.com/lordnordic-debug/StradaOS/releases"><img src="https://img.shields.io/github/downloads/lordnordic-debug/StradaOS/total?color=FE3211&label=descargas"></a>
<img src="https://img.shields.io/badge/Android-4.1%2B-3DDC84?logo=android&logoColor=white">
<a href="LICENSE"><img src="https://img.shields.io/github/license/lordnordic-debug/StradaOS?label=licencia"></a>
<br><br>
<a href="https://github.com/lordnordic-debug/StradaOS/releases/latest"><img src="https://raw.githubusercontent.com/NeoApplications/Neo-Backup/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" width="200" alt="Descargar en GitHub"></a>
<br>
<b>🌐 <a href="https://lordnordic-debug.github.io/StradaOS/">lordnordic-debug.github.io/StradaOS</a></b> · <a href="#english">English</a> · <a href="#português">Português</a>
</div>

---

**StradaOS** es un launcher (pantalla de inicio) **gratis, sin anuncios y de código abierto** para las pantallas Android de los carros. Se instala como una app más, **sin flashear ni tocar el sistema**, y si no te gusta la quitas y todo queda como estaba. Nació para revivir las radios que dicen *«Android 6»* pero por dentro corren Android 4.x, y también funciona en pantallas modernas.

<p align="center">
<img src="fastlane/metadata/android/es-ES/images/phoneScreenshots/1.png" width="49%">
<img src="fastlane/metadata/android/es-ES/images/phoneScreenshots/2.png" width="49%">
<img src="fastlane/metadata/android/es-ES/images/phoneScreenshots/3.png" width="49%">
<img src="fastlane/metadata/android/es-ES/images/phoneScreenshots/5.png" width="49%">
</p>

## ✨ Qué trae

**En el inicio**
- 🗺️ **Mapa en vivo y sin conexión:** descarga el mapa de tu país desde la app (gratis, de OpenStreetMap) y úsalo sin internet. La flecha se mueve suave con el GPS.
- 📲 **Android Auto y CarPlay** con un toque: abre la app de tu pantalla o [Open Headunit](https://github.com/andreknieriem/headunit-revived) por USB.
- 🎵 **Tu música:** título, artista y tiempo de lo que suena por Bluetooth, con controles grandes. Con Android Auto, también la carátula.
- ⏱️ **Velocímetro**, clima, reloj y **recordatorio de cambio de aceite**.
- 🚗 **Foto de tu vehículo**, a la que StradaOS le quita el fondo.

**Diseña tu inicio**
- Reproductor a la izquierda o a la derecha, tarjeta del vehículo visible u oculta, Android Auto y CarPlay plegables.
- Barra de botones **normal, compacta o lateral** (con reproductor vertical para pantallas anchas como 1280×480).
- Modo **claro, oscuro o automático** y 10 colores.

**A tu medida**
- Cada botón de la barra abre la app o la función que quieras. Oculta las apps que no usas.
- 7 idiomas (español, inglés, portugués, italiano, alemán, malayo y árabe), km/h o mph.
- Avisos de actualización con código QR. Pesa **menos de 1 MB**.

👉 Novedades de cada versión en [Releases](https://github.com/lordnordic-debug/StradaOS/releases).

## 📥 Instalación

1. Descarga **`StradaOS-x.y.z.apk`** desde [Releases](https://github.com/lordnordic-debug/StradaOS/releases/latest) y cópialo a la pantalla (si usas USB, pásalo primero a la memoria interna).
2. Activa **Ajustes → Seguridad → Orígenes desconocidos** e instálalo.
3. Presiona **Inicio** y elige **StradaOS → Siempre**. Un asistente te guía el resto.

**Android Auto en pantallas que no lo traen:** instala [Open Headunit](https://github.com/andreknieriem/headunit-revived/releases), conecta el celular por USB y toca **Android Auto**. En *Ajustes → Música* activa la carátula de Android Auto.

## 📋 Compatibilidad

| Mínimo | Probado |
|---|---|
| Android 4.1 (API 16), cualquier pantalla horizontal | Android 4.2 (Autochips AC8227, 1024×600), Android 10 y Android 17 |

¿La probaste en otra pantalla? Cuéntalo en un [issue](https://github.com/lordnordic-debug/StradaOS/issues).

## ❓ Preguntas frecuentes

<details><summary><b>Al encender sale el menú de fábrica, no StradaOS</b></summary>
Fija StradaOS como inicio: <b>Ajustes → Sistema → Pantalla de inicio</b>. Si tu pantalla siempre vuelve a su menú al encender, activa ahí <b>«Abrir StradaOS al encender»</b>.
</details>

<details><summary><b>¿Por qué no sale la carátula con música por Bluetooth?</b></summary>
El Bluetooth entre el celular y la radio solo envía el título, el artista y el tiempo, no la imagen. La carátula sí sale con Android Auto y con las apps de música instaladas en la pantalla.
</details>

<details><summary><b>La música Bluetooth se corta o no suena</b></summary>
Sal de la app Bluetooth con <b>Inicio</b>, no con Atrás. Revisa <b>Ajustes → Música → Audio Bluetooth</b>; ahí también está la ayuda «¿La música Bluetooth se corta o no aparece?».
</details>

<details><summary><b>El mapa no carga o no aparece mi ubicación</b></summary>
El mapa en vivo necesita internet; con el mapa sin conexión no. Si dice «Sin señal GPS», revisa que la antena GPS de la radio esté conectada. Deja presionado el mapa para ver el diagnóstico.
</details>

<details><summary><b>¿Cómo dejo de usar StradaOS?</b></summary>
En <b>Ajustes → Sistema</b> puedes volver al menú de fábrica o desinstalarlo. Si la pantalla quedara en negro, reiníciala (botón <b>RES</b> o apaga y enciende el carro).
</details>

## 🔒 Privacidad

Sin cuentas, anuncios ni analíticas. Tu ubicación se usa solo en el equipo. Detalles en [PRIVACY.md](PRIVACY.md).

## ☕ Apoya el proyecto

StradaOS es y seguirá siendo gratis. Si te sirvió, dale una ⭐ a este repositorio o invítame un café en **[ko-fi.com/stradaos](https://ko-fi.com/stradaos)**. Reportes, ideas y traducciones son bienvenidos: lee [CONTRIBUTING.md](CONTRIBUTING.md).

<a href="https://github.com/lordnordic-debug/StradaOS/graphs/contributors"><img src="https://contrib.rocks/image?repo=lordnordic-debug/StradaOS"></a>

## 📄 Licencia y créditos

[GNU GPL v3.0](LICENSE). Mapas © [colaboradores de OpenStreetMap](https://www.openstreetmap.org/copyright), OpenTopoMap y Esri; mapa sin conexión con un lector adaptado de [mapsforge](https://github.com/mapsforge/mapsforge) (LGPL-3.0) y mapas de [download.mapsforge.org](https://download.mapsforge.org/). Clima: [Open-Meteo](https://open-meteo.com). Android Auto con [Open Headunit](https://github.com/andreknieriem/headunit-revived) (GPL-3.0). QR de [Project Nayuki](https://github.com/nayuki/QR-Code-generator) (MIT). Íconos [Material Symbols](https://fonts.google.com/icons) (Apache 2.0).

StradaOS es un proyecto independiente, no afiliado a Google, Apple ni a ningún fabricante. *Android Auto* es marca de Google LLC y *CarPlay* de Apple Inc. No lo manipules mientras conduces.

---

### English

**StradaOS** is a free, ad-free, open-source launcher for Android car head units (Android 4.1+, under 1 MB). It installs like any app, without flashing. Live and **offline maps** (download your country from the app), Android Auto and CarPlay buttons, a media card for Bluetooth and Android Auto, speedometer, weather, oil change reminder, a customizable layout (player left or right, compact or side bar), light/dark themes and 7 languages. [Website](https://lordnordic-debug.github.io/StradaOS/?lang=en) · [Download](https://github.com/lordnordic-debug/StradaOS/releases/latest)

### Português

O **StradaOS** é um launcher grátis, sem anúncios e de código aberto para centrais Android de carro (Android 4.1+). Mapa ao vivo e **offline**, Android Auto e CarPlay, música, velocímetro, clima e um início que você personaliza. [Site](https://lordnordic-debug.github.io/StradaOS/?lang=pt) · [Baixar](https://github.com/lordnordic-debug/StradaOS/releases/latest)
