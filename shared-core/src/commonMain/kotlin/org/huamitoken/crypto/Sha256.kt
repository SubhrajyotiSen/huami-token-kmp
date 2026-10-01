package org.huamitoken.crypto

/** SHA-256 (FIPS 180-4). Used for Xiaomi RC4 key derivation. */
object Sha256 {
    private val K_LONG = longArrayOf(
        0x428a2f98L, 0x71374491L, 0xb5c0fbcfL, 0xe9b5dba5L, 0x3956c25bL, 0x59f111f1L, 0x923f82a4L, 0xab1c5ed5L,
        0xd807aa98L, 0x12835b01L, 0x243185beL, 0x550c7dc3L, 0x72be5d74L, 0x80deb1feL, 0x9bdc06a7L, 0xc19bf174L,
        0xe49b69c1L, 0xefbe4786L, 0x0fc19dc6L, 0x240ca1ccL, 0x2de92c6fL, 0x4a7484aaL, 0x5cb0a9dcL, 0x76f988daL,
        0x983e5152L, 0xa831c66dL, 0xb00327c8L, 0xbf597fc7L, 0xc6e00bf3L, 0xd5a79147L, 0x06ca6351L, 0x14292967L,
        0x27b70a85L, 0x2e1b2138L, 0x4d2c6dfcL, 0x53380d13L, 0x650a7354L, 0x766a0abbL, 0x81c2c92eL, 0x92722c85L,
        0xa2bfe8a1L, 0xa81a664bL, 0xc24b8b70L, 0xc76c51a3L, 0xd192e819L, 0xd6990624L, 0xf40e3585L, 0x106aa070L,
        0x19a4c116L, 0x1e376c08L, 0x2748774cL, 0x34b0bcb5L, 0x391c0cb3L, 0x4ed8aa4aL, 0x5b9cca4fL, 0x682e6ff3L,
        0x748f82eeL, 0x78a5636fL, 0x84c87814L, 0x8cc70208L, 0x90befffaL, 0xa4506cebL, 0xbef9a3f7L, 0xc67178f2L,
    )

    fun digest(input: ByteArray): ByteArray {
        val k = IntArray(64) { K_LONG[it].toInt() }
        val msgLen = input.size
        val bitLen = msgLen.toLong() * 8
        val paddedSize = (((msgLen + 8) / 64) + 1) * 64
        val msg = ByteArray(paddedSize)
        input.copyInto(msg)
        msg[msgLen] = 0x80.toByte()
        for (i in 0 until 8) msg[paddedSize - 8 + i] = (bitLen ushr (8 * (7 - i))).toByte()

        var h0 = 0x6a09e667
        var h1 = -0x4498517B // 0xbb67ae85
        var h2 = 0x3c6ef372
        var h3 = -0x5ab00ac6 // 0xa54ff53a
        var h4 = 0x510e527f
        var h5 = -0x64FA9774 // 0x9b05688c
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19

        val w = IntArray(64)
        var off = 0
        while (off < paddedSize) {
            for (i in 0 until 16) {
                w[i] = ((msg[off + 4 * i].toInt() and 0xFF) shl 24) or
                    ((msg[off + 4 * i + 1].toInt() and 0xFF) shl 16) or
                    ((msg[off + 4 * i + 2].toInt() and 0xFF) shl 8) or
                    (msg[off + 4 * i + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (i in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + ch + k[i] + w[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
            off += 64
        }
        return be(h0) + be(h1) + be(h2) + be(h3) + be(h4) + be(h5) + be(h6) + be(h7)
    }

    private fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))

    private fun be(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
}
