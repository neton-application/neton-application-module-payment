package controller.admin.transfer

import model.PayTransfer
import table.PayTransferTable
import neton.database.dsl.*

import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.PathVariable
import neton.core.annotations.Query

@Controller("/pay/transfer")
class PayTransferController {

    @Get("/get/{id}")
    suspend fun get(@PathVariable id: Long): PayTransfer? {
        return PayTransferTable.get(id)
    }

    @Get("/page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query appId: Long? = null,
        @Query channelCode: String? = null,
        @Query merchantTransferId: String? = null,
        @Query status: Int? = null
    ) = PayTransferTable.query {
        where {
            and(
                whenPresent(appId) { PayTransfer::appId eq it },
                whenNotBlank(channelCode) { PayTransfer::channelCode eq it },
                whenNotBlank(merchantTransferId) { PayTransfer::merchantTransferId eq it },
                whenPresent(status) { PayTransfer::status eq it }
            )
        }
        orderBy(PayTransfer::id.desc())
    }.page(pageNo, pageSize)
}
