package logic

import neton.core.http.HttpException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WithdrawStateMachineTest {
    private val SM = WithdrawStateMachine

    @Test fun approve_only_from_pending() {
        SM.ensureCanApprove(SM.PENDING)
        assertFailsWith<HttpException> { SM.ensureCanApprove(SM.APPROVED) }
        assertFailsWith<HttpException> { SM.ensureCanApprove(SM.PAID) }
    }

    @Test fun reject_and_cancel_only_from_pending() {
        SM.ensureCanReject(SM.PENDING)
        SM.ensureCanCancel(SM.PENDING)
        assertFailsWith<HttpException> { SM.ensureCanReject(SM.APPROVED) }
        assertFailsWith<HttpException> { SM.ensureCanCancel(SM.APPROVED) }
    }

    @Test fun mark_paid_failed_only_from_approved_or_processing() {
        SM.ensureCanMarkPaid(SM.APPROVED)
        SM.ensureCanMarkPaid(SM.PROCESSING)
        SM.ensureCanMarkFailed(SM.APPROVED)
        SM.ensureCanMarkFailed(SM.PROCESSING)
        assertFailsWith<HttpException> { SM.ensureCanMarkPaid(SM.PENDING) }
        assertFailsWith<HttpException> { SM.ensureCanMarkFailed(SM.PENDING) }
        assertFailsWith<HttpException> { SM.ensureCanMarkPaid(SM.PAID) }
    }

    @Test fun terminal_states() {
        assertTrue(SM.PAID in SM.TERMINAL)
        assertTrue(SM.REJECTED in SM.TERMINAL)
        assertTrue(SM.FAILED in SM.TERMINAL)
        assertTrue(SM.CANCELLED in SM.TERMINAL)
        assertTrue(SM.PENDING !in SM.TERMINAL)
    }
}
