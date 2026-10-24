AJUSTES DE REPRODUCTOR, FOCO DE AUDIO Y PANEL SOCIAL RESTAURADO

1. RESTAURACIÓN DEL PANEL SOCIAL Y ELIMINACIÓN DEL "CARTELITO"
- Se mantuvo el panel social completo de Me gusta, No me gusta y Compartir.
- Se elimino el marco semantico envolvente ("cartelito") alrededor del titulo central. Ahora el titulo y cada boton de voto son elementos independientes que se leen limpiamente sin cuadros molestos.

2. OPCIÓN EN AJUSTES PARA MANEJAR FOCO DE AUDIO ("handle_audio_focus")
En la pantalla de Ajustes de Reproduccion (PlaybackSettingsScreen.kt) se agrego la nueva opcion:
"Manejar foco de audio" (activada por defecto).
- Cuando esta activada: si entra una llamada o alerta, la reproduccion se detiene automaticamente y se reanuda al finalizar la llamada.
- Cuando esta desactivada: la app no interrumpe la reproduccion ante llamadas o transmisiones externas en plataformas como Discord o TikTok en segundo plano.

VERIFICACIÓN DEL PROYECTO
Compilación Kotlin: Exitosa con 0 errores.
