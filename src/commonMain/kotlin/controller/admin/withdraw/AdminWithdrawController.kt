package controller.admin.withdraw

import controller.admin.withdraw.dto.WithdrawApproveRequest
import controller.admin.withdraw.dto.WithdrawMarkFailedRequest
import controller.admin.withdraw.dto.WithdrawMarkPaidRequest
import controller.admin.withdraw.dto.WithdrawRejectRequest
import logic.OperatorContext
import logic.WalletWithdrawLogic
import model.WalletWithdrawOrder
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.PathVariable
import neton.core.annotations.Permission
import neton.core.annotations.Post
import neton.core.annotations.Query
import neton.core.http.HttpContext
import neton.core.interfaces.Identity

/**
 * 后台提现订单（P4-C）。第一版「财务中心/提现订单」统一一个页面，用状态筛选；
 * 操作按钮按权限点细分（list/detail/approve/reject/mark-paid/mark-failed）。人工打款。
 */
// 路由组由包名 controller.admin.* 推断为 admin → 框架自动挂 /admin 前缀；
// @Controller 只写相对业务路径（不要再写 /admin，否则双前缀 /admin/admin/...）。
@Controller("/wallet/withdraw")
class AdminWithdrawController(private val logic: WalletWithdrawLogic) {

    /** 提现订单分页（状态/用户筛选）。 */
    @Get("/page")
    @Permission("pay:withdraw:list")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query status: Int? = null,
        @Query userId: Long? = null,
    ) = logic.pageWithdrawOrders(pageNo, pageSize, status, userId)

    @Get("/detail/{id}")
    @Permission("pay:withdraw:detail")
    suspend fun detail(@PathVariable id: Long): WalletWithdrawOrder? = logic.getDetail(id)

    /** 审核通过（PENDING→APPROVED）。 */
    @Post("/approve/{id}")
    @Permission("pay:withdraw:approve")
    suspend fun approve(identity: Identity, ctx: HttpContext, @PathVariable id: Long, @Body request: WithdrawApproveRequest): WalletWithdrawOrder =
        logic.approve(OperatorContext.from(identity, ctx), id, request.remark)

    /** 驳回（PENDING→REJECTED + 解冻）。 */
    @Post("/reject/{id}")
    @Permission("pay:withdraw:reject")
    suspend fun reject(identity: Identity, ctx: HttpContext, @PathVariable id: Long, @Body request: WithdrawRejectRequest): WalletWithdrawOrder =
        logic.reject(OperatorContext.from(identity, ctx), id, request.reason)

    /** 标记已打款（APPROVED/PROCESSING→PAID + 从冻结实扣）。 */
    @Post("/mark-paid/{id}")
    @Permission("pay:withdraw:mark-paid")
    suspend fun markPaid(identity: Identity, ctx: HttpContext, @PathVariable id: Long, @Body request: WithdrawMarkPaidRequest): WalletWithdrawOrder =
        logic.markPaid(OperatorContext.from(identity, ctx), id, request.payoutTradeNo)

    /** 标记失败（APPROVED/PROCESSING→FAILED + 解冻）。 */
    @Post("/mark-failed/{id}")
    @Permission("pay:withdraw:mark-failed")
    suspend fun markFailed(identity: Identity, ctx: HttpContext, @PathVariable id: Long, @Body request: WithdrawMarkFailedRequest): WalletWithdrawOrder =
        logic.markFailed(OperatorContext.from(identity, ctx), id, request.reason)
}
