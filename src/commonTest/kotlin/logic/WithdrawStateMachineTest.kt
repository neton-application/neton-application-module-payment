package logic

import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 提现状态机**完整矩阵**锁定测试（P0）：7 状态 × 5 操作全覆盖。
 * 合法流转必须通过；非法流转必须抛 HttpException(OPERATION_CONFLICT → 409)。
 * 这是资金状态机的长期保险——未来若有人放开某个非法流转，此测试立即失败。
 */
class WithdrawStateMachineTest {
    private val SM = WithdrawStateMachine

    private val statuses = listOf(
        SM.PENDING, SM.APPROVED, SM.PROCESSING, SM.PAID, SM.REJECTED, SM.FAILED, SM.CANCELLED,
    )

    private val ops: Map<String, (Int) -> Unit> = mapOf(
        "approve" to { s -> SM.ensureCanApprove(s) },
        "reject" to { s -> SM.ensureCanReject(s) },
        "cancel" to { s -> SM.ensureCanCancel(s) },
        "markPaid" to { s -> SM.ensureCanMarkPaid(s) },
        "markFailed" to { s -> SM.ensureCanMarkFailed(s) },
    )

    /** 合法 (status, op) 组合——其余全部非法。 */
    private val legal: Set<Pair<Int, String>> = setOf(
        SM.PENDING to "approve",
        SM.PENDING to "reject",
        SM.PENDING to "cancel",
        SM.APPROVED to "markPaid",
        SM.APPROVED to "markFailed",
        SM.PROCESSING to "markPaid",
        SM.PROCESSING to "markFailed",
    )

    @Test
    fun full_transition_matrix() {
        for (status in statuses) {
            for ((opName, guard) in ops) {
                val isLegal = (status to opName) in legal
                if (isLegal) {
                    // 合法：守卫不得抛。
                    try {
                        guard(status)
                    } catch (e: Throwable) {
                        fail("legal ${SM.name(status)}->$opName should pass but threw ${e::class.simpleName}: ${e.message}")
                    }
                } else {
                    // 非法：必须抛 HttpException 且 code=OPERATION_CONFLICT(→409)。
                    val ex = assertFailsWith<HttpException>(
                        "illegal ${SM.name(status)}->$opName must be rejected",
                    ) { guard(status) }
                    assertEquals(
                        NetonErrorCode.OPERATION_CONFLICT, ex.code,
                        "illegal ${SM.name(status)}->$opName must map to 409 OPERATION_CONFLICT",
                    )
                }
            }
        }
    }

    @Test
    fun terminal_states_reject_every_operation() {
        for (terminal in listOf(SM.PAID, SM.REJECTED, SM.FAILED, SM.CANCELLED)) {
            assertTrue(terminal in SM.TERMINAL, "${SM.name(terminal)} must be terminal")
            for ((opName, guard) in ops) {
                assertFailsWith<HttpException>(
                    "terminal ${SM.name(terminal)}->$opName must be rejected",
                ) { guard(terminal) }
            }
        }
        assertTrue(SM.PENDING !in SM.TERMINAL)
        assertTrue(SM.APPROVED !in SM.TERMINAL)
        assertTrue(SM.PROCESSING !in SM.TERMINAL)
    }
}
