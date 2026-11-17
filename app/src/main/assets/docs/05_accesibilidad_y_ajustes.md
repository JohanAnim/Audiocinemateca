# Accesibilidad y Ajustes

Cada pantalla de Audiocinemateca está construida desde cero pensando en lectores de pantalla como **TalkBack** y **Jieshuo**.

---

## Funciones clave de accesibilidad

* **Foco virtual inteligente:** Al abrir el Reproductor, los Detalles de una obra, el Chat Global, Ajustes o el Menú Lateral, el cursor del lector de pantalla se coloca automáticamente en el elemento principal sin abrir el teclado por accidente.
* **Restauración exacta de foco:** Al volver atrás desde la ficha de una obra hacia Explorar, desde una subcategoría de Ajustes o al cerrar el Menú Lateral, el foco regresa exactamente al botón o película que habías tocado.
* **Encabezados nativos para rotor:** Las secciones principales, fechas del historial y separadores del chat están marcados como encabezados compatibles tanto con TalkBack como con Jieshuo (`stateDescription = "Encabezado"`).
* **Lectura de sinopsis por voz (TTS):** En la ficha de cualquier película o serie, toca la tarjeta de sinopsis para escucharla leída de inmediato con tu motor de voz configurado. Puedes detener cualquier locución en cualquier momento con el gesto hacia abajo / arriba configurado según la pantalla.
* **Consejos contextuales opcionales:** En **Ajustes -> General** puedes activar o desactivar *"Consejos de interacción contextuales"* si ya conoces los gestos y prefieres una lectura aún más corta y rápida.

---

## Categorías de Configuración (`Cuenta -> Configuración`)

* **General:** Tema (Claro, Oscuro, Sistema), frecuencia de animación de inicio, pestaña de inicio predeterminada, opción para ocultar por completo la pestaña Inicio, comprobación automática de actualizaciones, consejos de accesibilidad y envío de reportes de error (`.log`).
* **Reproducción:** Reproducción automática, temporizador de descanso, intervalos de retroceso/avance, gestos táctiles en el reproductor, manejo de foco de audio (pausa ante llamadas) y **Vibración Háptica Avanzada (DualSense / Android 12+)**.
* **Comunidad:** Mostrar u ocultar tu presencia en línea y elegir cuándo escuchar los anuncios de voz del Chat Global (solo en reposo, siempre o nunca).
* **Inteligencia Artificial:** Elegir proveedor (**Groq** recomendado o **Google Gemini**), configurar tu clave API con botón directo de pegar desde portapapeles, sincronizar modelos y activar mensaje de motivación al compartir.
* **Ajustes de Texto a Voz (TTS):** Configurar motor de voz, idioma, voz específica, velocidad y tono independientes.
* **Descargas:** Elegir carpeta de descarga y activar o desactivar el **Modo Offline**.
