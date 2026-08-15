package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("pay_channels")
data class PayChannel(
    @Id
    val id: Long = 0,
    /** 通道编码，业务侧唯一，如 alipay_1 / wechat_2。前端下单时传它。 */
    val code: String,
    /** 归属平台，对应 PayPlatform.platformCode（huifu / qijiu …）。 */
    val platformCode: String = "",
    /** 支付方式，决定前端图标与分组；同一方式可由多条通道提供。 */
    val method: String = "OTHER",
    /** 平台侧的通道标识（平台自己的编号），下单时透传给平台。 */
    val platformChannelId: String? = null,
    /** 呈现方式：REDIRECT_URL / QR_CODE / SDK_PARAMS。 */
    val displayMode: String = "REDIRECT_URL",
    /** 本通道的凭据（商户号/密钥等）JSON。同平台不同通道可能各有各的商户号。 */
    val config: String,
    val status: Int = 1,
    val feeRate: Int = 0,
    val remark: String? = null,
    @CreatedAt
    val createdAt: Long? = null,
    @UpdatedAt
    val updatedAt: Long? = null
)
