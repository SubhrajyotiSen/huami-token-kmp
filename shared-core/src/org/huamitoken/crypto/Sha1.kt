package org.huamitoken.crypto

/** SHA-1 (RFC 3174). Used for Xiaomi clientSign and RC4 request signing. */
object Sha1 {
    fun digest(input: ByteArray): ByteArray {
        val msgLen = input.size
        val bitLenHigh = (msgLen.toLong() * 8) ushr 32
        val bitLenLow = (msgLen.toLong() * 8) and 0xFFFFFFFFL
        val paddedSize = (((msgLen + 8) / 64) + 1) * 64
        val msg = ByteArray(paddedSize)
        input.copyInto(msg)
        msg[msgLen] = 0x80.toByte()
        for (i in 0 until 4) {
            msg[paddedSize - 8 + i] = (bitLenHigh ushr (8 * (3 - i))).toByte()
            msg[paddedSize - 4 + i] = (bitLenLow ushr (8 * (3 - i))).toByte()
        }

        var h0 = 0x67452301
        var h1 = -0x10325477 // 0xEFCDAB89
        var h2 = -0x67452302 // 0x98BADCFE
        var h3 = 0x10325476
        var h4 = -0x3C2D1E10 // 0xC3D2E1F0

        val w = IntArray(80)
        var off = 0
        while (off < paddedSize) {
            for (i in 0 until 16) {
                w[i] = ((msg[off + 4 * i].toInt() and 0xFF) shl 24) or
                    ((msg[off + 4 * i + 1].toInt() and 0xFF) shl 16) or
                    ((msg[off + 4 * i + 2].toInt() and 0xFF) shl 8) or
                    (msg[off + 4 * i + 3].toInt() and 0xFF)
            }
            for (i in 16 until 80) w[i] = rotateLeft(w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16], 1)
            var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
            for (i in 0 until 80) {
                val (f, k) = when (i) {
                    in 0 until 20 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                    in 20 until 40 -> (b xor c xor d) to 0x6ED9EBA1
                    in 40 until 60 -> ((b and c) or (b and d) or (c and d)) to -0x70E44324 // 0x8F1BBCDC
                    else -> (b xor c xor d) to -0x359D3E2A // 0xCA62C1D6
                }
                val tmp = rotateLeft(a, 5) + f + e + k + w[i]
                e = d; d = c; c = rotateLeft(b, 30); b = a; a = tmp
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
            off += 64
        }
        return beBytes(h0) + beBytes(h1) + beBytes(h2) + beBytes(h3) + beBytes(h4)
    }

    private fun rotateLeft(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

    private fun beBytes(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
}
