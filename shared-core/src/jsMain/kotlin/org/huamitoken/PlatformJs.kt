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

        if (proxyPrefix.isNotEmpty()) {
            return requestViaProxy(method, targetUrl, headers, cookies, body, followRedirects)
        }

        val jsHeaders = js("{}")
        for ((k, v) in headers) jsHeaders[k] = v
        if (cookies.isNotEmpty()) {
            jsHeaders["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
        val init = js("{}")
        init.method = method.uppercase()
        init.redirect = if (followRedirects) "follow" else "manual"
        init.headers = jsHeaders
        if (body != null) init.body = body.toUint8Array()

        val fullUrl = targetUrl
        val response: dynamic = js("fetch(fullUrl, init)").unsafeCast<Promise<dynamic>>().await()
        val status = (response.status as Number).toInt()
        val buffer = (response.arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await())
        val bytes = Uint8Array(buffer).toByteArray()
        val respHeaders = mutableMapOf<String, String>()
        val location = response.headers.get("location") as? String
        if (location != null) respHeaders["Location"] = location
        return HttpResult(status, bytes, respHeaders, emptyMap())
    }

    /** Sends the request as a JSON envelope to the proxy; the proxy answers with HTTP 200 + JSON. */
    private suspend fun requestViaProxy(
        method: String,
        targetUrl: String,
        headers: Map<String, String>,
        cookies: Map<String, String>,
        body: ByteArray?,
        followRedirects: Boolean,
    ): HttpResult {
        val endpoint = proxyEndpoint(proxyPrefix)
        val envelope = js("{}")
        envelope.url = targetUrl
        envelope.method = method.uppercase()
        val envHeaders = js("{}")
        for ((k, v) in headers) envHeaders[k] = v
        envelope.headers = envHeaders
        val envCookies = js("{}")
        for ((k, v) in cookies) envCookies[k] = v
        envelope.cookies = envCookies
        if (body != null && body.isNotEmpty()) envelope.bodyBase64 = body.toBase64()
        envelope.followRedirects = followRedirects

        val init = js("{}")
        init.method = "POST"
        val reqHeaders = js("{}")
        reqHeaders["Content-Type"] = "application/json"
        init.headers = reqHeaders
        init.body = JSON.stringify(envelope)

        val response: dynamic = js("fetch(endpoint, init)").unsafeCast<Promise<dynamic>>().await()
        val httpStatus = (response.status as Number).toInt()
        val text = response.text().unsafeCast<Promise<String>>().await()
        val json: dynamic = try {
            JSON.parse<dynamic>(text)
        } catch (_: Throwable) {
            throw HuamiTokenError(
                "proxy",
                "Proxy returned HTTP $httpStatus with non-JSON body: ${text.take(200)}",
            )
        }
        if (json == null || json.error != undefined || httpStatus != 200) {
            val err = json?.error as? String ?: "HTTP $httpStatus"
            val stage = json?.stage as? String ?: "unknown"
            throw HuamiTokenError("proxy", "Proxy error ($stage): $err")
        }

        val status = (json.status as Number).toInt()
        val b64 = json.bodyBase64 as? String
        val bytes = if (b64.isNullOrEmpty()) ByteArray(0) else base64ToBytes(b64)
        val respHeaders = mutableMapOf<String, String>()
        val location = json.location as? String
        if (location != null) respHeaders["Location"] = location
        val respCookies = mutableMapOf<String, String>()
        val setCookies = json.setCookies
        if (setCookies != null && setCookies != undefined) {
            val arr = setCookies.unsafeCast<Array<String>>()
            for (sc in arr) parseSetCookie(sc)?.let { (k, v) -> respCookies[k] = v }
        }
        return HttpResult(status, bytes, respHeaders, respCookies)
    }
}

/** Strips a legacy `?url=` suffix so `/api/proxy?url=` still works as the endpoint. */
internal fun proxyEndpoint(prefix: String): String {
    val i = prefix.indexOf('?')
    return if (i >= 0) prefix.substring(0, i) else prefix
}

/** Parses a single `Set-Cookie` value (`name=value; Expires=Wed, 01 ...`) up to the first `;`. */
internal fun parseSetCookie(setCookie: String): Pair<String, String>? {
    val pair = setCookie.substringBefore(";").trim()
    val i = pair.indexOf("=")
    if (i <= 0) return null
    return pair.substring(0, i).trim() to pair.substring(i + 1).trim()
}

private fun ByteArray.toBase64(): String {
    val sb = StringBuilder(size)
    for (b in this) sb.append((b.toInt() and 0xFF).toChar())
    val bin = sb.toString()
    return js("btoa(bin)").unsafeCast<String>()
}

private fun base64ToBytes(b64: String): ByteArray {
    val bin = js("atob(b64)").unsafeCast<String>()
    return ByteArray(bin.length) { bin[it].code.toByte() }
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
