package controller.admin.wallet.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Min

@Serializable
data class UpdateWalletBalanceRequest(
    @property:Min(1)
    val userId: Long,

    val balance: Long
)
