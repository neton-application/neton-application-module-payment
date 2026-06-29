package controller.app.bankcard

import controller.app.bankcard.dto.BindBankCardRequest
import logic.BankCardView
import logic.UserBankCardLogic
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Delete
import neton.core.annotations.Get
import neton.core.annotations.PathVariable
import neton.core.annotations.Post
import neton.core.interfaces.Identity

/**
 * 用户银行卡（P4-B1）。鉴权=本人（[Identity]）。所有返回**只含 masked**，绝不返回完整卡号。
 */
@Controller("/wallet/bank-cards")
class UserBankCardController(private val logic: UserBankCardLogic) {

    /** 绑定银行卡。 */
    @Post("/bind")
    suspend fun bind(identity: Identity, @Body request: BindBankCardRequest): BankCardView =
        logic.bindBankCard(
            userId = identity.id.toLong(),
            holderName = request.holderName,
            bankName = request.bankName,
            bankCode = request.bankCode,
            cardNo = request.cardNo,
        )

    /** 我的银行卡列表（masked）。 */
    @Get("/list")
    suspend fun list(identity: Identity): List<BankCardView> =
        logic.listMyBankCards(identity.id.toLong())

    /** 软删自己的银行卡。 */
    @Delete("/delete/{id}")
    suspend fun delete(identity: Identity, @PathVariable id: Long): Boolean =
        logic.deleteBankCard(identity.id.toLong(), id)
}
