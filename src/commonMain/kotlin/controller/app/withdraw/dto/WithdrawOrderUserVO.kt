package controller.app.withdraw.dto

import kotlinx.serialization.Serializable
import model.WalletWithdrawOrder

/**
 * 提现订单的**用户可见投影**。
 *
 * 实体上有三个纯内部字段（`reviewRemark` 审核备注、`failureReason` 内部失败原因、
 * `holdNoteInternal` 挂起内部备注），直接返回实体会把它们发到用户手机上。
 * 用户侧一律走这个投影，新增内部字段时默认不外泄。
 *
 * 挂起下发 `holdReasonText`（运营手填，原样展示，不做 i18n；spec WALLET_WITHDRAW_SPEC §10.5）。
 */
@Serializable
data class WithdrawOrderUserVO(
    val id: Long,
    val userId: Long,
    val bankCardId: Long,
    val amount: Long,
    val fee: Long,
    val actualAmount: Long,
    val currency: String,
    /** 0=PENDING 1=APPROVED 2=PROCESSING 3=PAID 4=REJECTED 5=FAILED 6=CANCELLED 7=ON_HOLD */
    val status: Int,
    /** 驳回/失败原因（客户可见）。 */
    val freezeRemarkUserVisible: String? = null,
    val holdReasonText: String? = null,
    val createdAt: Long = 0,
    val reviewedAt: Long = 0,
    val paidAt: Long = 0,
)

fun WalletWithdrawOrder.toUserVO(): WithdrawOrderUserVO = WithdrawOrderUserVO(
    id = id,
    userId = userId,
    bankCardId = bankCardId,
    amount = amount,
    fee = fee,
    actualAmount = actualAmount,
    currency = currency,
    status = status,
    freezeRemarkUserVisible = freezeRemarkUserVisible,
    holdReasonText = holdReasonText,
    createdAt = createdAt ?: 0,
    reviewedAt = reviewedAt,
    paidAt = paidAt,
)
