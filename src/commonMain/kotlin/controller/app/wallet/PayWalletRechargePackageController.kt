package controller.app.wallet

import model.PayWalletRechargePackage
import table.PayWalletRechargePackageTable
import neton.database.dsl.*
import neton.core.annotations.Controller
import neton.core.annotations.Get

@Controller("/pay/wallet-recharge-package")
class PayWalletRechargePackageController {

    @Get("/list")
    suspend fun list(): List<PayWalletRechargePackage> {
        return PayWalletRechargePackageTable.query {
            where {
                PayWalletRechargePackage::status eq 0
            }
            orderBy(PayWalletRechargePackage::payPrice.asc())
        }.list()
    }
}
