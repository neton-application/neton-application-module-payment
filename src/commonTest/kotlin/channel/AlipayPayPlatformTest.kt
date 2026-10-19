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
 *
 * 密钥对见 [AlipayTestKeys]：注册表布线那边的测试要用同一对，
 * 否则两份副本各说各话，漂移了也看不出来。
 */
class AlipayPayPlatformTest {

    private val privateKey = AlipayTestKeys.PRIVATE_KEY
    private val publicKey = AlipayTestKeys.PUBLIC_KEY
    private val platform = AlipayPayPlatform(neton.http.testkit.ScriptedHttpClient())

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
            "app_id" to "2021000000000000", "out_trade_no" to "m-1",
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

    /** 实付金额按分带回：入账前要与订单金额核对，不能只看「交易成功」。元转分不能走浮点。 */
    @Test
    fun notifyCarriesPaidAmountInCents() = runBlocking {
        for ((yuan, cents) in listOf("12.34" to 1234L, "0.1" to 10L, "5" to 500L, "0.07" to 7L)) {
            val params = mapOf(
                "app_id" to "2021000000000000", "out_trade_no" to "m-1", "trade_no" to "T1",
                "trade_status" to "TRADE_SUCCESS", "total_amount" to yuan,
            )
            val r = platform.parseNotify(PlatformNotifyRequest(params = params + ("sign" to signOf(params))), channel())
            assertEquals(cents, r?.paidAmount, "total_amount=$yuan")
        }
    }

    /** 金额格式不合法时不猜：返回 null，由入账处按「金额未知」拒绝。 */
    @Test
    fun malformedAmountIsUnknown() = runBlocking {
        for (bad in listOf("12.345", "1e3", "-1.00", "", "abc")) {
            val params = mapOf("app_id" to "2021000000000000", "out_trade_no" to "m-1", "trade_status" to "TRADE_SUCCESS", "total_amount" to bad)
            val r = platform.parseNotify(PlatformNotifyRequest(params = params + ("sign" to signOf(params))), channel())
            assertNull(r?.paidAmount, "total_amount=[$bad]")
        }
    }

    /** 别的应用的回调（同一个支付宝公钥体系下签名同样有效）不能入本渠道的账。 */
    @Test
    fun rejectsNotifyForAnotherApp() = runBlocking {
        val params = mapOf(
            "app_id" to "2021999999999999", "out_trade_no" to "m-1", "trade_no" to "T1",
            "trade_status" to "TRADE_SUCCESS", "total_amount" to "12.34",
        )
        assertNull(platform.parseNotify(PlatformNotifyRequest(params = params + ("sign" to signOf(params))), channel()))
    }

    /** time_expire 用东八区时间：UTC 02:30 是北京时间 10:30。 */
    @Test
    fun expireTimeIsFormattedInBeijingTime() {
        // 2026-08-15 02:30:00 UTC
        assertEquals("2026-08-15 10:30:00", platform.formatAlipayTime(1786761000000L))
        assertEquals("2026-01-01 08:00:00", platform.formatAlipayTime(1767225600000L))
    }

    /** sign_type 必须排除在待签串外，否则永远验不过 —— 支付宝的规定。 */
    @Test
    fun signTypeIsExcludedFromSigningString() = runBlocking {
        val params = mapOf("app_id" to "2021000000000000", "out_trade_no" to "m-2", "trade_status" to "TRADE_SUCCESS")
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
            "app_id" to "2021000000000000", "out_trade_no" to "m-4",
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
        val params = mapOf("app_id" to "2021000000000000", "out_trade_no" to "m-5", "trade_status" to "SOMETHING_NEW")
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
