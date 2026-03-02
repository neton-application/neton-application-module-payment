package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayWalletRechargeVO(
    val id: Long = 0,
    val walletId: Long? = null,
    val totalPrice: Long? = null,
    val payPrice: Long? = null,
    val bonusPrice: Long? = null,
    val packageId: Long? = null,
    val payStatus: Int? = null,
    val payOrderId: Long? = null,
    val payChannelCode: String? = null,
    val refundStatus: Int? = null,
    val refundTotalPrice: Long? = null,
    val createdAt: String? = null
)
