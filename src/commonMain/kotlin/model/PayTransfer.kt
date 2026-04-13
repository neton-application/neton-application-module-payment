package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("pay_transfers")
data class PayTransfer(
    @Id
    val id: Long = 0,
    val appId: Long,
    val channelCode: String? = null,
    val merchantTransferId: String,
    val type: Int,
    val price: Long,
    val subject: String,
    val userName: String? = null,
    val accountNo: String? = null,
    val status: Int = 1,
    val successTime: Long? = null,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
