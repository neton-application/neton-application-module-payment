package logic.crypto

import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class BankCardCryptoTest {

    private fun crypto(): BankCardCrypto {
        val masterKeyB64 = Base64.encode(ByteArray(32) { (it + 1).toByte() })
        val provider = EnvWalletCryptoKeyProvider(masterKeyB64)
        val hmacKey = ByteArray(32) { (it + 7).toByte() }
        return BankCardCrypto(provider, hmacKey)
    }

    @Test
    fun envelope_round_trips_and_masks() = runBlocking {
        val c = crypto()
        val enc = c.encrypt("6225 7600 1234 5678")
        assertEquals("**** **** **** 5678", enc.masked)
        assertTrue(enc.ciphertextBase64.isNotBlank())
        assertTrue(enc.wrappedDataKeyBase64.isNotBlank())
        // 密文里不能出现明文片段
        assertFalse(enc.ciphertextBase64.contains("6225"))
        // 解密还原（去掉空格后的规范化卡号）
        val full = c.decrypt(enc.ciphertextBase64, enc.wrappedDataKeyBase64)
        assertEquals("6225760012345678", full)
    }

    @Test
    fun hash_is_deterministic_for_dedup() {
        val c = crypto()
        assertEquals(c.hashOf("6225 7600 1234 5678"), c.hashOf("6225-7600-1234-5678"))
    }

    @Test
    fun two_encryptions_differ_but_both_decrypt() = runBlocking {
        val c = crypto()
        val a = c.encrypt("6225760012345678")
        val b = c.encrypt("6225760012345678")
        // 每次随机 data key + 随机 iv → 密文不同（语义安全）
        assertTrue(a.ciphertextBase64 != b.ciphertextBase64)
        assertEquals("6225760012345678", c.decrypt(a.ciphertextBase64, a.wrappedDataKeyBase64))
        assertEquals("6225760012345678", c.decrypt(b.ciphertextBase64, b.wrappedDataKeyBase64))
    }

    @Test
    fun invalid_card_rejected() = runBlocking {
        val c = crypto()
        assertFailsWith<IllegalArgumentException> { c.encrypt("abc") }
        Unit
    }
}
