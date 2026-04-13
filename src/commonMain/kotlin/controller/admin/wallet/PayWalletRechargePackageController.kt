package controller.admin.wallet

import controller.admin.wallet.dto.CreatePayWalletRechargePackageRequest
import controller.admin.wallet.dto.UpdatePayWalletRechargePackageRequest
import model.PayWalletRechargePackage
import table.PayWalletRechargePackageTable
import neton.database.dsl.*
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Post
import neton.core.annotations.Put
import neton.core.annotations.Delete
import neton.core.annotations.Body
import neton.core.annotations.PathVariable
import neton.core.annotations.Query

@Controller("/pay/wallet-recharge-package")
class PayWalletRechargePackageController {

    @Post("/create")
    suspend fun create(@Body request: CreatePayWalletRechargePackageRequest): Long {
        return PayWalletRechargePackageTable.insert(
            PayWalletRechargePackage(
                name = request.name,
                payPrice = request.payPrice,
                bonusPrice = request.bonusPrice,
                status = request.status
            )
        ).id
    }

    @Put("/update")
    suspend fun update(@Body request: UpdatePayWalletRechargePackageRequest) {
        PayWalletRechargePackageTable.update(
            PayWalletRechargePackage(
                id = request.id,
                name = request.name,
                payPrice = request.payPrice,
                bonusPrice = request.bonusPrice,
                status = request.status
            )
        )
    }

    @Delete("/delete/{id}")
    suspend fun delete(@PathVariable id: Long) {
        PayWalletRechargePackageTable.destroy(id)
    }

    @Get("/get/{id}")
    suspend fun get(@PathVariable id: Long): PayWalletRechargePackage? {
        return PayWalletRechargePackageTable.get(id)
    }

    @Get("/page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query name: String? = null,
        @Query status: Int? = null
    ) = PayWalletRechargePackageTable.query {
        where {
            and(
                whenNotBlank(name) { PayWalletRechargePackage::name like "%$it%" },
                whenPresent(status) { PayWalletRechargePackage::status eq it }
            )
        }
        orderBy(PayWalletRechargePackage::id.desc())
    }.page(pageNo, pageSize)
}
