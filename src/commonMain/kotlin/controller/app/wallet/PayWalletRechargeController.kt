package controller.app.wallet

import controller.app.wallet.dto.CreateWalletRechargeRequest
import logic.PayWalletLogic
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Post
import neton.core.interfaces.Identity

@Controller("/app/pay/wallet-recharge")
class PayWalletRechargeController(private val payWalletLogic: PayWalletLogic) {

    @Post("/create")
    suspend fun create(identity: Identity, @Body request: CreateWalletRechargeRequest): Long {
        return payWalletLogic.rechargeForUser(
            userId = identity.id.toLong(),
            totalPrice = request.totalPrice,
            payPrice = request.payPrice,
            bonusPrice = request.bonusPrice,
            packageId = request.packageId
        )
    }

    @Get("/page")
    suspend fun page(
        identity: Identity,
        pageNo: Int = 1,
        pageSize: Int = 20,
        payStatus: Int? = null
    ) = payWalletLogic.pageRechargesForUser(identity.id.toLong(), pageNo, pageSize, payStatus)
}
