package org.huamitoken

/** Raw HTTP result. Body is always captured as bytes; text is UTF-8 decoding. */
data class HttpResult(
    val statusCode: Int,
    val bodyBytes: ByteArray,
    val headers: Map<String, String>,
    val cookies: Map<String, String>,
) {
    val bodyText: String get() = bodyBytes.decodeToString()
}

/**
 * Platform HTTP transport. Actuals: HttpURLConnection (JVM/Android),
 * NSURLSession (iOS), fetch (JS). A proxy prefix (e.g. a CORS proxy for web)
 * is prepended to the URL when set.
 */
expect class HttpEngine() {
    suspend fun request(
        method: String,
        url: String,
        // Ordered pairs: Zepp sends `r` (and one multi-device flag) twice, like the Python build.
        query: List<Pair<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        followRedirects: Boolean = true,
    ): HttpResult
}

/** Platform randomness, clock, and ids. */
expect object PlatformRandom {
    fun randomBytes(size: Int): ByteArray
    fun currentTimeMillis(): Long
    fun uuid(): String
    /** Unsigned 64-bit value rendered as decimal, like Python secrets.randbits(64). */
    fun randomUint64String(): String
}
