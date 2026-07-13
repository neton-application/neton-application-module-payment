package controller.app.redpacket

import controller.app.redpacket.dto.SendRedPacketRequest
import logic.RedPacketDetailVO
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
    suspend fun send(identity: Identity, @Body req: SendRedPacketRequest): RedPacketDetailVO {
        // #85-A2: 向后兼容——保留完整订单字段（旧客户端不破）+ 追加 orderId/deliveryStatus/messageId。
        // code=0 = 资金已可靠受理；卡片是否已注入会话看 deliveryStatus。
        val r = logic.send(identity.id.toLong(), req.channelId, req.scene, req.type, req.totalAmount, req.totalCount, req.greeting)
        return RedPacketDetailVO.from(r.order, r.delivery.statusText, r.delivery.serverMessageIdOrNull?.toString())
    }

    @Post("/claim/{id}")
    suspend fun claim(identity: Identity, @PathVariable id: Long): RedPacketClaim =
        logic.claim(id, identity.id.toLong())

    @Get("/detail/{id}")
    suspend fun detail(@PathVariable id: Long): RedPacketDetailVO? {
        // #85-A2: 详情附带 deliveryStatus/messageId（运行时从 outbox 派生，不写订单表）。
        val order = logic.detail(id) ?: return null
        val (ds, mid) = logic.deliveryOf(id)
        return RedPacketDetailVO.from(order, ds, mid)
    }

    @Get("/claims/{id}")
    suspend fun claims(@PathVariable id: Long): List<RedPacketClaim> = logic.listClaims(id)

    @Get("/list")
    suspend fun list(identity: Identity, @Query pageNo: Int = 1, @Query pageSize: Int = 20) =
        logic.pageMine(identity.id.toLong(), pageNo, pageSize)
}
