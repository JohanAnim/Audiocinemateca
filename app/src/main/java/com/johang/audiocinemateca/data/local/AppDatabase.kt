package com.johang.audiocinemateca.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.johang.audiocinemateca.data.local.dao.CatalogDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.local.dao.SearchHistoryDao
import com.johang.audiocinemateca.data.local.entities.CatalogDataEntity
import com.johang.audiocinemateca.data.local.entities.CatalogVersionEntity
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.local.entities.SearchHistoryEntity
import com.johang.audiocinemateca.data.local.migrations.Migration4To5

@Database(entities = [CatalogVersionEntity::class, CatalogDataEntity::class, PlaybackProgressEntity::class, SearchHistoryEntity::class], version = 5, exportSchema = true, autoMigrations = [AutoMigration(from = 4, to = 5, spec = Migration4To5::class)])
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}