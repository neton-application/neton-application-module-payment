package logic

/**
 * #85 资金消息卡片**统一交付服务**（取代原 RP-12 事务内同步注入 [MoneyMessageCardInjector]）。
 *
 * 语义变更（消除跨服务 dual-write 孤儿卡片）：payment 在资金事务内**只**写 order + wallet +
 * ledger + outbox(PENDING) 并 commit；**事务外**（commit 之后）调 [tryDeliverByRef] 做一次快投递。
 * 事务内不再有任何跨服务 HTTP —— 杜绝「IM 消息已写、payment commit 失败」→ 有卡无钱。
 *
 * 快投递与后台 worker **复用同一个实现**（module-privchat 的 MoneyMessageDeliveryService），
 * 不再是两套注入代码。dedup_key 幂等：快投递与 worker 对同一订单至多产生一张卡片。
 *
 * 分层：payment 是 canonical，不能依赖 module-privchat —— 产品层启动时装配 [MoneyMessageInjection.delivery]；
 * 未装配（null，如纯支付网关/非 IM 部署）→ [tryDeliverByRef] 视作 [DeliveryOutcome.Processing]，
 * outbox 行留 PENDING 由后台 worker 补发（纯 at-least-once outbox，资金不受影响）。
 */
interface MoneyMessageDelivery {
    /**
     * 快投递：commit 之后立即尝试把 (refType, refId) 对应的 outbox 行注入 IM 会话。
     * 幂等（dedup_key），与后台 worker 重试互不产生重复卡片。**绝不抛出**：暂时失败一律返回
     * [DeliveryOutcome.Processing]，把行留给 worker —— 资金已成立，交付是可重试副作用。
     */
    suspend fun tryDeliverByRef(refType: String, refId: Long): DeliveryOutcome
}

/** send/transfer API 的交付结果（决定响应里的 deliveryStatus / messageId）。 */
sealed class DeliveryOutcome {
    /** 卡片已注入、outbox 标 SENT；携带 server message id。 */
    data class Delivered(val serverMessageId: Long) : DeliveryOutcome()

    /** 尚未交付 —— 资金已 commit，后台 worker 会继续补发。 */
    data object Processing : DeliveryOutcome()

    val statusText: String get() = when (this) {
        is Delivered -> "DELIVERED"
        Processing -> "PROCESSING"
    }
    val serverMessageIdOrNull: Long? get() = (this as? Delivered)?.serverMessageId
}

/** 装配点：产品层启动时赋值；payment 侧只读。 */
object MoneyMessageInjection {
    @kotlin.concurrent.Volatile
    var delivery: MoneyMessageDelivery? = null
}

/**
 * #85-A2 资金发送响应契约（红包 send / 转账 send 统一）。`code=0` 只表示平台已可靠接受并完成资金业务，
 * **不**表示聊天卡片一定已送达 —— 交付语义看 [deliveryStatus]：
 *   - `DELIVERED`：卡片已注入 IM，[messageId] 为 server message id；
 *   - `PROCESSING`：资金已成立，卡片由后台 outbox worker 补发，[messageId] 为 null。
 * 全系统只有这两个交付态（不引入 SUCCESS/FAILED）。
 */
@kotlinx.serialization.Serializable
data class MoneySendResultVO(
    val orderId: Long,
    val deliveryStatus: String,
    val messageId: String? = null,
) {
    companion object {
        fun of(orderId: Long, outcome: DeliveryOutcome) =
            MoneySendResultVO(orderId, outcome.statusText, outcome.serverMessageIdOrNull?.toString())
    }
}
