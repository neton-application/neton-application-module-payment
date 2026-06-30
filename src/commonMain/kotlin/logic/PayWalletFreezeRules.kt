package logic

/**
 * 钱包冻结/扣款的纯校验规则（P4-B0）。
 *
 * 与 [PayWalletRechargeStateMachine] 同思路：把可单测的判定从 DB 事务里抽出来。
 * 资金语义：
 *   - 可用余额 available = balance − freezePrice
 *   - 冻结(freeze) 只动 freezePrice，不动 balance，要求 available ≥ amount
 *   - 解冻(unfreeze) 只减 freezePrice，要求 freezePrice ≥ amount
 *   - 从冻结实扣(deductFrozen) 同时减 balance 和 freezePrice，要求两者都 ≥ amount
 *   - 普通借记(debit，price<0) 不得动用被冻结资金：扣后 balance 仍须 ≥ freezePrice
 */
object PayWalletFreezeRules {

    /** 可用余额 = 总余额 − 冻结额。 */
    fun available(balance: Long, freezePrice: Long): Long = balance - freezePrice

    // 判定条件原样不变；仅把拒绝改为标准 4xx：金额非法→400(requireParam)，
    // 可用/冻结/余额不足→409(requireState，属"当前账户状态不足")。
    /** 创建提现/冻结前校验：金额为正且可用余额充足。 */
    fun ensureCanFreeze(balance: Long, freezePrice: Long, amount: Long) {
        requireParam(amount > 0) { "freeze amount must be positive: $amount" }
        requireState(available(balance, freezePrice) >= amount) {
            "insufficient available balance: available=${available(balance, freezePrice)}, need=$amount"
        }
    }

    /** 解冻前校验：金额为正且冻结额充足。 */
    fun ensureCanUnfreeze(freezePrice: Long, amount: Long) {
        requireParam(amount > 0) { "unfreeze amount must be positive: $amount" }
        requireState(freezePrice >= amount) {
            "insufficient frozen amount: frozen=$freezePrice, need=$amount"
        }
    }

    /** 从冻结实扣（打款成功）前校验：金额为正，冻结额与余额都充足。 */
    fun ensureCanDeductFrozen(balance: Long, freezePrice: Long, amount: Long) {
        requireParam(amount > 0) { "deduct amount must be positive: $amount" }
        requireState(freezePrice >= amount) {
            "deduct exceeds frozen amount: frozen=$freezePrice, need=$amount"
        }
        requireState(balance >= amount) {
            "deduct exceeds balance: balance=$balance, need=$amount"
        }
    }

    /**
     * 普通借记（price<0）后的余额不得侵占冻结资金。
     * 返回 true 表示这笔借记合法（扣后 balance ≥ freezePrice）。
     */
    fun debitKeepsFrozenSafe(balanceAfter: Long, freezePrice: Long): Boolean =
        balanceAfter >= freezePrice
}
