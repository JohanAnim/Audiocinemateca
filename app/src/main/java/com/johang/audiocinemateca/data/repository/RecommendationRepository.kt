package com.johang.audiocinemateca.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class RecommendedItemReference(
    val id: String,
    val type: String
)

data class RecommendationCachePayload(
    val timestamp: Long,
    val userId: String,
    val itemReferences: List<RecommendedItemReference>
)

data class FranchiseInfo(
    val baseKey: String,
    val partNumber: Int
)

@Singleton
class RecommendationRepository @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val favoritesDao: FavoritesDao,
    private val voteRepository: VoteRepository,
    private val ratingRepository: RatingRepository,
    private val cloudRepository: CloudRepository,
    private val prefsManager: SharedPreferencesManager,
    private val gson: Gson
) {

    companion object {
        private const val PREF_RECOMMENDATION_CACHE = "user_recommendations_cache_v6"
        private const val ONE_WEEK_MS = 7L * 24L * 60L * 60L * 1000L // 7 días en ms
        private const val MAX_RECOMMENDATIONS = 20
    }

    /**
     * Motor Ultra-Preciso v5.0 (Calificaciones con Estrellas + Votos + Favoritos + Retención):
     * 1. Calificaciones del Usuario (1 a 5 Estrellas):
     *    - ⭐⭐⭐⭐⭐ (5 Estrellas): Máximo impacto (+14.0 al contenido, +8.0 a sus géneros, directores y narradores).
     *    - ⭐⭐⭐⭐ (4 Estrellas): Impacto alto (+8.0 al contenido, +5.0 a metadatos).
     *    - ⭐⭐⭐ (3 Estrellas): Impacto leve (+2.0).
     *    - ⭐⭐ (2 Estrellas): Penalización (-10.0 al contenido, -4.0 a metadatos).
     *    - ⭐ (1 Estrella): Penalización extrema (-20.0 al contenido, -8.0 a metadatos).
     * 2. Votos Explícitos "Me gusta" (+10.0) / "No me gusta" (-15.0) en Firebase.
     * 3. Ponderación por Favoritos (+8.0).
     * 4. Granularidad por Episodios y Retención de Reproducción.
     * 5. Lógica Secuencial de Secuelas (Parte N -> N+1).
     * 6. Filtro de Doblaje Preferido & Desduplicación Estricta (Latino vs Castellano).
     * 7. Caché Local de 1 Semana y Sincronización en la Nube con Firestore (`taste_profile`).
     */
    suspend fun getRecommendations(
        userId: String = "guest",
        forceRefresh: Boolean = false
    ): List<CatalogItem> = withContext(Dispatchers.IO) {
        val catalog = catalogRepository.getCatalog() ?: return@withContext emptyList()
        val allCatalogItems = mutableListOf<CatalogItem>().apply {
            catalog.movies?.let { addAll(it) }
            catalog.series?.let { addAll(it) }
            catalog.documentaries?.let { addAll(it) }
            catalog.shortFilms?.let { addAll(it) }
        }

        if (allCatalogItems.isEmpty()) return@withContext emptyList()

        val activeUserId = userId.ifBlank { "guest" }
        val now = System.currentTimeMillis()

        // 1. Verificar Caché Local de 1 Semana
        if (!forceRefresh) {
            val cachedJson = prefsManager.getString(PREF_RECOMMENDATION_CACHE)
            if (!cachedJson.isNullOrEmpty()) {
                try {
                    val typeToken = object : TypeToken<RecommendationCachePayload>() {}.type
                    val cachePayload: RecommendationCachePayload? = gson.fromJson(cachedJson, typeToken)

                    if (cachePayload != null &&
                        cachePayload.userId == activeUserId &&
                        (now - cachePayload.timestamp) < ONE_WEEK_MS &&
                        cachePayload.itemReferences.isNotEmpty()
                    ) {
                        val cachedItems = cachePayload.itemReferences.mapNotNull { ref ->
                            allCatalogItems.find { it.id.equals(ref.id, ignoreCase = true) }
                        }
                        if (cachedItems.size >= 5) {
                            return@withContext cachedItems.take(MAX_RECOMMENDATIONS)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. Obtener Historial, Favoritos, Votos y Calificaciones por Estrellas
        val playbackList: List<PlaybackProgressEntity> = try {
            playbackProgressRepository.getAllPlaybackProgress().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val favoritesList: List<FavoriteEntity> = try {
            favoritesDao.getAllFavorites().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val userVotesMap: Map<String, Int> = try {
            voteRepository.getUserVotes()
        } catch (e: Exception) {
            emptyMap()
        }

        val userRatingsMap: Map<String, Int> = try {
            ratingRepository.getUserRatings()
        } catch (e: Exception) {
            emptyMap()
        }

        val cloudTasteProfile: UserTasteProfile? = try {
            cloudRepository.getUserTasteProfile()
        } catch (e: Exception) {
            null
        }

        val favoriteIds = favoritesList.map { it.contentId.lowercase(Locale.ROOT) }.toSet()
        val finishedItemIds = mutableSetOf<String>()
        val franchiseMaxWatchedPart = mutableMapOf<String, Int>()

        var latinoCount = 0
        var castellanoCount = 0

        val genreWeights = mutableMapOf<String, Double>()
        val typeWeights = mutableMapOf<String, Double>()
        val directorWeights = mutableMapOf<String, Double>()
        val narratorWeights = mutableMapOf<String, Double>()

        // Fusionar perfil de la nube
        cloudTasteProfile?.let { profile ->
            if (profile.preferredDubbing == "castellano") castellanoCount += 5 else latinoCount += 5
            profile.topGenres.forEach { (genre, weight) ->
                genreWeights[genre] = (genreWeights[genre] ?: 0.0) + (weight * 0.8)
            }
            profile.topDirectors.forEach { (director, weight) ->
                directorWeights[director] = (directorWeights[director] ?: 0.0) + (weight * 0.8)
            }
            profile.topNarrators.forEach { (narrator, weight) ->
                narratorWeights[narrator] = (narratorWeights[narrator] ?: 0.0) + (weight * 0.8)
            }
        }

        val fourWeeksAgo = now - (28L * 24L * 60L * 60L * 1000L)

        // 3. Procesar Calificaciones por Estrellas (1 a 5 ⭐)
        userRatingsMap.forEach { (contentId, stars) ->
            val matchedItem = allCatalogItems.find { it.id.equals(contentId, ignoreCase = true) }
            if (matchedItem != null) {
                val weight = when (stars) {
                    5 -> 8.0
                    4 -> 5.0
                    3 -> 1.5
                    2 -> -4.0
                    1 -> -8.0
                    else -> 0.0
                }
                val type = getItemTypeForItem(matchedItem)
                typeWeights[type] = (typeWeights[type] ?: 0.0) + weight

                parseTokens(matchedItem.genero).forEach { genre ->
                    genreWeights[genre] = (genreWeights[genre] ?: 0.0) + weight
                }
                if (matchedItem.director.isNotBlank()) {
                    val director = matchedItem.director.trim().lowercase(Locale.ROOT)
                    directorWeights[director] = (directorWeights[director] ?: 0.0) + weight
                }
                if (matchedItem.narracion.isNotBlank()) {
                    parseTokens(matchedItem.narracion).forEach { narrator ->
                        narratorWeights[narrator] = (narratorWeights[narrator] ?: 0.0) + weight
                    }
                }
            }
        }

        // 4. Procesar Votos Explícitos "Me gusta" (+1) / "No me gusta" (-1)
        userVotesMap.forEach { (contentId, voteType) ->
            val matchedItem = allCatalogItems.find { it.id.equals(contentId, ignoreCase = true) }
            if (matchedItem != null) {
                val weight = if (voteType == 1) 6.0 else -4.0
                val type = getItemTypeForItem(matchedItem)
                typeWeights[type] = (typeWeights[type] ?: 0.0) + weight

                parseTokens(matchedItem.genero).forEach { genre ->
                    genreWeights[genre] = (genreWeights[genre] ?: 0.0) + weight
                }
                if (matchedItem.director.isNotBlank()) {
                    val director = matchedItem.director.trim().lowercase(Locale.ROOT)
                    directorWeights[director] = (directorWeights[director] ?: 0.0) + weight
                }
                if (matchedItem.narracion.isNotBlank()) {
                    parseTokens(matchedItem.narracion).forEach { narrator ->
                        narratorWeights[narrator] = (narratorWeights[narrator] ?: 0.0) + weight
                    }
                }
            }
        }

        // 5. Procesar Historial de Reproducción
        playbackList.forEach { progress ->
            val itemIdLower = progress.contentId.lowercase(Locale.ROOT)
            if (progress.isFinished) {
                finishedItemIds.add(itemIdLower)
            }

            val matchedItem = allCatalogItems.find { it.id.equals(progress.contentId, ignoreCase = true) }
            if (matchedItem != null) {
                val franchiseInfo = parseFranchiseInfo(matchedItem.title)
                if (franchiseInfo != null) {
                    val currentMax = franchiseMaxWatchedPart[franchiseInfo.baseKey] ?: 0
                    if (franchiseInfo.partNumber > currentMax) {
                        franchiseMaxWatchedPart[franchiseInfo.baseKey] = franchiseInfo.partNumber
                    }
                }

                val lang = getDubbingCategory(matchedItem)
                if (lang == "latino") latinoCount++ else if (lang == "castellano") castellanoCount++

                val progressRatio = if (progress.totalDurationMs > 0) {
                    (progress.currentPositionMs.toDouble() / progress.totalDurationMs.toDouble()).coerceIn(0.1, 1.0)
                } else 0.5

                val episodeBonus = (progress.partIndex * 1.2) + (progress.episodeIndex * 0.8) + 1.0
                val recencyMultiplier = if (progress.lastPlayedTimestamp > fourWeeksAgo) 1.8 else 1.0
                val weightFactor = progressRatio * episodeBonus * recencyMultiplier

                val type = getItemTypeForItem(matchedItem)
                typeWeights[type] = (typeWeights[type] ?: 0.0) + (1.5 * weightFactor)

                parseTokens(matchedItem.genero).forEach { genre ->
                    genreWeights[genre] = (genreWeights[genre] ?: 0.0) + (2.5 * weightFactor)
                }
                if (matchedItem.director.isNotBlank()) {
                    val director = matchedItem.director.trim().lowercase(Locale.ROOT)
                    directorWeights[director] = (directorWeights[director] ?: 0.0) + (1.8 * weightFactor)
                }
                if (matchedItem.narracion.isNotBlank()) {
                    parseTokens(matchedItem.narracion).forEach { narrator ->
                        narratorWeights[narrator] = (narratorWeights[narrator] ?: 0.0) + (1.8 * weightFactor)
                    }
                }
            }
        }

        // 6. Procesar FAVORITOS
        favoritesList.forEach { fav ->
            val matchedItem = allCatalogItems.find { it.id.equals(fav.contentId, ignoreCase = true) }
            if (matchedItem != null) {
                val lang = getDubbingCategory(matchedItem)
                if (lang == "latino") latinoCount += 3 else if (lang == "castellano") castellanoCount += 3

                val type = getItemTypeForItem(matchedItem)
                typeWeights[type] = (typeWeights[type] ?: 0.0) + 4.0

                parseTokens(matchedItem.genero).forEach { genre ->
                    genreWeights[genre] = (genreWeights[genre] ?: 0.0) + 5.0
                }
                if (matchedItem.director.isNotBlank()) {
                    val director = matchedItem.director.trim().lowercase(Locale.ROOT)
                    directorWeights[director] = (directorWeights[director] ?: 0.0) + 3.5
                }
                if (matchedItem.narracion.isNotBlank()) {
                    parseTokens(matchedItem.narracion).forEach { narrator ->
                        narratorWeights[narrator] = (narratorWeights[narrator] ?: 0.0) + 3.5
                    }
                }
            }
        }

        val preferredDubbing = if (castellanoCount > latinoCount) "castellano" else "latino"

        // Guardar/Actualizar Perfil de Gustos en Firebase Firestore en segundo plano
        try {
            val tasteProfile = UserTasteProfile(
                preferredDubbing = preferredDubbing,
                topGenres = genreWeights.entries.sortedByDescending { it.value }.take(10).associate { it.key to it.value },
                topDirectors = directorWeights.entries.sortedByDescending { it.value }.take(5).associate { it.key to it.value },
                topNarrators = narratorWeights.entries.sortedByDescending { it.value }.take(5).associate { it.key to it.value },
                lastUpdated = now
            )
            cloudRepository.saveUserTasteProfile(tasteProfile)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 7. Filtrar Candidatos y Calcular Puntaje Ultra-Preciso
        val candidateItems = allCatalogItems.filter { item ->
            val itemIdLower = item.id.lowercase(Locale.ROOT)
            
            if (finishedItemIds.contains(itemIdLower)) return@filter false

            val franchiseInfo = parseFranchiseInfo(item.title)
            if (franchiseInfo != null) {
                val maxWatched = franchiseMaxWatchedPart[franchiseInfo.baseKey] ?: 0
                val targetNextPart = maxWatched + 1
                if (franchiseInfo.partNumber != targetNextPart) {
                    return@filter false
                }
            }

            true
        }

        val scoredCandidates = candidateItems.map { item ->
            val itemIdLower = item.id.lowercase(Locale.ROOT)
            val itemType = getItemTypeForItem(item)
            val parsedGenres = parseTokens(item.genero)
            val itemDubbing = getDubbingCategory(item)
            val itemDirector = item.director.trim().lowercase(Locale.ROOT)
            val parsedNarrators = parseTokens(item.narracion)

            val officialRating = item.filmaffinity.trim().replace(",", ".").toDoubleOrNull()?.let { raw ->
                if (raw > 5.0) raw / 2.0 else raw
            } ?: 4.0

            var score = officialRating * 1.5

            if (itemDubbing == preferredDubbing) {
                score += 3.5
            }

            if (favoriteIds.contains(itemIdLower)) {
                score += 8.0
            }

            // Impacto Directo de Calificaciones por Estrellas (1 a 5 ⭐)
            val userStars = userRatingsMap[itemIdLower] ?: userRatingsMap[item.id] ?: 0
            when (userStars) {
                5 -> score += 14.0 // ⭐⭐⭐⭐⭐ Le encantó (Impacto Máximo Supremo)
                4 -> score += 8.0  // ⭐⭐⭐⭐ Muy buena
                3 -> score += 2.0  // ⭐⭐⭐ Promedio
                2 -> score -= 10.0 // ⭐⭐ Mala (Penalización)
                1 -> score -= 20.0 // ⭐ Pésima (Penalización Extrema)
            }

            // Impacto Directo de Votos en Firebase ("ME GUSTA" vs "NO ME GUSTA")
            val userVote = userVotesMap[itemIdLower] ?: userVotesMap[item.id] ?: 0
            if (userVote == 1) {
                score += 10.0
            } else if (userVote == -1) {
                score -= 15.0
            }

            score += (typeWeights[itemType] ?: 0.0) * 1.4

            parsedGenres.forEach { genre ->
                score += (genreWeights[genre] ?: 0.0) * 2.8
            }

            if (itemDirector.isNotEmpty() && directorWeights.containsKey(itemDirector)) {
                score += (directorWeights[itemDirector] ?: 0.0) * 2.0
            }

            parsedNarrators.forEach { narrator ->
                if (narratorWeights.containsKey(narrator)) {
                    score += (narratorWeights[narrator] ?: 0.0) * 2.0
                }
            }

            Pair(item, score)
        }

        // 8. Desduplicar Títulos por Doblaje Estricto
        val groupedByBaseTitle = scoredCandidates.groupBy { pair ->
            normalizeTitle(pair.first.title)
        }

        val deduplicatedItems = mutableListOf<CatalogItem>()
        for ((_, candidatePairs) in groupedByBaseTitle) {
            val preferredMatches = candidatePairs.filter { pair ->
                getDubbingCategory(pair.first) == preferredDubbing
            }

            val chosenPair = preferredMatches.maxByOrNull { it.second }
                ?: candidatePairs.maxByOrNull { it.second }

            if (chosenPair != null) {
                deduplicatedItems.add(chosenPair.first)
            }
        }

        val sortedFinalList = deduplicatedItems.sortedByDescending { item ->
            scoredCandidates.find { it.first.id == item.id }?.second ?: 0.0
        }.distinctBy { it.id }

        val finalRecommendations = sortedFinalList.take(MAX_RECOMMENDATIONS)

        // 9. Guardar Caché Local con Timestamp
        try {
            val references = finalRecommendations.map { item ->
                RecommendedItemReference(id = item.id, type = getItemTypeForItem(item))
            }
            val newPayload = RecommendationCachePayload(
                timestamp = now,
                userId = activeUserId,
                itemReferences = references
            )
            prefsManager.saveString(PREF_RECOMMENDATION_CACHE, gson.toJson(newPayload))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        finalRecommendations
    }

    private fun getDubbingCategory(item: CatalogItem): String {
        val rawIdioma = when (item) {
            is Movie -> item.idioma
            is Serie -> item.idioma
            is Documentary -> item.idioma
            is ShortFilm -> item.idioma
            else -> ""
        }.trim()

        if (rawIdioma == "2") return "latino"
        if (rawIdioma == "1") return "castellano"

        val combined = "$rawIdioma ${item.title}".lowercase(Locale.ROOT)
        if (combined.contains("castellano") || combined.contains("españa") || combined.contains("espana") || combined.contains("(cast)") || combined.contains("[cast]")) {
            return "castellano"
        }
        if (combined.contains("latino") || combined.contains("(lat)") || combined.contains("[lat]")) {
            return "latino"
        }
        return "latino"
    }

    private fun normalizeTitle(title: String): String {
        var clean = title.lowercase(Locale.ROOT)
        clean = java.text.Normalizer.normalize(clean, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        clean = clean.replace(Regex("(?i)\\b(latino|castellano|espana|espanol|doblaje|audio|subtitulado|lat|cast|es|mx)\\b"), "")
        clean = clean.replace(Regex("[():,\\[\\]\\-_.~'\"!]"), " ")
        return clean.replace(Regex("\\s+"), " ").trim()
    }

    private fun parseFranchiseInfo(title: String): FranchiseInfo? {
        val cleanTitle = title.trim()
        val regexPart = Regex("(?i)(.*?)\\s+(?:parte|volumen|vol|capítulo|cap|nº)?\\s*(\\d+|i{1,3}|iv|v|vi|vii|viii|ix|x)\\b")
        val match = regexPart.find(cleanTitle)

        if (match != null) {
            val baseName = match.groupValues[1].trim().lowercase(Locale.ROOT)
            val partStr = match.groupValues[2].trim()
            val partNum = parseRomanOrArabic(partStr)

            if (baseName.length >= 3 && partNum > 0) {
                return FranchiseInfo(baseKey = baseName, partNumber = partNum)
            }
        }

        return null
    }

    private fun parseRomanOrArabic(str: String): Int {
        str.toIntOrNull()?.let { return it }
        return when (str.lowercase(Locale.ROOT)) {
            "i" -> 1
            "ii" -> 2
            "iii" -> 3
            "iv" -> 4
            "v" -> 5
            "vi" -> 6
            "vii" -> 7
            "viii" -> 8
            "ix" -> 9
            "x" -> 10
            else -> 1
        }
    }

    private fun parseTokens(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        return rawText.split(".", "|", ",", "/", "-", ";")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() && it.length > 2 }
    }

    private fun getItemTypeForItem(item: CatalogItem): String {
        return when (item) {
            is Serie -> "serie"
            is Movie -> "pelicula"
            is Documentary -> "documental"
            is ShortFilm -> "cortometraje"
            else -> "pelicula"
        }
    }
}
