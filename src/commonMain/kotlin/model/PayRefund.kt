package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("pay_refunds")
data class PayRefund(
    @Id
    val id: Long = 0,
    val appId: Long,
    val orderId: Long,
    val merchantRefundId: String,
    val channelCode: String? = null,
    val channelRefundNo: String? = null,
    val payPrice: Long,
    val refundPrice: Long,
    val reason: String? = null,
    val status: Int = 0,
    val successTime: Long? = null,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
