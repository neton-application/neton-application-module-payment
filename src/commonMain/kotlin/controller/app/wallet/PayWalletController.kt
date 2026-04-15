package controller.app.wallet

import logic.PayWalletLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.interfaces.Identity

@Controller("/app/pay/wallet")
class PayWalletController(private val payWalletLogic: PayWalletLogic) {

    @Get("/get")
    suspend fun get(identity: Identity) = payWalletLogic.getWallet(identity.id.toLong())
}
