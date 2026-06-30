package logic

import dto.PageResponse
import model.PayWallet
import model.PayWalletTransaction
import model.PayWalletRecharge
import table.PayWalletTable
import table.PayWalletTransactionTable
import table.PayWalletRechargeTable
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.dbContext
import neton.logging.Logger

@neton.core.annotations.Logic(logger = "logic.pay-wallet")
class PayWalletLogic(
    private val log: Logger,
    private val db: DbContext = dbContext()
) {

    companion object {
        const val BIZ_TYPE_RECHARGE = 1
        const val BIZ_TYPE_RECHARGE_REFUND = 2
        const val BIZ_TYPE_ADMIN_ADJUST = 200
        // 提现资金动作（P4-B0）。这些 biz_type 在 pay_wallet_transactions 上有
        // (biz_type, biz_id) 部分唯一索引保证幂等（V003）；biz_id = 提现单 id。
        const val BIZ_TYPE_WITHDRAW_FREEZE = 300
        const val BIZ_TYPE_WITHDRAW_UNFREEZE = 301
        const val BIZ_TYPE_WITHDRAW_DEDUCT = 302
        const val BIZ_TYPE_WITHDRAW_REFUND = 303
    }

    private suspend fun requireWalletByUserId(userId: Long): PayWallet {
        return PayWalletTable.oneWhere { PayWallet::userId eq userId }
            ?: throw IllegalArgumentException("Wallet not found for userId: $userId")
    }

    suspend fun getWallet(userId: Long): PayWallet? {
        return PayWalletTable.oneWhere {
            PayWallet::userId eq userId
        }
    }

    suspend fun getWalletById(id: Long): PayWallet? {
        return PayWalletTable.get(id)
    }

    suspend fun getWalletByUserId(userId: Long): PayWallet? {
        return PayWalletTable.oneWhere { PayWallet::userId eq userId }
    }

    suspend fun adjustBalance(userId: Long, balance: Long) {
        val wallet = requireWalletByUserId(userId)
        val diff = balance - wallet.balance
        updateBalance(wallet.id, diff, BIZ_TYPE_ADMIN_ADJUST, 0L, "Admin adjust")
    }

    suspend fun updateBalance(walletId: Long, price: Long, bizType: Int, bizId: Long, title: String) {
        db.transaction {
            applyBalanceUpdate(walletId, price, bizType, bizId, title)
        }
    }

    private suspend fun applyBalanceUpdate(walletId: Long, price: Long, bizType: Int, bizId: Long, title: String) {
        val wallet = PayWalletTable.get(walletId)
            ?: walletNotFound("Wallet not found: $walletId")

        val newBalance = wallet.balance + price
        if (newBalance < 0) {
            throw IllegalArgumentException("Insufficient balance for wallet: $walletId")
        }
        // 普通借记不得侵占被冻结资金（R2：可用余额 = balance − freezePrice）。
        // freezePrice 默认 0 时此判定退化为无影响，对存量消费路径向后兼容。
        // 提现实扣走 deductFrozen（同时减 balance 与 freezePrice），不经此路径。
        if (price < 0 && !PayWalletFreezeRules.debitKeepsFrozenSafe(newBalance, wallet.freezePrice)) {
            throw IllegalArgumentException(
                "Insufficient available balance (frozen funds protected) for wallet: $walletId"
            )
        }

        // Update wallet balance
        val updatedWallet = if (price > 0) {
            wallet.copy(
                balance = newBalance,
                totalRecharge = wallet.totalRecharge + price
            )
        } else {
            wallet.copy(
                balance = newBalance,
                totalExpense = wallet.totalExpense + (-price)
            )
        }
        PayWalletTable.update(updatedWallet)

        // Create transaction record
        val transaction = PayWalletTransaction(
            walletId = walletId,
            bizType = bizType,
            bizId = bizId,
            title = title,
            price = price,
            balance = newBalance
        )
        PayWalletTransactionTable.insert(transaction)
        log.info("wallet.balance.updated", mapOf("walletId" to walletId, "price" to price, "newBalance" to newBalance))
    }

    // ==================== 提现资金动作（P4-B0）====================
    // 提现专用 money movers：申请只冻结、打款才实扣。提现绝不走 raw updateBalance。
    // 每个动作单独事务 + (biz_type, biz_id=提现单id) 幂等：重复调用是 no-op，
    // 并发由 V003 部分唯一索引兜底。

    // public 版各自开事务（独立调用）；xxxInTx 版不开事务，供调用方（如提现建单）
    // 把「建单 + 冻结 + 审计」合进同一事务——表操作走协程内环境事务（与 applyBalanceUpdate 同）。

    /** 冻结资金（创建提现单时）：校验 available≥amount，freezePrice+=amount，balance 不变。幂等。 */
    suspend fun freeze(walletId: Long, amount: Long, bizId: Long, title: String) =
        db.transaction { freezeInTx(walletId, amount, bizId, title) }

    suspend fun freezeInTx(walletId: Long, amount: Long, bizId: Long, title: String) {
        if (withdrawLedgerExists(BIZ_TYPE_WITHDRAW_FREEZE, bizId)) return
        val wallet = PayWalletTable.get(walletId)
            ?: walletNotFound("Wallet not found: $walletId")
        PayWalletFreezeRules.ensureCanFreeze(wallet.balance, wallet.freezePrice, amount)
        PayWalletTable.update(wallet.copy(freezePrice = wallet.freezePrice + amount))
        insertWithdrawLedger(walletId, BIZ_TYPE_WITHDRAW_FREEZE, bizId, title, price = 0, balance = wallet.balance)
        log.info("wallet.freeze", mapOf("walletId" to walletId, "amount" to amount, "bizId" to bizId))
    }

    /** 解冻资金（驳回/取消/打款失败）：freezePrice-=amount，balance 不变。幂等。 */
    suspend fun unfreeze(walletId: Long, amount: Long, bizId: Long, title: String) =
        db.transaction { unfreezeInTx(walletId, amount, bizId, title) }

    suspend fun unfreezeInTx(walletId: Long, amount: Long, bizId: Long, title: String) {
        if (withdrawLedgerExists(BIZ_TYPE_WITHDRAW_UNFREEZE, bizId)) return
        val wallet = PayWalletTable.get(walletId)
            ?: walletNotFound("Wallet not found: $walletId")
        PayWalletFreezeRules.ensureCanUnfreeze(wallet.freezePrice, amount)
        PayWalletTable.update(wallet.copy(freezePrice = wallet.freezePrice - amount))
        insertWithdrawLedger(walletId, BIZ_TYPE_WITHDRAW_UNFREEZE, bizId, title, price = 0, balance = wallet.balance)
        log.info("wallet.unfreeze", mapOf("walletId" to walletId, "amount" to amount, "bizId" to bizId))
    }

    /** 从冻结实扣（打款成功）：balance-=amount 且 freezePrice-=amount，可用余额不变。幂等。 */
    suspend fun deductFrozen(walletId: Long, amount: Long, bizId: Long, title: String) =
        db.transaction { deductFrozenInTx(walletId, amount, bizId, title) }

    suspend fun deductFrozenInTx(walletId: Long, amount: Long, bizId: Long, title: String) {
        if (withdrawLedgerExists(BIZ_TYPE_WITHDRAW_DEDUCT, bizId)) return
        val wallet = PayWalletTable.get(walletId)
            ?: walletNotFound("Wallet not found: $walletId")
        PayWalletFreezeRules.ensureCanDeductFrozen(wallet.balance, wallet.freezePrice, amount)
        val newBalance = wallet.balance - amount
        PayWalletTable.update(
            wallet.copy(
                balance = newBalance,
                freezePrice = wallet.freezePrice - amount,
                totalExpense = wallet.totalExpense + amount
            )
        )
        insertWithdrawLedger(walletId, BIZ_TYPE_WITHDRAW_DEDUCT, bizId, title, price = -amount, balance = newBalance)
        log.info("wallet.deductFrozen", mapOf("walletId" to walletId, "amount" to amount, "bizId" to bizId))
    }

    private suspend fun withdrawLedgerExists(bizType: Int, bizId: Long): Boolean =
        PayWalletTransactionTable.oneWhere {
            and(
                PayWalletTransaction::bizType eq bizType,
                PayWalletTransaction::bizId eq bizId
            )
        } != null

    private suspend fun insertWithdrawLedger(
        walletId: Long, bizType: Int, bizId: Long, title: String, price: Long, balance: Long
    ) {
        PayWalletTransactionTable.insert(
            PayWalletTransaction(
                walletId = walletId,
                bizType = bizType,
                bizId = bizId,
                title = title,
                price = price,
                balance = balance
            )
        )
    }

    suspend fun pageWallets(
        page: Int,
        size: Int,
        userId: Long? = null
    ): PageResponse<PayWallet> {
        val result = PayWalletTable.query {
            where {
                whenPresent(userId) { PayWallet::userId eq it }
            }
            orderBy(PayWallet::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    suspend fun pageTransactions(
        page: Int,
        size: Int,
        walletId: Long? = null,
        bizType: Int? = null
    ): PageResponse<PayWalletTransaction> {
        val result = PayWalletTransactionTable.query {
            where {
                and(
                    whenPresent(walletId) { PayWalletTransaction::walletId eq it },
                    whenPresent(bizType) { PayWalletTransaction::bizType eq it }
                )
            }
            orderBy(PayWalletTransaction::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    suspend fun recharge(recharge: PayWalletRecharge): Long {
        val inserted = PayWalletRechargeTable.insert(recharge)
        log.info("wallet.recharge.created", mapOf("rechargeId" to inserted.id, "walletId" to recharge.walletId))
        return inserted.id
    }

    suspend fun rechargeForUser(
        userId: Long,
        totalPrice: Long,
        payPrice: Long,
        bonusPrice: Long = 0,
        packageId: Long? = null
    ): Long {
        require(payPrice <= totalPrice) { "payPrice ($payPrice) must not exceed totalPrice ($totalPrice)" }
        val wallet = requireWalletByUserId(userId)
        val recharge = PayWalletRecharge(
            walletId = wallet.id,
            totalPrice = totalPrice,
            payPrice = payPrice,
            bonusPrice = bonusPrice,
            packageId = packageId
        )
        return recharge(recharge)
    }

    suspend fun pageRecharges(
        page: Int,
        size: Int,
        walletId: Long? = null,
        payStatus: Int? = null
    ): PageResponse<PayWalletRecharge> {
        val result = PayWalletRechargeTable.query {
            where {
                and(
                    whenPresent(walletId) { PayWalletRecharge::walletId eq it },
                    whenPresent(payStatus) { PayWalletRecharge::payStatus eq it }
                )
            }
            orderBy(PayWalletRecharge::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    suspend fun getRecharge(id: Long): PayWalletRecharge? {
        return PayWalletRechargeTable.get(id)
    }

    suspend fun markRechargePaid(id: Long, payChannelCode: String) {
        db.transaction {
            val recharge = PayWalletRechargeTable.get(id)
                ?: throw IllegalArgumentException("Recharge not found: $id")
            if (recharge.payStatus == PayWalletRechargeStateMachine.PAY_STATUS_PAID) {
                return@transaction
            }
            PayWalletRechargeStateMachine.ensureCanMarkPaid(recharge)

            // Optimistic lock: only update if payStatus is still the expected value.
            // If a concurrent request already set payStatus = PAID, this returns 0 and we abort.
            val updated = PayWalletRechargeTable.query {
                where {
                    and(
                        PayWalletRecharge::id eq id,
                        PayWalletRecharge::payStatus eq recharge.payStatus
                    )
                }
            }.update {
                set(PayWalletRecharge::payStatus, PayWalletRechargeStateMachine.PAY_STATUS_PAID)
                set(PayWalletRecharge::payChannelCode, payChannelCode)
            }
            if (updated == 0L) {
                throw IllegalStateException("Recharge $id was concurrently modified. Please retry.")
            }

            applyBalanceUpdate(
                walletId = recharge.walletId,
                price = recharge.totalPrice,
                bizType = BIZ_TYPE_RECHARGE,
                bizId = recharge.id,
                title = "Wallet recharge"
            )
        }
    }

    suspend fun markRechargeRefundRequested(id: Long) {
        db.transaction {
            val recharge = PayWalletRechargeTable.get(id)
                ?: throw IllegalArgumentException("Recharge not found: $id")
            PayWalletRechargeStateMachine.ensureCanRequestRefund(recharge)
            PayWalletRechargeTable.update(
                recharge.copy(
                    refundStatus = PayWalletRechargeStateMachine.REFUND_STATUS_REQUESTED,
                    refundTotalPrice = recharge.totalPrice
                )
            )
        }
    }

    suspend fun markRechargeRefunded(id: Long) {
        db.transaction {
            val recharge = PayWalletRechargeTable.get(id)
                ?: throw IllegalArgumentException("Recharge not found: $id")
            if (recharge.refundStatus == PayWalletRechargeStateMachine.REFUND_STATUS_REFUNDED) {
                return@transaction
            }
            PayWalletRechargeStateMachine.ensureCanMarkRefunded(recharge)

            // Optimistic lock: only update if refundStatus hasn't changed concurrently.
            val updated = PayWalletRechargeTable.query {
                where {
                    and(
                        PayWalletRecharge::id eq id,
                        PayWalletRecharge::refundStatus eq recharge.refundStatus
                    )
                }
            }.update {
                set(PayWalletRecharge::refundStatus, PayWalletRechargeStateMachine.REFUND_STATUS_REFUNDED)
            }
            if (updated == 0L) {
                throw IllegalStateException("Recharge $id refund was concurrently modified. Please retry.")
            }

            applyBalanceUpdate(
                walletId = recharge.walletId,
                price = -recharge.totalPrice,
                bizType = BIZ_TYPE_RECHARGE_REFUND,
                bizId = recharge.id,
                title = "Wallet recharge refund"
            )
        }
    }

    suspend fun getTransactionSummary(walletId: Long): Pair<Long, Long> {
        val transactions = PayWalletTransactionTable.query {
            where {
                PayWalletTransaction::walletId eq walletId
            }
        }.list()

        val totalIncome = transactions.filter { it.price > 0 }.sumOf { it.price }
        val totalExpense = transactions.filter { it.price < 0 }.sumOf { -it.price }
        return Pair(totalIncome, totalExpense)
    }

    suspend fun pageTransactionsForUser(
        userId: Long,
        page: Int,
        size: Int,
        bizType: Int? = null
    ): PageResponse<PayWalletTransaction> {
        val wallet = requireWalletByUserId(userId)
        return pageTransactions(page, size, wallet.id, bizType)
    }

    suspend fun pageRechargesForUser(
        userId: Long,
        page: Int,
        size: Int,
        payStatus: Int? = null
    ): PageResponse<PayWalletRecharge> {
        val wallet = requireWalletByUserId(userId)
        return pageRecharges(page, size, wallet.id, payStatus)
    }

    suspend fun getTransactionSummaryForUser(userId: Long): Pair<Long, Long> {
        val wallet = requireWalletByUserId(userId)
        return getTransactionSummary(wallet.id)
    }
}
