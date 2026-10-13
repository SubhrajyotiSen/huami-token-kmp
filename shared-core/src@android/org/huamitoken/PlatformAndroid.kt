package org.huamitoken

import java.net.HttpURLConnection
import java.net.URI
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class HttpEngine actual constructor() {
    var proxyPrefix: String = ""

    actual suspend fun request(
        method: String,
        url: String,
        query: List<Pair<String, String>>,
        headers: Map<String, String>,
        cookies: Map<String, String>,
        body: ByteArray?,
        followRedirects: Boolean,
    ): HttpResult = withContext(Dispatchers.IO) {
        var fullUrl = proxyPrefix + url
        if (query.isNotEmpty()) {
            fullUrl += (if (fullUrl.contains("?")) "&" else "?") + MiCrypto.formUrlEncode(query)
        }
        val conn = URI(fullUrl).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method.uppercase()
            conn.instanceFollowRedirects = followRedirects
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (cookies.isNotEmpty()) {
                conn.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            val rawStream = if (status >= 400) conn.errorStream else conn.inputStream
            val encoding = conn.contentEncoding
            val stream = if (rawStream != null && encoding != null && encoding.equals("gzip", ignoreCase = true)) {
                java.util.zip.GZIPInputStream(rawStream)
            } else {
                rawStream
            }
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)

            val respHeaders = mutableMapOf<String, String>()
            val respCookies = mutableMapOf<String, String>()
            for ((k, values) in conn.headerFields) {
                if (k == null || values.isEmpty()) continue
                if (k.equals("set-cookie", ignoreCase = true)) {
                    for (v in values) {
                        val pair = v.substringBefore(";")
                        val i = pair.indexOf("=")
                        if (i > 0) respCookies[pair.substring(0, i).trim()] = pair.substring(i + 1).trim()
                    }
                } else if (k.equals("location", ignoreCase = true)) {
                    respHeaders["Location"] = values.first()
                } else {
                    respHeaders[k] = values.joinToString(",")
                }
            }
            HttpResult(status, bytes, respHeaders, respCookies)
        } finally {
            conn.disconnect()
        }
    }
}

actual object PlatformRandom {
    private val secure = SecureRandom()

    actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secure.nextBytes(it) }

    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    actual fun uuid(): String = UUID.randomUUID().toString()

    actual fun randomUint64String(): String {
        var v = secure.nextLong()
        if (v == Long.MIN_VALUE) v = 0
        val u = if (v < 0) (-(v + 1)).toULong() + 1UL else v.toULong()
        return u.toString()
    }
}
