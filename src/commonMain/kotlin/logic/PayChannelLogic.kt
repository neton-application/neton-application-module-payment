package logic

import controller.admin.channel.dto.CreatePayChannelRequest
import controller.admin.channel.dto.UpdatePayChannelRequest
import dto.PageResponse
import model.PayChannel
import table.PayChannelTable
import neton.core.http.NotFoundException
import neton.database.dsl.*

import neton.logging.Logger

class PayChannelLogic(
    private val log: Logger
) {

    suspend fun create(request: CreatePayChannelRequest): Long {
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

    suspend fun update(request: UpdatePayChannelRequest) {
        PayChannelTable.get(request.id)
            ?: throw NotFoundException("Pay channel not found")
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

    suspend fun delete(id: Long) {
        PayChannelTable.get(id)
            ?: throw NotFoundException("Pay channel not found")
        PayChannelTable.destroy(id)
    }

    suspend fun getByAppAndCode(appId: Long, code: String): PayChannel? {
        return PayChannelTable.oneWhere {
            and(PayChannel::appId eq appId, PayChannel::code eq code)
        }
    }

    suspend fun getEnableCodeList(appId: Long): List<String> {
        return PayChannelTable.query {
            where {
                and(
                    PayChannel::appId eq appId,
                    PayChannel::status eq 1
                )
            }
        }.list().map { it.code }
    }

    suspend fun page(
        page: Int,
        size: Int,
        appId: Long? = null,
        code: String? = null,
        status: Int? = null
    ): PageResponse<PayChannel> {
        val result = PayChannelTable.query {
            where {
                and(
                    whenPresent(appId) { PayChannel::appId eq it },
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
