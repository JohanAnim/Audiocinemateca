package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.entities.CatalogItemEntity
import com.johang.audiocinemateca.domain.model.CatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private data class SearchResultCandidate(
    val entity: CatalogItemEntity,
    val score: Int
)

@Singleton
class SearchRepository @Inject constructor(
    private val catalogRepository: CatalogRepository
) {

    private val diacriticsRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val wordSplitRegex = "\\s+|[\\p{Punct}]".toRegex()

    private val stopWords = setOf(
        "de", "del", "la", "el", "los", "las", "un", "una", "unos", "unas",
        "en", "por", "con", "para", "que", "su", "sus", "al", "es", "o", "y",
        "the", "a", "an", "of", "in", "and", "or", "to", "for", "with", "by", "on"
    )

    private fun normalizeText(text: String): String {
        if (text.isBlank()) return ""
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        return diacriticsRegex.replace(temp, "").lowercase(Locale.ROOT).trim()
    }

    suspend fun searchCatalog(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val rawQuery = query.trim()
        if (rawQuery.isBlank()) {
            return@withContext emptyList<CatalogItem>()
        }

        val queryNorm = normalizeText(rawQuery)
        if (queryNorm.isEmpty()) return@withContext emptyList()

        val allWords = queryNorm.split(wordSplitRegex).filter { it.isNotBlank() }
        val meaningfulWords = allWords.filter { it.length > 1 && !stopWords.contains(it) }
        val searchWords = if (meaningfulWords.isNotEmpty()) meaningfulWords else allWords

        val cleanTokens = searchWords
            .map { it.replace("\"", "").replace("*", "").replace(":", "").trim() }
            .filter { it.isNotEmpty() }

        val stemToken: (String) -> String = { token ->
            when {
                token.endsWith("es") && token.length >= 6 && !token.endsWith("ces") && token != "series" ->
                    token.dropLast(2)
                token.endsWith("s") && token.length >= 5 ->
                    token.dropLast(1)
                else -> token
            }
        }
        val stemmedTokens = cleanTokens.map(stemToken).distinct()

        val candidateMap = LinkedHashMap<String, CatalogItemEntity>()

        if (cleanTokens.isNotEmpty()) {
            // 1. Prioridad máxima: coincidencia FTS4 directa en títulos
            val titleFtsQuery = cleanTokens.joinToString(" AND ") { "titleNorm:$it*" }
            val titleMatches = runCatching {
                catalogRepository.searchFtsEntities(titleFtsQuery, limit = 80)
            }.getOrDefault(emptyList())
            titleMatches.forEach { candidateMap["${it.category}_${it.id}"] = it }

            // 2. Si faltan candidatos, ampliar FTS4 al resto de campos (director, reparto, género, sinopsis)
            // usando raíz sin plurales finales para que "mangas de romance" encuentre "Manga. Romance".
            if (candidateMap.size < 40) {
                val fullFtsQuery = stemmedTokens.joinToString(" AND ") { "$it*" }
                val fullMatches = runCatching {
                    catalogRepository.searchFtsEntities(fullFtsQuery, limit = 80)
                }.getOrDefault(emptyList())
                fullMatches.forEach { candidateMap.putIfAbsent("${it.category}_${it.id}", it) }
            }
        }

        // 3. Respaldo rápido en SQLite para subcadenas internas (sin cargar todo el catálogo a RAM)
        if (candidateMap.isEmpty() && queryNorm.length >= 2) {
            val substringMatches = runCatching {
                catalogRepository.searchSubstringEntities(queryNorm, limit = 80)
            }.getOrDefault(emptyList())
            substringMatches.forEach { candidateMap["${it.category}_${it.id}"] = it }
        }

        if (candidateMap.isEmpty()) {
            return@withContext emptyList()
        }

        // Puntuar directamente sobre CatalogItemEntity sin deserializar mediaJson hasta tener el Top 40
        val scoredItems = candidateMap.values.mapNotNull { entity ->
            val titleNorm = entity.titleNorm
            val titleWords = titleNorm.split(wordSplitRegex).filter { it.isNotBlank() }

            var score = 0

            // 1. TÍTULO
            if (titleNorm == queryNorm) {
                score += 3000
            } else if (titleWords.any { it == queryNorm }) {
                score += 2500
            } else if (titleWords.any { it.startsWith(queryNorm) }) {
                score += 2000
            } else if (titleNorm.startsWith(queryNorm)) {
                score += 1500
            } else if (titleNorm.contains(queryNorm)) {
                val isInsideLongerWord = titleWords.any { it.contains(queryNorm) && !it.startsWith(queryNorm) }
                score += if (isInsideLongerWord) 50 else 600
            } else {
                val titleWordsMatched = searchWords.count { word -> titleWords.any { tw -> tw.startsWith(word) } }
                if (searchWords.isNotEmpty() && titleWordsMatched == searchWords.size) {
                    score += 400
                } else if (titleWordsMatched > 0) {
                    score += 100 * titleWordsMatched
                }
            }

            // 2. DIRECTOR
            if (entity.director.isNotBlank()) {
                val directorNorm = normalizeText(entity.director)
                if (directorNorm.contains(queryNorm)) {
                    score += 500
                }
            }

            // 3. REPARTO
            if (entity.reparto.isNotBlank() && score < 1000) {
                val repartoNorm = normalizeText(entity.reparto)
                if (repartoNorm.contains(queryNorm)) {
                    score += 250
                }
            }

            // 4. MÚSICA / AÑO
            if (entity.musica.isNotBlank() && score < 1000) {
                val musicaNorm = normalizeText(entity.musica)
                if (musicaNorm.contains(queryNorm)) score += 200
            }

            if (entity.anio.isNotBlank() && (entity.anio.trim() == rawQuery || (rawQuery.length >= 4 && entity.anio.contains(rawQuery)))) {
                score += 300
            }

            // 5. GÉNERO (soporta consultas exactas o múltiples géneros como "mangas de romance")
            if (entity.genero.isNotBlank() && score < 1000) {
                val generoNorm = normalizeText(entity.genero)
                val genreTokensMatched = stemmedTokens.count { token -> generoNorm.contains(token) }
                when {
                    generoNorm.contains(queryNorm) -> score += 600
                    stemmedTokens.isNotEmpty() && genreTokensMatched == stemmedTokens.size -> score += 500
                    genreTokensMatched > 0 -> score += 120 * genreTokensMatched
                }
            }

            // 6. SINOPSIS (Evaluación perezosa estricta)
            if (entity.sinopsis.isNotBlank() && queryNorm.length >= 3 && score < 500) {
                val sinopsisNorm = normalizeText(entity.sinopsis)
                if (sinopsisNorm.contains(queryNorm)) {
                    score += 60
                }
            }

            if (score >= 50) {
                SearchResultCandidate(entity = entity, score = score)
            } else {
                null
            }
        }

        if (scoredItems.isEmpty()) {
            return@withContext emptyList<CatalogItem>()
        }

        val maxScore = scoredItems.maxOf { it.score }
        val minScoreCutoff = maxOf(100, (maxScore * 0.35).toInt())
        val topEntities = scoredItems
            .asSequence()
            .filter { it.score >= minScoreCutoff }
            .sortedWith(
                compareByDescending<SearchResultCandidate> { it.score }
                    .thenBy { it.entity.anio.toIntOrNull() ?: 0 }
                    .thenBy { it.entity.titleNorm }
            )
            .take(40)
            .map { it.entity }
            .toList()

        return@withContext catalogRepository.entitiesToCatalogItems(topEntities)
    }

    suspend fun getRandomCatalogItem(categoryName: String? = null): CatalogItem? {
        return catalogRepository.getRandomItem(categoryName)
    }

    suspend fun getCatalogItemByIdAndType(itemId: String, itemType: String): CatalogItem? {
        val pluralItemType = when (itemType) {
            "pelicula" -> "peliculas"
            "serie" -> "series"
            "documental" -> "documentales"
            "cortometraje" -> "cortometrajes"
            else -> itemType
        }
        return catalogRepository.findItemByIdAndCategory(itemId, pluralItemType)
    }

    suspend fun findCatalogItemById(itemId: String, isSeriesHint: Boolean = false): CatalogItem? {
        return catalogRepository.findItemById(itemId, isSeriesHint)
    }
}
