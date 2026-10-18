package logic

/**
 * 提现订单状态机（P4-C）。纯校验，无 DB，照 [PayWalletRechargeStateMachine] 范式。
 *
 * 人工打款主路径：
 *   PENDING --approve--> APPROVED --mark-paid--> PAID（从冻结实扣）
 *   PENDING --reject--> REJECTED（解冻）
 *   PENDING --cancel--> CANCELLED（解冻）
 *   APPROVED/PROCESSING --mark-failed--> FAILED（解冻）
 * PROCESSING 为代付自动打款预留，人工版可不经过。
 */
object WithdrawStateMachine {
    const val PENDING = 0
    const val APPROVED = 1
    const val PROCESSING = 2
    const val PAID = 3
    const val REJECTED = 4
    const val FAILED = 5
    const val CANCELLED = 6

    /**
     * 挂起（spec WALLET_WITHDRAW_SPEC §10）。**非终态**：资金保持冻结，阻塞解除后
     * 回到 [WalletWithdrawOrder.holdResumeTo] 记录的原状态继续走完。
     *
     * 它必须是真状态而不是数据标注，因为挂起要**硬拦** approve / mark-paid / 用户 cancel
     * —— 那是授权约束，标注拦不住。
     */
    const val ON_HOLD = 7

    val TERMINAL = setOf(PAID, REJECTED, FAILED, CANCELLED)

    /** 可挂起的状态（在途）。终态不可挂起。 */
    val HOLDABLE = setOf(PENDING, APPROVED, PROCESSING)

    // 守卫判定条件原样不变；仅把拒绝从 require(IllegalArgumentException→500)
    // 改为 requireState(HttpException OPERATION_CONFLICT→409)。状态不允许=预期业务拒绝。
    fun ensureCanApprove(status: Int) =
        requireState(status == PENDING, PaymentErrorCodes.WITHDRAW_STATUS_NOT_ALLOWED)

    // ON_HOLD 也可拒绝：这是挂起的「防死锁出口」——等待无意义的阻塞（如卡号不可用）
    // 必须能终结并解冻，让用户换卡重新申请。见 spec §10.3。
    fun ensureCanReject(status: Int) =
        requireState(status == PENDING || status == ON_HOLD, PaymentErrorCodes.WITHDRAW_STATUS_NOT_ALLOWED)

    fun ensureCanCancel(status: Int) =
        requireState(status == PENDING, PaymentErrorCodes.WITHDRAW_STATUS_NOT_ALLOWED)

    fun ensureCanMarkPaid(status: Int) =
        requireState(status == APPROVED || status == PROCESSING, PaymentErrorCodes.WITHDRAW_STATUS_NOT_ALLOWED)

    fun ensureCanMarkFailed(status: Int) =
        requireState(status == APPROVED || status == PROCESSING || status == ON_HOLD, PaymentErrorCodes.WITHDRAW_STATUS_NOT_ALLOWED)

    /** 挂起：仅在途订单可挂；已挂起的重复挂起视为冲突（改文案请用 unhold 后重挂）。 */
    fun ensureCanHold(status: Int) =
        requireState(status in HOLDABLE, PaymentErrorCodes.WITHDRAW_STATUS_NOT_ALLOWED)

    /** 解除挂起：只能从 ON_HOLD 出来。 */
    fun ensureCanUnhold(status: Int) =
        requireState(status == ON_HOLD, PaymentErrorCodes.WITHDRAW_STATUS_NOT_ALLOWED)

    /**
     * 解除后要回到的状态必须是当初挂起时的在途状态。防止 hold_resume_to 被写脏后
     * 把订单放回一个非法状态（例如直接回到 PAID）。
     */
    fun ensureValidResumeTarget(resumeTo: Int) =
        requireState(resumeTo in HOLDABLE, PaymentErrorCodes.WITHDRAW_HOLD_RESUME_INVALID)

    fun name(status: Int): String = when (status) {
        PENDING -> "PENDING"
        APPROVED -> "APPROVED"
        PROCESSING -> "PROCESSING"
        PAID -> "PAID"
        REJECTED -> "REJECTED"
        FAILED -> "FAILED"
        CANCELLED -> "CANCELLED"
        ON_HOLD -> "ON_HOLD"
        else -> "UNKNOWN($status)"
    }
}
