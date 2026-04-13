package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("pay_notify_tasks")
data class PayNotifyTask(
    @Id
    val id: Long = 0,
    val appId: Long,
    val type: Int,
    val dataId: Long,
    val status: Int = 1,
    val merchantUrl: String? = null,
    val notifyTimes: Int = 0,
    val maxNotifyTimes: Int = 0,
    val nextNotifyTime: Long? = null,
    val lastExecuteTime: Long? = null,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
