package controller.admin.notify

import model.PayNotifyTask
import logic.PayNotifyLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Permission

@Controller("/pay/notify")
class PayNotifyController(private val payNotifyLogic: PayNotifyLogic) {

    @Get("/get-detail")
    @Permission("pay:notify:query")
    suspend fun getDetail(id: Long): PayNotifyTask? {
        return payNotifyLogic.getDetail(id)
    }

    @Get("/page")
    @Permission("pay:notify:page")
    suspend fun page(
        pageNo: Int = 1,
        pageSize: Int = 20,
        appId: Long? = null,
        type: Int? = null,
        status: Int? = null
    ) = payNotifyLogic.page(pageNo, pageSize, appId, type, status)
}
