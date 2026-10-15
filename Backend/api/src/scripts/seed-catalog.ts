import { PrismaClient, ContentType } from '@prisma/client';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const prisma = new PrismaClient();

function generateSlug(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)+/g, '') || 'item';
}

async function main() {
  console.log('🎬 Iniciando proceso de siembra de catálogo...');

  const catalogPath = path.resolve(__dirname, '../../../../back_otros/data/catalogo.json');
  if (!fs.existsSync(catalogPath)) {
    console.error(`❌ No se encontró catalogo.json en ${catalogPath}`);
    return;
  }

  const raw = fs.readFileSync(catalogPath, 'utf8');
  const catalog = JSON.parse(raw);

  // 1. Sembrar Categorías Base
  const commonCategories = [
    'Acción',
    'Aventuras',
    'Ciencia Ficción',
    'Comedia',
    'Drama',
    'Fantasía',
    'Intriga',
    'Misterio',
    'Romance',
    'Terror',
    'Thriller',
    'Bélico',
    'Animación',
    'Documental',
  ];

  console.log('📁 Creando o verificando categorías base...');
  for (const catName of commonCategories) {
    const slug = generateSlug(catName);
    await prisma.category.upsert({
      where: { name: catName },
      update: {},
      create: { name: catName, slug },
    });
  }

  // 2. Sembrar Películas (primeras 50)
  const peliculas = (catalog.peliculas || []).slice(0, 50);
  console.log(`🎥 Importando ${peliculas.length} películas de muestra...`);

  for (const m of peliculas) {
    const title = m.Titulo || 'Sin título';
    const baseSlug = generateSlug(title);
    const externalId = m.id ? String(m.id) : undefined;

    // Buscar si ya existe por externalId o slug
    const existing = await prisma.content.findFirst({
      where: {
        OR: [
          ...(externalId ? [{ externalId }] : []),
          { slug: baseSlug },
        ],
      },
    });

    if (existing) continue;

    const releaseYear = m.anio ? parseInt(m.anio, 10) : undefined;
    const durationMinutes = m.duracion ? parseInt(m.duracion, 10) : undefined;

    await prisma.content.create({
      data: {
        externalId,
        title,
        slug: `${baseSlug}-${Math.floor(1000 + Math.random() * 9000)}`,
        synopsis: m.sinopsis || 'Sinopsis no disponible.',
        type: ContentType.MOVIE,
        releaseYear: isNaN(releaseYear as number) ? undefined : releaseYear,
        duration: m.duracion ? `${m.duracion} min` : undefined,
        durationMinutes: isNaN(durationMinutes as number) ? undefined : durationMinutes,
        country: m.pais || undefined,
        director: m.director || undefined,
        screenplay: m.guion || undefined,
        music: m.musica || undefined,
        cinematography: m.fotografia || undefined,
        cast: m.reparto || undefined,
        production: m.productora || undefined,
        narrator: m.narracion || undefined,
        audioDescription: true,
        dubbing: m.idioma || 'Español',
        genreRaw: m.genero || undefined,
        filmaffinityUrl: m.filmaffinity || undefined,
        audioUrl: Array.isArray(m.enlaces) && m.enlaces.length > 0 ? m.enlaces[0] : undefined,
        audioUrls: Array.isArray(m.enlaces) ? m.enlaces : [],
      },
    });
  }

  // 3. Sembrar Series (primeras 15 con temporadas y episodios)
  const series = (catalog.series || []).slice(0, 15);
  console.log(`📺 Importando ${series.length} series de muestra con sus episodios...`);

  for (const s of series) {
    const title = s.titulo || 'Serie sin título';
    const baseSlug = generateSlug(title);
    const externalId = s.id ? String(s.id) : undefined;

    const existing = await prisma.content.findFirst({
      where: {
        OR: [
          ...(externalId ? [{ externalId }] : []),
          { slug: baseSlug },
        ],
      },
    });

    if (existing) continue;

    const releaseYear = s.anio ? parseInt(s.anio, 10) : undefined;

    const createdContent = await prisma.content.create({
      data: {
        externalId,
        title,
        slug: `${baseSlug}-${Math.floor(1000 + Math.random() * 9000)}`,
        synopsis: s.sinopsis || 'Sinopsis no disponible.',
        type: ContentType.SERIES,
        releaseYear: isNaN(releaseYear as number) ? undefined : releaseYear,
        country: s.pais || undefined,
        director: s.director || undefined,
        screenplay: s.guion || undefined,
        music: s.musica || undefined,
        cinematography: s.fotografia || undefined,
        cast: s.reparto || undefined,
        production: s.productora || undefined,
        narrator: s.narracion || undefined,
        audioDescription: true,
        genreRaw: s.genero || undefined,
        filmaffinityUrl: s.filmaffinity || undefined,
      },
    });

    // Crear temporadas y episodios
    if (s.capitulos && typeof s.capitulos === 'object') {
      const seasonKeys = Object.keys(s.capitulos);

      for (const seasonKey of seasonKeys) {
        const seasonNum = parseInt(seasonKey.replace(/[^0-9]/g, ''), 10) || 1;
        const episodeList = s.capitulos[seasonKey];

        if (Array.isArray(episodeList)) {
          const season = await prisma.season.create({
            data: {
              contentId: createdContent.id,
              seasonNumber: seasonNum,
              title: `Temporada ${seasonNum}`,
            },
          });

          const episodesData = episodeList.map((ep: any, idx: number) => ({
            seasonId: season.id,
            episodeNumber: parseInt(ep.capitulo, 10) || idx + 1,
            title: ep.titulo || `Episodio ${idx + 1}`,
            audioUrl: ep.enlace || '',
          }));

          if (episodesData.length > 0) {
            await prisma.episode.createMany({
              data: episodesData,
              skipDuplicates: true,
            });
          }
        }
      }
    }
  }

  // 4. Sembrar Documentales (primeros 10)
  const docs = (catalog.documentales || []).slice(0, 10);
  console.log(`🎙️ Importando ${docs.length} documentales de muestra...`);

  for (const d of docs) {
    const title = d.Titulo || 'Documental sin título';
    const baseSlug = generateSlug(title);
    const externalId = d.id ? String(d.id) : undefined;

    const existing = await prisma.content.findFirst({
      where: {
        OR: [
          ...(externalId ? [{ externalId }] : []),
          { slug: baseSlug },
        ],
      },
    });

    if (existing) continue;

    await prisma.content.create({
      data: {
        externalId,
        title,
        slug: `${baseSlug}-${Math.floor(1000 + Math.random() * 9000)}`,
        synopsis: d.sinopsis || 'Sinopsis no disponible.',
        type: ContentType.DOCUMENTARY,
        releaseYear: d.anio ? parseInt(d.anio, 10) : undefined,
        country: d.pais || undefined,
        director: d.director || undefined,
        narrator: d.narracion || undefined,
        audioDescription: true,
        genreRaw: d.genero || 'Documental',
        audioUrl: Array.isArray(d.enlaces) && d.enlaces.length > 0 ? d.enlaces[0] : undefined,
        audioUrls: Array.isArray(d.enlaces) ? d.enlaces : [],
      },
    });
  }

  const [totalContents, totalSeasons, totalEpisodes] = await Promise.all([
    prisma.content.count(),
    prisma.season.count(),
    prisma.episode.count(),
  ]);

  console.log(`✅ ¡Siembra finalizada con éxito!`);
  console.log(`   - Contenidos totales en BD: ${totalContents}`);
  console.log(`   - Temporadas totales: ${totalSeasons}`);
  console.log(`   - Episodios totales: ${totalEpisodes}`);
}

main()
  .catch((e) => {
    console.error('Error sembrando catálogo:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
