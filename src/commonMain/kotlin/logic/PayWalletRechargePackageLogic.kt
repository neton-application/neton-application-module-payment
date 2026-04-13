package logic

import controller.admin.wallet.dto.CreatePayWalletRechargePackageRequest
import controller.admin.wallet.dto.UpdatePayWalletRechargePackageRequest
import dto.PageResponse
import model.PayWalletRechargePackage
import table.PayWalletRechargePackageTable
import neton.core.http.NotFoundException
import neton.database.dsl.*

import neton.logging.Logger

class PayWalletRechargePackageLogic(
    private val log: Logger
) {

    suspend fun create(request: CreatePayWalletRechargePackageRequest): Long {
        return PayWalletRechargePackageTable.insert(
            PayWalletRechargePackage(
                name = request.name,
                payPrice = request.payPrice,
                bonusPrice = request.bonusPrice,
                status = request.status
            )
        ).id
    }

    suspend fun update(request: UpdatePayWalletRechargePackageRequest) {
        PayWalletRechargePackageTable.get(request.id)
            ?: throw NotFoundException("Pay wallet recharge package not found")
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

    suspend fun delete(id: Long) {
        PayWalletRechargePackageTable.get(id)
            ?: throw NotFoundException("Pay wallet recharge package not found")
        PayWalletRechargePackageTable.destroy(id)
    }

    suspend fun getById(id: Long): PayWalletRechargePackage? {
        return PayWalletRechargePackageTable.get(id)
    }

    suspend fun page(
        page: Int,
        size: Int,
        name: String? = null,
        status: Int? = null
    ): PageResponse<PayWalletRechargePackage> {
        val result = PayWalletRechargePackageTable.query {
            where {
                and(
                    whenNotBlank(name) { PayWalletRechargePackage::name like "%$it%" },
                    whenPresent(status) { PayWalletRechargePackage::status eq it }
                )
            }
            orderBy(PayWalletRechargePackage::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }
}
