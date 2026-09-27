package org.huamitoken

import org.huamitoken.crypto.Aes128
import org.huamitoken.crypto.Base64
import org.huamitoken.crypto.Md5
import org.huamitoken.crypto.Rc4
import org.huamitoken.crypto.Sha1
import org.huamitoken.crypto.Sha256
import org.huamitoken.crypto.toHex

/**
 * Xiaomi Mi Fitness API encryption (RC4-drop[1024]).
 * Key derivation: SHA256(base64Decode(ssecurity) || base64Decode(nonce)).
 * Ports huami_token/mi_crypto.py.
 */
object MiCrypto {
    fun deriveRc4Key(ssecurityB64: String, nonceB64: String): String {
        val combined = Base64.decode(ssecurityB64) + Base64.decode(nonceB64)
        return Base64.encode(Sha256.digest(combined))
    }

    private fun makeRc4(rc4KeyB64: String): Rc4 = Rc4(Base64.decode(rc4KeyB64)).also { it.drop(1024) }

    fun computeSigningPath(fullPath: String, pathPrefix: String = ""): String {
        if (pathPrefix.isEmpty()) {
            val idx = fullPath.indexOf("/")
            return if (idx >= 0) fullPath.substring(idx) else fullPath
        }
        val idx = fullPath.indexOf(pathPrefix)
        if (idx < 0) return fullPath
        val result = fullPath.substring(idx + pathPrefix.length)
        return if (result.startsWith("/")) result else "/$result"
    }

    /**
     * Nonce: base64(random_8_bytes || int32_be(now_ms / 60000)).
     * Randomness and clock are injected so common code stays platform-free.
     */
    fun generateNonce(random8: ByteArray, timeMillis: Long, timeDiffMs: Long = 0): String {
        require(random8.size == 8)
        val minutes = ((timeMillis + timeDiffMs) / 60000).toInt()
        val buf = ByteArray(12)
        random8.copyInto(buf)
        buf[8] = (minutes ushr 24).toByte()
        buf[9] = (minutes ushr 16).toByte()
        buf[10] = (minutes ushr 8).toByte()
        buf[11] = minutes.toByte()
        return Base64.encode(buf)
    }

    private fun sha1Sign(method: String, urlPath: String, params: Map<String, String>, rc4KeyB64: String): String {
        val parts = mutableListOf<String>()
        if (method.isNotEmpty()) parts.add(method.uppercase())
        if (urlPath.isNotEmpty()) parts.add(urlPath)
        for (k in params.keys.sorted()) parts.add("$k=${params[k]}")
        parts.add(rc4KeyB64)
        return Base64.encode(Sha1.digest(parts.joinToString("&").encodeToByteArray()))
    }

    private fun hmacSign(message: String, rc4KeyB64: String): String {
        val key = Base64.decode(rc4KeyB64)
        val blockSize = 64
        val k = if (key.size > blockSize) Sha256.digest(key) else key
        val padded = k + ByteArray(blockSize - k.size)
        val oKey = ByteArray(blockSize) { (padded[it].toInt() xor 0x5C).toByte() }
        val iKey = ByteArray(blockSize) { (padded[it].toInt() xor 0x36).toByte() }
        val inner = Sha256.digest(iKey + message.encodeToByteArray())
        return Base64.encode(Sha256.digest(oKey + inner))
    }

    fun miEncryptParams(
        method: String,
        signingPath: String,
        params: Map<String, String>,
        nonceB64: String,
        ssecurityB64: String,
    ): Map<String, String> {
        val rc4KeyB64 = deriveRc4Key(ssecurityB64, nonceB64)
        val rc4Hash = sha1Sign(method, signingPath, params, rc4KeyB64)
        val withHash = params + ("rc4_hash__" to rc4Hash)

        val rc4 = makeRc4(rc4KeyB64)
        val encrypted = mutableMapOf<String, String>()
        for (k in withHash.keys.sorted()) {
            encrypted[k] = Base64.encode(rc4.crypt(withHash[k]!!.encodeToByteArray()))
        }
        val signature = sha1Sign(method, signingPath, encrypted, rc4KeyB64)
        return encrypted + mapOf("signature" to signature, "_nonce" to nonceB64)
    }

    fun miSignParams(
        signingPath: String,
        params: Map<String, String>,
        nonceB64: String,
        ssecurityB64: String,
    ): Map<String, String> {
        val rc4KeyB64 = deriveRc4Key(ssecurityB64, nonceB64)
        val parts = mutableListOf<String>()
        if (signingPath.isNotEmpty()) parts.add(signingPath)
        for (k in params.keys.sorted()) parts.add("$k=${params[k]}")
        parts.add(rc4KeyB64)
        return params + mapOf("signature" to hmacSign(parts.joinToString("&"), rc4KeyB64))
    }

    fun miEncryptBody(bodyText: String, nonceB64: String, ssecurityB64: String): ByteArray {
        val rc4 = makeRc4(deriveRc4Key(ssecurityB64, nonceB64))
        return rc4.crypt(bodyText.encodeToByteArray())
    }

    fun miEncryptQueryParams(
        params: Map<String, String>,
        nonceB64: String,
        ssecurityB64: String,
    ): Map<String, String> {
        val rc4 = makeRc4(deriveRc4Key(ssecurityB64, nonceB64))
        val out = mutableMapOf<String, String>()
        for ((k, v) in params) {
            out[k] = Base64.encode(rc4.crypt(v.encodeToByteArray()))
        }
        out.putAll(params)
        return out
    }

    fun miDecryptResponse(bodyB64: String, nonceB64: String, ssecurityB64: String): String {
        val rc4 = makeRc4(deriveRc4Key(ssecurityB64, nonceB64))
        return rc4.crypt(Base64.decode(bodyB64)).decodeToString()
    }

    fun miDecryptParams(
        encryptedParams: Map<String, String>,
        nonceB64: String,
        ssecurityB64: String,
    ): Map<String, String> {
        val rc4 = makeRc4(deriveRc4Key(ssecurityB64, nonceB64))
        val result = mutableMapOf<String, String>()
        for (k in encryptedParams.keys.sorted()) {
            if (k == "signature" || k == "_nonce") continue
            result[k] = rc4.crypt(Base64.decode(encryptedParams[k]!!)).decodeToString()
        }
        return result
    }

    fun md5HexLower(text: String): String = Md5.hex(text.encodeToByteArray())

    fun passwordHash(password: String): String = md5HexLower(password).uppercase()

    fun generateDeviceId(username: String): String = "an_" + md5HexLower(username)

    /** base64(SHA1("nonce=<nonce>&<ssecurity>")) for the Xiaomi login step 3. */
    fun clientSign(nonce: String, ssecurity: String): String {
        val input = "nonce=$nonce&$ssecurity"
        return Base64.encode(Sha1.digest(input.encodeToByteArray()))
    }

    /**
     * application/x-www-form-urlencoded, matching Python's urllib urlencode
     * (quote_via=quote_plus): unreserved chars pass through, space becomes +,
     * everything else is %XX with uppercase hex.
     */
    fun formUrlEncode(params: List<Pair<String, String>>): String =
        params.joinToString("&") { (k, v) -> "${formEscape(k)}=${formEscape(v)}" }

    private fun formEscape(s: String): String {
        val bytes = s.encodeToByteArray()
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append(ch)
            } else if (ch == ' ') {
                sb.append('+')
            } else {
                sb.append('%')
                sb.append("0123456789ABCDEF"[c ushr 4])
                sb.append("0123456789ABCDEF"[c and 0x0F])
            }
        }
        return sb.toString()
    }

    /** Parse `Location: ...?access=..&refresh=..` query params (Zepp token step). */
    fun parseQueryParams(url: String): Map<String, String> {
        val q = url.substringAfter("?", "")
        if (q.isEmpty()) return emptyMap()
        return q.split("&").mapNotNull {
            val i = it.indexOf("=")
            if (i < 0) null else urlDecode(it.substring(0, i)) to urlDecode(it.substring(i + 1))
        }.toMap()
    }

    private fun urlDecode(s: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < s.length) {
            when (val c = s[i]) {
                '+' -> { bytes.add(' '.code.toByte()); i++ }
                '%' -> {
                    bytes.add(((hexVal(s[i + 1]) shl 4) or hexVal(s[i + 2])).toByte())
                    i += 3
                }
                else -> {
                    val enc = c.toString().encodeToByteArray()
                    enc.forEach { bytes.add(it) }
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("Bad percent-encoding")
    }
}

/** Zepp AES-128-CBC (PKCS7) payload cipher. Ports zepp_encrypt/decrypt_payload. */
object ZeppCrypto {
    fun encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == 16 && iv.size == 16)
        val rk = Aes128.expandKey(key)
        val padLen = 16 - (data.size % 16)
        val padded = data + ByteArray(padLen) { padLen.toByte() }
        val out = ByteArray(padded.size)
        var prev = iv.copyOf()
        for (off in padded.indices step 16) {
            val block = ByteArray(16) { i -> (padded[off + i].toInt() xor prev[i].toInt()).toByte() }
            val enc = Aes128.encryptBlock(block, rk)
            enc.copyInto(out, off)
            prev = enc
        }
        return out
    }

    fun decrypt(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == 16 && iv.size == 16 && encrypted.size % 16 == 0)
        val rk = Aes128.expandKey(key)
        val out = ByteArray(encrypted.size)
        var prev = iv.copyOf()
        for (off in encrypted.indices step 16) {
            val block = encrypted.copyOfRange(off, off + 16)
            val dec = Aes128.decryptBlock(block, rk)
            for (i in 0 until 16) out[off + i] = (dec[i].toInt() xor prev[i].toInt()).toByte()
            prev = block
        }
        val padLen = out.last().toInt() and 0xFF
        require(padLen in 1..16) { "Bad PKCS7 padding" }
        for (i in out.size - padLen until out.size) require(out[i].toInt() and 0xFF == padLen) { "Bad PKCS7 padding" }
        return out.copyOf(out.size - padLen)
    }
}
