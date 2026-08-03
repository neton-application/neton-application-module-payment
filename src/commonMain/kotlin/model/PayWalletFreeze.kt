package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Id
import neton.database.annotations.Table

/**
 * 一条冻结记录（spec WALLET_FREEZE_SPEC §3）。**冻结的真源**；
 * `pay_wallets.freeze_price` 只是由本表算出来的缓存。
 */
@Serializable
@Table("pay_wallet_freezes")
data class PayWalletFreeze(
    @Id
    val id: Long = 0,
    val walletId: Long,
    val userId: Long,
    /** 见 [logic.WalletFreezeType]。 */
    val freezeType: Int,
    /**
     * 金额型必填；司法冻结全额时为 `null`、定额时为目标额。
     *
     * 这里的 `null` 是**有含义的**（无上限），不是「没填」——不要用
     * `amount ?: 0` 之类的写法把它折叠掉，那会把全额冻结变成不冻。
     */
    val amount: Long? = null,
    /** 见 [logic.WalletFreezeStatus]；默认 0 = ACTIVE。 */
    val status: Int = 0,
    /** 见 [logic.WalletFreezeRefType]。 */
    val refType: Int,
    /** 提现单 id / 账变 id / 法律文书号。与 (freezeType, refType) 一起构成幂等键。 */
    val refId: String,
    val reasonCode: String? = null,
    /** 运营手填。司法冻结的这一段**不下发给用户**。 */
    val reasonText: String? = null,
    val operatorId: Long = 0,
    /** 0 = 无期限。 */
    val expiresAt: Long = 0,
    val createdAt: Long = 0,
    val releasedAt: Long = 0,
    val updatedAt: Long = 0,
)
