package com.johang.audiocinemateca.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSource
import okio.Okio

class GzipInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val responseBody = response.body

        if (response.header("Content-Encoding") == "gzip" && responseBody != null) {
            val gzipSource = GzipSource(responseBody.source())
            val buffer = Buffer()
            buffer.writeAll(gzipSource)

            val decompressedBody = buffer.readByteArray().toResponseBody(responseBody.contentType())

            return response.newBuilder()
                .removeHeader("Content-Encoding")
                .header("Content-Length", decompressedBody.contentLength().toString())
                .body(decompressedBody)
                .build()
        }
        return response
    }
}
