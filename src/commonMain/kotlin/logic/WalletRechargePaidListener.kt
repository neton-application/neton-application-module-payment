package logic

import event.PayOrderPaidEvent
import neton.core.event.DeliveryMode
import neton.core.event.DomainEventListener

/**
 * 在线充值到账：支付成功 → 充值单置为已支付 → 余额入账。
 *
 * 这是事件总线的**第二个消费方**，也是当初不做单绑定端口的原因：
 * 内容解锁与钱包到账都要消费同一个"支付成功"，端口只能有一个实现，
 * 第二方接入就得挤进第一方的实现里去解析业务前缀。
 *
 * [DeliveryMode.SYNC]：到账必须与支付成功同生共死。异步化会出现
 * "钱扣了余额还没加"的窗口，而余额是用户随时会看的数字，
 * 这个窗口产生的客诉远比多花一点事务时间贵。
 *
 * 幂等由 [PayWalletLogic.markRechargePaid] 保证（已支付直接返回 + 乐观锁），
 * 因此回调重投不会重复加钱。
 */
class WalletRechargePaidListener(
    private val wallets: PayWalletLogic,
) : DomainEventListener<PayOrderPaidEvent> {

    override val eventType = PayOrderPaidEvent::class

    /** 稳定标识，落库投递靠它路由回来，**不可更改**（重命名类不影响它）。 */
    override val listenerId = "payment.wallet-recharge-paid"

    override val mode: DeliveryMode = DeliveryMode.SYNC

    override suspend fun onEvent(event: PayOrderPaidEvent) {
        // 只认自己的业务前缀，其它订单（内容购买 / VIP）交给各自的监听者
        val rechargeId = parse(event.order.merchantOrderId) ?: return
        wallets.markRechargePaid(rechargeId, event.order.channelCode ?: "")
    }

    companion object {
        const val PREFIX = "wallet:recharge:"

        /** `wallet:recharge:{rechargeId}` → rechargeId；不匹配返回 null。 */
        fun parse(merchantOrderId: String): Long? =
            merchantOrderId.takeIf { it.startsWith(PREFIX) }
                ?.removePrefix(PREFIX)
                ?.toLongOrNull()

        /**
         * 充值单的商户订单号。
         *
         * 只放 rechargeId：充值单本身已记着 walletId，再把 userId 拼进来既冗余、
         * 又多一处可能与充值单对不上的地方。
         */
        fun merchantOrderId(rechargeId: Long): String = "$PREFIX$rechargeId"
    }
}
