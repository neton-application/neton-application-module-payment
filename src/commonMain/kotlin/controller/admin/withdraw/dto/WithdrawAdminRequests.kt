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

/**
 * 挂起（spec WALLET_WITHDRAW_SPEC §10）。
 */
@Serializable
data class WithdrawHoldRequest(
    /** 运营手填，原样展示给用户，不做 i18n。 */
    val reasonText: String,
    /** 内部备注，不下发给用户。 */
    val internalNote: String? = null,
)

@Serializable
data class WithdrawUnholdRequest(val internalNote: String? = null)
