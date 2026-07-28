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
 * 用户可见文案由各端按 [reasonCode] + [reasonParams] 渲染，**不在此传中文**。
 */
@Serializable
data class WithdrawHoldRequest(
    /** BANK_CUTOFF / CARD_UNUSABLE / NAME_MISMATCH / COMPLIANCE_REVIEW / OTHER */
    val reasonCode: String,
    /** JSON 文本，如 {"bank":"ICBC","resume_at":178...}；OTHER 用 {"text":"..."} */
    val reasonParams: String? = null,
    /** 内部备注，不下发给用户。 */
    val internalNote: String? = null,
)

@Serializable
data class WithdrawUnholdRequest(val internalNote: String? = null)
