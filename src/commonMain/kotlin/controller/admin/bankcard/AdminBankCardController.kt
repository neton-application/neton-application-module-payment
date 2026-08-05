package controller.admin.bankcard

import logic.BankCardView
import logic.OperatorContext
import logic.UserBankCardLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.PathVariable
import neton.core.annotations.Permission
import neton.core.annotations.Post
import neton.core.http.HttpContext
import neton.core.interfaces.Identity

/**
 * 后台银行卡（P4-B1）。仅打款/审核权限可解密完整卡号，且每次 reveal 都写审计日志。
 * 普通详情/列表接口永不返回完整卡号——完整卡号只在本 reveal 专用接口返回。
 */
// 路由组 admin 由包名推断 → 框架挂 /admin 前缀；@Controller 写相对路径即可。
@Controller("/wallet/bank-cards")
class AdminBankCardController(private val logic: UserBankCardLogic) {

    /**
     * 查某个用户绑定的银行卡（仅掩码）。
     *
     * 和 [reveal] 分成两个权限点：看「这人绑了哪几张卡、开户行是谁」是日常客服/风控
     * 就要做的事，解密完整卡号是打款时才做的事。合成一个权限点就等于让所有能查卡的人
     * 都能拿到完整卡号——那是 P4-B1 特意避开的。
     */
    @Get("/user/{userId}")
    @Permission("pay:bank-card:list")
    suspend fun listByUser(@PathVariable userId: Long): List<BankCardView> =
        logic.listMyBankCards(userId)

    /** 人工打款/审核时解密完整卡号。返回完整卡号字符串；调用即写审计。 */
    @Post("/reveal/{id}")
    @Permission("pay:bank-card:reveal")
    suspend fun reveal(identity: Identity, ctx: HttpContext, @PathVariable id: Long): String =
        logic.adminRevealCardNo(op = OperatorContext.from(identity, ctx), id = id)
}
