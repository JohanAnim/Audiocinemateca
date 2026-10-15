import { PrismaClient, Role, AuthProvider } from '@prisma/client';
import bcrypt from 'bcrypt';

const prisma = new PrismaClient();

async function main() {
  console.log('================================================================');
  console.log('👑 CREANDO / ACTUALIZANDO SUPER ADMINISTRADOR PRINCIPAL');
  console.log('================================================================\n');

  const adminEmail = 'gutierrezjohanantonio@gmail.com'.toLowerCase().trim();
  const adminUsername = 'johan-a-g'.toLowerCase().trim();
  const adminDisplayName = 'Johan-a-g';
  const rawPassword = '3tmgs8qd';

  const saltRounds = 10;
  const passwordHash = await bcrypt.hash(rawPassword, saltRounds);

  // Buscar si ya existe por email o por username
  let user = await prisma.user.findFirst({
    where: {
      OR: [
        { email: adminEmail },
        { username: adminUsername }
      ]
    }
  });

  if (user) {
    console.log(`ℹ️ Usuario encontrado (ID: ${user.id}). Actualizando a rol ADMIN y restableciendo contraseña...`);
    user = await prisma.user.update({
      where: { id: user.id },
      data: {
        email: adminEmail,
        username: adminUsername,
        displayName: adminDisplayName,
        passwordHash,
        role: Role.ADMIN,
      }
    });
  } else {
    console.log(`✨ Creando nuevo usuario Super Administrador: @${adminUsername}...`);
    user = await prisma.user.create({
      data: {
        email: adminEmail,
        username: adminUsername,
        displayName: adminDisplayName,
        passwordHash,
        role: Role.ADMIN,
      }
    });
  }

  // Asegurar cuenta de proveedor LOCAL vinculada
  const existingAccount = await prisma.authAccount.findUnique({
    where: {
      provider_providerId: {
        provider: AuthProvider.LOCAL,
        providerId: adminEmail,
      }
    }
  });

  if (!existingAccount) {
    await prisma.authAccount.create({
      data: {
        userId: user.id,
        provider: AuthProvider.LOCAL,
        providerId: adminEmail,
      }
    });
    console.log('   ✅ Cuenta AuthAccount (LOCAL) vinculada correctamente.');
  }

  console.log('\n================================================================');
  console.log('🎉 ¡SUPER ADMINISTRADOR LISTO Y VERIFICADO CON ÉXITO!');
  console.log('================================================================');
  console.log(`   👤 Usuario:      ${user.username}`);
  console.log(`   🏷️ Nombre:       ${user.displayName}`);
  console.log(`   ✉️ Correo:       ${user.email}`);
  console.log(`   🛡️ Rol:          ${user.role}`);
  console.log(`   🔑 Contraseña:   ${rawPassword}`);
  console.log(`   🆔 User ID:      ${user.id}`);
  console.log('================================================================\n');
}

main()
  .catch((e) => {
    console.error('❌ Error ejecutando seed de admin:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
