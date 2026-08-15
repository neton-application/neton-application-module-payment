package channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import model.PayChannel
import model.PayOrder
import neton.security.crypto.RsaSha256

/**
 * 支付宝对接的易错点：待签串的构造、回调验签必须排除 sign/sign_type、金额分转元。
 *
 * 这些错了支付宝只回一句"验签失败"，不会告诉你差在哪 —— 用真实密钥对钉死。
 */
class AlipayPayPlatformTest {

    private val privateKey = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCq9RtsUYAmTL/mjJ+84p6IU5dYT030twPgk9fjzIE8cyj1PteNEag0kg+Aedgezj6XNbeLaR++DWont7uln3qfqmpCpvl1rXbsUcabkyIgVoL4eMY93xt/FrNIrqSymvX2wkxFQd5j2CkOewiM8NCTufXoEd8vLbZMRiNNg5rALUpumUYk/gDxJPSfYHhfh6kolvCrPIHB1DzW52Vb+WPxz+bCFoo7hcW8SfnieKxA1jgY/H/wMCAg9TV3GdiYjzvuM/AlbZJCFs/AETFJubnAoiSKgbKspSaD5OT5paH/J+IwrJyuOyBqgubOdDgmocg43s4pEeDWPavy44yJ6avBAgMBAAECggEAT8PBIc79DeGtf/KI7WaHNXBbIxcNdmqV4ojYqC7Y9c19hL/nbqiYZL7pgLZZAjaUuZSUqPVJnDFCIHn3kZVRb4HhxmuF5UQkQqr9EcWanKAAx9ICHQgmGiwLRpRFwBfRP2r0jzPmgYtvzJPXL3uEtgiEFd2Q1sBrWDc5bYdEAvnat8KJHxq2wfeuXXYPtyCUtTmlRdlcDkPIVBfMhqxj/z7TanWwRg4ukuOQFYZLdQgfSMCF0Fyn2REKunczN7v4LJNYHPRIgHPfvWujA1+GBFCKdmWjsPA0MCBCL1tWXK2HmkR7pbhMxp1Q9C7T8Kzj0cu0sd5AGIZN9m7Z5WKxSQKBgQDltlH2on+flqqjwS1E4t+9orilDW+GaWfJD5HFzNYDnndc+3P/r+eUGDMECqZyaXMhSg/MEq9mkH2URIuW6KGb+0otZrasYIHtnmoknSWk0yctxd1+L2V63Jd19s+XnXWqZv3Us7TDmEaMwVuoP9QSPRaOWcbE/dflAjWdXH+V9wKBgQC+hYAD7heoF2W8EhYXD/N0MImiGo4xP2jMDe0m4XYJPiQXGg+004emsJRhyG4jcYULHRZoRm8Xc1vRyvo94OUJLkZdyuJR5BcM0WdI070CZLGCdLzm2+MVSgA1GLl+iPqIfvQJC1pjdgI4UOlGnfF0jwiTapp51wm204EkIwt+BwKBgQDBKGIbheDTDRpHwHSUbEG/cEjbYUTaPV/sDY+CSA/d0y6DnV2ZLw0H1qFvUJVNt6X75A8Mhtm+4Nj4B/to1gyu4MsrCiepIy2d5YtTZmD1DCjxsGPja29ltIAXzYYZ82mx9BCU/teNcUpBqYWtIJ7vBzckVBF0LA+Snhz/SXxvWQKBgCfxfUFVrYgEP8QKVq9HHNeDRZfC0YTpsmL1mH7KTiDp8k8Vm61hm9MKulE14EF2D1qhIo2CFtBn0xxM3eITQHGITiBj5MceduatEGZoXfweeEjNiL0t5JIWDa0UHe+1cDElzKwIwU6Q8y4zaHTxsCmrwzSE6RYaS2MVPMICxuoJAoGBALfZhx6WQ2ewP96PixtSAb5GjZRK+uOZj/BV7u/wUHfgxpdVFONPpj4+rkRNZXucVUBmVdpkVKaBllb5pZLRUX8OSVnP9bCeWVfZ5X8GzYwntmNUsIxfVJj1IBylyRu50nf6GQRY0axI1yMnZICmLZUDprXDR2K3msjwGmdJOD9z"
    private val publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqvUbbFGAJky/5oyfvOKeiFOXWE9N9LcD4JPX48yBPHMo9T7XjRGoNJIPgHnYHs4+lzW3i2kfvg1qJ7e7pZ96n6pqQqb5da127FHGm5MiIFaC+HjGPd8bfxazSK6kspr19sJMRUHeY9gpDnsIjPDQk7n16BHfLy22TEYjTYOawC1KbplGJP4A8ST0n2B4X4epKJbwqzyBwdQ81udlW/lj8c/mwhaKO4XFvEn54nisQNY4GPx/8DAgIPU1dxnYmI877jPwJW2SQhbPwBExSbm5wKIkioGyrKUmg+Tk+aWh/yfiMKycrjsgaoLmznQ4JqHION7OKRHg1j2r8uOMiemrwQIDAQAB"
    private val platform = AlipayPayPlatform()

    private fun channel() = PayChannel(
        code = "alipay_wap_1",
        platformCode = AlipayPayPlatform.PLATFORM_CODE,
        method = "ALIPAY",
        platformChannelId = "WAP",
        config = "{\"appId\":\"2021000000000000\",\"privateKey\":\"" + privateKey +
            "\",\"alipayPublicKey\":\"" + publicKey + "\"}",
    )

    /** 待签串：排除空值与 sign，按键升序，`k=v&` 拼接，**不做 URL 编码**。 */
    private fun signOf(params: Map<String, String>): String =
        RsaSha256.signBase64(
            privateKey,
            params.entries.filter { it.value.isNotBlank() }.sortedBy { it.key }
                .joinToString("&") { it.key + "=" + it.value }
                .encodeToByteArray(),
        )

    @Test
    fun acceptsNotifyWithValidSign() = runBlocking {
        val params = mapOf(
            "out_trade_no" to "m-1",
            "trade_no" to "2024TRADE01",
            "trade_status" to "TRADE_SUCCESS",
            "gmt_payment" to "2026-08-15 10:30:00",
        )
        val signed = params + mapOf("sign" to signOf(params), "sign_type" to "RSA2")
        val r = platform.parseNotify(PlatformNotifyRequest(params = signed), channel())
        assertEquals("m-1", r?.merchantOrderId)
        assertEquals("2024TRADE01", r?.platformOrderNo)
        assertEquals(PlatformOrderState.SUCCESS, r?.state)
        assertTrue((r?.paidAt ?: 0L) > 0L, "成功态必须带支付时间")
    }

    /** sign_type 必须排除在待签串外，否则永远验不过 —— 支付宝的规定。 */
    @Test
    fun signTypeIsExcludedFromSigningString() = runBlocking {
        val params = mapOf("out_trade_no" to "m-2", "trade_status" to "TRADE_SUCCESS")
        val wrong = RsaSha256.signBase64(
            privateKey,
            (params + ("sign_type" to "RSA2")).entries.sortedBy { it.key }
                .joinToString("&") { it.key + "=" + it.value }.encodeToByteArray(),
        )
        val signed = params + mapOf("sign" to wrong, "sign_type" to "RSA2")
        assertNull(platform.parseNotify(PlatformNotifyRequest(params = signed), channel()))
    }

    @Test
    fun rejectsBadSignature() = runBlocking {
        val params = mapOf("out_trade_no" to "m-3", "trade_status" to "TRADE_SUCCESS", "sign" to "AAAA")
        assertNull(
            platform.parseNotify(PlatformNotifyRequest(params = params), channel()),
            "验签失败必须拒绝：回调地址公开，伪造一条就是白送一笔货",
        )
    }

    /** 有退款金额即视为已退款 —— 支付宝没有独立的"退款成功"交易状态。 */
    @Test
    fun refundFeePresentMeansRefunded() = runBlocking {
        val params = mapOf(
            "out_trade_no" to "m-4",
            "trade_status" to "TRADE_SUCCESS",
            "refund_fee" to "9.90",
        )
        val signed = params + mapOf("sign" to signOf(params), "sign_type" to "RSA2")
        val r = platform.parseNotify(PlatformNotifyRequest(params = signed), channel())
        assertEquals(PlatformOrderState.REFUNDED, r?.state)
        assertNull(r?.paidAt, "非成功态不得带支付时间")
    }

    /** 未知交易状态按关闭处理，绝不能误判成已支付。 */
    @Test
    fun unknownTradeStatusIsClosed() = runBlocking {
        val params = mapOf("out_trade_no" to "m-5", "trade_status" to "SOMETHING_NEW")
        val signed = params + mapOf("sign" to signOf(params), "sign_type" to "RSA2")
        assertEquals(
            PlatformOrderState.CLOSED,
            platform.parseNotify(PlatformNotifyRequest(params = signed), channel())?.state,
        )
    }

    /** WAP 下单是页面接口：不发 HTTP 请求，直接产出带签名的跳转地址。 */
    @Test
    fun prepayBuildsSignedRedirectUrl() = runBlocking {
        val order = PayOrder(merchantOrderId = "m-6", subject = "会员", price = 1990)
        val r = platform.prepay(order, channel())
        assertEquals(PayDisplayMode.REDIRECT_URL, r.displayMode)
        assertTrue(r.payload.startsWith("https://openapi.alipay.com/gateway.do?"))
        assertTrue(r.payload.contains("sign="), "跳转地址必须带签名")
        assertTrue(r.payload.contains("alipay.trade.wap.pay"))
    }

    @Test
    fun isNotSandbox() {
        assertEquals(false, platform.sandbox)
    }
}
