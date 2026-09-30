package org.huamitoken.proxy

import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Top-level handler for Vercel Serverless Function and Node.js HTTP servers.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun handler(req: dynamic, res: dynamic): Promise<Unit> {
    return Promise { resolve, reject ->
        proxyScope.launch {
            try {
                handleProxyRequest(req, res)
                resolve(Unit)
            } catch (e: Throwable) {
                try {
                    sendError(res, 500, "Internal Server Error: ${e.message}")
                    resolve(Unit)
                } catch (resErr: Throwable) {
                    reject(e)
                }
            }
        }
    }
}

/**
 * Main request routing and proxying logic.
 */
suspend fun handleProxyRequest(req: dynamic, res: dynamic) {
    applyCorsHeaders(res)

    val method = ((req.method as? String) ?: "GET").uppercase()
    if (method == "OPTIONS") {
        sendResponse(res, 204, "")
        return
    }

    val targetUrl = extractTargetUrl(req)
    if (targetUrl.isNullOrBlank()) {
        sendError(
            res,
            400,
            "Missing target URL. Pass 'url' query parameter (e.g. /api/proxy?url=https%3A%2F%2F...) or 'x-target-url' header."
        )
        return
    }

    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
        sendError(res, 400, "Invalid URL scheme. Only http:// and https:// URLs are supported.")
        return
    }

    val bodyBytes = readRequestBody(req, method)
    relayRequest(req, res, method, targetUrl, bodyBytes)
}

/**
 * Extract target URL from query parameter, request URL, or custom headers.
 */
fun extractTargetUrl(req: dynamic): String? {
    // 1. Check req.query.url (Vercel / Express parsed query)
    val queryUrl = req.query?.url as? String
    if (!queryUrl.isNullOrBlank()) {
        return queryUrl.trim()
    }

    // 2. Check headers
    val headerTarget = getHeader(req.headers, "x-target-url")
        ?: getHeader(req.headers, "x-url")
    if (!headerTarget.isNullOrBlank()) {
        return headerTarget.trim()
    }

    // 3. Fallback: Parse raw req.url
    val rawUrl = req.url as? String ?: return null
    val qIndex = rawUrl.indexOf('?')
    if (qIndex != -1 && qIndex < rawUrl.length - 1) {
        val queryStr = rawUrl.substring(qIndex + 1)
        for (pair in queryStr.split("&")) {
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = decodeURIComponent(pair.substring(0, eq))
                if (key == "url") {
                    val value = decodeURIComponent(pair.substring(eq + 1))
                    if (value.isNotBlank()) return value.trim()
                }
            }
        }
    }

    return null
}

private fun applyCorsHeaders(res: dynamic) {
    res.setHeader("Access-Control-Allow-Origin", "*")
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
    res.setHeader(
        "Access-Control-Allow-Headers",
        "Content-Type, Authorization, Cookie, X-Requested-With, X-Target-URL, X-Cookie, X-Set-Cookie, x-target-url, x-cookie, x-set-cookie"
    )
    res.setHeader(
        "Access-Control-Expose-Headers",
        "Set-Cookie, Location, X-Set-Cookie, x-received-cookies, x-set-cookie, Content-Type, Content-Length, Date"
    )
    res.setHeader("Access-Control-Allow-Credentials", "true")
}

private suspend fun readRequestBody(req: dynamic, method: String): Uint8Array? {
    if (method == "GET" || method == "HEAD") return null

    val body = req.body
    if (body != null && body != undefined) {
        return when {
            js("typeof Buffer !== 'undefined' && Buffer.isBuffer(body)") as Boolean -> {
                Uint8Array(body.buffer.unsafeCast<ArrayBuffer>(), body.byteOffset.unsafeCast<Int>(), body.length.unsafeCast<Int>())
            }
            js("body instanceof Uint8Array") as Boolean -> {
                body.unsafeCast<Uint8Array>()
            }
            js("body instanceof ArrayBuffer") as Boolean -> {
                Uint8Array(body.unsafeCast<ArrayBuffer>())
            }
            js("typeof body === 'string'") as Boolean -> {
                encodeStringToUtf8(body.unsafeCast<String>())
            }
            js("typeof body === 'object'") as Boolean -> {
                val jsonStr = js("JSON.stringify(body)").unsafeCast<String>()
                encodeStringToUtf8(jsonStr)
            }
            else -> null
        }
    }

    // If body not parsed, read from Node.js stream if available
    return if (js("typeof req.on === 'function'") as Boolean) {
        readStreamAsUint8Array(req)
    } else {
        null
    }
}

private suspend fun readStreamAsUint8Array(stream: dynamic): Uint8Array? {
    return Promise<Uint8Array?> { resolve, reject ->
        val chunks = js("[]")
        stream.on("data", { chunk: dynamic ->
            chunks.push(chunk)
        })
        stream.on("end", {
            if (chunks.length == 0) {
                resolve(null)
            } else {
                val buffer = js("Buffer.concat(chunks)")
                val uint8 = Uint8Array(
                    buffer.buffer.unsafeCast<ArrayBuffer>(),
                    buffer.byteOffset.unsafeCast<Int>(),
                    buffer.length.unsafeCast<Int>()
                )
                resolve(uint8)
            }
        })
        stream.on("error", { err: dynamic ->
            reject(Throwable(err.toString()))
        })
    }.await()
}

private suspend fun relayRequest(
    req: dynamic,
    res: dynamic,
    method: String,
    targetUrl: String,
    body: Uint8Array?
) {
    val fetchHeaders = js("{}")

    // Forward relevant incoming headers
    val incomingHeaders = req.headers
    if (incomingHeaders != null && incomingHeaders != undefined) {
        val keys = js("Object.keys(incomingHeaders)").unsafeCast<Array<String>>()
        for (k in keys) {
            val lower = k.lowercase()
            if (lower == "host" || lower == "connection" || lower == "content-length" ||
                lower == "origin" || lower == "referer" || lower == "x-target-url" || lower == "x-url") {
                continue
            }
            val v = incomingHeaders[k]
            if (v != null && v != undefined) {
                fetchHeaders[k] = v
            }
        }
    }

    // Handle cookies from x-cookie header or standard cookie header
    val customCookie = getHeader(incomingHeaders, "x-cookie")
    if (!customCookie.isNullOrBlank()) {
        fetchHeaders["Cookie"] = customCookie
    }

    if (fetchHeaders["User-Agent"] == null && fetchHeaders["user-agent"] == null) {
        fetchHeaders["User-Agent"] = "huami-token-kmp/0.8.0"
    }

    val fetchOptions = js("{}")
    fetchOptions.method = method
    fetchOptions.headers = fetchHeaders
    fetchOptions.redirect = "manual"
    if (body != null) {
        fetchOptions.body = body
    }

    try {
        val response = js("fetch(targetUrl, fetchOptions)").unsafeCast<Promise<dynamic>>().await()
        val status = (response.status as Number).toInt()

        // Extract cookies from upstream response
        val cookies = extractResponseCookies(response)
        if (cookies.isNotEmpty()) {
            val joined = cookies.joinToString("; ")
            res.setHeader("x-received-cookies", joined)
            res.setHeader("x-set-cookie", joined)
            try {
                res.setHeader("Set-Cookie", cookies.toTypedArray())
            } catch (_: Throwable) {
                res.setHeader("Set-Cookie", joined)
            }
        }

        // Forward response headers
        val location = response.headers.get("location") as? String
        if (location != null) {
            res.setHeader("Location", location)
            res.setHeader("x-location", location)
        }

        val contentType = response.headers.get("content-type") as? String
        if (contentType != null) {
            res.setHeader("Content-Type", contentType)
        }

        val arrayBuffer = response.arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await()
        val respBytes = Uint8Array(arrayBuffer)

        sendResponseBytes(res, status, respBytes)
    } catch (e: Throwable) {
        sendError(res, 502, "Failed to connect to target URL ($targetUrl): ${e.message}")
    }
}

private fun extractResponseCookies(response: dynamic): List<String> {
    val result = mutableListOf<String>()
    try {
        // Node 18.14.0+ getSetCookie()
        if (js("typeof response.headers.getSetCookie === 'function'") as Boolean) {
            val list = response.headers.getSetCookie().unsafeCast<Array<String>>()
            for (c in list) {
                if (c.isNotBlank()) result.add(c)
            }
            if (result.isNotEmpty()) return result
        }

        // Node-fetch raw()
        if (js("typeof response.headers.raw === 'function'") as Boolean) {
            val raw = response.headers.raw()
            val setCookie = raw?.get("set-cookie") as? Array<String>
            if (setCookie != null) {
                for (c in setCookie) {
                    if (c.isNotBlank()) result.add(c)
                }
                if (result.isNotEmpty()) return result
            }
        }

        // Standard get("set-cookie")
        val single = response.headers.get("set-cookie") as? String
        if (!single.isNullOrBlank()) {
            result.add(single)
        }
    } catch (_: Throwable) {}
    return result
}

fun sendResponse(res: dynamic, status: Int, body: String, contentType: String = "application/json") {
    try {
        res.setHeader("Content-Type", contentType)
    } catch (_: Throwable) {}

    if (js("typeof res.status === 'function'") as Boolean) {
        res.status(status)
        if (js("typeof res.send === 'function'") as Boolean) {
            res.send(body)
            return
        }
    }

    res.statusCode = status
    res.end(body)
}

fun sendResponseBytes(res: dynamic, status: Int, bytes: Uint8Array) {
    if (js("typeof res.status === 'function'") as Boolean) {
        res.status(status)
    } else {
        res.statusCode = status
    }

    // Convert Uint8Array to Buffer for Node.js if available
    val data = if (js("typeof Buffer !== 'undefined' && typeof Buffer.from === 'function'") as Boolean) {
        js("Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength)")
    } else {
        bytes
    }

    if (js("typeof res.send === 'function'") as Boolean) {
        res.send(data)
    } else {
        res.end(data)
    }
}

fun sendError(res: dynamic, status: Int, message: String) {
    val escapedMsg = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    sendResponse(res, status, """{"error":"$escapedMsg"}""", "application/json")
}

private fun encodeStringToUtf8(str: String): Uint8Array {
    return if (js("typeof TextEncoder !== 'undefined'") as Boolean) {
        js("new TextEncoder().encode(str)").unsafeCast<Uint8Array>()
    } else if (js("typeof Buffer !== 'undefined'") as Boolean) {
        val buf = js("Buffer.from(str, 'utf8')")
        Uint8Array(buf.buffer.unsafeCast<ArrayBuffer>(), buf.byteOffset.unsafeCast<Int>(), buf.byteLength.unsafeCast<Int>())
    } else {
        val bytes = str.encodeToByteArray()
        val arr = Uint8Array(bytes.size)
        val d = arr.asDynamic()
        for (i in bytes.indices) d[i] = bytes[i]
        arr
    }
}

private fun decodeURIComponent(s: String): String {
    return try {
        js("decodeURIComponent(s.replace(/\\+/g, ' '))").unsafeCast<String>()
    } catch (_: Throwable) {
        s
    }
}

fun getHeader(headers: dynamic, name: String): String? {
    if (headers == null || headers == undefined) return null

    // 1. Direct and case variations indexing
    val direct = headers[name] ?: headers[name.lowercase()] ?: headers[name.uppercase()]
    if (direct != null && direct != undefined) {
        return direct.toString()
    }

    // 2. If object has .get() method (Fetch API Headers or Map)
    if (js("typeof headers.get === 'function'") as Boolean) {
        val fromFn = headers.get(name) ?: headers.get(name.lowercase()) ?: headers.get(name.uppercase())
        if (fromFn != null && fromFn != undefined) {
            return fromFn.toString()
        }
    }

    // 3. Case-insensitive search over keys
    try {
        val keys = js("Object.keys(headers)").unsafeCast<Array<String>>()
        val lowerTarget = name.lowercase()
        for (k in keys) {
            if (k.lowercase() == lowerTarget) {
                val valObj = headers[k]
                if (valObj != null && valObj != undefined) {
                    return valObj.toString()
                }
            }
        }
    } catch (_: Throwable) {}

    return null
}
