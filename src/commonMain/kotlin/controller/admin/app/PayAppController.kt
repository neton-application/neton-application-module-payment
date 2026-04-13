package controller.admin.app

import controller.admin.app.dto.CreatePayAppRequest
import controller.admin.app.dto.UpdatePayAppStatusRequest
import controller.admin.app.dto.UpdatePayAppRequest
import logic.PayAppLogic
import model.PayApp
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
    suspend fun create(@Body request: CreatePayAppRequest): Long {
        return payAppLogic.create(
            PayApp(
                name = request.name,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Put("/update")
    suspend fun update(@Body request: UpdatePayAppRequest) {
        payAppLogic.update(
            PayApp(
                id = request.id,
                name = request.name,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Delete("/delete/{id}")
    suspend fun delete(@PathVariable id: Long) {
        payAppLogic.delete(id)
    }

    @Get("/get/{id}")
    suspend fun get(@PathVariable id: Long): PayApp? {
        return payAppLogic.get(id)
    }

    @Put("/update-status/{id}")
    suspend fun updateStatus(@PathVariable id: Long, @Body req: UpdatePayAppStatusRequest) {
        payAppLogic.updateStatus(id, req.status)
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
