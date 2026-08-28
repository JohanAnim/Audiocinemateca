package com.johang.audiocinemateca.util

import java.net.URLDecoder
import java.net.URLEncoder

object AudioProxyUtil {
    private const val PROXY_BASE_URL = "http://207.231.110.156/audiocinemateca-server/api/stream/proxy"

    private const val BASE_CATALOG_URL = "https://audiocinemateca.com/"

    /**
     * Convierte cualquier URL de audio del catálogo con cabeceras privadas en una URL pública
     * canalizada a través del servidor Node.js, lista para Google Cast / Smart TVs.
     */
    fun buildCastProxyUrl(originalAudioUrl: String): String {
        return try {
            var cleanUrl = originalAudioUrl.trim()
            if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                cleanUrl = "${BASE_CATALOG_URL.removeSuffix("/")}/${cleanUrl.removePrefix("/")}"
            }
            while (cleanUrl.contains("%20") || cleanUrl.contains("%25")) {
                val decoded = URLDecoder.decode(cleanUrl, "UTF-8")
                if (decoded == cleanUrl) break
                cleanUrl = decoded
            }
            val encoded = URLEncoder.encode(cleanUrl, "UTF-8")
            "$PROXY_BASE_URL?url=$encoded"
        } catch (e: Exception) {
            val fallbackUrl = if (originalAudioUrl.startsWith("http", ignoreCase = true)) originalAudioUrl else "$BASE_CATALOG_URL$originalAudioUrl"
            val encoded = URLEncoder.encode(fallbackUrl, "UTF-8")
            "$PROXY_BASE_URL?url=$encoded"
        }
    }
}
