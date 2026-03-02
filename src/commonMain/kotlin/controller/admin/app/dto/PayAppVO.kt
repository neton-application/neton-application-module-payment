package controller.admin.app.dto

import kotlinx.serialization.Serializable

@Serializable
data class PayAppVO(
    val id: Long = 0,
    val name: String? = null,
    val status: Int? = null,
    val remark: String? = null,
    val createdAt: String? = null
)
