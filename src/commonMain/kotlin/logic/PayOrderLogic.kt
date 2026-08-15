package logic

import channel.PayDisplayMode
import channel.RefundCommand
import channel.PayPlatform
import channel.PayPlatformRegistry
import channel.PlatformNotifyRequest
import channel.PlatformOrderState
import setting.PaymentSettingKeys
import setting.currentValue
import channel.PrepayResult
import model.PayChannel
import model.PayRefund
import table.PayChannelTable
import table.PayRefundTable
import dto.PageResponse
import model.PayOrder
import event.PayOrderClosedEvent
import event.PayOrderPaidEvent
import event.PayOrderRefundedEvent
import neton.core.event.DomainEventBus
import table.PayOrderTable
import controller.admin.order.dto.PayOrderDetailVO
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.dbContext
import neton.logging.Logger

/**
 * 支付订单编排。下单经渠道 client 拿支付地址；渠道异步回调 → [updateSuccess] 事务内
 * 发布 [PayOrderPaidEvent]，由订阅的业务方模块自行解锁（没有订阅者 = 纯网关行为）。
 *
 * 非 @Logic —— 需要注入 [PayChannelRegistry] 与 [DomainEventBus]，
 * 由 PaymentRuntimeBootstrap 手动装配（KSP @Logic 只支持单 logger）。
 */
class PayOrderLogic(
    private val log: Logger,
    private val platforms: PayPlatformRegistry = PayPlatformRegistry.sandboxOnly(),
    /**
     * 事件总线。**非可空**：写成 `DomainEventBus?` 配上 `events.publish(...)`，
     * 漏注入时发布就是静默空转，下游（如钱包到账）整条不生效且没有任何信号。
     * 空总线本身已经是无副作用的空操作，不需要再用 null 表达一次。
     */
    private val events: DomainEventBus,
    private val db: DbContext = dbContext()
) {

    companion object {
        const val STATUS_WAITING = 0
        const val STATUS_SUCCESS = 1
        const val STATUS_REFUND = 2
        const val STATUS_CLOSED = 3

        /** 退款单状态：1=处理中（异步平台已受理）2=成功。 */
        const val REFUND_STATUS_PROCESSING = 1
        const val REFUND_STATUS_SUCCESS = 2
    }

    /**
     * 下单结果：订单 id + 渠道支付地址（客户端拉起支付）。
     *
     * 必须可序列化：它是下单接口的响应体，少了注解会被当普通对象走 toString，
     * 客户端拿到的是 `PrepayVO(orderId=7, ...)` 这样一串没法解析的文本。
     */
    @kotlinx.serialization.Serializable
    data class PrepayVO(
        val orderId: Long,
        /** 呈现方式：跳转 / 二维码 / SDK 参数，前端据此决定怎么送用户去付款。 */
        val displayMode: PayDisplayMode,
        val payload: String,
        val channelOrderNo: String,
    )

    /**
     * 解析渠道 client，并施加**模拟支付与真实渠道互斥**。
     *
     * 模拟支付开着时，真实渠道一律不可用；关着时，模拟渠道一律不可用。
     * 这条互斥是防止「生产环境误开模拟支付」的主要手段：一旦误开，收款会**立刻全断**，
     * 几分钟内就会被发现，而不是安静地让人白拿商品 —— 后者可能几周都没人察觉。
     * 单纯一个开关做不到这点，因为开着它并不影响真实支付照常工作。
     */
    private suspend fun resolveRoute(channelCode: String?): Route? {
        val code = channelCode ?: return null
        val channel = PayChannelTable.oneWhere { PayChannel::code eq code } ?: return null
        if (channel.status != 1) return null
        val platform = platforms.platform(channel.platformCode) ?: return null
        val mockEnabled = PaymentSettingKeys.MOCK_PAY_ENABLED.currentValue()
        // 用平台自报的 sandbox 属性，而不是判断具体实现类 ——
        // 否则每接一家支付公司都要回来改这里。
        return if (mockEnabled == platform.sandbox) Route(platform, channel) else null
    }

    /** 一次支付要用的两样东西：平台知道"怎么调"，通道提供"用哪套凭据、走哪条线"。 */
    private data class Route(val platform: PayPlatform, val channel: PayChannel)

    /** 下单：落 WAITING 订单 → 调渠道 client 拿支付地址，回写渠道单号。 */
    suspend fun submit(order: PayOrder): PrepayVO {
        val route = resolveRoute(order.channelCode)
            ?: throw BadRequestException("不可用的支付通道: ${order.channelCode}")
        val inserted = PayOrderTable.insert(order.copy(status = STATUS_WAITING))
        val prepay: PrepayResult = route.platform.prepay(inserted, route.channel)
        PayOrderTable.update(inserted.copy(channelOrderNo = prepay.platformOrderNo))
        log.info("pay.order.submit", mapOf("id" to inserted.id, "merchantOrderId" to order.merchantOrderId,
            "channel" to (order.channelCode ?: "")))
        return PrepayVO(inserted.id, prepay.displayMode, prepay.payload, prepay.platformOrderNo)
    }

    /**
     * 渠道回调入口：验签解析 → 命中订单 → 按渠道所报状态推进（幂等）。
     *
     * 只认渠道自己解析出来的状态，不看调用方传了什么 —— 回调地址是公开的。
     */
    suspend fun handleChannelNotify(channelCode: String, request: PlatformNotifyRequest): Boolean {
        val route = resolveRoute(channelCode) ?: return false
        val result = route.platform.parseNotify(request, route.channel) ?: return false
        if (result.state != PlatformOrderState.SUCCESS) return false
        val order = PayOrderTable.oneWhere { PayOrder::merchantOrderId eq result.merchantOrderId }
            ?: throw NotFoundException("订单不存在: ${result.merchantOrderId}")
        if (order.status == STATUS_SUCCESS) return true // 幂等
        updateSuccess(order.id, result.platformOrderNo, result.paidAt ?: return false)
        return true
    }

    /** 回调应答内容，交由渠道决定；渠道不可用时给一个通用失败回执。 */
    suspend fun ackBody(channelCode: String, accepted: Boolean): String =
        resolveRoute(channelCode)?.platform?.ackBody(accepted) ?: "fail"

    suspend fun get(id: Long): PayOrder? {
        return PayOrderTable.get(id)
    }

    suspend fun getDetail(id: Long): PayOrderDetailVO? {
        val order = PayOrderTable.get(id) ?: return null
        return PayOrderDetailVO(
            id = order.id,
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
        channelCode: String? = null,
        merchantOrderId: String? = null,
        status: Int? = null
    ): PageResponse<PayOrder> {
        val result = PayOrderTable.query {
            where {
                and(
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
     * 标记支付成功并在**同一事务内**发布 [PayOrderPaidEvent]。
     * 关键监听者抛异常 → 整个事务回滚，不出现「付了钱没解锁」。
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
            events.publish(PayOrderPaidEvent(paid))
            log.info("pay.order.success", mapOf("id" to id, "channelOrderNo" to channelOrderNo))
        }
    }

    /**
     * 标记已退款并发布 [PayOrderRefundedEvent]，业务方据此回收已发放的权益。
     *
     * 与 [updateSuccess] 一样放进事务：退款和权益回收必须同生共死，
     * 否则会出现「退了钱但 VIP 还在」。原实现既没有事务也不通知任何人。
     */
    suspend fun updateRefund(id: Long) {
        db.transaction {
            val order = PayOrderTable.get(id) ?: return@transaction
            if (order.status == STATUS_REFUND) return@transaction // 幂等
            val refunded = order.copy(status = STATUS_REFUND)
            PayOrderTable.update(refunded)
            events.publish(PayOrderRefundedEvent(refunded))
            log.info("pay.order.refund", mapOf("id" to id))
        }
    }

    /**
     * 关闭已过期的待支付订单，并发布 [PayOrderClosedEvent]。
     *
     * 原先 `expireTime` 字段存了却没人读：订单开出去就永远挂在"待支付"，
     * 既占着 merchantOrderId（业务侧想重新下单会撞唯一键），
     * 后台看到的待支付数也是假的。
     *
     * 逐单独立事务：一批里某单的监听者失败不该把其它单的关闭一起回滚。
     *
     * @return 本次关闭的订单数
     */
    suspend fun closeExpired(now: Long, batchSize: Int = 200): Int {
        val expired = PayOrderTable.query {
            where {
                and(
                    PayOrder::status eq STATUS_WAITING,
                    PayOrder::expireTime lt now,
                )
            }
            orderBy(PayOrder::id.asc())
        }.page(1, batchSize).items

        var closed = 0
        for (order in expired) {
            db.transaction {
                val fresh = PayOrderTable.get(order.id) ?: return@transaction
                if (fresh.status != STATUS_WAITING) return@transaction // 期间被支付/退款了
                val done = fresh.copy(status = STATUS_CLOSED)
                PayOrderTable.update(done)
                events.publish(PayOrderClosedEvent(done))
                closed++
            }
        }
        if (closed > 0) log.info("pay.order.close_expired", mapOf("count" to closed))
        return closed
    }


    /**
     * 对账：把待支付订单拿去渠道查一次，渠道说已支付就补推进。
     *
     * 只处理已经等了一会儿的订单（[settleGraceMillis]）：刚下单的用户可能正在付款页上，
     * 立刻去查只会得到"未支付"，白费一次渠道调用。
     *
     * 沙箱渠道的 queryOrder 返回 null（那边没有可查的真相），因此会被自动跳过。
     *
     * @return 本轮补回的订单数
     */
    suspend fun reconcileWaiting(
        now: Long,
        batchSize: Int = 100,
        settleGraceMillis: Long = 2 * 60 * 1000,
    ): Int {
        val waiting = PayOrderTable.query {
            where {
                and(
                    PayOrder::status eq STATUS_WAITING,
                    PayOrder::createdAt lt (now - settleGraceMillis),
                )
            }
            orderBy(PayOrder::id.asc())
        }.page(1, batchSize).items

        var recovered = 0
        for (order in waiting) {
            val route = resolveRoute(order.channelCode) ?: continue
            val state = try {
                route.platform.queryOrder(order.merchantOrderId, route.channel)
            } catch (e: Exception) {
                // 渠道抖动不该中断整批：记下继续，下一轮再试
                log.warn("pay.order.reconcile.failed", mapOf("id" to order.id, "err" to (e.message ?: "")))
                null
            } ?: continue

            if (state.state == PlatformOrderState.SUCCESS && state.paidAt != null) {
                updateSuccess(order.id, state.platformOrderNo, state.paidAt)
                recovered++
            }
        }
        return recovered
    }


    /**
     * 发起退款：调平台退款接口，登记 pay_refunds，成功则推进订单为已退款。
     *
     * 幂等锚点是 [merchantRefundId]：同一个号重复提交，平台应认作同一笔，
     * 我们这边也先查库直接返回，避免重复扣商户余额。
     *
     * 平台不支持退款（四方普遍如此）时返回 null，调用方据此提示"需线下退款"，
     * 而不是当成失败去重试 —— 重试多少次结果都一样。
     *
     * 异步退款的平台此刻只是"已受理"，订单状态由退款回调推进；
     * 提前置为已退款会导致权益被回收而钱还没到用户手上。
     */
    suspend fun refund(
        merchantOrderId: String,
        merchantRefundId: String,
        refundAmount: Long,
        reason: String? = null,
    ): PayRefund? {
        val existing = PayRefundTable.oneWhere { PayRefund::merchantRefundId eq merchantRefundId }
        if (existing != null) return existing // 幂等：同一退款单号只处理一次

        val order = PayOrderTable.oneWhere { PayOrder::merchantOrderId eq merchantOrderId }
            ?: throw NotFoundException("订单不存在: $merchantOrderId")
        if (order.status != STATUS_SUCCESS) {
            throw BadRequestException("订单未支付成功，不能退款: $merchantOrderId")
        }
        if (refundAmount <= 0 || refundAmount > order.price) {
            throw BadRequestException("退款金额不合法: $refundAmount")
        }

        val route = resolveRoute(order.channelCode)
            ?: throw BadRequestException("不可用的支付通道: ${order.channelCode}")

        val result = route.platform.refund(
            RefundCommand(
                merchantOrderId = merchantOrderId,
                merchantRefundId = merchantRefundId,
                refundAmount = refundAmount,
                originalAmount = order.price,
                reason = reason,
            ),
            route.channel,
        ) ?: return null // 平台不支持退款

        val record = PayRefundTable.insert(
            PayRefund(
                orderId = order.id,
                merchantRefundId = merchantRefundId,
                channelCode = order.channelCode,
                channelRefundNo = result.platformRefundId,
                payPrice = order.price,
                refundPrice = refundAmount,
                reason = reason,
                // 1=处理中 2=成功，与异步平台的"已受理"区分开
                status = if (result.success) REFUND_STATUS_SUCCESS else REFUND_STATUS_PROCESSING,
            )
        )
        // 同步退款的平台此刻已确认，直接推进订单并通知业务方回收权益
        if (result.success) updateRefund(order.id)
        return record
    }

    /** 退款回调：异步退款的平台据此确认结果并推进订单。 */
    suspend fun handleRefundNotify(channelCode: String, request: PlatformNotifyRequest): Boolean {
        val route = resolveRoute(channelCode) ?: return false
        val result = route.platform.parseRefundNotify(request, route.channel) ?: return false
        if (!result.success) return false

        val record = PayRefundTable.oneWhere {
            PayRefund::merchantRefundId eq result.merchantRefundId
        } ?: return false
        if (record.status == REFUND_STATUS_SUCCESS) return true // 幂等

        PayRefundTable.update(record.copy(status = REFUND_STATUS_SUCCESS))
        updateRefund(record.orderId)
        return true
    }

}
