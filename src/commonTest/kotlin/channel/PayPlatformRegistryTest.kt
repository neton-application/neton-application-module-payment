package channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import model.PayChannel
import model.PayOrder
import neton.core.http.HttpHeaders
import neton.http.client.HttpClientBody
import neton.http.client.HttpClientMethod
import neton.http.client.HttpClientResponse
import neton.http.testkit.ScriptedHttpClient

/**
 * [PayPlatformRegistry.default] 的两个回调 hook **真的接到了每个平台上**。
 *
 * 这件事必须单独测，因为漏接是彻底静默的：hook 有默认值，装配层传了、
 * 平台构造函数少写一个实参，编译不报错、下单也照样成功，
 * 只是渠道永远不回调 —— 表现是「用户付了钱、订单一直待支付」。
 *
 * 两家的证据在**不同地方**，这也是为什么不能用一个测试糊过去：
 * - 支付宝 WAP 是页面接口，不发 HTTP，payload 那串跳转地址就是全部证据；
 * - 四方要真发 POST，证据在 ScriptedHttpClient 录下来的**请求体**里。
 */
class PayPlatformRegistryTest {

    private companion object {
        /** 后端公网基址：渠道服务器要能从这里访问到我们的回调路由。 */
        const val BASE = "https://pay.example.com"

        /** 用户付完跳回的控制台账单页。 */
        const val RETURN_URL = "https://console.example.com/billing"
    }

    private fun order() = PayOrder(merchantOrderId = "m-reg-1", subject = "额度充值", price = 1990)

    private fun alipayChannel() = PayChannel(
        code = "alipay_wap_1",
        platformCode = AlipayPayPlatform.PLATFORM_CODE,
        method = "ALIPAY",
        platformChannelId = "WAP",
        config = "{\"appId\":\"2021000000000000\",\"privateKey\":\"" + AlipayTestKeys.PRIVATE_KEY +
            "\",\"alipayPublicKey\":\"" + AlipayTestKeys.PUBLIC_KEY + "\"}",
    )

    private fun sifangChannel() = PayChannel(
        code = "qijiu_alipay_1",
        platformCode = SifangPayPlatform.QIJIU,
        method = "ALIPAY",
        platformChannelId = "ALIPAY_WAP",
        config = """{"apiUrl":"https://pay.test","mchId":"M1","key":"SECRET"}""",
    )

    /** 四方下单成功的响应外壳：`retCode == SUCCESS`，支付地址在 `payParams.payUrl`。 */
    private fun sifangHttp() = ScriptedHttpClient()
        .on(HttpClientMethod.Post, "https://pay.test/api/pay/unifiedorder") {
            HttpClientResponse(
                200,
                HttpHeaders.EMPTY,
                """{"retCode":"SUCCESS","payParams":{"payUrl":"https://pay.test/cashier/1"}}""",
            )
        }

    /** 从下单跳转地址里取出某个公共参数并解百分号编码。 */
    private fun param(payload: String, name: String): String? =
        payload.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.let { percentDecode(it) }

    /** 只处理 `%XX`：被测值全是 ASCII，而生产侧的 urlEncode 也不会用 `+` 表示空格。 */
    private fun percentDecode(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            if (value[i] == '%' && i + 2 < value.length) {
                append(value.substring(i + 1, i + 3).toInt(16).toChar())
                i += 3
            } else {
                append(value[i])
                i++
            }
        }
    }

    // ==================== 支付宝：证据在 payload ====================

    /**
     * 不传 hook 时**一个回调地址都不该出现**。
     *
     * 这条是反向守卫：修这个缺陷的方式不是把地址写死成某个猜测值，
     * 而是让「没配」保持原样 —— 写进一个错的绝对地址比不写更糟，
     * 渠道会往一个不属于我们的域名上打回调。
     */
    @Test
    fun withoutHooksAlipayGetsNoCallbackUrls() = runBlocking {
        val platform = PayPlatformRegistry.default(ScriptedHttpClient())
            .platform(AlipayPayPlatform.PLATFORM_CODE)!!
        val payload = platform.prepay(order(), alipayChannel()).payload
        assertNull(param(payload, "notify_url"), "没配基址时不得凭空写 notify_url")
        assertNull(param(payload, "return_url"), "没配基址时不得凭空写 return_url")
    }

    @Test
    fun defaultPassesBothHooksToAlipay() = runBlocking {
        val platform = PayPlatformRegistry.default(
            ScriptedHttpClient(),
            notifyUrlOf = payNotifyUrlOf(BASE),
            returnUrlOf = { RETURN_URL },
        ).platform(AlipayPayPlatform.PLATFORM_CODE)!!

        val payload = platform.prepay(order(), alipayChannel()).payload
        // 期望值写成字面量而不是调 payNotifyPath 再拼：那样断言会跟着实现一起漂
        assertEquals("$BASE/app/pay/channel-notify/alipay_wap_1", param(payload, "notify_url"))
        assertEquals(RETURN_URL, param(payload, "return_url"))
        // quit_url 是用户在 WAP 收银台点「返回」时去的地方，与 return_url 同源；
        // 少了它，用户中途放弃支付就停在支付宝的页面里回不来。
        assertTrue(
            param(payload, "biz_content").orEmpty().contains("\"quit_url\":\"$RETURN_URL\""),
            "biz_content 里该带 quit_url: ${param(payload, "biz_content")}",
        )
    }

    // ==================== 四方：证据在发出去的请求体 ====================

    @Test
    fun defaultPassesNotifyHookToSifang() = runBlocking {
        val http = sifangHttp()
        val platform = PayPlatformRegistry.default(http, notifyUrlOf = payNotifyUrlOf(BASE))
            .platform(SifangPayPlatform.QIJIU)!!
        platform.prepay(order(), sifangChannel())

        val body = (http.recorded.single().body as HttpClientBody.Json).text
        assertTrue(
            body.contains("\"notifyUrl\":\"$BASE/app/pay/channel-notify/qijiu_alipay_1\""),
            "四方平台没拿到回调地址: $body",
        )
    }

    @Test
    fun withoutHooksSifangGetsNoNotifyUrl() = runBlocking {
        val http = sifangHttp()
        val platform = PayPlatformRegistry.default(http).platform(SifangPayPlatform.QIJIU)!!
        platform.prepay(order(), sifangChannel())

        val body = (http.recorded.single().body as HttpClientBody.Json).text
        assertFalse(body.contains("notifyUrl"), "没配基址时不得凭空写 notifyUrl: $body")
    }

    /**
     * 加参数不能顺手改掉平台清单。
     *
     * 少登记一家的表现是那条渠道一律「不可用的支付通道」，而这条错误信息
     * 与「渠道 status 关了」「mock 开关状态不对」完全一样，排查时会往错方向找。
     */
    @Test
    fun passingHooksDoesNotChangeThePlatformLineup() {
        val bare = PayPlatformRegistry.default(ScriptedHttpClient()).codes()
        val hooked = PayPlatformRegistry.default(
            ScriptedHttpClient(),
            notifyUrlOf = payNotifyUrlOf(BASE),
            returnUrlOf = { RETURN_URL },
        ).codes()
        assertEquals(bare, hooked)
        assertTrue(SifangPayPlatform.KNOWN_CODES.all { it in hooked }, "四方平台没登记全: $hooked")
    }
}
