package controller.app.wallet

import controller.app.wallet.dto.CreateWalletRechargeRequest
import logic.PayOrderLogic
import logic.PayWalletLogic
import logic.PaymentErrorCodes
import logic.WalletRechargePaidListener
import model.PayOrder
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Post
import neton.core.annotations.Query
import neton.core.interfaces.Identity

@Controller("/pay/wallet-recharge")
class PayWalletRechargeController(
    private val payWalletLogic: PayWalletLogic,
    private val payOrderLogic: PayOrderLogic,
) {

    /** 建充值单（未支付）。要发起支付请接着调 [submit]。 */
    @Post("/create")
    suspend fun create(identity: Identity, @Body request: CreateWalletRechargeRequest): Long {
        return payWalletLogic.rechargeForUser(
            userId = identity.id.toLong(),
            totalPrice = request.totalPrice,
            payPrice = request.payPrice,
            bonusPrice = request.bonusPrice,
            packageId = request.packageId
        )
    }

    /**
     * 充值单发起支付：建支付订单并返回收银台信息。
     *
     * 支付成功后由 `WalletRechargePaidListener` 在支付事务内到账 ——
     * 此处不做任何余额变更，避免出现"下单即到账"这种绕过支付的路径。
     *
     * 只允许给**自己的、未支付的**充值单发起支付。
     */
    @Post("/submit")
    suspend fun submit(
        identity: Identity,
        @Query rechargeId: Long,
        /** 支付通道码，取自 `/app/pay/channel/list`。 */
        @Query channelCode: String,
    ): PayOrderLogic.PrepayVO {
        val recharge = payWalletLogic.getRecharge(rechargeId)
            ?: throw neton.core.http.NotFoundException(PaymentErrorCodes.WALLET_RECHARGE_NOT_FOUND)
        val wallet = payWalletLogic.getWallet(identity.id.toLong())
        if (wallet == null || recharge.walletId != wallet.id) {
            throw neton.core.http.NotFoundException(PaymentErrorCodes.WALLET_RECHARGE_NOT_FOUND)
        }
        if (recharge.payStatus != 0) throw neton.core.http.BadRequestException(PaymentErrorCodes.WALLET_RECHARGE_ALREADY_PAID)

        return payOrderLogic.submit(
            PayOrder(
                merchantOrderId = WalletRechargePaidListener.merchantOrderId(rechargeId),
                subject = "钱包充值",
                price = recharge.payPrice,
                channelCode = channelCode,
            )
        )
    }

    @Get("/page")
    suspend fun page(
        identity: Identity,
        pageNo: Int = 1,
        pageSize: Int = 20,
        payStatus: Int? = null
    ) = payWalletLogic.pageRechargesForUser(identity.id.toLong(), pageNo, pageSize, payStatus)
}
