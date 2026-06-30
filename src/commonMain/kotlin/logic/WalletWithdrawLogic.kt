package logic

import dto.PageResponse
import model.UserBankCard
import model.WalletWithdrawAuditLog
import model.WalletWithdrawOrder
import table.UserBankCardTable
import table.WalletWithdrawAuditLogTable
import table.WalletWithdrawOrderTable
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.api.UpdateScope
import neton.database.dbContext
import neton.logging.Logger

/**
 * 提现订单与资金流转（P4-C）。第一版人工打款。
 *
 * 事务边界（spec §4）每个动作严格单事务：
 *  - 创建：建单 PENDING + freeze + WITHDRAW_FREEZE ledger
 *  - 驳回/取消/失败：乐观锁流转 + unfreeze + WITHDRAW_UNFREEZE ledger + audit
 *  - 打款：乐观锁流转 PAID + deductFrozen + WITHDRAW_DEDUCT ledger + audit
 * 资金动作复用 [PayWalletLogic] 的 *InTx 版（不自开事务，并入本事务）。
 */
@neton.core.annotations.Logic(logger = "logic.wallet-withdraw")
class WalletWithdrawLogic(
    private val log: Logger,
    private val db: DbContext = dbContext(),
) {
    private val payWallet = PayWalletLogic(log, db)
    private val SM = WithdrawStateMachine

    /** 用户提交提现：同一事务内 建单 PENDING + 冻结资金 + ledger。fee 第一版为 0。 */
    suspend fun createWithdrawOrder(
        userId: Long,
        bankCardId: Long,
        amount: Long,
        currency: String = "CNY",
    ): WalletWithdrawOrder {
        requireParam(amount > 0) { "amount must be positive: $amount" }
        val wallet = payWallet.getWalletByUserId(userId)
            ?: walletNotFound("wallet not found for user $userId")
        // 银行卡必须属于本人且有效。
        UserBankCardTable.oneWhere {
            and(
                UserBankCard::id eq bankCardId,
                UserBankCard::userId eq userId,
                UserBankCard::deletedAt eq 0L,
            )
        } ?: walletNotFound("bank card not found or not yours: $bankCardId")

        val fee = 0L
        return db.transaction {
            val order = WalletWithdrawOrderTable.insert(
                WalletWithdrawOrder(
                    userId = userId,
                    walletId = wallet.id,
                    bankCardId = bankCardId,
                    amount = amount,
                    fee = fee,
                    actualAmount = amount - fee,
                    currency = currency,
                    status = SM.PENDING,
                )
            )
            // 冻结校验 available = balance - freeze_price >= amount（freezeInTx 内做），并入本事务。
            payWallet.freezeInTx(wallet.id, amount, order.id, "withdraw freeze #${order.id}")
            audit(order.id, userId, "create", -1, SM.PENDING, null)
            log.info("withdraw.created", mapOf("orderId" to order.id, "userId" to userId, "amount" to amount))
            WalletWithdrawOrderTable.get(order.id)!!
        }
    }

    /** 用户取消（仅本人、仅 PENDING）：解冻。 */
    suspend fun cancel(userId: Long, orderId: Long): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        if (order.userId != userId) walletNotFound("not your order: $orderId")
        SM.ensureCanCancel(order.status)
        transit(orderId, order.status, SM.CANCELLED) { }
        payWallet.unfreezeInTx(order.walletId, order.amount, order.id, "withdraw unfreeze (cancel) #${order.id}")
        audit(orderId, userId, "cancel", order.status, SM.CANCELLED, null)
        requireOrder(orderId)
    }

    /** 后台审核通过（PENDING→APPROVED）。不动资金。 */
    suspend fun approve(operatorId: Long, orderId: Long, remark: String?): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanApprove(order.status)
        transit(orderId, order.status, SM.APPROVED) {
            set(WalletWithdrawOrder::reviewerId, operatorId)
            set(WalletWithdrawOrder::reviewRemark, remark)
            set(WalletWithdrawOrder::reviewedAt, nowMillis())
        }
        audit(orderId, operatorId, "approve", order.status, SM.APPROVED, remark)
        requireOrder(orderId)
    }

    /** 后台驳回（PENDING→REJECTED）：解冻 + 客户可见原因。 */
    suspend fun reject(operatorId: Long, orderId: Long, userVisibleReason: String): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanReject(order.status)
        transit(orderId, order.status, SM.REJECTED) {
            set(WalletWithdrawOrder::reviewerId, operatorId)
            set(WalletWithdrawOrder::freezeRemarkUserVisible, userVisibleReason)
            set(WalletWithdrawOrder::reviewedAt, nowMillis())
        }
        payWallet.unfreezeInTx(order.walletId, order.amount, order.id, "withdraw unfreeze (reject) #${order.id}")
        audit(orderId, operatorId, "reject", order.status, SM.REJECTED, userVisibleReason)
        requireOrder(orderId)
    }

    /** 后台标记已打款（APPROVED/PROCESSING→PAID）：从冻结实扣。 */
    suspend fun markPaid(operatorId: Long, orderId: Long, payoutTradeNo: String?): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanMarkPaid(order.status)
        transit(orderId, order.status, SM.PAID) {
            set(WalletWithdrawOrder::reviewerId, operatorId)
            set(WalletWithdrawOrder::payoutTradeNo, payoutTradeNo)
            set(WalletWithdrawOrder::paidAt, nowMillis())
        }
        payWallet.deductFrozenInTx(order.walletId, order.amount, order.id, "withdraw paid #${order.id}")
        audit(orderId, operatorId, "mark_paid", order.status, SM.PAID, payoutTradeNo)
        requireOrder(orderId)
    }

    /** 后台标记失败（APPROVED/PROCESSING→FAILED）：解冻。 */
    suspend fun markFailed(operatorId: Long, orderId: Long, failureReason: String): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanMarkFailed(order.status)
        transit(orderId, order.status, SM.FAILED) {
            set(WalletWithdrawOrder::reviewerId, operatorId)
            set(WalletWithdrawOrder::failureReason, failureReason)
        }
        payWallet.unfreezeInTx(order.walletId, order.amount, order.id, "withdraw unfreeze (failed) #${order.id}")
        audit(orderId, operatorId, "mark_failed", order.status, SM.FAILED, failureReason)
        requireOrder(orderId)
    }

    // ---------- queries ----------

    /** 我的提现订单（倒序）。 */
    suspend fun listMyWithdrawOrders(userId: Long, page: Int, size: Int): PageResponse<WalletWithdrawOrder> {
        val result = WalletWithdrawOrderTable.query {
            where { WalletWithdrawOrder::userId eq userId }
            orderBy(WalletWithdrawOrder::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    /** 我的提现详情（必须本人）。 */
    suspend fun getMyDetail(userId: Long, orderId: Long): WalletWithdrawOrder? =
        WalletWithdrawOrderTable.get(orderId)?.takeIf { it.userId == userId }

    /** 后台提现订单分页（可按状态/用户筛选）。 */
    suspend fun pageWithdrawOrders(
        page: Int,
        size: Int,
        status: Int? = null,
        userId: Long? = null,
    ): PageResponse<WalletWithdrawOrder> {
        val result = WalletWithdrawOrderTable.query {
            where {
                and(
                    whenPresent(status) { WalletWithdrawOrder::status eq it },
                    whenPresent(userId) { WalletWithdrawOrder::userId eq it },
                )
            }
            orderBy(WalletWithdrawOrder::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    /** 后台提现详情。 */
    suspend fun getDetail(orderId: Long): WalletWithdrawOrder? = WalletWithdrawOrderTable.get(orderId)

    // ---------- helpers ----------

    private suspend fun requireOrder(orderId: Long): WalletWithdrawOrder =
        WalletWithdrawOrderTable.get(orderId)
            ?: walletNotFound("withdraw order not found: $orderId")

    /** 乐观锁状态流转：仅当当前 status==expected 才更新；否则抛并发冲突。 */
    private suspend fun transit(
        orderId: Long,
        expected: Int,
        next: Int,
        extra: UpdateScope<WalletWithdrawOrder>.() -> Unit,
    ) {
        val updated = WalletWithdrawOrderTable.query {
            where {
                and(
                    WalletWithdrawOrder::id eq orderId,
                    WalletWithdrawOrder::status eq expected,
                )
            }
        }.update {
            set(WalletWithdrawOrder::status, next)
            extra()
        }
        if (updated == 0L) {
            walletConflict("withdraw order $orderId state changed concurrently; please retry")
        }
    }

    private suspend fun audit(
        orderId: Long,
        operatorId: Long,
        action: String,
        beforeStatus: Int,
        afterStatus: Int,
        remark: String?,
    ) {
        WalletWithdrawAuditLogTable.insert(
            WalletWithdrawAuditLog(
                orderId = orderId,
                operatorId = operatorId,
                action = action,
                beforeStatus = beforeStatus,
                afterStatus = afterStatus,
                remark = remark,
            )
        )
    }
}

private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
