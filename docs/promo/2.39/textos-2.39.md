# StradaOS 2.39 — textos para redes

Imágenes: `docs/promo/2.39/` (fb-es-1…7 = Facebook 4:5 · es-1…7 y en-1…7 = Reddit y X 16:9).

---

## Facebook — grupo «Claude Code en Español» (solo español)

Imágenes: fb-es-1 a fb-es-7, en ese orden (álbum).

> Les comparto cómo va StradaOS, el launcher que vengo programando con Claude Code para la pantalla Android de mi carro (un Picanto 2019 con radio china).
>
> Esas pantallas dicen «Android 10», pero por dentro muchas son Android 4.2 y casi nada moderno les funciona. Por eso StradaOS está hecho en Java puro, sin librerías, y corre desde Android 4.1 hasta 14.
>
> Lo nuevo de estas semanas:
> 🚗 Bienvenida al encender: el logo se ilumina y te saluda por tu nombre, con voz (se puede apagar)
> 🗺️ Navegación: escribes el destino y te guía giro a giro, también con mapas sin conexión
> 🎬 Deslizas el mapa y salen tus videos musicales; si el carro arranca, la imagen se tapa y el audio sigue
> 🔧 Escáner OBD-II con un ELM327: revoluciones junto al velocímetro, alertas y códigos de falla
> 🎵 Con internet, la carátula de la canción aparece sola
> 🎨 Temas premium: Faro, Carbono y el nuevo Premium Cards (y ya viene el cuarto)
>
> Lo que más me ha servido de Claude Code: le pido una mejora y antes de que yo la instale la prueba en emuladores de Android 4.1, 4.2, 6 y 14, con una prueba automática que recorre unas 110 pantallas. Después yo la pruebo en el carro y, si todo está bien, se publica.
>
> Es gratis, de código abierto (GPL-3.0), sin anuncios, sin root y está en 13 idiomas.
> 👉 github.com/lordnordic-debug/StradaOS
>
> Si tienen una de estas pantallas, cuéntenme la marca y el modelo, y les digo si les sirve 🙌

**Si lo publicas desde la cuenta de tu esposa**, cambia el comienzo por:
> Les comparto cómo va StradaOS, el launcher que mi esposo viene programando con Claude Code para la pantalla Android de nuestro carro (un Picanto 2019 con radio china).

…y en el párrafo de Claude Code: «Lo que más le ha servido…», «le pide una mejora…», «él la prueba en el carro». El cierre queda: «cuéntenme la marca y el modelo y le pregunto si les sirve 🙌».

---

## Reddit — inglés (r/coolgithubprojects, r/opensource, r/CarAV)

Imágenes: en-1 a en-7 (galería).

**Título:**
StradaOS v2.39: open-source launcher for cheap Android car head units, now with a boot greeting, turn-by-turn navigation and a map/video carousel

**Texto:**
> A few weeks ago I shared StradaOS here (v2.12) and the feedback was amazing. A lot of this update came from your comments and GitHub issues.
>
> Quick recap: many cheap Chinese head units say "Android 10" but secretly run Android 4.x. StradaOS is written in plain Java with no libraries, so it runs on Android 4.1 all the way up to 14.
>
> New since then:
> - **Boot welcome:** the logo lights up and greets you by name, out loud (you can turn it off)
> - **Built-in navigation:** search a place and get turn-by-turn directions, also with offline maps
> - **Map/video carousel:** swipe the map to play your saved music videos; once the car moves, the picture is covered and the audio keeps playing
> - **OBD-II scanner** (ELM327): RPM next to the speedometer, alerts and fault codes
> - **Automatic album art** when the unit has internet
> - **Premium themes:** Faro, Carbono and the new Premium Cards. The app stays free with no ads and no accounts; themes are an optional thank-you for a donation
>
> Free, GPL-3.0, no root, ~1.5 MB, 13 languages.
> Repo and signed APK: https://github.com/lordnordic-debug/StradaOS
>
> If you have one of these units, tell me the brand, model and real Android version (Settings → About in StradaOS) and I'll help you out.

### Variante para r/ClaudeAI (sin Ko-fi y explicando el uso de Claude)

**Título:**
Built with Claude Code: an open-source launcher for cheap Android car screens that still has to run on Android 4.1

**Texto:**
> My car has one of those cheap Android head units that says "Android 10" but actually runs Android 4.2. I wanted a modern home screen for it, so I've been building StradaOS with Claude Code. It's now at v2.39, free and GPL-3.0.
>
> How I use Claude Code on it:
> - **Hard constraint:** everything must work on Android 4.1 (2012). Claude writes plain Java with no libraries and keeps newer APIs behind version checks in separate classes, because touching an Android 4.3+ class from shared code crashes old devices.
> - **Testing before I touch it:** for every change, Claude runs a GitHub Actions regression that walks about 110 screens and dialogs on Android 4.1, 4.2, 6 and 14 emulators and checks the screenshots. Only then do I install the test build in my car.
> - **Releases:** I approve, Claude bumps the version and CI builds and signs the APK and publishes the release.
> - **Users:** Claude drafts the replies to issues and Reddit comments; I read them and post them myself.
>
> Latest features: a boot greeting by voice, turn-by-turn navigation with offline maps, a map/video carousel that covers the video while driving, an OBD-II scanner and new themes.
>
> Repo: https://github.com/lordnordic-debug/StradaOS
> Happy to answer questions about the workflow.

---

## Reddit — español (r/programacion, r/Colombia)

Imágenes: es-1 a es-7 (galería).

**Título:**
StradaOS 2.39: mi launcher de código abierto para pantallas Android de carro, ahora con bienvenida por voz, navegación y videos

**Texto:**
> Hace un tiempo empecé StradaOS para la pantalla de mi carro: esas radios chinas que dicen «Android 10» y por dentro son Android 4. Está hecho en Java puro, sin librerías, y corre desde Android 4.1 hasta 14. Lo programo con Claude Code.
>
> Lo nuevo:
> - **Bienvenida al encender:** el logo se ilumina y te saluda por tu nombre, con voz (se puede apagar)
> - **Navegación:** buscas un lugar y te guía giro a giro, también con mapas sin conexión
> - **Carrusel mapa/video:** deslizas el mapa y salen tus videos musicales; si el carro arranca, la imagen se tapa y el audio sigue
> - **Escáner OBD-II** (ELM327): revoluciones junto al velocímetro, alertas y códigos de falla
> - **Carátulas automáticas** cuando la pantalla tiene internet
> - **Temas premium:** Faro, Carbono y el nuevo Premium Cards. La app sigue gratis, sin anuncios ni cuentas
>
> Gratis, GPL-3.0, sin root, ~1,5 MB, 13 idiomas.
> Repo y APK firmado: https://github.com/lordnordic-debug/StradaOS
>
> Si tienen una de estas pantallas, cuéntenme marca, modelo y la versión real de Android (Ajustes → Acerca de en StradaOS) y les ayudo.

---

## X / Twitter

Publica el primer tuit con 4 imágenes y responde a ese mismo tuit con el segundo (hilo).

### Inglés
**Tuit 1** (imágenes en-1, en-3, en-4, en-6):
> StradaOS v2.39 🚗 A free, open-source launcher for cheap Android car head units, even the ones that say "Android 10" but secretly run Android 4.
>
> New: a boot greeting by voice, turn-by-turn navigation, map/video carousel and premium themes.
>
> github.com/lordnordic-debug/StradaOS

**Tuit 2, respuesta** (imágenes en-2, en-5, en-7):
> Also inside: an OBD-II scanner (ELM327) with RPM next to the speedometer, automatic album art and 13 languages.
>
> Plain Java, no libraries, runs on Android 4.1 → 14. No ads, no root, no accounts. Built with Claude Code.

### Español
**Tuit 1** (imágenes es-1, es-3, es-4, es-6):
> StradaOS v2.39 🚗 Launcher gratis y de código abierto para pantallas Android de carro, incluso las baratas que dicen «Android 10» y por dentro son Android 4.
>
> Nuevo: saludo por voz al encender, navegación, carrusel mapa/video y temas premium.
>
> github.com/lordnordic-debug/StradaOS

**Tuit 2, respuesta** (imágenes es-2, es-5, es-7):
> Además: escáner OBD-II (ELM327) con revoluciones junto al velocímetro, carátulas automáticas y 13 idiomas.
>
> Java puro, sin librerías, corre de Android 4.1 a 14. Sin anuncios, sin root, sin cuentas. Hecho con Claude Code 🇨🇴

---

## Horarios sugeridos (hora de Colombia)

Colombia está en la misma hora que la costa este de EE. UU. en esta época, así que los horarios de EE. UU. son los mismos.

| Red | Mejor momento | Por qué |
|---|---|---|
| Facebook (grupo) | Martes a jueves, 7–9 p. m. · o sábado 9–11 a. m. | Es cuando la gente de Latinoamérica revisa grupos con calma |
| Reddit inglés | Martes a jueves, 7–9 a. m. | Así le queda todo el día a EE. UU. y Europa para votar |
| Reddit español | Martes a jueves, 7–9 p. m. | Público latino después del trabajo |
| X inglés | Martes a jueves, 9–11 a. m. | Hora de más actividad en EE. UU. |
| X español | Martes a jueves, 12–1 p. m. o 7–9 p. m. | Almuerzo y noche en Latinoamérica |

Son reglas generales, no datos de tu cuenta. Orden sugerido para no publicar todo el mismo día: Reddit inglés + X inglés en la mañana de un día, y Facebook + Reddit español + X español en la noche del día siguiente. Este viernes y el fin de semana están llenos de anuncios de Black Friday, así que mejor empezar el martes 1 de diciembre. En Reddit, quédate la primera hora contestando comentarios: eso es lo que más empuja el post.
