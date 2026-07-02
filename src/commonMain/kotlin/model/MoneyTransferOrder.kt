package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id

/**
 * 转账订单（RP-2，PrivChat Money Message）。转账无需接收确认：发送即在同一事务内
 * 完成发送方扣款(ledger 500) + 接收方入账(ledger 501)。成功即终态；status 仅为异常留痕。
 * 金额 bigint(分)，时间 epoch ms。
 */
@Serializable
@Table("money_transfer_orders")
data class MoneyTransferOrder(
    @Id
    val id: Long = 0,
    val fromUserId: Long,
    val toUserId: Long,
    /** IM 场景引用（不透明字符串）。 */
    val channelId: String = "",
    val amount: Long,
    val remark: String? = null,
    /** 0 SUCCESS / 1 REFUNDED(异常回滚) */
    val status: Int = 0,
    val createdAt: Long = 0,
)
