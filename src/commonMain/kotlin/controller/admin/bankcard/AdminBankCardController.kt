package controller.admin.bankcard

import logic.OperatorContext
import logic.UserBankCardLogic
import neton.core.annotations.Controller
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

    /** 人工打款/审核时解密完整卡号。返回完整卡号字符串；调用即写审计。 */
    @Post("/reveal/{id}")
    @Permission("pay:bank-card:reveal")
    suspend fun reveal(identity: Identity, ctx: HttpContext, @PathVariable id: Long): String =
        logic.adminRevealCardNo(op = OperatorContext.from(identity, ctx), id = id)
}
