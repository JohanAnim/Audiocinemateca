package com.johang.audiocinemateca.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface AuthService {
    @GET("system/files/catalogo/version.json.gz")
    suspend fun getVersion(
        @Header("Authorization") authHeader: String
    ): Response<ResponseBody>

    @GET("system/files/catalogo/catalogo.json.gz")
    suspend fun getCatalog(
        @Header("Authorization") authHeader: String,
        @Header("Accept-Encoding") acceptEncoding: String = "identity"
    ): Response<ResponseBody>
}