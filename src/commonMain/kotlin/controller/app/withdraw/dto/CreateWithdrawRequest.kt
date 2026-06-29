package controller.app.withdraw.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateWithdrawRequest(
    val bankCardId: Long,
    val amount: Long,
    val currency: String = "CNY",
)
