package controller.admin.order

import model.PayOrder
import logic.PayOrderLogic
import controller.admin.order.dto.PayOrderSubmitRequest
import controller.admin.order.dto.PayOrderDetailVO
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Post
import neton.core.annotations.Body
import neton.core.annotations.PathVariable
import neton.core.annotations.Query

@Controller("/pay/order")
class PayOrderController(private val payOrderLogic: PayOrderLogic) {

    @Get("/get/{id}")
    suspend fun get(@PathVariable id: Long, @Query sync: Boolean? = null): PayOrder? {
        return payOrderLogic.get(id)
    }

    @Get("/get-detail/{id}")
    suspend fun getDetail(@PathVariable id: Long): PayOrderDetailVO? {
        return payOrderLogic.getDetail(id)
    }

    @Post("/submit")
    suspend fun submit(@Body request: PayOrderSubmitRequest): Long {
        val order = PayOrder(
            appId = request.appId,
            merchantOrderId = request.merchantOrderId,
            subject = request.subject,
            body = request.body,
            price = request.price,
            channelCode = request.channelCode,
            userIp = request.userIp,
            expireTime = request.expireTime
        )
        return payOrderLogic.submit(order)
    }

    @Get("/page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query appId: Long? = null,
        @Query channelCode: String? = null,
        @Query merchantOrderId: String? = null,
        @Query status: Int? = null
    ) = payOrderLogic.page(pageNo, pageSize, appId, channelCode, merchantOrderId, status)
}
