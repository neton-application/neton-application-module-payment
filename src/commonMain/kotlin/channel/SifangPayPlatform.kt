package channel

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import model.PayChannel
import model.PayOrder
import neton.http.client.NetonHttpBody
import neton.http.client.NetonHttpClient
import neton.http.client.create
import neton.http.client.NetonHttpMethod
import neton.http.client.NetonHttpRequest
import neton.security.digest.Md5
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 四方支付平台对接。
 *
 * **一套协议服务多家平台**：七九、至尊、文腾等都是同一套四方系统的不同实例（多半是白标），
 * 网关地址、商户号、密钥各不相同，但接口、签名与状态码完全一致。
 * 旧 Java 里它们是三个类（`WentengPayClient` / `QijiuPayClient` / `ZhizunPayClient`），
 * 而三个类加起来只有构造函数不同 —— 逻辑全在共同的抽象基类里。
 * 这里直接用 [platformCode] 参数化，一份实现登记多个实例即可。
 *
 * 协议要点：
 * - 请求为表单参数，签名 = 排除空值与 sign → 按键名升序拼 `k=v&` → 接 `key={密钥}` → MD5**大写**。
 *   （注意与另一家四方 Chiming 的区别：那家是小写，且回调走 JSON body。签名大小写弄错的表现是
 *   平台一直回「签名错误」，而报错不会告诉你差在哪。）
 * - 下单 `POST {apiUrl}/api/pay/unifiedorder`，查单 `POST {apiUrl}/api/pay/query_order`。
 * - 响应以 `retCode == "SUCCESS"` 判成功，支付地址在 `payParams.payUrl`。
 * - 回调是**表单参数**（非 JSON），字段 `mchOrderNo` / `channelOrderNo` / `status` / `paySuccTime`。
 * - `productId` 指定走平台的哪条通道 —— 对应本系统的 [PayChannel.platformChannelId]。
 *
 * 凭据（apiUrl / mchId / key）来自 [PayChannel.config]：同平台下每条通道可能是不同商户号。
 */
@OptIn(ExperimentalTime::class)
class SifangPayPlatform(
    override val platformCode: String,
    private val http: NetonHttpClient = NetonHttpClient.create { requestMillis = 15_000 },
    /** 回调地址，由部署方给出（平台需要能公网访问到我们）。 */
    private val notifyUrlOf: (PayChannel) -> String = { "" },
) : AbstractPayPlatform() {

    override suspend fun prepay(order: PayOrder, channel: PayChannel): PrepayResult {
        val cfg = config(channel)
        val productId = channel.platformChannelId?.takeIf { it.isNotBlank() }
            ?: error("$platformCode 通道 ${channel.code} 缺少 platformChannelId(productId)")

        val params = buildMap<String, Any> {
            put("mchId", cfg.mchId)
            put("productId", productId)
            put("mchOrderNo", order.merchantOrderId)
            put("amount", order.price) // 平台以分为单位，与本系统一致
            put("subject", order.subject)
            put("clientIp", normalizeIp(order.userIp))
            notifyUrlOf(channel).takeIf { it.isNotBlank() }?.let { put("notifyUrl", it) }
            order.body?.takeIf { it.isNotBlank() }?.let { put("body", it) }
        }

        val response = post("${cfg.apiUrl}/api/pay/unifiedorder", params, cfg.key)
            ?: error("$platformCode 下单失败：无响应")
        val payParams = successData(response, "payParams")
            ?: error("$platformCode 下单失败：${retMsg(response)}")
        val payUrl = payParams["payUrl"]?.jsonPrimitive?.contentOrNull
            ?: error("$platformCode 下单失败：未返回支付地址")

        return PrepayResult(
            displayMode = displayModeOf(channel),
            payload = payUrl,
            platformOrderNo = response["payOrderId"]?.jsonPrimitive?.contentOrNull
                ?: order.merchantOrderId,
        )
    }

    /**
     * 回调解析。回调是**表单参数**，不是 JSON。
     *
     * **验签失败一律返回 null** —— 回调地址是公开的，不验签等于任何人拼几个参数就能白拿一笔货。
     */
    override suspend fun parseNotify(
        request: PlatformNotifyRequest,
        channel: PayChannel,
    ): PlatformNotifyResult? {
        val cfg = runCatching { config(channel) }.getOrNull() ?: return null
        val params = request.params
        val sign = params["sign"]?.takeIf { it.isNotBlank() } ?: return null
        if (sign != signOf(params.filterKeys { it != "sign" }, cfg.key)) return null

        val merchantOrderId = params["mchOrderNo"]?.takeIf { it.isNotBlank() } ?: return null
        val state = stateOf(params["status"]?.toIntOrNull())
        return PlatformNotifyResult(
            merchantOrderId = merchantOrderId,
            platformOrderNo = params["channelOrderNo"]?.takeIf { it.isNotBlank() }
                ?: params["payOrderId"].orEmpty().ifEmpty { merchantOrderId },
            state = state,
            paidAt = if (state == PlatformOrderState.SUCCESS) {
                params["paySuccTime"]?.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds()
            } else null,
        )
    }

    override suspend fun queryOrder(merchantOrderId: String, channel: PayChannel): PlatformNotifyResult? {
        val cfg = runCatching { config(channel) }.getOrNull() ?: return null
        val params = mapOf<String, Any>("mchId" to cfg.mchId, "mchOrderNo" to merchantOrderId)
        val response = post("${cfg.apiUrl}/api/pay/query_order", params, cfg.key) ?: return null
        if (response["retCode"]?.jsonPrimitive?.contentOrNull != RET_SUCCESS) return null

        val state = stateOf(response["status"]?.jsonPrimitive?.intOrNull)
        return PlatformNotifyResult(
            merchantOrderId = merchantOrderId,
            platformOrderNo = response["channelOrderNo"]?.jsonPrimitive?.contentOrNull
                ?: merchantOrderId,
            state = state,
            paidAt = if (state == PlatformOrderState.SUCCESS) {
                response["paySuccTime"]?.jsonPrimitive?.longOrNull
                    ?: Clock.System.now().toEpochMilliseconds()
            } else null,
        )
    }

    // ==================== 内部实现 ====================

    private suspend fun post(url: String, params: Map<String, Any>, key: String): JsonObject? {
        val signed = params + ("sign" to signOf(params.mapValues { it.value.toString() }, key))
        val body = JsonObject(signed.mapValues { (_, v) -> JsonPrimitive(v.toString()) }).toString()
        return postJson(http, url, NetonHttpBody.Json(body))
    }

    /**
     * 签名：排除空值与 sign，按键名升序拼 `k=v&`，末尾接 `key={密钥}`，MD5 后**转大写**。
     *
     * 大写这一步是本系四方与 Chiming 的唯一可见差异，也最容易抄错。
     */
    private fun signOf(params: Map<String, String?>, key: String): String {
        // 基类给出排序拼接，本平台在其后接密钥再取 MD5 并转大写
        val plain = sortedJoin(params)
        return Md5.hex(if (plain.isEmpty()) "key=$key" else "$plain&key=$key").uppercase()
    }

    /** `retCode == SUCCESS` 才算成功。 */
    private fun successData(response: JsonObject, field: String): JsonObject? {
        if (response["retCode"]?.jsonPrimitive?.contentOrNull != RET_SUCCESS) return null
        return response[field] as? JsonObject
    }

    private fun retMsg(response: JsonObject): String =
        response["retMsg"]?.jsonPrimitive?.contentOrNull ?: "未知错误"

    /**
     * 平台状态码 → 本系统状态。
     *
     * 0=订单生成 1=支付中 → 待支付；2=支付成功 3=业务处理完成 → 成功；其余（含未知码）→ 关闭。
     * 未知码按关闭而非成功：宁可让用户重新发起，也不能误判成已付款。
     */
    private fun stateOf(status: Int?): PlatformOrderState = when (status) {
        0, 1 -> PlatformOrderState.WAITING
        2, 3 -> PlatformOrderState.SUCCESS
        else -> PlatformOrderState.CLOSED
    }

    private fun config(channel: PayChannel): SifangConfig {
        val obj = configOf(channel)
        return SifangConfig(
            apiUrl = obj.require("apiUrl", channel).trimEnd('/'),
            mchId = obj.require("mchId", channel),
            key = obj.require("key", channel),
        )
    }

    private data class SifangConfig(val apiUrl: String, val mchId: String, val key: String)

    companion object {
        private const val RET_SUCCESS = "SUCCESS"

        /** 已知使用本套协议的平台。新增同系平台只需往这里加一个 code。 */
        const val QIJIU = "qijiu"
        const val ZHIZUN = "zhizun"
        const val WENTENG = "wenteng"

        val KNOWN_CODES = listOf(QIJIU, ZHIZUN, WENTENG)
    }
}
