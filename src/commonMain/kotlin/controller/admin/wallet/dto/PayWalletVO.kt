package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

/**
 * 后台钱包列表的一行：钱包本身 + 一个它自己答不出来的问题。
 *
 * `freeze_price` 只记金额型冻结（提现占用、单笔风控），账户冻结是「整个账户不许动」，
 * 没有金额，所以在钱包表上看不出来。后台要在操作里显示「账户解冻」，就得知道这件事，
 * 于是在这里补一个 [accountFrozen]。
 */
@Serializable
data class PayWalletVO(
    val id: Long = 0,
    val userId: Long? = null,
    val balance: Long? = null,
    val totalExpense: Long? = null,
    val totalRecharge: Long? = null,
    val freezePrice: Long? = null,
    val createdAt: Long? = null,
    /**
     * 账户是否处于冻结中（司法冻结 ACTIVE 且未到期）。
     *
     * 注意序列化：本项目的响应 Json 是 `encodeDefaults = false`，值等于默认值的字段
     * 整个不出现在 JSON 里。这里 false 被省略是可接受的 —— 前端只在 true 时显示
     * 解冻入口，缺字段与 false 同义。前端仍做了兜底，不依赖这一点。
     */
    val accountFrozen: Boolean = false,
)
