package controller.app.wallet.dto

import kotlinx.serialization.Serializable

/**
 * 一条冻结的用户可见投影。
 *
 * **只出机器码，不出文案**：客户端有中/繁/英/越四种语言，文案由各端按 `freezeType` /
 * `status` 映射（通用模块不写产品文案，分层规则见 WALLET_WITHDRAW_SPEC §3.1）。
 *
 * 司法冻结的 `reasonText` 里是法律文书细节，**永不下发**——本 VO 压根没有这个字段。
 */
@Serializable
data class WalletFreezeVO(
    val id: Long = 0,
    /** 1=提现冻结 2=资金审核中 3=账户冻结。 */
    val freezeType: Int = 0,
    /**
     * 实际冻住的金额（分）。
     *
     * 对全额司法冻结来说，记录里的 `amount` 是 `null`（无上限），但用户要看到一个数，
     * 所以这里下发的是**按当前余额算出来的实际冻结额**，不是记录里的原始值。
     */
    val amount: Long = 0,
    /** 0=冻结中 1=已解除 2=已扣划 3=已到期。 */
    val status: Int = 0,
    /** 关联单号（提现单 id 等）；账户冻结不下发任何关联信息。 */
    val refId: String? = null,
    val createdAt: Long = 0,
    val releasedAt: Long = 0,
)

@Serializable
data class WalletFreezePageVO(
    val list: List<WalletFreezeVO> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val size: Int = 20,
    val totalPages: Int = 0,
)
