package logic

/**
 * 冻结类型（spec WALLET_FREEZE_SPEC §2）。
 *
 * 1/2 是**金额型**（金额确定、可单独释放），3 是**状态型**（金额随余额浮动）。
 * 这个分界是整套设计的基础：`WITHDRAW`/`RISK_HOLD` 进 `amountHolds` 求和，
 * `JUDICIAL` 走 [WalletFreezeModel.judicialHold] 单独算。
 */
object WalletFreezeType {
    /** 提现冻结：创建提现单时占住，打款成功后 CONSUMED。 */
    const val WITHDRAW = 1

    /** 单笔风控冻结：「这笔钱可能有问题」。 */
    const val RISK_HOLD = 2

    /** 司法冻结：执法部门发函。一个钱包最多一条 ACTIVE。 */
    const val JUDICIAL = 3

    fun isAmountHold(freezeType: Int): Boolean =
        freezeType == WITHDRAW || freezeType == RISK_HOLD
}

/**
 * 冻结终态（spec WALLET_FREEZE_SPEC §3.1）。
 *
 * 三种终态必须分开，**不许用「amount 归零」隐式表达**——本项目已经因为
 * 「用取值表达状态」栽过多次（`hydrated` 不能靠 `pts IS NULL` 推断、
 * 角色 0 不能兼作缺省与群主）。
 */
object WalletFreezeStatus {
    const val ACTIVE = 0

    /** 放行：钱回到可用余额。 */
    const val RELEASED = 1

    /** 被实扣走：提现打款成功。钱离开了账户，不是「解冻」。 */
    const val CONSUMED = 2

    /** 到期失效：司法冻结过了 expires_at。 */
    const val EXPIRED = 3
}

/** 冻结来源（幂等键的一部分）。 */
object WalletFreezeRefType {
    const val WITHDRAW_ORDER = 1
    const val WALLET_TRANSACTION = 2
    const val LEGAL_DOCUMENT = 3
}
