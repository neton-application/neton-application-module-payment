package controller.app.order

import model.PayOrder
import logic.PayOrderLogic
import controller.admin.order.dto.PayOrderSubmitRequest
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Post
import neton.core.annotations.PathVariable

@Controller("/app/pay/order")
class PayOrderController(private val payOrderLogic: PayOrderLogic) {

    @Get("/get/{id}")
    suspend fun get(@PathVariable id: Long): PayOrder? {
        return payOrderLogic.get(id)
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
}
