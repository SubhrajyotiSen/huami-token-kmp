package org.huamitoken

actual fun saveGpsFiles(files: Map<String, ByteArray>): String {
    // In browser JS, files can be triggered as downloads via DOM or Blob in Web UI
    return "Browser: ${files.size} files ready for download"
}
