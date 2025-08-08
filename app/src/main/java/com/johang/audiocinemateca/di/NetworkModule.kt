package com.johang.audiocinemateca.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.johang.audiocinemateca.data.remote.AuthService
import com.johang.audiocinemateca.data.remote.interceptor.GzipInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://audiocinemateca.com/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(GzipInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

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
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthService(retrofit: Retrofit): AuthService {
        return retrofit.create(AuthService::class.java)
    }
}