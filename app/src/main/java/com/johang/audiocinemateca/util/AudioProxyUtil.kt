package com.johang.audiocinemateca.util

import java.net.URLEncoder

object AudioProxyUtil {
    private const val PROXY_BASE_URL = "http://207.231.110.156/audiocinemateca-server/api/stream/proxy"

    /**
     * Convierte cualquier URL de audio del catálogo con cabeceras privadas en una URL pública
     * canalizada a través del servidor Node.js, lista para Google Cast / Smart TVs.
     */
    fun buildCastProxyUrl(originalAudioUrl: String): String {
        return try {
            val encoded = URLEncoder.encode(originalAudioUrl, "UTF-8")
            "$PROXY_BASE_URL?url=$encoded"
        } catch (e: Exception) {
            originalAudioUrl
        }
    }
}
