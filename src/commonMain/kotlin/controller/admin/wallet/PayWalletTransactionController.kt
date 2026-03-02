package controller.admin.wallet

import logic.PayWalletLogic
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
}
