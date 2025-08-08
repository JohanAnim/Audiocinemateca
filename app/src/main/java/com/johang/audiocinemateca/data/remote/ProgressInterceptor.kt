package com.johang.audiocinemateca.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressFlow: MutableStateFlow<Int>
) : ResponseBody() {

    private var bufferedSource: BufferedSource? = null

    override fun contentType(): MediaType? {
        return responseBody.contentType()
    }

    override fun contentLength(): Long {
        return responseBody.contentLength()
    }

    override fun source(): BufferedSource {
        if (bufferedSource == null) {
            bufferedSource = source(responseBody.source()).buffer()
        }
        return bufferedSource!!
    }

    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            var totalBytesRead = 0L
            var lastProgress = 0

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                val contentLength = responseBody.contentLength()
                
                if (contentLength != -1L) { // Solo calcular progreso si contentLength es conocido
                    val progress = (100 * totalBytesRead / contentLength).toInt()
                    if (progress != lastProgress) {
                        progressFlow.value = progress
                        lastProgress = progress
                        android.util.Log.d("ProgressDebug", "Progress: $progress%, Total Read: $totalBytesRead, Content Length: $contentLength")
                    }
                } else {
                    android.util.Log.d("ProgressDebug", "ContentLength is unknown (-1L). Total Read: $totalBytesRead")
                }
                return bytesRead
            }
        }
    }
}

class ProgressInterceptor(
    private val progressFlow: MutableStateFlow<Int>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())
        val responseBuilder = originalResponse.newBuilder()
        val responseBody = originalResponse.body

        if (responseBody != null) {
            // Eliminar el encabezado Content-Encoding para evitar la descompresión automática de OkHttp
            // Esto asegura que ProgressResponseBody vea los bytes crudos (comprimidos si el servidor los envía así)
            responseBuilder.removeHeader("Content-Encoding")
            responseBuilder.body(ProgressResponseBody(responseBody, progressFlow))
        }

        return responseBuilder.build()
    }
}
