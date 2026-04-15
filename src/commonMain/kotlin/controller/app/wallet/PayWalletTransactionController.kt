package controller.app.wallet

import logic.PayWalletLogic
import controller.admin.wallet.dto.PayWalletTransactionSummaryVO
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.interfaces.Identity

@Controller("/app/pay/wallet-transaction")
class PayWalletTransactionController(private val payWalletLogic: PayWalletLogic) {

    @Get("/page")
    suspend fun page(
        identity: Identity,
        pageNo: Int = 1,
        pageSize: Int = 20,
        bizType: Int? = null
    ) = payWalletLogic.pageTransactionsForUser(identity.id.toLong(), pageNo, pageSize, bizType)

    @Get("/get-summary")
    suspend fun getSummary(identity: Identity): PayWalletTransactionSummaryVO {
        val (totalIncome, totalExpense) = payWalletLogic.getTransactionSummaryForUser(identity.id.toLong())
        return PayWalletTransactionSummaryVO(
            totalIncome = totalIncome,
            totalExpense = totalExpense
        )
    }
}
