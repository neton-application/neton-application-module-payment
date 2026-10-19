package channel

import model.PayChannel

/**
 * 支付回调地址 hook：部署在哪个域名下是部署事实，payment 模块无从知道，由装配层在 bind 阶段给出
 * （`bind(PayCallbackHooks::class, PayCallbackHooks(notifyUrlOf = payNotifyUrlOf(base), returnUrlOf = { url }))`）。
 *
 * 装配层只给地址，不再自己拼 [PayPlatformRegistry]：平台对接地址（支付宝 serverUrl、四方 apiUrl）是后台渠道配置里的
 * 运行期数据，平台必须用受控出站客户端去打，这个客户端由 [init.PaymentRuntimeBootstrap] 从运维出站策略建，
 * 装配层不该、也无从在 bind 阶段拿到它。没绑定 = 两个 hook 都给空串（不写 notify_url / return_url，见
 * [PayPlatformRegistry.default]）。
 */
class PayCallbackHooks(
    val notifyUrlOf: (PayChannel) -> String = { "" },
    val returnUrlOf: (PayChannel) -> String = { "" },
)
