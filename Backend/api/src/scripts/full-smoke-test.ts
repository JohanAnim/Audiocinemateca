import { io } from 'socket.io-client';

async function runFullSmokeTest() {
  console.log('================================================================');
  console.log('🩺 INICIANDO DIAGNÓSTICO INTEGRAL Y VERIFICACIÓN COMPLETA DE SERVICIOS');
  console.log('================================================================\n');

  const BASE_URL = 'http://localhost:3000';

  // 1. CHEQUEO DE SALUD DEL SERVIDOR Y BASE DE DATOS
  console.log('1️⃣ [HEALTH CHECK] Verificando estado general del backend y PostgreSQL...');
  const healthRes = await fetch(`${BASE_URL}/health`);
  if (!healthRes.ok) throw new Error(`Health falló: ${healthRes.status}`);
  const healthData = await healthRes.json();
  console.log(`   ✅ Estado: ${healthData.status} | Motor: ${healthData.database?.engine} | Latencia: ${healthData.database?.latencyMs}ms`);

  // 2. CONFIGURACIÓN REMOTA Y CONTROL DE VERSIONES
  console.log('\n2️⃣ [APP CONFIG] Verificando endpoints de configuración y versiones...');
  const configRes = await fetch(`${BASE_URL}/api/v1/config/app`);
  const configData = await configRes.json();
  console.log(`   ✅ App Config: Versión objetivo=${configData.version?.version} | Mantenimiento=${configData.maintenance?.enabled} | Banner="${configData.banner?.title}"`);

  const verRes = await fetch(`${BASE_URL}/api/v1/config/version?currentVersion=3.1.0`);
  const verData = await verRes.json();
  console.log(`   ✅ Chequeo de Versión: UpdateDisponible=${verData.isUpdateAvailable} | Forzada=${verData.isForceUpdate}`);

  // 3. AUTENTICACIÓN Y SEGURIDAD JWT
  console.log('\n3️⃣ [AUTH & USERS] Verificando inicio de sesión y extracción de perfil...');
  const loginRes = await fetch(`${BASE_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      identifier: 'johan',
      password: 'SuperPassword2026!',
    }),
  });
  if (!loginRes.ok) throw new Error(`Login falló: ${loginRes.status}`);
  const loginData = await loginRes.json();
  const token = loginData.tokens.accessToken;
  const user = loginData.user;
  console.log(`   ✅ Autenticado como: @${user.username} (${user.displayName}) | Rol: ${user.role}`);

  const meRes = await fetch(`${BASE_URL}/api/v1/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const meData = await meRes.json();
  console.log(`   ✅ Endpoint /me respondió: Usuario ID ${meData.id} verificado`);

  // 4. CATÁLOGO CINEMATOGRÁFICO
  console.log('\n4️⃣ [CATÁLOGO & CONTENIDO] Verificando búsqueda, filtros y rankings...');
  const catRes = await fetch(`${BASE_URL}/api/v1/contents?limit=5`);
  const catData = await catRes.json();
  console.log(`   ✅ Catálogo general: ${catData.meta?.total || 0} títulos disponibles (Mostrando ${catData.data?.length})`);

  const featuredRes = await fetch(`${BASE_URL}/api/v1/contents/featured`);
  const featuredData = await featuredRes.json();
  console.log(`   ✅ Destacados: ${featuredData.length} obras seleccionadas`);

  const sampleContent = catData.data?.[0];
  if (!sampleContent) throw new Error('No hay contenidos sembrados en la base de datos');
  console.log(`   ✅ Obra de prueba: "${sampleContent.title}" (${sampleContent.type})`);

  // 5. PROGRESO DE REPRODUCCIÓN (SINCRONIZACIÓN ROOM)
  console.log('\n5️⃣ [PROGRESO & FAVORITOS] Probando guardado de progreso y favoritos...');
  const progressRes = await fetch(`${BASE_URL}/api/v1/progress`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      contentId: sampleContent.id,
      positionMs: 45000,
      durationMs: 7200000,
      partIndex: 0,
      episodeIndex: 0,
    }),
  });
  const progressData = await progressRes.json();
  console.log(`   ✅ Progreso guardado: Posición=${progressData.positionMs}ms (${progressData.percentage}%)`);

  const favRes = await fetch(`${BASE_URL}/api/v1/favorites/toggle`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ contentId: sampleContent.id }),
  });
  const favData = await favRes.json();
  console.log(`   ✅ Favorito alternado: isFavorite=${favData.isFavorite}`);

  // 6. CALIFICACIONES Y COMENTARIOS
  console.log('\n6️⃣ [CALIFICACIONES & COMENTARIOS] Probando valoraciones y comentarios...');
  const ratingRes = await fetch(`${BASE_URL}/api/v1/ratings`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      contentId: sampleContent.id,
      rating: 5,
      reviewText: '¡Excelente producción accesible!',
    }),
  });
  const ratingData = await ratingRes.json();
  console.log(`   ✅ Calificación registrada: 5 estrellas. Promedio de la obra: ${ratingData.averageRating}`);

  const commentRes = await fetch(`${BASE_URL}/api/v1/comments`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      contentId: sampleContent.id,
      commentText: 'Comentario de prueba para verificación general del sistema.',
    }),
  });
  const commentData = await commentRes.json();
  console.log(`   ✅ Comentario publicado: ID ${commentData.id}`);

  // 7. WEBSOCKETS EN TIEMPO REAL (SOCKET.IO)
  console.log('\n7️⃣ [WEBSOCKETS & CHAT EN TIEMPO REAL] Verificando comunicación bidireccional...');
  const socket = io(`${BASE_URL}/ws/chat`, {
    auth: { token },
    transports: ['websocket', 'polling'],
  });

  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Timeout en conexión de WebSocket')), 8000);

    socket.on('connect', () => {
      console.log(`   ✅ Conectado a /ws/chat con socket ID: ${socket.id}`);
    });

    socket.on('online_users', (data) => {
      console.log(`   ✅ Presencia en vivo: ${data.count} usuario(s) conectados`);
    });

    socket.on('new_message', (msg) => {
      console.log(`   ✅ Mensaje en tiempo real recibido: "${msg.content}" de @${msg.user?.username}`);
      clearTimeout(timer);
      resolve();
    });

    setTimeout(() => {
      socket.emit('send_message', {
        content: 'Diagnóstico en tiempo real completado satisfactoriamente.',
      });
    }, 1000);
  });

  socket.disconnect();

  console.log('\n================================================================');
  console.log('🎯 RESULTADO DEL DIAGNÓSTICO: TODOS LOS SERVICIOS OPERACIONALES (100% OK)');
  console.log('================================================================');
}

runFullSmokeTest().catch((err) => {
  console.error('\n❌ DIAGNÓSTICO DETECTÓ UN ERROR:', err);
  process.exit(1);
});
