package controller.app.notify

import channel.PAY_NOTIFY_ROUTE
import channel.PlatformNotifyRequest
import logic.PayOrderLogic
import neton.core.annotations.AllowAnonymous
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.PathVariable
import neton.core.annotations.Post
import neton.core.http.NotFoundException
import setting.PaymentSettingKeys
import setting.currentValue

/**
 * 支付渠道异步回调入口。第三方支付服务器回调（无用户会话）→ 匿名；
 * 由渠道 client 验签，验签失败拒绝。
 *
 * 基础路径取自 [PAY_NOTIFY_ROUTE] 而不是就地写字面量：下单时发给渠道的 notify_url
 * 由 [channel.payNotifyUrlOf] 从同一个常量拼出来。两边各写一份字符串的话，
 * 改路由不会有任何编译错误，只会在生产上表现为「用户付了钱、订单一直待支付」
 * —— 渠道打过来 404，重试几次就放弃，而对账任务（[job.PAY_RECONCILE_JOB_ID]）
 * 十来分钟才查一次单，且刚下单 2 分钟内的会被跳过。
 */
@AllowAnonymous
@Controller(PAY_NOTIFY_ROUTE)
class PayChannelNotifyController(private val payOrderLogic: PayOrderLogic) {

    /**
     * 渠道回调。
     *
     * 回执格式由渠道自己给（[channel.PayPlatform.ackBody]）：答错了渠道会认为
     * 投递失败并持续重试，而各家要求并不一致，不能在这里统一硬编码。
     */
    @Post("/{channelCode}")
    suspend fun notify(
        @PathVariable channelCode: String,
        @Body params: Map<String, String>
    ): String {
        val request = PlatformNotifyRequest(params = params)
        val ok = payOrderLogic.handleChannelNotify(channelCode, request)
        return payOrderLogic.ackBody(channelCode, ok)
    }

    /**
     * 联调用：模拟渠道支付成功回调（真实上线由第三方回调 /{channelCode}）。
     *
     * **必须由全局设置 [PaymentSettingKeys.MOCK_PAY_ENABLED] 显式打开。** 这个端点匿名、不验签，
     * 只要给出 merchantOrderId 就能把任意订单置为已支付并触发业务解锁 ——
     * 在生产环境开着它等于把「免费领取」挂在公网上。关闭时返回 404。
     */
    @Post("/{channelCode}/mock-success")
    suspend fun mockSuccess(
        @PathVariable channelCode: String,
        @Body params: Map<String, String>
    ): Boolean {
        if (!PaymentSettingKeys.MOCK_PAY_ENABLED.currentValue()) throw NotFoundException("Not found")
        return payOrderLogic.handleChannelNotify(
            channelCode,
            PlatformNotifyRequest(params = params + ("status" to "success")),
        )
    }

    /**
     * 退款回调（异步退款的平台用，如微信）。
     *
     * 与支付回调同为公网入口，同样由平台验签/解密后才认。
     */
    @Post("/{channelCode}/refund")
    suspend fun refundNotify(
        @PathVariable channelCode: String,
        @Body params: Map<String, String>
    ): String {
        val ok = payOrderLogic.handleRefundNotify(channelCode, PlatformNotifyRequest(params = params))
        return payOrderLogic.ackBody(channelCode, ok)
    }

}
