package com.johang.audiocinemateca.di

import android.content.Context
import com.google.gson.Gson
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.remote.AuthService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.johang.audiocinemateca.data.repository.LoginRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.local.dao.SearchHistoryDao

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthCatalogRepository(
        @ApplicationContext context: Context,
        catalogRepository: CatalogRepository,
        authService: AuthService
    ): AuthCatalogRepository {
        return AuthCatalogRepository(context, catalogRepository, authService)
    }

    @Provides
    @Singleton
    fun provideLoginRepository(authCatalogRepository: AuthCatalogRepository): LoginRepository {
        return authCatalogRepository
    }

    @Provides
    @Singleton
    fun provideSearchRepository(
        catalogRepository: CatalogRepository
    ): SearchRepository {
        return SearchRepository(catalogRepository)
    }

    @Provides
    @Singleton
    fun providePlaybackProgressRepository(
        playbackProgressDao: PlaybackProgressDao
    ): PlaybackProgressRepository {
        return PlaybackProgressRepository(playbackProgressDao)
    }

    @Provides
    @Singleton
    fun provideSearchHistoryRepository(
        searchHistoryDao: SearchHistoryDao
    ): com.johang.audiocinemateca.data.repository.SearchHistoryRepository {
        return com.johang.audiocinemateca.data.repository.SearchHistoryRepository(searchHistoryDao)
    }

    @Provides
    @Singleton
    fun provideContentRepository(
        catalogRepository: com.johang.audiocinemateca.data.local.CatalogRepository,
        gson: Gson
    ): com.johang.audiocinemateca.data.repository.ContentRepository {
        return com.johang.audiocinemateca.data.repository.ContentRepository(catalogRepository, gson)
    }
}