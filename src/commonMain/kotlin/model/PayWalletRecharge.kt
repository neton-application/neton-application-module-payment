package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("pay_wallet_recharges")
data class PayWalletRecharge(
    @Id
    val id: Long = 0,
    val walletId: Long,
    val totalPrice: Long,
    val payPrice: Long,
    val bonusPrice: Long = 0,
    val packageId: Long? = null,
    val payStatus: Int = 0,
    val payOrderId: Long? = null,
    val payChannelCode: String? = null,
    val refundStatus: Int = 0,
    val refundTotalPrice: Long = 0,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
