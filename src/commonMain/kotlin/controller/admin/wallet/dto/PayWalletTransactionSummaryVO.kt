package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayWalletTransactionSummaryVO(
    val totalIncome: Long,
    val totalExpense: Long
)
