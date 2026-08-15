package channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import model.PayChannel
import model.PayOrder

/**
 * 平台抽象的两条约束。
 *
 * 一是沙箱标记默认 false：将来接汇付天下 / 七九时，实现方即使完全不知道 sandbox 这回事，
 * 拿到的也是"真实平台"语义。若默认成 true，新接的平台会在非沙箱模式被静默拒绝 ——
 * 表现为"新平台怎么都下不了单"，很难查。
 *
 * 二是**同一平台可承载多条通道**：这正是平台与通道分离的目的，
 * 加一条「支付宝2」应该是加一行数据，而不是加一个实现类。
 */
class PayPlatformSandboxTest {

    /** 模拟一家真实支付公司：只实现必需成员，不碰 sandbox / queryOrder / ackBody。 */
    private class RealPlatform : PayPlatform {
        override val platformCode = "huifu"
        override suspend fun prepay(order: PayOrder, channel: PayChannel) = PrepayResult(
            displayMode = PayDisplayMode.REDIRECT_URL,
            payload = "https://example.test/pay/${channel.platformChannelId}",
            platformOrderNo = "NO-1",
        )
        override suspend fun parseNotify(
            request: PlatformNotifyRequest,
            channel: PayChannel,
        ): PlatformNotifyResult? = null
    }

    private fun channel(code: String, platformChannelId: String) = PayChannel(
        code = code, platformCode = "huifu", method = "ALIPAY",
        platformChannelId = platformChannelId, config = "{}",
    )

    @Test
    fun realPlatformIsNotSandboxByDefault() {
        assertFalse(RealPlatform().sandbox, "真实平台默认必须是非沙箱")
    }

    @Test
    fun sandboxPlatformDeclaresItself() {
        assertTrue(SandboxPayPlatform().sandbox, "沙箱平台必须自报")
    }

    /** 同一平台实现服务多条通道，各自带自己的平台侧编号。 */
    @Test
    fun onePlatformServesManyChannels() = runBlocking {
        val platform = RealPlatform()
        val order = PayOrder(merchantOrderId = "m-1", subject = "s", price = 100)
        val first = platform.prepay(order, channel("alipay_1", "CH-A1"))
        val second = platform.prepay(order, channel("alipay_2", "CH-A2"))
        assertTrue(first.payload.endsWith("CH-A1"))
        assertTrue(second.payload.endsWith("CH-A2"))
    }

    @Test
    fun registryResolvesByPlatformCode() {
        val registry = PayPlatformRegistry(listOf(RealPlatform(), SandboxPayPlatform()))
        assertEquals("huifu", registry.platform("huifu")?.platformCode)
        assertTrue(registry.platform("sandbox")!!.sandbox)
        assertEquals(null, registry.platform("unknown"))
    }

    /** 未实现查单的平台返回 null，让对账任务跳过而不是拿到假答案。 */
    @Test
    fun queryOrderIsOptional() = runBlocking {
        assertEquals(null, RealPlatform().queryOrder("any", channel("alipay_1", "CH-A1")))
    }

    @Test
    fun ackBodyHasUsableDefault() {
        assertEquals("success", RealPlatform().ackBody(true))
        assertEquals("fail", RealPlatform().ackBody(false))
    }
}
