package channel

import model.PayChannel
import model.PayOrder

/**
 * 支付方式：用户眼里"用什么付"。
 *
 * 与平台、通道都正交：同一种方式可以由多个平台、多条通道提供。
 * 前端拿它决定图标与分组，用户不需要知道背后走的是哪家。
 */
enum class PayMethod { ALIPAY, WECHAT, UNIONPAY, BANK_CARD, OTHER }

/**
 * 支付结果的呈现方式。前端据此决定怎么把用户送到收银台。
 *
 * 之所以要有这个维度：同一条通道在不同场景返回的东西根本不是一类
 * —— H5 给跳转地址、扫码给二维码内容、APP 给一串待签名的 SDK 参数。
 * 只回一个地址字符串会逼前端按通道码硬猜，每接一条通道就改一次前端。
 */
enum class PayDisplayMode { REDIRECT_URL, QR_CODE, SDK_PARAMS }

/** 下单结果。 */
data class PrepayResult(
    val displayMode: PayDisplayMode,
    val payload: String,
    /** 平台侧订单号，用于对账与查单。 */
    val platformOrderNo: String,
)

/**
 * 平台回调的原始请求。
 *
 * 同时带上 query、body 与 headers，是因为各平台验签所依据的东西不同：
 * 老式平台对 query 参数排序签名，微信 V3 则对 `header 时间戳 + 随机串 + body` 验签。
 * 只传一个参数 map 的话，验签强的平台根本接不进来 —— 而验签正是防止伪造回调
 * （伪造一条就是白送一笔货）的唯一手段。
 */
data class PlatformNotifyRequest(
    val params: Map<String, String> = emptyMap(),
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
)

/** 平台侧的订单状态。与本地 PayOrder.status 分开：这是"平台怎么说"，不是"我们记成什么"。 */
enum class PlatformOrderState { WAITING, SUCCESS, CLOSED, REFUNDED }

/** 发起退款的指令。 */
data class RefundCommand(
    /** 原支付订单的商户单号。 */
    val merchantOrderId: String,
    /** 本次退款的商户单号，**幂等锚点**：同一个号重复提交，平台应认作同一笔。 */
    val merchantRefundId: String,
    /** 退款金额（分）。可小于订单金额，即部分退款。 */
    val refundAmount: Long,
    /**
     * 原订单总额（分）。部分平台（微信）要求同时给出总额与退款额来校验部分退款；
     * 为空时由实现按退款额兜底，即视为全额退款。
     */
    val originalAmount: Long? = null,
    val reason: String? = null,
)

/** 退款结果。 */
data class RefundResult(
    val merchantRefundId: String,
    val platformRefundId: String,
    /**
     * 是否已确认退款。
     *
     * 部分平台（如支付宝）同步返回结果，此处即为最终态；
     * 另一些平台是异步的，这里为 false 表示"已受理"，真正的结果由退款回调带来。
     */
    val success: Boolean,
)

/** 回调解析（或主动查单）的结果。 */
data class PlatformNotifyResult(
    val merchantOrderId: String,
    val platformOrderNo: String,
    val state: PlatformOrderState,
    /** 支付成功时间；非成功态为 null。 */
    val paidAt: Long? = null,
)

/**
 * 支付平台：对接的那家支付公司，一家一个实现。
 *
 * **平台与通道是两件事**，混在一起是这类系统最常见的建模错误：
 * - 平台（本接口）是**协议**——网关地址、商户号、怎么签名、回调怎么解析。
 *   汇付天下、七九、文腾各是一个平台，各写一个实现。
 * - 通道（[PayChannel]）是平台下的**一条具体线路**——「汇付天下的支付宝1」
 *   「汇付天下的支付宝2」「汇付天下的微信1」。它们协议完全相同，
 *   区别只是平台侧的通道编号与各自的费率、限额、开关。
 *
 * 把通道也做成实现类，就会出现 `HuifuAlipay1Client`、`HuifuAlipay2Client` 这种
 * 只有常量不同的重复类；而运营想加一条通道时得改代码发版。
 * 现在加通道 = 后台加一行数据，代码不动。
 *
 * 所以本接口的每个方法都接收 [PayChannel]：平台知道"怎么调"，通道提供"用哪套凭据、
 * 走平台的哪条线"。
 */
interface PayPlatform {

    /** 平台编码，与 [PayChannel.platformCode] 对应，如 huifu / qijiu / wenteng。 */
    val platformCode: String

    /**
     * 是否为沙箱（测试用）实现。真实平台一律为 false，无需覆写。
     *
     * 沙箱实现不校验签名、也不产生真实扣款，因此**与真实平台互斥**：
     * 系统在沙箱模式下只接受沙箱平台，非沙箱模式下只接受真实平台
     * （见 `PayOrderLogic` 的通道解析）。这样"生产环境误开沙箱"的后果是收款立刻全断、
     * 立即会被发现，而不是安静地让人白拿商品。
     */
    val sandbox: Boolean get() = false

    /** 下单：按通道给定的凭据调平台 API，返回可把用户送去付款的结果。 */
    suspend fun prepay(order: PayOrder, channel: PayChannel): PrepayResult

    /**
     * 解析并验签异步回调。验签失败或数据不可信时返回 null，调用方据此拒绝。
     *
     * **实现必须验签**：回调地址是公开的，不验签等于任何人都能伪造一笔"支付成功"。
     * 验签用的密钥来自 [channel]，因为同一平台下每条通道的商户号/密钥可能不同。
     */
    suspend fun parseNotify(request: PlatformNotifyRequest, channel: PayChannel): PlatformNotifyResult?

    /**
     * 主动查单。
     *
     * 回调**一定会丢**：平台重试有上限、我们也可能正好在重启。没有查单能力时，
     * 丢掉的那笔就只能等用户来投诉。对账任务据此把状态补齐。
     *
     * 默认返回 null 表示未实现查单（沙箱等）；真实平台都应实现。
     */
    suspend fun queryOrder(merchantOrderId: String, channel: PayChannel): PlatformNotifyResult? = null

    /**
     * 发起退款。
     *
     * 默认返回 null 表示**该平台不支持退款** —— 这不是偷懒：四方平台普遍不开放退款接口
     * （旧 Java 里 Wenteng 系与 Chiming 的三个退款方法全是 NOT_IMPLEMENTED），
     * 这种情况下退款只能线下操作，由后台登记冲正。返回 null 让上层能明确区分
     * "平台不支持"与"调用失败"，前者不该重试。
     */
    suspend fun refund(command: RefundCommand, channel: PayChannel): RefundResult? = null

    /**
     * 解析退款回调。异步退款的平台用它，同步返回结果的平台无需实现。
     */
    suspend fun parseRefundNotify(
        request: PlatformNotifyRequest,
        channel: PayChannel,
    ): RefundResult? = null

    /**
     * 回调的应答内容。
     *
     * 平台靠这个判断我们收到没有，格式各家不同（支付宝要纯文本 `success`，
     * 微信 V3 要一段 JSON）。答错了平台会认为投递失败并持续重试，
     * 所以这件事必须由平台自己说了算，不能由 controller 统一硬编码。
     */
    fun ackBody(accepted: Boolean): String = if (accepted) "success" else "fail"
}
