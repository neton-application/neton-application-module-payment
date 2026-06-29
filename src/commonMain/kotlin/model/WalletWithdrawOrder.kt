package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

/**
 * 提现订单（P4-C）。申请时只冻结资金（不扣 balance），PAID 才从冻结实扣。
 * status 见 [logic.WithdrawStateMachine]。金额 bigint(分)。
 */
@Serializable
@Table("wallet_withdraw_orders")
data class WalletWithdrawOrder(
    @Id
    val id: Long = 0,
    val userId: Long,
    val walletId: Long,
    val bankCardId: Long,
    val amount: Long,
    val fee: Long = 0,
    val actualAmount: Long,
    val currency: String = "CNY",
    /** 0=PENDING 1=APPROVED 2=PROCESSING 3=PAID 4=REJECTED 5=FAILED 6=CANCELLED */
    val status: Int = 0,
    val reviewerId: Long = 0,
    /** 内部审核备注（仅后台）。 */
    val reviewRemark: String? = null,
    /** 驳回/失败原因（客户可见）。 */
    val freezeRemarkUserVisible: String? = null,
    /** 内部失败原因。 */
    val failureReason: String? = null,
    // 代付通道扩展位（第一版人工打款留空）。
    val paymentChannelId: Long = 0,
    val payoutChannel: String? = null,
    val payoutTradeNo: String? = null,
    @CreatedAt
    val createdAt: String? = null,
    val reviewedAt: Long = 0,
    val paidAt: Long = 0,
    @UpdatedAt
    val updatedAt: String? = null,
)
