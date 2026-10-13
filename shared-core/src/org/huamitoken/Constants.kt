package org.huamitoken

/** Ports huami_token/constants.py. Nulls in the Python dicts become explicit parameters. */
object ZeppUrls {
    const val TOKENS = "https://api-user-us2.zepp.com/v2/registrations/tokens"
    const val LOGIN = "https://api-mifit-us2.zepp.com/v2/client/login"
    const val LOGOUT = "https://api-mifit-us2.zepp.com/v1/client/logout"
    const val DEVICES = "https://api-mifit.zepp.com/users/{user_id}/devices"
    const val GPS = "https://api-mifit-us2.zepp.com/apps/com.xiaomi.hm.health/fileTypes/{file_type}/files"
}

object ZeppCryptoParams {
    const val KEY = "xeNtBVqzDc6tuNTh"
    const val IV = "MAAAYAAAAAAAAABg"
}

object ZeppConstants {
    const val CHANNEL = "a100900101016"
    const val APP_VERSION = "9.12.5"
    const val CV = "151689_9.12.5"
    const val VB = "202509151347"
    const val UA = "Zepp/9.12.5 (Pixel 4; Android 12; Density/2.75)"

    fun tokenHeaders(): Map<String, String> = mapOf(
        "app_name" to "com.huami.midong",
        "appname" to "com.huami.midong",
        "cv" to CV,
        "v" to "2.0",
        "appplatform" to "android_phone",
        "vb" to VB,
        "vn" to APP_VERSION,
        "user-agent" to UA,
        "x-hm-ekv" to "1",
        "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "accept-encoding" to "gzip",
    )

    fun tokenPayload(email: String, password: String): List<Pair<String, String>> = listOf(
        "emailOrPhone" to email,
        "state" to "REDIRECTION",
        "client_id" to "HuaMi",
        "password" to password,
        "redirect_uri" to "https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html",
        "region" to "us-west-2",
        "token" to "access",
        "token" to "refresh",
        "country_code" to "US",
    )

    fun loginHeaders(): Map<String, String> = mapOf(
        "app_name" to "com.huami.webapp",
        "appname" to "com.huami.webapp",
        "origin" to "https://user.zepp.com",
        "referer" to "https://user.zepp.com/",
        "user-agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0",
        "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "accept" to "application/json, text/plain, */*",
        "accept-language" to "en-US,en;q=0.5",
    )

    fun loginPayload(code: String, deviceId: String): List<Pair<String, String>> = listOf(
        "code" to code,
        "device_id" to deviceId,
        "device_model" to "android_phone",
        "app_version" to APP_VERSION,
        "dn" to "api-mifit.zepp.com,api-user.zepp.com,api-mifit.zepp.com,api-watch.zepp.com,app-analytics.zepp.com,auth.zepp.com,api-analytics.zepp.com",
        "third_name" to "huami",
        "source" to "com.huami.watch.hmwatchmanager:9.12.5:151689",
        "app_name" to "com.huami.midong",
        "country_code" to "US",
        "grant_type" to "access_token",
        "allow_registration" to "false",
        "lang" to "en",
        "countryState" to "US-NY",
    )

    fun authedHeaders(appToken: String, requestId: String): Map<String, String> = mapOf(
        "hm-privacy-diagnostics" to "false",
        "country" to "US",
        "appplatform" to "android_phone",
        "hm-privacy-ceip" to "true",
        "x-request-id" to requestId,
        "timezone" to "Europe/London",
        "channel" to CHANNEL,
        "vb" to VB,
        "cv" to CV,
        "appname" to "com.huami.midong",
        "v" to "2.0",
        "vn" to APP_VERSION,
        "apptoken" to appToken,
        "lang" to "en_US",
        "user-agent" to UA,
        "accept-encoding" to "gzip",
    )

    fun gpsHeaders(appToken: String, requestId: String): Map<String, String> = mapOf(
        "hm-privacy-diagnostics" to "false",
        "country" to "US",
        "appplatform" to "android_phone",
        "hm-privacy-ceip" to "false",
        "x-request-id" to requestId,
        "timezone" to "Europe/London",
        "channel" to CHANNEL,
        "vb" to VB,
        "cv" to CV,
        "appname" to "com.huami.midong",
        "v" to "2.0",
        "vn" to APP_VERSION,
        "apptoken" to appToken,
        "lang" to "en_US",
        "user-agent" to UA,
        "accept-encoding" to "gzip",
    )


    fun deviceParams(random1: String, random2: String, userId: String, appId: String): List<Pair<String, String>> = listOf(
        "r" to random1,
        "r" to random2,
        "enableMultiDeviceOnMultiType" to "true",
        "enableMultiDeviceOnMultiType" to "true",
        "userid" to userId,
        "appid" to appId,
        "channel" to CHANNEL,
        "country" to "US",
        "cv" to CV,
        "device" to "android_32",
        "device_type" to "android_phone",
        "enableMultiDevice" to "true",
        "lang" to "en_US",
        "timezone" to "Europe/London",
        "v" to "2.0",
    )

    fun gpsParams(random1: String, random2: String, userId: String, appId: String): List<Pair<String, String>> = listOf(
        "r" to random1,
        "r" to random2,
        "userid" to userId,
        "appid" to appId,
        "channel" to CHANNEL,
        "country" to "US",
        "cv" to CV,
        "device" to "android_32",
        "device_type" to "android_phone",
        "lang" to "en_US",
        "timezone" to "Europe/Berlin",
        "v" to "2.0",
    )

    fun logoutHeaders(): Map<String, String> = mapOf(
        "app_name" to "com.huami.midong",
        "hm-privacy-ceip" to "false",
        "accept-language" to "en-US",
        "appname" to "com.huami.midong",
        "cv" to CV,
        "v" to "2.0",
        "appplatform" to "android_phone",
        "vb" to VB,
        "vn" to APP_VERSION,
        "user-agent" to UA,
        "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
    )

    val GPS_FILE_TYPES = listOf("AGPS_ALM", "AGPSZIP", "LLE", "AGPS", "EPO", "LTO")
}

object XiaomiUrls {
    const val SERVICE_LOGIN = "https://account.xiaomi.com/pass/serviceLogin"
    const val SERVICE_LOGIN_AUTH2 = "https://account.xiaomi.com/pass/serviceLoginAuth2"
    const val SOURCE_LIST = "https://hlth.io.mi.com/app/v1/source/get_source_list"
}

object XiaomiConstants {
    const val UA = "Mozilla/5.0 (Linux; Android 12; Pixel 4 Build/SP1A.210812.016.C1;" +
        " wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0" +
        " Chrome/131.0.6778.200 Mobile Safari/537.36"
    const val API_UA = "Android-12-9.8.348i-google-Pixel 4"

    fun serviceLoginHeaders(): Map<String, String> = mapOf(
        "User-Agent" to UA,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
    )

    fun serviceLoginAuth2Headers(): Map<String, String> = mapOf(
        "User-Agent" to UA,
        "Content-Type" to "application/x-www-form-urlencoded",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
    )

    fun apiHeaders(region: String): Map<String, String> = mapOf(
        "User-Agent" to API_UA,
        "Accept-Encoding" to "gzip",
        "region_tag" to region,
    )
}
