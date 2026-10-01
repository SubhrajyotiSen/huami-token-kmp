package org.huamitoken.crypto

/**
 * AES-128 block cipher. S-box is derived at runtime from GF(2^8) arithmetic
 * (multiplicative inverse + affine transform), so no tables are memorized.
 */
object Aes128 {
    private fun xtime(a: Int): Int {
        val v = a and 0xFF
        return ((v shl 1) xor (if (v and 0x80 != 0) 0x1B else 0)) and 0xFF
    }

    private fun gfMul(a: Int, b: Int): Int {
        var x = a and 0xFF
        var y = b and 0xFF
        var r = 0
        while (y != 0) {
            if (y and 1 != 0) r = r xor x
            x = xtime(x)
            y = y ushr 1
        }
        return r
    }

    private fun gfPow(a: Int, e: Int): Int {
        var r = 1
        var x = a and 0xFF
        var n = e
        while (n > 0) {
            if (n and 1 != 0) r = gfMul(r, x)
            x = gfMul(x, x)
            n = n ushr 1
        }
        return r
    }

    private val SBOX: IntArray = IntArray(256) { i ->
        val inv = if (i == 0) 0 else gfPow(i, 254)
        var v = inv xor ((inv shl 1) or (inv ushr 7)) xor
            ((inv shl 2) or (inv ushr 6)) xor ((inv shl 3) or (inv ushr 5)) xor
            ((inv shl 4) or (inv ushr 4)) xor 0x63
        v and 0xFF
    }

    private val INV_SBOX: IntArray = IntArray(256).also { inv ->
        for (i in 0 until 256) inv[SBOX[i]] = i
    }

    fun expandKey(key: ByteArray): IntArray {
        require(key.size == 16)
        val w = IntArray(44)
        for (i in 0 until 4) {
            w[i] = ((key[4 * i].toInt() and 0xFF) shl 24) or
                ((key[4 * i + 1].toInt() and 0xFF) shl 16) or
                ((key[4 * i + 2].toInt() and 0xFF) shl 8) or
                (key[4 * i + 3].toInt() and 0xFF)
        }
        var rcon = 1
        for (i in 4 until 44) {
            var t = w[i - 1]
            if (i % 4 == 0) {
                // RotWord+SubWord: [a0,a1,a2,a3] -> [S(a1)^rcon, S(a2), S(a3), S(a0)]
                t = ((SBOX[(t ushr 16) and 0xFF] xor rcon) shl 24) or
                    (SBOX[(t ushr 8) and 0xFF] shl 16) or
                    (SBOX[t and 0xFF] shl 8) or
                    SBOX[(t ushr 24) and 0xFF]
                rcon = xtime(rcon)
            }
            w[i] = w[i - 4] xor t
        }
        return w
    }

    fun encryptBlock(input: ByteArray, roundKeys: IntArray): ByteArray {
        require(input.size == 16)
        val s = IntArray(16) { input[it].toInt() and 0xFF }
        addRoundKey(s, roundKeys, 0)
        for (round in 1 until 10) {
            subBytes(s, SBOX)
            shiftRows(s)
            mixColumns(s)
            addRoundKey(s, roundKeys, round)
        }
        subBytes(s, SBOX)
        shiftRows(s)
        addRoundKey(s, roundKeys, 10)
        return ByteArray(16) { s[it].toByte() }
    }

    fun decryptBlock(input: ByteArray, roundKeys: IntArray): ByteArray {
        require(input.size == 16)
        val s = IntArray(16) { input[it].toInt() and 0xFF }
        addRoundKey(s, roundKeys, 10)
        for (round in 9 downTo 1) {
            invShiftRows(s)
            subBytes(s, INV_SBOX)
            addRoundKey(s, roundKeys, round)
            invMixColumns(s)
        }
        invShiftRows(s)
        subBytes(s, INV_SBOX)
        addRoundKey(s, roundKeys, 0)
        return ByteArray(16) { s[it].toByte() }
    }

    private fun addRoundKey(s: IntArray, rk: IntArray, round: Int) {
        for (c in 0 until 4) {
            val w = rk[round * 4 + c]
            for (r in 0 until 4) s[r + 4 * c] = s[r + 4 * c] xor ((w ushr (24 - 8 * r)) and 0xFF)
        }
    }

    private fun subBytes(s: IntArray, box: IntArray) {
        for (i in 0 until 16) s[i] = box[s[i]]
    }

    private fun shiftRows(s: IntArray) {
        val t = s.copyOf()
        for (r in 0 until 4) for (c in 0 until 4) s[r + 4 * c] = t[r + 4 * ((c + r) % 4)]
    }

    private fun invShiftRows(s: IntArray) {
        val t = s.copyOf()
        for (r in 0 until 4) for (c in 0 until 4) s[r + 4 * c] = t[r + 4 * ((c - r + 4) % 4)]
    }

    private fun mixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = s[4 * c]; val a1 = s[4 * c + 1]; val a2 = s[4 * c + 2]; val a3 = s[4 * c + 3]
            s[4 * c] = gfMul(a0, 2) xor gfMul(a1, 3) xor a2 xor a3
            s[4 * c + 1] = a0 xor gfMul(a1, 2) xor gfMul(a2, 3) xor a3
            s[4 * c + 2] = a0 xor a1 xor gfMul(a2, 2) xor gfMul(a3, 3)
            s[4 * c + 3] = gfMul(a0, 3) xor a1 xor a2 xor gfMul(a3, 2)
        }
    }

    private fun invMixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = s[4 * c]; val a1 = s[4 * c + 1]; val a2 = s[4 * c + 2]; val a3 = s[4 * c + 3]
            s[4 * c] = gfMul(a0, 0x0E) xor gfMul(a1, 0x0B) xor gfMul(a2, 0x0D) xor gfMul(a3, 0x09)
            s[4 * c + 1] = gfMul(a0, 0x09) xor gfMul(a1, 0x0E) xor gfMul(a2, 0x0B) xor gfMul(a3, 0x0D)
            s[4 * c + 2] = gfMul(a0, 0x0D) xor gfMul(a1, 0x09) xor gfMul(a2, 0x0E) xor gfMul(a3, 0x0B)
            s[4 * c + 3] = gfMul(a0, 0x0B) xor gfMul(a1, 0x0D) xor gfMul(a2, 0x09) xor gfMul(a3, 0x0E)
        }
    }
}
