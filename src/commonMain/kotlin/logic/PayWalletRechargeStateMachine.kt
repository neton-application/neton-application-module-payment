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
            walletConflict(PaymentErrorCodes.WALLET_RECHARGE_REFUND_IN_PROGRESS)
        }
    }

    fun ensureCanRequestRefund(recharge: PayWalletRecharge) {
        if (recharge.payStatus != PAY_STATUS_PAID) {
            walletConflict(PaymentErrorCodes.WALLET_RECHARGE_NOT_PAID)
        }
        if (recharge.refundStatus != REFUND_STATUS_NONE) {
            walletConflict(PaymentErrorCodes.WALLET_RECHARGE_REFUND_ALREADY_REQUESTED)
        }
    }

    fun ensureCanMarkRefunded(recharge: PayWalletRecharge) {
        if (recharge.payStatus != PAY_STATUS_PAID) {
            walletConflict(PaymentErrorCodes.WALLET_RECHARGE_NOT_PAID)
        }
        if (recharge.refundStatus == REFUND_STATUS_REFUNDED) {
            return
        }
        if (recharge.refundStatus != REFUND_STATUS_REQUESTED) {
            walletConflict(PaymentErrorCodes.WALLET_RECHARGE_REFUND_NOT_REQUESTED)
        }
    }
}
