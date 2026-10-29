package logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import neton.core.http.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 支付通道配置里的密钥只写不读：后台看到的是 ******，保存时 ****** / 留空 / 不写都沿用原值。 */
class PayChannelSecretsTest {

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private fun field(config: String, key: String) = obj(config)[key]?.jsonPrimitive?.content

    @Test fun secretFieldNames() {
        listOf("privateKey", "key", "apiV3Key", "appSecret", "mch_key", "password", "accessToken", "PRIVATE_KEY").forEach {
            assertTrue(PayChannelSecrets.isSecret(it), it)
        }
        listOf("alipayPublicKey", "publicKey", "appId", "mchId", "serverUrl", "apiUrl", "keyId").forEach {
            assertFalse(PayChannelSecrets.isSecret(it), it)
        }
    }

    @Test fun maskHidesSecretsAndKeepsTheRest() {
        val masked = PayChannelSecrets.mask("""{"serverUrl":"https://openapi.alipay.com/gateway.do","appId":"2021","privateKey":"MIIE-real","alipayPublicKey":"MIIB-pub"}""")
        assertEquals(PayChannelSecrets.MASK, field(masked, "privateKey"))
        assertEquals("2021", field(masked, "appId"))
        assertEquals("MIIB-pub", field(masked, "alipayPublicKey"))
        assertFalse("MIIE-real" in masked)
    }

    @Test fun maskNeverEchoesUnparseableConfig() {
        assertEquals(PayChannelSecrets.MASK, PayChannelSecrets.mask("appId=1&key=real"))
        assertEquals("{}", PayChannelSecrets.mask("{}"))
        assertEquals("", PayChannelSecrets.mask(""))
    }

    @Test fun mergeKeepsStoredSecretWhenMaskedBlankOrOmitted() {
        val stored = """{"apiUrl":"https://pay.example.com","mchId":"m1","key":"real-key"}"""
        listOf(
            """{"apiUrl":"https://pay.example.com","mchId":"m2","key":"******"}""",
            """{"apiUrl":"https://pay.example.com","mchId":"m2","key":""}""",
            """{"apiUrl":"https://pay.example.com","mchId":"m2"}""",
        ).forEach { submitted ->
            val merged = PayChannelSecrets.merge(submitted, stored)
            assertEquals("real-key", field(merged, "key"), submitted)
            assertEquals("m2", field(merged, "mchId"), submitted)
        }
    }

    @Test fun mergeReplacesSecretWhenANewValueIsGiven() {
        val merged = PayChannelSecrets.merge("""{"mchId":"m1","key":"new-key"}""", """{"mchId":"m1","key":"old-key"}""")
        assertEquals("new-key", field(merged, "key"))
    }

    @Test fun wholeMaskedConfigKeepsStoredConfig() {
        assertEquals("appId=1&key=real", PayChannelSecrets.merge(PayChannelSecrets.MASK, "appId=1&key=real"))
    }

    @Test fun createCannotSubmitTheMask() {
        val e = assertFailsWith<ValidationException> { PayChannelSecrets.merge("""{"mchId":"m1","key":"******"}""", null) }
        assertEquals(PaymentErrorCodes.PAY_CHANNEL_SECRET_REQUIRED, e.message)
        assertFailsWith<ValidationException> { PayChannelSecrets.merge(PayChannelSecrets.MASK, null) }
    }
}
