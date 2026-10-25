RESTRUCTURACIÓN DEL DISEÑO Y CONTROLES DEL REPRODUCTOR (PlayerScreen.kt)

1. DISEÑO ELEGANTE Y RESTAURADO
Se restauro la estructura original de tarjetas:
- Tarjeta central (Card en tono 0xFF1E293B) con icono de ecualizador, subtitulo y panel social de botones (Me gusta, No me gusta, Compartir).
- Tarjeta de Sinopsis inteligente.
- Tarjeta de Comentarios.

2. CONTROLES DE REPRODUCCIÓN FIJOS EN LA PARTE INFERIOR
Se ajusto el contenedor central con peso desplazable (weight 1f) para que el area de tarjetas se desplace suavemente en la zona media, mientras que la barra de controles (barra de progreso, tiempos digitales, Play/Pause FAB, saltos de tiempo y botones de capitulo) permanezca fijada en la parte inferior de la pantalla sin superponerse en ningun momento.

3. CAPA DE GESTOS GLOBALES ACTIVA
Se mantuvo el detector de bajo nivel (PointerEventPass.Initial) con los 4 gestos y respuesta haptica:
- Deslizar arriba: Play / Pause
- Deslizar abajo: Anuncio de tiempo y estado por nuestro servicio TTS
- Deslizar a la derecha: Adelantar
- Deslizar a la izquierda: Retroceder

VERIFICACIÓN DEL PROYECTO
Compilacion Kotlin: Exitosa con 0 errores.
