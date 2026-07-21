package controller.app.notify

import logic.PayOrderLogic
import neton.core.annotations.AllowAnonymous
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.PathVariable
import neton.core.annotations.Post

/**
 * 支付渠道异步回调入口。第三方支付服务器回调（无用户会话）→ 匿名；
 * 由渠道 client 验签，验签失败拒绝。
 */
@AllowAnonymous
@Controller("/pay/channel-notify")
class PayChannelNotifyController(private val payOrderLogic: PayOrderLogic) {

    /** 渠道回调：body 为渠道透传参数（含 merchantOrderId 等）。 */
    @Post("/{channelCode}")
    suspend fun notify(
        @PathVariable channelCode: String,
        @Body params: Map<String, String>
    ): String {
        val ok = payOrderLogic.handleChannelNotify(channelCode, params)
        return if (ok) "success" else "fail" // 多数渠道要求回 "success" 纯文本
    }

    /**
     * 开发/联调用：模拟渠道支付成功回调（真实上线由第三方回调 /{channelCode}）。
     * 传 merchantOrderId 即触发该订单成功 + 业务解锁。
     */
    @Post("/{channelCode}/mock-success")
    suspend fun mockSuccess(
        @PathVariable channelCode: String,
        @Body params: Map<String, String>
    ): Boolean =
        payOrderLogic.handleChannelNotify(channelCode, params + ("status" to "success"))
}
