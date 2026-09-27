package org.huamitoken

import org.huamitoken.crypto.Base64
import org.huamitoken.crypto.Crc32
import org.huamitoken.crypto.Md5
import org.huamitoken.crypto.Sha1
import org.huamitoken.crypto.Sha256
import org.huamitoken.crypto.hexToBytes
import org.huamitoken.crypto.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val SSECURITY = "YrTdzxpoL2f5MVGlER9E8w=="
private const val NONCE = "R42d+0k3e1wBv42r"

private const val ZEPP_RAW_HEX = "656d61696c4f7250686f6e653d61253430612e636f6d2673746174653d5245444952454354494f4e26636c69656e745f69643d4875614d692670617373776f72643d612672656469726563745f7572693d687474707325334125324625324673332d75732d776573742d322e616d617a6f6e6177732e636f6d253246686d2d726567697374726174696f6e253246737563636573737369676e696e2e68746d6c26726567696f6e3d75732d776573742d3226746f6b656e3d61636365737326746f6b656e3d7265667265736826636f756e7472795f636f64653d5553"
private const val ZEPP_ENC_HEX = "5b0602a5523c4c8fe0b0adb987d612320c18f98c4584d1c8845c04068f34a6b1564cf1b022899425fea777bc18287085a295dd1e34ccdba917988fe909546eaa169dd4c5d5a0f3e82c9e302c050f4d7849935ddfeb6333ed7f6d0bb7f24ec5509f46eddbc60ffde73aaaf8913301bfd169054d4c943009a83879ccce66376cb18f953dac58eff6fc3c77b533b71911489da246dfd8f1decd8ccb9e6a68ee4376c8258c706df8fcfecdfe633b297ad795560fa8673fd5b84f6e75d1093ef599b6a87cd3449837ca63b53cfdbadfecfe08ba9caf65f903511f4d84a388338fc4b6"
private val ZEPP_KEY = "xeNtBVqzDc6tuNTh".encodeToByteArray()
private val ZEPP_IV = "MAAAYAAAAAAAAABg".encodeToByteArray()

class ZeppAesTest {
    @Test
    fun encryptMatchesCapturedTraffic() {
        val raw = hexToBytes(ZEPP_RAW_HEX)
        assertTrue(ZeppCrypto.encrypt(raw, ZEPP_KEY, ZEPP_IV).contentEquals(hexToBytes(ZEPP_ENC_HEX)))
    }

    @Test
    fun decryptMatchesOriginalPayload() {
        val raw = hexToBytes(ZEPP_RAW_HEX)
        assertTrue(ZeppCrypto.decrypt(hexToBytes(ZEPP_ENC_HEX), ZEPP_KEY, ZEPP_IV).contentEquals(raw))
    }

    @Test
    fun encryptDecryptRoundtrip() {
        val data = "arbitrary 27-byte payload!!".encodeToByteArray()
        assertTrue(ZeppCrypto.decrypt(ZeppCrypto.encrypt(data, ZEPP_KEY, ZEPP_IV), ZEPP_KEY, ZEPP_IV).contentEquals(data))
    }
}

class MiCryptoVectorTest {
    @Test
    fun decryptResponse() {
        val body = "DPGjfdGeLhOcEauyRBJHKM845nz2j9E2TTStMnWkp4bRnqLLUVfXnpEn7jHaRzCqyjNaNaKWbETueJbGRFA="
        assertEquals(
            """{"code":0,"message":"ok","result":{"datas":null,"last_id":-1}}""",
            MiCrypto.miDecryptResponse(body, NONCE, SSECURITY),
        )
    }

    @Test
    fun decryptParams() {
        val decrypted = MiCrypto.miDecryptParams(
            mapOf(
                "data" to "DPGke9HZNgvUVOiwTAhDLMkvmyekkJg4Q3q+JHKOopbRnunFF1rKkotx9mWdG3CkhTBfM7qsJxruJt6BUE5lyIr+qsjYUDgvkGZinSG8hS97q4Y6VMZso+A=",
                "rc4_hash__" to "QPhJ2ehekyhADdflTzZv3o7f0qeW0+RdOZSFjA==",
            ),
            NONCE, SSECURITY,
        )
        assertEquals(
            """{"did":"xiaomiwear_app","last_id":0,"limit":20,"module":"device_setting","update_time":0}""",
            decrypted["data"],
        )
    }

    @Test
    fun encryptMatchesCapturedTraffic() {
        val result = MiCrypto.miEncryptParams(
            "POST",
            "/setting/get_user_device_settings",
            mapOf("data" to """{"did":"xiaomiwear_app","last_id":0,"limit":20,"module":"device_setting","update_time":0}"""),
            NONCE, SSECURITY,
        )
        assertEquals("DPGke9HZNgvUVOiwTAhDLMkvmyekkJg4Q3q+JHKOopbRnunFF1rKkotx9mWdG3CkhTBfM7qsJxruJt6BUE5lyIr+qsjYUDgvkGZinSG8hS97q4Y6VMZso+A=", result["data"])
        assertEquals("QPhJ2ehekyhADdflTzZv3o7f0qeW0+RdOZSFjA==", result["rc4_hash__"])
    }

    @Test
    fun encryptDecryptRoundtrip() {
        val nonce = MiCrypto.generateNonce(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), 1_700_000_000_000L)
        val plain = mapOf("data" to """{"test":"value","num":42}""")
        val encrypted = MiCrypto.miEncryptParams("POST", "/setting/get_x", plain, nonce, SSECURITY)
        val encOnly = encrypted.filterKeys { it != "signature" && it != "_nonce" }
        assertEquals(plain["data"], MiCrypto.miDecryptParams(encOnly, nonce, SSECURITY)["data"])
    }

    @Test
    fun deriveRc4KeyVector() {
        assertEquals("WTdYar02+Bog/2hz82k5V46Lq/ZYVrEpmd/B4AHW/k4=", MiCrypto.deriveRc4Key(SSECURITY, NONCE))
    }

    @Test
    fun computeSigningPath() {
        assertEquals(
            "/setting/get_user_device_settings",
            MiCrypto.computeSigningPath("/healthapp/setting/get_user_device_settings", "healthapp/"),
        )
        assertEquals(
            "/privacy/get_privacy_change",
            MiCrypto.computeSigningPath("/healthapp/privacy/get_privacy_change", "healthapp/"),
        )
        assertEquals(
            "/app/v1/source/get_source_list",
            MiCrypto.computeSigningPath("/app/v1/source/get_source_list", ""),
        )
        assertEquals(
            "/sports/list",
            MiCrypto.computeSigningPath("/cgi-op/api/v1/miwear/sports/list", "cgi-op/api/v1/miwear/"),
        )
    }

    @Test
    fun sourceListReencryptVector() {
        val nonce = "WJLZldGzqgEBv42r"
        val decrypted = MiCrypto.miDecryptParams(
            mapOf(
                "data" to "13wc21mXVNlEBm1V6377FO82qjYB0rMbnvH/",
                "rc4_hash__" to "iEb0n3F4yHV8uK5wr8PlxmaMwhKqVO+++pa8Sg==",
            ),
            nonce, SSECURITY,
        )
        val signingPath = MiCrypto.computeSigningPath("/app/v1/source/get_source_list", "")
        val dataOnly = decrypted.filterKeys { it != "rc4_hash__" }
        val result = MiCrypto.miEncryptParams("POST", signingPath, dataOnly, nonce, SSECURITY)
        assertEquals("13wc21mXVNlEBm1V6377FO82qjYB0rMbnvH/", result["data"])
        assertEquals("iEb0n3F4yHV8uK5wr8PlxmaMwhKqVO+++pa8Sg==", result["rc4_hash__"])
    }

    @Test
    fun deviceIdAndPasswordHash() {
        assertEquals("an_55502f40dc8b7c769880b10874abc9d0", MiCrypto.generateDeviceId("test@example.com"))
        assertEquals("32250170A0DCA92D53EC9624F336CA24", MiCrypto.passwordHash("pass123"))
    }

    @Test
    fun clientSignVector() {
        assertEquals("RXGVBk+TQNYKRNnr6ltBaOTwyHs=", MiCrypto.clientSign("123456", SSECURITY))
    }

    @Test
    fun nonceStructure() {
        val random = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2)
        val nonce = MiCrypto.generateNonce(random, 1_700_000_000_000L)
        val raw = Base64.decode(nonce)
        assertEquals(12, raw.size)
        assertTrue(raw.copyOf(8).contentEquals(random))
        val minutes = ((raw[8].toInt() and 0xFF) shl 24) or ((raw[9].toInt() and 0xFF) shl 16) or
            ((raw[10].toInt() and 0xFF) shl 8) or (raw[11].toInt() and 0xFF)
        assertEquals((1_700_000_000_000L / 60000).toInt(), minutes)
    }
}

class HashVectorTest {
    @Test
    fun md5Abc() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.hex("abc".encodeToByteArray()))
    }

    @Test
    fun sha1Abc() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Sha1.digest("abc".encodeToByteArray()).toHex())
    }

    @Test
    fun sha256Abc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.digest("abc".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun base64Roundtrip() {
        val bytes = ByteArray(256) { it.toByte() }
        assertTrue(Base64.decode(Base64.encode(bytes)).contentEquals(bytes))
        assertEquals("+//+", Base64.encode(byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte())))
    }

    @Test
    fun crc32Abc() {
        assertEquals(0x352441C2L, Crc32.checksum("abc".encodeToByteArray()))
    }
}

class FormEncodeTest {
    @Test
    fun tokenPayloadMatchesPythonUrlencode() {
        val encoded = MiCrypto.formUrlEncode(ZeppConstants.tokenPayload("a@a.com", "a"))
        assertEquals(hexToBytes(ZEPP_RAW_HEX).decodeToString(), encoded)
    }

    @Test
    fun spaceAndUnicodeEscaping() {
        assertEquals("a+b%40c%2F%C3%A9", MiCrypto.formUrlEncode(listOf("x" to "a b@c/é")).substringAfter("="))
    }
}

class ModelParseTest {
    @Test
    fun zeppDevice() {
        val item = """{"macAddress":"AA:BB:CC:DD:EE:FF","activeStatus":1,"additionalInfo":"{\"auth_key\":\"deadbeef\"}"}"""
        val d = Device.fromApiResponse(item)
        assertEquals("AA:BB:CC:DD:EE:FF", d.mac)
        assertEquals(true, d.active)
        assertEquals("deadbeef", d.authKey)
    }

    @Test
    fun zeppDeviceDefaults() {
        val d = Device.fromApiResponse("{}")
        assertEquals("??:??:??:??:??:??", d.mac)
        assertEquals(false, d.active)
        assertEquals("??", d.authKey)
    }

    @Test
    fun xiaomiSourceWithObjectDetail() {
        val src = """{"name":"Amazfit Band 7 ","detail":{"mac":"AB:CD:EF:12:34:56","auth_key":"a3c10e34"},"mac":"AB:CD:EF:12:34:56"}"""
        val s = XiaomiSource.fromApiResponse(src)
        assertEquals("Amazfit Band 7", s.name)
        assertEquals("AB:CD:EF:12:34:56", s.mac)
        assertEquals("a3c10e34", s.authKey)
    }

    @Test
    fun xiaomiSourceWithStringDetail() {
        val src = """{"name":"Band","detail":"{\"mac\":\"11:22:33:44:55:66\",\"auth_key\":\"ff\"}"}"""
        val s = XiaomiSource.fromApiResponse(src)
        assertEquals("11:22:33:44:55:66", s.mac)
        assertEquals("ff", s.authKey)
    }
}

class JsonLiteTest {
    @Test
    fun stripsXiaomiPrefixAndReadsFields() {
        val data = XiaomiSession.stripPrefix("""&&&START&&&{"code":0,"ssecurity":"abc"}""")
        assertEquals("abc", JsonLite.stringField(data, "ssecurity"))
        assertEquals(0L, JsonLite.longField(data, "code"))
    }

    @Test
    fun numericUserIdReadsAsString() {
        assertEquals("9876543", JsonLite.stringField("""{"userId":9876543}""", "userId"))
    }

    @Test
    fun nestedTokenInfoAndItems() {
        val body = """{"token_info":{"login_token":"lt","app_token":"at","user_id":"42"}}"""
        val info = JsonLite.objectField(body, "token_info")!!
        assertEquals("at", JsonLite.stringField(info, "app_token"))
        val items = JsonLite.arrayField("""{"items":[{"a":1},{"a":2}]}""", "items")!!
        assertEquals(2, JsonLite.splitObjects(items).size)
    }

    @Test
    fun queryParamParsing() {
        val params = MiCrypto.parseQueryParams("https://x.example/cb?access=acc123&refresh=ref456")
        assertEquals("acc123", params["access"])
        assertEquals("ref456", params["refresh"])
    }

    @Test
    fun missingFieldIsNull() {
        assertEquals(null, JsonLite.stringField("""{"a":1}""", "b"))
    }

    @Test
    fun gpsResponseParsing() {
        val jsonArrayResponse = """[{"fileUrl":"https://api-mifit.zepp.com/cdn/AGPS_ALM_2026.zip"}]"""
        val fileUrl = JsonLite.stringField(jsonArrayResponse, "fileUrl")
        assertEquals("https://api-mifit.zepp.com/cdn/AGPS_ALM_2026.zip", fileUrl)
        val fileName = fileUrl?.substringAfterLast("/")
        assertEquals("AGPS_ALM_2026.zip", fileName)
    }

    @Test
    fun gpsHeadersMatchExpected() {
        val headers = ZeppConstants.gpsHeaders("token123", "req456")
        assertEquals("token123", headers["apptoken"])
        assertEquals("req456", headers["x-request-id"])
        assertEquals("false", headers["hm-privacy-ceip"])
        assertEquals("false", headers["hm-privacy-diagnostics"])
    }
}


class HelpersVectorTest {
    @Test
    fun encodeUint32Vectors() {
        assertTrue(encodeUint32(0).contentEquals(byteArrayOf(0, 0, 0, 0)))
        assertTrue(encodeUint32(1).contentEquals(byteArrayOf(1, 0, 0, 0)))
        assertTrue(encodeUint32(0x12345678).contentEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12)))
        assertTrue(
            encodeUint32(0xFFFFFFFF).contentEquals(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            ),
        )
    }

    @Test
    fun uihhVector() {
        val members = mapOf(
            "gps_alm.bin" to byteArrayOf(1, 2, 3),
            "gln_alm.bin" to byteArrayOf(4, 5),
            "lle_bds.lle" to byteArrayOf(6),
            "lle_gps.lle" to byteArrayOf(7, 8),
            "lle_glo.lle" to byteArrayOf(9),
            "lle_gal.lle" to byteArrayOf(10, 11, 12),
            "lle_qzss.lle" to byteArrayOf(13),
        )
        val expected = "55494848040000000000000195633e9a000000000000530000000000000000000105030000001d80bc55010203010f020000007423df550405018601000000b84a613b060187020000000a0c430007080188010000002957deab0901890300000024c994180a0b0c018a010000003093b3ac0d"
        assertEquals(expected, buildGpsUihh(members).toHex())
    }

    @Test
    fun uihhMissingMemberThrows() {
        assertFailsWith<HuamiTokenError> { buildGpsUihh(emptyMap()) }
    }
}

class ZipVectorTest {
    private val storedHex = "504b0304140000000000cbbd325d480383a3030000000300000005000000612e747874414243504b01021403140000000000cbbd325d480383a30300000003000000050000000000000000000000800100000000612e747874504b0506000000000100010033000000260000000000"
    private val deflatedHex = "504b0304140000000800cbbd325d406a108e1d000000b80100000900000068656c6c6f2e747874f348cdc9c95748494dcb492c494d5128cf2fca495154f018151de4a200504b0304140000000800cbbd325d00000000020000000000000009000000656d7074792e62696e0300504b01021403140000000800cbbd325d406a108e1d000000b801000009000000000000000000000080010000000068656c6c6f2e747874504b01021403140000000800cbbd325d000000000200000000000000090000000000000000000000800144000000656d7074792e62696e504b050600000000020002006e0000006d0000000000"

    @Test
    fun storedEntry() {
        assertEquals("ABC", ZipReader(hexToBytes(storedHex)).read("a.txt").decodeToString())
    }

    @Test
    fun deflatedEntries() {
        val zip = ZipReader(hexToBytes(deflatedHex))
        assertEquals("Hello deflated world! ".repeat(20), zip.read("hello.txt").decodeToString())
        assertEquals(0, zip.read("empty.bin").size)
    }

    @Test
    fun missingEntryThrows() {
        assertFailsWith<HuamiTokenError> { ZipReader(hexToBytes(storedHex)).read("nope.txt") }
    }
}

class ErrorContractTest {
    @Test
    fun unauthenticatedAccessorsThrow() {
        val engine = HttpEngine()
        val zepp = ZeppSession("u", "p", engine)
        assertFailsWith<AuthenticationError> { zepp.appToken }
        assertFailsWith<AuthenticationError> { zepp.userId }
        assertFailsWith<AuthenticationError> { zepp.loginToken }
        val xiaomi = XiaomiSession("u", "p", engine)
        assertFailsWith<AuthenticationError> { xiaomi.ssecurity }
        assertFailsWith<AuthenticationError> { xiaomi.serviceToken }
        assertFailsWith<AuthenticationError> { xiaomi.userId }
        assertFailsWith<AuthenticationError> { xiaomi.cUserId }
    }
}
