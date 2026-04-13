package controller.admin.wallet.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class MarkWalletRechargePaidRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val payChannelCode: String
)

@Serializable
data class MarkWalletRechargeRefundRequest(
    @property:Min(1)
    val id: Long
)

@Serializable
data class MarkWalletRechargeRefundedRequest(
    @property:Min(1)
    val id: Long
)
