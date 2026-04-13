package logic

import model.PayWalletRecharge

internal object PayWalletRechargeStateMachine {
    const val PAY_STATUS_PENDING = 0
    const val PAY_STATUS_PAID = 1

    const val REFUND_STATUS_NONE = 0
    const val REFUND_STATUS_REQUESTED = 1
    const val REFUND_STATUS_REFUNDED = 2

    fun ensureCanMarkPaid(recharge: PayWalletRecharge) {
        if (recharge.payStatus == PAY_STATUS_PAID) {
            return
        }
        if (recharge.refundStatus != REFUND_STATUS_NONE) {
            throw IllegalArgumentException("Recharge refund flow already started: ${recharge.id}")
        }
    }

    fun ensureCanRequestRefund(recharge: PayWalletRecharge) {
        if (recharge.payStatus != PAY_STATUS_PAID) {
            throw IllegalArgumentException("Recharge not paid yet: ${recharge.id}")
        }
        if (recharge.refundStatus != REFUND_STATUS_NONE) {
            throw IllegalArgumentException("Recharge refund already requested: ${recharge.id}")
        }
    }

    fun ensureCanMarkRefunded(recharge: PayWalletRecharge) {
        if (recharge.payStatus != PAY_STATUS_PAID) {
            throw IllegalArgumentException("Recharge not paid yet: ${recharge.id}")
        }
        if (recharge.refundStatus == REFUND_STATUS_REFUNDED) {
            return
        }
        if (recharge.refundStatus != REFUND_STATUS_REQUESTED) {
            throw IllegalArgumentException("Recharge refund must be requested first: ${recharge.id}")
        }
    }
}
