# Stage 1: Compilar la aplicación Angular 22
FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Servir con Nginx Alpine ultraligero (<25MB)
FROM nginx:alpine

COPY --from=build /app/dist/admin/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
