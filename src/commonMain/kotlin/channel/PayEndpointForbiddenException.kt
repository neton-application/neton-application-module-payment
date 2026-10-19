package channel

import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.http.client.TargetRejection

/**
 * 支付平台网关地址（渠道配置里的支付宝 serverUrl、四方 apiUrl）被运维出站策略拒绝，一个包都没发出去。
 *
 * 对外只有机器码 [logic.PaymentErrorCodes.PAY_ENDPOINT_FORBIDDEN]（503，与网络失败、平台报错区分开）；
 * 被拒的原因与地址（[reason]、[detail]）只给服务端日志（[logic.PayOrderLogic] 记 `pay.platform.endpoint_forbidden`），
 * 不进响应：解析到哪个内网地址正是不该告诉调用方的东西。放行内网网关要在部署配置 application.conf `[egress]` 里做。
 */
class PayEndpointForbiddenException(
    val platformCode: String,
    val reason: TargetRejection,
    val detail: String,
) : HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, logic.PaymentErrorCodes.PAY_ENDPOINT_FORBIDDEN)
