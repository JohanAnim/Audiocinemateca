# Novedades del Proyecto

Resumen de las mejoras más recientes integradas en esta nueva generación de Audiocinemateca:

---

## 1. Migración integral a Jetpack Compose 100% nativo
* Toda la aplicación fue reconstruida en **Jetpack Compose** moderno, eliminando por completo los antiguos layouts XML, Fragments y diálogos clásicos.
* Rediseño de la ficha técnica estilo streaming con píldoras de metadatos, sinopsis interactiva con lectura TTS inmediata y selector accesible de temporadas.

## 2. Catálogo en SQLite + Buscador instantáneo FTS4
* El catálogo dejó de depender de un archivo JSON gigante en memoria y ahora vive indexado en **SQLite (Room + FTS4)**.
* Explorar pagina y filtra directo en base de datos, abrir fichas o listas es casi instantáneo y el buscador responde en milisegundos mientras escribes.

## 3. Motor Háptico de Cine y soporte PS5 DualSense
* Análisis de audio en tiempo real para sentir explosiones, graves y efectos en los motores estéreo del mando **PS5 DualSense** o en teléfonos con vibración HD.
* Navegación 2D completa con el mando y atajos directos en el reproductor (L1/R1, L2/R2, Triángulo, Cuadrado).

## 4. Aura renovada: Multi-proveedor, Historial y Consultas Reales
* Soporte para **Groq** y **Google Gemini** con selector de modelos en la barra superior y sesiones de chat persistentes con títulos generados automáticamente.
* Nuevos súper poderes para Aura: consultas SQL directas sobre el catálogo (`query_catalog_sql`), acceso a tu historial/favoritos/descargas y consulta de esta documentación oficial.
* Dictado por voz **Push-to-Talk** en Aura, Chat Global y Comentarios.

## 5. Accesibilidad y Estabilidad de Audio
* Foco virtual instantáneo y restauración exacta de foco compatible con TalkBack y Jieshuo.
* Google Cast blindado en segundo plano y pausa automática ante llamadas telefónicas o desconexión de auriculares.
