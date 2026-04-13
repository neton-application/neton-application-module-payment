package controller.admin.channel

import controller.admin.channel.dto.CreatePayChannelRequest
import controller.admin.channel.dto.UpdatePayChannelRequest
import model.PayChannel
import table.PayChannelTable
import neton.database.dsl.*
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Post
import neton.core.annotations.Put
import neton.core.annotations.Delete
import neton.core.annotations.Body
import neton.core.annotations.PathVariable
import neton.core.annotations.Query

@Controller("/pay/channel")
class PayChannelController {

    @Post("/create")
    suspend fun create(@Body request: CreatePayChannelRequest): Long {
        return PayChannelTable.insert(
            PayChannel(
                appId = request.appId,
                code = request.code,
                config = request.config,
                status = request.status,
                feeRate = request.feeRate,
                remark = request.remark
            )
        ).id
    }

    @Put("/update")
    suspend fun update(@Body request: UpdatePayChannelRequest) {
        PayChannelTable.update(
            PayChannel(
                id = request.id,
                appId = request.appId,
                code = request.code,
                config = request.config,
                status = request.status,
                feeRate = request.feeRate,
                remark = request.remark
            )
        )
    }

    @Delete("/delete/{id}")
    suspend fun delete(@PathVariable id: Long) {
        PayChannelTable.destroy(id)
    }

    @Get("/get-by-app-and-code")
    suspend fun getByAppAndCode(@Query appId: Long, @Query code: String): PayChannel? {
        return PayChannelTable.oneWhere {
            and(PayChannel::appId eq appId, PayChannel::code eq code)
        }
    }

    @Get("/get-enable-code-list")
    suspend fun getEnableCodeList(@Query appId: Long): List<String> {
        return PayChannelTable.query {
            where {
                and(
                    PayChannel::appId eq appId,
                    PayChannel::status eq 0
                )
            }
        }.list().map { it.code }
    }

    @Get("/page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query appId: Long? = null,
        @Query code: String? = null,
        @Query status: Int? = null
    ) = PayChannelTable.query {
        where {
            and(
                whenPresent(appId) { PayChannel::appId eq it },
                whenNotBlank(code) { PayChannel::code eq it },
                whenPresent(status) { PayChannel::status eq it }
            )
        }
        orderBy(PayChannel::id.desc())
    }.page(pageNo, pageSize)
}
