package controller.app.wallet

import model.PayWallet
import logic.PayWalletLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get

@Controller("/pay/wallet")
class PayWalletController(private val payWalletLogic: PayWalletLogic) {

    @Get("/get")
    suspend fun get(userId: Long): PayWallet? {
        return payWalletLogic.getWallet(userId)
    }
}
