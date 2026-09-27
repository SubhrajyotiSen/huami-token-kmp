package org.huamitoken

/** Zepp (Amazfit) authentication session. Ports ZeppSession. */
class ZeppSession(
    val username: String,
    private val password: String,
    private val engine: HttpEngine,
) {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var loginTokenValue: String? = null
    private var appTokenValue: String? = null
    private var userIdValue: String? = null

    val appToken: String
        get() = appTokenValue ?: throw AuthenticationError(message = "Not logged in — no app_token available")
    val userId: String
        get() = userIdValue ?: throw AuthenticationError(message = "Not logged in — no user_id available")
    val loginToken: String
        get() = loginTokenValue ?: throw AuthenticationError(message = "Not logged in — no login_token available")

    suspend fun login() {
        fetchRefreshAndAccessTokens()
        loginWithAccessToken()
    }

    private suspend fun fetchRefreshAndAccessTokens() {
        val encoded = MiCrypto.formUrlEncode(ZeppConstants.tokenPayload(username, password))
        val encrypted = ZeppCrypto.encrypt(
            encoded.encodeToByteArray(),
            ZeppCryptoParams.KEY.encodeToByteArray(),
            ZeppCryptoParams.IV.encodeToByteArray(),
        )
        val response = engine.request(
            method = "POST",
            url = ZeppUrls.TOKENS,
            headers = ZeppConstants.tokenHeaders(),
            body = encrypted,
            followRedirects = false,
        )
        if (response.statusCode != 303) {
            throw AuthenticationError(
                code = "no-redirect",
                message = "No redirect after token request, status code is ${response.statusCode} instead of 303",
            )
        }
        val location = response.headers.entries.firstOrNull { it.key.equals("location", ignoreCase = true) }?.value
            ?: throw AuthenticationError(code = "no-location", message = "No redirect location found in the response headers")
        val params = MiCrypto.parseQueryParams(location)
        refreshToken = params["refresh"]
        accessToken = params["access"]
        if (refreshToken == null || accessToken == null) {
            throw AuthenticationError(code = "no-tokens", message = "No refresh or access token found in the redirect URL")
        }
    }

    private suspend fun loginWithAccessToken() {
        val code = accessToken ?: throw AuthenticationError(message = "No access token")
        val body = MiCrypto.formUrlEncode(ZeppConstants.loginPayload(code, PlatformRandom.uuid()))
        val response = engine.request(
            method = "POST",
            url = ZeppUrls.LOGIN,
            headers = ZeppConstants.loginHeaders(),
            body = body.encodeToByteArray(),
        )
        if (response.statusCode != 200) {
            throw AuthenticationError(code = "login-failed", message = "Login request failed with status code ${response.statusCode}")
        }
        val tokenInfo = JsonLite.objectField(response.bodyText, "token_info")
            ?: throw AuthenticationError(code = "no-login-tokens", message = "No token_info found in the login response")
        loginTokenValue = JsonLite.stringField(tokenInfo, "login_token")
        appTokenValue = JsonLite.stringField(tokenInfo, "app_token")
        if (loginTokenValue == null || appTokenValue == null) {
            throw AuthenticationError(code = "no-login-tokens", message = "No login_token or app_token found in the login response")
        }
        userIdValue = JsonLite.stringField(tokenInfo, "user_id")
            ?: throw AuthenticationError(code = "no-user-id", message = "No user_id found in the login response")
    }

    suspend fun logout() {
        val body = MiCrypto.formUrlEncode(listOf("login_token" to loginToken, "os_verison" to "vnull"))
        val response = engine.request(
            method = "POST",
            url = ZeppUrls.LOGOUT,
            headers = ZeppConstants.logoutHeaders(),
            body = body.encodeToByteArray(),
        )
        if (response.statusCode != 200) {
            throw LogoutError(code = "logout-failed", message = "Logout request failed with status code ${response.statusCode}")
        }
        if (JsonLite.stringField(response.bodyText, "result") != "ok") {
            throw LogoutError(code = "logout-error", message = "Logout failed with response: ${response.bodyText}")
        }
    }
}

/** Zepp API client. Ports ZeppClient. */
class ZeppClient(private val session: ZeppSession, private val engine: HttpEngine) {
    suspend fun getDevices(): List<Device> {
        // Python sends the same cache-buster twice (`[uuid] * 2`).
        val r = PlatformRandom.uuid()
        val params = ZeppConstants.deviceParams(
            r, r, session.userId, PlatformRandom.randomUint64String(),
        )
        val headers = ZeppConstants.authedHeaders(session.appToken, PlatformRandom.uuid())
        val url = ZeppUrls.DEVICES.replace("{user_id}", session.userId)
        val response = engine.request("GET", url, query = params, headers = headers)
        if (response.statusCode != 200) {
            throw DeviceError(code = "get-devices-failed", message = "Get devices request failed with status code ${response.statusCode}")
        }
        val items = JsonLite.arrayField(response.bodyText, "items") ?: throw DeviceError(
            code = "no-devices", message = "No devices found in the response",
        )
        val devices = JsonLite.splitObjects(items).map { Device.fromApiResponse(it) }
        if (devices.isEmpty()) throw DeviceError(code = "no-devices", message = "No devices found in the response")
        return devices
    }

    /**
     * Download GPS archives. Returns filename -> zip bytes.
     * The platform layer writes them to disk and builds gps_uihh.bin.
     */
    suspend fun downloadGpsData(): Map<String, ByteArray> {
        val r = PlatformRandom.uuid()
        val params = ZeppConstants.gpsParams(
            r, r, session.userId, PlatformRandom.randomUint64String(),
        )
        val headers = ZeppConstants.gpsHeaders(session.appToken, PlatformRandom.uuid())
        val out = mutableMapOf<String, ByteArray>()
        for (fileType in ZeppConstants.GPS_FILE_TYPES) {
            val meta = engine.request(
                "GET", ZeppUrls.GPS.replace("{file_type}", fileType), query = params, headers = headers,
            )
            if (meta.statusCode != 200) {
                throw DeviceError(code = "get-gps-failed", message = "Get GPS data failed with status code ${meta.statusCode}")
            }
            val fileUrl = JsonLite.stringField(meta.bodyText, "fileUrl") ?: continue
            val fileName = fileUrl.substringAfterLast("/")
            val file = engine.request("GET", fileUrl, headers = headers)
            file.raiseForStatus()
            out[fileName] = file.bodyBytes
        }
        return out

    }

    private fun HttpResult.raiseForStatus() {
        if (statusCode !in 200..299) throw DeviceError(code = "gps-download-failed", message = "GPS file download failed: HTTP $statusCode")
    }
}
