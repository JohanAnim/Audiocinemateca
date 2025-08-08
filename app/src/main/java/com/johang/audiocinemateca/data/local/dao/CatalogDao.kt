package com.johang.audiocinemateca.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.johang.audiocinemateca.data.local.entities.CatalogDataEntity
import com.johang.audiocinemateca.data.local.entities.CatalogVersionEntity
import kotlinx.coroutines.flow.Flow

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
}