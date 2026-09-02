package channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import model.PayChannel
import neton.security.digest.Md5

/**
 * 四方协议的三处细节：签名（**大写** MD5）、状态映射、以及一套实现服务多家平台。
 *
 * 这些错了都不抛异常，只会表现为"平台一直说签名错误"或"订单永远不到账"，排查成本极高。
 */
class SifangPayPlatformTest {

    private val qijiu = SifangPayPlatform(SifangPayPlatform.QIJIU, neton.http.testkit.ScriptedHttpClient())

    private fun channel(code: String = "qijiu_alipay_1", productId: String = "ALIPAY_WAP") = PayChannel(
        code = code,
        platformCode = SifangPayPlatform.QIJIU,
        method = "ALIPAY",
        platformChannelId = productId,
        config = """{"apiUrl":"https://pay.test","mchId":"M1","key":"SECRET"}""",
    )

    /** 按协议手算：排除空值与 sign、按键升序、接 key=、MD5 后转大写。 */
    private fun sign(vararg pairs: Pair<String, String>): String =
        Md5.hex(pairs.sortedBy { it.first }.joinToString("") { "${it.first}=${it.second}&" } + "key=SECRET")
            .uppercase()

    /** 签名必须是大写：小写是另一家四方(Chiming)的规则，抄错了平台会一直回签名错误。 */
    @Test
    fun signatureIsUppercase() {
        val s = sign("mchOrderNo" to "m-1", "status" to "2")
        assertEquals(s.uppercase(), s)
        assertTrue(s.none { it.isLowerCase() })
    }

    @Test
    fun acceptsNotifyWithValidSign() = runBlocking {
        val params = mapOf(
            "mchOrderNo" to "m-1", "status" to "2",
            "channelOrderNo" to "CH-9", "paySuccTime" to "1700000000000",
        )
        val signed = params + ("sign" to sign(*params.map { it.key to it.value }.toTypedArray()))
        val r = qijiu.parseNotify(PlatformNotifyRequest(params = signed), channel())
        assertEquals("m-1", r?.merchantOrderId)
        assertEquals("CH-9", r?.platformOrderNo)
        assertEquals(PlatformOrderState.SUCCESS, r?.state)
        assertEquals(1700000000000L, r?.paidAt)
    }

    @Test
    fun rejectsNotifyWithBadSign() = runBlocking {
        val params = mapOf("mchOrderNo" to "m-1", "status" to "2", "sign" to "DEADBEEF")
        assertNull(
            qijiu.parseNotify(PlatformNotifyRequest(params = params), channel()),
            "验签失败必须拒绝：回调地址公开，伪造一条就是白送一笔货",
        )
    }

    /** 0/1=待支付，2/3=成功，其余（含未知码）一律关闭，绝不能把未知误判成已付。 */
    @Test
    fun mapsPlatformStates() = runBlocking {
        suspend fun stateOf(status: String): PlatformOrderState? {
            val p = mapOf("mchOrderNo" to "m-1", "status" to status)
            val signed = p + ("sign" to sign(*p.map { it.key to it.value }.toTypedArray()))
            return qijiu.parseNotify(PlatformNotifyRequest(params = signed), channel())?.state
        }
        assertEquals(PlatformOrderState.WAITING, stateOf("0"))
        assertEquals(PlatformOrderState.WAITING, stateOf("1"))
        assertEquals(PlatformOrderState.SUCCESS, stateOf("2"))
        assertEquals(PlatformOrderState.SUCCESS, stateOf("3"))
        assertEquals(PlatformOrderState.CLOSED, stateOf("4"))
        assertEquals(PlatformOrderState.CLOSED, stateOf("99"))
    }

    @Test
    fun nonSuccessHasNoPaidAt() = runBlocking {
        val p = mapOf("mchOrderNo" to "m-1", "status" to "1")
        val signed = p + ("sign" to sign(*p.map { it.key to it.value }.toTypedArray()))
        assertNull(qijiu.parseNotify(PlatformNotifyRequest(params = signed), channel())?.paidAt)
    }

    /** 一套实现服务多家平台：七九/至尊/文腾只是 platformCode 不同。 */
    @Test
    fun onePlatformClassServesManyProviders() {
        val registry = PayPlatformRegistry.default(neton.http.testkit.ScriptedHttpClient())
        for (code in SifangPayPlatform.KNOWN_CODES) {
            assertEquals(code, registry.platform(code)?.platformCode, "未登记四方平台: $code")
            assertEquals(false, registry.platform(code)?.sandbox)
        }
    }
}
