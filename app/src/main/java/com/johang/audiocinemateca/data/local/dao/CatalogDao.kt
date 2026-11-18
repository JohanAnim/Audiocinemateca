package com.johang.audiocinemateca.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.johang.audiocinemateca.data.local.entities.CatalogDataEntity
import com.johang.audiocinemateca.data.local.entities.CatalogItemEntity
import com.johang.audiocinemateca.data.local.entities.CatalogVersionEntity

data class IdTitleTuple(
    val id: String,
    val title: String
)

@Dao
interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogVersion(version: CatalogVersionEntity)

    @Query("SELECT versionDate FROM catalog_version WHERE id = :id")
    suspend fun getCatalogVersion(id: String): String?

    @Query("DELETE FROM catalog_version WHERE id = :id")
    suspend fun deleteCatalogVersion(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogData(data: CatalogDataEntity)

    @Query("SELECT * FROM catalog_data WHERE id LIKE :pattern")
    suspend fun getCatalogData(pattern: String): List<CatalogDataEntity>

    @Query("DELETE FROM catalog_data WHERE id LIKE :pattern")
    suspend fun deleteCatalogData(pattern: String)

    // --- CATÁLOGO EN SQLITE + FTS4 ---

    @Query("SELECT COUNT(*) FROM catalog_items")
    suspend fun getCatalogCount(): Int

    @Query("SELECT COUNT(*) FROM catalog_items WHERE category = :category")
    suspend fun getCategoryCount(category: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogItems(items: List<CatalogItemEntity>)

    @Query("DELETE FROM catalog_items")
    suspend fun clearAllCatalogItems()

    @Transaction
    suspend fun replaceCatalogItems(items: List<CatalogItemEntity>) {
        clearAllCatalogItems()
        items.chunked(500).forEach { batch ->
            insertCatalogItems(batch)
        }
    }

    @Query("SELECT * FROM catalog_items WHERE category = :category ORDER BY sortOrder ASC")
    suspend fun getItemsByCategory(category: String): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE category = :category ORDER BY titleNorm ASC LIMIT :limit OFFSET :offset")
    suspend fun getItemsAlphaAsc(category: String, limit: Int, offset: Int): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE category = :category ORDER BY titleNorm DESC LIMIT :limit OFFSET :offset")
    suspend fun getItemsAlphaDesc(category: String, limit: Int, offset: Int): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE category = :category ORDER BY sortOrder ASC LIMIT :limit OFFSET :offset")
    suspend fun getItemsDateAsc(category: String, limit: Int, offset: Int): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE category = :category ORDER BY sortOrder DESC LIMIT :limit OFFSET :offset")
    suspend fun getItemsDateDesc(category: String, limit: Int, offset: Int): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE category = :category AND idioma = '1' ORDER BY sortOrder ASC LIMIT :limit OFFSET :offset")
    suspend fun getItemsSpanishSpain(category: String, limit: Int, offset: Int): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE category = :category AND idioma != '1' ORDER BY sortOrder ASC LIMIT :limit OFFSET :offset")
    suspend fun getItemsSpanishLatin(category: String, limit: Int, offset: Int): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE category = :category AND genero LIKE '%' || :genre || '%' ORDER BY sortOrder ASC")
    suspend fun getItemsMatchingGenre(category: String, genre: String): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE category = :category AND pais LIKE '%' || :country || '%' ORDER BY sortOrder ASC")
    suspend fun getItemsMatchingCountry(category: String, country: String): List<CatalogItemEntity>

    @Query("SELECT DISTINCT genero FROM catalog_items WHERE category = :category AND genero != ''")
    suspend fun getDistinctGenresByCategory(category: String): List<String>

    @Query("SELECT DISTINCT genero FROM catalog_items WHERE genero != ''")
    suspend fun getAllDistinctGenres(): List<String>

    @Query("SELECT DISTINCT pais FROM catalog_items WHERE category = :category AND pais != ''")
    suspend fun getDistinctCountriesByCategory(category: String): List<String>

    @Query("SELECT DISTINCT pais FROM catalog_items WHERE pais != ''")
    suspend fun getAllDistinctCountries(): List<String>

    @Query("SELECT DISTINCT narracion FROM catalog_items WHERE category = :category AND TRIM(narracion) != ''")
    suspend fun getDistinctNarratorsByCategory(category: String): List<String>

    @Query("SELECT DISTINCT narracion FROM catalog_items WHERE category = :category AND TRIM(narracion) != '' AND (TRIM(idioma) = '1' OR LOWER(idioma) LIKE '%españa%' OR LOWER(idioma) LIKE '%castellano%')")
    suspend fun getDistinctNarratorsByCategorySpain(category: String): List<String>

    @Query("SELECT DISTINCT narracion FROM catalog_items WHERE category = :category AND TRIM(narracion) != '' AND TRIM(idioma) != '1'")
    suspend fun getDistinctNarratorsByCategoryLatin(category: String): List<String>

    @Query("SELECT DISTINCT narracion FROM catalog_items WHERE TRIM(narracion) != ''")
    suspend fun getAllDistinctNarrators(): List<String>

    @Query("SELECT DISTINCT productora FROM catalog_items WHERE category = :category AND TRIM(productora) != ''")
    suspend fun getDistinctProvidersByCategory(category: String): List<String>

    @Query("SELECT DISTINCT productora FROM catalog_items WHERE category = :category AND TRIM(productora) != '' AND (TRIM(idioma) = '1' OR LOWER(idioma) LIKE '%españa%' OR LOWER(idioma) LIKE '%castellano%')")
    suspend fun getDistinctProvidersByCategorySpain(category: String): List<String>

    @Query("SELECT DISTINCT productora FROM catalog_items WHERE category = :category AND TRIM(productora) != '' AND TRIM(idioma) != '1'")
    suspend fun getDistinctProvidersByCategoryLatin(category: String): List<String>

    @Query("SELECT DISTINCT productora FROM catalog_items WHERE TRIM(productora) != ''")
    suspend fun getAllDistinctProviders(): List<String>

    @Query("SELECT * FROM catalog_items WHERE category = :category AND id = :id COLLATE NOCASE LIMIT 1")
    suspend fun findByIdAndCategory(id: String, category: String): CatalogItemEntity?

    @Query("SELECT * FROM catalog_items WHERE id = :id COLLATE NOCASE")
    suspend fun findAllById(id: String): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE title = :title COLLATE NOCASE LIMIT 1")
    suspend fun findByTitleInsensitive(title: String): CatalogItemEntity?

    @Query("SELECT * FROM catalog_items WHERE category = :category ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomByCategory(category: String): CatalogItemEntity?

    @Query("SELECT * FROM catalog_items ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomAny(): CatalogItemEntity?

    @Query(
        """
        SELECT catalog_items.* FROM catalog_items
        JOIN catalog_items_fts ON catalog_items.rowid = catalog_items_fts.rowid
        WHERE catalog_items_fts MATCH :ftsQuery
        LIMIT :limit
        """
    )
    suspend fun searchFts(ftsQuery: String, limit: Int = 150): List<CatalogItemEntity>

    @Query("SELECT id, title FROM catalog_items WHERE category = 'series' AND id IN (:ids)")
    suspend fun findSeriesTitlesByIds(ids: List<String>): List<IdTitleTuple>

    @Query(
        """
        SELECT * FROM catalog_items
        WHERE titleNorm LIKE '%' || :queryNorm || '%'
           OR searchTextNorm LIKE '%' || :queryNorm || '%'
        ORDER BY CASE WHEN titleNorm LIKE '%' || :queryNorm || '%' THEN 0 ELSE 1 END, sortOrder ASC
        LIMIT :limit
        """
    )
    suspend fun searchSubstringFallback(queryNorm: String, limit: Int = 80): List<CatalogItemEntity>

    @RawQuery
    suspend fun getFilteredItemsRaw(query: SupportSQLiteQuery): List<CatalogItemEntity>
}
