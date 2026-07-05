package controller.admin.wallet

import logic.PayWalletLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Permission

@Controller("/pay/wallet-transaction")
class PayWalletTransactionController(private val payWalletLogic: PayWalletLogic) {

    // 路由跨 group 按 method+pattern 全局去重：app 侧已注册 `/pay/wallet-transaction/page`，
    // admin 同名 pattern 被丢弃导致 404。admin 侧用独立 pattern（惯例同 admin 牌谱 /full-replay）。
    @Get("/page-by-wallet")
    @Permission("pay:wallet-transaction:page")
    suspend fun page(
        pageNo: Int = 1,
        pageSize: Int = 20,
        walletId: Long? = null,
        bizType: Int? = null
    ) = payWalletLogic.pageTransactions(pageNo, pageSize, walletId, bizType)
}
