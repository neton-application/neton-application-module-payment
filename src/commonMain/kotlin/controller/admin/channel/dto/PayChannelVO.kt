package controller.admin.channel.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class PayChannelVO(
    val id: Long = 0,
    val appId: Long? = null,
    val code: String? = null,
    val config: String? = null,
    val status: Int? = null,
    val feeRate: Int? = null,
    val remark: String? = null,
    val createdAt: String? = null
)

@Serializable
data class CreatePayChannelRequest(
    @property:Min(1)
    val appId: Long,

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val code: String,

    @property:NotBlank
    @property:Size(min = 2, max = 4000)
    val config: String,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Min(0)
    @property:Max(10000)
    val feeRate: Int = 0,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)

@Serializable
data class UpdatePayChannelRequest(
    @property:Min(1)
    val id: Long,

    @property:Min(1)
    val appId: Long,

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val code: String,

    @property:NotBlank
    @property:Size(min = 2, max = 4000)
    val config: String,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Min(0)
    @property:Max(10000)
    val feeRate: Int = 0,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)
