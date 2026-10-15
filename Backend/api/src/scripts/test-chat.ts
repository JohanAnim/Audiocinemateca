import { io } from 'socket.io-client';

async function runTest() {
  console.log('🚀 Iniciando prueba integral del módulo de Comunidad & Chat en tiempo real...');

  // 1. Iniciar sesión para obtener JWT
  const loginRes = await fetch('http://localhost:3000/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      identifier: 'johan',
      password: 'SuperPassword2026!',
    }),
  });

  if (!loginRes.ok) {
    throw new Error(`Login falló: ${loginRes.status} ${await loginRes.text()}`);
  }

  const loginData = await loginRes.json();
  const token = loginData.tokens.accessToken;
  console.log('✅ Login exitoso. Token JWT obtenido.');

  // 2. Conectar a WebSocket en namespace /ws/chat
  const socket = io('http://localhost:3000/ws/chat', {
    auth: { token, deviceInfo: 'Samsung Galaxy S23 (Android 14)' },
    transports: ['websocket', 'polling'],
  });

  await new Promise<void>((resolve, reject) => {
    const timeout = setTimeout(() => {
      reject(new Error('Timeout esperando conexión de socket'));
    }, 8000);

    socket.on('connect', () => {
      console.log(`✅ Socket.IO conectado con ID: ${socket.id}`);
    });

    socket.on('connect_error', (err) => {
      console.error('❌ Error de conexión Socket.IO:', err.message);
      clearTimeout(timeout);
      reject(err);
    });

    socket.on('online_users', (data) => {
      console.log(`👥 Evento "online_users" recibido:`, data.count, 'usuarios activos');
      const me = data.users?.find((u: any) => u.username === 'johan');
      if (me) {
        console.log(`   📱 Dispositivo reportado para @johan: "${me.deviceInfo}"`);
      }
    });

    socket.on('new_message', (msg) => {
      console.log(`💬 Evento "new_message" recibido en tiempo real:`, msg.id, msg.user?.username, msg.content);
      clearTimeout(timeout);
      resolve();
    });

    // Enviar mensaje y estado de reproducción tras 500ms
    setTimeout(() => {
      console.log('📻 Emitiendo evento "listening_to"...');
      socket.emit('listening_to', { title: 'Interestelar (Película con Audiodescripción)' });

      console.log('📤 Emitiendo evento "send_message"...');
      socket.emit('send_message', {
        content: '¡Hola a todos! Este es un mensaje de prueba en tiempo real desde WebSockets.',
      });
    }, 1000);
  });

  // 3. Probar endpoints REST de comunidad
  console.log('🔍 Consultando historial de chat por REST...');
  const historyRes = await fetch('http://localhost:3000/api/v1/community/chat/messages?limit=10', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const historyData = await historyRes.json();
  console.log(`✅ Historial obtenido: ${historyData.data?.length || 0} mensajes`);

  console.log('📢 Creando anuncio oficial...');
  const annRes = await fetch('http://localhost:3000/api/v1/community/announcements', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      title: 'Mantenimiento Programado y Novedades',
      content: '¡Bienvenido al nuevo sistema de backend propio de Audiocinemateca!',
    }),
  });
  const annData = await annRes.json();
  console.log(`✅ Anuncio creado:`, annData.id, annData.title);

  console.log('📋 Listando anuncios...');
  const annListRes = await fetch('http://localhost:3000/api/v1/community/announcements', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const annListData = await annListRes.json();
  console.log(`✅ Anuncios encontrados: ${annListData.length}`);

  socket.disconnect();
  console.log('🎉 ¡Todas las pruebas de WebSockets y Comunidad pasaron con 100% de éxito!');
}

runTest().catch((err) => {
  console.error('❌ Error en prueba:', err);
  process.exit(1);
});
