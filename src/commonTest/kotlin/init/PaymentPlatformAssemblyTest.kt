package init

import channel.AlipayPayPlatform
import channel.AlipayTestKeys
import channel.PayCallbackHooks
import channel.PayEndpointForbiddenException
import channel.SifangPayPlatform
import channel.payNotifyUrlOf
import kotlinx.coroutines.runBlocking
import logic.PaymentErrorCodes
import model.PayChannel
import model.PayOrder
import neton.core.component.NetonContext
import neton.core.component.NetonLifecycle
import neton.core.http.HttpHeaders
import neton.core.http.NetonErrorCode
import neton.http.client.EgressSettings
import neton.http.client.GuardedHttpClientProvider
import neton.http.client.HttpClientBody
import neton.http.client.HttpClientConfig
import neton.http.client.HttpClientError
import neton.http.client.HttpClientException
import neton.http.client.HttpClientMethod
import neton.http.client.HttpClientProvider
import neton.http.client.HttpClientResponse
import neton.http.client.TargetRejection
import neton.http.testkit.ScriptedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 启动装配：支付平台注册表只由 payment 用运维出站策略的受控客户端建，回调 hook 接到平台上，客户端随进程关闭；
 * 平台网关地址被策略拒绝时报 PAY_ENDPOINT_FORBIDDEN，而不是和网络失败一样静默变成「无响应」。
 */
class PaymentPlatformAssemblyTest {

    private val order = PayOrder(merchantOrderId = "m-egress-1", subject = "充值", price = 990)

    private fun sifangChannel(apiUrl: String = "https://pay.test") = PayChannel(
        code = "qijiu_alipay_1", platformCode = SifangPayPlatform.QIJIU, method = "ALIPAY", platformChannelId = "ALIPAY_WAP",
        config = """{"apiUrl":"$apiUrl","mchId":"M1","key":"SECRET"}""",
    )

    private fun alipayChannel(serverUrl: String) = PayChannel(
        code = "alipay_wap_1", platformCode = AlipayPayPlatform.PLATFORM_CODE, method = "ALIPAY", platformChannelId = "WAP",
        config = "{\"appId\":\"2021000000000000\",\"serverUrl\":\"$serverUrl\",\"privateKey\":\"" + AlipayTestKeys.PRIVATE_KEY +
            "\",\"alipayPublicKey\":\"" + AlipayTestKeys.PUBLIC_KEY + "\"}",
    )

    /** 受控客户端的底层替身：记下 payment 建客户端时的配置，返回 [client]。 */
    private class Recording(val client: ScriptedHttpClient) {
        val configs = mutableListOf<HttpClientConfig>()
        val guarded = GuardedHttpClientProvider(EgressSettings(), HttpClientProvider { block ->
            configs += HttpClientConfig().apply(block)
            client
        })
    }

    private fun refusing(reason: TargetRejection) = ScriptedHttpClient().apply {
        on(HttpClientMethod.Post, "http") { throw HttpClientException(HttpClientError.TargetRejected(reason, "'pay.test' resolves to non-public address 10.0.0.8")) }
    }

    @Test
    fun withoutTheEgressProviderStartupFails() {
        val e = assertFailsWith<IllegalStateException> { paymentPlatformRegistry(NetonContext(emptyArray())) }
        assertTrue("GuardedHttpClientProvider" in e.message.orEmpty(), e.message)
    }

    @Test
    fun platformsUseTheGuardedClientAndTheBoundCallbackHooks() = runBlocking {
        val http = ScriptedHttpClient().on(HttpClientMethod.Post, "https://pay.test/api/pay/unifiedorder") {
            HttpClientResponse(200, HttpHeaders.EMPTY, """{"retCode":"SUCCESS","payParams":{"payUrl":"https://pay.test/cashier/1"}}""")
        }
        val rec = Recording(http)
        val ctx = NetonContext(emptyArray()).apply {
            bind(GuardedHttpClientProvider::class, rec.guarded)
            bind(PayCallbackHooks::class, PayCallbackHooks(notifyUrlOf = payNotifyUrlOf("https://api.example.com")))
        }
        val registry = paymentPlatformRegistry(ctx)

        // 一个平台客户端，带的正是运维策略
        assertSame(rec.guarded.policy, rec.configs.single().addressPolicy)
        registry.platform(SifangPayPlatform.QIJIU)!!.prepay(order, sifangChannel())
        val body = (http.recorded.single().body as HttpClientBody.Json).text
        assertTrue("\"notifyUrl\":\"https://api.example.com/app/pay/channel-notify/qijiu_alipay_1\"" in body, body)

        // 客户端登记了随进程关闭（同名再登记会被拒）
        assertFailsWith<IllegalStateException> {
            ctx.lifecycle.register(PLATFORM_HTTP_LIFECYCLE, object : NetonLifecycle {
                override suspend fun start() = Unit
                override suspend fun stop() = Unit
            })
        }
        Unit
    }

    @Test
    fun aRefusedPlatformEndpointIsPayEndpointForbidden() = runBlocking {
        val ctx = NetonContext(emptyArray()).apply { bind(GuardedHttpClientProvider::class, Recording(refusing(TargetRejection.ADDRESS)).guarded) }
        val registry = paymentPlatformRegistry(ctx)
        val sifang = registry.platform(SifangPayPlatform.QIJIU)!!

        val prepay = assertFailsWith<PayEndpointForbiddenException> { sifang.prepay(order, sifangChannel("http://10.0.0.8")) }
        assertEquals(PaymentErrorCodes.PAY_ENDPOINT_FORBIDDEN, prepay.message)
        assertEquals(NetonErrorCode.SERVICE_UNAVAILABLE, prepay.code)
        // 原因与地址只给服务端日志，不进对外 message
        assertEquals(TargetRejection.ADDRESS, prepay.reason)
        assertTrue("10.0.0.8" in prepay.detail && "10.0.0.8" !in prepay.message)

        assertFailsWith<PayEndpointForbiddenException> { sifang.queryOrder("m-egress-1", sifangChannel("http://10.0.0.8")) }
        val alipay = registry.platform(AlipayPayPlatform.PLATFORM_CODE)!!
        val query = assertFailsWith<PayEndpointForbiddenException> {
            alipay.queryOrder("m-egress-1", alipayChannel("http://169.254.169.254/gateway.do"))
        }
        assertEquals(AlipayPayPlatform.PLATFORM_CODE, query.platformCode)
    }

    @Test
    fun aNetworkFailureIsStillAnOrdinaryFailedCall() = runBlocking {
        val down = ScriptedHttpClient().apply {
            on(HttpClientMethod.Post, "https://pay.test") { throw HttpClientException(HttpClientError.Network("connection refused", null)) }
        }
        val ctx = NetonContext(emptyArray()).apply { bind(GuardedHttpClientProvider::class, Recording(down).guarded) }
        val sifang = paymentPlatformRegistry(ctx).platform(SifangPayPlatform.QIJIU)!!
        assertNull(sifang.queryOrder("m-egress-1", sifangChannel()))
    }
}
