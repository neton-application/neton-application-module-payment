package controller.admin.wallet.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class CreatePayWalletRechargePackageRequest(
    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:Min(0)
    val payPrice: Long,

    @property:Min(0)
    val bonusPrice: Long = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1
)

@Serializable
data class UpdatePayWalletRechargePackageRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:Min(0)
    val payPrice: Long,

    @property:Min(0)
    val bonusPrice: Long = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1
)
