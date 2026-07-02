package controller.app.redpacket.dto

import kotlinx.serialization.Serializable

/** 发红包请求（金额分）。type=0 普通(MVP)；scene 0=dm 1=group。 */
@Serializable
data class SendRedPacketRequest(
    val channelId: String = "",
    val scene: Int = 0,
    val type: Int = 0,
    val totalAmount: Long,
    val totalCount: Int,
    val greeting: String? = null,
)
