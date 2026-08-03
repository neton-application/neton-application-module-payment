package logic

import model.PayWallet
import model.PayWalletFreeze
import neton.core.annotations.Logic
import neton.database.api.DbContext
import neton.database.dsl.*
import neton.logging.Logger
import table.PayWalletFreezeTable
import table.PayWalletTable

/**
 * 冻结记录的读写（spec WALLET_FREEZE_SPEC §3）。
 *
 * **本表是冻结的真源，`pay_wallets.freeze_price` 是它算出来的缓存。**
 * 每一处改动冻结的地方都必须在**同一事务内**调 [recomputeFreezePriceInTx]，
 * 否则缓存与真源漂移：偏小 = 冻结失效（钱被花掉），偏大 = 多冻用户的钱。
 *
 * 所有 `...InTx` 方法都**不自开事务**，由调用方（建单/审批/后台操作）合进它自己的事务。
 */
@Logic
class WalletFreezeLogic(
    private val log: Logger,
    private val db: DbContext,
) {

    /**
     * 某钱包当前**仍然生效**的冻结。算 available 的热路径。
     *
     * 过了 `expires_at` 的行不算数，哪怕它的 status 还是 ACTIVE：
     * **到期必须立刻生效，不能等定时任务来改状态**。本项目现在根本没有跑着的
     * 定时任务，把「钱什么时候解冻」挂在一个不存在的 cron 上，等于用户的钱到期了
     * 还冻着。status 的翻正由 [sweepExpired] 补，那只是记账，不是判据。
     */
    suspend fun activeFreezes(walletId: Long): List<PayWalletFreeze> {
        val now = nowMillis()
        return activeFreezesRaw(walletId).filter { it.expiresAt <= 0 || it.expiresAt > now }
    }

    /**
     * 状态仍是 ACTIVE 的行，**不看到期**。
     *
     * 只给两种人用：[sweepExpired]（要找出到期待翻正的行）和 [finishInTx]
     * （结束一条冻结时要把它自己算进快照里——它马上就不生效了，但快照记的是
     * 「结束前冻住了多少」）。判可用余额一律走 [activeFreezes]。
     */
    private suspend fun activeFreezesRaw(walletId: Long): List<PayWalletFreeze> =
        PayWalletFreezeTable.query {
            where {
                and(
                    PayWalletFreeze::walletId eq walletId,
                    PayWalletFreeze::status eq WalletFreezeStatus.ACTIVE,
                )
            }
        }.list()

    /**
     * 把 ACTIVE 冻结折算成 [WalletFreezeModel] 的两个入参。
     *
     * 司法冻结取**第一条**（DB 上有部分唯一索引保证一个钱包最多一条 ACTIVE）。
     */
    fun summarize(freezes: List<PayWalletFreeze>): FreezeSummary {
        val amountHolds = freezes
            .filter { WalletFreezeType.isAmountHold(it.freezeType) }
            // 金额型的 amount 必填；真出现 null 说明数据坏了，按 0 记会让冻结静默失效，
            // 所以这里显式拒绝而不是兜底。
            .sumOf { it.amount ?: error("amount hold #${it.id} has no amount") }
        val judicial = freezes
            .firstOrNull { it.freezeType == WalletFreezeType.JUDICIAL }
            ?.let { WalletFreezeModel.JudicialHold(targetAmount = it.amount) }
        return FreezeSummary(amountHolds = amountHolds, judicial = judicial)
    }

    data class FreezeSummary(
        val amountHolds: Long,
        val judicial: WalletFreezeModel.JudicialHold?,
    ) {
        val hasJudicial: Boolean get() = judicial != null
    }

    /** 当前可用余额（唯一公式，见 [WalletFreezeModel.available]）。 */
    suspend fun availableOf(wallet: PayWallet): Long {
        val s = summarize(activeFreezes(wallet.id))
        return WalletFreezeModel.available(wallet.balance, s.amountHolds, s.judicial)
    }

    /** 这个钱包是否处于司法冻结。打款闸门用它（§4.3）。到期的不算。 */
    suspend fun isJudiciallyFrozen(walletId: Long): Boolean =
        activeFreezes(walletId).any { it.freezeType == WalletFreezeType.JUDICIAL }

    /**
     * 按冻结记录重算 `freeze_price` 缓存并落库。
     *
     * **必须与冻结记录的改动在同一事务内。** 分两个事务的话，中间那一刻钱包的
     * 可用余额是错的，而普通消费路径（红包/转账）读的正是这个缓存。
     */
    suspend fun recomputeFreezePriceInTx(walletId: Long): Long {
        val wallet = PayWalletTable.get(walletId) ?: walletNotFound("Wallet not found: $walletId")
        val s = summarize(activeFreezes(walletId))
        val cache = WalletFreezeModel.freezePriceCache(wallet.balance, s.amountHolds, s.judicial)
        if (cache != wallet.freezePrice) {
            PayWalletTable.update(wallet.copy(freezePrice = cache))
        }
        return cache
    }

    /**
     * 落一条冻结记录。**幂等**：`(freezeType, refType, refId)` 已存在时原样返回，
     * 不重复冻结（重试、并发、运营重复点击）。
     *
     * 注意这里**不做** available 校验——不同类型的准入规则不同（金额型要够、
     * 司法冻结有多少冻多少），校验留在各自的调用方。
     */
    suspend fun placeInTx(freeze: PayWalletFreeze): PayWalletFreeze {
        val existing = PayWalletFreezeTable.oneWhere {
            and(
                PayWalletFreeze::freezeType eq freeze.freezeType,
                PayWalletFreeze::refType eq freeze.refType,
                PayWalletFreeze::refId eq freeze.refId,
            )
        }
        if (existing != null) {
            log.info("wallet.freeze.duplicate", mapOf("freezeId" to existing.id, "refId" to freeze.refId))
            return existing
        }
        val now = nowMillis()
        val saved = PayWalletFreezeTable.insert(
            freeze.copy(status = WalletFreezeStatus.ACTIVE, createdAt = now, updatedAt = now),
        )
        recomputeFreezePriceInTx(freeze.walletId)
        log.info(
            "wallet.freeze.placed",
            mapOf("walletId" to freeze.walletId, "type" to freeze.freezeType, "amount" to freeze.amount),
        )
        return saved
    }

    /**
     * 结束一条冻结。[terminalStatus] 必须是 RELEASED / CONSUMED / EXPIRED 之一 —— 三者含义不同，
     * 不许用同一个「已解冻」糊过去（§3.1）。
     *
     * 幂等：已经是终态时直接返回 false。
     */
    suspend fun finishInTx(freezeId: Long, terminalStatus: Int): Boolean {
        requireParam(terminalStatus != WalletFreezeStatus.ACTIVE) {
            "terminal status must not be ACTIVE"
        }
        val freeze = PayWalletFreezeTable.get(freezeId) ?: return false
        if (freeze.status != WalletFreezeStatus.ACTIVE) return false
        val now = nowMillis()
        // 全额司法冻结（amount = null，无上限）结束时把**当时实际冻住的钱**快照进 amount。
        // 不快照的话这条记录就永远算不出金额了——余额早就变了——用户在冻结历史里
        // 只能看到「账户冻结 ¥0.00 已解除」，看起来像什么都没发生过。
        // amount 只在 ACTIVE 行上表示「上限」，终态行没人再拿它当上限读，写它是安全的。
        val snapshotAmount = if (freeze.freezeType == WalletFreezeType.JUDICIAL && freeze.amount == null) {
            val wallet = PayWalletTable.get(freeze.walletId)
            // 用 raw：到期结束时这条已经不生效了，但快照要记的正是「结束前冻住了多少」。
            val s = summarize(activeFreezesRaw(freeze.walletId))
            if (wallet == null) 0L else WalletFreezeModel.judicialHold(wallet.balance, s.amountHolds, s.judicial)
        } else {
            freeze.amount
        }
        PayWalletFreezeTable.update(
            freeze.copy(amount = snapshotAmount, status = terminalStatus, releasedAt = now, updatedAt = now),
        )
        recomputeFreezePriceInTx(freeze.walletId)
        log.info("wallet.freeze.finished", mapOf("freezeId" to freezeId, "status" to terminalStatus))
        return true
    }


    /**
     * 我的冻结列表（用户端）。倒序：进行中的在最上面。
     *
     * 全额司法冻结在记录里 `amount` 是 `null`（无上限），但用户要看到一个数：
     * 进行中的按当前余额换算成实际冻结额；已结束的直接用 [finishInTx] 结束时
     * 快照进 `amount` 的那个数（余额早变了，事后再算只会算出 0）。
     */
    suspend fun pageMyFreezes(walletId: Long, page: Int, size: Int): Pair<List<VisibleFreeze>, Long> {
        val q = PayWalletFreezeTable.query {
            where { PayWalletFreeze::walletId eq walletId }
            orderBy(PayWalletFreeze::id.desc())
        }
        val result = q.page(page, size)
        val wallet = PayWalletTable.get(walletId)
        val summary = summarize(activeFreezes(walletId))
        val judicialActual = if (wallet == null) 0L else WalletFreezeModel.judicialHold(
            wallet.balance, summary.amountHolds, summary.judicial,
        )
        val visible = result.items.map { f ->
            val shown = when {
                f.freezeType != WalletFreezeType.JUDICIAL -> f.amount ?: 0L
                // 终态行的 amount 是 finishInTx 结束时快照下来的真实冻结额，直接用。
                f.status != WalletFreezeStatus.ACTIVE -> f.amount ?: 0L
                else -> judicialActual
            }
            VisibleFreeze(freeze = f, shownAmount = shown)
        }
        return visible to result.total
    }

    /** 我的单条冻结（必须本人钱包）。 */
    suspend fun myFreezeDetail(walletId: Long, freezeId: Long): VisibleFreeze? {
        val f = PayWalletFreezeTable.get(freezeId)?.takeIf { it.walletId == walletId } ?: return null
        val wallet = PayWalletTable.get(walletId)
        val summary = summarize(activeFreezes(walletId))
        val shown = when {
            f.freezeType != WalletFreezeType.JUDICIAL -> f.amount ?: 0L
            // 终态行的 amount 是结束时快照下来的真实冻结额（见 finishInTx）。
            f.status != WalletFreezeStatus.ACTIVE -> f.amount ?: 0L
            wallet == null -> 0L
            else -> WalletFreezeModel.judicialHold(wallet.balance, summary.amountHolds, summary.judicial)
        }
        return VisibleFreeze(freeze = f, shownAmount = shown)
    }

    /** 冻结记录 + 展示用金额。展示金额与记录里的 amount 不是一回事，见 [pageMyFreezes]。 */
    data class VisibleFreeze(val freeze: PayWalletFreeze, val shownAmount: Long)


    // ==================== 后台冻结操作（spec §4.2 / §4.5）====================

    /**
     * 下一笔**单笔风控冻结**（「这笔钱可能有问题」）。
     *
     * 可用余额不足时**显式失败**，不按可用余额部分冻结——运营会以为冻住了。
     * 失败之后由运营决定是否改用账户冻结（那个本来就是「有多少冻多少」）。
     */
    suspend fun placeRiskHold(
        op: OperatorContext,
        userId: Long,
        amount: Long,
        refId: String,
        reasonText: String?,
    ): PayWalletFreeze = db.transaction {
        val wallet = PayWalletTable.oneWhere { PayWallet::userId eq userId }
            ?: walletNotFound("Wallet not found for user: $userId")
        val summary = summarize(activeFreezes(wallet.id))
        requireState(
            WalletFreezeModel.canPlaceAmountHold(wallet.balance, summary.amountHolds, summary.judicial, amount)
        ) {
            val available = WalletFreezeModel.available(wallet.balance, summary.amountHolds, summary.judicial)
            "insufficient available balance to hold: available=$available, need=$amount"
        }
        placeInTx(
            PayWalletFreeze(
                walletId = wallet.id,
                userId = userId,
                freezeType = WalletFreezeType.RISK_HOLD,
                amount = amount,
                refType = WalletFreezeRefType.WALLET_TRANSACTION,
                refId = refId,
                reasonCode = "risk_review",
                reasonText = reasonText,
                operatorId = op.operatorId,
            ),
        )
    }

    /**
     * 下**账户冻结**（司法）。[targetAmount] 为 null = 全额，否则是定额目标。
     *
     * 这里**不校验余额**：账户冻结的语义就是「有多少冻多少，后续到账继续吸收」，
     * 余额为 0 时下达也是有效的——钱一到就被冻住。
     *
     * 一个钱包最多一条 ACTIVE（DB 部分唯一索引兜底）；重复下达按幂等键返回原记录。
     */
    suspend fun placeJudicialFreeze(
        op: OperatorContext,
        userId: Long,
        targetAmount: Long?,
        legalDocNo: String,
        reasonText: String?,
        expiresAt: Long,
    ): PayWalletFreeze = db.transaction {
        requireParam(legalDocNo.isNotBlank()) { "legal document number is required" }
        requireParam(targetAmount == null || targetAmount > 0) {
            "judicial target amount must be positive when present: $targetAmount"
        }
        val wallet = PayWalletTable.oneWhere { PayWallet::userId eq userId }
            ?: walletNotFound("Wallet not found for user: $userId")
        requireState(!isJudiciallyFrozen(wallet.id)) {
            "wallet already has an active account freeze"
        }
        placeInTx(
            PayWalletFreeze(
                walletId = wallet.id,
                userId = userId,
                freezeType = WalletFreezeType.JUDICIAL,
                amount = targetAmount,
                refType = WalletFreezeRefType.LEGAL_DOCUMENT,
                refId = legalDocNo,
                reasonCode = "judicial",
                reasonText = reasonText,
                operatorId = op.operatorId,
                expiresAt = expiresAt,
            ),
        )
    }

    /** 解除一条冻结（放行，钱回到可用余额）。提现冻结不走这里——那由提现状态机管。 */
    suspend fun release(op: OperatorContext, freezeId: Long): Boolean = db.transaction {
        val freeze = PayWalletFreezeTable.get(freezeId) ?: return@transaction false
        requireState(freeze.freezeType != WalletFreezeType.WITHDRAW) {
            "a withdrawal hold is released by the withdrawal state machine, not here"
        }
        log.info("wallet.freeze.release", mapOf("freezeId" to freezeId, "operatorId" to op.operatorId))
        finishInTx(freezeId, WalletFreezeStatus.RELEASED)
    }

    /**
     * 把已过 `expires_at` 的冻结翻成 `EXPIRED`。返回处理条数。
     *
     * **这是记账，不是判据。** 到期那一刻钱就已经可用了（见 [activeFreezes]），
     * 这里只是让状态、`freeze_price` 缓存和后台列表追上事实。所以它晚跑、漏跑、
     * 跑两遍都不会让用户的钱多冻一分钟——[finishInTx] 本身幂等。
     */
    suspend fun sweepExpired(limit: Int = 200): Int {
        val now = nowMillis()
        val due = PayWalletFreezeTable.query {
            where {
                and(
                    PayWalletFreeze::status eq WalletFreezeStatus.ACTIVE,
                    PayWalletFreeze::expiresAt gt 0L,
                    PayWalletFreeze::expiresAt lt now,
                )
            }
            orderBy(PayWalletFreeze::id.asc())
        }.page(1, limit.coerceIn(1, 1000)).items
        var n = 0
        for (f in due) {
            val done = db.transaction { finishInTx(f.id, WalletFreezeStatus.EXPIRED) }
            if (done) n++
        }
        if (n > 0) log.info("wallet.freeze.sweep_expired", mapOf("count" to n))
        return n
    }

    /** 后台冻结分页（可按用户/类型/状态筛选）。 */
    suspend fun pageFreezes(
        page: Int,
        size: Int,
        userId: Long? = null,
        freezeType: Int? = null,
        status: Int? = null,
    ): Pair<List<PayWalletFreeze>, Long> {
        val result = PayWalletFreezeTable.query {
            where {
                and(
                    whenPresent(userId) { PayWalletFreeze::userId eq it },
                    whenPresent(freezeType) { PayWalletFreeze::freezeType eq it },
                    whenPresent(status) { PayWalletFreeze::status eq it },
                )
            }
            orderBy(PayWalletFreeze::id.desc())
        }.page(page, size)
        return result.items to result.total
    }

    /** 按幂等键结束（调用方通常只有业务单号，没有 freeze id）。 */
    suspend fun finishByRefInTx(freezeType: Int, refType: Int, refId: String, terminalStatus: Int): Boolean {
        val freeze = PayWalletFreezeTable.oneWhere {
            and(
                PayWalletFreeze::freezeType eq freezeType,
                PayWalletFreeze::refType eq refType,
                PayWalletFreeze::refId eq refId,
            )
        } ?: return false
        return finishInTx(freeze.id, terminalStatus)
    }
}

private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
