package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("pay_wallets")
data class PayWallet(
    @Id
    val id: Long = 0,
    val userId: Long,
    val balance: Long = 0,
    val totalExpense: Long = 0,
    val totalRecharge: Long = 0,
    val freezePrice: Long = 0,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
