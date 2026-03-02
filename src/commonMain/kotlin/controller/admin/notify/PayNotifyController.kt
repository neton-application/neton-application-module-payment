package controller.admin.notify

import model.PayNotifyTask
import logic.PayNotifyLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get

@Controller("/pay/notify")
class PayNotifyController(private val payNotifyLogic: PayNotifyLogic) {

    @Get("/get-detail")
    suspend fun getDetail(id: Long): PayNotifyTask? {
        return payNotifyLogic.getDetail(id)
    }

    @Get("/page")
    suspend fun page(
        pageNo: Int = 1,
        pageSize: Int = 20,
        appId: Long? = null,
        type: Int? = null,
        status: Int? = null
    ) = payNotifyLogic.page(pageNo, pageSize, appId, type, status)
}
