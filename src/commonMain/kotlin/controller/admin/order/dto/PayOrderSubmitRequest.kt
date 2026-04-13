package controller.admin.order.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class PayOrderSubmitRequest(
    @property:Min(1)
    val appId: Long,

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val merchantOrderId: String,

    @property:NotBlank
    @property:Size(min = 2, max = 128)
    val subject: String,

    @property:Size(min = 0, max = 1000)
    val body: String? = null,

    @property:Min(1)
    val price: Long,

    @property:Size(min = 1, max = 64)
    val channelCode: String? = null,

    @property:Size(min = 7, max = 64)
    val userIp: String? = null,

    @property:Min(0)
    val expireTime: Long? = null
)
