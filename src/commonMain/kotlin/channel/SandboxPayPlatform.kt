package channel

import model.PayChannel
import model.PayOrder
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 沙箱平台：把用户送到本地模拟收银台，回调不验签。
 *
 * 用来在没接真实平台时跑通「下单 → 支付 → 回调 → 业务解锁」，尤其是**失败路径**
 * —— 只测成功路径的系统，往往在上线当天才发现失败没处理。
 *
 * 它同样是"一个平台"，因此可以挂任意多条通道（沙箱-支付宝1、沙箱-微信1…），
 * 前端与真实环境走完全一样的下单流程。由全局设置 payment.mock.enabled 控制。
 */
class SandboxPayPlatform : PayPlatform {

    override val platformCode: String = PLATFORM_CODE

    override val sandbox: Boolean = true

    override suspend fun prepay(order: PayOrder, channel: PayChannel): PrepayResult = PrepayResult(
        displayMode = PayDisplayMode.REDIRECT_URL,
        payload = "/app/pay/mock/checkout" +
            "?merchantOrderId=${order.merchantOrderId}&channelCode=${channel.code}",
        platformOrderNo = "SANDBOX-${channel.code}-${order.merchantOrderId}",
    )

    @OptIn(ExperimentalTime::class)
    override suspend fun parseNotify(
        request: PlatformNotifyRequest,
        channel: PayChannel,
    ): PlatformNotifyResult? {
        val merchantOrderId = request.params["merchantOrderId"] ?: return null
        val paid = request.params["status"].let { it == "success" || it == "1" || it == null }
        return PlatformNotifyResult(
            merchantOrderId = merchantOrderId,
            platformOrderNo = request.params["platformOrderNo"]
                ?: "SANDBOX-${channel.code}-$merchantOrderId",
            state = if (paid) PlatformOrderState.SUCCESS else PlatformOrderState.WAITING,
            paidAt = if (paid) Clock.System.now().toEpochMilliseconds() else null,
        )
    }

    /**
     * 沙箱不实现查单：这里没有"平台那边的真相"可查，订单状态完全由点按钮决定。
     * 保持默认的 null，让对账任务跳过沙箱而不是拿到假答案。
     */

    companion object {
        const val PLATFORM_CODE = "sandbox"
    }
}
