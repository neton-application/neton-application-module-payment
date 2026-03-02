package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

@Serializable
data class WalletUpdateBalanceReqVO(
    val userId: Long,
    val balance: Long
)
