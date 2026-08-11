package controller.admin.wallet

import model.PayWallet
import controller.admin.wallet.dto.ManualDeductRequest
import controller.admin.wallet.dto.ManualRechargeRequest
import controller.admin.wallet.dto.UpdateWalletBalanceRequest
import controller.admin.wallet.dto.PayWalletVO
import controller.admin.wallet.dto.WalletOverviewVO
import dto.PageResponse
import logic.OperatorContext
import logic.PayWalletLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Permission
import neton.core.annotations.Post
import neton.core.annotations.Put
import neton.core.annotations.Body
import neton.core.annotations.Query
import neton.core.http.BadRequestException
import neton.core.http.HttpContext
import neton.core.interfaces.Identity

@Controller("/pay/wallet")
class PayWalletController(private val payWalletLogic: PayWalletLogic) {

    @Get("/get-by-user-id")
    @Permission("pay:wallet:query")
    suspend fun getByUserId(@Query userId: Long): PayWallet? {
        return payWalletLogic.getWalletByUserId(userId)
    }

    /**
     * 钱包分页。逐行带上「账户是否冻结」——后台要据此显示解冻入口，
     * 而钱包表本身答不出这个问题（账户冻结没有金额，不进 freeze_price）。
     */
    @Get("/page")
    @Permission("pay:wallet:page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query userId: Long? = null,
        @Query username: String? = null,
        @Query nickname: String? = null,
        @Query mobile: String? = null,
    ): PageResponse<PayWalletVO> {
        val result = payWalletLogic.pageWallets(pageNo, pageSize, userId, username, nickname, mobile)
        // 一次查完整页共用，避免逐行去问变成 N+1
        val frozen = payWalletLogic.judiciallyFrozenWalletIds()
        return PageResponse(
            result.list.map { w ->
                PayWalletVO(
                    id = w.id,
                    userId = w.userId,
                    balance = w.balance,
                    totalExpense = w.totalExpense,
                    totalRecharge = w.totalRecharge,
                    freezePrice = w.freezePrice,
                    createdAt = w.createdAt,
                    accountFrozen = w.id in frozen,
                )
            },
            result.total,
            result.page,
            result.size,
            result.totalPages,
        )
    }

    @Put("/update-balance")
    @Permission("pay:wallet:update")
    suspend fun updateBalance(identity: Identity, ctx: HttpContext, @Body req: UpdateWalletBalanceRequest) {
        payWalletLogic.adjustBalance(OperatorContext.from(identity, ctx), req.userId, req.balance)
    }

    /** 手动充值（银行汇款/异常手动到账等）：正数入账 + 备注进账变，可追溯。 */
    @Post("/manual-recharge")
    @Permission("pay:wallet:update")
    suspend fun manualRecharge(identity: Identity, ctx: HttpContext, @Body req: ManualRechargeRequest) {
        val remark = req.remark.trim()
        if (remark.isEmpty()) throw BadRequestException("充值备注必填")
        payWalletLogic.manualRecharge(OperatorContext.from(identity, ctx), req.userId, req.amount, remark)
    }

    /**
     * 手动划扣：冲正充错、重复到账这类人工失误。
     *
     * 与充值同一个权限点：能凭空加钱的人本来就能把它扣回去，拆成两个权限只会
     * 让「充错了却没权限改」变成常态。余额不足、动到冻结资金都会被拒（见 logic）。
     */
    @Post("/manual-deduct")
    @Permission("pay:wallet:update")
    suspend fun manualDeduct(identity: Identity, ctx: HttpContext, @Body req: ManualDeductRequest) {
        val remark = req.remark.trim()
        if (remark.isEmpty()) throw BadRequestException("划扣备注必填")
        payWalletLogic.manualDeduct(OperatorContext.from(identity, ctx), req.userId, req.amount, remark)
    }

    /** 财务总览轻量版（P1）：钱包资金聚合 + 提现分状态聚合。 */
    @Get("/overview")
    @Permission("pay:wallet:overview")
    suspend fun overview(): WalletOverviewVO = payWalletLogic.getFinanceOverview()
}
