package org.huamitoken

/** Login method chosen in the UI or on the CLI. */
enum class LoginMethod { AMAZFIT, XIAOMI }

/**
 * UI-facing facade shared by Android, iOS, web, and CLI.
 * Throws [HuamiTokenError] subclasses on failure; callers map them to messages.
 */
class TokenRepository(private val engine: HttpEngine) {
    suspend fun fetchDevices(method: LoginMethod, username: String, password: String): List<DeviceDisplay> {
        require(username.isNotBlank() && password.isNotEmpty()) {
            "Username and password must not be empty"
        }
        return when (method) {
            LoginMethod.AMAZFIT -> {
                val session = ZeppSession(username, password, engine)
                session.login()
                try {
                    ZeppClient(session, engine).getDevices().mapIndexed { i, d ->
                        DeviceDisplay(
                            title = "Device $i",
                            mac = d.mac,
                            active = if (d.active) "Yes" else "No",
                            key = "0x${d.authKey}",
                        )
                    }
                } finally {
                    runCatching { session.logout() }
                }
            }
            LoginMethod.XIAOMI -> {
                val session = XiaomiSession(username, password, engine)
                session.login()
                XiaomiClient(session, engine).getSourceList().map { s ->
                    DeviceDisplay(
                        title = s.name,
                        mac = s.mac,
                        active = null,
                        key = if (s.authKey.isNotEmpty()) "0x${s.authKey}" else "(not available)",
                    )
                }
            }
        }
    }

    /**
     * Amazfit-only: download GPS archives and build gps_uihh.bin.
     * Returns filename -> bytes, including "gps_uihh.bin".
     */
    suspend fun fetchGpsFiles(username: String, password: String): Map<String, ByteArray> {
        val session = ZeppSession(username, password, engine)
        session.login()
        try {
            val archives = ZeppClient(session, engine).downloadGpsData()
            val cep = findArchive(archives, "cep_7days.zip")
            val lle = findArchive(archives, "lle_1week.zip")
            val cepZip = ZipReader(cep)
            val lleZip = ZipReader(lle)
            val members = mapOf(
                "gps_alm.bin" to cepZip.read("gps_alm.bin"),
                "gln_alm.bin" to cepZip.read("gln_alm.bin"),
                "lle_bds.lle" to lleZip.read("lle_bds.lle"),
                "lle_gps.lle" to lleZip.read("lle_gps.lle"),
                "lle_glo.lle" to lleZip.read("lle_glo.lle"),
                "lle_gal.lle" to lleZip.read("lle_gal.lle"),
                "lle_qzss.lle" to lleZip.read("lle_qzss.lle"),
            )
            return archives + ("gps_uihh.bin" to buildGpsUihh(members))
        } finally {
            runCatching { session.logout() }
        }
    }
}

/** Filenames are matched by substring so timestamped GPS archives resolve. */
fun findArchive(archives: Map<String, ByteArray>, namePart: String): ByteArray =
    archives.entries.firstOrNull { it.key.contains(namePart) }?.value
        ?: throw HuamiTokenError(message = "Archive containing '$namePart' was not downloaded")
