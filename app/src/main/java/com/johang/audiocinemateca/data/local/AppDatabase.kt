package com.johang.audiocinemateca.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.johang.audiocinemateca.data.local.dao.CatalogDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.local.entities.CatalogDataEntity
import com.johang.audiocinemateca.data.local.entities.CatalogVersionEntity
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity

@Database(entities = [CatalogVersionEntity::class, CatalogDataEntity::class, PlaybackProgressEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
}