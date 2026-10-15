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


