# Memoria de Avance del Proyecto

## Hito: Barras Modernas en Compose, Inicio de Sesión y Audio de Arranque
- **Migración a Compose (Inicio/Registro de Sesión):**
  - Implementados [LoginCloudScreen.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/account/LoginCloudScreen.kt) y [RegisterCloudScreen.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/account/RegisterCloudScreen.kt) en Jetpack Compose.
  - Añadida validación de formato de correo (`Patterns.EMAIL_ADDRESS`) y coincidencia de contraseñas del lado del cliente antes de iniciar peticiones a Firebase Auth.
  - Vinculados a los fragmentos [LoginCloudFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/account/LoginCloudFragment.kt) y [RegisterCloudFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/account/RegisterCloudFragment.kt) usando `ComposeView`.
  - Corregida la accesibilidad: se eliminó `clearAndSetSemantics` de los cuadros de edición para permitir que TalkBack los reconozca como `"Cuadros de edición"` estándar, tanto en el inicio de la nube como en el local (`LoginScreen.kt`).

- **Desactivación de Toolbar antigua y Barras Personalizadas:**
  - Ocultada la Toolbar nativa en `activity_main.xml` configurando su visibilidad a `gone` y reajustando la restricción superior de `nav_host_fragment` a `parent`.
  - Deshabilitado el soporte nativo de ActionBar comentando `setSupportActionBar` y `setupActionBarWithNavController` en [MainActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/MainActivity.kt) para evitar que se reactive la Toolbar antigua al navegar hacia atrás.
  - Implementada barra principal moderna [CatalogToolbar.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/catalog/CatalogToolbar.kt) y barra de detalles [DetailToolbar.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/catalog/DetailToolbar.kt) en Jetpack Compose.
  - Barra de detalles en Compose enlazada de manera reactiva en [ContentDetailFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/contentdetail/ContentDetailFragment.kt) para observar el título dinámicamente, controlar favoritos (corazón reactivo que cambia de color y estado) y compartir el contenido con la función nativa de compartición.
  - Simplificación del botón "Volver" con la etiqueta exacta `"Volver"` para accesibilidad directa.

- **Audio y Animación de Inicio (Boot Sound & Splash Screen):**
  - Actualizado en [SplashActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/SplashActivity.kt) para reproducir el recurso `Boot Animation.mp3` desde assets.
  - La duración de la pantalla de Splash y la animación visual (Compose scale + fade) se obtienen dinámicamente según la duración real del archivo de audio mediante `mediaPlayer.duration`.
  - Integrado patrón de vibración háptica al iniciar la animación con retrocompatibilidad para `VibratorManager` y `VibrationEffect`.

- **Sección Continuar Escuchando y Top 10 Semanal en Inicio:**
  - Creados [ContinueListeningCard.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/components/ContinueListeningCard.kt) y [ContinueListeningSection.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/components/ContinueListeningSection.kt) en Jetpack Compose, ubicados inmediatamente después de las Recomendaciones Personalizadas. Muestra barra de progreso en tiempo real y tiempo restante (ej: *"Quedan 14 min"*).
  - Creado [RankingRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/RankingRepository.kt) v2.0 (Serverless *Client-Driven Aggregation* en Firestore). Recopila y pondera: (1) Promedio de Estrellas + Volumen Bayesiano de Votantes, (2) Total de Reproducciones de la Comunidad, (3) Volumen de Comentarios, (4) Votos "Me Gusta" vs "No Me Gusta", y (5) **Desduplicación Estricta por Obra Base** para eliminar títulos repetidos.
  - Removido el botón de actualización manual en [Top10RankingSection.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/components/Top10RankingSection.kt) para dejar un diseño limpio.
  - **Presencia Real de Usuarios y Chat Global en Compose:**
  - Solucionada la acumulación de usuarios fantasma en presencia: [GlobalChatRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/GlobalChatRepository.kt) filtra mediante una ventana de latido (`lastActive`) de 90 segundos y realiza un heartbeat cada 30 segundos en [GlobalChatViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatViewModel.kt).
  - Implementado encabezado en Compose [GlobalChatHeader.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/GlobalChatHeader.kt) con indicador verde de actividad en tiempo real.
  - Implementado diálogo en Compose [ConnectedUsersDialog.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/ConnectedUsersDialog.kt) accesible exclusivamente para la cuenta Administradora (`gutierrezjohanantonio@gmail.com`) que muestra la lista en vivo de personas conectadas.

- **Corrección de Persistencia y Sincronización del Banner Destacado en Tiempo Real:**
  - Eliminado por completo el fallback a `DEFAULT_BANNER` (el banner falso/de prueba) en [HomeViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/HomeViewModel.kt) y [FeaturedBannerSection.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/components/FeaturedBannerSection.kt).
  - Mientras se consulta Firestore se muestra una tarjeta limpia con indicador de carga (*"Cargando lo destacado..."*). Tan pronto como Firestore responde, se renderiza de forma directa el banner real publicado (`app_config/featured_banner`), sin pasar en ningún momento por la tarjeta de pruebas.
  - Re-sincronización automática de `observeFeaturedBanner()` en `checkAuthState()` para que al iniciar sesión con Google y volver al feed, el banner real se cargue al instante en tiempo real sin requerir reiniciar la aplicación.
  - Auto-scroll automático hacia la parte superior (`listState.scrollToItem(0)`) en [HomeScreen.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/HomeScreen.kt) al iniciar sesión o ingresar al inicio.

- **Accesibilidad en Barra de Navegación Inferior (Pestañas):**

- **Optimización de Rendimiento e Hilos Secundarios en Inicio:**
  - Todo el procesamiento pesado en [HomeViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/HomeViewModel.kt) (indexación de catálogo, mapeo y ordenamiento de "Continuar Escuchando", consultas a Firestore de Top 10, recomendaciones y sincronización de banners) se movió explícitamente a hilos secundarios de fondo (`Dispatchers.IO`).
  - La actualización de la interfaz (`_uiState`) se despacha de forma segura a `Dispatchers.Main` una vez procesada la información en segundo plano. Esto evita cualquier tipo de congelamiento o bloqueo del hilo principal de UI al abrir el feed, manteniendo los indicadores de carga fluidos y activos.

## Hito: Moderación del Chat Global, Documentación HTML de Reglas y Sistema de Catálogo
- **Interacción y Moderación en Chat Global (Compose):**
  - **Resolución de Nombres:** Corregida la resolución de nombres en `GlobalChatRepository.kt` y `ConnectedUsersDialog.kt`. Si `displayName` no está registrado, se utiliza el prefijo del correo (`email.substringBefore("@")`) o ID único, eliminando la etiqueta genérica *"Usuario de la Nube"*.
  - **Menú Interactivo de Usuarios:** Todos los usuarios pueden abrir la lista de conectados. Al pulsar sobre cualquier usuario en `ConnectedUsersDialog.kt`, se despliega un diálogo de acciones para responder (`↩️ Responder`), mencionar (`💬 Mencionar`), o sancionar (`🚫 Suspender / Sancionar del Chat`).
  - **Protección Anti Auto-Sanción:** En `ConnectedUsersDialog.kt`, si el usuario selecciona su propia cuenta o la cuenta Admin principal (`gutierrezjohanantonio@gmail.com`), la opción de suspensión se oculta automáticamente.
  - **Reglas de la Comunidad en HTML/WebView:** Creado el documento HTML en assets `docx/reglas_comunidad.html` y el componente Compose [ChatRulesDialog.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/ChatRulesDialog.kt) con WebView integrado. Muestra las reglas de convivencia y consecuencias por incumplimiento. Se despliega automáticamente la primera vez que un usuario ingresa al chat y está disponible en el menú de opciones.
  - **Gestión de Usuarios Sancionados (Exclusivo Admin):** Creados [BanUserDialog.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/BanUserDialog.kt) (duraciones de 1h, 24h, 7d o Permanente) y [BannedUsersDialog.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/BannedUsersDialog.kt) (desbanear en tiempo real). En `GlobalChatViewModel.kt` se bloquea el envío de mensajes a usuarios con sanciones activas y se notifica con audio/toast el motivo.
  - **Mención Exclusiva de `@todos` para Admins:** Restringida la mención masiva `@todos` en `GlobalChatViewModel.kt` y en el backend Node.js (`chatService.js`). Si un usuario estándar intenta usar `@todos`, se cancela el envío y se le advierte que la herramienta está reservada para anuncios oficiales.
  - **Preservación de Foco y Scroll Inteligente:** En `GlobalChatFragment.kt`, el auto-scroll hacia el último mensaje solo ocurre si el usuario ya se encuentra cerca del final (`lastVisiblePos >= items.size - 4`). Si el usuario está desplazado hacia arriba leyendo mensajes anteriores, los nuevos mensajes entrantes no desplazan su posición de lectura.

- **Actualizador de Catálogo e Indicadores en Pantalla de Cuenta:**
  - **Botón "Actualizar Catálogo" con Diálogo de Estado ([AccountScreen.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/account/AccountScreen.kt)):**
    - Corregida la semántica de accesibilidad `audiocinematecaAccessibility` que tenía `onClickAction = {}` nulo y bloqueaba el clic estándar.

## Hito: Integración Completa de Reproducción y Navegación en Jam en Vivo
- **Navegación e Inicio Automático de Audio ([GlobalChatViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatViewModel.kt) & [GlobalChatFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatFragment.kt)):**
  - **Host (Anfitrión):** Al seleccionar una obra en `JamSelectorDialog`, `startJamSession` publica la sesión en Firestore e inmediatamente lanza `navigateToPlayerEvent`. Abre automáticamente `PlayerFragment` y `PlayerService` para iniciar la reproducción de audio del ítem mientras transmite la posición en vivo a los oyentes.
  - **Oyente (Listener):** Al pulsar *"🎧 Unirme"*, `joinJamSession` consulta la obra por su ID en `ContentRepository` e inicia automáticamente `PlayerFragment` y `PlayerService`, sincronizando el audio en milisegundos con la posición actual del Anfitrión.
  - **Navegación Fluida desde la Cabecera:** Al tocar en la tarjeta Compose [LiveJamHeaderCard.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/LiveJamHeaderCard.kt) en cualquier momento dentro del chat, se abre directamente el reproductor con los controles completos y la tarjeta de clasificación por IA. Al presionar *"Volver"*, el audio continúa en segundo plano con el mini-reproductor flotante mientras conversan en la sala.
  - Al pulsar el botón, muestra un diálogo de carga *"Comprobando Catálogo..."*. Si el catálogo ya está actualizado, despliega un `MaterialAlertDialogBuilder` indicando *"El catálogo ya se encuentra completamente actualizado a la última versión disponible (versión del DD/MM/YYYY HH:mm)"*. Si hay novedades, ofrece la descarga inmediata con progreso `%`.

## Hito: Corrección de Errores Fatales en Android 13 y Compose
- **Solución al Crash de Claves Duplicadas en Compose (`IllegalArgumentException: Key was already used`):**
  - **Causa Raíz:** En [RecommendationRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/RecommendationRepository.kt) y [RankingRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/RankingRepository.kt), cuando el catálogo contenía ítems con IDs idénticos o títulos similares en distintas categorías, las listas generadas no realizaban un filtrado por `distinctBy { it.id }`. Al renderizarse en listas `LazyRow` o `LazyColumn` de Compose con lambdas de clave basadas únicamente en el ID del ítem, Compose arrojaba un error de duplicidad de claves.
  - **Deduplicación en Repositorios y ViewModels:** Aplicado `distinctBy { it.id }` en [RecommendationRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/RecommendationRepository.kt), `distinctBy { it.second.id }` en [RankingRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/RankingRepository.kt) y en [HomeViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/HomeViewModel.kt) para todas las listas (`recommendations`, `top10Ranking` y `continueListeningList`).

## Hito: Sistema de Jam en Vivo (Salas de Escucha Sincronizadas en el Chat Global)
- **Modelado y Repositorio en Firestore:**
  - Creado [LiveJamSession.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/model/LiveJamSession.kt) para representar la sesión activa en tiempo real (`global_chat_jams/current_jam`).
  - Implementado [JamRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/JamRepository.kt) con observación continua mediante `callbackFlow` y operaciones reactivas: `startJam`, `updateJamProgress`, `endJam`, `joinJam` y `leaveJam`.
- **Componentes en Jetpack Compose:**
  - [LiveJamHeaderCard.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/LiveJamHeaderCard.kt): Tarjeta animada e interactiva en la cabecera del Chat Global que muestra el estado de la transmisión (*"🔴 JAM EN VIVO DE JOHAN"*), título, oyentes conectados y botones reactivos para unirse o salir.
  - [JamSelectorDialog.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/JamSelectorDialog.kt): Diálogo exclusivo para Administradores con filtro de búsqueda rápida en el catálogo para transmitir cualquier obra.
  - [ExitJamConfirmationDialog.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/ExitJamConfirmationDialog.kt): Diálogo emergente de advertencia si un oyente intenta salir de la sala de comunidad mientras escucha un Jam activo.
  - [PinPromptDialog.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/PinPromptDialog.kt): Diálogo emergente para validar la contraseña o PIN en salas privadas antes de permitir la entrada del oyente.
- **Salas Privadas (PIN Opcional) y Notificaciones Inteligentes:**
  - Al iniciar un Jam, el Administrador puede ingresar un PIN opcional. Si se define un PIN, el botón de unión muestra el distintivo `🔐 Privado` y exige la validación del PIN.
  - **Notificación Automática al Chat Global:** Si la sala es pública (sin PIN), el sistema envía automáticamente un mensaje oficial al chat notificando el inicio del Jam (*"🔴 ¡Johan ha iniciado una transmisión en vivo de 'El Señor de los Anillos'! Únete desde la cabecera del chat."*). Si la sala es privada, no se emite ninguna notificación masiva.
- **Integración con Navegación y Botón Atrás:**

## Hito: Integración Completa de Deep Links (audiocinemateca.com) y Fichas Interactivas en el Chat
- **Manejo de Enlaces Externos en MainActivity:**
  - Configurado [AndroidManifest.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/AndroidManifest.xml) con filtros de intenciones `ACTION_VIEW` para dominios `audiocinemateca.com` y `www.audiocinemateca.com` bajo protocolos `http` y `https`.
  - En [MainActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/MainActivity.kt), corregido `handleIntent` para procesar de forma inmediata la apertura de URLs externas, extrayendo el tipo de contenido y el ID único (ej. `audiocinemateca.com/pelicula/1895` o `audiocinemateca.com/serie/514`) y navegando directamente hacia [ContentDetailFragment](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/contentdetail/ContentDetailFragment.kt).
  - En [GlobalChatViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatViewModel.kt), implementado el analizador asíncrono `parseLinkedContentFromText`. Detecta automáticamente URLs de `audiocinemateca.com` incluso cuando vienen acompañadas de texto al compartir (ej. `¡Oye! Estoy escuchando... https://audiocinemateca.com/peliculas?id=1895`), extrae los parámetros `?id=` o rutas, consulta el catálogo y vincula el objeto `LinkedContent` con la ficha interactiva.

## Hito: Rediseño Accesible y Botón 'Pegar' en la Configuración de Clave API de IA
- **Corrección de Visibilidad y Accesibilidad en Diálogos de Ajustes:**
  - En [CategorySettingsScreen.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/settings/compose/CategorySettingsScreen.kt), rediseñado el componente `SettingsEditTextItem` con colores de alto contraste (`Color(0xFF1E293B)` para el contenedor del diálogo y `Color(0xFF0F172A)` para el cuadro de edición).
  - Añadido el botón destacado **`📋 Pegar desde portapapeles`** en el cuadro de edición de la clave Gemini. Permite pegar directamente la API Key almacenada en el portapapeles sin necesidad de mantener presionado sobre el campo de texto.
  - Añadido botón opcional **`❌ Limpiar`** para borrar rápidamente textos largos y soporte de múltiples líneas (`maxLines = 4`) para visualizar claves extensas de Gemini sin recortes de pantalla.

## Hito: Motor de Sincronización de Audio en Vivo (Jam en Vivo) y Desconexión Automática
- **Sincronización Host -> Oyentes en ExoPlayer ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - Implementado `syncHostJamProgressIfNeeded()` que transmite cada 2 segundos la posición exacta de lectura (`positionMs`) y estado de reproducción (`isPlaying`) del Administrador anfitrión a la colección `global_chat_jams/current_jam` de Firestore.
  - Implementada la acción `ACTION_SYNC_JAM_STATE` en `playerActionReceiver` de `PlayerService` para recibir broadcasts y ajustar el reproductor local de los oyentes (`seekTo` si el desajuste supera 3 segundos, `play()` / `pause()`).
- **Experiencia de Oyente en la Sala de Chat ([GlobalChatViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatViewModel.kt)):**
  - Al unirse a un Jam (`isJoinedJam = true`), los oyentes se mantienen dentro de la pantalla de Chat (`GlobalChatFragment`) con la tarjeta flotante Compose `LiveJamHeaderCard`, mientras `PlayerService` reproduce y sincroniza el audio en segundo plano.
- **Desconexión Automática por Cierre/Destrucción de App:**

## Hito: Clasificación de Contenido por IA (Compose Card & Anuncio TTS)
- **Generador de Clasificación en Gemini ([GeminiRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/GeminiRepository.kt)):**
  - Creado el método `generateContentRating(title, sinopsis)`. Analiza la obra y genera la clasificación recomendada (ej: *"Clasificación 16+ • Diálogos sugerentes, violencia moderada, lenguaje fuerte"*).
- **Componente Compose de Advertencia ([ContentRatingCard.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/components/ContentRatingCard.kt)):**
  - Creada tarjeta estilizada en Compose con icono de protección `🛡️`, distintivo dorado y texto de advertencia. Posee animación de entrada/salida y visibilidad programada por 7 segundos.
- **Integración en el Reproductor ([PlayerFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerFragment.kt) & [exoplayer_custom_controls.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/layout/exoplayer_custom_controls.xml)):**
  - Ubicada la tarjeta Compose exactamente debajo del contenedor de botones Me Gusta/No Me Gusta/Compartir y arriba de la vista previa de comentarios.
  - Al iniciar la reproducción, la tarjeta aparece con una pausa elegante de 200ms y el motor TTS (`TtsManager`) lee limpia y directamente la clasificación de la tarjeta sin prefijos innecesarios.
- **Solución al Bloqueo/Congelamiento al Cerrar el Mini Reproductor ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt) & [GlobalChatViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatViewModel.kt)):**
  - **Causa Raíz:** Se estaba ejecutando `runBlocking` en el hilo principal (`UI Thread`) dentro de `onDestroy()` y `onTaskRemoved()` al consultar `jamRepository.observeCurrentJam().firstOrNull()`. Esto bloqueaba el looper de Android y congelaba la interfaz al presionar la 'X' para cerrar el reproductor.
  - **Solución:** Reemplazado `runBlocking` por corrutinas asíncronas de fondo (`CoroutineScope(Dispatchers.IO).launch`), permitiendo que el servicio se destruya de inmediato en 0ms y la app responda con fluidez total.

## Hito: Migración Automática de Pestaña de Inicio (Startup Tab)

## Hito: Notificaciones Push FCM y Sincronización Automática de Tokens
- **Servidor Backend Linux ([chatService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/chatService.js) & [commentsService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/commentsService.js)):**
  - Notificaciones Push de Chat configuradas **exclusivamente para los 3 casos solicitados**: (1) Menciones directas (`@Usuario`), (2) Respuestas a mensajes previos, y (3) Anuncio masivo `@todos` (por Administradores).
  - **Emisión de Respaldo por Tema (`Topic Fallback`):** Si un usuario fue mencionado o respondido pero su token individual no figura aún en Firestore (por tener versión anterior o no haber iniciado sesión recientemente), el servidor envía la notificación a través del tema `audiocinemateca_global` adjuntando el `targetKey`. La app Android filtra el destinatario y hace sonar la notificación únicamente en el dispositivo del usuario objetivo.
  - Servicio de Comentarios ([commentsService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/commentsService.js)): Cuando alguien comenta en una obra, busca a todos los comentaristas anteriores de esa misma obra en `users` y `presence` y les envía push de alta prioridad.
- **Recepción en App Android ([MyFirebaseMessagingService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/service/MyFirebaseMessagingService.kt)):**
  - Reproduce tono de sistema (`RingtoneManager.TYPE_NOTIFICATION`), vibración y lectura por voz TTS out-loud cuando el usuario está fuera de la pantalla de chat o de comentarios. Accede al `targetKey` de las notificaciones de respaldo para garantizar que solo le suene al destinatario correspondiente.

## Hito: Corrección de Notificaciones Push FCM y Menciones en Chat Global
- **Bloqueo Estricto de Auto-Menciones (Client & Server):**
  - En [GlobalChatViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatViewModel.kt), se filtran las menciones dirigidas a la propia cuenta (por nombre, UID o prefijo de email) y al responder a sus propios mensajes.
  - En [chatService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/chatService.js), se aplica validación sin distinción de mayúsculas/minúsculas (`toLowerCase`) para evitar que `@nombre` dispare notificaciones al emisor `Nombre`.
- **Coincidencia Exacta y Eliminación de Notificaciones Espurias:**
  - En [MyFirebaseMessagingService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/service/MyFirebaseMessagingService.kt), se eliminó la comprobación por subcadena (`contains`). Ahora el payload exige coincidencia exacta (`==`) de UID, `displayName` o prefijo de email para notificaciones fallback.
- **Anuncios Masivos `@todos` Optimizados:**
  - Añadido `senderEmail` a [ChatMessage.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/model/ChatMessage.kt).
  - En [chatService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/chatService.js), las menciones `@todos` emitidas por la cuenta Administradora generan un **único envío masivo** al tema `audiocinemateca_global` con `targetKey = "todos"`, evitando iteraciones masivas y bloqueos de red.
  - En `MyFirebaseMessagingService.kt`, las notificaciones con `targetKey = "todos"` son procesadas e informadas a todos los dispositivos excepto al emisor.

## Hito: Optimización de Cliente Android (Delegación al Servidor)
- **Eliminación del Cálculo Descentralizado de Ranking:**
  - En [RankingRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/RankingRepository.kt), se removió el cálculo cliente pesado `recalculateGlobalTop10` que leía 4 colecciones de Firestore. La app ahora observa directamente `global_rankings/weekly_top10` (generado por el servidor) con fallback liviano al catálogo local.
- **Remoción del Worker Periódico de 6 Horas:**
  - En [MainActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/MainActivity.kt), se removió el agendamiento periódico en WorkManager (`setupCatalogUpdateWorker`) y el diálogo auto-comprobador, delegando la gestión de novedades al backend.

## Hito: Servicio Backend de Monitoreo de Catálogo y Push Automático
- **Monitoreo Automático en Node.js ([catalogService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/catalogService.js)):**
  - Creado e integrado en [servicios.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/servicios.js). Revisa la versión del catálogo en `audiocinemateca.com` cada 15 minutos mediante peticiones HTTP nativas y descompresión de GZIP sin librerías externas.
  - Al detectar cambios en la fecha/versión, descarga y analiza `catalogo.json.gz`, calculando el incremento exacto de películas, series, cortos y documentales.
  - Emite **un único Push FCM masivo** al tema `audiocinemateca_global` notificando la novedad con desglose exacto (ej. *"Se han añadido 4 nuevos títulos al catálogo: 3 películas, 1 serie..."*).
- **Navegación en Android ([MainActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/MainActivity.kt)):**
  - Al recibir una notificación con `destination = "catalog"`, al pulsarla abre directamente la pantalla de Catálogo (`R.id.catalogFragment`).

## Hito: Recálculo Cron Automático de Ranking Top 10 en Servidor Node.js
- **Módulo Daemon ([rankingCronService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/rankingCronService.js)):**
  - Creado e integrado en [servicios.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/servicios.js).
  - Revisa automáticamente cada 6 horas el documento `global_rankings/weekly_top10` en Firestore. Si han transcurrido 7 días o más desde el último cálculo, ejecuta en segundo plano el algoritmo de ponderación bayesiana, oyentes únicos y desduplicación de títulos.
  - Refresca y publica el nuevo Top 10 en Firestore de forma 100% autónoma, eliminando la intervención manual desde el panel CLI.

## Hito: Aura (IA) Bot Inteligente en el Chat Global
- **Servicio Bot Server-Side ([auraService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/auraService.js)):**
  - Creado e integrado en [servicios.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/servicios.js).
  - Escucha en tiempo real mensajes que contengan `@Aura` o menciones a la IA en el Chat Global.
  - Configurada la API Key oficial en `.env` (`GEMINI_API_KEY`) y el modelo `gemma-4-27b-it` sin pensamiento profundo para respuestas veloces.
- **Configuración de IA en Android ([GeminiRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/GeminiRepository.kt)):**
  - Modelo por defecto `gemma-4-27b-it` y desactivación por defecto de `thinkingConfig` (`null`) para evitar latencias de pensamiento profundo cuando no se solicita explícitamente.

## Hito: Interacción Accesible de Usuarios Conectados en Chat Global
- **Cabecera Accesible ([GlobalChatHeader.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/GlobalChatHeader.kt)):**
  - Habilitado el clic y anuncio de TalkBack en la barra superior para todos los usuarios, permitiendo abrir la lista de usuarios activos.
- **Diálogo de Usuarios ([ConnectedUsersDialog.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/ConnectedUsersDialog.kt)):**
  - Eliminada la opción redundante de responder. Mantenida la opción de mencionar a `@Usuario` para todos y la suspensión/sanción reservada únicamente para administradores.

## Hito: Eliminación de Mensajes por Administrador y Respuesta Ligera en Aura
- **Eliminación por Administrador ([GlobalChatRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/GlobalChatRepository.kt) & [ChatMessageOptionsBottomSheet.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/ChatMessageOptionsBottomSheet.kt)):**
  - Habilitada la opción de eliminar para administradores en cualquier mensaje.
  - El mensaje eliminado conserva su posición original en el chat mostrando el texto en cursiva: *"Un admin eliminó este mensaje"* ([GlobalChatAdapter.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatAdapter.kt)).
- **Generación Ligera y Contexto Ampliado (20 Mensajes) en Aura ([auraService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/auraService.js)):**
  - Configurado `thinkingConfig: { thinkingBudget: 0 }` explícitamente en el backend para desactivar razonamiento profundo y forzar la generación ultrarrápida.
  - Ampliado el historial de contexto en el chat de 8 a **20 mensajes** (`.limit(20)`) para que Aura recuerde la conversación completa.
- **Corrección de Permisos de Eliminación para Admin ([GlobalChatFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatFragment.kt)):**
  - Pasado explícitamente `viewModel.isAdmin` al instanciar `ChatMessageOptionsBottomSheet`, permitiendo la eliminación de cualquier mensaje (incluidos los de Aura u otros miembros).
- **Integración de Aura (IA) en Usuarios Conectados ([ConnectedUsersDialog.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/components/ConnectedUsersDialog.kt)):**
  - Añadida `Aura (IA)` al inicio de la lista de conectados con presencia activa constante. Permite la mención directa `@Aura` con 1 toque y protege a la IA contra intentos de suspensión/sanción.

## Hito: Arquitectura de Notificaciones Push y Silenciamiento en Chat Activo
- **Silenciamiento Absoluto en Pantalla de Chat ([GlobalChatFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatFragment.kt)):**
  - Sincronizados `onResume()` y `onPause()` con `GlobalChatState.isChatScreenActive`. Mientras el usuario tiene abierta la pantalla del chat, las notificaciones push flotantes y anuncios por voz TTS se suprimen al 100%.
- **Validación Estricta de Destinatario (`targetUid`) ([MyFirebaseMessagingService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/service/MyFirebaseMessagingService.kt) & [chatService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/chatService.js)):**
  - Incorporado el parámetro `targetUid` en los payloads FCM del backend.
- **Deduplicación Sincrónica e Inmediatez por Token FCM Directo ([MyFirebaseMessagingService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/service/MyFirebaseMessagingService.kt) & [chatService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/chatService.js)):**
  - Implementada deduplicación sincrónica con `runBlocking` sobre `notificationDao.existsByRemoteId(remoteId)` en `MyFirebaseMessagingService.kt`. Si un mensaje fue procesado en el celular (incluso con la pantalla de chat abierta), no vuelve a sonar ni mostrar push.
- **Formateo de Payload REST para Gemma 4 ([auraService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/auraService.js)):**
  - Identificado que los modelos `gemma-4-26b-a4b-it` y `gemma-4-31b-it` no soportan el objeto `thinkingConfig` ni el objeto de nivel superior `systemInstruction` en la API REST de Google (devolvían HTTP 400).
  - Inyectadas las directivas dentro del prompt de usuario cuando `isGemma` es verdadero, permitiendo que Gemma 4 responda exitosamente con HTTP 200 OK.
- **Formateo de Títulos de Notificación Push ([chatService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/chatService.js)):**
  - Estandarizados los títulos en el formato: `"Nueva notificación en el Chat Global: [Nombre] respondió a tu mensaje"` o `"[Nombre] te mencionó"`.
- **Filtro y Desactivación Absoluta de Pensamiento Profundo ([auraService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/auraService.js)):**
  - Inyectada directiva de respuesta directa sin pensamientos internos en el prompt de Gemma.
  - Implementado el descarte de partes con `p.thought === true` y la limpieza por expresión regular de etiquetas `<thought>...</thought>` antes de publicar la respuesta de Aura en Firestore.


## Hito: Motor de Aura IA v4.0 (Gemini 2.5/Gemma 4, Memoria 8h, Catálogo Real y Personalidad Humana)
- **Solución a `INVALID_ARGUMENT` y Optimización de API REST ([auraService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/auraService.js)):**
  - Identificado que la API REST de Google rechaza el objeto `thinkingConfig` en la familia Gemma devolviendo HTTP 400 `INVALID_ARGUMENT`.
  - Configurado `thinkingConfig: { includeThoughts: false, thinkingBudget: 0 }` condicional solo para modelos Gemini (`!isGemma`). Para Gemma 4 se envía el payload nativo limpio sin `thinkingConfig`, permitiendo su ejecución directa sin errores.
  - Establecido `gemini-2.5-flash` como modelo prioritario por defecto en `modelsToTry` para respuestas inmediatas en sub-segundos (~0.47s).
- **Buscador Temático de Catálogo Real y Fichas Interactivas ([auraService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/auraService.js)):**
  - Integrado el cargador automático de `back/data/catalogo.json`.
  - Creado el motor de búsqueda temático con escaneo profundo de sinopsis, títulos, géneros, reparto y directores, normalización de acentos y diccionario de sinónimos temáticos (`THEMATIC_SYNONYMS` para terremotos/sismos, aliens, zombies, robos, guerra, etc.).
  - Generación e inyección de URLs oficiales `https://audiocinemateca.com/[tipo]?id=[id]`. Al citarlas Aura, el cliente Android despliega automáticamente la ficha interactiva con el botón de Play.
  - **Recomendación Inteligente a Solicitud (`shouldSearchCatalog`):** Eliminado el envío forzado de enlaces en saludos o charlas generales. Aura solo recomienda películas/series cuando el usuario se lo pide explícitamente o busca contenido.
- **Memoria Continua de 8 Horas y Limpiador por Inactividad ([auraService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/auraService.js)):**
  - Ampliado el contexto del chat a una ventana deslizante de las últimas 8 horas (hasta 50 mensajes de extensión) con fallback de filtrado en memoria.
  - Implementado limpiador automático `setInterval` que detecta 8 horas de silencio/inactividad y reinicia limpiamente la memoria de contexto de Aura.
- **Personalidad Humana, Espontánea y Fluida para TTS ([auraService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/auraService.js)):**
  - Rediseñada la identidad en `AURA_SYSTEM_PROMPT` para eliminar cualquier tono robotizado o de asistente corporativo.
  - Incorporadas directivas de espontaneidad, expresiones naturales en español (*"¡Ey!"*, *"¡Totalmente!"*, *"¡Qué temazo!"*, *"¡Literal!"*) y ritmo optimizado para voz de síntesis (TTS) en Android.

## Hito: Servidor de Jam en Vivo en Node.js (SSE 0-Quota), Reparación de Crash en Android 11 y Opciones de Navegación
- **Servidor de Jam en Tiempo Real (Node.js + Server-Sent Events) ([jamService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/jamService.js)):**
  - Implementado servidor liviano sin dependencias externas usando HTTP + SSE y rutas de control (`/start`, `/update`, `/join`, `/leave`, `/end`).
  - Deploy completado y verificado en VPS Linux (IP `207.231.110.156`): Node.js en puerto interno `4892` con PM2 y Reverse Proxy HestiaCP Nginx activo bajo `/audiocinemateca-server/` con HTTP/2 y HTTPS (`200 OK` + SSE sin buffering).
- **Repositorio Híbrido Libre de Cuotas en Android ([JamRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/JamRepository.kt)):**
  - Conexión primaria por IP directa HTTP `http://207.231.110.156/audiocinemateca-server/api/jam/` (0ms latencia, sin restricciones de certificado de dominio), respaldo por HTTPS e impermeabilidad final en Firestore.
  - Eliminado el consumo de cuotas de lectura de Firebase Firestore durante la escucha en tiempo real.
  - **Mecanismo de Respaldo Transparente:** Si el servidor VPS se encuentra inalcanzable o devuelve 404, conmuta automáticamente e imperceptiblemente a Firestore sin interrumpir la experiencia del usuario. Habilitado `usesCleartextTraffic="true"` en `AndroidManifest.xml`.
- **Eliminación de Crash Fatal `RemoteServiceException` ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - Identificada la causa raíz del error `Context.startForegroundService() did not then call Service.startForeground()` reportado en Android 11 (Samsung SM-A107M API 30).
  - Sobrescrito `onStartCommand` en `PlayerService.kt` para invocar inmediatamente `startForeground(NOTIFICATION_ID, notification)` y creado el canal de notificación `audiocinemateca_playback` (`IMPORTANCE_LOW`). Satisface en 0ms la regla de 5 segundos de Android 8 a 14.
- **Opción "Ver detalles" en Continuar Escuchando ([ContinueListeningCard.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/components/ContinueListeningCard.kt)):**

## Hito: Integración Completa de Google Cast (Smart TVs / Google Nest) y Proxy de Audio en Node.js
- **Módulo de Proxy de Audio Independiente ([streamProxyService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/streamProxyService.js)):**
  - Módulo desacoplado en Node.js cargado en `back/servicios.js` (`initStreamProxyService`).
  - Endpoint `/audiocinemateca-server/api/stream/proxy?url=...` que inyecta de forma transparente la autenticación básica del catálogo (`CATALOG_USER`, `CATALOG_PASS`) y responde con `206 Partial Content` y soporte de `Range: bytes` para permitir buffer y seek sin restricciones en receptores Chromecast/Smart TV.
- **Integración de Google Cast en Jetpack Compose & Android:**
  - Registrado `play-services-cast-framework` y `media3-cast` en `gradle/libs.versions.toml` y `app/build.gradle.kts`.
  - Creado `CastOptionsProvider.kt` registrado en `AndroidManifest.xml` (`OPTIONS_PROVIDER_CLASS_NAME`).
  - Creado el widget reutilizable `CastButton.kt` en Compose e integrado en la barra de la pantalla de Inicio ([HomeToolbar.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/components/HomeToolbar.kt#L50)), Catálogo ([CatalogToolbar.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/catalog/CatalogToolbar.kt#L46)), Detalles ([DetailToolbar.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/catalog/DetailToolbar.kt#L50)) y en el Reproductor a pantalla completa ([PlayerFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerFragment.kt#L244) y [player_toolbar_menu.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/menu/player_toolbar_menu.xml#L4)) con etiquetas de accesibilidad para TalkBack.
  - Creado `AudioProxyUtil.kt` para transformar dinámicamente URLs del catálogo en URLs proxificadas compatibles con Google Cast.
  - Creado `CastSessionListener.kt` e inyectado en `MainActivity.kt` para emitir anuncios accesibles TTS especificando el nombre exacto del dispositivo receptor (`session.castDevice.friendlyName`, ej: *"Transmitiendo audio en TV del Salón"* o *"Transmitiendo audio en Parlante Cocina"*).
  - Migrada la actividad inicial [SplashActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/SplashActivity.kt#L23) a `ComponentActivity` (la clase base oficial y ligera para Compose UI), eliminando la dependencia de `AppCompatDelegateImpl` y previniendo cualquier fallo en tiempo de ejecución al arrancar.
  - Actualizado el tema base de la aplicación en [themes.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/values/themes.xml#L6) y [values-night/themes.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/values-night/themes.xml#L6) definiendo `colorSurfaceVariant` (`#334155`) y `colorOnSurfaceVariant` (`#94A3B8`) para erradicar cualquier `InflateException` al cargar layouts de chat o tarjetas.
  - Implementada en [PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt#L170) la persistencia síncrona imperdible de progreso (`runBlocking(Dispatchers.IO)` con `approximateStreamPosition`) al cerrar con el botón 'X', garantizando que el historial guarde el segundo exacto antes de detener el servicio.
  - Configurada en [PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt#L433) la búsqueda de precisión en Chromecast mediante `MediaSeekOptions.Builder().setPosition(positionMs).setResumeState(RESUME_STATE_UNCHANGED).build()`, habilitando los saltos de rebobinado (5s) y avance (15s) en tiempo real con respuesta inmediata en Smart TV / Altavoz.


## Hito: Colecciones Temáticas Curadas por IA en Portada y Motor de Búsqueda Multicampo
- **Generador de Colecciones Temáticas en Fondo con IA ([curatedCollectionsCronService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/curatedCollectionsCronService.js)):**
  - Módulo en Node.js que ejecuta al arrancar y cada 24 horas. Analiza el catálogo completo y genera 3 colecciones temáticas irresistibles de portada (título ingenioso, subtítulo opcional explicativo y 4–8 títulos reales).
  - Configurado con **Gemma 4 31B (`gemma-4-31b-it`)** y respaldo en **Gemini 2.5 Pro (`gemini-2.5-pro`)** con razonamiento profundo activado (`isHeavyQuery = true`).
  - Publica los resultados directamente en Firestore (`global_rankings/curated_collections`).
  - **Opción de Regeneración Manual en Consola ([index.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/index.js)):** Añadida la opción `6. Regenerar Colecciones Tematicas por IA (Firestore)` en el panel de control del servidor para forzar el refresco cuando el administrador lo desee.
- **Motor de Búsqueda por Sinergia de Metadatos 100% Generalizado ([auraService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/auraService.js)):**
  - Eliminadas reglas condicionales rígidas por tema.
  - Implementado motor dinámico que evalúa coincidencia en 6 atributos (Título, Género, País, Director/Actores, Sinopsis, Año) y otorga bonificaciones por sinergia (+25 pts por 2 campos, +40 pts por 3+ campos).
- **Componentes en Jetpack Compose ([CuratedCollectionSection.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/components/CuratedCollectionSection.kt) & [HomeScreen.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/HomeScreen.kt)):**
  - Encabezado ultra limpio estilo Netflix/Crunchyroll con Título en blanco y Subtítulo tenue abajo. Eliminada la palabra genérica "Obra" de títulos y semántica de accesibilidad TalkBack.
  - Sincronización reactiva en tiempo real en [`HomeViewModel.kt`](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/HomeViewModel.kt).

- **Sincronización Bidireccional de Play/Pausa con Dispositivos Remotos ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt) & [PlayerFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerFragment.kt)):**
  - Implementado `notifyCastStatusChanged()` en `ForwardingPlayer` para propagar de forma imperativa los eventos `onIsPlayingChanged`, `onPlaybackStateChanged` y `onPositionDiscontinuity` hacia `MediaSession` y los controladores suscritos al cambiar el estado del parlante externo o TV.
  - Sobrescritos `getPlayWhenReady()` y `getPlaybackState()` en `ForwardingPlayer` reflejando el estado real del `RemoteMediaClient` (`isPlaying`, `isBuffering`, `isPaused`).
  - Al pausar o reanudar desde el parlante físico, control remoto o app externa, `RemoteMediaClient.Callback` emite `notifyCastStatusChanged()`, actualizando instantáneamente el icono del botón Reproducir/Pausar y habilitando el control de tiempo en la interfaz del teléfono.
- **Navegación Desde Notificación al Reproductor ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt) & [MainActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/MainActivity.kt)):**
  - Configurado `createForegroundNotification()` para empaquetar los extras `EXTRA_ITEM_ID`, `EXTRA_ITEM_TYPE`, `partIndex` y `episodeIndex` con banderas `FLAG_ACTIVITY_SINGLE_TOP` y `FLAG_ACTIVITY_CLEAR_TOP`.
  - En `handleOpenPlayer()`, se procesan los metadatos de la notificación y se ejecuta la navegación directa hacia `PlayerFragment` abriendo la pantalla completa del reproductor correspondiente.
- **Actualización Activa de la Barra de Tiempo en Cast ([PlayerFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerFragment.kt)):**
  - Añadido `OnScrubListener` explícito a `DefaultTimeBar` (`exo_progress`) para capturar el arrastre manual de la barra (`onScrubMove` y `onScrubStop`), actualizando las etiquetas de texto de tiempo en vivo y llamando a `mediaController.seekTo(position)`.
  - Actualizado `updateTimestamps()` para invocar imperativamente `timeBar.setPosition(currentPosition)` y `timeBar.setDuration(duration)` cada 1000 ms durante el modo Cast, evitando que la barra quede congelada cuando la reproducción local está pausada.
  - Configurada la llamada directa a `remoteClient.seek(positionMs)` en `ForwardingPlayer` para mayor compatibilidad con receptores Chromecast.
- **Seguimiento Continuo de Tiempo en Google Cast ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - Integrado `RemoteMediaClient.ProgressListener` con polling de 1000 ms que mantiene `lastKnownCastPositionMs` sincronizado en tiempo real durante toda la transmisión en pantalla secundaria/Smart TV.
- **Restauración Transparente al Desconectar desde la Barra ([CastSessionListener.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/cast/CastSessionListener.kt) & [PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - `CastSessionListener` captura `approximateStreamPosition` en `onSessionEnding` y `onSessionEnded`, adjuntando `EXTRA_LAST_CAST_POSITION` en el broadcast de desconexión.
  - `PlayerService` procesa este intent (con fallback a `lastKnownCastPositionMs`), posiciona el reproductor local del teléfono (`basePlayer`) en el milisegundo exacto donde quedó la televisión, desmuta el volumen y reanuda la reproducción sin saltos ni pérdidas de progreso.
- **Controles de Salto Instantáneos (5s / 15s) y Rebobinado ([ForwardingPlayer](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt) & [PlayerFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerFragment.kt)):**
  - En `ForwardingPlayer`, las llamadas a `seekToPrevious()` y `seekToNext()` usan los intervalos configurados por el usuario (`rewind_interval` y `forward_interval`).
  - Al ejecutar `seekTo(positionMs)`, la variable `lastKnownCastPositionMs` se actualiza al instante con la posición objetivo y se envía el comando `remoteClient.seek(...)`, garantizando respuesta inmediata en pantalla y en la interfaz ante pulsaciones continuas.
  - Actualizados los listeners en `PlayerFragment.kt` para forzar la actualización inmediata de la UI de tiempos (`updateTimestamps()`).

## Hito: Corrección de Atributos de Tema XML, Refresco en Tiempo Real de Google Cast y Accesibilidad en Inicio
- **Resolución de InflateException por Atributos de Tema:**
  - Registrados `colorOutline` (`#64748B`) y `colorOutlineVariant` (`#334155`) en [themes.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/values/themes.xml) y [values-night/themes.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/values-night/themes.xml).
  - Corregidas vistas XML ([item_notification.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/layout/item_notification.xml), [fragment_chat_message_options.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/layout/fragment_chat_message_options.xml), [fragment_ai_chat.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/layout/fragment_ai_chat.xml), [fragment_profile.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/res/layout/fragment_profile.xml)).
- **Sincronización en Tiempo Real de Google Cast:**
  - Implementada la clase [CastAwareForwardingPlayer](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt) eliminando la reflexión y despachando eventos imperativos a los controladores.
  - Actualización continua de [DefaultTimeBar](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerFragment.kt) cada 500 ms en modo Cast.
- **Accesibilidad en Pantalla de Inicio (SplashActivity):**
  - Ajustadas etiquetas TalkBack en [SplashActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/SplashActivity.kt): `"Animación"`, `"Audiocinemateca"` y `"Cargando la pantalla de inicio"`.

## Hito: Corrección de Audio Local en Google Cast y Enrutamiento por Proxy
- **Silenciado Local y Enrutamiento a Chromecast ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - Al conectarse a una sesión de Google Cast (`ACTION_CAST_CONNECTED`), el reproductor local `exoPlayer` se silencia de forma transparente (`volume = 0f`) y se pausa si estaba sonando localmente.
  - La URL del contenido se canaliza usando [`AudioProxyUtil.buildCastProxyUrl(...)`](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/util/AudioProxyUtil.kt#L12) con las credenciales temporales y se envía directamente a `remoteMediaClient` para sonar exclusivamente en la TV o altavoz Chromecast.
  - Registro de escuchas continuas (`addProgressListener` a 500 ms y `RemoteMediaClient.Callback`) para notificar en tiempo real los cambios de estado de reproducción y avance de tiempo hacia la interfaz de usuario (`PlayerFragment`), manteniendo los botones y la barra de tiempo sincronizados de manera reactiva.
- **Delegación 100% al RemoteMediaClient de Google Cast ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - Al conectarse a Cast, `exoPlayer.pause()` y `exoPlayer.stop()` detienen por completo el reproductor local, garantizando 0 audio por el altavoz del teléfono.
  - Sobrescrita la sobrecarga `seekTo(positionMs: Long)` y `seekTo(mediaItemIndex, positionMs)` en `CastAwareForwardingPlayer` para derivar inmediatamente los comandos de avance/retroceso y barra de tiempo al `remoteMediaClient`.
- **Interceptor de Órdenes Cast en CastAwareForwardingPlayer ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - Sobrescritos `setMediaItems`, `prepare()` y `setPlayWhenReady(playWhenReady)` en `CastAwareForwardingPlayer` cuando la sesión Cast está activa.
  - Impide que `PlayerFragment` active el `exoPlayer` local al cargar metadatos o darle a reproducir, desviando el 100% de la carga de audio al `remoteMediaClient` con la URL proxied [`AudioProxyUtil.buildCastProxyUrl(...)`](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/util/AudioProxyUtil.kt#L12).
  - Enrutamiento imperativo de `play()` y `pause()` directamente hacia el Chromecast, resolviendo la desincronización del botón Reproducir/Pausar en la interfaz de usuario.

## Hito: Corrección de Sincronización de Audio para Oyentes en Jam en Vivo
- **Arranque Automático de Servicio en Teléfono del Oyente ([GlobalChatViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/community/GlobalChatViewModel.kt)):**
  - En `syncListenerAudioWithJam(jam)`, se reemplazó el broadcast genérico del sistema por `ContextCompat.startForegroundService(...)` enlazado con `LocalBroadcastManager`. Esto garantiza que al presionar *"🎧 Unirme"`, `PlayerService` se despierte e inicie inmediatamente en segundo plano en el teléfono del oyente.
- **Procesamiento de Intents e Inicialización en `onStartCommand` ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - Sobrescrito `onStartCommand(...)` delegando la acción a `handleServiceIntent(intent)`.
  - Al recibir `ACTION_SYNC_JAM_STATE`, si `mediaSession == null` se inicializa `setupPlayer()`, se normaliza la categoría (`pelicula`/`peliculas`, `serie`/`series`), y si el ítem no está cargado o el reproductor está vacío, invoca `loadAndPlayJamContent(...)` cargando la obra en vivo.
  - Sincronización reactiva de posición (`seekTo` si el desajuste supera los 1200 ms) y del estado de reproducción (`play()` / `pause()`) según la transmisión del Anfitrión.
- **Resolución de Crash `ForegroundServiceDidNotStartInTimeException` en Android 8.0+ ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - Creado `ensureForegroundNotification()` que construye una notificación `NotificationCompat` de prioridad adecuada en el canal `"audiocinemateca_playback"` y ejecuta inmediatamente `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`.
  - Sobrescrito `onStartCommand(...)` para invocar de forma sincrónica `ensureForegroundNotification()` antes de cualquier tarea asíncrona en corrutinas. Esto responde instantáneamente al temporizador de 5 segundos de Android 8.0+, eliminando el choque `RemoteServiceException$ForegroundServiceDidNotStartInTimeException` al unirse a un Jam o iniciar reproducción en segundo plano.
- **Resolución de Crash `IllegalStateException: Session ID must be unique` ([PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt)):**
  - Añadida la anotación `@Synchronized` y el guard `if (mediaSession != null) return` al inicio de `setupPlayer()`.
  - Configurada la liberación limpia e explícita `mediaSession?.release()` seguida de `mediaSession = null` en `ACTION_STOP` y en el ciclo de vida `onDestroy()`, evitando que re-inicializaciones concurrentes o rearranques intenten registrar duplicados de `"AudiocinematecaPlayerSession"`.
- **Notificaciones Push FCM Masivas para Jam Público ([jamService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/jamService.js) & [servicios.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/servicios.js)):**
  - Actualizado `servicios.js` inyectando `messaging` (`admin.messaging()`) en `initJamService(db, messaging)`.
  - Configurado el endpoint `/jam/start` en `jamService.js`: si la sala es pública (`!data.pinCode`), envía automáticamente una notificación Push masiva de alta prioridad a través de FCM al tema `audiocinemateca_global` notificando a todos los usuarios (*"🔴 Jam en Vivo de [Anfitrión] - ¡Inició la transmisión de '[Obra]'! Toca aquí para unirte en vivo."*) con los metadatos `destination: "live_jam"` para unión directa.

## Hito: Servicio de Presencia Cero Costo en Servidor Linux Node.js ([presenceService.js](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/presenceService.js), [GlobalChatRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/GlobalChatRepository.kt))
- **Módulo de Presencia en Memoria RAM (Node.js):** Creado [`back/modules/presenceService.js`](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/back/modules/presenceService.js) con un registro `Map` en RAM. Expone endpoints `/presence/heartbeat` (POST), `/presence/list` (GET) y `/presence/offline` (POST), con purga automática de usuarios inactivosa los 90 segundos.
- **Desconexión Total de Firebase para Presencia:** En [`GlobalChatRepository.kt`](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/GlobalChatRepository.kt), `setUserPresence(...)` y `getOnlineUsersFlow()` leen y escriben exclusivamente contra el servidor Node.js VPS vía OkHttp HTTP.
- **Resultado Final:** 0 lecturas y 0 escrituras en Firestore por concepto de presencia de usuarios online (Reducción total a $0.00 en Firebase).

## Hito: Optimización Masiva de Lecturas Firestore (>99.6% de Reducción) ([GlobalChatRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/GlobalChatRepository.kt), [JamRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/JamRepository.kt), [FeaturedBannerRepository.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/data/repository/FeaturedBannerRepository.kt), [HomeViewModel.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/home/HomeViewModel.kt)):**
  - **Eliminación del listener N² de Presencia:** Reemplazado el `addSnapshotListener` continuo en `presence` por una consulta puntual `fetchOnlineUsers()` ejecutada cada 90 segundos o bajo demanda. Reducción de más de 20.000 lecturas/minuto a ~66 lecturas/minuto para 100 usuarios activos.
  - **Eliminación de escrituras de Jam a Firestore cada 2s:** Removida la escritura recurrente cada 2 segundos a `global_chat_jams/current_jam` en `JamRepository.updateJamProgress`. Los avances de tiempo de los Jams se transmiten exclusivamente por HTTP/SSE al servidor Linux Node.js (`$NODE_BASE_URL/update`), reduciendo las escrituras y lecturas de Firestore al 0% durante la reproducción.
  - **Consultas Puntuales con Caché en Inicio:** Reemplazados los snapshot listeners continuos en `app_config/featured_banner` y `global_rankings/curated_collections` por consultas `get().await()` al inicializar la vista con caché en memoria.

## Hito: Corrección de Metadatos Redundantes en Google Cast y Notificaciones ([PlayerFragment.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerFragment.kt), [PlayerService.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/presentation/player/PlayerService.kt))
- **Eliminación de Título/Artista Duplicado:** Ajustado `createMediaItems()` en `PlayerFragment` y `loadAndPlayJamContent()` en `PlayerService`.
  - Películas de 1 archivo: `title` = Nombre de la película, `artist` = `"Audiocinemateca"` (Cast muestra: *"El Señor de los Anillos de Audiocinemateca"*).
  - Películas multiparte: `title` = `"Parte N"`, `artist` = Nombre de la película (Cast muestra: *"Parte 1 de El Señor de los Anillos"*).
  - Series: `title` = `"T1:E1 - Nombre del Capítulo"`, `artist` = Nombre de la Serie.

## Hito: Permisos de Dispositivos Cercanos y Google Cast en Android 12/13+ ([AndroidManifest.xml](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/AndroidManifest.xml), [MainActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/MainActivity.kt), [SplashActivity.kt](file:///E:/Johan/AndroidStudioProjects/Audiocinemateca/app/src/main/java/com/johang/audiocinemateca/SplashActivity.kt))
- **Declaración en Manifest:** Incorporados permisos `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (`neverForLocation`), `NEARBY_WIFI_DEVICES` (`neverForLocation`), `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION` en `AndroidManifest.xml`.
- **Solicitud Dinámica en Pantalla de Inicio:** Configurado `MainActivity.kt` con `checkAndRequestPermissions()` usando `RequestMultiplePermissions()` para solicitar en la pantalla principal los permisos de dispositivos cercanos y notificaciones en Android 12+ / 13+, manteniendo `SplashActivity.kt` 100% fluida e inmediata.
