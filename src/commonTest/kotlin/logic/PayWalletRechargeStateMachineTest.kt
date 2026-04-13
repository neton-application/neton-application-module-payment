package logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import model.PayWalletRecharge

class PayWalletRechargeStateMachineTest {

    @Test
    fun `mark refunded requires refund request first`() {
        val recharge = paidRecharge(refundStatus = PayWalletRechargeStateMachine.REFUND_STATUS_NONE)

        assertFailsWith<IllegalArgumentException> {
            PayWalletRechargeStateMachine.ensureCanMarkRefunded(recharge)
        }
    }

    @Test
    fun `refund request requires paid recharge`() {
        val recharge = paidRecharge(
            payStatus = PayWalletRechargeStateMachine.PAY_STATUS_PENDING,
            refundStatus = PayWalletRechargeStateMachine.REFUND_STATUS_NONE
        )

        assertFailsWith<IllegalArgumentException> {
            PayWalletRechargeStateMachine.ensureCanRequestRefund(recharge)
        }
    }

    @Test
    fun `mark paid rejects recharge already in refund flow`() {
        val recharge = paidRecharge(
            payStatus = PayWalletRechargeStateMachine.PAY_STATUS_PENDING,
            refundStatus = PayWalletRechargeStateMachine.REFUND_STATUS_REQUESTED
        )

        assertFailsWith<IllegalArgumentException> {
            PayWalletRechargeStateMachine.ensureCanMarkPaid(recharge)
        }
    }

    @Test
    fun `mark refunded allows requested refund`() {
        val recharge = paidRecharge(refundStatus = PayWalletRechargeStateMachine.REFUND_STATUS_REQUESTED)

        PayWalletRechargeStateMachine.ensureCanMarkRefunded(recharge)
    }

    @Test
    fun `duplicate refund request is rejected`() {
        val recharge = paidRecharge(refundStatus = PayWalletRechargeStateMachine.REFUND_STATUS_REQUESTED)

        val error = assertFailsWith<IllegalArgumentException> {
            PayWalletRechargeStateMachine.ensureCanRequestRefund(recharge)
        }

        assertEquals("Recharge refund already requested: 1", error.message)
    }

    @Test
    fun `already refunded recharge is idempotent for mark refunded`() {
        val recharge = paidRecharge(refundStatus = PayWalletRechargeStateMachine.REFUND_STATUS_REFUNDED)

        PayWalletRechargeStateMachine.ensureCanMarkRefunded(recharge)
    }

    private fun paidRecharge(
        payStatus: Int = PayWalletRechargeStateMachine.PAY_STATUS_PAID,
        refundStatus: Int = PayWalletRechargeStateMachine.REFUND_STATUS_NONE
    ): PayWalletRecharge = PayWalletRecharge(
        id = 1,
        walletId = 10,
        totalPrice = 100,
        payPrice = 100,
        bonusPrice = 0,
        payStatus = payStatus,
        payChannelCode = "alipay",
        refundStatus = refundStatus
    )
}
