package controller.admin.wallet

import controller.admin.wallet.dto.MarkWalletRechargePaidRequest
import controller.admin.wallet.dto.MarkWalletRechargeRefundRequest
import controller.admin.wallet.dto.MarkWalletRechargeRefundedRequest
import logic.PayWalletLogic
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Post

@Controller("/pay/wallet-recharge")
class PayWalletRechargeController(private val payWalletLogic: PayWalletLogic) {

    @Post("/update-paid")
    suspend fun updatePaid(@Body request: MarkWalletRechargePaidRequest) {
        payWalletLogic.markRechargePaid(request.id, request.payChannelCode)
    }

    @Post("/refund")
    suspend fun refund(@Body request: MarkWalletRechargeRefundRequest) {
        payWalletLogic.markRechargeRefundRequested(request.id)
    }

    @Post("/update-refunded")
    suspend fun updateRefunded(@Body request: MarkWalletRechargeRefundedRequest) {
        payWalletLogic.markRechargeRefunded(request.id)
    }
}
