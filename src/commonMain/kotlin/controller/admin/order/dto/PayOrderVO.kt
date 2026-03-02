package controller.admin.order.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayOrderVO(
    val id: Long = 0,
    val appId: Long? = null,
    val merchantOrderId: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val price: Long? = null,
    val channelCode: String? = null,
    val channelOrderNo: String? = null,
    val status: Int? = null,
    val userIp: String? = null,
    val expireTime: Long? = null,
    val successTime: Long? = null,
    val notifyTime: Long? = null,
    val createdAt: String? = null
)
