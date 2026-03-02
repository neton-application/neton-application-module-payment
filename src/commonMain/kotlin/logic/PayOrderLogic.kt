package logic

import dto.PageResponse
import model.PayOrder
import model.PayApp
import table.PayOrderTable
import table.PayAppTable
import controller.admin.order.dto.PayOrderDetailVO
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.dbContext
import neton.logging.Logger

class PayOrderLogic(
    private val log: Logger,
    private val db: DbContext = dbContext()
) {

    companion object {
        const val STATUS_WAITING = 0
        const val STATUS_SUCCESS = 1
        const val STATUS_REFUND = 2
    }

    suspend fun submit(order: PayOrder): Long {
        // Create the order with WAITING status
        val newOrder = order.copy(status = STATUS_WAITING)
        val inserted = PayOrderTable.insert(newOrder)
        log.info("Submitted pay order with id: ${inserted.id}, merchantOrderId: ${order.merchantOrderId}")
        // TODO: Call payment channel to initiate payment
        return inserted.id
    }

    suspend fun get(id: Long): PayOrder? {
        return PayOrderTable.get(id)
    }

    suspend fun getDetail(id: Long): PayOrderDetailVO? {
        val order = PayOrderTable.get(id) ?: return null
        val app = PayAppTable.get(order.appId)
        return PayOrderDetailVO(
            id = order.id,
            appId = order.appId,
            appName = app?.name,
            merchantOrderId = order.merchantOrderId,
            subject = order.subject,
            body = order.body,
            price = order.price,
            channelCode = order.channelCode,
            channelOrderNo = order.channelOrderNo,
            status = order.status,
            userIp = order.userIp,
            expireTime = order.expireTime,
            successTime = order.successTime,
            notifyTime = order.notifyTime,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt
        )
    }

    suspend fun page(
        page: Int,
        size: Int,
        appId: Long? = null,
        channelCode: String? = null,
        merchantOrderId: String? = null,
        status: Int? = null
    ): PageResponse<PayOrder> {
        val result = PayOrderTable.query {
            where {
                and(
                    whenPresent(appId) { PayOrder::appId eq it },
                    whenNotBlank(channelCode) { PayOrder::channelCode eq it },
                    whenNotBlank(merchantOrderId) { PayOrder::merchantOrderId eq it },
                    whenPresent(status) { PayOrder::status eq it }
                )
            }
            orderBy(PayOrder::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    suspend fun updateSuccess(id: Long, channelOrderNo: String, successTime: Long) {
        val order = PayOrderTable.get(id) ?: return
        PayOrderTable.update(order.copy(
            status = STATUS_SUCCESS,
            channelOrderNo = channelOrderNo,
            successTime = successTime
        ))
        log.info("Pay order success with id: $id, channelOrderNo: $channelOrderNo")
    }

    suspend fun updateRefund(id: Long) {
        val order = PayOrderTable.get(id) ?: return
        PayOrderTable.update(order.copy(status = STATUS_REFUND))
        log.info("Pay order refund with id: $id")
    }
}
