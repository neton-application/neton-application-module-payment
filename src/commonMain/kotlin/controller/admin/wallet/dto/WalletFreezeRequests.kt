package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

/** 下单笔风控冻结（「这笔钱可能有问题」）。可用余额不足会显式失败。 */
@Serializable
data class PlaceRiskHoldRequest(
    val userId: Long,
    /** 分。 */
    val amount: Long,
    /**
     * 幂等键：同一笔钱重复下达是 no-op。通常填触发冻结的那条账变流水 id；
     * 没有对应流水时运营自己填一个工单号，但**必须唯一**。
     */
    val refId: String,
    val reasonText: String? = null,
)

/** 下账户冻结（司法）。 */
@Serializable
data class PlaceJudicialFreezeRequest(
    val userId: Long,
    /**
     * 目标冻结额（分）。**留空 = 全额冻结**（后续到账继续吸收）。
     *
     * 法院一般冻结具体数额，全额只是「无上限」这一特例，两者走同一套逻辑。
     */
    val targetAmount: Long? = null,
    /** 法律文书号。同时是幂等键，必填。 */
    val legalDocNo: String,
    val reasonText: String? = null,
    /** 到期时间（epoch 毫秒）；0 = 无期限。 */
    val expiresAt: Long = 0,
)
