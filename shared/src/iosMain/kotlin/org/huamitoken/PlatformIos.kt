package org.huamitoken

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataWithBytes
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
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
    ): HttpResult = suspendCoroutine { cont ->
        var fullUrl = proxyPrefix + url
        if (query.isNotEmpty()) {
            fullUrl += (if (fullUrl.contains("?")) "&" else "?") + MiCrypto.formUrlEncode(query)
        }
        val nsRequest = NSMutableURLRequest.requestWithURL(NSURL(string = fullUrl)).apply {
            setHTTPMethod(method.uppercase())
            for ((k, v) in headers) setValue(v, forHTTPHeaderField = k)
            if (cookies.isNotEmpty()) {
                setValue(cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }, forHTTPHeaderField = "Cookie")
            }
            if (body != null) {
                setHTTPBody(body.toNSData())
            }
        }
        val session = if (followRedirects) {
            NSURLSession.sharedSession
        } else {
            NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration,
                NoRedirectDelegate(),
                NSOperationQueue.mainQueue(),
            )
        }
        session.dataTaskWithRequest(nsRequest) { data, response, error ->
            if (error != null) {
                cont.resumeWith(Result.failure(Exception("Network error: ${error.localizedDescription}")))
                return@dataTaskWithRequest
            }
            val http = response as? NSHTTPURLResponse
            val status = http?.statusCode?.toInt() ?: 0
            val bytes = (data as? NSData)?.toByteArray() ?: ByteArray(0)
            val respHeaders = mutableMapOf<String, String>()
            val respCookies = mutableMapOf<String, String>()
            @Suppress("UNCHECKED_CAST")
            val fields = http?.allHeaderFields as? Map<Any?, Any?> ?: emptyMap()
            for ((k, v) in fields) {
                val key = k.toString()
                if (!key.equals("set-cookie", ignoreCase = true)) respHeaders[key] = v.toString()
            }
            val stored = NSHTTPCookie.cookiesWithResponseHeaderFields(fields, NSURL(string = fullUrl))
            @Suppress("UNCHECKED_CAST")
            (stored as? List<NSHTTPCookie>)?.forEach { c ->
                val name = c.name
                if (name != null) respCookies[name] = c.value ?: ""
            }
            cont.resumeWith(Result.success(HttpResult(status, bytes, respHeaders, respCookies)))
        }.resume()
    }

    private class NoRedirectDelegate : NSObject(), NSURLSessionTaskDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit,
        ) {
            completionHandler(null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return memScoped {
        NSData.dataWithBytes(
            bytes = allocArrayOf(this@toNSData),
            length = this@toNSData.size.toULong(),
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    if (length == 0UL) return ByteArray(0)
    return ByteArray(length.toInt()).also {
        it.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual object PlatformRandom {
    actual fun randomBytes(size: Int): ByteArray {
        val out = ByteArray(size)
        if (size > 0) {
            out.usePinned {
                SecRandomCopyBytes(kSecRandomDefault, size.toULong(), it.addressOf(0))
            }
        }
        return out
    }

    actual fun currentTimeMillis(): Long {
        return (NSDate().timeIntervalSince1970 * 1000).toLong()
    }

    actual fun uuid(): String = platform.Foundation.NSUUID().UUIDString

    actual fun randomUint64String(): String {
        val b = randomBytes(8)
        // Render as unsigned decimal via two 32-bit halves.
        var hi = 0UL
        var lo = 0UL
        for (i in 0 until 4) hi = (hi shl 8) or (b[i].toULong() and 0xFFUL)
        for (i in 4 until 8) lo = (lo shl 8) or (b[i].toULong() and 0xFFUL)
        return (hi * 4294967296UL + lo).toString()
    }
}
