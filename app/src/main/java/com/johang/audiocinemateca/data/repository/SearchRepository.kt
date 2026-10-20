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
        val allWords = queryNorm.split("\\s+|[\\p{Punct}]".toRegex()).filter { it.isNotBlank() }
        val meaningfulWords = allWords.filter { it.length > 1 && !stopWords.contains(it) }
        val searchWords = if (meaningfulWords.isNotEmpty()) meaningfulWords else allWords

        val allItems = mutableListOf<CatalogItem>()
        catalogRepository.getCatalog()?.let {
            it.movies?.let { movies -> allItems.addAll(movies) }
            it.series?.let { series -> allItems.addAll(series) }
            it.documentaries?.let { docs -> allItems.addAll(docs) }
            it.shortFilms?.let { shorts -> allItems.addAll(shorts) }
        }

        val scoredItems = allItems.mapNotNull { item ->
            val titleNorm = normalizeText(item.title)
            val titleWords = titleNorm.split("\\s+|[\\p{Punct}]".toRegex()).filter { it.isNotBlank() }

            val directorNorm = normalizeText(item.director)
            val repartoNorm = normalizeText(item.reparto)
            val musicaNorm = normalizeText(item.musica)
            val guionNorm = normalizeText(item.guion)
            val sinopsisNorm = normalizeText(item.sinopsis)
            val generoNorm = normalizeText(item.genero)
            val narracionNorm = normalizeText(item.narracion)
            val paisNorm = normalizeText(item.pais)
            val anioNorm = item.anio.trim()

            var score = 0
            var matchReason: String? = null

            // 1. EVALUACIÓN DE TÍTULO (Lógica estricta de límites de palabra vs subcadena)
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
                if (isInsideLongerWord) {
                    score += 50
                } else {
                    score += 600
                }
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
            if (directorNorm.isNotBlank()) {
                val directorWords = directorNorm.split("\\s+|[\\p{Punct}]".toRegex()).filter { it.isNotBlank() }
                if (directorNorm == queryNorm || directorWords.any { it == queryNorm }) {
                    score += 500
                    if (matchReason == null) matchReason = "Director: ${item.director}"
                } else if (directorWords.any { it.startsWith(queryNorm) }) {
                    score += 300
                    if (matchReason == null) matchReason = "Director: ${item.director}"
                }
            }

            // 3. REPARTO / ACTORES
            if (repartoNorm.isNotBlank()) {
                val repartoWords = repartoNorm.split("\\s+|[\\p{Punct}]".toRegex()).filter { it.isNotBlank() }
                if (repartoWords.any { it == queryNorm || it.startsWith(queryNorm) }) {
                    score += 250
                    if (matchReason == null) matchReason = "Reparto: ${item.reparto.take(40)}..."
                }
            }

            // 4. MÚSICA / COMPOSITOR
            if (musicaNorm.isNotBlank()) {
                val musicaWords = musicaNorm.split("\\s+|[\\p{Punct}]".toRegex()).filter { it.isNotBlank() }
                if (musicaWords.any { it == queryNorm || it.startsWith(queryNorm) }) {
                    score += 200
                    if (matchReason == null) matchReason = "Música: ${item.musica}"
                }
            }

            // 5. GUION / NARRACIÓN
            if (guionNorm.isNotBlank() && searchWords.any { guionNorm.contains(it) }) {
                score += 150
            }
            if (narracionNorm.isNotBlank() && searchWords.any { narracionNorm.contains(it) }) {
                score += 120
            }

            // 6. AÑO DE ESTRENO
            if (anioNorm.isNotBlank() && (anioNorm == rawQuery || (rawQuery.length >= 4 && anioNorm.contains(rawQuery)))) {
                score += 300
                if (matchReason == null) matchReason = "Año ${item.anio}"
            }

            // 7. GÉNERO / PAÍS
            if (generoNorm.contains(queryNorm)) {
                score += 100
            }
            if (paisNorm.contains(queryNorm)) {
                score += 100
            }

            // 8. SINOPSIS (Estricto para descarte de basura)
            if (sinopsisNorm.isNotBlank()) {
                if (queryNorm.length >= 4 && sinopsisNorm.contains(queryNorm)) {
                    score += 60
                } else {
                    val synWordsMatched = searchWords.count { sinopsisNorm.contains(it) }
                    if (synWordsMatched >= 2) {
                        score += 20 * synWordsMatched
                    }
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
