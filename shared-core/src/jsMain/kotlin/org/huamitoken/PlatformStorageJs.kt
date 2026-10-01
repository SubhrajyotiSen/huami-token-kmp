package org.huamitoken

actual fun saveGpsFiles(files: Map<String, ByteArray>): String {
    val downloaded = files.keys.sorted().joinToString(", ")
    for ((name, bytes) in files) {
        val blob = js("new Blob([new Uint8Array(bytes)], {type: 'application/octet-stream'})")
        val url = js("URL.createObjectURL(blob)") as String
        val link = js("document.createElement('a')")
        link.href = url
        link.download = name
        link.style.display = "none"
        js("document.body.appendChild(link)")
        link.click()
        link.remove()
        js("URL.revokeObjectURL(url)")
    }
    return "Started downloads: $downloaded"
}
