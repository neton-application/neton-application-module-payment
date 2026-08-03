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

    /** 某钱包当前全部 ACTIVE 冻结。算 available 的热路径。 */
    suspend fun activeFreezes(walletId: Long): List<PayWalletFreeze> =
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

    /** 这个钱包是否处于司法冻结。打款闸门用它（§4.3）。 */
    suspend fun isJudiciallyFrozen(walletId: Long): Boolean =
        PayWalletFreezeTable.existsWhere {
            and(
                PayWalletFreeze::walletId eq walletId,
                PayWalletFreeze::freezeType eq WalletFreezeType.JUDICIAL,
                PayWalletFreeze::status eq WalletFreezeStatus.ACTIVE,
            )
        }

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
        PayWalletFreezeTable.update(
            freeze.copy(status = terminalStatus, releasedAt = now, updatedAt = now),
        )
        recomputeFreezePriceInTx(freeze.walletId)
        log.info("wallet.freeze.finished", mapOf("freezeId" to freezeId, "status" to terminalStatus))
        return true
    }


    /**
     * 我的冻结列表（用户端）。倒序：进行中的在最上面。
     *
     * 全额司法冻结在记录里 `amount` 是 `null`（无上限），但用户要看到一个数，
     * 所以这里把它换算成**按当前余额算出来的实际冻结额**再下发。
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
                // 已结束的司法冻结不再占用余额，展示 0 比展示一个算不出来的数诚实。
                f.status != WalletFreezeStatus.ACTIVE -> 0L
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
            f.status != WalletFreezeStatus.ACTIVE -> 0L
            wallet == null -> 0L
            else -> WalletFreezeModel.judicialHold(wallet.balance, summary.amountHolds, summary.judicial)
        }
        return VisibleFreeze(freeze = f, shownAmount = shown)
    }

    /** 冻结记录 + 展示用金额。展示金额与记录里的 amount 不是一回事，见 [pageMyFreezes]。 */
    data class VisibleFreeze(val freeze: PayWalletFreeze, val shownAmount: Long)

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
