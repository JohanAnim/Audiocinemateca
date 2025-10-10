package com.johang.audiocinemateca.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.johang.audiocinemateca.data.remote.AuthService
import com.johang.audiocinemateca.data.remote.GithubApiService
import com.johang.audiocinemateca.data.remote.ProgressInterceptor
import com.johang.audiocinemateca.data.remote.interceptor.GzipInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.MovieTypeAdapter
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.model.ShortFilmTypeAdapter
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.DocumentaryTypeAdapter
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.SerieTypeAdapter
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://audiocinemateca.com/"
    private const val GITHUB_API_BASE_URL = "https://api.github.com/"

    @Provides
    @Singleton
    fun provideOfflineModeInterceptor(sharedPreferencesManager: SharedPreferencesManager): OfflineModeInterceptor {
        return OfflineModeInterceptor(sharedPreferencesManager)
    }

    @Provides
    @Singleton
    @Named("AudiocinematecaClient")
    fun provideOkHttpClient(
        offlineModeInterceptor: OfflineModeInterceptor,
        authInterceptor: com.johang.audiocinemateca.data.remote.interceptor.AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(GzipInterceptor())
            .addInterceptor(offlineModeInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("GithubClient")
    fun provideGithubOkHttpClient(offlineModeInterceptor: OfflineModeInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(offlineModeInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(sharedPreferencesManager: SharedPreferencesManager): com.johang.audiocinemateca.data.remote.interceptor.AuthInterceptor {
        return com.johang.audiocinemateca.data.remote.interceptor.AuthInterceptor(sharedPreferencesManager)
    }

    @Provides
    @Singleton
    @DownloadClient
    fun provideDownloadOkHttpClient(
        progressFlow: MutableStateFlow<Int>,
        authInterceptor: com.johang.audiocinemateca.data.remote.interceptor.AuthInterceptor,
        offlineModeInterceptor: OfflineModeInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(offlineModeInterceptor)
            .addInterceptor(ProgressInterceptor(progressFlow))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideProgressListener(): MutableStateFlow<Int> = MutableStateFlow(0)


    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .registerTypeAdapter(Movie::class.java, MovieTypeAdapter())
            .registerTypeAdapter(ShortFilm::class.java, ShortFilmTypeAdapter())
            .registerTypeAdapter(Documentary::class.java, DocumentaryTypeAdapter())
            .registerTypeAdapter(Serie::class.java, SerieTypeAdapter())
            .create()
    }

    @Provides
    @Singleton
    @Named("AudiocinematecaRetrofit")
    fun provideRetrofit(@Named("AudiocinematecaClient") okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @Named("GithubRetrofit")
    fun provideGithubRetrofit(@Named("GithubClient") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthService(@Named("AudiocinematecaRetrofit") retrofit: Retrofit): AuthService {
        return retrofit.create(AuthService::class.java)
    }

    @Provides
    @Singleton
    fun provideGithubApiService(@Named("GithubRetrofit") retrofit: Retrofit): GithubApiService {
        return retrofit.create(GithubApiService::class.java)
    }
}