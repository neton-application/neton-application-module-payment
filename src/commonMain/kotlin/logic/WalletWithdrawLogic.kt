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
        op: OperatorContext,
        bankCardId: Long,
        amount: Long,
        currency: String = "CNY",
    ): WalletWithdrawOrder {
        val userId = op.operatorId
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
            audit(order.id, op, "create", -1, SM.PENDING, null)
            log.info("withdraw.created", mapOf("orderId" to order.id, "userId" to userId, "amount" to amount))
            WalletWithdrawOrderTable.get(order.id)!!
        }
    }

    /** 用户取消（仅本人、仅 PENDING）：解冻。 */
    suspend fun cancel(op: OperatorContext, orderId: Long): WalletWithdrawOrder = db.transaction {
        val userId = op.operatorId
        val order = requireOrder(orderId)
        if (order.userId != userId) walletNotFound("not your order: $orderId")
        SM.ensureCanCancel(order.status)
        transit(orderId, order.status, SM.CANCELLED) { }
        payWallet.unfreezeInTx(order.walletId, order.amount, order.id, "withdraw unfreeze (cancel) #${order.id}")
        audit(orderId, op, "cancel", order.status, SM.CANCELLED, null)
        requireOrder(orderId)
    }

    /** 后台审核通过（PENDING→APPROVED）。不动资金。 */
    suspend fun approve(op: OperatorContext, orderId: Long, remark: String?): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanApprove(order.status)
        transit(orderId, order.status, SM.APPROVED) {
            set(WalletWithdrawOrder::reviewerId, op.operatorId)
            set(WalletWithdrawOrder::reviewRemark, remark)
            set(WalletWithdrawOrder::reviewedAt, nowMillis())
        }
        audit(orderId, op, "approve", order.status, SM.APPROVED, remark)
        requireOrder(orderId)
    }

    /** 后台驳回（PENDING→REJECTED）：解冻 + 客户可见原因。 */
    suspend fun reject(op: OperatorContext, orderId: Long, userVisibleReason: String): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanReject(order.status)
        transit(orderId, order.status, SM.REJECTED) {
            set(WalletWithdrawOrder::reviewerId, op.operatorId)
            set(WalletWithdrawOrder::freezeRemarkUserVisible, userVisibleReason)
            set(WalletWithdrawOrder::reviewedAt, nowMillis())
        }
        payWallet.unfreezeInTx(order.walletId, order.amount, order.id, "withdraw unfreeze (reject) #${order.id}")
        audit(orderId, op, "reject", order.status, SM.REJECTED, userVisibleReason)
        requireOrder(orderId)
    }

    /** 后台标记已打款（APPROVED/PROCESSING→PAID）：从冻结实扣。 */
    suspend fun markPaid(op: OperatorContext, orderId: Long, payoutTradeNo: String?): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanMarkPaid(order.status)
        transit(orderId, order.status, SM.PAID) {
            set(WalletWithdrawOrder::reviewerId, op.operatorId)
            set(WalletWithdrawOrder::payoutTradeNo, payoutTradeNo)
            set(WalletWithdrawOrder::paidAt, nowMillis())
        }
        payWallet.deductFrozenInTx(order.walletId, order.amount, order.id, "withdraw paid #${order.id}")
        audit(orderId, op, "mark_paid", order.status, SM.PAID, payoutTradeNo)
        requireOrder(orderId)
    }

    /** 后台标记失败（APPROVED/PROCESSING→FAILED）：解冻。 */
    suspend fun markFailed(op: OperatorContext, orderId: Long, failureReason: String): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanMarkFailed(order.status)
        transit(orderId, order.status, SM.FAILED) {
            set(WalletWithdrawOrder::reviewerId, op.operatorId)
            set(WalletWithdrawOrder::failureReason, failureReason)
            // 用户可见原因（App/H5 读 freezeRemarkUserVisible）；同 reject，失败原因也要让用户看到。
            set(WalletWithdrawOrder::freezeRemarkUserVisible, failureReason)
        }
        payWallet.unfreezeInTx(order.walletId, order.amount, order.id, "withdraw unfreeze (failed) #${order.id}")
        audit(orderId, op, "mark_failed", order.status, SM.FAILED, failureReason)
        requireOrder(orderId)
    }

    /**
     * 后台挂起（PENDING/APPROVED/PROCESSING → ON_HOLD）。**不动资金**：钱继续冻着，
     * 阻塞解除后回到 [WalletWithdrawOrder.holdResumeTo] 继续走完（spec §10）。
     *
     * 用户可见文案不入库，只存 [reasonCode] + [reasonParams]，由各端按 locale 渲染。
     */
    suspend fun hold(
        op: OperatorContext,
        orderId: Long,
        reasonCode: String,
        reasonParams: String?,
        internalNote: String?,
    ): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanHold(order.status)
        val resumeTo = order.status
        transit(orderId, order.status, SM.ON_HOLD) {
            set(WalletWithdrawOrder::holdResumeTo, resumeTo)
            set(WalletWithdrawOrder::holdReasonCode, reasonCode)
            set(WalletWithdrawOrder::holdReasonParams, reasonParams)
            set(WalletWithdrawOrder::holdNoteInternal, internalNote)
            set(WalletWithdrawOrder::holdAt, nowMillis())
            set(WalletWithdrawOrder::holdBy, op.operatorId)
        }
        audit(orderId, op, "hold", resumeTo, SM.ON_HOLD, reasonCode)
        requireOrder(orderId)
    }

    /**
     * 后台解除挂起（ON_HOLD → 挂起前的在途状态）。同样不动资金。
     * 解除后清空挂起痕迹，避免界面出现「待打款 + 红字卡住原因」这种自相矛盾的状态。
     */
    suspend fun unhold(op: OperatorContext, orderId: Long, internalNote: String?): WalletWithdrawOrder = db.transaction {
        val order = requireOrder(orderId)
        SM.ensureCanUnhold(order.status)
        val resumeTo = order.holdResumeTo
        SM.ensureValidResumeTarget(resumeTo)
        transit(orderId, order.status, resumeTo) {
            set(WalletWithdrawOrder::holdResumeTo, 0)
            set(WalletWithdrawOrder::holdReasonCode, null)
            set(WalletWithdrawOrder::holdReasonParams, null)
            set(WalletWithdrawOrder::holdNoteInternal, internalNote)
            set(WalletWithdrawOrder::holdAt, 0L)
            set(WalletWithdrawOrder::holdBy, 0L)
        }
        audit(orderId, op, "unhold", SM.ON_HOLD, resumeTo, internalNote)
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
        op: OperatorContext,
        action: String,
        beforeStatus: Int,
        afterStatus: Int,
        remark: String?,
    ) {
        WalletWithdrawAuditLogTable.insert(
            WalletWithdrawAuditLog(
                orderId = orderId,
                operatorId = op.operatorId,
                action = action,
                beforeStatus = beforeStatus,
                afterStatus = afterStatus,
                remark = remark,
                operatorName = op.operatorName,
                operatorRole = op.operatorRole,
                ip = op.ip,
                userAgent = op.userAgent,
                traceId = op.traceId,
            )
        )
    }
}

private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
