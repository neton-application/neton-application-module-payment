package controller.admin.app.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min

@Serializable
data class UpdatePayAppStatusRequest(
    @property:Min(0)
    @property:Max(1)
    val status: Int
)
