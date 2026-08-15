package event

import kotlinx.serialization.Serializable
import model.PayOrder
import neton.core.event.DomainEvent

/**
 * 支付订单的领域事件。由 payment 发布，业务方模块订阅。
 *
 * 取代原先的 `PayOrderPaidPort`：那是单绑定端口（`ctx.getOrNull(PayOrderPaidPort::class)`），
 * 只能有一个消费方，第二个业务方（例如钱包在线充值到账）要接就得挤进同一个实现里、
 * 靠解析 `merchantOrderId` 前缀分流 —— 那个实现于是变成认识所有业务的中央调度器，
 * 恰好是端口本想避免的耦合。改成事件后 payment 不认识任何业务方。
 *
 * 三个状态都发，而不是只发"已支付"：原来退款走 `updateRefund` 却不通知任何人，
 * 业务侧无从回收权益。
 */
sealed interface PayOrderEvent : DomainEvent {
    val order: PayOrder
}

/** 支付成功。消费方在此解锁权益/到账，失败则整笔支付回滚。 */
@Serializable
data class PayOrderPaidEvent(override val order: PayOrder) : PayOrderEvent

/** 已退款。消费方据此回收之前发放的权益。 */
@Serializable
data class PayOrderRefundedEvent(override val order: PayOrder) : PayOrderEvent

/** 订单关闭（超时/取消），从未支付。消费方通常只需清理占位数据。 */
@Serializable
data class PayOrderClosedEvent(override val order: PayOrder) : PayOrderEvent
