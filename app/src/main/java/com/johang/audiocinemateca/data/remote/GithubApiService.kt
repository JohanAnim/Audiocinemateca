package com.johang.audiocinemateca.data.remote

import com.johang.audiocinemateca.data.model.GithubRelease
import retrofit2.Response
import retrofit2.http.GET

interface GithubApiService {
    @GET("repos/JohanAnim/Audiocinemateca/releases/latest")
    suspend fun getLatestRelease(): Response<GithubRelease>
}
