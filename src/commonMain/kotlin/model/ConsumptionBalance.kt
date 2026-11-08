package model

import neton.database.annotations.*

/** 平台消费余额，金额单位为 μUSD。 */
@Table("pay_consumption_accounts")
data class ConsumptionAccount(
    @Id val id: Long = 0,
    val userId: Long,
    val balance: Long = 0,
    val reservedBalance: Long = 0,
    val version: Long = 0,
    @CreatedAt val createdAt: Long? = null,
    @UpdatedAt val updatedAt: Long? = null,
)

@Table("pay_consumption_transactions")
data class ConsumptionTransaction(
    @Id val id: Long = 0,
    val userId: Long,
    val type: String,
    val amount: Long,
    val balanceAfter: Long,
    val ref: String? = null,
    @CreatedAt val createdAt: Long? = null,
    val operatorId: Long? = null,
    val remark: String? = null,
)
