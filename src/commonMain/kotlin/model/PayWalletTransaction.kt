package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt

@Serializable
@Table("pay_wallet_transactions")
data class PayWalletTransaction(
    @Id
    val id: Long = 0,
    val walletId: Long,
    val bizType: Int,
    val bizId: Long,
    val title: String,
    val price: Long,
    val balance: Long,
    @CreatedAt
    val createdAt: String? = null
)
