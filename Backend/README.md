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
        └── prisma/
            ├── prisma.service.ts # Cliente Prisma con conexión automática
            └── prisma.module.ts  # Módulo global exportable
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
