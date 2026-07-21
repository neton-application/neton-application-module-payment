package logic

import channel.PayChannelRegistry
import channel.PrepayResult
import dto.PageResponse
import model.PayOrder
import model.PayApp
import port.PayOrderPaidPort
import table.PayOrderTable
import table.PayAppTable
import controller.admin.order.dto.PayOrderDetailVO
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.dbContext
import neton.logging.Logger

/**
 * 支付订单编排。下单经渠道 client 拿支付地址；渠道异步回调 → [updateSuccess] 事务内
 * 通过 [PayOrderPaidPort] 通知业务方解锁（未装配 = 纯网关行为）。
 *
 * 非 @Logic —— 需要注入 [PayChannelRegistry] 与可选 [PayOrderPaidPort]，
 * 由 PaymentRuntimeBootstrap 手动装配（KSP @Logic 只支持单 logger）。
 */
class PayOrderLogic(
    private val log: Logger,
    private val channels: PayChannelRegistry = PayChannelRegistry.defaultMock(),
    private val paidPort: PayOrderPaidPort? = null,
    private val db: DbContext = dbContext()
) {

    companion object {
        const val STATUS_WAITING = 0
        const val STATUS_SUCCESS = 1
        const val STATUS_REFUND = 2
    }

    /** 下单结果：订单 id + 渠道支付地址（客户端拉起支付）。 */
    data class PrepayVO(val orderId: Long, val payUrl: String, val channelOrderNo: String)

    /** 下单：落 WAITING 订单 → 调渠道 client 拿支付地址，回写渠道单号。 */
    suspend fun submit(order: PayOrder): PrepayVO {
        val client = channels.client(order.channelCode)
            ?: throw BadRequestException("不支持的支付渠道: ${order.channelCode}")
        val inserted = PayOrderTable.insert(order.copy(status = STATUS_WAITING))
        val prepay: PrepayResult = client.prepay(inserted)
        PayOrderTable.update(inserted.copy(channelOrderNo = prepay.channelOrderNo))
        log.info("pay.order.submit", mapOf("id" to inserted.id, "merchantOrderId" to order.merchantOrderId,
            "channel" to (order.channelCode ?: "")))
        return PrepayVO(inserted.id, prepay.payUrl, prepay.channelOrderNo)
    }

    /** 渠道回调入口：验签解析 → 命中订单 → 标记成功（幂等）。 */
    suspend fun handleChannelNotify(channelCode: String, params: Map<String, String>): Boolean {
        val client = channels.client(channelCode) ?: return false
        val result = client.parseNotify(params) ?: return false
        if (!result.success) return false
        val order = PayOrderTable.oneWhere { PayOrder::merchantOrderId eq result.merchantOrderId }
            ?: throw NotFoundException("订单不存在: ${result.merchantOrderId}")
        if (order.status == STATUS_SUCCESS) return true // 幂等
        updateSuccess(order.id, result.channelOrderNo, result.paidAt)
        return true
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

    /**
     * 标记支付成功并在**同一事务内**通知业务方（[PayOrderPaidPort]）。
     * port 实现抛异常 → 整个事务回滚，不出现「付了钱没解锁」。
     */
    suspend fun updateSuccess(id: Long, channelOrderNo: String, successTime: Long) {
        db.transaction {
            val order = PayOrderTable.get(id) ?: return@transaction
            if (order.status == STATUS_SUCCESS) return@transaction // 幂等
            val paid = order.copy(
                status = STATUS_SUCCESS,
                channelOrderNo = channelOrderNo,
                successTime = successTime,
                notifyTime = successTime
            )
            PayOrderTable.update(paid)
            paidPort?.onPaid(paid)
            log.info("pay.order.success", mapOf("id" to id, "channelOrderNo" to channelOrderNo))
        }
    }

    suspend fun updateRefund(id: Long) {
        val order = PayOrderTable.get(id) ?: return
        PayOrderTable.update(order.copy(status = STATUS_REFUND))
        log.info("Pay order refund with id: $id")
    }
}
