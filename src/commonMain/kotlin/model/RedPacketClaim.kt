package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id

/**
 * 红包领取记录（RP-2/RP-3）。唯一键 (red_packet_id, user_id) 防重复领（见 V008）；
 * 每条领取对应一笔 ledger 401(biz_id=claim.id)。金额 bigint(分)，时间 epoch ms。
 */
@Serializable
@Table("red_packet_claims")
data class RedPacketClaim(
    @Id
    val id: Long = 0,
    val redPacketId: Long,
    val userId: Long,
    val amount: Long,
    val claimedAt: Long = 0,
)
