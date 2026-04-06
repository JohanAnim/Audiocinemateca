package com.johang.audiocinemateca.di

import android.content.Context
import com.google.gson.Gson
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.AppDatabase
import com.johang.audiocinemateca.data.local.dao.CatalogDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.johang.audiocinemateca.data.local.dao.DownloadDao
import com.johang.audiocinemateca.data.local.dao.SearchHistoryDao

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        // DESACTIVAR CACHÉ LOCAL: Forzamos honestidad total con el servidor.
        // Si no se puede escribir en la nube (por cuota), la app NO lo mostrará localmente.
        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false)
            .build()
        firestore.firestoreSettings = settings
        return firestore
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE playback_progress ADD COLUMN isFinished INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`contentId` TEXT NOT NULL, `partIndex` INTEGER NOT NULL, `episodeIndex` INTEGER NOT NULL, `contentType` TEXT NOT NULL, `title` TEXT NOT NULL, `downloadStatus` TEXT NOT NULL, `filePath` TEXT, `downloadedAt` INTEGER NOT NULL, `totalSizeMb` REAL NOT NULL, PRIMARY KEY(`contentId`, `partIndex`, `episodeIndex`))")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `downloads` ADD COLUMN `errorMessage` TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `downloads` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `favorites` (`contentId` TEXT NOT NULL, `title` TEXT NOT NULL, `contentType` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`contentId`))")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Crear tabla nueva con la PK simplificada (solo contentId)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playback_progress_new` (
                        `contentId` TEXT NOT NULL, 
                        `contentType` TEXT NOT NULL, 
                        `currentPositionMs` INTEGER NOT NULL, 
                        `totalDurationMs` INTEGER NOT NULL, 
                        `partIndex` INTEGER NOT NULL, 
                        `episodeIndex` INTEGER NOT NULL, 
                        `lastPlayedTimestamp` INTEGER NOT NULL, 
                        `isFinished` INTEGER NOT NULL DEFAULT 0, 
                        PRIMARY KEY(`contentId`)
                    )
                """.trimIndent())

                // 2. Copiar los datos. Usamos un truco de SQL para quedarnos solo con el registro más reciente de cada serie/peli
                database.execSQL("""
                    INSERT OR REPLACE INTO `playback_progress_new` 
                    SELECT * FROM `playback_progress` 
                    GROUP BY `contentId` 
                    HAVING MAX(`lastPlayedTimestamp`)
                """.trimIndent())

                // 3. Eliminar tabla vieja y renombrar la nueva
                database.execSQL("DROP TABLE `playback_progress`")
                database.execSQL("ALTER TABLE `playback_progress_new` RENAME TO `playback_progress`")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Restauramos la PK compuesta
                database.execSQL("CREATE TABLE IF NOT EXISTS `playback_progress_temp` (`contentId` TEXT NOT NULL, `contentType` TEXT NOT NULL, `currentPositionMs` INTEGER NOT NULL, `totalDurationMs` INTEGER NOT NULL, `partIndex` INTEGER NOT NULL, `episodeIndex` INTEGER NOT NULL, `lastPlayedTimestamp` INTEGER NOT NULL, `isFinished` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`contentId`, `partIndex`, `episodeIndex`))")
                database.execSQL("INSERT OR REPLACE INTO `playback_progress_temp` SELECT * FROM `playback_progress`")
                database.execSQL("DROP TABLE `playback_progress`")
                database.execSQL("ALTER TABLE `playback_progress_temp` RENAME TO `playback_progress`")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `notifications` ADD COLUMN `remoteId` TEXT")
                database.execSQL("ALTER TABLE `notifications` ADD COLUMN `linkUrl` TEXT")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `notifications` ADD COLUMN `destination` TEXT")
            }
        }

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "audiocinemateca.db"
        ).addMigrations(
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, 
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, 
            MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideCatalogDao(appDatabase: AppDatabase): CatalogDao {
        return appDatabase.catalogDao()
    }

    @Provides
    @Singleton
    fun providePlaybackProgressDao(appDatabase: AppDatabase): PlaybackProgressDao {
        return appDatabase.playbackProgressDao()
    }

    @Provides
    @Singleton
    fun provideSearchHistoryDao(appDatabase: AppDatabase): SearchHistoryDao {
        return appDatabase.searchHistoryDao()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(appDatabase: AppDatabase): DownloadDao {
        return appDatabase.downloadDao()
    }

    @Provides
    @Singleton
    fun provideFavoritesDao(appDatabase: AppDatabase): com.johang.audiocinemateca.data.local.dao.FavoritesDao {
        return appDatabase.favoritesDao()
    }

    @Provides
    @Singleton
    fun provideNotificationDao(appDatabase: AppDatabase): com.johang.audiocinemateca.data.local.dao.NotificationDao {
        return appDatabase.notificationDao()
    }

    @Provides
    @Singleton
    fun provideSharedPreferencesManager(@ApplicationContext context: Context): SharedPreferencesManager {
        return SharedPreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideCatalogRepository(catalogDao: CatalogDao, @ApplicationContext context: Context): CatalogRepository {
        return CatalogRepository(catalogDao, context)
    }

    @Provides
    @Singleton
    fun provideLoginUseCase(loginRepository: com.johang.audiocinemateca.data.repository.LoginRepository): com.johang.audiocinemateca.domain.usecase.LoginUseCase {
        return com.johang.audiocinemateca.domain.usecase.LoginUseCase(loginRepository)
    }

    @Provides
    @Singleton
    fun provideLoadCatalogUseCase(authCatalogRepository: com.johang.audiocinemateca.data.AuthCatalogRepository): com.johang.audiocinemateca.domain.usecase.LoadCatalogUseCase {
        return com.johang.audiocinemateca.domain.usecase.LoadCatalogUseCase(authCatalogRepository)
    }
}
