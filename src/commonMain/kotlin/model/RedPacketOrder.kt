package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id

/**
 * 红包订单（RP-2，PrivChat Money Message）。发红包即从发送方扣全额进托管（ledger 400），
 * `remaining_amount` 是托管余额真相；领取减 remaining（ledger 401），过期退剩余给发送方（ledger 402）。
 * MVP：普通红包（均分、先到先得）；`type=1` 拼手气预留。金额 bigint(分)，时间 epoch ms。
 */
@Serializable
@Table("red_packet_orders")
data class RedPacketOrder(
    @Id
    val id: Long = 0,
    val senderUserId: Long,
    /** IM 场景引用（不透明字符串，payment 不解释）。 */
    val channelId: String = "",
    /** 0=dm 1=group */
    val scene: Int = 0,
    /** 0=Normal 均分(MVP) / 1=Lucky 拼手气(预留，逻辑后置) */
    val type: Int = 0,
    val totalAmount: Long,
    val totalCount: Int,
    val remainingAmount: Long,
    val remainingCount: Int,
    /** 0 ACTIVE / 1 FINISHED(领完) / 2 EXPIRED(已退) / 3 REFUNDING(退款抢占中) */
    val status: Int = 0,
    val greeting: String? = null,
    val expireAt: Long = 0,
    val createdAt: Long = 0,
    val finishedAt: Long = 0,
)
