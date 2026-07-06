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
import kotlin.random.Random

/**
 * 红包（RP-2/RP-3，PrivChat Money Message）。资金真相在订单/领取记录/wallet/ledger/audit；
 * 消息只搬运引用（见 RED_PACKET_AND_TRANSFER_DESIGN_SPEC）。
 *
 * 普通红包（type=0，均分先到先得）+ 拼手气红包（type=1，二倍均值法随机分配 + 最佳手气读时计算）；均支持过期退款。
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

        // 红包类型：0=普通均分（先到先得等额）；1=拼手气（随机分配，最佳手气）。
        const val TYPE_NORMAL = 0
        const val TYPE_LUCKY = 1

        // 并发领取乐观锁 CAS 冲突内部重试上限（超过才把 409 抛给用户）。
        const val MAX_CLAIM_RETRY = 5

        // RP-7-A 通知事件类型（写 pay_money_message_notification_outbox）。
        const val EVENT_RED_PACKET_RECEIVED = "RED_PACKET_RECEIVED"
        const val EVENT_RED_PACKET_EMPTY = "RED_PACKET_EMPTY"
        const val EVENT_RED_PACKET_EXPIRED = "RED_PACKET_EXPIRED"
        // RP-12：卡片注入事件（send 时写，服务端注入红包卡片消息，ref=RED_PACKET:{orderId} 幂等）。
        const val EVENT_RED_PACKET_CARD = "RED_PACKET_CARD"
        const val REF_RED_PACKET = "RED_PACKET"
    }

    private fun now() = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun amountText(fen: Long): String {
        val neg = fen < 0; val a = if (neg) -fen else fen
        val cents = a % 100
        return "${if (neg) "-" else ""}¥${a / 100}.${if (cents < 10) "0$cents" else "$cents"}"
    }

    /**
     * 拼手气分配（二倍均值法 + 保底）。仅在 remainingCount>1 时调用（最后一人在调用方直接拿余额）。
     * 不变量：`1 ≤ amount ≤ remainingAmount-(remainingCount-1)` —— 保证其余每人至少 1 分，
     * 从而领后 `remainingAmount' ≥ remainingCount'`，递归到最后一人恰好拿走余额，Σ 精确等于 total。
     * 前置（由 send 校验 totalAmount≥totalCount + 上述不变量维持）：remainingAmount ≥ remainingCount。
     */
    private fun luckyDraw(remainingAmount: Long, remainingCount: Int): Long {
        val reserve = (remainingCount - 1).toLong()               // 其余每人至少保留 1 分
        val meanX2 = remainingAmount / remainingCount * 2          // 二倍均值上界（防止前面的人拿太多）
        val maxDraw = minOf(meanX2, remainingAmount - reserve).coerceAtLeast(1L)
        return if (maxDraw <= 1L) 1L else Random.nextLong(1L, maxDraw + 1L)  // [1, maxDraw]
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

    /**
     * RP-12 卡片注入 outbox 入队（send 时，同事务）。带 `(event_type, ref_type, ref_id)` 唯一键，
     * `ON CONFLICT DO NOTHING` → 同一订单至多一张卡片（幂等第一闸）。relatedUserId=卡片发送方 uid。
     */
    private suspend fun enqueueCardOutbox(
        eventType: String, refType: String, refId: Long, channelId: String, scene: Int, senderUid: Long, payload: String,
    ) {
        val ts = now()
        db.execute(
            "INSERT INTO pay_money_message_notification_outbox " +
                "(event_type, channel_id, scene, red_packet_id, related_user_id, target_user_id, payload_json, ref_type, ref_id, status, retry_count, next_retry_at, created_at, updated_at) " +
                "VALUES (:event_type, :channel_id, :scene, :red_packet_id, :sender, 0, :payload, :ref_type, :ref_id, 0, 0, 0, :ts, :ts) " +
                "ON CONFLICT (event_type, ref_type, ref_id) WHERE ref_id IS NOT NULL DO NOTHING",
            mapOf(
                "event_type" to eventType, "channel_id" to channelId, "scene" to scene,
                "red_packet_id" to refId, "sender" to senderUid, "payload" to payload,
                "ref_type" to refType, "ref_id" to refId, "ts" to ts,
            ),
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
        requireParam(type == TYPE_NORMAL || type == TYPE_LUCKY) { "red packet type must be 0 (normal) or 1 (lucky): $type" }
        val senderWallet = wallet.getWalletByUserId(senderUserId)
            ?: walletNotFound("wallet not found for user $senderUserId")

        val ts = now()
        val order = RedPacketOrderTable.insert(
            RedPacketOrder(
                senderUserId = senderUserId,
                channelId = channelId,
                scene = scene,
                type = type,
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
        // RP-12：同事务写卡片注入 outbox（服务端注入红包卡片，客户端不再自发）。payload 由 platform 从订单组装。
        val cardPayload = buildJsonObject {
            put("redPacketId", order.id.toString())
            put("scene", if (scene == 1) "group" else "dm")
            put("title", greeting?.takeIf { it.isNotBlank() } ?: "恭喜发财，大吉大利")
            put("status", "active")
            put("senderUserId", senderUserId)
            put("type", type)
            put("totalCount", totalCount)
        }.toString()
        enqueueCardOutbox(EVENT_RED_PACKET_CARD, REF_RED_PACKET, order.id, channelId, scene, senderUserId, cardPayload)
        // RP-12 同步注入主路径：卡片注入成功才算红包发送成功（同事务把 outbox 标 SENT，job 只兜底）；
        // 注入失败抛异常 → 本事务回滚（不扣款不建单），客户端得到「发送失败」。injector 未装配 → 退回 outbox 异步。
        MoneyMessageInjection.injector?.let { inj ->
            val messageId = inj.injectCard(
                EVENT_RED_PACKET_CARD, REF_RED_PACKET, order.id, channelId, senderUserId, cardPayload,
            )
            markCardOutboxSent(EVENT_RED_PACKET_CARD, REF_RED_PACKET, order.id)
            log.info("red_packet.card_injected", mapOf("id" to order.id, "messageId" to messageId))
        }
        log.info("red_packet.sent", mapOf("id" to order.id, "sender" to senderUserId, "amount" to totalAmount))
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

    /** 乐观锁 CAS 未命中（并发抢红包高频正常事件）——内部信号，用于自动重试，不外泄为业务错误。 */
    private class OptimisticClaimConflict : RuntimeException()

    /**
     * 领红包。多人并发抢红包时乐观锁 CAS 冲突是**正常高频事件**，不应直接把 409 抛给用户：
     * 内部对 CAS 未命中自动重试（每次重读订单快照重算），仅真正抢完/已领过/已过期才返回业务错误。
     * 资金安全不变：每次尝试都是一个独立的「快照→守卫更新」原子事务，绝不基于陈旧快照落库。
     */
    suspend fun claim(redPacketId: Long, userId: Long): RedPacketClaim {
        var attempt = 0
        while (true) {
            try {
                return claimOnce(redPacketId, userId)
            } catch (e: OptimisticClaimConflict) {
                if (++attempt >= MAX_CLAIM_RETRY) {
                    log.warn("red_packet.claim.conflict_exhausted", mapOf("id" to redPacketId, "attempts" to attempt))
                    walletConflict("red packet claim conflict; please retry: $redPacketId")
                }
            }
        }
    }

    /**
     * 单次领取尝试：一事务内乐观锁原子递减 remaining + 唯一键防重领 + 入账(401)。
     * 已抢完/已领过/已过期 → 409（不落 500）。过期先惰性结算退款再拒。CAS 未命中 → OptimisticClaimConflict（外层重试）。
     */
    private suspend fun claimOnce(redPacketId: Long, userId: Long): RedPacketClaim = db.transaction {
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

        // 分配金额（金额由观测快照算出，与下面乐观锁 remaining_count 守卫同快照 → 并发下要么整体成功要么冲突重试，
        // 绝不基于陈旧快照落库，因此不会超领/负剩余）。最后一份一律拿走余额，保证 Σ=total。
        //  · 普通(type=0)：等额 total/count；
        //  · 拼手气(type=1)：二倍均值法随机分配（每人≥1分，其余人保底）。
        val claimAmount = when {
            order.remainingCount == 1 -> order.remainingAmount
            order.type == TYPE_LUCKY -> luckyDraw(order.remainingAmount, order.remainingCount)
            else -> order.totalAmount / order.totalCount
        }
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
        if (updated == 0L) throw OptimisticClaimConflict()

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
