# Backend Audiocinemateca 🎬🎧

Arquitectura de backend moderna, desacoplada e independiente para **Audiocinemateca**, diseñada para reemplazar por completo la dependencia de Firebase y preparada para despliegue en la nube (Dokploy / Oracle Cloud).

---

## 🛠️ Stack Tecnológico

- **Entorno Local:** Windows 11 + Docker Desktop + PowerShell 7.
- **Orquestación:** Docker Compose (`compose.yaml`).
- **Base de Datos:** PostgreSQL 16 Alpine en contenedor (volumen persistente `audiocinemateca_postgres_data`).
- **API Framework:** NestJS 12 + TypeScript (Modo ESM, compilación estricta).
- **ORM & Migraciones:** Prisma ORM 6 (Type-safe, migraciones declarativas y relaciones íntegras).
- **Próximos componentes:** Keycloak (Identidad/Tokens) y SeaweedFS (Almacenamiento de audio/archivos).

---

## 📁 Estructura del Proyecto

```text
Backend/
├── compose.yaml          # Orquestador Docker (PostgreSQL aislado y optimizado)
├── .env                  # Variables de entorno locales
├── .env.example          # Plantilla pública de variables
├── .gitignore            # Exclusión de credenciales y dependencias
├── README.md             # Esta documentación
└── api/                  # Código fuente de la API NestJS
    ├── Dockerfile        # Construcción multi-stage para Dokploy / Producción
    ├── .dockerignore
    ├── .env              # Conexión local a PostgreSQL y puertos
    ├── package.json
    ├── prisma/
    │   ├── schema.prisma # Esquema relacional completo (12 tablas)
    │   └── migrations/   # Historial de migraciones SQL
    └── src/
        ├── main.ts       # Arranque, CORS, pipes de validación y shutdown hooks
        ├── app.module.ts # Módulo raíz con ConfigModule y PrismaModule
        ├── app.controller.ts
        ├── app.service.ts # Health check con ping real a PostgreSQL
        ├── prisma/
        │   ├── prisma.service.ts # Cliente Prisma con conexión automática
        │   └── prisma.module.ts  # Módulo global exportable
        └── modules/
            ├── users/            # Gestión de usuarios, perfiles y presencia
            └── auth/             # Autenticación multi-método (Local, Google, Keycloak, JWT)
```

---

## 🚀 Comandos Rápidos

### 1. Iniciar Base de Datos (PostgreSQL)
Desde la carpeta `Backend/`:
```powershell
docker compose up -d
```
Para detenerla:
```powershell
docker compose down
```

### 2. Iniciar API NestJS en Desarrollo
Desde la carpeta `Backend/api/`:
```powershell
npm run start:dev
```
O compilar e iniciar:
```powershell
npm run build
npm run start:prod
```

### 3. Verificar Salud del Servidor
```powershell
curl http://localhost:3000/health
```

Respuesta esperada:
```json
{
  "name": "Audiocinemateca API",
  "version": "1.0.0",
  "status": "operational",
  "database": {
    "engine": "PostgreSQL 16",
    "status": "healthy",
    "latencyMs": 5
  }
}
```

### 4. Administrar Base de Datos con Prisma Studio (Visual)
Para abrir la interfaz visual en tu navegador:
```powershell
npx prisma studio
```

---

## 🔐 Endpoints de Autenticación (`/api/v1/auth`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Público | Registro local (email, username, password, displayName) |
| `POST` | `/api/v1/auth/login` | Público | Inicio de sesión local (email/usuario + password) |
| `POST` | `/api/v1/auth/google` | Público | Inicio de sesión con Google Sign-In (`idToken`) |
| `POST` | `/api/v1/auth/keycloak` | Público | Inicio de sesión con Keycloak OIDC (`token`) |
| `POST` | `/api/v1/auth/refresh` | Público | Renovación de access token mediante `refreshToken` |
| `GET` | `/api/v1/auth/me` | Protegido (Bearer) | Obtener perfil del usuario autenticado |
| `PATCH`| `/api/v1/auth/profile` | Protegido (Bearer) | Actualizar datos del perfil (`displayName`, `avatarUrl`) |
| `POST` | `/api/v1/auth/presence`| Protegido (Bearer) | Actualizar presencia en línea (`isOnline`) |

---

## 🎬 Endpoints de Catálogo y Contenido (`/api/v1/contents`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/contents` | Público | Lista paginada con filtros (`type`, `genre`, `search`, `year`, `sortBy`, `order`, `page`, `limit`) |
| `GET` | `/api/v1/contents/featured` | Público | Contenidos destacados para el feed principal |
| `GET` | `/api/v1/contents/rankings` | Público | Rankings de más vistos y mejor valorados |
| `GET` | `/api/v1/contents/categories` | Público | Lista de categorías y conteo de obras |
| `GET` | `/api/v1/contents/:id` | Público (opc. Auth) | Detalle completo de película o serie por ID, slug o externalId con temporadas y capítulos |
| `POST` | `/api/v1/contents/:id/view` | Público | Registrar reproducción / incremento de vista |
| `POST` | `/api/v1/contents` | Protegido | Crear nuevo contenido (película o serie con episodios) |

---

### 5. Sembrar Datos Iniciales del Catálogo
Para importar una muestra inicial de películas, series completas y documentales a la base de datos:
```powershell
npm run seed
```

---

## ⏱️ Endpoints de Progreso de Reproducción (`/api/v1/progress`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/progress` | Protegido (Bearer) | Guardar/actualizar progreso en ms (`contentId`, `positionMs`, `durationMs`, `partIndex`, `episodeIndex`) |
| `GET` | `/api/v1/progress/continue-listening` | Protegido (Bearer) | Obtener lista de "Continuar Escuchando" ordenada por fecha reciente |
| `GET` | `/api/v1/progress/:contentId` | Protegido (Bearer) | Obtener progreso guardado para una obra específica |
| `DELETE`| `/api/v1/progress/:contentId` | Protegido (Bearer) | Eliminar progreso de una obra (o capítulo mediante `?part=X&episode=Y`) |
| `DELETE`| `/api/v1/progress` | Protegido (Bearer) | Limpiar todo el historial de reproducción del usuario |

---

## ⭐ Endpoints de Favoritos (`/api/v1/favorites`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/favorites/toggle` | Protegido (Bearer) | Alternar favorito (agrega si no existe, remueve si ya existía) |
| `GET` | `/api/v1/favorites` | Protegido (Bearer) | Lista paginada de favoritos del usuario con metadatos completos |
| `GET` | `/api/v1/favorites/check/:contentId` | Protegido (Bearer) | Comprobar rápidamente si una obra está en favoritos (`{ isFavorite: boolean }`) |
| `DELETE`| `/api/v1/favorites/:contentId` | Protegido (Bearer) | Remover obra de favoritos |
| `DELETE`| `/api/v1/favorites` | Protegido (Bearer) | Vaciar lista completa de favoritos |

---

## 🌟 Endpoints de Calificaciones (`/api/v1/ratings`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/ratings` | Protegido (Bearer) | Calificar con 1-5 estrellas y reseña opcional (recalcula promedio en vivo) |
| `GET` | `/api/v1/ratings/:contentId` | Público (opc. Auth) | Obtener promedio, total y la calificación del usuario |
| `DELETE`| `/api/v1/ratings/:contentId` | Protegido (Bearer) | Eliminar calificación y recalcular promedio de la obra |

---

## 💬 Endpoints de Comentarios (`/api/v1/comments`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/comments` | Protegido (Bearer) | Publicar comentario en una obra o capítulo (`contentId`, `partIndex`, `episodeIndex`, `commentText`) |
| `GET` | `/api/v1/comments` | Público (opc. Auth) | Listar comentarios paginados con autor y estado de like |
| `GET` | `/api/v1/comments/preview` | Público | Vista previa con total de comentarios y el más reciente |
| `POST` | `/api/v1/comments/:id/like` | Protegido (Bearer) | Alternar "Me gusta" a un comentario |
| `DELETE`| `/api/v1/comments/:id` | Protegido (Bearer) | Eliminar comentario (autor o moderador) |

---

## 🌐 WebSockets y Chat Global en Tiempo Real (`/ws/chat`)

La comunicación bidireccional y en tiempo real utiliza Socket.IO sobre el namespace `/ws/chat`, autenticado automáticamente mediante el token JWT en el handshake.

### Conexión del Cliente (Android / Web / Node):
```typescript
import { io } from 'socket.io-client';

const socket = io('http://localhost:3000/ws/chat', {
  auth: { token: 'MI_ACCESS_TOKEN_JWT' },
  transports: ['websocket', 'polling'],
});
```

### Eventos del Socket:

| Evento | Dirección | Payload | Descripción |
| :--- | :--- | :--- | :--- |
| `send_message` | Cliente → Servidor | `{ content: string }` | Enviar mensaje al chat global |
| `new_message` | Servidor → Clientes | `ChatMessage` (con usuario anidado) | Mensaje transmitido a todos los clientes conectados |
| `typing` | Cliente → Servidor | `{ isTyping: boolean }` | Notificar estado de escritura |
| `user_typing` | Servidor → Clientes | `{ userId, username, isTyping }` | Indicador de quién está escribiendo |
| `delete_message`| Cliente → Servidor | `{ messageId: string }` | Eliminar mensaje propio (o admin) |
| `message_deleted`| Servidor → Clientes| `{ messageId: string }` | Notificación de eliminación a los clientes |
| `online_users` | Servidor → Clientes | `{ count: number, users: User[] }` | Lista en vivo de usuarios activos conectados |

---

## 📢 Endpoints de Comunidad, Moderación y Anuncios (`/api/v1/community`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/community/chat/messages` | Protegido (Bearer) | Historial paginado de mensajes de chat global |
| `GET` | `/api/v1/community/online` | Público | Lista de usuarios actualmente conectados al chat |
| `GET` | `/api/v1/community/announcements` | Público | Lista de anuncios oficiales de la plataforma |
| `POST` | `/api/v1/community/announcements` | Protegido (Admin) | Publicar un anuncio oficial |
| `DELETE`| `/api/v1/community/announcements/:id`| Protegido (Admin) | Eliminar un anuncio oficial |
| `POST` | `/api/v1/community/moderation/ban` | Protegido (Admin) | Suspender a un usuario del chat (temporal o permanente) |
| `POST` | `/api/v1/community/moderation/unban` | Protegido (Admin) | Levantar la suspensión a un usuario |

---

## ⚙️ Endpoints de Configuración Remota y Actualizaciones (`/api/v1/config`)

Reemplazo completo de Firebase Remote Config. Proporciona control de versiones para la app Android, parámetros de mantenimiento, banners globales del feed, límites de chat y enlaces de soporte, con respuestas en sub-milisegundos mediante caché en memoria.

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/config/app` | Público | Configuración completa y consolidada para inicializar la app Android (versión, banners, mantenimiento, donaciones) |
| `GET` | `/api/v1/config/version` | Público | Comprobación de actualizaciones (parámetro `?currentVersion=X.Y.Z`). Devuelve `isUpdateAvailable`, `isForceUpdate` y el objeto `UpdateInfo` de Android |
| `GET` | `/api/v1/config/banner` | Público | Obtener el banner o aviso activo en el feed principal |
| `GET` | `/api/v1/config/all` | Protegido (Admin) | Listado de todas las configuraciones almacenadas en base de datos |
| `GET` | `/api/v1/config/:key` | Protegido (Admin) | Consultar valor de una configuración por su clave |
| `PUT` | `/api/v1/config/:key` | Protegido (Admin) | Crear o actualizar configuración (`value`, `description`) invalidando la caché |
| `DELETE`| `/api/v1/config/:key` | Protegido (Admin) | Eliminar una configuración del sistema |
| `POST` | `/api/v1/config/seed` | Protegido (Admin) | Re-sembrar configuraciones por defecto |---

## 📊 Endpoints de Administración y Métricas (`/api/v1/admin`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/admin/stats` | Protegido (Admin/Mod) | Métricas ejecutivas del sistema: recuento de usuarios, distribución por plataformas (Android, Windows, macOS, Linux, Web), catálogo total, horas reproducidas, valoraciones y telemetría de memoria/uptime del servidor |

---

## 👥 Endpoints de Gestión de Usuarios (`/api/v1/users`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/users` | Protegido (Admin/Mod) | Listado paginado con búsqueda por nombre, email o UID, dispositivos (`deviceInfo`), estado online y obra en escucha |
| `GET` | `/api/v1/users/:id` | Protegido (Admin/Mod) | Ficha completa y detallada de un usuario |
| `PATCH` | `/api/v1/users/:id/role` | Protegido (Admin) | Cambiar rol de usuario (`USER`, `MODERATOR`, `ADMIN`) |
| `DELETE` | `/api/v1/users/:id` | Protegido (Admin) | Eliminar cuenta de usuario de la plataforma |

---

## 🖥️ Panel de Control de Administración (Angular 22 Standalone) (`Backend/admin`)

Ubicado en `Backend/admin`, preparado para publicarse en `audiocinemateca.johanag.dev` o ejecutarse localmente:

### Características Principales:
1. **Autenticación Estricta de Administradores:**
   - Inicio de sesión seguro con JWT, validación reactiva de roles (`ADMIN` / `MODERATOR`) y guardia de rutas `authGuard`.
2. **Dashboard Ejecutivo en Tiempo Real:**
   - Tarjetas de KPIs: Usuarios totales, Contenidos en catálogo, Vistas acumuladas, Promedio de valoraciones.
   - Gráficas y desglose de dispositivos y plataformas activas (Android, Windows, macOS, Linux, Web).
   - Estado de salud del nodo y consumo de memoria en tiempo real.
3. **Catálogo Exclusivo y Creador de Fichas Cinematográficas:**
   - Alta completa de películas, series, documentales y cortometrajes.
   - Selector multilingüe de audiodescripción: *Español Latino*, *Español de España*, *Inglés*, etc.
   - Constructor jerárquico dinámico de temporadas y episodios para series con URLs de audio y duración en milisegundos.
   - Lista interactiva de contenidos con filtros, enlaces directos y eliminación protegida.
4. **Moderación de Usuarios y Sanciones:**
   - Visualización de UIDs de usuarios, correos, nombres, rol, dispositivo registrado (`deviceInfo`) y estado de escucha actual.
   - Modal de sanciones: suspensión temporal (horas/días) o permanente con motivo auditable, y levantamiento de veto instantáneo.
   - Asignación de roles administrativos y eliminación de cuentas.
5. **Comunidad, Chat en Vivo y Comunicados Oficiales:**
   - Consola de streaming de mensajes mediante WebSockets (`/ws/chat`) en tiempo real con indicador de usuarios en línea.
   - Eliminación instantánea de mensajes ofensivos o spam con broadcast inmediato a todos los clientes.
   - Publicación y gestión de comunicados oficiales para la comunidad.
6. **Configuración Remota y Actualizador de la Aplicación:**
   - Control de versiones de la app Android (APK): versión, `versionCode`, changelog, enlace directo, versión mínima y toggle de actualización forzada.
   - Gestor de banner global en el Feed con previsualización en vivo (tipos: info, warning, alert, update).
   - Interruptor global de Modo Mantenimiento.
   - Parámetros y reglas del chat comunitario (modo lento, longitud de mensaje).
   - Canales oficiales de soporte y donaciones (PayPal, PayPal.Me).
7. **Centro de Agentes de IA y Automatizaciones:**
   - Configuración de Aura 2.0 (modelo de lenguaje, temperatura, system prompt maestro, activación de herramientas `play_content` y búsqueda de catálogo).
   - Emisor de Notificaciones Push masivas (FCM) dirigido a dispositivos Android y clientes.
   - Automatizaciones Cron (purga de mensajes antiguos, auditoría de enlaces de streaming, recálculo diario de tendencias y backups).

### Comandos de Ejecución Local del Panel:
```powershell
# En la carpeta Backend/admin:
npm install
npm start
# Abre automáticamente http://localhost:4200
```

### Compilación para Producción:
```powershell
npm run build
# Genera los archivos estáticos en dist/admin/browser listos para Nginx o Dokploy
```

