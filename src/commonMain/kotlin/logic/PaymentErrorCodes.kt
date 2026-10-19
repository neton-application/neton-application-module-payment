package logic

/**
 * payment 模块的业务错误机器码（套件规则：HttpException 的 message 只放机器码，细节进字段错误）。
 * 数值类别（NetonErrorCode）由抛出点决定，见规范 `neton-docs/.../spec/application/payment-errors.md`。
 * 机器码一经发布即是对外契约，不改名。
 */
object PaymentErrorCodes {
    // ---- 通用参数 ----
    /** 金额非法（≤0 / 超出订单金额等）；字段错误给出具体字段。 */
    const val AMOUNT_INVALID = "AMOUNT_INVALID"
    /** 后台手动充值 / 划扣的备注必填。 */
    const val REMARK_REQUIRED = "REMARK_REQUIRED"

    // ---- 钱包 / 余额 / 冻结 ----
    const val WALLET_NOT_FOUND = "WALLET_NOT_FOUND"
    /** 余额不足（扣完会变负）。 */
    const val WALLET_BALANCE_INSUFFICIENT = "WALLET_BALANCE_INSUFFICIENT"
    /** 可用余额（余额 − 冻结）不足：钱在，但被冻结了。 */
    const val WALLET_AVAILABLE_BALANCE_INSUFFICIENT = "WALLET_AVAILABLE_BALANCE_INSUFFICIENT"
    /** 解冻 / 冻结实扣的金额超过当前冻结金额。 */
    const val WALLET_FROZEN_AMOUNT_INSUFFICIENT = "WALLET_FROZEN_AMOUNT_INSUFFICIENT"
    /** 冻结终态参数非法（不能是 ACTIVE）。 */
    const val WALLET_FREEZE_STATUS_INVALID = "WALLET_FREEZE_STATUS_INVALID"
    /** 钱包已有生效中的账户（司法）冻结。 */
    const val WALLET_ALREADY_FROZEN = "WALLET_ALREADY_FROZEN"
    /** 提现冻结只能由提现状态机解除，不能在冻结管理里直接释放。 */
    const val WALLET_FREEZE_WITHDRAW_HOLD = "WALLET_FREEZE_WITHDRAW_HOLD"

    // ---- 充值 ----
    const val WALLET_RECHARGE_NOT_FOUND = "WALLET_RECHARGE_NOT_FOUND"
    const val WALLET_RECHARGE_ALREADY_PAID = "WALLET_RECHARGE_ALREADY_PAID"
    const val WALLET_RECHARGE_NOT_PAID = "WALLET_RECHARGE_NOT_PAID"
    /** 充值单已进入退款流程，不能再标记支付。 */
    const val WALLET_RECHARGE_REFUND_IN_PROGRESS = "WALLET_RECHARGE_REFUND_IN_PROGRESS"
    const val WALLET_RECHARGE_REFUND_ALREADY_REQUESTED = "WALLET_RECHARGE_REFUND_ALREADY_REQUESTED"
    /** 标记已退款前必须先发起退款。 */
    const val WALLET_RECHARGE_REFUND_NOT_REQUESTED = "WALLET_RECHARGE_REFUND_NOT_REQUESTED"
    /** 充值单被并发修改（乐观锁），请重试。 */
    const val WALLET_RECHARGE_CONCURRENT_MODIFIED = "WALLET_RECHARGE_CONCURRENT_MODIFIED"
    const val WALLET_RECHARGE_PACKAGE_NOT_FOUND = "WALLET_RECHARGE_PACKAGE_NOT_FOUND"

    // ---- 转账 ----
    const val TRANSFER_TO_SELF = "TRANSFER_TO_SELF"
    const val PAY_TRANSFER_NOT_FOUND = "PAY_TRANSFER_NOT_FOUND"

    // ---- 红包 ----
    /** 红包份数超出 1..1000。 */
    const val RED_PACKET_COUNT_INVALID = "RED_PACKET_COUNT_INVALID"
    /** 总金额不足每份 1 分。 */
    const val RED_PACKET_AMOUNT_BELOW_COUNT = "RED_PACKET_AMOUNT_BELOW_COUNT"
    const val RED_PACKET_TYPE_INVALID = "RED_PACKET_TYPE_INVALID"
    const val RED_PACKET_NOT_FOUND = "RED_PACKET_NOT_FOUND"
    const val RED_PACKET_EXPIRED = "RED_PACKET_EXPIRED"
    /** 红包不在可领状态（已退款 / 已结束）。 */
    const val RED_PACKET_NOT_CLAIMABLE = "RED_PACKET_NOT_CLAIMABLE"
    /** 红包已被领完。 */
    const val RED_PACKET_DRAINED = "RED_PACKET_DRAINED"
    const val RED_PACKET_ALREADY_CLAIMED = "RED_PACKET_ALREADY_CLAIMED"
    /** 并发领取冲突重试耗尽，请重试。 */
    const val RED_PACKET_CLAIM_CONFLICT = "RED_PACKET_CLAIM_CONFLICT"
    /** 无权领取（非会话成员等，#84）。 */
    const val RED_PACKET_CLAIM_FORBIDDEN = "RED_PACKET_CLAIM_FORBIDDEN"

    // ---- 银行卡 ----
    const val BANK_CARD_HOLDER_NAME_REQUIRED = "BANK_CARD_HOLDER_NAME_REQUIRED"
    const val BANK_CARD_BANK_NAME_REQUIRED = "BANK_CARD_BANK_NAME_REQUIRED"
    const val BANK_CARD_NUMBER_REQUIRED = "BANK_CARD_NUMBER_REQUIRED"
    /** 卡号不是 8–19 位数字。 */
    const val BANK_CARD_NUMBER_INVALID = "BANK_CARD_NUMBER_INVALID"
    /** 不存在，或不属于当前用户 / 已失效。 */
    const val BANK_CARD_NOT_FOUND = "BANK_CARD_NOT_FOUND"
    /** 该卡还有未打款的提现单，不能解绑。 */
    const val BANK_CARD_HAS_PENDING_WITHDRAWALS = "BANK_CARD_HAS_PENDING_WITHDRAWALS"

    // ---- 提现 ----
    /** 不存在，或不属于当前用户。 */
    const val WITHDRAW_ORDER_NOT_FOUND = "WITHDRAW_ORDER_NOT_FOUND"
    /** 当前提现状态不允许此操作（审核 / 驳回 / 取消 / 打款 / 失败 / 挂起 / 解除挂起）。 */
    const val WITHDRAW_STATUS_NOT_ALLOWED = "WITHDRAW_STATUS_NOT_ALLOWED"
    /** 挂起前状态（hold_resume_to）被写脏，无法解除挂起。 */
    const val WITHDRAW_HOLD_RESUME_INVALID = "WITHDRAW_HOLD_RESUME_INVALID"
    const val WITHDRAW_HOLD_REASON_REQUIRED = "WITHDRAW_HOLD_REASON_REQUIRED"
    /** 提现单被并发修改（乐观锁），请重试。 */
    const val WITHDRAW_CONCURRENT_MODIFIED = "WITHDRAW_CONCURRENT_MODIFIED"

    // ---- 支付渠道 / 订单 ----
    const val PAY_CHANNEL_CODE_EXISTS = "PAY_CHANNEL_CODE_EXISTS"
    const val PAY_CHANNEL_NOT_FOUND = "PAY_CHANNEL_NOT_FOUND"
    /** 支付通道不存在、停用或平台未注册。 */
    const val PAY_CHANNEL_UNAVAILABLE = "PAY_CHANNEL_UNAVAILABLE"
    const val PAY_ORDER_NOT_FOUND = "PAY_ORDER_NOT_FOUND"
    /** 订单未支付成功，不能退款。 */
    const val PAY_ORDER_NOT_PAID = "PAY_ORDER_NOT_PAID"
    /** 模拟支付未开启（对外表现为 404）。 */
    const val MOCK_PAY_DISABLED = "MOCK_PAY_DISABLED"
    /**
     * 渠道配置的平台网关地址（支付宝 serverUrl、四方 apiUrl）被运维出站策略 `[egress]` 拒绝：解析到回环 / 内网 /
     * 链路本地、端口不在放行列表、或解析不出。一个包都没发出去——与网络失败、平台报错是两回事，要改的是渠道配置或部署配置。
     */
    const val PAY_ENDPOINT_FORBIDDEN = "PAY_ENDPOINT_FORBIDDEN"
}
