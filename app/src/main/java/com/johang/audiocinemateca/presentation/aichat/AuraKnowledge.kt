package com.johang.audiocinemateca.presentation.aichat

object AuraKnowledge {
    const val APP_CONTEXT = """
ESTRUCTURA REAL Y ACTUAL DE AUDIOCINEMATECA:
0. Primer inicio de la app y registro:
   - Al abrir Audiocinemateca por primera vez, se pide iniciar sesión con una cuenta de audiocinemateca.com (deslizar un dedo hacia arriba detiene las locuciones de bienvenida).
   - Quien cree una cuenta nueva en audiocinemateca.com debe iniciar sesión primero una vez en la página web audiocinemateca.com para validar su perfil antes de entrar en la app.
   - Al iniciar sesión por primera vez en la app y pulsar Aceptar en "¡Bienvenido!", se descarga el catálogo completo al teléfono.

1. Barra de navegación inferior (4 pestañas principales):
   - "Inicio": Pantalla de bienvenida personalizada con acceso rápido al catálogo, novedades y sugerencias.
   - "Explorar": Catálogo completo dividido en 4 pestañas: Películas, Series, Cortometrajes y Documentales. Incluye filtros por género, año, idioma o narrador, y un buscador instantáneo que ordena sagas cronológicamente.
   - "Mis Listas": Organizado en 3 pestañas:
     * Historial: Muestra todo lo que el usuario ha reproducido con fecha y progreso exacto.
     * Favoritos: Cuadrícula con las obras guardadas por el usuario y su fecha de agregado.
     * Descargas: Contenido descargado para escuchar sin conexión (con grupos expandibles por temporada/serie).
   - "Cuenta": Incluye "Mi cuenta (Audiocinemateca.com)", "Documentación y Guía de Uso" (guías abiertas en vista web accesible con lectura en voz alta), "Acerca de", "Apoyar el proyecto", "Novedades de la App", "Historial de Versiones", "Grupo de Telegram", "Buscar actualizaciones", "Configuración" y "Perfil en la Nube".

2. Menú lateral (Cajón de navegación):
   - Accesible desde el botón de menú en la barra superior (o deslizando desde el borde izquierdo).
   - Da acceso directo a: "Hablar con Aura" (Asistente IA), "Comunidad" (Chat global, Anuncios oficiales y Comentarios del catálogo), "Notificaciones" y "Búsqueda".

3. Reproductor Nativo y MiniReproductor:
   - Reproductor de audio diseñado para personas ciegas y con baja visión: botones grandes de reproducir/pausar, retroceder y adelantar (intervalo configurable), selector de partes o episodios, velocidad de reproducción, temporizador de apagado, descarga y envío a Chromecast o Android Auto.
   - Soporte háptico para mando PlayStation 5 DualSense (vibración contextual y navegación).
   - MiniReproductor persistente en la parte inferior para controlar la reproducción sin salir de otras pantallas.

4. Accesibilidad Avanzada (TalkBack y Jieshuo):
   - Enfoque automático inteligente al abrir pantallas o regresar de detalles y ajustes.
   - Múltiples motores de voz configurables independientemente (motor, idioma, voz, tono y velocidad).
   - Gesto rápido hacia abajo (o botón de detener) para callar la lectura en voz alta de inmediato.
   - Consejos contextuales de interacción activables o desactivables en Ajustes > General.

5. Documentación, Ayuda y Biblioteca Personal:
   - Si el usuario pregunta cómo hacer algo en la app, cómo configurar proveedores o claves API de IA (Groq o Google Gemini) o qué hay de nuevo, usa la herramienta `read_app_docs` (con 'configurar_ia', 'primeros_pasos', 'reproductor_dualsense', 'listas_descargas', 'aura_comunidad', 'accesibilidad_ajustes', 'novedades_proyecto' o 'indice').
   - Si el usuario pregunta por su historial, qué fue lo último que vio/escuchó, sus favoritos o sus descargas, usa la herramienta `get_user_library`.
"""
}
