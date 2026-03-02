package controller.app.wallet

import model.PayWalletRecharge
import logic.PayWalletLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Post

@Controller("/pay/wallet-recharge")
class PayWalletRechargeController(private val payWalletLogic: PayWalletLogic) {

    @Post("/create")
    suspend fun create(recharge: PayWalletRecharge): Long {
        return payWalletLogic.recharge(recharge)
    }

    @Get("/page")
    suspend fun page(
        pageNo: Int = 1,
        pageSize: Int = 20,
        walletId: Long? = null,
        payStatus: Int? = null
    ) = payWalletLogic.pageRecharges(pageNo, pageSize, walletId, payStatus)
}
