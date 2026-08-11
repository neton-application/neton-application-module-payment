package controller.admin.wallet.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

/**
 * admin 手动划扣（冲正充错、重复到账等）。金额单位：分。
 *
 * 与 [ManualRechargeRequest] 字段一致但不复用同一个类：两者是**方向相反的资金动作**，
 * 共用一个请求体会让「扣了还是充了」只能靠调用的是哪个 URL 来分辨，
 * 日志、审计、以后加字段（比如冲正指向哪一笔充值）都会立刻纠缠在一起。
 */
@Serializable
data class ManualDeductRequest(
    @property:Min(1)
    val userId: Long,

    @property:Min(1)
    val amount: Long,

    /** 划扣依据。必填，且事后只能从这里看出冲正的是哪一笔。 */
    @property:NotBlank
    @property:Size(max = 200)
    val remark: String,
)
