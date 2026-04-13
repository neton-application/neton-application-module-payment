package controller.app.wallet.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Min

@Serializable
data class CreateWalletRechargeRequest(
    @property:Min(1)
    val totalPrice: Long,

    @property:Min(1)
    val payPrice: Long,

    @property:Min(0)
    val bonusPrice: Long = 0,
    val packageId: Long? = null
)
