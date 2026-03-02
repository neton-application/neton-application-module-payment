package controller.admin.app

import model.PayApp
import controller.admin.app.dto.AppUpdateStatusReqVO
import logic.PayAppLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Post
import neton.core.annotations.Put
import neton.core.annotations.Delete
import neton.core.annotations.Body
import neton.core.annotations.PathVariable
import neton.core.annotations.Query

@Controller("/pay/app")
class PayAppController(private val payAppLogic: PayAppLogic) {

    @Post("/create")
    suspend fun create(@Body app: PayApp): Long {
        return payAppLogic.create(app)
    }

    @Put("/update")
    suspend fun update(@Body app: PayApp) {
        payAppLogic.update(app)
    }

    @Delete("/delete/{id}")
    suspend fun delete(@PathVariable id: Long) {
        payAppLogic.delete(id)
    }

    @Get("/get/{id}")
    suspend fun get(@PathVariable id: Long): PayApp? {
        return payAppLogic.get(id)
    }

    @Put("/update-status")
    suspend fun updateStatus(@Body req: AppUpdateStatusReqVO) {
        payAppLogic.updateStatus(req.id, req.status)
    }

    @Get("/list")
    suspend fun list(@Query name: String? = null, @Query status: Int? = null) =
        payAppLogic.list(name, status)

    @Get("/page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query name: String? = null,
        @Query status: Int? = null
    ) = payAppLogic.page(pageNo, pageSize, name, status)
}
