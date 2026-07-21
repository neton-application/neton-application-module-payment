package port

import model.PayOrder

/**
 * 支付成功业务回调端口（与 member 的 RewardPort/InvitePort 同款 port/adapter 分层）。
 *
 * payment 是通用支付网关，不懂业务语义（解锁内容 / 开通 VIP / 充值到账）。
 * 支付成功后由 [logic.PayOrderLogic.updateSuccess] 在**同一事务内**调用本端口，
 * 业务方（content / member 等）在 application 装配层 bind 实现，按
 * [PayOrder.merchantOrderId] 编码的业务前缀路由处理。
 *
 * 语义（fail-fast，资金/解锁一致性）：
 * - 实现方的 DB 副作用加入支付成功的同一事务。
 * - 实现方抛异常 = 支付成功整体回滚（不出现「付了钱没解锁」）。
 * - 幂等锚点：merchantOrderId（业务订单唯一）或 PayOrder.id。
 * - 未装配（builtin）= no-op，纯网关行为不变（向后兼容 privchat 等既有消费方）。
 */
interface PayOrderPaidPort {
    suspend fun onPaid(order: PayOrder)
}
