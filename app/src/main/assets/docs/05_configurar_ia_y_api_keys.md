# Configuración de Inteligencia Artificial

Para que **Aura** cobre vida en tu teléfono con la máxima velocidad y sin límites compartidos con otras personas, Audiocinemateca te permite conectar directamente tu propia **Clave API gratuita** de **Groq** o de **Google Gemini**.

No necesitas conocimientos técnicos ni tarjeta bancaria: solo eliges el proveedor que prefieras, creas tu clave gratuita en su página oficial y la pegas dentro de la aplicación. Tu clave se guarda únicamente en tu dispositivo y nunca se comparte con terceros.

---

## ¿Qué proveedor me conviene elegir?

* **Groq (Recomendado para respuestas veloces):** Es gratis y responde casi al instante con varios modelos muy capaces. Ideal para charlas rápidas y fluidas; en conversaciones muy largas ajusta automáticamente el tamaño del historial cuando un modelo tiene límite estricto de tokens por minuto.
* **Google Gemini (Ideal para memoria extensa y voz expresiva):** Destaca en conversaciones muy largas (con caché automática de contexto), razonamiento profundo y matices del español, y se crea usando tu cuenta habitual de Google.

*(Puedes configurar ambas claves a la vez y cambiar de proveedor o de modelo de texto cuando quieras sin perder el modelo ni la voz que elegiste para leer las respuestas de Aura).*

---

## Opción 1: Cómo crear tu Clave API de Groq (Gratis y muy rápida)

* **Enlace directo oficial:** [Crear Clave API en Groq Console](https://console.groq.com/keys)

Sigue estos pasos con calma (la página funciona muy bien con lectores de pantalla):

1. Pulsa el enlace de arriba **[Crear Clave API en Groq Console](https://console.groq.com/keys)** (también puedes ir en la app a **Cuenta -> Configuración -> Inteligencia Artificial** y pulsar **Crear o copiar Clave API de Groq**).
2. Inicia sesión gratuitamente usando tu cuenta de Google, GitHub o correo electrónico.
3. Una vez dentro de la sección de claves, busca y pulsa el botón **Create API Key** (Crear clave API).
4. Escribe un nombre para identificarla, por ejemplo `Audiocinemateca`, y pulsa **Submit** (Enviar).
5. Aparecerá tu nueva clave en pantalla (comienza por `gsk_...`). Pulsa el botón **Copy** (Copiar) enseguida, porque por seguridad solo se muestra completa una vez.
6. Vuelve a **Audiocinemateca**, entra en **Cuenta -> Configuración -> Inteligencia Artificial**:
   * En **Proveedor de Inteligencia Artificial**, selecciona **Groq (Recomendado • Ultra rápido)**.
   * Entra en **Establecer Clave API de Groq**, pega tu clave y guarda. La app detectará los modelos disponibles automáticamente.

### Modelos recomendados en Groq

* **GPT-OSS 120B (Recomendado):** El más completo, cálido y preciso para conversar con Aura, consultar el catálogo, revisar tu historial y darte recomendaciones detalladas.
* **Llama 3.3 70B Versatile:** Muy natural y ágil cuando prefieres respuestas directas y fluidas.
* **Llama 3.1 8B Instant:** Ligero y veloz para preguntas rápidas.
* **Otras buenas opciones disponibles:** *Qwen 3 32B*, *Qwen 3.8*, *DeepSeek R1*, *GPT-OSS 20B* y *Llama 4*.

---

## Opción 2: Cómo crear tu Clave API de Google Gemini

* **Enlace directo oficial:** [Crear Clave API en Google AI Studio](https://aistudio.google.com/app/apikey)

Sigue estos pasos exactos:

1. Pulsa el enlace **[Crear Clave API en Google AI Studio](https://aistudio.google.com/app/apikey)** (también accesible desde **Cuenta -> Configuración -> Inteligencia Artificial** cuando eliges Google Gemini).
2. Inicia sesión con tu cuenta habitual de Google y acepta los términos del servicio si es tu primera vez.
3. Pulsa **Create API key** (Crear clave de API) y elige **Create API key in new project**.
4. Espera unos segundos a que se genere la clave (empieza por `AIzaSy...`) y pulsa **Copy** (Copiar).
5. Regresa a **Audiocinemateca**, entra en **Cuenta -> Configuración -> Inteligencia Artificial**:
   * En **Proveedor de Inteligencia Artificial**, selecciona **Google Gemini**.
   * Abre **Establecer Clave API de Google Gemini**, pega la clave y guarda.

### Modelos recomendados en Google Gemini

* **Gemini 2.5 Flash (Predeterminado):** La opción más equilibrada y estable para conversar con Aura, usar sus herramientas del catálogo y obtener respuestas rápidas con gran memoria de conversación.
* **Gemini 2.5 Flash-Lite:** Alternativa más ligera para respuestas rápidas.
* **Gemini 3 / 3.8 / Gemini 4:** Modelos recientes de razonamiento avanzado para análisis profundos.
* **Gemma 4 (26B / 31B):** Modelos abiertos rápidos totalmente compatibles con el sistema de herramientas de Aura y niveles de pensamiento (`minimal` en modo Bajo y `high` en Medio/Alto).

---

## Ajustes de voz y extras dentro de Inteligencia Artificial

En **Cuenta -> Configuración -> Inteligencia Artificial** también puedes personalizar cómo habla y trabaja Aura:

* **Forma de leer las respuestas de Aura:** Elige entre el **TTS del dispositivo (Predeterminado)** o **Voz expresiva de Aura (Google Gemini)** con modelos dedicados (como **Gemini 3.8 Flash TTS**) y distintos timbres de voz femenina, más un botón **Probar voz expresiva de Aura** para escuchar una muestra antes de decidir.
* **Independencia total de tu voz:** Tanto tu **modelo de voz y timbre de Gemini TTS** como tu **motor/voz del dispositivo** se conservan exactamente tal como los elegiste aunque cambies de modelo de texto o alternes entre **Groq** y **Google Gemini** *(incluso puedes chatear con Groq mientras Aura te habla usando tu clave de Gemini TTS)*.
* **Esfuerzo de razonamiento:** Ajusta cuánto piensa Aura antes de responder (**Bajo**, **Medio**, **Alto** o **Extra** en modelos compatibles).
* **Comprobar estado de la conexión IA:** Verifica en segundos que tu clave y el modelo seleccionado responden correctamente.
* **Clasificación profesional y mensajes al compartir:** Activa o desactiva la clasificación orientativa de contenido generada con IA (ej. `12+`, violencia, lenguaje, temas adultos) y los mensajes cálidos de Aura al compartir una obra.
