package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayWalletRechargePackageVO(
    val id: Long = 0,
    val name: String? = null,
    val payPrice: Long? = null,
    val bonusPrice: Long? = null,
    val status: Int? = null,
    val createdAt: String? = null
)
