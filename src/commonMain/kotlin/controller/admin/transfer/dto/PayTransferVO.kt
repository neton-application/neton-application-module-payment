package controller.admin.transfer.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayTransferVO(
    val id: Long = 0,
    val appId: Long? = null,
    val channelCode: String? = null,
    val merchantTransferId: String? = null,
    val type: Int? = null,
    val price: Long? = null,
    val subject: String? = null,
    val userName: String? = null,
    val accountNo: String? = null,
    val status: Int? = null,
    val successTime: Long? = null,
    val createdAt: String? = null
)
