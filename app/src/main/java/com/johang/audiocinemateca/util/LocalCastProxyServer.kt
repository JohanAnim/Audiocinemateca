package com.johang.audiocinemateca.util

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Servidor proxy HTTP local embebido en el dispositivo.
 * Transmite audio directamente a Chromecast y bocinas inteligentes Google Home / Nest Audio
 * en la red Wi-Fi local, resolviendo problemas de CORS, autenticación básica y archivos locales.
 */
object LocalCastProxyServer {
    private const val TAG = "LocalCastProxyServer"
    private const val BASE_CATALOG_URL = "https://audiocinemateca.com/"

    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private var client: OkHttpClient? = null
    private var appContext: Context? = null
    private var localPort: Int = 0
    private var localIpAddress: String? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    @Synchronized
    fun start(context: Context, okHttpClient: OkHttpClient) {
        if (isRunning) return

        appContext = context.applicationContext
        client = okHttpClient
        localIpAddress = getLocalIpAddress()

        try {
            serverSocket = ServerSocket(0) // Asigna un puerto libre automáticamente
            localPort = serverSocket!!.localPort
            executor = Executors.newCachedThreadPool()
            isRunning = true
            Log.d(TAG, "LocalCastProxyServer iniciado en http://$localIpAddress:$localPort")

            executor?.execute {
                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        executor?.execute { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error en accept(): ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar LocalCastProxyServer: ${e.message}", e)
            isRunning = false
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        try {
            executor?.shutdownNow()
        } catch (e: Exception) {}
        executor = null
        Log.d(TAG, "LocalCastProxyServer detenido.")
    }

    /**
     * Construye una URL accesible para Google Cast.
     * Si el servidor local está activo, devuelve la URL local en la red Wi-Fi;
     * de lo contrario, utiliza la URL del catálogo.
     */
    fun buildCastUrl(originalUri: String, remoteUrl: String? = null): String {
        val target = when {
            originalUri.startsWith("content://") || originalUri.startsWith("file://") || originalUri.startsWith("/") -> originalUri
            !remoteUrl.isNullOrBlank() -> remoteUrl
            originalUri.startsWith("http://", ignoreCase = true) || originalUri.startsWith("https://", ignoreCase = true) -> originalUri
            else -> "${BASE_CATALOG_URL.removeSuffix("/")}/${originalUri.removePrefix("/")}"
        }

        val ip = getLocalIpAddress() ?: localIpAddress
        if (isRunning && !ip.isNullOrBlank() && localPort > 0) {
            val encodedTarget = URLEncoder.encode(target, "UTF-8")
            return "http://$ip:$localPort/cast_stream?target=$encodedTarget"
        }

        // Fallback si no hay IP local disponible
        return if (target.startsWith("http", ignoreCase = true)) target else "${BASE_CATALOG_URL.removeSuffix("/")}/${target.removePrefix("/")}"
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.keepAlive = true
            socket.tcpNoDelay = true
            socket.soTimeout = 0 // Sin timeout artificial para streaming progresivo de audio
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val reader = input.bufferedReader(Charsets.UTF_8)
            val requestLine = reader.readLine() ?: return socket.close()

            val parts = requestLine.split(" ")
            if (parts.size < 2) return socket.close()

            val method = parts[0].uppercase()
            val uriPath = parts[1]

            // Leer cabeceras de la petición
            var rangeHeader: String? = null
            var line = reader.readLine()
            while (!line.isNullOrBlank()) {
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(":").trim()
                }
                line = reader.readLine()
            }

            if (method == "OPTIONS") {
                sendCorsOptionsResponse(output)
                socket.close()
                return
            }

            val targetParam = extractQueryParam(uriPath, "target")
            if (targetParam.isNullOrBlank()) {
                sendErrorResponse(output, 400, "Target missing")
                socket.close()
                return
            }

            val decodedTarget = URLDecoder.decode(targetParam, "UTF-8")
            Log.d(TAG, "Handling Cast request for: $decodedTarget (Range: $rangeHeader)")

            if (decodedTarget.startsWith("content://") || decodedTarget.startsWith("file://") || decodedTarget.startsWith("/")) {
                streamLocalFile(decodedTarget, rangeHeader, method, output)
            } else {
                streamRemoteUrl(decodedTarget, rangeHeader, method, output)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client socket: ${e.message}")
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun streamLocalFile(fileUriOrPath: String, rangeHeader: String?, method: String, output: OutputStream) {
        val ctx = appContext ?: return
        var inputStream: InputStream? = null
        var totalLength = -1L

        try {
            if (fileUriOrPath.startsWith("content://")) {
                val uri = Uri.parse(fileUriOrPath)
                try {
                    ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        totalLength = pfd.statSize
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo obtener statSize de openFileDescriptor: ${e.message}")
                }
                inputStream = ctx.contentResolver.openInputStream(uri)
                if (totalLength <= 0 && inputStream != null) {
                    try {
                        totalLength = inputStream.available().toLong()
                    } catch (e: Exception) {}
                }
            } else {
                val path = if (fileUriOrPath.startsWith("file://")) fileUriOrPath.removePrefix("file://") else fileUriOrPath
                val file = File(path)
                if (file.exists()) {
                    totalLength = file.length()
                    inputStream = FileInputStream(file)
                }
            }

            if (inputStream == null || totalLength <= 0) {
                Log.e(TAG, "streamLocalFile error: inputStream es null o totalLength ($totalLength) <= 0 para $fileUriOrPath")
                sendErrorResponse(output, 404, "File Not Found")
                return
            }

            var start = 0L
            var end = totalLength - 1
            var isPartial = false

            if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
                val rangeValue = rangeHeader.removePrefix("bytes=").trim()
                val rangeParts = rangeValue.split("-")
                val startParsed = rangeParts.getOrNull(0)?.toLongOrNull()
                val endParsed = rangeParts.getOrNull(1)?.toLongOrNull()

                if (startParsed != null) {
                    start = startParsed
                    if (endParsed != null && endParsed >= start) {
                        end = endParsed.coerceAtMost(totalLength - 1)
                    }
                    isPartial = true
                }
            }

            val contentLength = (end - start + 1).coerceAtLeast(0L)
            val statusCode = if (isPartial) 206 else 200
            val statusText = if (isPartial) "Partial Content" else "OK"

            val headerBuilder = StringBuilder()
            headerBuilder.append("HTTP/1.1 $statusCode $statusText\r\n")
            headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
            headerBuilder.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            headerBuilder.append("Access-Control-Allow-Headers: Range, Content-Type, Authorization\r\n")
            headerBuilder.append("Accept-Ranges: bytes\r\n")
            headerBuilder.append("Content-Type: audio/mpeg\r\n")
            headerBuilder.append("Content-Length: $contentLength\r\n")
            if (isPartial) {
                headerBuilder.append("Content-Range: bytes $start-$end/$totalLength\r\n")
            }
            headerBuilder.append("Connection: close\r\n\r\n")

            output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
            output.flush()

            if (method == "HEAD") return

            if (start > 0) {
                var skipped = 0L
                while (skipped < start) {
                    val s = inputStream.skip(start - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }

            val buffer = ByteArray(32 * 1024)
            var bytesRemaining = contentLength
            while (bytesRemaining > 0) {
                val toRead = bytesRemaining.coerceAtMost(buffer.size.toLong()).toInt()
                val bytesRead = inputStream.read(buffer, 0, toRead)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
                bytesRemaining -= bytesRead
            }
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming local file to Cast: ${e.message}")
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    private fun streamRemoteUrl(remoteUrl: String, rangeHeader: String?, method: String, output: OutputStream) {
        val okClient = client ?: return
        val fullUrl = if (remoteUrl.startsWith("http", ignoreCase = true)) remoteUrl else "${BASE_CATALOG_URL.removeSuffix("/")}/${remoteUrl.removePrefix("/")}"

        val streamingClient = okClient.newBuilder()
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .addHeader("User-Agent", "Audiocinemateca-Cast/1.0")

        if (!rangeHeader.isNullOrBlank()) {
            requestBuilder.addHeader("Range", rangeHeader)
        }

        try {
            val response = streamingClient.newCall(requestBuilder.build()).execute()
            val code = response.code
            val body = response.body

            val headerBuilder = StringBuilder()
            headerBuilder.append("HTTP/1.1 $code ${response.message.ifBlank { "OK" }}\r\n")
            headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
            headerBuilder.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            headerBuilder.append("Access-Control-Allow-Headers: Range, Content-Type, Authorization\r\n")
            headerBuilder.append("Accept-Ranges: bytes\r\n")
            headerBuilder.append("Content-Type: audio/mpeg\r\n")

            val contentLength = body?.contentLength() ?: -1L
            if (contentLength > 0) {
                headerBuilder.append("Content-Length: $contentLength\r\n")
            }
            val contentRange = response.header("Content-Range")
            if (!contentRange.isNullOrBlank()) {
                headerBuilder.append("Content-Range: $contentRange\r\n")
            }
            headerBuilder.append("Connection: close\r\n\r\n")

            output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
            output.flush()

            if (method == "HEAD") {
                response.close()
                return
            }

            body?.byteStream()?.use { stream ->
                val buffer = ByteArray(32 * 1024)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming remote audio to Cast: ${e.message}")
        }
    }

    private fun sendCorsOptionsResponse(output: OutputStream) {
        val resp = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Range, Content-Type, Authorization\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Connection: close\r\n\r\n"
        output.write(resp.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun sendErrorResponse(output: OutputStream, code: Int, message: String) {
        val resp = "HTTP/1.1 $code $message\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Type: text/plain\r\n" +
                "Connection: close\r\n\r\n" +
                message
        output.write(resp.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun extractQueryParam(uri: String, paramName: String): String? {
        val queryStart = uri.indexOf('?')
        if (queryStart == -1) return null
        val query = uri.substring(queryStart + 1)
        for (pair in query.split("&")) {
            val keyValue = pair.split("=")
            if (keyValue.size == 2 && keyValue[0] == paramName) {
                return keyValue[1]
            }
        }
        return null
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving local IP: ${e.message}")
        }
        return null
    }
}
