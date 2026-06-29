package controller.app.bankcard.dto

import kotlinx.serialization.Serializable

@Serializable
data class BindBankCardRequest(
    val holderName: String,
    val bankName: String,
    val bankCode: String? = null,
    val cardNo: String,
)
