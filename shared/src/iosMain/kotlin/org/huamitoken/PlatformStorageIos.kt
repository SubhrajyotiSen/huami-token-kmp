package org.huamitoken

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual fun saveGpsFiles(files: Map<String, ByteArray>): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    val docDir = (paths.firstOrNull() as? String) ?: ""
    val gpsDir = (docDir as NSString).stringByAppendingPathComponent("huami-gps")
    NSFileManager.defaultManager.createDirectoryAtPath(gpsDir, true, null, null)

    for ((name, bytes) in files) {
        val filePath = (gpsDir as NSString).stringByAppendingPathComponent(name)
        val data = memScoped {
            if (bytes.isEmpty()) NSData()
            else NSData.dataWithBytes(allocArrayOf(bytes), bytes.size.toULong())
        }
        data.writeToFile(filePath, true)
    }
    return gpsDir
}
