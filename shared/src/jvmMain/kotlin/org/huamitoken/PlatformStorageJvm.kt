package org.huamitoken

import java.io.File

actual fun saveGpsFiles(files: Map<String, ByteArray>): String {
    val userHome = System.getProperty("user.home") ?: "."
    val downloadsDir = File(userHome, "Downloads")
    val targetDir = if (downloadsDir.exists() && downloadsDir.isDirectory) {
        File(downloadsDir, "huami-gps").apply { mkdirs() }
    } else {
        File("gps-files").apply { mkdirs() }
    }
    for ((name, bytes) in files) {
        File(targetDir, name).writeBytes(bytes)
    }
    return targetDir.absolutePath
}
