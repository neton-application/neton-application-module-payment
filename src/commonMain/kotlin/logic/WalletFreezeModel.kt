package logic

/**
 * 冻结体系的**纯计算核心**（spec WALLET_FREEZE_SPEC §2.1）。
 *
 * 冻结有两种，它们不是同一种东西：
 *
 * - **金额型**（提现冻结、单笔风控冻结）：金额确定、不随余额变化、可单独释放。
 * - **状态型**（司法冻结）：金额随余额浮动，「有资金转入就直接冻结」。
 *
 * 把两者塞进 `pay_wallets.freeze_price` 这一个标量，冻结就不可归因——
 * 「减 500」无法表达「减的是哪一笔」，驳回一笔提现会把司法冻结的钱一起放出来，
 * 只要数字对得上。真源是 `pay_wallet_freezes` 表，`freeze_price` 只是这里算出来的缓存。
 */
object WalletFreezeModel {

    /**
     * 司法冻结：`null` = 没有；`JudicialHold(null)` = 全额；`JudicialHold(n)` = 定额。
     *
     * 定额与全额**用同一个公式**，全额只是 `target = ∞` 的特例。不要为全额单开分支——
     * 那等于把无穷大硬编码进代码。
     */
    data class JudicialHold(
        /** 目标冻结额（分）；`null` = 全额，即冻住余额里除金额型冻结之外的全部。 */
        val targetAmount: Long?,
    ) {
        init {
            require(targetAmount == null || targetAmount > 0) {
                "judicial target amount must be positive when present: $targetAmount"
            }
        }

        val isFullAccount: Boolean get() = targetAmount == null
    }

    /**
     * 司法冻结实际冻住多少。
     *
     * 「到账即冻」是这个函数的**自然结果**，不需要在每条入账上挂钩子：余额涨了，
     * `balance - amountHolds` 就涨了，冻结额跟着吸收，可用余额纹丝不动。
     * 挂钩子的做法是 O(入账数) 条冻结记录 + 竞态，这里零成本。
     *
     * 定额冻结在余额不足时**尽可能多冻**（而不是失败）：法院要求冻 5 万而账上只有 3 万，
     * 正确的做法是先冻住这 3 万，后续到账继续补足，而不是因为不够就一分不冻。
     */
    fun judicialHold(balance: Long, amountHolds: Long, judicial: JudicialHold?): Long {
        if (judicial == null) return 0
        val freezable = (balance - amountHolds).coerceAtLeast(0)
        val target = judicial.targetAmount ?: return freezable
        return minOf(target, freezable)
    }

    /**
     * `freeze_price` 缓存值 = 金额型冻结之和 + 司法冻结实际冻住的部分。
     *
     * 司法部分**必须**计入这个缓存：普通借记路径校验的是
     * [PayWalletFreezeRules.debitKeepsFrozenSafe]（按 `freezePrice` 判），
     * 漏掉司法部分的话，司法冻结对红包/转账这些日常消费就是不生效的。
     */
    fun freezePriceCache(balance: Long, amountHolds: Long, judicial: JudicialHold?): Long =
        amountHolds + judicialHold(balance, amountHolds, judicial)

    /**
     * 可用余额。**全系统唯一公式**（[PayWalletFreezeRules.available] 消费它的缓存结果）。
     *
     * 全额司法冻结下恒为 0：无论收进来多少钱。
     */
    fun available(balance: Long, amountHolds: Long, judicial: JudicialHold?): Long =
        (balance - freezePriceCache(balance, amountHolds, judicial)).coerceAtLeast(0)

    /**
     * 单笔风控冻结能不能冻住。
     *
     * 冻不住时**必须显式失败**，不许静默按可用余额部分冻结——运营会以为冻住了。
     * 失败之后由运营决定是否升级成司法冻结（那个本来就是「有多少冻多少」）。
     */
    fun canPlaceAmountHold(balance: Long, amountHolds: Long, judicial: JudicialHold?, amount: Long): Boolean =
        amount > 0 && available(balance, amountHolds, judicial) >= amount
}
