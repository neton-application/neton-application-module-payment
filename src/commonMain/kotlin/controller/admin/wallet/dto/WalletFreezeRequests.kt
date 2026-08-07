package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

/** 下单笔风控冻结（「这笔钱可能有问题」）。可用余额不足会显式失败。 */
@Serializable
data class PlaceRiskHoldRequest(
    val userId: Long,
    /** 分。 */
    val amount: Long,
    /**
     * 幂等键，**可留空**。
     *
     * 有对应账变流水或工单时填它，重复下达就是 no-op（重试、并发、运营手抖点两次都安全）。
     * 留空表示「这次冻结没有外部单据」——服务端按 用户+操作员+毫秒 生成一个，
     * 于是同一个人在同一毫秒内的重复提交仍然被幂等吃掉，正常的多次冻结则各自成一条。
     *
     * 之所以放开必填：多数风控冻结是运营看着账目当场决定的，没有单号可填，
     * 强制必填只会逼出「1」「test」这种假单号，反而让幂等键失去意义。
     */
    val refId: String? = null,
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
    /**
     * 法律文书号，**可留空**（紧急处置常常先冻结、文书后补）。
     *
     * 填了就是幂等键，重复下达是 no-op，并且会作为「关联单号」显示给用户；
     * 留空则服务端自造一个内部幂等键，那串东西**不下发**（见 WalletFreezeLogic.AUTO_REF_PREFIX）。
     */
    val legalDocNo: String? = null,
    /** 冻结说明。**会显示在用户的冻结详情里**，别往里写办案细节。 */
    val reasonText: String? = null,
    /** 到期时间（epoch 毫秒）；0 = 无期限。 */
    val expiresAt: Long = 0,
)
