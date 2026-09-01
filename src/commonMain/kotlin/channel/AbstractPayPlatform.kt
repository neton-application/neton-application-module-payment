package channel

import neton.core.http.HttpHeaders

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.PayChannel
import neton.http.client.NetonHttpBody
import neton.http.client.NetonHttpClient
import neton.http.client.NetonHttpMethod
import neton.http.client.NetonHttpRequest

/**
 * 支付平台实现的公共骨架。
 *
 * 各家平台**真正不同**的只有四件事：签名算法、接口路径、响应外壳、状态码含义。
 * 除此之外的部分（待签串怎么拼、配置怎么读、请求怎么发、IP 怎么规范化）每家都一样，
 * 抄第二遍就会开始出现细微差异 —— 而支付这类代码的细微差异往往要到联调才暴露。
 *
 * 所以这里只收拢**确已重复**的部分，不预先抽象尚未出现的共性：
 * 签名算法与状态映射留在各实现里，因为它们本就该不同。
 */
abstract class AbstractPayPlatform : PayPlatform {

    protected val json: Json = Json { ignoreUnknownKeys = true }

    /**
     * 待签串：排除空值与 `sign`，按键名升序拼成 `k=v` 并以 `&` 连接。
     *
     * 这是各家共同的部分。差异在后续步骤 —— 支付宝直接对它做 RSA2，
     * 四方则要再接一段 `&key=密钥` 后取 MD5。所以这里**不负责拼密钥**，
     * 由各实现决定，避免把某一家的特例塞进公共逻辑。
     *
     * 注意不做 URL 编码：签名针对原始值，编码只在拼接请求时做。
     * 顺序搞反是这类对接最常见的错误。
     */
    protected fun sortedJoin(params: Map<String, String?>): String =
        params.entries
            .filter { it.key != "sign" && !it.value.isNullOrBlank() }
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }

    /**
     * IPv6 回环换成 IPv4。
     *
     * 部分平台风控不认 `::1`，直接判参数非法。这类"细节不对就整单失败"的规则
     * 每家都一样，没必要各写一遍。
     */
    protected fun normalizeIp(ip: String?): String = when (ip) {
        null, "", "::1", "0:0:0:0:0:0:0:1" -> "127.0.0.1"
        else -> ip
    }

    /** 通道声明的呈现方式；配错时退回跳转，至少不会让前端拿到无法处理的类型。 */
    protected fun displayModeOf(channel: PayChannel): PayDisplayMode =
        runCatching { PayDisplayMode.valueOf(channel.displayMode) }
            .getOrDefault(PayDisplayMode.REDIRECT_URL)

    /** 通道凭据（JSON）。取不到必填项就抛，带上通道码方便定位是哪条线配错了。 */
    protected fun configOf(channel: PayChannel): JsonObject =
        runCatching { json.parseToJsonElement(channel.config).jsonObject }
            .getOrElse { error("$platformCode 通道 ${channel.code} 的 config 不是合法 JSON") }

    protected fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    protected fun JsonObject.require(key: String, channel: PayChannel): String =
        str(key) ?: error("$platformCode 通道 ${channel.code} 缺少 $key")

    /**
     * 发请求并解析 JSON 响应。
     *
     * 任何失败（网络异常、非 2xx、响应不是 JSON）都返回 null 而不是抛：
     * 调用点多在对账与回调路径上，单笔失败不该中断整批，由上层决定重试还是跳过。
     */
    protected suspend fun postJson(
        http: NetonHttpClient,
        url: String,
        body: NetonHttpBody,
        headers: Map<String, String> = emptyMap(),
        method: NetonHttpMethod = NetonHttpMethod.Post,
    ): JsonObject? = try {
        val resp = http.request(
            NetonHttpRequest(method = method, url = url, headers = HttpHeaders.from(headers), body = body)
        )
        if (resp.statusCode !in 200..299) null
        else json.parseToJsonElement(resp.body).jsonObject
    } catch (_: Exception) {
        null
    }

    /**
     * 百分号编码。只保留 RFC 3986 的非保留字符，其余一律转义。
     *
     * 不能只处理 `&=` 之类"看起来危险"的字符：商品名里的中文、空格同样会破坏请求，
     * 而平台通常只回一句参数非法。
     */
    protected fun urlEncode(value: String): String = buildString {
        for (b in value.encodeToByteArray()) {
            val i = b.toInt() and 0xff
            val c = i.toChar()
            if (i < 128 && (c.isLetterOrDigit() || c in "-_.~")) append(c)
            else append('%').append(i.toString(16).uppercase().padStart(2, '0'))
        }
    }

    /** 参数拼成 `k=v&` 形式的请求串，值做百分号编码。 */
    protected fun formEncode(params: Map<String, String>): String =
        params.entries.sortedBy { it.key }
            .joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
}
