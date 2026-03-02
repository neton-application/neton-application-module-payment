package controller.admin.app.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateStatusReqVO(
    val id: Long,
    val status: Int
)
