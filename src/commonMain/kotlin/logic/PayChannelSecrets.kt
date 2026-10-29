package logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import neton.core.http.ValidationError
import neton.core.http.ValidationException

/**
 * 支付通道配置里的密钥**只写不读**：配置里是商户私钥、签名 key 这类一旦泄露就能冒充我们收款 / 退款的东西，
 * 后台任何读接口（列表、详情）都只返回 [MASK]；保存时密钥仍是 [MASK]、留空或干脆不写，都沿用库里的原值，
 * 填了新值才替换。
 *
 * 哪些字段算密钥按字段名判定（与平台无关：四方类平台按实例参数化平台码，没法按平台码登记）：
 * 名字含 secret / password / private / token，或以 key 结尾但不含 public。
 * 宁可多遮：新接的平台不用回来登记字段也不会漏。
 */
object PayChannelSecrets {
    const val MASK = "******"

    private val json = Json

    fun isSecret(field: String): Boolean {
        val n = field.lowercase().filter { it.isLetterOrDigit() }
        if ("public" in n) return false
        return "secret" in n || "password" in n || "private" in n || "token" in n || n.endsWith("key")
    }

    /** 给后台看的配置：密钥换成 [MASK]。不是 JSON 对象的配置看不出哪里是密钥，整段遮掉。 */
    fun mask(config: String): String {
        if (config.isBlank()) return config
        val obj = parse(config) ?: return MASK
        return JsonObject(obj.mapValues { (k, v) -> if (isSecret(k) && v.isFilledString()) JsonPrimitive(MASK) else v }).toString()
    }

    /**
     * 后台提交的配置与库里原配置合并：密钥是 [MASK] / 空串 / 没写 → 沿用原值。
     * 新建（[stored] = null）时没有原值可沿用，提交 [MASK] 直接拒绝。
     */
    fun merge(submitted: String, stored: String?): String {
        if (submitted.trim() == MASK) return stored ?: reject()
        val obj = parse(submitted) ?: return submitted
        val old = stored?.let(::parse).orEmpty()
        val merged = LinkedHashMap(obj)
        for ((k, v) in obj) {
            if (!isSecret(k)) continue
            val text = (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            if (text != MASK && text.isNotBlank()) continue
            val keep = old[k]
            when {
                keep != null -> merged[k] = keep
                text == MASK -> reject()
            }
        }
        // 没写出来的密钥也沿用：运营粘贴配置时常把密钥那行删掉，不能因此把密钥清空
        old.forEach { (k, v) -> if (isSecret(k) && k !in merged) merged[k] = v }
        return JsonObject(merged).toString()
    }

    private fun parse(config: String): JsonObject? =
        runCatching { json.parseToJsonElement(config).jsonObject }.getOrNull()

    private fun kotlinx.serialization.json.JsonElement.isFilledString() =
        this is JsonPrimitive && isString && content.isNotEmpty()

    private fun reject(): Nothing = throw ValidationException(
        listOf(ValidationError("config", PaymentErrorCodes.PAY_CHANNEL_SECRET_REQUIRED, PaymentErrorCodes.PAY_CHANNEL_SECRET_REQUIRED)),
        PaymentErrorCodes.PAY_CHANNEL_SECRET_REQUIRED,
    )
}
