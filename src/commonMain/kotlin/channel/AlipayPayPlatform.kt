package channel

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import model.PayChannel
import model.PayOrder
import neton.http.client.HttpClientBody
import neton.http.client.HttpClient
import neton.http.client.create
import neton.security.crypto.RsaSha256
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 支付宝开放平台对接（RSA2 / SHA256withRSA）。
 *
 * 旧 Java 走官方 SDK，Kotlin/Native 没有对应 SDK，因此按开放平台文档直接实现协议。
 * 好在支付宝的公共参数与签名规则很稳定，手写反而少一层 SDK 版本包袱。
 *
 * 协议要点：
 * - 公共参数 `app_id / method / charset / sign_type / timestamp / version / biz_content`，
 *   业务参数放在 `biz_content` 这个 JSON 字符串里。
 * - **签名对象是公共参数本身**（含 biz_content 整串），按键名升序拼 `k=v&`，
 *   RSA2 私钥签名后 base64。回调验签同理，但要排除 `sign` 与 `sign_type`。
 * - WAP 支付不需要先请求服务端：把签好名的参数拼成 GET 地址让用户跳过去即可
 *   （`alipay.trade.wap.pay` 是页面接口）。所以 [prepay] 不发 HTTP 请求。
 * - 查单与退款是 POST 接口，响应形如 `{"alipay_trade_query_response":{...},"sign":"..."}`。
 *
 * 时间用支付宝要求的 `yyyy-MM-dd HH:mm:ss`（东八区），这里由 [nowTimestamp] 生成。
 */
@OptIn(ExperimentalTime::class)
class AlipayPayPlatform(
    private val http: HttpClient = HttpClient.create { requestMillis = 15_000 },
    /** 支付回调地址，部署方给出（支付宝需要能公网访问到我们）。 */
    private val notifyUrlOf: (PayChannel) -> String = { "" },
    /** 支付完成后用户跳回的页面地址。 */
    private val returnUrlOf: (PayChannel) -> String = { "" },
) : AbstractPayPlatform() {

    override val platformCode: String = PLATFORM_CODE

    override suspend fun prepay(order: PayOrder, channel: PayChannel): PrepayResult {
        val cfg = config(channel)
        val biz = buildJsonObject {
            put("out_trade_no", order.merchantOrderId)
            put("subject", order.subject)
            put("total_amount", yuan(order.price))
            // WAP 场景固定用 QUICK_WAP_PAY，其余销售产品码支付宝不接受
            put("product_code", "QUICK_WAP_PAY")
            order.body?.takeIf { it.isNotBlank() }?.let { put("body", it) }
            returnUrlOf(channel).takeIf { it.isNotBlank() }?.let { put("quit_url", it) }
        }.toString()

        val params = commonParams(cfg, METHOD_WAP_PAY, biz).toMutableMap()
        notifyUrlOf(channel).takeIf { it.isNotBlank() }?.let { params["notify_url"] = it }
        returnUrlOf(channel).takeIf { it.isNotBlank() }?.let { params["return_url"] = it }
        val signed = params + ("sign" to sign(params, cfg.privateKey))

        // 页面接口：不请求服务端，直接把签好名的参数拼成跳转地址
        val query = formEncode(signed)

        return PrepayResult(
            displayMode = displayModeOf(channel),
            payload = "${cfg.serverUrl}?$query",
            // 页面接口此刻还没有支付宝交易号，先用商户单号占位，回调/查单时会拿到真实的
            platformOrderNo = order.merchantOrderId,
        )
    }

    /**
     * 回调解析。回调是**表单参数**。
     *
     * **验签失败一律返回 null** —— 回调地址是公开的，不验签等于任何人拼几个参数就能白拿一笔货。
     * 验签要排除 `sign` 与 `sign_type` 两个键，这是支付宝的规定，漏掉就永远验不过。
     */
    override suspend fun parseNotify(
        request: PlatformNotifyRequest,
        channel: PayChannel,
    ): PlatformNotifyResult? {
        val cfg = runCatching { config(channel) }.getOrNull() ?: return null
        val params = request.params
        val signature = params["sign"]?.takeIf { it.isNotBlank() } ?: return null

        val signable = params.filterKeys { it != "sign" && it != "sign_type" }
        if (!RsaSha256.verifyBase64(cfg.alipayPublicKey, sortedJoin(signable).encodeToByteArray(), signature)) {
            return null
        }

        val merchantOrderId = params["out_trade_no"]?.takeIf { it.isNotBlank() } ?: return null
        // 支付宝没有"退款成功"这个交易状态：有退款金额就说明发生过退款
        val refunded = (params["refund_fee"]?.toDoubleOrNull() ?: 0.0) > 0.0
        val state = if (refunded) PlatformOrderState.REFUNDED else stateOf(params["trade_status"])

        return PlatformNotifyResult(
            merchantOrderId = merchantOrderId,
            platformOrderNo = params["trade_no"].orEmpty().ifEmpty { merchantOrderId },
            state = state,
            paidAt = if (state == PlatformOrderState.SUCCESS) {
                parseAlipayTime(params["gmt_payment"]) ?: Clock.System.now().toEpochMilliseconds()
            } else null,
        )
    }

    override suspend fun queryOrder(merchantOrderId: String, channel: PayChannel): PlatformNotifyResult? {
        val cfg = runCatching { config(channel) }.getOrNull() ?: return null
        val biz = buildJsonObject { put("out_trade_no", merchantOrderId) }.toString()
        val response = post(cfg, METHOD_QUERY, biz, "alipay_trade_query_response") ?: return null

        val state = stateOf(response["trade_status"]?.jsonPrimitive?.contentOrNull)
        return PlatformNotifyResult(
            merchantOrderId = merchantOrderId,
            platformOrderNo = response["trade_no"]?.jsonPrimitive?.contentOrNull ?: merchantOrderId,
            state = state,
            paidAt = if (state == PlatformOrderState.SUCCESS) {
                parseAlipayTime(response["send_pay_date"]?.jsonPrimitive?.contentOrNull)
                    ?: Clock.System.now().toEpochMilliseconds()
            } else null,
        )
    }

    /**
     * 退款 `alipay.trade.refund`。
     *
     * 支付宝的退款是**同步**返回结果的，不像下单那样等回调；
     * `fund_change=Y` 表示这次调用真的退了钱，`N` 表示重复请求（同一 refundNo 已退过）。
     * 两者都算成功 —— 幂等重试不该被当成失败。
     */
    override suspend fun refund(command: RefundCommand, channel: PayChannel): RefundResult? {
        val cfg = runCatching { config(channel) }.getOrNull() ?: return null
        val biz = buildJsonObject {
            put("out_trade_no", command.merchantOrderId)
            put("refund_amount", yuan(command.refundAmount))
            put("out_request_no", command.merchantRefundId)
            command.reason?.takeIf { it.isNotBlank() }?.let { put("refund_reason", it) }
        }.toString()

        val response = post(cfg, METHOD_REFUND, biz, "alipay_trade_refund_response") ?: return null
        return RefundResult(
            merchantRefundId = command.merchantRefundId,
            platformRefundId = response["trade_no"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            success = true,
        )
    }

    // ==================== 内部实现 ====================

    private fun commonParams(cfg: AlipayConfig, method: String, bizContent: String): Map<String, String> =
        mapOf(
            "app_id" to cfg.appId,
            "method" to method,
            "format" to "JSON",
            "charset" to "utf-8",
            "sign_type" to SIGN_TYPE,
            "timestamp" to nowTimestamp(),
            "version" to "1.0",
            "biz_content" to bizContent,
        )


    private fun sign(params: Map<String, String>, privateKey: String): String =
        RsaSha256.signBase64(privateKey, sortedJoin(params).encodeToByteArray())

    /** 发 POST 接口并取出业务响应节点；验签失败或业务码非 10000 返回 null。 */
    private suspend fun post(
        cfg: AlipayConfig,
        method: String,
        bizContent: String,
        responseField: String,
    ): JsonObject? {
        val params = commonParams(cfg, method, bizContent)
        val signed = params + ("sign" to sign(params, cfg.privateKey))
        val form = formEncode(signed)

        val root = postJson(
            http,
            cfg.serverUrl,
            HttpClientBody.Text(form, "application/x-www-form-urlencoded;charset=utf-8"),
        ) ?: return null
        val node = root[responseField] as? JsonObject ?: return null
        // 10000 是支付宝的"接口调用成功"，其余都带 sub_msg 说明原因
        return if (node["code"]?.jsonPrimitive?.contentOrNull != CODE_SUCCESS) null else node
    }

    /**
     * 交易状态映射。
     *
     * WAIT_BUYER_PAY=等待付款；TRADE_SUCCESS/TRADE_FINISHED=已付款（后者是不可退款的终态）；
     * TRADE_CLOSED=已关闭。未知状态按关闭处理 —— 宁可让用户重发，也不能误判成已付款。
     */
    private fun stateOf(tradeStatus: String?): PlatformOrderState = when (tradeStatus) {
        "WAIT_BUYER_PAY" -> PlatformOrderState.WAITING
        "TRADE_SUCCESS", "TRADE_FINISHED" -> PlatformOrderState.SUCCESS
        else -> PlatformOrderState.CLOSED
    }

    /** 分 → 元，保留两位小数。支付宝金额单位是元且必须带两位。 */
    private fun yuan(cents: Long): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    /** `yyyy-MM-dd HH:mm:ss` → epoch 毫秒；解析不了返回 null 由调用方兜底。 */
    private fun parseAlipayTime(text: String?): Long? {
        val t = text?.trim()?.takeIf { it.length >= 19 } ?: return null
        return try {
            val year = t.substring(0, 4).toInt()
            val month = t.substring(5, 7).toInt()
            val day = t.substring(8, 10).toInt()
            val hour = t.substring(11, 13).toInt()
            val minute = t.substring(14, 16).toInt()
            val second = t.substring(17, 19).toInt()
            val days = daysFromCivil(year, month, day)
            // 支付宝给的是东八区时间，转 UTC 要减 8 小时
            (days * 86_400L + hour * 3600L + minute * 60L + second - 8 * 3600L) * 1000L
        } catch (_: Exception) {
            null
        }
    }

    private fun nowTimestamp(): String {
        val epochSecond = Clock.System.now().toEpochMilliseconds() / 1000 + 8 * 3600 // 东八区
        val days = epochSecond / 86_400
        val secondOfDay = epochSecond % 86_400
        val (y, m, d) = civilFromDays(days)
        fun p(v: Long) = v.toString().padStart(2, '0')
        return "$y-${p(m)}-${p(d)} ${p(secondOfDay / 3600)}:${p(secondOfDay % 3600 / 60)}:${p(secondOfDay % 60)}"
    }

    /** 民用历 → 自 1970-01-01 起的天数（Howard Hinnant 的算法，避免引入日期库）。 */
    private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146_097 + doe - 719_468
    }

    private fun civilFromDays(days: Long): Triple<Long, Long, Long> {
        val z = days + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple(if (m <= 2) y + 1 else y, m, d)
    }

    private fun config(channel: PayChannel): AlipayConfig {
        val obj = configOf(channel)
        return AlipayConfig(
            serverUrl = obj.str("serverUrl") ?: DEFAULT_SERVER_URL,
            appId = obj.require("appId", channel),
            privateKey = obj.require("privateKey", channel),
            alipayPublicKey = obj.require("alipayPublicKey", channel),
        )
    }

    private data class AlipayConfig(
        val serverUrl: String,
        val appId: String,
        /** 我方私钥，用于给请求签名。 */
        val privateKey: String,
        /** 支付宝公钥，用于验回调的签名。 */
        val alipayPublicKey: String,
    )

    companion object {
        const val PLATFORM_CODE = "alipay"

        private const val DEFAULT_SERVER_URL = "https://openapi.alipay.com/gateway.do"
        private const val SIGN_TYPE = "RSA2"
        private const val CODE_SUCCESS = "10000"

        private const val METHOD_WAP_PAY = "alipay.trade.wap.pay"
        private const val METHOD_QUERY = "alipay.trade.query"
        private const val METHOD_REFUND = "alipay.trade.refund"
    }
}
