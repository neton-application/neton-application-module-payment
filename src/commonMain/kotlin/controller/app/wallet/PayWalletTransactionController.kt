package controller.app.wallet

import logic.PayWalletLogic
import controller.admin.wallet.dto.PayWalletTransactionSummaryVO
import neton.core.annotations.Controller
import neton.core.annotations.Get

@Controller("/pay/wallet-transaction")
class PayWalletTransactionController(private val payWalletLogic: PayWalletLogic) {

    @Get("/page")
    suspend fun page(
        pageNo: Int = 1,
        pageSize: Int = 20,
        walletId: Long? = null,
        bizType: Int? = null
    ) = payWalletLogic.pageTransactions(pageNo, pageSize, walletId, bizType)

    @Get("/get-summary")
    suspend fun getSummary(walletId: Long): PayWalletTransactionSummaryVO {
        val (totalIncome, totalExpense) = payWalletLogic.getTransactionSummary(walletId)
        return PayWalletTransactionSummaryVO(
            totalIncome = totalIncome,
            totalExpense = totalExpense
        )
    }
}
