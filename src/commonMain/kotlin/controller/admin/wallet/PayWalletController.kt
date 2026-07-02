package controller.admin.wallet

import model.PayWallet
import controller.admin.wallet.dto.UpdateWalletBalanceRequest
import controller.admin.wallet.dto.WalletOverviewVO
import logic.PayWalletLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Permission
import neton.core.annotations.Put
import neton.core.annotations.Body
import neton.core.annotations.Query

@Controller("/pay/wallet")
class PayWalletController(private val payWalletLogic: PayWalletLogic) {

    @Get("/get-by-user-id")
    @Permission("pay:wallet:query")
    suspend fun getByUserId(@Query userId: Long): PayWallet? {
        return payWalletLogic.getWalletByUserId(userId)
    }

    @Get("/page")
    @Permission("pay:wallet:page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query userId: Long? = null
    ) = payWalletLogic.pageWallets(pageNo, pageSize, userId)

    @Put("/update-balance")
    @Permission("pay:wallet:update")
    suspend fun updateBalance(@Body req: UpdateWalletBalanceRequest) {
        payWalletLogic.adjustBalance(req.userId, req.balance)
    }

    /** 财务总览轻量版（P1）：钱包资金聚合 + 提现分状态聚合。 */
    @Get("/overview")
    @Permission("pay:wallet:overview")
    suspend fun overview(): WalletOverviewVO = payWalletLogic.getFinanceOverview()
}
