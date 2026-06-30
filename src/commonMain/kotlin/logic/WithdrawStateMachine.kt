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

    val TERMINAL = setOf(PAID, REJECTED, FAILED, CANCELLED)

    // 守卫判定条件原样不变；仅把拒绝从 require(IllegalArgumentException→500)
    // 改为 requireState(HttpException OPERATION_CONFLICT→409)。状态不允许=预期业务拒绝。
    fun ensureCanApprove(status: Int) =
        requireState(status == PENDING) { "withdraw can only be approved from PENDING, was $status" }

    fun ensureCanReject(status: Int) =
        requireState(status == PENDING) { "withdraw can only be rejected from PENDING, was $status" }

    fun ensureCanCancel(status: Int) =
        requireState(status == PENDING) { "withdraw can only be cancelled from PENDING, was $status" }

    fun ensureCanMarkPaid(status: Int) =
        requireState(status == APPROVED || status == PROCESSING) {
            "withdraw can only be marked paid from APPROVED/PROCESSING, was $status"
        }

    fun ensureCanMarkFailed(status: Int) =
        requireState(status == APPROVED || status == PROCESSING) {
            "withdraw can only be marked failed from APPROVED/PROCESSING, was $status"
        }

    fun name(status: Int): String = when (status) {
        PENDING -> "PENDING"
        APPROVED -> "APPROVED"
        PROCESSING -> "PROCESSING"
        PAID -> "PAID"
        REJECTED -> "REJECTED"
        FAILED -> "FAILED"
        CANCELLED -> "CANCELLED"
        else -> "UNKNOWN($status)"
    }
}
