package com.johang.audiocinemateca.data.remote

import com.johang.audiocinemateca.data.model.GithubRelease
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GithubApiService {
    @GET("repos/JohanAnim/Audiocinemateca/releases/latest")
    suspend fun getLatestRelease(): Response<GithubRelease>

    @GET("repos/JohanAnim/Audiocinemateca/releases")
    suspend fun getReleases(@Query("per_page") perPage: Int = 20): Response<List<GithubRelease>>
}
