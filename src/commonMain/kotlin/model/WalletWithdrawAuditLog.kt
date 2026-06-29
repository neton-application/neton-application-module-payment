package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt

/** 提现订单审批审计（P4-C）：每次状态流转 who/when/before→after/why 留痕。 */
@Serializable
@Table("wallet_withdraw_audit_logs")
data class WalletWithdrawAuditLog(
    @Id
    val id: Long = 0,
    val orderId: Long,
    val operatorId: Long,
    /** approve / reject / mark_paid / mark_failed / cancel / create */
    val action: String,
    val beforeStatus: Int,
    val afterStatus: Int,
    val remark: String? = null,
    @CreatedAt
    val createdAt: String? = null,
)
