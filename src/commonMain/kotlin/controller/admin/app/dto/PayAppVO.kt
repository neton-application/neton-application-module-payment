package controller.admin.app.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class PayAppVO(
    val id: Long = 0,
    val name: String? = null,
    val status: Int? = null,
    val remark: String? = null,
    val createdAt: String? = null
)

@Serializable
data class CreatePayAppRequest(
    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val name: String,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)

@Serializable
data class UpdatePayAppRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val name: String,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)
