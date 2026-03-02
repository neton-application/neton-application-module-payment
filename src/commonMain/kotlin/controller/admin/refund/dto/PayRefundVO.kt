package controller.admin.refund.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayRefundVO(
    val id: Long = 0,
    val appId: Long? = null,
    val orderId: Long? = null,
    val merchantRefundId: String? = null,
    val channelCode: String? = null,
    val channelRefundNo: String? = null,
    val payPrice: Long? = null,
    val refundPrice: Long? = null,
    val reason: String? = null,
    val status: Int? = null,
    val successTime: Long? = null,
    val createdAt: String? = null
)
