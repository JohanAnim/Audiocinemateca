async function runConfigTest() {
  console.log('🚀 Iniciando pruebas del módulo de Configuración Global y Actualizaciones...');

  // 1. Obtener token de administrador
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
  console.log('✅ Token JWT de Admin obtenido.');

  // 2. Probar GET /api/v1/config/app (Público)
  console.log('📱 Consultando configuración pública de la app...');
  const appRes = await fetch('http://localhost:3000/api/v1/config/app');
  if (!appRes.ok) throw new Error(`Error en config/app: ${appRes.status}`);
  const appData = await appRes.json();
  console.log('✅ Configuración pública recibida con éxito:');
  console.log(`   - Versión actual: ${appData.version?.version} (${appData.version?.name})`);
  console.log(`   - Mantenimiento: ${appData.maintenance?.enabled ? 'ACTIVADO' : 'Desactivado'}`);
  console.log(`   - Banner activo: "${appData.banner?.title}"`);
  console.log(`   - Donaciones: PayPal=${appData.donations?.paypal}`);

  // 3. Probar comprobación de versiones
  console.log('\n🔄 Probando comprobación de versiones (Android UpdateInfo)...');
  // Caso A: Cliente desactualizado (v3.0.0)
  const v1Res = await fetch('http://localhost:3000/api/v1/config/version?currentVersion=3.0.0');
  const v1Data = await v1Res.json();
  console.log(`   - Cliente v3.0.0: Actualización=${v1Data.isUpdateAvailable}, Forzada=${v1Data.isForceUpdate}`);
  if (!v1Data.isUpdateAvailable || v1Data.isForceUpdate) {
    throw new Error('Validación falló para versión 3.0.0');
  }

  // Caso B: Cliente al día (v3.1.0)
  const v2Res = await fetch('http://localhost:3000/api/v1/config/version?currentVersion=3.1.0');
  const v2Data = await v2Res.json();
  console.log(`   - Cliente v3.1.0: Actualización=${v2Data.isUpdateAvailable}, Forzada=${v2Data.isForceUpdate}`);
  if (v2Data.isUpdateAvailable) {
    throw new Error('Validación falló para versión 3.1.0');
  }

  // Caso C: Cliente obsoleto (v2.8.0 - menor a minRequiredVersion 3.0.0)
  const v3Res = await fetch('http://localhost:3000/api/v1/config/version?currentVersion=2.8.0');
  const v3Data = await v3Res.json();
  console.log(`   - Cliente v2.8.0 (Obsoleto): Actualización=${v3Data.isUpdateAvailable}, Forzada=${v3Data.isForceUpdate}`);
  if (!v3Data.isForceUpdate) {
    throw new Error('Validación de actualización forzada falló para versión 2.8.0');
  }

  // 4. Probar GET /api/v1/config/banner
  console.log('\n📢 Consultando banner activo del feed...');
  const bannerRes = await fetch('http://localhost:3000/api/v1/config/banner');
  const bannerData = await bannerRes.json();
  console.log(`✅ Banner obtenido: [${bannerData.type}] ${bannerData.title}`);

  // 5. Probar PUT /api/v1/config/:key (Modificación Admin)
  console.log('\n⚙️ Modificando banner desde endpoint de Admin...');
  const updateRes = await fetch('http://localhost:3000/api/v1/config/featured_banner', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      value: {
        enabled: true,
        id: 'promo_backend',
        title: '¡Backend Propio Activo!',
        message: 'Disfruta de la máxima velocidad y audio en alta fidelidad.',
        type: 'update',
        dismissible: false,
        actionUrl: 'https://audiocinemateca.com',
        actionText: 'Conocer más',
      },
      description: 'Banner temporal anunciando el nuevo backend',
    }),
  });
  if (!updateRes.ok) throw new Error(`Error actualizando config: ${updateRes.status}`);
  const updatedData = await updateRes.json();
  console.log(`✅ Banner modificado con éxito: "${updatedData.value?.title}"`);

  // 6. Verificar que la caché y el endpoint público reflejen el cambio al instante
  const verifyRes = await fetch('http://localhost:3000/api/v1/config/banner');
  const verifyData = await verifyRes.json();
  console.log(`✅ Verificación en vivo: "${verifyData.title}" (${verifyData.type})`);
  if (verifyData.id !== 'promo_backend') {
    throw new Error('La caché no se actualizó reactivamente');
  }

  // 7. Probar GET /api/v1/config/all (Admin)
  console.log('\n📋 Consultando listado completo de configuraciones...');
  const allRes = await fetch('http://localhost:3000/api/v1/config/all', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const allConfigs = await allRes.json();
  console.log(`✅ Total de configuraciones en base de datos: ${allConfigs.length}`);
  allConfigs.forEach((c: any) => console.log(`   - [${c.key}]: ${c.description || 'Sin descripción'}`));

  // 8. Restaurar banner por defecto
  console.log('\n🔄 Restaurando banner original...');
  await fetch('http://localhost:3000/api/v1/config/seed', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });

  console.log('\n🎉 ¡Todas las pruebas del Módulo de Configuración pasaron con 100% de éxito!');
}

runConfigTest().catch((err) => {
  console.error('❌ Error en prueba de configuración:', err);
  process.exit(1);
});
