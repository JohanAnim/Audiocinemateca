package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private data class SearchResultCandidate(
    val item: CatalogItem,
    val score: Int,
    val matchReason: String? = null
)

@Singleton
class SearchRepository @Inject constructor(
    private val catalogRepository: CatalogRepository
) {

    private val stopWords = setOf(
        "de", "del", "la", "el", "los", "las", "un", "una", "unos", "unas",
        "en", "por", "con", "para", "que", "su", "sus", "al", "es", "o", "y",
        "the", "a", "an", "of", "in", "and", "or", "to", "for", "with", "by", "on"
    )

    private fun normalizeText(text: String): String {
        if (text.isBlank()) return ""
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        return regex.replace(temp, "").lowercase(Locale.ROOT).trim()
    }

    suspend fun searchCatalog(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val rawQuery = query.trim()
        if (rawQuery.isBlank()) {
            return@withContext emptyList<CatalogItem>()
        }

        val queryNorm = normalizeText(rawQuery)
        if (queryNorm.isEmpty()) return@withContext emptyList()

        val allWords = queryNorm.split("\\s+|[\\p{Punct}]".toRegex()).filter { it.isNotBlank() }
        val meaningfulWords = allWords.filter { it.length > 1 && !stopWords.contains(it) }
        val searchWords = if (meaningfulWords.isNotEmpty()) meaningfulWords else allWords

        val catalog = catalogRepository.getCatalog() ?: return@withContext emptyList()
        val allItems = mutableListOf<CatalogItem>()
        catalog.movies?.let { allItems.addAll(it) }
        catalog.series?.let { allItems.addAll(it) }
        catalog.documentaries?.let { allItems.addAll(it) }
        catalog.shortFilms?.let { allItems.addAll(it) }

        val scoredItems = allItems.mapNotNull { item ->
            val titleNorm = normalizeText(item.title)
            val titleWords = titleNorm.split("\\s+|[\\p{Punct}]".toRegex()).filter { it.isNotBlank() }

            var score = 0
            var matchReason: String? = null

            // 1. TÍTULO
            if (titleNorm == queryNorm) {
                score += 3000
                matchReason = "Título exacto"
            } else if (titleWords.any { it == queryNorm }) {
                score += 2500
                matchReason = "Palabra exacta en título"
            } else if (titleWords.any { it.startsWith(queryNorm) }) {
                score += 2000
                matchReason = "Inicio de palabra en título"
            } else if (titleNorm.startsWith(queryNorm)) {
                score += 1500
                matchReason = "Comienza con '$rawQuery'"
            } else if (titleNorm.contains(queryNorm)) {
                val isInsideLongerWord = titleWords.any { it.contains(queryNorm) && !it.startsWith(queryNorm) }
                score += if (isInsideLongerWord) 50 else 600
                matchReason = "Título contiene '$rawQuery'"
            } else {
                val titleWordsMatched = searchWords.count { word -> titleWords.any { tw -> tw.startsWith(word) } }
                if (searchWords.isNotEmpty() && titleWordsMatched == searchWords.size) {
                    score += 400
                    matchReason = "Título coincide"
                } else if (titleWordsMatched > 0) {
                    score += 100 * titleWordsMatched
                }
            }

            // 2. DIRECTOR
            if (item.director.isNotBlank()) {
                val directorNorm = normalizeText(item.director)
                if (directorNorm.contains(queryNorm)) {
                    score += 500
                    if (matchReason == null) matchReason = "Director: ${item.director}"
                }
            }

            // 3. REPARTO
            if (item.reparto.isNotBlank() && score < 1000) {
                val repartoNorm = normalizeText(item.reparto)
                if (repartoNorm.contains(queryNorm)) {
                    score += 250
                    if (matchReason == null) matchReason = "Reparto: ${item.reparto.take(40)}..."
                }
            }

            // 4. MÚSICA / GUION / AÑO
            if (item.musica.isNotBlank() && score < 1000) {
                val musicaNorm = normalizeText(item.musica)
                if (musicaNorm.contains(queryNorm)) score += 200
            }

            if (item.anio.isNotBlank() && (item.anio.trim() == rawQuery || (rawQuery.length >= 4 && item.anio.contains(rawQuery)))) {
                score += 300
                if (matchReason == null) matchReason = "Año ${item.anio}"
            }

            // 5. GÉNERO
            if (item.genero.isNotBlank() && score < 1000) {
                val generoNorm = normalizeText(item.genero)
                if (generoNorm.contains(queryNorm)) score += 100
            }

            // 6. SINOPSIS (Evaluación perezosa estricta)
            if (item.sinopsis.isNotBlank() && queryNorm.length >= 3 && score < 500) {
                val sinopsisNorm = normalizeText(item.sinopsis)
                if (sinopsisNorm.contains(queryNorm)) {
                    score += 60
                }
            }

            if (score >= 50) {
                SearchResultCandidate(item = item, score = score, matchReason = matchReason)
            } else {
                null
            }
        }

        if (scoredItems.isEmpty()) {
            return@withContext emptyList<CatalogItem>()
        }

        val maxScore = scoredItems.maxOf { it.score }
        val minScoreCutoff = maxOf(100, (maxScore * 0.35).toInt())
        val filteredCandidates = scoredItems.filter { it.score >= minScoreCutoff }

        return@withContext filteredCandidates.sortedWith(
            compareByDescending<SearchResultCandidate> { it.score }
                .thenBy { it.item.anio.toIntOrNull() ?: 0 }
                .thenBy { it.item.title.lowercase(Locale.ROOT) }
        ).take(40).map { it.item }
    }

    suspend fun getRandomCatalogItem(categoryName: String? = null): CatalogItem? {
        val catalog = catalogRepository.getCatalog() ?: return null
        val items = when (categoryName) {
            "peliculas" -> catalog.movies
            "series" -> catalog.series
            "documentales" -> catalog.documentaries
            "cortometrajes" -> catalog.shortFilms
            else -> {
                val allItems = mutableListOf<CatalogItem>()
                catalog.movies?.let { allItems.addAll(it) }
                catalog.series?.let { allItems.addAll(it) }
                catalog.documentaries?.let { allItems.addAll(it) }
                catalog.shortFilms?.let { allItems.addAll(it) }
                allItems
            }
        }
        return items?.randomOrNull()
    }

    suspend fun getCatalogItemByIdAndType(itemId: String, itemType: String): CatalogItem? {
        val catalog = catalogRepository.getCatalog()
        val pluralItemType = when (itemType) {
            "pelicula" -> "peliculas"
            "serie" -> "series"
            "documental" -> "documentales"
            "cortometraje" -> "cortometrajes"
            else -> itemType
        }
        return when (pluralItemType) {
            "peliculas" -> catalog?.movies?.find { it.id == itemId }
            "series" -> catalog?.series?.find { it.id == itemId }
            "documentales" -> catalog?.documentaries?.find { it.id == itemId }
            "cortometrajes" -> catalog?.shortFilms?.find { it.id == itemId }
            else -> null
        }
    }

    suspend fun findCatalogItemById(itemId: String, isSeriesHint: Boolean = false): CatalogItem? {
        val catalog = catalogRepository.getCatalog() ?: return null

        if (isSeriesHint) {
            return catalog.series?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.movies?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.documentaries?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.shortFilms?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
        } else {
            return catalog.movies?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.documentaries?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.shortFilms?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.series?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
        }
    }
}
