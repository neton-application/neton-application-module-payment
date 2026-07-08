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

        // 红包 + 转账（PrivChat Money Message, RP-2）。幂等索引见 V008。
        const val BIZ_TYPE_RED_PACKET_CREATE_DEDUCT = 400  // 发红包扣发送方（biz_id=red_packet_id）
        const val BIZ_TYPE_RED_PACKET_RECEIVE_INCOME = 401 // 领取入账（biz_id=claim_id）
        const val BIZ_TYPE_RED_PACKET_REFUND = 402         // 过期退款回发送方（biz_id=red_packet_id）
        const val BIZ_TYPE_TRANSFER_OUT = 500              // 转账扣发送方（biz_id=transfer_id）
        const val BIZ_TYPE_TRANSFER_IN = 501               // 转账入账接收方（biz_id=transfer_id）
        const val BIZ_TYPE_TRANSFER_REFUND = 502           // 转账异常回滚（biz_id=transfer_id）
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

    /**
     * admin 手动充值（银行汇款、异常手动到账等线下入账）：正数入账，
     * 备注写进 ledger title 供审计追溯。无钱包用户懒创建（与充值同语义）。
     */
    suspend fun manualRecharge(userId: Long, amount: Long, remark: String) {
        require(amount > 0) { "manual recharge amount must be positive" }
        val wallet = getWalletByUserId(userId) ?: db.transaction { getOrCreateWalletInTx(userId) }
        updateBalance(wallet.id, amount, BIZ_TYPE_ADMIN_ADJUST, 0L, "手动充值：$remark")
        log.info("wallet.manual-recharge", mapOf("userId" to userId, "amount" to amount, "remark" to remark))
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

    /** (bizType,bizId) 是否已有账变（通用幂等检查，红包/转账/提现共用）。 */
    suspend fun ledgerExists(bizType: Int, bizId: Long): Boolean = withdrawLedgerExists(bizType, bizId)

    /**
     * 通用带幂等的余额变更（红包/转账等 Money Message 复用，RP-2）。**在调用方事务内**执行，
     * 让「订单 + 动钱 + 审计」合成同一原子事务。
     * - (bizType,bizId) 已存在 → no-op（幂等，防重复入账/扣款，并发由 V008 唯一索引兜底）。
     * - price<0 借记受可用余额保护（不侵占冻结资金）；余额不足 → 409（非 500）。
     * - **不改** totalRecharge/totalExpense（红包/转账非充值非消费计数；账变真相以 ledger 为准）。
     */
    suspend fun applyMoneyMoveInTx(walletId: Long, price: Long, bizType: Int, bizId: Long, title: String) {
        if (withdrawLedgerExists(bizType, bizId)) return
        val wallet = PayWalletTable.get(walletId) ?: walletNotFound("Wallet not found: $walletId")
        val newBalance = wallet.balance + price
        requireState(newBalance >= 0) { "Insufficient balance for wallet: $walletId" }
        if (price < 0) {
            requireState(PayWalletFreezeRules.debitKeepsFrozenSafe(newBalance, wallet.freezePrice)) {
                "Insufficient available balance (frozen funds protected) for wallet: $walletId"
            }
        }
        PayWalletTable.update(wallet.copy(balance = newBalance))
        insertWithdrawLedger(walletId, bizType, bizId, title, price = price, balance = newBalance)
        log.info("wallet.money.move", mapOf("walletId" to walletId, "price" to price, "bizType" to bizType, "bizId" to bizId))
    }

    /** 取用户钱包（无则懒创建零钱包，红包领取/转账入账目标复用）。在调用方事务内安全。 */
    suspend fun getOrCreateWalletInTx(userId: Long): PayWallet {
        PayWalletTable.oneWhere { PayWallet::userId eq userId }?.let { return it }
        val created = PayWalletTable.insert(PayWallet(userId = userId))
        log.info("wallet.lazy_created", mapOf("userId" to userId, "walletId" to created.id))
        return created
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
        // 充值是资金入口：无钱包用户懒创建（与红包领取/转账入账同语义），不能 500 挡住首充。
        val wallet = getWalletByUserId(userId) ?: db.transaction { getOrCreateWalletInTx(userId) }
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
        // 只读路径：钱包懒创建（首次动钱才建行），新用户无钱包 = 无账变，返回空页而非 500。
        val wallet = getWalletByUserId(userId)
            ?: return PageResponse(emptyList(), 0, page, size, 0)
        return pageTransactions(page, size, wallet.id, bizType)
    }

    suspend fun pageRechargesForUser(
        userId: Long,
        page: Int,
        size: Int,
        payStatus: Int? = null
    ): PageResponse<PayWalletRecharge> {
        // 只读路径：无钱包 = 无充值单，返回空页（钱包懒创建语义，见 pageTransactionsForUser）。
        val wallet = getWalletByUserId(userId)
            ?: return PageResponse(emptyList(), 0, page, size, 0)
        return pageRecharges(page, size, wallet.id, payStatus)
    }

    suspend fun getTransactionSummaryForUser(userId: Long): Pair<Long, Long> {
        // 只读路径：无钱包 = 零支出零充值（钱包懒创建语义）。
        val wallet = getWalletByUserId(userId) ?: return 0L to 0L
        return getTransactionSummary(wallet.id)
    }

    /**
     * 财务总览轻量版（P1）：钱包资金聚合 + 提现分状态聚合。
     * 走 DB SUM/COUNT 聚合（不 list 全表，随规模可扩展）；只 embed postgresql，用 `::bigint`
     * 保证 SUM(numeric) 映射回 Long。金额单位=分。
     */
    suspend fun getFinanceOverview(): controller.admin.wallet.dto.WalletOverviewVO {
        val w = db.fetchAll(
            "SELECT COUNT(*) AS c, " +
                "COALESCE(SUM(balance),0)::bigint AS bal, " +
                "COALESCE(SUM(freeze_price),0)::bigint AS frz, " +
                "COALESCE(SUM(total_recharge),0)::bigint AS rec, " +
                "COALESCE(SUM(total_expense),0)::bigint AS exp " +
                "FROM pay_wallets"
        ).firstOrNull()
        val walletCount = w?.long("c") ?: 0L
        val totalBalance = w?.long("bal") ?: 0L
        val totalFrozen = w?.long("frz") ?: 0L
        val totalRecharge = w?.long("rec") ?: 0L
        val totalExpense = w?.long("exp") ?: 0L

        // 提现按状态聚合（pending=0 / approved=1 / paid=3）
        val rows = db.fetchAll(
            "SELECT status, COUNT(*) AS c, COALESCE(SUM(amount),0)::bigint AS amt " +
                "FROM wallet_withdraw_orders GROUP BY status"
        )
        var pc = 0L; var pa = 0L; var ac = 0L; var aa = 0L; var dc = 0L; var da = 0L
        for (r in rows) {
            val c = r.long("c"); val amt = r.long("amt")
            when (r.int("status")) {
                WithdrawStateMachine.PENDING -> { pc = c; pa = amt }
                WithdrawStateMachine.APPROVED -> { ac = c; aa = amt }
                WithdrawStateMachine.PAID -> { dc = c; da = amt }
            }
        }

        return controller.admin.wallet.dto.WalletOverviewVO(
            walletCount = walletCount,
            totalBalance = totalBalance,
            totalFrozen = totalFrozen,
            totalAvailable = totalBalance - totalFrozen,
            totalRecharge = totalRecharge,
            totalExpense = totalExpense,
            withdrawPendingCount = pc, withdrawPendingAmount = pa,
            withdrawApprovedCount = ac, withdrawApprovedAmount = aa,
            withdrawPaidCount = dc, withdrawPaidAmount = da,
        )
    }
}
