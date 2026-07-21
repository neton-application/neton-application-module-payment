package channel

import model.PayOrder
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 模拟支付渠道 client。下单返回一个可在 dev 拉起「模拟收银台」的地址，
 * 回调直接信任（不验签）。七九四方 / 微信 / 支付宝上线前都用它跑通链路，
 * 真实对接时替换为各自实现（实现 [PayChannelClient] 即可，无需改 Logic）。
 */
class MockPayChannelClient(override val channelCode: String) : PayChannelClient {

    @OptIn(ExperimentalTime::class)
    override suspend fun prepay(order: PayOrder): PrepayResult {
        val channelOrderNo = "MOCK-$channelCode-${order.merchantOrderId}"
        // 模拟收银台：前端可展示二维码/H5；点「已支付」即请求 mock-notify 触发回调
        val payUrl = "mock://pay/$channelCode?merchantOrderId=${order.merchantOrderId}&price=${order.price}"
        return PrepayResult(payUrl = payUrl, channelOrderNo = channelOrderNo)
    }

    @OptIn(ExperimentalTime::class)
    override fun parseNotify(params: Map<String, String>): ChannelNotifyResult? {
        val merchantOrderId = params["merchantOrderId"] ?: return null
        val success = params["status"]?.let { it == "success" || it == "1" } ?: true
        return ChannelNotifyResult(
            merchantOrderId = merchantOrderId,
            channelOrderNo = params["channelOrderNo"] ?: "MOCK-$channelCode-$merchantOrderId",
            success = success,
            paidAt = Clock.System.now().toEpochMilliseconds()
        )
    }
}
