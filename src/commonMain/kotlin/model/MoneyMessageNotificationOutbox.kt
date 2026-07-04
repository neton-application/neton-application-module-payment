package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id

/**
 * Money Message 通知 outbox（RP-7-A）。RedPacketLogic 在 claim/empty/expire 成功时与资金动作
 * **同事务**写入一行；module-privchat 的 adapter/job 后续读 PENDING 消费并注入 IM notification。
 * payload_json 由 payment 产出（IDs + 金额 + 进度 + 状态），用户名等展示字段由 adapter 解析。
 */
@Serializable
@Table("pay_money_message_notification_outbox")
data class MoneyMessageNotificationOutbox(
    @Id
    val id: Long = 0,
    /** RED_PACKET_RECEIVED / RED_PACKET_EMPTY / RED_PACKET_EXPIRED */
    val eventType: String,
    val channelId: String = "",
    val scene: Int = 0,
    val redPacketId: Long = 0,
    /** RECEIVED: 领取人；EMPTY/EXPIRED: 发送人。 */
    val relatedUserId: Long = 0,
    /** RECEIVED: 发送人；其它 0。 */
    val targetUserId: Long = 0,
    val payloadJson: String,
    /** 0 PENDING / 1 SENT / 2 FAILED */
    val status: Int = 0,
    val retryCount: Int = 0,
    val nextRetryAt: Long = 0,
    val lastError: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val sentAt: Long = 0,
)
