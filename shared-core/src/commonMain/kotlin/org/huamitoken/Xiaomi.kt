package org.huamitoken

/** Xiaomi Mi Account authentication session. Ports XiaomiSession (3-step login flow). */
class XiaomiSession(
    val username: String,
    private val password: String,
    private val engine: HttpEngine,
) {
    val deviceId: String = MiCrypto.generateDeviceId(username)

    private var ssecurityValue: String? = null
    private var serviceTokenValue: String? = null
    private var userIdValue: String? = null
    private var cUserIdValue: String? = null
    private var nonceValue: Long? = null
    private var locationValue: String? = null

    val ssecurity: String
        get() = ssecurityValue ?: throw AuthenticationError(message = "Not logged in — no ssecurity available")
    val serviceToken: String
        get() = serviceTokenValue ?: throw AuthenticationError(message = "Not logged in — no service_token available")
    val userId: String
        get() = userIdValue ?: throw AuthenticationError(message = "Not logged in — no user_id available")
    val cUserId: String
        get() = cUserIdValue ?: throw AuthenticationError(message = "Not logged in — no c_user_id available")

    suspend fun login() {
        val (sign, qs, callback) = getLoginPageParams()
        authenticate(sign, qs, callback)
        fetchServiceToken()
    }

    private suspend fun getLoginPageParams(): Triple<String, String, String> {
        val response = engine.request(
            method = "GET",
            url = XiaomiUrls.SERVICE_LOGIN,
            query = listOf("_json" to "true", "sid" to "miothealth", "_locale" to "en_US"),
            headers = XiaomiConstants.serviceLoginHeaders(),
            cookies = mapOf("userId" to username, "deviceId" to deviceId),
        )
        if (response.statusCode != 200) {
            throw AuthenticationError(code = "service-login-failed", message = "serviceLogin request failed with status ${response.statusCode}")
        }
        val data = stripPrefix(response.bodyText)
        val sign = JsonLite.stringField(data, "_sign")
        val qs = JsonLite.stringField(data, "qs")
        val callback = JsonLite.stringField(data, "callback")
        if (sign == null || qs == null || callback == null) {
            throw AuthenticationError(code = "missing-login-params", message = "Missing _sign/qs/callback in serviceLogin response")
        }
        return Triple(sign, qs, callback)
    }

    private suspend fun authenticate(sign: String, qs: String, callback: String) {
        val form = listOf(
            "qs" to qs,
            "callback" to callback,
            "_json" to "true",
            "_sign" to sign,
            "user" to username,
            "hash" to MiCrypto.passwordHash(password),
            "sid" to "miothealth",
            "_locale" to "en_US",
        )
        val response = engine.request(
            method = "POST",
            url = XiaomiUrls.SERVICE_LOGIN_AUTH2,
            headers = XiaomiConstants.serviceLoginAuth2Headers(),
            cookies = mapOf("deviceId" to deviceId),
            body = MiCrypto.formUrlEncode(form).encodeToByteArray(),
        )
        if (response.statusCode != 200) {
            throw AuthenticationError(code = "auth-request-failed", message = "serviceLoginAuth2 failed with status ${response.statusCode}")
        }
        val data = stripPrefix(response.bodyText)
        if ((JsonLite.longField(data, "code") ?: -1L) != 0L) {
            val desc = JsonLite.stringField(data, "description") ?: JsonLite.stringField(data, "code")
            throw AuthenticationError(code = "auth-failed", message = "Authentication failed: $desc")
        }
        ssecurityValue = JsonLite.stringField(data, "ssecurity")
        nonceValue = JsonLite.longField(data, "nonce")
        userIdValue = JsonLite.stringField(data, "userId")
        cUserIdValue = JsonLite.stringField(data, "cUserId")
        locationValue = JsonLite.stringField(data, "location")
        if (ssecurityValue == null || locationValue == null) {
            throw AuthenticationError(code = "missing-auth-data", message = "Missing ssecurity or location in auth response")
        }
    }

    private suspend fun fetchServiceToken() {
        val nonce = nonceValue ?: throw AuthenticationError(code = "missing-nonce", message = "No nonce from authentication step")
        val sign = MiCrypto.clientSign(nonce.toString(), ssecurity)
        val url = "$locationValue&clientSign=${formQueryEscape(sign)}"
        var token = engine.request("GET", url, followRedirects = false).cookies["serviceToken"]
        if (token == null) token = engine.request("GET", url).cookies["serviceToken"]
        serviceTokenValue = token
            ?: throw AuthenticationError(code = "no-service-token", message = "serviceToken not found in response cookies")
    }

    companion object {
        private const val PREFIX = "&&&START&&&"

        fun stripPrefix(text: String): String =
            if (text.startsWith(PREFIX)) text.substring(PREFIX.length) else text

        private fun formQueryEscape(s: String): String {
            val sb = StringBuilder()
            for (b in s.encodeToByteArray()) {
                val c = b.toInt() and 0xFF
                val ch = c.toChar()
                if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                    sb.append(ch)
                } else {
                    sb.append('%')
                    sb.append("0123456789ABCDEF"[c ushr 4])
                    sb.append("0123456789ABCDEF"[c and 0x0F])
                }
            }
            return sb.toString()
        }
    }
}

/** Authenticated Xiaomi health API client. Ports XiaomiClient. */
class XiaomiClient(
    private val session: XiaomiSession,
    private val engine: HttpEngine,
    private val region: String = "ru",
) {
    suspend fun request(
        method: String,
        url: String,
        params: Map<String, String>? = null,
        pathPrefix: String = "",
        extraQuery: Map<String, String>? = null,
    ): String {
        val fullPath = url.substringAfter("://").substringAfter("/", "")
        val signingPath = MiCrypto.computeSigningPath("/$fullPath", pathPrefix)
        val nonceB64 = MiCrypto.generateNonce(PlatformRandom.randomBytes(8), PlatformRandom.currentTimeMillis())
        val encrypted = MiCrypto.miEncryptParams(method, signingPath, params ?: emptyMap(), nonceB64, session.ssecurity)
        val query = (extraQuery?.toList() ?: emptyList()) + encrypted.toList()
        val cookies = mapOf(
            "cUserId" to session.cUserId,
            "serviceToken" to session.serviceToken,
            "locale" to "en_us",
        )
        val headers = XiaomiConstants.apiHeaders(region)
        val response = if (method.uppercase() == "GET") {
            engine.request("GET", url, query = query, headers = headers, cookies = cookies)
        } else {
            engine.request(
                "POST", url,
                headers = headers, cookies = cookies,
                body = MiCrypto.formUrlEncode(encrypted.toList()).encodeToByteArray(),
            )
        }
        return MiCrypto.miDecryptResponse(response.bodyText.trim(), nonceB64, session.ssecurity)
    }

    suspend fun getSourceList(pageSize: Int = 50, status: Int = 1): List<XiaomiSource> {
        val data = """{"page_size":$pageSize,"status":$status}"""
        val decrypted = request("POST", XiaomiUrls.SOURCE_LIST, params = mapOf("data" to data))
        val result = JsonLite.objectField(decrypted, "result") ?: return emptyList()
        val list = JsonLite.arrayField(result, "list") ?: return emptyList()
        return JsonLite.splitObjects(list).map { XiaomiSource.fromApiResponse(it) }
    }
}
