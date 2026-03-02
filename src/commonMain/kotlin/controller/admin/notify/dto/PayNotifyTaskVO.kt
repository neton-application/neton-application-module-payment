package controller.admin.notify.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayNotifyTaskVO(
    val id: Long = 0,
    val appId: Long? = null,
    val type: Int? = null,
    val dataId: Long? = null,
    val status: Int? = null,
    val merchantUrl: String? = null,
    val notifyTimes: Int? = null,
    val maxNotifyTimes: Int? = null,
    val nextNotifyTime: Long? = null,
    val lastExecuteTime: Long? = null,
    val createdAt: String? = null
)
