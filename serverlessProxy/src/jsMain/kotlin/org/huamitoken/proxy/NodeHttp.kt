package org.huamitoken.proxy

import kotlin.js.Promise

/**
 * Minimal external definitions for Node.js / Vercel Serverless Function request/response.
 */
external interface VercelRequest {
    val method: String?
    val url: String?
    val query: dynamic
    val headers: dynamic
    val body: dynamic
}

external interface VercelResponse {
    var statusCode: Int
    fun setHeader(name: String, value: dynamic): VercelResponse
    fun getHeader(name: String): dynamic
    fun status(code: Int): VercelResponse
    fun send(body: dynamic): VercelResponse
    fun json(body: dynamic): VercelResponse
    fun writeHead(statusCode: Int, headers: dynamic = definedExternally): VercelResponse
    fun write(chunk: dynamic): Boolean
    fun end(data: dynamic = definedExternally): VercelResponse
}

@JsModule("http")
@JsNonModule
external object NodeHttp {
    fun createServer(requestListener: (req: dynamic, res: dynamic) -> Unit): dynamic
}

@JsModule("url")
@JsNonModule
external object NodeUrl {
    fun parse(urlStr: String, parseQueryString: Boolean = definedExternally): dynamic
}
