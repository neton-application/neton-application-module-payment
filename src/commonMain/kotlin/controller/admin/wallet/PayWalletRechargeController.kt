package controller.admin.wallet

import logic.PayWalletLogic
import neton.core.annotations.Controller
import neton.core.annotations.Post

@Controller("/pay/wallet-recharge")
class PayWalletRechargeController(private val payWalletLogic: PayWalletLogic) {

    @Post("/update-paid")
    suspend fun updatePaid(id: Long, payChannelCode: String) {
        val recharge = payWalletLogic.getRecharge(id)
            ?: throw IllegalArgumentException("Recharge not found: $id")
        payWalletLogic.updateRecharge(recharge.copy(
            payStatus = 1,
            payChannelCode = payChannelCode
        ))
        // Credit wallet balance
        payWalletLogic.updateBalance(
            walletId = recharge.walletId,
            price = recharge.totalPrice,
            bizType = 1, // Recharge
            bizId = recharge.id,
            title = "Wallet recharge"
        )
    }

    @Post("/refund")
    suspend fun refund(id: Long) {
        val recharge = payWalletLogic.getRecharge(id)
            ?: throw IllegalArgumentException("Recharge not found: $id")
        payWalletLogic.updateRecharge(recharge.copy(
            refundStatus = 1,
            refundTotalPrice = recharge.totalPrice
        ))
    }

    @Post("/update-refunded")
    suspend fun updateRefunded(id: Long) {
        val recharge = payWalletLogic.getRecharge(id)
            ?: throw IllegalArgumentException("Recharge not found: $id")
        payWalletLogic.updateRecharge(recharge.copy(refundStatus = 2))
        // Deduct wallet balance
        payWalletLogic.updateBalance(
            walletId = recharge.walletId,
            price = -recharge.totalPrice,
            bizType = 2, // Refund
            bizId = recharge.id,
            title = "Wallet recharge refund"
        )
    }
}
