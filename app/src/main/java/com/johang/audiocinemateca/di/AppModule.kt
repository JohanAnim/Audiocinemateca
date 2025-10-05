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
import com.johang.audiocinemateca.data.local.dao.SearchHistoryDao

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE playback_progress ADD COLUMN isFinished INTEGER NOT NULL DEFAULT 0")
            }
        }

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "audiocinemateca.db"
        ).addMigrations(MIGRATION_5_6).build()
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
