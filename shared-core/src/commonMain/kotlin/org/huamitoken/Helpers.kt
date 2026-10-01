package org.huamitoken

import org.huamitoken.crypto.Crc32

/** Little-endian uint32, matching Python helpers.encode_uint32. */
fun encodeUint32(value: Long): ByteArray =
    byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte(),
    )

/** File id table for the gps_uihh.bin container (ports build_gps_uihh). */
val GPS_UIHH_ORDER = linkedMapOf(
    "gps_alm.bin" to 0x05,
    "gln_alm.bin" to 0x0F,
    "lle_bds.lle" to 0x86,
    "lle_gps.lle" to 0x87,
    "lle_glo.lle" to 0x88,
    "lle_gal.lle" to 0x89,
    "lle_qzss.lle" to 0x8A,
)

/**
 * Build the gps_uihh.bin container from already-downloaded archive contents.
 * Keys are member names inside cep_7days.zip (gps_alm/gln_alm) and lle_1week.zip (lle_*).
 * Platform layers handle zip extraction and disk writes.
 */
fun buildGpsUihh(members: Map<String, ByteArray>): ByteArray {
    var content = byteArrayOf()
    for ((name, id) in GPS_UIHH_ORDER) {
        val fileContent = members[name]
            ?: throw HuamiTokenError(message = "Missing GPS member for uihh: $name")
        val header = byteArrayOf(1, id.toByte()) +
            encodeUint32(fileContent.size.toLong()) +
            encodeUint32(Crc32.checksum(fileContent))
        content += header + fileContent
    }
    val header = "UIHH".encodeToByteArray() +
        byteArrayOf(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01) +
        encodeUint32(Crc32.checksum(content)) +
        ByteArray(6) +
        encodeUint32(content.size.toLong()) +
        ByteArray(6)
    return header + content
}
