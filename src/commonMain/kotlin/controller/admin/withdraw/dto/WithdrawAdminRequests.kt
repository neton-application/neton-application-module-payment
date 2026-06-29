package controller.admin.withdraw.dto

import kotlinx.serialization.Serializable

@Serializable
data class WithdrawApproveRequest(val remark: String? = null)

@Serializable
data class WithdrawRejectRequest(val reason: String)

@Serializable
data class WithdrawMarkPaidRequest(val payoutTradeNo: String? = null)

@Serializable
data class WithdrawMarkFailedRequest(val reason: String)
