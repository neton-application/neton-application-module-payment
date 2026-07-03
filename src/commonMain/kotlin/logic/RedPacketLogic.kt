package logic

import dto.PageResponse
import model.MoneyMessageNotificationOutbox
import model.PaySensitiveAuditLog
import model.RedPacketClaim
import model.RedPacketOrder
import table.MoneyMessageNotificationOutboxTable
import table.PaySensitiveAuditLogTable
import table.RedPacketClaimTable
import table.RedPacketOrderTable
import neton.database.api.DbContext
import neton.database.dbContext
import neton.database.dsl.*
import neton.logging.Logger
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 红包（RP-2/RP-3，PrivChat Money Message）。资金真相在订单/领取记录/wallet/ledger/audit；
 * 消息只搬运引用（见 RED_PACKET_AND_TRANSFER_DESIGN_SPEC）。
 *
 * MVP：普通红包（均分、先到先得、过期退款）；拼手气(type=1) 预留未实现。
 * 每个动作单事务 + `PayWalletLogic.applyMoneyMoveInTx` 幂等动钱，合「订单 + 动钱 + 审计」为原子。
 * 防超领双闸：① 条件更新 `remaining_count=observed`（乐观锁原子递减）② (red_packet_id,user_id) 唯一键。
 */
@neton.core.annotations.Logic(logger = "logic.red-packet")
class RedPacketLogic(
    private val log: Logger,
    private val db: DbContext = dbContext(),
) {
    // 资金动作复用 PayWalletLogic 的 InTx 版（同 db → 事务合并），与 WalletWithdrawLogic 同模式。
    private val wallet = PayWalletLogic(log, db)

    companion object {
        const val STATUS_ACTIVE = 0
        const val STATUS_FINISHED = 1
        const val STATUS_EXPIRED = 2
        const val STATUS_REFUNDING = 3
        const val DEFAULT_TTL_MS = 24L * 60 * 60 * 1000 // 24h

        // RP-7-A 通知事件类型（写 money_message_notification_outbox）。
        const val EVENT_RED_PACKET_RECEIVED = "RED_PACKET_RECEIVED"
        const val EVENT_RED_PACKET_EMPTY = "RED_PACKET_EMPTY"
        const val EVENT_RED_PACKET_EXPIRED = "RED_PACKET_EXPIRED"
    }

    private fun now() = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun amountText(fen: Long): String {
        val neg = fen < 0; val a = if (neg) -fen else fen
        val cents = a % 100
        return "${if (neg) "-" else ""}¥${a / 100}.${if (cents < 10) "0$cents" else "$cents"}"
    }

    /** 通知 outbox 入队（RP-7-A）。在调用方事务内执行 → 与资金动作同事务原子；status=PENDING 待 adapter 消费。 */
    private suspend fun enqueueOutbox(
        eventType: String, order: RedPacketOrder, relatedUserId: Long, targetUserId: Long, payload: String,
    ) {
        val ts = now()
        MoneyMessageNotificationOutboxTable.insert(
            MoneyMessageNotificationOutbox(
                eventType = eventType,
                channelId = order.channelId,
                scene = order.scene,
                redPacketId = order.id,
                relatedUserId = relatedUserId,
                targetUserId = targetUserId,
                payloadJson = payload,
                status = 0, // PENDING
                createdAt = ts,
                updatedAt = ts,
            )
        )
    }

    /** 发红包：校验 available≥total，一事务内建单 + 扣发送方全额(400) + 审计。返回 redPacketId。 */
    suspend fun send(
        senderUserId: Long,
        channelId: String,
        scene: Int,
        type: Int,
        totalAmount: Long,
        totalCount: Int,
        greeting: String?,
    ): RedPacketOrder = db.transaction {
        requireParam(totalAmount > 0) { "totalAmount must be positive: $totalAmount" }
        requireParam(totalCount in 1..1000) { "totalCount out of range: $totalCount" }
        requireParam(totalAmount >= totalCount) { "totalAmount must cover 1 fen per share" }
        requireParam(type == 0) { "only normal red packet supported in MVP (type=0)" }
        val senderWallet = wallet.getWalletByUserId(senderUserId)
            ?: walletNotFound("wallet not found for user $senderUserId")

        val ts = now()
        val order = RedPacketOrderTable.insert(
            RedPacketOrder(
                senderUserId = senderUserId,
                channelId = channelId,
                scene = scene,
                type = 0,
                totalAmount = totalAmount,
                totalCount = totalCount,
                remainingAmount = totalAmount,
                remainingCount = totalCount,
                status = STATUS_ACTIVE,
                greeting = greeting,
                expireAt = ts + DEFAULT_TTL_MS,
                createdAt = ts,
            )
        )
        // 扣发送方全额进托管（ledger 400，biz_id=red_packet_id）。
        wallet.applyMoneyMoveInTx(
            senderWallet.id, -totalAmount,
            PayWalletLogic.BIZ_TYPE_RED_PACKET_CREATE_DEDUCT, order.id, "red_packet_send"
        )
        audit("RED_PACKET_SEND", order.id, senderUserId, "amount=$totalAmount count=$totalCount")
        log.info("red_packet.sent", mapOf("id" to order.id, "sender" to senderUserId, "amount" to totalAmount))
        order
    }

    /**
     * 领红包：一事务内乐观锁原子递减 remaining + 唯一键防重领 + 入账(401)。
     * 已抢完/已领过/已过期 → 409（不落 500）。过期先惰性结算退款再拒。
     */
    suspend fun claim(redPacketId: Long, userId: Long): RedPacketClaim = db.transaction {
        val order = RedPacketOrderTable.get(redPacketId)
            ?: walletNotFound("red packet not found: $redPacketId")

        // 惰性过期结算：过期且仍 ACTIVE → 退款并拒领。
        if (order.status == STATUS_ACTIVE && now() > order.expireAt) {
            refundExpiredInTx(order)
            walletConflict("red packet expired: $redPacketId")
        }
        requireState(order.status == STATUS_ACTIVE) { "red packet not claimable (status=${order.status})" }
        requireState(order.remainingCount > 0) { "red packet drained: $redPacketId" }

        // 防重复领取：快路径预检（唯一键为并发硬兜底）。
        val existed = RedPacketClaimTable.oneWhere {
            and(RedPacketClaim::redPacketId eq redPacketId, RedPacketClaim::userId eq userId)
        }
        if (existed != null) walletConflict("already claimed: $redPacketId")

        // 均分（先到先得）：最后一份拿走余额（含整除余数），保证 Σ=total。
        val claimAmount = if (order.remainingCount == 1) order.remainingAmount
        else order.totalAmount / order.totalCount
        val nextCount = order.remainingCount - 1
        val nextRemaining = order.remainingAmount - claimAmount
        val finished = nextCount == 0

        // 乐观锁原子递减：仅当 remaining_count 仍等于观测值时更新，否则并发冲突。
        val updated = RedPacketOrderTable.query {
            where {
                and(
                    RedPacketOrder::id eq redPacketId,
                    RedPacketOrder::status eq STATUS_ACTIVE,
                    RedPacketOrder::remainingCount eq order.remainingCount,
                )
            }
        }.update {
            set(RedPacketOrder::remainingCount, nextCount)
            set(RedPacketOrder::remainingAmount, nextRemaining)
            set(RedPacketOrder::status, if (finished) STATUS_FINISHED else STATUS_ACTIVE)
            if (finished) set(RedPacketOrder::finishedAt, now())
        }
        if (updated == 0L) walletConflict("red packet claim conflict; please retry: $redPacketId")

        val claim = RedPacketClaimTable.insert(
            RedPacketClaim(redPacketId = redPacketId, userId = userId, amount = claimAmount, claimedAt = now())
        )
        // 入账领取方（ledger 401，biz_id=claim_id）。
        val claimerWallet = wallet.getOrCreateWalletInTx(userId)
        wallet.applyMoneyMoveInTx(
            claimerWallet.id, claimAmount,
            PayWalletLogic.BIZ_TYPE_RED_PACKET_RECEIVE_INCOME, claim.id, "red_packet_claim"
        )
        audit("RED_PACKET_CLAIM", redPacketId, userId, "amount=$claimAmount claimId=${claim.id}")

        // RP-7-A：同事务写通知 outbox。领取 → RECEIVED；若领完 → 再补 EMPTY。
        val claimedCount = order.totalCount - nextCount
        enqueueOutbox(
            EVENT_RED_PACKET_RECEIVED, order, relatedUserId = userId, targetUserId = order.senderUserId,
            payload = buildJsonObject {
                put("redPacketId", order.id.toString())
                put("channelId", order.channelId)
                put("claimerUserId", userId)
                put("senderUserId", order.senderUserId)
                put("claimAmount", claimAmount)
                put("amountText", amountText(claimAmount))
                put("claimedCount", claimedCount)
                put("totalCount", order.totalCount)
                put("status", if (finished) "FINISHED" else "ACTIVE")
            }.toString(),
        )
        if (finished) {
            enqueueOutbox(
                EVENT_RED_PACKET_EMPTY, order, relatedUserId = order.senderUserId, targetUserId = 0,
                payload = buildJsonObject {
                    put("redPacketId", order.id.toString())
                    put("channelId", order.channelId)
                    put("senderUserId", order.senderUserId)
                    put("claimedCount", order.totalCount)
                    put("totalCount", order.totalCount)
                    put("status", "FINISHED")
                }.toString(),
            )
        }
        log.info("red_packet.claimed", mapOf("id" to redPacketId, "user" to userId, "amount" to claimAmount))
        claim
    }

    /** 过期退款（惰性触发 or 定时扫描）：抢占 ACTIVE→REFUNDING，退剩余给发送方(402)，置 EXPIRED。幂等。 */
    suspend fun expireRefund(redPacketId: Long): Boolean = db.transaction {
        val order = RedPacketOrderTable.get(redPacketId)
            ?: walletNotFound("red packet not found: $redPacketId")
        if (order.status != STATUS_ACTIVE) return@transaction false
        refundExpiredInTx(order)
    }

    /** 扫描所有已过期仍 ACTIVE 的红包并退款（定时任务兜底）。返回处理条数。 */
    suspend fun sweepExpired(limit: Int = 200): Int {
        val expired = RedPacketOrderTable.query {
            where { and(RedPacketOrder::status eq STATUS_ACTIVE, RedPacketOrder::expireAt lt now()) }
            orderBy(RedPacketOrder::id.asc())
        }.page(1, limit).items
        var n = 0
        for (o in expired) if (expireRefund(o.id)) n++
        if (n > 0) log.info("red_packet.sweep_expired", mapOf("count" to n))
        return n
    }

    /** 在调用方事务内退款（抢占防并发重复退）。返回是否本次执行了退款。 */
    private suspend fun refundExpiredInTx(order: RedPacketOrder): Boolean {
        // 抢占：ACTIVE→REFUNDING，仅一个执行者成功。
        val claimed = RedPacketOrderTable.query {
            where { and(RedPacketOrder::id eq order.id, RedPacketOrder::status eq STATUS_ACTIVE) }
        }.update { set(RedPacketOrder::status, STATUS_REFUNDING) }
        if (claimed == 0L) return false // 别人已在退 / 已终态

        val remaining = order.remainingAmount
        if (remaining > 0) {
            val senderWallet = wallet.getOrCreateWalletInTx(order.senderUserId)
            wallet.applyMoneyMoveInTx(
                senderWallet.id, remaining,
                PayWalletLogic.BIZ_TYPE_RED_PACKET_REFUND, order.id, "red_packet_refund"
            )
        }
        RedPacketOrderTable.query { where { RedPacketOrder::id eq order.id } }.update {
            set(RedPacketOrder::status, STATUS_EXPIRED)
            set(RedPacketOrder::remainingAmount, 0L)
            set(RedPacketOrder::remainingCount, 0)
            set(RedPacketOrder::finishedAt, now())
        }
        audit("RED_PACKET_REFUND", order.id, order.senderUserId, "refund=$remaining")

        // RP-7-A：过期退款 → EXPIRED 通知 outbox（同事务）。
        enqueueOutbox(
            EVENT_RED_PACKET_EXPIRED, order, relatedUserId = order.senderUserId, targetUserId = 0,
            payload = buildJsonObject {
                put("redPacketId", order.id.toString())
                put("channelId", order.channelId)
                put("senderUserId", order.senderUserId)
                put("refundAmount", remaining)
                put("amountText", amountText(remaining))
                put("status", "EXPIRED")
            }.toString(),
        )
        log.info("red_packet.refunded", mapOf("id" to order.id, "refund" to remaining))
        return true
    }

    suspend fun detail(redPacketId: Long): RedPacketOrder? {
        val order = RedPacketOrderTable.get(redPacketId) ?: return null
        // 惰性过期结算。
        if (order.status == STATUS_ACTIVE && now() > order.expireAt) {
            expireRefund(redPacketId)
            return RedPacketOrderTable.get(redPacketId)
        }
        return order
    }

    suspend fun listClaims(redPacketId: Long): List<RedPacketClaim> =
        RedPacketClaimTable.query {
            where { RedPacketClaim::redPacketId eq redPacketId }
            orderBy(RedPacketClaim::id.asc())
        }.list()

    suspend fun pageMine(userId: Long, page: Int, size: Int): PageResponse<RedPacketOrder> {
        val result = RedPacketOrderTable.query {
            where { RedPacketOrder::senderUserId eq userId }
            orderBy(RedPacketOrder::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    private suspend fun audit(action: String, rpId: Long, userId: Long, reason: String) {
        PaySensitiveAuditLogTable.insert(
            PaySensitiveAuditLog(
                operatorId = userId,
                operatorRole = "user",
                action = action,
                targetType = "RED_PACKET",
                targetId = rpId,
                targetUserId = userId,
                reason = reason,
            )
        )
    }
}
