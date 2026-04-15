package controller.admin.refund

import model.PayRefund
import logic.PayRefundLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.PathVariable
import neton.core.annotations.Permission
import neton.core.annotations.Query

@Controller("/pay/refund")
class PayRefundController(private val payRefundLogic: PayRefundLogic) {

    @Get("/get/{id}")
    @Permission("pay:refund:query")
    suspend fun get(@PathVariable id: Long): PayRefund? {
        return payRefundLogic.get(id)
    }

    @Get("/page")
    @Permission("pay:refund:page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query appId: Long? = null,
        @Query channelCode: String? = null,
        @Query merchantRefundId: String? = null,
        @Query status: Int? = null
    ) = payRefundLogic.page(pageNo, pageSize, appId, channelCode, merchantRefundId, status)
}
