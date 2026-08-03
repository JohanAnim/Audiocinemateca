package com.johang.audiocinemateca.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.johang.audiocinemateca.data.model.FeaturedBanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeaturedBannerRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private val bannerDocument = firestore.collection("app_config").document("featured_banner")
    private var cachedBanner: FeaturedBanner? = null

    suspend fun getFeaturedBannerOnce(forceRefresh: Boolean = false): FeaturedBanner? {
        if (!forceRefresh && cachedBanner != null) {
            return cachedBanner
        }
        return try {
            val snapshot = bannerDocument.get().await()
            if (snapshot.exists()) {
                val banner = snapshot.toObject(FeaturedBanner::class.java)
                cachedBanner = banner
                Log.d("FeaturedBannerRepo", "Banner destacado real obtenido de Firestore: ${banner?.title}")
                banner
            } else {
                Log.d("FeaturedBannerRepo", "No existe el documento app_config/featured_banner en Firestore.")
                null
            }
        } catch (e: Exception) {
            Log.e("FeaturedBannerRepo", "Error obteniendo banner destacado: ${e.message}", e)
            null
        }
    }

    fun getFeaturedBanner(): Flow<FeaturedBanner?> = kotlinx.coroutines.flow.flow {
        emit(getFeaturedBannerOnce())
    }

    suspend fun publishFeaturedBanner(
        title: String,
        description: String,
        linkUrl: String,
        rating: Double = 5.0,
        genres: List<String> = emptyList()
    ): Result<Unit> {
        return try {
            val (itemId, itemType) = parseLinkUrl(linkUrl)
            
            val bannerMap = hashMapOf(
                "id" to "banner_${System.currentTimeMillis()}",
                "title" to title,
                "description" to description,
                "linkUrl" to linkUrl,
                "itemId" to itemId,
                "itemType" to itemType,
                "rating" to rating,
                "maxRating" to 5,
                "genres" to genres
            )

            bannerDocument.set(bannerMap).await()
            cachedBanner = FeaturedBanner(
                id = bannerMap["id"] as String,
                title = title,
                description = description,
                linkUrl = linkUrl,
                itemId = itemId,
                itemType = itemType,
                rating = rating,
                maxRating = 5,
                genres = genres
            )
            Log.d("FeaturedBannerRepo", "Banner publicado exitosamente en app_config/featured_banner")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FeaturedBannerRepo", "Error publicando banner destacado: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun parseLinkUrl(linkUrl: String): Pair<String, String> {
        var itemId = ""
        var itemType = "pelicula"

        try {
            val cleanUrl = linkUrl.trim().trimEnd('/')
            if (cleanUrl.contains("?")) {
                val uri = android.net.Uri.parse(cleanUrl)
                itemId = uri.getQueryParameter("id") ?: ""
                val pathSeg = uri.pathSegments.lastOrNull()?.lowercase() ?: ""
                itemType = when (pathSeg) {
                    "pelicula", "movie" -> "pelicula"
                    "serie", "series" -> "serie"
                    "documental", "documentary" -> "documental"
                    "cortometraje", "short" -> "cortometraje"
                    else -> "pelicula"
                }
            } else {
                val parts = cleanUrl.split("/")
                if (parts.size >= 2) {
                    itemId = parts.last()
                    val typeSegment = parts[parts.size - 2].lowercase()
                    itemType = when (typeSegment) {
                        "pelicula", "movie" -> "pelicula"
                        "serie", "series" -> "serie"
                        "documental", "documentary" -> "documental"
                        "cortometraje", "short" -> "cortometraje"
                        else -> "pelicula"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(itemId, itemType)
    }
}
