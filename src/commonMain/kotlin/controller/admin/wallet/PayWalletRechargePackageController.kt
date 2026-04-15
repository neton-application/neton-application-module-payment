package controller.admin.wallet

import controller.admin.wallet.dto.CreatePayWalletRechargePackageRequest
import controller.admin.wallet.dto.UpdatePayWalletRechargePackageRequest
import logic.PayWalletRechargePackageLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Permission
import neton.core.annotations.Post
import neton.core.annotations.Put
import neton.core.annotations.Delete
import neton.core.annotations.Body
import neton.core.annotations.PathVariable
import neton.core.annotations.Query

@Controller("/pay/wallet-recharge-package")
class PayWalletRechargePackageController(
    private val packageLogic: PayWalletRechargePackageLogic
) {

    @Post("/create")
    @Permission("pay:wallet-recharge-package:create")
    suspend fun create(@Body request: CreatePayWalletRechargePackageRequest): Long =
        packageLogic.create(request)

    @Put("/update")
    @Permission("pay:wallet-recharge-package:update")
    suspend fun update(@Body request: UpdatePayWalletRechargePackageRequest) =
        packageLogic.update(request)

    @Delete("/delete/{id}")
    @Permission("pay:wallet-recharge-package:delete")
    suspend fun delete(@PathVariable id: Long) = packageLogic.delete(id)

    @Get("/get/{id}")
    @Permission("pay:wallet-recharge-package:query")
    suspend fun get(@PathVariable id: Long) = packageLogic.getById(id)

    @Get("/page")
    @Permission("pay:wallet-recharge-package:page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query name: String? = null,
        @Query status: Int? = null
    ) = packageLogic.page(pageNo, pageSize, name, status)
}
