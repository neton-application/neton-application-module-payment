package controller.admin.wallet

import model.PayWallet
import controller.admin.wallet.dto.UpdateWalletBalanceRequest
import logic.PayWalletLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Put
import neton.core.annotations.Body
import neton.core.annotations.Query

@Controller("/pay/wallet")
class PayWalletController(private val payWalletLogic: PayWalletLogic) {

    @Get("/get-by-user-id")
    suspend fun getByUserId(@Query userId: Long): PayWallet? {
        return payWalletLogic.getWalletByUserId(userId)
    }

    @Get("/page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query userId: Long? = null
    ) = payWalletLogic.pageWallets(pageNo, pageSize, userId)

    @Put("/update-balance")
    suspend fun updateBalance(@Body req: UpdateWalletBalanceRequest) {
        payWalletLogic.adjustBalance(req.userId, req.balance)
    }
}
