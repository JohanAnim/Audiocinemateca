package com.johang.audiocinemateca.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.domain.model.CatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.min

data class Top10ItemReference(
    val id: String,
    val score: Double,
    val rankPosition: Int,
    val contentType: String? = null,
    val title: String? = null
)

data class GlobalRankingPayload(
    val lastCalculatedTimestamp: Long = 0L,
    val items: List<Top10ItemReference> = emptyList()
)

@Singleton
class RankingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val catalogRepository: CatalogRepository,
    private val prefsManager: SharedPreferencesManager,
    private val gson: Gson
) {

    companion object {
        private const val PREF_RANKING_CACHE = "global_top10_cache_v2"
        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L // 7 días (Caducidad Semanal)
        private const val COLLECTION_GLOBAL_RANKINGS = "global_rankings"
        private const val DOC_WEEKLY_TOP10 = "weekly_top10"
    }

    /**
     * Escucha en TIEMPO REAL los cambios del documento 'weekly_top10' en Firestore.
     * Si la administración o el backend actualiza el ranking, la app refleja los cambios al instante.
     */
    fun observeWeeklyTop10(): Flow<List<Pair<Int, CatalogItem>>> = callbackFlow {
        val docRef = firestore.collection(COLLECTION_GLOBAL_RANKINGS).document(DOC_WEEKLY_TOP10)

        val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }

            launch(Dispatchers.IO) {
                val catalog = catalogRepository.getCatalog() ?: return@launch
                val allCatalogItems = mutableListOf<CatalogItem>().apply {
                    catalog.movies?.let { addAll(it) }
                    catalog.series?.let { addAll(it) }
                    catalog.documentaries?.let { addAll(it) }
                    catalog.shortFilms?.let { addAll(it) }
                }

                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val rawItems = snapshot.get("items") as? List<Map<String, Any>>
                    val parsedItems = rawItems?.mapNotNull { itemMap ->
                        val id = itemMap["id"]?.toString()
                            ?: itemMap["contentId"]?.toString()
                            ?: itemMap["itemId"]?.toString()
                            ?: ""
                        val title = itemMap["title"]?.toString()
                            ?: itemMap["titulo"]?.toString()
                            ?: itemMap["name"]?.toString()
                            ?: ""
                        val score = (itemMap["score"] as? Number)?.toDouble() ?: 0.0
                        val rankPos = (itemMap["rankPosition"] as? Number)?.toInt()
                            ?: (itemMap["position"] as? Number)?.toInt()
                            ?: 1
                        if (id.isBlank() && title.isBlank()) return@mapNotNull null
                        Top10ItemReference(id, score, rankPos, itemMap["contentType"]?.toString(), title)
                    }?.sortedWith(compareBy({ it.rankPosition }, { -it.score })) ?: emptyList()

                    val mapped = mapPayloadToCatalogItems(parsedItems, allCatalogItems)
                    if (mapped.isNotEmpty()) {
                        try {
                            val timestamp = snapshot.getLong("lastCalculatedTimestamp") ?: 0L
                            prefsManager.saveString(PREF_RANKING_CACHE, gson.toJson(GlobalRankingPayload(timestamp, parsedItems)))
                        } catch (e: Exception) { e.printStackTrace() }
                        trySend(mapped)
                        return@launch
                    }
                }

                val fallbackItems = getFallbackTop10(allCatalogItems)
                trySend(fallbackItems)
            }
        }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Consulta el Top 10 Semanal Global desde Firestore.
     * Si no está publicado aún en el servidor, utiliza la caché o una lista ordenada por calificación.
     */
    suspend fun getWeeklyTop10(forceRefresh: Boolean = false): List<Pair<Int, CatalogItem>> = withContext(Dispatchers.IO) {
        val catalog = catalogRepository.getCatalog() ?: return@withContext emptyList()
        val allCatalogItems = mutableListOf<CatalogItem>().apply {
            catalog.movies?.let { addAll(it) }
            catalog.series?.let { addAll(it) }
            catalog.documentaries?.let { addAll(it) }
            catalog.shortFilms?.let { addAll(it) }
        }

        if (allCatalogItems.isEmpty()) return@withContext emptyList()

        // 1. Consultar prioritariamente Firestore (Servidor / Firebase)
        val docRef = firestore.collection(COLLECTION_GLOBAL_RANKINGS).document(DOC_WEEKLY_TOP10)
        var remotePayload: GlobalRankingPayload? = null

        try {
            val docSnapshot = docRef.get().await()
            if (docSnapshot.exists()) {
                val timestamp = docSnapshot.getLong("lastCalculatedTimestamp") ?: 0L
                @Suppress("UNCHECKED_CAST")
                val rawItems = docSnapshot.get("items") as? List<Map<String, Any>>
                val parsedItems = rawItems?.mapNotNull { itemMap ->
                    val id = itemMap["id"]?.toString()
                        ?: itemMap["contentId"]?.toString()
                        ?: itemMap["itemId"]?.toString()
                        ?: ""
                    val title = itemMap["title"]?.toString()
                        ?: itemMap["titulo"]?.toString()
                        ?: itemMap["name"]?.toString()
                        ?: ""
                    val score = (itemMap["score"] as? Number)?.toDouble() ?: 0.0
                    val rankPos = (itemMap["rankPosition"] as? Number)?.toInt()
                        ?: (itemMap["position"] as? Number)?.toInt()
                        ?: 1
                    if (id.isBlank() && title.isBlank()) return@mapNotNull null
                    Top10ItemReference(id, score, rankPos, itemMap["contentType"]?.toString(), title)
                } ?: emptyList()

                remotePayload = GlobalRankingPayload(timestamp, parsedItems)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Usar documento oficial de Firestore si existe
        if (remotePayload != null && remotePayload.items.isNotEmpty()) {
            val mappedRemote = mapPayloadToCatalogItems(remotePayload.items, allCatalogItems)
            if (mappedRemote.isNotEmpty()) {
                try {
                    prefsManager.saveString(PREF_RANKING_CACHE, gson.toJson(remotePayload))
                } catch (e: Exception) { e.printStackTrace() }
                return@withContext mappedRemote
            }
        }

        // 3. Verificar Caché Local como respaldo
        if (!forceRefresh) {
            val cachedJson = prefsManager.getString(PREF_RANKING_CACHE)
            if (!cachedJson.isNullOrEmpty()) {
                try {
                    val typeToken = object : TypeToken<GlobalRankingPayload>() {}.type
                    val cachePayload: GlobalRankingPayload? = gson.fromJson(cachedJson, typeToken)

                    if (cachePayload != null && cachePayload.items.isNotEmpty()) {
                        val mappedLocal = mapPayloadToCatalogItems(cachePayload.items, allCatalogItems)
                        if (mappedLocal.isNotEmpty()) {
                            return@withContext mappedLocal
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 4. Fallback directo al catálogo ordenado por mejor calificación sin cálculo pesado descentralizado
        getFallbackTop10(allCatalogItems)
    }

    private fun getFallbackTop10(allCatalogItems: List<CatalogItem>): List<Pair<Int, CatalogItem>> {
        val sorted = allCatalogItems.distinctBy { it.id }.sortedByDescending { item ->
            val raw = item.filmaffinity.trim().replace(",", ".").toDoubleOrNull() ?: 3.5
            if (raw > 5.0) raw / 2.0 else raw
        }.take(10)
        return sorted.mapIndexed { index, item -> Pair(index + 1, item) }
    }

    private fun mapPayloadToCatalogItems(
        items: List<Top10ItemReference>,
        allCatalogItems: List<CatalogItem>
    ): List<Pair<Int, CatalogItem>> {
        val sortedRefs = items.sortedWith(compareBy({ it.rankPosition }, { -it.score }))
        val seenIds = mutableSetOf<String>()
        val matchedCatalogItems = mutableListOf<CatalogItem>()

        for (ref in sortedRefs) {
            val refIdClean = ref.id.trim().lowercase(Locale.ROOT)
            val refTitle = ref.title?.trim() ?: ""
            val normRefTitle = if (refTitle.isNotEmpty()) normalizeTitle(refTitle) else ""

            // 1. Coincidencia por ID + Tipo
            var matched: CatalogItem? = if (refIdClean.isNotEmpty()) {
                allCatalogItems.find { item ->
                    item.id.trim().lowercase(Locale.ROOT) == refIdClean && isTypeMatching(getCatalogItemType(item), ref.contentType)
                }
            } else null

            // 2. Coincidencia por ID solo (si falla tipo)
            if (matched == null && refIdClean.isNotEmpty()) {
                matched = allCatalogItems.find { item ->
                    item.id.trim().lowercase(Locale.ROOT) == refIdClean
                }
            }

            // 3. Coincidencia por Título Normalizado Exacto
            if (matched == null && normRefTitle.isNotEmpty()) {
                matched = allCatalogItems.find { item ->
                    normalizeTitle(item.title) == normRefTitle
                }
            }

            // 4. Coincidencia por Título Parcial
            if (matched == null && normRefTitle.length >= 4) {
                matched = allCatalogItems.find { item ->
                    val normItem = normalizeTitle(item.title)
                    normItem.contains(normRefTitle) || normRefTitle.contains(normItem)
                }
            }

            if (matched != null) {
                val compositeKey = "${getCatalogItemType(matched)}_${matched.id.trim().lowercase(Locale.ROOT)}"
                if (seenIds.add(compositeKey)) {
                    matchedCatalogItems.add(matched)
                }
            }
        }

        // Si hay menos de 10 elementos mapeados, rellenar con las obras del catálogo
        if (matchedCatalogItems.size < 10) {
            val remainingCatalog = allCatalogItems
                .filterNot { item ->
                    val compositeKey = "${getCatalogItemType(item)}_${item.id.trim().lowercase(Locale.ROOT)}"
                    seenIds.contains(compositeKey)
                }
                .sortedByDescending { item ->
                    val rating = item.filmaffinity.trim().replace(",", ".").toDoubleOrNull() ?: 3.5
                    if (rating > 5.0) rating / 2.0 else rating
                }

            for (fallbackItem in remainingCatalog) {
                if (matchedCatalogItems.size >= 10) break
                val compositeKey = "${getCatalogItemType(fallbackItem)}_${fallbackItem.id.trim().lowercase(Locale.ROOT)}"
                if (seenIds.add(compositeKey)) {
                    matchedCatalogItems.add(fallbackItem)
                }
            }
        }

        // Asignar posiciones continuas y secuenciales del 1 al 10 sin huecos
        return matchedCatalogItems.distinctBy { it.id }.take(10).mapIndexed { index, item ->
            Pair(index + 1, item)
        }
    }

    private fun isTypeMatching(matchedType: String, refType: String?): Boolean {
        if (refType.isNullOrBlank()) return true
        val cleanRef = refType.trim().lowercase(Locale.ROOT)
        val cleanMatched = matchedType.trim().lowercase(Locale.ROOT)
        if (cleanRef == cleanMatched) return true
        if (cleanRef in listOf("movie", "pelicula", "peliculas") && cleanMatched in listOf("movie", "pelicula", "peliculas")) return true
        if (cleanRef in listOf("serie", "series") && cleanMatched in listOf("serie", "series")) return true
        if (cleanRef in listOf("documentary", "documental", "documentales") && cleanMatched in listOf("documentary", "documental", "documentales")) return true
        if (cleanRef in listOf("short", "shortfilm", "cortometraje", "cortometrajes") && cleanMatched in listOf("short", "shortfilm", "cortometraje", "cortometrajes")) return true
        return false
    }

    private fun getCatalogItemType(item: CatalogItem): String = when (item) {
        is com.johang.audiocinemateca.data.model.Movie -> "pelicula"
        is com.johang.audiocinemateca.data.model.Serie -> "serie"
        is com.johang.audiocinemateca.data.model.Documentary -> "documental"
        is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometraje"
        else -> ""
    }

    private fun normalizeTitle(title: String): String {
        var clean = title.lowercase(Locale.ROOT)
        clean = java.text.Normalizer.normalize(clean, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        clean = clean.replace(Regex("(?i)\\b(latino|castellano|espana|espanol|doblaje|audio|subtitulado|lat|cast)\\b"), "")
        clean = clean.replace(Regex("[():,\\[\\]\\-_.~'\"!]"), " ")
        return clean.replace(Regex("\\s+"), " ").trim()
    }
}
