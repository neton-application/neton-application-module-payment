package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayWalletTransactionVO(
    val id: Long = 0,
    val walletId: Long? = null,
    val bizType: Int? = null,
    val bizId: Long? = null,
    val title: String? = null,
    val price: Long? = null,
    val balance: Long? = null,
    val createdAt: String? = null
)
