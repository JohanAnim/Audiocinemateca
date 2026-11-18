# Changelog

Todos los cambios notables de **Audiocinemateca** se documentan en este archivo.
El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y las versiones siguen [Semantic Versioning](https://semver.org/).

## [3.4.0-beta.1]

¡Hola a toda la comunidad! Les presento la primera **Beta pública de Audiocinemateca 3.4.0 (`3.4.0-beta.1`)**, el salto técnico, visual y de accesibilidad más grande que ha recibido la aplicación desde su creación.

Esta versión marca la **migración completa a Jetpack Compose 100% nativo**, una base de datos de catálogo muchísimo más rápida en **SQLite (Room + FTS4)**, la llegada del nuevo **logotipo oficial**, el **avatar anime de Aura** y un sinfín de mejoras pensadas de raíz para disfrutarse tanto visualmente como con **TalkBack**, **Jieshuo** y mando **PS5 DualSense**.

Aquí tienen todo lo que trae esta Beta:

---

### 🎨 1. Nueva Identidad Visual, Menú Lateral Vivo y Avatar de Aura
* **Logotipo oficial de Audiocinemateca:** Reemplazamos el ícono genérico por un emblema propio de cine audiodescrito (audífonos, ondas sonoras y pantalla de reproducción en tonos índigo y cian), integrado en el launcher adaptativo, ícono monocromático para Android 13+, pantalla de bienvenida, inicio de sesión y notificaciones del sistema.
* **Avatar estilo anime para Aura:** Nuestra compañera inteligente estrena su propio retrato en el cajón lateral, su pantalla de bienvenida, su barra de estado y cada burbuja del chat.
* **Menú lateral completamente rediseñado:** Encabezado limpio sin correo visible, título prominente, descripción profesional accesible de la insignia, saludo personal según la hora del día, reloj en tiempo real con tu ciudad y país detectados por zona horaria, y un **cielo animado** que cambia entre **amanecer, mañana, mediodía, tarde, atardecer, noche y madrugada**.
* **Mensajes sorpresa de Aura en el menú:** Si tienes configurada tu clave de IA, Aura te sorprende cada vez que abres el cajón lateral con uno de **35 mensajes ingeniosos** adaptados al momento exacto del día.

---

### ✨ 2. Aura 2.0: Multi-Proveedor, Gemini 3.8 TTS y Memoria Completa
* **Groq y Google Gemini bajo un mismo techo:** Cambia de proveedor o de modelo directamente desde la barra superior del chat o en Ajustes de IA, con soporte completo para los modelos **Gemma 4** (`gemma-4-26b-a4b-it`, `gemma-4-31b-it`), **GPT-OSS 120B** y gestión inteligente del límite de tokens por minuto en Groq.
* **Voz expresiva con Gemini 3.8 TTS:** Además de la síntesis de voz nativa del dispositivo (con ajustes independientes por motor, idioma y voz), ahora puedes escuchar a Aura con voces expresivas y naturales, conservando tu configuración de voz intacta incluso al cambiar de modelo o proveedor de texto.
* **Controles en cada respuesta e historial persistente:** Tus chats se guardan en disco con títulos automáticos generados por IA; puedes reproducir, pausar, repetir desde el inicio, copiar, compartir o regenerar cualquier mensaje, además de usar dictado por voz **pulsar para hablar** (*push-to-talk*).

---

### 🎬 3. Reproductor Renovado, CinemaHaptics y Mando PS5 DualSense
* **Dos diseños en pantalla completa:** Escoge en Ajustes de Reproducción entre el diseño clásico y el diseño alternativo estilo YouTube.
* **Comodidad total al escuchar:** Nuevo salto configurable de **60 segundos (1 minuto)**, **sidechain automático** (baja suavemente el volumen del contenido cuando habla Aura o el lector), subtítulos claros de episodios (`T1:E1 - Título`) y ficha de clasificación orientativa.
* **Motor háptico CinemaHaptics y PS5 DualSense:** Vibración sincronizada en tiempo real con escenas de acción/sonido y navegación 2D completa por paneles y atajos directos usando un mando DualSense o gamepad compatible.

---

### 🔍 4. Catálogo Ultrarrápido en Room (FTS4), Filtros y Centro de Documentación
* **Migración completa a SQLite + FTS4:** Búsquedas instantáneas sin congelamientos, ordenamiento cronológico inteligente de sagas y soporte para filtros combinados (incluyendo peticiones complejas desde Aura).
* **Nuevos filtros y detalles en listas:** Filtros por **Narrador** (ajustados automáticamente al idioma elegido) y **Proveedor/Productora**, junto con la fecha exacta de agregado en **Favoritos**.
* **Centro de Documentación y Preguntas Frecuentes:** Nueva sección integrada en Cuenta con guías accesibles paso a paso y un apartado completo de **Preguntas frecuentes**.
* **Ficha técnica estilo Netflix y lectura inmediata:** Sinopsis interactiva con lectura/pausa instantánea por voz y pestañas optimizadas para **TalkBack** y **Jieshuo**.

---

### ⚡ 5. Rendimiento de Arranque, Canales de Actualización y Aviso Rumbo a v3.5.0
* **Arranque en frío mucho más ligero:** Desbloqueamos el hilo principal al iniciar la app moviendo a segundo plano la inicialización de Google Cast, TTS, efectos `SoundPool`, audio de bienvenida, WorkManager y comprobaciones remotas.
* **Canales Estable y Beta:** Elige en **Ajustes → General → Canal de actualizaciones** si deseas recibir únicamente lanzamientos oficiales estables o probar anticipadamente las compilaciones Beta (con comparador SemVer completo).
* **Preparación para nuestro propio servidor (v3.5.0+):** Incorporamos un sistema preventivo de avisos para que, cuando desconectemos el servidor actual en Firebase y migremos a nuestra propia infraestructura en la **v3.5.0**, recibas notificaciones claras y acceso directo a la actualización sin perder nunca tu catálogo local, descargas, reproductor ni a Aura.

---

> **Nota de esta versión Beta:** Algunas novedades siguen en etapa de prueba y ajuste fino antes del lanzamiento estable. Si notas algún detalle o tienes sugerencias, compártelo en la comunidad para seguir puliendo Audiocinemateca juntos. ¡Muchas gracias por acompañarnos en esta nueva etapa!

## [3.3.2]

¡Hola a todos! Les doy la bienvenida a la **versión 3.3.2** de Audiocinemateca. En esta pequeña actualización me enfoqué en pulir la estabilidad, la comodidad en tu día a día y resolver detalles importantes que me reportaron para que disfrutes de tus películas y series sin ninguna interrupción.

Aquí te comparto todo lo que preparé y corregí para ti:

---

## 📞 1. Pausa Automática al Recibir Llamadas y Mayor Comodidad de Audio
*   **Pausa inteligente ante llamadas:** Ahora, cuando entre una llamada telefónica (ya sea una llamada normal, de WhatsApp o cualquier otra app), la película o serie que estés escuchando se pausará por completo de forma automática. Así nunca te perderás ni un segundo del diálogo o la trama. En cuanto cuelgues, la reproducción se reanudará sola en el mismo punto donde se quedó.
*   **Protección al desconectar auriculares:** Si desconectas tus audífonos con cable o se apagan tus audífonos Bluetooth mientras escuchas una obra, la app detendrá el audio al instante para evitar que empiece a sonar por los altavoces de tu teléfono.
*   **Cero permisos invasivos:** Todo funciona de forma totalmente nativa respetando al 100% tu privacidad, sin pedirte permisos de teléfono ni acceder a tus contactos.

---

## 📺 2. Estabilidad Total en Google Cast (Smart TV, Chromecast y Bocinas)
*   **Adiós a las desconexiones a los 10 o 20 minutos:** Corregí de raíz el problema que provocaba que la transmisión a tu Smart TV, Chromecast o bocinas Google Home / Nest se cerrara sola con la pantalla apagada (especialmente en Android 14 y versiones superiores). Ahora la conexión se mantiene firme, fluida y estable de forma continua hasta que tú decidas desconectarte a mano.
*   **Pausa en Cast durante llamadas:** Si estás escuchando audio a través de tu bocina o televisor y recibes una llamada, la app también pausará el dispositivo remoto de inmediato y lo reanudará al finalizar la llamada.
*   **Controles claros y sin duplicados:** Centralicé la notificación multimedia para que tengas controles accesibles y directos en tu pantalla de bloqueo sin avisos repetidos.

---

## 🔄 3. Actualizador de la Aplicación y Feedback Claro
*   **Comprobación al iniciar:** Si tienes activada la opción en ajustes, al abrir la aplicación te avisará con un diálogo claro si hay una nueva versión disponible para que puedas descargarla con un solo toque o dejarla para más tarde.
*   **Botón 'Actualizar' en tu Cuenta:** En la pestaña Cuenta agregué un botón accesible junto a la versión. Al pulsarlo, la app siempre te informará con claridad: si ya cuentas con la versión más reciente, te lo confirmará amablemente; y si hay una nueva, te ofrecerá actualizarla de inmediato.

---

## ⚡ 4. Sincronización y Mejoras de Rendimiento
*   **Catálogo siempre al día:** Optimicé la sincronización para que los nuevos contenidos que vayamos agregando aparezcan de forma automática en tu aplicación.
*   **Votaciones fluidas:** Corregí los botones de Me Gusta y No Me Gusta para que respondan con máxima rapidez y TalkBack te anuncie tu voto de forma inmediata y sin trabas.

---

¡Muchísimas gracias por tu apoyo constante y por disfrutar de Audiocinemateca! Espero que esta versión 3.3.2 te brinde una experiencia aún más cómoda y placentera.
