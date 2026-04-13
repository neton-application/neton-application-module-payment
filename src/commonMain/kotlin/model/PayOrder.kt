package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("pay_orders")
data class PayOrder(
    @Id
    val id: Long = 0,
    val appId: Long,
    val merchantOrderId: String,
    val subject: String,
    val body: String? = null,
    val price: Long,
    val channelCode: String? = null,
    val channelOrderNo: String? = null,
    val status: Int = 1,
    val userIp: String? = null,
    val expireTime: Long? = null,
    val successTime: Long? = null,
    val notifyTime: Long? = null,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
