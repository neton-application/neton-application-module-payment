package controller.app.redpacket

import controller.app.redpacket.dto.SendRedPacketRequest
import logic.MoneySendResultVO
import logic.RedPacketLogic
import model.RedPacketClaim
import model.RedPacketOrder
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.PathVariable
import neton.core.annotations.Post
import neton.core.annotations.Query
import neton.core.interfaces.Identity

/**
 * 用户红包（RP-2，PLATFORM-only 由产品端 gating；后端不感知模式）。鉴权=本人。
 * 发即扣款进托管，领取先到先得，过期退款。资金真相在服务端，消息只搬运引用。
 */
@Controller("/red-packet")
class UserRedPacketController(private val logic: RedPacketLogic) {

    @Post("/send")
    suspend fun send(identity: Identity, @Body req: SendRedPacketRequest): MoneySendResultVO {
        // #85-A2: 返回 {orderId, deliveryStatus, messageId}。code=0 = 资金已可靠受理；卡片交付看 deliveryStatus。
        val r = logic.send(identity.id.toLong(), req.channelId, req.scene, req.type, req.totalAmount, req.totalCount, req.greeting)
        return MoneySendResultVO.of(r.order.id, r.delivery)
    }

    @Post("/claim/{id}")
    suspend fun claim(identity: Identity, @PathVariable id: Long): RedPacketClaim =
        logic.claim(id, identity.id.toLong())

    @Get("/detail/{id}")
    suspend fun detail(@PathVariable id: Long): RedPacketOrder? = logic.detail(id)

    @Get("/claims/{id}")
    suspend fun claims(@PathVariable id: Long): List<RedPacketClaim> = logic.listClaims(id)

    @Get("/list")
    suspend fun list(identity: Identity, @Query pageNo: Int = 1, @Query pageSize: Int = 20) =
        logic.pageMine(identity.id.toLong(), pageNo, pageSize)
}
