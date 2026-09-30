package org.huamitoken

import android.content.Context
import java.io.File

object AndroidAppContext {
    var context: Context? = null
}

actual fun saveGpsFiles(files: Map<String, ByteArray>): String {
    val ctx = AndroidAppContext.context
    val targetDir = if (ctx != null) {
        ctx.getExternalFilesDir(null) ?: ctx.filesDir
    } else {
        File(System.getProperty("java.io.tmpdir") ?: ".", "huami-gps").apply { mkdirs() }
    }
    for ((name, bytes) in files) {
        File(targetDir, name).writeBytes(bytes)
    }
    return targetDir.absolutePath
}
