package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayWalletVO(
    val id: Long = 0,
    val userId: Long? = null,
    val balance: Long? = null,
    val totalExpense: Long? = null,
    val totalRecharge: Long? = null,
    val freezePrice: Long? = null,
    val createdAt: String? = null
)
