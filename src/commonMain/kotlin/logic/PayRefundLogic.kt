package logic

import dto.PageResponse
import model.PayRefund
import table.PayRefundTable
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.dbContext
import neton.logging.Logger

class PayRefundLogic(
    private val log: Logger,
    private val db: DbContext = dbContext()
) {

    suspend fun get(id: Long): PayRefund? {
        return PayRefundTable.get(id)
    }

    suspend fun page(
        page: Int,
        size: Int,
        appId: Long? = null,
        channelCode: String? = null,
        merchantRefundId: String? = null,
        status: Int? = null
    ): PageResponse<PayRefund> {
        val result = PayRefundTable.query {
            where {
                and(
                    whenPresent(appId) { PayRefund::appId eq it },
                    whenNotBlank(channelCode) { PayRefund::channelCode eq it },
                    whenNotBlank(merchantRefundId) { PayRefund::merchantRefundId eq it },
                    whenPresent(status) { PayRefund::status eq it }
                )
            }
            orderBy(PayRefund::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }
}
