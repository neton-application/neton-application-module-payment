package controller.admin.channel.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class PayChannelVO(
    val id: Long = 0,
    val code: String? = null,
    /** 归属平台（汇付天下 / 七九 …）。同平台可挂多条通道。 */
    val platformCode: String? = null,
    /** 支付方式：ALIPAY / WECHAT / …，决定前端图标与分组。 */
    val method: String? = null,
    /** 平台侧通道标识，下单时透传。 */
    val platformChannelId: String? = null,
    val displayMode: String? = null,
    val config: String? = null,
    val status: Int? = null,
    val feeRate: Int? = null,
    val remark: String? = null,
    val createdAt: Long? = null
)

@Serializable
data class CreatePayChannelRequest(
    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val code: String,

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val platformCode: String,

    @property:NotBlank
    @property:Size(min = 2, max = 32)
    val method: String,

    @property:Size(min = 0, max = 128)
    val platformChannelId: String? = null,

    @property:Size(min = 2, max = 32)
    val displayMode: String = "REDIRECT_URL",

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

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val code: String,

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val platformCode: String,

    @property:NotBlank
    @property:Size(min = 2, max = 32)
    val method: String,

    @property:Size(min = 0, max = 128)
    val platformChannelId: String? = null,

    @property:Size(min = 2, max = 32)
    val displayMode: String = "REDIRECT_URL",

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
