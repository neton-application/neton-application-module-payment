package logic

/**
 * 钱包账变 `biz_type` 语义分类（P1 账变明细，通用 payment 能力）。
 *
 * 只暴露**机器可读**的稳定 code + 收支方向；本地化文案（中文「充值/提现扣款」等）
 * 由产品端（App/H5/Web）按 code 映射，**不在通用模块写产品文案**（分层：产品文案留下游）。
 *
 * biz_type 取值与 [PayWalletLogic] 常量一致：1 充值 / 2 充值退款 / 200 管理员调整 /
 * 300 提现冻结 / 301 提现解冻 / 302 提现扣款 / 303 提现退款。
 */
object WalletBizType {
    const val RECHARGE = 1
    const val RECHARGE_REFUND = 2
    const val ADMIN_ADJUST = 200
    const val WITHDRAW_FREEZE = 300
    const val WITHDRAW_UNFREEZE = 301
    const val WITHDRAW_DEDUCT = 302
    const val WITHDRAW_REFUND = 303

    /** 收支方向：+1 收入 / -1 支出 / 0 中性（冻结/解冻不改变余额净值）。以 ledger price 符号为准。 */
    fun direction(price: Long): Int = when {
        price > 0 -> 1
        price < 0 -> -1
        else -> 0
    }

    /** 稳定机器码（i18n key）；产品端据此映射本地化文案，新增 biz_type 未登记回退 `other`。 */
    fun code(bizType: Int): String = when (bizType) {
        RECHARGE -> "recharge"
        RECHARGE_REFUND -> "recharge_refund"
        ADMIN_ADJUST -> "admin_adjust"
        WITHDRAW_FREEZE -> "withdraw_freeze"
        WITHDRAW_UNFREEZE -> "withdraw_unfreeze"
        WITHDRAW_DEDUCT -> "withdraw_deduct"
        WITHDRAW_REFUND -> "withdraw_refund"
        else -> "other"
    }
}
