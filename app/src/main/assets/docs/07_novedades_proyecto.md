# Novedades de esta versión (3.4.0-beta.1)

¡Bienvenido al registro completo de novedades de **Audiocinemateca 3.4.0 Beta 1**! Esta es una de las actualizaciones más grandes, modernas, lindas y técnicamente sólidas que ha recibido la aplicación en Android. Aquí tienes desglosado todo lo que cambia para ti:

---

## 1. Nueva identidad visual, avatar de Aura y menú lateral vivo
* **Logotipo oficial de Audiocinemateca:** Nuevo diseño propio de audífonos con ondas sonoras y pantalla de reproducción en tonos índigo y cian, presente en el ícono adaptativo, versión monocromática de Android 13+, pantalla de bienvenida, inicio de sesión, menú lateral e ícono vectorial limpio en notificaciones.
* **Avatar estilo anime para Aura:** Aura estrena su retrato oficial en el cajón lateral, su pantalla de bienvenida, su barra de estado y cada burbuja de respuesta en el chat.
* **Menú lateral rediseñado y dinámico:**
  * Se eliminó el correo electrónico para una presentación más limpia.
  * Encabezado ordenado con título grande **Audiocinemateca**, logotipo insignia con descripción accesible profesional, saludo según tu nombre y etapa del día, hora actual con tu ciudad y país detectados desde tu zona horaria, versión y canal visible (**Lanzamiento oficial**, **Canal Beta** o **Versión Beta**).
  * **Cielo animado según la hora:** Cambia visualmente entre amanecer, mañana, mediodía, tarde, atardecer, noche y madrugada con sol, nubes, luna creciente y estrellas centelleantes.
  * **35 mensajes sorpresa de Aura:** Cuando tienes configurada tu clave de IA, Aura muestra en cada apertura del cajón un mensaje ingenioso acorde al momento del día.
  * **Blindaje de accesibilidad:** Evita filtraciones de botones de fondo como Transmitir mientras navegas por el menú lateral y elimina lecturas duplicadas al hacer flicks.

---

## 2. Arquitectura 100% Jetpack Compose y Accesibilidad de Punta a Punta
* **Migración nativa completa:** Inicio, Explorar, Mis Listas (Historial, Favoritos y Descargas), Comunidad (Chat Global, Anuncios y Comentarios), Chat con Aura, Notificaciones, Detalles, Reproductor y todos los Ajustes funcionan íntegramente en Jetpack Compose nativo sin fragmentos ni layouts XML heredados.
* **Foco instantáneo y restauración exacta:** Al abrir cada pantalla, el reproductor, las categorías de ajustes o el cajón lateral, TalkBack y Jieshuo se posicionan automáticamente en el elemento principal y recuerdan exactamente dónde estabas al regresar.
* **Navegación más limpia en listas y comunidad:** Encabezados de fecha nativos para salto por rotor, estados de expansión claros en descargas por serie, citas estilo Telegram con salto al mensaje original, menciones directas en el chat global y ajuste opcional para ocultar o mostrar consejos contextuales de interacción.

---

## 3. Catálogo SQLite (Room + FTS4), Ficha Netflix y Nuevos Filtros
* **Base de datos ultrarrápida:** Todo el catálogo está indexado en SQLite con búsqueda FTS4 instantánea, priorización inteligente por palabras exactas, ordenamiento cronológico de sagas y persistencia de la pestaña y filtros al volver de ver una ficha.
* **Nuevos filtros avanzados:** Ahora puedes filtrar el catálogo por **Narrador** (actualizado automáticamente según el idioma seleccionado) y por **Proveedor / Productora**, además de género, país, idioma y criterio de ordenamiento.
* **Favoritos y Detalles enriquecidos:** Cada tarjeta de Favoritos muestra el tipo de obra y la fecha exacta en que la agregaste (`se agregó el...`). En los detalles cuentas con cabecera estilo Netflix, metadatos clave y sinopsis interactiva con lectura o pausa inmediata por voz.

---

## 4. Aura 2.0: Groq, Gemini, Gemma 4 y Gemini 3.8 TTS Expresivo
* **Multi-proveedor en un clic:** Cambia entre **Groq** (con soporte optimizado de presupuesto de tokens y GPT-OSS 120B) y **Google Gemini** (con soporte nativo completo para **Gemma 4**, Gemini 2.5 y familia 3.x) desde la barra superior del chat o Ajustes de IA.
* **Voz expresiva Gemini 3.8 TTS:** Respuestas narradas con matices naturales, preservando siempre el modelo y la voz TTS que elegiste aunque cambies de proveedor o modelo de texto.
* **Herramientas y memoria completa:** Aura conserva tus conversaciones en disco con títulos automáticos, permite reproducir o pausar cada respuesta desde el punto exacto o desde el inicio, dictar por voz manteniendo pulsado el botón (*push-to-talk*), consultar estadísticas reales de tu biblioteca/descargas y reproducir contenidos directamente con un solo toque.

---

## 5. Reproductor Flexible, CinemaHaptics y Arranque Ligero
* **Dos diseños en pantalla completa:** Alterna en Ajustes de Reproducción entre el diseño clásico y el alternativo estilo YouTube.
* **Mayor confort al reproducir:** Nuevo salto de **60 segundos (1 minuto)**, **sidechain automático** (atenúa suavemente la obra cuando habla Aura o el lector), subtítulos de capítulo en series (`T1:E1 - Título`) y clasificación orientativa profesional.
* **CinemaHaptics:** Retroalimentación háptica cinematográfica en tiempo real sincronizada con la intensidad y ritmo del audio.
* **Arranque en frío optimizado:** Inicialización diferida y en segundo plano de Google Cast, Android TTS, carga de sonidos `.ogg`, audio de bienvenida, WorkManager y sincronización remota para que abrir la app sea mucho más ágil y fluido desde el primer segundo.

---

## 6. Documentación Integrada, Canales y Preparación Rumbo a v3.5.0
* **Centro de Documentación y Preguntas Frecuentes:** Nueva sección completa dentro de **Cuenta → Documentación** con guías paso a paso y respuestas a las dudas más comunes sobre accesibilidad, Aura, API Keys, reproductor y funcionamiento offline.
* **Canales de actualización Estable y Beta:** Elige en **Ajustes → General → Canal de actualizaciones** si prefieres solo lanzamientos estables o betas anticipadas, con comparador SemVer 2.0 y descarga directa dentro de la app.
* **Sistema de aviso de migración (`v3.5.0+`):** Preparación preventiva para avisar de forma clara y accesible cuando apaguemos Firebase y estrenemos nuestro servidor propio a partir de la versión **3.5.0**, asegurando en todo momento la continuidad de tu catálogo local, tus descargas, el reproductor y Aura.
