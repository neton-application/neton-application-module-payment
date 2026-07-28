package controller.app.withdraw

import controller.app.withdraw.dto.CreateWithdrawRequest
import controller.app.withdraw.dto.WithdrawOrderUserVO
import controller.app.withdraw.dto.toUserVO
import dto.PageResponse
import logic.OperatorContext
import logic.WalletWithdrawLogic
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.PathVariable
import neton.core.annotations.Post
import neton.core.annotations.Query
import neton.core.http.HttpContext
import neton.core.interfaces.Identity

/**
 * 用户提现（P4-C）。鉴权=本人。提交即冻结资金（PENDING），等后台人工审核打款。
 */
@Controller("/wallet/withdraw")
class UserWithdrawController(private val logic: WalletWithdrawLogic) {

    /** 提交提现申请：校验可用余额 → 建单 PENDING + 冻结。 */
    @Post("/create")
    suspend fun create(identity: Identity, ctx: HttpContext, @Body request: CreateWithdrawRequest): WithdrawOrderUserVO =
        logic.createWithdrawOrder(
            op = OperatorContext.from(identity, ctx),
            bankCardId = request.bankCardId,
            amount = request.amount,
            currency = request.currency,
        ).toUserVO()

    /** 我的提现订单（分页倒序）。 */
    @Get("/list")
    suspend fun list(
        identity: Identity,
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
    ): PageResponse<WithdrawOrderUserVO> =
        logic.listMyWithdrawOrders(identity.id.toLong(), pageNo, pageSize).let { p ->
            PageResponse(p.list.map { it.toUserVO() }, p.total, p.page, p.size, p.totalPages)
        }

    /** 我的提现详情。 */
    @Get("/detail/{id}")
    suspend fun detail(identity: Identity, @PathVariable id: Long): WithdrawOrderUserVO? =
        logic.getMyDetail(identity.id.toLong(), id)?.toUserVO()

    /** 取消（仅 PENDING）：解冻。 */
    @Post("/cancel/{id}")
    suspend fun cancel(identity: Identity, ctx: HttpContext, @PathVariable id: Long): WithdrawOrderUserVO =
        logic.cancel(OperatorContext.from(identity, ctx), id).toUserVO()
}
