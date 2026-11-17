# Configuración de Inteligencia Artificial

Para que **Aura** cobre vida en tu teléfono con la máxima velocidad y sin límites compartidos con otras personas, Audiocinemateca te permite conectar directamente tu propia **Clave API gratuita** de **Groq** o de **Google Gemini**.

No necesitas conocimientos técnicos ni tarjeta bancaria: solo eliges el proveedor que prefieras, creas tu clave gratuita en su página oficial y la pegas dentro de la aplicación. Tu clave se guarda únicamente en tu dispositivo y nunca se comparte con terceros.

---

## ¿Qué proveedor me conviene elegir?

* **Groq (Recomendado para la mayoría):** Es gratis, responde casi al instante y ofrece varios de los mejores modelos actuales sin complicaciones. Ideal si buscas fluidez total al charlar con Aura.
* **Google Gemini (Excelente alternativa):** Muy bueno entendiendo conversaciones largas y matices del español, y se crea usando tu cuenta habitual de Google.

*(Incluso puedes configurar ambos y cambiar entre uno y otro cuando quieras desde el chat o desde Ajustes).*

---

## Opción 1: Cómo crear tu Clave API de Groq (Gratis y muy rápida)

* **Enlace directo oficial:** [Crear Clave API en Groq Console](https://console.groq.com/keys)

Sigue estos pasos con calma (la página funciona muy bien con lectores de pantalla):

1. Pulsa el enlace de arriba **[Crear Clave API en Groq Console](https://console.groq.com/keys)** (también puedes ir en la app a **Cuenta -> Configuración -> Inteligencia Artificial** y pulsar el botón **Crear o copiar Clave API de Groq**).
2. Inicia sesión gratuitamente usando tu cuenta de Google, GitHub o correo electrónico.
3. Una vez dentro de la sección de claves, busca y pulsa el botón **Create API Key** (Crear clave API).
4. Se abrirá un cuadro pidiendo un nombre para identificar tu clave. Escribe, por ejemplo, `Audiocinemateca` y pulsa el botón **Submit** (Enviar).
5. Aparecerá tu nueva clave en pantalla (comienza por `gsk_...`). Pulsa el botón **Copy** (Copiar).
   * *Consejo importante:* Copia la clave en ese mismo instante y pégala en Audiocinemateca, porque por seguridad la página solo te la muestra completa una vez.
6. Vuelve a **Audiocinemateca**, entra en **Cuenta -> Configuración -> Inteligencia Artificial**:
   * En **Proveedor de Inteligencia Artificial**, asegúrate de tener seleccionado **Groq (Recomendado • Ultra rápido)**.
   * Entra en **Establecer Clave API de Groq**, pega tu clave y guarda. La app detectará los modelos automáticamente.

### Modelos recomendados en Groq

* **GPT-OSS 120B (Recomendado):** El más completo, cálido y preciso para conversar con Aura, consultar el catálogo, revisar tu historial y darte recomendaciones detalladas.
* **Llama 3.3 70B Versatile:** Muy natural y ágil cuando prefieres respuestas directas sin pausas de pensamiento profundo.
* **Llama 3.1 8B Instant:** El más ligero y veloz de todos, perfecto para preguntas rápidas o conexiones de internet lentas.
* **Otras buenas opciones disponibles:** *Qwen 3 32B*, *DeepSeek R1 70B*, *GPT-OSS 20B* y *Llama 4 Scout 17B*.

---

## Opción 2: Cómo crear tu Clave API de Google Gemini

* **Enlace directo oficial:** [Crear Clave API en Google AI Studio](https://aistudio.google.com/app/apikey)

Sigue estos pasos exactos:

1. Pulsa el enlace **[Crear Clave API en Google AI Studio](https://aistudio.google.com/app/apikey)** (también accesible desde **Cuenta -> Configuración -> Inteligencia Artificial** cuando eliges Google Gemini).
2. Inicia sesión con tu cuenta normal de Google. Si es la primera vez que entras, marca la casilla y acepta los términos del servicio.
3. Busca y pulsa el botón **Create API key** (Crear clave de API).
4. Si la página te pregunta dónde crearla, elige la opción **Create API key in new project** (Crear clave de API en un proyecto nuevo).
5. Espera unos segundos a que se genere la clave (empieza por `AIzaSy...`) y pulsa el botón **Copy** (Copiar).
6. Regresa a **Audiocinemateca**, entra en **Cuenta -> Configuración -> Inteligencia Artificial**:
   * En **Proveedor de Inteligencia Artificial**, selecciona **Google Gemini**.
   * Abre **Establecer Clave API de Google Gemini**, pega la clave y guarda.

### Modelos recomendados en Google Gemini

* **Gemini 2.5 Flash (Predeterminado):** La opción más estable y rápida para conversar con Aura, consultar el catálogo y obtener respuestas inmediatas sin errores de saturación.
* **Gemini 2.5 Flash-Lite:** Alternativa aún más ligera y veloz para respuestas simples.
* **Gemini 3.8 Flash / Gemini 4 Hydrogen:** Modelos más recientes de razonamiento avanzado para análisis profundos cuando haya disponibilidad en los servidores de Google.
* **Modelos Gemma 4:** Opciones abiertas adicionales también compatibles con la app.

---

## Ajustes recomendados dentro de Inteligencia Artificial

En **Cuenta -> Configuración -> Inteligencia Artificial** también puedes afinar cómo responde Aura:

* **Esfuerzo de razonamiento:** Elige cuánto analiza Aura antes de contestarte en modelos compatibles entre cuatro niveles: **Bajo**, **Medio (Predeterminado)**, **Alto** o **Extra (solo en modelos compatibles)**.
* **Voz de Aura:** Elige si deseas escuchar a Aura con el **TTS del dispositivo (Predeterminado)** o activar la **Voz expresiva de Aura (Google Gemini)** con modelos de voz dedicados y distintos timbres (requiere tener guardada una Clave API de Google Gemini, aunque uses Groq para el chat, y cuenta con respaldo automático al TTS del dispositivo).
* **Comprobar estado de la conexión IA:** Usa esta opción después de pegar tu clave para confirmar en segundos que todo está conectado y funcionando bien.
* **Funciones extra con IA:** Puedes activar la clasificación informativa de contenido antes de reproducir y los mensajes cálidos creados por Aura al compartir una película con tus amigos.
