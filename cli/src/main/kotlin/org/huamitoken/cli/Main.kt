package org.huamitoken.cli

import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlinx.coroutines.runBlocking
import org.huamitoken.HttpEngine
import org.huamitoken.HuamiTokenError
import org.huamitoken.XiaomiClient
import org.huamitoken.XiaomiSession
import org.huamitoken.ZeppClient
import org.huamitoken.ZeppSession
import org.huamitoken.ZipReader
import org.huamitoken.buildGpsUihh
import org.huamitoken.findArchive

private fun usage(): String = """
usage: huami-token [-h] -m {amazfit,xiaomi} [-e EMAIL] [-p PASSWORD] [-b] [-g] [-n]

Obtain Bluetooth Auth key from Amazfit (Zepp) or Xiaomi Mi Fitness.
""".trimIndent()

fun main(args: Array<String>) {
    var method: String? = null
    var email: String? = null
    var password: String? = null
    var btKeys = false
    var gps = false
    var noLogout = false
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "-h", "--help" -> { println(usage()); return }
            "-m", "--method" -> { method = args.getOrNull(++i) }
            "-e", "--email" -> { email = args.getOrNull(++i) }
            "-p", "--password" -> { password = args.getOrNull(++i) }
            "-b", "--bt_keys" -> btKeys = true
            "-g", "--gps" -> gps = true
            "-n", "--no_logout" -> noLogout = true
            else -> { System.err.println("Unknown argument: ${args[i]}\n${usage()}"); kotlin.system.exitProcess(2) }
        }
        i++
    }
    if (method != "amazfit" && method != "xiaomi") {
        System.err.println("Missing or invalid -m/--method (amazfit|xiaomi)\n${usage()}")
        kotlin.system.exitProcess(2)
    }
    if (password == null) {
        password = promptPassword()
    }
    val exit = try {
        runBlocking {
            when (method) {
                "amazfit" -> runAmazfit(email, password, btKeys, gps, noLogout)
                else -> runXiaomi(email, password, btKeys, noLogout)
            }
        }
        0
    } catch (e: HuamiTokenError) {
        System.err.println("Error: ${e.message}")
        1
    } catch (e: IllegalArgumentException) {
        System.err.println("Error: ${e.message}")
        1
    }
    kotlin.system.exitProcess(exit)
}

private fun promptPassword(): String {
    val console = System.console()
    return if (console != null) {
        String(console.readPassword("Password: ") ?: charArrayOf())
    } else {
        print("Password: ")
        readlnOrNull() ?: ""
    }
}

private suspend fun runAmazfit(email: String?, password: String, btKeys: Boolean, gps: Boolean, noLogout: Boolean) {
    val engine = HttpEngine()
    val session = ZeppSession(email ?: "", password, engine)
    session.login()
    println("Logged in! User id: ${session.userId}")
    val client = ZeppClient(session, engine)
    if (btKeys) {
        client.getDevices().forEachIndexed { idx, d ->
            println("Device $idx:")
            println("  MAC: ${d.mac}, Active: ${if (d.active) "Yes" else "No"}")
            println("  Key: 0x${d.authKey}")
        }
    }
    if (gps) {
        val outDir = Path.of("").toAbsolutePath()
        val archives = client.downloadGpsData()
        for ((name, bytes) in archives) {
            val dest = outDir.resolve(name)
            dest.writeBytes(bytes)
            println("Wrote $dest")
        }
        val cep = ZipReader(findArchive(archives, "cep_7days.zip"))
        val lle = ZipReader(findArchive(archives, "lle_1week.zip"))
        val uihh = buildGpsUihh(
            mapOf(
                "gps_alm.bin" to cep.read("gps_alm.bin"),
                "gln_alm.bin" to cep.read("gln_alm.bin"),
                "lle_bds.lle" to lle.read("lle_bds.lle"),
                "lle_gps.lle" to lle.read("lle_gps.lle"),
                "lle_glo.lle" to lle.read("lle_glo.lle"),
                "lle_gal.lle" to lle.read("lle_gal.lle"),
                "lle_qzss.lle" to lle.read("lle_qzss.lle"),
            ),
        )
        outDir.resolve("gps_uihh.bin").writeBytes(uihh)
        println("Wrote ${outDir.resolve("gps_uihh.bin")}")
    }
    if (noLogout) {
        println("\nNo logout!")
        println("app_token=${session.appToken}\nlogin_token=${session.loginToken}")
    } else {
        runCatching { session.logout() }.onFailure { println("\nError logging out.") }
            .onSuccess { println("\nLogged out.") }
    }
}

private suspend fun runXiaomi(email: String?, password: String, btKeys: Boolean, noLogout: Boolean) {
    val engine = HttpEngine()
    val session = XiaomiSession(email ?: "", password, engine)
    session.login()
    if (btKeys) {
        val sources = XiaomiClient(session, engine).getSourceList()
        if (sources.isEmpty()) println("No bound devices found.")
        sources.forEachIndexed { idx, s ->
            println("Device $idx: ${s.name}")
            println("  MAC: ${s.mac}")
            if (s.authKey.isNotEmpty()) println("  Key: 0x${s.authKey}") else println("  Key: (not available)")
        }
    }
    if (noLogout) {
        println("\nNo logout!")
        println("ssecurity=${session.ssecurity}")
        println("service_token=${session.serviceToken}")
        println("user_id=${session.userId}")
        println("c_user_id=${session.cUserId}")
    } else {
        println("\nLogged in successfully.")
        println("user_id=${session.userId}")
    }
}
