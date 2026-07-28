package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

/**
 * 提现订单（P4-C）。申请时只冻结资金（不扣 balance），PAID 才从冻结实扣。
 * status 见 [logic.WithdrawStateMachine]。金额 bigint(分)。
 */
@Serializable
@Table("wallet_withdraw_orders")
data class WalletWithdrawOrder(
    @Id
    val id: Long = 0,
    val userId: Long,
    val walletId: Long,
    val bankCardId: Long,
    val amount: Long,
    val fee: Long = 0,
    val actualAmount: Long,
    val currency: String = "CNY",
    /** 0=PENDING 1=APPROVED 2=PROCESSING 3=PAID 4=REJECTED 5=FAILED 6=CANCELLED 7=ON_HOLD */
    val status: Int = 0,
    val reviewerId: Long = 0,
    /** 内部审核备注（仅后台）。 */
    val reviewRemark: String? = null,
    /** 驳回/失败原因（客户可见）。 */
    val freezeRemarkUserVisible: String? = null,
    /** 内部失败原因。 */
    val failureReason: String? = null,
    // ── 挂起（spec WALLET_WITHDRAW_SPEC §10）──
    /**
     * 解除挂起后要回到的在途状态；非挂起时为 0。
     * **不能省**：少了它，已过审(APPROVED)的单子解除后会被打回 PENDING 重审。
     */
    val holdResumeTo: Int = 0,
    /** 挂起原因码（BANK_CUTOFF / CARD_UNUSABLE / NAME_MISMATCH / COMPLIANCE_REVIEW / OTHER）。 */
    val holdReasonCode: String? = null,
    /**
     * 供各端按 locale 渲染的参数（JSON），如 `{"bank":"ICBC","resume_at":178...}`。
     * 用户可见文案**不入库** —— 客户端有中/繁/英/越四语言，存中文会让非中文用户看到中文。
     */
    val holdReasonParams: String? = null,
    /** 内部备注，绝不下发给用户。 */
    val holdNoteInternal: String? = null,
    val holdAt: Long = 0,
    val holdBy: Long = 0,
    // 代付通道扩展位（第一版人工打款留空）。
    val paymentChannelId: Long = 0,
    val payoutChannel: String? = null,
    val payoutTradeNo: String? = null,
    @CreatedAt
    val createdAt: Long? = null,
    val reviewedAt: Long = 0,
    val paidAt: Long = 0,
    @UpdatedAt
    val updatedAt: Long? = null,
)
