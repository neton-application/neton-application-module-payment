package controller.admin.wallet.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

/** admin 手动充值（银行汇款/异常手动到账等线下入账）。金额单位：分。 */
@Serializable
data class ManualRechargeRequest(
    @property:Min(1)
    val userId: Long,

    @property:Min(1)
    val amount: Long,

    @property:NotBlank
    @property:Size(max = 200)
    val remark: String,
)
