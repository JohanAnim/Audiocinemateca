# Audiocinemateca para Android

Este repositorio contiene el código fuente oficial del cliente de Android para el proyecto **Audiocinemateca**. La aplicación permite a los usuarios acceder y disfrutar del extenso catálogo de contenido de audio directamente desde sus dispositivos Android.

La finalidad principal del proyecto es ofrecer una experiencia de usuario nativa, rápida y accesible para consumir el contenido de la Audiocinemateca, que incluye audiodescripciones de películas, series, documentales y más.

## Características Principales

*   **Catálogo Completo:** Navega por todo el contenido disponible, incluyendo películas, series, documentales y cortometrajes, organizados por categorías.
*   **Reproductor de Audio Avanzado:** Disfruta de una experiencia de reproducción fluida con controles avanzados, soporte para reproducción en segundo plano y gestión de la sesión de audio.
*   **Gestión de Cuenta:** Inicia sesión para sincronizar tu actividad, como el historial de reproducción y tus listas personales.
*   **Historial de Reproducción:** Lleva un registro de todo lo que has visto y reanuda la reproducción fácilmente desde donde la dejaste.
*   **Listas Personales:** Crea y gestiona tus propias listas, como "Favoritos", para tener siempre a mano el contenido que más te gusta.
*   **Búsqueda Inteligente:** Encuentra rápidamente cualquier contenido del catálogo utilizando la función de búsqueda integrada, que también guarda tu historial de búsquedas.
*   **Interfaz Moderna y Accesible:** Una interfaz de usuario limpia, intuitiva y diseñada siguiendo las guías de Material Design para una fácil navegación.
*   **Actualizaciones en la App:** La aplicación puede buscar y notificar al usuario sobre nuevas versiones, permitiendo la descarga e instalación de la actualización directamente.

## Compilación e Instalación

Sigue estos pasos para compilar el código fuente y ejecutar la aplicación en un dispositivo o emulador.

### Requisitos

*   **Android Studio:** Se recomienda la última versión estable.
*   **JDK:** Versión 11 o superior.
*   **Dispositivo Android:** Un dispositivo con Android 5.0 (API 21) o superior.

### Pasos para Compilar

1.  **Clonar el Repositorio:**
    ```bash
    git clone https://github.com/JohanAnim/Audiocinemateca.git
    ```
2.  **Abrir en Android Studio:**
    *   Abre Android Studio.
    *   Selecciona `File > Open` y navega hasta el directorio donde clonaste el proyecto.
    *   Espera a que Gradle sincronice todas las dependencias del proyecto.

3.  **Compilar desde la Línea de Comandos (Opcional):**
    Puedes generar un APK de depuración ejecutando el siguiente comando en la raíz del proyecto:

    *   En Windows:
        ```bash
        .\gradlew assembleDebug
        ```
    *   En macOS/Linux:
        ```bash
        ./gradlew assembleDebug
        ```
    El APK generado se encontrará en `app/build/outputs/apk/debug/audiocinemateca_debug.apk`.

### Instalación

1.  **Desde Android Studio:**
    *   Conecta tu dispositivo Android a tu computadora o inicia un emulador.
    *   Asegúrate de que la depuración por USB esté habilitada en tu dispositivo.
    *   Selecciona tu dispositivo en la barra de herramientas de Android Studio y presiona el botón `Run 'app'` (Shift+F10).

2.  **Instalación Manual del APK:**
    *   Transfiere el archivo `.apk` (generado en los pasos de compilación) a tu dispositivo Android.
    *   Abre un explorador de archivos en tu dispositivo, busca el APK y tócalo para instalarlo.
    *   Es posible que necesites habilitar la opción "Instalar aplicaciones de fuentes desconocidas" en la configuración de seguridad de tu dispositivo.

## Arquitectura y Tecnologías

La aplicación está construida siguiendo las mejores prácticas de desarrollo de Android y una arquitectura moderna.

*   **Lenguaje:** 100% [Kotlin](https://kotlinlang.org/).
*   **Arquitectura:** MVVM (Model-View-ViewModel) sobre una Arquitectura Limpia (Clean Architecture).
*   **Inyección de Dependencias:** [Hilt](https://dagger.dev/hilt/) para la gestión de dependencias.
*   **Networking:** [Retrofit](https://square.github.io/retrofit/) y [OkHttp](https://square.github.io/okhttp/) para las llamadas a la API.
*   **Base de Datos Local:** [Room](https://developer.android.com/training/data-storage/room) para el almacenamiento en caché del catálogo y datos de usuario.
*   **Asincronía:** [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) de Kotlin para gestionar tareas en segundo plano.
*   **Navegación:** [Android Navigation Component](https://developer.android.com/guide/navigation).
*   **Reproducción Multimedia:** [ExoPlayer (Media3)](https://developer.android.com/guide/topics/media/media3).

## Desarrolladores

*   **[Johan G.](https://github.com/JohanAnim)**
*   **[César Verástegui (metalalchemist)](https://github.com/metalalchemist)**

## Agradecimientos y Derechos

Extendemos un agradecimiento especial a **José Manuel Delicado**, creador de la página oficial [audiocinemateca.com](https://audiocinemateca.com).

Todos los derechos sobre el contenido reproducido en esta aplicación pertenecen exclusivamente a él y a audiocinemateca.com. Esta aplicación es una extensión que busca acercar su valioso contenido a la comunidad de usuarios de Android.
