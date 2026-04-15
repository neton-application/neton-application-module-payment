package controller.admin.channel

import controller.admin.channel.dto.CreatePayChannelRequest
import controller.admin.channel.dto.UpdatePayChannelRequest
import logic.PayChannelLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Permission
import neton.core.annotations.Post
import neton.core.annotations.Put
import neton.core.annotations.Delete
import neton.core.annotations.Body
import neton.core.annotations.PathVariable
import neton.core.annotations.Query

@Controller("/pay/channel")
class PayChannelController(
    private val channelLogic: PayChannelLogic
) {

    @Post("/create")
    @Permission("pay:channel:create")
    suspend fun create(@Body request: CreatePayChannelRequest): Long = channelLogic.create(request)

    @Put("/update")
    @Permission("pay:channel:update")
    suspend fun update(@Body request: UpdatePayChannelRequest) = channelLogic.update(request)

    @Delete("/delete/{id}")
    @Permission("pay:channel:delete")
    suspend fun delete(@PathVariable id: Long) = channelLogic.delete(id)

    @Get("/get-by-app-and-code")
    @Permission("pay:channel:query")
    suspend fun getByAppAndCode(@Query appId: Long, @Query code: String) =
        channelLogic.getByAppAndCode(appId, code)

    @Get("/get-enable-code-list")
    @Permission("pay:channel:query")
    suspend fun getEnableCodeList(@Query appId: Long) = channelLogic.getEnableCodeList(appId)

    @Get("/page")
    @Permission("pay:channel:page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query appId: Long? = null,
        @Query code: String? = null,
        @Query status: Int? = null
    ) = channelLogic.page(pageNo, pageSize, appId, code, status)
}
