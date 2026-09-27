package org.huamitoken.crypto

import kotlin.math.abs
import kotlin.math.sin

/** MD5 (RFC 1321). Used for Xiaomi device-id and password hash. */
object Md5 {
    private val S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    private val K = IntArray(64) { i ->
        (abs(sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
    }

    fun digest(input: ByteArray): ByteArray {
        val msgLen = input.size
        val bitLen = msgLen.toLong() * 8
        val paddedSize = (((msgLen + 8) / 64) + 1) * 64
        val msg = ByteArray(paddedSize)
        input.copyInto(msg)
        msg[msgLen] = 0x80.toByte()
        for (i in 0 until 8) msg[paddedSize - 8 + i] = (bitLen ushr (8 * i)).toByte()

        var a0 = 0x67452301
        var b0 = -0x10325477 // 0xefcdab89
        var c0 = -0x67452302 // 0x98badcfe
        var d0 = 0x10325476

        val m = IntArray(16)
        var off = 0
        while (off < paddedSize) {
            for (i in 0 until 16) {
                m[i] = (msg[off + 4 * i].toInt() and 0xFF) or
                    ((msg[off + 4 * i + 1].toInt() and 0xFF) shl 8) or
                    ((msg[off + 4 * i + 2].toInt() and 0xFF) shl 16) or
                    ((msg[off + 4 * i + 3].toInt() and 0xFF) shl 24)
            }
            var a = a0; var b = b0; var c = c0; var d = d0
            for (i in 0 until 64) {
                val (f, g) = when (i) {
                    in 0 until 16 -> ((b and c) or (b.inv() and d)) to i
                    in 16 until 32 -> ((d and b) or (d.inv() and c)) to ((5 * i + 1) % 16)
                    in 32 until 48 -> (b xor c xor d) to ((3 * i + 5) % 16)
                    else -> (c xor (b or d.inv())) to ((7 * i) % 16)
                }
                val tmp = d
                d = c
                c = b
                b = b + rotateLeft(a + f + K[i] + m[g], S[i])
                a = tmp
            }
            a0 += a; b0 += b; c0 += c; d0 += d
            off += 64
        }
        return leBytes(a0) + leBytes(b0) + leBytes(c0) + leBytes(d0)
    }

    fun hex(input: ByteArray): String = digest(input).toHex()

    private fun rotateLeft(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

    private fun leBytes(v: Int): ByteArray =
        byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
}
