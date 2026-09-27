package org.huamitoken

/** Raw DEFLATE (RFC 1951) decompressor: fixed + dynamic Huffman, stored blocks. */
object Inflate {
    fun decompress(input: ByteArray): ByteArray {
        val r = BitReader(input)
        val out = DynamicBuffer()
        var final = false
        while (!final) {
            final = r.bits(1) != 0
            when (r.bits(2)) {
                0 -> {
                    r.alignToByte()
                    val len = r.bits(16)
                    val nlen = r.bits(16)
                    require(len xor 0xFFFF == nlen) { "Bad stored block lengths" }
                    out.put(r.bytes(len))
                }
                1 -> decodeBlock(r, out, fixedLitLen(), fixedDist())
                2 -> {
                    val hlits = r.bits(5) + 257
                    val hdist = r.bits(5) + 1
                    val hclen = r.bits(4) + 4
                    val order = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
                    val clLens = IntArray(19)
                    for (i in 0 until hclen) clLens[order[i]] = r.bits(3)
                    val clTree = Huffman(clLens)
                    val lens = IntArray(hlits + hdist)
                    var i = 0
                    while (i < lens.size) {
                        val sym = clTree.decode(r)
                        when (sym) {
                            in 0..15 -> lens[i++] = sym
                            16 -> {
                                val rep = r.bits(2) + 3
                                val prev = if (i > 0) lens[i - 1] else 0
                                repeat(rep) { lens[i++] = prev }
                            }
                            17 -> repeat(r.bits(3) + 3) { lens[i++] = 0 }
                            18 -> repeat(r.bits(7) + 11) { lens[i++] = 0 }
                        }
                    }
                    decodeBlock(
                        r, out,
                        Huffman(lens.copyOf(hlits)),
                        Huffman(lens.copyOfRange(hlits, hlits + hdist)),
                    )
                }
                else -> throw IllegalArgumentException("Bad deflate block type")
            }
        }
        return out.toByteArray()
    }

    private fun fixedLitLen(): Huffman {
        val lens = IntArray(288)
        for (i in 0 until 144) lens[i] = 8
        for (i in 144 until 256) lens[i] = 9
        for (i in 256 until 280) lens[i] = 7
        for (i in 280 until 288) lens[i] = 8
        return Huffman(lens)
    }

    private fun fixedDist(): Huffman = Huffman(IntArray(32) { 5 })

    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
        8193, 12289, 16385, 24577,
    )
    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )

    private fun decodeBlock(r: BitReader, out: DynamicBuffer, lit: Huffman, dist: Huffman) {
        while (true) {
            val sym = lit.decode(r)
            when {
                sym < 256 -> out.put(sym.toByte())
                sym == 256 -> return
                else -> {
                    val li = sym - 257
                    val len = LENGTH_BASE[li] + r.bits(LENGTH_EXTRA[li])
                    val di = dist.decode(r)
                    val d = DIST_BASE[di] + r.bits(DIST_EXTRA[di])
                    out.copyMatch(d, len)
                }
            }
        }
    }

    class BitReader(private val data: ByteArray) {
        private var bytePos = 0
        private var bitBuf = 0
        private var bitCount = 0

        fun bits(n: Int): Int {
            if (n == 0) return 0
            require(n in 1..16)
            while (bitCount < n) {
                require(bytePos < data.size) { "Truncated deflate stream" }
                bitBuf = bitBuf or ((data[bytePos++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            val v = bitBuf and ((1 shl n) - 1)
            bitBuf = bitBuf ushr n
            bitCount -= n
            return v
        }

        fun alignToByte() {
            bitBuf = 0
            bitCount = 0
        }

        fun bytes(n: Int): ByteArray {
            require(bitCount == 0)
            require(bytePos + n <= data.size) { "Truncated deflate stream" }
            val b = data.copyOfRange(bytePos, bytePos + n)
            bytePos += n
            return b
        }

        // Stored blocks only appear right after alignToByte, so bitBuf is empty here.
    }

    class Huffman(lengths: IntArray) {
        private val maxBits: Int
        private val codes: IntArray // sym -> canonical code
        private val table: Map<Pair<Int, Int>, Int>

        init {
            maxBits = lengths.maxOrNull() ?: 0
            codes = IntArray(lengths.size) { -1 }
            val blCount = IntArray(maxBits + 1)
            for (len in lengths) if (len > 0) blCount[len]++
            var code = 0
            val nextCode = IntArray(maxBits + 1)
            for (bits in 1..maxBits) {
                code = (code + blCount[bits - 1]) shl 1
                nextCode[bits] = code
            }
            val map = mutableMapOf<Pair<Int, Int>, Int>()
            for (sym in lengths.indices) {
                val len = lengths[sym]
                if (len > 0) {
                    codes[sym] = nextCode[len]++
                    map[len to codes[sym]] = sym
                }
            }
            table = map
        }

        fun decode(r: BitReader): Int {
            var code = 0
            for (len in 1..maxBits) {
                code = (code shl 1) or r.bits(1)
                table[len to code]?.let { return it }
            }
            throw IllegalArgumentException("Invalid Huffman code")
        }
    }

    private class DynamicBuffer {
        private var buf = ByteArray(1024)
        var size = 0

        private fun ensure(n: Int) {
            if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
        }

        fun put(b: Byte) {
            ensure(1); buf[size++] = b
        }

        fun put(bs: ByteArray) {
            ensure(bs.size); bs.copyInto(buf, size); size += bs.size
        }

        fun copyMatch(dist: Int, len: Int) {
            require(dist in 1..size) { "Bad match distance" }
            ensure(len)
            for (i in 0 until len) {
                buf[size] = buf[size - dist]
                size++
            }
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}

/**
 * Minimal ZIP reader (stored + deflated entries).
 * Entries are located through the central directory, so archives using data
 * descriptors (sizes only known after the data, like Python's own output)
 * read correctly.
 */
class ZipReader(private val data: ByteArray) {
    private data class Entry(val method: Int, val offset: Int, val compSize: Int)

    private fun u16(at: Int): Int = (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)
    private fun u32(at: Int): Long =
        (data[at].toLong() and 0xFF) or ((data[at + 1].toLong() and 0xFF) shl 8) or
            ((data[at + 2].toLong() and 0xFF) shl 16) or ((data[at + 3].toLong() and 0xFF) shl 24)

    private fun entries(): Map<String, Entry> {
        // End of central directory: last 22 bytes at minimum (plus comment).
        var eocd = -1
        var p = data.size - 22
        while (p >= maxOf(0, data.size - 65557)) {
            if (u32(p) == 0x06054B50L) {
                eocd = p
                break
            }
            p--
        }
        if (eocd < 0) throw HuamiTokenError(message = "Not a zip archive (EOCD not found)")
        val count = u16(eocd + 10)
        var c = u32(eocd + 16).toInt()
        val out = mutableMapOf<String, Entry>()
        repeat(count) {
            if (c + 46 > data.size || u32(c) != 0x02014B50L) {
                throw HuamiTokenError(message = "Corrupt zip central directory")
            }
            val method = u16(c + 10)
            val compSize = u32(c + 20).toInt()
            val nameLen = u16(c + 28)
            val extraLen = u16(c + 30)
            val commentLen = u16(c + 32)
            val localOffset = u32(c + 42).toInt()
            val name = data.copyOfRange(c + 46, c + 46 + nameLen).decodeToString()
            if (u32(localOffset) != 0x04034B50L) {
                throw HuamiTokenError(message = "Corrupt zip local header for $name")
            }
            val localNameLen = u16(localOffset + 26)
            val localExtraLen = u16(localOffset + 28)
            out[name] = Entry(method, localOffset + 30 + localNameLen + localExtraLen, compSize)
            c += 46 + nameLen + extraLen + commentLen
        }
        return out
    }

    fun read(name: String): ByteArray {
        val e = entries()[name] ?: throw HuamiTokenError(message = "Zip member not found: $name")
        val raw = data.copyOfRange(e.offset, e.offset + e.compSize)
        return when (e.method) {
            0 -> raw
            8 -> Inflate.decompress(raw)
            else -> throw HuamiTokenError(message = "Unsupported zip method ${e.method} for $name")
        }
    }
}
