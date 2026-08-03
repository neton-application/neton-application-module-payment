package controller.admin.wallet

import controller.admin.wallet.dto.PlaceJudicialFreezeRequest
import controller.admin.wallet.dto.PlaceRiskHoldRequest
import dto.PageResponse
import logic.OperatorContext
import logic.WalletFreezeLogic
import model.PayWalletFreeze
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
 * 后台冻结管理（spec WALLET_FREEZE_SPEC）。
 *
 * 两种冻结分成两个入口，**由运营显式选择类型**，不做自动判定：
 *  - `/risk-hold` 单笔风控冻结：金额确定，可用余额不足会失败。
 *  - `/judicial`  账户冻结：金额随余额浮动，到账即冻；余额为 0 时下达同样有效。
 *
 * 提现冻结不在这里下达/解除——那由提现状态机管，在这里只读得到。
 */
@Controller("/wallet/freeze")
class AdminWalletFreezeController(private val logic: WalletFreezeLogic) {

    /** 冻结分页（用户/类型/状态筛选）。 */
    @Get("/page")
    @Permission("pay:wallet-freeze:list")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query userId: Long? = null,
        @Query freezeType: Int? = null,
        @Query status: Int? = null,
    ): PageResponse<PayWalletFreeze> {
        val (items, total) = logic.pageFreezes(pageNo, pageSize, userId, freezeType, status)
        val size = pageSize.coerceAtLeast(1)
        return PageResponse(items, total, pageNo, pageSize, ((total + size - 1) / size).toInt())
    }

    /** 单笔风控冻结。 */
    @Post("/risk-hold")
    @Permission("pay:wallet-freeze:place")
    suspend fun placeRiskHold(
        identity: Identity,
        ctx: HttpContext,
        @Body request: PlaceRiskHoldRequest,
    ): PayWalletFreeze = logic.placeRiskHold(
        op = OperatorContext.from(identity, ctx),
        userId = request.userId,
        amount = request.amount,
        refId = request.refId,
        reasonText = request.reasonText,
    )

    /**
     * 账户冻结（司法）。**独立权限点**：这是对用户全部资产的强制处分，
     * 不该和单笔风控冻结共用一个授权。
     */
    @Post("/judicial")
    @Permission("pay:wallet-freeze:judicial")
    suspend fun placeJudicial(
        identity: Identity,
        ctx: HttpContext,
        @Body request: PlaceJudicialFreezeRequest,
    ): PayWalletFreeze = logic.placeJudicialFreeze(
        op = OperatorContext.from(identity, ctx),
        userId = request.userId,
        targetAmount = request.targetAmount,
        legalDocNo = request.legalDocNo,
        reasonText = request.reasonText,
        expiresAt = request.expiresAt,
    )

    /**
     * 把到期的冻结翻成 EXPIRED。
     *
     * 到期本身**不需要**这个接口：过了期限的冻结在可用余额计算里当场失效
     * （见 `WalletFreezeLogic.activeFreezes`）。这里只是把状态和缓存追上事实，
     * 供运维手动触发或将来挂定时任务。所以权限点复用「解除冻结」。
     */
    @Post("/sweep-expired")
    @Permission("pay:wallet-freeze:release")
    suspend fun sweepExpired(@Query limit: Int = 200): Int = logic.sweepExpired(limit)

    /** 解除冻结（放行，钱回到可用余额）。 */
    @Post("/release/{id}")
    @Permission("pay:wallet-freeze:release")
    suspend fun release(
        identity: Identity,
        ctx: HttpContext,
        @PathVariable id: Long,
    ): Boolean = logic.release(OperatorContext.from(identity, ctx), id)
}
