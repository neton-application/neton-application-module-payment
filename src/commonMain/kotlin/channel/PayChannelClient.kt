package channel

import model.PayOrder

/** 渠道下单结果。payUrl 为客户端拉起支付的地址（H5/二维码/deeplink，渠道而定）。 */
data class PrepayResult(
    val payUrl: String,
    val channelOrderNo: String
)

/** 渠道回调解析结果（验签后）。 */
data class ChannelNotifyResult(
    val merchantOrderId: String,
    val channelOrderNo: String,
    val success: Boolean,
    val paidAt: Long
)

/**
 * 支付渠道 client 抽象。每个渠道（七九四方 / 微信 / 支付宝）一个实现。
 * 下单 [prepay] 调渠道 API 拿支付地址；回调 [parseNotify] 验签并解析异步通知。
 *
 * 真实渠道对接（商户号 / 密钥 / 网关 / 验签）在实现内完成；当前提供 mock 实现，
 * 用于跑通「下单 → 支付 → 回调 → 解锁」全链路，上线前替换为真实实现即可。
 */
interface PayChannelClient {
    /** 渠道编码，与 PayOrder.channelCode / PayChannel.code 对应。 */
    val channelCode: String

    /** 下单：返回客户端支付地址与渠道订单号。 */
    suspend fun prepay(order: PayOrder): PrepayResult

    /** 解析并验签异步回调；验签失败返回 null（调用方拒绝）。 */
    fun parseNotify(params: Map<String, String>): ChannelNotifyResult?
}
