package org.huamitoken.proxy

import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

@OptIn(DelicateCoroutinesApi::class)
class ProxyHandlerTest {

    @Test
    fun testExtractTargetUrlFromQuery() {
        val req = js("{}")
        req.query = js("{}")
        req.query.url = "https://api-mifit-us2.huami.com/test"

        val target = extractTargetUrl(req)
        assertEquals("https://api-mifit-us2.huami.com/test", target)
    }

    @Test
    fun testExtractTargetUrlFromHeaders() {
        val req = js("{}")
        req.headers = js("{}")
        req.headers["x-target-url"] = "https://account.xiaomi.com/pass/serviceLogin"

        val target = extractTargetUrl(req)
        assertEquals("https://account.xiaomi.com/pass/serviceLogin", target)
    }

    @Test
    fun testExtractTargetUrlFromRawUrl() {
        val req = js("{}")
        req.url = "/api/proxy?url=https%3A%2F%2Fapi-mifit-us2.huami.com%2Fv1%2Fdata&foo=bar"

        val target = extractTargetUrl(req)
        assertEquals("https://api-mifit-us2.huami.com/v1/data", target)
    }

    @Test
    fun testExtractTargetUrlMissing() {
        val req = js("{}")
        req.url = "/api/proxy"
        assertNull(extractTargetUrl(req))
    }

    @Test
    fun testHandleOptionsCorsPreflight(): Promise<Unit> = GlobalScope.promise {
        val req = js("{}")
        req.method = "OPTIONS"

        val headersSet = mutableMapOf<String, Any?>()
        var statusCode = 0
        var ended = false

        val res = js("{}")
        res.setHeader = { name: String, value: dynamic ->
            headersSet[name] = value
            res
        }
        res.status = { code: Int ->
            statusCode = code
            res
        }
        res.send = { _: dynamic ->
            ended = true
            res
        }
        res.end = {
            ended = true
            res
        }

        handleProxyRequest(req, res)

        assertEquals("*", headersSet["Access-Control-Allow-Origin"])
        assertTrue(headersSet.containsKey("Access-Control-Allow-Methods"))
        assertTrue(headersSet.containsKey("Access-Control-Allow-Headers"))
        assertEquals(204, statusCode)
    }

    @Test
    fun testHandleMissingUrlReturns400(): Promise<Unit> = GlobalScope.promise {
        val req = js("{}")
        req.method = "GET"
        req.url = "/api/proxy"

        var statusCode = 0
        var responseBody = ""

        val res = js("{}")
        res.setHeader = { _: String, _: dynamic -> res }
        res.status = { code: Int ->
            statusCode = code
            res
        }
        res.send = { body: dynamic ->
            responseBody = body.toString()
            res
        }
        res.end = { body: dynamic ->
            if (body != undefined && body != null) responseBody = body.toString()
            res
        }

        handleProxyRequest(req, res)

        assertEquals(400, statusCode)
        assertTrue(responseBody.contains("Missing target URL"))
    }

    @Test
    fun testHandleInvalidUrlSchemeReturns400(): Promise<Unit> = GlobalScope.promise {
        val req = js("{}")
        req.method = "GET"
        req.url = "/api/proxy?url=ftp%3A%2F%2Fexample.com"

        var statusCode = 0
        var responseBody = ""

        val res = js("{}")
        res.setHeader = { _: String, _: dynamic -> res }
        res.status = { code: Int ->
            statusCode = code
            res
        }
        res.send = { body: dynamic ->
            responseBody = body.toString()
            res
        }
        res.end = { body: dynamic ->
            if (body != undefined && body != null) responseBody = body.toString()
            res
        }

        handleProxyRequest(req, res)

        assertEquals(400, statusCode)
        assertTrue(responseBody.contains("Invalid URL scheme"))
    }
}
