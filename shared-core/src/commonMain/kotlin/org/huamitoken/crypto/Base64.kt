package org.huamitoken.crypto

/** Standard Base64 (RFC 4648), no line breaks. Pure Kotlin, all targets. */
object Base64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val REVERSE = IntArray(128) { -1 }.also {
        ALPHABET.forEachIndexed { i, c -> it[c.code] = i }
        it['='.code] = -2
    }

    fun encode(input: ByteArray): String {
        val out = StringBuilder(((input.size + 2) / 3) * 4)
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else 0
            out.append(ALPHABET[(b0 ushr 2)])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            out.append(if (i + 1 < input.size) ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
            out.append(if (i + 2 < input.size) ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    fun decode(s: String): ByteArray {
        val clean = s.filter { !it.isWhitespace() }
        require(clean.length % 4 == 0) { "Invalid base64 length" }
        val out = ByteArray((clean.length / 4) * 3)
        var o = 0
        var i = 0
        while (i < clean.length) {
            val c = IntArray(4) { k ->
                val ch = clean[i + k]
                require(ch.code < 128 && REVERSE[ch.code] != -1) { "Invalid base64 char: $ch" }
                REVERSE[ch.code]
            }
            require(!(c[0] == -2 || c[1] == -2)) { "Invalid base64 padding" }
            out[o++] = ((c[0] shl 2) or (c[1] ushr 4)).toByte()
            if (c[2] != -2) {
                out[o++] = (((c[1] and 0x0F) shl 4) or (c[2] ushr 2)).toByte()
                if (c[3] != -2) out[o++] = (((c[2] and 0x03) shl 6) or c[3]).toByte()
            }
            i += 4
        }
        return out.copyOf(o)
    }
}

fun ByteArray.toHex(): String = joinToString("") {
    val v = it.toInt() and 0xFF
    "0123456789abcdef"[v ushr 4].toString() + "0123456789abcdef"[v and 0x0F]
}

fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Odd hex length" }
    return ByteArray(hex.length / 2) { i ->
        ((hexDigit(hex[2 * i]) shl 4) or hexDigit(hex[2 * i + 1])).toByte()
    }
}

private fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> throw IllegalArgumentException("Bad hex char: $c")
}
