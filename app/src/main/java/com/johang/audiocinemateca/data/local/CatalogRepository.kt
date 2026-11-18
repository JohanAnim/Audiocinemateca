package com.johang.audiocinemateca.data.local

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.johang.audiocinemateca.data.local.dao.CatalogDao
import com.johang.audiocinemateca.data.local.entities.CatalogItemEntity
import com.johang.audiocinemateca.data.local.entities.CatalogVersionEntity
import com.johang.audiocinemateca.data.model.CatalogResponse
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.DocumentaryTypeAdapter
import com.johang.audiocinemateca.data.model.Episode
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.MovieTypeAdapter
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.SerieTypeAdapter
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.model.ShortFilmTypeAdapter
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.domain.model.FilterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class CatalogRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val catalogDao: CatalogDao,
    @ApplicationContext private val context: Context
) {

    @Volatile
    private var cachedCatalog: CatalogResponse? = null

    private val _catalogUpdated = MutableSharedFlow<Unit>(replay = 0)
    val catalogUpdated = _catalogUpdated.asSharedFlow()

    private val catalogMutex = Mutex()

    private val gson = GsonBuilder()
        .registerTypeAdapter(Movie::class.java, MovieTypeAdapter())
        .registerTypeAdapter(Serie::class.java, SerieTypeAdapter())
        .registerTypeAdapter(Documentary::class.java, DocumentaryTypeAdapter())
        .registerTypeAdapter(ShortFilm::class.java, ShortFilmTypeAdapter())
        .create()

    private val enlacesListType = object : TypeToken<List<String>>() {}.type
    private val capitulosMapType = object : TypeToken<Map<String, List<Episode>>>() {}.type
    private val diacriticsRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    private val CATALOG_FILE_NAME = "audiocinemateca_catalog.json"

    suspend fun saveCatalogVersion(versionDate: Date) = withContext(Dispatchers.IO) {
        catalogDao.insertCatalogVersion(CatalogVersionEntity("mainCatalog", versionDate.time.toString()))
    }

    suspend fun getCatalogVersion(): Date? = withContext(Dispatchers.IO) {
        val versionString = catalogDao.getCatalogVersion("mainCatalog")
        versionString?.toLongOrNull()?.let { Date(it) }
    }

    suspend fun saveCatalog(catalog: CatalogResponse) = withContext(Dispatchers.IO) {
        Log.d("CatalogRepository", "Saving catalog to SQLite...")
        try {
            val entities = catalogToEntities(catalog)
            catalogMutex.withLock {
                catalogDao.replaceCatalogItems(entities)
                cachedCatalog = catalog
                deleteLegacyCatalogFileLocked()
            }
            Log.d("CatalogRepository", "Saved ${entities.size} catalog items to SQLite")
            _catalogUpdated.emit(Unit)
        } catch (e: Exception) {
            Log.e("CatalogRepository", "Error saving catalog to SQLite", e)
            throw e
        }
    }

    suspend fun getCatalog(): CatalogResponse? = withContext(Dispatchers.IO) {
        cachedCatalog?.let { return@withContext it }
        catalogMutex.withLock {
            cachedCatalog?.let { return@withLock it }
            if (!ensureCatalogInSqliteLocked()) {
                return@withLock null
            }
            val movies = catalogDao.getItemsByCategory("peliculas").mapNotNull { entityToCatalogItem(it) as? Movie }
            val series = catalogDao.getItemsByCategory("series").mapNotNull { entityToCatalogItem(it) as? Serie }
            val docs = catalogDao.getItemsByCategory("documentales").mapNotNull { entityToCatalogItem(it) as? Documentary }
            val shorts = catalogDao.getItemsByCategory("cortometrajes").mapNotNull { entityToCatalogItem(it) as? ShortFilm }
            val reconstructed = CatalogResponse(
                movies = movies,
                series = series,
                documentaries = docs,
                shortFilms = shorts
            )
            cachedCatalog = reconstructed
            reconstructed
        }
    }

    private suspend fun ensureCatalogInSqlite(): Boolean {
        if (catalogDao.getCatalogCount() > 0) return true
        return catalogMutex.withLock {
            ensureCatalogInSqliteLocked()
        }
    }

    private suspend fun ensureCatalogInSqliteLocked(): Boolean {
        if (catalogDao.getCatalogCount() > 0) return true
        return migrateLegacyJsonIfExistsLocked()
    }

    private suspend fun migrateLegacyJsonIfExistsLocked(): Boolean {
        try {
            val legacyEntity = catalogDao.getCatalogData("mainCatalogFile").firstOrNull()
            val candidateFile = when {
                legacyEntity != null && File(legacyEntity.data).exists() -> File(legacyEntity.data)
                File(context.filesDir, CATALOG_FILE_NAME).exists() -> File(context.filesDir, CATALOG_FILE_NAME)
                else -> null
            } ?: return false

            Log.d("CatalogRepository", "Migrating legacy catalog JSON (${candidateFile.absolutePath}) to SQLite...")
            val catalog = candidateFile.inputStream().buffered().reader(Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, CatalogResponse::class.java)
            } ?: return false

            val entities = catalogToEntities(catalog)
            catalogDao.replaceCatalogItems(entities)
            cachedCatalog = catalog
            deleteLegacyCatalogFileLocked()
            Log.d("CatalogRepository", "Migrated ${entities.size} items to SQLite and removed legacy JSON file")
            return true
        } catch (e: Exception) {
            Log.e("CatalogRepository", "Error migrating legacy JSON catalog to SQLite", e)
            return false
        }
    }

    private suspend fun deleteLegacyCatalogFileLocked() {
        try {
            val legacyEntity = catalogDao.getCatalogData("mainCatalogFile").firstOrNull()
            if (legacyEntity != null) {
                val file = File(legacyEntity.data)
                if (file.exists()) {
                    file.delete()
                }
            }
            val defaultFile = File(context.filesDir, CATALOG_FILE_NAME)
            if (defaultFile.exists()) {
                defaultFile.delete()
            }
            catalogDao.deleteCatalogData("mainCatalogFile")
        } catch (e: Exception) {
            Log.w("CatalogRepository", "No se pudo limpiar archivo JSON legado: ${e.message}")
        }
    }

    fun clearCache() {
        cachedCatalog = null
        Log.d("CatalogRepository", "Cache cleared")
    }

    suspend fun deleteCatalogData(idPattern: String) = withContext(Dispatchers.IO) {
        if (idPattern == "mainCatalogFile") {
            catalogMutex.withLock {
                cachedCatalog = null
                catalogDao.clearAllCatalogItems()
                deleteLegacyCatalogFileLocked()
            }
        }
    }

    private fun collectDistinctFieldTokens(
        rawValues: List<String>,
        delimiters: Regex
    ): List<String> {
        val tokens = java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
        rawValues.forEach { raw ->
            raw.split(delimiters).forEach { individual ->
                val trimmed = individual.trim()
                if (trimmed.isNotBlank()) tokens.add(trimmed)
            }
        }
        return tokens.toList()
    }

    suspend fun getGenres(categoryName: String? = null): List<String> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext emptyList()
        val rawGenres = if (categoryName != null) {
            catalogDao.getDistinctGenresByCategory(categoryName)
        } else {
            catalogDao.getAllDistinctGenres()
        }
        collectDistinctFieldTokens(rawGenres, Regex("[.|,]"))
    }

    suspend fun getCountries(categoryName: String? = null): List<String> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext emptyList()
        val rawCountries = if (categoryName != null) {
            catalogDao.getDistinctCountriesByCategory(categoryName)
        } else {
            catalogDao.getAllDistinctCountries()
        }
        collectDistinctFieldTokens(rawCountries, Regex(","))
    }

    suspend fun getNarrators(
        categoryName: String? = null,
        language: String = "Todos"
    ): List<String> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext emptyList()
        val rawNarrators = when {
            categoryName == null -> catalogDao.getAllDistinctNarrators()
            language == "Español de España" || language == "Castellano" ->
                catalogDao.getDistinctNarratorsByCategorySpain(categoryName)
            language == "Español Latino" || language == "Latino" ->
                catalogDao.getDistinctNarratorsByCategoryLatin(categoryName)
            else -> catalogDao.getDistinctNarratorsByCategory(categoryName)
        }
        collectDistinctFieldTokens(rawNarrators, Regex("[,/|;•]"))
    }

    suspend fun getProviders(
        categoryName: String? = null,
        language: String = "Todos"
    ): List<String> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext emptyList()
        val rawProviders = when {
            categoryName == null -> catalogDao.getAllDistinctProviders()
            language == "Español de España" || language == "Castellano" ->
                catalogDao.getDistinctProvidersByCategorySpain(categoryName)
            language == "Español Latino" || language == "Latino" ->
                catalogDao.getDistinctProvidersByCategoryLatin(categoryName)
            else -> catalogDao.getDistinctProvidersByCategory(categoryName)
        }
        collectDistinctFieldTokens(rawProviders, Regex("[,/|;•]"))
    }

    suspend fun getFilteredAndPaginatedItems(
        categoryName: String,
        filterType: String,
        filterValue: String?,
        offset: Int,
        limit: Int
    ): List<CatalogItem> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext emptyList()

        val entities: List<CatalogItemEntity> = when (filterType) {
            "Alfabéticamente" -> when (filterValue) {
                "Z-A" -> catalogDao.getItemsAlphaDesc(categoryName, limit, offset)
                else -> catalogDao.getItemsAlphaAsc(categoryName, limit, offset)
            }
            "Fecha" -> when (filterValue) {
                "Más nuevo" -> catalogDao.getItemsDateDesc(categoryName, limit, offset)
                else -> catalogDao.getItemsDateAsc(categoryName, limit, offset)
            }
            "Idiomas" -> when (filterValue) {
                "Español de España" -> catalogDao.getItemsSpanishSpain(categoryName, limit, offset)
                "Español Latino" -> catalogDao.getItemsSpanishLatin(categoryName, limit, offset)
                else -> catalogDao.getItemsDateAsc(categoryName, limit, offset)
            }
            "Género" -> {
                if (filterValue != null && filterValue != "Todos") {
                    catalogDao.getItemsMatchingGenre(categoryName, filterValue)
                        .filter { entity ->
                            entity.genero.split(".", "|", ",").any { it.trim().equals(filterValue, ignoreCase = true) }
                        }
                        .drop(offset)
                        .take(limit)
                } else {
                    catalogDao.getItemsDateAsc(categoryName, limit, offset)
                }
            }
            "Países" -> {
                if (filterValue != null && filterValue != "Todos") {
                    catalogDao.getItemsMatchingCountry(categoryName, filterValue)
                        .filter { entity ->
                            entity.pais.split(",").any { it.trim().equals(filterValue, ignoreCase = true) }
                        }
                        .drop(offset)
                        .take(limit)
                } else {
                    catalogDao.getItemsDateAsc(categoryName, limit, offset)
                }
            }
            else -> catalogDao.getItemsDateAsc(categoryName, limit, offset)
        }

        entities.mapNotNull { entityToCatalogItem(it) }
    }

    suspend fun searchFtsItems(ftsQuery: String, limit: Int = 150): List<CatalogItem> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext emptyList()
        catalogDao.searchFts(ftsQuery, limit).mapNotNull { entityToCatalogItem(it) }
    }

    suspend fun searchFtsEntities(ftsQuery: String, limit: Int = 80): List<CatalogItemEntity> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext emptyList()
        catalogDao.searchFts(ftsQuery, limit)
    }

    suspend fun searchSubstringEntities(queryNorm: String, limit: Int = 80): List<CatalogItemEntity> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext emptyList()
        catalogDao.searchSubstringFallback(queryNorm, limit)
    }

    fun entitiesToCatalogItems(entities: List<CatalogItemEntity>): List<CatalogItem> {
        return entities.mapNotNull { entityToCatalogItem(it) }
    }

    suspend fun findItemByIdAndCategory(id: String, category: String): CatalogItem? = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext null
        catalogDao.findByIdAndCategory(id, category)?.let { entityToCatalogItem(it) }
    }

    suspend fun findItemById(id: String, isSeriesHint: Boolean = false): CatalogItem? = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext null
        val matches = catalogDao.findAllById(id)
        if (matches.isEmpty()) return@withContext null
        val ordered = if (isSeriesHint) {
            matches.sortedBy { if (it.category == "series") 0 else 1 }
        } else {
            matches.sortedBy { if (it.category == "series") 1 else 0 }
        }
        entityToCatalogItem(ordered.first())
    }

    suspend fun findItemByTitle(title: String): CatalogItem? = withContext(Dispatchers.IO) {
        if (title.isBlank() || !ensureCatalogInSqlite()) return@withContext null
        catalogDao.findByTitleInsensitive(title)?.let { entityToCatalogItem(it) }
    }

    suspend fun getRandomItem(categoryName: String? = null): CatalogItem? = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext null
        val entity = if (categoryName != null && categoryName in setOf("peliculas", "series", "documentales", "cortometrajes")) {
            catalogDao.getRandomByCategory(categoryName)
        } else {
            catalogDao.getRandomAny()
        }
        entity?.let { entityToCatalogItem(it) }
    }

    suspend fun hasCatalog(): Boolean = withContext(Dispatchers.IO) {
        ensureCatalogInSqlite()
    }

    suspend fun findSeriesTitlesByIds(ids: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (ids.isEmpty() || !ensureCatalogInSqlite()) return@withContext emptyMap()
        val result = mutableMapOf<String, String>()
        ids.distinct().chunked(500).forEach { batch ->
            catalogDao.findSeriesTitlesByIds(batch).forEach { tuple ->
                result[tuple.id] = tuple.title
            }
        }
        result
    }

    suspend fun getFilteredAndPaginatedItems(
        categoryName: String,
        options: FilterOptions,
        offset: Int,
        limit: Int
    ): List<CatalogItem> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) return@withContext emptyList()

        val whereClauses = mutableListOf("category = ?")
        val args = mutableListOf<Any>(categoryName)

        if (!options.searchQuery.isNullOrBlank()) {
            val qNorm = normalizeText(options.searchQuery)
            if (qNorm.isNotEmpty()) {
                whereClauses.add("titleNorm LIKE ?")
                args.add("%$qNorm%")
            }
        }

        if (options.genre != "Todos" && options.genre.isNotBlank()) {
            whereClauses.add("LOWER(genero) LIKE ?")
            args.add("%${options.genre.trim().lowercase(Locale.ROOT)}%")
        }

        if (options.language != "Todos" && options.language.isNotBlank()) {
            when (options.language) {
                "Español de España", "Castellano" -> {
                    whereClauses.add("(TRIM(idioma) = '1' OR LOWER(idioma) LIKE '%españa%' OR LOWER(idioma) LIKE '%castellano%')")
                }
                "Español Latino", "Latino" -> {
                    whereClauses.add("TRIM(idioma) != '1'")
                }
            }
        }

        if (options.country != "Todos" && options.country.isNotBlank()) {
            whereClauses.add("LOWER(pais) LIKE ?")
            args.add("%${options.country.trim().lowercase(Locale.ROOT)}%")
        }

        if (options.narrator != "Todos" && options.narrator.isNotBlank()) {
            whereClauses.add("LOWER(narracion) LIKE ?")
            args.add("%${options.narrator.trim().lowercase(Locale.ROOT)}%")
        }

        if (options.provider != "Todos" && options.provider.isNotBlank()) {
            whereClauses.add("LOWER(productora) LIKE ?")
            args.add("%${options.provider.trim().lowercase(Locale.ROOT)}%")
        }

        val orderBy = when (options.sortType) {
            "Alfabéticamente" -> if (options.sortValue == "Z-A") "titleNorm DESC" else "titleNorm ASC"
            "Año", "Año de estreno" -> if (options.sortValue == "Más antiguo") "CAST(anio AS INTEGER) ASC, sortOrder ASC" else "CAST(anio AS INTEGER) DESC, sortOrder DESC"
            "Fecha", "Fecha de inclusión" -> if (options.sortValue == "Más antiguo") "sortOrder ASC" else "sortOrder DESC"
            else -> "sortOrder ASC"
        }

        val sql = "SELECT * FROM catalog_items WHERE ${whereClauses.joinToString(" AND ")} ORDER BY $orderBy LIMIT ? OFFSET ?"
        args.add(limit)
        args.add(offset)

        catalogDao.getFilteredItemsRaw(SimpleSQLiteQuery(sql, args.toTypedArray()))
            .mapNotNull { entityToCatalogItem(it) }
    }

    suspend fun executeReadOnlySqlForAi(rawSql: String): Result<List<Map<String, String>>> = withContext(Dispatchers.IO) {
        if (!ensureCatalogInSqlite()) {
            return@withContext Result.failure(IllegalStateException("El catálogo local aún no está disponible."))
        }

        val cleanedSql = rawSql
            .trim()
            .removePrefix("```sql")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
            .trimEnd(';')
            .trim()

        val upperSql = cleanedSql.uppercase(Locale.ROOT)
        if (!upperSql.startsWith("SELECT ") && !upperSql.startsWith("WITH ")) {
            return@withContext Result.failure(IllegalArgumentException("Solo se permiten consultas SELECT o WITH de lectura."))
        }
        if (cleanedSql.contains(";")) {
            return@withContext Result.failure(IllegalArgumentException("No se permiten múltiples sentencias SQL."))
        }

        val forbiddenKeywords = listOf(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "REPLACE", "CREATE",
            "ATTACH", "DETACH", "PRAGMA", "VACUUM", "REINDEX", "SQLITE_MASTER",
            "SQLITE_SCHEMA", "SQLITE_TEMP_MASTER", "USERS", "FAVORITES",
            "PLAYBACK_PROGRESS", "DOWNLOADS", "NOTIFICATIONS", "SEARCH_HISTORY",
            "CATALOG_DATA", "CATALOG_VERSION"
        )
        val hasForbidden = forbiddenKeywords.any { kw ->
            Regex("""\b$kw\b""").containsMatchIn(upperSql)
        }
        if (hasForbidden) {
            return@withContext Result.failure(IllegalArgumentException("Consulta no permitida: solo se puede consultar catalog_items o catalog_items_fts en modo lectura."))
        }

        return@withContext runCatching {
            val db = appDatabase.openHelper.readableDatabase
            val rows = mutableListOf<Map<String, String>>()
            db.query(cleanedSql).use { cursor ->
                val colCount = cursor.columnCount
                while (cursor.moveToNext() && rows.size < 20) {
                    val row = LinkedHashMap<String, String>(colCount)
                    for (i in 0 until colCount) {
                        val colName = cursor.getColumnName(i)
                        val value = when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> ""
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i).toString()
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i).toString()
                            android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(i).take(220)
                            else -> ""
                        }
                        row[colName] = value
                    }
                    rows.add(row)
                }
            }
            rows
        }
    }

    private fun normalizeText(text: String): String {
        if (text.isBlank()) return ""
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return diacriticsRegex.replace(decomposed, "").lowercase(Locale.ROOT).trim()
    }

    private fun catalogToEntities(catalog: CatalogResponse): List<CatalogItemEntity> {
        val entities = ArrayList<CatalogItemEntity>(
            (catalog.movies?.size ?: 0) +
                (catalog.series?.size ?: 0) +
                (catalog.documentaries?.size ?: 0) +
                (catalog.shortFilms?.size ?: 0)
        )

        catalog.movies?.forEachIndexed { index, movie ->
            entities.add(
                createEntity(
                    item = movie,
                    category = "peliculas",
                    partes = movie.partes,
                    mediaJson = gson.toJson(movie.enlaces),
                    sortOrder = index
                )
            )
        }
        catalog.series?.forEachIndexed { index, serie ->
            entities.add(
                createEntity(
                    item = serie,
                    category = "series",
                    partes = serie.temporadas,
                    mediaJson = gson.toJson(serie.capitulos),
                    sortOrder = index
                )
            )
        }
        catalog.documentaries?.forEachIndexed { index, doc ->
            entities.add(
                createEntity(
                    item = doc,
                    category = "documentales",
                    partes = doc.partes,
                    mediaJson = doc.enlace,
                    sortOrder = index
                )
            )
        }
        catalog.shortFilms?.forEachIndexed { index, shortFilm ->
            entities.add(
                createEntity(
                    item = shortFilm,
                    category = "cortometrajes",
                    partes = "1",
                    mediaJson = shortFilm.enlace,
                    sortOrder = index
                )
            )
        }
        return entities
    }

    private fun createEntity(
        item: CatalogItem,
        category: String,
        partes: String,
        mediaJson: String,
        sortOrder: Int
    ): CatalogItemEntity {
        val titleNorm = normalizeText(item.title)
        val searchTextNorm = normalizeText(
            listOf(
                item.title,
                item.director,
                item.reparto,
                item.musica,
                item.genero,
                item.anio,
                item.sinopsis
            ).filter { it.isNotBlank() }.joinToString(" ")
        )
        return CatalogItemEntity(
            id = item.id,
            category = category,
            title = item.title,
            titleNorm = titleNorm,
            anio = item.anio,
            genero = item.genero,
            pais = item.pais,
            director = item.director,
            guion = item.guion,
            musica = item.musica,
            fotografia = item.fotografia,
            reparto = item.reparto,
            productora = item.productora,
            narracion = item.narracion,
            duracion = item.duracion,
            idioma = item.idioma,
            partes = partes,
            filmaffinity = item.filmaffinity,
            sinopsis = item.sinopsis,
            mediaJson = mediaJson,
            searchTextNorm = searchTextNorm,
            sortOrder = sortOrder
        )
    }

    private fun entityToCatalogItem(entity: CatalogItemEntity): CatalogItem? {
        return when (entity.category) {
            "peliculas" -> {
                val enlaces: List<String> = runCatching {
                    gson.fromJson<List<String>>(entity.mediaJson, enlacesListType) ?: emptyList()
                }.getOrDefault(emptyList())
                Movie(
                    id = entity.id,
                    title = entity.title,
                    anio = entity.anio,
                    genero = entity.genero,
                    pais = entity.pais,
                    director = entity.director,
                    guion = entity.guion,
                    musica = entity.musica,
                    fotografia = entity.fotografia,
                    reparto = entity.reparto,
                    productora = entity.productora,
                    narracion = entity.narracion,
                    duracion = entity.duracion,
                    idioma = entity.idioma,
                    partes = entity.partes,
                    filmaffinity = entity.filmaffinity,
                    sinopsis = entity.sinopsis,
                    enlaces = enlaces
                )
            }
            "series" -> {
                val capitulos: Map<String, List<Episode>> = runCatching {
                    gson.fromJson<Map<String, List<Episode>>>(entity.mediaJson, capitulosMapType) ?: emptyMap()
                }.getOrDefault(emptyMap())
                Serie(
                    id = entity.id,
                    title = entity.title,
                    anio = entity.anio,
                    duracion = entity.duracion,
                    pais = entity.pais,
                    director = entity.director,
                    guion = entity.guion,
                    musica = entity.musica,
                    fotografia = entity.fotografia,
                    reparto = entity.reparto,
                    genero = entity.genero,
                    temporadas = entity.partes,
                    idioma = entity.idioma,
                    narracion = entity.narracion,
                    filmaffinity = entity.filmaffinity,
                    sinopsis = entity.sinopsis,
                    productora = entity.productora,
                    capitulos = capitulos
                )
            }
            "documentales" -> Documentary(
                id = entity.id,
                title = entity.title,
                anio = entity.anio,
                genero = entity.genero,
                pais = entity.pais,
                director = entity.director,
                guion = entity.guion,
                musica = entity.musica,
                fotografia = entity.fotografia,
                reparto = entity.reparto,
                productora = entity.productora,
                narracion = entity.narracion,
                duracion = entity.duracion,
                idioma = entity.idioma,
                partes = entity.partes,
                filmaffinity = entity.filmaffinity,
                sinopsis = entity.sinopsis,
                enlace = entity.mediaJson
            )
            "cortometrajes" -> ShortFilm(
                id = entity.id,
                title = entity.title,
                anio = entity.anio,
                genero = entity.genero,
                pais = entity.pais,
                director = entity.director,
                guion = entity.guion,
                musica = entity.musica,
                fotografia = entity.fotografia,
                reparto = entity.reparto,
                productora = entity.productora,
                narracion = entity.narracion,
                duracion = entity.duracion,
                idioma = entity.idioma,
                filmaffinity = entity.filmaffinity,
                sinopsis = entity.sinopsis,
                enlace = entity.mediaJson
            )
            else -> null
        }
    }
}
