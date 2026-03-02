package controller.admin.order.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayOrderSubmitRequest(
    val appId: Long,
    val merchantOrderId: String,
    val subject: String,
    val body: String? = null,
    val price: Long,
    val channelCode: String? = null,
    val userIp: String? = null,
    val expireTime: Long? = null
)
