package com.johang.audiocinemateca.data.local

import android.content.Context
import com.google.gson.GsonBuilder
import android.content.ClipData
import android.content.ClipboardManager
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import com.johang.audiocinemateca.data.local.dao.CatalogDao
import com.johang.audiocinemateca.data.local.entities.CatalogDataEntity
import com.johang.audiocinemateca.data.local.entities.CatalogVersionEntity
import com.johang.audiocinemateca.data.model.CatalogResponse
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.MovieTypeAdapter
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.SerieTypeAdapter
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.DocumentaryTypeAdapter
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.model.ShortFilmTypeAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.util.Log
import android.widget.Toast





class CatalogRepository @Inject constructor(
    private val catalogDao: CatalogDao,
    @ApplicationContext private val context: Context
) {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Movie::class.java, MovieTypeAdapter())
        .registerTypeAdapter(Serie::class.java, SerieTypeAdapter())
        .registerTypeAdapter(Documentary::class.java, DocumentaryTypeAdapter())
        .registerTypeAdapter(ShortFilm::class.java, ShortFilmTypeAdapter())
        .create()
    private val CATALOG_FILE_NAME = "audiocinemateca_catalog.json"

    suspend fun saveCatalogVersion(versionDate: Date) = withContext(Dispatchers.IO) {
        catalogDao.insertCatalogVersion(CatalogVersionEntity("mainCatalog", versionDate.time.toString()))
    }

    suspend fun getCatalogVersion(): Date? = withContext(Dispatchers.IO) {
        val versionString = catalogDao.getCatalogVersion("mainCatalog")
        versionString?.toLongOrNull()?.let { Date(it) }
    }

    suspend fun saveCatalog(catalog: CatalogResponse) = withContext(Dispatchers.IO) {
        Log.d("CatalogRepository", "Saving catalog: ${catalog.javaClass.simpleName}")
        try {
            // Delete old catalog file if it exists
            val oldCatalogFile = File(context.filesDir, CATALOG_FILE_NAME)
            if (oldCatalogFile.exists()) {
                oldCatalogFile.delete()
                Log.d("CatalogRepository", "Deleted old catalog file: ${oldCatalogFile.absolutePath}")
            }

            // Write the new catalog to a file
            val catalogFile = File(context.filesDir, CATALOG_FILE_NAME)
            catalogFile.writeText(gson.toJson(catalog))
            Log.d("CatalogRepository", "Saved catalog to file: ${catalogFile.absolutePath}")

            // Save the file path in the database
            catalogDao.deleteCatalogData("mainCatalogFile") // Clear old entry
            catalogDao.insertCatalogData(CatalogDataEntity("mainCatalogFile", catalogFile.absolutePath))
            Log.d("CatalogRepository", "Saved catalog file path to DB: ${catalogFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("CatalogRepository", "Error saving catalog", e)
            throw e // Re-throw to propagate the error
        }
    }

    suspend fun getCatalog(): CatalogResponse? = withContext(Dispatchers.IO) {
        Log.d("CatalogRepository", "Attempting to get catalog")
        try {
            val catalogEntity = catalogDao.getCatalogData("mainCatalogFile").firstOrNull()
            if (catalogEntity != null) {
                val filePath = catalogEntity.data
                val catalogFile = File(filePath)
                Log.d("CatalogRepository", "Catalog file path from DB: ${filePath}")
                if (catalogFile.exists()) {
                    val jsonString = catalogFile.readText()
                    Log.d("CatalogRepository", "Read catalog JSON: ${jsonString.take(200)}...") // Log first 200 chars
                    val type = object : TypeToken<CatalogResponse>() {}.type
                    val catalog = gson.fromJson<CatalogResponse>(jsonString, type)
                    Log.d("CatalogRepository", "Deserialized catalog: ${catalog != null}")
                    return@withContext catalog
                } else {
                    Log.w("CatalogRepository", "Catalog file does not exist: ${filePath}")
                }
            } else {
                Log.w("CatalogRepository", "Catalog file path not found in DB.")
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e("CatalogRepository", "Error al cargar el catálogo desde el archivo", e) // Log the full stack trace
            val errorMessage = "Error al cargar el catálogo desde el archivo: ${e.message ?: "Mensaje de error nulo"}"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Error", errorMessage)
            clipboard.setPrimaryClip(clip)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al cargar catálogo: ${e.localizedMessage ?: "Error desconocido"}", Toast.LENGTH_LONG).show()
            }
            return@withContext null
        }
    }

    suspend fun deleteCatalogData(idPattern: String) = withContext(Dispatchers.IO) {
        // This method will now primarily delete the file entry from DB and the file itself
        // Assuming idPattern is "mainCatalogFile" for the single catalog file entry
        if (idPattern == "mainCatalogFile") {
            val catalogEntity = catalogDao.getCatalogData("mainCatalogFile").firstOrNull()
            if (catalogEntity != null) {
                val filePath = catalogEntity.data
                val catalogFile = File(filePath)
                if (catalogFile.exists()) {
                    catalogFile.delete()
                }
            }
            catalogDao.deleteCatalogData("mainCatalogFile") // Delete the DB entry
        }
    }

    suspend fun getGenres(categoryName: String? = null): List<String> = withContext(Dispatchers.IO) {
        val catalog = getCatalog()
        val genres = mutableSetOf<String>()

        when (categoryName) {
            "peliculas" -> catalog?.movies?.forEach { movie ->
                (movie as? Movie)?.genero?.split(".", "|", ",")?.forEach { individualGenre ->
                    val trimmedGenre = individualGenre.trim()
                    if (trimmedGenre.isNotBlank()) genres.add(trimmedGenre)
                }
            }
            "series" -> catalog?.series?.forEach { serie ->
                (serie as? Serie)?.genero?.split(".", "|", ",")?.forEach { individualGenre ->
                    val trimmedGenre = individualGenre.trim()
                    if (trimmedGenre.isNotBlank()) genres.add(trimmedGenre)
                }
            }
            "documentales" -> catalog?.documentaries?.forEach { documentary ->
                (documentary as? Documentary)?.genero?.split(".", "|", ",")?.forEach { individualGenre ->
                    val trimmedGenre = individualGenre.trim()
                    if (trimmedGenre.isNotBlank()) genres.add(trimmedGenre)
                }
            }
            "cortometrajes" -> catalog?.shortFilms?.forEach { shortFilm ->
                (shortFilm as? ShortFilm)?.genero?.split(".", "|", ",")?.forEach { individualGenre ->
                    val trimmedGenre = individualGenre.trim()
                    if (trimmedGenre.isNotBlank()) genres.add(trimmedGenre)
                }
            }
            null -> { // Process all categories if categoryName is null
                catalog?.movies?.forEach { movie ->
                    (movie as? Movie)?.genero?.split(".", "|", ",")?.forEach { individualGenre ->
                        val trimmedGenre = individualGenre.trim()
                        if (trimmedGenre.isNotBlank()) genres.add(trimmedGenre)
                    }
                }
                catalog?.series?.forEach { serie ->
                    (serie as? Serie)?.genero?.split(".", "|", ",")?.forEach { individualGenre ->
                        val trimmedGenre = individualGenre.trim()
                        if (trimmedGenre.isNotBlank()) genres.add(trimmedGenre)
                    }
                }
                catalog?.documentaries?.forEach { documentary ->
                    (documentary as? Documentary)?.genero?.split(".", "|", ",")?.forEach { individualGenre ->
                        val trimmedGenre = individualGenre.trim()
                        if (trimmedGenre.isNotBlank()) genres.add(trimmedGenre)
                    }
                }
                catalog?.shortFilms?.forEach { shortFilm ->
                    (shortFilm as? ShortFilm)?.genero?.split(".", "|", ",")?.forEach { individualGenre ->
                        val trimmedGenre = individualGenre.trim()
                        if (trimmedGenre.isNotBlank()) genres.add(trimmedGenre)
                    }
                }
            }
        }
        genres.toList().sorted()
    }

    suspend fun getCountries(categoryName: String? = null): List<String> = withContext(Dispatchers.IO) {
        val catalog = getCatalog()
        val countries = mutableSetOf<String>()

        when (categoryName) {
            "peliculas" -> catalog?.movies?.forEach { movie ->
                (movie as? Movie)?.pais?.split(",")?.forEach { individualCountry ->
                    val trimmedCountry = individualCountry.trim()
                    if (trimmedCountry.isNotBlank()) countries.add(trimmedCountry)
                }
            }
            "series" -> catalog?.series?.forEach { serie ->
                (serie as? Serie)?.pais?.split(",")?.forEach { individualCountry ->
                    val trimmedCountry = individualCountry.trim()
                    if (trimmedCountry.isNotBlank()) countries.add(trimmedCountry)
                }
            }
            "documentales" -> catalog?.documentaries?.forEach { documentary ->
                (documentary as? Documentary)?.pais?.split(",")?.forEach { individualCountry ->
                    val trimmedCountry = individualCountry.trim()
                    if (trimmedCountry.isNotBlank()) countries.add(trimmedCountry)
                }
            }
            "cortometrajes" -> catalog?.shortFilms?.forEach { shortFilm ->
                (shortFilm as? ShortFilm)?.pais?.split(",")?.forEach { individualCountry ->
                    val trimmedCountry = individualCountry.trim()
                    if (trimmedCountry.isNotBlank()) countries.add(trimmedCountry)
                }
            }
            null -> { // Process all categories if categoryName is null
                catalog?.movies?.forEach { movie ->
                    (movie as? Movie)?.pais?.split(",")?.forEach { individualCountry ->
                        val trimmedCountry = individualCountry.trim()
                        if (trimmedCountry.isNotBlank()) countries.add(trimmedCountry)
                    }
                }
                catalog?.series?.forEach { serie ->
                    (serie as? Serie)?.pais?.split(",")?.forEach { individualCountry ->
                        val trimmedCountry = individualCountry.trim()
                        if (trimmedCountry.isNotBlank()) countries.add(trimmedCountry)
                    }
                }
                catalog?.documentaries?.forEach { documentary ->
                    (documentary as? Documentary)?.pais?.split(",")?.forEach { individualCountry ->
                        val trimmedCountry = individualCountry.trim()
                        if (trimmedCountry.isNotBlank()) countries.add(trimmedCountry)
                    }
                }
                catalog?.shortFilms?.forEach { shortFilm ->
                    (shortFilm as? ShortFilm)?.pais?.split(",")?.forEach { individualCountry ->
                        val trimmedCountry = individualCountry.trim()
                        if (trimmedCountry.isNotBlank()) countries.add(trimmedCountry)
                    }
                }
            }
        }
        countries.toList().sorted()
    }

    suspend fun getFilteredAndPaginatedItems(
        categoryName: String,
        filterType: String,
        filterValue: String?,
        offset: Int,
        limit: Int
    ): List<com.johang.audiocinemateca.domain.model.CatalogItem> = withContext(Dispatchers.IO) {
        val catalog = getCatalog()
        val allItems: List<com.johang.audiocinemateca.domain.model.CatalogItem> = when (categoryName) {
            "peliculas" -> catalog?.movies?.filterIsInstance<Movie>() ?: emptyList()
            "series" -> catalog?.series?.filterIsInstance<Serie>() ?: emptyList()
            "documentales" -> catalog?.documentaries?.filterIsInstance<Documentary>() ?: emptyList()
            "cortometrajes" -> catalog?.shortFilms?.filterIsInstance<ShortFilm>() ?: emptyList()
            else -> emptyList()
        }

        var filteredItems: List<com.johang.audiocinemateca.domain.model.CatalogItem> = allItems

        // Aplicar filtros
        when (filterType) {
            "Alfabéticamente" -> {
                filteredItems = when (filterValue) {
                    "A-Z" -> filteredItems.sortedBy { it.title.lowercase() }
                    "Z-A" -> filteredItems.sortedByDescending { it.title.lowercase() }
                    else -> filteredItems
                }
            }
            "Fecha" -> {
                filteredItems = when (filterValue) {
                    "Más nuevo" -> filteredItems.reversed()
                    "Más antiguo" -> filteredItems
                    else -> filteredItems
                }
            }
            "Género" -> {
                if (filterValue != null && filterValue != "Todos") {
                    filteredItems = filteredItems.filter { item ->
                        val genre = (item as? Movie)?.genero ?: (item as? Serie)?.genero ?: (item as? Documentary)?.genero ?: (item as? ShortFilm)?.genero ?: ""
                        // Split the genre string by common delimiters and check if any part contains the filter value
                        val genresInItem = genre.split(".", "|", ",").map { it.trim() }
                        genresInItem.any { it.equals(filterValue, ignoreCase = true) }
                    }
                }
            }
            "Idiomas" -> {
                if (filterValue != null && filterValue != "Todos") {
                    filteredItems = filteredItems.filter { item ->
                        val itemLanguageCode = (item as? Movie)?.idioma ?: (item as? Serie)?.idioma ?: (item as? Documentary)?.idioma ?: (item as? ShortFilm)?.idioma ?: ""
                        when (filterValue) {
                            "Español de España" -> itemLanguageCode == "1"
                            "Español Latino" -> itemLanguageCode != "1"
                            else -> false
                        }
                    }
                }
            }
            "Países" -> {
                Log.d("CatalogRepository", "Filtering by country: $filterValue")
                if (filterValue != null && filterValue != "Todos") {
                    filteredItems = filteredItems.filter { item ->
                        val country = (item as? Movie)?.pais ?: (item as? Serie)?.pais ?: (item as? Documentary)?.pais ?: (item as? ShortFilm)?.pais ?: ""
                        val countriesInItem = country.split(",").map { it.trim() }
                        val result = countriesInItem.any { it.equals(filterValue, ignoreCase = true) }
                        Log.d("CatalogRepository", "Item: ${item.title}, Country: $country, CountriesInItem: $countriesInItem, Result: $result")
                        result
                    }
                }
            }
        }

        // Aplicar paginación
        val paginatedItems = filteredItems.drop(offset).take(limit)

        paginatedItems
    }
}
