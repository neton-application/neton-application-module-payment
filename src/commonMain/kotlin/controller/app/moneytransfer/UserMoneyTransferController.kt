package controller.app.moneytransfer

import controller.app.moneytransfer.dto.SendMoneyTransferRequest
import logic.MoneySendResultVO
import logic.MoneyTransferDetailVO
import logic.MoneyTransferLogic
import model.MoneyTransferOrder
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.PathVariable
import neton.core.annotations.Post
import neton.core.annotations.Query
import neton.core.interfaces.Identity

/**
 * 用户转账（RP-2，PLATFORM-only 由产品端 gating）。鉴权=本人。
 * 无需接收确认，发送即原子到账（扣发送方 + 入账接收方 + 双边 ledger + 审计）。
 */
@Controller("/money-transfer")
class UserMoneyTransferController(private val logic: MoneyTransferLogic) {

    @Post("/send")
    suspend fun send(identity: Identity, @Body req: SendMoneyTransferRequest): MoneySendResultVO {
        // #85-A2: 返回 {orderId, deliveryStatus, messageId}。code=0 = 资金已可靠受理；卡片交付看 deliveryStatus。
        val r = logic.transfer(identity.id.toLong(), req.toUserId, req.channelId, req.amount, req.remark)
        return MoneySendResultVO.of(r.order.id, r.delivery)
    }

    @Get("/detail/{id}")
    suspend fun detail(@PathVariable id: Long): MoneyTransferDetailVO? {
        // #85-A2: 详情附带 deliveryStatus/messageId（运行时从 outbox 派生，不写订单表）。
        val order = logic.detail(id) ?: return null
        val (ds, mid) = logic.deliveryOf(id)
        return MoneyTransferDetailVO.from(order, ds, mid)
    }

    @Get("/list")
    suspend fun list(identity: Identity, @Query pageNo: Int = 1, @Query pageSize: Int = 20) =
        logic.pageMine(identity.id.toLong(), pageNo, pageSize)
}
