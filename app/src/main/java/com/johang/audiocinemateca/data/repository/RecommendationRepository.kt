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
import java.util.Random
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
        private const val PREF_RECOMMENDATION_CACHE = "user_recommendations_cache_v7"
        private const val ONE_WEEK_MS = 7L * 24L * 60L * 60L * 1000L // 7 días en ms
        private const val MAX_RECOMMENDATIONS = 20

        private val STOP_WORDS = setOf(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "a", "al", "en", "para", "por",
            "con", "sin", "sobre", "entre", "tras", "durante", "hasta", "hacia", "desde", "contra", "segun", "bajo",
            "y", "e", "ni", "o", "u", "pero", "mas", "que", "como", "cuando", "donde", "quien", "cual", "cuyo",
            "su", "sus", "mi", "mis", "tu", "tus", "nuestro", "nuestra", "nuestros", "nuestras", "este", "esta",
            "estos", "estas", "ese", "esa", "esos", "esas", "aquel", "aquella", "aquellos", "aquellas", "todo",
            "toda", "todos", "todas", "otro", "otra", "otros", "otras", "mismo", "misma", "mismos", "mismas",
            "pelicula", "serie", "capitulo", "episodio", "temporada", "historia", "vida", "ano", "anos", "parte",
            "audio", "doblaje", "latino", "castellano", "espanol", "official", "version", "completa", "alta", "calidad",
            "the", "and", "of", "to", "in", "for", "with", "on", "at", "from", "by", "about", "as", "into"
        )
    }

    /**
     * Motor Ultra-Preciso v6.0 (Hábitos Semánticos + Clusters Temáticos + Cero Contenido Ya Visto):
     * 1. Extracción Temática Profunda: Aprende hábitos específicos (Anime, Aviación, Ciencia Ficción Espacial, Bélico, etc.)
     *    analizando géneros, palabras clave en títulos, sinopsis y países.
     * 2. Exclusión Estricta de Todo Contenido Visto / En Progreso: Descarta películas iniciadas (>1 min o >15%), series con
     *    episodios iniciados y obras calificadas.
     * 3. Depuración Instantánea: Si el usuario empieza o termina una obra recomendada, se purga de inmediato del caché.
     * 4. Rotación Dinámica Semanal: Distribución balanceada por afinidad con semilla semanal para variedad constante.
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

        // 1. Obtener Historial de Reproducción, Calificaciones, Votos y Favoritos
        val playbackList: List<PlaybackProgressEntity> = try {
            playbackProgressRepository.getAllPlaybackProgress().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val userRatingsMap: Map<String, Int> = try {
            ratingRepository.getUserRatings()
        } catch (e: Exception) {
            emptyMap()
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

        val cloudTasteProfile: UserTasteProfile? = try {
            cloudRepository.getUserTasteProfile()
        } catch (e: Exception) {
            null
        }

        // 2. Identificar Contenidos Ya Vistos o Iniciados (DESCALIFICADOS)
        val disqualifiedItemIds = mutableSetOf<String>()
        val franchiseMaxWatchedPart = mutableMapOf<String, Int>()

        playbackList.forEach { progress ->
            val idLower = progress.contentId.lowercase(Locale.ROOT)
            val isPlayed = progress.isFinished ||
                    progress.currentPositionMs > 60_000L ||
                    (progress.totalDurationMs > 0 && (progress.currentPositionMs.toDouble() / progress.totalDurationMs.toDouble()) >= 0.15)

            if (isPlayed) {
                disqualifiedItemIds.add(idLower)
            }
        }

        userRatingsMap.keys.forEach { ratedId ->
            disqualifiedItemIds.add(ratedId.lowercase(Locale.ROOT))
        }

        // 3. Verificar Caché Local con Evicción Inmediata de Contenido Visto
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
                        val validCachedItems = cachePayload.itemReferences.mapNotNull { ref ->
                            allCatalogItems.find { it.id.equals(ref.id, ignoreCase = true) }
                        }.filter { item ->
                            !disqualifiedItemIds.contains(item.id.lowercase(Locale.ROOT))
                        }

                        if (validCachedItems.size >= 8) {
                            return@withContext validCachedItems.take(MAX_RECOMMENDATIONS)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 4. Construcción del Perfil de Hábitos y Afinidad Temática
        val genreWeights = mutableMapOf<String, Double>()
        val keywordWeights = mutableMapOf<String, Double>()
        val typeWeights = mutableMapOf<String, Double>()
        val directorWeights = mutableMapOf<String, Double>()
        val narratorWeights = mutableMapOf<String, Double>()

        var latinoCount = 0
        var castellanoCount = 0

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

        val twoWeeksAgo = now - (14L * 24L * 60L * 60L * 1000L)
        val fourWeeksAgo = now - (28L * 24L * 60L * 60L * 1000L)

        // A) Procesar Calificaciones por Estrellas (1 a 5 ⭐)
        userRatingsMap.forEach { (contentId, stars) ->
            val matchedItem = allCatalogItems.find { it.id.equals(contentId, ignoreCase = true) }
            if (matchedItem != null) {
                val weight = when (stars) {
                    5 -> 9.0
                    4 -> 6.0
                    3 -> 2.0
                    2 -> -6.0
                    1 -> -12.0
                    else -> 0.0
                }
                val type = getItemTypeForItem(matchedItem)
                typeWeights[type] = (typeWeights[type] ?: 0.0) + weight

                parseTokens(matchedItem.genero).forEach { genre ->
                    genreWeights[genre] = (genreWeights[genre] ?: 0.0) + weight
                }

                extractSemanticKeywords(matchedItem).forEach { kw ->
                    keywordWeights[kw] = (keywordWeights[kw] ?: 0.0) + (weight * 1.5)
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

        // B) Procesar Votos Explícitos "Me gusta" (+1) / "No me gusta" (-1)
        userVotesMap.forEach { (contentId, voteType) ->
            val matchedItem = allCatalogItems.find { it.id.equals(contentId, ignoreCase = true) }
            if (matchedItem != null) {
                val weight = if (voteType == 1) 7.0 else -6.0
                val type = getItemTypeForItem(matchedItem)
                typeWeights[type] = (typeWeights[type] ?: 0.0) + weight

                parseTokens(matchedItem.genero).forEach { genre ->
                    genreWeights[genre] = (genreWeights[genre] ?: 0.0) + weight
                }

                extractSemanticKeywords(matchedItem).forEach { kw ->
                    keywordWeights[kw] = (keywordWeights[kw] ?: 0.0) + (weight * 1.5)
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

        // C) Procesar Historial de Reproducción con Ponderación de Retención
        playbackList.forEach { progress ->
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
                } else 0.6

                val episodeBonus = (progress.partIndex * 1.2) + (progress.episodeIndex * 0.8) + 1.0
                val recencyMultiplier = if (progress.lastPlayedTimestamp > twoWeeksAgo) 2.2
                else if (progress.lastPlayedTimestamp > fourWeeksAgo) 1.5
                else 1.0

                val weightFactor = progressRatio * episodeBonus * recencyMultiplier

                val type = getItemTypeForItem(matchedItem)
                typeWeights[type] = (typeWeights[type] ?: 0.0) + (1.8 * weightFactor)

                parseTokens(matchedItem.genero).forEach { genre ->
                    genreWeights[genre] = (genreWeights[genre] ?: 0.0) + (3.0 * weightFactor)
                }

                extractSemanticKeywords(matchedItem).forEach { kw ->
                    keywordWeights[kw] = (keywordWeights[kw] ?: 0.0) + (3.8 * weightFactor)
                }

                if (matchedItem.director.isNotBlank()) {
                    val director = matchedItem.director.trim().lowercase(Locale.ROOT)
                    directorWeights[director] = (directorWeights[director] ?: 0.0) + (2.0 * weightFactor)
                }
                if (matchedItem.narracion.isNotBlank()) {
                    parseTokens(matchedItem.narracion).forEach { narrator ->
                        narratorWeights[narrator] = (narratorWeights[narrator] ?: 0.0) + (2.0 * weightFactor)
                    }
                }
            }
        }

        // D) Procesar FAVORITOS
        val favoriteIds = favoritesList.map { it.contentId.lowercase(Locale.ROOT) }.toSet()
        favoritesList.forEach { fav ->
            val matchedItem = allCatalogItems.find { it.id.equals(fav.contentId, ignoreCase = true) }
            if (matchedItem != null) {
                val lang = getDubbingCategory(matchedItem)
                if (lang == "latino") latinoCount += 3 else if (lang == "castellano") castellanoCount += 3

                val type = getItemTypeForItem(matchedItem)
                typeWeights[type] = (typeWeights[type] ?: 0.0) + 4.5

                parseTokens(matchedItem.genero).forEach { genre ->
                    genreWeights[genre] = (genreWeights[genre] ?: 0.0) + 5.5
                }

                extractSemanticKeywords(matchedItem).forEach { kw ->
                    keywordWeights[kw] = (keywordWeights[kw] ?: 0.0) + 6.0
                }

                if (matchedItem.director.isNotBlank()) {
                    val director = matchedItem.director.trim().lowercase(Locale.ROOT)
                    directorWeights[director] = (directorWeights[director] ?: 0.0) + 4.0
                }
                if (matchedItem.narracion.isNotBlank()) {
                    parseTokens(matchedItem.narracion).forEach { narrator ->
                        narratorWeights[narrator] = (narratorWeights[narrator] ?: 0.0) + 4.0
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

        // 5. Filtrar Candidatos Estrictamente No Vistos
        val candidateItems = allCatalogItems.filter { item ->
            val itemIdLower = item.id.lowercase(Locale.ROOT)
            if (disqualifiedItemIds.contains(itemIdLower)) return@filter false

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

        // 6. Calcular Puntaje con Afinidades Temáticas Semánticas
        val scoredCandidates = candidateItems.map { item ->
            val itemIdLower = item.id.lowercase(Locale.ROOT)
            val itemType = getItemTypeForItem(item)
            val parsedGenres = parseTokens(item.genero)
            val itemKeywords = extractSemanticKeywords(item)
            val itemDubbing = getDubbingCategory(item)
            val itemDirector = item.director.trim().lowercase(Locale.ROOT)
            val parsedNarrators = parseTokens(item.narracion)

            val officialRating = item.filmaffinity.trim().replace(",", ".").toDoubleOrNull()?.let { raw ->
                if (raw > 5.0) raw / 2.0 else raw
            } ?: 4.0

            var score = officialRating * 1.5

            if (itemDubbing == preferredDubbing) {
                score += 4.0
            }

            if (favoriteIds.contains(itemIdLower)) {
                score += 8.0
            }

            score += (typeWeights[itemType] ?: 0.0) * 1.5

            // Impacto de Afinidad Temática Semántica (ej. anime, aviones, bélico, espacio)
            itemKeywords.forEach { kw ->
                score += (keywordWeights[kw] ?: 0.0) * 3.5
            }

            parsedGenres.forEach { genre ->
                score += (genreWeights[genre] ?: 0.0) * 2.8
            }

            if (itemDirector.isNotEmpty() && directorWeights.containsKey(itemDirector)) {
                score += (directorWeights[itemDirector] ?: 0.0) * 2.5
            }

            parsedNarrators.forEach { narrator ->
                if (narratorWeights.containsKey(narrator)) {
                    score += (narratorWeights[narrator] ?: 0.0) * 2.2
                }
            }

            Pair(item, score)
        }

        // 7. Desduplicar Títulos por Doblaje Estricto
        val groupedByBaseTitle = scoredCandidates.groupBy { pair ->
            normalizeTitle(pair.first.title)
        }

        val deduplicatedItems = mutableListOf<Pair<CatalogItem, Double>>()
        for ((_, candidatePairs) in groupedByBaseTitle) {
            val preferredMatches = candidatePairs.filter { pair ->
                getDubbingCategory(pair.first) == preferredDubbing
            }

            val chosenPair = preferredMatches.maxByOrNull { it.second }
                ?: candidatePairs.maxByOrNull { it.second }

            if (chosenPair != null) {
                deduplicatedItems.add(chosenPair)
            }
        }

        // 8. Rotación Semanal Dinámica y Balance de Categorías
        val weeklySeed = (now / (7L * 24L * 60L * 60L * 1000L)).toInt()
        val sortedByScore = deduplicatedItems.sortedByDescending { it.second }

        val topThemeMatches = sortedByScore.filter { it.second > 15.0 }
        val freshDiscoveries = sortedByScore.filter { it.second <= 15.0 }

        val finalSelection = mutableListOf<CatalogItem>()

        // 50% Afición Temática Principal, 30% Afinidad Secundaria, 20% Descubrimientos de Alta Calidad
        val random = Random(weeklySeed.toLong())
        val topPicks = topThemeMatches.take(12).shuffled(random)
        finalSelection.addAll(topPicks.map { it.first })

        val remainingCount = MAX_RECOMMENDATIONS - finalSelection.size
        if (remainingCount > 0) {
            val nextBest = sortedByScore.filter { pair -> finalSelection.none { it.id == pair.first.id } }
                .take(remainingCount)
                .map { it.first }
            finalSelection.addAll(nextBest)
        }

        val finalRecommendations = finalSelection.distinctBy { it.id }.take(MAX_RECOMMENDATIONS)

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

    private fun extractSemanticKeywords(item: CatalogItem): Set<String> {
        val keywords = mutableSetOf<String>()

        parseTokens(item.genero).forEach { g ->
            val clean = normalizeWord(g)
            if (clean.length > 2 && clean !in STOP_WORDS) keywords.add(clean)
        }

        val rawPais = when (item) {
            is Movie -> item.pais
            is Serie -> item.pais
            is Documentary -> item.pais
            is ShortFilm -> item.pais
            else -> ""
        }
        parseTokens(rawPais).forEach { p ->
            val clean = normalizeWord(p)
            if (clean.length > 2 && clean !in STOP_WORDS) {
                keywords.add(clean)
                if (clean == "japon" || clean == "japan") {
                    keywords.add("anime")
                    keywords.add("manga")
                }
            }
        }

        val rawTitle = item.title
        val rawSinopsis = when (item) {
            is Movie -> item.sinopsis
            is Serie -> item.sinopsis
            is Documentary -> item.sinopsis
            is ShortFilm -> item.sinopsis
            else -> ""
        }

        val combinedText = normalizeText("$rawTitle $rawSinopsis ${item.genero} $rawPais")

        // Detección de clusters temáticos profundos:
        if (combinedText.contains("anime") || combinedText.contains("manga") || combinedText.contains("otaku") ||
            (combinedText.contains("animacion") && (combinedText.contains("japon") || combinedText.contains("japones")))) {
            keywords.add("anime")
            keywords.add("animacion_japonesa")
        }
        if (combinedText.contains("avion") || combinedText.contains("aviones") || combinedText.contains("aviacion") ||
            combinedText.contains("vuelo") || combinedText.contains("piloto") || combinedText.contains("aereo") ||
            combinedText.contains("aeropuerto") || combinedText.contains("catastrofes aereas") || combinedText.contains("mayday")) {
            keywords.add("aviacion")
            keywords.add("aviones")
            keywords.add("aereo")
        }
        if (combinedText.contains("espacio") || combinedText.contains("espacial") || combinedText.contains("galaxia") ||
            combinedText.contains("extraterrestre") || combinedText.contains("alien") || combinedText.contains("astronomo") ||
            combinedText.contains("astronauta")) {
            keywords.add("espacio")
            keywords.add("ciencia_ficcion_espacial")
        }
        if (combinedText.contains("guerra") || combinedText.contains("militar") || combinedText.contains("ejercito") ||
            combinedText.contains("soldado") || combinedText.contains("batalla") || combinedText.contains("belico")) {
            keywords.add("belico")
            keywords.add("militar")
        }
        if (combinedText.contains("zombi") || combinedText.contains("apocalipsis") || combinedText.contains("infectados") ||
            combinedText.contains("supervivencia")) {
            keywords.add("zombies")
            keywords.add("apocalipsis")
        }
        if (combinedText.contains("superheroe") || combinedText.contains("mutante") || combinedText.contains("marvel") ||
            combinedText.contains("dc comics") || combinedText.contains("vengadores") || combinedText.contains("batman")) {
            keywords.add("superheroes")
        }
        if (combinedText.contains("crimen") || combinedText.contains("policia") || combinedText.contains("detective") ||
            combinedText.contains("asesino") || combinedText.contains("mafia") || combinedText.contains("investigacion")) {
            keywords.add("crimen_policial")
            keywords.add("misterio")
        }
        if (combinedText.contains("terror") || combinedText.contains("miedo") || combinedText.contains("paranormal") ||
            combinedText.contains("fantasmas") || combinedText.contains("posesion")) {
            keywords.add("terror")
        }

        extractMeaningfulWords(rawTitle).forEach { word ->
            if (word.length >= 4 && word !in STOP_WORDS) {
                keywords.add(word)
            }
        }

        return keywords
    }

    private fun normalizeWord(word: String): String {
        return java.text.Normalizer.normalize(word.lowercase(Locale.ROOT).trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9_]"), "")
    }

    private fun normalizeText(text: String): String {
        return java.text.Normalizer.normalize(text.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun extractMeaningfulWords(text: String): List<String> {
        val clean = normalizeText(text)
        return clean.split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in STOP_WORDS }
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
            .map { normalizeWord(it) }
            .filter { it.isNotEmpty() && it.length > 2 && it !in STOP_WORDS }
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
