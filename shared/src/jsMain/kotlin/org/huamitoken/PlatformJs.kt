package org.huamitoken

import kotlin.js.Date
import kotlin.js.Promise
import kotlin.random.Random
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

actual class HttpEngine actual constructor() {
    /** Prepended to every URL, e.g. a CORS-proxy prefix when the browser blocks calls. */
    var proxyPrefix: String = ""

    actual suspend fun request(
        method: String,
        url: String,
        query: List<Pair<String, String>>,
        headers: Map<String, String>,
        cookies: Map<String, String>,
        body: ByteArray?,
        followRedirects: Boolean,
    ): HttpResult {
        var targetUrl = url
        if (query.isNotEmpty()) {
            targetUrl += (if (targetUrl.contains("?")) "&" else "?") + MiCrypto.formUrlEncode(query)
        }

        var fullUrl = targetUrl
        val jsHeaders = js("{}")
        for ((k, v) in headers) jsHeaders[k] = v
        if (cookies.isNotEmpty()) {
            val cookieStr = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            jsHeaders["Cookie"] = cookieStr
            jsHeaders["X-Cookie"] = cookieStr
        }

        if (proxyPrefix.isNotEmpty()) {
            jsHeaders["X-Target-URL"] = targetUrl
            if (proxyPrefix.endsWith("?url=") || proxyPrefix.endsWith("&url=")) {
                val encodedTarget = js("encodeURIComponent(targetUrl)").unsafeCast<String>()
                fullUrl = proxyPrefix + encodedTarget
            } else {
                fullUrl = proxyPrefix + targetUrl
            }
        }
        val init = js("{}")
        init.method = method.uppercase()
        init.redirect = if (followRedirects) "follow" else "manual"
        init.headers = jsHeaders
        if (body != null) init.body = body.toUint8Array()

        val response: dynamic = js("fetch(fullUrl, init)").unsafeCast<Promise<dynamic>>().await()
        val status = (response.status as Number).toInt()
        val buffer = (response.arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await())
        val bytes = Uint8Array(buffer).toByteArray()
        val respHeaders = mutableMapOf<String, String>()
        val location: String? = (response.headers.get("location") as? String)
            ?: (response.headers.get("x-location") as? String)
        if (location != null) respHeaders["Location"] = location

        val respCookies = mutableMapOf<String, String>()
        val setCookieHeader = (response.headers.get("x-received-cookies") as? String)
            ?: (response.headers.get("x-set-cookie") as? String)
            ?: (response.headers.get("set-cookie") as? String)
        if (setCookieHeader != null) {
            for (part in setCookieHeader.split(",")) {
                val pair = part.substringBefore(";").trim()
                val i = pair.indexOf("=")
                if (i > 0) {
                    respCookies[pair.substring(0, i).trim()] = pair.substring(i + 1).trim()
                }
            }
        }
        return HttpResult(status, bytes, respHeaders, respCookies)
    }
}

private fun ByteArray.toUint8Array(): Uint8Array {
    val a = Uint8Array(size)
    val d = a.asDynamic()
    for (i in indices) d[i] = this[i]
    return a
}

private fun Uint8Array.toByteArray(): ByteArray {
    val d = asDynamic()
    return ByteArray(length) { d[it].unsafeCast<Byte>() }
}

actual object PlatformRandom {
    actual fun randomBytes(size: Int): ByteArray {
        val rand = Random(Date.now().toLong())
        return ByteArray(size) { rand.nextInt(256).toByte() }
    }

    actual fun currentTimeMillis(): Long = Date.now().toLong()

    actual fun uuid(): String {
        val r = Random(Date.now().toLong())
        val hex = "0123456789abcdef"
        val sb = StringBuilder()
        for (i in 0 until 36) {
            sb.append(
                when (i) {
                    8, 13, 18, 23 -> '-'
                    14 -> '4'
                    19 -> hex[(r.nextInt(4) + 8)]
                    else -> hex[r.nextInt(16)]
                },
            )
        }
        return sb.toString()
    }

    actual fun randomUint64String(): String {
        val r = Random(Date.now().toLong())
        var hi = 0UL
        var lo = 0UL
        repeat(4) { hi = (hi shl 8) or r.nextInt(256).toULong() }
        repeat(4) { lo = (lo shl 8) or r.nextInt(256).toULong() }
        return (hi * 4294967296UL + lo).toString()
    }
}
