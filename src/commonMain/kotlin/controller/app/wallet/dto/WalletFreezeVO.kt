package controller.app.wallet.dto

import kotlinx.serialization.Serializable

/**
 * 一条冻结的用户可见投影。
 *
 * **只出机器码，不出文案**：客户端有中/繁/英/越四种语言，文案由各端按 `freezeType` /
 * `status` 映射（通用模块不写产品文案，分层规则见 WALLET_WITHDRAW_SPEC §3.1）。
 *
 * 司法冻结的原因里是法律文书细节，**永不下发**（[reasonText] 对司法冻结恒为 null）。
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
    /**
     * 冻结原因，**给用户看的一句人话**（运营在后台填）。
     *
     * 钱动不了却不说为什么，用户只会以为系统出错或者钱被吞了，然后去找客服——
     * 所以风控冻结必须能把原因带到用户面前。
     *
     * 但**账户冻结（司法）恒为 null**：那栏填的是办案依据，多数司法辖区禁止 tipping-off。
     * 这个区分由服务端投影保证，不靠各端自觉。
     */
    val reasonText: String? = null,
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
