package logic

import controller.admin.channel.dto.CreatePayChannelRequest
import controller.admin.channel.dto.UpdatePayChannelRequest
import dto.PageResponse
import model.PayChannel
import table.PayChannelTable
import neton.core.http.NotFoundException
import neton.database.dsl.*

import neton.logging.Logger

@neton.core.annotations.Logic(logger = "logic.pay-channel")
class PayChannelLogic(
    private val log: Logger
) {

    suspend fun create(request: CreatePayChannelRequest): Long {
        return PayChannelTable.insert(
            PayChannel(
                code = request.code,
                platformCode = request.platformCode,
                method = request.method,
                platformChannelId = request.platformChannelId,
                displayMode = request.displayMode,
                config = request.config,
                status = request.status,
                feeRate = request.feeRate,
                remark = request.remark
            )
        ).id
    }

    suspend fun update(request: UpdatePayChannelRequest) {
        PayChannelTable.get(request.id)
            ?: throw NotFoundException("Pay channel not found")
        PayChannelTable.update(
            PayChannel(
                id = request.id,
                code = request.code,
                platformCode = request.platformCode,
                method = request.method,
                platformChannelId = request.platformChannelId,
                displayMode = request.displayMode,
                config = request.config,
                status = request.status,
                feeRate = request.feeRate,
                remark = request.remark
            )
        )
    }

    suspend fun delete(id: Long) {
        PayChannelTable.get(id)
            ?: throw NotFoundException("Pay channel not found")
        PayChannelTable.destroy(id)
    }

    /** 渠道按 code 全局唯一 —— 单应用部署不需要再按 app 维度分组。 */
    suspend fun getByCode(code: String): PayChannel? =
        PayChannelTable.oneWhere { PayChannel::code eq code }

    /** 启用中的通道，供收银台展示。 */
    suspend fun enabledForApp(): List<PayChannel> =
        PayChannelTable.query {
            where { PayChannel::status eq 1 }
        }.list()

    suspend fun getEnableCodeList(): List<String> =
        PayChannelTable.query {
            where { PayChannel::status eq 1 }
        }.list().map { it.code }

    suspend fun page(
        page: Int,
        size: Int,
        code: String? = null,
        status: Int? = null
    ): PageResponse<PayChannel> {
        val result = PayChannelTable.query {
            where {
                and(
                    whenNotBlank(code) { PayChannel::code eq it },
                    whenPresent(status) { PayChannel::status eq it }
                )
            }
            orderBy(PayChannel::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }
}
