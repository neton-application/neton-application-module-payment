package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt

/**
 * 通用敏感操作审计（P0/V007，不可抵赖）。用于不一定绑定提现订单的敏感操作：
 * 银行卡 reveal 完整卡号 / 余额调整 / 敏感数据导出 / 人工补账 / 人工解冻 等。
 * 提现订单生命周期审计仍用 [WalletWithdrawAuditLog]。
 */
@Serializable
@Table("pay_sensitive_audit_logs")
data class PaySensitiveAuditLog(
    @Id
    val id: Long = 0,
    val operatorId: Long = 0,
    val operatorName: String? = null,
    val operatorRole: String? = null,
    /** BANK_CARD_REVEAL / WALLET_ADJUST / WITHDRAW_MANUAL_FIX / EXPORT_SENSITIVE_DATA ... */
    val action: String,
    /** BANK_CARD / WALLET / WITHDRAW_ORDER / USER ... */
    val targetType: String,
    val targetId: Long = 0,
    val targetUserId: Long = 0,
    val ip: String? = null,
    val userAgent: String? = null,
    val traceId: String? = null,
    val beforeSnapshot: String? = null,
    val afterSnapshot: String? = null,
    val reason: String? = null,
    @CreatedAt
    val createdAt: String? = null,
)
