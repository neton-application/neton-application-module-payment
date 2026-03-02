package controller.admin.channel.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayChannelVO(
    val id: Long = 0,
    val appId: Long? = null,
    val code: String? = null,
    val config: String? = null,
    val status: Int? = null,
    val feeRate: Int? = null,
    val remark: String? = null,
    val createdAt: String? = null
)
