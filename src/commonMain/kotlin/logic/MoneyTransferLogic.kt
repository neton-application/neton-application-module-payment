package logic

import dto.PageResponse
import model.MoneyTransferOrder
import model.PaySensitiveAuditLog
import table.MoneyTransferOrderTable
import table.PaySensitiveAuditLogTable
import neton.database.api.DbContext
import neton.database.dbContext
import neton.database.dsl.*
import neton.logging.Logger
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 转账（RP-2，PrivChat Money Message）。无需接收确认：一事务内完成发送方扣款(500) +
 * 接收方入账(501) + 订单 + 审计 + 卡片注入 outbox。成功即终态。资金真相在订单/双边 ledger/audit；
 * 消息由服务端注入（RP-12），客户端不再自发。
 */
@neton.core.annotations.Logic(logger = "logic.money-transfer")
class MoneyTransferLogic(
    private val log: Logger,
    private val db: DbContext = dbContext(),
) {
    private val wallet = PayWalletLogic(log, db)

    companion object {
        const val STATUS_SUCCESS = 0
        const val STATUS_REFUNDED = 1
        // RP-12：卡片注入事件（transfer 时写，ref=MONEY_TRANSFER:{orderId} 幂等）。
        const val EVENT_MONEY_TRANSFER_CARD = "MONEY_TRANSFER_CARD"
        const val REF_MONEY_TRANSFER = "MONEY_TRANSFER"
    }

    private fun now() = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun amountText(fen: Long): String {
        val neg = fen < 0; val a = if (neg) -fen else fen
        val cents = a % 100
        return "${if (neg) "-" else ""}¥${a / 100}.${if (cents < 10) "0$cents" else "$cents"}"
    }

    /** 转账：校验发送方 available≥amount，一事务内扣发送方 + 入账接收方（懒建收方钱包）+ 审计。 */
    suspend fun transfer(
        fromUserId: Long,
        toUserId: Long,
        channelId: String,
        amount: Long,
        remark: String?,
    ): MoneyTransferOrder = db.transaction {
        requireParam(amount > 0) { "amount must be positive: $amount" }
        requireParam(fromUserId != toUserId) { "cannot transfer to self" }
        val fromWallet = wallet.getWalletByUserId(fromUserId)
            ?: walletNotFound("wallet not found for user $fromUserId")

        val order = MoneyTransferOrderTable.insert(
            MoneyTransferOrder(
                fromUserId = fromUserId,
                toUserId = toUserId,
                channelId = channelId,
                amount = amount,
                remark = remark,
                status = STATUS_SUCCESS,
                createdAt = now(),
            )
        )
        // 扣发送方(500) → 入账接收方(501)，同一事务，biz_id=transfer_id。
        wallet.applyMoneyMoveInTx(fromWallet.id, -amount, PayWalletLogic.BIZ_TYPE_TRANSFER_OUT, order.id, "transfer_out")
        val toWallet = wallet.getOrCreateWalletInTx(toUserId)
        wallet.applyMoneyMoveInTx(toWallet.id, amount, PayWalletLogic.BIZ_TYPE_TRANSFER_IN, order.id, "transfer_in")
        audit(order.id, fromUserId, toUserId, amount)
        // RP-12：同事务写卡片注入 outbox（服务端注入转账卡片，客户端不再自发）。payload 由 platform 从订单组装。
        val cardPayload = buildJsonObject {
            put("transferId", order.id.toString())
            put("title", "转账")
            put("summary", remark ?: "")
            put("status", "success")
            put("fromUserId", fromUserId)
            put("toUserId", toUserId)
            put("amountText", amountText(amount))
        }.toString()
        enqueueCardOutbox(order.id, channelId, fromUserId, cardPayload)
        // RP-12 同步注入主路径：注入成功才算转账成功（同事务把 outbox 标 SENT，job 只兜底）；
        // 注入失败抛异常 → 本事务回滚（不扣款不建单），客户端得到「发送失败」。injector 未装配 → 退回 outbox 异步。
        MoneyMessageInjection.injector?.let { inj ->
            val messageId = inj.injectCard(
                EVENT_MONEY_TRANSFER_CARD, REF_MONEY_TRANSFER, order.id, channelId, fromUserId, cardPayload,
            )
            markCardOutboxSent(EVENT_MONEY_TRANSFER_CARD, REF_MONEY_TRANSFER, order.id)
            log.info("money_transfer.card_injected", mapOf("id" to order.id, "messageId" to messageId))
        }
        log.info("money_transfer.done", mapOf("id" to order.id, "from" to fromUserId, "to" to toUserId, "amount" to amount))
        order
    }

    /** 同步注入成功后同事务把 outbox 行标 SENT（job 不再重发；行为审计保留）。 */
    private suspend fun markCardOutboxSent(eventType: String, refType: String, refId: Long) {
        val ts = now()
        db.execute(
            "UPDATE pay_money_message_notification_outbox SET status=1, sent_at=$ts, updated_at=$ts " +
                "WHERE event_type=:event_type AND ref_type=:ref_type AND ref_id=:ref_id",
            mapOf("event_type" to eventType, "ref_type" to refType, "ref_id" to refId),
        )
    }

    /**
     * RP-12 卡片注入 outbox 入队（transfer 时，同事务）。`(event_type, ref_type, ref_id)` 唯一键
     * ON CONFLICT DO NOTHING → 同一转账单至多一张卡片（幂等第一闸）。relatedUserId=发送方 uid。
     */
    private suspend fun enqueueCardOutbox(transferId: Long, channelId: String, fromUserId: Long, payload: String) {
        val ts = now()
        db.execute(
            "INSERT INTO pay_money_message_notification_outbox " +
                "(event_type, channel_id, scene, red_packet_id, related_user_id, target_user_id, payload_json, ref_type, ref_id, status, retry_count, next_retry_at, created_at, updated_at) " +
                "VALUES (:event_type, :channel_id, 0, 0, :sender, 0, :payload, :ref_type, :ref_id, 0, 0, 0, :ts, :ts) " +
                "ON CONFLICT (event_type, ref_type, ref_id) WHERE ref_id IS NOT NULL DO NOTHING",
            mapOf(
                "event_type" to EVENT_MONEY_TRANSFER_CARD, "channel_id" to channelId, "sender" to fromUserId,
                "payload" to payload, "ref_type" to REF_MONEY_TRANSFER, "ref_id" to transferId, "ts" to ts,
            ),
        )
    }

    suspend fun detail(transferId: Long): MoneyTransferOrder? = MoneyTransferOrderTable.get(transferId)

    suspend fun pageMine(userId: Long, page: Int, size: Int): PageResponse<MoneyTransferOrder> {
        val result = MoneyTransferOrderTable.query {
            where { or(MoneyTransferOrder::fromUserId eq userId, MoneyTransferOrder::toUserId eq userId) }
            orderBy(MoneyTransferOrder::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    private suspend fun audit(transferId: Long, fromUserId: Long, toUserId: Long, amount: Long) {
        PaySensitiveAuditLogTable.insert(
            PaySensitiveAuditLog(
                operatorId = fromUserId,
                operatorRole = "user",
                action = "MONEY_TRANSFER",
                targetType = "MONEY_TRANSFER",
                targetId = transferId,
                targetUserId = toUserId,
                reason = "amount=$amount from=$fromUserId to=$toUserId",
            )
        )
    }
}
