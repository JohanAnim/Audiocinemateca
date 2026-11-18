# Changelog

Todos los cambios notables de **Audiocinemateca** se documentan en este archivo.
El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y las versiones siguen [Semantic Versioning](https://semver.org/).

## [3.4.0-beta.2]

¡Hola a todos! Aquí tienen la **Beta 2 de Audiocinemateca 3.4.0 (`3.4.0-beta.2`)**, una actualización rápida pero clave basada en los primeros reportes de la comunidad, además de permitirles probar el nuevo **actualizador e instalador integrado** directamente desde `3.4.0-beta.1`.

### 🛠️ Correcciones y mejoras en esta Beta 2
* **Descarga y guardado del catálogo blindados:** Corregimos el error `Parameter specified as non-null is null (parameter partes)` que impedía descargar o guardar el catálogo en algunos dispositivos cuando una obra del servidor llegaba con `partes` u otros metadatos vacíos/nulos. Ahora todos los campos se normalizan de forma segura en SQLite.
* **Prueba en vivo del actualizador automático:** Quienes ya instalaron `3.4.0-beta.1` pueden actualizar directo desde el diálogo automático al iniciar la app o desde **Cuenta → Buscar actualizaciones**, verificando también el nuevo diálogo de *Novedades de la versión* con formato limpio.

---

> Incluye todas las mejoras masivas de **Jetpack Compose**, **Aura 2.0**, el **nuevo logotipo**, el **menú lateral animado** y el **arranque optimizado** estrenados en `3.4.0-beta.1`.

## [3.4.0-beta.1]

¡Hola a toda la familia de **Audiocinemateca**! Hoy es un día muy especial: les presento la **Beta pública de Audiocinemateca 3.4.0 (`3.4.0-beta.1`)**, **una de las versiones más masivas e importantes que ha recibido Audiocinemateca desde que se creó para Android**.

Este lanzamiento marca un **paso gigantesco** hacia nuestra gran meta: ofrecerles una aplicación **más moderna, más linda visualmente, accesible de punta a punta y, por supuesto, la más poderosa técnicamente** que hemos construido hasta hoy.

Sería imposible mencionar aquí cada mejora, optimización y detalle que trabajamos para esta versión porque la lista sería enorme. Por eso, aquí abajo reuní **por categorías los cambios más importantes y relevantes**. Y si quieres explorar las notas completas con todos los detalles y guías paso a paso, entra dentro de la app al nuevo apartado de **Documentación → Novedades de esta versión**, donde iré dejando todo registrado al detalle.

Aquí tienen lo más destacado de esta Beta:

---

### 🎨 1. Nueva Identidad Visual, Menú Lateral Vivo y Avatar de Aura
* **Logotipo oficial de Audiocinemateca:** Despedimos el ícono genérico de Android para estrenar un emblema propio de cine audiodescrito en azul noche, índigo y cian, integrado en el launcher adaptativo, íconos monocromáticos de Android 13+, pantalla de bienvenida, inicio de sesión y notificaciones del sistema.
* **Avatar estilo anime para Aura:** Nuestra compañera inteligente estrena un **retrato propio amigable y expresivo** que la acompaña en el cajón lateral, su pantalla de bienvenida, la barra de estado y cada burbuja del chat.
* **Menú lateral completamente rediseñado:** Eliminamos el correo electrónico para lograr una vista más limpia, con el título **Audiocinemateca** en grande, nuestra insignia con descripción accesible profesional, saludo según tu nombre y hora del día, reloj en vivo con tu ciudad y país detectados por zona horaria, versión + canal visible y un **cielo animado** que evoluciona entre amanecer, mañana, mediodía, tarde, atardecer, noche y madrugada.
* **Sorpresas de Aura al abrir el menú:** Si tienes configurada tu clave de IA, Aura te recibe en cada apertura con una frase ingeniosa y distinta pensada justo para el momento del día.

---

### ✨ 2. Aura 2.0: Mucho Más Expresiva, Inteligente y Memoriosa
* **Groq y Google Gemini bajo un mismo techo:** Cambia libremente de proveedor y modelo desde la barra superior del chat o Ajustes de IA, con compatibilidad total para **Gemma 4** (`gemma-4-26b-a4b-it`, `gemma-4-31b-it`), **GPT-OSS 120B** y optimización automática del límite de tokens por minuto en Groq.
* **Voz expresiva con Gemini 3.8 TTS:** Escucha a Aura con matices naturales y emotivos además de las voces del teléfono (con configuración independiente por motor, idioma y voz). Tu elección de voz y modelo TTS se mantiene intacta incluso al cambiar de proveedor o modelo de texto.
* **Memoria completa y control en cada mensaje:** Conversaciones guardadas en disco con títulos inteligentes, controles para reproducir, pausar, escuchar desde el inicio, copiar, compartir o regenerar cada respuesta, dictado por voz **pulsar para hablar**, búsquedas por géneros combinados y estadísticas exactas de tu biblioteca y descargas.

---

### 🎧 3. Reproductor Renovado, CinemaHaptics y Mayor Comodidad
* **Dos diseños en pantalla completa:** Escoge en Ajustes de Reproducción entre el diseño clásico y el diseño alternativo estilo YouTube, con subtítulos claros de capítulo (`T1:E1 - Título`) y ficha de clasificación orientativa profesional.
* **Escucha sin interrupciones ni sobresaltos:** Nuevo intervalo de salto de **60 segundos (1 minuto)** y **sidechain automático** que baja suavemente el volumen del contenido cuando habla Aura o el lector y lo restaura al terminar.
* **CinemaHaptics:** Vibración cinematográfica en tiempo real sincronizada con el audio de tus películas y series.

---

### ⚡ 4. 100% Jetpack Compose Nativo, SQLite FTS4 y Accesibilidad de Punta a Punta
* **Arquitectura moderna de extremo a extremo:** Migramos los módulos clave a **Jetpack Compose nativo** eliminando vistas y layouts XML heredados, con foco inicial instantáneo, restauración exacta de posición al volver atrás y navegación impecable para **TalkBack** y **Jieshuo**.
* **Catálogo ultrarrápido y nuevos filtros:** Base de datos en **Room (SQLite + FTS4)** con búsqueda instantánea, ordenamiento cronológico de sagas, fecha exacta en **Favoritos** (`se agregó el...`) y nuevos filtros por **Narrador** (adaptado al idioma elegido) y **Proveedor / Productora**.
* **Ficha Netflix, Centro de Documentación y arranque ligero:** Sinopsis con lectura/pausa inmediata por voz, nueva sección de **Documentación** integrada (con **Preguntas frecuentes**), y optimización profunda del arranque moviendo Cast, TTS, sonidos y comprobaciones de red fuera del hilo principal.

---

### 🔄 5. Canales de Actualización y Camino al Nuevo Servidor (v3.5.0+)
* **Canales Estable y Beta:** Elige en **Ajustes → General → Canal de actualizaciones** si prefieres solo lanzamientos estables o acceso anticipado a Betas, con comparador SemVer completo.
* **Preparación para nuestro servidor propio (`v3.5.0+`):** Dejamos listo un sistema de avisos claro y accesible para informar cuando desconectemos Firebase y estrenemos nuestro propio servidor a partir de la versión **3.5.0**, garantizando que tu catálogo local, descargas, reproductor y Aura sigan funcionando siempre.

---

> **Nota sobre esta Beta:** Como versión anticipada previa al lanzamiento estable, es el espacio ideal para probar estas novedades a fondo. Si notas cualquier detalle, cuéntanos en la comunidad para seguir puliendo Audiocinemateca entre todos. ¡Gracias por vivir este momento histórico con nosotros!

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
