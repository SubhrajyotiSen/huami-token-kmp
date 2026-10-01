package org.huamitoken.crypto

/** CRC-32 (ISO 3309, zlib-compatible). Used for the GPS uihh container. */
object Crc32 {
    private val TABLE = IntArray(256) { i ->
        var c = i
        repeat(8) { c = if (c and 1 != 0) -0x12477CE0 xor (c ushr 1) else c ushr 1 }
        c
    }

    fun checksum(data: ByteArray): Long {
        var crc = -1
        for (b in data) crc = TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        return (crc.inv().toLong() and 0xFFFFFFFFL)
    }
}

/** RC4 stream cipher with configurable keystream drop (Xiaomi uses drop[1024]). */
class Rc4(key: ByteArray) {
    private val s = IntArray(256) { it }
    private var i = 0
    private var j = 0

    init {
        require(key.isNotEmpty())
        var jj = 0
        for (ii in 0 until 256) {
            jj = (jj + s[ii] + (key[ii % key.size].toInt() and 0xFF)) and 0xFF
            val t = s[ii]; s[ii] = s[jj]; s[jj] = t
        }
    }

    fun crypt(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (idx in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val t = s[i]; s[i] = s[j]; s[j] = t
            out[idx] = (data[idx].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
        }
        return out
    }

    fun drop(n: Int) {
        crypt(ByteArray(n))
    }
}
