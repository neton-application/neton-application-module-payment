package controller.app.moneytransfer.dto

import kotlinx.serialization.Serializable

/** 转账请求（金额分）。 */
@Serializable
data class SendMoneyTransferRequest(
    val toUserId: Long,
    val channelId: String = "",
    val amount: Long,
    val remark: String? = null,
)
